-- Migration 20261110000004_architectural_repair.sql
-- Phase 1 - 6 Comprehensive Architectural Repair

-- 1. Avatars & Attachments Storage Buckets & Policies
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('avatars', 'avatars', true, 5242880, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE
SET public = true,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

UPDATE storage.buckets
SET public = true
WHERE id = 'attachments';

DROP POLICY IF EXISTS storage_avatars_select_public ON storage.objects;
DROP POLICY IF EXISTS avatars_public_read ON storage.objects;
CREATE POLICY storage_avatars_select_public ON storage.objects
    FOR SELECT TO public
    USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS storage_avatars_insert_own ON storage.objects;
DROP POLICY IF EXISTS avatars_insert_own ON storage.objects;
CREATE POLICY storage_avatars_insert_own ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS storage_avatars_update_own ON storage.objects;
DROP POLICY IF EXISTS avatars_update_own ON storage.objects;
CREATE POLICY storage_avatars_update_own ON storage.objects
    FOR UPDATE TO authenticated
    USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text)
    WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS storage_avatars_delete_own ON storage.objects;
DROP POLICY IF EXISTS avatars_delete_own ON storage.objects;
CREATE POLICY storage_avatars_delete_own ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS storage_attachments_select_public ON storage.objects;
CREATE POLICY storage_attachments_select_public ON storage.objects
    FOR SELECT TO public
    USING (bucket_id = 'attachments');

-- 2. Verification Requests Table & RLS
CREATE TABLE IF NOT EXISTS public.verification_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reason text NOT NULL,
    full_name text,
    category text,
    links text,
    status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    reviewed_by text,
    decision_reason text
);

ALTER TABLE public.verification_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS verification_requests_select_own ON public.verification_requests;
CREATE POLICY verification_requests_select_own ON public.verification_requests
    FOR SELECT TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS verification_requests_insert_own ON public.verification_requests;
CREATE POLICY verification_requests_insert_own ON public.verification_requests
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS verification_requests_delete_own ON public.verification_requests;
CREATE POLICY verification_requests_delete_own ON public.verification_requests
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id AND status = 'pending');

