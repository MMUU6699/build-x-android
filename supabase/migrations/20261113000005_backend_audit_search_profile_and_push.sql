-- ============================================================================
-- Migration 20261113000005: Full Backend Audit, Fix get_public_profile &
-- search_people & get_account_info & change_username, Storage Policies, and
-- Push Notification Infrastructure
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

-- 1. Keep username and handle 100% synchronized across all existing rows
UPDATE public.profiles
SET handle = lower(username::text)
WHERE handle IS DISTINCT FROM lower(username::text);

-- 2. Ensure banner and wallpaper columns exist on public.profiles
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS banner_url text DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS banner_type text NOT NULL DEFAULT 'none',
    ADD COLUMN IF NOT EXISTS banner_updated_at timestamptz DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS default_wallpaper_id text NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS default_wallpaper_custom_url text DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS default_wallpaper_dim real NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS default_wallpaper_blur boolean NOT NULL DEFAULT false;

-- 3. Fix get_public_profile(target_username text)
-- Fixes the fatal `target_id` -> `author_id` bug on public.note_subscriptions,
-- includes `extensions` in search_path, and matches by username, handle, or UUID.
CREATE OR REPLACE FUNCTION public.get_public_profile(target_username text)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    clean text := lower(trim(leading '@' from btrim(coalesce(target_username, ''))));
    v_caller uuid := auth.uid();
    v_p record;
    v_following_cnt integer := 0;
    v_followers_cnt integer := 0;
    v_is_following boolean := false;
    v_follows_you boolean := false;
    v_note jsonb := NULL;
    v_subscribed boolean := false;
    v_blocked_by_me boolean := false;
