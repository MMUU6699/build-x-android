# AGENT 1 — Backend & API Health Specification (Messaging / Chat System)

- **Role:** AGENT 1 (`@sql-auditor`) — SQL, Permissions & API Telemetry Auditor
- **Scope:** Strictly Messaging / Chat System (Direct Messages, Group Chats, Public AI Chat, Read Receipts, Pins, Mutes, Note Likes/Replies, Notifications, Realtime Subscriptions, Chat Wallpapers, Push Token Registration). Profile & Settings screens were excluded per guardrails.
- **Target Project:** `https://owgpuolvfqfjhezuohyf.supabase.co` (Supabase project ref `owgpuolvfqfjhezuohyf`, verified in `app/build.gradle.kts#L25` and `docs/backend_rebuild_report.md#L3`).
- **Artifacts Produced:**
  - Migration (static SQL deliverable, pending live DB apply): `supabase/migrations/20261114000001_messaging_audit_hardening.sql` (1,617 lines)
  - Specification: `docs/AGENT1_BACKEND_HEALTH_SPEC.md`
  - **Zero Kotlin files modified** (all Android-side changes are specified in Section 7 for `@chat-logic`).

---

## 1. Live Database / MCP Telemetry Status & Guardrails

| Check | Status | Notes |
| --- | --- | --- |
| Static SQL Audit (`20261109..20261113`) | **Completed** | Audited all messaging migrations, `supabase/functions/chat/index.ts`, `Models.kt`, `BuildXRepository.kt`, `BuildXViewModel.kt`, `ChatMessageMapper.kt`, `ErrorMapper.kt`. |
| Supabase MCP (`list_projects`, `list_tables`, `list_migrations`, `execute_sql`, `get_advisors`, `query_logs`) | **Not Executed (Tooling Gap)** | Supabase MCP tools are not bound in the `@sql-auditor` worker runtime, and the `supabase` CLI is not installed locally. |
| Live Migration Application (`20261114000001`) | **Pending `@coordinator`** | `@coordinator` must apply `supabase/migrations/20261114000001_messaging_audit_hardening.sql` via Supabase MCP and run the Section 8 verification queries. |

> [!IMPORTANT]
> Because live MCP execution was unavailable in this worker environment, no live advisor metrics, live Postgres error logs, or live row counts are claimed below. Every finding in Sections 2–7 is directly verified from repository source files and SQL migrations.

---

## 2. Messaging Schema Inventory & Structural Findings

### 2.1 Table Mapping (including Locks & Pins)
There are **no** separate `public.conversation_locks` or `public.message_pins` tables in the schema:
- **Locks:** Stored as `public.conversations.is_locked` (`boolean`, added in `supabase/migrations/20261111000006_conversation_lock.sql`).
- **Pins:**
  - **AI Chat conversation pins:** `public.conversations.pinned` (`20261109000001_buildx_schema.sql`) and **`public.conversations.is_pinned`** (expected by `BuildXRepository.togglePinConversation` at `L1175–L1178`, added + trigger-synced in `20261114000001` Section 1).
  - **DM/Group conversation pins (up to 3 active/request chats per user):** `public.dm_participants.is_pinned` and `public.dm_participants.pinned_at`.
  - **In-conversation pinned message:** `public.dm_conversations.pinned_message_id` (`REFERENCES public.dm_messages(id)`).

### 2.2 Schema & Constraint Defects Found
1. **Missing `public.conversations.is_pinned` column:**
   - `BuildXRepository.togglePinConversation` (`L1172–L1184`) sends `put("pinned", pinned)` and `put("is_pinned", pinned)`, and `Models.kt` `Conversation` (`L194–L195`) models both `pinned` and `is_pinned`.
   - `20261109000001_buildx_schema.sql` only defined `pinned`, causing `PGRST204` (`Could not find the 'is_pinned' column`) or silent update failure unless already patched manually on live DB.
   - **Fix (`20261114000001` L14–L46):** Added `conversations.is_pinned boolean NOT NULL DEFAULT false`, backfilled from `pinned`, and added `trg_conversations_sync_pin()` so `pinned` and `is_pinned` remain synchronized in both directions.
