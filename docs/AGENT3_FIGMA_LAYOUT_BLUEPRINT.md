# AGENT 3 — Figma & Telegram Structural Layout Blueprint

> **Scope & Guardrails:** Covers **exclusively** the Messaging / Chat System — Chat List (Inbox), Chat Conversation Screen, Floating In-Chat Header, Message Bubbles & Clustering, Reply/Quote Bars, Bottom Composer Input Bar, Context Menus, and Swipe-to-Reply Mechanics. Profile System and Settings Screens are strictly excluded.
>
> **Tooling Disclosure:** `figma_figma` MCP tools (`get_metadata`, `get_design_context`, `get_variable_defs`, `get_screenshot`) were **not available** in this session's toolset. All structural metrics below are grounded in:
> 1. **Direct Codebase Audit** of `app/src/main/java/app/buildx/chat/ui/chat/*`, `app/src/main/java/app/buildx/chat/ui/screens/DmInboxScreen.kt`, `DmConversationScreen.kt`, `DmConversationPopup.kt`, `BuildXSheets.kt`, `BuildXComponents.kt`, and `docs/design/SPEC.md` (Messaging sections S2, S3, S5 only).
> 2. **Cross-Reference via Web Search** of Figma *Telegram Design System* (`988466927624217947`, v7.8.0 auto-layout conventions) and `DrKLO/Telegram` Android source classes (`DialogCell.java`, `ChatMessageCell.java`, `ChatActivity.java`, `ChatActivityEnterView.java`, `ActionBarPopupWindow.java`, `SharedConfig.java`).

---

## 1. Executive Summary & Alignment Deltas

Build-X's Compose chat UI already follows a clean 8dp/4dp spatial rhythm and matches most Telegram-grade layout proportions. The table below summarizes the key target metrics alongside exact deltas observed in `ChatTokens.kt` and screen composables for Agents 5 & 6:

| Surface / Metric | Target / Telegram Reference | Current Build-X Value (Observed) | Source Symbol | Alignment Delta / Action |
| :--- | :--- | :--- | :--- | :--- |
| **Inbox Avatar Size** | `52.dp`–`56.dp` (`DialogCell`) | `56.dp` (composite group: `38.dp` back + `40.dp` front inside `45.dp` cutout) | `DmInboxScreen.kt` (`DmConversationRow`, `DmConversationAvatar`) | Aligned (`56.dp`). |
| **Inbox Row Height** | `70.dp`–`72.dp` (`DialogCell`) | `72.dp` minHeight (`16.dp` H / `8.dp` V padding) | `DmInboxScreen.kt` (`DmConversationRow`) | Aligned (`72.dp` minHeight). |
| **Chat Header Capsule Height** | `56.dp` floating capsule | `46.dp` (`HeaderCapsuleHeight`, `HeaderButtonSize`) | `ChatTokens.kt`, `ChatHeader.kt` | **Delta:** `-10.dp` shorter than `56.dp` target; bump `HeaderCapsuleHeight` & `HeaderButtonSize` to `56.dp` (or `52–56.dp`) if matching spec. |
| **Chat Header Avatar Size** | `36.dp`–`40.dp` | `38.dp` (`HeaderAvatarSize`) | `ChatTokens.kt`, `ChatHeader.kt` | Aligned (midpoint of `36.dp`–`40.dp`; adjust to `40.dp` if capsule becomes `56.dp`). |
| **Group Chat Bubble Avatar** | `32.dp` | `28.dp` (`GroupAvatarSize`), `8.dp` gap | `ChatTokens.kt`, `ChatBubble.kt` | **Delta:** `-4.dp`; update `GroupAvatarSize` to `32.dp` if adopting target spec. |
| **Bubble Corner Radii (Clustered)** | `18.dp` outer / `4.dp` inner | `18.dp` (`BubbleCornerLarge`) / `4.dp` (`BubbleCornerGrouped`) | `ChatTokens.kt`, `ChatBubble.kt` | Aligned (`DrKLO/Telegram` default `SharedConfig.bubbleRadius = 17`). |
| **Inter-Bubble Vertical Spacing** | `2.dp` intra-cluster / `8.dp` inter-cluster | `3.dp` (`IntraGroupSpacing`) / `11.dp` (`InterGroupSpacing`) | `ChatTokens.kt`, `ChatBubble.kt` | **Delta:** `+1.dp` intra / `+3.dp` inter; tighten to `2.dp` / `8.dp` for denser Telegram clustering. |
| **Bubble Max Width** | `70%`–`78%` viewport width | `0.78f` (`BubbleMaxWidthFraction`), media `248.dp` cap | `ChatTokens.kt`, `ChatBubble.kt` | Aligned (`78%` text, `248.dp` media). |
| **Reply/Quote Bar Geometry** | `3.dp` accent bar, `36.dp` height | In-bubble: `3.dp` bar, `minHeight 32.dp` (`~42.dp` w/ 2 lines); Composer banner: `3.dp` × `30.dp` bar | `ChatBubble.kt` (`ChatQuotedReplyCard`), `ChatComposer.kt` (`ChatComposerContextBanner`) | **Delta:** Set explicit `heightIn(min = 36.dp)` on quote bar / `36.dp` height on composer banner accent bar for exact `36.dp` match. |
| **Bottom Input Bar Capsule** | `minHeight 48.dp`, `24.dp` corner radius | `minHeight 50.dp` (`ComposerMinHeight`), `26.dp` corner (`ComposerCorner`) -> `20.dp` expanded | `ChatTokens.kt`, `ChatComposer.kt` | **Delta:** `+2.dp` height / `+2.dp` radius; align to `48.dp` / `24.dp` if strict spec required. |
| **Context Menu Popup** | `188.dp`–`232.dp` width, `16.dp` radius, soft elevation | Bubble: `200.dp` W, `16.dp` R, `18.dp` blur; Inbox: `232.dp` W, `16.dp` R, `22.dp` blur | `ChatBubble.kt`, `BuildXSheets.kt`, `DmConversationPopup.kt` | Aligned (`16.dp` radius, `0.8.dp`–`1.dp` border, `18.dp`–`22.dp` soft shadow blur). |
| **Swipe-to-Reply Threshold** | `56.dp`–`64.dp` | `56.dp` trigger (`SwipeReplyTriggerDp`), `96.dp` max rubber-band | `ChatTokens.kt`, `ChatBubble.kt` | Aligned (`56.dp` trigger). |

