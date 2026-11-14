package app.buildx.chat.ui.chat

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.ui.theme.BuildXColors
import app.buildx.chat.ui.theme.BuildXFontFamily

/**
 * Color tokens specifically tuned for User-to-User (DM) and Group chat.
 * Light mode pairs Build X brand blue (#0064E0) with soft neutral gray (#F1F3F2) and Telegram-grade
 * translucency/blur tokens for floating glass surfaces over wallpapers.
 * Dark mode uses an intentional deep slate palette (Telegram Night family) rather than naive inversion.
 */
@Immutable
data class ChatColors(
    val pageBackground: Color,
    val receivedBubbleBg: Color,
    val receivedBubbleText: Color,
    val receivedMetaText: Color,
    val sentBubbleBg: Color,
    val sentBubbleText: Color,
    val sentMetaText: Color,
    val readCheckAccent: Color,
    val failedAccent: Color,
    val deletedPillBg: Color,
    val deletedPillBorder: Color,
    val deletedPillText: Color,
    val quotedBarSent: Color,
    val quotedBarReceived: Color,
    val quotedBgSent: Color,
    val quotedBgReceived: Color,
    val dateChipBg: Color,
    val dateChipText: Color,
    val headerGlassBg: Color,
    val headerGlassBorder: Color,
    val headerShadow: Color,
    val composerBg: Color,
    val composerBorder: Color,
    val composerShadow: Color,
    val composerActionBg: Color,
    val composerActionDisabledBg: Color,
    val composerIconTint: Color,
    val scrollToBottomBg: Color,
    val scrollToBottomBorder: Color,
    val scrollToBottomShadow: Color,
    val unreadBadgeBg: Color,
    val unreadBadgeText: Color,
    val onlineDot: Color,
    val typingDot: Color,
    val menuBackdropScrim: Color = Color(0x33000000),
    // Telegram-grade extended visual tokens (appended with defaults for full backward compatibility)
    val sentCheckPendingTint: Color = Color(0xB3FFFFFF),
    val sentCheckDeliveredTint: Color = Color(0xD1FFFFFF),
    val inboxCheckReadAccent: Color = Color(0xFF0064E0),
    val divider: Color = Color(0xFFEFEFEF),
    val dateChipBorder: Color = Color(0x14000000),
    val onlineDotRing: Color = Color(0xFFFFFFFF),
    val unreadBadgeMutedBg: Color = Color(0xFFB8BDC2),
    val pinnedBannerBg: Color = Color(0xEBFFFFFF),
    val pinnedBannerAccent: Color = Color(0xFF0064E0),
    val pinnedBadgeTint: Color = Color(0xFF8E9398),
    val swipeReplyPillBg: Color = Color(0x1F000000),
    val swipeReplyIconTint: Color = Color(0xFFFFFFFF),
    val replyHighlightOverlay: Color = Color(0x260064E0),
    val unreadDividerBg: Color = Color(0x140064E0),
    val unreadDividerText: Color = Color(0xFF0064E0),
    // System Event & Date Pills + Reaction Bar / Context Menu translucent tokens
    val systemPillBg: Color = Color(0xCCEBEEF2),
    val systemPillText: Color = Color(0xFF4F5660),
    val systemPillBorder: Color = Color(0x14000000),
    val reactionPillBg: Color = Color(0xF5FFFFFF),
    val reactionPillBorder: Color = Color(0x14000000),
    val contextMenuBg: Color = Color(0xF8FFFFFF),
    val contextMenuBorder: Color = Color(0x14000000)
)

