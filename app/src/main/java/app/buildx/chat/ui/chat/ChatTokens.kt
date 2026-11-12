package app.buildx.chat.ui.chat

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.ui.theme.BuildXColors

/**
 * Color tokens specifically tuned for User-to-User (DM) chat.
 * Light mode uses Meta AI-inspired soft neutral gray (#F1F3F2) and brand blue (#0064E0).
 * Dark mode uses an intentional deep slate palette rather than naive inversion.
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
    val typingDot: Color
)

fun BuildXColors.toChatColors(): ChatColors = if (isDark) {
    ChatColors(
        pageBackground = pageBackground,
        receivedBubbleBg = Color(0xFF1D2126),
        receivedBubbleText = Color(0xFFF3F5F6),
        receivedMetaText = Color(0xFFF3F5F6).copy(alpha = 0.60f),
        sentBubbleBg = Color(0xFF1668E3),
        sentBubbleText = Color(0xFFFFFFFF),
        sentMetaText = Color(0xFFFFFFFF).copy(alpha = 0.72f),
        readCheckAccent = Color(0xFFB5ECFF),
        failedAccent = destructiveRed,
        deletedPillBg = Color(0xFF15181C),
        deletedPillBorder = Color(0xFF282D34),
        deletedPillText = secondaryText,
        quotedBarSent = Color(0xFFFFFFFF).copy(alpha = 0.80f),
        quotedBarReceived = primaryBlue,
        quotedBgSent = Color(0xFF000000).copy(alpha = 0.16f),
        quotedBgReceived = Color(0xFFFFFFFF).copy(alpha = 0.06f),
        dateChipBg = Color(0xFF1A1E23),
        dateChipText = secondaryText,
        headerGlassBg = Color(0xFA171A1E),
        headerGlassBorder = subtleSurfaceBorder,
        headerShadow = headerHaloShadowColor,
        composerBg = Color(0xFF171A1E),
        composerBorder = subtleSurfaceBorder,
        composerShadow = composerShadowColor,
        composerActionBg = Color(0xFF1668E3),
        composerActionDisabledBg = Color(0xFF2A3038),
        composerIconTint = secondaryText,
        scrollToBottomBg = Color(0xFF1E2228),
        scrollToBottomBorder = subtleSurfaceBorder,
        scrollToBottomShadow = headerHaloShadowColor,
        unreadBadgeBg = primaryBlue,
        unreadBadgeText = Color.White,
        onlineDot = Color(0xFF34C759),
        typingDot = primaryBlue
    )
} else {
    ChatColors(
        pageBackground = pageBackground,
        receivedBubbleBg = Color(0xFFF1F3F2),
        receivedBubbleText = Color(0xFF0A0A0A),
        receivedMetaText = Color(0xFF0A0A0A).copy(alpha = 0.60f),
        sentBubbleBg = Color(0xFF0064E0),
        sentBubbleText = Color(0xFFFFFFFF),
        sentMetaText = Color(0xFFFFFFFF).copy(alpha = 0.70f),
        readCheckAccent = Color(0xFFB5ECFF),
        failedAccent = destructiveRed,
        deletedPillBg = Color(0xFFF8F9F8),
        deletedPillBorder = Color(0xFFE1E4E2),
        deletedPillText = secondaryText,
        quotedBarSent = Color(0xFFFFFFFF).copy(alpha = 0.85f),
        quotedBarReceived = Color(0xFF0064E0),
        quotedBgSent = Color(0xFF000000).copy(alpha = 0.14f),
        quotedBgReceived = Color(0xFF000000).copy(alpha = 0.05f),
        dateChipBg = Color(0xFFF1F3F2),
        dateChipText = secondaryText,
        headerGlassBg = Color(0xFCFFFFFF),
        headerGlassBorder = subtleSurfaceBorder,
        headerShadow = headerHaloShadowColor,
        composerBg = Color(0xFFFFFFFF),
        composerBorder = subtleSurfaceBorder,
        composerShadow = composerShadowColor,
        composerActionBg = Color(0xFF0064E0),
        composerActionDisabledBg = Color(0xFFE4E7EA),
        composerIconTint = secondaryText,
        scrollToBottomBg = Color(0xFFFFFFFF),
        scrollToBottomBorder = subtleSurfaceBorder,
        scrollToBottomShadow = headerHaloShadowColor,
        unreadBadgeBg = Color(0xFF0064E0),
        unreadBadgeText = Color.White,
        onlineDot = Color(0xFF28CD41),
        typingDot = Color(0xFF0064E0)
    )
}

/**
 * Single source of truth for all DM chat geometry, spacing (8dp grid), and typography sizes.
 */