---

## 2. Chat List (Inbox) Structural Layout (`DmInboxScreen.kt`)

### 2.1 Vertical Screen Anatomy (Top to Bottom)
Parent container applies `statusBarsPadding()` and `navigationBarsPadding()`, with `fillMaxSize()`.

1. **Top Navigation Bar** (`Row`, `height = 56.dp`, `padding(horizontal = 20.dp)`):
   - Start: `HeaderCircleButton` (`44.dp × 44.dp`, `22.dp` `ic_back`, `0.8.dp` border, `softShadow(cornerRadius = 22.dp, blurRadius = 14.dp, offsetY = 2.5.dp)`).
   - Gap: `12.dp`.
   - Center: Title (`weight(1f)`, `22.sp` Bold).
   - End: `HeaderCircleButton` (`44.dp × 44.dp`, `22.dp` `ic_edit_compose`).
2. **Search Pill Bar** (`Box`, `padding(horizontal = 16.dp, vertical = 8.dp)`, `height = 48.dp`, `RoundedCornerShape(24.dp)`, inner `padding(horizontal = 16.dp)`):
   - Leading icon: `ic_search` (`20.dp`), `10.dp` gap.
   - Input field: `weight(1f)`, `15.sp` (`SemiBold` placeholder, `Normal` input).
   - Trailing clear icon (when non-empty): `ic_close` (`18.dp`).
3. **Notes Rail** (`DmNotesStrip`, hidden when search query is active):
   - `LazyRow`: `height = 130.dp`, `contentPadding(horizontal = 16.dp)`, `spacedBy(10.dp)`.
   - Each cell (`NoteStripCell`): `90.dp × 130.dp`:
     - Thought bubble zone (`TopCenter`, `height = 48.dp`, `zIndex = 2f`): `widthIn(56.dp..90.dp)`, `minHeight = 32.dp`, `RoundedCornerShape(16.dp)`, `0.8.dp` border, `padding(horizontal = 10.dp, vertical = 4.dp)`, shadow `blurRadius = 12.dp, offsetY = 2.5.dp`.
     - Tail dots (`padding(start = 15.dp)`): `6.dp` circle + `1.dp` gap + `4.dp` circle (`0.8.dp` border).
     - Avatar (`BottomCenter`, `padding(bottom = 22.dp)`): `68.dp × 68.dp` circle (`0.8.dp` border); own slot has `24.dp` add badge (`BottomEnd`, `padding(end = 6.dp, bottom = 20.dp)`, `2.dp` page-bg ring, `13.dp` `ic_plus`).
     - Label (`BottomCenter`): `11.5.sp`, 1 line + optional `12.dp` `VerifiedBadge` (`3.dp` gap).
   - Bottom spacer: `8.dp`.
