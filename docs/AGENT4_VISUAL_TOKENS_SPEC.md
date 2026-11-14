# AGENT 4 — Chat Visual Tokens, States & Micro-UI Specification

> **Scope**: Exclusively Chat & DM Conversation / Inbox UI tokens, glassmorphic surfaces, typography, motion physics, and chat vector drawables. Profile and Settings screens/drawables are strictly excluded.
>
> **Sources Verified**:
> - Codebase design system: `app/src/main/java/app/buildx/chat/ui/chat/ChatTokens.kt`, `app/src/main/java/app/buildx/chat/ui/theme/BuildXTheme.kt`, `docs/design/SPEC.md`, `docs/design/tokens.json`.
> - Telegram Android reference (`org.telegram.ui.ActionBar.Theme.java` keys & Telegram iOS/Android typography/blur/bubble conventions via Web Search).
> - Note: Figma MCP (`figma_figma`) and Canva MCP (`canva`) tools were not available in this worker runtime; all tokens are reconciled directly against the codebase's `tokens.json` / `SPEC.md` and Telegram's `Theme.java` design model.

---

## 1. Color & Surface Tokens (`ChatColors` / `toChatColors()`)

Every token is exposed in `app.buildx.chat.ui.chat.ChatColors` via `BuildXColors.toChatColors()`, maintaining full backward compatibility with all existing callers.

### 1.1 Message Bubbles, Quotes & Text

| Token | Telegram `Theme.java` Reference | Light Value | Dark Value | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `pageBackground` | `key_chat_wallpaper` / `windowBackgroundWhite` | `#FFFFFFFF` | `#FF0E1012` | Base canvas behind chat/inbox (or wallpaper backdrop) |
| `sentBubbleBg` | `key_chat_outBubble` | `#FF0064E0` | `#FF1668E3` | Outgoing bubble fill |
| `sentBubbleText` | `key_chat_messageTextOut` | `#FFFFFFFF` | `#FFFFFFFF` | Outgoing message body text |
| `sentMetaText` | `key_chat_outTimeText` | `#B8FFFFFF` (`0.72f` alpha) | `#BDFFFFFF` (`0.74f` alpha) | Timestamp & "Edited" label inside outgoing bubble |
| `receivedBubbleBg` | `key_chat_inBubble` | `#FFF1F3F2` | `#FF1D2126` | Incoming bubble fill |
| `receivedBubbleText` | `key_chat_messageTextIn` | `#FF0A0A0A` | `#FFF3F5F6` | Incoming message body text |
| `receivedMetaText` | `key_chat_inTimeText` | `#990A0A0A` (`0.60f` alpha) | `#99F3F5F6` (`0.60f` alpha) | Timestamp & "Edited" label inside incoming bubble |
| `quotedBarSent` | `key_chat_outReplyLine` | `#FFFFFFFF` | `#FFFFFFFF` | `3dp` vertical accent bar on outgoing quote |
| `quotedBgSent` | `key_chat_outReplyBackground` | `#29FFFFFF` (`0.16f` alpha) | `#29FFFFFF` (`0.16f` alpha) | Quote card fill inside outgoing bubble |
| `quotedBarReceived` | `key_chat_inReplyLine` | `#FF0064E0` (+ per-sender palette) | `primaryBlue` (`#FF3B8BF7`) (+ per-sender palette) | `3dp` vertical accent bar on incoming quote |
| `quotedBgReceived` | `key_chat_inReplyBackground` | `#1A0064E0` (`0.10f` alpha) | `primaryBlue @ 0.14f` | Quote card fill inside incoming bubble |
| `deletedPillBg` | — | `#FFF8F9F8` | `#FF15181C` | Deleted message tombstones |
| `deletedPillBorder` | — | `#FFE1E4E2` | `#FF282D34` | Deleted pill `1dp` border |
| `deletedPillText` | — | `#FF6E7274` | `#FF9BA0A5` | Italic deleted message text |
| `replyHighlightOverlay` | `key_chat_messagePanelCursor` / flash | `#1F0064E0` (`0.12f` alpha) | `primaryBlue @ 0.22f` | Transient flash overlay on jump-to-message |