fun BuildXColors.toChatColors(): ChatColors = if (isDark) {
    ChatColors(
        pageBackground = pageBackground,
        receivedBubbleBg = Color(0xFF1D2126),
        receivedBubbleText = Color(0xFFF3F5F6),
        receivedMetaText = Color(0xFFF3F5F6).copy(alpha = 0.60f),
        sentBubbleBg = Color(0xFF1668E3),
        sentBubbleText = Color(0xFFFFFFFF),
        sentMetaText = Color(0xFFFFFFFF).copy(alpha = 0.74f),
        readCheckAccent = Color(0xFFFFFFFF),
        failedAccent = destructiveRed,
        deletedPillBg = Color(0xFF15181C),
        deletedPillBorder = Color(0xFF282D34),
        deletedPillText = secondaryText,
        quotedBarSent = Color(0xFFFFFFFF),
        quotedBarReceived = primaryBlue,
        quotedBgSent = Color(0xFFFFFFFF).copy(alpha = 0.16f),
        quotedBgReceived = primaryBlue.copy(alpha = 0.14f),
        dateChipBg = Color(0xDB1A1E23), // 86% alpha frosted dark chip over wallpapers
        dateChipText = secondaryText,
        headerGlassBg = Color(0xE0171A1E), // 88% alpha glassmorphic header surface
        headerGlassBorder = subtleSurfaceBorder,
        headerShadow = headerHaloShadowColor,
        composerBg = Color(0xEB171A1E), // 92% alpha glassmorphic composer dock
        composerBorder = subtleSurfaceBorder,
        composerShadow = composerShadowColor,
        composerActionBg = Color(0xFF1668E3),
        composerActionDisabledBg = Color(0xFF2A3038),
        composerIconTint = secondaryText,
        scrollToBottomBg = Color(0xEB1E2228), // 92% alpha floating pill
        scrollToBottomBorder = subtleSurfaceBorder,
        scrollToBottomShadow = headerHaloShadowColor,
        unreadBadgeBg = primaryBlue,
        unreadBadgeText = Color.White,
        onlineDot = Color(0xFF34C759),
        typingDot = primaryBlue,
        menuBackdropScrim = Color(0x4D000000),
        sentCheckPendingTint = Color(0xB3FFFFFF),
        sentCheckDeliveredTint = Color(0xD6FFFFFF),
        inboxCheckReadAccent = Color(0xFF34C759),
        divider = Color(0xFF282C30),
        dateChipBorder = Color(0x1FFFFFFF),
        onlineDotRing = Color(0xFF0E1012),
        unreadBadgeMutedBg = Color(0xFF4B525B),
        pinnedBannerBg = Color(0xE6171A1E), // 90% alpha
        pinnedBannerAccent = primaryBlue,
        pinnedBadgeTint = Color(0xFF7D848D),
        swipeReplyPillBg = Color(0x52000000),
        swipeReplyIconTint = Color(0xFFFFFFFF),
        replyHighlightOverlay = primaryBlue.copy(alpha = 0.22f),
        unreadDividerBg = primaryBlue.copy(alpha = 0.16f),
        unreadDividerText = primaryBlue,
        systemPillBg = Color(0xCC1B2027), // 80% translucent dark slate pill over wallpapers
        systemPillText = Color(0xFFD0D5DC),
        systemPillBorder = Color(0x1FFFFFFF),
        reactionPillBg = Color(0xF21E2228),
        reactionPillBorder = subtleSurfaceBorder,
        contextMenuBg = Color(0xF51D2126),
        contextMenuBorder = subtleSurfaceBorder
    )
} else {
    ChatColors(
        pageBackground = pageBackground,
        receivedBubbleBg = Color(0xFFF1F3F2),
        receivedBubbleText = Color(0xFF0A0A0A),
        receivedMetaText = Color(0xFF0A0A0A).copy(alpha = 0.60f),
        sentBubbleBg = Color(0xFF0064E0),
        sentBubbleText = Color(0xFFFFFFFF),
        sentMetaText = Color(0xFFFFFFFF).copy(alpha = 0.72f),
        readCheckAccent = Color(0xFFFFFFFF),
        failedAccent = destructiveRed,
        deletedPillBg = Color(0xFFF8F9F8),
        deletedPillBorder = Color(0xFFE1E4E2),
        deletedPillText = secondaryText,
        quotedBarSent = Color(0xFFFFFFFF),
        quotedBarReceived = Color(0xFF0064E0),
        quotedBgSent = Color(0xFFFFFFFF).copy(alpha = 0.16f),
        quotedBgReceived = Color(0xFF0064E0).copy(alpha = 0.10f),
        dateChipBg = Color(0xE6F1F3F2), // 90% alpha frosted light chip over wallpapers
        dateChipText = secondaryText,
        headerGlassBg = Color(0xEBFFFFFF), // 92% alpha glassmorphic header surface
        headerGlassBorder = subtleSurfaceBorder,
        headerShadow = headerHaloShadowColor,
        composerBg = Color(0xF5FFFFFF), // 96% alpha glassmorphic composer dock
        composerBorder = subtleSurfaceBorder,
        composerShadow = composerShadowColor,
        composerActionBg = Color(0xFF0064E0),
        composerActionDisabledBg = Color(0xFFE4E7EA),
        composerIconTint = secondaryText,
        scrollToBottomBg = Color(0xF5FFFFFF), // 96% alpha floating pill
        scrollToBottomBorder = subtleSurfaceBorder,
        scrollToBottomShadow = headerHaloShadowColor,
        unreadBadgeBg = Color(0xFF0064E0),
        unreadBadgeText = Color.White,
        onlineDot = Color(0xFF28CD41),
        typingDot = Color(0xFF0064E0),
        menuBackdropScrim = Color(0x2E000000),
        sentCheckPendingTint = Color(0xB3FFFFFF),
        sentCheckDeliveredTint = Color(0xD6FFFFFF),
        inboxCheckReadAccent = Color(0xFF0064E0),
        divider = Color(0xFFEFEFEF),
        dateChipBorder = Color(0x14000000),
        onlineDotRing = Color(0xFFFFFFFF),
        unreadBadgeMutedBg = Color(0xFFB8BDC2),
        pinnedBannerBg = Color(0xF0FFFFFF), // 94% alpha
        pinnedBannerAccent = Color(0xFF0064E0),
        pinnedBadgeTint = Color(0xFF8E9398),
        swipeReplyPillBg = Color(0x33000000),
        swipeReplyIconTint = Color(0xFFFFFFFF),
        replyHighlightOverlay = Color(0x1F0064E0),
        unreadDividerBg = Color(0x140064E0),
        unreadDividerText = Color(0xFF0064E0),
        systemPillBg = Color(0xCCEBEEF2), // 80% translucent frosted pill over light/wallpaper canvas
        systemPillText = Color(0xFF4F5660),
        systemPillBorder = Color(0x14000000),
        reactionPillBg = Color(0xF5FFFFFF),
        reactionPillBorder = subtleSurfaceBorder,
        contextMenuBg = Color(0xF8FFFFFF),
        contextMenuBorder = subtleSurfaceBorder
    )
}

