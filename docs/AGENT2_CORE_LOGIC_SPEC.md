# AGENT 2 — Chat Core Logic & Functional Specification

**Owner:** AGENT 2 (`chat-logic`)  
**Scope:** Chat/DM/Group Messaging Core Logic (`Models.kt`, `BuildXRepository.kt`, `BuildXViewModel.kt`, `ChatUiModels.kt`, `ChatMessageMapper.kt`, `ChatMessageMapperTest.kt`, `DmMessagingTest.kt`). Profile & Settings untouched; UI screen composables untouched.

---

## 1. End-to-End Messaging Lifecycle Map

### 1.1 Public / AI Chat (`Conversation`, `ChatMessage`)
- **State (`BuildXUiState`)**: `conversations`, `activeConversationId`, `messages`, `isStreaming`, `editingMessageId`, `editingMessageText`, `unreadConversationIds`.
- **Lifecycle**:
  1. `sendMessage` inserts optimistic user message + streaming assistant placeholder immediately and streams SSE chunks (`ReasoningStepEvent`, content deltas, completion).
  2. `sendEditedMessage` truncates subsequent local messages, persists via `repository.editUserMessage`, and re-streams completion. **Fixed:** if `editUserMessage` fails on non-incognito chats, `BuildXViewModel` now rolls back `messages` and clears `isStreaming = false` instead of leaving the stream spinner stuck.
  3. `unreadConversationIds` tracks background AI completions viewed outside the active conversation and clears on conversation selection.

### 1.2 Direct & Group Messaging (`DmInboxItem`, `DmConversationDetail`, `DmMessage`)
1. **Inbox Loading & Real-Time Sync**:
   - `loadDmInbox` fetches `filter = "all"` whenever `search.isBlank()` and stores the canonical unfiltered list in `dmAllInboxCache`, deriving `dmInboxCounters` and `totalUnreadDmCount` via `ChatMessageMapper.computeInboxCounters`.
   - `selectDmFilterTab` switches between `ALL`, `UNREAD`, and `GROUPS` at `0ms` using `dmAllInboxCache` without overwriting `dmInboxItems` with a partial subset (fixing stale `unreadCount` / `groupsCount` badges and tab-switch flicker). Only `REQUESTS` or an empty cache triggers a server fetch.
   - `onDmSearchQueryChanged` restores `dmAllInboxCache` immediately when cleared and debounces server inbox (`150ms`) and people (`180ms`) searches with job cancellation.
   - `subscribeToRealtimeInbox` is guarded by `dmInboxSubscriptionJob` and coalesces burst events from `dm_participants`, `dm_messages`, and `dm_conversations` via `scheduleRealtimeInboxRefresh` (`160ms` debounce) with silent refreshes (`isDmInboxLoading` only shown when `dmInboxItems.isEmpty()`).
2. **Room Entry, Pagination & Cache**:
   - `enterDmRoom(conversationId)` immediately hydrates `activeDmDetail` (`resolveInitialDmDetail`), `dmMessages` (`dmMessagesCache`), `activeDmWallpaper`, and saved `dm_draft_<id>`, while clearing `unreadCount = 0` and `isMarkedUnread = false` on the inbox row atomically via `applyInboxMutation`.
   - Server fetches `getDmConversationDetail` (applied monotonically via `applyDetailMonotonic`) and latest 50 messages via `getDmMessages`, then reconciles with local `SENDING`/`FAILED` items (`ChatMessageMapper.reconcileOptimisticMessages`) and calls `markDmRead(conversationId, lastMsgId)` (falling back to `effectiveLastMessageId`).
   - `loadOlderDmMessages` keyset-paginates using the oldest `SENT` server message's `createdAt` and preserves paginated history in `dmMessagesCache`.
   - `subscribeToDmRoom` coalesces room reloads (`80ms` for messages, `100ms` for detail), preserves already-paginated older `SENT` messages above the fetched 50-message window, applies `mergeParticipantReadState` on `read_receipt` broadcasts, handles typing indicator expiry (`3.6s`), and only triggers `markDmRead` + `broadcastDmReadReceipt` when an incoming message is from another user (`latest.senderId != myUid`).
3. **Optimistic Send, Status Transitions & Retry (`0ms` Perceived Latency)**:
   - `sendDmMessage` generates both `tempId` and `clientTempId` and computes a monotonic timestamp `maxOf(System.currentTimeMillis(), lastKnownMsgMs + 1L)` so local messages always sort at the bottom regardless of client/server clock skew.
   - Immediately updates `dmMessages`, `dmMessagesCache`, `justSentClientTempIds`, `lastOptimisticMessageId`, clears draft/attachments/reply state, and updates the inbox conversation preview (`lastMessageText`, `lastMessageType`, `lastMessageAt`, `unreadCount = 0`) at `0ms`.
   - **Status transitions**: `DmSendStatus.SENDING` (`ChatDeliveryStatus.SENDING`) $\rightarrow$ `DmSendStatus.SENT` (`ChatDeliveryStatus.SENT` ✓) upon `send_dm_message` RPC success $\rightarrow$ `ChatDeliveryStatus.READ` ✓✓ when any peer's `lastReadMessageId == msg.id` or `maxPeerReadAtMillis >= msgMillis`.
   - **Upload failure & retry**: If `uploadDmAttachment` fails in `dispatchDmSend`, the send aborts cleanly, marks the optimistic message `DmSendStatus.FAILED`, shows an error snackbar, and retains the `StagedAttachment` in `dmFailedStagedAttachmentByTempId[tempId]` so `retryFailedDmMessage` re-uploads and resends without losing media.