### 1.2 Delivery Checkmarks, Status Indicators, Badges & Dividers

| Token | Telegram `Theme.java` Reference | Light Value | Dark Value | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `sentCheckPendingTint` | `key_chat_outSendingIcon` | `#B3FFFFFF` (`0.70f` alpha) | `#B3FFFFFF` (`0.70f` alpha) | Clock icon while `SENDING` |
| `sentCheckDeliveredTint` | `key_chat_outSentCheck` | `#D6FFFFFF` (`0.84f` alpha) | `#D6FFFFFF` (`0.84f` alpha) | Single checkmark `SENT` / `DELIVERED` |
| `readCheckAccent` | `key_chat_outSentCheckRead` | `#FFFFFFFF` | `#FFFFFFFF` | Double checkmark `READ` inside blue bubble |
| `inboxCheckReadAccent` | `key_chats_sentReadCheck` | `#FF0064E0` | `#FF34C759` | Double checkmark `READ` on inbox list rows |
| `failedAccent` | `key_text_RedRegular` | `#FFC3192E` | `#FFFF5A6E` | Failed delivery badge (`ic_dm_error`) |
| `onlineDot` | `key_chats_onlineCircle` | `#FF28CD41` | `#FF34C759` | Active online presence dot (`10dp`) |
| `onlineDotRing` | `key_chats_background` | `#FFFFFFFF` | `#FF0E1012` | `2dp` cutout border around online dot |
| `typingDot` | `key_chat_status` | `#FF0064E0` | `#FF3B8BF7` | Animated 3-dot typing indicator |
| `unreadBadgeBg` | `key_chats_unreadCounter` | `#FF0064E0` | `#FF3B8BF7` | Active unread count pill |
| `unreadBadgeMutedBg` | `key_chats_unreadCounterMuted` | `#FFB8BDC2` | `#FF4B525B` | Muted conversation unread pill |
| `unreadBadgeText` | `key_chats_unreadCounterText` | `#FFFFFFFF` | `#FFFFFFFF` | Unread count digits (`tnum`) |
| `pinnedBadgeTint` | `key_chats_pinnedIcon` | `#FF8E9398` | `#FF7D848D` | Pinned indicator in inbox rows |
| `divider` | `key_divider` | `#FFEFEFEF` | `#FF282C30` | `0.6dp` hairline dividers (inbox/sheets/headers) |
| `unreadDividerBg` | `key_chat_unreadMessagesStartBackground` | `#140064E0` (`0.08f` alpha) | `primaryBlue @ 0.16f` | "Unread messages" banner pill |
| `unreadDividerText` | `key_chat_unreadMessagesStartText` | `#FF0064E0` | `#FF3B8BF7` | "Unread messages" label |

### 1.3 Per-Sender Group Accent Palette (`senderNameColor`)

Deterministic 8-color palette indexed by `(senderId.hashCode() and Int.MAX_VALUE) % 8`:
- **Light Mode**: `#FF0F7B43` (Emerald), `#FFBE123C` (Deep Rose), `#FF1D4ED8` (Cobalt Blue), `#FFB45309` (Deep Amber), `#FF6D28D9` (Rich Violet), `#FF9D174D` (Magenta Berry), `#FF0E7490` (Deep Teal), `#FFC2410C` (Burnt Sienna).
- **Dark Mode**: `#FF4ADE80` (Mint Green), `#FFFB7185` (Rose), `#FF60A5FA` (Sky Blue), `#FFFBBF24` (Amber Gold), `#FFC084FC` (Soft Violet), `#FFF472B6` (Coral Pink), `#FF22D3EE` (Cyan), `#FFFB923C` (Warm Peach).

---

## 2. Glassmorphic Surface Alpha & Blur Tokens (`ChatGlassTokens`)

Floating chrome surfaces use layered alpha translucency (`headerGlassBg`, `composerBg`, `dateChipBg`, `pinnedBannerBg`, `scrollToBottomBg`) paired with `0.8.dp` `subtleSurfaceBorder` contours and soft Gaussian halo shadows. On Android 12+ (API 31+), pair with `ChatGlassTokens.*BlurRadius`; on API < 31, the alpha channel provides an equivalent frosted fallback.

