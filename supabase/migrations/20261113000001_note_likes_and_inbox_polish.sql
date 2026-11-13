-- ============================================================================
-- Migration: Note Likes, Note Interaction Polish, Notification Support,
--            mark_dm_read Sync & Strict Function Grant Hardening
-- ============================================================================

-- 1. public.note_likes table + indexes + deny-all direct RLS (RPC-only)
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

DROP POLICY IF EXISTS "note_likes_no_direct_access" ON public.note_likes;
CREATE POLICY "note_likes_no_direct_access" ON public.note_likes
    FOR ALL
    USING (false)
    WITH CHECK (false);

REVOKE ALL ON TABLE public.note_likes FROM PUBLIC, anon, authenticated;

-- 2. Extend public.notifications to support 'note_like' + trigger
ALTER TABLE public.notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('follow', 'note', 'note_like'));

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

-- 3. Update get_user_notifications so 'note_like' also resolves liked note snippet
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

-- 4. toggle_note_like(p_note_id uuid) -> { success, liked, likes_count }
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

    SELECT id, user_id, is_active, expires_at
    INTO v_note
    FROM public.notes
    WHERE id = p_note_id;

    IF v_note.id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOTE_NOT_FOUND');
    END IF;

    IF NOT v_note.is_active OR v_note.expires_at <= now() THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOTE_EXPIRED');
    END IF;

    IF public._are_blocked(v_caller, v_note.user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'BLOCKED');
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext('buildx:note_like:' || p_note_id::text || ':' || v_caller::text));

    IF EXISTS (
        SELECT 1 FROM public.note_likes
        WHERE note_id = p_note_id AND user_id = v_caller
    ) THEN
        DELETE FROM public.note_likes
        WHERE note_id = p_note_id AND user_id = v_caller;
        v_liked := false;
    ELSE
        INSERT INTO public.note_likes (note_id, user_id)
        VALUES (p_note_id, v_caller)
        ON CONFLICT DO NOTHING;
        v_liked := true;
    END IF;

    SELECT count(*)::int INTO v_likes_count
    FROM public.note_likes
    WHERE note_id = p_note_id;

    RETURN jsonb_build_object(
        'success', true,
        'liked', v_liked,
        'likes_count', v_likes_count
    );
END;
$$;

-- 5. Upgrade get_followed_active_notes() with likes_count & is_liked_by_me
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

-- 6. Upgrade get_my_active_note() with likes_count, is_liked_by_me, and top 20 likers
CREATE OR REPLACE FUNCTION public.get_my_active_note()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT jsonb_build_object(
        'id', n.id,
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

-- 7. Keep create_note payload aligned with PublicNote likes fields
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

-- 8. Fix mark_dm_read to also clear manual marked_unread flag
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

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 9. Strict privilege hardening: revoke PUBLIC & anon, grant authenticated only on RPCs
REVOKE ALL ON FUNCTION public.trg_note_like_notification() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_dm_messages_sync_last_message() FROM PUBLIC, anon, authenticated;

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

REVOKE ALL ON FUNCTION public.delete_conversation_for_me(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_conversation_for_me(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.get_dm_inbox_deprecated_v2(text, timestamptz, timestamptz, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_dm_inbox_deprecated_v2(text, timestamptz, timestamptz, integer) TO authenticated;

REVOKE ALL ON FUNCTION public.leave_group(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.leave_group(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.mark_conversation_read(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_conversation_read(uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.mark_conversation_unread(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_conversation_unread(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.set_conversation_mute(uuid, timestamptz) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_conversation_mute(uuid, timestamptz) TO authenticated;

REVOKE ALL ON FUNCTION public.set_conversation_pin(uuid, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_conversation_pin(uuid, boolean) TO authenticated;
