-- 20261111000005_index_resolved_by_fks.sql
--
-- Adds covering indexes for the four admin-only foreign keys flagged by the
-- Supabase performance advisor (lint 0001 unindexed_foreign_keys). All four
-- columns are populated only by privileged review flows, so these indexes are
-- small and exist to keep administrative queries and advisor output clean.
--
-- Rollback:
--   drop index public.profile_audit_log_changed_by_idx;
--   drop index public.reports_resolved_by_idx;
--   drop index public.verification_events_actor_idx;
--   drop index public.verification_requests_reviewed_by_idx;

create index if not exists profile_audit_log_changed_by_idx
    on public.profile_audit_log (changed_by);
create index if not exists reports_resolved_by_idx
    on public.reports (resolved_by);
create index if not exists verification_events_actor_idx
    on public.verification_events (actor_id);
create index if not exists verification_requests_reviewed_by_idx
    on public.verification_requests (reviewed_by);
