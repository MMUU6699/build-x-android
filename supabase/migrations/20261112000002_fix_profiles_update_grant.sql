-- Fix UPDATE privileges and protection on public.profiles
-- Ensures authenticated users can update their own display_name, bio, avatar_url, etc. without 42501 permission denied
-- Protects is_verified, follower/following counts, and username from direct tampering.

create or replace function public.set_profiles_updated_at()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $$
begin
    new.updated_at := now();
    return new;
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
        new.is_verified := false;
        new.verified_at := null;
        new.following_count := 0;
        new.followers_count := 0;
        new.username_changed_at := null;
        new.username_reviewed_at := null;
        new.created_at := now();
        new.updated_at := now();
    end if;

    if tg_op = 'UPDATE' then
        -- Preserve privileged fields from old row so direct table UPDATE cannot tamper with them
        new.is_verified := old.is_verified;
        new.verified_at := old.verified_at;
        new.following_count := old.following_count;
        new.followers_count := old.followers_count;
        new.created_at := old.created_at;

        -- Username changes must go through change_username RPC
        if new.username is distinct from old.username and new.username_changed_at is not distinct from old.username_changed_at then
            new.username := old.username;
            new.handle := old.handle;
        end if;
    end if;

    return new;
end;
$$;

-- Grant UPDATE on profiles to authenticated (RLS policy profiles_update_own restricts rows to auth.uid() = id)
grant update on public.profiles to authenticated;
grant execute on function public.set_profiles_updated_at() to authenticated;
grant execute on function public.enforce_profile_username_rules() to authenticated;
