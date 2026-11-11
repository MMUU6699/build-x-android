-- Build X - clean production rebuild of the profile/social domain.
-- Authorized full data reset. Does NOT touch unrelated work_*/assistants/workspaces
-- tables, Supabase-managed schemas, or project configuration.
--
-- Rollback: docs/rebuild/ROLLBACK_20261111000002.sql
-- Apply inside a single transaction.

begin;

-- ---------------------------------------------------------------------------
-- 1. Drop dependent functions first (CASCADE also drops their triggers)
-- ---------------------------------------------------------------------------
do $$
declare r record;
begin
  for r in
    select p.oid
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'handle_new_user','auto_confirm_email','handle_follow_counts',
        'handle_follow_notification','handle_note_notifications',
        'set_profiles_updated_at','audit_username_change',
        'enforce_profile_username_rules','check_username_available',
        'change_username','mark_username_reviewed','get_public_profile',
        'search_people','toggle_follow','create_note','delete_note',
        'get_my_active_note','set_verified','toggle_note_subscription',
        'get_user_followers','get_user_following','get_user_notifications',
        'get_my_verification_status','request_verification',
        'withdraw_verification_request','delete_my_account',
        'update_profile_avatar','update_profile_details','get_login_email',
        'edit_user_message','block_user','unblock_user','submit_report',
        'resolve_report','get_account_info','get_blocked_users',
        'reconcile_follow_counts','purge_expired_notes',
        'purge_follow_rate_events','_are_blocked'
      )
  loop
    execute format('drop function if exists %s cascade', r.oid::regprocedure);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- 2. Drop old profile-domain tables (data reset was explicitly authorized)
-- ---------------------------------------------------------------------------
drop table if exists public.blocks cascade;
drop table if exists public.reports cascade;
drop table if exists public.follow_rate_events cascade;
drop table if exists public.note_subscriptions cascade;
drop table if exists public.notifications cascade;
drop table if exists public.notes cascade;
drop table if exists public.follows cascade;
drop table if exists public.verification_requests cascade;
drop table if exists public.verification_events cascade;
drop table if exists public.profile_audit_log cascade;
drop table if exists public.reserved_usernames cascade;
drop table if exists public.profiles cascade;

-- ---------------------------------------------------------------------------
-- 3. Tables
-- ---------------------------------------------------------------------------
create table public.profiles (
    id                  uuid primary key references auth.users(id) on delete cascade,
    username            citext not null unique
                        check (username::text ~ '^[a-z0-9._]{3,24}$'
                               and username::text !~ '^\.'
                               and username::text !~ '\.$'
                               and username::text !~ '\.\.'),
    handle              text not null,
    display_name        text not null default 'User'
                        check (char_length(trim(display_name)) between 1 and 60),
    bio                 text check (char_length(bio) <= 300),
    avatar_url          text,
    country_code        char(2) check (country_code ~ '^[A-Z]{2}$'),
    is_verified         boolean not null default false,
    verified_at         timestamptz,
    following_count     integer not null default 0 check (following_count >= 0),
    followers_count     integer not null default 0 check (followers_count >= 0),
    username_changed_at timestamptz,
    username_reviewed_at timestamptz,
    theme_mode          text not null default 'automatic'
                        check (theme_mode in ('automatic','light','dark')),
    theme_palette       text not null default 'default',
    locale              text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint profiles_verified_consistency check (not is_verified or verified_at is not null)
);

create table public.follows (
    follower_id  uuid not null references public.profiles(id) on delete cascade,
    following_id uuid not null references public.profiles(id) on delete cascade,
    created_at   timestamptz not null default now(),
    primary key (follower_id, following_id),
    constraint follows_no_self check (follower_id <> following_id)
);
create index follows_following_idx on public.follows (following_id, created_at desc);

create table public.notes (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references public.profiles(id) on delete cascade,
    content    text not null check (char_length(trim(content)) between 1 and 60),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '24 hours'),
    is_active  boolean not null default true,
    constraint notes_expiry_after_creation check (expires_at > created_at)
);
create unique index notes_one_active_per_user on public.notes (user_id) where is_active;
create index notes_active_expiry_idx on public.notes (expires_at) where is_active;

create table public.note_subscriptions (
    subscriber_id uuid not null references public.profiles(id) on delete cascade,
    author_id     uuid not null references public.profiles(id) on delete cascade,
    created_at    timestamptz not null default now(),
    primary key (subscriber_id, author_id),
    constraint note_subscriptions_no_self check (subscriber_id <> author_id)
);
create index note_subscriptions_author_idx on public.note_subscriptions (author_id);

create table public.notifications (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references public.profiles(id) on delete cascade,
    actor_id     uuid not null references public.profiles(id) on delete cascade,
    type         text not null check (type in ('follow','note')),
    reference_id text,
    is_read      boolean not null default false,
    created_at   timestamptz not null default now(),
    constraint notifications_no_self check (user_id <> actor_id)
);
create unique index notifications_unique_follow
    on public.notifications (user_id, actor_id) where type = 'follow';
create unique index notifications_unique_note
    on public.notifications (user_id, actor_id, reference_id) where type = 'note';
create index notifications_user_created_idx on public.notifications (user_id, created_at desc);
create index notifications_actor_idx on public.notifications (actor_id);

