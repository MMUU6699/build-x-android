# Phase A — Discovery Report (`docs/profile/DISCOVERY.md`)

Generated: `2026-11-10`  
Supabase Project ID: `owgpuolvfqfjhezuohyf` (`https://owgpuolvfqfjhezuohyf.supabase.co`)  
Android Application ID / Namespace: `app.buildx.chat` (`v1.2.0`, `versionCode = 102`)

---

## 1. Codebase Architecture & Existing Files Inventory

### 1.1 Entry Point, Navigation & Deep Links
- **Manifest**: [`app/src/main/AndroidManifest.xml`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/AndroidManifest.xml)
  - Declares `.BuildXApp`, `.MainActivity` (`exported="true"`, `windowSoftInputMode="adjustResize"`), and `.BuildXFileProvider` (`${applicationId}.fileprovider`).
  - Currently only registers `MAIN` / `LAUNCHER` intent-filter on `MainActivity`. No deep-link `<intent-filter>` (`buildx://u/{username}`) is registered yet.
  - No Firebase / FCM service or `google-services.json` is present.
- **Navigation Graph**: [`app/src/main/java/app/buildx/chat/MainActivity.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/MainActivity.kt)
  - Uses Jetpack Navigation Compose (`androidx.navigation.compose.NavHost`) with horizontal slide transitions (`slideIntoContainer` / `slideOutOfContainer`, `250ms FastOutSlowInEasing`) over an opaque `Box(Modifier.fillMaxSize().background(colors.pageBackground))`.
  - Existing routes:
    - `"chat"` -> `ChatAndDrawerScreen` (hosts `MainSurfacePane.CHAT`, `MainSurfacePane.NOTIFICATIONS`, `MainSurfacePane.MEDIA` + slide-under Drawer)
    - `"notifications"` -> `NotificationsScreen` (when opened from `Settings -> Notifications`)
    - `"media"` -> `MediaScreen`
    - `"settings"` -> `SettingsScreen`
    - `"appearance"` -> `AppAppearanceScreen`
    - `"language"` -> `AppLanguageScreen`
    - `"app_info"` -> `AppInfoScreen`
    - `"profile"` -> `ProfileEditScreen` (**Conflict**: currently Settings `"View profile"` navigates directly to `ProfileEditScreen` instead of `P1 Own Profile`).
    - `"data_privacy"` -> `DataPrivacyScreen`
    - `"help_support"` -> `HelpSupportScreen`
    - `"legal_safety"` -> `LegalSafetyScreen`

### 1.2 Auth Flow & Sign-Up
- **UI**: [`app/src/main/java/app/buildx/chat/ui/screens/AuthScreen.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/AuthScreen.kt)
  - Sign-in collects `email` + `password`.
  - Sign-up collects `displayName`, `handle`, `email`, `password`.
- **Repository**: [`app/src/main/java/app/buildx/chat/data/repository/BuildXRepository.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/data/repository/BuildXRepository.kt) (`lines 110–179`)
  - `signUpWithEmail` strips leading `@`, lowercases `handle`, passes `display_name` and `handle` in `raw_user_meta_data`, and calls `ensureUserProfile()`.
  - Database trigger `on_auth_user_created` on `auth.users` calls `public.handle_new_user()` which inserts `(id, handle, display_name)` into `public.profiles`.

### 1.3 Existing Data Models & Repository
- **Models**: [`app/src/main/java/app/buildx/chat/data/model/Models.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/data/model/Models.kt)
  - `UserProfile(id, handle, displayName, avatarUrl, appearance, createdAt)`
  - `Conversation`, `ChatMessage`, `AttachmentRow`, `StagedAttachment`, `ChatMode`, `AppAppearance`, `AppLanguage`, `MainSurfacePane`, `AppNotificationItem`.
