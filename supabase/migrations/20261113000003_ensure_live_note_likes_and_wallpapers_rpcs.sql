-- ============================================================================
-- Migration: 20261113000003_ensure_live_note_likes_and_wallpapers_rpcs.sql
-- Description:
--   Live Supabase deployment & hardening against public.notes + public.note_likes,
--   notifications ('note_like', 'dm', 'message_request'), resilient client RLS
--   fallbacks on note_likes and dm_participants (wallpaper-only guarded trigger),
--   dm_conversations & profiles wallpaper columns, REPLICA IDENTITY FULL for
--   Realtime read receipts, synced mark_dm_read + mark_conversation_read,
--   toggle_note_like, set_chat_wallpaper, get_dm_conversation_detail, and
--   PostgREST schema/config cache reload.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. public.note_likes table + indexes + RLS for secure direct fallback & RPC
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.note_likes (
    note_id uuid NOT NULL REFERENCES public.notes(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (note_id, user_id)
);

CREATE INDEX IF NOT EXISTS note_likes_user_idx
    ON public.note_likes (user_id);

CREATE INDEX IF NOT EXISTS note_likes_note_created_idx
    ON public.note_likes (note_id, created_at DESC);

ALTER TABLE public.note_likes ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.note_likes FROM PUBLIC, anon;
GRANT SELECT, INSERT, DELETE ON TABLE public.note_likes TO authenticated;

CREATE OR REPLACE FUNCTION public._can_view_active_note(p_note_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    IF v_uid IS NULL OR p_note_id IS NULL THEN
        RETURN false;
    END IF;

    RETURN EXISTS (
        SELECT 1
        FROM public.notes n
        WHERE n.id = p_note_id
          AND NOT public._are_blocked(v_uid, n.user_id)
          AND (
              n.user_id = v_uid
              OR (
                  n.is_active = true
                  AND n.expires_at > now()
                  AND EXISTS (
                      SELECT 1
                      FROM public.follows f
                      WHERE f.follower_id = v_uid
                        AND f.following_id = n.user_id
                  )
              )
          )
    );
END;
$$;

CREATE OR REPLACE FUNCTION public._can_like_active_note(p_note_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    IF v_uid IS NULL OR p_note_id IS NULL THEN
        RETURN false;
    END IF;

    RETURN EXISTS (
        SELECT 1
        FROM public.notes n
        WHERE n.id = p_note_id
          AND n.is_active = true
          AND n.expires_at > now()
          AND NOT public._are_blocked(v_uid, n.user_id)
    );
END;
$$;

DROP POLICY IF EXISTS "note_likes_no_direct_access" ON public.note_likes;
DROP POLICY IF EXISTS "note_likes_select_visible" ON public.note_likes;
DROP POLICY IF EXISTS "note_likes_insert_own" ON public.note_likes;
DROP POLICY IF EXISTS "note_likes_delete_own" ON public.note_likes;

CREATE POLICY "note_likes_select_visible" ON public.note_likes
    FOR SELECT TO authenticated
    USING (public._can_view_active_note(note_id));

CREATE POLICY "note_likes_insert_own" ON public.note_likes
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = (SELECT auth.uid())
        AND public._can_like_active_note(note_id)
    );

CREATE POLICY "note_likes_delete_own" ON public.note_likes
    FOR DELETE TO authenticated
    USING (user_id = (SELECT auth.uid()));

-- ---------------------------------------------------------------------------
-- 2. Notifications constraint ('note_like', 'dm', 'message_request') + trigger
-- ---------------------------------------------------------------------------
ALTER TABLE public.notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('follow', 'note', 'note_like', 'dm', 'message_request'));

CREATE UNIQUE INDEX IF NOT EXISTS notifications_unique_note_like
    ON public.notifications (user_id, actor_id, reference_id)
    WHERE (type = 'note_like');

CREATE OR REPLACE FUNCTION public.trg_note_like_notification()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_author_id uuid;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT n.user_id INTO v_author_id
        FROM public.notes n
        WHERE n.id = NEW.note_id;

        IF v_author_id IS NOT NULL
           AND NEW.user_id <> v_author_id
           AND NOT public._are_blocked(NEW.user_id, v_author_id) THEN
            INSERT INTO public.notifications (user_id, actor_id, type, reference_id, is_read, created_at)
            VALUES (v_author_id, NEW.user_id, 'note_like', NEW.note_id::text, false, NEW.created_at)
            ON CONFLICT (user_id, actor_id, reference_id) WHERE (type = 'note_like')
            DO UPDATE SET is_read = false, created_at = EXCLUDED.created_at;
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        DELETE FROM public.notifications
        WHERE type = 'note_like'
          AND actor_id = OLD.user_id
          AND reference_id = OLD.note_id::text;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_note_like_notification ON public.note_likes;
CREATE TRIGGER trg_note_like_notification
    AFTER INSERT OR DELETE ON public.note_likes
    FOR EACH ROW EXECUTE FUNCTION public.trg_note_like_notification();

-- ---------------------------------------------------------------------------
-- 3. Update get_user_notifications to resolve note snippet for note & note_like
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_user_notifications(p_limit integer DEFAULT 50, p_offset integer DEFAULT 0)
RETURNS TABLE(
    id uuid,
    user_id uuid,
    actor_id uuid,
    type text,
    reference_id text,
    is_read boolean,
    created_at timestamp with time zone,
    actor_username text,
    actor_display_name text,
    actor_avatar_url text,
    actor_is_verified boolean,
    note_content text,
    note_expires_at timestamp with time zone
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        n.id,
        n.user_id,
        n.actor_id,
        n.type,
        n.reference_id,
        n.is_read,
        n.created_at,
        a.username::text,
        a.display_name,
        a.avatar_url,
        a.is_verified,
        nt.content,
        nt.expires_at
    FROM public.notifications n
    JOIN public.profiles a ON a.id = n.actor_id
    LEFT JOIN public.notes nt
        ON n.type IN ('note', 'note_like')
       AND nt.id::text = n.reference_id
    WHERE n.user_id = auth.uid()
      AND NOT public._are_blocked(n.user_id, n.actor_id)
    ORDER BY n.created_at DESC
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 50), 1), 100)
    OFFSET GREATEST(COALESCE(p_offset, 0), 0);
