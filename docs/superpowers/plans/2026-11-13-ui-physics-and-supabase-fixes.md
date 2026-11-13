# Build X UI/UX, Physics & Live Supabase Remediation Plan

> **For agentic workers:** Use parallel subagents or focused module edits by layer (Supabase DB -> Repository/ViewModel -> Theme/Scrim -> Bubbles/Mapper/Composer -> Wallpaper Studio -> Tests) and verify each layer with live SQL checks and Gradle unit/screenshot tests before claiming completion.

**Goal:** Fix the `"The requested item was not found."` (`PGRST202` / 404) failure on Note Likes and Chat Wallpapers, eliminate striped `softShadow` banding and heavy grey press-menu dimming, restructure message bubbles to place group sender display name (+ `VerifiedBadge`) and quoted replies inside the bubble with crisp `HH:mm` + `✓`/`✓✓` read receipts and real-time broadcasts, upgrade composer-to-bubble send physics and composer polish, and overhaul `ChatWallpaperPickerScreen`.

**Architecture:** Adapt live SQL migrations to the actual Supabase schema (`public.notes` + `public.note_likes`, `dm_participants`, `dm_conversations`, `profiles`, `notifications`), back repository mutations with local-first `SharedPreferences` storage and direct PostgREST fallbacks, replace multi-pass `drawRoundRect` shadow simulation with GPU-compatible `BlurMaskFilter` Gaussian blurs, and enforce strict Compose measurement (`MeasurePolicy` + intrinsic width support) so in-bubble sender headers, quoted reply cards, and bottom-right `HH:mm` metadata never collide across LTR or RTL text.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose BOM 2024.10.00, Material 3, Supabase Kotlin SDK 3.0.2 (`auth-kt`, `postgrest-kt`, `storage-kt`, `realtime-kt`), Coil 2.7.0, PostgreSQL 15+ / PostgREST on Supabase (`owgpuolvfqfjhezuohyf`), Robolectric 4.13, Roborazzi 1.29.0.

---

### Schema & Symbol Adaptation Notes (Verified Against Live Repo & DB)
- Live Supabase `owgpuolvfqfjhezuohyf` has **`public.notes`** (not `public.feed_notes`) and latest applied migration `20261112000008`. Therefore `20261113000003` targets **`public.note_likes REFERENCES public.notes(id)`**, updates `notifications_type_check` to include `'note_like'`, updates `get_user_notifications`, recreates `get_followed_active_notes()`, `get_my_active_note()`, `create_note()`, `mark_dm_read()`, `mark_conversation_read()`, `toggle_note_like(uuid)`, `set_chat_wallpaper(...)`, `get_dm_conversation_detail(uuid)`, sets `REPLICA IDENTITY FULL` on `dm_participants` and `dm_messages`, and reloads PostgREST schema/config.
- Direct-table PostgREST fallbacks require `SELECT`/`INSERT`/`DELETE` RLS policies on `public.note_likes` for `authenticated` (`auth.uid() = user_id`), and an `UPDATE` RLS policy + column-tamper guard trigger on `public.dm_participants` restricting direct `authenticated` updates to `wallpaper_*` columns only.
- Existing `ic_check.xml` was used for `SENT`; create dedicated `res/drawable/ic_dm_single_check.xml` matching `ic_dm_double_check.xml` (`24dp`, stroke `1.85`).
- `profile/{username}` route resolves by handle/username; clicking a group sender header must resolve handle/username (`resolvedSenderHandle`) while rendering **only** `displayName` (`resolvedSenderDisplayName`), never `@username` or handle.

---

### Task 1: Supabase Migration & Live DB Verification

**Files:**
- Create: `supabase/migrations/20261113000003_ensure_live_note_likes_and_wallpapers_rpcs.sql`