/**
 * Glassmorphic surface alpha and blur radius tokens for floating Chat chrome
 * (header capsules, composer dock, date chips, scroll-to-bottom FAB, and pinned banner).
 * On API 31+ (`Build.VERSION_CODES.S`), pair surface alphas with the corresponding blur radius;
 * on API < 31, the surface alpha provides a crisp translucent scrim fallback.
 */
object ChatGlassTokens {
    val HeaderBlurRadius: Dp = 24.dp
    val ComposerBlurRadius: Dp = 22.dp
    val MenuBlurRadius: Dp = 20.dp
    val PinnedBannerBlurRadius: Dp = 20.dp
    val FloatingChipBlurRadius: Dp = 16.dp

    const val HeaderGlassAlphaLight: Float = 0.92f
    const val HeaderGlassAlphaDark: Float = 0.88f
    const val ComposerGlassAlphaLight: Float = 0.96f
    const val ComposerGlassAlphaDark: Float = 0.92f
    const val DateChipGlassAlphaLight: Float = 0.90f
    const val DateChipGlassAlphaDark: Float = 0.86f
    const val PinnedBannerGlassAlphaLight: Float = 0.94f
    const val PinnedBannerGlassAlphaDark: Float = 0.90f
    const val ScrollFabGlassAlphaLight: Float = 0.96f
    const val ScrollFabGlassAlphaDark: Float = 0.92f
}

/**
 * Single source of truth for all DM chat geometry, spacing (8dp grid), stroke weights, and sizing.
 */
object ChatDimens {
    // Grouping & list spacing (2.dp..4.dp for clustered, 10.dp..12.dp for separated)
    val IntraGroupSpacing: Dp = 3.dp
    val InterGroupSpacing: Dp = 11.dp
    val GroupMergeThresholdMillis: Long = 120_000L // 2 minutes (includes same-minute bursts)
    val ListHorizontalPadding: Dp = 12.dp
    val ListTopPadding: Dp = 8.dp
    val ListBottomPadding: Dp = 6.dp
    val PaginationPrefetchThreshold: Int = 8

