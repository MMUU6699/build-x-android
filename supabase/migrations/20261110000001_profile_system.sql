-- =====================================================================
-- Build X — Profile System Migration
-- Migration ID: 20261110000001_profile_system.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- SECTION A: EXTENSIONS
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- ---------------------------------------------------------------------
-- SECTION B: ADDITIVE COLUMNS ON public.profiles
-- (Existing columns handle, theme_mode, theme_palette, locale stay untouched)
-- ---------------------------------------------------------------------
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS username citext;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS bio text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_verified boolean NOT NULL DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS verified_at timestamptz;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS following_count integer NOT NULL DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS followers_count integer NOT NULL DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS username_changed_at timestamptz;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS username_reviewed_at timestamptz;

-- Bio constraint: max 160 chars and max 4 newline breaks
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'check_profiles_bio_length') THEN
        ALTER TABLE public.profiles ADD CONSTRAINT check_profiles_bio_length
            CHECK (bio IS NULL OR (char_length(bio) <= 160 AND (char_length(bio) - char_length(replace(bio, E'\n', ''))) <= 4));
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- SECTION C: RESERVED USERNAMES & AUDIT LOG TABLES
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.reserved_usernames (
    username citext PRIMARY KEY,
    reason text NOT NULL DEFAULT 'system_reserved',
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.reserved_usernames ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "reserved_usernames_select_authenticated" ON public.reserved_usernames;
CREATE POLICY "reserved_usernames_select_authenticated" ON public.reserved_usernames
    FOR SELECT TO authenticated USING (true);

-- Seed system reserved handles
INSERT INTO public.reserved_usernames (username, reason) VALUES
    ('admin', 'system'),
    ('administrator', 'system'),
    ('root', 'system'),
    ('buildx', 'brand'),
    ('build_x', 'brand'),
    ('official', 'brand'),
    ('support', 'system'),
    ('help', 'system'),
    ('system', 'system'),
    ('security', 'system'),
    ('moderator', 'system'),
    ('mod', 'system'),
    ('staff', 'system'),
    ('verified', 'system'),
    ('explore', 'system'),
    ('notifications', 'system'),
    ('settings', 'system'),
    ('profile', 'system'),
    ('search', 'system'),
    ('chat', 'system')
ON CONFLICT (username) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.profile_audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    field_changed text NOT NULL,
    old_value text,
    new_value text,
    changed_by uuid REFERENCES auth.users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.profile_audit_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "audit_log_select_own" ON public.profile_audit_log;
CREATE POLICY "audit_log_select_own" ON public.profile_audit_log
    FOR SELECT USING (auth.uid() = user_id);

CREATE TABLE IF NOT EXISTS public.verification_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    action text NOT NULL CHECK (action IN ('granted', 'revoked')),
    reason text,
    granted_by text NOT NULL DEFAULT 'service_role',
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.verification_events ENABLE ROW LEVEL SECURITY;
-- service_role only (no public RLS policy, inaccessible to client roles)

-- ---------------------------------------------------------------------
-- SECTION D: NORMALIZING EXISTING 27 ROWS INTO username
-- Disambiguates duplicate test handles without deleting any row.
-- Manual accounts (jddodd, msodj, kdodo, hgygu, kskos, user_0809b21d) are preserved verbatim.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    rec RECORD;
    candidate_username text;
    clean_base text;
    counter int;
BEGIN
    FOR rec IN 
        SELECT id, handle, created_at 
        FROM public.profiles 
        ORDER BY created_at ASC 
    LOOP
        -- Sanitize handle: lowercase, alphanumeric and periods/underscores only
        clean_base := lower(regexp_replace(COALESCE(rec.handle, 'user_' || substr(rec.id::text, 1, 8)), '[^a-z0-9._]', '', 'g'));
        clean_base := trim(both '._' from clean_base);
        IF length(clean_base) < 3 THEN
            clean_base := 'user_' || substr(rec.id::text, 1, 6);
        END IF;
        IF length(clean_base) > 24 THEN
            clean_base := substr(clean_base, 1, 24);
        END IF;

        candidate_username := clean_base;
        counter := 1;
        -- Disambiguate duplicate candidate usernames
        WHILE EXISTS (SELECT 1 FROM public.profiles WHERE username = candidate_username::citext AND id <> rec.id)
           OR EXISTS (SELECT 1 FROM public.reserved_usernames WHERE username = candidate_username::citext)
        LOOP
            candidate_username := substr(clean_base, 1, 20) || '_' || counter;
            counter := counter + 1;
        END LOOP;

        UPDATE public.profiles
        SET username = candidate_username::citext
        WHERE id = rec.id;

        INSERT INTO public.profile_audit_log (user_id, field_changed, old_value, new_value)
        VALUES (rec.id, 'username_migration', rec.handle, candidate_username);
    END LOOP;
END $$;

-- Enforce format on username
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'check_profiles_username_format') THEN
        ALTER TABLE public.profiles ADD CONSTRAINT check_profiles_username_format
            CHECK (username IS NOT NULL AND username ~ '^[a-z0-9._]{3,24}$' AND username !~ '^\.|\.$|\.\.');
    END IF;
