-- Bulletproof profile protection with explicit transaction config gates
-- Direct table updates can ONLY modify user-editable profile details (display_name, bio, avatar_url, country_code, theme_mode, theme_palette, locale, updated_at).
-- Privileged fields (is_verified, verified_at, followers_count, following_count) can ONLY be changed via set_verified or admin functions.
-- Username can ONLY be changed via change_username RPC.

create or replace function public.set_verified(target_username text, verified_status boolean, reason text DEFAULT NULL::text)
returns jsonb
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_id uuid;
begin
    select id into v_id from public.profiles where username = lower(trim(target_username))::citext;
    if v_id is null then
        return jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    end if;

    perform set_config('buildx.allow_profile_admin_update', 'true', true);

    update public.profiles
    set is_verified = verified_status,
        verified_at = case when verified_status then coalesce(verified_at, now()) else null end
    where id = v_id;

    insert into public.verification_events (user_id, action, reason)
    values (v_id, case when verified_status then 'approved' else 'revoked' end, reason);

    return jsonb_build_object('success', true);
end;
$$;

create or replace function public.change_username(new_username text)
returns jsonb
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
declare
    v_user_id uuid := auth.uid();
    v_old text;
    v_clean text;
    v_changed_at timestamptz;
begin
    if v_user_id is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    v_clean := lower(trim(new_username));
    if v_clean !~ '^[a-z0-9._]{3,24}$' or v_clean ~ '^\.|\.$|\.\.' then
        return jsonb_build_object('success', false, 'error', 'INVALID_FORMAT');
    end if;
    if exists (select 1 from public.reserved_usernames r where r.username = v_clean::citext) then
        return jsonb_build_object('success', false, 'error', 'RESERVED');
    end if;

    perform pg_advisory_xact_lock(hashtext('buildx:user:' || v_user_id::text));
    select username::text, username_changed_at into v_old, v_changed_at
    from public.profiles where id = v_user_id;

    if v_clean = v_old then
        return jsonb_build_object('success', true, 'username', v_clean, 'new_username', v_clean);
    end if;
    if v_changed_at is not null and (v_changed_at + interval '14 days') > now() then
        return jsonb_build_object('success', false, 'error', 'COOLDOWN_ACTIVE',
                                  'available_at', (v_changed_at + interval '14 days'));
    end if;
    if exists (select 1 from public.profiles p where p.username = v_clean::citext and p.id <> v_user_id) then
        return jsonb_build_object('success', false, 'error', 'TAKEN');
    end if;

    perform set_config('buildx.allow_username_change', 'true', true);

    begin
        update public.profiles
        set username = v_clean::citext,
            username_changed_at = now(),
            username_reviewed_at = now()
        where id = v_user_id;
    exception when unique_violation then
        return jsonb_build_object('success', false, 'error', 'TAKEN');
    end;

    return jsonb_build_object('success', true, 'username', v_clean, 'new_username', v_clean);
end;
$$;

create or replace function public.enforce_profile_username_rules()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
begin
    new.username := lower(trim(new.username::text))::citext;
    new.handle := new.username::text;
    if new.username::text !~ '^[a-z0-9._]{3,24}$'
       or new.username::text ~ '^\.' or new.username::text ~ '\.$'
       or new.username::text ~ '\.\.' then
        raise exception 'INVALID_USERNAME' using errcode = '23514';
    end if;
    if exists (select 1 from public.reserved_usernames r where r.username = new.username) then
        raise exception 'RESERVED_USERNAME' using errcode = '23514';
    end if;

    if tg_op = 'INSERT' then
        if current_setting('buildx.allow_profile_admin_update', true) is distinct from 'true' then
            new.is_verified := false;
            new.verified_at := null;
            new.following_count := 0;
            new.followers_count := 0;
            new.username_changed_at := null;
            new.username_reviewed_at := null;
            new.created_at := now();
            new.updated_at := now();
        end if;
    end if;

    if tg_op = 'UPDATE' then
        -- If admin update gate is NOT open, freeze privileged columns
        if current_setting('buildx.allow_profile_admin_update', true) is distinct from 'true' then
            new.is_verified := old.is_verified;
            new.verified_at := old.verified_at;
            new.following_count := old.following_count;
            new.followers_count := old.followers_count;
            new.created_at := old.created_at;
        end if;

        -- If username change gate is NOT open and admin gate is NOT open, freeze username
        if current_setting('buildx.allow_username_change', true) is distinct from 'true'
           and current_setting('buildx.allow_profile_admin_update', true) is distinct from 'true' then
            new.username := old.username;
            new.handle := old.handle;
            new.username_changed_at := old.username_changed_at;
            new.username_reviewed_at := old.username_reviewed_at;
        end if;
    end if;

    return new;
end;
$$;