2. **Legacy `dm_participants_status_check` missing `'hidden'`:**
   - `delete_conversation_for_me` (`20261112000006`) sets `dm_participants.status = 'hidden'`.
   - **Fix (`20261114000001` L48–L53):** Explicitly enforces `CHECK (status IN ('active', 'request', 'left', 'hidden'))` idempotently.
3. **Deleted pinned message dangling reference:**
   - `delete_dm_message` (`20261112000001`) soft-deleted `dm_messages.deleted_at` without clearing `dm_conversations.pinned_message_id` when the deleted message was currently pinned.
   - **Fix (`20261114000001` L1353–L1390):** `delete_dm_message` now clears `pinned_message_id = NULL` on `dm_conversations` if `pinned_message_id = p_message_id`.
4. **Unused broken legacy RPC `leave_or_delete_dm_conversation`:**
   - Defined in `20261112000004_dm_inbox_enhancements.sql`; attempts to `SET updated_at = now()` on `dm_participants`, which has no `updated_at` column (`20261112000001`).
   - Current Kotlin (`BuildXRepository.kt` `L2168`) calls `leave_group` / `delete_conversation_for_me` (`20261112000006`) instead. Hardened `REVOKE PUBLIC, anon` in `20261114000001` L1587; legacy clients should never call it.

---

## 3. RLS Policies, Grants, Function ACLs & Realtime Matrix

### 3.1 Table RLS, Grants & Realtime Status

| Table | RLS Policy Pattern | Grants (`authenticated`) | Realtime (`supabase_realtime`) | Notes & Hardening in `20261114000001` |
| --- | --- | --- | --- | --- |
| `public.conversations` | Own `user_id = (SELECT auth.uid())` (S/I/U/D) | Owner CRUD | Yes (`20261109000001`) | Replaced bare `auth.uid()` with `(SELECT auth.uid())` to eliminate `auth_rls_initplan` WARN (`L88–L107`). |
| `public.messages` | Owned conversation via `(SELECT auth.uid())` (S/I/U/D) | Owner CRUD | Yes (`20261109000001`) | Replaced bare `auth.uid()` with `(SELECT auth.uid())` (`L109–L153`). |
| `public.attachments` | Own `user_id` / conv owner via `(SELECT auth.uid())` (S/I/U/D) | Owner CRUD | No | Replaced bare `auth.uid()` with `(SELECT auth.uid())` (`L155–L180`). |
| `public.dm_conversations` | `dm_conversations_select`: `_is_dm_participant(id, (SELECT auth.uid()))` | **`SELECT` only** (all writes via `SECURITY DEFINER` RPCs) | Yes + `REPLICA IDENTITY FULL` | Revoked all direct table privileges from `PUBLIC, anon, authenticated`, then granted `SELECT` only (`L183–L207`). |
| `public.dm_participants` | `dm_participants_select` + `dm_participants_update_own_wallpaper` | **`SELECT` + column-level `UPDATE (wallpaper_id, wallpaper_custom_url, wallpaper_dim, wallpaper_blur)`** | Yes + `REPLICA IDENTITY FULL` | Trigger `trg_guard_dm_participants_direct_update` (`20261113000003`) blocks direct mutation of non-wallpaper columns (`status`, `last_read_at`, `is_pinned`, etc.). |
| `public.dm_messages` | `dm_messages_select`: `_is_dm_participant(conversation_id, (SELECT auth.uid()))` | **`SELECT` only** | Yes + `REPLICA IDENTITY FULL` | Revoked direct `INSERT/UPDATE/DELETE` from `authenticated`; all message writes go through RPCs (`L193–L207`). |
| `public.notifications` | User's own `user_id` (`20261113000003`/`05`) | Client read / read-mark | Yes + `REPLICA IDENTITY FULL` | `REPLICA IDENTITY FULL` reinforced (`L215`). |
| `public.note_likes` | Followed / active note access (`20261113000003`) | `SELECT, INSERT, DELETE` | N/A | `REVOKE ALL FROM PUBLIC, anon` already applied in `20261113000003` (`L31–L32`). |
| `public.user_push_tokens` | Own `user_id` (`20261113000005` `L424`) | `SELECT, INSERT, UPDATE, DELETE` | No | `register_push_token` RPC hardened with `REVOKE ... FROM PUBLIC, anon` (`L1611–L1612`). |

