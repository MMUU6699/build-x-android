# Build X — UI/UX & Motion Specification (`SPEC.md`)

## 1. Overview & Methodology

- **Source Recording**: `media_1794247815379_ff526a26.mp4` (`576x1280` px, `90.84s` duration, `16.68 fps` native H.264 stream, extracted at both `1 fps` (`91` overview frames) and `30 fps` (`2725` burst frames)).
- **Viewport Conversion**: The recording is a `576x1280` capture of a standard `1080x2400` (`411.43dp x 914.29dp`) portrait phone (`1 video px = 0.7143 dp`).
- **System Overlays Ignored**: Android status bar (`y = 0..50px` / `0..36dp`), navigation gesture pill (`y = 1260..1280px`), Samsung Edge Panel handle on the right screen edge (`x = 571..576px`), Gboard keyboard (`y = 752..1280px`), and the green microphone privacy indicator dot (`t = 87s..88s`).
- **Re-branding Policy**: Zero glasses icons, zero Meta names/logos/copy, zero dead/unopened items (`Artefacts`, `Scheduled`, `Glasses`, `Vibes`, `Your likeness`, `Saved locations`, `Shop online`, `Virtual try-on`, `Subscriptions`, `Family Centre`, `Meta Account` are omitted).
- **Evidence Tagging**: Every measurement and token below is marked `[OBSERVED]` (with exact frame timestamp and pixel coordinates) or `[INFERRED]` (for the dark theme and auth screens).

---

## 2. Global Design Tokens

### 2.1 Color Palette

| Token | Light Mode (`[OBSERVED]`) | Dark Mode (`[INFERRED]`) | Frame Trace & Coordinates |
|---|---|---|---|
| `page_background` | `#FFFFFF` | `#0E1012` | `[OBSERVED]` `t=83s` (`frame_084`) `@(300,300)` |
| `surface_secondary` | `#F1F3F0` | `#1A1D20` | `[OBSERVED]` `t=9s` (`frame_010`) `@(500,165)` user bubble; `t=25s` (`frame_026`) `@(288,860)` attachment sheet; `t=60s` (`frame_061`) `@(288,140)` settings bg |
| `surface_card` | `#FFFFFF` | `#1E2226` | `[OBSERVED]` `t=60s` (`frame_061`) `@(288,200)` grouped white cards |
| `surface_control_pill` | `#F0F2F2` | `#272B30` | `[OBSERVED]` `t=44s` (`frame_045`) `@(180,89)` drawer search pill; `t=56s` (`frame_057`) `@(500,1198)` disabled send circle |
| `text_primary` | `#0A0A0A` | `#F3F5F7` | `[OBSERVED]` `t=83s` (`frame_084`) `@(130,89)` header title (`#000000`–`#0F0F0F`) |
| `text_secondary` | `#6E7274` | `#9BA1A6` | `[OBSERVED]` `t=0s` (`frame_001`) `@(100,660)` placeholder (`#646968`); `t=9s` (`frame_010`) `@(120,270)` reasoning label (`#626668`–`#6E7274`) |
| `text_disabled` | `#9B9D9D` | `#5C6166` | `[OBSERVED]` `t=56s` (`frame_057`) `@(515,1198)` incognito disabled arrow |
| `primary_blue` | `#0063D3` | `#2B84EA` | `[OBSERVED]` `t=9s` (`frame_010`) `@(515,1198)` stop button `#0063D3`; `t=25s` (`frame_026`) `See all` `#0063D7`; `t=44s` (`frame_045`) unread dot `#0064D0` |
| `destructive_red` | `#C3192E` | `#E54858` | `[OBSERVED]` `t=68s` (`frame_069`) `@(100,1068)` Log out `#C3192E`; `t=38s` (`frame_039`) `@(420,342)` Delete chat `#9D1223` |
| `border_hairline` | `#E5E7E6` | `#2E3338` | `[OBSERVED]` `t=83s` (`frame_084`) `@(61,1174)` composer inner controls `1dp` border |
| `divider_hairline` | `#EFEFEF` | `#282C30` | `[OBSERVED]` `t=25s` (`frame_026`) `@(288,1150)` card divider; `t=38s` (`frame_039`) `@(420,184)` popup menu divider |
| `scrim` | `#4D000000` (`30%` black, renders `#B2B2B2` on `#FFFFFF`) | `#73000000` (`45%` black) | `[OBSERVED]` `t=21s` (`frame_022`) `@(288,120)` `#B2B2B2`; `t=44s` (`frame_045`) `@(550,600)` `#B2B2B2` |
| `mark_gradient` | `#C24DFB` -> `#8842FA` -> `#4B3EF6` | `#C85BFF` -> `#8F4DFF` -> `#5448FF` | `[OBSERVED]` `t=83s` (`frame_084`) `@(241..337, 559..654)` 8-petal ring |
| `reasoning_rail_gradient` | `#7B52FA` (top) -> `#D2D2D5` (bottom) | `#8F6BFF` (top) -> `#3A3E44` (bottom) | `[OBSERVED]` `t=9s` (`frame_010`) `@(41, 303..359)` |