END $$;

-- Unique index on username
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_username_unique ON public.profiles (username);
CREATE INDEX IF NOT EXISTS idx_profiles_username_trgm ON public.profiles USING gin (username gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_profiles_display_name_trgm ON public.profiles USING gin (display_name gin_trgm_ops);

-- ---------------------------------------------------------------------
-- SECTION E: FOLLOWS TABLE & TRIGGERS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.follows (
    follower_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    following_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT check_cannot_follow_self CHECK (follower_id <> following_id)
);

CREATE INDEX IF NOT EXISTS idx_follows_following_created ON public.follows (following_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_follows_follower_created ON public.follows (follower_id, created_at DESC);

ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "follows_select_all" ON public.follows;
CREATE POLICY "follows_select_all" ON public.follows
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "follows_insert_own" ON public.follows;
CREATE POLICY "follows_insert_own" ON public.follows
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = follower_id);

DROP POLICY IF EXISTS "follows_delete_own" ON public.follows;
CREATE POLICY "follows_delete_own" ON public.follows
    FOR DELETE TO authenticated USING (auth.uid() = follower_id);

-- Trigger to maintain follower/following counts
CREATE OR REPLACE FUNCTION public.handle_follow_counts()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.profiles SET following_count = following_count + 1 WHERE id = NEW.follower_id;
        UPDATE public.profiles SET followers_count = followers_count + 1 WHERE id = NEW.following_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.profiles SET following_count = GREATEST(0, following_count - 1) WHERE id = OLD.follower_id;
        UPDATE public.profiles SET followers_count = GREATEST(0, followers_count - 1) WHERE id = OLD.following_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_follow_counts ON public.follows;
CREATE TRIGGER trg_follow_counts
    AFTER INSERT OR DELETE ON public.follows
    FOR EACH ROW EXECUTE FUNCTION public.handle_follow_counts();

-- ---------------------------------------------------------------------
-- SECTION F: PUBLIC NOTES TABLE & TRIGGERS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.notes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '24 hours'),
    CONSTRAINT check_note_content_length CHECK (char_length(content) >= 1 AND char_length(content) <= 60)
);

-- Partial index ensuring at most one active note per user at any time
CREATE UNIQUE INDEX IF NOT EXISTS idx_notes_user_active_unique
    ON public.notes (user_id)
    WHERE expires_at > now();

CREATE INDEX IF NOT EXISTS idx_notes_expires_at ON public.notes (expires_at);

ALTER TABLE public.notes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "notes_select_active" ON public.notes;
CREATE POLICY "notes_select_active" ON public.notes
    FOR SELECT TO authenticated
    USING (expires_at > now());

DROP POLICY IF EXISTS "notes_insert_own" ON public.notes;
CREATE POLICY "notes_insert_own" ON public.notes
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "notes_delete_own" ON public.notes;
CREATE POLICY "notes_delete_own" ON public.notes
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id);

-- ---------------------------------------------------------------------
-- SECTION G: IN-APP NOTIFICATIONS TABLE
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE, -- recipient
    actor_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE, -- actor
    type text NOT NULL CHECK (type IN ('follow', 'note')),
    reference_id text,
    is_read boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON public.notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.notifications (user_id) WHERE is_read = false;

ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "notifications_select_own" ON public.notifications;
CREATE POLICY "notifications_select_own" ON public.notifications
    FOR SELECT TO authenticated USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "notifications_update_own" ON public.notifications;
CREATE POLICY "notifications_update_own" ON public.notifications
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Realtime publication for notifications
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'notifications'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
    END IF;
END $$;