| Surface | Token (`ChatColors`) | Light ARGB / Alpha | Dark ARGB / Alpha | Blur Radius (`ChatGlassTokens`) | Border |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Floating Header Capsules | `headerGlassBg` | `#EBFFFFFF` (`0.92f`) | `#E0171A1E` (`0.88f`) | `HeaderBlurRadius = 24.dp` | `0.8.dp` `headerGlassBorder` |
| Composer Input Dock | `composerBg` | `#F5FFFFFF` (`0.96f`) | `#EB171A1E` (`0.92f`) | `ComposerBlurRadius = 22.dp` | `0.8.dp` `composerBorder` |
| Pinned Message Strip | `pinnedBannerBg` | `#F0FFFFFF` (`0.94f`) | `#E6171A1E` (`0.90f`) | `PinnedBannerBlurRadius = 20.dp` | `0.8.dp` `headerGlassBorder` |
| Date Separator Chip | `dateChipBg` | `#E6F1F3F2` (`0.90f`) | `#DB1A1E23` (`0.86f`) | `FloatingChipBlurRadius = 16.dp` | `0.8.dp` `dateChipBorder` |
| Scroll-to-Bottom FAB | `scrollToBottomBg` | `#F5FFFFFF` (`0.96f`) | `#EB1E2228` (`0.92f`) | `FloatingChipBlurRadius = 16.dp` | `0.8.dp` `scrollToBottomBorder` |
| Context Menu Scrim | `menuBackdropScrim` | `#2E000000` (`0.18f`) | `#4D000000` (`0.30f`) | `MenuBlurRadius = 20.dp` | — |
| Swipe-to-Reply Circle | `swipeReplyPillBg` | `#33000000` (`0.20f`) | `#52000000` (`0.32f`) | — | — |

---

## 3. Geometry, Spacing & Stroke Tokens (`ChatDimens`)

- **Bubble Clustering**:
  - `IntraGroupSpacing = 3.dp` (consecutive messages from same sender within `GroupMergeThresholdMillis = 120_000L` / 2 min).
  - `InterGroupSpacing = 11.dp` (different sender or time gap > 2 min).
  - `BubbleCornerLarge = 18.dp` (outer & ungrouped corners).
  - `BubbleCornerGrouped = 4.dp` / `BubbleTailCorner = 4.dp` (connected inner corners between clustered bubbles).
  - `BubbleMaxWidthFraction = 0.78f`, `BubbleHorizontalPadding = 14.dp`, `BubbleVerticalPadding = 8.dp`.
- **Quoted Reply Card**:
  - `QuoteCornerSmall = 6.dp`, `QuoteCornerLarge = 10.dp`, `QuoteAccentBarWidth = 3.dp`.
- **Gesture & Chrome Sizing**:
  - `SwipeReplyTriggerDp = 56.dp`, `SwipeReplyMaxOffsetDp = 96.dp`, `SwipeReplyIconCircleSize = 36.dp`.
  - `HeaderButtonSize = 46.dp`, `HeaderCapsuleHeight = 46.dp`, `HeaderAvatarSize = 38.dp`.
  - `ComposerMinHeight = 50.dp`, `ComposerCorner = 26.dp`, `ComposerExpandedCorner = 20.dp`, `ComposerActionSize = 42.dp`, `ComposerAttachButtonSize = 40.dp`.
  - `OnlineDotSize = 10.dp`, `OnlineDotRingWidth = 2.dp`, `TypingDotSize = 4.5.dp`.
- **Stroke & Divider Weights**:
  - `IconStrokeWeightStandard = 1.75.dp`
  - `IconStrokeWeightEmphasis = 1.85.dp` (default across all refined chat vector icons)
  - `IconStrokeWeightBold = 2.0.dp`
  - `DividerThickness = 0.6.dp`, `GlassSurfaceBorderWidth = 0.8.dp`.

---