4. **Reply Threading, Editing, Deletion, Pinning & Muting**:
   - `setDmEditing(null)` restores any saved conversation draft from `dmDraftsByConversation` / `SharedPreferences` instead of wiping composer text. Composer typing during edit no longer overwrites the unsent message draft.
   - Editing (`sendDmMessage` edit branch) uses `ChatMessageMapper.applyMessageEditToHistory` to update the message, all in-memory `replyTo` previews referencing it, `dmMessagesCache`, `activeDmDetail.pinnedMessage`, and the inbox preview atomically.
   - Deletion (`deleteDmMessage`) removes failed local messages immediately without RPC, and for sent messages updates the message, nested `replyTo` previews (`applyMessageDeleteToHistory`), `activeDmDetail.pinnedMessage`, and the inbox preview atomically.
   - Pinning (`togglePinDmMessage`) applies optimistic `pinnedMessage` state immediately, syncs via `getDmConversationDetail`, and rolls back + surfaces snackbar errors on failure. Also fixed `DmMessage` / `DmReplyPreview` / `DmLastMessageSummary` defaults in `Models.kt` so `pinned_message` JSON lacking `conversation_id` never throws `MissingFieldException`, and backfilled `conversationId` in `BuildXRepository.getDmConversationDetail`.
   - Conversation pinning (`setConversationPin`) checks the 3-pin limit against the canonical unfiltered `dmAllInboxCache` list and keeps inbox + detail participant flags synced. Muting (`toggleMuteDmConversation`, `setConversationMute`) and leave/delete (`leaveOrDeleteDmConversation`, `deleteConversationForMe`, `leaveGroup`) all flow through `applyInboxMutation`.

---

## 2. Exposed Contracts for Agents 5 & 6

### 2.1 `BuildXUiState` (`BuildXViewModel.kt`)
- `lastOptimisticMessageId: String?` — clientTempId of the most recently inserted optimistic message (cleared when `consumeJustSentAnimation(clientTempId)` runs or on `leaveDmRoom`), complementing `justSentClientTempIds: Set<String>`.
- `dmInboxCounters: ChatInboxCounters` — canonical unfiltered badge counts (`totalUnreadMessages`, `unreadConversationsCount`, `groupsCount`).
- `totalUnreadDmCount: Int` — always synchronized with `dmInboxCounters.totalUnreadMessages` across all loads and optimistic mutations.

### 2.2 `ChatUiModels.kt`
- `ChatListItem.MessageItem` (all new fields have defaults so existing previews/callers remain source-compatible):
  - `isNewlyInserted: Boolean = false` — true when `msg.id` or `msg.clientTempId` is in `newlyInsertedIds`.
  - `isFirstInCluster: Boolean` / `isLastInCluster: Boolean` — explicit cluster boundary flags (`SINGLE` has both `true`; `FIRST` has `first = true, last = false`; `MIDDLE` has both `false`; `LAST` has `first = false, last = true`). Deleted and `system` messages never merge into clusters.
  - `topCornerDp: Float` / `bottomCornerDp: Float` — computed cluster corner radii (`18f` outer vs `4f` grouped).
  - `isolatedSenderName: String?` — FSI/PDI wrapped sender display name.
  - `isolatedFormattedTime: String` — LRI/PDI isolated `HH:mm` timestamp.
  - `isolatedReplySenderName: String?` / `isolatedReplyContent: String?` — FSI/PDI isolated reply quote strings.
- `ChatHeaderUiState`:
  - `isolatedTitle: String` — FSI/PDI wrapped header title.
  - `isolatedSubtitle: String` — LRI/PDI (for `@handle`) or FSI/PDI wrapped subtitle.
- `ChatInboxCounters`: immutable `{ totalUnreadMessages, unreadConversationsCount, groupsCount }`.

### 2.3 `ChatMessageMapper.kt` & `ChatBidiFormatter`
- `ChatMessageMapper.mapToReversedListItems(..., newlyInsertedIds: Set<String> = emptySet())` — pass `uiState.justSentClientTempIds` (or `setOfNotNull(uiState.lastOptimisticMessageId)`) to drive `isNewlyInserted` flight animations.
- `ChatBidiFormatter` (pure Kotlin, zero Android framework dependency; safe in JVM unit tests and UI):
  - `firstStrongIsRtl(text: String, defaultRtl: Boolean = false): Boolean`
  - `isolateFirstStrong(text: String): String` — wraps in Unicode `\u2068...\u2069` (FSI/PDI).
  - `isolateWithDirection(text: String, isRtl: Boolean): String`, `isolateLtr(text: String)`, `isolateRtl(text: String)` — wraps in LRI `\u2066` / RLI `\u2067` ... PDI `\u2069`.
  - `formatInboxPreview(prefix: String?, body: String, isRtlContext: Boolean = false): String` — safely isolates `"You: ..."` or `"<Sender>: ..."` previews for mixed Arabic/English text.

### 2.4 `Models.kt` Helpers
- `DmInboxItem`: `effectiveLastMessageId`, `effectiveLastMessageText`, `effectiveLastMessageType`, `effectiveLastMessageSenderId`, `effectiveLastMessageAt`, `effectiveIsUnread`, `effectiveIsMuted`.
- `DmMessage`: `isDeleted`, `isEdited`, safe defaults on `conversationId`, `senderId`, `createdAt`.
