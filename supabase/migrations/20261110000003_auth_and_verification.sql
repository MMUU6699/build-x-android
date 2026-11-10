-- Migration 20261110000003_auth_and_verification.sql
-- 1. Support login by username or email
-- 2. Enhanced verification requests with category, full_name, and evidence links

CREATE OR REPLACE FUNCTION public.get_login_email(p_identifier text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_clean text;
    v_email text;
BEGIN
    v_clean := lower(trim(p_identifier));
    IF v_clean = '' THEN
        RETURN NULL;
    END IF;

    -- If identifier already looks like an email, return it directly
    IF v_clean LIKE '%@%' AND v_clean LIKE '%.%' THEN
        RETURN v_clean;
    END IF;

    -- Otherwise treat as username (strip optional leading '@')
    v_clean := ltrim(v_clean, '@');

    SELECT u.email INTO v_email
    FROM auth.users u
    JOIN public.profiles p ON p.id = u.id
    WHERE lower(p.username::text) = v_clean
    LIMIT 1;

    RETURN v_email;
END;
$$;

REVOKE ALL ON FUNCTION public.get_login_email(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_login_email(text) TO anon, authenticated, service_role;

ALTER TABLE public.verification_requests 
ADD COLUMN IF NOT EXISTS category text,
ADD COLUMN IF NOT EXISTS full_name text,
ADD COLUMN IF NOT EXISTS links text;

CREATE OR REPLACE FUNCTION public.request_verification(
    reason text,
    full_name text DEFAULT NULL,
    category text DEFAULT NULL,
    links text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_reason text;
    v_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF EXISTS (SELECT 1 FROM public.profiles WHERE id = v_user_id AND is_verified = true) THEN
        RETURN jsonb_build_object('success', false, 'error', 'ALREADY_VERIFIED');
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.verification_requests
        WHERE user_id = v_user_id AND status = 'pending'
    ) THEN
        RETURN jsonb_build_object('success', false, 'error', 'PENDING');
    END IF;

    v_reason := trim(reason);
    IF char_length(v_reason) < 10 OR char_length(v_reason) > 500 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_REASON');
    END IF;

    INSERT INTO public.verification_requests (user_id, reason, full_name, category, links)
    VALUES (v_user_id, v_reason, nullif(trim(full_name), ''), nullif(trim(category), ''), nullif(trim(links), ''))
    RETURNING id INTO v_id;

    RETURN jsonb_build_object('success', true, 'id', v_id, 'status', 'pending');
END;
$$;

REVOKE ALL ON FUNCTION public.request_verification(text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.request_verification(text, text, text, text) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_my_verification_status()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_is_verified boolean := false;
    v_verified_at timestamptz;
    v_req record;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT is_verified, verified_at INTO v_is_verified, v_verified_at
    FROM public.profiles
    WHERE id = v_user_id;

    SELECT id, status, reason, full_name, category, links, created_at, decision_reason, reviewed_at
    INTO v_req
    FROM public.verification_requests
    WHERE user_id = v_user_id
    ORDER BY created_at DESC
    LIMIT 1;

    RETURN jsonb_build_object(
        'is_verified', coalesce(v_is_verified, false),
        'verified_at', v_verified_at,
        'has_request', (v_req.id IS NOT NULL),
        'request_id', v_req.id,
        'status', v_req.status,
        'reason', v_req.reason,
        'full_name', v_req.full_name,
        'category', v_req.category,
        'links', v_req.links,
        'created_at', v_req.created_at,
        'decision_reason', v_req.decision_reason,
        'reviewed_at', v_req.reviewed_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.get_my_verification_status() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_my_verification_status() TO authenticated, service_role;
