-- Migration 20261111000001_backend_repairs_and_hardening.sql
-- Phase 2 Backend & Supabase Database Hardening & Defect Resolution

-- =====================================================================
-- 0. BACKUP TABLES BEFORE MODIFICATION (Global Rule 6)
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS backup_20261111;

CREATE TABLE IF NOT EXISTS backup_20261111.notes AS SELECT * FROM public.notes;
CREATE TABLE IF NOT EXISTS backup_20261111.notifications AS SELECT * FROM public.notifications;
CREATE TABLE IF NOT EXISTS backup_20261111.profile_audit_log AS SELECT * FROM public.profile_audit_log;
CREATE TABLE IF NOT EXISTS backup_20261111.verification_events AS SELECT * FROM public.verification_events;
CREATE TABLE IF NOT EXISTS backup_20261111.profiles AS SELECT * FROM public.profiles;

-- =====================================================================
-- 1. NOTES: is_active COLUMN & ACTIVE NOTE INDEXES (Deliverable 1)
-- =====================================================================
ALTER TABLE public.notes ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_notes_user_active_unique 
    ON public.notes (user_id) WHERE (is_active = true);

CREATE INDEX IF NOT EXISTS idx_notes_user_active_expires 
    ON public.notes (user_id, is_active, expires_at DESC);

-- =====================================================================
-- 2. FOREIGN KEY INDEXES (Deliverable 2 - Performance Advisors)
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_notifications_actor_id 
    ON public.notifications (actor_id);

CREATE INDEX IF NOT EXISTS idx_profile_audit_log_changed_by 
    ON public.profile_audit_log (changed_by);

CREATE INDEX IF NOT EXISTS idx_verification_events_user_id 
    ON public.verification_events (user_id);