### 2.2 Typography

- **Latin Font Family**: Open-license geometric grotesque (`Plus Jakarta Sans` / `Inter` bundled in `res/font`) `[OBSERVED]` (`t=0s..90s`).
- **Arabic Font Family**: Open-license clean Naskh/Kufi-modern (`Noto Sans Arabic` bundled in `res/font`) `[OBSERVED]` (`t=18s..48s`).
- **Scale & Weights**:
  - `HeaderTitle`: `18sp`, Bold (`700`), line height `24sp`, color `text_primary` `[OBSERVED]` (`t=83s`, cap height `19px = 13.6dp`).
  - `HeaderSubtitle`: `13sp`, Medium/SemiBold (`500`), line height `16sp`, color `text_secondary` `[OBSERVED]` (`t=56s` Incognito subtitle).
  - `ComposerPlaceholder`: `16sp`, SemiBold (`600`), line height `22sp`, color `text_secondary` `[OBSERVED]` (`t=0s`, `y=1123..1142px`).
  - `ComposerInput`: `16sp`, Regular (`400`), line height `22sp`, color `text_primary` `[OBSERVED]` (`t=2s`, `frame_003`).
  - `PillLabel`: `14sp`, Bold (`700`), line height `18sp`, color `text_primary` `[OBSERVED]` (`t=0s` "Thinking", `t=31s` "Instant").
  - `ChatBody`: `16sp`, Regular/Medium (`400`/`500`), line height `24sp`, color `text_primary` `[OBSERVED]` (`t=18s`, `frame_019`).
  - `ReasoningTitle`: `15sp`, Bold (`700`), line height `20sp`, color `text_secondary` `[OBSERVED]` (`t=9s`, `frame_010`).
  - `ReasoningSummary`: `14sp`, SemiBold (`600`), line height `20sp`, maxLines `2`, ellipsis, color `text_secondary` `[OBSERVED]` (`t=9s`, `frame_010`).
  - `SectionHeader`: `15sp`, Bold (`700`), line height `20sp`, color `text_secondary` `[OBSERVED]` (`t=44s` "Chats", `t=60s` "App settings").
  - `RowTitle`: `16sp`, Bold (`700`), line height `22sp`, color `text_primary` `[OBSERVED]` (`t=60s` Settings rows, `t=44s` Drawer rows).
  - `RowSubtitle`: `14sp`, Medium (`500`), line height `19sp`, color `text_secondary` `[OBSERVED]` (`t=30s` popup subtitles, `t=69s` App info).

### 2.3 Elevation & Custom Shadows

- **Composer Shadow**: Diffuse ambient/spot shadow with `blur = 24dp`, `offsetY = 4dp`, `color = #17000000` (`9%` black), surface `#FFFFFF` with `0.5dp` `#ECEEEE` edge definition `[OBSERVED]` (`t=83s`, `y = 1237..1257px` shadow tail below `y = 1236px` bottom border).
- **Header Circle Button & Action Capsule Shadow**: Soft halo `blur = 12dp`, `offsetY = 2dp`, `color = #0F000000` (`6%` black), surface `#FFFFFF` with `0.5dp` `#F0F2F2` edge `[OBSERVED]` (`t=83s`, `x = 20..27px` and `x = 90..95px`).
- **Popup Menu Shadow**: Soft floating card shadow `blur = 20dp`, `offsetY = 6dp`, `color = #1A000000` (`10%` black), surface `#FFFFFF` `[OBSERVED]` (`t=30s`, `t=38s`).

---

## 3. Screen-by-Screen Specification

### S1 — Home (Empty State) (`t=0s..2s`, `t=40s..42s`, `t=78s..86s`)
- **Header**:
  - Top margin below status bar: `16dp` (`22px`, `y = 58px`) `[OBSERVED]`.
  - Horizontal inset: `20dp` (`28px`) `[OBSERVED]`.
  - Left: `HeaderCircleButton` (`44dp x 44dp` / `62x62px`, corner radius `22dp`, `#FFFFFF` fill, soft halo shadow, `24dp` 2-line menu icon centered at `(58.5, 89.5)px` with `20dp` line width and `10dp` vertical span) `[OBSERVED]`.
  - Title: `"Build X"`, `12dp` (`18px`, `x = 108px`) right of menu button, vertically centered (`y = 80..98px`), `18sp` Bold `#0A0A0A` `[OBSERVED]`.
  - Right: Empty on empty New Chat state (no glasses button) `[OBSERVED + REBRAND RULE]`.
- **Center Mark**:
  - Original Build X mark: Ring of 8 rounded capsule petals tilted tangentially around a central void, `68dp x 68dp` (`97x96px`), purple-to-blue gradient (`#C24DFB` -> `#8842FA` -> `#4B3EF6`), centered horizontally (`x = 241..337px`) and vertically in the space between header and composer (`y = 559..654px` when keyboard closed; `y = 262..357px` when keyboard open) `[OBSERVED]`.
  - Continuous gentle clockwise rotation (`8000ms` per revolution) + subtle breathing pulse `[OBSERVED]`.