### 3.2 Function ACL Hardening (`PUBLIC` / `anon` Exposure Fix)
PostgreSQL grants `EXECUTE` on newly created functions to `PUBLIC` (which includes `anon`) by default. Prior migrations (`20261112000001`, `20261112000004`, `20261112000005`, `20261112000006`, `20261113000005`) omitted `REVOKE ALL ON FUNCTION ... FROM PUBLIC, anon;` on 21 messaging/trigger/push functions (including `get_dm_inbox`, `get_dm_messages`, `send_dm_message`, `set_conversation_pin`, `set_conversation_mute`, `mark_conversation_unread`, `delete_conversation_for_me`, `leave_group`, `register_push_token`).

**Fix (`20261114000001` Section 7, `L1537–L1613`):**
- Trigger functions (`trg_conversations_sync_pin`, `trg_dm_messages_sync_last_message`) revoked from `PUBLIC, anon, authenticated`.
- All 21 DM/messaging/push RPCs explicitly have `REVOKE ALL ON FUNCTION ... FROM PUBLIC, anon;` followed by `GRANT EXECUTE ... TO authenticated` (and `service_role` on `edit_user_message`).

---

## 4. Covering Indexes & Query Performance (`20261114000001` Section 2)

Seven missing covering/partial indexes added in `20261114000001` (`L58–L84`):
1. `idx_dm_conversations_last_message_id` on `dm_conversations (last_message_id) WHERE last_message_id IS NOT NULL`
2. `idx_dm_conversations_pinned_message_id` on `dm_conversations (pinned_message_id) WHERE pinned_message_id IS NOT NULL`
3. `idx_dm_conversations_last_sender` on `dm_conversations (last_message_sender_id) WHERE last_message_sender_id IS NOT NULL`
4. `idx_dm_messages_reply_to_msg` on `dm_messages (reply_to_message_id) WHERE reply_to_message_id IS NOT NULL`
5. `idx_dm_messages_reply_to_note` on `dm_messages (reply_to_note_id) WHERE reply_to_note_id IS NOT NULL`
6. `idx_dm_messages_conv_unread` on `dm_messages (conversation_id, created_at) WHERE deleted_at IS NULL` — accelerates unread counting & `cleared_before` filtering in `get_dm_inbox` / `get_dm_messages`.
7. `idx_messages_conv_role_status_created` on `messages (conversation_id, role, status, created_at DESC)` — accelerates AI chat unread/history lookups.

---

## 5. RPC Contract & Payload Verification (`Models.kt` & `BuildXRepository.kt` vs SQL)

Every messaging RPC was cross-checked against `app/src/main/java/app/buildx/chat/data/model/Models.kt` and `app/src/main/java/app/buildx/chat/data/repository/BuildXRepository.kt`:

### 5.1 Critical Bugs Fixed in `20261114000001_messaging_audit_hardening.sql`

1. **`get_dm_conversation_detail` — Deserialization Crash on Pinned Message (`L835–L987`)**
   - **Bug (`20261113000003` L766–L778):** `pinned_message` JSON omitted `conversation_id`, whereas `DmMessage.conversationId` (`Models.kt` `L500`) is non-nullable with **no default value**. Loading any DM/group with a pinned message fails Kotlinx Serialization (`MissingFieldException`).
   - **Bug 2 (`20261113000003` L790):** Returned raw `p.is_muted` instead of evaluating `(p.muted_until IS NOT NULL AND p.muted_until > now())` when timed mutes (`1h`, `8h`, `1w`) expire; also `is_pinned` now evaluates `COALESCE(p.is_pinned, false) OR (p.pinned_at IS NOT NULL)`.
   - **Fix:** Added `'conversation_id', pm.conversation_id` inside `pinned_message` and dynamic mute/pin evaluation.

2. **`toggle_pin_dm_message` — Unpin PGRST202 Failure (`L1308–L1350`)**
   - **Bug (`20261112000001` L1098):** Signature had `p_message_id uuid` with **no default**, while `BuildXRepository.togglePinDmMessage` (`L2086`) omits `p_message_id` when `messageId == null` (unpin). PostgREST throws `PGRST202` (function not found in schema cache).
   - **Fix:** Changed parameter to `p_message_id uuid DEFAULT NULL` and verified `p_message_id` belongs to `p_conversation_id` and is not deleted when pinning.