-- =====================================================================
-- 3. SCHEMA DRIFT RESOLUTION (Deliverable 3 - follow_rate_events & note_subscriptions)
-- =====================================================================
CREATE TABLE IF NOT EXISTS public.follow_rate_events (
    id bigserial PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    action text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_follow_rate_events_user_created 
    ON public.follow_rate_events (user_id, created_at DESC);

ALTER TABLE public.follow_rate_events ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS public.note_subscriptions (
    subscriber_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (subscriber_id, author_id)
);

CREATE INDEX IF NOT EXISTS idx_note_subscriptions_author 
    ON public.note_subscriptions (author_id);

ALTER TABLE public.note_subscriptions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS note_subscriptions_select_own ON public.note_subscriptions;
CREATE POLICY note_subscriptions_select_own ON public.note_subscriptions
    FOR SELECT TO authenticated
    USING (auth.uid() = subscriber_id);

-- =====================================================================
-- 4. PROFILES COLUMN GRANTS & DEDICATED RPCS (Deliverable 4 - Prevent 42501)
-- =====================================================================
-- Grant clean column update privileges to authenticated users on their editable fields
GRANT UPDATE (display_name, bio, avatar_url, updated_at, theme_mode, theme_palette, locale) 
    ON public.profiles TO authenticated;

-- Dedicated security-definer RPC for updating avatar URL
CREATE OR REPLACE FUNCTION public.update_profile_avatar(p_avatar_url text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    UPDATE public.profiles
    SET avatar_url = p_avatar_url,
        updated_at = now()
    WHERE id = v_uid;

    RETURN jsonb_build_object('success', true, 'avatar_url', p_avatar_url);
END;
$$;
GRANT EXECUTE ON FUNCTION public.update_profile_avatar(text) TO authenticated;

-- Dedicated security-definer RPC for updating profile details
CREATE OR REPLACE FUNCTION public.update_profile_details(
    p_display_name text,
    p_bio text DEFAULT NULL,
    p_avatar_url text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_prof record;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    UPDATE public.profiles
    SET display_name = COALESCE(NULLIF(trim(p_display_name), ''), display_name),
        bio = p_bio,
        avatar_url = COALESCE(p_avatar_url, avatar_url),
        updated_at = now()
    WHERE id = v_uid
    RETURNING * INTO v_prof;

    RETURN to_jsonb(v_prof);
END;
$$;
GRANT EXECUTE ON FUNCTION public.update_profile_details(text, text, text) TO authenticated;

-- =====================================================================
-- 5. RPC ALIGNMENT FOR PublicNote & PublicProfile (audit-arch [P2] fix)
-- =====================================================================
CREATE OR REPLACE FUNCTION public.get_my_active_note()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_note record;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT id, user_id, content, created_at, expires_at, is_active
    INTO v_note
    FROM public.notes
    WHERE user_id = v_user_id
      AND is_active = true
      AND expires_at > now()
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_note.id IS NULL THEN
        RETURN NULL;
    END IF;

    RETURN jsonb_build_object(
        'id', v_note.id,
        'user_id', v_note.user_id,
        'author_id', v_note.user_id,
        'content', v_note.content,
        'created_at', v_note.created_at,
        'expires_at', v_note.expires_at,
        'is_active', v_note.is_active
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.get_my_active_note() TO authenticated;

CREATE OR REPLACE FUNCTION public.create_note(note_content text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_clean text;
    v_note_id uuid;
    v_created_at timestamptz := now();
    v_expires_at timestamptz;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    v_clean := trim(note_content);
    IF char_length(v_clean) < 1 OR char_length(v_clean) > 60 THEN
        RAISE EXCEPTION 'Note length must be between 1 and 60 characters';
    END IF;

    UPDATE public.notes
    SET is_active = false
    WHERE user_id = v_user_id AND is_active = true;

    v_expires_at := v_created_at + interval '24 hours';
    INSERT INTO public.notes (user_id, content, created_at, expires_at, is_active)
    VALUES (v_user_id, v_clean, v_created_at, v_expires_at, true)
    RETURNING id INTO v_note_id;

    RETURN jsonb_build_object(
        'id', v_note_id,
        'user_id', v_user_id,
        'author_id', v_user_id,
        'content', v_clean,
        'created_at', v_created_at,
        'expires_at', v_expires_at,
        'is_active', true
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.create_note(text) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_public_profile(target_username text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_caller_id uuid := auth.uid();
    v_profile record;
    v_active_note jsonb := null;
    v_is_following boolean := false;
    v_follows_you boolean := false;
    v_note_subscribed boolean := false;
BEGIN
    SELECT p.id, p.username::text as username, p.display_name, p.bio, p.avatar_url,
           p.is_verified, p.following_count, p.followers_count
    INTO v_profile
    FROM public.profiles p
    WHERE p.username = lower(trim(target_username))::citext;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT jsonb_build_object(
        'id', id,
        'user_id', user_id,
        'content', content,
        'created_at', created_at,
        'expires_at', expires_at,
        'is_active', is_active
    )
    INTO v_active_note
    FROM public.notes
    WHERE user_id = v_profile.id AND is_active = true AND expires_at > now()
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_caller_id IS NOT NULL AND v_caller_id <> v_profile.id THEN
        SELECT EXISTS (
            SELECT 1 FROM public.follows
            WHERE follower_id = v_caller_id AND following_id = v_profile.id
        ) INTO v_is_following;
        SELECT EXISTS (
            SELECT 1 FROM public.follows
            WHERE follower_id = v_profile.id AND following_id = v_caller_id
        ) INTO v_follows_you;
        SELECT EXISTS (
            SELECT 1 FROM public.note_subscriptions
            WHERE subscriber_id = v_caller_id AND author_id = v_profile.id
        ) INTO v_note_subscribed;
    END IF;

    RETURN jsonb_build_object(
        'id', v_profile.id,
        'username', v_profile.username,
        'display_name', v_profile.display_name,
        'bio', v_profile.bio,
        'avatar_url', v_profile.avatar_url,
        'is_verified', v_profile.is_verified,
        'following_count', v_profile.following_count,
        'followers_count', v_profile.followers_count,
        'active_note', v_active_note,
        'is_following', v_is_following,
        'follows_you', v_follows_you,
        'note_subscribed', v_note_subscribed
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.get_public_profile(text) TO authenticated, anon;

-- =====================================================================
-- 6. STORAGE BUCKETS & OBJECT POLICIES (Deliverable 5)
-- =====================================================================
-- Ensure avatars bucket is public and properly configured
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('avatars', 'avatars', true, 5242880, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE
SET public = true,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

-- Ensure attachments bucket is public and properly configured
INSERT INTO storage.buckets (id, name, public, file_size_limit)
VALUES ('attachments', 'attachments', true, 20971520)
ON CONFLICT (id) DO UPDATE
SET public = true,
    file_size_limit = 20971520;

-- Avatars RLS policies on storage.objects
DROP POLICY IF EXISTS storage_avatars_select_public ON storage.objects;
DROP POLICY IF EXISTS avatars_public_read ON storage.objects;
CREATE POLICY avatars_public_read ON storage.objects
    FOR SELECT TO public
    USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS storage_avatars_insert_own ON storage.objects;
DROP POLICY IF EXISTS avatars_insert_own ON storage.objects;
CREATE POLICY avatars_insert_own ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS storage_avatars_update_own ON storage.objects;
DROP POLICY IF EXISTS avatars_update_own ON storage.objects;
CREATE POLICY avatars_update_own ON storage.objects
    FOR UPDATE TO authenticated
    USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text)
    WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS storage_avatars_delete_own ON storage.objects;
DROP POLICY IF EXISTS avatars_delete_own ON storage.objects;
CREATE POLICY avatars_delete_own ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

-- Attachments RLS policies on storage.objects
DROP POLICY IF EXISTS storage_attachments_select_public ON storage.objects;
DROP POLICY IF EXISTS storage_attachments_select_own ON storage.objects;
CREATE POLICY storage_attachments_select_public ON storage.objects
    FOR SELECT TO public
    USING (bucket_id = 'attachments');

DROP POLICY IF EXISTS storage_attachments_insert_own ON storage.objects;
CREATE POLICY storage_attachments_insert_own ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'attachments' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS storage_attachments_update_own ON storage.objects;
CREATE POLICY storage_attachments_update_own ON storage.objects
    FOR UPDATE TO authenticated
    USING (bucket_id = 'attachments' AND (storage.foldername(name))[1] = auth.uid()::text)
    WITH CHECK (bucket_id = 'attachments' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS storage_attachments_delete_own ON storage.objects;
CREATE POLICY storage_attachments_delete_own ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id = 'attachments' AND (storage.foldername(name))[1] = auth.uid()::text);
