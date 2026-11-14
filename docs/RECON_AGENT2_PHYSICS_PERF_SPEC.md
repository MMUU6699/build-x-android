# RECON AGENT 2 — High-FPS Physics, Edge Protection & GPU Rendering Specification

**Author:** Agent 2 — Video Motion Analyst & High-FPS Animation/Library Researcher  
**Target Codebase:** `app.buildx.chat` (`compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`, Compose BOM `2024.10.00`, Kotlin `2.0.21`)  
**Scope:** Messaging / Chat Conversation & Chat/Group Info Surfaces ONLY (`ChatTokens.kt`, `ChatHeader.kt`, `ChatMessageList.kt`, `ChatBubble.kt`, `ChatComposer.kt`, `DmConversationScreen.kt`, `ChatInfoScreen.kt`, `DmChatInfoSheet.kt`).  
**Excluded:** `ProfileScreen.kt`, `EditProfileScreen.kt`, `SettingsScreens.kt`.

---

## 1. Current Codebase Audit & Frame-Budget Bottlenecks (120Hz = 8.33 ms/frame)

Inspection of `app/build.gradle.kts`, `gradle/libs.versions.toml`, and `app/src/main/java/app/buildx/chat/ui/chat/` + `ui/screens/` reveals **seven concrete rendering/physics bottlenecks** preventing true Telegram-grade (`DrKLO/Telegram`) 120fps motion:

| # | Current File & Lines | Observed Issue | 120fps / Visual Failure Mode |
|---|---|---|---|
| 1 | `DmConversationScreen.kt` (L347–567) | `ChatMessageList` has no `Modifier.clipToBounds()` and no `zIndex`; top/bottom chrome `Column`s have no explicit `Modifier.zIndex(10f)`; custom-wallpaper top scrim starts at `0.78f` alpha instead of `0.95f -> 0.78f -> 0f`. | Scrolling bubbles or `animateItem` placements can overdraw or peek through the status bar clock/battery icons on custom wallpapers; `ChatMessageRow`'s `zIndex(10f)` only orders inside `LazyColumn`, not relative to root overlays. |
| 2 | `DmConversationScreen.kt` (L313–321, L436, L521) | `onSizeChanged { bottomOverlayHeightPx = it.height }` feeds `listBottomPadding` into `LazyColumn(contentPadding = ...)`. | Whenever the unified reply bar collapses or composer shrinks from multi-line to 1 line on Send, `bottomOverlayHeightPx` changes every animation frame, forcing a **full `LazyColumn` layout/measure pass per frame** that fights `animateItem` and send flight. |
| 3 | `BuildXViewModel.kt` (L4038–4046) & `ChatComposer.kt` (L145–274) | `sendDmMessage()` clears `dmComposerText = ""` and `dmReplyingToMessage = null` in the same state emission as inserting `optimisticMsg`. `ChatComposer` runs `animateDpAsState` (`targetCorner` 20dp→24dp), `Column.animateContentSize`, `AnimatedVisibility` reply-bar collapse, and `expandHorizontally` attach reveal simultaneously. | Composer shape invalidates `RoundedCornerShape` outline every frame while `LazyColumn` inserts item `0` and relayouts `contentPadding`. |
| 4 | `ChatBubble.kt` (L444, L625–652, L838–865) | `ChatBubbleEntryWrapper` uses `ChatMotion.interactiveSpring()` (`0.72f / 480f`) instead of `0.82f / 420f`, and passes `sendFlightProgress: Float` by value into `ChatBubbleContent` where `lerp(composerBg, sentBubbleBg, flightFraction)` executes during **Composition** and passes a changing `Color` into `Modifier.drawWithCache`. | Forces **per-frame Recomposition + `drawWithCache` cache invalidation** on the flying bubble for the entire duration of the spring. |
| 5 | `ChatMessageList.kt` (L276–280) & `ChatBubble.kt` (L2036–2073) | Long-press context menu applies `Modifier.blur((2.5f * menuFocusProgress).dp)` **per individual `ChatMessageRow`** inside `LazyColumn`, and opens a plain `Popup` anchored inside the clipped row without a floating emoji reaction pill bar or foreground-sharp clone. | Allocates $N$ separate offscreen blur render nodes per frame; leaves `ChatHeader`, `ChatComposer`, `DateSeparator`, `PeerIntroItem`, and wallpaper unblurred; clips near header/composer; lacks Telegram reaction pill bar (`👍 ❤️ 🔥 😍 👏 🤔`). |
| 6 | `BuildXSheets.kt` (L233–290, L349–370) | `ActionMenuPopup` uses right-aligned icons (`OverflowMenuRow`) and default `188.dp` width rather than Telegram's compact left-icon popup (`210.dp` width, `16.dp` corner radius). | Visual mismatch with Telegram Android (`ChatActivity` context menu). |
| 7 | `ChatInfoScreen.kt` (L187–218) & `DmChatInfoSheet.kt` (L179–215) | `HeaderCircleButton` back button sits *inside* the `verticalScroll` `Column`, so it scrolls off-screen. Hero avatar is `92.dp` (`ChatInfoScreen`) / `84.dp` (`DmChatInfoSheet`) with zero scroll-coupled scaling/translation into a pinned `56.dp` status-bar-safe top bar. | No pinned navigation bar on scroll; no `88.dp -> 36.dp` Telegram `ProfileActivity` avatar collapse; risk of content colliding with system clock if `statusBarsPadding()` is scrolled past. |

### Dependency Verification (`gradle/libs.versions.toml` & `app/build.gradle.kts`)
- **Compose BOM:** `2024.10.00` (Compose UI 1.7.x — includes `Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)` on `LazyItemScope`, `Modifier.graphicsLayer { renderEffect = ... }`, `WindowInsets.ime` / `statusBars` / `navigationBars`).
- **Graphics/Blur:** No external blur library (`haze`, `cloudy`) is needed or configured. Native `android.graphics.RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP).asComposeRenderEffect()` (API 31+, `Build.VERSION_CODES.S`) is already wrapped by `compatRenderEffectBlur` in `ChatHeader.kt` (L593–609), with API 26–30 falling back to translucent gradient + dim scrims.
- **Target API:** `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`. All specs below run natively on API 26–35 with zero new Gradle dependencies.

---

## 2. Canonical Motion & Geometry Tokens (`ChatMotion` & `ChatDimens` Additions)

Add/align these constants in `ChatTokens.kt` (keeping all existing properties intact):

```kotlin
object ChatMotion {
    // ... existing constants ...

    // Zero-Jank 120fps Send Flight & Reply Collapse Spring
    const val SendFlightDampingRatio: Float = 0.82f
    const val SendFlightStiffness: Float = 420f

    // Reaction Pill Bar Staggered Micro-Spring
    const val ReactionPillDampingRatio: Float = 0.68f
    const val ReactionPillStiffness: Float = 480f
    const val ReactionEmojiStaggerMs: Long = 22L

    fun <T> sendFlightSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = SendFlightDampingRatio,
        stiffness = SendFlightStiffness,
        visibilityThreshold = visibilityThreshold
    )

    fun <T> reactionPillSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = ReactionPillDampingRatio,
        stiffness = ReactionPillStiffness,
        visibilityThreshold = visibilityThreshold
    )
}
```