create table public.profile_audit_log (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null references public.profiles(id) on delete cascade,
    field_changed text not null,
    old_value     text,
    new_value     text,
    changed_by    uuid references public.profiles(id) on delete set null,
    created_at    timestamptz not null default now()
);
create index profile_audit_log_user_idx on public.profile_audit_log (user_id, created_at desc);

create table public.reserved_usernames (
    username   citext primary key,
    reason     text not null default 'system_reserved',
    created_at timestamptz not null default now()
);

create table public.follow_rate_events (
    id         bigint generated always as identity primary key,
    user_id    uuid not null references public.profiles(id) on delete cascade,
    action     text not null check (action in ('follow','unfollow')),
    created_at timestamptz not null default now()
);
create index follow_rate_events_user_idx on public.follow_rate_events (user_id, created_at desc);

create table public.blocks (
    blocker_id uuid not null references public.profiles(id) on delete cascade,
    blocked_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint blocks_no_self check (blocker_id <> blocked_id)
);
create index blocks_blocked_idx on public.blocks (blocked_id);

create table public.reports (
    id              uuid primary key default gen_random_uuid(),
    reporter_id     uuid not null references public.profiles(id) on delete cascade,
    target_id       uuid not null references public.profiles(id) on delete cascade,
    reason          text not null check (reason in (
                        'spam','impersonation','fake_account',
                        'inappropriate_content','harassment','other')),
    details         text check (char_length(details) <= 500),
    status          text not null default 'open'
                    check (status in ('open','resolved','dismissed')),
    created_at      timestamptz not null default now(),
    resolved_at     timestamptz,
    resolved_by     uuid references auth.users(id) on delete set null,
    resolution_note text check (char_length(resolution_note) <= 500),
    constraint reports_no_self check (reporter_id <> target_id)
);
create index reports_reporter_idx on public.reports (reporter_id, created_at desc);
create index reports_target_idx on public.reports (target_id, created_at desc);
create index reports_open_idx on public.reports (status) where status = 'open';

create table public.verification_requests (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references public.profiles(id) on delete cascade,
    full_name       text not null check (char_length(trim(full_name)) between 2 and 80),
    category        text not null check (char_length(category) between 1 and 40),
    links           text check (char_length(links) <= 500),
    reason          text not null check (char_length(trim(reason)) between 10 and 1000),
    status          text not null default 'pending'
                    check (status in ('pending','approved','rejected','withdrawn')),
    decision_reason text check (char_length(decision_reason) <= 500),
    reviewed_at     timestamptz,
    reviewed_by     uuid references auth.users(id) on delete set null,
    created_at      timestamptz not null default now()
);
create unique index verification_requests_one_pending
    on public.verification_requests (user_id) where status = 'pending';