- [ ] **Step 1: Write `20261113000003_ensure_live_note_likes_and_wallpapers_rpcs.sql`**
  - Create `public.note_likes(note_id uuid REFERENCES public.notes(id) ON DELETE CASCADE, user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE, created_at timestamptz DEFAULT now(), PRIMARY KEY (note_id, user_id))` + indexes + `GRANT SELECT, INSERT, DELETE TO authenticated`.
  - Configure RLS on `public.note_likes` (`SELECT TO authenticated` for visible unblocked active notes / own notes, `INSERT` / `DELETE` restricted to `user_id = auth.uid()`).
  - Add `'note_like'` to `notifications_type_check` constraint and include `'note_like'` join in `get_user_notifications`.
  - Add wallpaper columns on `dm_conversations`, `dm_participants`, and `profiles`.
  - Add `dm_participants_update_own_wallpaper` RLS policy (`TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid())`) + `BEFORE UPDATE` trigger blocking client direct modifications to non-wallpaper columns (`role`, `status`, `is_muted`, `is_pinned`, `marked_unread`, etc.) unless invoked inside `SECURITY DEFINER` (`pg_trigger_depth()` / session role check via `current_setting('role')` / `current_user = 'authenticated'` carefully verifying security definer owner!). Wait — note: in Postgres, inside a `SECURITY DEFINER` function owned by `postgres`, `current_user` is `'postgres'` while `session_user` / JWT `request.jwt.claims` is `authenticator`/`authenticated`! Checking `IF current_user = 'authenticated' THEN ...` cleanly lets `SECURITY DEFINER` functions mutate `role`/`status`/`last_read_at` while blocking raw PostgREST client tampering!
  - Set `REPLICA IDENTITY FULL` on `public.dm_participants` and `public.dm_messages`.
  - Create/replace `toggle_note_like(p_note_id uuid)`, `get_followed_active_notes()`, `get_my_active_note()`, `create_note(note_content text)`, `mark_dm_read(...)`, `mark_conversation_read(...)`, `set_chat_wallpaper(...)` (with exact default args & `jsonb` return), `get_dm_conversation_detail(p_conversation_id uuid)` (participant -> conversation -> profile fallback), and `REVOKE`/`GRANT EXECUTE TO authenticated`.
  - Run `NOTIFY pgrst, 'reload schema'; NOTIFY pgrst, 'reload config';`.
- [ ] **Step 2: Apply migration to live Supabase `owgpuolvfqfjhezuohyf`**
  - Call `supabase:apply_migration` (`name = "ensure_live_note_likes_and_wallpapers_rpcs"`).
  - If applied, query `pg_proc`, `pg_policies`, `pg_class.relreplident`, and `supabase:get_advisors` (`security`) to confirm live deployment and zero new RLS issues. (If client confirmation UI auto-cancels `apply_migration`, report the exact tool block honestly without bypassing confirmation gates.)

---

### Task 2: Repository Fallbacks & Realtime Read Receipt Broadcasts

**Files:**
- Modify: `app/src/main/java/app/buildx/chat/data/repository/BuildXRepository.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/viewmodel/BuildXViewModel.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatMessageMapper.kt` (add pure monotonic participant read-state merge helper)

- [ ] **Step 1: Upgrade `BuildXRepository.toggleNoteLike`**
  - Call `supabase.postgrest.rpc("toggle_note_like", buildJsonObject { put("p_note_id", cleanId) })`.
  - On any non-cancellation exception (including `NotFoundRestException` / schema cache miss), execute direct `supabase.postgrest.from("note_likes")` toggle + row count query and return `NoteLikeToggleResult(success = true, noteId = cleanId, liked = nowLiked, likesCount = updatedRows.size)`.
- [ ] **Step 2: Upgrade `BuildXRepository.setChatWallpaper`**
  - Normalize inputs (`normalizedId`, `normalizedCustom`, `normalizedDim = dim.coerceIn(0f, 0.70f)`).
  - Persist `resolvedLocal` to `SharedPreferences` **before** remote sync (`saveDefaultWallpaperLocal` + `clearAllPerChatWallpapersLocal`, or `saveConversationWallpaperLocal`).
  - Pass all 6 explicit JSON keys (`p_conversation_id` / `JsonNull`, `p_wallpaper_id`, `p_custom_url` / `JsonNull`, `p_dim`, `p_blur`, `p_apply_to_all`).
  - Execute `rpc("set_chat_wallpaper", params)` inside nested `runCatching`; on non-cancellation failure, fall back inside nested `runCatching` to `supabase.from("profiles").update(...)` (when `applyToAll || conversationId.isNullOrBlank()`) or `supabase.from("dm_participants").update(...)` (when per-conversation).
  - Return `Result.success(resolved)` cleanly after local persistence + best-effort remote sync.