## 4. Typography Scale & Line Heights (`ChatTypography`)

All styles use `BuildXFontFamily` (`Plus Jakarta Sans` for Latin + `Noto Sans Arabic` for Arabic) and enforce BiDi isolation (`TextDirection.ContentOrLtr` on content, `TextDirection.Ltr` + `fontFeatureSettings = "tnum"` on numeric metadata).

| Token | Font Size | Line Height | Weight / Style | TextDirection | Features |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `messageBody` | `16.sp` | `22.sp` | `Normal (400)` | `ContentOrLtr` | — |
| `messageDeleted` | `13.5.sp` | `18.sp` | `Normal (400)` / `Italic` | `ContentOrLtr` | — |
| `senderName` | `13.sp` | `16.sp` | `SemiBold (600)` | `ContentOrLtr` | — |
| `quoteSender` | `12.5.sp` | `16.sp` | `SemiBold (600)` | `ContentOrLtr` | — |
| `quoteBody` | `13.sp` | `17.sp` | `Normal (400)` | `ContentOrLtr` | — |
| `metaTimestamp` | `11.sp` | `13.sp` | `Medium (500)` | `Ltr` | `tnum` |
| `dateChip` | `12.sp` | `16.sp` | `SemiBold (600)` | `ContentOrLtr` | — |
| `headerTitle` | `15.5.sp` | `18.sp` | `Bold (700)` | `ContentOrLtr` | — |
| `headerSubtitle` | `12.5.sp` | `15.sp` | `Medium (500)` | `ContentOrLtr` | — |
| `composerInput` | `16.sp` | `22.sp` | `Normal (400)` | `ContentOrLtr` | — |
| `composerPlaceholder` | `16.sp` | `22.sp` | `Medium (500)` | `ContentOrLtr` | — |
| `pinnedBannerTitle` | `12.5.sp` | `16.sp` | `SemiBold (600)` | `ContentOrLtr` | — |
| `pinnedBannerPreview` | `13.5.sp` | `18.sp` | `Normal (400)` | `ContentOrLtr` | — |
| `inboxTitle` | `16.sp` | `21.sp` | `SemiBold (600)` | `ContentOrLtr` | — |
| `inboxPreview` | `14.sp` | `19.sp` | `Normal (400)` | `ContentOrLtr` | — |
| `inboxTime` | `12.sp` | `15.sp` | `Medium (500)` | `Ltr` | `tnum` |
| `unreadBadge` | `11.5.sp` | `14.sp` | `Bold (700)` | `Ltr` | `tnum` |

---

## 5. Spring Physics & Easing Curves (`ChatMotion`)

