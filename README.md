# Build X Android Client

<div align="center">

**Native AI Chatbot & Conversational Social Platform**  
*Built with Kotlin, Jetpack Compose, and Supabase*

[English](#-english) • [العربية](#-العربية)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android-SDK%2035%20(min%2026)-green?style=flat-square&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Supabase](https://img.shields.io/badge/Supabase-Backend%203.0.2-3ECF8E?style=flat-square&logo=supabase)](https://supabase.com)
[![Version](https://img.shields.io/badge/Version-v1.4.0%20(104)-blue?style=flat-square)](#)

</div>

---

## 🇬🇧 English

### Overview

**Build X** is a production-grade, native Android application offering a dual-mode generative AI chatbot combined with an integrated conversational social platform. Built entirely from scratch using Kotlin and Jetpack Compose, Build X connects to a live Supabase backend (PostgreSQL 17, Row Level Security, Edge Functions with Server-Sent Events, Realtime subscriptions, and Storage).

Every component in Build X is real and functional: there are zero mock screens, zero seeded placeholders, zero emojis in UI or assets, and zero compromise on pixel fidelity.

---

### Key Capabilities

#### 1. Dual AI Engine: Instant & Thinking
- **Instant Mode**: Fast, concise responses for standard conversational queries.
- **Thinking Mode**: Deep reasoning engine that renders live, multi-step thought processes (`ThinkingRow`), timing metrics, and expandable bottom sheets (`StepsSheetContent`) detailing each reasoning stage before displaying the final response.
- **Mode Selector Banner**: Floating pill switcher anchored above the composer allowing one-tap switching between modes.

#### 2. Advanced In-Place Message Actions
- **User Message Context Popup**: Tap on any user bubble to trigger an anchored card popup with:
  - **Edit**: Converts the bubble into an inline multiline editor (`#F1F3F0`, 20dp corner radius) with auto-focus, replacement warning, and Cancel/Send pills. Saves changes via an atomic backend RPC (`edit_user_message`), cascades deletion of subsequent messages, triggers a fresh assistant reply, and adds an "Edited" badge.
  - **Resend**: Retries failed turns in place or appends the query to the end as a new exchange.
  - **Copy**: Copies message text to the system clipboard with instant toast/snackbar feedback.
  - **Select Text**: Opens a dedicated modal bottom sheet wrapping full content in an interactive `SelectionContainer` with draggable handles.
- **Assistant Action Row**: Thumbs-up / thumbs-down ratings, copy, and a 3-dots popup offering **Regenerate** (last assistant turn only, retaining original answer until streaming begins), **Share** (system share sheet), and **Select Text**.
- **Incognito Chat**: Fully private sessions executed 100% in-memory with zero cloud database persistence.

#### 3. Complete Social Profile System
- **Unique Handles**: Case-insensitive `@username` system enforced by PostgreSQL triggers and constraints with a 14-day modification cooldown.
- **Profile Customization**: Display name, 160-character bio with line-break limits, and avatar management.
- **Follow Graph**: Follow, Follow back, and Following states with live follower/following counters and interactive lists with confirmation dialogs.
- **Verified Badges**: Administrative blue checkmarks granted via secure audit-logged database procedures.
- **Universal Search**: Full-screen dual-tab search across both Conversation history and People with recent search history caching.

#### 4. Ephemeral Public Notes
- **Status Bubbles**: Lightweight 60-character ephemeral notes shown floating directly above user avatars in profiles.
- **24-Hour Expiration**: Automatic database-backed expiration window, with instant create, replace, and delete controls.

#### 5. Realtime In-App Notifications
- Dedicated notifications screen listening to PostgreSQL changes via Supabase Realtime channels.
- Real-time event notifications for new followers and posted notes with unread count badges and mark-all-read capabilities.

#### 6. Multimedia & Audio Dictation
- **Attachments Sheet**: Modal drawer supporting device photo gallery selection, live camera capture, and file document browsing.
- **Voice Dictation**: Audio recording with a real-time 28-bar amplitude waveform visualization (`VoiceDictationBar`) and live transcript preview.

#### 7. Original Design System & Arabic RTL
- **Brand Identity**: Original 8-petal rotating gradient mark (purple-to-blue vector) with zero dependency on third-party trademarks.
- **Zero Emoji**: 100% vector drawables (`VectorDrawable`) designed with clean, thin outlines.
- **Bilingual & Native RTL**: Full Arabic (RTL) and English (LTR) string localization. Message text uses dynamic bidirectional detection (`TextDirection.Content`) to automatically align Arabic text to the right and Latin text to the left.
- **Theming**: Coherent Light and Dark color systems with soft diffuse drop shadows and zero-flicker transitions.

---

### Architecture & Tech Stack

```
Build-X Android
 ├── app/src/main/java/app/buildx/chat/
 │    ├── data/
 │    │    ├── model/          # Serializable data models (ChatMessage, PublicProfile, etc.)
 │    │    └── repository/     # Supabase Postgrest, Auth, Storage, Realtime client
 │    ├── ui/
 │    │    ├── components/     # Reusable Compose widgets (UserBubble, ActionRow, Composer, etc.)
 │    │    ├── screens/        # Main chat, Profile, Edit Profile, Follow Lists, Search, Settings
 │    │    ├── theme/          # Typography (Plus Jakarta Sans, Noto Sans Arabic), Colors, Strings
 │    │    └── viewmodel/      # BuildXViewModel (StateFlow, UI state management)
 │    └── MainActivity.kt      # Single activity hosting navigation and permission flows
 ├── supabase/
 │    ├── functions/chat/      # Deno / TypeScript Edge Function for SSE streaming
 │    └── migrations/          # Version-controlled SQL migrations with RLS policies
 └── docs/design/screenshots/  # Visual regression and UI fidelity test artifacts
```

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI Toolkit** | Jetpack Compose (Compose BOM `2024.10.01`, Material 3) |
| **Architecture** | Unidirectional Data Flow (UDF) + MVVM / Clean Architecture |
| **Networking & DB** | Supabase Kotlin SDK 3.0.2 (Postgrest, Auth, Storage, Realtime) |
| **Edge Compute** | Supabase Edge Functions (Deno / TypeScript, SSE streaming) |
| **Typography** | Plus Jakarta Sans (Latin) + Noto Sans Arabic |
| **UI Testing** | Robolectric 4.13 + Roborazzi 1.34.0 (Native graphics verification) |

---

### Getting Started

#### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: Java 17
- **Android SDK**: Compile SDK `35`, Minimum SDK `26`

#### Clone & Open
```bash
git clone https://github.com/MMUU6699/build-x-android.git
cd build-x-android
```

#### Build & Run
To compile and assemble the debug or release APKs:
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (Signed with debug keys for direct installation)
./gradlew assembleRelease
```
The output APKs are generated in `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/apk/debug/app-debug.apk`.

#### Run Tests
```bash
# Execute unit test suite and screen fidelity verification
./gradlew testDebugUnitTest
```

---

<br/>

## 🇸🇦 العربية

### نظرة عامة

**Build X** هو تطبيق أندرويد أصيل (Native) فائق الجودة، يجمع بين محادثات الذكاء الاصطناعي التوليدي التفاعلية ومنصة تواصل اجتماعي محادثاتية مدمجة. تم بناء التطبيق بالكامل من الصفر باستخدام **Kotlin** و **Jetpack Compose**، مع ربطه بقاعدة بيانات سحابية متكاملة عبر **Supabase** (تشمل PostgreSQL 17، وحماية الوصول على مستوى الصفوف RLS، و Edge Functions للبث اللحظي SSE، وقنوات Realtime، ونظام التخزين Storage).

جميع وظائف التطبيق حقيقية وقابلة للاستخدام بالكامل: لا توجد شاشات وهمية، ولا بيانات مزيفة (No Mock Data)، وخالٍ تماماً من استخدام الإيموجي في الواجهات مع الالتزام التام بأعلى معايير الدقة البصرية وتطابق الواجهات.

---

### أهم المزايا والخصائص

#### 1. محرك الذكاء الاصطناعي المزدوج: Instant و Thinking
- **نمط الرد اللحظي (Instant)**: يقدم إجابات سريعة وموجزة ومباشرة للاستفسارات اليومية.
- **نمط التفكير العميق (Thinking)**: محرك استدلال يعرض خطوات تفكير النموذج خطوة بخطوة مع مؤقت زمني وشريط تقدم بنفسجي، مع إمكانية فتح نافذة تفصيلية سفلية (`StepsSheetContent`) لمراجعة الخطوات كاملة قبل عرض الإجابة.
- **لافتة تبديل الأنماط**: كبسولة عائمة فوق صندوق الكتابة تسمح بالتبديل الفوري بين النمطين بلمسة واحدة.

#### 2. إجراءات الرسائل المتقدمة في مكانها
- **قائمة رسائل المستخدم**: عند الضغط على فقاعة المستخدم، تظهر قائمة خيارات عائمة مرتبطة بمكان الرسالة:
  - **تعديل (Edit)**: تحويل الفقاعة إلى محرر نصي مضمن في مكانها مع تركيز تلقائي، وتحذير استبدال الردود، وزري "إلغاء" و "إرسال". يؤدي التعديل إلى استدعاء دالة ذرية (`edit_user_message`) في قاعدة البيانات لحذف الردود اللاحقة تلقائياً وتوليد إجابة جديدة مع إظهار شارة "معدلة".
  - **إعادة الإرسال (Resend)**: إعادة المحاولة في نفس المكان عند فشل الإرسال، أو تكرار الاستفسار كرسالة جديدة.
  - **نسخ (Copy)**: نسخ نص الرسالة فورياً إلى الحافظة مع ظهور إشعار تأكيد.
  - **تحديد النص (Select text)**: فتح ورقة سفلية مخصصة تتيح تحديد ونسخ أي جزء من النص بحرية عبر مقابض السحب.
- **شريط إجراءات المساعد**: تقييم بالإعجاب / عدم الإعجاب، نسخ النص، وزر الخيارات الإضافية (إعادة التوليد Regenerate، والمشاركة Share، وتحديد النص).
- **المحادثة السرية (Incognito)**: جلسات خاصة تعمل بنسبة 100% داخل الذاكرة المحلية دون حفظ أي سجل أو رسالة على الخوادم.

#### 3. نظام الملفات الشخصية والتواصل الاجتماعي
- **معرّفات فريدة (@username)**: نظام أسماء مستخدمين مدعوم بمحددات وقواعد تحقق مشددة في PostgreSQL مع فترة تبريد (Cooldown) مدتها 14 يوماً بين التعديلات لمنع انتحال الهوية.
- **تخصيص الملف الشخصي**: الاسم الظاهر، ونبذة شخصية (Bio) بحد أقصى 160 حرفاً، وتحديث الصورة الرمزية.
- **شبكة المتابعة**: دعم كامل لحالات (متابعة / رد المتابعة / متابَع)، مع عدادات فورية وقوائم تفاعلية للمتابعين ومن يتابعهم مع نوافذ تأكيد إلغاء المتابعة.
- **شارة التوثيق الزرقاء**: شارات موثقة تمنح إدارياً عبر سجل تدقيق مؤمن في قاعدة البيانات.
- **البحث الشامل**: نافذة بحث بشاشة كاملة تدعم التبويب بين "المحادثات" و "الأشخاص" مع حفظ عمليات البحث الأخيرة.

#### 4. الملاحظات العامة المؤقتة (Public Notes)
- **فقاعة الحالة المؤقتة**: ميزة اجتماعية تتيح للمستخدم نشر ملاحظة نصية قصيرة (حتى 60 حرفاً) تظهر عائمة مباشرة فوق صورته الشخصية.
- **انتهاء صلاحية خلال 24 ساعة**: نظام إخفاء تلقائي مؤتمت بعد 24 ساعة، مع إمكانية التحديث أو الحذف في أي وقت.

#### 5. إشعارات داخلية لحظية (In-App Realtime Notifications)
- شاشة إشعارات تستمع مباشرة للتغييرات في قاعدة البيانات عبر قنوات Supabase Realtime لتنبيه المستخدم فورياً عند قيام شخص بمتابعته أو نشر ملاحظة جديدة.

#### 6. الوسائط والإملاء الصوتي
- **ورقة إرفاق الوسائط**: نافذة سفلية سلسة تدعم التقاط الصور بالكاميرا، واختيار الصور المتعددة من المعرض، وتصفح المستندات والملفات.
- **إملاء صوتي مباشر**: تسجيل صوتي مع رسم بياني تفاعلي لترددات الصوت (Waveform عبر 28 عموداً متغيراً) مع تفريغ نصي فوري.

#### 7. نظام تصميم أصلي ودعم كامل للغة العربية
- **الهوية البصرية**: شعار أصلي مكون من 8 بتلات متدرجة (من البنفسجي إلى الأزرق) مع حركة دوران ديناميكية أثناء التحميل.
- **خالٍ تماماً من الإيموجي**: تم استبدال جميع الإيموجي بأيقونات متجهات نقية (`VectorDrawable`) بخطوط رفيعة وأنيقة.
- **دعم أصيل للـ RTL**: تعريب كامل لجميع النصوص والنوافذ مع تحديد اتجاه النص (`TextDirection.Content`) تلقائياً بحسب لغة كل رسالة (العربية إلى اليمين والإنجليزية إلى اليسار).
- **المظهر الداكن والفاتح**: تناسق كامل في توزيع الظلال وتدرجات الألوان للشاشتين الفاتحة والداكنة دون أي وميض أثناء التنقل.

---

### البنية البرمجية والتقنيات المستخدمة

| الطبقة | التقنية المستخدمة |
|---|---|
| **لغة البرمجة** | Kotlin 2.0.21 |
| **واجهة المستخدم** | Jetpack Compose (BOM `2024.10.01`, Material 3) |
| **النمط المعماري** | تدفق البيانات أحادي الاتجاه (UDF) + MVVM / Clean Architecture |
| **الخلفية وقاعدة البيانات** | مكتبة Supabase الرسمية 3.0.2 (Postgrest, Auth, Storage, Realtime) |
| **المعالجة السحابية** | دوال Supabase Edge Functions بتقنية SSE لبث الإجابات نصاً بنص |
| **الخطوط** | Plus Jakarta Sans (للإنجليزية) + Noto Sans Arabic (للعربية) |
| **فحص الواجهات** | اختبارات Robolectric 4.13 و Roborazzi لتوثيق ومطابقة الواجهات بالبكسل |

---

### طريقة التشغيل والبناء (Build Instructions)

1. **المتطلبات الأساسية:**
   - بيئة Android Studio الإصدار Ladybug (2024.2.1) أو أحدث.
   - حزمة تطوير جافا JDK 17.
   - Android SDK الإصدار 35 (مع دعم إصدارات أندرويد من Android 8.0 فأحدث).

2. **الاستنساخ والبناء:**
   ```bash
   git clone https://github.com/MMUU6699/build-x-android.git
   cd build-x-android

   # بناء نسخة الـ Release المجهزة للتثبيت المباشر
   ./gradlew assembleRelease
   ```
   ستجد ملف التطبيق الناتج بصيغة APK جاهزاً في:
   `app/build/outputs/apk/release/app-release.apk`

---

<div align="center">
Built with precision using <b>Kotlin & Jetpack Compose</b>.
</div>