4. **Filter Chips Row** (`Row`, `padding(horizontal = 16.dp)`, `spacedBy(8.dp)`):
   - Chips (`All`, `Unread`, `Groups`): `height = 36.dp`, `RoundedCornerShape(18.dp)`, `padding(horizontal = 14.dp)`, `1.dp` border when unselected, `13.5.sp` text (`Bold` selected / `Medium` unselected).
   - Count badge pill: `6.dp` start spacer, `height = 18.dp`, `defaultMinSize(minWidth = 18.dp)`, `padding(horizontal = 4.dp)`, `CircleShape`, `10.5.sp` Bold.
   - Bottom spacer: `10.dp`.
5. **Conversation List (`LazyColumn`)**: `weight(1f)`, `contentPadding(bottom = 24.dp)`.

### 2.2 Conversation Row Blueprint (`DmConversationRow`)
Cross-referenced against `DrKLO/Telegram` `DialogCell.java` (`~70–72dp` row height, `52–56dp` avatar, `12dp` avatar-to-text gap) and Figma *Telegram Design System* Auto Layout (`Horizontal`, `VerticalAlignment.Center`, `8dp` V / `16dp` H padding):

```text
|<-- 16dp -->| [ 56dp Avatar ] |<- 12dp ->| [ Column (weight 1f) ]                |<-- 16dp -->|
|            |                 |          | Line 1: Title + Badge      Time (Ltr) |            |
|   8dp V    |   Single 56dp   |          | (2dp gap)                             |   8dp V    |
|            |   or Composite  |          | Line 2: BiDi Preview   Pin Mute Badge |            |
```

- **Row Container**: `fillMaxWidth()`, `defaultMinSize(minHeight = 72.dp)`, `padding(horizontal = 16.dp, vertical = 8.dp)`.
- **Avatar Container (`56.dp × 56.dp`)**:
  - *Direct or Group with Photo*: `56.dp` `CircleShape`, `0.8.dp` `subtleSurfaceBorder` (placeholder icon `30.dp`).
  - *Group Fallback (No Photo)*: Unclipped `56.dp` box containing:
    - Back circle (`TopStart`): `38.dp`, `0.8.dp` border.
    - Front circle cutout (`BottomEnd`): `45.dp` outer circle filled with `pageBackground`, `2.5.dp` padding, inner `40.dp` member circle with `0.8.dp` border (`12.sp` Bold fallback initials).
- **Line 1 (Title + Timestamp)**:
  - Title: `\u2068{effectiveTitle}\u2069`, `16.sp` (`Bold` unread / `SemiBold` read), `maxLines = 1`, `TextOverflow.Ellipsis`, `weight(1f, fill = false)`.
  - Verified Badge: `4.dp` start spacer, `15.dp` `VerifiedBadge`.
  - Gap before timestamp: `8.dp`.
  - Timestamp: `\u2066{timeText}\u2069`, `12.sp`, `fontFeatureSettings = "tnum"`, `TextDirection.Ltr`.
- **Vertical Gap**: `2.dp` between Line 1 and Line 2.
- **Line 2 (Preview + Trailing Status)**:
  - Preview: BiDi-isolated prefix (`$dirMark\u2068$cleanSender\u2069$dirMark: $dirMark\u2068$content\u2069`), `14.sp` (`SemiBold` unread / `Normal` read), `maxLines = 1`, `weight(1f)`.
  - Gap before trailing indicators: `8.dp`.
  - Trailing Indicators (`spacedBy(6.dp)`):
    - Pin icon (`ic_pin`): `14.dp`.
    - Mute icon (`ic_bell_off`): `14.dp`.
    - Unread Badge Pill: `height = 20.dp`, `defaultMinSize(minWidth = 20.dp)`, `CircleShape`, `padding(horizontal = 5.dp)`, `11.sp` Bold (`"99+"` cap, `"•"` if marked unread with `0` count).
- **Row Divider**: `HorizontalDivider`, `thickness = 0.6.dp`, `padding(start = 84.dp, end = 16.dp)` (`84dp = 16dp start + 56dp avatar + 12dp gap`, aligning cleanly with the text column).

---

## 3. Chat Conversation Screen & Floating Header (`DmConversationScreen.kt`, `ChatHeader.kt`)

### 3.1 Screen Z-Layer Stack (`Box(fillMaxSize())`)
1. **Layer 0 — Background & Wallpaper**: `ChatWallpaperLayer(fillMaxSize())`.
2. **Layer 1 — Reversed Message List (`ChatMessageList`)**: `LazyColumn(reverseLayout = true, fillMaxSize())`.
   - `contentPadding`: `start = 12.dp`, `end = 12.dp`, `top = topOverlayDp + 8.dp` (fallback `96.dp`), `bottom = bottomOverlayDp + 8.dp` (fallback `navAndImeInsets + 76.dp`).
