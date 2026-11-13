-- ============================================================================
-- Migration 20261113000004: Profile Atmospheric Banner Support
-- Target Project: owgpuolvfqfjhezuohyf
-- Adds banner_url, banner_type ('none' | 'image' | 'video'), banner_updated_at
-- on public.profiles, adds set_profile_banner RPC, and updates get_public_profile
-- (preserving live public.blocks, public.follows, public.notes, public.note_subscriptions).
-- ============================================================================

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS banner_url TEXT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS banner_type TEXT NOT NULL DEFAULT 'none'
        CHECK (banner_type IN ('none', 'image', 'video')),
    ADD COLUMN IF NOT EXISTS banner_updated_at TIMESTAMPTZ DEFAULT NULL;

-- Backfill safety for any pre-existing null banner_type rows
UPDATE public.profiles
SET banner_type = 'none'
WHERE banner_type IS NULL;

-- ============================================================================
-- RPC: set_profile_banner(p_banner_url TEXT, p_banner_type TEXT)
-- Allows the authenticated user to set or remove their own profile banner.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.set_profile_banner(
    p_banner_url TEXT DEFAULT NULL,
    p_banner_type TEXT DEFAULT 'none'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_clean_type TEXT := lower(coalesce(nullif(trim(p_banner_type), ''), 'none'));
    v_clean_url TEXT := nullif(trim(coalesce(p_banner_url, '')), '');
    v_updated_at TIMESTAMPTZ := now();
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF v_clean_type NOT IN ('none', 'image', 'video') THEN
        RAISE EXCEPTION 'Invalid banner type: %', v_clean_type;
    END IF;

    IF v_clean_url IS NULL OR v_clean_type = 'none' THEN
        v_clean_url := NULL;
        v_clean_type := 'none';
    END IF;

    UPDATE public.profiles
    SET
        banner_url = v_clean_url,
        banner_type = v_clean_type,
        banner_updated_at = v_updated_at,
        updated_at = v_updated_at
    WHERE id = v_uid;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Profile not found for user %', v_uid;
    END IF;

    RETURN jsonb_build_object(
        'banner_url', v_clean_url,
        'banner_type', v_clean_type,
        'banner_updated_at', v_updated_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.set_profile_banner(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_profile_banner(TEXT, TEXT) TO authenticated;

-- ============================================================================
-- RPC: get_public_profile(target_username text)
-- Preserves live schema (public.blocks, public.follows, public.notes,
-- public.note_subscriptions) while returning banner_url & banner_type.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_public_profile(target_username text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    clean TEXT := lower(trim(leading '@' from trim(target_username)));
    v_caller UUID := auth.uid();
    v_p RECORD;
    v_following_cnt INTEGER;
    v_followers_cnt INTEGER;
    v_is_following BOOLEAN := false;
    v_follows_you BOOLEAN := false;
    v_note JSONB := NULL;
    v_subscribed BOOLEAN := false;
    v_blocked_by_me BOOLEAN := false;
BEGIN
    SELECT
        p.id,
        p.username,
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
    WHERE lower(p.username) = clean
       OR lower(p.handle) = clean
    LIMIT 1;

    IF v_p.id IS NULL THEN
        RETURN NULL;
    END IF;

    -- Mutual stealth block: if target blocked caller, pretend account does not exist
    IF v_caller IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.blocks b
        WHERE b.blocker_id = v_p.id AND b.blocked_id = v_caller
    ) THEN
        RETURN NULL;
    END IF;

    IF v_caller IS NOT NULL THEN
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
            'country_code', NULL,
            'created_at', v_p.created_at,
            'blocked_by_me', true
        );
    END IF;

    SELECT count(*) INTO v_following_cnt FROM public.follows WHERE follower_id = v_p.id;
    SELECT count(*) INTO v_followers_cnt FROM public.follows WHERE following_id = v_p.id;

    IF v_caller IS NOT NULL THEN
        v_is_following := EXISTS (
            SELECT 1 FROM public.follows WHERE follower_id = v_caller AND following_id = v_p.id
        );
        v_follows_you := EXISTS (
            SELECT 1 FROM public.follows WHERE follower_id = v_p.id AND following_id = v_caller
        );
        v_subscribed := EXISTS (
            SELECT 1 FROM public.note_subscriptions WHERE subscriber_id = v_caller AND target_id = v_p.id
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
    WHERE n.user_id = v_p.id AND n.expires_at > now()
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

NOTIFY pgrst, 'reload schema';