    // Bubble radii (RTL-aware start/end: 18.dp outer corners, 4.dp connected inner corners)
    val BubbleCornerLarge: Dp = 18.dp
    val BubbleCornerGrouped: Dp = 4.dp
    val BubbleTailCorner: Dp = 4.dp
    val BubbleMaxWidthFraction: Float = 0.78f
    val BubbleHorizontalPadding: Dp = 14.dp
    val BubbleVerticalPadding: Dp = 8.dp
    val MediaBubbleCorner: Dp = 18.dp
    val MediaMaxWidth: Dp = 248.dp
    val MediaMinHeight: Dp = 120.dp
    val MediaMaxHeight: Dp = 280.dp
    val GroupAvatarSize: Dp = 28.dp
    val GroupAvatarGap: Dp = 8.dp

    // Quoted reply inside bubble
    val QuoteCornerSmall: Dp = 6.dp
    val QuoteCornerLarge: Dp = 10.dp
    val QuoteAccentBarWidth: Dp = 3.dp

    // Bubble typography & metadata
    val MessageFontSize = 16.sp
    val MessageLineHeight = 22.sp
    val DeletedFontSize = 13.5.sp
    val MetaTimeFontSize = 11.sp
    val MetaTimeLineHeight = 13.sp
    val StatusIconSize: Dp = 13.5.dp
    val MetaGap: Dp = 4.dp
    val InlineMetaStartGap: Dp = 8.dp
    val WrapMetaTopGap: Dp = 3.dp
    val SeenAvatarSize: Dp = 15.dp
    val SeenAvatarOverlap: Dp = (-4).dp

    // Vector icon stroke weights (1.75dp - 2.0dp rounded caps/joins)
    val IconStrokeWeightStandard: Dp = 1.75.dp
    val IconStrokeWeightEmphasis: Dp = 1.85.dp
    val IconStrokeWeightBold: Dp = 2.0.dp
    val DividerThickness: Dp = 0.6.dp
    val GlassSurfaceBorderWidth: Dp = 0.8.dp

    // Date separator chip
    val DateChipCorner: Dp = 12.dp
    val DateChipPaddingH: Dp = 12.dp
    val DateChipPaddingV: Dp = 4.dp
    val DateChipVerticalMargin: Dp = 14.dp

    // Compact shared profile / intro card
    val ProfileCardMaxWidth: Dp = 280.dp
    val ProfileCardCorner: Dp = 20.dp
    val ProfileCardAvatarSize: Dp = 48.dp
    val ProfileCardPadding: Dp = 12.dp

    // Floating glass header
    val HeaderHorizontalPadding: Dp = 16.dp
    val HeaderVerticalPadding: Dp = 8.dp
    val HeaderButtonSize: Dp = 46.dp
    val HeaderCapsuleHeight: Dp = 46.dp
    val HeaderAvatarSize: Dp = 38.dp
    val HeaderItemGap: Dp = 8.dp
    val HeaderShadowBlurBase: Dp = 12.dp
    val HeaderShadowBlurScrolled: Dp = 20.dp
    val HeaderShadowOffsetBase: Dp = 2.dp
    val HeaderShadowOffsetScrolled: Dp = 5.dp
    val TypingDotSize: Dp = 4.5.dp
    val OnlineDotSize: Dp = 10.dp
    val OnlineDotRingWidth: Dp = 2.dp

    // Composer pill (8dp grid rhythm)
    val ComposerOuterPaddingH: Dp = 14.dp
    val ComposerOuterPaddingTop: Dp = 4.dp
    val ComposerOuterPaddingBottom: Dp = 8.dp
    val ComposerMinHeight: Dp = 50.dp
    val ComposerCorner: Dp = 26.dp
    val ComposerExpandedCorner: Dp = 20.dp
    val ComposerBorderWidth: Dp = 0.8.dp
    val ComposerAttachButtonSize: Dp = 40.dp
    val ComposerActionSize: Dp = 42.dp
    val ComposerTextMaxLines: Int = 6
    val ComposerTextMaxHeight: Dp = 136.dp
    val ComposerShadowBlur: Dp = 14.dp
    val ComposerShadowOffsetY: Dp = 3.dp

    // Swipe-to-reply & scroll-to-bottom
    val SwipeReplyMaxOffsetDp: Dp = 96.dp
    val SwipeReplyTriggerDp: Dp = 56.dp
    val SwipeReplyIconCircleSize: Dp = 36.dp
    val ScrollToBottomSize: Dp = 42.dp
    val ScrollToBottomMargin: Dp = 16.dp