-- Follow notification trigger
CREATE OR REPLACE FUNCTION public.handle_follow_notification()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    INSERT INTO public.notifications (user_id, actor_id, type, reference_id)
    VALUES (NEW.following_id, NEW.follower_id, 'follow', NEW.follower_id::text);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_follow_notification ON public.follows;
CREATE TRIGGER trg_follow_notification
    AFTER INSERT ON public.follows
    FOR EACH ROW EXECUTE FUNCTION public.handle_follow_notification();

-- Note notification trigger (notifies followers of the note author)
CREATE OR REPLACE FUNCTION public.handle_note_notifications()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    INSERT INTO public.notifications (user_id, actor_id, type, reference_id)
    SELECT f.follower_id, NEW.user_id, 'note', NEW.id::text
    FROM public.follows f
    WHERE f.following_id = NEW.user_id;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_note_notifications ON public.notes;
CREATE TRIGGER trg_note_notifications
    AFTER INSERT ON public.notes
    FOR EACH ROW EXECUTE FUNCTION public.handle_note_notifications();

-- ---------------------------------------------------------------------
-- SECTION H: COLUMN PRIVILEGES ON public.profiles (REQUIREMENT 2)
-- Client can update only display_name, bio, avatar_url, theme_mode, theme_palette, locale, updated_at.
-- ---------------------------------------------------------------------
REVOKE UPDATE ON public.profiles FROM authenticated;
GRANT UPDATE (display_name, bio, avatar_url, theme_mode, theme_palette, locale, updated_at) 
    ON public.profiles TO authenticated;

-- Allow authenticated users to read public profile columns (excluding theme settings and locale of others)
DROP POLICY IF EXISTS "profiles_select_public" ON public.profiles;
CREATE POLICY "profiles_select_public" ON public.profiles
    FOR SELECT TO authenticated USING (true);

-- ---------------------------------------------------------------------
-- SECTION I: RPC FUNCTIONS FOR CLIENT
-- ---------------------------------------------------------------------