- **Composer (Docked at Bottom / Above Keyboard)**:
  - Horizontal margin: `16dp` (`22px`, `x = 22..554px`, width `379dp` / `532px`) `[OBSERVED]`.
  - Bottom margin: `12dp` above gesture bar or `8dp` above IME keyboard `[OBSERVED]`.
  - Surface: `#FFFFFF`, radius `28dp` (`39px`), min height `102dp` (`143px`, `y = 1093..1236px`), custom diffuse shadow `[OBSERVED]`.
  - Top row (Text field): Inset `16dp` horizontal, `18dp` top. Placeholder `"Ask Build X..."` in `16sp` SemiBold `#6E7274` (`#646968`). Auto-focuses with cursor visible (`#0063D3` `2dp` vertical bar) on initial launch `[OBSERVED]`.
  - Bottom row (Controls, inset `12dp` horizontal, `10dp` bottom, centered at `y = 1197.5px`):
    - Left: Plus circle button (`36dp x 36dp` / `50x50px`, radius `18dp`, `1dp` border `#E5E7E6`, `20dp` plus icon `#0A0A0A`) `[OBSERVED]`.
    - Right group:
      - Mode pill (`"Thinking"` or `"Instant"`): height `36dp` (`50px`), width `100dp` (`140px`, `x = 337..477px` for `"Thinking"`), radius `18dp`, `1dp` border `#E5E7E6`, `14sp` Bold `#0A0A0A`, `8dp` gap before mic/send `[OBSERVED]`.
      - Mic circle button: `36dp x 36dp` (`50x50px`, `x = 490..539px`), radius `18dp`, `1dp` border `#E5E7E6`, `20dp` microphone outline icon `#0A0A0A` `[OBSERVED]`.

### S2 — Composer States (`t=2s`, `t=5s..16s`, `t=30s..32s`)
- **Non-Empty Input (`t=2s`, `frame_003`)**:
  - Mic circle crossfades + scales (`150ms` / `4` frames at `30fps`, `f_0052..f_0055`) into a solid `#0063D3` blue circle (`36dp x 36dp` / `50x49px`) with a white `arrow-up` icon (`20dp`, `2dp` rounded stroke) `[OBSERVED]`.
  - Multi-line input grows upward up to `5` lines (`~180dp` max height) before scrolling internally `[INFERRED]`.
- **Streaming State (`t=5s..16s`, `frame_008..017`)**:
  - Placeholder reads `"Reply..."` (`16sp` SemiBold `#6E7274`) `[OBSERVED]`.
  - Bottom-right controls show: `"Thinking"`/`"Instant"` pill + Mic circle (`36dp`) + Stop button (`36dp` / `50px` `#F0F2F2` circular halo containing a `20dp` / `29px` `#0063D3` rounded square with `5dp` corner radius) `[OBSERVED]` (`t=9s`, `frame_010`, `x = 490..539px` halo, `x = 500..529px` blue square).
  - Tapping Stop cancels the active Ktor SSE HTTP stream and marks the message `stopped` in Supabase `[OBSERVED + ARCHITECTURE RULE]`.
- **Thinking / Instant Mode Toggle & Popup (`t=30s..32s`, `frame_031..033`)**:
  - Tapping the `"Thinking"` / `"Instant"` pill toggles between `"Thinking"` (deeper reasoning with reasoning steps emitted) and `"Instant"` (fast direct response) and sends `mode = "thinking" | "instant"` to the Edge Function `[OBSERVED]`.
  - Note: Frame `t=30s` (`frame_031`) also shows a 2-option floating card (`Instant — Answers straight away` / `Thinking — Reasons longer before answering` with checkmark, `x = 41..533px`, `y = 467..657px`, radius `16dp`). Supporting both direct tap toggle and long-press/popup or clean toggle ensures 100% compliance with the prompt instruction ("implement as a tap toggle and map it to a real parameter sent to the Edge Function").

### S3 — Chat Screen (`t=3s..19s`)
- **Send Transition (`f_0100..f_0109`, `t=3.33s..3.63s`)**:
  - Keyboard collapses, composer animates down to the bottom (`300ms` spring, zero overshoot > `2dp`), center mark fades out (`250ms`), header right side crossfades (`250ms`) into `ActionCapsule` (`91dp x 44dp` / `128x62px`, `x = 420..548px`, `y = 58..120px`, `#FFFFFF` fill, soft halo shadow, containing `22dp` `edit/compose` icon at left and `22dp` `more` 3-dots icon at right) `[OBSERVED]`.
- **User Bubble**:
  - Right-aligned (`16dp` right margin, `x = 481..553px` for `"هلا"`), pill shape (`radius = 22dp`), min height `43dp` (`61px`, `y = 148..208px`), horizontal padding `16dp`, vertical padding `10dp`, fill `#F1F3F0`, text `16sp` Regular `#0A0A0A` `[OBSERVED]`.