    // Tight Telegram Bubble Tokens
    val BubblePaddingHorizontal: Dp = 11.dp
    val BubblePaddingVertical: Dp = 6.dp
    val BubbleIntraClusterSpacing: Dp = 2.dp
    val BubbleInterClusterSpacing: Dp = 8.dp
    val BubbleCornerSmall: Dp = 4.dp

    // System Event & Date Pills
    val SystemPillHeight: Dp = 26.dp
    const val SystemPillCornerPercent: Int = 50
    val SystemPillPaddingH: Dp = 12.dp
    val SystemPillPaddingV: Dp = 4.dp

    // Telegram ChatBox (Unified Capsule + External Mic/Send Action Button)
    val ComposerCapsuleMinHeight: Dp = 46.dp
    val ComposerExternalButtonSize: Dp = 48.dp
    val UnifiedReplyBarHeight: Dp = 42.dp
    val UnifiedReplyAccentWidth: Dp = 3.dp

    // Reaction Pill Bar & Left-Icon Context Menu
    val ReactionPillHeight: Dp = 44.dp
    val ReactionPillCornerRadius: Dp = 22.dp
    val ContextMenuWidth: Dp = 210.dp
    val ContextMenuCornerRadius: Dp = 16.dp

    // Group/Chat Info Screen
    val ChatInfoAvatarExpandedSize: Dp = 88.dp
    val ChatInfoAvatarCollapsedSize: Dp = 36.dp
    val ChatInfoActionSquircleRadius: Dp = 14.dp
    val ChatInfoTopBarHeight: Dp = 56.dp
}

/**
 * BiDi-safe Telegram-grade typography scale for all Chat & DM Inbox surfaces.
 * Uses `BuildXFontFamily` (Plus Jakarta Sans + Noto Sans Arabic), tabular numerals (`tnum`)
 * on timestamps/counters, and `TextDirection.ContentOrLtr` / `TextDirection.Ltr` to prevent
 * mixed Arabic/English metadata flipping.
 */
object ChatTypography {
    val messageBody = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = ChatDimens.MessageFontSize,
        lineHeight = ChatDimens.MessageLineHeight,
        textDirection = TextDirection.ContentOrLtr
    )

    val messageDeleted = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = ChatDimens.DeletedFontSize,
        lineHeight = 18.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val senderName = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val quoteSender = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val quoteBody = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val metaTimestamp = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = ChatDimens.MetaTimeFontSize,
        lineHeight = ChatDimens.MetaTimeLineHeight,
        fontFeatureSettings = "tnum",
        textDirection = TextDirection.Ltr
    )

    val dateChip = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val systemPill = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val headerTitle = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp,
        lineHeight = 18.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val headerSubtitle = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 15.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val composerInput = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val composerPlaceholder = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val pinnedBannerTitle = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val pinnedBannerPreview = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val inboxTitle = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val inboxPreview = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        textDirection = TextDirection.ContentOrLtr
    )

    val inboxTime = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = "tnum",
        textDirection = TextDirection.Ltr
    )

    val unreadBadge = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum",
        textDirection = TextDirection.Ltr
    )
}

/**
 * Deterministic high-contrast sender accent colors for group message headers and quoted reply bars.
 */
private val DarkSenderNamePalette = listOf(
    Color(0xFF4ADE80), // Mint Green
    Color(0xFFFB7185), // Rose
    Color(0xFF60A5FA), // Sky Blue
    Color(0xFFFBBF24), // Amber Gold
    Color(0xFFC084FC), // Soft Violet
    Color(0xFFF472B6), // Coral Pink
    Color(0xFF22D3EE), // Cyan
    Color(0xFFFB923C)  // Warm Peach
)

private val LightSenderNamePalette = listOf(
    Color(0xFF0F7B43), // Emerald
    Color(0xFFBE123C), // Deep Rose
    Color(0xFF1D4ED8), // Cobalt Blue
    Color(0xFFB45309), // Deep Amber
    Color(0xFF6D28D9), // Rich Violet
    Color(0xFF9D174D), // Magenta Berry
    Color(0xFF0E7490), // Deep Teal
    Color(0xFFC2410C)  // Burnt Sienna
)