create table public.verification_events (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references public.profiles(id) on delete cascade,
    action     text not null check (action in ('approved','rejected','revoked','withdrawn')),
    reason     text check (char_length(reason) <= 500),
    actor_id   uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now()
);
create index verification_events_user_idx on public.verification_events (user_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 4. Internal helper
-- ---------------------------------------------------------------------------
create function public._are_blocked(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select a is not null and b is not null and exists (
        select 1 from public.blocks
        where (blocker_id = a and blocked_id = b)
           or (blocker_id = b and blocked_id = a)
    );
$$;
revoke all on function public._are_blocked(uuid, uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 5. Trigger functions
-- ---------------------------------------------------------------------------
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_base text;
    v_uname citext;
    v_suffix int := 0;
    v_display text;
begin
    v_base := lower(coalesce(
        new.raw_user_meta_data->>'username',
        new.raw_user_meta_data->>'preferred_username',
        split_part(coalesce(new.email, ''), '@', 1),
        'user'));
    v_base := regexp_replace(v_base, '[^a-z0-9._]', '', 'g');
    if char_length(v_base) < 3 then
        v_base := 'user';
    end if;
    v_base := left(v_base, 20);
    v_uname := v_base::citext;
    while exists (select 1 from public.profiles where username = v_uname)
       or exists (select 1 from public.reserved_usernames where username = v_uname)
    loop
        v_suffix := v_suffix + 1;
        v_uname := (v_base || '_' || v_suffix)::citext;
    end loop;

    v_display := coalesce(
        nullif(trim(new.raw_user_meta_data->>'display_name'), ''),
        nullif(trim(new.raw_user_meta_data->>'full_name'), ''),
        nullif(trim(new.raw_user_meta_data->>'name'), ''),
        v_uname::text);

    insert into public.profiles (id, username, display_name, avatar_url, created_at, updated_at)
    values (new.id, v_uname, left(v_display, 60), nullif(new.raw_user_meta_data->>'avatar_url', ''), now(), now())
    on conflict (id) do nothing;
    return new;
end;
$$;

create function public.set_profiles_updated_at()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create function public.enforce_profile_username_rules()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
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
    -- Never trust client-supplied privileged fields on insert.
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
    return new;
end;
$$;

create function public.audit_username_change()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.username is distinct from old.username then
        insert into public.profile_audit_log (user_id, field_changed, old_value, new_value, changed_by)
        values (new.id, 'username', old.username::text, new.username::text, auth.uid());
    end if;
    return new;
end;
$$;

create function public.handle_follow_counts()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if tg_op = 'INSERT' then
        update public.profiles set following_count = following_count + 1 where id = new.follower_id;
        update public.profiles set followers_count = followers_count + 1 where id = new.following_id;
    elsif tg_op = 'DELETE' then
        update public.profiles set following_count = greatest(following_count - 1, 0) where id = old.follower_id;
        update public.profiles set followers_count = greatest(followers_count - 1, 0) where id = old.following_id;
    end if;
    return null;
end;
$$;

create function public.handle_follow_notification()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if public._are_blocked(new.follower_id, new.following_id) then
        return null;
    end if;
    insert into public.notifications (user_id, actor_id, type)
    values (new.following_id, new.follower_id, 'follow')
    on conflict do nothing;
    return null;
end;
$$;

create function public.handle_note_notifications()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.is_active is not true then
        return null;
    end if;
    insert into public.notifications (user_id, actor_id, type, reference_id)
    select s.subscriber_id, new.user_id, 'note', new.id::text
    from public.note_subscriptions s
    where s.author_id = new.user_id
      and s.subscriber_id <> new.user_id
      and not public._are_blocked(s.subscriber_id, new.user_id)
    on conflict do nothing;
    return null;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPC functions
-- ---------------------------------------------------------------------------
create function public.check_username_available(candidate text)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select case
        when lower(trim(candidate)) !~ '^[a-z0-9._]{3,24}$' then false
        when lower(trim(candidate)) ~ '^\.' or lower(trim(candidate)) ~ '\.$'
             or lower(trim(candidate)) ~ '\.\.' then false
        when exists (select 1 from public.reserved_usernames r where r.username = lower(trim(candidate))::citext) then false
        else not exists (select 1 from public.profiles p where p.username = lower(trim(candidate))::citext)
    end;
$$;

create function public.change_username(new_username text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
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

create function public.mark_username_reviewed()
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    update public.profiles set username_reviewed_at = now() where id = auth.uid();
$$;

create function public.get_public_profile(target_username text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_p record;
    v_note jsonb := null;
    v_is_following boolean := false;
    v_follows_you boolean := false;
    v_subscribed boolean := false;
begin
    select id, username::text as username, display_name, bio, avatar_url, country_code,
           is_verified, verified_at, following_count, followers_count, created_at
    into v_p
    from public.profiles
    where username = lower(trim(target_username))::citext;

    if not found then
        return null;
    end if;

    if v_caller is not null and v_caller <> v_p.id then
        if exists (select 1 from public.blocks b
                   where b.blocker_id = v_caller and b.blocked_id = v_p.id) then
            return jsonb_build_object(
                'id', v_p.id,
                'username', v_p.username,
                'display_name', v_p.display_name,
                'avatar_url', v_p.avatar_url,
                'blocked_by_me', true);
        end if;
        if exists (select 1 from public.blocks b
                   where b.blocker_id = v_p.id and b.blocked_id = v_caller) then
            return null;
        end if;
    end if;

    select jsonb_build_object(
        'id', n.id, 'user_id', n.user_id, 'content', n.content,
        'created_at', n.created_at, 'expires_at', n.expires_at, 'is_active', n.is_active)
    into v_note
    from public.notes n
    where n.user_id = v_p.id and n.is_active and n.expires_at > now()
    order by n.created_at desc limit 1;

    if v_caller is not null and v_caller <> v_p.id then
        select exists (select 1 from public.follows f
                       where f.follower_id = v_caller and f.following_id = v_p.id)
        into v_is_following;
        select exists (select 1 from public.follows f
                       where f.follower_id = v_p.id and f.following_id = v_caller)
        into v_follows_you;
        select exists (select 1 from public.note_subscriptions s
                       where s.subscriber_id = v_caller and s.author_id = v_p.id)
        into v_subscribed;
    end if;

    return jsonb_build_object(
        'id', v_p.id,
        'username', v_p.username,
        'display_name', v_p.display_name,
        'bio', v_p.bio,
        'avatar_url', v_p.avatar_url,
        'country_code', v_p.country_code,
        'is_verified', v_p.is_verified,
        'verified_at', v_p.verified_at,
        'following_count', v_p.following_count,
        'followers_count', v_p.followers_count,
        'created_at', v_p.created_at,
        'active_note', v_note,
        'is_following', v_is_following,
        'follows_you', v_follows_you,
        'note_subscribed', v_subscribed,
        'blocked_by_me', false);
end;
$$;

create function public.search_people(query text, p_limit int default 20, p_offset int default 0)
returns table (id uuid, username text, display_name text, avatar_url text,
               is_verified boolean, followers_count integer,
               is_following boolean, follows_you boolean)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_norm text;
    v_limit int;
    v_offset int;
begin
    if v_caller is null then
        return;
    end if;
    v_norm := lower(trim(query));
    v_norm := translate(v_norm, 'أإآىة', 'ااايه');
    v_limit := least(greatest(coalesce(p_limit, 20), 1), 50);
    v_offset := greatest(coalesce(p_offset, 0), 0);

    return query
    select p.id, p.username::text, p.display_name, p.avatar_url, p.is_verified,
           p.followers_count,
           coalesce((select true from public.follows f
                     where f.follower_id = v_caller and f.following_id = p.id), false),
           coalesce((select true from public.follows f
                     where f.follower_id = p.id and f.following_id = v_caller), false)
    from public.profiles p
    where p.id <> v_caller
      and not public._are_blocked(v_caller, p.id)
      and (v_norm = ''
        or p.username::text ilike ('%' || v_norm || '%')
        or translate(lower(p.display_name), 'أإآىة', 'ااايه') ilike ('%' || v_norm || '%')
        or similarity(p.username::text, v_norm) > 0.25
        or similarity(translate(lower(p.display_name), 'أإآىة', 'ااايه'), v_norm) > 0.25)
    order by (p.username::text = v_norm) desc,
             (p.username::text ilike (v_norm || '%')) desc,
             similarity(p.username::text, v_norm) desc,
             p.is_verified desc,
             p.followers_count desc
    limit v_limit offset v_offset;
end;
$$;

create function public.toggle_follow(target_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_now boolean;
    v_recent int;
    v_followers int;
    v_following int;
begin
    if v_caller is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if v_caller = target_user_id then
        return jsonb_build_object('success', false, 'error', 'SELF_FOLLOW');
    end if;
    if not exists (select 1 from public.profiles where id = target_user_id) then
        return jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    end if;
    if public._are_blocked(v_caller, target_user_id) then
        return jsonb_build_object('success', false, 'error', 'BLOCKED');
    end if;

    perform pg_advisory_xact_lock(hashtext('buildx:follow-rate:' || v_caller::text));
    delete from public.follow_rate_events
    where user_id = v_caller and created_at < now() - interval '1 day';
    select count(*) into v_recent from public.follow_rate_events
    where user_id = v_caller and created_at > now() - interval '1 minute';
    if v_recent >= 30 then
        return jsonb_build_object('success', false, 'error', 'RATE_LIMITED');
    end if;

    if exists (select 1 from public.follows
               where follower_id = v_caller and following_id = target_user_id) then
        delete from public.follows
        where follower_id = v_caller and following_id = target_user_id;
        delete from public.note_subscriptions
        where subscriber_id = v_caller and author_id = target_user_id;
        insert into public.follow_rate_events (user_id, action) values (v_caller, 'unfollow');
        v_now := false;
    else
        insert into public.follows (follower_id, following_id)
        values (v_caller, target_user_id)
        on conflict do nothing;
        insert into public.follow_rate_events (user_id, action) values (v_caller, 'follow');
        v_now := true;
    end if;

    select followers_count into v_followers from public.profiles where id = target_user_id;
    select following_count into v_following from public.profiles where id = v_caller;

    return jsonb_build_object(
        'success', true, 'is_following', v_now, 'following', v_now,
        'followers_count', coalesce(v_followers, 0),
        'following_count', coalesce(v_following, 0));
end;
$$;

create function public.get_user_followers(target_user_id uuid)
returns table (id uuid, username text, display_name text, avatar_url text,
               is_verified boolean, is_following boolean, follows_you boolean)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null or public._are_blocked(v_caller, target_user_id) then
        return;
    end if;
    return query
    select p.id, p.username::text, p.display_name, p.avatar_url, p.is_verified,
        exists (select 1 from public.follows f2
                where f2.follower_id = v_caller and f2.following_id = p.id),
        exists (select 1 from public.follows f3
                where f3.follower_id = p.id and f3.following_id = v_caller)
    from public.follows f
    join public.profiles p on p.id = f.follower_id
    where f.following_id = target_user_id
      and not public._are_blocked(v_caller, p.id)
    order by f.created_at desc;
end;
$$;

create function public.get_user_following(target_user_id uuid)
returns table (id uuid, username text, display_name text, avatar_url text,
               is_verified boolean, is_following boolean, follows_you boolean)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null or public._are_blocked(v_caller, target_user_id) then
        return;
    end if;
    return query
    select p.id, p.username::text, p.display_name, p.avatar_url, p.is_verified,
        exists (select 1 from public.follows f2
                where f2.follower_id = v_caller and f2.following_id = p.id),
        exists (select 1 from public.follows f3
                where f3.follower_id = p.id and f3.following_id = v_caller)
    from public.follows f
    join public.profiles p on p.id = f.following_id
    where f.follower_id = target_user_id
      and not public._are_blocked(v_caller, p.id)
    order by f.created_at desc;
end;
$$;

create function public.get_user_notifications(p_limit integer default 50, p_offset integer default 0)
returns table (id uuid, user_id uuid, actor_id uuid, type text, reference_id text,
               is_read boolean, created_at timestamptz, actor_username text,
               actor_display_name text, actor_avatar_url text,
               actor_is_verified boolean, note_content text,
               note_expires_at timestamptz)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select n.id, n.user_id, n.actor_id, n.type, n.reference_id, n.is_read, n.created_at,
           a.username::text, a.display_name, a.avatar_url, a.is_verified,
           nt.content, nt.expires_at
    from public.notifications n
    join public.profiles a on a.id = n.actor_id
    left join public.notes nt on n.type = 'note' and nt.id::text = n.reference_id
    where n.user_id = auth.uid()
      and not public._are_blocked(n.user_id, n.actor_id)
    order by n.created_at desc
    limit least(greatest(coalesce(p_limit, 50), 1), 100)
    offset greatest(coalesce(p_offset, 0), 0);
$$;

create function public.create_note(note_content text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_clean text;
    v_id uuid;
    v_created timestamptz := now();
begin
    if v_user is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    v_clean := trim(note_content);
    if char_length(v_clean) < 1 or char_length(v_clean) > 60 then
        raise exception 'NOTE_LENGTH';
    end if;

    update public.notes set is_active = false
    where user_id = v_user and is_active;

    insert into public.notes (user_id, content, created_at, expires_at, is_active)
    values (v_user, v_clean, v_created, v_created + interval '24 hours', true)
    returning id into v_id;

    return jsonb_build_object(
        'id', v_id, 'user_id', v_user, 'content', v_clean,
        'created_at', v_created, 'expires_at', v_created + interval '24 hours',
        'is_active', true);
end;
$$;

create function public.delete_note()
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    update public.notes set is_active = false where user_id = auth.uid() and is_active;
$$;

create function public.get_my_active_note()
returns jsonb
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select jsonb_build_object(
        'id', n.id, 'user_id', n.user_id, 'content', n.content,
        'created_at', n.created_at, 'expires_at', n.expires_at, 'is_active', n.is_active)
    from public.notes n
    where n.user_id = auth.uid() and n.is_active and n.expires_at > now()
    order by n.created_at desc limit 1;
$$;

create function public.toggle_note_subscription(target_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_subscribed boolean;
begin
    if v_caller is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if v_caller = target_user_id then
        return jsonb_build_object('success', false, 'error', 'SELF');
    end if;
    if public._are_blocked(v_caller, target_user_id) then
        return jsonb_build_object('success', false, 'error', 'BLOCKED');
    end if;
    if not exists (select 1 from public.follows
                   where follower_id = v_caller and following_id = target_user_id) then
        return jsonb_build_object('success', false, 'error', 'NOT_FOLLOWING');
    end if;

    if exists (select 1 from public.note_subscriptions
               where subscriber_id = v_caller and author_id = target_user_id) then
        delete from public.note_subscriptions
        where subscriber_id = v_caller and author_id = target_user_id;
        v_subscribed := false;
    else
        insert into public.note_subscriptions (subscriber_id, author_id)
        values (v_caller, target_user_id) on conflict do nothing;
        v_subscribed := true;
    end if;
    return jsonb_build_object('success', true, 'subscribed', v_subscribed);
end;
$$;

create function public.block_user(target_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if v_caller = target_user_id then
        return jsonb_build_object('success', false, 'error', 'SELF');
    end if;
    if not exists (select 1 from public.profiles where id = target_user_id) then
        return jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    end if;

    perform pg_advisory_xact_lock(hashtext('buildx:block:' || least(v_caller, target_user_id)::text
                                            || greatest(v_caller, target_user_id)::text));

    insert into public.blocks (blocker_id, blocked_id)
    values (v_caller, target_user_id)
    on conflict do nothing;

    delete from public.follows
    where (follower_id = v_caller and following_id = target_user_id)
       or (follower_id = target_user_id and following_id = v_caller);

    delete from public.note_subscriptions
    where (subscriber_id = v_caller and author_id = target_user_id)
       or (subscriber_id = target_user_id and author_id = v_caller);

    delete from public.notifications
    where (user_id = v_caller and actor_id = target_user_id)
       or (user_id = target_user_id and actor_id = v_caller);

    return jsonb_build_object('success', true, 'blocked', true);
end;
$$;

create function public.unblock_user(target_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    delete from public.blocks where blocker_id = v_caller and blocked_id = target_user_id;
    return jsonb_build_object('success', true, 'blocked', false);
end;
$$;

create function public.get_blocked_users(p_limit integer default 30, p_offset integer default 0)
returns table (id uuid, username text, display_name text, avatar_url text,
               is_verified boolean, blocked_at timestamptz)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select p.id, p.username::text, p.display_name, p.avatar_url, p.is_verified, b.created_at
    from public.blocks b
    join public.profiles p on p.id = b.blocked_id
    where b.blocker_id = auth.uid()
    order by b.created_at desc
    limit least(greatest(coalesce(p_limit, 30), 1), 100)
    offset greatest(coalesce(p_offset, 0), 0);
$$;

create function public.submit_report(p_target uuid, p_reason text, p_details text default null)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_id uuid;
    v_recent int;
begin
    if v_caller is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if v_caller = p_target then
        return jsonb_build_object('success', false, 'error', 'SELF');
    end if;
    if not exists (select 1 from public.profiles where id = p_target) then
        return jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    end if;
    if p_reason not in ('spam','impersonation','fake_account',
                        'inappropriate_content','harassment','other') then
        return jsonb_build_object('success', false, 'error', 'INVALID_REASON');
    end if;
    if p_details is not null and char_length(p_details) > 500 then
        return jsonb_build_object('success', false, 'error', 'DETAILS_TOO_LONG');
    end if;

    perform pg_advisory_xact_lock(hashtext('buildx:report:' || v_caller::text));

    if exists (select 1 from public.reports r
               where r.reporter_id = v_caller and r.target_id = p_target
                 and r.status = 'open' and r.created_at > now() - interval '24 hours') then
        return jsonb_build_object('success', false, 'error', 'ALREADY_REPORTED');
    end if;
    select count(*) into v_recent from public.reports
    where reporter_id = v_caller and created_at > now() - interval '24 hours';
    if v_recent >= 20 then
        return jsonb_build_object('success', false, 'error', 'RATE_LIMITED');
    end if;

    insert into public.reports (reporter_id, target_id, reason, details)
    values (v_caller, p_target, p_reason, nullif(trim(coalesce(p_details, '')), ''))
    returning id into v_id;

    return jsonb_build_object('success', true, 'report_id', v_id);
end;
$$;

create function public.resolve_report(p_report_id uuid, p_action text, p_note text default null)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if p_action not in ('resolved','dismissed') then
        return jsonb_build_object('success', false, 'error', 'INVALID_ACTION');
    end if;
    if p_note is not null and char_length(p_note) > 500 then
        return jsonb_build_object('success', false, 'error', 'NOTE_TOO_LONG');
    end if;
    update public.reports
    set status = p_action, resolved_at = now(), resolved_by = auth.uid(),
        resolution_note = nullif(trim(coalesce(p_note, '')), '')
    where id = p_report_id and status = 'open';
    if not found then
        return jsonb_build_object('success', false, 'error', 'NOT_FOUND');
    end if;
    return jsonb_build_object('success', true, 'status', p_action);
end;
$$;

create function public.get_account_info(target_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_p record;
    v_changes int;
    v_follows_you boolean := false;
    v_following_since timestamptz := null;
begin
    if v_caller is null then
        return null;
    end if;
    if public._are_blocked(v_caller, target_user_id) then
        return null;
    end if;

    select id, created_at, country_code, is_verified, verified_at
    into v_p from public.profiles where id = target_user_id;
    if not found then
        return null;
    end if;

    select count(*) into v_changes
    from public.profile_audit_log l
    where l.user_id = target_user_id and l.field_changed = 'username';

    if v_caller <> target_user_id then
        select exists (select 1 from public.follows f
                       where f.follower_id = target_user_id and f.following_id = v_caller)
        into v_follows_you;
        select f.created_at into v_following_since
        from public.follows f
        where f.follower_id = v_caller and f.following_id = target_user_id;
    end if;

    return jsonb_build_object(
        'joined_at', v_p.created_at,
        'country_code', v_p.country_code,
        'verified_at', case when v_p.is_verified then v_p.verified_at else null end,
        'username_change_count', coalesce(v_changes, 0),
        'follows_you', v_follows_you,
        'following_since', v_following_since,
        'is_self', v_caller = target_user_id);
end;
$$;

create function public.get_my_verification_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_p record;
    v_r record;
begin
    if v_user is null then
        return null;
    end if;
    select is_verified, verified_at into v_p from public.profiles where id = v_user;
    select id, status, reason, full_name, category, links, created_at, reviewed_at, decision_reason
    into v_r from public.verification_requests
    where user_id = v_user and status in ('pending','approved','rejected')
    order by created_at desc limit 1;

    return jsonb_build_object(
        'is_verified', coalesce(v_p.is_verified, false),
        'verified_at', v_p.verified_at,
        'has_request', v_r.id is not null,
        'request_id', v_r.id,
        'status', v_r.status,
        'reason', v_r.reason,
        'full_name', v_r.full_name,
        'category', v_r.category,
        'links', v_r.links,
        'created_at', v_r.created_at,
        'decision_reason', v_r.decision_reason,
        'reviewed_at', v_r.reviewed_at);
end;
$$;

create function public.request_verification(p_full_name text, p_category text,
                                            p_links text, p_reason text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_id uuid;
begin
    if v_user is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if char_length(trim(coalesce(p_full_name, ''))) < 2
       or char_length(trim(coalesce(p_reason, ''))) < 10 then
        return jsonb_build_object('success', false, 'error', 'INVALID_INPUT');
    end if;
    if exists (select 1 from public.verification_requests
               where user_id = v_user and status = 'pending') then
        return jsonb_build_object('success', false, 'error', 'ALREADY_PENDING');
    end if;
    if exists (select 1 from public.profiles where id = v_user and is_verified) then
        return jsonb_build_object('success', false, 'error', 'ALREADY_VERIFIED');
    end if;

    insert into public.verification_requests (user_id, full_name, category, links, reason)
    values (v_user, left(trim(p_full_name), 80), left(trim(p_category), 40),
            nullif(trim(coalesce(p_links, '')), ''), left(trim(p_reason), 1000))
    returning id into v_id;

    return jsonb_build_object('success', true, 'request_id', v_id);
end;
$$;

create function public.withdraw_verification_request(p_request_id uuid default null)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_id uuid;
begin
    if v_user is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    update public.verification_requests
    set status = 'withdrawn', reviewed_at = now()
    where user_id = v_user and status = 'pending'
      and (p_request_id is null or id = p_request_id)
    returning id into v_id;
    return jsonb_build_object('success', v_id is not null);
end;
$$;

create function public.set_verified(target_username text, verified_status boolean,
                                    reason text default null)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_id uuid;
begin
    select id into v_id from public.profiles where username = lower(trim(target_username))::citext;
    if v_id is null then
        return jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    end if;
    update public.profiles
    set is_verified = verified_status,
        verified_at = case when verified_status then coalesce(verified_at, now()) else null end
    where id = v_id;
    insert into public.verification_events (user_id, action, reason)
    values (v_id, case when verified_status then 'approved' else 'revoked' end, reason);
    return jsonb_build_object('success', true);
end;
$$;

create function public.get_login_email(p_identifier text)
returns text
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select u.email
    from public.profiles p
    join auth.users u on u.id = p.id
    where p.username = lower(trim(p_identifier))::citext
    limit 1;
$$;

create function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user uuid := auth.uid();
begin
    if v_user is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    -- Remove storage metadata rows first (the client also removes the objects
    -- through the Storage API before calling this RPC).
    delete from storage.objects
    where bucket_id in ('avatars','attachments')
      and (storage.foldername(name))[1] = v_user::text;
    -- Cascades profiles, follows, notes, notifications, blocks, reports.
    delete from auth.users where id = v_user;
end;
$$;

create function public.edit_user_message(p_message_id uuid, p_new_content text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_clean text := trim(p_new_content);
begin
    if v_user is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;
    if char_length(v_clean) < 1 or char_length(v_clean) > 8000 then
        return jsonb_build_object('success', false, 'error', 'INVALID_LENGTH');
    end if;
    update public.messages m
    set content = v_clean, edited_at = now()
    from public.conversations c
    where m.id = p_message_id and c.id = m.conversation_id
      and c.user_id = v_user and m.role = 'user';
    if not found then
        return jsonb_build_object('success', false, 'error', 'NOT_FOUND');
    end if;
    return jsonb_build_object('success', true);
end;
$$;

create function public.reconcile_follow_counts(p_user_id uuid default null)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.profiles p
    set followers_count = (select count(*) from public.follows f where f.following_id = p.id),
        following_count = (select count(*) from public.follows f where f.follower_id = p.id)
    where p_user_id is null or p.id = p_user_id;
end;
$$;

create function public.purge_expired_notes()
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    update public.notes set is_active = false
    where is_active and expires_at <= now();
$$;

create function public.purge_follow_rate_events()
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    delete from public.follow_rate_events where created_at < now() - interval '2 days';
$$;

-- ---------------------------------------------------------------------------
-- 7. Triggers
-- ---------------------------------------------------------------------------
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

create trigger trg_profiles_updated_at
    before update on public.profiles
    for each row execute function public.set_profiles_updated_at();

create trigger trg_enforce_username_rules
    before insert or update of username on public.profiles
    for each row execute function public.enforce_profile_username_rules();

create trigger trg_profile_audit_username
    after update of username on public.profiles
    for each row execute function public.audit_username_change();

create trigger trg_follow_counts
    after insert or delete on public.follows
    for each row execute function public.handle_follow_counts();

create trigger trg_follow_notification
    after insert on public.follows
    for each row execute function public.handle_follow_notification();

create trigger trg_note_notifications
    after insert on public.notes
    for each row execute function public.handle_note_notifications();

-- ---------------------------------------------------------------------------
-- 8. Row Level Security
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.follows enable row level security;
alter table public.notes enable row level security;
alter table public.note_subscriptions enable row level security;
alter table public.notifications enable row level security;
alter table public.profile_audit_log enable row level security;
alter table public.reserved_usernames enable row level security;
alter table public.follow_rate_events enable row level security;
alter table public.blocks enable row level security;
alter table public.reports enable row level security;
alter table public.verification_requests enable row level security;
alter table public.verification_events enable row level security;

create policy profiles_select_own on public.profiles
    for select to authenticated using ((select auth.uid()) = id);
create policy profiles_insert_own on public.profiles
    for insert to authenticated with check ((select auth.uid()) = id);
create policy profiles_update_own on public.profiles
    for update to authenticated
    using ((select auth.uid()) = id)
    with check ((select auth.uid()) = id);

create policy notifications_select_own on public.notifications
    for select to authenticated using ((select auth.uid()) = user_id);
create policy notifications_update_own on public.notifications
    for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

create policy note_subscriptions_select_own on public.note_subscriptions
    for select to authenticated using ((select auth.uid()) = subscriber_id);

create policy audit_log_select_own on public.profile_audit_log
    for select to authenticated using ((select auth.uid()) = user_id);

create policy blocks_select_own on public.blocks
    for select to authenticated using ((select auth.uid()) = blocker_id);

create policy reports_select_own on public.reports
    for select to authenticated using ((select auth.uid()) = reporter_id);

create policy verification_requests_select_own on public.verification_requests
    for select to authenticated using ((select auth.uid()) = user_id);

create policy verification_events_select_own on public.verification_events
    for select to authenticated using ((select auth.uid()) = user_id);

-- Explicit deny policies: these tables are RPC-only. Resolves advisor lint 0008
-- and guarantees no direct client access even if a grant is added later.
create policy follows_no_direct_access on public.follows
    for all to public using (false) with check (false);
create policy notes_no_direct_access on public.notes
    for all to public using (false) with check (false);
create policy reserved_usernames_no_direct_access on public.reserved_usernames
    for all to public using (false) with check (false);
create policy follow_rate_events_no_direct_access on public.follow_rate_events
    for all to public using (false) with check (false);

-- ---------------------------------------------------------------------------
-- 9. Least-privilege grants
-- ---------------------------------------------------------------------------
revoke all on public.profiles, public.follows, public.notes, public.note_subscriptions,
    public.notifications, public.profile_audit_log, public.reserved_usernames,
    public.follow_rate_events, public.blocks, public.reports,
    public.verification_requests, public.verification_events
from public, anon, authenticated;

grant select on public.profiles to authenticated;
grant insert on public.profiles to authenticated;
grant update (display_name, bio, avatar_url, country_code, theme_mode, theme_palette,
              locale, updated_at) on public.profiles to authenticated;
grant select on public.notifications to authenticated;
grant update (is_read) on public.notifications to authenticated;
grant select on public.note_subscriptions to authenticated;
grant select on public.profile_audit_log to authenticated;
grant select on public.blocks to authenticated;
grant select on public.reports to authenticated;
grant select on public.verification_requests to authenticated;
grant select on public.verification_events to authenticated;

revoke all on function public._are_blocked(uuid, uuid) from public, anon, authenticated;
revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.set_profiles_updated_at() from public, anon, authenticated;
revoke all on function public.enforce_profile_username_rules() from public, anon, authenticated;
revoke all on function public.audit_username_change() from public, anon, authenticated;
revoke all on function public.handle_follow_counts() from public, anon, authenticated;
revoke all on function public.handle_follow_notification() from public, anon, authenticated;
revoke all on function public.handle_note_notifications() from public, anon, authenticated;

revoke all on function public.check_username_available(text) from public, anon, authenticated;
revoke all on function public.change_username(text) from public, anon, authenticated;
revoke all on function public.mark_username_reviewed() from public, anon, authenticated;
revoke all on function public.get_public_profile(text) from public, anon, authenticated;
revoke all on function public.search_people(text, int, int) from public, anon, authenticated;
revoke all on function public.toggle_follow(uuid) from public, anon, authenticated;
revoke all on function public.get_user_followers(uuid) from public, anon, authenticated;
revoke all on function public.get_user_following(uuid) from public, anon, authenticated;
revoke all on function public.get_user_notifications(int, int) from public, anon, authenticated;
revoke all on function public.create_note(text) from public, anon, authenticated;
revoke all on function public.delete_note() from public, anon, authenticated;
revoke all on function public.get_my_active_note() from public, anon, authenticated;
revoke all on function public.toggle_note_subscription(uuid) from public, anon, authenticated;
revoke all on function public.block_user(uuid) from public, anon, authenticated;
revoke all on function public.unblock_user(uuid) from public, anon, authenticated;
revoke all on function public.get_blocked_users(int, int) from public, anon, authenticated;
revoke all on function public.submit_report(uuid, text, text) from public, anon, authenticated;
revoke all on function public.get_account_info(uuid) from public, anon, authenticated;
revoke all on function public.get_my_verification_status() from public, anon, authenticated;
revoke all on function public.request_verification(text, text, text, text) from public, anon, authenticated;
revoke all on function public.withdraw_verification_request(uuid) from public, anon, authenticated;
revoke all on function public.delete_my_account() from public, anon, authenticated;
revoke all on function public.edit_user_message(uuid, text) from public, anon, authenticated;

grant execute on function public.check_username_available(text) to anon, authenticated;
grant execute on function public.get_public_profile(text) to anon, authenticated;
grant execute on function public.get_login_email(text) to anon, authenticated;
grant execute on function public.change_username(text) to authenticated;
grant execute on function public.mark_username_reviewed() to authenticated;
grant execute on function public.search_people(text, int, int) to authenticated;
grant execute on function public.toggle_follow(uuid) to authenticated;
grant execute on function public.get_user_followers(uuid) to authenticated;
grant execute on function public.get_user_following(uuid) to authenticated;
grant execute on function public.get_user_notifications(int, int) to authenticated;
grant execute on function public.create_note(text) to authenticated;
grant execute on function public.delete_note() to authenticated;
grant execute on function public.get_my_active_note() to authenticated;
grant execute on function public.toggle_note_subscription(uuid) to authenticated;
grant execute on function public.block_user(uuid) to authenticated;
grant execute on function public.unblock_user(uuid) to authenticated;
grant execute on function public.get_blocked_users(int, int) to authenticated;
grant execute on function public.submit_report(uuid, text, text) to authenticated;
grant execute on function public.get_account_info(uuid) to authenticated;
grant execute on function public.get_my_verification_status() to authenticated;
grant execute on function public.request_verification(text, text, text, text) to authenticated;
grant execute on function public.withdraw_verification_request(uuid) to authenticated;
grant execute on function public.delete_my_account() to authenticated;
grant execute on function public.edit_user_message(uuid, text) to authenticated;

grant execute on function public.set_verified(text, boolean, text) to service_role;
grant execute on function public.resolve_report(uuid, text, text) to service_role;
grant execute on function public.reconcile_follow_counts(uuid) to service_role;
grant execute on function public.purge_expired_notes() to service_role;
grant execute on function public.purge_follow_rate_events() to service_role;

-- ---------------------------------------------------------------------------
-- 10. Storage policies (avatars + attachments; other buckets untouched)
-- ---------------------------------------------------------------------------
do $$
declare r record;
begin
  for r in
    select policyname from pg_policies
    where schemaname = 'storage' and tablename = 'objects'
      and (policyname like '%avatars%' or policyname like '%attachments%')
  loop
    execute format('drop policy if exists %I on storage.objects', r.policyname);
  end loop;
end $$;

create policy avatars_public_read on storage.objects
    for select using (bucket_id = 'avatars');
create policy avatars_insert_own on storage.objects
    for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy avatars_update_own on storage.objects
    for update to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text)
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy avatars_delete_own on storage.objects
    for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy attachments_public_read on storage.objects
    for select using (bucket_id = 'attachments');
create policy attachments_insert_own on storage.objects
    for insert to authenticated
    with check (bucket_id = 'attachments' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy attachments_update_own on storage.objects
    for update to authenticated
    using (bucket_id = 'attachments' and (storage.foldername(name))[1] = (select auth.uid())::text)
    with check (bucket_id = 'attachments' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy attachments_delete_own on storage.objects
    for delete to authenticated
    using (bucket_id = 'attachments' and (storage.foldername(name))[1] = (select auth.uid())::text);

commit;
