-- =====================================================================
-- Build X — Profile System Rollback Script
-- Target Schema: public
-- Source Backup Schema: backup_20261110
-- =====================================================================

-- 1. Remove Realtime publication
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'notifications'
    ) THEN
        ALTER PUBLICATION supabase_realtime DROP TABLE public.notifications;
    END IF;
END $$;

-- 2. Drop triggers
DROP TRIGGER IF EXISTS trg_note_notifications ON public.notes;
DROP TRIGGER IF EXISTS trg_follow_notification ON public.follows;
DROP TRIGGER IF EXISTS trg_follow_counts ON public.follows;

-- 3. Drop RPC functions
DROP FUNCTION IF EXISTS public.handle_note_notifications();
DROP FUNCTION IF EXISTS public.handle_follow_notification();
DROP FUNCTION IF EXISTS public.handle_follow_counts();
DROP FUNCTION IF EXISTS public.check_username_available(text);
DROP FUNCTION IF EXISTS public.change_username(text);
DROP FUNCTION IF EXISTS public.mark_username_reviewed();
DROP FUNCTION IF EXISTS public.get_public_profile(text);
DROP FUNCTION IF EXISTS public.search_people(text, int, int);
DROP FUNCTION IF EXISTS public.toggle_follow(uuid);
DROP FUNCTION IF EXISTS public.create_note(text);
DROP FUNCTION IF EXISTS public.delete_note();
DROP FUNCTION IF EXISTS public.set_verified(text, boolean, text);

-- 4. Restore original handle_new_user() function
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id, handle, display_name)
    VALUES (
        NEW.id,
        COALESCE(
            NEW.raw_user_meta_data->>'handle',
            regexp_replace(split_part(COALESCE(NEW.email, 'user'), '@', 1), '[^a-zA-Z0-9_]', '', 'g') || '_' || substr(NEW.id::text, 1, 4)
        ),
        COALESCE(
            NEW.raw_user_meta_data->>'display_name',
            split_part(COALESCE(NEW.email, 'User'), '@', 1)
        )
    )
    ON CONFLICT (id) DO UPDATE
    SET handle = COALESCE(public.profiles.handle, EXCLUDED.handle),
        display_name = COALESCE(NULLIF(public.profiles.display_name, 'User'), EXCLUDED.display_name);
    RETURN NEW;
END;
$$;

-- 5. Drop new tables
DROP TABLE IF EXISTS public.notifications CASCADE;
DROP TABLE IF EXISTS public.notes CASCADE;
DROP TABLE IF EXISTS public.follows CASCADE;
DROP TABLE IF EXISTS public.verification_events CASCADE;
DROP TABLE IF EXISTS public.profile_audit_log CASCADE;
DROP TABLE IF EXISTS public.reserved_usernames CASCADE;

-- 6. Drop added columns and constraints on public.profiles
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS check_profiles_bio_length;
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS check_profiles_username_format;
DROP INDEX IF EXISTS public.idx_profiles_username_unique;
DROP INDEX IF EXISTS public.idx_profiles_username_trgm;
DROP INDEX IF EXISTS public.idx_profiles_display_name_trgm;

ALTER TABLE public.profiles DROP COLUMN IF EXISTS username;
ALTER TABLE public.profiles DROP COLUMN IF EXISTS bio;
ALTER TABLE public.profiles DROP COLUMN IF EXISTS is_verified;
ALTER TABLE public.profiles DROP COLUMN IF EXISTS verified_at;
ALTER TABLE public.profiles DROP COLUMN IF EXISTS following_count;
ALTER TABLE public.profiles DROP COLUMN IF EXISTS followers_count;
ALTER TABLE public.profiles DROP COLUMN IF EXISTS username_changed_at;
ALTER TABLE public.profiles DROP COLUMN IF EXISTS username_reviewed_at;

-- 7. Reset privileges
GRANT UPDATE ON public.profiles TO authenticated;

-- (Optional Full Table Restore if profiles data had any inconsistency):
-- TRUNCATE TABLE public.profiles CASCADE;
-- INSERT INTO public.profiles SELECT * FROM backup_20261110.profiles;
