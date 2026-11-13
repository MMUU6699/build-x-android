package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.DmReplyPreview
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.ActionMenuPopup
import app.buildx.chat.ui.components.BubblePopupPositionProvider
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * RTL-aware bubble corner system:
 * Large radius (20dp) on outer corners, smaller radius (6dp) on grouped sender-side corners.
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
    isHighlighted: Boolean = false,
    isDimmed: Boolean = false
) {
    val topSpacing = if (item.isGroupedWithPrevious) {
        ChatDimens.IntraGroupSpacing
    } else {
        ChatDimens.InterGroupSpacing
    }
    val rowDimAlpha by animateFloatAsState(
        targetValue = if (isDimmed && !isMenuOpen) 0.42f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else 150),
        label = "row_dim_alpha"
    )

    ChatBubbleEntryWrapper(
        isOwn = item.isOwn,
        isJustSent = isJustSent,
        isNewlyReceived = isNewlyReceived,
        reduceMotion = reduceMotion,
        onConsumeJustSent = onConsumeJustSent,
        onConsumeNewlyReceived = onConsumeNewlyReceived,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isMenuOpen) 10f else 0f)
            .graphicsLayer { alpha = rowDimAlpha }
            .padding(top = topSpacing)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ChatSwipeToReplyContainer(
                enabled = !item.isDeleted && item.deliveryStatus != ChatDeliveryStatus.SENDING,
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
                                displayName = item.message.senderDisplayName,
                                chatColors = chatColors
                            )
                        } else {
                            Spacer(modifier = Modifier.width(ChatDimens.GroupAvatarSize))
                        }
                        Spacer(modifier = Modifier.width(ChatDimens.GroupAvatarGap))
                    }

                    Column(
                        horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (item.showSenderName) {
                            Text(
                                text = item.message.senderDisplayName ?: "",
                                color = BuildXTheme.colors.secondaryText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
                            )
                        }

                        ChatBubbleContent(
                            item = item,
                            chatColors = chatColors,
                            isMenuOpen = isMenuOpen,
                            isHighlighted = isHighlighted,
                            menuItems = menuItems,
                            reduceMotion = reduceMotion,
                            onBubbleTap = onBubbleTap,
                            onBubbleLongPress = onBubbleLongPress,
                            onImageClick = onImageClick,
                            onRetryClick = onRetryClick,
                            onReplyPreviewClick = onReplyPreviewClick,
                            onDismissMenu = onDismissMenu
                        )

                        if (item.seenParticipants.isNotEmpty() && item.isOwn) {
                            ChatSeenAvatarsRow(
                                participants = item.seenParticipants,
                                chatColors = chatColors,
                                modifier = Modifier.padding(top = 3.dp, end = 4.dp)
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
    onConsumeJustSent: () -> Unit,
    onConsumeNewlyReceived: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isScreenRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val animProgress = remember {
        Animatable(if ((isJustSent || isNewlyReceived) && !reduceMotion) 0f else 1f)
    }

    LaunchedEffect(isJustSent, isNewlyReceived, reduceMotion) {
        if (reduceMotion) {
            animProgress.snapTo(1f)
            if (isJustSent) onConsumeJustSent()
            if (isNewlyReceived) onConsumeNewlyReceived()
            return@LaunchedEffect
        }
        if (isJustSent) {
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = ChatMotion.SendLiftDampingRatio,
                    stiffness = ChatMotion.SendLiftStiffness
                )
            )
            onConsumeJustSent()
        } else if (isNewlyReceived) {
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ChatMotion.ReceiveEntryDurationMs,
                    easing = ChatMotion.EmphasizedDecelerate
                )
            )
            onConsumeNewlyReceived()
        }
    }

    val p = animProgress.value
    val graphicsModifier = if (p >= 0.999f) {
        Modifier
    } else if (isJustSent) {
        val startOffsetPx = with(density) { ChatMotion.SendLiftStartTranslationY.toPx() }
        val scale = ChatMotion.SendLiftStartScale + (1f - ChatMotion.SendLiftStartScale) * p
        val alphaVal = ChatMotion.SendLiftStartAlpha +
            (1f - ChatMotion.SendLiftStartAlpha) * (p * 1.35f).coerceAtMost(1f)
        val originX = if (isOwn xor isScreenRtl) 1f else 0f
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = startOffsetPx * (1f - p)
            alpha = alphaVal
            transformOrigin = TransformOrigin(originX, 1f)
        }
    } else {
        val slidePx = with(density) { ChatMotion.ReceiveEntrySlideY.toPx() }
        val scale = ChatMotion.ReceiveEntryStartScale + (1f - ChatMotion.ReceiveEntryStartScale) * p
        val originX = if (isScreenRtl) 0.9f else 0.1f
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = slidePx * (1f - p)
            alpha = p
            transformOrigin = TransformOrigin(originX, 1f)
        }
    }

    Box(modifier = modifier.then(graphicsModifier)) {
        content()
    }
}