And canonical geometry constants (referencing Agent 3's layout tokens):
- `ChatDimens.HeaderCapsuleHeight = 56.dp` (paired with `48.dp` circular Back/Overflow buttons)
- `ChatDimens.ReactionPillHeight = 44.dp`, `ReactionPillCornerRadius = 22.dp`
- `ChatDimens.ContextMenuWidth = 210.dp`, `ContextMenuCornerRadius = 16.dp`
- `ChatDimens.ChatInfoAvatarExpandedSize = 88.dp`, `ChatInfoAvatarCollapsedSize = 36.dp`, `ChatInfoTopBarHeight = 56.dp`, `ChatInfoCollapseRange = 136.dp`

---

## 3. Architecture 1: True Top/Bottom Edge Protection & Glassmorphic Header/Composer

### 3.1 Reference Pattern (`DrKLO/Telegram` & Compose Layering)
In Telegram Android (`ChatActivity`), `chatListView` sits strictly behind `actionBar` and `chatActivityEnterView`, clipped to the chat viewport, while `BlurredBackgroundDrawable` / protective gradient scrims mask the `statusBarHeight` + `actionBar` header zone and the bottom composer dock so scrolling messages never visually collide with the system clock, carrier icons, or floating capsules.

On Android Compose (BOM `2024.10.00`), **`Modifier.blur()` / `RenderEffect` blurs a node's own pixels, NOT the siblings behind it.** Therefore, true 120fps glassmorphic chrome without expensive `RenderNode` backdrop-copy shaders uses a **3-part composite**:
1. **Strict Z-Index Stack + `clipToBounds()` Viewport** on the message list so items never escape the viewport into parent chrome.
2. **Protective Vertical Gradient Scrim** spanning `WindowInsets.statusBars` + header zone (`0.95f` → `0.78f` → `0.0f` of `themeBackground`, where `themeBackground = chatColors.pageBackground` or wallpaper-tinted base) and mirrored at the bottom (`0.0f` → `0.78f` → `0.95f`) so text scrolling underneath fades smoothly before reaching the status bar clock or navigation bar.
3. **Frosted Glass Capsule (`ChatGlassCapsuleContainer`)** rendered above the scrim with `compatRenderEffectBlur` softening its internal multi-stop gradient + top specular highlight + `0.8.dp` refractive rim border, while keeping foreground icons/text razor-sharp.

### 3.2 Z-Order Stack Diagram

```text
Root Box (Modifier.fillMaxSize())
├── [zIndex(0f)]   ChatWallpaperLayer (fillMaxSize)
├── [zIndex(1f)]   MessageViewport Box (fillMaxSize().clipToBounds())
│                  └── ChatMessageList (LazyColumn reverseLayout = true)
│                        └── contentPadding(top = restingTopClearance, bottom = restingBottomClearance)
├── [zIndex(5f)]   Scroll-to-Bottom FAB (aligned BottomEnd, offset above bottom chrome)
├── [zIndex(10f)]  Top Protective Scrim + Glassmorphic Header Zone
│                  ├── Background: Brush.verticalGradient(0.0f -> bg@0.95f, statusPlusHeaderMid -> bg@0.78f, 1.0f -> Transparent)
│                  ├── Modifier.windowInsetsPadding(WindowInsets.statusBars)
│                  ├── ChatHeader (56.dp capsule + 48.dp circles)
│                  └── Optional DmInChatSearchBar / DmPinnedMessageBanner
├── [zIndex(10f)]  Bottom Protective Scrim + Glassmorphic Composer Dock
│                  ├── Background: Brush.verticalGradient(0.0f -> Transparent, 0.38f -> bg@0.78f, 1.0f -> bg@0.95f)
│                  ├── Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
│                  └── Typing Pill / VoiceDictationBar / ChatComposer
└── [zIndex(30f)]  Foreground-Sharp Context Menu + Floating Reaction Pill Overlay (Section 5)
```

> **Critical Detail on Blur vs Foreground Menu:** To make the background chat blur in a single GPU pass when the context menu opens (Section 5), wrap `[zIndex(0f)..zIndex(10f)]` inside a single `backgroundScaffold` `Box` that receives `Modifier.compatRenderEffectBlur(18.dp * menuFocusProgress)`, leaving `[zIndex(30f)]` as its sibling above it!

### 3.3 Copy-Paste-Ready Implementation (`DmConversationScreen.kt` & `ChatMessageList.kt`)

```kotlin
// Inside DmConversationScreen.kt

val density = LocalDensity.current
val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
val navAndImeInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

// Latch resting top/bottom clearance so reply-bar collapse or multi-line typing
// NEVER triggers per-frame LazyColumn contentPadding relayouts (see Section 4).
val baseHeaderHeightDp = 72.dp // 56.dp capsule + 8.dp * 2 vertical padding
val extraTopBannerDp = when {
    isSearchActive && detail?.pinnedMessage != null -> 112.dp
    isSearchActive -> 58.dp
    detail?.pinnedMessage != null -> 56.dp
    else -> 0.dp
}
val stableListTopPadding = statusBarTopDp + baseHeaderHeightDp + extraTopBannerDp + 8.dp
val restingComposerHeightDp = 62.dp // 50.dp minHeight + 4.dp top + 8.dp bottom

// Read WindowInsets.ime bottom via PaddingValues only for resting anchor or use imePadding()
// on the viewport container so LazyColumn scrolls naturally with the keyboard without
// mixing animated onSizeChanged px into contentPadding.
val bottomSystemPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
val stableListBottomPadding = restingComposerHeightDp + 10.dp

val topProtectionBrush = remember(chatColors.pageBackground) {
    Brush.verticalGradient(
        0.00f to chatColors.pageBackground.copy(alpha = 0.95f),
        0.58f to chatColors.pageBackground.copy(alpha = 0.78f),
        1.00f to Color.Transparent
    )
}

val bottomProtectionBrush = remember(chatColors.pageBackground) {
    Brush.verticalGradient(
        0.00f to Color.Transparent,
        0.38f to chatColors.pageBackground.copy(alpha = 0.78f),
        1.00f to chatColors.pageBackground.copy(alpha = 0.95f)
    )
}

Box(
    modifier = modifier
        .fillMaxSize()
        .background(chatColors.pageBackground)
) {
    // 1. Background Scaffold (blurred in 1 GPU pass ONLY while context menu is open)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (menuFocusProgress > 0.01f && !reduceMotion) {
                    Modifier.compatRenderEffectBlur(blurRadius = (18f * menuFocusProgress).dp)
                } else {
                    Modifier
                }
            )
    ) {
        ChatWallpaperLayer(
            config = uiState.activeDmWallpaper,
            isDark = colors.isDark,
            fallbackBackground = chatColors.pageBackground,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        )

        // 2. Strictly Clipped Message Viewport (NEVER bleeds into status bar or over chrome)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .clipToBounds()
        ) {
            ChatMessageList(
                // ...
                topContentPadding = stableListTopPadding,
                bottomContentPadding = stableListBottomPadding,
                scrollToBottomFabBottomOffset = restingComposerHeightDp + 16.dp,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Top Protective Scrim + Glassmorphic Header Zone (strict zIndex 10f)
        // Covers full statusBars height + header capsule + optional search/pinned banner.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .background(topProtectionBrush)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = 14.dp) // Extra fade tail below header capsule
        ) {
            ChatHeader(
                state = headerState,
                chatColors = chatColors,
                isListScrolled = isListScrolled,
                reduceMotion = reduceMotion,
                applyStatusBarsPadding = false, // Already applied on outer protective scrim Column
                onBackClick = onBackClick,
                onPillClick = openChatInfoAction,
                // ...
            )
            // AnimatedVisibility(isSearchActive) ...
            // detail?.pinnedMessage?.let { DmPinnedMessageBanner(...) }
        }

        // 4. Bottom Protective Scrim + Glassmorphic Composer Dock (strict zIndex 10f)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .background(bottomProtectionBrush)
                .padding(top = 16.dp) // Smooth fade-in tail above composer capsule
                .windowInsetsPadding(navAndImeInsets)
        ) {
            // Typing pill / VoiceDictationBar / ChatComposer
        }
    }

    // 5. Foreground-Sharp Context Menu + Floating Reaction Pill Overlay (Section 5)
    // Placed OUTSIDE the blurred background scaffold at zIndex(30f).
}
```

### 3.4 Why This Eliminates Status-Bar Bleed & Wallpaper Contrast Drops
- **Status-bar clock safety:** `0.00f -> 0.95f` covers the entire `WindowInsets.statusBars` strip *and* the top half of the header row regardless of whether a custom wallpaper is active (`hasCustomWallpaper`), so bright/high-contrast message bubbles or photo thumbnails scrolling beneath are attenuated by 95% behind the clock/battery icons and 78% behind the header capsule's outer halo.
- **No bleed during `animateItem` or overscroll:** `Modifier.clipToBounds()` on the `zIndex(1f)` viewport container combined with `zIndex(10f)` on both top and bottom protective scrims guarantees zero pixel bleed into system bars or above the header capsule.
- **No per-frame `LazyColumn` relayout on IME animation:** By applying `WindowInsets.navigationBars.union(WindowInsets.ime)` to the viewport wrapper `Box` (or using Compose's hardware-synchronized `imePadding()`), `LazyColumn` only resizes its viewport bounds cleanly with the system `WindowInsetsAnimationCompat` callback instead of receiving stale `onSizeChanged` pixel feedback from `ChatComposer`.

---

## 4. Architecture 2: Zero-Jank 120fps Send Flight & Reply Collapse

### 4.1 Root Cause of Current Jank
When the user taps Send while replying to a message (`replyingTo != null`) and typing multi-line text:
1. `BuildXViewModel.sendDmMessage()` atomically sets `dmComposerText = ""`, `dmReplyingToMessage = null`, and appends `optimisticMsg` to `dmMessages`.
2. In the current code:
   - `ChatComposer` runs `Column.animateContentSize()` + `AnimatedVisibility` `shrinkVertically()` on `ChatComposerContextBanner` + `animateDpAsState` on `targetCorner`.
   - `DmConversationScreen` observes `onSizeChanged { bottomOverlayHeightPx = it.height }` and feeds it straight into `ChatMessageList(bottomContentPadding = listBottomPadding)`. That mutates `LazyColumn`'s `PaddingValues` on **every frame** of the reply-bar collapse!
   - `ChatBubbleEntryWrapper` reads `animProgress.value` in composable scope and calls `lerp(chatColors.composerBg, chatColors.sentBubbleBg, flightFraction)` during **Composition**, passing a dynamic `Color` into `Modifier.drawBubbleSurface` (`drawWithCache`), invalidating the GPU `Path` cache on every frame!

### 4.2 Decoupled 3-Phase Pipeline (Zero Per-Frame Layout Recomposition)

To keep `LazyColumn` at a rock-solid **120fps (< 2 ms CPU frame time)** during simultaneous Send + Reply Collapse + Text Clear:

1. **Stable Keys & Immutable Item Payload:**
   - Every item in `ChatMessageList` already uses `key = { it.stableKey }` where `MessageItem.stableKey = message.clientTempId ?: message.id` (`ChatMessageMapper.kt` + `ChatUiModels.kt`). Because `clientTempId` is preserved when the server ACK replaces the optimistic message, `LazyColumn` **never recreates or cross-fades the row** on network ACK.
   - Keep `contentType = { ... }` strict (`"message"`, `"date_separator"`, `"peer_intro"`) so Compose reuses slot tables without reallocating composition nodes.

2. **Decouple `LazyColumn` `contentPadding` from Composer Collapse:**
   - Never wire `onSizeChanged { bottomOverlayHeightPx = ... }` into `LazyColumn(contentPadding = ...)`.
   - Keep `LazyColumn`'s `bottomContentPadding` fixed to `restingComposerHeightDp + 10.dp` (Section 3.3). When the reply banner (`42.dp`) or multi-line input collapses, the `ChatComposer` visually shrinks downward toward the navigation bar/IME using a **GPU-only collapse animation**, while `LazyColumn` stays completely undisturbed—only running `Modifier.animateItem(placementSpec = ChatMotion.sendFlightSpring())` once for the newly inserted message at index `0`!

3. **Defer ALL Flight & Collapse Reads to `Modifier.graphicsLayer` & `Modifier.drawBehind`:**
   - Use **`ChatMotion.sendFlightSpring()` (`dampingRatio = 0.82f, stiffness = 420f`)** for both the bubble's composer-to-list flight and the reply bar's collapse.
   - Never pass `sendFlightProgress: Float` into `@Composable` functions. Pass a state reader lambda `() -> Float` (or `State<Float>`) read **exclusively** inside `Modifier.graphicsLayer { ... }` and `Modifier.drawBehind { ... }`.
   - For the `composerBg -> sentBubbleBg` color transition: cache the rounded `Path` once in `drawWithCache` (using `corners` only), and inside `onDrawBehind` evaluate `val p = sendFlightProgressProvider()`, drawing `sentBubbleBg` + a `composerBg.copy(alpha = (1f - p).coerceIn(0f, 1f))` overlay (or `lerp` inside `onDrawBehind`). **Zero recompositions, zero `drawWithCache` rebuilds!**

### 4.3 Copy-Paste-Ready Implementation (`ChatBubble.kt` & `ChatComposer.kt`)

#### A. Zero-Recomposition Send Flight Wrapper (`ChatBubble.kt`)
```kotlin
/**
 * 120fps GPU-only send flight wrapper.
 * Reads Animatable.value strictly inside graphicsLayer {} and drawBehind {} lambdas
 * so Composition and Layout phases are skipped 100% on every animation frame.
 */
@Composable
fun ChatBubbleEntryWrapper(
    isOwn: Boolean,
    isJustSent: Boolean,
    isNewlyReceived: Boolean,
    reduceMotion: Boolean,
    composerFlightBoundsInWindow: Rect?,
    bubbleBoundsInWindow: Rect?,
    onConsumeJustSent: () -> Unit,
    onConsumeNewlyReceived: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (sendFlightProgressProvider: () -> Float) -> Unit
) {
    val isScreenRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current

    val flightProgress = remember {
        Animatable(if ((isJustSent || isNewlyReceived) && !reduceMotion) 0f else 1f)
    }
    val flightAlpha = remember {
        Animatable(if (isJustSent && !reduceMotion) ChatMotion.SendLiftStartAlpha else 1f)
    }

    LaunchedEffect(isJustSent, isNewlyReceived, reduceMotion) {
        if (reduceMotion) {
            flightProgress.snapTo(1f)
            flightAlpha.snapTo(1f)
            if (isJustSent) onConsumeJustSent()
            if (isNewlyReceived) onConsumeNewlyReceived()
            return@LaunchedEffect
        }
        if (isJustSent) {
            flightProgress.snapTo(0f)
            flightAlpha.snapTo(ChatMotion.SendLiftStartAlpha)
            coroutineScope {
                launch {
                    flightAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = ChatMotion.fastOutBezierTween(durationMillis = ChatMotion.SendFadeDurationMs)
                    )
                }
                launch {
                    flightProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = ChatMotion.sendFlightSpring() // dampingRatio = 0.82f, stiffness = 420f
                    )
                }
            }
            onConsumeJustSent()
        } else if (isNewlyReceived) {
            flightAlpha.snapTo(1f)
            flightProgress.snapTo(0f)
            flightProgress.animateTo(
                targetValue = 1f,
                animationSpec = ChatMotion.sendFlightSpring() // dampingRatio = 0.82f, stiffness = 420f
            )
            onConsumeNewlyReceived()
        }
    }

    // Precompute pixel fallbacks once outside graphicsLayer
    val fallbackYPx = with(density) { ChatMotion.SendLiftStartTranslationY.toPx() }
    val fallbackXPx = with(density) { ChatMotion.SendLiftStartTranslationX.toPx() }
    val receiveSlidePx = with(density) { ChatMotion.ReceiveEntrySlideY.toPx() }

    // Stable lambda reader — never triggers recomposition when flightProgress changes
    val progressProvider = remember(isJustSent, reduceMotion) {
        { if (isJustSent && !reduceMotion) flightProgress.value.coerceIn(0f, 1f) else 1f }
    }

    // Snapshot start deltas once when flight begins so window-bounds read doesn't jitter mid-flight
    val startDelta = remember(isJustSent, composerFlightBoundsInWindow, bubbleBoundsInWindow) {
        if (!isJustSent) {
            Offset.Zero
        } else if (
            composerFlightBoundsInWindow != null &&
            bubbleBoundsInWindow != null &&
            composerFlightBoundsInWindow.height > 0f &&
            bubbleBoundsInWindow.height > 0f
        ) {
            val dx = ((composerFlightBoundsInWindow.center.x - bubbleBoundsInWindow.center.x) * 0.45f)
                .coerceIn(-220f, 220f)
            val dy = (composerFlightBoundsInWindow.center.y - bubbleBoundsInWindow.center.y)
                .coerceIn(24f, 240f)
            Offset(dx, dy)
        } else {
            val signedFallbackX = if (isOwn xor isScreenRtl) -fallbackXPx else fallbackXPx
            Offset(signedFallbackX, fallbackYPx)
        }
    }

    val gpuFlightModifier = if (reduceMotion) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            val p = flightProgress.value
            val a = flightAlpha.value
            if (p >= 0.999f && a >= 0.999f) {
                scaleX = 1f
                scaleY = 1f
                translationX = 0f
                translationY = 0f
                alpha = 1f
            } else if (isJustSent) {
                val scale = ChatMotion.SendFlightStartScale + (1f - ChatMotion.SendFlightStartScale) * p
                val originX = if (isOwn xor isScreenRtl) 1f else 0f
                scaleX = scale
                scaleY = scale
                translationX = startDelta.x * (1f - p)
                translationY = startDelta.y * (1f - p)
                alpha = a.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(originX, 1f)
            } else if (isNewlyReceived) {
                val scale = ChatMotion.ReceiveEntryStartScale + (1f - ChatMotion.ReceiveEntryStartScale) * p
                val originX = if (isScreenRtl) 0.9f else 0.1f
                scaleX = scale
                scaleY = scale
                translationY = receiveSlidePx * (1f - p)
                alpha = p.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(originX, 1f)
            }
        }
    }

    Box(modifier = modifier.then(gpuFlightModifier)) {
        content(progressProvider)
    }
}
```

#### B. Zero-Cache-Invalidation Canvas Bubble Surface (`ChatBubble.kt`)
```kotlin
/**
 * Caches the Path once per bubble size/corners; reads flightProgressProvider() inside onDrawBehind
 * so color morphing (composerBg -> sentBubbleBg) incurs ZERO recompositions and ZERO Path rebuilds.
 */
private fun Modifier.drawBubbleSurfaceZeroJank(
    corners: BubbleCornerRadii,
    targetBackgroundColor: Color,
    flightStartBackgroundColor: Color? = null,
    flightProgressProvider: () -> Float = { 1f },
    borderColor: Color? = null,
    borderWidth: Dp = ChatDimens.GlassSurfaceBorderWidth,
    highlightOverlayColor: Color = Color.Transparent
): Modifier = this.drawWithCache {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val tl = (if (isRtl) corners.topEnd else corners.topStart).toPx()
    val tr = (if (isRtl) corners.topStart else corners.topEnd).toPx()
    val br = (if (isRtl) corners.bottomStart else corners.bottomEnd).toPx()
    val bl = (if (isRtl) corners.bottomEnd else corners.bottomStart).toPx()
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(tl, tl),
                topRightCornerRadius = CornerRadius(tr, tr),
                bottomRightCornerRadius = CornerRadius(br, br),
                bottomLeftCornerRadius = CornerRadius(bl, bl)
            )
        )
    }
    val strokePx = borderWidth.toPx()
    onDrawBehind {
        val p = flightProgressProvider().coerceIn(0f, 1f)
        val resolvedBg = if (flightStartBackgroundColor != null && p < 0.999f) {
            lerp(flightStartBackgroundColor, targetBackgroundColor, p)
        } else {
            targetBackgroundColor
        }
        drawPath(path = path, color = resolvedBg)
        if (highlightOverlayColor.alpha > 0.005f) {
            drawPath(path = path, color = highlightOverlayColor)
        }
        if (borderColor != null && borderColor.alpha > 0.005f) {
            drawPath(path = path, color = borderColor, style = Stroke(width = strokePx))
        }
    }
}
```

#### C. Unified Reply-Bar Collapse via `graphicsLayer` + `sendFlightSpring()` (`ChatComposer.kt`)
Replace `Column.animateContentSize()` + multi-spec `AnimatedVisibility` nesting with a single `sendFlightSpring()` (`dampingRatio = 0.82f, stiffness = 420f`) driving a `Modifier.layout` + `Modifier.graphicsLayer` collapse wrapper, AND snap `targetCorner` instead of invalidating shape per-frame (or use a fixed `22.dp`/`24.dp` capsule radius when single-line and `20.dp` only on discrete state toggle without per-frame outline thrash):

```kotlin
@Composable
fun UnifiedReplyCollapseContainer(
    visible: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 1f = expanded (42.dp + spacing), 0f = collapsed
    val expandAnim = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(visible, reduceMotion) {
        val target = if (visible) 1f else 0f
        if (reduceMotion) {
            expandAnim.snapTo(target)
        } else {
            expandAnim.animateTo(
                targetValue = target,
                animationSpec = ChatMotion.sendFlightSpring() // dampingRatio = 0.82f, stiffness = 420f
            )
        }
    }

    // Keep child composed only while visible or collapsing so exit animation completes cleanly
    if (visible || expandAnim.value > 0.005f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val p = expandAnim.value.coerceIn(0f, 1f)
                    val currentHeight = (placeable.height * p).roundToInt().coerceAtLeast(0)
                    layout(placeable.width, currentHeight) {
                        // Slide down toward the input baseline without remeasuring child
                        placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer {
                    val p = expandAnim.value.coerceIn(0f, 1f)
                    alpha = p
                    translationY = (1f - p) * 14.dp.toPx()
                    scaleY = 0.92f + (0.08f * p)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            content()
        }
    }
}
```

> **Why this works at 120fps:** The child `ChatComposerContextBanner` is measured at its natural height **once** (`placeable.height`), while `expandAnim.value` only changes the clip height in `layout {}` and GPU transform (`alpha`, `translationY`, `scaleY`) in `graphicsLayer {}`—and because `LazyColumn`'s `bottomContentPadding` is fixed to the resting composer height (Section 3.3), the `LazyColumn` NEVER re-measures during the reply bar's collapse!

---

## 5. Architecture 3: Foreground-Sharp Context Menu + Floating Reaction Pill Bar

### 5.1 Reference Pattern (`DrKLO/Telegram` `ChatActivity` Blur + Focused Message Overlay)
In Telegram Android:
1. Long-pressing a message bubble snapshots/blurs the **entire chat behind it** (`RenderNode` / `RenderEffect` single-pass blur + subtle dark scrim).
2. The **selected message bubble** stays **100% razor-sharp** in the foreground overlay at its exact screen position (clamped vertically so both the reaction pill above it and the popup menu below it fit safely inside `statusBars` and `navigationBars`).
3. Directly **above** the sharp bubble sits a horizontal **Reaction Pill Bar** (`👍 ❤️ 🔥 😍 👏 🤔`, height `44.dp`, corner radius `22.dp`) whose emojis bounce in with staggered spring micro-animations.
4. Directly **below** the sharp bubble sits a **Compact Left-Icon Context Menu** (`210.dp` width, `16.dp` corner radius, leading `20.dp` icon on `Start`, label after `12.dp` gap, destructive rows tinted `#E53935`).

### 5.2 Coordinate & Clamping Math (No Off-Screen Clipping)
Let `anchorRect: Rect` be the selected bubble's bounds in root coordinates (`coords.boundsInRoot()`), captured when `onBubbleLongPress` fires.
Given root viewport height `rootHeightPx`, `statusBarTopPx`, `navBarBottomPx`, `reactionPillHeightPx = 44.dp.toPx()`, `gapPx = 8.dp.toPx()`, `menuHeightPx` (measured via `onSizeChanged` on the menu column), and screen margin `edgeMarginPx = 12.dp.toPx()`:

```kotlin
val topSafePx = statusBarTopPx + edgeMarginPx + reactionPillHeightPx + gapPx
val bottomSafePx = rootHeightPx - navBarBottomPx - edgeMarginPx - menuHeightPx - gapPx

// Target Y for the sharp bubble's top edge so [ReactionPill + gap + Bubble + gap + Menu]
// fits completely inside safe system insets:
val clampedBubbleTopPx = when {
    // Normal case: fits without shifting, or gentle shift into safe band
    topSafePx <= bottomSafePx -> anchorRect.top.coerceIn(topSafePx, bottomSafePx)
    // Very tall bubble (taller than remaining space): pin reaction pill at top safe inset
    else -> topSafePx
}
```
Animate `bubbleShiftY` from `anchorRect.top` to `clampedBubbleTopPx` using `ChatMotion.interactiveSpring()` (`dampingRatio = 0.72f, stiffness = 480f`) inside `Modifier.offset { IntOffset(x, y) }`!

### 5.3 Copy-Paste-Ready Implementation (`FocusedMessageMenuOverlay`)

```kotlin
/**
 * State captured on message long-press so the foreground overlay can render the selected bubble
 * razor-sharp above a single-pass blurred background scaffold.
 */
@Immutable
data class FocusedMessageMenuState(
    val item: ChatListItem.MessageItem,
    val anchorBoundsInRoot: Rect,
    val menuItems: List<ActionMenuItem>
)

private val DefaultQuickReactions = listOf("👍", "❤️", "🔥", "😍", "👏", "🤔")

@Composable
fun FocusedMessageMenuOverlay(
    state: FocusedMessageMenuState?,
    chatColors: ChatColors,
    reduceMotion: Boolean,
    onSelectReaction: (messageId: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
    renderSharpBubble: @Composable (ChatListItem.MessageItem) -> Unit
) {
    // Keep last non-null state during exit animation
    var cachedState by remember { mutableStateOf(state) }
    if (state != null) cachedState = state
    val activeState = cachedState ?: return

    val focusAnim = remember { Animatable(0f) }
    LaunchedEffect(state != null, reduceMotion) {
        val target = if (state != null) 1f else 0f
        if (reduceMotion) {
            focusAnim.snapTo(target)
            if (state == null) cachedState = null
        } else {
            focusAnim.animateTo(
                targetValue = target,
                animationSpec = if (state != null) {
                    ChatMotion.interactiveSpring() // 0.72f / 480f
                } else {
                    ChatMotion.fastOutBezierTween(140)
                }
            )
            if (state == null) cachedState = null
        }
    }

    if (state == null && focusAnim.value <= 0.005f) return

    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val pillHeightPx = with(density) { 44.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val edgeMarginPx = with(density) { 12.dp.toPx() }
    val horizontalMarginPx = with(density) { 16.dp.toPx() }
    val menuWidthPx = with(density) { 210.dp.toPx() }

    var rootWidthPx by remember { mutableIntStateOf(0) }
    var rootHeightPx by remember { mutableIntStateOf(0) }
    var menuHeightPx by remember { mutableIntStateOf(with(density) { 220.dp.toPx() }.roundToInt()) }
    var pillWidthPx by remember { mutableIntStateOf(with(density) { 276.dp.toPx() }.roundToInt()) }

    val anchor = activeState.anchorBoundsInRoot
    val isOwn = activeState.item.isOwn

    // Safe vertical clamping so Reaction Pill + Bubble + Context Menu never clip
    val topSafePx = statusBarTopPx + edgeMarginPx + pillHeightPx + gapPx
    val bottomSafePx = (rootHeightPx - navBarBottomPx - edgeMarginPx - menuHeightPx - gapPx)
        .coerceAtLeast(topSafePx)
    val targetBubbleTopPx = if (rootHeightPx > 0) {
        anchor.top.coerceIn(topSafePx, bottomSafePx)
    } else {
        anchor.top
    }

    val bubbleAlignEnd = isOwn xor isRtl
    val pillX = if (bubbleAlignEnd) {
        (anchor.right - pillWidthPx).coerceIn(horizontalMarginPx, (rootWidthPx - pillWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx))
    } else {
        anchor.left.coerceIn(horizontalMarginPx, (rootWidthPx - pillWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx))
    }
    val menuX = if (bubbleAlignEnd) {
        (anchor.right - menuWidthPx).coerceIn(horizontalMarginPx, (rootWidthPx - menuWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx))
    } else {
        anchor.left.coerceIn(horizontalMarginPx, (rootWidthPx - menuWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .onSizeChanged {
                rootWidthPx = it.width
                rootHeightPx = it.height
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        // 1. Protective Backdrop Dim Scrim (drawn GPU-only via graphicsLayer alpha)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = focusAnim.value }
                .background(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Color.Black.copy(alpha = 0.24f)
                    } else {
                        // Stronger crisp dim fallback on API 26-30 where RenderEffect blur is unavailable
                        Color.Black.copy(alpha = 0.44f)
                    }
                )
        )

        // 2. Foreground-Sharp Selected Bubble (anchored & lifted with 1.025f focus scale)
        Box(
            modifier = Modifier
                .offset {
                    val p = focusAnim.value
                    val currentY = anchor.top + (targetBubbleTopPx - anchor.top) * p
                    IntOffset(anchor.left.roundToInt(), currentY.roundToInt())
                }
                .width(with(density) { anchor.width.toDp() })
                .graphicsLayer {
                    val p = focusAnim.value
                    val s = 1f + 0.025f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (bubbleAlignEnd) 1f else 0f, 0.5f)
                }
        ) {
            renderSharpBubble(activeState.item)
        }

        // 3. Floating Emoji Reaction Pill Bar ABOVE the Sharp Bubble
        TelegramReactionPillBar(
            emojis = DefaultQuickReactions,
            progressProvider = { focusAnim.value },
            pivotX = if (bubbleAlignEnd) 0.85f else 0.15f,
            reduceMotion = reduceMotion,
            onEmojiClick = { emoji ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectReaction(activeState.item.message.id, emoji)
            },
            modifier = Modifier
                .onSizeChanged { pillWidthPx = it.width }
                .offset {
                    val p = focusAnim.value
                    val currentBubbleTop = anchor.top + (targetBubbleTopPx - anchor.top) * p
                    val pillTop = currentBubbleTop - pillHeightPx - gapPx
                    IntOffset(pillX.roundToInt(), pillTop.roundToInt())
                }
        )

        // 4. Compact Left-Icon Context Menu BELOW the Sharp Bubble
        CompactLeftIconContextMenu(
            items = activeState.menuItems,
            progressProvider = { focusAnim.value },
            transformOrigin = TransformOrigin(if (bubbleAlignEnd) 0.88f else 0.12f, 0f),
            modifier = Modifier
                .onSizeChanged { menuHeightPx = it.height }
                .offset {
                    val p = focusAnim.value
                    val currentBubbleTop = anchor.top + (targetBubbleTopPx - anchor.top) * p
                    val menuTop = currentBubbleTop + anchor.height + gapPx
                    IntOffset(menuX.roundToInt(), menuTop.roundToInt())
                }
        )
    }
}

@Composable
private fun TelegramReactionPillBar(
    emojis: List<String>,
    progressProvider: () -> Float,
    pivotX: Float,
    reduceMotion: Boolean,
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                val p = progressProvider().coerceIn(0f, 1f)
                val s = 0.80f + 0.20f * p
                scaleX = s
                scaleY = s
                alpha = p
                translationY = (1f - p) * 8.dp.toPx()
                transformOrigin = TransformOrigin(pivotX, 1f)
            }
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 22.dp,
                blurRadius = 18.dp,
                offsetY = 4.dp
            )
            .clip(RoundedCornerShape(22.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        emojis.forEachIndexed { index, emoji ->
            val emojiAnim = remember(emoji) { Animatable(if (reduceMotion) 1f else 0f) }
            val tapBurst = remember(emoji) { Animatable(1f) }

            LaunchedEffect(Unit) {
                if (!reduceMotion) {
                    delay(index * ChatMotion.ReactionEmojiStaggerMs)
                    emojiAnim.animateTo(1f, ChatMotion.reactionPillSpring())
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        val e = emojiAnim.value
                        val burst = tapBurst.value
                        val combinedScale = (0.4f + 0.6f * e) * burst
                        scaleX = combinedScale
                        scaleY = combinedScale
                        translationY = (1f - e) * 10.dp.toPx()
                        alpha = e.coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        scope.launch {
                            if (!reduceMotion) {
                                tapBurst.animateTo(1.32f, spring(dampingRatio = 0.5f, stiffness = 700f))
                                tapBurst.animateTo(1f, ChatMotion.reactionPillSpring())
                            }
                            onEmojiClick(emoji)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 21.sp)
            }
        }
    }
}

@Composable
private fun CompactLeftIconContextMenu(
    items: List<ActionMenuItem>,
    progressProvider: () -> Float,
    transformOrigin: TransformOrigin,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = modifier
            .width(210.dp)
            .graphicsLayer {
                val p = progressProvider().coerceIn(0f, 1f)
                val s = 0.85f + 0.15f * p
                scaleX = s
                scaleY = s
                alpha = p
                translationY = (1f - p) * -6.dp.toPx()
                this.transformOrigin = transformOrigin
            }
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 16.dp,
                blurRadius = 18.dp,
                offsetY = 6.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) {
        items.forEachIndexed { index, item ->
            val tint = if (item.isDestructive) colors.destructiveRed else colors.primaryText
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(0.6.dp)
                        .background(colors.hairlineBorder)
                )
            }
        }
    }
}
```

> **Double-Shadow Prevention:** Inside `ChatMessageList`, pass `isHiddenForFocusOverlay = (item.message.id == focusedMenuState?.item?.message?.id)` to `ChatMessageRow`, and set `Modifier.graphicsLayer { alpha = if (isHiddenForFocusOverlay) 0f else 1f }`. This keeps the row's layout space reserved in `LazyColumn` while preventing a double blurred ghost underneath the sharp foreground bubble!

---

## 6. Architecture 4: Collapsible Status-Bar-Safe Chat/Group Info Header

### 6.1 Reference Pattern (`DrKLO/Telegram` `ProfileActivity`)
Telegram's `ProfileActivity` never uses Android's `CollapsingToolbarLayout` (which triggers layout re-measurement on every scroll frame). Instead, it computes a normalized scalar `progress ∈ [0f, 1f]` from the vertical scroll offset (`scrollY / collapseRangePx`) and drives `scaleX`, `scaleY`, `translationX`, `translationY`, and `alpha` directly on the GPU render properties of the avatar, title, and subtitle.

### 6.2 Exact Scroll Offset & Coordinate Math

Let:
- $H_{\text{status}}$ = `WindowInsets.statusBars.getTop(density)` (in px)
- $H_{\text{bar}}$ = `56.dp.toPx()` (pinned top bar height below status bar)
- $S_{\text{exp}}$ = `88.dp.toPx()` (expanded avatar diameter)
- $S_{\text{col}}$ = `36.dp.toPx()` (collapsed avatar diameter inside top bar)
- $R_{\text{collapse}}$ = `136.dp.toPx()` (scroll distance over which collapse completes)
- $f(\text{scrollY}) = \text{clamp}\left(\frac{\text{scrollY}}{R_{\text{collapse}}},\; 0,\; 1\right)$

Because `Modifier.graphicsLayer` applies `scaleX` / `scaleY` around `TransformOrigin.Center` (`(0.5f, 0.5f)`), **center-to-center interpolation** is exact and avoids top-left drift:

1. **Avatar Scale:**
   $$\text{scale}_{\text{avatar}} = 1.0 - f \cdot \left(1.0 - \frac{S_{\text{col}}}{S_{\text{exp}}}\right) \quad \left(1.0 \rightarrow \frac{36}{88} \approx 0.4091\right)$$

2. **Avatar Center Coordinates (Expanded vs Collapsed):**
   - Expanded center:
     $$C_{\text{exp}} = \left(\frac{W_{\text{viewport}}}{2},\; H_{\text{status}} + H_{\text{bar}} + 8\text{dp} + \frac{S_{\text{exp}}}{2}\right)$$
   - Collapsed center (start-aligned `12.dp` after the `44.dp` back button at `16.dp` start margin, RTL-aware):
     $$\text{backEnd} = 16\text{dp} + 44\text{dp} + 12\text{dp} = 72\text{dp}$$
     $$x_{\text{col}} = \begin{cases} W_{\text{viewport}} - (\text{backEnd} + \frac{S_{\text{col}}}{2}) & \text{if RTL} \\ \text{backEnd} + \frac{S_{\text{col}}}{2} & \text{if LTR} \end{cases}$$
     $$y_{\text{col}} = H_{\text{status}} + \frac{H_{\text{bar}}}{2}$$
   - Applied translation inside `Modifier.graphicsLayer {}` (when the avatar's base layout box is placed at $C_{\text{exp}}$ inside the non-scrolling `zIndex(11f)` overlay):
     $$\text{translationX} = (x_{\text{col}} - C_{\text{exp},x}) \cdot f$$
     $$\text{translationY} = (y_{\text{col}} - C_{\text{exp},y}) \cdot f$$
   - **Status-Bar Clock Guarantee:** At $f = 1$, the top edge of the `36.dp` collapsed avatar sits at:
     $$y_{\text{col}} - \frac{S_{\text{col}}}{2} = H_{\text{status}} + \frac{56\text{dp} - 36\text{dp}}{2} = H_{\text{status}} + 10\text{dp}$$
     It can **never** enter `[0 .. H_status]` and can **never** collide with the system clock!

3. **Title & Subtitle Interpolation:**
   - Expanded title sits centered at $y_{\text{title,exp}} = H_{\text{status}} + H_{\text{bar}} + 8\text{dp} + S_{\text{exp}} + 12\text{dp}$.
   - Collapsed title sits vertically centered in the upper half of the `56.dp` top bar at $x_{\text{title,col}} = \text{backEnd} + S_{\text{col}} + 10\text{dp}$ (`118.dp` from start edge), $y_{\text{title,col}} = H_{\text{status}} + 19\text{dp}$, scaled cleanly from `22.sp -> 16.sp` ($\text{scale} \approx 0.727$), while subtitle (`14.sp -> 12.sp`) translates smoothly to $y_{\text{sub,col}} = H_{\text{status}} + 38\text{dp}$.
   - **Zero-overlap width constraint:** The collapsed title/subtitle block must leave `72.dp` on the trailing side if a right action button exists (or `16.dp` if none), handled via `widthIn(max = ...)` or crossfading cleanly into the top bar slot at $f \ge 0.85$.

### 6.3 Copy-Paste-Ready Implementation (`rememberScrollState` + `NestedScrollConnection` Variants)

#### Variant A: Drop-in `rememberScrollState()` Architecture for `ChatInfoScreen.kt`

```kotlin
@Composable
fun CollapsibleChatInfoScaffold(
    title: String,
    subtitle: String,
    avatarUrl: String?,
    isDirect: Boolean,
    isVerified: Boolean,
    chatColors: ChatColors,
    onBackClick: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
    scrollableBody: @Composable ColumnScope.() -> Unit
) {
    val colors = BuildXTheme.colors
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scrollState = rememberScrollState()

    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    val topBarHeightDp = 56.dp
    val avatarExpandedDp = 88.dp
    val avatarCollapsedDp = 36.dp
    val collapseRangePx = with(density) { 136.dp.toPx() }

    val topBarHeightPx = with(density) { topBarHeightDp.toPx() }
    val avatarExpandedPx = with(density) { avatarExpandedDp.toPx() }
    val avatarCollapsedPx = with(density) { avatarCollapsedDp.toPx() }
    val expandedTopGapPx = with(density) { 8.dp.toPx() }
    val backButtonEndPx = with(density) { (16.dp + 44.dp + 12.dp).toPx() } // 72.dp
    val titleTopGapPx = with(density) { 12.dp.toPx() }
    val collapsedAvatarToTitleGapPx = with(density) { 10.dp.toPx() }

    // Discrete boolean ONLY for accessibility/elevation hairline; continuous scroll reads stay in graphicsLayer
    val isPinnedHeaderVisible by remember {
        derivedStateOf { scrollState.value > collapseRangePx * 0.82f }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(chatColors.pageBackground)
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }

        // Scrollable Body (zIndex 1f) with top spacer matching expanded hero height
        // Hero height = statusBar + 56dp top bar + 8dp + 88dp avatar + 12dp + ~52dp title/subtitle + 16dp
        val heroSpacerHeightDp = with(density) { statusBarHeightPx.toDp() } + topBarHeightDp + 176.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .clipToBounds()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(heroSpacerHeightDp))
            scrollableBody()
        }

        // Pinned Status-Bar-Safe Top Bar Background & Protective Scrim (zIndex 10f)
        // Guarantees system clock & status icons never collide with scrolled rows.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .height(with(density) { statusBarHeightPx.toDp() } + topBarHeightDp)
                .drawBehind {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    // Base status-bar-safe protective gradient at f = 0 -> solid glass bar at f = 1
                    drawRect(
                        color = chatColors.pageBackground.copy(alpha = 0.92f + 0.06f * f)
                    )
                    if (f > 0.75f) {
                        val lineAlpha = ((f - 0.75f) / 0.25f).coerceIn(0f, 1f)
                        drawLine(
                            color = colors.hairlineBorder.copy(alpha = lineAlpha),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 0.6.dp.toPx()
                        )
                    }
                }
        ) {
            // Pinned Back Button & Optional Trailing Action (strictly below statusBars)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(topBarHeightDp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCircleButton(
                    iconRes = R.drawable.ic_back,
                    contentDescription = stringResource(R.string.dm_back),
                    onClick = onBackClick,
                    backgroundColor = chatColors.headerGlassBg,
                    iconTint = colors.primaryText
                )
                Spacer(modifier = Modifier.weight(1f))
                if (trailingAction != null) {
                    trailingAction()
                }
            }
        }

        // Collapsing Hero Avatar + Title + Subtitle Overlay (zIndex 11f, above top bar bg, below status bar)
        val expandedCenterX = viewportWidthPx / 2f
        val expandedCenterY = statusBarHeightPx + topBarHeightPx + expandedTopGapPx + (avatarExpandedPx / 2f)
        val collapsedCenterX = if (isRtl) {
            viewportWidthPx - (backButtonEndPx + avatarCollapsedPx / 2f)
        } else {
            backButtonEndPx + (avatarCollapsedPx / 2f)
        }
        val collapsedCenterY = statusBarHeightPx + (topBarHeightPx / 2f)
        val targetScale = avatarCollapsedPx / avatarExpandedPx // 36 / 88 = 0.4091f

        // Avatar placed at expanded layout origin, shifted & scaled purely on GPU
        Box(
            modifier = Modifier
                .zIndex(11f)
                .offset {
                    IntOffset(
                        x = ((viewportWidthPx - avatarExpandedPx) / 2f).roundToInt(),
                        y = (statusBarHeightPx + topBarHeightPx + expandedTopGapPx).roundToInt()
                    )
                }
                .size(avatarExpandedDp)
                .graphicsLayer {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    val s = 1f - f * (1f - targetScale)
                    scaleX = s
                    scaleY = s
                    translationX = (collapsedCenterX - expandedCenterX) * f
                    translationY = (collapsedCenterY - expandedCenterY) * f
                }
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(1.dp, colors.subtleSurfaceBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(id = if (isDirect) R.drawable.ic_user else R.drawable.ic_plus),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Title + Subtitle dual-layer crossfade/translate:
        // 1) Centered hero title/subtitle fades & slides up for f in [0..0.65]
        val heroTextExpandedTopPx = statusBarHeightPx + topBarHeightPx + expandedTopGapPx + avatarExpandedPx + titleTopGapPx
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .zIndex(11f)
                .fillMaxWidth()
                .offset { IntOffset(0, heroTextExpandedTopPx.roundToInt()) }
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    val fade = (1f - (f / 0.65f)).coerceIn(0f, 1f)
                    alpha = fade
                    translationY = -f * with(density) { 44.dp.toPx() }
                    val s = 1f - 0.14f * f
                    scaleX = s
                    scaleY = s
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = colors.primaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isDirect && isVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VerifiedBadge(size = 18.dp)
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 2) Pinned Top-Bar Title + Subtitle fades & slides in for f in [0.55..1.0]
        val collapsedTitleStartDp = with(density) {
            (backButtonEndPx + avatarCollapsedPx + collapsedAvatarToTitleGapPx).toDp()
        }
        val collapsedTitleEndDp = if (trailingAction != null) 72.dp else 16.dp
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .zIndex(11f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(topBarHeightDp)
                .padding(start = collapsedTitleStartDp, end = collapsedTitleEndDp)
                .graphicsLayer {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    val fadeIn = ((f - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    alpha = fadeIn
                    translationY = (1f - fadeIn) * with(density) { 10.dp.toPx() }
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDirect && isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
```

#### Variant B: `NestedScrollConnection` (For `LazyColumn`-backed Info Screens or `DmChatInfoSheet`)
If `ChatInfoScreen` or `DmChatInfoSheet` migrates its body to a `LazyColumn` (e.g., for large member lists or shared media grids), `scrollState.value` is not linear across lazy items. Use a `NestedScrollConnection` with `Animatable` / `mutableFloatStateOf` tracking cumulative header collapse `offsetPx ∈ [-collapseRangePx, 0f]`:

```kotlin
class CollapsingHeaderNestedScrollConnection(
    private val collapseRangePx: Float
) : NestedScrollConnection {
    var headerOffsetPx by mutableFloatStateOf(0f)
        private set

    val collapseFraction: Float
        get() = (-headerOffsetPx / collapseRangePx.coerceAtLeast(1f)).coerceIn(0f, 1f)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        // Collapse upward before scrolling list down
        if (delta < 0f && headerOffsetPx > -collapseRangePx) {
            val prev = headerOffsetPx
            headerOffsetPx = (headerOffsetPx + delta).coerceIn(-collapseRangePx, 0f)
            return Offset(0f, headerOffsetPx - prev)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        // Expand header only once LazyColumn reaches index 0 top
        if (delta > 0f && headerOffsetPx < 0f) {
            val prev = headerOffsetPx
            headerOffsetPx = (headerOffsetPx + delta).coerceIn(-collapseRangePx, 0f)
            return Offset(0f, headerOffsetPx - prev)
        }
        return Offset.Zero
    }
}
```
Feed `connection.collapseFraction` into the exact same `graphicsLayer {}` formulas from Variant A (`scaleX`, `scaleY`, `translationX`, `translationY`, `alpha`). For `DmChatInfoSheet` (`fillMaxHeight(0.92f)`), replace `statusBarHeightPx` with the sheet's drag-handle clearance (`20.dp.toPx()`).

---

## 7. Implementation Checklist & Verification Matrix for Downstream Agents

| Target File | Exact Change Required | Verification Check |
|---|---|---|
| `ChatTokens.kt` | Add `SendFlightDampingRatio = 0.82f`, `SendFlightStiffness = 420f`, `sendFlightSpring()`, `reactionPillSpring()`. | Preserves all existing `ChatMotion` & `ChatDimens` members. |
| `DmConversationScreen.kt` | Wrap background scaffold in 1-pass `compatRenderEffectBlur`; enforce `zIndex(1f)` + `clipToBounds()` on `ChatMessageList` viewport; enforce `zIndex(10f)` + `0.95f -> 0.78f -> 0f` gradient scrims on top/bottom chrome; replace per-frame `onSizeChanged -> listBottomPadding` feedback loop with resting padding; host `FocusedMessageMenuOverlay` at `zIndex(30f)`. | Scrolling messages never bleed into status bar clock or over header capsule on light/dark or custom wallpapers; zero `LazyColumn` relayout during reply-bar collapse. |
| `ChatBubble.kt` | Update `ChatBubbleEntryWrapper` to pass `() -> Float` lambda and use `ChatMotion.sendFlightSpring()`; replace color-lerping in composition with `drawBubbleSurfaceZeroJank`; add `isHiddenForFocusOverlay` alpha hide + `FocusedMessageMenuOverlay` (`TelegramReactionPillBar` + `CompactLeftIconContextMenu`). | Compose Layout Inspector / Recomposition counter shows **0 recompositions** on message rows during the 120fps send flight. |
| `ChatComposer.kt` | Wrap `ChatComposerContextBanner` in `UnifiedReplyCollapseContainer` using `sendFlightSpring()` (`0.82f, 420f`) in `layout {}` + `graphicsLayer {}`. | Reply bar collapses smoothly alongside text clear without resizing `LazyColumn` `contentPadding`. |
| `ChatInfoScreen.kt` & `DmChatInfoSheet.kt` | Adopt `CollapsibleChatInfoScaffold` (`rememberScrollState` or `CollapsingHeaderNestedScrollConnection`) with `88.dp -> 36.dp` avatar, `56.dp` pinned bar, and `WindowInsets.statusBars` protective scrim at `zIndex(10f)`. | Back button stays pinned; collapsed `36.dp` avatar stays `10.dp` below `WindowInsets.statusBars` bottom edge. |

### Automated & Profiling Verification Commands
```powershell
# Compile check
.\gradlew.bat :app:compileDebugKotlin --console=plain

# Unit & Roborazzi screenshot regression tests
.\gradlew.bat testDebugUnitTest --tests "app.buildx.chat.ChatMessageMapperTest" --tests "app.buildx.chat.DmMessagingTest" --tests "app.buildx.chat.DmScreenshotTest"
```
**120Hz GPU Profiling Verification:** Capture a Systrace / Perfetto trace (`gfx`, `view`, `sched`) on a 120Hz device in Release mode (`isMinifyEnabled` + Baseline Profile) while executing: (1) Send message with active reply banner, (2) Fast fling under top header + custom wallpaper, (3) Long-press context menu open/dismiss, (4) `ChatInfoScreen` scroll `0 -> 136dp`. Verify `DoFrame` remains `< 8.33 ms` and `Choreographer` exhibits zero `measure/layout` spikes on `LazyColumn` during send flight and reply collapse.
