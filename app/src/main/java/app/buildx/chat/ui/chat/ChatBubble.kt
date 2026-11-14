package app.buildx.chat.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.DmReplyPreview
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private val BubbleIntraClusterSpacing: Dp = ChatDimens.BubbleIntraClusterSpacing
private val BubbleInterClusterSpacing: Dp = ChatDimens.BubbleInterClusterSpacing
private val BubbleGroupAvatarSize: Dp = 32.dp
private val QuoteMinHeight: Dp = 36.dp

@Immutable
data class BubbleCornerRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp
) {
    fun toShape(): RoundedCornerShape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}

/**
 * RTL-aware bubble corner system:
 * Large radius (18.dp) on outer corners, smaller radius (4.dp) on grouped sender-side corners.
 */
fun chatBubbleShape(
    isOwn: Boolean,
    position: ChatBubblePosition
): Shape {
    val large = ChatDimens.BubbleCornerLarge
    val grouped = ChatDimens.BubbleCornerGrouped
    return if (isOwn) {
        when (position) {
            ChatBubblePosition.SINGLE -> RoundedCornerShape(large)
            ChatBubblePosition.FIRST -> RoundedCornerShape(
                topStart = large, topEnd = large, bottomEnd = grouped, bottomStart = large
            )
            ChatBubblePosition.MIDDLE -> RoundedCornerShape(
                topStart = large, topEnd = grouped, bottomEnd = grouped, bottomStart = large
            )
            ChatBubblePosition.LAST -> RoundedCornerShape(
                topStart = large, topEnd = grouped, bottomEnd = large, bottomStart = large
            )
        }
    } else {
        when (position) {
            ChatBubblePosition.SINGLE -> RoundedCornerShape(large)
            ChatBubblePosition.FIRST -> RoundedCornerShape(
                topStart = large, topEnd = large, bottomEnd = large, bottomStart = grouped
            )
            ChatBubblePosition.MIDDLE -> RoundedCornerShape(
                topStart = grouped, topEnd = large, bottomEnd = large, bottomStart = grouped
            )
            ChatBubblePosition.LAST -> RoundedCornerShape(
                topStart = grouped, topEnd = large, bottomEnd = large, bottomStart = large
            )
        }
    }
}

/**
 * Dynamically morphs inner connected bubble corners between 18.dp and 4.dp in real time
 * using the non-bouncy layout spring (`dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f`)
 * when adjacent messages from the same sender arrive above or below.
 */