- [ ] **Step 3: Add real-time `"read_receipt"` broadcast & listener in `BuildXViewModel.kt`**
  - Add `private fun broadcastDmReadReceipt(conversationId: String, lastReadMessageId: String?)` sending `{user_id, conversation_id, last_read_message_id, read_at}` on `dmRoomChannel` (`event = "read_receipt"`).
  - Call it from `markDmRead`, `enterDmRoom` (after channel is ready / `markDmRead`), and `subscribeToDmRoom` `msgFlow` when marking incoming messages read.
  - Subscribe to `channel.broadcastFlow<JsonObject>(event = "read_receipt")` inside `subscribeToDmRoom` and immediately update `activeDmDetail.participants` with monotonic read merge + trigger `getDmConversationDetail(conversationId)`.

---

### Task 3: Smooth Gaussian Shadows & Zero-Dimming Message Menu

**Files:**
- Modify: `app/src/main/java/app/buildx/chat/ui/theme/BuildXTheme.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/screens/DmConversationScreen.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatMessageList.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatBubble.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/components/BuildXSheets.kt` (ensure `ActionMenuPopup` width/padding/border tokens cleanly match)

- [ ] **Step 1: Replace striped `Modifier.softShadow` loop in `BuildXTheme.kt`**
  - Add optional `spread: Dp = 0.dp` (defaulted for binary source compatibility with all existing callers) and replace `for (i in 1..layers)` with `drawWithCache` + `android.graphics.Paint(ANTI_ALIAS_FLAG)` + `BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)` drawn via `canvas.nativeCanvas.drawRoundRect`.
  - Soften `LightBuildXColors` (`composerShadowColor = Color.Black.copy(alpha = 0.05f)`, `headerHaloShadowColor = Color.Black.copy(alpha = 0.05f)`, `innerControlShadowColor = Color.Black.copy(alpha = 0.035f)`) and `DarkBuildXColors` (`composerShadowColor = Color.Black.copy(alpha = 0.20f)`, `headerHaloShadowColor = Color.Black.copy(alpha = 0.22f)`, `innerControlShadowColor = Color.Black.copy(alpha = 0.14f)`).
- [ ] **Step 2: Eliminate grey press-menu dimming across `DmConversationScreen.kt`, `ChatMessageList.kt`, `ChatBubble.kt`**
  - In `DmConversationScreen.kt`: keep top/bottom dismiss hitboxes completely transparent (`chromeScrimColor = Color.Transparent`), trigger `activeMenuMessage = msg` strictly via `onMessageLongPress` (only when non-deleted or menu has items), and make `onMessageTap` retry failed messages or dismiss any open menu instead of dimming the screen.
  - In `ChatMessageList.kt`: set `menuBackdropColor = Color.Transparent`, `nonMessageDimAlpha = 1f`, pass `isDimmed = false` to `ChatMessageRow`, keep transparent click-outside dismissal.
  - In `ChatBubble.kt`: remove `0.42f` `rowDimAlpha` dimming (`rowDimAlpha = 1f`), preserve `focusScale = 1.02f`, replace heavy `scrimColor.copy(alpha = 0.45f)` focus shadow with a gentle subtle shadow + subtle contour border on `isMenuOpen`, and refine `ChatBubbleActionPopup` (`200.dp` width, `RoundedCornerShape(16.dp)`, `0.8.dp` `subtleSurfaceBorder`, `8.dp` vertical offset, guard `menuItems.isNotEmpty()`).

---

### Task 4: Message Bubbles, In-Bubble Group Header + `VerifiedBadge`, Quoted Replies & Receipts