-- 3. Withdraw Verification Request RPC
CREATE OR REPLACE FUNCTION public.withdraw_verification_request(p_request_id uuid DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_count int;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF p_request_id IS NOT NULL THEN
        DELETE FROM public.verification_requests
        WHERE id = p_request_id AND user_id = v_user_id AND status = 'pending';
    ELSE
        DELETE FROM public.verification_requests
        WHERE user_id = v_user_id AND status = 'pending';
    END IF;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN jsonb_build_object('success', true, 'deleted_count', v_count);
END;
$$;

GRANT EXECUTE ON FUNCTION public.withdraw_verification_request(uuid) TO authenticated, service_role;

-- 4. True Account Deletion RPC
CREATE OR REPLACE FUNCTION public.delete_my_account()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, storage
AS $$
DECLARE
    v_user_id uuid := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    DELETE FROM storage.objects
    WHERE (bucket_id IN ('avatars', 'attachments', 'workspace_screenshots'))
      AND ((storage.foldername(name))[1] = v_user_id::text OR owner = v_user_id);

    DELETE FROM auth.users WHERE id = v_user_id;

    RETURN true;
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_my_account() TO authenticated, service_role;

-- 5. Active Note Retrieval RPC
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

    SELECT id, content, created_at, expires_at
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
        'content', v_note.content,
        'created_at', v_note.created_at,
        'expires_at', v_note.expires_at,
        'author_id', v_user_id
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_my_active_note() TO authenticated, service_role;

-- 6. Follower/Following/Notification RPC Commitments
CREATE OR REPLACE FUNCTION public.get_user_followers(target_user_id uuid)
RETURNS TABLE(id uuid, username text, display_name text, avatar_url text, is_verified boolean, is_following boolean, follows_you boolean)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare
    caller_id uuid := auth.uid();
begin
    if caller_id is null then
        return;
    end if;
    return query
    select p.id, p.username::text, p.display_name, p.avatar_url, p.is_verified,
        exists (select 1 from public.follows f2
                where f2.follower_id = caller_id and f2.following_id = p.id),
        exists (select 1 from public.follows f3
                where f3.follower_id = p.id and f3.following_id = caller_id)
    from public.follows f
    join public.profiles p on p.id = f.follower_id
    where f.following_id = target_user_id
    order by f.created_at desc;
end;
$function$;

GRANT EXECUTE ON FUNCTION public.get_user_followers(uuid) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_user_following(target_user_id uuid)
RETURNS TABLE(id uuid, username text, display_name text, avatar_url text, is_verified boolean, is_following boolean, follows_you boolean)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare
    caller_id uuid := auth.uid();
begin
    if caller_id is null then
        return;
    end if;
    return query
    select p.id, p.username::text, p.display_name, p.avatar_url, p.is_verified,
        exists (select 1 from public.follows f2
                where f2.follower_id = caller_id and f2.following_id = p.id),
        exists (select 1 from public.follows f3
                where f3.follower_id = p.id and f3.following_id = caller_id)
    from public.follows f
    join public.profiles p on p.id = f.following_id
    where f.follower_id = target_user_id
    order by f.created_at desc;
end;
$function$;

GRANT EXECUTE ON FUNCTION public.get_user_following(uuid) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_user_notifications(p_limit integer DEFAULT 50, p_offset integer DEFAULT 0)
RETURNS TABLE(id uuid, user_id uuid, actor_id uuid, type text, reference_id text, is_read boolean, created_at timestamp with time zone, actor_username text, actor_display_name text, actor_avatar_url text, actor_is_verified boolean, note_content text, note_expires_at timestamp with time zone)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare
    caller_id uuid := auth.uid();
    v_limit int;
    v_offset int;
begin
    if caller_id is null then
        return;
    end if;
    v_limit := least(greatest(coalesce(p_limit, 50), 1), 100);
    v_offset := greatest(coalesce(p_offset, 0), 0);
    return query
    select n.id, n.user_id, n.actor_id, n.type, n.reference_id, n.is_read, n.created_at,
        p.username::text, p.display_name, p.avatar_url, p.is_verified,
        nt.content, nt.expires_at
    from public.notifications n
    left join public.profiles p on p.id = n.actor_id
    left join public.notes nt on (n.type = 'note' and nt.id::text = n.reference_id)
    where n.user_id = caller_id
    order by n.created_at desc
    limit v_limit offset v_offset;
end;
$function$;

GRANT EXECUTE ON FUNCTION public.get_user_notifications(integer, integer) TO authenticated, service_role;

-- 7. Ensure check_username_available is accessible to anon
GRANT EXECUTE ON FUNCTION public.check_username_available(text) TO anon, authenticated, service_role;

-- 8. Update toggle_follow to return both is_following and following
CREATE OR REPLACE FUNCTION public.toggle_follow(target_user_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare
    v_caller_id uuid := auth.uid();
    v_is_now_following boolean;
    v_recent int;
    v_followers int;
    v_following int;
begin
    if v_caller_id is null then
        raise exception 'Not authenticated';
    end if;
    if v_caller_id = target_user_id then
        raise exception 'Cannot follow self';
    end if;
    if not exists (select 1 from public.profiles where id = target_user_id) then
        return jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    end if;

    perform pg_advisory_xact_lock(hashtext('buildx:follow-rate:' || v_caller_id::text));

    delete from public.follow_rate_events
    where user_id = v_caller_id and created_at < now() - interval '1 day';

    select count(*) into v_recent
    from public.follow_rate_events
    where user_id = v_caller_id and created_at > now() - interval '1 minute';

    if v_recent >= 30 then
        return jsonb_build_object('success', false, 'error', 'RATE_LIMITED');
    end if;

    if exists (
        select 1 from public.follows
        where follower_id = v_caller_id and following_id = target_user_id
    ) then
        delete from public.follows
        where follower_id = v_caller_id and following_id = target_user_id;
        delete from public.note_subscriptions
        where subscriber_id = v_caller_id and author_id = target_user_id;
        insert into public.follow_rate_events (user_id, action)
        values (v_caller_id, 'unfollow');
        v_is_now_following := false;
    else
        insert into public.follows (follower_id, following_id)
        values (v_caller_id, target_user_id)
        on conflict (follower_id, following_id) do nothing;
        insert into public.follow_rate_events (user_id, action)
        values (v_caller_id, 'follow');
        v_is_now_following := true;
    end if;

    select followers_count into v_followers from public.profiles where id = target_user_id;
    select following_count into v_following from public.profiles where id = v_caller_id;

    return jsonb_build_object(
        'success', true,
        'is_following', v_is_now_following,
        'following', v_is_now_following,
        'followers_count', coalesce(v_followers, 0),
        'following_count', coalesce(v_following, 0)
    );
end;
$function$;

GRANT EXECUTE ON FUNCTION public.toggle_follow(uuid) TO authenticated, service_role;

-- 9. Update change_username to return both username and new_username
CREATE OR REPLACE FUNCTION public.change_username(new_username text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare
    v_user_id uuid := auth.uid();
    v_old_username text;
    v_clean text;
    v_changed_at timestamptz;
begin
    if v_user_id is null then
        raise exception 'Not authenticated';
    end if;

    v_clean := lower(trim(new_username));
    if v_clean !~ '^[a-z0-9._]{3,24}$' or v_clean ~ '^\.|\.$|\.\.' then
        return jsonb_build_object('success', false, 'error', 'INVALID_FORMAT');
    end if;
    if exists (select 1 from public.reserved_usernames where username = v_clean::citext) then
        return jsonb_build_object('success', false, 'error', 'RESERVED');
    end if;

    perform pg_advisory_xact_lock(hashtext('buildx:user:' || v_user_id::text));

    select username::text, username_changed_at
    into v_old_username, v_changed_at
    from public.profiles
    where id = v_user_id;

    if v_clean = v_old_username then
        return jsonb_build_object('success', true, 'username', v_clean, 'new_username', v_clean);
    end if;

    perform pg_advisory_xact_lock(hashtext('buildx:username:' || v_clean));

    if v_changed_at is not null and (v_changed_at + interval '14 days') > now() then
        return jsonb_build_object(
            'success', false,
            'error', 'COOLDOWN_ACTIVE',
            'available_at', (v_changed_at + interval '14 days')
        );
    end if;

    if exists (
        select 1 from public.profiles
        where username = v_clean::citext and id <> v_user_id
    ) then
        return jsonb_build_object('success', false, 'error', 'TAKEN');
    end if;

    begin
        update public.profiles
        set username = v_clean::citext,
            handle = v_clean,
            username_changed_at = now(),
            username_reviewed_at = now(),
            updated_at = now()
        where id = v_user_id;
    exception when unique_violation then
        return jsonb_build_object('success', false, 'error', 'TAKEN');
    end;

    return jsonb_build_object('success', true, 'username', v_clean, 'new_username', v_clean);
end;
$function$;

GRANT EXECUTE ON FUNCTION public.change_username(text) TO authenticated, service_role;

-- 10. Update handle_new_user trigger function supporting Google OAuth metadata
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    candidate_user text;
    counter int := 1;
    v_display text;
    v_avatar text;
BEGIN
    candidate_user := lower(coalesce(
        NEW.raw_user_meta_data->>'username',
        NEW.raw_user_meta_data->>'handle',
        split_part(coalesce(NEW.email, 'user'), '@', 1)
    ));
    candidate_user := regexp_replace(candidate_user, '[^a-z0-9._]', '', 'g');
    candidate_user := regexp_replace(candidate_user, '\.+', '.', 'g');
    candidate_user := trim(both '._' from candidate_user);

    IF length(candidate_user) < 3 THEN
        candidate_user := 'user_' || substr(NEW.id::text, 1, 6);
    END IF;
    IF length(candidate_user) > 24 THEN
        candidate_user := substr(candidate_user, 1, 24);
        candidate_user := trim(trailing '._' from candidate_user);
        IF length(candidate_user) < 3 THEN
            candidate_user := 'user_' || substr(NEW.id::text, 1, 6);
        END IF;
    END IF;

    WHILE exists (SELECT 1 FROM public.profiles WHERE username = candidate_user::citext)
       OR exists (SELECT 1 FROM public.reserved_usernames WHERE username = candidate_user::citext)
    LOOP
        candidate_user := substr(candidate_user, 1, 18) || '_' || counter;
        counter := counter + 1;
    END LOOP;

    v_display := COALESCE(
        NULLIF(trim(NEW.raw_user_meta_data->>'display_name'), ''),
        NULLIF(trim(NEW.raw_user_meta_data->>'full_name'), ''),
        NULLIF(trim(NEW.raw_user_meta_data->>'name'), ''),
        split_part(COALESCE(NEW.email, 'User'), '@', 1)
    );
    IF length(v_display) > 30 THEN
        v_display := substr(v_display, 1, 30);
    END IF;

    v_avatar := COALESCE(
        NULLIF(trim(NEW.raw_user_meta_data->>'avatar_url'), ''),
        NULLIF(trim(NEW.raw_user_meta_data->>'picture'), '')
    );

    INSERT INTO public.profiles (
        id, username, handle, display_name, avatar_url, username_reviewed_at
    ) VALUES (
        NEW.id,
        candidate_user::citext,
        candidate_user,
        v_display,
        v_avatar,
        now()
    )
    ON CONFLICT (id) DO UPDATE
    SET username = coalesce(public.profiles.username, excluded.username),
        handle = coalesce(public.profiles.handle, excluded.handle),
        display_name = coalesce(public.profiles.display_name, excluded.display_name),
        avatar_url = coalesce(public.profiles.avatar_url, excluded.avatar_url),
        username_reviewed_at = coalesce(public.profiles.username_reviewed_at, now());

    RETURN NEW;
END;
$$;

-- 11. Reconcile existing data
UPDATE public.profiles
SET handle = username::text
WHERE username IS NOT NULL AND (handle IS DISTINCT FROM username::text);

UPDATE public.profiles p
SET display_name = COALESCE(
        NULLIF(trim(u.raw_user_meta_data->>'display_name'), ''),
        NULLIF(trim(u.raw_user_meta_data->>'full_name'), ''),
        NULLIF(trim(u.raw_user_meta_data->>'name'), ''),
        p.display_name
    ),
    avatar_url = COALESCE(p.avatar_url, NULLIF(trim(u.raw_user_meta_data->>'avatar_url'), ''), NULLIF(trim(u.raw_user_meta_data->>'picture'), ''))
FROM auth.users u
WHERE p.id = u.id AND (u.raw_user_meta_data->>'iss' LIKE '%google%' OR u.raw_app_meta_data->>'provider' = 'google');