BEGIN
    IF clean = '' THEN
        RETURN NULL;
    END IF;

    SELECT
        p.id,
        p.username::text AS username,
        p.display_name,
        p.avatar_url,
        p.bio,
        p.is_verified,
        p.verified_at,
        p.country_code,
        p.created_at,
        p.banner_url,
        coalesce(p.banner_type, 'none') AS banner_type
    INTO v_p
    FROM public.profiles p
    WHERE lower(p.username::text) = clean
       OR lower(p.handle) = clean
       OR p.id::text = clean
    LIMIT 1;

    IF v_p.id IS NULL THEN
        RETURN NULL;
    END IF;

    -- Mutual stealth block: if target blocked caller, return NULL
    IF v_caller IS NOT NULL AND v_caller <> v_p.id AND EXISTS (
        SELECT 1 FROM public.blocks b
        WHERE b.blocker_id = v_p.id AND b.blocked_id = v_caller
    ) THEN
        RETURN NULL;
    END IF;

    IF v_caller IS NOT NULL AND v_caller <> v_p.id THEN
        v_blocked_by_me := EXISTS (
            SELECT 1 FROM public.blocks b
            WHERE b.blocker_id = v_caller AND b.blocked_id = v_p.id
        );
    END IF;

    IF v_blocked_by_me THEN
        RETURN jsonb_build_object(
            'id', v_p.id,
            'username', v_p.username,
            'display_name', v_p.display_name,
            'avatar_url', v_p.avatar_url,
            'bio', NULL,
            'banner_url', NULL,
            'banner_type', 'none',
            'is_verified', v_p.is_verified,
            'verified_at', v_p.verified_at,
            'following_count', 0,
            'followers_count', 0,
            'is_following', false,
            'follows_you', false,
            'active_note', NULL,
            'note_subscribed', false,
            'country_code', v_p.country_code,
            'created_at', v_p.created_at,
            'blocked_by_me', true
        );
    END IF;

    SELECT count(*)::int INTO v_following_cnt FROM public.follows WHERE follower_id = v_p.id;
    SELECT count(*)::int INTO v_followers_cnt FROM public.follows WHERE following_id = v_p.id;

    IF v_caller IS NOT NULL AND v_caller <> v_p.id THEN
        v_is_following := EXISTS (
            SELECT 1 FROM public.follows WHERE follower_id = v_caller AND following_id = v_p.id
        );
        v_follows_you := EXISTS (
            SELECT 1 FROM public.follows WHERE follower_id = v_p.id AND following_id = v_caller
        );
        -- FIXED: column on public.note_subscriptions is author_id (NOT target_id)
        v_subscribed := EXISTS (
            SELECT 1 FROM public.note_subscriptions
            WHERE subscriber_id = v_caller AND author_id = v_p.id
        );
    END IF;

    SELECT jsonb_build_object(
        'id', n.id,
        'user_id', n.user_id,
        'content', n.content,
        'created_at', n.created_at,
        'expires_at', n.expires_at,
        'is_active', true
    ) INTO v_note
    FROM public.notes n
    WHERE n.user_id = v_p.id
      AND n.is_active = true
      AND n.expires_at > now()
    ORDER BY n.created_at DESC
    LIMIT 1;

    RETURN jsonb_build_object(
        'id', v_p.id,
        'username', v_p.username,
        'display_name', v_p.display_name,
        'avatar_url', v_p.avatar_url,
        'bio', v_p.bio,
        'banner_url', v_p.banner_url,
        'banner_type', v_p.banner_type,
        'is_verified', v_p.is_verified,
        'verified_at', v_p.verified_at,
        'following_count', v_following_cnt,
        'followers_count', v_followers_cnt,
        'is_following', v_is_following,
        'follows_you', v_follows_you,
        'active_note', v_note,
        'note_subscribed', v_subscribed,
        'country_code', v_p.country_code,
        'created_at', v_p.created_at,
        'blocked_by_me', false
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_public_profile(text) TO anon, authenticated;

-- 4. Fix search_people(query text, p_limit int, p_offset int)
-- Includes `extensions` in search_path so pg_trgm similarity() never throws 42883,
-- strips a leading '@', and searches username, handle, and Arabic-normalized
-- display_name. Anonymous callers are supported so public search never fails.
CREATE OR REPLACE FUNCTION public.search_people(
    query text,
    p_limit int DEFAULT 20,
    p_offset int DEFAULT 0
)
RETURNS TABLE (
    id uuid,
    username text,
    display_name text,
    avatar_url text,
    is_verified boolean,
    followers_count integer,
    is_following boolean,
    follows_you boolean
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_norm text;
    v_limit int;
    v_offset int;
BEGIN
    v_norm := lower(trim(leading '@' from btrim(coalesce(query, ''))));
    v_norm := translate(v_norm, 'أإآىة', 'ااايه');
    v_limit := least(greatest(coalesce(p_limit, 20), 1), 50);
    v_offset := greatest(coalesce(p_offset, 0), 0);

    RETURN QUERY
    SELECT
        p.id,
        p.username::text AS username,
        p.display_name,
        p.avatar_url,
        p.is_verified,
        (SELECT count(*)::int FROM public.follows fl WHERE fl.following_id = p.id) AS followers_count,
        CASE WHEN v_caller IS NOT NULL THEN
            EXISTS (SELECT 1 FROM public.follows f WHERE f.follower_id = v_caller AND f.following_id = p.id)
        ELSE false END AS is_following,
        CASE WHEN v_caller IS NOT NULL THEN
            EXISTS (SELECT 1 FROM public.follows f WHERE f.follower_id = p.id AND f.following_id = v_caller)
        ELSE false END AS follows_you
    FROM public.profiles p
    WHERE (v_caller IS NULL OR p.id <> v_caller)
      AND (v_caller IS NULL OR NOT public._are_blocked(v_caller, p.id))
      AND (
          v_norm = ''
          OR lower(p.username::text) ILIKE ('%' || v_norm || '%')
          OR lower(coalesce(p.handle, '')) ILIKE ('%' || v_norm || '%')
          OR translate(lower(p.display_name), 'أإآىة', 'ااايه') ILIKE ('%' || v_norm || '%')
          OR similarity(lower(p.username::text), v_norm) > 0.22
          OR similarity(translate(lower(p.display_name), 'أإآىة', 'ااايه'), v_norm) > 0.22
      )
    ORDER BY
        (lower(p.username::text) = v_norm) DESC,
        (lower(p.username::text) ILIKE (v_norm || '%')) DESC,
        similarity(lower(p.username::text), v_norm) DESC,
        p.is_verified DESC,
        followers_count DESC
    LIMIT v_limit OFFSET v_offset;
END;
$$;

GRANT EXECUTE ON FUNCTION public.search_people(text, int, int) TO anon, authenticated;

-- 5. Fix change_username so both username AND handle are updated and the audit
-- log is recorded.
CREATE OR REPLACE FUNCTION public.change_username(new_username text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_old text;
    v_clean text;
    v_changed_at timestamptz;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;
    v_clean := lower(trim(leading '@' from btrim(coalesce(new_username, ''))));
    IF v_clean !~ '^[a-z0-9._]{3,24}$' OR v_clean ~ '^\.|\.$|\.\.' THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_FORMAT');
    END IF;
    IF EXISTS (SELECT 1 FROM public.reserved_usernames r WHERE lower(r.username::text) = v_clean) THEN
        RETURN jsonb_build_object('success', false, 'error', 'RESERVED');
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext('buildx:user:' || v_user_id::text));
    SELECT username::text, username_changed_at INTO v_old, v_changed_at
    FROM public.profiles WHERE id = v_user_id;

    IF v_clean = lower(coalesce(v_old, '')) THEN
        RETURN jsonb_build_object('success', true, 'username', v_clean, 'new_username', v_clean);
    END IF;
    IF v_changed_at IS NOT NULL AND (v_changed_at + interval '14 days') > now() THEN
        RETURN jsonb_build_object('success', false, 'error', 'COOLDOWN_ACTIVE',
                                  'available_at', (v_changed_at + interval '14 days'));
    END IF;
    IF EXISTS (SELECT 1 FROM public.profiles p WHERE lower(p.username::text) = v_clean AND p.id <> v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'TAKEN');
    END IF;

    BEGIN
        UPDATE public.profiles
        SET username = v_clean::citext,
            handle = v_clean,
            username_changed_at = now(),
            username_reviewed_at = now(),
            updated_at = now()
        WHERE id = v_user_id;

        INSERT INTO public.profile_audit_log (user_id, field_changed, old_value, new_value, changed_by)
        VALUES (v_user_id, 'username', v_old, v_clean, v_user_id);
    EXCEPTION WHEN unique_violation THEN
        RETURN jsonb_build_object('success', false, 'error', 'TAKEN');
    END;

    RETURN jsonb_build_object('success', true, 'username', v_clean, 'new_username', v_clean);
END;
$$;

GRANT EXECUTE ON FUNCTION public.change_username(text) TO authenticated;

-- 6. Upgrade get_account_info(target_user_id uuid) to return full profile
-- metadata plus the former_usernames list used by the About page.
CREATE OR REPLACE FUNCTION public.get_account_info(target_user_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_p record;
    v_changes int := 0;
    v_former_usernames jsonb := '[]'::jsonb;
    v_follows_you boolean := false;
    v_following_since timestamptz := null;
BEGIN
    IF target_user_id IS NULL THEN
        RETURN NULL;
    END IF;
    IF v_caller IS NOT NULL AND public._are_blocked(v_caller, target_user_id) THEN
        RETURN NULL;
    END IF;

    SELECT id, username::text AS username, display_name, avatar_url, bio, created_at,
           country_code, is_verified, verified_at
    INTO v_p
    FROM public.profiles
    WHERE id = target_user_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT count(*)::int INTO v_changes
    FROM public.profile_audit_log l
    WHERE l.user_id = target_user_id AND l.field_changed = 'username';

    SELECT coalesce(jsonb_agg(sub.old_value ORDER BY sub.created_at DESC), '[]'::jsonb)
    INTO v_former_usernames
    FROM (
        SELECT l.old_value, l.created_at
        FROM public.profile_audit_log l
        WHERE l.user_id = target_user_id
          AND l.field_changed = 'username'
          AND l.old_value IS NOT NULL
          AND btrim(l.old_value) <> ''
        ORDER BY l.created_at DESC
        LIMIT 20
    ) sub;

    IF v_caller IS NOT NULL AND v_caller <> target_user_id THEN
        SELECT EXISTS (
            SELECT 1 FROM public.follows f
            WHERE f.follower_id = target_user_id AND f.following_id = v_caller
        ) INTO v_follows_you;

        SELECT f.created_at INTO v_following_since
        FROM public.follows f
        WHERE f.follower_id = v_caller AND f.following_id = target_user_id;
    END IF;

    RETURN jsonb_build_object(
        'user_id', v_p.id,
        'username', v_p.username,
        'display_name', v_p.display_name,
        'avatar_url', v_p.avatar_url,
        'bio', v_p.bio,
        'joined_at', v_p.created_at,
        'country_code', v_p.country_code,
        'is_verified', v_p.is_verified,
        'verified_at', CASE WHEN v_p.is_verified THEN v_p.verified_at ELSE NULL END,
        'username_change_count', coalesce(v_changes, 0),
        'former_usernames', coalesce(v_former_usernames, '[]'::jsonb),
        'follows_you', v_follows_you,
        'following_since', v_following_since,
        'is_self', (v_caller IS NOT NULL AND v_caller = target_user_id)
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_account_info(uuid) TO authenticated, anon;

-- 7. Ensure Storage Buckets (avatars, attachments) exist and stay public-read.
-- Object-level RLS policies already restrict writes to the owner's folder.
INSERT INTO storage.buckets (id, name, public)
VALUES ('avatars', 'avatars', true), ('attachments', 'attachments', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- 8. Notifications: support follow, note, note_like, dm and message_request.
-- Existing triggers insert follow/note/note_like rows; dm rows are inserted by
-- the client notification bridge and message_request is kept for compatibility.
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('follow', 'note', 'note_like', 'dm', 'message_request'));

ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS title text,
    ADD COLUMN IF NOT EXISTS body text,
    ADD COLUMN IF NOT EXISTS conversation_id uuid;

ALTER TABLE public.notifications REPLICA IDENTITY FULL;

-- 9. Device push tokens for Firebase Cloud Messaging (FCM).
CREATE TABLE IF NOT EXISTS public.user_push_tokens (
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    fcm_token text NOT NULL,
    device_info text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, fcm_token)
);

ALTER TABLE public.user_push_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_push_tokens_own" ON public.user_push_tokens;
CREATE POLICY "user_push_tokens_own"
    ON public.user_push_tokens FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_push_tokens TO authenticated;

CREATE OR REPLACE FUNCTION public.register_push_token(
    p_fcm_token text,
    p_device_info text DEFAULT 'android'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    IF v_uid IS NULL OR coalesce(btrim(p_fcm_token), '') = '' THEN
        RETURN jsonb_build_object('success', false);
    END IF;

    INSERT INTO public.user_push_tokens (user_id, fcm_token, device_info, updated_at)
    VALUES (v_uid, btrim(p_fcm_token), p_device_info, now())
    ON CONFLICT (user_id, fcm_token)
    DO UPDATE SET updated_at = now(), device_info = EXCLUDED.device_info;

    RETURN jsonb_build_object('success', true);
END;
$$;

GRANT EXECUTE ON FUNCTION public.register_push_token(text, text) TO authenticated;

-- 10. Ensure public.notifications is in the supabase_realtime publication so
-- the Android client can raise phone notifications as rows arrive.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'notifications'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
    END IF;
END $$;

NOTIFY pgrst, 'reload schema';
NOTIFY pgrst, 'reload config';