$$;

-- ---------------------------------------------------------------------------
-- 4. Bulletproof toggle_note_like(p_note_id uuid) against public.notes
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.toggle_note_like(p_note_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_note record;
    v_liked boolean := false;
    v_likes_count int := 0;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF p_note_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOTE_NOT_FOUND');
    END IF;

    SELECT n.id, n.user_id, n.is_active, n.expires_at
    INTO v_note
    FROM public.notes n
    WHERE n.id = p_note_id;

    -- Fallback: allow lookup by author user_id if upstream caller forwarded author UUID
    IF v_note.id IS NULL THEN
        SELECT n.id, n.user_id, n.is_active, n.expires_at
        INTO v_note
        FROM public.notes n
        WHERE n.user_id = p_note_id
          AND n.is_active = true
          AND n.expires_at > now()
        ORDER BY n.created_at DESC
        LIMIT 1;
    END IF;

    IF v_note.id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOTE_NOT_FOUND');
    END IF;

    IF NOT v_note.is_active OR v_note.expires_at <= now() THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOTE_EXPIRED');
    END IF;

    IF public._are_blocked(v_caller, v_note.user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'BLOCKED');
    END IF;

    PERFORM pg_advisory_xact_lock(
        hashtext('buildx:note_like:' || v_note.id::text || ':' || v_caller::text)
    );

    IF EXISTS (
        SELECT 1 FROM public.note_likes nl
        WHERE nl.note_id = v_note.id AND nl.user_id = v_caller
    ) THEN
        DELETE FROM public.note_likes
        WHERE note_id = v_note.id AND user_id = v_caller;
        v_liked := false;
    ELSE
        INSERT INTO public.note_likes (note_id, user_id)
        VALUES (v_note.id, v_caller)
        ON CONFLICT DO NOTHING;
        v_liked := true;
    END IF;

    SELECT count(*)::int INTO v_likes_count
    FROM public.note_likes nl
    WHERE nl.note_id = v_note.id;

    RETURN jsonb_build_object(
        'success', true,
        'note_id', v_note.id,
        'liked', v_liked,
        'likes_count', v_likes_count
    );
END;
$$;

-- ---------------------------------------------------------------------------
-- 5. get_followed_active_notes(), get_my_active_note(), create_note()
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_followed_active_notes()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_result jsonb;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    WITH visible_notes AS (
        SELECT
            n.id,
            n.user_id,
            n.content,
            n.created_at,
            n.expires_at,
            pr.username::text AS username,
            pr.handle,
            pr.display_name,
            pr.avatar_url,
            pr.is_verified,
            (
                SELECT count(*)::int
                FROM public.note_likes nl
                WHERE nl.note_id = n.id
            ) AS likes_count,
            EXISTS (
                SELECT 1
                FROM public.note_likes nl
                WHERE nl.note_id = n.id AND nl.user_id = v_caller
            ) AS is_liked_by_me,
            CASE WHEN n.user_id = v_caller THEN 0 ELSE 1 END AS sort_priority
        FROM public.notes n
        JOIN public.profiles pr ON pr.id = n.user_id
        WHERE n.is_active = true
          AND n.expires_at > now()
          AND (
              n.user_id = v_caller
              OR n.user_id IN (
                  SELECT following_id FROM public.follows WHERE follower_id = v_caller
              )
          )
          AND NOT public._are_blocked(v_caller, n.user_id)
        ORDER BY sort_priority ASC, n.created_at DESC
        LIMIT 30
    )
    SELECT coalesce(jsonb_agg(
        jsonb_build_object(
            'id', id,
            'note_id', id,
            'user_id', user_id,
            'content', content,
            'created_at', created_at,
            'expires_at', expires_at,
            'is_own', (user_id = v_caller),
            'likes_count', likes_count,
            'is_liked_by_me', is_liked_by_me,
            'author', jsonb_build_object(
                'id', user_id,
                'username', username,
                'handle', handle,
                'display_name', display_name,
                'avatar_url', avatar_url,
                'is_verified', is_verified
            )
        )
    ), '[]'::jsonb) INTO v_result
    FROM visible_notes;

    RETURN v_result;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_my_active_note()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT jsonb_build_object(
        'id', n.id,
        'note_id', n.id,
        'user_id', n.user_id,
        'content', n.content,
        'created_at', n.created_at,
        'expires_at', n.expires_at,
        'is_active', n.is_active,
        'likes_count', (
            SELECT count(*)::int
            FROM public.note_likes nl
            WHERE nl.note_id = n.id
        ),
        'is_liked_by_me', EXISTS (
            SELECT 1
            FROM public.note_likes nl
            WHERE nl.note_id = n.id AND nl.user_id = auth.uid()
        ),
        'likers', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id', p.id,
                    'username', p.username::text,
                    'handle', p.handle,
                    'display_name', p.display_name,
                    'avatar_url', p.avatar_url,
                    'is_verified', p.is_verified
                )
                ORDER BY sub.created_at DESC
            )
            FROM (
                SELECT nl.user_id, nl.created_at
                FROM public.note_likes nl
                WHERE nl.note_id = n.id
                  AND NOT public._are_blocked(auth.uid(), nl.user_id)
                ORDER BY nl.created_at DESC
                LIMIT 20
            ) sub
            JOIN public.profiles p ON p.id = sub.user_id
        ), '[]'::jsonb)
    )
    FROM public.notes n
    WHERE n.user_id = auth.uid()
      AND n.is_active
      AND n.expires_at > now()
    ORDER BY n.created_at DESC
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.create_note(note_content text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user uuid := auth.uid();
    v_clean text;
    v_id uuid;
    v_created timestamptz := now();
BEGIN
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;
    v_clean := trim(note_content);
    IF char_length(v_clean) < 1 OR char_length(v_clean) > 60 THEN
        RAISE EXCEPTION 'NOTE_LENGTH';
    END IF;

    UPDATE public.notes
    SET is_active = false
    WHERE user_id = v_user AND is_active;

    INSERT INTO public.notes (user_id, content, created_at, expires_at, is_active)
    VALUES (v_user, v_clean, v_created, v_created + interval '24 hours', true)
    RETURNING id INTO v_id;

    RETURN jsonb_build_object(
        'id', v_id,
        'note_id', v_id,
        'user_id', v_user,
        'content', v_clean,
        'created_at', v_created,
        'expires_at', v_created + interval '24 hours',
        'is_active', true,
        'likes_count', 0,
        'is_liked_by_me', false,
        'likers', '[]'::jsonb
    );
END;
$$;

-- ---------------------------------------------------------------------------
-- 6. Synced mark_dm_read + mark_conversation_read + Realtime replica identity
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.mark_dm_read(p_conversation_id uuid, p_last_read_message_id uuid DEFAULT NULL::uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_msg_id uuid := p_last_read_message_id;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF v_msg_id IS NULL THEN
        SELECT last_message_id INTO v_msg_id
        FROM public.dm_conversations
        WHERE id = p_conversation_id;
    END IF;

    UPDATE public.dm_participants
    SET
        marked_unread = false,
        last_read_at = now(),
        last_read_message_id = COALESCE(v_msg_id, last_read_message_id)
    WHERE conversation_id = p_conversation_id
      AND user_id = v_caller
      AND status != 'left';

    RETURN jsonb_build_object('success', true, 'is_marked_unread', false);
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_conversation_read(p_conversation_id uuid, p_last_read_message_id uuid DEFAULT NULL::uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_msg_id uuid := p_last_read_message_id;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF v_msg_id IS NULL THEN
        SELECT last_message_id INTO v_msg_id
        FROM public.dm_conversations
        WHERE id = p_conversation_id;
    END IF;

    UPDATE public.dm_participants
    SET
        marked_unread = false,
        last_read_at = now(),
        last_read_message_id = COALESCE(v_msg_id, last_read_message_id)
    WHERE conversation_id = p_conversation_id
      AND user_id = v_caller
      AND status != 'left';

    RETURN jsonb_build_object('success', true, 'is_marked_unread', false);
END;
$$;

ALTER TABLE public.dm_participants REPLICA IDENTITY FULL;
ALTER TABLE public.dm_messages REPLICA IDENTITY FULL;

-- ---------------------------------------------------------------------------
-- 7. Chat Wallpapers: dm_participants, dm_conversations, profiles + guard
-- ---------------------------------------------------------------------------
ALTER TABLE public.dm_participants
    ADD COLUMN IF NOT EXISTS wallpaper_id text DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS wallpaper_custom_url text DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS wallpaper_dim real NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS wallpaper_blur boolean NOT NULL DEFAULT false;

ALTER TABLE public.dm_conversations
    ADD COLUMN IF NOT EXISTS wallpaper_id text DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS wallpaper_custom_url text DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS wallpaper_dim real NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS wallpaper_blur boolean NOT NULL DEFAULT false;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS default_wallpaper_id text NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS default_wallpaper_custom_url text DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS default_wallpaper_dim real NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS default_wallpaper_blur boolean NOT NULL DEFAULT false;

-- Guard direct authenticated updates on dm_participants so only wallpaper_* columns can change
CREATE OR REPLACE FUNCTION public.guard_dm_participants_direct_update()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF current_user = 'authenticated' THEN
        IF NEW.conversation_id IS DISTINCT FROM OLD.conversation_id
           OR NEW.user_id IS DISTINCT FROM OLD.user_id
           OR NEW.role IS DISTINCT FROM OLD.role
           OR NEW.status IS DISTINCT FROM OLD.status
           OR NEW.is_muted IS DISTINCT FROM OLD.is_muted
           OR NEW.muted_until IS DISTINCT FROM OLD.muted_until
           OR NEW.is_pinned IS DISTINCT FROM OLD.is_pinned
           OR NEW.pinned_at IS DISTINCT FROM OLD.pinned_at
           OR NEW.last_read_at IS DISTINCT FROM OLD.last_read_at
           OR NEW.last_read_message_id IS DISTINCT FROM OLD.last_read_message_id
           OR NEW.joined_at IS DISTINCT FROM OLD.joined_at
           OR NEW.marked_unread IS DISTINCT FROM OLD.marked_unread
           OR NEW.cleared_before IS DISTINCT FROM OLD.cleared_before
           OR NEW.left_at IS DISTINCT FROM OLD.left_at
           OR NEW.last_activity_at IS DISTINCT FROM OLD.last_activity_at THEN
            RAISE EXCEPTION 'WALLPAPER_ONLY_DIRECT_UPDATE' USING errcode = '42501';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_dm_participants_direct_update ON public.dm_participants;
CREATE TRIGGER trg_guard_dm_participants_direct_update
    BEFORE UPDATE ON public.dm_participants
    FOR EACH ROW EXECUTE FUNCTION public.guard_dm_participants_direct_update();

DROP POLICY IF EXISTS "dm_participants_update_own_wallpaper" ON public.dm_participants;
CREATE POLICY "dm_participants_update_own_wallpaper" ON public.dm_participants
    FOR UPDATE TO authenticated
    USING (user_id = (SELECT auth.uid()) AND status != 'left')
    WITH CHECK (user_id = (SELECT auth.uid()) AND status != 'left');

CREATE OR REPLACE FUNCTION public.set_chat_wallpaper(
    p_conversation_id uuid DEFAULT NULL,
    p_wallpaper_id text DEFAULT 'default',
    p_custom_url text DEFAULT NULL,
    p_dim real DEFAULT 0.0,
    p_blur boolean DEFAULT false,
    p_apply_to_all boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_wp_id text := lower(trim(coalesce(p_wallpaper_id, 'default')));
    v_custom text := NULLIF(trim(coalesce(p_custom_url, '')), '');
    v_dim real := LEAST(GREATEST(COALESCE(p_dim, 0.0), 0.0), 0.70);
    v_blur boolean := COALESCE(p_blur, false);
    v_apply_all boolean := COALESCE(p_apply_to_all, false);
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF v_wp_id = '' THEN
        v_wp_id := 'default';
    END IF;

    IF v_wp_id = 'custom' THEN
        IF v_custom IS NULL THEN
            v_wp_id := 'default';
        END IF;
    ELSE
        v_custom := NULL;
    END IF;

    IF v_apply_all THEN
        UPDATE public.profiles
        SET
            default_wallpaper_id = v_wp_id,
            default_wallpaper_custom_url = v_custom,
            default_wallpaper_dim = v_dim,
            default_wallpaper_blur = v_blur,
            updated_at = now()
        WHERE id = v_caller;

        UPDATE public.dm_participants
        SET
            wallpaper_id = NULL,
            wallpaper_custom_url = NULL,
            wallpaper_dim = 0.0,
            wallpaper_blur = false
        WHERE user_id = v_caller;
    ELSE
        IF p_conversation_id IS NULL THEN
            RETURN jsonb_build_object('success', false, 'error', 'CONVERSATION_REQUIRED');
        END IF;

        IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
            RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
        END IF;

        UPDATE public.dm_participants
        SET
            wallpaper_id = v_wp_id,
            wallpaper_custom_url = v_custom,
            wallpaper_dim = v_dim,
            wallpaper_blur = v_blur
        WHERE conversation_id = p_conversation_id
          AND user_id = v_caller;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'wallpaper_id', v_wp_id,
        'wallpaper_custom_url', v_custom,
        'wallpaper_dim', v_dim,
        'wallpaper_blur', v_blur,
        'applied_to_all', v_apply_all
    );
END;
$$;

-- ---------------------------------------------------------------------------
-- 8. Enrich get_dm_conversation_detail(uuid) with resolved wallpaper fields
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_dm_conversation_detail(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_conv record;
    v_pinned_obj jsonb := NULL;
    v_participants jsonb;
    v_peer_intro jsonb := NULL;
    v_peer_id uuid;
    v_wp record;
    v_wp_id text := 'default';
    v_wp_custom text := NULL;
    v_wp_dim real := 0.0;
    v_wp_blur boolean := false;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    SELECT * INTO v_conv FROM public.dm_conversations WHERE id = p_conversation_id;
    IF v_conv.id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'CONVERSATION_NOT_FOUND');
    END IF;

    SELECT
        COALESCE(dp.wallpaper_id, v_conv.wallpaper_id, pr.default_wallpaper_id, 'default') AS resolved_wallpaper_id,
        CASE
            WHEN dp.wallpaper_id IS NOT NULL THEN dp.wallpaper_custom_url
            WHEN v_conv.wallpaper_id IS NOT NULL THEN v_conv.wallpaper_custom_url
            ELSE pr.default_wallpaper_custom_url
        END AS resolved_custom_url,
        CASE
            WHEN dp.wallpaper_id IS NOT NULL THEN dp.wallpaper_dim
            WHEN v_conv.wallpaper_id IS NOT NULL THEN COALESCE(v_conv.wallpaper_dim, 0.0)
            ELSE COALESCE(pr.default_wallpaper_dim, 0.0)
        END AS resolved_dim,
        CASE
            WHEN dp.wallpaper_id IS NOT NULL THEN dp.wallpaper_blur
            WHEN v_conv.wallpaper_id IS NOT NULL THEN COALESCE(v_conv.wallpaper_blur, false)
            ELSE COALESCE(pr.default_wallpaper_blur, false)
        END AS resolved_blur
    INTO v_wp
    FROM public.dm_participants dp
    JOIN public.profiles pr ON pr.id = dp.user_id
    WHERE dp.conversation_id = p_conversation_id
      AND dp.user_id = v_caller;

    IF FOUND THEN
        v_wp_id := COALESCE(v_wp.resolved_wallpaper_id, 'default');
        v_wp_custom := v_wp.resolved_custom_url;
        v_wp_dim := LEAST(GREATEST(COALESCE(v_wp.resolved_dim, 0.0), 0.0), 0.70);
        v_wp_blur := COALESCE(v_wp.resolved_blur, false);
    END IF;

    IF v_conv.pinned_message_id IS NOT NULL THEN
        SELECT jsonb_build_object(
            'id', pm.id,
            'sender_id', pm.sender_id,
            'sender_display_name', pr.display_name,
            'content', pm.content,
            'attachment_mime', pm.attachment_mime,
            'created_at', pm.created_at
        ) INTO v_pinned_obj
        FROM public.dm_messages pm
        JOIN public.profiles pr ON pr.id = pm.sender_id
        WHERE pm.id = v_conv.pinned_message_id AND pm.deleted_at IS NULL;
    END IF;

    SELECT coalesce(jsonb_agg(
        jsonb_build_object(
            'user_id', p.user_id,
            'username', pr.username,
            'handle', pr.handle,
            'display_name', pr.display_name,
            'avatar_url', pr.avatar_url,
            'is_verified', pr.is_verified,
            'role', p.role,
            'status', p.status,
            'is_muted', p.is_muted,
            'is_pinned', p.is_pinned,
            'last_read_at', p.last_read_at,
            'last_read_message_id', p.last_read_message_id
        )
    ), '[]'::jsonb) INTO v_participants
    FROM public.dm_participants p
    JOIN public.profiles pr ON pr.id = p.user_id
    WHERE p.conversation_id = p_conversation_id;

    IF v_conv.kind = 'direct' THEN
        v_peer_id := CASE WHEN v_conv.direct_user1_id = v_caller THEN v_conv.direct_user2_id ELSE v_conv.direct_user1_id END;

        SELECT jsonb_build_object(
            'id', pr.id,
            'username', pr.username,
            'handle', pr.handle,
            'display_name', pr.display_name,
            'avatar_url', pr.avatar_url,
            'is_verified', pr.is_verified,
            'bio', pr.bio,
            'followers_count', pr.followers_count,
            'following_count', pr.following_count,
            'posts_count', COALESCE((SELECT count(*)::int FROM public.attachments a WHERE a.user_id = pr.id), 0),
            'following_since', (SELECT created_at FROM public.follows WHERE follower_id = v_caller AND following_id = pr.id),
            'created_at', pr.created_at
        ) INTO v_peer_intro
        FROM public.profiles pr
        WHERE pr.id = v_peer_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'wallpaper_id', v_wp_id,
        'wallpaper_custom_url', v_wp_custom,
        'wallpaper_dim', v_wp_dim,
        'wallpaper_blur', v_wp_blur,
        'conversation', jsonb_build_object(
            'id', v_conv.id,
            'kind', v_conv.kind,
            'title', v_conv.title,
            'avatar_url', v_conv.avatar_url,
            'pinned_message_id', v_conv.pinned_message_id,
            'created_by', v_conv.created_by,
            'created_at', v_conv.created_at,
            'wallpaper_id', v_wp_id,
            'wallpaper_custom_url', v_wp_custom,
            'wallpaper_dim', v_wp_dim,
            'wallpaper_blur', v_wp_blur
        ),
        'pinned_message', v_pinned_obj,
        'participants', v_participants,
        'peer_intro', v_peer_intro
    );
END;
$$;

-- ---------------------------------------------------------------------------
-- 9. Strict privilege hardening + PostgREST schema/config cache reload
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.trg_note_like_notification() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.guard_dm_participants_direct_update() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.guard_dm_participants_direct_update() TO authenticated;

REVOKE ALL ON FUNCTION public._can_view_active_note(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public._can_view_active_note(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public._can_like_active_note(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public._can_like_active_note(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.toggle_note_like(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.toggle_note_like(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.get_followed_active_notes() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_followed_active_notes() TO authenticated;

REVOKE ALL ON FUNCTION public.get_my_active_note() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_active_note() TO authenticated;

REVOKE ALL ON FUNCTION public.get_user_notifications(integer, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_user_notifications(integer, integer) TO authenticated;

REVOKE ALL ON FUNCTION public.create_note(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_note(text) TO authenticated;

REVOKE ALL ON FUNCTION public.mark_dm_read(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_dm_read(uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.mark_conversation_read(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_conversation_read(uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.set_chat_wallpaper(uuid, text, text, real, boolean, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_chat_wallpaper(uuid, text, text, real, boolean, boolean) TO authenticated;

REVOKE ALL ON FUNCTION public.get_dm_conversation_detail(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_dm_conversation_detail(uuid) TO authenticated;

NOTIFY pgrst, 'reload schema';
NOTIFY pgrst, 'reload config';