**Files:**
- Create: `app/src/main/res/drawable/ic_dm_single_check.xml`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatTokens.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatUiModels.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatMessageMapper.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatBubble.kt`

- [ ] **Step 1: Add `ic_dm_single_check.xml` & `senderNameColor` helper**
  - Create crisp single checkmark drawable (`24x24` viewport, `1.85` stroke width).
  - Add deterministic `fun senderNameColor(senderId: String, isDark: Boolean): Color` in `ChatTokens.kt`.
  - Update `ChatColors.readCheckAccent` / outgoing check styling so `READ` uses bright `Color.White` (`1f` alpha).
- [ ] **Step 2: Update `ChatUiModels.kt` & `ChatMessageMapper.kt`**
  - Add `resolvedSenderName: String? = null`, `resolvedSenderHandle: String? = null`, `senderIsVerified: Boolean = false` to `ChatListItem.MessageItem`.
  - Resolve `resolvedSenderName` strictly from `msg.senderDisplayName?.takeIf { it.isNotBlank() } ?: participantLookup[msg.senderId]?.displayName?.takeIf { it.isNotBlank() }` (never `@username`/`handle`).
  - Compute `showSenderName = !isDirect && !isOwn && !isDeleted && !groupedWithPrev && !resolvedSenderName.isNullOrBlank()`.
  - Change `ChatMessageMapper.formatShortTime` pattern from `"h:mm a"` to 24-hour `"HH:mm"`.
  - Update `computeBubbleMetaPlacement` so metadata (`HH:mm` + `✓`/`✓✓`) is anchored at the bottom-right corner (`metaX = (bubbleWidth - metaWidthPx).coerceAtLeast(0)`):
    - LTR (`!isRtlContent`): inline at bottom-right if `lastLineWidthPx + inlineGapPx + metaWidthPx <= safeMaxWidth`; else wrap below (`metaY = textHeightPx + wrapTopGapPx`, `metaX = bubbleWidth - metaWidthPx`).
    - RTL (`isRtlContent`): for 1-line text fitting inline (`lineCount == 1 && lastLineWidthPx + inlineGapPx + metaWidthPx <= safeMaxWidth`), place `textX = (bubbleWidth - textWidthPx - inlineGapPx - metaWidthPx).coerceAtLeast(0)` and `metaX = bubbleWidth - metaWidthPx`; for multi-line RTL or overflow, place `textX = (bubbleWidth - textWidthPx).coerceAtLeast(0)` and wrap `metaY = textHeightPx + wrapTopGapPx` at `metaX = (bubbleWidth - metaWidthPx).coerceAtLeast(0)` so Arabic text is never overlapped.
- [ ] **Step 3: Restructure `ChatBubble.kt` to match Telegram dark/light layout**
  - Remove external `if (item.showSenderName)` text above the bubble in `ChatMessageRow`; thread `onSenderProfileClick: (String) -> Unit` down into `ChatBubbleContent` (passing `resolvedSenderHandle` to `onViewProfileClick`).
  - Place in-bubble Group Sender Header (`Text(displayName, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = senderNameColor(...))` + `if (item.senderIsVerified) VerifiedBadge(size = 13.dp)`) INSIDE the top of the bubble container.
  - Move `ChatQuotedReplyCard` (and `ChatNoteReplyPill`) INSIDE the bubble container (below sender header if present, `6.dp` above body/media). Style `ChatQuotedReplyCard` with `RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 10.dp, bottomEnd = 10.dp)`, `widthIn(min = 140.dp)`, `fillMaxWidth()`, `3.dp` leading bar (`Color.White` on outgoing, deterministic `senderNameColor(reply.senderId, isDark)` / `#F43F5E` on incoming), background (`Color.White.copy(alpha = 0.16f)` on outgoing; accent-tinted `0.14f` dark / `0.10f` light on incoming), bold sender display name + 1-line ellipsis body.
  - Ensure `ChatInlineMetaTextBlock` uses an explicit `MeasurePolicy` with intrinsic width calculations (safe under `IntrinsicSize.Max` bubble columns!) AND honors `constraints.minWidth` so when a wider quote/sender header stretches the bubble width, `HH:mm` + `✓`/`✓✓` pins cleanly to the far bottom-right edge of the bubble!
  - Update `ChatStatusIcon` to use `ic_dm_clock` (`13.dp`, `0.70f` white), `ic_dm_single_check` (`14.dp`, `0.82f` white for `SENT`/`DELIVERED`), `ic_dm_double_check` (`15.dp`, `1.0f` `Color.White` for `READ`).

---

### Task 5: Physics Send Transition, `ChatComposer` Polish & `ChatWallpaperPickerScreen` Studio Overhaul