- **ViewModel**: [`app/src/main/java/app/buildx/chat/ui/viewmodel/BuildXViewModel.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/viewmodel/BuildXViewModel.kt)
  - Single `AndroidViewModel` holding `BuildXUiState` via `StateFlow` (no Hilt/Koin; manual construction via `BuildXRepository(application.applicationContext)`).
  - **Conflict in `deleteAccount()` (`lines 853–863`)**: Currently `deleteAccount()` only calls `repository.deleteAllConversations()` and `repository.signOut()`. It does not delete the `auth.users` / `public.profiles` record or storage objects.
  - **Conflict in `uploadAvatar()` (`BuildXRepository.kt lines 450–468`)**: Currently uploads raw bytes to the private `attachments` bucket (`$uid/avatar_${timestamp}.jpg`) without square cropping/resizing to `<= 512px`, and because `attachments` is `public = false`, `bucket.publicUrl(...)` is not publicly readable.

### 1.4 Drawer, Notifications & Settings
- **Drawer**: [`app/src/main/java/app/buildx/chat/ui/screens/ChatAndDrawerScreen.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/ChatAndDrawerScreen.kt) (`lines 812–1044`)
  - Top row: Search pill (`#F1F3F0`, 44dp height, radius 22dp) + 44dp white circular Incognito button.
  - **Conflict**: The Search pill currently contains an inline `BasicTextField` that filters `conversations` inside the drawer. Per P8, tapping the Search pill must expand it via container-transform into the Full-Screen Search screen (`Chats` | `People` chips) while the drawer closes.
  - Navigation rows: `DrawerNavRow` for `New chat` (`R.drawable.ic_edit_compose`) and `Media` (`R.drawable.ic_image`). We will add `Profile` (`R.drawable.ic_user`) right below `Media` per P7.
  - Bottom bar: floating white 44dp circular buttons for `Notifications` (`R.drawable.ic_bell`) and `Settings` (`R.drawable.ic_settings`).
- **Notifications**: [`app/src/main/java/app/buildx/chat/ui/screens/NotificationsAndMediaScreens.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/NotificationsAndMediaScreens.kt) (`lines 55–197`)
  - Currently displays `NotificationPermissionCard` at the top and a list of in-memory `AppNotificationItem`s (or the bell empty state).
  - No `public.notifications` table exists in Supabase yet.
- **Settings**: [`app/src/main/java/app/buildx/chat/ui/screens/SettingsScreens.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/screens/SettingsScreens.kt)
  - Top `GroupedCard` ("View profile") shows 44dp avatar, `"View profile"`, and `@handle` in `#0063D3`. Will be extended to display the verified seal (`P6`) and open `P1 Own Profile` (`P7`).