3. **Layer 2 — Top Floating Overlay (`Alignment.TopCenter`)**:
   - Vertical edge fade gradient (`0.0f` -> `0.72f` -> `1.0f` transparent) with `statusBarsPadding()`.
   - Contains `ChatHeader` + optional `DmInChatSearchBar` + optional `DmPinnedMessageBanner`.
4. **Layer 3 — Bottom Floating Overlay (`Alignment.BottomCenter`)**:
   - Vertical edge fade gradient (`0.0f` transparent -> `0.32f` -> `1.0f`) with `windowInsetsPadding(navigationBars union ime)`.
   - Contains optional `DmBottomTypingPill` (`padding(start = 16.dp, end = 16.dp, bottom = 4.dp)`, `RoundedCornerShape(16.dp)`, `0.7.dp` border, `padding(12.dp H, 7.dp V)`, `6.dp` dots with `5.dp` gap) + `ChatComposer` (or `VoiceDictationBar`).
5. **Layer 4 — Scroll-to-Bottom FAB (`Alignment.BottomEnd`)**:
   - Positioned inside `ChatMessageList` at `padding(end = 16.dp, bottom = bottomOverlayDp + 16.dp)`.
   - Button: `42.dp × 42.dp` circle (`padding(top = 6.dp, end = 2.dp)` for badge headroom), `0.8.dp` border, shadow `blur = 12.dp, offsetY = 2.dp`, `20.dp` `ic_chevron_down`.
   - Unread badge (`TopEnd`): `CircleShape`, `padding(horizontal = 6.dp, vertical = 2.dp)`, `10.sp` Bold.

### 3.2 Floating Header Capsule Geometry (`ChatHeader.kt`)
Outer container: `padding(horizontal = 16.dp, vertical = 8.dp)`, `Row(spacedBy = 8.dp, verticalAlignment = CenterVertically)`.

- **Back Circle Button**:
  - Current: `46.dp × 46.dp` (`HeaderButtonSize`), `21.dp` `ic_back` (mirrored on RTL via `scaleX = -1f`), `0.8.dp` border.
  - Target Spec option: `56.dp` if matching `56.dp` capsule height, or keep `46.dp`–`48.dp` compact floating pill.
- **Wide Identity Capsule (`weight(1f)`)**:
  - Height: currently `46.dp` (`HeaderCapsuleHeight`); **Target Spec: `56.dp`**.
  - Shape: Full capsule `RoundedCornerShape(height / 2)` (`23.dp` at `46.dp` height; `28.dp` at `56.dp` height).
  - Inner Padding: `start = 4.dp, end = 14.dp`, `0.8.dp` border.
  - **Avatar**: currently `38.dp` (`HeaderAvatarSize`; **Target Spec: `36.dp`–`40.dp`**), `CircleShape`, `18.dp` fallback icon / `14.sp` Bold initial.
  - **Avatar-to-Text Gap**: `10.dp`.
  - **Title Row**: `15.sp` Bold (`lineHeight = 16.sp`), `TextDirection.ContentOrLtr`, `maxLines = 1` + `4.dp` gap + `14.dp` `VerifiedBadge` + `5.dp` gap + `13.dp` `ic_bell_off` when muted.
  - **Subtitle / Typing**: `12.sp` (`lineHeight = 14.sp`), `@handle` wrapped in `\u2066...\u2069` with `TextDirection.Ltr`; when typing: label + 3 animated `4.5.dp` dots (`spacedBy(3.dp)`).
- **Overflow Circle Button**:
  - `46.dp × 46.dp` (or matching `HeaderButtonSize`), `21.dp` `ic_more_vertical`, `0.8.dp` border.
- **Scroll-Reactive Shadow**:
  - Rest (`!isListScrolled`): `blurRadius = 12.dp`, `offsetY = 2.dp`.
  - Scrolled (`isListScrolled`): `blurRadius = 20.dp`, `offsetY = 5.dp`.

### 3.3 In-Chat Search Bar & Pinned Banner (`DmConversationScreen.kt`)
- **In-Chat Search Bar (`DmInChatSearchBar`)**:
  - Outer margin: `padding(horizontal = 16.dp, vertical = 6.dp)`.
  - Container: `RoundedCornerShape(18.dp)`, `0.8.dp` border, shadow `blur = 10.dp, offsetY = 2.dp`, inner `padding(horizontal = 12.dp, vertical = 8.dp)`.
  - Contents: `17.dp` search icon -> `8.dp` gap -> `BasicTextField` (`14.sp`) -> `6.dp` gap -> `"1 / N"` counter (`12.sp`) -> `4.dp` gap -> three `28.dp` circular buttons (prev, next, close) with `16.dp` icons.