3. **`get_or_create_direct_conversation` — Locked Out After "Delete Chat for Me" (`L347–L425`)**
   - **Bug (`20261112000001` L211):** `delete_conversation_for_me` sets `status = 'hidden'`, but `get_or_create_direct_conversation` only reactivated `status = 'left'`. Re-opening a hidden 1:1 DM left `status = 'hidden'`, causing `_is_dm_participant` (`status IN ('active', 'request')`) to reject subsequent `get_dm_conversation_detail`, `get_dm_messages`, and `send_dm_message` calls with `NOT_A_PARTICIPANT`.
   - **Fix:** Reactivates `status IN ('left', 'hidden')` back to `'active'` (clearing `left_at`, `hidden_at`), and initializes newly created non-caller participant `last_read_at` to `'1970-01-01T00:00:00Z'::timestamptz` instead of `now()`.

4. **`get_dm_messages` — Exposed Pre-Clear History & Non-Array Error Shape (`L990–L1099`)**
   - **Bug (`20261112000001` L763–L765):** Ignored `dm_participants.cleared_before`, leaking deleted history after `delete_conversation_for_me` once the user re-entered or received a new message. Also returned `'[]'::jsonb` only on empty result, while returning JSON object `{"success": false, "error": "NOT_A_PARTICIPANT"}` on auth failure — crashing `decodeAs<List<DmMessage>>()`.
   - **Fix:** Filters `AND (v_cleared_before IS NULL OR m.created_at > v_cleared_before)`, raises `'NOT_A_PARTICIPANT'` exception on unauthorized access (mapped properly by `ErrorMapper` rather than failing JSON array decoding), and applies deterministic `ORDER BY` inside `jsonb_agg`.

5. **`get_dm_inbox` — Filter Bug, Missing Fields, Cursor Bug & Display Fallback (`L595–L832`)**
   - **Bugs (`20261112000008`):**
     - `v_filter = 'requests'` (`OR v_filter = 'requests'` at `L174`) leaked ALL chats instead of `ec.status = 'request'`.
     - Omitted `status`, `last_read_at`, `last_read_message_id`, and nested `last_message` (`DmInboxItem` `L378, L384–L385, L394`; `BuildXViewModel` `L3183`).
     - Keyset pagination duplicated pinned chats if `p_cursor_activity` was passed with `p_cursor_pinned IS NULL`.
     - Blank/whitespace `sender_prof.display_name` resolved `split_part` to `''` instead of falling back to `username`/`'User'`.
     - Correlated `peer_user_id` subquery instead of direct `direct_user1_id`/`direct_user2_id`.
     - Missing explicit `ORDER BY` in `jsonb_agg`.
   - **Fix:** Restores all DTO fields matching `DmInboxItem` + `DmLastMessageSummary`, enforces `ec.status = 'request'` for `'requests'`, fixes keyset tuple comparison `(is_pinned, pinned_at, sort_ts)`, uses `direct_user1_id`/`direct_user2_id` for 1:1 peers, respects `cleared_before` on unread counts & `last_message`, and preserves `NULLIF(BTRIM(...), '')` display-name fallback.

6. **`send_dm_message` & `trg_dm_messages_sync_last_message` — Double Realtime Events & Validation (`L242–L341`, `L1102–L1305`)**
   - **Bugs (`20261112000001` + `20261112000006`):**
     - `send_dm_message` inserted into `dm_messages` (firing `trg_dm_messages_sync_last_message`, which updated `dm_conversations` & `dm_participants`) and then explicitly `UPDATE`d `dm_conversations` and `dm_participants` a second time, producing **duplicate Supabase Realtime events per message**.
     - `trg_dm_messages_sync_last_message` checked `message_type = 'image'` instead of `'media'`, mislabeling media previews.
     - `p_reply_to_message_id` was not verified to belong to `p_conversation_id` (cross-conversation reply leak).
     - `p_client_temp_id` had a unique index (`dm_messages_client_temp_id_idx`) but `send_dm_message` never checked it for retry idempotency (causing `23505` on retry).
     - Empty messages (`content = ''` with no attachment and no note reply) were accepted.
   - **Fix:**
     - `trg_dm_messages_sync_last_message` now handles `'media'` and `'note_reply'`, un-hides `'hidden'` participants (reactivating `'hidden'` -> `'active'`, clearing `cleared_before` only via new msg > `cleared_before`), and updates `last_activity_at` + `last_message_*` once cleanly.
     - `send_dm_message` checks `(conversation_id, client_temp_id)` idempotently and returns existing row on retry; validates `p_reply_to_message_id` belongs to `p_conversation_id`; rejects empty messages (`EMPTY_MESSAGE`); relies on the single trigger write for `dm_conversations` & peer `dm_participants`.