### 1.5 Design Tokens, Typography, Icons & Shared Components
- **Theme & Tokens**: [`app/src/main/java/app/buildx/chat/ui/theme/BuildXTheme.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/theme/BuildXTheme.kt) & [`docs/design/tokens.json`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/docs/design/tokens.json)
  - Light colors: `pageBackground = #FFFFFF`, `surfaceMuted = #F1F3F0`, `cardBackground = #FFFFFF`, `primaryText = #0A0A0A`, `secondaryText = #6E7274`, `primaryBlue = #0063D3`, `onPrimaryBlue = #FFFFFF`, `destructiveRed = #C3192E`, `hairlineBorder = #E1E3E1`, `subtleSurfaceBorder = #ECEEEC`, `scrimColor = #4D000000`.
  - Dark colors (`DarkBuildXColors`): `pageBackground = #0E1012`, `surfaceMuted = #1A1D20`, `cardBackground = #171A1E`, `primaryText = #F3F5F6`, `secondaryText = #9BA0A5`, `primaryBlue = #3B8BF7`, `destructiveRed = #FF5A6E`, `hairlineBorder = #2E3338`, `subtleSurfaceBorder = #25292E`.
  - Fonts: bundled `Plus Jakarta Sans` (Regular, Medium, SemiBold, Bold) + `Noto Sans Arabic`.
  - Shadows: `Modifier.softShadow(color, cornerRadius, blurRadius, offsetY, offsetX)` (10-layer Gaussian-like hardware-independent shadow).
- **Reusable Components**: [`app/src/main/java/app/buildx/chat/ui/components/BuildXComponents.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/components/BuildXComponents.kt) & [`BuildXSheets.kt`](file:///C:/Users/musta/.gemini/antigravity/scratch/build-x/app/src/main/java/app/buildx/chat/ui/components/BuildXSheets.kt)
  - `HeaderCircleButton` (44dp circle, soft shadow, 0.8dp subtle border, press-scale animation, RTL auto-mirroring for `ic_back` / `ic_chevron_right`).
  - `ActionCapsule` (92dp x 44dp pill with two 22dp icon buttons — will be generalized or reused for Bell + Share on `P2 Other Profile`).
  - `GroupedCard` (20dp radius white card on `#F1F3F0` surface).
  - `DrawerNavRow`, `SettingsRow`, `AppearanceRadioRow`, `pressScaleClickable`.
- **Image Loading**: `io.coil-kt:coil-compose:2.7.0` (`AsyncImage`).
- **Localization**: Currently uses `BuildXStrings` (`EnglishBuildXStrings` / `ArabicBuildXStrings`) in `BuildXTheme.kt` plus `app/src/main/res/values/strings.xml`. To strictly satisfy Rule 8 ("use string resources for every user-visible string, so layouts mirror correctly in RTL languages"), we will also provide complete `res/values/strings.xml` and `res/values-ar/strings.xml` string resources synced with `BuildXTheme.strings` and locale configuration.

---

## 2. Live Supabase Project Inspection (`owgpuolvfqfjhezuohyf`)

### 2.1 Installed Postgres Extensions
| Extension | Version | Notes |
|---|---|---|
| `plpgsql` | `1.0` | Installed |
| `pgcrypto` | `1.3` | Installed |
| `uuid-ossp` | `1.1` | Installed |
| `pg_stat_statements` | `1.11` | Installed |
| `supabase_vault` | `0.3.1` | Installed |
| `citext` | *Not installed yet* | Will be enabled in migration (`CREATE EXTENSION IF NOT EXISTS citext`) |
| `pg_trgm` | *Not installed yet* | Will be enabled in migration (`CREATE EXTENSION IF NOT EXISTS pg_trgm`) |

### 2.2 Current `public.profiles` Schema Dump
```sql
CREATE TABLE public.profiles (
    id uuid NOT NULL PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name text NOT NULL DEFAULT 'User'::text,
    avatar_url text NULL,
    theme_mode text NOT NULL DEFAULT 'system'::text,
    theme_palette text NOT NULL DEFAULT 'default'::text,
    locale text NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    handle text NULL
);
```
- **Existing Rows in `public.profiles` (27 rows)**:
  - Several test accounts created during v1 testing have duplicate `handle` values (`usera` appears 6 times, `userb` appears 6 times), plus `user_0809b21d`, `mustafa_1794249485`, `sara_1794249485`, `mustafa_1794249559`, `sara_1794249559`, `mustafa_1794249653`, `sara_1794249653`, `mustafa_1794249702`, `sara_1794249702`, `incogtest`, `jddodd`, `msodj`, `kdodo`, `hgygu`, `kskos`.
  - When migrating `handle` -> `username` (`citext UNIQUE`), our migration will back up `public.profiles` to a backup table (`public.profiles_backup_pre_username`) first, normalize each `handle` (`lowercase`, strip invalid chars, enforce `3..24` chars), resolve duplicates deterministically by appending a numeric suffix (`usera`, `usera_2`, `usera_3`, ...), and record every change in a migration log table (`public.username_migration_log`) so we can report the exact before/after table.

### 2.3 Current RLS Policies Dump (`pg_policies`)
#### `public.profiles`
- `"Users manage own profile"`: `PERMISSIVE`, `ALL`, roles `{public}`, `USING (auth.uid() = id)`
- `"profiles_select_own"`: `PERMISSIVE`, `SELECT`, roles `{public}`, `USING (auth.uid() = id)`
- `"profiles_insert_own"`: `PERMISSIVE`, `INSERT`, roles `{public}`, `WITH CHECK (auth.uid() = id)`
- `"profiles_update_own"`: `PERMISSIVE`, `UPDATE`, roles `{public}`, `USING (auth.uid() = id)`, `WITH CHECK (auth.uid() = id)`
- `"profiles_delete_own"`: `PERMISSIVE`, `DELETE`, roles `{public}`, `USING (auth.uid() = id)`
  - *Note*: The broad `"Users manage own profile"` (`FOR ALL`) and unrestricted `"profiles_update_own"` currently allow a client to `UPDATE` any column on their own row. In Phase D, we will drop `"Users manage own profile"`, restrict client `UPDATE` via column-level privileges (`REVOKE UPDATE ON public.profiles FROM authenticated, anon; GRANT UPDATE (display_name, bio, avatar_url, theme_mode, theme_palette, locale, username_prompt_seen) ON public.profiles TO authenticated;`) AND a `BEFORE UPDATE` trigger guarding `is_verified`, `verified_at`, `followers_count`, `following_count`, `username`, and `username_changed_at`.

#### `public.conversations`, `public.messages`, `public.attachments`
- `conversations`: `"Users manage own conversations"`, `"conversations_select_own"`, `"conversations_insert_own"`, `"conversations_update_own"`, `"conversations_delete_own"` (`auth.uid() = user_id`).
- `messages`: `"Users manage own messages"`, `"messages_select_own"`, `"messages_insert_own"`, `"messages_update_own"`, `"messages_delete_own"`.
- `attachments`: `"attachments_select_own"`, `"attachments_insert_own"`, `"attachments_update_own"`, `"attachments_delete_own"`.

#### `storage.buckets` & `storage.objects`
- Buckets:
  - `attachments` (`public = false`, `file_size_limit = 20971520`)
  - `workspace_screenshots` (`public = false`)
- Policies on `storage.objects`:
  - `storage_attachments_select_own`, `storage_attachments_insert_own`, `storage_attachments_update_own`, `storage_attachments_delete_own` (`bucket_id = 'attachments' AND (storage.foldername(name))[1] = auth.uid()::text`).
  - `Users read/write/update/delete own screenshots` (`bucket_id = 'workspace_screenshots'`).

### 2.4 Existing Triggers & Functions (`public`)
- `public.auto_confirm_email()` -> `BEFORE INSERT ON auth.users` (`on_auth_user_auto_confirm`)
- `public.handle_new_user()` -> `AFTER INSERT ON auth.users` (`on_auth_user_created`)
- `public.consume_work_usage(...)` (unrelated legacy table function)

### 2.5 Existing Edge Functions
- `chat` (`supabase/functions/chat/index.ts`, active `v15`): handles SSE streaming chat completions and `GET ?share_token=...` shared conversation rendering.

---

## 3. Summary of What Will Be Reused vs Extended

| Area | Reuse | Extend / Build New |
|---|---|---|
| **Design System** | `BuildXTheme`, `BuildXColors` (Light & Dark), `Plus Jakarta Sans` + `Noto Sans Arabic`, `softShadow`, `HeaderCircleButton`, `ActionCapsule`, `GroupedCard`, `DrawerNavRow` | Add `ic_verified_badge.xml`, `ic_pencil_edit.xml`, `ic_bell_filled.xml`, `ic_caret_down.xml`; add `ProfileActionCapsule`, `NoteBubble`, `ProfileHeaderSection`, `UserListRow`, `SkeletonBox` |
| **Database (`public.profiles`)** | Existing `id`, `display_name`, `avatar_url`, `theme_mode`, `created_at` | Migrate `handle` -> `username citext UNIQUE NOT NULL`, add `bio`, `is_verified`, `verified_at`, `username_changed_at`, `followers_count`, `following_count`, `username_prompt_seen` |
| **New Tables** | — | `reserved_usernames`, `follows`, `follow_rate_limits`, `notes`, `note_subscriptions`, `notifications`, `verification_events`, `username_migration_log` |
| **RPCs & Views** | — | `public_profiles` view, `is_username_available`, `change_username`, `get_profile`, `get_follow_list`, `search_people`, `toggle_follow`, `toggle_note_subscription`, `upsert_note`, `delete_note`, `delete_my_account`, `set_verified` (`service_role` only) |
| **Storage** | Supabase Storage client | Create `avatars` bucket (`public = true`), RLS policies restricting `INSERT/UPDATE/DELETE` to `(storage.foldername(name))[1] = auth.uid()::text`, client-side square crop + `<= 512px` resize + old avatar deletion + `?v=` cache-busting |
| **Navigation & Screens** | `MainActivity` `NavHost`, `ChatAndDrawerScreen`, `NotificationsScreen`, `SettingsScreen` | Add `P1 Own Profile`, `P2 Other Profile`, `P3 Edit Profile`, `P4 Followers/Following List`, `P5 Note Sheet`, `P7 Drawer Profile Row`, `P8 Full-Screen Search (Chats \| People)`, `P9 Follow/Note Notifications`, `buildx://u/{username}` deep link handling |