- **Pinned Message Banner (`DmPinnedMessageBanner`)**:
  - Outer margin: `padding(horizontal = 16.dp, vertical = 6.dp)`.
  - Container: `RoundedCornerShape(14.dp)`, `0.8.dp` border, shadow `blur = 8.dp, offsetY = 1.dp`, inner `padding(horizontal = 12.dp, vertical = 8.dp)`.
  - Contents: `16.dp` `ic_pin` -> `8.dp` gap -> `Column(weight(1f))` (`11.5.sp` Bold `"Pinned message"` + `13.sp` 1-line preview) -> `16.dp` `ic_close`.

---

## 4. Message Bubbles, Clustering & Tail Geometry (`ChatBubble.kt`, `ChatTokens.kt`, `ChatMessageMapper.kt`)

### 4.1 Cluster Grouping Rules & Inter-Bubble Spacing
Messages are grouped chronologically (`ChatMessageMapper.mapToReversedListItems`) when all conditions hold:
- Same `senderId`
- Neither message is deleted (`deletedAt == null`)
- Same calendar day in `zoneId`
- Time delta `0 <= (nextMillis - prevMillis) <= 120_000L` (2 minutes, `GroupMergeThresholdMillis`)

| Spacing Token | Build-X Current | Telegram Target Spec | Usage |
| :--- | :--- | :--- | :--- |
| `IntraGroupSpacing` | `3.dp` | `2.dp` | Top padding between consecutive bubbles in the same cluster (`isGroupedWithPrevious == true`). |
| `InterGroupSpacing` | `11.dp` | `8.dp` | Top padding between different senders, >2min gaps, or after date chips (`isGroupedWithPrevious == false`). |
| `DateChipVerticalMargin` | `14.dp` | `12.dp`–`14.dp` | Vertical padding around centered date chip (`12.dp` radius, `12.dp` H / `4.dp` V padding, `11.sp` Medium). |
| `ListHorizontalPadding` | `12.dp` | `8.dp`–`12.dp` | Left/right inset of `LazyColumn`. |

### 4.2 Corner Radii Matrix (Single vs. Clustered, RTL-Aware)
`rememberAnimatedChatBubbleShape` uses Compose layout-relative `topStart`, `topEnd`, `bottomEnd`, `bottomStart` (so RTL mirrors automatically) and animates corner transitions via `ChatMotion.layoutGestureSpring()` (`NoBouncy`, `stiffness = 420f`).
- **Outer Radius (`BubbleCornerLarge`)**: `18.dp` (`DrKLO/Telegram` default `SharedConfig.bubbleRadius = 17`).
- **Clustered Inner Radius (`BubbleCornerGrouped`)**: `4.dp`.

| `ChatBubblePosition` | Own (`isOwn = true`) `(topStart, topEnd, bottomEnd, bottomStart)` | Incoming (`isOwn = false`) `(topStart, topEnd, bottomEnd, bottomStart)` |
| :--- | :--- | :--- |
| `SINGLE` | `(18.dp, 18.dp, 18.dp, 18.dp)` | `(18.dp, 18.dp, 18.dp, 18.dp)` |
| `FIRST` (top of cluster) | `(18.dp, 18.dp, 4.dp, 18.dp)` | `(18.dp, 18.dp, 18.dp, 4.dp)` |
| `MIDDLE` (middle of cluster) | `(18.dp, 4.dp, 4.dp, 18.dp)` | `(4.dp, 18.dp, 18.dp, 4.dp)` |
| `LAST` (bottom of cluster) | `(18.dp, 4.dp, 18.dp, 18.dp)` | `(4.dp, 18.dp, 18.dp, 18.dp)` |

### 4.3 Tail Geometry Note (Modern Corner-Morph vs. Classic Telegram Tail)
- **Current Build-X Implementation**: Uses **tail-less asymmetric corner morphing** (`18.dp` outer / `4.dp` inner) with flush vertical alignment across `FIRST` -> `MIDDLE` -> `LAST` (modern Telegram-X / iMessage capsule aesthetic). No horizontal tail offset is needed.
- **Classic `DrKLO/Telegram` Tail Option (Reference)**: If a protruding vector/canvas tail is ever desired on `SINGLE` and `LAST` bubbles:
  - Tail bounding box: `9.dp` width × `12.dp` height, anchored at bottom-end (outgoing) or bottom-start (incoming).
  - Bottom outer corner on the tail side flattens into a smooth cubic Bézier flare (`M 0,0 C 0,6 4,10 9,12 L 0,12 Z`) while all bubbles without tails keep `9.dp` extra outer margin on the tail side so stacked bubbles remain edge-aligned.
  - **Recommendation**: Keep Build-X's tail-less `18.dp`/`4.dp` corner morphing unless explicitly requested otherwise, as it integrates cleanly with the glassmorphic floating composer and `rememberAnimatedChatBubbleShape`.

