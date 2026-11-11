-- 20261111000003_harden_privileged_rpcs.sql
--
-- Privileged maintenance and moderation routines must never be callable by
-- end users. The rebuild migration granted EXECUTE explicitly to service_role
-- but left the default PUBLIC grant in place, which made every one of these
-- functions reachable from the anon and authenticated PostgREST roles.
--
-- This migration removes the inherited PUBLIC grant (and any explicit anon /
-- authenticated grant) and re-grants only service_role.
--
-- Rollback:
--   grant execute on function public.resolve_report(uuid, text, text) to public;
--   grant execute on function public.set_verified(text, boolean, text) to public;
--   grant execute on function public.reconcile_follow_counts(uuid) to public;
--   grant execute on function public.purge_expired_notes() to public;
--   grant execute on function public.purge_follow_rate_events() to public;
--   (restoring the insecure state is not recommended)

begin;

revoke all on function public.resolve_report(uuid, text, text)
    from public, anon, authenticated;
revoke all on function public.set_verified(text, boolean, text)
    from public, anon, authenticated;
revoke all on function public.reconcile_follow_counts(uuid)
    from public, anon, authenticated;
revoke all on function public.purge_expired_notes()
    from public, anon, authenticated;
revoke all on function public.purge_follow_rate_events()
    from public, anon, authenticated;

-- These three are legitimate user-facing RPCs, but they still carried the
-- inherited PUBLIC grant. Drop it and keep only the explicit roles that need
-- them. get_login_email stays callable by anon because username sign-in
-- resolves the email before a session exists.
revoke all on function public.get_login_email(text) from public;
revoke all on function public.set_locale(text) from public;
revoke all on function public.set_theme_mode(text) from public;

grant execute on function public.resolve_report(uuid, text, text) to service_role;
grant execute on function public.set_verified(text, boolean, text) to service_role;
grant execute on function public.reconcile_follow_counts(uuid) to service_role;
grant execute on function public.purge_expired_notes() to service_role;
grant execute on function public.purge_follow_rate_events() to service_role;

grant execute on function public.get_login_email(text) to anon, authenticated;
grant execute on function public.set_locale(text) to authenticated;
grant execute on function public.set_theme_mode(text) to authenticated;

commit;
