# RECON AGENT 3 — Figma Iconography & Chat Component Specs

> **Scope**: Authoritative iconography & design-token specification for the Telegram-grade Chat & Group/DM overhaul.
> **Owned Artifacts**:
> - 15 Android vector drawables: `app/src/main/res/drawable/ic_tg_*.xml`
> - Token foundation: `app/src/main/java/app/buildx/chat/ui/chat/ChatTokens.kt`
> - **Guardrail**: Zero modifications to `ProfileScreen.kt`, `EditProfileScreen.kt`, or `SettingsScreens.kt`.

---

## 1. Telegram Vector Iconography (`app/src/main/res/drawable/ic_tg_*.xml`)

All 15 icons use a uniform `24dp × 24dp` viewport (`viewportWidth="24"`, `viewportHeight="24"`), `#FF0A0A0A` base ink (tinted dynamically via Compose `Icon(..., tint = ...)`), and `1.85dp` rounded strokes (`strokeLineCap="round"`, `strokeLineJoin="round"`) matching `ChatDimens.IconStrokeWeightEmphasis`.

| Drawable Resource | Role & Placement | Style / Stroke | `autoMirrored` | Geometry Notes |
| :--- | :--- | :--- | :--- | :--- |
| `ic_tg_send.xml` | External send action button (replaces generic `ic_arrow_up`) | Filled silhouette (`#FF0A0A0A`) | `false` | Authentic Telegram right-pointing aerodynamic paper plane with tapered inner crease slit (`x: 2.6..21.8`, `y: 3.6..20.4`). Explicitly non-mirrored (`autoMirrored="false"`) so it always points right/forward. |
| `ic_tg_emoji.xml` | Left side of ChatBox capsule (emoji/sticker toggle) | `1.85dp` stroke + filled eyes (`r=1.25`) | `false` | Outer face circle (`r=9.5` at `12,12`), dot eyes (`8.8,9.8` & `15.2,9.8`), warm cubic smile curve (`y: 14.0..15.8`). |
| `ic_tg_attach.xml` | Right side inside ChatBox capsule (media/file attach) | `1.85dp` stroke | `false` | Optically centered 45° Telegram paperclip (`r=5.5 / 3.75 / 2.0` concentric arcs). |
| `ic_tg_mic.xml` | External voice action button when input is empty | `1.85dp` stroke | `false` | Capsule condenser head (`x: 9..15, y: 2.75..13.25`), `r=6.5` cradle arc, stem & `7dp` base bar. |
| `ic_tg_reply.xml` | Context menu & swipe-reply action | `1.85dp` stroke | `true` | Left-pointing arrowhead (`x=3.5`) with curved `r=6` stem descending to `y=19`. |
| `ic_tg_forward.xml` | Context menu forward action | `1.85dp` stroke | `true` | Right-pointing mirror of `ic_tg_reply` (`x=20.5` arrowhead, `r=6` curved stem). |
| `ic_tg_copy.xml` | Context menu copy text action | `1.85dp` stroke | `false` | Two overlapping `2dp`-cornered sheets (`7.5..20.0` front, `3.5..15.5` back). |
| `ic_tg_pin.xml` | Context menu & chat info pin action | `1.85dp` stroke | `false` | 45°-tilted Telegram pushpin with cap (`13.5,4.5..19.5,10.5`), tapered neck, collar, and bottom-left needle (`4.0,20.0`). |
| `ic_tg_mute.xml` | Context menu & chat info mute notifications | `1.85dp` stroke | `false` | Bell dome (`r=6`) + clapper arc + diagonal slash (`3.5,3.5 -> 20.5,20.5`). |
| `ic_tg_unmute.xml` | Context menu & chat info unmute notifications | `1.85dp` stroke | `false` | Matching bell dome (`r=6`) + clapper arc without slash. |
| `ic_tg_edit.xml` | Context menu edit message action | `1.85dp` stroke | `false` | 45° rounded-cap pencil (`r=2.6` eraser cap, collar line, sharp nib at `3.0,21.0`). |
| `ic_tg_delete.xml` | Context menu destructive delete action | `1.85dp` stroke | `false` | Bin lid (`x: 3.5..20.5`), top handle (`r=1.5`), tapered bin body (`2dp` bottom corners), dual vertical slats. |
| `ic_tg_search.xml` | Header & chat info search action | `1.85dp` stroke | `false` | `r=7.25` lens centered at `(10.75, 10.75)` with 45° handle (`16,16 -> 20.5,20.5`). |
| `ic_tg_video_chat.xml` | Group/chat info video chat squircle action | `1.85dp` stroke | `false` | Rounded camera body (`x: 2.5..15.5, y: 6.5..17.5`) + right lens cone (`x: 15.5..21.5`). |
| `ic_tg_leave.xml` | Group/chat info leave group action | `1.85dp` stroke | `true` | Left exit door frame (`x: 4.0..10.5`) with outward right-pointing exit arrow (`x: 9.5..20.0`). |

---

## 2. Chat Component & Measurement Tokens (`ChatTokens.kt`)

All existing properties in `ChatColors`, `ChatGlassTokens`, `ChatDimens`, `ChatTypography`, and `ChatMotion` are preserved for 100% backward compatibility. New tokens added:

### 2.1 Tight Telegram Bubbles (`ChatDimens`)

| Token | Value | Purpose |
| :--- | :--- | :--- |
| `BubblePaddingHorizontal` | `11.dp` | Tight horizontal inner padding inside text bubbles |
| `BubblePaddingVertical` | `6.dp` | Tight vertical inner padding inside text bubbles |
| `BubbleIntraClusterSpacing` | `2.dp` | Vertical gap between consecutive messages in the same cluster |
| `BubbleInterClusterSpacing` | `8.dp` | Vertical gap between distinct sender/minute clusters |
| `BubbleCornerLarge` | `18.dp` | Outer bubble corner radius |
| `BubbleCornerSmall` | `4.dp` | Connected inner cluster / tail corner radius |

### 2.2 System Event & Date Pills (`ChatDimens`, `ChatColors`, `ChatTypography`)

| Token | Value (Light / Dark) | Purpose |
| :--- | :--- | :--- |
| `ChatDimens.SystemPillHeight` | `26.dp` | Fixed compact height for centered system events (`"created the group"`) & date pills |
| `ChatDimens.SystemPillCornerPercent` | `50` | Full capsule corner percentage (`RoundedCornerShape(percent = 50)`) |
| `ChatDimens.SystemPillPaddingH` | `12.dp` | Horizontal inner padding inside system/date pills |
| `ChatDimens.SystemPillPaddingV` | `4.dp` | Vertical inner padding inside system/date pills |
| `ChatColors.systemPillBg` | `#CCEBEEF2` / `#CC1B2027` | 80% translucent pill fill over light/dark/wallpaper canvases |
| `ChatColors.systemPillText` | `#FF4F5660` / `#FFD0D5DC` | High-contrast pill label text |
| `ChatColors.systemPillBorder` | `#14000000` / `#1FFFFFFF` | Subtle hairline contour around translucent pills |
| `ChatTypography.systemPill` | `12.sp` / `16.sp` Medium (`ContentOrLtr`) | BiDi-safe pill typography |

### 2.3 Telegram ChatBox (`ChatDimens`)

| Token | Value | Purpose |
| :--- | :--- | :--- |
| `ComposerCapsuleMinHeight` | `46.dp` | Minimum height of the main input capsule (`ic_tg_emoji` + input + `ic_tg_attach`) |
| `ComposerExternalButtonSize` | `48.dp` | External circular Mic (`ic_tg_mic`) / Send (`ic_tg_send`) action button diameter |
| `UnifiedReplyBarHeight` | `42.dp` | Height of the integrated reply/edit preview strip inside the unified ChatBox |
| `UnifiedReplyAccentWidth` | `3.dp` | Vertical accent bar thickness on the unified reply/edit strip |

### 2.4 Reaction Pill Bar & Left-Icon Context Menu (`ChatDimens`, `ChatColors`)

| Token | Value (Light / Dark) | Purpose |
| :--- | :--- | :--- |
| `ChatDimens.ReactionPillHeight` | `44.dp` | Floating emoji reaction bar height (`👍 ❤️ 🔥 😍 👏 🤔`) |
| `ChatDimens.ReactionPillCornerRadius` | `22.dp` | Pill corner radius (`height / 2`) |
| `ChatDimens.ContextMenuWidth` | `210.dp` | Compact left-icon message context menu width |
| `ChatDimens.ContextMenuCornerRadius` | `16.dp` | Context menu surface corner radius |
| `ChatColors.reactionPillBg` | `#F5FFFFFF` / `#F21E2228` | Floating reaction pill surface fill |
| `ChatColors.reactionPillBorder` | `subtleSurfaceBorder` | Hairline border for reaction pill |
| `ChatColors.contextMenuBg` | `#F8FFFFFF` / `#F51D2126` | Context menu popup fill |
| `ChatColors.contextMenuBorder` | `subtleSurfaceBorder` | Hairline border for context menu |

### 2.5 Group / Chat Info Screen (`ChatDimens`)

| Token | Value | Purpose |
| :--- | :--- | :--- |
| `ChatInfoAvatarExpandedSize` | `88.dp` | Hero avatar size when Chat/Group Info header is expanded |
| `ChatInfoAvatarCollapsedSize` | `36.dp` | Avatar size when collapsed into the status-bar-safe pinned top bar |
| `ChatInfoActionSquircleRadius` | `14.dp` | Squircle corner radius for quick-action tiles (`Mute`, `Video Chat`, `Search`, `Leave`) |
| `ChatInfoTopBarHeight` | `56.dp` | Height of the pinned status-bar-safe top bar |

### 2.6 Send Flight Spring Physics (`ChatMotion`)

| Symbol | Value | Purpose |
| :--- | :--- | :--- |
| `ChatMotion.SendFlightDampingRatio` | `0.82f` | Crisp slight-settle damping for composer-to-bubble flight |
| `ChatMotion.SendFlightStiffness` | `420f` | Responsive 120fps spring stiffness |
| `ChatMotion.sendFlightSpring<T>(visibilityThreshold)` | `spring(0.82f, 420f)` | Reusable `SpringSpec<T>` for `graphicsLayer` send flight & collapse sync |
