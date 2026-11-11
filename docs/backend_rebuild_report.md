# Build X Backend Rebuild — Final Report

Date: 2026-11-11 · Project: `owgpuolvfqfjhezuohyf` · Scope: profile/social backend rebuild, hardening, profile v2 UI, Google sign-in redirect fix.

---

## 1. What was deleted and recreated

With the owner's explicit authorization, **all 109 `auth.users` rows** and all app data for them
(70 users without profiles included) were permanently deleted — no backup of user records was
kept. Storage objects in `avatars`/`attachments` and all rows in the profile/social tables were
removed. A **schema-only** snapshot `backup_schema_20261111` was created; the pre-existing
backup schemas (`backup_20261110`, `backup_profile_20261110`, `backup_20261111`) were preserved
untouched and are never dropped. Shared resources (`work_*`, `assistants`, `workspaces`,
`integrations`, `conversations`/`messages`/`attachments`, `workspace_screenshots` bucket) were
left as-is.

The profile/social backend was then rebuilt from scratch in
`supabase/migrations/20261111000002_clean_rebuild.sql`, applied to the live project, and
recorded in `supabase_migrations.schema_migrations` together with all earlier repo migrations.

Post-test database state (verified by direct query): **1 `auth.users` row** (`sh***@gmail.com`,
created by the owner at 11:58 UTC, after the wipe — intentionally not touched), 1 profile,
1 note owned by that user, 0 follows / notifications / blocks / reports / storage objects.

---

## 2. Schema / ER

```
auth.users (id PK)
  └─1:1─ profiles            id uuid PK, FK→auth.users ON DELETE CASCADE
            │                 handle (text, trigger-synced to username), display_name, bio,
            │                 avatar_url, country_code, theme_mode, theme_palette, locale,
            │                 username, is_verified, verified_at, followers_count, following_count
            ├─1:N─ follows             follower_id, following_id → profiles, PK pair, no_self check
            ├─1:N─ notes               user_id → profiles, unique active note per user, 24h expiry
            ├─1:N─ note_subscriptions  subscriber_id, author_id → profiles
            ├─1:N─ notifications       user_id, actor_id → profiles, no_self check
            ├─1:N─ profile_audit_log   user_id → profiles, changed_by → profiles ON DELETE SET NULL
            ├─1:N─ blocks              blocker_id, blocked_id → profiles, PK pair, no_self check
            ├─1:N─ reports             reporter_id, target_id → profiles;
            │                          resolved_by → auth.users ON DELETE SET NULL
            ├─1:N─ verification_requests user_id → profiles; reviewed_by → auth.users SET NULL
            ├─1:N─ verification_events   user_id → profiles; actor_id → auth.users SET NULL
            └─  reserved_usernames, follow_rate_events (rate-limit ledger, no FK to profiles)
```

- `auth.users` deletion cascades to `profiles`, which cascades to every social row — one delete
  removes an account completely. `delete_my_account()` deletes storage metadata rows first,
  then `auth.users`.
- `handle` is a plain `text NOT NULL` column (the client serializes it); a BEFORE
  INSERT/UPDATE trigger keeps it synced with `username` and forces privileged fields
  (`is_verified`, counts) on insert.
- `citext` (case-insensitive usernames) and `pg_trgm` (fuzzy search) extensions live in `public`
  — deliberate, existing columns/operators depend on them.

## 3. All SQL (repo migrations) and rollback

Applied in order; every file lives in `supabase/migrations/`:

| Migration | Purpose | Rollback |
|---|---|---|
| `20261111000002_clean_rebuild.sql` | Full rebuild: tables, RLS, grants, RPCs, triggers, storage policies | Not rolled back (rebuild baseline); schema preserved in `backup_schema_20261111` |
| `20261111000003_harden_privileged_rpcs.sql` | Removes PUBLIC/anon/authenticated EXECUTE from 5 privileged functions + PUBLIC from 3 user RPCs; grants explicit roles | `grant execute … to public` for each (insecure, not recommended) — listed in file header |
| `20261111000004_fix_account_deletion_storage_guard.sql` | `delete_my_account` sets transaction-scoped `storage.allow_delete_query=true` before deleting storage metadata (Supabase's new `storage.protect_delete` guard blocks postgres-role SQL deletes) | Recreate previous version without `set_config` — header lists it; that version now fails when the account owns storage rows |
| `20261111000005_index_resolved_by_fks.sql` | 4 covering indexes for advisor lint 0001 (admin-only FK columns) | `drop index` ×4 — listed in file header |

## 4. Secured RPC surface

All functions are `SECURITY DEFINER` with explicit `set search_path = public, pg_temp` and
explicit EXECUTE grants (no inherited PUBLIC):

| Function | Roles | Notes |
|---|---|---|
| `get_public_profile` | anon, authenticated | Block-aware; returns single jsonb object or NULL |
| `search_people` | authenticated | Block-aware fuzzy search |
| `toggle_follow` | authenticated | Optimistic-friendly counts, self/block/rate checks |
| `get_user_followers` / `get_user_following` | authenticated | Block-aware lists |
| `create_note` / `delete_note` / `get_my_active_note` / `toggle_note_subscription` | authenticated | 60-char limit, 24h expiry, notification fan-out with block checks |
| `get_user_notifications` | authenticated | Own rows only |
| `change_username` / `mark_username_reviewed` | authenticated | Reserved names, audit log |
| `request_verification` / `withdraw_verification_request` / `get_my_verification_status` | authenticated | |
| `block_user` / `unblock_user` / `get_blocked_users` | authenticated | |
| `submit_report` | authenticated | Reporter identity private; duplicate + self-report rejected |
| `get_account_info` | authenticated | Real rows only, empty values omitted client-side |
| `delete_my_account` | authenticated | Storage cleanup + `auth.users` delete (cascade) |
| `check_username_available` | anon, authenticated | Signup UX |
| `get_login_email` | anon, authenticated | Username login resolves email pre-auth (residual risk, §9) |
| `set_locale` / `set_theme_mode` | authenticated | |
| `resolve_report` / `set_verified` / `review_verification_request` / `reconcile_follow_counts` / `purge_expired_notes` / `purge_follow_rate_events` | **service_role only** | Verified: `=X/postgres` PUBLIC grant removed |
| `edit_user_message`, `consume_work_usage` | authenticated (+service_role) | Legacy chat/work features |

Verified by catalog query after hardening: no app-owned function carries a PUBLIC EXECUTE grant;
remaining `=X` grants belong to `citext`/`pg_trgm` extension functions owned by
`supabase_admin` (must not be revoked — operators would break).

## 5. RLS and permissions

- RLS **enabled on every table** in the rebuild.
- **Owner-only read/write policies** (`(select auth.uid()) = owner column`) on `profiles`,
  `notifications`, `note_subscriptions`, `profile_audit_log`, `blocks`, `reports`,
  `verification_requests`, `verification_events`. All social reads/writes go through
  block-aware RPCs instead of direct table access.
- **Explicit deny-all policies** on `follows`, `notes`, `reserved_usernames`,
  `follow_rate_events` (RPC-only tables) — resolves advisor lint 0008 and blocks any future
  stray grant.
- `profiles` update grant restricted to `display_name, bio, avatar_url, country_code,
  theme_mode, theme_palette, locale, updated_at`; privileged fields are forced by trigger.
- Storage: `avatars` + `attachments` public-read (client uses `publicUrl`), insert/update/delete
  only into the caller's own `{uid}/` folder. Accepted risk noted in §9.
- Privileges revoked from `anon`/`authenticated` on everything except the RPC surface above;
  `service_role` retains full control for moderation flows (`resolve_report`, `set_verified`,
  `review_verification_request`).

## 6. Supabase advisor results (final run)

Security advisor: **no ERROR findings.** Remaining WARNs, all deliberate or out of scope:
- `authenticated_security_definer_function_executable` (27) + `anon_security_definer_function_executable` (3) — the RPC design itself; every function validates `auth.uid()` internally and grants are minimal.
- `extension_in_public` (citext, pg_trgm) — deliberate; moving them would break existing columns.
- `auth_allow_anonymous_sign_ins` — was enabled in auth config; **now disabled** (verified `external_anonymous_users_enabled=false`; the app has no anonymous auth usage — incognito mode is a client-side conversation flag).
- `auth_leaked_password_protection` — HIBP check unavailable on the free plan (plan limitation).
- Backup schemas' INFO/WARN findings — intentional, untouched backups.

Performance advisor: **no ERROR findings.** WARNs (`auth_rls_initplan`, `multiple_permissive_policies`) are all on legacy shared tables outside the rebuild scope (`conversations`, `messages`, `attachments`, `work_*`, `assistants`, `workspaces`, `integrations`) — none on rebuilt tables. The 4 in-scope INFO `unindexed_foreign_keys` lints were fixed with `20261111000005` indexes.

## 7. Client: profile v2, menus, sheets, backend integration

- Rewrote `ProfileScreen.kt` (44dp white header circles, `@username` LTR-safe title, 88dp
  avatar, note bubble in a custom `Layout` overlapping the avatar top-start by 8dp without
  squeezing the name/stats column, 15sp auto-linked expandable bio, joined meta, action rows),
  `ProfileSheets.kt` (three-dot sheets, about-this-account with empty-row hiding, 3-step
  report, block confirmation, unfollow popup, pill buttons), `BlockedAccountsScreen.kt`,
  `CountryPickerSheet.kt` (locale-derived ISO countries), Edit-profile country card.
- `BuildXViewModel` / `BuildXRepository`: profile v2 DTOs, RPC wiring, optimistic follow with
  rollback + double-tap guard, block/unblock, report (steps, 500-char cap, already-reported),
  account info, blocked-users pagination, country persistence.
- All new strings in `values/profile_v2_strings.xml` + `values-ar/` with unique `pv2_` prefix
  (English + Arabic); unused keys removed after lint flagged them (70 keys each locale, parity
  verified). No emoji/TODO/mock/raw-error text anywhere (static scans clean); every error path
  goes through `ErrorMapper`.
- New icons `ic_chat_bubble` (autoMirrored — directional tail), `ic_flag`, `ic_block`,
  `ic_calendar`, `ic_globe`, `ic_user_x`: 24dp, 1.75dp stroke, round caps/joins, tintable at
  usage, matching the existing `ic_back` pattern.

## 8. Verification evidence

- **Build:** `:app:assembleDebug` BUILD SUCCESSFUL.
- **Lint:** 0 errors, 204 warnings; **zero warnings in any profile-v2 file** (down from 213
  after removing the 9 unused `pv2_` strings; remaining warnings are pre-existing repo-wide
  items: UnusedResources in legacy resources, GradleDependency, TypographyEllipsis, etc.).
- **Unit + screenshot tests:** 108 tests, 0 failures — including the new 17-case
  `ProfileV2ScreenshotTest` (360/411dp × light/dark × LTR/RTL × font 1.3 × states) and the
  37-case `ScreenFidelityScreenshotTest` compare run. A pre-existing Roborazzi bug
  (`onRoot()` failing when a compose Popup adds a second window root) was fixed by capturing
  the largest root; the 4 previously failing A1/A2 message-menu captures now pass at clean
  HEAD too.
- **Screenshots:** `docs/design/screenshots/profile_v2/` — 17 PNGs, visually reviewed (own +
  visited profiles, skeleton, unavailable, blocked-by-me, blocked accounts, edit profile).
- **Two-account live workflow (REST via anon key, 54/54 PASS)** — script:
  `C:\Users\musta\AppData\Local\Temp\opencode\bx_twoway_test.ps1`. Both accounts were created
  and then **deleted themselves through `delete_my_account`** as the final step:
  - Signup auto-creates profile; `get_public_profile` by username (and for anon) works; missing
    user resolves NULL.
  - Profile update own-row only; cross-user PATCH rejected.
  - Follow/unfollow round-trip updates counts and both lists; self-follow rejected.
  - Notifications created on follow; RLS keeps each account to its own rows.
  - Notes: create, 61-char rejection, visibility on public profile, subscription, delete.
  - Block: blocks → target's view becomes NULL (blocked-by-them), blocked list correct, own-rows
    RLS on `blocks`, self-block rejected; unblock restores visibility.
  - Reports: submit, duplicate/self/invalid-reason rejected, reporter sees own report, reported
    user and anon see none (reporter identity private).
  - `get_account_info` returns real rows only.
  - Direct-table RLS matrix: `profiles` own-row only; `follows`/`notes` direct access denied;
    foreign-key DELETE via REST cannot remove another user's follow edge.
  - Storage: own-folder upload OK, other-user folder rejected, public read OK, own delete OK.
  - Username change + resolution; account deletion for both accounts (verified clean DB after).

## 9. Remaining / unverified issues

1. **Google sign-in on a real device — UNVERIFIED.** Config is correct and verified server-side
   (`site_url=app.buildx.chat://login-callback`, allow-list
   `app.buildx.chat://login-callback,buildx://,psyche.kelivo://auth-callback`, Google enabled).
   Browser-OAuth path needs a manual device test: build, tap "Continue with Google", confirm
   the browser opens and the `app.buildx.chat://login-callback` redirect lands back in the app.
2. **HIBP leaked-password protection** unavailable on the free plan (reported limitation).
3. **`get_login_email` exposes the email for a username** — required for username login before
   auth; accepted residual privacy risk (documented).
4. **Public-read storage buckets** — required by the client's `publicUrl` usage; object names
   are folder-scoped by uid. Accepted risk (documented).
5. **`SUPABASE_ANON_KEY` is committed** in `app/build.gradle.kts` — public-by-design for the
   client, but the key was also echoed to this session's terminal output once during testing
   (it contains no privileges beyond anon RLS). Optional hardening: move it out of the repo and
   rotate the Google provider secret if desired.
6. **Owner's own account** (`sh***@gmail.com`, created 11:58 UTC today) was left untouched —
   created after the wipe, not one of the test accounts.
7. Legacy WARN advisor findings on shared tables (conversations/messages/work_*) are outside
   the rebuild scope; recommend a follow-up pass if desired.