@Composable
private fun ChatBubbleContent(
    item: ChatListItem.MessageItem,
    chatColors: ChatColors,
    isMenuOpen: Boolean,
    isHighlighted: Boolean,
    menuItems: List<ActionMenuItem>,
    reduceMotion: Boolean,
    onBubbleTap: () -> Unit,
    onBubbleLongPress: () -> Unit,
    onImageClick: () -> Unit,
    onRetryClick: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onDismissMenu: () -> Unit
) {
    val msg = item.message
    val shape = chatBubbleShape(isOwn = item.isOwn, position = item.position)
    val hasAttachment = !msg.attachmentPath.isNullOrBlank() && !item.isDeleted
    val haptic = LocalHapticFeedback.current
    val focusScale by animateFloatAsState(
        targetValue = if (isMenuOpen && !reduceMotion) 1.02f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else 150),
        label = "bubble_focus_scale"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.72f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 240),
        label = "bubble_jump_highlight"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * ChatDimens.BubbleMaxWidthFraction

        Column(
            horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
                .animateContentSize(
                    animationSpec = if (reduceMotion) tween(0) else tween(ChatMotion.DeleteCrossfadeMs)
                )
        ) {
            if (!item.isDeleted && item.replyPreview != null) {
                ChatQuotedReplyCard(
                    reply = item.replyPreview,
                    isOwn = item.isOwn,
                    chatColors = chatColors,
                    maxBubbleWidth = maxBubbleWidth,
                    onClick = { onReplyPreviewClick(item.replyPreview.id) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else if (!item.isDeleted && msg.messageType == "note_reply" && !msg.noteSnapshotText.isNullOrBlank()) {
                ChatNoteReplyPill(
                    snapshotText = msg.noteSnapshotText,
                    chatColors = chatColors,
                    maxBubbleWidth = maxBubbleWidth
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (hasAttachment) {
                val mediaShape = RoundedCornerShape(ChatDimens.MediaBubbleCorner)
                val focusShadowModifier = if (isMenuOpen) {
                    Modifier.softShadow(
                        color = BuildXTheme.colors.scrimColor.copy(alpha = 0.45f),
                        cornerRadius = ChatDimens.MediaBubbleCorner,
                        blurRadius = 16.dp,
                        offsetY = 4.dp
                    )
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .widthIn(max = ChatDimens.MediaMaxWidth.coerceAtMost(maxBubbleWidth))
                        .heightIn(min = ChatDimens.MediaMinHeight, max = ChatDimens.MediaMaxHeight)
                        .then(focusShadowModifier)
                        .clip(mediaShape)
                        .background(chatColors.receivedBubbleBg)
                        .border(0.8.dp, chatColors.headerGlassBorder, mediaShape)
                        .then(
                            if (highlightAlpha > 0.01f) {
                                Modifier.border(2.dp, BuildXTheme.colors.primaryBlue.copy(alpha = highlightAlpha), mediaShape)
                            } else {
                                Modifier
                            }
                        )
                        .pointerInput(msg.id) {
                            detectTapGestures(
                                onTap = { onImageClick() },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBubbleLongPress()
                                }
                            )
                        }
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
                                readTint = chatColors.readCheckAccent,
                                failedTint = chatColors.failedAccent,
                                reduceMotion = reduceMotion
                            )
                        }
                    }

                    if (isMenuOpen && msg.content.isBlank()) {
                        ChatBubbleActionPopup(items = menuItems, onDismiss = onDismissMenu)
                    }
                }

                if (msg.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            if (item.isDeleted || msg.content.isNotBlank()) {
                AnimatedContent(
                    targetState = item.isDeleted,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            fadeIn(tween(ChatMotion.DeleteCrossfadeMs)) togetherWith
                                fadeOut(tween(ChatMotion.DeleteCrossfadeMs))
                        }
                    },
                    label = "bubble_delete_crossfade"
                ) { deleted ->
                    if (deleted) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = maxBubbleWidth)
                                .clip(shape)
                                .background(chatColors.deletedPillBg)
                                .border(0.8.dp, chatColors.deletedPillBorder, shape)
                                .pointerInput(msg.id) {
                                    detectTapGestures(
                                        onTap = { onBubbleTap() },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onBubbleLongPress()
                                        }
                                    )
                                }
                                .padding(
                                    horizontal = ChatDimens.BubbleHorizontalPadding,
                                    vertical = ChatDimens.BubbleVerticalPadding
                                )
                        ) {
                            Text(
                                text = stringResource(R.string.dm_message_deleted),
                                color = chatColors.deletedPillText,
                                fontSize = ChatDimens.DeletedFontSize,
                                fontStyle = FontStyle.Italic,
                                fontFamily = BuildXFontFamily
                            )
                            if (isMenuOpen) {
                                ChatBubbleActionPopup(items = menuItems, onDismiss = onDismissMenu)
                            }
                        }
                    } else {
                        val bubbleBg = if (item.isOwn) chatColors.sentBubbleBg else chatColors.receivedBubbleBg
                        val textColor = if (item.isOwn) chatColors.sentBubbleText else chatColors.receivedBubbleText
                        val metaTint = if (item.isOwn) chatColors.sentMetaText else chatColors.receivedMetaText
                        val focusShadowModifier = if (isMenuOpen) {
                            Modifier.softShadow(
                                color = BuildXTheme.colors.scrimColor.copy(alpha = 0.45f),
                                cornerRadius = ChatDimens.BubbleCornerLarge,
                                blurRadius = 16.dp,
                                offsetY = 4.dp
                            )
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .widthIn(max = maxBubbleWidth)
                                .then(focusShadowModifier)
                                .clip(shape)
                                .background(bubbleBg)
                                .then(
                                    if (highlightAlpha > 0.01f) {
                                        Modifier.border(2.dp, BuildXTheme.colors.primaryBlue.copy(alpha = highlightAlpha), shape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .pointerInput(msg.id, item.deliveryStatus) {
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
                                .padding(
                                    horizontal = ChatDimens.BubbleHorizontalPadding,
                                    vertical = ChatDimens.BubbleVerticalPadding
                                )
                        ) {
                            val maxTextWidth = (maxBubbleWidth - ChatDimens.BubbleHorizontalPadding * 2)
                                .coerceAtLeast(120.dp)
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
                                readTint = chatColors.readCheckAccent,
                                failedTint = chatColors.failedAccent,
                                reduceMotion = reduceMotion
                            )

                            if (isMenuOpen) {
                                ChatBubbleActionPopup(items = menuItems, onDismiss = onDismissMenu)
                            }
                        }
                    }
                }
            }

            if (item.deliveryStatus == ChatDeliveryStatus.FAILED && !item.isDeleted) {
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
}

/**
 * Custom Compose [Layout] pairing message text + metadata cluster (time + optional status icon).
 *
 * Uses [rememberTextMeasurer] for synchronous last-line measurement and places metadata with
 * disjoint coordinates via [ChatMessageMapper.computeBubbleMetaPlacement], so overlap between
 * RTL/LTR text and timestamps is impossible by construction and bubbles tightly hug their content.
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
    readTint: Color,
    failedTint: Color,
    reduceMotion: Boolean
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(textColor, isRtlContent) {
        TextStyle(
            fontFamily = BuildXFontFamily,
            color = textColor,
            fontSize = ChatDimens.MessageFontSize,
            lineHeight = ChatDimens.MessageLineHeight,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start,
            textDirection = if (isRtlContent) TextDirection.Rtl else TextDirection.Ltr
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

    Layout(
        modifier = Modifier.widthIn(max = maxTextWidth),
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
                readTint = readTint,
                failedTint = failedTint,
                reduceMotion = reduceMotion
            )
        }
    ) { measurables, constraints ->
        val childMaxWidth = constraints.maxWidth.coerceAtMost(maxWidthPx).coerceAtLeast(1)
        val looseConstraints = Constraints(minWidth = 0, maxWidth = childMaxWidth)
        val textPlaceable = measurables[0].measure(looseConstraints)
        val metaPlaceable = measurables[1].measure(looseConstraints)

        val lastLineIndex = (layoutResult.lineCount - 1).coerceAtLeast(0)
        val measuredLastLinePx = if (layoutResult.lineCount > 0) {
            ceil((layoutResult.getLineRight(lastLineIndex) - layoutResult.getLineLeft(lastLineIndex)).toDouble())
                .toInt()
                .coerceAtLeast(0)
        } else {
            textPlaceable.width
        }
        val effectiveLastLinePx = if (layoutResult.lineCount <= 1) {
            textPlaceable.width
        } else {
            measuredLastLinePx.coerceAtMost(textPlaceable.width)
        }

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

        layout(spec.widthPx, spec.heightPx) {
            // Use raw physical coordinates (x, y) because spec already handles RTL vs LTR geometry
            textPlaceable.place(spec.textX, spec.textY)
            metaPlaceable.place(spec.metaX, spec.metaY)
        }
    }
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
                    color = tint,
                    fontSize = ChatDimens.MetaTimeFontSize,
                    lineHeight = ChatDimens.MetaTimeLineHeight,
                    fontFamily = BuildXFontFamily
                )
            }
            if (timeText.isNotBlank()) {
                Text(
                    text = timeText,
                    style = TextStyle(
                        fontFamily = BuildXFontFamily,
                        color = tint,
                        fontSize = ChatDimens.MetaTimeFontSize,
                        lineHeight = ChatDimens.MetaTimeLineHeight,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                        textDirection = TextDirection.Ltr
                    )
                )
            }
            if (isOwn) {
                ChatStatusIcon(
                    status = status,
                    defaultTint = Color.White.copy(alpha = 0.88f),
                    readTint = readTint,
                    failedTint = failedTint,
                    reduceMotion = reduceMotion
                )
            }
        }
    }
}

@Composable
private fun ChatStatusIcon(
    status: ChatDeliveryStatus,
    defaultTint: Color,
    readTint: Color,
    failedTint: Color,
    reduceMotion: Boolean
) {
    Box(
        modifier = Modifier.size(ChatDimens.StatusIconSize),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = status,
            contentAlignment = Alignment.Center,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (fadeIn(tween(ChatMotion.StatusTransitionMs)) +
                        scaleIn(tween(ChatMotion.StatusTransitionMs), initialScale = ChatMotion.StatusStartScale)) togetherWith
                        (fadeOut(tween(ChatMotion.StatusTransitionMs)) +
                            scaleOut(tween(ChatMotion.StatusTransitionMs), targetScale = ChatMotion.StatusStartScale))
                }
            },
            label = "dm_status_icon"
        ) { target ->
            if (target == ChatDeliveryStatus.FAILED) {
                Box(
                    modifier = Modifier
                        .size(ChatDimens.StatusIconSize)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dm_error),
                        contentDescription = target.name,
                        tint = failedTint,
                        modifier = Modifier.size(ChatDimens.StatusIconSize)
                    )
                }
            } else {
                val (iconRes, iconTint) = when (target) {
                    ChatDeliveryStatus.SENDING -> R.drawable.ic_dm_clock to defaultTint
                    ChatDeliveryStatus.SENT -> R.drawable.ic_check to defaultTint
                    ChatDeliveryStatus.DELIVERED -> R.drawable.ic_dm_double_check to defaultTint
                    ChatDeliveryStatus.READ -> R.drawable.ic_dm_double_check to readTint
                    ChatDeliveryStatus.FAILED -> R.drawable.ic_dm_error to failedTint
                }
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = target.name,
                    tint = iconTint,
                    modifier = Modifier.size(ChatDimens.StatusIconSize)
                )
            }
        }
    }
}

@Composable
private fun ChatQuotedReplyCard(
    reply: DmReplyPreview,
    isOwn: Boolean,
    chatColors: ChatColors,
    maxBubbleWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val bg = if (isOwn) chatColors.quotedBgSent else chatColors.quotedBgReceived
    val bar = if (isOwn) chatColors.quotedBarSent else chatColors.quotedBarReceived
    val secondary = BuildXTheme.colors.secondaryText
    val primary = BuildXTheme.colors.primaryText

    Row(
        modifier = Modifier
            .widthIn(max = maxBubbleWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(bar)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = reply.senderDisplayName ?: stringResource(R.string.dm_you),
                color = bar,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BuildXFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (reply.isDeleted) {
                    stringResource(R.string.dm_message_deleted)
                } else {
                    reply.content.ifBlank { stringResource(R.string.dm_media_attachment) }
                },
                color = if (reply.isDeleted) secondary else if (isOwn) primary.copy(alpha = 0.85f) else secondary,
                style = TextStyle(
                    fontFamily = BuildXFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontStyle = if (reply.isDeleted) FontStyle.Italic else FontStyle.Normal,
                    textDirection = TextDirection.Content
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatNoteReplyPill(
    snapshotText: String,
    chatColors: ChatColors,
    maxBubbleWidth: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .widthIn(max = maxBubbleWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(chatColors.dateChipBg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${stringResource(R.string.dm_replied_to_note)}: \"$snapshotText\"",
            color = chatColors.dateChipText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChatGroupSenderAvatar(
    avatarUrl: String?,
    displayName: String?,
    chatColors: ChatColors
) {
    Box(
        modifier = Modifier
            .size(ChatDimens.GroupAvatarSize)
            .clip(CircleShape)
            .background(chatColors.receivedBubbleBg)
            .border(0.8.dp, chatColors.headerGlassBorder, CircleShape),
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChatSeenAvatarsRow(
    participants: List<DmParticipant>,
    chatColors: ChatColors,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ChatDimens.SeenAvatarOverlap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        participants.take(5).forEach { participant ->
            Box(
                modifier = Modifier
                    .size(ChatDimens.SeenAvatarSize)
                    .clip(CircleShape)
                    .border(1.dp, chatColors.pageBackground, CircleShape)
                    .background(chatColors.receivedBubbleBg)
            ) {
                if (!participant.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = participant.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ChatDimens.DateChipVerticalMargin),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = chatColors.dateChipText,
            fontSize = ChatDimens.MetaTimeFontSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(ChatDimens.DateChipCorner))
                .background(chatColors.dateChipBg)
                .padding(
                    horizontal = ChatDimens.DateChipPaddingH,
                    vertical = ChatDimens.DateChipPaddingV
                )
        )
    }
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
    onSwipeToReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = BuildXTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val maxPx = with(density) { ChatDimens.SwipeReplyMaxOffsetDp.toPx() }
    val triggerPx = with(density) { ChatDimens.SwipeReplyTriggerDp.toPx() }

    val draggableState = rememberDraggableState { delta ->
        if (!enabled) return@rememberDraggableState
        val direction = if (isRtl) -1f else 1f
        offsetX = (offsetX + (delta * direction)).coerceIn(0f, maxPx)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = {
                    if (offsetX > triggerPx) {
                        onSwipeToReply()
                    }
                    offsetX = 0f
                }
            )
    ) {
        if (offsetX > 16f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_reply),
                    contentDescription = stringResource(R.string.dm_action_reply),
                    tint = colors.primaryBlue,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset((if (isRtl) -offsetX else offsetX).roundToInt(), 0) }
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun ChatBubbleActionPopup(
    items: List<ActionMenuItem>,
    onDismiss: () -> Unit
) {
    val strings = BuildXTheme.strings
    Popup(
        popupPositionProvider = remember(strings.isRtl) { BubblePopupPositionProvider(strings.isRtl) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        ActionMenuPopup(items = items)
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