**Files:**
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatTokens.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatBubble.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatComposer.kt`
- Modify: `app/src/main/java/app/buildx/chat/ui/chat/ChatWallpaperPickerScreen.kt`

- [ ] **Step 1: Upgrade `ChatMotion` & `ChatBubbleEntryWrapper` (`ChatTokens.kt`, `ChatBubble.kt`)**
  - Update `ChatMotion` send physics constants: `SendLiftStartTranslationY = 48.dp`, `SendLiftStartTranslationX = 18.dp`, `SendLiftStartScaleX = 0.78f`, `SendLiftStartScaleY = 0.72f`, `SendLiftDampingRatio = 0.76f`, `SendLiftStiffness = Spring.StiffnessMedium`, `SendFadeDurationMs = 120`.
  - Animate `sendTranslationY`, `sendTranslationX` (`if (isRtl) -startX else startX`), `sendScaleX`, `sendScaleY` concurrently using `Animatable` + `spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMedium)`, plus `alpha` `0f -> 1f` over `120ms` (`tween(120)`), with `transformOrigin = TransformOrigin(if (isRtl) 0f else 1f, 1f)`.
- [ ] **Step 2: Refine `ChatComposer.kt`**
  - Update border to `0.8.dp`, corner radius `26.dp -> 20.dp` when expanded (`hasTopBanner || lineCount > 1`), balanced inner padding (`start = 6.dp, end = 6.dp, vertical = 5.dp` on main row while preserving text readability).
  - Add snappy Send button spring pulse (`scale: 0.84f -> 1.0f` with `Spring.DampingRatioMediumBouncy`) synchronized on Send tap.
- [ ] **Step 3: Overhaul `ChatWallpaperPickerScreen.kt`**
  - Top live interactive preview card (`210.dp`, `RoundedCornerShape(22.dp)`) displaying realistic incoming & outgoing bubbles (with an in-bubble quoted reply card and `"23:01"` + white `✓✓` receipt) over `ChatWallpaperLayer(config = livePreviewConfig)`.
  - Overlay glassmorphic `Blur` toggle pill and `Reset to default` pill cleanly at top-right / bottom-left corners inside the preview frame.
  - Combine `"Choose from Gallery"` button + sleek compact `Dimming (0%..70%)` slider into one unified control strip (`padding(horizontal = 16.dp, vertical = 6.dp)`).
  - Keep all 6 category filter chips (`All` + 6 categories) and 24+1 wallpapers in 3-column grid (`aspectRatio(0.68f)`, `RoundedCornerShape(16.dp)`), rendering mini chat-bubble silhouettes inside each swatch, clean bottom title pill, and animated `2.dp` `colors.primaryBlue` selection ring + top-right checkmark badge.

---

### Task 6: Unit Tests, Screenshot Baselines, Zero-Emoji Audit & Full Build

**Files:**
- Modify: `app/src/test/java/app/buildx/chat/ChatMessageMapperTest.kt`
- Modify: `app/src/test/java/app/buildx/chat/DmMessagingTest.kt`
- Modify: `app/src/test/java/app/buildx/chat/DmScreenshotTest.kt`

- [ ] **Step 1: Update & expand unit tests**
  - Update `ChatMessageMapperTest`: verify `"HH:mm"` formatting, strictly display-name resolution (`resolvedSenderName` never uses `@username`/handle), updated `computeBubbleMetaPlacement` bottom-right placement across LTR & RTL (single-line inline vs multi-line wrap), and monotonic participant read-merge helper.
  - Update `DmMessagingTest` & `DmScreenshotTest`: remove `\uD83C\uDF89\uD83D\uDD25` emoji fixture in `DmScreenshotTest:646` (zero-emoji compliance), keep all 24-preset/6-category/deserialization tests green, add test coverage for repository `setChatWallpaper` local-first preference fallback & `toggleNoteLike` resilient model behavior.
- [ ] **Step 2: Record/Verify Roborazzi baselines & inspect screenshots**
  - Run `./gradlew :app:recordRoborazziDebug --console=plain` for DM chat / composer / wallpaper picker baselines after UI updates, and visually inspect the rendered PNGs (`dm_chat_dark.png`, `dm_group_chat_light.png`, `dm_matrix_dark_rtl.png`, `dm_composer_states.png`, `dm_wallpaper_picker_light.png`, `dm_wallpaper_picker_dark.png`).
- [ ] **Step 3: Full verification gate**
  - Run zero-emoji regex scan across all `.kt`, `.xml`, `.sql`, and test files (including `\uD83C`-style surrogate escapes).
  - Run `.\gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:verifyRoborazziDebug --console=plain` and verify `BUILD SUCCESSFUL` with zero failures.