### 4.4 Bubble Dimensions, Group Avatars & Inline vs. Wrapped Metadata
- **Bubble Max Width**: `maxWidth * 0.78f` (`BubbleMaxWidthFraction = 0.78f`; `DrKLO/Telegram` & Figma TDS standard is `70%`–`78%`).
  - `maxTextWidth = (maxBubbleWidth - 28.dp).coerceAtLeast(120.dp)`.
- **Internal Bubble Padding**: `horizontal = 14.dp` (`BubbleHorizontalPadding`), `vertical = 8.dp` (`BubbleVerticalPadding`).
- **Group Chat Sender Avatar**:
  - Shown ONLY when `!isDirect && !isOwn && !groupedWithNext` (`SINGLE` or `LAST` in cluster); `FIRST`/`MIDDLE` render an invisible `Spacer(width = GroupAvatarSize)` so bubble edges stay vertically aligned.
  - Current size: `28.dp` (`GroupAvatarSize`), `0.8.dp` border, followed by `8.dp` gap (`GroupAvatarGap`).
  - **Target Spec**: `32.dp` avatar size + `8.dp` gap (`11.sp` -> `12.sp` fallback initial).
- **Group Sender Header (inside bubble)**:
  - Shown when `!isDirect && !isOwn && !isDeleted && !groupedWithPrev && name.isNotBlank()`.
  - `13.sp` Bold (`lineHeight = 16.sp`), `4.dp` gap to `13.dp` `VerifiedBadge`, `bottomPadding = 4.dp` (if followed by quote) or `2.dp` (if followed by text).
- **Media Attachment Bubbles**:
  - Width: `widthIn(max = min(248.dp, maxBubbleWidth))`, `width(IntrinsicSize.Max)`.
  - Height: `heightIn(min = 120.dp, max = 280.dp)`.
  - Standalone media (no caption/header/quote): `RoundedCornerShape(18.dp)`, `0.8.dp` border; bottom-end overlay timestamp pill (`8.dp` margin, `10.dp` radius, `7.dp` H / `3.dp` V padding, `52%` black bg).
  - Captioned or quoted media: `4.dp` horizontal inset around image clipped to `14.dp` radius; caption uses standard `14.dp` H / `8.dp` V padding below image.
- **Inline vs. Wrapped Metadata (`ChatInlineMetaTextBlock` + `ChatMessageMapper.computeBubbleMetaPlacement`)**:
  - Metadata row (`ChatBubbleMetaCluster`): locked to `LayoutDirection.Ltr`, `spacedBy(4.dp)`, `11.sp` Medium `tnum` timestamp (`lineHeight = 13.sp`), optional `"Edited"` (`11.sp`), and `15.dp` status icon box (`13.dp` clock / `14.dp` single check / `15.dp` double check / `13.5.dp` error).
  - `InlineMetaStartGap = 8.dp`, `WrapMetaTopGap = 3.dp`.
  - **LTR**: Places metadata inline on the bottom line if `lastLineWidthPx + 8.dp + metaWidthPx <= maxTextWidth`; otherwise wraps below at `textHeight + 3.dp`, anchored bottom-right (`metaX = bubbleWidth - metaWidthPx`).
  - **RTL**: Places metadata inline to the right of single-line text only when `lineCount == 1 && lastLineWidthPx + 8.dp + metaWidthPx <= maxTextWidth`; multi-line RTL wraps cleanly below at bottom-right so Arabic glyphs never collide with timestamps.
- **Seen Avatars Row (`ChatSeenAvatarsRow`)**:
  - Below own bubbles (`padding(top = 3.dp, end = 4.dp)`), up to `5` avatars, `15.dp` size (`SeenAvatarSize`), `-4.dp` overlap (`SeenAvatarOverlap`), `1.dp` `pageBackground` border.

---

## 5. Reply / Quote Bars (`ChatQuotedReplyCard` & `ChatComposerContextBanner`)

### 5.1 In-Bubble Quoted Reply Card (`ChatQuotedReplyCard`)
```text
+--------------------------------------------------+
| [3dp]  Sender Name (12.5sp Bold)                 |  top: 5dp
| [Bar]  Quoted snippet (12.5sp, 1 line, ellipsis) |  bottom: 5dp
+--------------------------------------------------+
  ^-- 6dp topStart/bottomStart, 10dp topEnd/bottomEnd radius
```