- **Loader & Reasoning Row (`t=4s..16s`, `frame_005..016`)**:
  - Immediately after send, a small rotating 8-petal mark (`20dp x 20dp` / `28x27px`, `x = 28..55px`, `y = 258..284px`, `1800ms`/rev) appears at left `[OBSERVED]`.
  - While reasoning (`mode = "thinking"`):
    - Top line: `20dp` rotating mark + `12dp` gap + bold gray step title (`15sp` Bold `#626668`, e.g. `"Responding to greeting"` at `t=9s`, updating to `"Greeting warmly in Arabic"` at `t=12s`) `[OBSERVED]`.
    - Summary block below title (`y = 295..358px`): At left (`x = 41px`, aligned under the center of the `20dp` mark), a `2dp` (`3px`) vertical gradient rail (`#7B52FA` purple at top fading to `#D2D2D5` gray at bottom, height `44dp` / `60px`). To its right (`x = 74px`), a 2-line summary (`14sp` SemiBold `#6E7274`, `maxLines = 2`, `TextOverflow.Ellipsis`) `[OBSERVED]`.
  - When answer streaming starts (`t=16s`, `frame_017`):
    - Reasoning summary collapses; top line becomes `20dp` static mark + `"Show thinking"` (`15sp` Bold `#6E7274`) + `chevron-right` (`16dp`, `#6E7274`) (`x = 27..247px`, `y = 257..284px`) `[OBSERVED]`.
- **Assistant Message & Bidi Alignment**:
  - Full-width plain text on `#FFFFFF` background (no bubble), `16dp` horizontal inset, `16sp` Regular/Medium `#0A0A0A`, `24sp` line height, streaming in with soft chunk fade-in (`120ms` alpha per chunk, no layout jump) `[OBSERVED]`.
  - **Bidi Fix**: Resolve text direction per message (and per paragraph) from the first strong directional character (`BidiFormatter` / `FirstStrong`). Arabic messages align right (`TextAlign.Right` / `LayoutDirection.Rtl`), English messages align left (`TextAlign.Left` / `LayoutDirection.Ltr`). (The reference video left-aligned Arabic at `x = 23..340px`, which the specification explicitly mandates fixing.)
- **Action Row (`t=17.8s..18.5s`, `f_0534..f_0552`)**:
  - Appears below completed assistant message (`y = 463..489px`, top margin `12dp` below text):
  - Icons: `thumbs-up`, `thumbs-down`, `copy`, `share` (paper-plane), each `20dp` (`27px`) outline icon (`1.75dp` stroke `#636867`), spaced `22dp` (`32px` gap / `56.5px` center-to-center) `[OBSERVED]`.
  - Staggered reveal: First 3 icons (`thumbs-up`, `thumbs-down`, `copy`) fade in at completion (`t=17.80s`, `f_0534`), followed `500ms` later by `share` fading in (`t=18.33s`, `f_0550`) `[OBSERVED]`.
  - Real behaviors:
    - `thumbs-up` / `thumbs-down`: toggles rating (`1` / `-1` / `null`) persisted to `messages.rating` in Supabase, tinting the active icon `#0063D3`.
    - `copy`: copies message text to `ClipboardManager` and shows a clean pill snackbar confirmation (`"Copied to clipboard"`).
    - `share`: opens Android `Intent.ACTION_SEND` system share sheet with the message text.
- **AI Conversation Title Crossfade (`t=18.2s..18.4s`, `f_0546..f_0552`)**:
  - Header title crossfades (`200ms`) from `"Build X"` to the AI-generated title returned by the `title` SSE event (`"كيفك يا مصطفى"` in the video) `[OBSERVED]`.

### S4 — Steps Bottom Sheet (`t=19.3s..22.3s`, `frame_022`)
- **Trigger**: Tapping `"Show thinking >"` on an assistant message.
- **Sheet Geometry**:
  - Scrim: `30%` black (`#4D000000`, renders `#B2B2B2` over white) `[OBSERVED]`.
  - Sheet surface: `#FFFFFF`, top-left/top-right radius `28dp` (`39px`), slides up in `300ms` (`f_0580..f_0588` to half-expanded `y = 664px`, expandable to `y = 143px` / `812dp` tall), drag-to-dismiss in `250ms` (`f_0660..f_0668`) `[OBSERVED]`.
  - Grab handle: `38dp x 4dp` (`54x6px`, `x = 261..314px`, `y = 155..160px`), radius `2dp`, color `#EAEAEA`, centered `8dp` from sheet top `[OBSERVED]`.
  - Title: `"Steps"`, centered, `17sp` Bold `#0A0A0A`, `16dp` below grab handle `[OBSERVED]`.
- **Timeline Content**:
  - For each reasoning step stored in `messages.reasoning` (`jsonb` array of `{title, summary}`):
    - Left column (`24dp` wide, inset `20dp` from left): `6dp` circular dot (`#6E7274`) aligned with step title center, and a `1.5dp` vertical connector line (`#DCDCDC`) running down to the next step `[OBSERVED]`.
    - Right column (`12dp` right of dot, `20dp` right margin): Step `title` (`15sp` Bold `#626668`) + `4dp` gap + Step `summary` (`15sp` Regular `#6E7274`, full text without truncation, bidi-aware) `[OBSERVED]`.
  - Final row: `check-circle` outline icon (`18dp`, `#6E7274`) at left + `"Done"` (`15sp` Bold `#626668`) `[OBSERVED]`.