object ChatDimens {
    // Grouping & list spacing
    val IntraGroupSpacing: Dp = 2.dp
    val InterGroupSpacing: Dp = 12.dp
    val GroupMergeThresholdMillis: Long = 120_000L // 2 minutes
    val ListHorizontalPadding: Dp = 12.dp
    val ListTopPadding: Dp = 8.dp
    val ListBottomPadding: Dp = 6.dp
    val PaginationPrefetchThreshold: Int = 8

    // Bubble radii (RTL-aware start/end)
    val BubbleCornerLarge: Dp = 20.dp
    val BubbleCornerGrouped: Dp = 6.dp
    val BubbleMaxWidthFraction: Float = 0.78f
    val BubbleHorizontalPadding: Dp = 14.dp
    val BubbleVerticalPadding: Dp = 8.dp
    val MediaBubbleCorner: Dp = 18.dp
    val MediaMaxWidth: Dp = 248.dp
    val MediaMinHeight: Dp = 120.dp
    val MediaMaxHeight: Dp = 280.dp
    val GroupAvatarSize: Dp = 28.dp
    val GroupAvatarGap: Dp = 8.dp

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

    // Composer pill (8dp grid rhythm)
    val ComposerOuterPaddingH: Dp = 14.dp
    val ComposerOuterPaddingTop: Dp = 4.dp
    val ComposerOuterPaddingBottom: Dp = 8.dp
    val ComposerMinHeight: Dp = 50.dp
    val ComposerCorner: Dp = 26.dp
    val ComposerBorderWidth: Dp = 1.dp
    val ComposerAttachButtonSize: Dp = 40.dp
    val ComposerActionSize: Dp = 44.dp
    val ComposerTextMaxLines: Int = 6
    val ComposerTextMaxHeight: Dp = 136.dp
    val ComposerShadowBlur: Dp = 14.dp
    val ComposerShadowOffsetY: Dp = 3.dp

    // Swipe-to-reply & scroll-to-bottom
    val SwipeReplyMaxOffsetDp: Dp = 76.dp
    val SwipeReplyTriggerDp: Dp = 52.dp
    val ScrollToBottomSize: Dp = 42.dp
    val ScrollToBottomMargin: Dp = 16.dp
}

/**
 * Tunable animation durations, springs, and motion curves.
 * Change values here to tune the feel of the entire DM chat experience.
 */
object ChatMotion {
    // Bubble send transition ("composer -> bubble" lift)
    const val SendLiftStartScale: Float = 0.72f
    const val SendLiftStartAlpha: Float = 0.35f
    val SendLiftStartTranslationY: Dp = 44.dp
    const val SendLiftDampingRatio: Float = 0.80f
    const val SendLiftStiffness: Float = Spring.StiffnessMediumLow
    const val SendFadeDurationMs: Int = 200

    // List item placement & receive entry
    const val ItemPlacementDamping: Float = 0.84f
    const val ItemPlacementStiffness: Float = Spring.StiffnessMediumLow
    const val ReceiveEntryDurationMs: Int = 220
    const val ReceiveEntryStartScale: Float = 0.96f
    val ReceiveEntrySlideY: Dp = 12.dp

    // Composer mic <-> send button swap
    const val ActionSwapDurationMs: Int = 175
    const val ActionSwapStartScale: Float = 0.60f
    const val ActionSwapRotationDeg: Float = 24f
    const val AttachSlideDurationMs: Int = 160
    const val PlaceholderFadeMs: Int = 150

    // Status icon transitions (clock -> check -> double check)
    const val StatusTransitionMs: Int = 180
    const val StatusStartScale: Float = 0.75f

    // Deletion & scroll-to-bottom
    const val DeleteCrossfadeMs: Int = 220
    const val ScrollToBottomAnimMs: Int = 180
    const val HeaderStateAnimMs: Int = 180
    const val PressScaleDown: Float = 0.96f

    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
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
