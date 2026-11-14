# AGENT 1 — Deep Codebase & State Flow Root-Cause Trace

- **Scope**: Read-only structural trace across `DmConversationScreen.kt`, `ChatAndDrawerScreen.kt`, `ChatBubble.kt`, `ChatMessageList.kt`, `ChatComposer.kt`, `ChatHeader.kt`, `DmConversationPopup.kt`, `ChatMessageMapper.kt`, `ChatInfoScreen.kt`, `DmChatInfoSheet.kt`, plus supporting state/UI/SQL files (`ChatUiModels.kt`, `ChatTokens.kt`, `BuildXSheets.kt`, `BuildXComponents.kt`, `BuildXTheme.kt`, `BuildXViewModel.kt`, and Supabase SQL migrations).
- **Guardrails**: Zero Kotlin source files modified; `ProfileScreen.kt`, `EditProfileScreen.kt`, and `SettingsScreens.kt` untouched.

---

## Summary Matrix

| # | Defect Area | Root Cause Summary | Primary Files & Lines |
|---|---|---|---|
| 1 | Broken Backdrop Blur & Z-Index Bleeding | `compatRenderEffectBlur` blurs only the capsule's own static gradient `Box` (not underlying `LazyColumn`); `ChatMessageList` / `LazyColumn` fill `y = 0` with no `clipToBounds()` or `zIndex`; top protective gradient is translucent (`0.78f`/`0.96f` at `0.0f`, `0.46f`/`0.84f` at `0.72f`) with no opaque status-bar scrim. | `ChatHeader.kt:556-609`, `DmConversationScreen.kt:347-438`, `ChatMessageList.kt:235-245`, `ChatTokens.kt:107,160,208-209` |
| 2 | Orphaned "Seen" Avatar Below Message (`غ`) & Header Scroll Glitch | Actual composable is `ChatSeenAvatarsRow` (`ChatBubble.kt:1656`, `ChatDimens.SeenAvatarSize = 15.dp`, NOT `SeenByAvatarsRow`). `ChatMessageMapper.kt:323-325` attaches `seenParticipants` to *any* `isOwn` message matching `lastReadMessageId`, even old messages far behind the latest message or in 1:1 chats (`✓✓` already present). `subscribeToDmRoom` only auto-marks read when `latest.senderId != myUid`, leaving stale `last_read_message_id` pinned to older message `غ`, which then scrolls up behind the translucent top bar (`Defect 1`). | `ChatBubble.kt:387-393, 1656-1685`, `ChatMessageMapper.kt:323-325, 595-634`, `BuildXViewModel.kt:3270-3291, 3509-3517, 3652-3658`, `20261113000003_ensure_live_note_likes_and_wallpapers_rpcs.sql:518-549` |
| 3 | Illogical System Messages (`"created the group"` as Outgoing Blue Bubble) | Backend SQL (`create_group_conversation`) inserts `message_type = 'system'`, `sender_id = v_caller`, `content = 'created the group'`. Kotlin `ChatListItem` has no `SystemEventItem` variant; `ChatMessageMapper.kt:281-375` sets `isOwn = msg.senderId == currentUserId` and still emits `ChatListItem.MessageItem` (for creator, `isOwn == true`; for others, `showSenderAvatar` doesn't even exclude `!isSystem`). `ChatBubbleContent` renders it in the standard text bubble branch (`sentBubbleBg`, right-aligned, time + checkmarks). | `ChatUiModels.kt:24-73`, `ChatMessageMapper.kt:281-375`, `ChatBubble.kt:340-357, 837-973`, `20261114000001_messaging_audit_hardening.sql:475-479` |
| 4 | Dark Screen Overlay Flicker & Composer Jump | (A) **Status-bar/Overlay flicker**: `UpdateStatusBarAppearance` runs inside `SideEffect` on every `ChatHeader` recomposition (`ChatHeader.kt:117-119, 616-626`) and in `ChatAndDrawerScreen.kt:196`, fighting `BuildXTheme.kt:755-762`'s root `SideEffect` (`!useDark` vs `headerGlassBg.luminance() > 0.5f`); additionally `menuBackdropScrim` (`0x2E000000`/`0x4D000000`) animates with underdamped `interactiveSpring()` (`dampingRatio = 0.72f`, `stiffness = 480f`) and `ChatComposer` lacks `.imePadding()` directly (relying on parent `Column.onSizeChanged` + `navAndImeInsets`). (B) **Composer jump on reply-send**: `sendDmMessage` mutates `dmMessages`, `dmComposerText`, `dmStagedAttachments`, and `dmReplyingToMessage = null` in one synchronous state update; `AnimatedVisibility` exit (`shrinkVertically` + `slideOutVertically`) fights parent `Column.animateContentSize`, `stagedAttachments` disappears instantaneously, `bottomOverlayHeightPx` changes every frame feeding `LazyColumn(contentPadding.bottom)` at the exact same moment `LazyColumn` inserts index `0` and runs send-flight bounds tracking. | `ChatHeader.kt:117-119, 616-626`, `BuildXTheme.kt:753-763`, `ChatAndDrawerScreen.kt:196, 201-210`, `ChatMessageList.kt:160-166, 190-222`, `ChatComposer.kt:144-153, 200-277, 363-384`, `DmConversationScreen.kt:313-321, 510-523`, `BuildXViewModel.kt:4033-4047` |
| 5 | Broken Message Context Menu | Selected message is wrapped inside `LazyColumn` and `ChatBubbleContent` opens a native Android `Popup(focusable = true)` containing `ActionMenuPopup` (`200.dp` opaque `colors.cardBackground` box with `OverflowMenuRow` text-left / icon-right, NOT left-icon rows). `BubblePopupPositionProvider` clamps tall 7-item menus (`Reply`, `Copy`, `Select text`, `Edit`, `Pin`, `Forward`, `Delete`) directly over the bubble on mid/low messages, while `ChatMessageList` renders a separate `menuBackdropColor` behind `LazyColumn` and applies `Modifier.blur(2.5.dp)` + `0.55f` alpha to siblings, leaving top/bottom chrome unblurred. On AI Chat (`ChatAndDrawerScreen.kt:201-210, 654-658`), `modalBlurDp` blurs the entire `MainChatSurface` container. Zero floating emoji reaction bar (`👍 ❤️ 🔥 😍 👏 🤔`) exists anywhere in the codebase. | `DmConversationScreen.kt:332-333, 357-405, 569-594, 723-810`, `ChatMessageList.kt:190-222, 275-290`, `ChatBubble.kt:309-329, 866-972, 2036-2073`, `BuildXSheets.kt:233-389`, `ChatAndDrawerScreen.kt:201-210, 654-658` |
| 6 | Illogical Chat/Group Info Screen | (1) **Avatar vs Status Bar collision**: `ChatInfoScreen.kt:187-194` puts `HeaderCircleButton` back nav *inside* the single `verticalScroll` `Column` instead of a pinned top bar with `zIndex`; scrolling scrolls the back button away and pulls the `92.dp` hero avatar straight against the top status-bar boundary, while `DmChatInfoSheet.kt:156-168` is `fillMaxHeight(0.92f)` with no `statusBarsPadding()`. (2) **Duplicated `Add members`**: Admin quick-circle action #1 (`R.string.dm_info_add_label` = `"Add"`, `ChatInfoScreen.kt:435-445`) AND vertical `ChatInfoControlRow` (`R.string.dm_add_members` = `"Add members"`, `ChatInfoScreen.kt:540-548`) both toggle `isAddingMembersOpen`. (3) **`Leave group` above media tab**: `ChatInfoScreen.kt:738-744` places `ChatInfoControlRow(dm_leave_group)` *before* `Spacer(14.dp)` and the Bottom Shared Media Section (`L747-780`), whereas `DmChatInfoSheet.kt:375-424` puts `InfoRow(dm_leave_group)` *after* `SharedMediaGrid`. | `ChatInfoScreen.kt:187-250, 435-445, 540-548, 738-780`, `DmChatInfoSheet.kt:156-215, 375-425`, `BuildXComponents.kt:185-220` |

---

## Detailed Root-Cause Traces

### 1. Broken Backdrop Blur & Z-Index Bleeding

1. **`RenderEffect` only blurs the capsule's own internal gradient node, never the message list behind it**
   - In [ChatHeader.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatHeader.kt#L556-L609), `ChatGlassCapsuleContainer` creates an internal sibling `Box` (`L556-L576`) containing only two `Brush.verticalGradient(...)` backgrounds (`glassColor`/`bottomTint` and `topHighlight`/`Color.Transparent`) and applies `.compatRenderEffectBlur(blurRadius = blurRadius, clipShape = shape)`.
   - `compatRenderEffectBlur` (`ChatHeader.kt:593-609`) sets `RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP)` on `Modifier.graphicsLayer`. In Jetpack Compose / Android `RenderNode`, `RenderEffect` only blurs the pixels drawn **inside that `graphicsLayer` subtree** (i.e., the static vertical gradient inside the capsule), NOT any sibling composables drawn underneath it in the parent `Box` (such as `ChatMessageList`).
   - Resulting glass alpha (`ChatTokens.kt:107, 160, 208-209`) is `0.88f` (dark) / `0.92f` (light) — and `ChatHeader.kt:142-144` even lowers it by `0.04f` when `!isListScrolled` — so `8%–16%` of raw, unblurred underlying message text and avatars show straight through the capsules.

2. **Full-bleed `LazyColumn` extends under the status bar (`y = 0`) with no clipping or `zIndex` discipline**
   - In [DmConversationScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/DmConversationScreen.kt#L347-L438):
     - `ChatMessageList` is placed in the root `Box` (`L335`) with `modifier = Modifier.fillMaxSize()` (`L417`), starting at `y = 0` behind both the system status bar and the floating `ChatHeader`.
     - Inside [ChatMessageList.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatMessageList.kt#L235-L245), `LazyColumn` also uses `modifier = Modifier.fillMaxSize()` with `top = topContentPadding` inside `contentPadding`. `contentPadding` only offsets scroll rest position; as soon as the user scrolls, items translate freely into the top `topContentPadding` region all the way to `y = 0` (the status bar clock/icons).
     - Neither `ChatMessageList` (`DmConversationScreen.kt:417`) nor the top overlay `Column` (`DmConversationScreen.kt:425-438`) sets `Modifier.zIndex(...)` or `Modifier.clipToBounds()` below the header. Furthermore, `ChatBubbleEntryWrapper` (`ChatBubble.kt:327`) elevates active menu items with `.zIndex(10f)` inside `LazyColumn`.

3. **Translucent top gradient scrim fails to mask scrolled content in the status-bar zone**
   - [DmConversationScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/DmConversationScreen.kt#L420-L438) defines:
     - `topGradientHighAlpha = if (hasCustomWallpaper) 0.78f else 0.96f` (`L421`)
     - `topGradientLowAlpha = if (hasCustomWallpaper) 0.46f else 0.84f` (`L422`)
   - The top `Column` applies `.background(Brush.verticalGradient(0.0f to ...copy(alpha = topGradientHighAlpha), 0.72f to ...copy(alpha = topGradientLowAlpha), 1.0f to Color.Transparent))` and `.statusBarsPadding()` (`L437`).
   - There is **no** opaque (or properly masked/clipped) status-bar-height protective scrim above the `LazyColumn`, and with a custom wallpaper (`hasCustomWallpaper == true`) the gradient is `22%` transparent at `y = 0` and `54%` transparent at `0.72f` of the header height. Scrolled system messages (`created the group`), bubbles, and `15.dp` seen avatars remain visibly legible behind the header capsule and collide directly with the system status bar clock/icons.

---

### 2. Orphaned "Seen" Avatar Below Message (`غ`) & Header Scroll Glitch

1. **Exact Composable Identity**
   - Note: `SeenByAvatarsRow` does not exist in the codebase (`ChatMessageList.kt` has no seen-avatar helper). The actual implementation is **`ChatSeenAvatarsRow`** in [ChatBubble.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatBubble.kt#L1656-L1685), invoked from `ChatMessageRow` at [ChatBubble.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatBubble.kt#L387-L393):
     - Renders a `15.dp` circular avatar (`ChatDimens.SeenAvatarSize = 15.dp`, overlap `-4.dp`, `ChatTokens.kt:262-263`) with `padding(top = 3.dp, end = 4.dp)` underneath any `ChatListItem.MessageItem` where `item.seenParticipants.isNotEmpty() && item.isOwn`.

2. **Why the avatar becomes orphaned under an older message (`غ`)**
   - **Per-message exact-ID matching on any message, in both 1:1 and group chats**:
     In [ChatMessageMapper.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatMessageMapper.kt#L323-L325):
     ```kotlin
     val seenAtThisMessage = otherParticipants.filter {
         it.userId != msg.senderId && it.lastReadMessageId == msg.id
     }
     ```
     This does not constrain seen indicators to the latest own message (or group-only, even though 1:1 chats already display inline double checkmarks `ChatDeliveryStatus.READ`), nor does it advance/ignore a participant whose `lastReadAt` is newer than `msg.createdAt` when `lastReadMessageId` lagged behind.
   - **Stale `last_read_message_id` updates across client + SQL**:
     - In [BuildXViewModel.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/viewmodel/BuildXViewModel.kt#L3652-L3658), realtime `subscribeToDmRoom` only calls `markDmRead(conversationId, latest.id)` if `latest.senderId != myUid`.
     - In [BuildXViewModel.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/viewmodel/BuildXViewModel.kt#L3270-L3277), `markDmRead` prefers `conv?.effectiveLastMessageId` (which can lag behind `dmMessages` if inbox state hasn't refreshed).
     - In [20261113000003_ensure_live_note_likes_and_wallpapers_rpcs.sql](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/supabase/migrations/20261113000003_ensure_live_note_likes_and_wallpapers_rpcs.sql#L518-L549), `mark_conversation_read` executes `last_read_message_id = COALESCE(v_msg_id, last_read_message_id)`, and `mergeMonotonicParticipants` (`ChatMessageMapper.kt:614-634`) only replaces `lastReadMessageId` when `prevMs < nextMs` or incoming is non-null. Thus if a participant last marked read when message `غ` was latest (and subsequent messages were sent by `currentUserId` or read state remained on `غ`), the `15.dp` avatar stays orphaned underneath `غ` far up in scroll history.

3. **Why it glitches near the top header during scroll**
   - Because `LazyColumn` spans `Modifier.fillMaxSize()` starting at `y = 0` with no clipping below the top header and a translucent top gradient (`Defect 1`), scrolling `غ` upward causes `ChatSeenAvatarsRow` (anchored below `غ`) to slide directly underneath the floating `ChatHeader` glass capsule and into the status-bar area.

---

### 3. Illogical System Messages (`"created the group"` Rendered as Outgoing Blue Bubble)

1. **Backend creates `message_type = 'system'` with `sender_id = v_caller`**
   - In [20261114000001_messaging_audit_hardening.sql](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/supabase/migrations/20261114000001_messaging_audit_hardening.sql#L475-L479) (`create_group_conversation`):
     ```sql
     INSERT INTO public.dm_messages (
         conversation_id, sender_id, message_type, content
     ) VALUES (
         v_conv_id, v_caller, 'system', 'created the group'
     );
     ```
   - Likewise, `20261112000004_dm_inbox_enhancements.sql:33` (`'system', 'left the group'`) and `20261112000006_dm_inbox_modern_restructure.sql:411` (`'system'`) store group events with `message_type = 'system'` and the actor's `sender_id`. (The backend `message_type` is already `'system'`, NOT `'text'`.)

2. **`ChatUiModels.kt` lacks a `SystemEventItem` model**
   - [ChatUiModels.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatUiModels.kt#L24-L73) defines `sealed interface ChatListItem` with only 3 variants: `MessageItem`, `DateSeparator`, and `PeerIntroItem`.

3. **`ChatMessageMapper.kt` forces system rows into `ChatListItem.MessageItem` with `isOwn = true`**
   - In [ChatMessageMapper.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatMessageMapper.kt#L281-L375):
     - Line `281` detects `val isSystem = msg.messageType == "system"`, and uses `!isSystem` solely to block bubble clustering (`L288, L298`) and sender name display (`L350`).
     - Line `282` computes `val isOwn = msg.senderId == currentUserId` without checking `!isSystem`. For the group creator (`v_caller == currentUserId`), `isOwn` evaluates to `true`.
     - Lines `339-375` unconditionally emit `ChatListItem.MessageItem(...)`. (Secondary bug at `L351`: `showSenderAvatar = !isDirect && !isOwn && !groupedWithNext` omits `!isSystem`, so for other participants, a system message even triggers a `32.dp` avatar slot at `ChatBubble.kt:343-357`.)

4. **`ChatBubbleContent` renders it as a standard blue sent bubble with timestamp & checkmarks**
   - Because `ChatMessageList.kt:250-268` only handles `MessageItem`, `DateSeparator`, and `PeerIntroItem`, `"created the group"` enters `ChatMessageRow` (`ChatBubble.kt:340`, `Arrangement.End` when `isOwn == true`) and `ChatBubbleContent`'s standard text branch (`ChatBubble.kt:837-973`), drawing `chatColors.sentBubbleBg` (`0xFF0064E0` / `0xFF1668E3`), `ChatInlineMetaTextBlock` (`L951-L966`) with `item.formattedTime`, `isOwn = true`, and `ChatStatusIcon` delivery checkmarks.

---

### 4. Dark Screen Overlay Flicker & Composer Jump

#### A. Dark Screen / Status-Bar / Overlay Flicker on Input Tap/Focus
1. **Conflicting `UpdateStatusBarAppearance` `SideEffect`s firing on every recomposition**
   - [ChatHeader.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatHeader.kt#L117-L119) calls `UpdateStatusBarAppearance(isLightSurface = chatColors.headerGlassBg.luminance() > 0.5f)` unconditionally inside `ChatHeader`, which executes a `SideEffect` (`ChatHeader.kt:616-626`) mutating `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightSurface` on **every recomposition** of `ChatHeader` (e.g., typing indicator changes, scroll state, header updates).
   - Meanwhile, [BuildXTheme.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/theme/BuildXTheme.kt#L753-L763) runs its own root `SideEffect` setting `controller.isAppearanceLightStatusBars = !useDark` and `controller.isAppearanceLightNavigationBars = !useDark` on root recomposition (which can disagree with or re-apply against `ChatHeader.kt` / `ChatAndDrawerScreen.kt:196` / `DmInboxScreen.kt:230`).

2. **IME & Window Insets / Backdrop Scrim Interactions**
   - Unlike `ChatAndDrawerScreen.kt` / `DmInboxScreen.kt` which apply `.imePadding()` to specific containers, `DmConversationScreen.kt:315-316, 522` applies `.windowInsetsPadding(navAndImeInsets)` (`WindowInsets.navigationBars.union(WindowInsets.ime)`) on the bottom overlay `Column`, which also carries `.onSizeChanged { bottomOverlayHeightPx = it.height }` (`L521`), driving `listBottomPadding` (`L320`). During keyboard open/focus (`BasicTextField` at `ChatComposer.kt:363-384`), every IME animation frame alters `bottomOverlayHeightPx` -> `listBottomPadding`, forcing `ChatMessageList` (`LazyColumn` `contentPadding.bottom`) and `ChatWallpaperLayer` to re-layout continuously.
   - When `activeMenuMessage != null` is active or dismissing as the user taps into the composer (`DmConversationScreen.kt:569-594`, `ChatMessageList.kt:190-222`), `menuFocusProgress` animates `menuBackdropScrim` (`Color(0x2E000000)` light / `Color(0x4D000000)` dark at `ChatTokens.kt:123, 176`) using `ChatMotion.interactiveSpring()` (`dampingRatio = 0.72f`, `stiffness = 480f`, `ChatTokens.kt:513-514`). Because `interactiveSpring` is underdamped, `menuFocusProgress` can overshoot (`> 1f`, clamped by `coerceIn`) or bounce on dismiss/re-trigger, causing `menuBackdropColor` (`ChatMessageList.kt:196-198, 216-221`) to pulse/flicker when the menu closes or `activeMenuMessage` state transitions.
   - Note also on the AI chat surface ([ChatAndDrawerScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/ChatAndDrawerScreen.kt#L201-L210)): `hasModalOverlay` drives `modalBackdropBlurDp` with `compatRenderEffectBlur(modalBlurDp)` on the entire `MainChatSurface` (`L654-L658`).

#### B. Composer Jump When Sending a Reply
1. **Single synchronous state mutation drops reply banner, attachments, and text simultaneously**
   - In [BuildXViewModel.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/viewmodel/BuildXViewModel.kt#L4033-L4047) (`sendDmMessage`), one `_uiState.update` atomically:
     - Appends `optimisticMsg` to `dmMessages` (`L4036-L4039`)
     - Clears `dmComposerText = ""` (`L4040`)
     - Sets `justSentClientTempIds` & `lastOptimisticMessageId` (`L4042-L4043`)
     - Clears `dmStagedAttachments = emptyList()` (`L4044`)
     - Clears `dmReplyingToMessage = null` (`L4045`)

2. **Competing height animations + live `contentPadding.bottom` mutation on the exact insert frame**
   - In [ChatComposer.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatComposer.kt#L219-L277):
     - The inner `Column` applies `.animateContentSize(animationSpec = ChatMotion.layoutGestureSpring())` (`L222-L228`).
     - Its child `AnimatedVisibility(visible = replyingTo != null || editing != null)` (`L230-L257`) *also* runs `slideOutVertically` + `shrinkVertically(ChatMotion.layoutGestureSpring())` + `fadeOut`, creating nested/competing size animations (and if `stagedAttachments` were present, `if (stagedAttachments.isNotEmpty())` at `L276` has zero `AnimatedVisibility`, collapsing instantaneously on frame 0).
     - `targetCorner` (`L145-L153`) simultaneously animates back from `composerExpandedCorner` to `composerDefaultCorner`.
   - Meanwhile, [DmConversationScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/DmConversationScreen.kt#L314, L320, L521) feeds `bottomOverlayHeightPx` -> `listBottomPadding` directly into `ChatMessageList`'s `LazyColumn(contentPadding = PaddingValues(..., bottom = bottomContentPadding))` (`ChatMessageList.kt:243`).
   - On the exact frame `optimisticMsg` is inserted at index `0` (`reverseLayout = true`) and `ChatMessageList.kt:160-166` / `animateItem(placementSpec = ChatMotion.layoutGestureSpring())` (`L260-L264`) + `ChatBubbleEntryWrapper` send-flight (`composerFlightBoundsInWindow` at `ChatComposer.kt:200-202` vs `bubbleBoundsInWindow` at `ChatBubble.kt:363-367`) begin animating, `bottomContentPadding` shrinks on every frame as the reply banner collapses — causing the `LazyColumn` viewport floor, item placement, and composer dock height to jump/jolt together.

---

### 5. Broken Message Context Menu

1. **White `ActionMenuPopup` box with text-left / icon-right rows overlaps the bubble; no floating emoji reaction bar**
   - In [DmConversationScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/DmConversationScreen.kt#L397-L401, L723-L810), long-pressing a message sets `activeMenuMessage = msg` and builds up to 7 actions (`Reply`, `Copy`, `Select text`, `Edit`, `Pin`, `Forward`, `Delete`/`Report`) in `buildDmMessageMenuItems`.
   - Inside [ChatBubble.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatBubble.kt#L969-L971, L2036-L2073), `ChatBubbleActionPopup` opens a native Android `Popup(properties = PopupProperties(focusable = true, ...))` wrapping `ActionMenuPopup`.
   - [BuildXSheets.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/components/BuildXSheets.kt#L233-L389):
     - `ActionMenuPopup` (`L233-L290`) renders an opaque `colors.cardBackground` (solid white `#FFFFFF` in light mode) `200.dp` wide column box (`L268, L276`), NOT a glassmorphic/compact left-icon menu.
     - `OverflowMenuRow` (`L349-L389`) places `Text(..., modifier = Modifier.weight(1f))` on the **start/left** and `Icon` on the **end/right** (`L366-L380`).
     - `BubblePopupPositionProvider` (`L296-L324`): when `spaceBelow < popupContentSize.height + marginPx` and `spaceBelow >= anchorBounds.top`, or when a 7-item menu (`~340.dp` tall) doesn't fit cleanly above or below, `y.coerceIn(marginPx, windowSize.height - popupContentSize.height - marginPx)` (`L322`) clamps the large white box directly over the selected message bubble.
   - **Missing reaction bar**: Searching the entire `app/src/main/java/app/buildx/chat` tree confirms there is **no** floating emoji reaction bar (`👍 ❤️ 🔥 😍 👏 🤔` or any reaction UI) in `ChatBubble.kt`, `BuildXSheets.kt`, or `DmConversationScreen.kt`.

2. **Inconsistent background blur & focus isolation**
   - In [ChatMessageList.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatMessageList.kt#L190-L222, L275-L280), `menuBackdropColor` is drawn **behind** the `LazyColumn`, while only sibling `MessageItem`s receive `Modifier.blur((2.5f * menuFocusProgress).dp)` (`L276-L278`) and `dimAlpha = 0.55f` (`ChatBubble.kt:310`), and `DateSeparator`/`PeerIntroItem` only receive `nonMessageDimAlpha`. Top `ChatHeader` and bottom `ChatComposer` receive `chromeScrimColor = Color.Transparent` (`DmConversationScreen.kt:333, 569-594`) with zero blur/dim.
   - Because `ChatBubbleActionPopup` uses a separate Android `Popup` window rather than an overlay layer above a full-screen blurred/dimmed backdrop containing a sharp lifted copy/portal of the selected message + reaction pill + menu, the popup window sits awkwardly inside `LazyColumn`'s item bounds and clashes with partial per-item blurs.
   - Additionally, on the AI Chat screen ([ChatAndDrawerScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/ChatAndDrawerScreen.kt#L201-L210, L654-L658)), `compatRenderEffectBlur(modalBlurDp)` is placed on the entire `MainChatSurface` wrapper `Box`, blurring the entire screen including underlying message content whenever a modal/select-text overlay opens.

---

### 6. Illogical Chat/Group Info Screen (`ChatInfoScreen.kt` & `DmChatInfoSheet.kt`)

1. **Scrolling pushes the group avatar into the system status bar clock**
   - In [ChatInfoScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatInfoScreen.kt#L187-L234):
     - The root layout is a single `Column` with `.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())` (`L187-L194`) and **no pinned header bar**, **no `zIndex` separation**, and **no top protective scrim**.
     - The `HeaderCircleButton` back button (`L196-L210`) is placed *inside* the scrolling `Column` rather than pinned above it.
     - As soon as the user scrolls down, the back button scrolls off-screen and the `92.dp` centered group avatar (`L219-L234`, plus its `16.dp` `softShadow` halo extending upward) travels straight up against/into the top status-bar region where the system clock sits (and in [DmChatInfoSheet.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/DmChatInfoSheet.kt#L156-L168), the sheet takes `.fillMaxHeight(0.92f)` with no `statusBarsPadding()`, allowing tall sheets/content to crowd the status bar).

2. **`Add members` is duplicated twice**
   - In [ChatInfoScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatInfoScreen.kt):
     1. **Quick Circle Action #1** (`L435-L445`): for `isAdmin`, renders `ChatInfoQuickCircleAction(iconRes = R.drawable.ic_plus, label = stringResource(R.string.dm_info_add_label), ...)` (`"Add"` / `"إضافة"` in `dm_strings.xml:185`), toggling `isMembersExpanded = true; isAddingMembersOpen = !isAddingMembersOpen`.
     2. **Vertical Control Row** (`L540-L548`): again under `if (isAdmin)`, renders `ChatInfoControlRow(iconRes = R.drawable.ic_plus, title = stringResource(R.string.dm_add_members), ...)` (`"Add members"` / `"إضافة أعضاء"` in `dm_strings.xml:132`), toggling the exact same `isMembersExpanded = true; isAddingMembersOpen = !isAddingMembersOpen` state.

3. **`Leave group` sits above the Shared Media tab**
   - In [ChatInfoScreen.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/chat/ChatInfoScreen.kt#L738-L780):
     - `ChatInfoControlRow(iconRes = R.drawable.ic_leave_group, title = stringResource(R.string.dm_leave_group), isDestructive = true, ...)` is emitted at `L738-L744` (immediately after the collapsible members list), **above** `Spacer(14.dp)` (`L747`) and the Bottom Shared Media Section (`L750-L780`, media tab icon + underline + 3-column grid).
     - By contrast, [DmChatInfoSheet.kt](file:///c:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/DmChatInfoSheet.kt#L375-L425) renders `SharedMediaGrid` first (`L375`) and places the destructive `InfoRow(dm_leave_group)` at the bottom (`L419-L424`).