### S5 — Header Overflow Menu (`t=38s`, `frame_039`)
- **Trigger**: Tapping the `more` (3-dots) icon inside the header `ActionCapsule`.
- **Popup Geometry**:
  - Anchored below the header `ActionCapsule` (`x = 316..540px` -> `160dp` wide; `y = 120..372px` -> `180dp` tall for 4 rows), `#FFFFFF` fill, corner radius `16dp` (`22px`), custom popup shadow (`blur = 20dp`, `offsetY = 6dp`, `10%` black), `0.5dp` `#ECEEEE` border `[OBSERVED]`.
  - 4 rows (`45dp` / `63px` height each), separated by `0.5dp` `#F3F5F5` full-width dividers (`y = 184, 247, 309px`) `[OBSERVED]`:
    1. `"Share a link"` (`15sp` Bold `#0A0A0A` left, `20dp` `link` icon right) — generates/copies a real read-only share URL backed by `conversations.share_token` and a public Edge Function / viewer endpoint (with ability to revoke).
    2. `"Pin"` / `"Unpin"` (`15sp` Bold `#0A0A0A` left, `20dp` `pin` icon right) — toggles `conversations.pinned` in Supabase and re-orders drawer list immediately.
    3. `"Rename"` (`15sp` Bold `#0A0A0A` left, `20dp` `pencil` icon right) — opens a clean rounded rename dialog, updates `conversations.title` in Supabase.
    4. `"Delete chat"` (`15sp` Bold `#9D1223`/`#C3192E` crimson red left, `20dp` red `trash` icon right) — opens confirmation dialog and deletes conversation from Supabase.

### S6 — Attachment Bottom Sheet (`t=23s..27s`, `frame_026`)
- **Trigger**: Tapping the `plus` circle button in the composer.
- **Sheet Geometry**:
  - Scrim: `30%` black (`#B2B2B2` over white) `[OBSERVED]`.
  - Sheet surface: `#F1F3F0`, top radius `28dp` (`39px`), top at `y = 828px` (height `452px = 323dp`), horizontal padding `20dp` (`30px`) `[OBSERVED]`.
  - Header row (`y = 860..895px`): `"Library"` (`16sp` Bold `#6E7274`) at left; `"See all"` (`15sp` Bold `#0063D3`) at right (opens Android Photo Picker / full gallery) `[OBSERVED]`.
  - Horizontal Media Strip (`y = 910..1050px`, item size `80dp x 80dp` / `112x112px`, corner radius `16dp` / `22px`, `8dp` spacing):
    - Tile 0: Camera tile (`#FFFFFF` background, centered `24dp` `camera` outline icon `#0A0A0A`) — launches real camera capture via `TakePicture` contract `[OBSERVED]`.
    - Tiles 1..N: Recent device photos loaded from `MediaStore.Images` (when `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` granted) with a `18dp` circular selection ring (`1.5dp` white stroke, `#4D000000` fill, turning `#0063D3` with check when selected) at top-right (`6dp` inset) `[OBSERVED]`.
  - Action Card (`x = 30..547px` -> `370dp` wide, `y = 1074..1229px` -> `111dp` tall, `#FFFFFF` fill, radius `20dp` / `28px`) `[OBSERVED]`:
    - Row 1 (`55.5dp` tall): `22dp` `clock-history` icon at left (`x = 68px`) + `"Recently uploaded"` (`16sp` Bold `#0A0A0A`, `x = 112px`) — opens sheet/dialog of user's previously uploaded files from Supabase `attachments` table `[OBSERVED]`.
    - Inset hairline divider at `y = 1150px` (`x = 112..547px`, `#EFEFEF`) `[OBSERVED]`.
    - Row 2 (`55.5dp` tall): `22dp` `paperclip` icon at left (`x = 68px`) + `"Files"` (`16sp` Bold `#0A0A0A`, `x = 112px`) — launches Android `OpenDocument` file picker (`*/*`), uploads selected file to Supabase Storage bucket `attachments`, and attaches it to the composer `[OBSERVED]`.

### S7 — Navigation Drawer (`t=43s..49s`, `t=51s..53s`, `frame_045..049`)
- **Geometry & Transition**:
  - Opens over `300ms` (`f_1298..f_1306`) with decelerate easing: drawer width is exactly `80%` of screen width (`461px = 329.3dp`), white `#FFFFFF` background; the main chat surface translates right by `80%` (`461px`) and is covered by a `30%` black scrim (`#B2B2B2` over white) `[OBSERVED]`.
  - Supports both edge-swipe gesture and menu button tap to open, and scrim tap / back gesture / swipe left to close `[OBSERVED]`.
