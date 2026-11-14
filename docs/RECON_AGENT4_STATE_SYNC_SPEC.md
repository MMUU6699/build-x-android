# RECON Agent 4: SQL, Realtime & Optimistic State Synchronizer Spec

## 1. System Event Mapping (`ChatUiModels.kt`, `ChatMessageMapper.kt`)
- **Root Cause Fixed**:
  - `create_group_conversation` (`supabase/migrations/20261114000001_messaging_audit_hardening.sql:478`) inserts `('system', 'created the group')` into `dm_messages`, while legacy rows or optimistic helpers could also carry `message_type = "text"` with `"created the group"` / `"joined the group"` / `"left the group"`.
  - Previously `ChatMessageMapper.mapToReversedListItems()` checked `msg.messageType == "system"` only for cluster-breaking and STILL emitted a `ChatListItem.MessageItem`, rendering `"created the group"` as an outgoing blue bubble with timestamps and checkmarks.
- **New Contract**:
  - Added `ChatListItem.SystemEventItem(stableKey, message, text, createdAtIso, isolatedText)` to `sealed interface ChatListItem` in `ChatUiModels.kt`.
  - Added `ChatMessageMapper.isSystemEventMessage(msg)` matching `msg.messageType.equals("system", ignoreCase = true)` OR trimmed content matching `"created the group"`, `"joined the group"`, `"left the group"`, or starting/ending with `"created the group"` (case-insensitive, non-deleted).
  - Added `ChatMessageMapper.formatSystemEventText(msg, senderDisplayName)` which formats `"${senderName} ${trimmed}"` for bare actions when `senderDisplayName` is present and not already prefixed, or falls back to `trimmed` (e.g. `"created the group"`).
  - `mapToReversedListItems()` now emits `ChatListItem.SystemEventItem` (`stableKey = "system_${msg.clientTempId ?: msg.id}"`) and `continue`s — NEVER emitting a `MessageItem` bubble for system events.
  - `groupedWithPrev` and `groupedWithNext` now both check `!isSystemEventMessage(...)`, ensuring `SystemEventItem` breaks `isFirstInCluster` / `isLastInCluster` on both sides.

## 2. Read Receipts & Reaction State (`ChatUiModels.kt`, `ChatMessageMapper.kt`, `BuildXViewModel.kt`)
- **Eliminated Orphaned Seen Avatar Data**:
  - `ChatListItem.MessageItem` now exposes `seenByNames: List<String> = emptyList()` and `isSeenByPeer: Boolean = false`.
  - `ChatMessageMapper.mapToReversedListItems()` sets `seenParticipants = emptyList()` (eliminating the orphaned floating `16.dp` avatar row under messages) while computing `deliveryStatus` (`ChatDeliveryStatus.READ`), `isSeenByPeer`, and `seenByNames` from both `last_read_message_id` (matching message or later message index in chronological order) and `last_read_at`.
  - `seenParticipants` property remains on `MessageItem` with `= emptyList()` default for backward compatibility until Agent 6 removes `ChatSeenAvatarsRow` in `ChatBubble.kt`.
- **Reaction Support**:
  - Added `reactions: Map<String, Int> = emptyMap()` and `myReaction: String? = null` to `ChatListItem.MessageItem`.
  - Added `dmMessageReactions: Map<String, Map<String, Int>>` and `dmMyMessageReactions: Map<String, String>` to `BuildXUiState` (backed by per-conversation `dmReactionsCache` in `BuildXViewModel`).
  - Added `ChatMessageMapper.toggleMessageReaction(...)` and `BuildXViewModel.toggleDmMessageReaction(messageId, emoji)` for instantaneous `0ms` local toggling (add, switch emoji, or toggle off) plus best-effort `dmRoomChannel` `"reaction"` broadcast sync across connected room peers.
  - Updated `ChatMessageMapper.mapToReversedListItems()` with optional `reactionsByMessageId` and `myReactionsByMessageId` parameters (default `emptyMap()`).

## 3. Zero-Jank Optimistic Send & Realtime Coalescing (`BuildXViewModel.kt`)
- **Root Causes Fixed**:
  - `sendDmMessage()` inserted an optimistic message (`0ms`) and cleared `dmReplyingToMessage = null` in one state update, but `dispatchDmSend()` subsequently triggered:
    1. A self-echo `PostgresAction.Insert` on `dmRoomChannel`'s `msgFlow` -> redundant full 50-message `getDmMessages()` reload.
    2. A `dm_conversations` `last_activity_at` update on `convFlow` & `dm_participants` watermark update on `partFlow` -> redundant `getDmConversationDetail()` reloads.
    3. Duplicate `loadDmInbox()` calls from both `dispatchDmSend.onSuccess` and `subscribeToRealtimeInbox`.
- **Coalescing Fixes**:
  - Track `inFlightClientTempIds` and `recentlySentClientTempIds` in `BuildXViewModel`.
  - Added `ChatMessageMapper.shouldSkipRealtimeMessageInsert(...)` and `shouldSkipRealtimeMessageUpdate(...)`:
    - `subscribeToDmRoom` `msgFlow` skips `getDmMessages()` for self-echoed `Insert`s matching `inFlightClientTempIds`/`recentlySentClientTempIds` or existing local `id`/`clientTempId`, and skips `Update`s matching already-applied local edits/deletes.
    - `partFlow` ignores own `last_read_at` watermark updates and merges peer read-receipt updates monotonically in-memory (`ChatMessageMapper.mergeParticipantReadState`) when the participant is already in `activeDmDetail`.
    - `convFlow` skips `getDmConversationDetail()` when `title`, `avatar_url`, and `pinned_message_id` are unchanged.
  - `dispatchDmSend.onSuccess` updates the local inbox row (`lastMessage` + `lastMessageId`/`Text`/`At`) and skips `loadDmInbox()` when the conversation is already in `dmInboxItems`; `subscribeToRealtimeInbox` skips `scheduleRealtimeInboxRefresh()` for self-echoed inserts in the active room.
  - `msgFlow` only updates `_uiState` when `reconciled != state.dmMessages`, avoiding no-op LazyColumn recompositions.