7. **Read-Receipt Pre-Read Timestamp Bug (`create_group_conversation` & `get_or_create_direct_conversation`, `L347–L487`)**
   - **Bug:** `dm_participants.last_read_at` defaulted to `now()`. In `create_group_conversation`, members were inserted in the same transaction timestamp `now()` as the `'created the group'` system message, and `ChatMessageMapper.computeDeliveryStatus` (`L276–L297`) checks `maxPeerReadAtMillis >= msgMillis` -> marking initial messages/DMs **read** (`✓✓`) before peers ever opened the chat.
   - **Fix:** Both `get_or_create_direct_conversation` and `create_group_conversation` initialize non-creator/recipient `last_read_at` to `'1970-01-01T00:00:00Z'::timestamptz`. *(Note: `add_group_members` intentionally keeps `last_read_at = now()` at join time so historical pre-join messages do not flood a newly added member as unread.)*

8. **Pin Slot Leak & Un-clean Group Removal (`L490–L542`, `L1393–L1452`)**
   - **Bugs:** `set_conversation_pin` counted `'left'`/`'hidden'` rows toward the 3-pin limit; `remove_or_leave_group_member` left `is_pinned`, `pinned_at` set and did not set `left_at`.
   - **Fix:** `set_conversation_pin` counts only `status IN ('active', 'request')`; `remove_or_leave_group_member` sets `left_at = now()` and clears `is_pinned = false, pinned_at = NULL`; `delete_conversation_for_me` also clears `marked_unread = false` and advances `last_read_at = now()`.

9. **`update_group_info` Validation (`L545–L591`)**
   - Added explicit `kind = 'group'` verification (`INVALID_CONVERSATION_KIND`) and normalized `1..80` character title check (`INVALID_GROUP_TITLE`) before hitting SQL `CHECK` constraint `23514`.

10. **`edit_user_message` (AI Chat) Truncation Regression (`L1484–L1535`)**
    - **Bug:** `20261111000002_clean_rebuild.sql` (`L1195`) dropped `DELETE FROM public.messages WHERE conversation_id = ... AND created_at > v_created_at` that existed in `20261110000002_message_actions.sql`, breaking AI chat edit-and-regenerate (`BuildXViewModel.kt` `L1284`).
    - **Fix:** Restored atomic deletion of subsequent messages in the same conversation + `updated_at = now()` touch on `conversations`.

### 5.2 Contract Check Summary of Remaining Messaging RPCs
| RPC / Endpoint | Kotlin Caller (`BuildXRepository.kt`) | Status |
| --- | --- | --- |
| `mark_conversation_read(p_conversation_id, p_message_id)` | `markDmRead` (`L2022–L2034`) | Monotonic `GREATEST` & timestamp check verified (`20261113000003`); ACL OK (`REVOKE PUBLIC, anon`). |
| `mark_conversation_unread(p_conversation_id)` | `markConversationUnread` (`L2136–L2147`) | Defined in `20261112000006`; ACL hardened in `20261114000001`. |
| `set_conversation_pin` / `set_conversation_mute` | `L2100–L2134` | `set_conversation_pin` fixed (`20261114000001`); both ACL-hardened. |
| `delete_conversation_for_me` / `leave_group` | `L2150–L2172` | `delete_conversation_for_me` fixed (`20261114000001`); both ACL-hardened. |
| `edit_dm_message` / `add_group_members` | `L2036–L2070` / `L2195` | Logic OK; ACL hardened in `20261114000001`. |
| `toggle_note_like`, `get_followed_active_notes`, `get_my_active_note`, `create_note` | `L2220–L2285` | Verified in `20261113000003`; `FeedNoteItem` / `NoteLikeToggleResult` match SQL output & `REVOKE PUBLIC, anon`. |
| `set_chat_wallpaper` + direct `profiles`/`dm_participants` fallback | `L2287–L2340` | Verified in `20261113000003`; matches `ChatWallpaperConfig`. |
| `get_user_notifications` + `notifications` table | `L1011–L1045` | Verified in `20261113000003` + `20261113000005`; `REPLICA IDENTITY FULL` confirmed. |
| `register_push_token` | `L2342–L2357` | Verified in `20261113000005`; ACL hardened in `20261114000001`. |