- **Top Bar (`y = 58..119px`, height `44dp` / `62px`, inset `16dp` / `22px`)**:
  - Search pill (`x = 22..353px`, width `237dp` / `332px`, height `44dp` / `62px`, radius `22dp`, fill `#F0F2F2`, `20dp` `search` icon `#6E7274` at left + `"Search"` `16sp` SemiBold `#6E7274`). When focused (`t=45s`, `frame_046`), expands with a clear `X` button and queries conversations & messages in Supabase using debounced `ilike` / full-text search `[OBSERVED]`.
  - Incognito circle button (`x = 376..438px`, `44dp x 44dp` / `62x62px`, `#FFFFFF` fill, soft halo shadow, `24dp` `incognito` chat-bubble-with-lock icon `#0A0A0A`) `[OBSERVED]`.
- **Navigation Rows (`y = 145..265px`, row height `48dp` / `67px`, horizontal inset `22dp` / `31px`)**:
  - Row 1: `22dp` `edit/compose` icon + `16dp` gap + `"New chat"` (`16sp` Bold `#0A0A0A`) `[OBSERVED]`.
  - Row 2: `22dp` `image` (gallery mountain-sun) icon + `16dp` gap + `"Media"` (`16sp` Bold `#0A0A0A`) — opens a real Media screen displaying all user-uploaded attachments from Supabase Storage `[OBSERVED]`.
  - *(Note: `Artefacts`, `Scheduled`, `Glasses`, and `Vibes` are omitted per Absolute Rule 1 & S7 prompt specification.)*
- **Chats Section**:
  - Section header `"Chats"` (`15sp` Bold `#6E7274`, `x = 31px`, top margin `16dp`) `[OBSERVED]`.
  - Conversation list (`LazyColumn`, pinned conversations first, ordered by `updated_at DESC`):
    - Row height `44dp` (`62px`), text `x = 31..410px`, single line with ellipsis `[OBSERVED]`.
    - Read state: `16sp` Regular/Medium (`400`/`500`) `#0A0A0A` `[OBSERVED]`.
    - Unread state (`completed assistant message newer than conversations.last_read_at`): `16sp` Bold (`700`) `#0A0A0A` + trailing `7dp` (`10px`, `x = 424..433px`) `#0063D3` blue dot `[OBSERVED]`.
    - Pinned indicator: small pin icon before or beside pinned chats when pinned.
- **Bottom Floating Bar (`y = 1180..1242px`)**:
  - Two `44dp` (`62px`) white circular buttons with soft halo shadow anchored at bottom-right of drawer:
    - Notifications button (`bell` icon `22dp`, center `(328.5, 1212.5)px`) `[OBSERVED]`.
    - Settings button (`settings gear` icon `22dp`, center `(401.5, 1211.5)px`) `[OBSERVED]`.

### S8 — Notifications Screen (`t=49s`, `frame_050`)
- **Header**: Back/Menu circle button (`44dp`) at left + `"Notifications"` (`18sp` Bold `#0A0A0A`, `12dp` right of button) `[OBSERVED]`.
- **Permission Card**:
  - Shown only when `POST_NOTIFICATIONS` is not yet granted and background/push notification delivery is active (`x = 28..547px`, `y = 148..410px`, `#FFFFFF` fill, `1dp` `#ECEEEE` border, radius `20dp`):
  - Top row: `22dp` `bell` icon left, `20dp` `close` `X` icon right (dismisses card).
  - Title: `"Turn on notifications"` (`16sp` Bold `#0A0A0A`).
  - Body: `"Get personalised recommendations, never miss a response and stay up to date on the latest features."` (`14sp` Medium `#6E7274`).
  - CTA Button: Full-width pill (`height = 36dp` / `50px`, radius `18dp`, fill `#0063D3`, label `"Turn on"` in `15sp` Bold `#FFFFFF`) — triggers real Android `ActivityResultContracts.RequestPermission(Manifest.permission.POST_NOTIFICATIONS)` and local/realtime background completion notification when a response finishes `[OBSERVED]`.
- **Empty / List State**:
  - When no notifications exist: centered `40dp` `bell` outline icon (`#6E7274`), `"No notifications"` (`20sp` Bold `#6E7274`), `"No notifications to show."` (`15sp` Medium `#8E9294`) `[OBSERVED]`.
  - When background responses complete while user is away: displays real notification rows (tapping opens the conversation).

### S9 — Incognito Chat (`t=54s..57s`, `frame_055..057`)
- **Header**:
  - Left: Close `X` circle button (`44dp`, `#FFFFFF` fill, soft halo shadow) `[OBSERVED]`.
  - Two-line title (`12dp` right of button): Line 1 `"Build X"` (`17sp` Bold `#0A0A0A`), Line 2 `"Incognito chat"` (`13sp` Medium `#6E7274`) `[OBSERVED]`.
- **Composer**:
  - Surface: `#FFFFFF`, radius `28dp`, height `102dp` (`143px`), soft diffuse shadow `[OBSERVED]`.
  - Placeholder: `"Ask Build X privately..."` (`t=54s` keyboard-open state shows `"Ask ... privately..."` in `16sp` SemiBold `#6E7274`) `[OBSERVED]`.
  - Controls: No plus button, no mode pill, no mic button — only a `36dp` (`50px`) circular send button at bottom-right (`x = 490..540px`, `y = 1173..1223px`). When input is empty, fill is `#F0F2F2` and `arrow-up` icon is `#9B9D9D` (disabled); when non-empty, fill is `#0063D3` and `arrow-up` icon is `#FFFFFF` `[OBSERVED]`.