-- 1. Check Username Availability
CREATE OR REPLACE FUNCTION public.check_username_available(candidate text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    clean_username text;
BEGIN
    clean_username := lower(trim(candidate));
    IF clean_username !~ '^[a-z0-9._]{3,24}$' OR clean_username ~ '^\.|\.$|\.\.' THEN
        RETURN false;
    END IF;
    IF EXISTS (SELECT 1 FROM public.reserved_usernames WHERE username = clean_username::citext) THEN
        RETURN false;
    END IF;
    IF EXISTS (SELECT 1 FROM public.profiles WHERE username = clean_username::citext AND id <> auth.uid()) THEN
        RETURN false;
    END IF;
    RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.check_username_available(text) TO authenticated;

-- 2. Change Username (with 14-day cooldown and one-time review bypass)
CREATE OR REPLACE FUNCTION public.change_username(new_username text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_old_username text;
    v_clean text;
    v_changed_at timestamptz;
    v_reviewed_at timestamptz;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    v_clean := lower(trim(new_username));
    IF v_clean !~ '^[a-z0-9._]{3,24}$' OR v_clean ~ '^\.|\.$|\.\.' THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_FORMAT');
    END IF;

    IF EXISTS (SELECT 1 FROM public.reserved_usernames WHERE username = v_clean::citext) THEN
        RETURN jsonb_build_object('success', false, 'error', 'RESERVED');
    END IF;

    SELECT username, username_changed_at, username_reviewed_at
    INTO v_old_username, v_changed_at, v_reviewed_at
    FROM public.profiles
    WHERE id = v_user_id;

    IF v_clean = v_old_username THEN
        RETURN jsonb_build_object('success', true, 'username', v_clean);
    END IF;

    -- Check cooldown: 14 days, UNLESS this is the one-time initial review
    IF v_reviewed_at IS NOT NULL AND v_changed_at IS NOT NULL AND (v_changed_at + interval '14 days') > now() THEN
        RETURN jsonb_build_object(
            'success', false, 
            'error', 'COOLDOWN_ACTIVE',
            'available_at', (v_changed_at + interval '14 days')
        );
    END IF;

    IF EXISTS (SELECT 1 FROM public.profiles WHERE username = v_clean::citext AND id <> v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'TAKEN');
    END IF;

    UPDATE public.profiles
    SET username = v_clean::citext,
        username_changed_at = now(),
        username_reviewed_at = COALESCE(username_reviewed_at, now()),
        updated_at = now()
    WHERE id = v_user_id;

    INSERT INTO public.profile_audit_log (user_id, field_changed, old_value, new_value, changed_by)
    VALUES (v_user_id, 'username', v_old_username, v_clean, v_user_id);

    RETURN jsonb_build_object('success', true, 'username', v_clean);
END;
$$;
GRANT EXECUTE ON FUNCTION public.change_username(text) TO authenticated;

-- 3. Mark Username Reviewed (Non-blocking one-time review confirm)
CREATE OR REPLACE FUNCTION public.mark_username_reviewed()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    UPDATE public.profiles
    SET username_reviewed_at = now()
    WHERE id = auth.uid() AND username_reviewed_at IS NULL;
    RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.mark_username_reviewed() TO authenticated;

-- 4. Get Public Profile (by username)
CREATE OR REPLACE FUNCTION public.get_public_profile(target_username text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_caller_id uuid := auth.uid();
    v_profile RECORD;
    v_active_note jsonb := NULL;
    v_is_following boolean := false;
    v_follows_you boolean := false;
BEGIN
    SELECT p.id, p.username::text, p.display_name, p.bio, p.avatar_url, p.is_verified,
           p.following_count, p.followers_count
    INTO v_profile
    FROM public.profiles p
    WHERE p.username = lower(trim(target_username))::citext;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    -- Fetch active note if valid
    SELECT jsonb_build_object('id', id, 'content', content, 'expires_at', expires_at)
    INTO v_active_note
    FROM public.notes
    WHERE user_id = v_profile.id AND expires_at > now()
    ORDER BY created_at DESC LIMIT 1;

    IF v_caller_id IS NOT NULL AND v_caller_id <> v_profile.id THEN
        SELECT EXISTS (SELECT 1 FROM public.follows WHERE follower_id = v_caller_id AND following_id = v_profile.id) INTO v_is_following;
        SELECT EXISTS (SELECT 1 FROM public.follows WHERE follower_id = v_profile.id AND following_id = v_caller_id) INTO v_follows_you;
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
        'follows_you', v_follows_you
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.get_public_profile(text) TO authenticated;

-- 5. Search People (with pg_trgm and Arabic normalization)
CREATE OR REPLACE FUNCTION public.search_people(query text, p_limit int DEFAULT 20, p_offset int DEFAULT 0)
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
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_caller_id uuid := auth.uid();
    v_norm text;
BEGIN
    v_norm := lower(trim(query));
    -- Normalize Arabic letters
    v_norm := translate(v_norm, 'أإآىة', 'ااايه');

    RETURN QUERY
    SELECT 
        p.id,
        p.username::text,
        p.display_name,
        p.avatar_url,
        p.is_verified,
        p.followers_count,
        COALESCE((SELECT true FROM public.follows f WHERE f.follower_id = v_caller_id AND f.following_id = p.id), false) AS is_following,
        COALESCE((SELECT true FROM public.follows f WHERE f.follower_id = p.id AND f.following_id = v_caller_id), false) AS follows_you
    FROM public.profiles p
    WHERE 
        (
            p.username::text ILIKE ('%' || v_norm || '%')
            OR translate(lower(p.display_name), 'أإآىة', 'ااايه') ILIKE ('%' || v_norm || '%')
            OR similarity(p.username::text, v_norm) > 0.25
            OR similarity(translate(lower(p.display_name), 'أإآىة', 'ااايه'), v_norm) > 0.25
        )
    ORDER BY 
        (p.username::text = v_norm) DESC,
        (p.username::text ILIKE (v_norm || '%')) DESC,
        similarity(p.username::text, v_norm) DESC,
        p.is_verified DESC,
        p.followers_count DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$;
GRANT EXECUTE ON FUNCTION public.search_people(text, int, int) TO authenticated;

-- 6. Toggle Follow RPC
CREATE OR REPLACE FUNCTION public.toggle_follow(target_user_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_caller_id uuid := auth.uid();
    v_is_now_following boolean;
BEGIN
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF v_caller_id = target_user_id THEN
        RAISE EXCEPTION 'Cannot follow self';
    END IF;

    IF EXISTS (SELECT 1 FROM public.follows WHERE follower_id = v_caller_id AND following_id = target_user_id) THEN
        DELETE FROM public.follows WHERE follower_id = v_caller_id AND following_id = target_user_id;
        v_is_now_following := false;
    ELSE
        INSERT INTO public.follows (follower_id, following_id)
        VALUES (v_caller_id, target_user_id);
        v_is_now_following := true;
    END IF;

    RETURN jsonb_build_object(
        'is_following', v_is_now_following,
        'follower_id', v_caller_id,
        'target_id', target_user_id
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.toggle_follow(uuid) TO authenticated;

-- 7. Create Note RPC
CREATE OR REPLACE FUNCTION public.create_note(note_content text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_clean text;
    v_note_id uuid;
    v_expires_at timestamptz;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    v_clean := trim(note_content);
    IF char_length(v_clean) < 1 OR char_length(v_clean) > 60 THEN
        RAISE EXCEPTION 'Note length must be between 1 and 60 characters';
    END IF;

    -- Expire any previous active note for this user
    UPDATE public.notes
    SET expires_at = now()
    WHERE user_id = v_user_id AND expires_at > now();

    v_expires_at := now() + interval '24 hours';
    INSERT INTO public.notes (user_id, content, expires_at)
    VALUES (v_user_id, v_clean, v_expires_at)
    RETURNING id INTO v_note_id;

    RETURN jsonb_build_object(
        'id', v_note_id,
        'content', v_clean,
        'expires_at', v_expires_at
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.create_note(text) TO authenticated;

-- 8. Delete Note RPC
CREATE OR REPLACE FUNCTION public.delete_note()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    UPDATE public.notes
    SET expires_at = now()
    WHERE user_id = auth.uid() AND expires_at > now();
    RETURN true;
END;
$$;
GRANT EXECUTE ON FUNCTION public.delete_note() TO authenticated;

-- 9. Admin-Granted Verification (service_role only)
CREATE OR REPLACE FUNCTION public.set_verified(target_username text, verified_status boolean, reason text DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_target_id uuid;
BEGIN
    -- Verify caller is service_role
    IF current_setting('request.jwt.claim.role', true) <> 'service_role' THEN
        RAISE EXCEPTION 'Unauthorized: set_verified is restricted to service_role only.';
    END IF;

    SELECT id INTO v_target_id
    FROM public.profiles
    WHERE username = lower(trim(target_username))::citext;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    END IF;

    UPDATE public.profiles
    SET is_verified = verified_status,
        verified_at = CASE WHEN verified_status THEN now() ELSE NULL END
    WHERE id = v_target_id;

    INSERT INTO public.verification_events (user_id, action, reason, granted_by)
    VALUES (v_target_id, CASE WHEN verified_status THEN 'granted' ELSE 'revoked' END, reason, 'service_role');

    RETURN jsonb_build_object('success', true, 'username', target_username, 'is_verified', verified_status);
END;
$$;
REVOKE EXECUTE ON FUNCTION public.set_verified(text, boolean, text) FROM public, authenticated, anon;
GRANT EXECUTE ON FUNCTION public.set_verified(text, boolean, text) TO service_role;

-- Update handle_new_user() trigger for future signups to populate username
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    candidate_user text;
    counter int := 1;
BEGIN
    candidate_user := lower(regexp_replace(
        COALESCE(NEW.raw_user_meta_data->>'username', NEW.raw_user_meta_data->>'handle', split_part(COALESCE(NEW.email, 'user'), '@', 1)),
        '[^a-z0-9._]', '', 'g'
    ));
    candidate_user := trim(both '._' from candidate_user);
    IF length(candidate_user) < 3 THEN
        candidate_user := 'user_' || substr(NEW.id::text, 1, 6);
    END IF;
    IF length(candidate_user) > 24 THEN
        candidate_user := substr(candidate_user, 1, 24);
    END IF;

    WHILE EXISTS (SELECT 1 FROM public.profiles WHERE username = candidate_user::citext)
       OR EXISTS (SELECT 1 FROM public.reserved_usernames WHERE username = candidate_user::citext)
    LOOP
        candidate_user := substr(candidate_user, 1, 20) || '_' || counter;
        counter := counter + 1;
    END LOOP;

    INSERT INTO public.profiles (id, username, handle, display_name)
    VALUES (
        NEW.id,
        candidate_user::citext,
        candidate_user,
        COALESCE(NEW.raw_user_meta_data->>'display_name', split_part(COALESCE(NEW.email, 'User'), '@', 1))
    )
    ON CONFLICT (id) DO UPDATE
    SET username = COALESCE(public.profiles.username, EXCLUDED.username),
        display_name = COALESCE(NULLIF(public.profiles.display_name, 'User'), EXCLUDED.display_name);
    RETURN NEW;
END;
$$;
