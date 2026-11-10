# Phase C — Profile System UI/UX Design & Token Specification (`docs/profile/DESIGN_SPEC.md`)

Screen Reference Viewport: **`411dp x 914dp`** (`1080px x 2400px` at `2.625x` density)  
Design Language: **Build X** (ultra-clean, soft diffuse shadows, full-pill controls, rounded grouped cards, zero hard borders, zero emoji in UI strings, full RTL/LTR symmetry)

---

## 1. Color Token Mapping (Existing + Documented New Tokens)

All colors map directly to [`BuildXColors`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/theme/BuildXTheme.kt#L38-L105) in `BuildXTheme.kt` and [`docs/design/tokens.json`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/docs/design/tokens.json):

| Token Name | Light Value | Dark Value | Source | Usage in Profile System |
|---|---|---|---|---|
| `pageBackground` | `#FFFFFF` | `#0E1012` | Existing (`tokens.json`) | Profile screen (`P1`, `P2`), Follow lists (`P4`), Search (`P8`), Notifications (`P9`) |
| `surfaceMuted` | `#F1F3F0` | `#1A1D20` | Existing (`tokens.json`) | Secondary pill buttons (`Following v`, `Follow` secondary), Edit Profile background (`P3`), Note sheet background (`P5`), Search field pill, Skeleton blocks |
| `cardBackground` | `#FFFFFF` | `#171A1E` | Existing (`tokens.json`) | Header 44dp circle buttons, `ActionCapsule` (`Bell` + `Share`), Note speech bubble above avatar, Edit Profile grouped cards, Unfollow/Bell popups |
| `primaryText` | `#0A0A0A` | `#F3F5F6` | Existing (`tokens.json`) | Display name (`32sp Bold`), stat numbers (`20sp Bold`), bio text (`16sp Regular`), secondary pill button label (`15sp Bold`), Note text (`13.5sp SemiBold`) |
| `secondaryText` | `#6E7274` | `#9BA0A5` | Existing (`tokens.json`) | `@username` (`16sp SemiBold`), stat labels (`15sp Medium`), `"Add bio"` prompt, Note placeholder (`"Share a thought..."`), timestamps, character counters |
| `primaryBlue` | `#0063D3` | `#3B8BF7` | Existing (`tokens.json`) | Primary `Follow` / `Follow back` pill button, `Save` / `Share` primary CTA, **Verified badge seal (`16dp`)**, unread notification dot (`8dp`), auto-linked bio URLs |
| `onPrimaryBlue` | `#FFFFFF` | `#FFFFFF` | Existing (`tokens.json`) | Label text on primary blue buttons, inner checkmark of the Verified seal |
| `destructiveRed` | `#C3192E` | `#FF5A6E` | Existing (`tokens.json`) | `"Unfollow @username"` popup action, `"Delete note"` action in Note sheet, username validation errors |
| `statusAvailableGreen` | `#168A45` | `#34C769` | **New Token** (Documented) | Live username availability confirmation (`"Username is available"`) in Sign-Up & `P3 Edit Profile` |
| `hairlineBorder` | `#E1E3E1` | `#2E3338` | Existing (`tokens.json`) | `1dp` dividers inside grouped cards, popup menus, and tab bar baseline |
| `subtleSurfaceBorder` | `#ECEEEC` | `#25292E` | Existing (`tokens.json`) | `0.8dp` contour around floating white header circles, `ActionCapsule`, and Note speech bubble |
| `headerHaloShadowColor` | `#16000000` | `#3D000000` | Existing (`tokens.json`) | Soft diffuse shadow (`blur = 14dp, offsetY = 2.5dp`) under 44dp circles, `ActionCapsule`, and Note speech bubble |
| `composerShadowColor` | `#1E000000` | `#55000000` | Existing (`tokens.json`) | Soft diffuse shadow (`blur = 20dp, offsetY = 6dp`) under floating popup cards (`Unfollow`, `Bell notice`) |
| `scrimColor` | `#4D000000` | `#73000000` | Existing (`tokens.json`) | `30%` (Light) / `45%` (Dark) backdrop scrim behind `P5 Note Bottom Sheet` |

---

## 2. Typography Scale & Bidi / RTL Rules

Fonts: **`Plus Jakarta Sans`** (Latin) + **`Noto Sans Arabic`** (Arabic), bundled in `app/src/main/res/font/`.

| Element | Size | Weight | Line Height | Color Token | Direction / Alignment Rule |
|---|---|---|---|---|---|
| **Profile Display Name** | `30sp` (scales up to `32sp`) | `Bold` (`700`) | `36sp` | `primaryText` | `TextDirection.Content` (auto-detects Arabic vs Latin; max 2 lines) |
| **Profile `@username`** | `16sp` | `SemiBold` (`600`) | `21sp` | `secondaryText` | **Always LTR** (`\u200E@username`), followed by `4dp` gap + `16dp` Verified Seal |
| **Stat Number (`Following` / `Followers`)** | `20sp` | `Bold` (`700`) | `24sp` | `primaryText` | Compact locale-aware formatting (`28`, `1.2K`, `7.6M`, `99M`) |
| **Stat Label (`Following` / `Followers`)** | `14.5sp` | `Medium` (`500`) | `18sp` | `secondaryText` | Localized (`Following` / `أتابعه`, `Followers` / `المتابعون`) |
| **Action Pill Button Label (`Follow`, `Following`)** | `15.5sp` | `Bold` (`700`) | `20sp` | `onPrimaryBlue` / `primaryText` | Centered inside `42dp` height full-pill (`999dp` radius); `48dp` min touch target |
| **Bio Body Text** | `15.5sp` | `Normal` (`400`) | `22sp` | `primaryText` | `TextDirection.Content` per paragraph; max 160 chars, max 4 line breaks |
| **Bio Auto-Linked URL** | `15.5sp` | `SemiBold` (`600`) | `22sp` | `primaryBlue` | Single-line truncated with ellipsis (`maxLines = 1`), clickable via `Intent.ACTION_VIEW` |
| **Note Bubble Text** | `13sp` | `SemiBold` (`600`) | `17sp` | `primaryText` (active) / `secondaryText` (prompt) | `TextDirection.Content`, max 2 lines inside bubble (`<= 60` chars) |
| **List Row Title (Name)** | `16sp` | `Bold` (`700`) | `21sp` | `primaryText` | `TextDirection.Content`, `maxLines = 1`, ellipsis + `15dp` Verified Seal |
| **List Row Subtitle (`@username`)** | `14sp` | `Medium` (`500`) | `18sp` | `secondaryText` | Always LTR (`\u200E@username`) |
| **Screen Header Title** | `20sp` | `Bold` (`700`) | `26sp` | `primaryText` | Start-aligned `12dp` after `44dp` back/close circle button |

---

## 3. Component Geometry & Layout Specifications (`411dp x 914dp`)

1. **Top Header Bar (`P1`, `P2`, `P3`, `P4`, `P8`, `P9`)**:
   - Horizontal padding: `20dp`; vertical padding: `10dp`.
   - Start control: `HeaderCircleButton` (`44dp` visual circle inside `48dp` touch target, `cardBackground`, `0.8dp subtleSurfaceBorder`, `softShadow(blur = 14dp, offsetY = 2.5dp)`).
   - End control on **`P1 Own Profile`**: `HeaderCircleButton` with `ic_pencil_edit` (`22dp`), `contentDescription = "Edit profile"`.
   - End control on **`P2 Other Profile`**: `ProfileActionCapsule` (`92dp x 44dp`, radius `22dp`, `cardBackground`, `0.8dp subtleSurfaceBorder`, `softShadow`), containing two `44dp` touch targets:
     - Start half: `ic_bell` (outline when unsubscribed) or `ic_bell_filled` (filled `#0063D3` when subscribed to notes).
     - End half: `ic_share` (opens Android system share sheet with `buildx://u/{username}`).
2. **Profile Identity & Avatar Row (`P1`, `P2`)**:
   - Content inset: `horizontal = 20dp`.
   - Start column (`weight(1f)`, `padding(end = 16dp)`):
     - Display Name (`30sp Bold`, `primaryText`).
     - `Spacer(4.dp)`
     - Username Row: `@username` (`16sp SemiBold`, `secondaryText`, LTR) + if `is_verified`, `Spacer(5.dp)` + `VerifiedBadgeIcon(size = 16.dp)`.
     - `Spacer(16.dp)`
     - Stats Row (`horizontalArrangement = Arrangement.spacedBy(28.dp)`):
       - **Following** stat block (`clickable`, `minHeight = 48.dp`): `following_count` (`20sp Bold`) over `"Following"` (`14.5sp Medium`).
       - **Followers** stat block (`clickable`, `minHeight = 48.dp`): `followers_count` (`20sp Bold`) over `"Followers"` (`14.5sp Medium`).
   - End column (Avatar + Note Bubble container, `width = 112.dp`, `horizontalAlignment = Alignment.CenterHorizontally`):
     - **Avatar**: `96dp` circle (`surfaceMuted` fallback with `40dp` `ic_user`, `0.8dp subtleSurfaceBorder`, no `+` badge, no story ring).
     - **Public Note Bubble**: floating speech-bubble card positioned overlapping the top-start of the `96dp` avatar (`offset(y = (-14).dp)`):
       - Pill/rounded card (`cornerRadius = 18dp`, `cardBackground`, `0.8dp subtleSurfaceBorder`, `softShadow(blur = 14dp, offsetY = 3dp)`), `padding(horizontal = 12.dp, vertical = 7.dp)`, with two small speech-bubble tail dots (`7dp` and `4dp` circles) pointing toward the avatar.
       - On `P1 Own Profile` when empty: shows `"Share a thought..."` / `"شارك فكرة..."` in `secondaryText`.
       - On `P1` / `P2` when active (`expires_at > now()`): shows the note text in `primaryText`.
       - On `P2` when the user has no active note: hidden completely.
3. **Action Buttons Row on `P2 Other Profile` (Message Button Hidden per `Q1a`)**:
   - Placed `18dp` below the identity row, `fillMaxWidth()`, `height = 44.dp` (`48dp` touch target), full pill (`RoundedCornerShape(999.dp)`), animated via `animateColorAsState` + `AnimatedContent` (`250ms` smooth transition, zero layout jump):
   - **Not Following**: Full-width primary pill (`background = primaryBlue`, `text = "Follow"` in `onPrimaryBlue 15.5sp Bold`).
   - **Not Following + `follows_you == true`**: Full-width primary pill (`background = primaryBlue`, `text = "Follow back"` / `"رد المتابعة"` in `onPrimaryBlue 15.5sp Bold`).
   - **Following**: Full-width secondary pill (`background = surfaceMuted`, `text = "Following"` / `"تتابعه"` in `primaryText 15.5sp Bold` + `Spacer(6.dp)` + `14dp` `ic_caret_down`).
     - Tapping `"Following v"` opens an anchored app-styled popup card (`cornerRadius = 16dp`, `cardBackground`, `softShadow`) with destructive action `"Unfollow @username"` (`destructiveRed`, `ic_user_unfollow`).
4. **Bio Section (`P1`, `P2`)**:
   - Placed `16dp` below stats (on `P1`) or below action button (on `P2`).
   - On `P1 Own Profile` when `bio.isBlank()`: renders tappable `"Add bio"` / `"إضافة نبذة"` pill/link in `secondaryText` (`15sp SemiBold`) that opens `P3 Edit Profile`.
   - When non-empty: renders up to 5 lines (`<= 160` chars, `<= 4` newlines) with `TextDirection.Content` and auto-detects `https://` / `http://` URLs, rendering them on a single truncated line in `primaryBlue` (`FontWeight.SemiBold`).

---

## 4. Custom Vector Icons Specification (`24dp` Viewport, `1.75dp` Stroke, Rounded Caps)

Saved in `docs/profile/icons/`:
1. **`ic_verified_badge.svg` / `ic_verified_badge.xml`**: `16dp` default display size (`24x24` viewport), 8-scallop rosettes seal filled with `primaryBlue` (`#0063D3` / `#3B8BF7`) and a crisp white (`#FFFFFF`) rounded checkmark (`strokeWidth = 2.2`, `strokeLineCap = round`, `strokeLineJoin = round`). `contentDescription = "Verified account"`.
2. **`ic_pencil_edit.svg` / `ic_pencil_edit.xml`**: Clean diagonal outline pencil (`strokeWidth = 1.75`, `strokeLineCap = round`, `strokeLineJoin = round`) for the top-end button on `P1 Own Profile`.
3. **`ic_bell_filled.svg` / `ic_bell_filled.xml`**: Filled bell with clapper in `primaryBlue` for active note subscription state on `P2 Other Profile`.
4. **`ic_caret_down.svg` / `ic_caret_down.xml`**: Compact downward chevron (`M7,10 L12,15 L17,10`, `strokeWidth = 2.0`, rounded caps) for the `Following v` button.