fun senderNameColor(senderId: String, isDark: Boolean): Color {
    val palette = if (isDark) DarkSenderNamePalette else LightSenderNamePalette
    val idx = (senderId.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[idx]
}

/**
 * Telegram-Grade Physics & Animation Engine (120fps GPU-accelerated foundation).
 *
 * 1. Interactive/Popup Spring: dampingRatio = 0.72f, stiffness = 480f
 *    (context menus, reactions, morphing buttons, scroll-to-bottom pill)
 * 2. Non-Bouncy Layout/Gesture Spring: dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f
 *    (swipe-to-reply snapback, bubble corner morphing, layout bounds, composer expansion)
 * 3. Fast Out Cubic Bezier: CubicBezierEasing(0.19f, 1.0f, 0.22f, 1.0f)
 *    (screen and sheet transitions)
 */
object ChatMotion {
    // Core Physics Specifications
    const val InteractivePopupDampingRatio: Float = 0.72f
    const val InteractivePopupStiffness: Float = 480f

    const val LayoutGestureDampingRatio: Float = Spring.DampingRatioNoBouncy
    const val LayoutGestureStiffness: Float = 420f

    val FastOutCubicBezier: Easing = CubicBezierEasing(0.19f, 1.0f, 0.22f, 1.0f)

    fun <T> interactiveSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = InteractivePopupDampingRatio,
        stiffness = InteractivePopupStiffness,
        visibilityThreshold = visibilityThreshold
    )

    fun <T> layoutGestureSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = LayoutGestureDampingRatio,
        stiffness = LayoutGestureStiffness,
        visibilityThreshold = visibilityThreshold
    )

    fun <T> fastOutBezierTween(durationMillis: Int = 280): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        easing = FastOutCubicBezier
    )

    // Telegram send flight physics (0.82f damping, 420f stiffness)
    const val SendFlightDampingRatio: Float = 0.82f
    const val SendFlightStiffness: Float = 420f

    fun <T> sendFlightSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = SendFlightDampingRatio,
        stiffness = SendFlightStiffness,
        visibilityThreshold = visibilityThreshold
    )

    // Bubble send transition ("composer -> bubble" spring flight)
    const val SendLiftStartScale: Float = 0.84f
    const val SendFlightStartScale: Float = 0.88f
    const val SendLiftStartScaleX: Float = 0.84f
    const val SendLiftStartScaleY: Float = 0.78f
    const val SendLiftStartAlpha: Float = 0.18f
    val SendLiftStartTranslationY: Dp = 52.dp
    val SendLiftStartTranslationX: Dp = 22.dp
    const val SendLiftDampingRatio: Float = InteractivePopupDampingRatio
    const val SendLiftStiffness: Float = InteractivePopupStiffness
    const val SendFadeDurationMs: Int = 110

    // List item placement & receive entry
    const val ItemPlacementDamping: Float = LayoutGestureDampingRatio
    const val ItemPlacementStiffness: Float = LayoutGestureStiffness
    const val ReceiveEntryDurationMs: Int = 220
    const val ReceiveEntryStartScale: Float = 0.94f
    val ReceiveEntrySlideY: Dp = 14.dp

    // Context menu scale/alpha spring
    const val ContextMenuInitialScale: Float = 0.85f

    // Composer mic <-> send button morph
    const val ActionSwapDurationMs: Int = 175
    const val ActionSwapStartScale: Float = 0.60f
    const val ActionSwapRotationDeg: Float = 90f
    const val AttachSlideDurationMs: Int = 160
    const val PlaceholderFadeMs: Int = 140

    // Status icon transitions (clock -> check -> double check)
    const val StatusTransitionMs: Int = 180
    const val StatusStartScale: Float = 0.75f

    // Typing indicator & reply jump flash
    const val TypingDotCycleMs: Int = 1200
    const val TypingDotStaggerMs: Int = 180
    const val HighlightFlashDurationMs: Int = 900

    // Deletion & scroll-to-bottom
    const val DeleteCrossfadeMs: Int = 220
    const val ScrollToBottomAnimMs: Int = 180
    const val HeaderStateAnimMs: Int = 180
    const val PressScaleDown: Float = 0.96f

    val EmphasizedDecelerate: Easing = FastOutCubicBezier
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        }.getOrDefault(false)
    }
}