@Composable
fun rememberAnimatedBubbleCorners(
    isOwn: Boolean,
    position: ChatBubblePosition,
    topCornerDp: Dp? = null,
    bottomCornerDp: Dp? = null,
    reduceMotion: Boolean = false
): BubbleCornerRadii {
    val large = ChatDimens.BubbleCornerLarge
    val grouped = ChatDimens.BubbleCornerGrouped
    val connectedAbove = position == ChatBubblePosition.MIDDLE || position == ChatBubblePosition.LAST
    val connectedBelow = position == ChatBubblePosition.FIRST || position == ChatBubblePosition.MIDDLE

    val resolvedTopCluster = topCornerDp ?: if (connectedAbove) grouped else large
    val resolvedBottomCluster = bottomCornerDp ?: if (connectedBelow) grouped else large

    val targetTopStart = if (!isOwn && connectedAbove) resolvedTopCluster else large
    val targetTopEnd = if (isOwn && connectedAbove) resolvedTopCluster else large
    val targetBottomEnd = if (isOwn && connectedBelow) resolvedBottomCluster else large
    val targetBottomStart = if (!isOwn && connectedBelow) resolvedBottomCluster else large

    val spec = if (reduceMotion) snap<Dp>() else ChatMotion.layoutGestureSpring()
    val topStart by animateDpAsState(targetValue = targetTopStart, animationSpec = spec, label = "corner_top_start")
    val topEnd by animateDpAsState(targetValue = targetTopEnd, animationSpec = spec, label = "corner_top_end")
    val bottomEnd by animateDpAsState(targetValue = targetBottomEnd, animationSpec = spec, label = "corner_bottom_end")
    val bottomStart by animateDpAsState(targetValue = targetBottomStart, animationSpec = spec, label = "corner_bottom_start")

    return BubbleCornerRadii(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}

@Composable
fun rememberAnimatedChatBubbleShape(
    isOwn: Boolean,
    position: ChatBubblePosition,
    reduceMotion: Boolean = false
): RoundedCornerShape = rememberAnimatedBubbleCorners(
    isOwn = isOwn,
    position = position,
    reduceMotion = reduceMotion
).toShape()

/**
 * Hardware-accelerated GPU Canvas bubble surface renderer.
 * Caches the morphed 18.dp/4.dp rounded [Path] once per size/corners and reads
 * [flightProgressProvider] exclusively inside [onDrawBehind], eliminating per-frame
 * recompositions and Path rebuilds during 120fps send flights.
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

@Composable
fun ChatMessageRow(
    item: ChatListItem.MessageItem,
    chatColors: ChatColors,
    isMenuOpen: Boolean,
    menuItems: List<ActionMenuItem>,
    isJustSent: Boolean,
    isNewlyReceived: Boolean,
    reduceMotion: Boolean,
    onConsumeJustSent: () -> Unit,
    onConsumeNewlyReceived: () -> Unit,
    onBubbleTap: () -> Unit,
    onRetryClick: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onDismissMenu: () -> Unit,
    onSwipeToReply: () -> Unit,
    modifier: Modifier = Modifier,
    onImageClick: () -> Unit = onBubbleTap,
    onBubbleLongPress: () -> Unit = onBubbleTap,
    onBubbleLongPressWithBounds: ((Rect, Dp) -> Unit)? = null,
    onReactionClick: (String) -> Unit = {},
    onSenderProfileClick: (String) -> Unit = {},
    isHighlighted: Boolean = false,
    isDimmed: Boolean = false,
    isHiddenForFocusOverlay: Boolean = false,
    composerFlightBoundsInWindow: Rect? = null
) {
    val topSpacing = if (item.isGroupedWithPrevious) {
        BubbleIntraClusterSpacing
    } else {
        BubbleInterClusterSpacing
    }
    val dimAlpha by animateFloatAsState(
        targetValue = if (isDimmed && !isMenuOpen) 0.55f else 1f,
        animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
        label = "row_dim_alpha"
    )
    var bubbleBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    ChatBubbleEntryWrapper(
        isOwn = item.isOwn,
        isJustSent = isJustSent,
        isNewlyReceived = isNewlyReceived,
        reduceMotion = reduceMotion,
        composerFlightBoundsInWindow = composerFlightBoundsInWindow,
        bubbleBoundsInWindow = bubbleBoundsInWindow,
        onConsumeJustSent = onConsumeJustSent,
        onConsumeNewlyReceived = onConsumeNewlyReceived,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isMenuOpen) 10f else 0f)
            .graphicsLayer { alpha = if (isHiddenForFocusOverlay) 0f else dimAlpha }
            .padding(top = topSpacing)
    ) { sendFlightProgressProvider ->
        Column(modifier = Modifier.fillMaxWidth()) {
            ChatSwipeToReplyContainer(
                enabled = !item.isDeleted && item.deliveryStatus != ChatDeliveryStatus.SENDING,
                chatColors = chatColors,
                reduceMotion = reduceMotion,
                onSwipeToReply = onSwipeToReply
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (item.isOwn) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (item.isGroupChat && !item.isOwn) {
                        if (item.showSenderAvatar) {
                            ChatGroupSenderAvatar(
                                avatarUrl = item.message.senderAvatarUrl,
                                displayName = item.isolatedSenderName ?: item.resolvedSenderName,
                                chatColors = chatColors,
                                onClick = item.resolvedSenderHandle?.let { handle ->
                                    { onSenderProfileClick(handle) }
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.width(BubbleGroupAvatarSize))
                        }
                        Spacer(modifier = Modifier.width(ChatDimens.GroupAvatarGap))
                    }

                    BoxWithConstraints(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        val maxBubbleWidth = maxWidth * ChatDimens.BubbleMaxWidthFraction
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (item.isOwn) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            ChatBubbleCard(
                                item = item,
                                chatColors = chatColors,
                                maxBubbleWidth = maxBubbleWidth,
                                isMenuOpen = isMenuOpen,
                                isHighlighted = isHighlighted,
                                reduceMotion = reduceMotion,
                                isJustSentFlight = isJustSent && !reduceMotion,
                                sendFlightProgressProvider = sendFlightProgressProvider,
                                onBubbleTap = onBubbleTap,
                                onBubbleLongPress = {
                                    val captured = bubbleBoundsInWindow
                                    if (onBubbleLongPressWithBounds != null && captured != null) {
                                        onBubbleLongPressWithBounds(captured, maxBubbleWidth)
                                    } else {
                                        onBubbleLongPress()
                                    }
                                },
                                onImageClick = onImageClick,
                                onRetryClick = onRetryClick,
                                onReplyPreviewClick = onReplyPreviewClick,
                                onReactionClick = onReactionClick,
                                onSenderProfileClick = onSenderProfileClick,
                                onBubbleBoundsChanged = { bounds -> bubbleBoundsInWindow = bounds }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleEntryWrapper(
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
    val animProgress = remember {
        Animatable(if ((isJustSent || isNewlyReceived) && !reduceMotion) 0f else 1f)
    }
    val sendAlpha = remember {
        Animatable(if (isJustSent && !reduceMotion) ChatMotion.SendLiftStartAlpha else 1f)
    }

    LaunchedEffect(isJustSent, isNewlyReceived, reduceMotion) {
        if (reduceMotion) {
            animProgress.snapTo(1f)
            sendAlpha.snapTo(1f)
            if (isJustSent) onConsumeJustSent()
            if (isNewlyReceived) onConsumeNewlyReceived()
            return@LaunchedEffect
        }
        if (isJustSent) {
            animProgress.snapTo(0f)
            sendAlpha.snapTo(ChatMotion.SendLiftStartAlpha)
            coroutineScope {
                launch {
                    sendAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = ChatMotion.fastOutBezierTween(durationMillis = ChatMotion.SendFadeDurationMs)
                    )
                }
                launch {
                    animProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = ChatMotion.sendFlightSpring()
                    )
                }
            }
            onConsumeJustSent()
        } else if (isNewlyReceived) {
            sendAlpha.snapTo(1f)
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = ChatMotion.sendFlightSpring()
            )
            onConsumeNewlyReceived()
        }
    }

    val progressProvider = remember(isJustSent, reduceMotion) {
        { if (isJustSent && !reduceMotion) animProgress.value.coerceIn(0f, 1f) else 1f }
    }
    val fallbackYPx = with(density) { ChatMotion.SendLiftStartTranslationY.toPx() }
    val fallbackXPx = with(density) { ChatMotion.SendLiftStartTranslationX.toPx() }
    val receiveSlidePx = with(density) { ChatMotion.ReceiveEntrySlideY.toPx() }

    // Latch start delta once valid bounds arrive so composer collapse / scroll never jitters mid-flight
    var latchedStartDelta by remember(isJustSent) { mutableStateOf<Offset?>(null) }
    val resolvedStartDelta = if (!isJustSent) {
        Offset.Zero
    } else {
        latchedStartDelta ?: run {
            val cBounds = composerFlightBoundsInWindow
            val bBounds = bubbleBoundsInWindow
            if (cBounds != null && bBounds != null && cBounds.height > 0f && bBounds.height > 0f) {
                val dx = ((cBounds.center.x - bBounds.center.x) * 0.45f).coerceIn(-220f, 220f)
                val dy = (cBounds.center.y - bBounds.center.y).coerceIn(24f, 240f)
                Offset(dx, dy).also { latchedStartDelta = it }
            } else {
                val signedFallbackX = if (isOwn xor isScreenRtl) -fallbackXPx else fallbackXPx
                Offset(signedFallbackX, fallbackYPx)
            }
        }
    }

    val graphicsModifier = if (reduceMotion || (!isJustSent && !isNewlyReceived && !animProgress.isRunning)) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            val p = animProgress.value
            if (p >= 0.999f && sendAlpha.value >= 0.999f) {
                scaleX = 1f
                scaleY = 1f
                translationX = 0f
                translationY = 0f
                alpha = 1f
            } else if (isJustSent) {
                val startScale = ChatMotion.SendFlightStartScale
                val scale = startScale + (1f - startScale) * p
                val originX = if (isOwn xor isScreenRtl) 1f else 0f
                scaleX = scale
                scaleY = scale
                translationX = resolvedStartDelta.x * (1f - p)
                translationY = resolvedStartDelta.y * (1f - p)
                alpha = sendAlpha.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(originX, 1f)
            } else {
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

    Box(modifier = modifier.then(graphicsModifier)) {
        content(progressProvider)
    }
}

@Composable
internal fun ChatBubbleCard(
    item: ChatListItem.MessageItem,
    chatColors: ChatColors,
    maxBubbleWidth: Dp,
    isMenuOpen: Boolean,
    isHighlighted: Boolean,
    reduceMotion: Boolean,
    isJustSentFlight: Boolean = false,
    sendFlightProgressProvider: () -> Float = { 1f },
    onBubbleTap: () -> Unit = {},
    onBubbleLongPress: () -> Unit = {},
    onImageClick: () -> Unit = onBubbleTap,
    onRetryClick: () -> Unit = {},
    onReplyPreviewClick: (String) -> Unit = {},
    onReactionClick: (String) -> Unit = {},
    onSenderProfileClick: (String) -> Unit = {},
    onBubbleBoundsChanged: ((Rect) -> Unit)? = null,
    isForegroundOverlay: Boolean = false
) {
    val msg = item.message
    val corners = rememberAnimatedBubbleCorners(
        isOwn = item.isOwn,
        position = item.position,
        topCornerDp = item.topCornerDp.dp,
        bottomCornerDp = item.bottomCornerDp.dp,
        reduceMotion = reduceMotion
    )
    val shape = remember(corners) { corners.toShape() }
    val hasAttachment = !msg.attachmentPath.isNullOrBlank() && !item.isDeleted
    val hasSenderHeader = item.showSenderName && !item.resolvedSenderName.isNullOrBlank()
    val hasQuote = !item.isDeleted && item.replyPreview != null
    val hasNoteReply = !item.isDeleted && msg.messageType == "note_reply" && !msg.noteSnapshotText.isNullOrBlank()
    val isDark = BuildXTheme.colors.isDark
    val haptic = LocalHapticFeedback.current
    val focusScale by animateFloatAsState(
        targetValue = if (isMenuOpen && !isForegroundOverlay && !reduceMotion) 1.02f else 1f,
        animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
        label = "bubble_focus_scale"
    )
    val highlightFraction by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
        label = "bubble_jump_highlight"
    )
    val highlightBorderAlpha = (highlightFraction * 0.72f).coerceIn(0f, 1f)
    val highlightOverlay = if (highlightFraction > 0.01f) {
        chatColors.replyHighlightOverlay.copy(
            alpha = (chatColors.replyHighlightOverlay.alpha * highlightFraction).coerceIn(0f, 1f)
        )
    } else {
        Color.Transparent
    }
    val maxTextWidth = (maxBubbleWidth - ChatDimens.BubblePaddingHorizontal * 2).coerceAtLeast(120.dp)

    Column(
        horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start,
        modifier = Modifier
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .then(
                if (onBubbleBoundsChanged != null) {
                    Modifier.onGloballyPositioned { coords ->
                        if (coords.isAttached) {
                            onBubbleBoundsChanged(coords.boundsInWindow())
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (reduceMotion || isForegroundOverlay) {
                    Modifier
                } else {
                    Modifier.animateContentSize(animationSpec = ChatMotion.layoutGestureSpring())
                }
            )
    ) {
        if (item.isDeleted) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .drawBubbleSurfaceZeroJank(
                        corners = corners,
                        targetBackgroundColor = chatColors.deletedPillBg,
                        borderColor = chatColors.deletedPillBorder,
                        borderWidth = 1.dp,
                        highlightOverlayColor = highlightOverlay
                    )
                    .then(
                        if (isForegroundOverlay) {
                            Modifier
                        } else {
                            Modifier.pointerInput(msg.id) {
                                detectTapGestures(
                                    onTap = { onBubbleTap() },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBubbleLongPress()
                                    }
                                )
                            }
                        }
                    )
                    .padding(
                        horizontal = ChatDimens.BubblePaddingHorizontal,
                        vertical = ChatDimens.BubblePaddingVertical
                    )
            ) {
                Text(
                    text = stringResource(R.string.dm_message_deleted),
                    color = chatColors.deletedPillText,
                    style = ChatTypography.messageDeleted
                )
            }
        } else if (hasAttachment) {
            val resolvedMaxMediaWidth = ChatDimens.MediaMaxWidth.coerceAtMost(maxBubbleWidth)
            val mediaMaxTextWidth = (resolvedMaxMediaWidth - ChatDimens.BubblePaddingHorizontal * 2).coerceAtLeast(120.dp)
            val bubbleBg = if (item.isOwn) chatColors.sentBubbleBg else chatColors.receivedBubbleBg
            val flightStartBg = if (item.isOwn && isJustSentFlight) chatColors.composerBg else null
            val textColor = if (item.isOwn) chatColors.sentBubbleText else chatColors.receivedBubbleText
            val metaTint = if (item.isOwn) chatColors.sentMetaText else chatColors.receivedMetaText
            val isStandaloneMedia = msg.content.isBlank() && !hasSenderHeader && !hasQuote && !hasNoteReply
            val containerShape = if (isStandaloneMedia) RoundedCornerShape(ChatDimens.MediaBubbleCorner) else shape
            val focusShadowModifier = if (isMenuOpen) {
                Modifier.softShadow(
                    color = BuildXTheme.colors.composerShadowColor,
                    cornerRadius = if (isStandaloneMedia) ChatDimens.MediaBubbleCorner else ChatDimens.BubbleCornerLarge,
                    blurRadius = 14.dp,
                    offsetY = 4.dp
                )
            } else {
                Modifier
            }

            Column(
                modifier = Modifier
                    .widthIn(max = resolvedMaxMediaWidth)
                    .width(IntrinsicSize.Max)
                    .then(focusShadowModifier)
                    .clip(containerShape)
                    .background(if (isStandaloneMedia) chatColors.receivedBubbleBg else bubbleBg)
                    .then(
                        when {
                            highlightBorderAlpha > 0.01f -> Modifier.border(
                                2.dp,
                                BuildXTheme.colors.primaryBlue.copy(alpha = highlightBorderAlpha),
                                containerShape
                            )
                            isMenuOpen -> Modifier.border(
                                ChatDimens.GlassSurfaceBorderWidth,
                                if (item.isOwn) Color.White.copy(alpha = 0.28f) else BuildXTheme.colors.subtleSurfaceBorder,
                                containerShape
                            )
                            isStandaloneMedia -> Modifier.border(
                                ChatDimens.GlassSurfaceBorderWidth,
                                chatColors.headerGlassBorder,
                                containerShape
                            )
                            else -> Modifier
                        }
                    )
                    .then(
                        if (isForegroundOverlay) {
                            Modifier
                        } else {
                            Modifier.pointerInput(msg.id, item.deliveryStatus) {
                                detectTapGestures(
                                    onTap = {
                                        if (item.deliveryStatus == ChatDeliveryStatus.FAILED) {
                                            onRetryClick()
                                        } else {
                                            onBubbleTap()
                                        }
                                    },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBubbleLongPress()
                                    }
                                )
                            }
                        }
                    )
            ) {
                if (hasSenderHeader || hasQuote || hasNoteReply) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = ChatDimens.BubblePaddingHorizontal,
                                end = ChatDimens.BubblePaddingHorizontal,
                                top = ChatDimens.BubblePaddingVertical,
                                bottom = 5.dp
                            )
                    ) {
                        if (hasSenderHeader) {
                            ChatGroupSenderHeader(
                                name = item.isolatedSenderName ?: item.resolvedSenderName!!,
                                senderId = msg.senderId,
                                isVerified = item.senderIsVerified,
                                handle = item.resolvedSenderHandle,
                                isDark = isDark,
                                onSenderProfileClick = onSenderProfileClick,
                                modifier = Modifier
                                    .widthIn(max = mediaMaxTextWidth)
                                    .padding(bottom = if (hasQuote || hasNoteReply) 4.dp else 2.dp)
                            )
                        }
                        if (hasQuote) {
                            ChatQuotedReplyCard(
                                reply = item.replyPreview!!,
                                isolatedSenderName = item.isolatedReplySenderName,
                                isolatedContent = item.isolatedReplyContent,
                                isOwn = item.isOwn,
                                chatColors = chatColors,
                                maxContentWidth = mediaMaxTextWidth,
                                onClick = { if (!isForegroundOverlay) onReplyPreviewClick(item.replyPreview.id) }
                            )
                        } else if (hasNoteReply) {
                            ChatNoteReplyPill(
                                snapshotText = msg.noteSnapshotText!!,
                                isOwn = item.isOwn,
                                chatColors = chatColors,
                                maxContentWidth = mediaMaxTextWidth
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ChatDimens.MediaMinHeight, max = ChatDimens.MediaMaxHeight)
                        .let { mod ->
                            if (isStandaloneMedia) mod else mod.padding(horizontal = 4.dp).clip(RoundedCornerShape(14.dp))
                        }
                        .then(
                            if (isForegroundOverlay) {
                                Modifier
                            } else {
                                Modifier.pointerInput(msg.id) {
                                    detectTapGestures(
                                        onTap = { onImageClick() },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onBubbleLongPress()
                                        }
                                    )
                                }
                            }
                        )
                ) {
                    AsyncImage(
                        model = msg.attachmentPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (msg.content.isBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.52f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            ChatBubbleMetaCluster(
                                timeText = item.formattedTime,
                                isEdited = item.isEdited,
                                isOwn = item.isOwn,
                                status = item.deliveryStatus,
                                tint = Color.White,
                                pendingTint = chatColors.sentCheckPendingTint,
                                deliveredTint = chatColors.sentCheckDeliveredTint,
                                readTint = chatColors.readCheckAccent,
                                failedTint = chatColors.failedAccent,
                                reduceMotion = reduceMotion
                            )
                        }
                    }
                }

                if (msg.content.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = ChatDimens.BubblePaddingHorizontal,
                                vertical = ChatDimens.BubblePaddingVertical
                            )
                    ) {
                        ChatInlineMetaTextBlock(
                            text = msg.content,
                            textColor = textColor,
                            isRtlContent = item.isRtlContent,
                            maxTextWidth = mediaMaxTextWidth,
                            timeText = item.formattedTime,
                            isEdited = item.isEdited,
                            isOwn = item.isOwn,
                            status = item.deliveryStatus,
                            metaTint = metaTint,
                            pendingTint = chatColors.sentCheckPendingTint,
                            deliveredTint = chatColors.sentCheckDeliveredTint,
                            readTint = chatColors.readCheckAccent,
                            failedTint = chatColors.failedAccent,
                            reduceMotion = reduceMotion
                        )
                    }
                } else if (!isStandaloneMedia) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Subtle flight tint overlay when photo caption bubble takes off
                if (flightStartBg != null) {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
        } else if (msg.content.isNotBlank()) {
            val bubbleBg = if (item.isOwn) chatColors.sentBubbleBg else chatColors.receivedBubbleBg
            val flightStartBg = if (item.isOwn && isJustSentFlight) chatColors.composerBg else null
            val textColor = if (item.isOwn) chatColors.sentBubbleText else chatColors.receivedBubbleText
            val metaTint = if (item.isOwn) chatColors.sentMetaText else chatColors.receivedMetaText
            val focusShadowModifier = if (isMenuOpen) {
                Modifier.softShadow(
                    color = BuildXTheme.colors.composerShadowColor,
                    cornerRadius = ChatDimens.BubbleCornerLarge,
                    blurRadius = 14.dp,
                    offsetY = 4.dp
                )
            } else {
                Modifier
            }
            val surfaceBorderColor = when {
                highlightBorderAlpha > 0.01f -> BuildXTheme.colors.primaryBlue.copy(alpha = highlightBorderAlpha)
                isMenuOpen -> if (item.isOwn) Color.White.copy(alpha = 0.28f) else BuildXTheme.colors.subtleSurfaceBorder
                else -> null
            }
            val surfaceBorderWidth = if (highlightBorderAlpha > 0.01f) 2.dp else ChatDimens.GlassSurfaceBorderWidth

            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .then(focusShadowModifier)
                    .drawBubbleSurfaceZeroJank(
                        corners = corners,
                        targetBackgroundColor = bubbleBg,
                        flightStartBackgroundColor = flightStartBg,
                        flightProgressProvider = sendFlightProgressProvider,
                        borderColor = surfaceBorderColor,
                        borderWidth = surfaceBorderWidth,
                        highlightOverlayColor = highlightOverlay
                    )
                    .then(
                        if (isForegroundOverlay) {
                            Modifier
                        } else {
                            Modifier.pointerInput(msg.id, item.deliveryStatus) {
                                detectTapGestures(
                                    onTap = {
                                        if (item.deliveryStatus == ChatDeliveryStatus.FAILED) {
                                            onRetryClick()
                                        } else {
                                            onBubbleTap()
                                        }
                                    },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBubbleLongPress()
                                    }
                                )
                            }
                        }
                    )
                    .padding(
                        horizontal = ChatDimens.BubblePaddingHorizontal,
                        vertical = ChatDimens.BubblePaddingVertical
                    )
            ) {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    if (hasSenderHeader) {
                        ChatGroupSenderHeader(
                            name = item.isolatedSenderName ?: item.resolvedSenderName!!,
                            senderId = msg.senderId,
                            isVerified = item.senderIsVerified,
                            handle = item.resolvedSenderHandle,
                            isDark = isDark,
                            onSenderProfileClick = { if (!isForegroundOverlay) onSenderProfileClick(it) },
                            modifier = Modifier
                                .widthIn(max = maxTextWidth)
                                .padding(bottom = if (hasQuote || hasNoteReply) 4.dp else 2.dp)
                        )
                    }
                    if (hasQuote) {
                        ChatQuotedReplyCard(
                            reply = item.replyPreview!!,
                            isolatedSenderName = item.isolatedReplySenderName,
                            isolatedContent = item.isolatedReplyContent,
                            isOwn = item.isOwn,
                            chatColors = chatColors,
                            maxContentWidth = maxTextWidth,
                            onClick = { if (!isForegroundOverlay) onReplyPreviewClick(item.replyPreview.id) }
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                    } else if (hasNoteReply) {
                        ChatNoteReplyPill(
                            snapshotText = msg.noteSnapshotText!!,
                            isOwn = item.isOwn,
                            chatColors = chatColors,
                            maxContentWidth = maxTextWidth
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                    }

                    ChatInlineMetaTextBlock(
                        text = msg.content,
                        textColor = textColor,
                        isRtlContent = item.isRtlContent,
                        maxTextWidth = maxTextWidth,
                        timeText = item.formattedTime,
                        isEdited = item.isEdited,
                        isOwn = item.isOwn,
                        status = item.deliveryStatus,
                        metaTint = metaTint,
                        pendingTint = chatColors.sentCheckPendingTint,
                        deliveredTint = chatColors.sentCheckDeliveredTint,
                        readTint = chatColors.readCheckAccent,
                        failedTint = chatColors.failedAccent,
                        reduceMotion = reduceMotion
                    )
                }
            }
        }

        if (item.reactions.isNotEmpty() && !item.isDeleted) {
            ChatBubbleReactionsRow(
                reactions = item.reactions,
                myReaction = item.myReaction,
                isOwn = item.isOwn,
                maxWidth = maxBubbleWidth,
                chatColors = chatColors,
                onReactionClick = { emoji ->
                    if (!isForegroundOverlay) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReactionClick(emoji)
                    }
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (item.deliveryStatus == ChatDeliveryStatus.FAILED && !item.isDeleted && !isForegroundOverlay) {
            Text(
                text = stringResource(R.string.dm_tap_to_retry),
                color = chatColors.failedAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 2.dp, end = 6.dp)
                    .clickable(onClick = onRetryClick)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubbleReactionsRow(
    reactions: Map<String, Int>,
    myReaction: String?,
    isOwn: Boolean,
    maxWidth: Dp,
    chatColors: ChatColors,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (isOwn) Alignment.End else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.widthIn(max = maxWidth)
    ) {
        reactions.entries.forEach { (emoji, count) ->
            val isMine = myReaction == emoji
            val chipBg = if (isMine) {
                colors.primaryBlue.copy(alpha = if (colors.isDark) 0.28f else 0.14f)
            } else {
                chatColors.reactionPillBg
            }
            val chipBorder = if (isMine) {
                colors.primaryBlue.copy(alpha = 0.65f)
            } else {
                chatColors.reactionPillBorder
            }
            val countColor = if (isMine) colors.primaryBlue else colors.primaryText
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(chipBg)
                    .border(0.8.dp, chipBorder, RoundedCornerShape(50))
                    .pressScaleClickable { onReactionClick(emoji) }
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = emoji,
                    fontSize = 13.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = count.toString(),
                    color = countColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BuildXFontFamily
                )
            }
        }
    }
}

@Composable
private fun ChatGroupSenderHeader(
    name: String,
    senderId: String,
    isVerified: Boolean,
    handle: String?,
    isDark: Boolean,
    onSenderProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.then(
            if (!handle.isNullOrBlank()) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSenderProfileClick(handle) }
                )
            } else {
                Modifier
            }
        )
    ) {
        Text(
            text = name,
            color = senderNameColor(senderId, isDark),
            style = ChatTypography.senderName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (isVerified) {
            VerifiedBadge(size = 13.dp)
        }
    }
}

/**
 * Custom Compose [Layout] pairing message text + metadata cluster (time + optional status icon).
 *
 * Uses [rememberTextMeasurer] for synchronous measurement and an explicit [MeasurePolicy]
 * supporting `IntrinsicSize.Max` parent columns, placing metadata cleanly at the bottom-right
 * edge of the bubble across both LTR and RTL content without overlapping text.
 */
@Composable
private fun ChatInlineMetaTextBlock(
    text: String,
    textColor: Color,
    isRtlContent: Boolean,
    maxTextWidth: androidx.compose.ui.unit.Dp,
    timeText: String,
    isEdited: Boolean,
    isOwn: Boolean,
    status: ChatDeliveryStatus,
    metaTint: Color,
    pendingTint: Color,
    deliveredTint: Color,
    readTint: Color,
    failedTint: Color,
    reduceMotion: Boolean
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(textColor, isRtlContent) {
        ChatTypography.messageBody.copy(
            color = textColor,
            textAlign = if (isRtlContent) TextAlign.Right else TextAlign.Left,
            textDirection = TextDirection.ContentOrLtr
        )
    }

    val maxWidthPx = with(density) { maxTextWidth.roundToPx().coerceAtLeast(1) }
    val inlineGapPx = with(density) { ChatDimens.InlineMetaStartGap.roundToPx() }
    val wrapTopGapPx = with(density) { ChatDimens.WrapMetaTopGap.roundToPx() }

    val layoutResult = remember(text, textStyle, maxWidthPx) {
        textMeasurer.measure(
            text = text,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx)
        )
    }

    val measurePolicy = remember(layoutResult, maxWidthPx, inlineGapPx, wrapTopGapPx, isRtlContent) {
        object : MeasurePolicy {
            private fun effectiveLastLineWidth(textWidth: Int): Int {
                val lastLineIndex = (layoutResult.lineCount - 1).coerceAtLeast(0)
                val measuredLastLinePx = if (layoutResult.lineCount > 0) {
                    ceil((layoutResult.getLineRight(lastLineIndex) - layoutResult.getLineLeft(lastLineIndex)).toDouble())
                        .toInt()
                        .coerceAtLeast(0)
                } else {
                    textWidth
                }
                return if (layoutResult.lineCount <= 1) {
                    textWidth
                } else {
                    measuredLastLinePx.coerceAtMost(textWidth)
                }
            }

            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult {
                val childMaxWidth = constraints.maxWidth.coerceAtMost(maxWidthPx).coerceAtLeast(1)
                val looseConstraints = Constraints(minWidth = 0, maxWidth = childMaxWidth)
                val textPlaceable = measurables[0].measure(looseConstraints)
                val metaPlaceable = measurables[1].measure(looseConstraints)

                val effectiveLastLinePx = effectiveLastLineWidth(textPlaceable.width)
                val spec = ChatMessageMapper.computeBubbleMetaPlacement(
                    textWidthPx = textPlaceable.width,
                    textHeightPx = textPlaceable.height,
                    lineCount = layoutResult.lineCount.coerceAtLeast(1),
                    lastLineWidthPx = effectiveLastLinePx,
                    metaWidthPx = metaPlaceable.width,
                    metaHeightPx = metaPlaceable.height,
                    maxWidthPx = childMaxWidth,
                    inlineGapPx = inlineGapPx,
                    wrapTopGapPx = wrapTopGapPx,
                    isRtlContent = isRtlContent
                )

                val minW = constraints.minWidth.coerceAtMost(constraints.maxWidth)
                val boxWidth = spec.widthPx.coerceIn(minW, constraints.maxWidth)
                val minH = constraints.minHeight.coerceAtMost(constraints.maxHeight)
                val boxHeight = spec.heightPx.coerceIn(minH, constraints.maxHeight)

                val finalMetaX = (boxWidth - metaPlaceable.width).coerceAtLeast(0)
                val finalTextX = if (isRtlContent) {
                    if (spec.isInline) {
                        (finalMetaX - inlineGapPx - textPlaceable.width).coerceAtLeast(0)
                    } else {
                        (boxWidth - textPlaceable.width).coerceAtLeast(0)
                    }
                } else {
                    0
                }

                return layout(boxWidth, boxHeight) {
                    textPlaceable.place(finalTextX, spec.textY)
                    metaPlaceable.place(finalMetaX, spec.metaY)
                }
            }

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int {
                val metaW = measurables[1].maxIntrinsicWidth(height).coerceAtMost(maxWidthPx)
                val metaH = measurables[1].maxIntrinsicHeight(metaW.coerceAtLeast(1))
                val textW = layoutResult.size.width.coerceAtMost(maxWidthPx)
                val textH = layoutResult.size.height
                return ChatMessageMapper.computeBubbleMetaPlacement(
                    textWidthPx = textW,
                    textHeightPx = textH,
                    lineCount = layoutResult.lineCount.coerceAtLeast(1),
                    lastLineWidthPx = effectiveLastLineWidth(textW),
                    metaWidthPx = metaW,
                    metaHeightPx = metaH,
                    maxWidthPx = maxWidthPx,
                    inlineGapPx = inlineGapPx,
                    wrapTopGapPx = wrapTopGapPx,
                    isRtlContent = isRtlContent
                ).widthPx
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = maxIntrinsicWidth(measurables, height)

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int {
                val clampedW = width.coerceAtMost(maxWidthPx).coerceAtLeast(1)
                val textH = measurables[0].maxIntrinsicHeight(clampedW)
                val metaW = measurables[1].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(clampedW)
                val metaH = measurables[1].maxIntrinsicHeight(metaW.coerceAtLeast(1))
                val textW = layoutResult.size.width.coerceAtMost(clampedW)
                return ChatMessageMapper.computeBubbleMetaPlacement(
                    textWidthPx = textW,
                    textHeightPx = textH,
                    lineCount = layoutResult.lineCount.coerceAtLeast(1),
                    lastLineWidthPx = effectiveLastLineWidth(textW),
                    metaWidthPx = metaW,
                    metaHeightPx = metaH,
                    maxWidthPx = clampedW,
                    inlineGapPx = inlineGapPx,
                    wrapTopGapPx = wrapTopGapPx,
                    isRtlContent = isRtlContent
                ).heightPx
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int = maxIntrinsicHeight(measurables, width)
        }
    }

    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxTextWidth),
        content = {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (isRtlContent) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Text(
                    text = text,
                    style = textStyle
                )
            }
            ChatBubbleMetaCluster(
                timeText = timeText,
                isEdited = isEdited,
                isOwn = isOwn,
                status = status,
                tint = metaTint,
                pendingTint = pendingTint,
                deliveredTint = deliveredTint,
                readTint = readTint,
                failedTint = failedTint,
                reduceMotion = reduceMotion
            )
        },
        measurePolicy = measurePolicy
    )
}