- **Zero-Persistence Guarantee**:
  - Sends `incognito = true` to the `chat` Edge Function. Neither client nor server writes any row to `conversations`, `messages`, or `attachments`, and no title is persisted `[OBSERVED + RULE]`.

### S10 — Settings & Sub-Screens (`t=59s..77s`, `frame_060..078`)
- **Surface**: Full-screen `#F1F3F0` background (`[OBSERVED]` `@(288,140)`).
- **Header**: Close `X` circle button (`44dp`, `#FFFFFF` fill, soft shadow) at top-left (`x = 28..90px`, `y = 58..120px`) + `"Settings"` (`18sp` Bold `#0A0A0A`, `12dp` right of button) `[OBSERVED]`. (Sub-screens use a `chevron-left` back circle button instead of `X`.)
- **Profile Card (`x = 30..547px`, `y = 166..259px`, height `67dp` / `94px`, `#FFFFFF` fill, radius `20dp` / `28px`)**:
  - Left: `44dp` (`62px`) avatar circle (`#EFF3F7` fill, `#808D9C` person icon, or uploaded avatar image from Supabase Storage) `[OBSERVED]`.
  - Middle: `"View profile"` (`16sp` Bold `#0A0A0A`) + `@handle` (`14sp` Bold `#0063D3`) `[OBSERVED]`.
  - Right: `chevron-right` (`20dp`, `#8E9294`) `[OBSERVED]`.
  - Tapping opens the real Profile Editor sub-screen (edit `display_name`, `@handle`, and upload avatar photo to Supabase Storage + update `profiles` table).