- **Width**: `fillMaxWidth()`, `widthIn(min = 140.dp, max = maxContentWidth)`, `height(IntrinsicSize.Min)`.
- **Container Corner Radii**: `topStart = 6.dp, bottomStart = 6.dp, topEnd = 10.dp, bottomEnd = 10.dp`.
- **Vertical Accent Bar**: `width = 3.dp`, `fillMaxHeight()`, currently `heightIn(min = 32.dp)` (**Target Spec: `36.dp` height / `minHeight = 36.dp`**).
- **Text Column Padding**: `start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp` (with two `16.sp` lineHeight lines + `10.dp` V padding, natural height is `42.dp`; `minHeight = 36.dp` guarantees at least `36.dp` even if single-line).
- **Bottom Spacer to Message Body**: `6.dp`.
- **Note Reply Pill (`ChatNoteReplyPill`)**: Same corner radii (`6.dp` start / `10.dp` end), `widthIn(min = 140.dp, max = maxContentWidth)`, `padding(horizontal = 10.dp, vertical = 5.dp)`, `12.sp` Medium 1 line, `6.dp` bottom spacer.

### 5.2 In-Composer Reply / Edit Banner (`ChatComposerContextBanner`)
- **Outer Margin**: `padding(start = 8.dp, end = 8.dp, top = 8.dp)`.
- **Banner Surface**: `RoundedCornerShape(14.dp)`, `padding(horizontal = 10.dp, vertical = 8.dp)`.
- **Vertical Accent Bar**: `width = 3.dp`, currently `height = 30.dp` (**Target Spec: `36.dp` height**), `RoundedCornerShape(1.5.dp)`.
- **Text Column**: `8.dp` start gap -> Title (`12.sp` Bold, 1 line) + Preview (`13.sp`, 1 line, `TextDirection.ContentOrLtr`).
- **Dismiss Button**: `8.dp` start gap -> `26.dp × 26.dp` circle, `14.dp` `ic_close`.
- **Bottom Separator**: `6.dp` spacer + `0.6.dp` hairline divider (`padding(horizontal = 12.dp)`).

---

## 6. Bottom Input Bar / Composer Geometry (`ChatComposer.kt`)

```text
|<-- 14dp -->| [ Composer Capsule (minHeight 48-50dp, corner 24-26dp -> 20dp) ] |<-- 14dp -->|
|            | [Optional Reply/Edit Banner + 0.6dp Divider]                  |            |
|            | [Optional 56dp Staged Media LazyRow (8dp gap)]                |            |
|            | |<6dp>| [ TextField (minH 42dp, max 6 lines/136dp) ] [40] [42]|            |
```

- **Outer Margins**: `start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp`.
- **Capsule Height & Corner Radius**:
  - Current Build-X: `minHeight = 50.dp` (`ComposerMinHeight`), single-line radius `26.dp` (`ComposerCorner`), multi-line / banner / attachments radius `20.dp` (`ComposerExpandedCorner`).
  - **Target Spec (`DrKLO/Telegram` `ChatActivityEnterView` + brief)**: `minHeight = 48.dp`, single-line capsule radius `24.dp` (exact half of `48.dp`), expanding smoothly via `layoutGestureSpring()` to `20.dp` for multi-line or banner states.
- **Border & Shadow**: `0.8.dp` border (`ComposerBorderWidth`), `softShadow(blurRadius = 14.dp, offsetY = 3.dp)`.
- **Inner Input Row**: `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`, `padding(horizontal = 6.dp, vertical = 5.dp)`, `verticalAlignment = Alignment.Bottom`.
- **Text Field Area**:
  - `weight(1f)`, `defaultMinSize(minHeight = 42.dp)`, `padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp)`.
  - `BasicTextField`: `heightIn(min = 22.dp, max = 136.dp)`, `minLines = 1, maxLines = 6`, `16.sp` Regular / `22.sp` lineHeight, auto-BiDi `TextDirection.ContentOrLtr` + per-character leading Arabic detection.
- **Trailing Action Tray (`heightIn(min = 42.dp)`)**:
  - **Attach Button** (visible when `text.isEmpty()`, animates out via `fadeOut + shrinkHorizontally`): `40.dp × 40.dp` (`ComposerAttachButtonSize`), `20.dp` `ic_paperclip`.
  - **Spacer**: `2.dp`.
  - **Primary Action Circle (Mic <-> Send)**: `42.dp × 42.dp` (`ComposerActionSize`), `CircleShape`, `20.dp` icon (`ic_mic` <-> `ic_arrow_up` morphing with `±90°` rotation and scale over `interactiveSpring()`), `0.84f` send pulse scale.