/**
 * Isolated LTR bidi container with tabular digits so timestamps ("7:07 PM") and check icons
 * never invert or reorder inside Arabic RTL bubbles.
 */
@Composable
private fun ChatBubbleMetaCluster(
    timeText: String,
    isEdited: Boolean,
    isOwn: Boolean,
    status: ChatDeliveryStatus,
    tint: Color,
    pendingTint: Color,
    deliveredTint: Color,
    readTint: Color,
    failedTint: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChatDimens.MetaGap)
        ) {
            if (isEdited) {
                Text(
                    text = stringResource(R.string.dm_edited),
                    style = ChatTypography.metaTimestamp.copy(
                        color = tint,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
            if (timeText.isNotBlank()) {
                Text(
                    text = timeText,
                    style = ChatTypography.metaTimestamp.copy(color = tint)
                )
            }
            if (isOwn) {
                ChatStatusIcon(
                    status = status,
                    pendingTint = pendingTint,
                    deliveredTint = deliveredTint,
                    readTint = readTint,
                    failedTint = failedTint,
                    reduceMotion = reduceMotion
                )
            }
        }
    }
}

/**
 * Hardware-accelerated direct Canvas stroke renderer for delivery status (`SENDING` clock,
 * `SENT`/`DELIVERED` single checkmark ✓, `READ` double checkmark ✓✓).
 * Animates stroke draw progression and single->double shift with Telegram spring physics.
 */
@Composable
private fun ChatStatusIcon(
    status: ChatDeliveryStatus,
    pendingTint: Color,
    deliveredTint: Color,
    readTint: Color,
    failedTint: Color,
    reduceMotion: Boolean
) {
    var previousStatus by remember { mutableStateOf(status) }
    val checkDrawProgress = remember {
        Animatable(if (status == ChatDeliveryStatus.SENDING) 0f else 1f)
    }
    val doubleCheckProgress = remember {
        Animatable(if (status == ChatDeliveryStatus.READ) 1f else 0f)
    }
    val popScale = remember { Animatable(1f) }

    LaunchedEffect(status, reduceMotion) {
        if (reduceMotion) {
            previousStatus = status
            checkDrawProgress.snapTo(if (status == ChatDeliveryStatus.SENDING) 0f else 1f)
            doubleCheckProgress.snapTo(if (status == ChatDeliveryStatus.READ) 1f else 0f)
            popScale.snapTo(1f)
            return@LaunchedEffect
        }
        if (status == previousStatus) return@LaunchedEffect
        val prev = previousStatus
        previousStatus = status

        when (status) {
            ChatDeliveryStatus.SENDING -> {
                checkDrawProgress.snapTo(0f)
                doubleCheckProgress.snapTo(0f)
                popScale.snapTo(1f)
            }
            ChatDeliveryStatus.SENT,
            ChatDeliveryStatus.DELIVERED -> {
                doubleCheckProgress.animateTo(0f, ChatMotion.interactiveSpring())
                if (prev == ChatDeliveryStatus.SENDING) {
                    checkDrawProgress.snapTo(0f)
                    coroutineScope {
                        launch { checkDrawProgress.animateTo(1f, ChatMotion.interactiveSpring()) }
                        launch {
                            popScale.snapTo(ChatMotion.StatusStartScale)
                            popScale.animateTo(1f, ChatMotion.interactiveSpring())
                        }
                    }
                } else {
                    checkDrawProgress.snapTo(1f)
                }
            }
            ChatDeliveryStatus.READ -> {
                checkDrawProgress.snapTo(1f)
                if (prev == ChatDeliveryStatus.SENDING) {
                    doubleCheckProgress.snapTo(0f)
                }
                coroutineScope {
                    launch { doubleCheckProgress.animateTo(1f, ChatMotion.interactiveSpring()) }
                    launch {
                        popScale.snapTo(0.88f)
                        popScale.animateTo(1f, ChatMotion.interactiveSpring())
                    }
                }
            }
            ChatDeliveryStatus.FAILED -> {
                popScale.snapTo(ChatMotion.StatusStartScale)
                popScale.animateTo(1f, ChatMotion.interactiveSpring())
            }
        }
    }

    val checkColor by animateColorAsState(
        targetValue = when (status) {
            ChatDeliveryStatus.SENDING -> pendingTint
            ChatDeliveryStatus.SENT,
            ChatDeliveryStatus.DELIVERED -> deliveredTint
            ChatDeliveryStatus.READ -> readTint
            ChatDeliveryStatus.FAILED -> failedTint
        },
        animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
        label = "status_check_tint"
    )

    Box(
        modifier = Modifier
            .size(15.dp)
            .graphicsLayer {
                val s = popScale.value
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center
    ) {
        if (status == ChatDeliveryStatus.FAILED) {
            Box(
                modifier = Modifier
                    .size(ChatDimens.StatusIconSize)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_dm_error),
                    contentDescription = status.name,
                    tint = failedTint,
                    modifier = Modifier.size(ChatDimens.StatusIconSize)
                )
            }
        } else if (status == ChatDeliveryStatus.SENDING) {
            val clockRotation = if (reduceMotion) {
                0f
            } else {
                val infinite = rememberInfiniteTransition(label = "sending_clock")
                val angle by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1400, easing = ChatMotion.FastOutCubicBezier),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "clock_sweep"
                )
                angle
            }
            Canvas(modifier = Modifier.size(13.dp)) {
                val scale = size.minDimension / 24f
                val strokePx = 1.85f * scale
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(
                    color = pendingTint,
                    radius = 8.5f * scale,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokePx)
                )
                drawLine(
                    color = pendingTint,
                    start = Offset(cx, cy),
                    end = Offset(cx + 3.4f * scale, cy + 2.2f * scale),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
                rotate(degrees = clockRotation, pivot = Offset(cx, cy)) {
                    drawLine(
                        color = pendingTint,
                        start = Offset(cx, cy),
                        end = Offset(cx, cy - 5.2f * scale),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }
        } else {
            val strokePath = remember { Path() }
            Canvas(modifier = Modifier.size(15.dp)) {
                val scale = size.minDimension / 24f
                val strokePx = 1.85f * scale
                val shiftX = 3.25f * doubleCheckProgress.value.coerceIn(0f, 1f)
                val firstDraw = checkDrawProgress.value.coerceIn(0f, 1f)
                val secondDraw = doubleCheckProgress.value.coerceIn(0f, 1f)

                if (firstDraw > 0.01f) {
                    drawProgressiveCheck(
                        path = strokePath,
                        p0 = Offset((5.0f - shiftX) * scale, 12.5f * scale),
                        p1 = Offset((9.75f - shiftX) * scale, 17.25f * scale),
                        p2 = Offset((19.25f - shiftX) * scale, 6.75f * scale),
                        progress = firstDraw,
                        color = checkColor,
                        strokeWidthPx = strokePx
                    )
                }
                if (secondDraw > 0.01f) {
                    drawProgressiveCheck(
                        path = strokePath,
                        p0 = Offset(9.5f * scale, 14.5f * scale),
                        p1 = Offset(12.25f * scale, 17.25f * scale),
                        p2 = Offset(21.75f * scale, 6.75f * scale),
                        progress = secondDraw,
                        color = checkColor,
                        strokeWidthPx = strokePx
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProgressiveCheck(
    path: Path,
    p0: Offset,
    p1: Offset,
    p2: Offset,
    progress: Float,
    color: Color,
    strokeWidthPx: Float
) {
    val seg1Dx = p1.x - p0.x
    val seg1Dy = p1.y - p0.y
    val len1 = kotlin.math.hypot(seg1Dx.toDouble(), seg1Dy.toDouble()).toFloat().coerceAtLeast(0.001f)
    val seg2Dx = p2.x - p1.x
    val seg2Dy = p2.y - p1.y
    val len2 = kotlin.math.hypot(seg2Dx.toDouble(), seg2Dy.toDouble()).toFloat().coerceAtLeast(0.001f)
    val totalLen = len1 + len2
    val targetLen = totalLen * progress.coerceIn(0f, 1f)

    path.reset()
    path.moveTo(p0.x, p0.y)
    if (targetLen <= len1) {
        val t = (targetLen / len1).coerceIn(0f, 1f)
        path.lineTo(p0.x + seg1Dx * t, p0.y + seg1Dy * t)
    } else {
        path.lineTo(p1.x, p1.y)
        val t = ((targetLen - len1) / len2).coerceIn(0f, 1f)
        path.lineTo(p1.x + seg2Dx * t, p1.y + seg2Dy * t)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
private fun ChatQuotedReplyCard(
    reply: DmReplyPreview,
    isOwn: Boolean,
    chatColors: ChatColors,
    maxContentWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    isolatedSenderName: String? = null,
    isolatedContent: String? = null
) {
    val isDark = BuildXTheme.colors.isDark
    val incomingAccent = senderNameColor(reply.senderId, isDark)
    val bgColor = if (isOwn) chatColors.quotedBgSent else incomingAccent.copy(alpha = if (isDark) 0.14f else 0.10f)
    val barColor = if (isOwn) Color.White else incomingAccent
    val nameColor = if (isOwn) Color.White else incomingAccent
    val bodyColor = if (isOwn) {
        Color.White.copy(alpha = 0.88f)
    } else {
        BuildXTheme.colors.primaryText.copy(alpha = 0.84f)
    }
    val quoteShape = RoundedCornerShape(ChatDimens.QuoteCornerSmall)
    val resolvedSender = isolatedSenderName
        ?: reply.senderDisplayName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.dm_you)
    val resolvedBody = if (reply.isDeleted) {
        stringResource(R.string.dm_message_deleted)
    } else {
        (isolatedContent ?: reply.content).ifBlank { stringResource(R.string.dm_media_attachment) }
    }

    Row(
        modifier = Modifier
            .widthIn(max = maxContentWidth)
            .fillMaxWidth()
            .heightIn(min = QuoteMinHeight)
            .clip(quoteShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(ChatDimens.QuoteAccentBarWidth)
                .fillMaxHeight()
                .heightIn(min = QuoteMinHeight)
                .background(barColor)
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = resolvedSender,
                style = ChatTypography.quoteSender.copy(color = nameColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = resolvedBody,
                style = ChatTypography.quoteBody.copy(
                    color = if (reply.isDeleted) BuildXTheme.colors.secondaryText else bodyColor,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontStyle = if (reply.isDeleted) FontStyle.Italic else FontStyle.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatNoteReplyPill(
    snapshotText: String,
    isOwn: Boolean,
    chatColors: ChatColors,
    maxContentWidth: androidx.compose.ui.unit.Dp
) {
    val bgColor = if (isOwn) chatColors.quotedBgSent else chatColors.quotedBgReceived
    val textColor = if (isOwn) Color.White.copy(alpha = 0.90f) else chatColors.receivedBubbleText.copy(alpha = 0.80f)
    Box(
        modifier = Modifier
            .widthIn(max = maxContentWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ChatDimens.QuoteCornerSmall))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "${stringResource(R.string.dm_replied_to_note)}: \"$snapshotText\"",
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(textDirection = TextDirection.ContentOrLtr),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChatGroupSenderAvatar(
    avatarUrl: String?,
    displayName: String?,
    chatColors: ChatColors,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(BubbleGroupAvatarSize)
            .clip(CircleShape)
            .background(chatColors.receivedBubbleBg)
            .border(ChatDimens.GlassSurfaceBorderWidth, chatColors.headerGlassBorder, CircleShape)
            .let { mod ->
                if (onClick != null) mod.clickable(onClick = onClick) else mod
            },
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = displayName?.firstOrNull()?.uppercase() ?: "",
                color = BuildXTheme.colors.primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChatSystemPill(
    text: String,
    chatColors: ChatColors,
    verticalPadding: Dp,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    val pillShape = RoundedCornerShape(ChatDimens.SystemPillCornerPercent)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(ChatDimens.SystemPillHeight)
                .widthIn(max = 300.dp)
                .clip(pillShape)
                .background(chatColors.systemPillBg)
                .border(ChatDimens.GlassSurfaceBorderWidth, chatColors.systemPillBorder, pillShape)
                .padding(horizontal = ChatDimens.SystemPillPaddingH),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = ChatTypography.systemPill.copy(color = chatColors.systemPillText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChatSystemEventPill(
    text: String,
    chatColors: ChatColors,
    modifier: Modifier = Modifier
) {
    ChatSystemPill(
        text = text,
        chatColors = chatColors,
        verticalPadding = 6.dp,
        modifier = modifier
    )
}

@Composable
fun ChatDateSeparatorChip(
    createdAtIso: String,
    chatColors: ChatColors,
    modifier: Modifier = Modifier,
    todayOverride: java.time.LocalDate? = null
) {
    val label = ChatMessageMapper.formatDayLabel(
        iso = createdAtIso,
        todayLabel = stringResource(R.string.dm_today),
        yesterdayLabel = stringResource(R.string.dm_yesterday),
        todayOverride = todayOverride
    )
    if (label.isBlank()) return

    ChatSystemPill(
        text = label,
        chatColors = chatColors,
        verticalPadding = 8.dp,
        modifier = modifier
    )
}

/**
 * Compact shared profile intro card at top of 1-on-1 conversations:
 * - Wrap-content height, max width ~280dp, zero dead whitespace
 * - Unclipped circular avatar with placeholder, name, @handle, followers · posts, subtle caption
 */
@Composable
fun ChatCompactPeerIntroCard(
    intro: DmPeerIntro,
    chatColors: ChatColors,
    onViewProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val followYear = remember(intro.followingSince) {
        intro.followingSince?.let { Regex("""\d{4}""").find(it)?.value }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ChatDimens.ProfileCardMaxWidth)
                .softShadow(
                    color = chatColors.headerShadow,
                    cornerRadius = ChatDimens.ProfileCardCorner,
                    blurRadius = 12.dp,
                    offsetY = 2.dp
                )
                .clip(RoundedCornerShape(ChatDimens.ProfileCardCorner))
                .background(chatColors.composerBg)
                .border(0.8.dp, chatColors.headerGlassBorder, RoundedCornerShape(ChatDimens.ProfileCardCorner))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(ChatDimens.ProfileCardAvatarSize)
                    .clip(CircleShape)
                    .background(chatColors.receivedBubbleBg)
                    .border(0.8.dp, chatColors.headerGlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!intro.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = intro.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = intro.displayName?.takeIf { it.isNotBlank() } ?: intro.handle.orEmpty(),
                    color = colors.primaryText,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (intro.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                }
            }

            if (!intro.handle.isNullOrBlank()) {
                Text(
                    text = "@${intro.handle}",
                    color = colors.secondaryText,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.dm_followers_posts,
                    intro.followersCount.toString(),
                    intro.postsCount.toString()
                ),
                color = colors.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!followYear.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.dm_following_since_year, followYear),
                    color = colors.secondaryText.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(chatColors.receivedBubbleBg)
                    .border(0.8.dp, chatColors.headerGlassBorder, RoundedCornerShape(18.dp))
                    .pressScaleClickable(onClick = onViewProfileClick)
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_view_profile),
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ChatSwipeToReplyContainer(
    enabled: Boolean,
    chatColors: ChatColors,
    reduceMotion: Boolean = false,
    onSwipeToReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = BuildXTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetAnim = remember { Animatable(0f) }
    var rawDragPx by remember { mutableFloatStateOf(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val maxPx = with(density) { ChatDimens.SwipeReplyMaxOffsetDp.toPx() }
    val triggerPx = with(density) { ChatDimens.SwipeReplyTriggerDp.toPx() }

    val isThresholdCrossed by remember(triggerPx) {
        derivedStateOf { offsetAnim.value >= triggerPx }
    }
    val showReplyBadge by remember {
        derivedStateOf { offsetAnim.value > 2f }
    }
    val thresholdScaleBoost = remember { Animatable(0f) }
    val thresholdRotationKick = remember { Animatable(0f) }

    LaunchedEffect(isThresholdCrossed, reduceMotion) {
        if (reduceMotion) {
            thresholdScaleBoost.snapTo(if (isThresholdCrossed) 0.08f else 0f)
            thresholdRotationKick.snapTo(0f)
            return@LaunchedEffect
        }
        if (isThresholdCrossed) {
            coroutineScope {
                launch {
                    thresholdScaleBoost.snapTo(-0.06f)
                    thresholdScaleBoost.animateTo(
                        targetValue = 0.08f,
                        animationSpec = ChatMotion.interactiveSpring()
                    )
                }
                launch {
                    thresholdRotationKick.snapTo(-20f)
                    thresholdRotationKick.animateTo(
                        targetValue = 0f,
                        animationSpec = ChatMotion.interactiveSpring()
                    )
                }
            }
        } else {
            coroutineScope {
                launch { thresholdScaleBoost.animateTo(0f, ChatMotion.interactiveSpring()) }
                launch { thresholdRotationKick.animateTo(0f, ChatMotion.interactiveSpring()) }
            }
        }
    }

    val badgeBgColor by animateColorAsState(
        targetValue = if (isThresholdCrossed) colors.primaryBlue else chatColors.scrollToBottomBg,
        animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
        label = "swipe_reply_badge_bg"
    )
    val badgeIconTint by animateColorAsState(
        targetValue = if (isThresholdCrossed) Color.White else colors.primaryBlue,
        animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
        label = "swipe_reply_badge_tint"
    )

    val draggableState = rememberDraggableState { delta ->
        if (!enabled) return@rememberDraggableState
        val direction = if (isRtl) -1f else 1f
        rawDragPx = (rawDragPx + (delta * direction)).coerceAtLeast(0f)

        // Elastic rubber-band resistance past the 56.dp trigger threshold up to 96.dp max
        val visualOffset = if (rawDragPx <= triggerPx) {
            rawDragPx
        } else {
            val excess = rawDragPx - triggerPx
            val maxExtra = (maxPx - triggerPx).coerceAtLeast(1f)
            val resisted = maxExtra * (1f - (1f / (1f + (excess / (triggerPx * 1.35f)))))
            (triggerPx + resisted).coerceAtMost(maxPx)
        }

        if (visualOffset >= triggerPx && !hasTriggeredHaptic) {
            hasTriggeredHaptic = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (visualOffset < triggerPx * 0.85f && hasTriggeredHaptic) {
            hasTriggeredHaptic = false
        }

        scope.launch {
            offsetAnim.snapTo(visualOffset)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = {
                    val shouldReply = offsetAnim.value >= triggerPx || rawDragPx >= triggerPx
                    rawDragPx = 0f
                    hasTriggeredHaptic = false
                    if (shouldReply) {
                        onSwipeToReply()
                    }
                    scope.launch {
                        if (reduceMotion) {
                            offsetAnim.snapTo(0f)
                        } else {
                            offsetAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = ChatMotion.layoutGestureSpring()
                            )
                        }
                    }
                }
            )
    ) {
        if (showReplyBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .graphicsLayer {
                        val current = offsetAnim.value
                        val progress = (current / triggerPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val badgeScale = (0.55f + (0.45f * progress) + thresholdScaleBoost.value).coerceAtLeast(0.4f)
                        val dragRotation = (1f - progress) * -28f
                        alpha = progress
                        scaleX = badgeScale
                        scaleY = badgeScale
                        rotationZ = dragRotation + thresholdRotationKick.value
                    }
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(badgeBgColor)
                    .border(
                        width = ChatDimens.GlassSurfaceBorderWidth,
                        color = if (isThresholdCrossed) Color.Transparent else chatColors.scrollToBottomBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_tg_reply),
                    contentDescription = stringResource(R.string.dm_action_reply),
                    tint = badgeIconTint,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    val current = offsetAnim.value
                    translationX = if (isRtl) -current else current
                }
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

@Immutable
data class FocusedMessageMenuState(
    val item: ChatListItem.MessageItem,
    val anchorBoundsInRoot: Rect,
    val maxBubbleWidth: Dp,
    val menuItems: List<ActionMenuItem>
)

private val DefaultQuickReactions = listOf("👍", "❤️", "🔥", "😍", "👏", "🤔")

/**
 * Foreground-sharp Telegram long-press overlay:
 * Renders the floating Emoji Reaction Pill Bar (`44.dp`, `22.dp` radius) above the selected
 * razor-sharp bubble (`1.02f` focus scale) and the compact `210.dp` left-icon context menu
 * (including `"Seen"` read receipt status for read outgoing messages) below it, completely
 * immune to the background blur applied to the chat scaffold behind it.
 */
@Composable
fun ChatFocusedMessageMenuOverlay(
    state: FocusedMessageMenuState?,
    chatColors: ChatColors,
    reduceMotion: Boolean,
    onSelectReaction: (messageId: String, emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
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
                    ChatMotion.interactiveSpring()
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

    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val pillHeightPx = with(density) { ChatDimens.ReactionPillHeight.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val edgeMarginPx = with(density) { 12.dp.toPx() }
    val horizontalMarginPx = with(density) { 16.dp.toPx() }
    val menuWidthPx = with(density) { ChatDimens.ContextMenuWidth.toPx() }

    var rootWidthPx by remember { mutableIntStateOf(0) }
    var rootHeightPx by remember { mutableIntStateOf(0) }
    var menuHeightPx by remember { mutableIntStateOf(with(density) { 240.dp.toPx() }.roundToInt()) }
    var pillWidthPx by remember { mutableIntStateOf(with(density) { 276.dp.toPx() }.roundToInt()) }

    val anchor = activeState.anchorBoundsInRoot
    val isOwn = activeState.item.isOwn

    val topSafePx = statusBarTopPx + edgeMarginPx + pillHeightPx + gapPx
    val bottomSafePx = (rootHeightPx - navBarBottomPx - edgeMarginPx - menuHeightPx - gapPx - anchor.height)
        .coerceAtLeast(topSafePx)
    val targetBubbleTopPx = if (rootHeightPx > 0) {
        anchor.top.coerceIn(topSafePx, bottomSafePx)
    } else {
        anchor.top
    }

    val bubbleAlignEnd = isOwn xor isRtl
    val pillX = if (bubbleAlignEnd) {
        (anchor.right - pillWidthPx).coerceIn(
            horizontalMarginPx,
            (rootWidthPx - pillWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
        )
    } else {
        anchor.left.coerceIn(
            horizontalMarginPx,
            (rootWidthPx - pillWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
        )
    }
    val menuX = if (bubbleAlignEnd) {
        (anchor.right - menuWidthPx).coerceIn(
            horizontalMarginPx,
            (rootWidthPx - menuWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
        )
    } else {
        anchor.left.coerceIn(
            horizontalMarginPx,
            (rootWidthPx - menuWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
        )
    }

    val seenLabel = remember(activeState.item.isOwn, activeState.item.isSeenByPeer, activeState.item.isDeleted, activeState.item.isGroupChat, activeState.item.seenByNames) {
        if (activeState.item.isOwn && activeState.item.isSeenByPeer && !activeState.item.isDeleted) {
            if (activeState.item.isGroupChat && activeState.item.seenByNames.isNotEmpty()) {
                "Seen · ${activeState.item.seenByNames.take(2).joinToString(", ")}"
            } else {
                "Seen"
            }
        } else {
            null
        }
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
        // 1. Protective Backdrop Dim Scrim (paired with 1-pass RenderEffect blur on background scaffold)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = focusAnim.value }
                .background(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Color.Black.copy(alpha = 0.24f)
                    } else {
                        Color.Black.copy(alpha = 0.44f)
                    }
                )
        )

        // 2. Foreground-Sharp Selected Bubble (razor-sharp, no blur, 1.02f focus lift)
        Box(
            modifier = Modifier
                .offset {
                    val p = focusAnim.value
                    val currentY = anchor.top + (targetBubbleTopPx - anchor.top) * p
                    IntOffset(anchor.left.roundToInt(), currentY.roundToInt())
                }
                .graphicsLayer {
                    val p = focusAnim.value
                    val s = 1f + 0.02f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (bubbleAlignEnd) 1f else 0f, 0.5f)
                }
        ) {
            ChatBubbleCard(
                item = activeState.item,
                chatColors = chatColors,
                maxBubbleWidth = activeState.maxBubbleWidth,
                isMenuOpen = true,
                isHighlighted = false,
                reduceMotion = reduceMotion,
                isForegroundOverlay = true
            )
        }

        // 3. Floating Emoji Reaction Pill Bar ABOVE the Sharp Bubble
        if (!activeState.item.isDeleted) {
            TelegramReactionPillBar(
                emojis = DefaultQuickReactions,
                activeEmoji = activeState.item.myReaction,
                chatColors = chatColors,
                progressProvider = { focusAnim.value },
                pivotX = if (bubbleAlignEnd) 0.85f else 0.15f,
                reduceMotion = reduceMotion,
                onEmojiClick = { emoji ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectReaction(activeState.item.message.id, emoji)
                    onDismiss()
                },
                modifier = Modifier
                    .onSizeChanged { pillWidthPx = it.width }
                    .offset {
                        val p = focusAnim.value
                        val currentBubbleTop = anchor.top + (targetBubbleTopPx - anchor.top) * p
                        val pillTop = (currentBubbleTop - pillHeightPx - gapPx)
                            .coerceAtLeast(statusBarTopPx + edgeMarginPx)
                        IntOffset(pillX.roundToInt(), pillTop.roundToInt())
                    }
            )
        }

        // 4. Compact Left-Icon Context Menu BELOW the Sharp Bubble
        if (activeState.menuItems.isNotEmpty() || seenLabel != null) {
            CompactLeftIconContextMenu(
                items = activeState.menuItems,
                seenLabel = seenLabel,
                chatColors = chatColors,
                progressProvider = { focusAnim.value },
                transformOrigin = TransformOrigin(if (bubbleAlignEnd) 0.88f else 0.12f, 0f),
                modifier = Modifier
                    .onSizeChanged { menuHeightPx = it.height }
                    .offset {
                        val p = focusAnim.value
                        val currentBubbleTop = anchor.top + (targetBubbleTopPx - anchor.top) * p
                        val minMenuTop = statusBarTopPx + edgeMarginPx + pillHeightPx + gapPx
                        val maxMenuTop = (rootHeightPx - navBarBottomPx - edgeMarginPx - menuHeightPx)
                            .coerceAtLeast(minMenuTop)
                        val menuTop = (currentBubbleTop + anchor.height + gapPx)
                            .coerceAtMost(maxMenuTop)
                        IntOffset(menuX.roundToInt(), menuTop.roundToInt())
                    }
            )
        }
    }
}

@Composable
private fun TelegramReactionPillBar(
    emojis: List<String>,
    activeEmoji: String?,
    chatColors: ChatColors,
    progressProvider: () -> Float,
    pivotX: Float,
    reduceMotion: Boolean,
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val scope = rememberCoroutineScope()
    val pillShape = RoundedCornerShape(ChatDimens.ReactionPillCornerRadius)

    Row(
        modifier = modifier
            .height(ChatDimens.ReactionPillHeight)
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
                cornerRadius = ChatDimens.ReactionPillCornerRadius,
                blurRadius = 18.dp,
                offsetY = 4.dp
            )
            .clip(pillShape)
            .background(chatColors.reactionPillBg)
            .border(0.8.dp, chatColors.reactionPillBorder, pillShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        emojis.forEachIndexed { index, emoji ->
            val emojiAnim = remember(emoji) { Animatable(if (reduceMotion) 1f else 0f) }
            val tapBurst = remember(emoji) { Animatable(1f) }
            val isSelected = activeEmoji == emoji

            LaunchedEffect(Unit) {
                if (!reduceMotion) {
                    delay(index * 22L)
                    emojiAnim.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 560f))
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
                    .background(
                        if (isSelected) colors.primaryBlue.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        scope.launch {
                            if (!reduceMotion) {
                                tapBurst.animateTo(1.30f, spring(dampingRatio = 0.5f, stiffness = 700f))
                                tapBurst.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 560f))
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
    seenLabel: String?,
    chatColors: ChatColors,
    progressProvider: () -> Float,
    transformOrigin: TransformOrigin,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val menuShape = RoundedCornerShape(ChatDimens.ContextMenuCornerRadius)
    Column(
        modifier = modifier
            .width(ChatDimens.ContextMenuWidth)
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
                cornerRadius = ChatDimens.ContextMenuCornerRadius,
                blurRadius = 18.dp,
                offsetY = 6.dp
            )
            .clip(menuShape)
            .background(chatColors.contextMenuBg)
            .border(0.8.dp, chatColors.contextMenuBorder, menuShape)
            .padding(vertical = 4.dp)
    ) {
        if (!seenLabel.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check_double),
                    contentDescription = null,
                    tint = chatColors.inboxCheckReadAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = seenLabel,
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (items.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(ChatDimens.DividerThickness)
                        .background(colors.hairlineBorder)
                )
            }
        }

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
                        .height(ChatDimens.DividerThickness)
                        .background(colors.hairlineBorder)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews (Light + Dark, LTR + RTL, Short + Long, Grouped, Failed)
// ---------------------------------------------------------------------------

private fun sampleMessage(
    id: String,
    senderId: String,
    content: String,
    status: DmSendStatus = DmSendStatus.SENT
) = DmMessage(
    id = id,
    conversationId = "c1",
    senderId = senderId,
    senderDisplayName = if (senderId == "me") "Mustafa" else "Voxin",
    content = content,
    createdAt = "2026-11-12T18:30:00Z",
    localSendStatus = status
)

@Preview(showBackground = true, name = "Bubbles - Light LTR Grouped & Failed")
@Composable
private fun PreviewChatBubblesLightLtr() {
    BuildXTheme(appearance = app.buildx.chat.data.model.AppAppearance.LIGHT) {
        val chatColors = BuildXTheme.colors.toChatColors()
        Column(
            modifier = Modifier
                .background(chatColors.pageBackground)
                .padding(12.dp)
        ) {
            ChatCompactPeerIntroCard(
                intro = DmPeerIntro(
                    id = "peer",
                    username = "shopramahoo",
                    handle = "shopramahoo",
                    displayName = "Voxin",
                    isVerified = true,
                    followersCount = 1280,
                    postsCount = 12,
                    followingSince = "2026-02-14T10:00:00Z"
                ),
                chatColors = chatColors,
                onViewProfileClick = {}
            )
            ChatDateSeparatorChip(createdAtIso = "2026-11-12T18:30:00Z", chatColors = chatColors)
            ChatMessageRow(
                item = ChatListItem.MessageItem(
                    stableKey = "1",
                    message = sampleMessage("1", "peer", "Hey! Check out this new layout."),
                    isOwn = false,
                    isGroupChat = false,
                    isDeleted = false,
                    isEdited = false,
                    isRtlContent = false,
                    position = ChatBubblePosition.FIRST,
                    isGroupedWithPrevious = false,
                    showSenderName = false,
                    showSenderAvatar = false,
                    formattedTime = "6:30 PM",
                    deliveryStatus = ChatDeliveryStatus.SENT,
                    replyPreview = null,
                    seenParticipants = emptyList()
                ),
                chatColors = chatColors,
                isMenuOpen = false,
                menuItems = emptyList(),
                isJustSent = false,
                isNewlyReceived = false,
                reduceMotion = true,
                onConsumeJustSent = {},
                onConsumeNewlyReceived = {},
                onBubbleTap = {},
                onRetryClick = {},
                onReplyPreviewClick = {},
                onDismissMenu = {},
                onSwipeToReply = {}
            )
            ChatMessageRow(
                item = ChatListItem.MessageItem(
                    stableKey = "2",
                    message = sampleMessage("2", "peer", "هلا"),
                    isOwn = false,
                    isGroupChat = false,
                    isDeleted = false,
                    isEdited = false,
                    isRtlContent = true,
                    position = ChatBubblePosition.LAST,
                    isGroupedWithPrevious = true,
                    showSenderName = false,
                    showSenderAvatar = false,
                    formattedTime = "7:07 PM",
                    deliveryStatus = ChatDeliveryStatus.SENT,
                    replyPreview = null,
                    seenParticipants = emptyList()
                ),
                chatColors = chatColors,
                isMenuOpen = false,
                menuItems = emptyList(),
                isJustSent = false,
                isNewlyReceived = false,
                reduceMotion = true,
                onConsumeJustSent = {},
                onConsumeNewlyReceived = {},
                onBubbleTap = {},
                onRetryClick = {},
                onReplyPreviewClick = {},
                onDismissMenu = {},
                onSwipeToReply = {}
            )
            ChatMessageRow(
                item = ChatListItem.MessageItem(
                    stableKey = "3",
                    message = sampleMessage("3", "me", "Looks great, smooth spring motion and inline timestamps."),
                    isOwn = true,
                    isGroupChat = false,
                    isDeleted = false,
                    isEdited = false,
                    isRtlContent = false,
                    position = ChatBubblePosition.FIRST,
                    isGroupedWithPrevious = false,
                    showSenderName = false,
                    showSenderAvatar = false,
                    formattedTime = "6:31 PM",
                    deliveryStatus = ChatDeliveryStatus.READ,
                    replyPreview = null,
                    seenParticipants = emptyList()
                ),
                chatColors = chatColors,
                isMenuOpen = false,
                menuItems = emptyList(),
                isJustSent = false,
                isNewlyReceived = false,
                reduceMotion = true,
                onConsumeJustSent = {},
                onConsumeNewlyReceived = {},
                onBubbleTap = {},
                onRetryClick = {},
                onReplyPreviewClick = {},
                onDismissMenu = {},
                onSwipeToReply = {}
            )
            ChatMessageRow(
                item = ChatListItem.MessageItem(
                    stableKey = "4",
                    message = sampleMessage("4", "me", "Failed message example", DmSendStatus.FAILED),
                    isOwn = true,
                    isGroupChat = false,
                    isDeleted = false,
                    isEdited = false,
                    isRtlContent = false,
                    position = ChatBubblePosition.LAST,
                    isGroupedWithPrevious = true,
                    showSenderName = false,
                    showSenderAvatar = false,
                    formattedTime = "6:31 PM",
                    deliveryStatus = ChatDeliveryStatus.FAILED,
                    replyPreview = null,
                    seenParticipants = emptyList()
                ),
                chatColors = chatColors,
                isMenuOpen = false,
                menuItems = emptyList(),
                isJustSent = false,
                isNewlyReceived = false,
                reduceMotion = true,
                onConsumeJustSent = {},
                onConsumeNewlyReceived = {},
                onBubbleTap = {},
                onRetryClick = {},
                onReplyPreviewClick = {},
                onDismissMenu = {},
                onSwipeToReply = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Bubbles - Dark RTL Arabic")
@Composable
private fun PreviewChatBubblesDarkRtl() {
    BuildXTheme(appearance = app.buildx.chat.data.model.AppAppearance.DARK) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val chatColors = BuildXTheme.colors.toChatColors()
            Column(
                modifier = Modifier
                    .background(chatColors.pageBackground)
                    .padding(12.dp)
            ) {
                ChatMessageRow(
                    item = ChatListItem.MessageItem(
                        stableKey = "ar1",
                        message = sampleMessage("ar1", "peer", "مرحباً! التصميم الجديد سلس جداً مع Build X"),
                        isOwn = false,
                        isGroupChat = false,
                        isDeleted = false,
                        isEdited = false,
                        isRtlContent = true,
                        position = ChatBubblePosition.SINGLE,
                        isGroupedWithPrevious = false,
                        showSenderName = false,
                        showSenderAvatar = false,
                        formattedTime = "6:32 PM",
                        deliveryStatus = ChatDeliveryStatus.SENT,
                        replyPreview = null,
                        seenParticipants = emptyList()
                    ),
                    chatColors = chatColors,
                    isMenuOpen = false,
                    menuItems = emptyList(),
                    isJustSent = false,
                    isNewlyReceived = false,
                    reduceMotion = true,
                    onConsumeJustSent = {},
                    onConsumeNewlyReceived = {},
                    onBubbleTap = {},
                    onRetryClick = {},
                    onReplyPreviewClick = {},
                    onDismissMenu = {},
                    onSwipeToReply = {}
                )
                ChatMessageRow(
                    item = ChatListItem.MessageItem(
                        stableKey = "ar2",
                        message = sampleMessage("ar2", "me", "هلا"),
                        isOwn = true,
                        isGroupChat = false,
                        isDeleted = false,
                        isEdited = false,
                        isRtlContent = true,
                        position = ChatBubblePosition.SINGLE,
                        isGroupedWithPrevious = false,
                        showSenderName = false,
                        showSenderAvatar = false,
                        formattedTime = "7:52 PM",
                        deliveryStatus = ChatDeliveryStatus.READ,
                        replyPreview = null,
                        seenParticipants = emptyList()
                    ),
                    chatColors = chatColors,
                    isMenuOpen = false,
                    menuItems = emptyList(),
                    isJustSent = false,
                    isNewlyReceived = false,
                    reduceMotion = true,
                    onConsumeJustSent = {},
                    onConsumeNewlyReceived = {},
                    onBubbleTap = {},
                    onRetryClick = {},
                    onReplyPreviewClick = {},
                    onDismissMenu = {},
                    onSwipeToReply = {}
                )
            }
        }
    }
}