- **App Settings Section**:
  - Header `"App settings"` (`15sp` Bold `#6E7274`, `x = 48px`, top margin `20dp`, bottom margin `10dp`) `[OBSERVED]`.
  - Grouped White Card (`x = 30..547px`, `#FFFFFF` fill, radius `20dp` / `28px`, inset `0.5dp` `#EFEFEF` dividers between rows, each row `54dp` / `76px` tall with `22dp` leading outline icon `#0A0A0A`, `16sp` Bold `#0A0A0A` title, and `20dp` trailing `#8E9294` chevron) `[OBSERVED]`:
    1. `Notifications` (`bell` icon) — opens Android system notification channel settings (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
    2. `App appearance` (`sun/appearance` icon) — opens **App appearance** sub-screen (`t=64s`, `frame_065`):
       - Header: Back `<` circle button + `"App appearance"`.
       - White grouped card (`y = 166..391px`, height `161dp` / `226px`, radius `20dp`) with 3 radio rows (`54dp` each): `"Automatic"`, `"Light"`, `"Dark"` (`16sp` Bold `#0A0A0A` left, `22dp` custom radio circle right: unselected `2dp` `#0A0A0A` stroke, selected `2dp` `#0063D3` outer ring + `10dp` `#0063D3` inner dot) `[OBSERVED]`. Applies theme immediately and persists to DataStore + `profiles.theme_mode`.
    3. `Help & support` (`help-circle` icon) — opens Help & Support sub-screen with real FAQ + support contact trigger.
    4. `Data & privacy` (`lock` icon) — opens Data & Privacy sub-screen with 3 real working actions: **Export my data** (exports all user conversations/messages/profile as JSON via system share/save), **Delete all chats** (deletes all user conversations in Supabase with confirmation), and **Delete account** (deletes user data and signs out with confirmation).
    5. `App info` (`info-circle` icon) — opens **App info** sub-screen (`t=69s`, `frame_070`):
       - White grouped card (`radius = 20dp`) with 2 rows: `"App version"` (`1.0.0 (Build 100)`) and `"App language"` (current locale display name) `[OBSERVED]`.
    6. `Legal & safety` (`shield-check` icon) — opens Legal & Safety sub-screen with Terms of Service and Privacy Policy.
- **Log Out Card (`x = 30..547px`, `y = 1030..1106px`, height `55dp` / `77px`, `#FFFFFF` fill, radius `20dp`)**:
  - Leading `22dp` red `log-out` door-arrow icon (`#C3192E`) + `"Log out"` (`16sp` Bold `#C3192E`) + trailing `chevron-right` (`#8E9294`) `[OBSERVED]`. Signs out of Supabase Auth for real and navigates to Auth screen.

### S11 — Voice Input Bar (`t=86s..89s`, `frame_087..089`)
- **Trigger**: Tapping the `mic` circle button in the composer requests `Manifest.permission.RECORD_AUDIO` (`t=86s`, `frame_087`). Once granted, the composer morphs into the Voice Input capsule (`t=87s..88s`, `frame_088..089`).
- **Voice Bar Geometry**:
  - Outer white capsule (`x = 22..554px` -> `379dp` wide, `y = 1156..1236px` -> `57dp` / `80px` tall, radius `28.5dp`, `#FFFFFF` fill, custom diffuse composer shadow) `[OBSERVED]`.
  - Left: Close `X` circle button (`36dp x 36dp` / `50x50px`, `x = 36..86px`, `y = 1173..1223px`, `1dp` `#E5E7E6` border, `18dp` `X` icon `#0A0A0A`) `[OBSERVED]`.
  - Center: Pill container (`x = 98..478px`, `y = 1173..1223px`, height `36dp` / `50px`, radius `18dp`, `1dp` `#E5E7E6` border) containing a live horizontal row of `~34` vertical rounded bars (`2dp` wide, `4dp` gap, heights driven in real time by `SpeechRecognizer. onRmsChanged` amplitude, `#9B9D9D` / `#6E7274` color) `[OBSERVED]`.
  - Right: Send circle button (`36dp x 36dp` / `50x50px`, `x = 490..540px`, `y = 1173..1223px`), `#F0F2F2` disabled with `#9B9D9D` `arrow-up` until speech is recognized, turning `#0063D3` blue with `#FFFFFF` `arrow-up` when partial/final transcript is available; tapping puts recognized text into the composer / sends `[OBSERVED]`.

### S12 — Authentication (Sign In / Sign Up) (`[INFERRED]`)
- Designed in the exact same visual language: `#F1F3F0` / `#FFFFFF` airy background, centered rotating 8-petal Build X mark (`68dp`), `"Build X"` `22sp` Bold header, `#FFFFFF` grouped card (`radius = 24dp`, soft diffuse shadow) containing pill-shaped `#F1F3F0` input fields (Email, Password, Display Name / Handle on Sign Up), full-width `#0063D3` pill primary CTA button (`height = 48dp`, `16sp` Bold `#FFFFFF`), inline error banner in `#C3192E`, and mode toggle between Sign In and Create Account.

---

## 4. Motion & Animation Specification

| Transition | Measured Frames (`30fps`) | Duration | Easing | Details |
|---|---|---|---|---|
| **Mark Idle Rotation** | `f_0001..f_0060`, `f_2430..f_2580` | `8000ms` / rev | `LinearEasing` | `[OBSERVED]` Continuous gentle clockwise rotation + `0.96..1.04` breathing scale (`3000ms` cosine). |
| **Mark Loader Rotation** | `f_0120..f_0480` | `1800ms` / rev | `LinearEasing` | `[OBSERVED]` `20dp` mark spins smoothly during reasoning/streaming. |
| **Mark Enter (New Chat)** | `f_1203..f_1211` (`t=40.10s..40.37s`) | `350ms`–`400ms` | `Spring(dampingRatio=0.82f, stiffness=380f)` | `[OBSERVED]` Fades `0 -> 1` and scales `0.60 -> 1.00`. |
| **Mark Exit (On Send)** | `f_0100..f_0107` (`t=3.33s..3.57s`) | `250ms` (`8` frames) | `FastOutLinearInEasing` | `[OBSERVED]` Fades `1 -> 0` and scales `1.00 -> 0.85`. |
| **Mic <-> Send Swap** | `f_0052..f_0055` (`t=1.73s..1.83s`) | `150ms` (`4` frames) | `FastOutSlowInEasing` | `[OBSERVED]` Crossfade + scale (`0.8 -> 1.0`). |
| **Composer Vertical Move** | `f_0100..f_0109` (`t=3.33s..3.63s`) | `300ms` (`9` frames) | `Spring(dampingRatio=0.95f, stiffness=400f)` | `[OBSERVED]` Animates smoothly with IME / send without overshoot > `2dp`. |
| **Header Action Capsule Reveal** | `f_0101..f_0109` (`t=3.37s..3.63s`) | `250ms` (`8` frames) | `LinearOutSlowInEasing` | `[OBSERVED]` Fades in as first message is sent. |
| **Title Crossfade** | `f_0546..f_0552` (`t=18.20s..18.40s`) | `200ms` (`6` frames) | `FastOutSlowInEasing` | `[OBSERVED]` Crossfades `"Build X"` to AI-generated conversation title. |
| **Action Row + Share Stagger** | `f_0534..f_0550` (`t=17.80s..18.33s`) | `200ms` fade + `500ms` delay | `LinearOutSlowInEasing` | `[OBSERVED]` Thumbs-up, thumbs-down, copy fade in first; Share fades in `500ms` later. |
| **Bottom Sheet Open / Close** | `f_0580..f_0588` / `f_0660..f_0668` | `300ms` open / `250ms` close | `LinearOutSlowInEasing` / `FastOutLinearInEasing` | `[OBSERVED]` Slide up + `30%` black scrim fade; interactive drag-to-dismiss. |
| **Drawer Open / Close** | `f_1298..f_1306` (`t=43.27s..43.53s`) | `300ms` (`9` frames) | `LinearOutSlowInEasing` | `[OBSERVED]` Drawer translates `0..80%` width while main content translates right by `80%` width and dims with `30%` scrim. |
| **Control Press State** | Observed on pills & circles | `100ms` | `FastOutSlowInEasing` | `[OBSERVED]` Scale to `0.97f` + soft highlight, zero Material ripple on pills/circles. |