- **Staged Attachments Strip**:
  - `LazyRow`, `padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp)`, `spacedBy(8.dp)`.
  - Thumbnail tile: `56.dp × 56.dp`, `RoundedCornerShape(12.dp)`.
  - Remove badge (`TopEnd`, `2.dp` padding): `20.dp` circle, `11.dp` `ic_close`.

---

## 7. Context Menus & Popup Positioning (`ActionMenuPopup`, `DmConversationPopupCard`)

### 7.1 Popup Dimensions & Elevation Comparison
| Menu Type | Width | Corner Radius | Border | Shadow (`softShadow`) | Row Padding / Icon | Positioning |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **In-Chat Bubble Menu** (`ChatBubbleActionPopup`) | `200.dp` | `16.dp` | `0.8.dp` | `blur = 18.dp, offsetY = 6.dp` | `16.dp` H / `12.dp` V, `15.sp` SemiBold, `19.dp` icon, `1.dp` divider | `BubblePopupPositionProvider`: `24px` screen edge margin, `8px` vertical gap above/below bubble |
| **Header Overflow Menu** (`ChatHeader.kt`) | `188.dp` (`196.dp` in `HeaderOverflowPopup`) | `16.dp` | `1.dp` | `blur = 22.dp, offsetY = 6.dp` | Same `OverflowMenuRow` | `Alignment.TopEnd`, `padding(top = 58.dp, end = 16.dp)` |
| **Inbox Row Long-Press Menu** (`DmConversationPopupCard`) | `232.dp` | `16.dp` | `1.dp` | `blur = 22.dp, offsetY = 6.dp` | `PopupActionRow` inside `4.dp` V card padding | `RowAnchoredPopupPositionProvider`: `16.dp` edge margin, `10.dp` vertical gap above/below lifted row |

### 7.2 Focus & Backdrop Behavior When Bubble Context Menu Is Open
- **Focused Bubble**: `zIndex(10f)`, scales to `1.03f` (`interactiveSpring()`), gains `14.dp` blur / `4.dp` offsetY shadow and `0.8.dp` highlight border (`28%` white on own, `subtleSurfaceBorder` on received).
- **Sibling Bubbles**: Dim to `0.55f` alpha + `2.5.dp` blur (`Modifier.blur`).
- **Date Chips & Peer Intro Card**: Dim to `0.62f` alpha (`nonMessageDimAlpha`).
- **Backdrop Scrim**: `ChatColors.menuBackdropScrim` animated by `menuFocusProgress`.
- **Popup Entry**: `scaleX/Y` from `0.85f -> 1.0f` + `alpha 0f -> 1f` via `interactiveSpring()`, anchored at `TransformOrigin(pivotX = 0.85f/0.15f, pivotY = 0f/1f)`.

---

## 8. Swipe-to-Reply Mechanics (`ChatSwipeToReplyContainer`)

- **Trigger Threshold (`SwipeReplyTriggerDp`)**: `56.dp` (within `56.dp`–`64.dp` Telegram standard).
- **Max Visual Offset (`SwipeReplyMaxOffsetDp`)**: `96.dp`.
- **Gesture Direction**: Horizontal drag (`Orientation.Horizontal`), LTR drags right (`+1f`), RTL drags left (`-1f`); disabled on deleted or `SENDING` messages.
- **Elastic Rubber-Band Curve (beyond `56.dp`)**:
  ```text
  if rawDragPx <= triggerPx:
      visualOffset = rawDragPx
  else:
      excess = rawDragPx - triggerPx
      maxExtra = max(1, maxPx - triggerPx)
      visualOffset = triggerPx + maxExtra * (1 - 1 / (1 + excess / (triggerPx * 1.35)))
  ```
- **Haptic Detent**: Fires `HapticFeedbackType.LongPress` once when `visualOffset >= 56.dp` (resets if dragged back below `0.85 * 56.dp`).
- **Reply Pill Badge**:
  - Position: `Alignment.CenterStart`, `padding(start = 10.dp)`, `32.dp × 32.dp` `CircleShape`, `15.dp` `ic_reply`.
  - Animation (`progress = (currentOffset / triggerPx).coerceIn(0f, 1f)`):
    - `alpha = progress`
    - `scale = 0.55f + 0.45f * progress + (0.08f if triggered)`
    - `rotationZ = (1f - progress) * -28f`
    - Fill flips from `surfaceMuted` (`primaryBlue` icon) to `primaryBlue` (`White` icon) at `>= 56.dp`.
- **Snap-Back Spring**: `ChatMotion.layoutGestureSpring()` (`Spring.DampingRatioNoBouncy`, `stiffness = 420f`) on `onDragStopped`.