Zero linear tweens for interactive transitions; all motion respects `rememberReduceMotion()` (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`).

| Motion Class | Specification | Parameters | Target Interactions |
| :--- | :--- | :--- | :--- |
| **Interactive / Popup Spring** | `interactiveSpring()` | `dampingRatio = 0.72f`, `stiffness = 480f` | Message enter flight, context menu pop, action icon morph, scroll-to-bottom FAB, swipe reply icon pop |
| **Layout / Gesture Spring** | `layoutGestureSpring()` | `dampingRatio = Spring.DampingRatioNoBouncy (1.0f)`, `stiffness = 420f` | Bubble corner morphing (`18.dp <-> 4.dp`), swipe-to-reply snapback, list `animateItem` placement, composer expansion |
| **Fast Out Cubic Bezier** | `FastOutCubicBezier` / `fastOutBezierTween()` | `CubicBezierEasing(0.19f, 1.0f, 0.22f, 1.0f)` (`280ms` default) | Screen/sheet navigation transitions, send alpha lift (`110ms`) |

Key constants in `ChatMotion`:
- **Message Flight**: `SendFlightStartScale = 0.88f` (plus `SendLiftStartScale = 0.84f`, `SendLiftStartScaleX = 0.84f`, `SendLiftStartScaleY = 0.78f`), `SendLiftStartAlpha = 0.18f`, `SendLiftStartTranslationY = 52.dp`, `SendLiftStartTranslationX = 22.dp`, `SendFadeDurationMs = 110`.
- **Receive Entry**: `ReceiveEntryDurationMs = 220`, `ReceiveEntryStartScale = 0.94f`, `ReceiveEntrySlideY = 14.dp`.
- **Composer Morph**: `ActionSwapDurationMs = 175`, `ActionSwapStartScale = 0.60f`, `ActionSwapRotationDeg = 90f`, `AttachSlideDurationMs = 160`.
- **Status & Typing**: `StatusTransitionMs = 180`, `StatusStartScale = 0.75f`, `TypingDotCycleMs = 1200`, `TypingDotStaggerMs = 180`, `HighlightFlashDurationMs = 900`.

---

## 6. Chat Vector Drawables (`app/src/main/res/drawable/`)

All chat icons share a `24dp x 24dp` (`viewportWidth="24"`, `viewportHeight="24"`) grid, `strokeWidth="1.85"`, `strokeLineCap="round"`, `strokeLineJoin="round"`, and explicit `fillColor="#00000000"`. Directional icons set `android:autoMirrored="true"` for native RTL support.

| Drawable | Status | `autoMirrored` | Path Data (`viewport 24x24`) |
| :--- | :--- | :--- | :--- |
| `ic_send.xml` | **Created** (keeps `ic_arrow_up.xml` intact) | `true` | `M20.75,3.25 L14.25,20.75 L10.75,13.25 L3.25,9.75 Z` + `M20.75,3.25 L10.75,13.25` |
| `ic_mic.xml` | **Refined** (centered `2.5..21.5`) | `false` | `M12,2.5 A3,3 0 0,0 9,5.5 V10.5 A3,3 0 0,0 15,10.5 V5.5 A3,3 0 0,0 12,2.5 Z` + `M19,10 V10.5 A7,7 0 0,1 5,10.5 V10` + `M12,17.5 V21.5` + `M8.5,21.5 H15.5` |
| `ic_attachment.xml` | **Created** (keeps `ic_paperclip.xml` intact) | `false` | `M20.0,11.5 L11.5,20.0 A5.5,5.5 0 0,1 3.72,12.22 L12.22,3.72 A3.75,3.75 0 0,1 17.52,9.02 L9.02,17.52 A2.0,2.0 0 0,1 6.19,14.69 L14.0,6.88` |
| `ic_reply.xml` | **Refined** (optically centered `y=4.5..19.5`) | `true` | `M9,16.5 L3,10.5 M3,10.5 L9,4.5 M3,10.5 H15 A6,6 0 0,1 21,16.5 V19.5` |
| `ic_check_single.xml` | **Created** (+ synced `ic_dm_single_check.xml`) | `false` | `M5.0,12.5 L9.75,17.25 L19.25,6.75` |
| `ic_check_double.xml` | **Created** (+ synced `ic_dm_double_check.xml`) | `false` | `M1.75,12.5 L6.5,17.25 L16.0,6.75` + `M9.5,14.5 L12.25,17.25 L21.75,6.75` (exact parallel slopes, `5.75dp` horizontal offset) |
| `ic_pin.xml` | **Refined** (rounded pushpin `y=3.5..20.5`) | `false` | `M8,3.5 H16` + `M9,3.5 V10 L6.5,13.5 C6.2,14 6.5,14.5 7.1,14.5 H16.9 C17.5,14.5 17.8,14 17.5,13.5 L15,10 V3.5` + `M12,14.5 V20.5` |
| `ic_volume_off.xml` | **Refined** (centered `x=2.5..21.5`, `y=5..19`) | `true` | `M11,5 L6.5,9 H3 C2.72,9 2.5,9.22 2.5,9.5 V14.5 C2.5,14.78 2.72,15 3,15 H6.5 L11,19 V5 Z` + `M21.5,9.5 L16.5,14.5` + `M16.5,9.5 L21.5,14.5` |
| `ic_search.xml` | **Refined** (`1.85dp` stroke, transparent fill) | `false` | `M11,19 A8,8 0 1,0 11,3 A8,8 0 1,0 11,19 Z` + `M21,21 L16.65,16.65` |