---

## 6. Edge Function Audit (`supabase/functions/chat/index.ts`)

Audited `supabase/functions/chat/index.ts` (`L1–L516`):
- **Auth & Ownership:** `GET ?share_token=...` reads publicly only where `share_token` matches and strips sensitive fields. `POST` validates JWT via Supabase Auth before proceeding, verifies `conversation.user_id === user.id`, and uses `service_role` admin client to persist streaming tokens, partial chunks, and abort status (`status = 'stopped'`).
- **Consistency Check:** No schema mismatch with `conversations` or `messages` (`status IN ('streaming', 'done', 'error', 'stopped')`). Compatible with `20261114000001` `is_pinned` trigger and RLS updates.

---

## 7. Android-Side Telemetry, N+1 & Serialization Spec for `@chat-logic`

> **Action Owner:** `@chat-logic` (per guardrails, `@sql-auditor` did not modify Kotlin files).

1. **CRITICAL — Inbox Realtime N+1 Storm (`BuildXViewModel.kt` `L3015–L3048` & `sendDmMessage` `L3758`):**
   - `subscribeToRealtimeInbox` collects three separate `postgresChangeFlow` streams (`dm_participants`, `dm_messages`, `dm_conversations`) and invokes `loadDmInbox()` directly on **every emission** without debouncing or coalescing, AND `sendDmMessage` / `dispatchDmSend` also calls `loadDmInbox()` on success.
   - Combined with the prior double-write in `send_dm_message`, a single outgoing message triggered **4–6 back-to-back `get_dm_inbox` RPCs** and visible `isDmInboxLoading` flicker.
   - **Recommendation:**
     - Add a `dmInboxSubscriptionJob: Job?` guard (cancel previous before re-subscribing).
     - Coalesce/debounce (`debounce(250–350ms)` or `conflate()` + silent refresh without flipping `isDmInboxLoading = true`) all three realtime inbox triggers into a single `refreshDmInboxSilently()` call, and avoid duplicate foreground reloads when `dm_messages` / `dm_participants` events already deliver the update.
     - Log channel errors instead of swallowing them in empty `catch`.

2. **DM Room Subscription Churn (`BuildXViewModel.kt` `L3458–L3581`):**
   - Every `msgFlow` event inside `subscribeToDmRoom` triggers a full `getDmMessages(limit = 50)` + `markDmRead` + `broadcastDmReadReceipt`, while both `partFlow` and `convFlow` call `getDmConversationDetail`.
   - **Recommendation:** Decode INSERT/UPDATE `PostgresAction.Insert`/`Update` payloads incrementally (or debounce `getDmMessages` / `getDmConversationDetail` by ~150ms) and only emit `markDmRead` / `broadcastDmReadReceipt` when an **incoming** peer message (`senderId != currentUserId`) arrives while the room is foregrounded.

3. **Full-Table Assistant Message Scan (`BuildXRepository.kt` `fetchUnreadConversationIds` `L1128–L1156`):**
   - Fetches **all** completed assistant messages (`eq("role", "assistant")`, `eq("status", "done")`, no limit) across all user AI conversations on every inbox refresh.
   - **Recommendation:** Add a dedicated lightweight SQL RPC (or filtered query / View) returning only unread conversation IDs once ready; in the interim, `idx_messages_conv_role_status_created` (`20261114000001` `L82`) mitigates scan cost.

