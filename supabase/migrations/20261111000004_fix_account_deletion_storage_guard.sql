-- 20261111000004_fix_account_deletion_storage_guard.sql
--
-- Supabase now blocks direct SQL deletion from storage.objects for the
-- postgres role (trigger storage.protect_delete raises 42501 "Direct deletion
-- from storage tables is not allowed") unless the transaction-scoped GUC
-- storage.allow_delete_query is set to 'true'. delete_my_account deletes the
-- account's own storage metadata rows before deleting auth.users, so it must
-- enable that flag for its own transaction only.
--
-- The flag is set with set_config(..., true) which scopes it to the current
-- transaction, so it never leaks into other statements or sessions.
--
-- Rollback:
--   recreate the previous version of public.delete_my_account() without the
--   set_config call (that version now fails on any account that still owns
--   storage objects).

create or replace function public.delete_my_account()
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
    -- Supabase's storage.protect_delete trigger refuses SQL deletions from
    -- storage.objects unless the backend opts in per transaction. Enable it
    -- only here, scoped to this transaction, to remove the leaving account's
    -- own storage metadata rows. (The client removes the actual objects
    -- through the Storage API first; this covers anything left behind.)
    perform set_config('storage.allow_delete_query', 'true', true);
    delete from storage.objects
    where bucket_id in ('avatars','attachments')
      and (storage.foldername(name))[1] = v_user::text;
    -- Cascades profiles, follows, notes, notifications, blocks, reports,
    -- verification requests and events.
    delete from auth.users where id = v_user;
end;
$$;

-- create or replace keeps the existing ACL, but re-assert it defensively:
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