4. **AI Search Double-Fetch (`BuildXRepository.kt` `searchConversations` `L1079–L1110`):**
   - Executes `ilike` on `messages` AND calls `fetchConversations()` when non-empty. Debounce search input >= 300ms in UI/ViewModel.

5. **DTO Resilience & Error Mapping (`Models.kt` `L500`, `ErrorMapper.kt` `L200–L420`):**
   - **Defense in depth:** Add `= ""` defaults on `DmMessage.conversationId` (and optional defaults on `id`, `senderId`, `createdAt`) in `Models.kt` so partial nested JSON payloads (like `pinned_message`) can never crash with `MissingFieldException`.
   - **ErrorMapper (`ErrorMapper.kt`):** Map backend messaging error tokens raised by DM RPCs: `PIN_LIMIT_REACHED` ("You can pin up to 3 conversations"), `NOT_A_PARTICIPANT` ("You are no longer a participant in this chat"), `EMPTY_MESSAGE`, `INVALID_GROUP_TITLE`, `INVALID_CONVERSATION_KIND`, and `PGRST202` ("Please update app or refresh schema").

---

## 8. Live Verification & Apply Playbook (For `@coordinator`)

Once `@coordinator` applies `supabase/migrations/20261114000001_messaging_audit_hardening.sql` via Supabase MCP (`apply_migration` / `execute_sql` on project `owgpuolvfqfjhezuohyf`), execute these 5 verification checks:

1. **Verify applied migrations (`list_migrations` / `execute_sql`):**
   ```sql
   SELECT version, name FROM supabase_migrations.schema_migrations
   WHERE version >= '20261109000001'
   ORDER BY version;
   ```
2. **Verify `is_pinned`, indexes, and RLS `(SELECT auth.uid())`:**
   ```sql
   SELECT column_name, data_type, is_nullable
   FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name = 'conversations'
     AND column_name IN ('pinned', 'is_pinned', 'is_locked');

   SELECT tablename, policyname, cmd, roles, qual
   FROM pg_policies
   WHERE schemaname = 'public'
     AND tablename IN ('conversations', 'messages', 'attachments', 'dm_conversations', 'dm_participants', 'dm_messages', 'note_likes', 'notifications')
   ORDER BY tablename, policyname;
   ```
3. **Verify zero `anon` privileges on DM tables & Messaging RPCs:**
   ```sql
   SELECT table_name, grantee, privilege_type
   FROM information_schema.role_table_grants
   WHERE table_schema = 'public'
     AND table_name IN ('dm_conversations', 'dm_participants', 'dm_messages')
     AND grantee IN ('PUBLIC', 'anon', 'authenticated')
   ORDER BY table_name, grantee, privilege_type;

   SELECT p.proname, r.rolname
   FROM pg_proc p
   JOIN pg_namespace n ON n.oid = p.pronamespace
   CROSS JOIN (VALUES ('anon'), ('public')) AS r(rolname)
   WHERE n.nspname = 'public'
     AND p.proname IN (
       'get_dm_inbox', 'get_dm_conversation_detail', 'get_dm_messages',
       'send_dm_message', 'toggle_pin_dm_message', 'set_conversation_pin',
       'set_conversation_mute', 'mark_conversation_unread', 'delete_conversation_for_me',
       'leave_group', 'register_push_token', 'edit_user_message'
     )
     AND has_function_privilege(
       CASE WHEN r.rolname = 'public' THEN 0::oid ELSE (SELECT oid FROM pg_roles WHERE rolname = r.rolname) END,
       p.oid, 'EXECUTE'
     );
   -- Expected: 0 rows returned!
   ```
4. **Verify `get_advisors` (`security` & `performance`):** Confirm zero `auth_rls_initplan` / `unindexed_foreign_keys` warnings remain on messaging tables (`conversations`, `messages`, `attachments`, `dm_conversations`, `dm_participants`, `dm_messages`).
5. **Verify `query_logs` (`postgres` / `postgrest` / `realtime`):** Confirm zero `PGRST202` (`toggle_pin_dm_message`), `PGRST204` (`is_pinned`), `MissingFieldException` triggers (`pinned_message`), or `NOT_A_PARTICIPANT` errors after applying the migration.
