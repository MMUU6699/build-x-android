package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage

/**
 * Single-pill DM Composer aligned on an 8dp grid:
 * - Multi-line text field up to 6 lines with smooth animateContentSize
 * - Auto-detects Arabic/RTL vs English/LTR text direction
 * - Attach button slides/fades smoothly out when typing to give maximum text room
 * - Primary circular button swaps between Mic and Send with crossfade + scale (0.6->1) + rotation
 * - Disables Send for blank/whitespace-only text and prevents rapid double-send
 * - Triggers light haptic feedback on send and keeps the keyboard open
 */
@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    stagedAttachments: List<StagedAttachment>,
    onRemoveAttachment: (StagedAttachment) -> Unit,
    replyingTo: DmMessage?,
    onCancelReply: () -> Unit,
    editing: DmMessage?,
    onCancelEdit: () -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    onVoiceDictationClick: () -> Unit,
    chatColors: ChatColors,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    var lastSendClickMs by remember { mutableLongStateOf(0L) }
    var lineCount by remember { mutableIntStateOf(1) }

    val hasSendableContent = text.isNotBlank() || stagedAttachments.isNotEmpty()
    val showSendMode = text.isNotEmpty() || stagedAttachments.isNotEmpty()
    val isTextRtl = remember(text, strings.isRtl) {
        isRtlText(text, defaultRtl = strings.isRtl)
    }
    val hasLeadingArabic = remember(text) {
        text.firstOrNull { !it.isWhitespace() }?.let { ch ->
            val dir = Character.getDirectionality(ch)
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
        } ?: false
    }
    val effectiveInputRtl = if (text.isEmpty()) strings.isRtl else (hasLeadingArabic || isTextRtl)

    val hasTopBanner = replyingTo != null || editing != null || stagedAttachments.isNotEmpty()
    val targetCorner by animateDpAsState(
        targetValue = if (hasTopBanner || lineCount > 1) {
            ChatDimens.ComposerExpandedCorner
        } else {
            ChatDimens.ComposerCorner
        },
        animationSpec = tween(if (reduceMotion) 0 else 180),
        label = "composer_corner"
    )
    val composerShape = RoundedCornerShape(targetCorner)

    val activeBannerTitle = when {
        replyingTo != null -> stringResource(
            R.string.dm_replying_to,
            replyingTo.senderDisplayName ?: stringResource(R.string.dm_you)
        )
        editing != null -> stringResource(R.string.dm_editing_message)
        else -> null
    }
    val activeBannerPreview = when {
        replyingTo != null -> replyingTo.content.ifBlank { stringResource(R.string.dm_media_attachment) }
        editing != null -> editing.content.ifBlank { stringResource(R.string.dm_media_attachment) }
        else -> null
    }
    var lastBannerTitle by remember { mutableStateOf("") }
    var lastBannerPreview by remember { mutableStateOf("") }
    var lastBannerIsEdit by remember { mutableStateOf(false) }
    if (activeBannerTitle != null && activeBannerPreview != null) {
        lastBannerTitle = activeBannerTitle
        lastBannerPreview = activeBannerPreview
        lastBannerIsEdit = replyingTo == null && editing != null
    }

    // Outer container owns padding -> minHeight -> softShadow -> clip -> background -> border
    // so animateContentSize on the inner Column never clips or tears the outer shadow.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ChatDimens.ComposerOuterPaddingH,
                end = ChatDimens.ComposerOuterPaddingH,
                top = ChatDimens.ComposerOuterPaddingTop,
                bottom = ChatDimens.ComposerOuterPaddingBottom
            )
            .defaultMinSize(minHeight = ChatDimens.ComposerMinHeight)
            .softShadow(
                color = chatColors.composerShadow,
                cornerRadius = targetCorner,
                blurRadius = ChatDimens.ComposerShadowBlur,
                offsetY = ChatDimens.ComposerShadowOffsetY
            )
            .clip(composerShape)
            .background(chatColors.composerBg)
            .border(
                ChatDimens.ComposerBorderWidth,
                chatColors.composerBorder,
                composerShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = if (reduceMotion) {
                        tween(0)
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    }
                )
        ) {
            AnimatedVisibility(
                visible = replyingTo != null || editing != null,
                enter = if (reduceMotion) {
                    fadeIn(tween(0))
                } else {
                    expandVertically(tween(180)) + fadeIn(tween(160))
                },
                exit = if (reduceMotion) {
                    fadeOut(tween(0))
                } else {
                    shrinkVertically(tween(160)) + fadeOut(tween(120))
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ChatComposerContextBanner(
                        title = activeBannerTitle ?: lastBannerTitle,
                        preview = activeBannerPreview ?: lastBannerPreview,
                        accentColor = colors.primaryBlue,
                        onCancel = if (lastBannerIsEdit) onCancelEdit else onCancelReply
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(0.6.dp)
                            .background(colors.hairlineBorder)
                    )
                }
            }

            if (stagedAttachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stagedAttachments, key = { it.uri.toString() }) { att ->
                        Box(modifier = Modifier.size(56.dp)) {
                            AsyncImage(
                                model = att.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { onRemoveAttachment(att) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.dm_cancel),
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }

            val scope = rememberCoroutineScope()
            val sendPulseScale = remember { Animatable(1f) }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = ChatDimens.ComposerActionSize)
                            .padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val placeholderAlpha by animateFloatAsState(
                            targetValue = if (text.isEmpty()) 1f else 0f,
                            animationSpec = tween(if (reduceMotion) 0 else ChatMotion.PlaceholderFadeMs),
                            label = "composer_placeholder_alpha"
                        )
                        if (text.isEmpty() || placeholderAlpha > 0.01f) {
                            Text(
                                text = stringResource(R.string.dm_composer_placeholder),
                                color = colors.secondaryText,
                                style = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Normal,
                                    textDirection = if (strings.isRtl) TextDirection.Rtl else TextDirection.Ltr
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(placeholderAlpha),
                                textAlign = if (strings.isRtl) TextAlign.Right else TextAlign.Left
                            )
                        }

                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (effectiveInputRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            BasicTextField(
                                value = text,
                                onValueChange = { updated ->
                                    if (updated.isEmpty()) lineCount = 1
                                    onTextChange(updated)
                                },
                                minLines = 1,
                                maxLines = ChatDimens.ComposerTextMaxLines,
                                onTextLayout = { layoutResult ->
                                    lineCount = layoutResult.lineCount
                                },
                                textStyle = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    color = colors.primaryText,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Start,
                                    textDirection = if (effectiveInputRtl) TextDirection.Rtl else TextDirection.Ltr
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 22.dp, max = ChatDimens.ComposerTextMaxHeight)
                                    .focusRequester(focusRequester)
                            )
                        }
                    }

                    // Right action tray: uniform 5dp/6dp outer row ring around 42dp circle button
                    Row(
                        modifier = Modifier.heightIn(min = ChatDimens.ComposerActionSize),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = text.isEmpty(),
                            enter = fadeIn(tween(if (reduceMotion) 0 else ChatMotion.AttachSlideDurationMs)) +
                                expandHorizontally(tween(if (reduceMotion) 0 else ChatMotion.AttachSlideDurationMs)),
                            exit = fadeOut(tween(if (reduceMotion) 0 else ChatMotion.AttachSlideDurationMs)) +
                                shrinkHorizontally(tween(if (reduceMotion) 0 else ChatMotion.AttachSlideDurationMs))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(ChatDimens.ComposerAttachButtonSize)
                                    .clip(CircleShape)
                                    .pressScaleClickable(onClick = onAttachClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_paperclip),
                                    contentDescription = "Attach",
                                    tint = chatColors.composerIconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        val actionEnabled = if (showSendMode) hasSendableContent else true
                        val actionBg = if (showSendMode && !hasSendableContent) {
                            chatColors.composerActionDisabledBg
                        } else {
                            chatColors.composerActionBg
                        }

                        Box(
                            modifier = Modifier
                                .size(ChatDimens.ComposerActionSize)
                                .graphicsLayer {
                                    scaleX = sendPulseScale.value
                                    scaleY = sendPulseScale.value
                                }
                                .clip(CircleShape)
                                .background(actionBg)
                                .pressScaleClickable(
                                    enabled = actionEnabled
                                ) {
                                    if (showSendMode) {
                                        val now = System.currentTimeMillis()
                                        if (hasSendableContent && now - lastSendClickMs > 250L) {
                                            lastSendClickMs = now
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!reduceMotion) {
                                                scope.launch {
                                                    sendPulseScale.snapTo(0.84f)
                                                    sendPulseScale.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                }
                                            }
                                            onSendClick()
                                        }
                                    } else {
                                        onVoiceDictationClick()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val iconRotation by animateFloatAsState(
                                targetValue = if (showSendMode) 0f else -ChatMotion.ActionSwapRotationDeg,
                                animationSpec = tween(if (reduceMotion) 0 else ChatMotion.ActionSwapDurationMs),
                                label = "composer_action_rotation"
                            )
                            AnimatedContent(
                                targetState = showSendMode,
                                transitionSpec = {
                                    if (reduceMotion) {
                                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    } else {
                                        (fadeIn(tween(ChatMotion.ActionSwapDurationMs)) +
                                            scaleIn(
                                                tween(ChatMotion.ActionSwapDurationMs),
                                                initialScale = ChatMotion.ActionSwapStartScale
                                            )) togetherWith
                                            (fadeOut(tween(ChatMotion.ActionSwapDurationMs)) +
                                                scaleOut(
                                                    tween(ChatMotion.ActionSwapDurationMs),
                                                    targetScale = ChatMotion.ActionSwapStartScale
                                                ))
                                    }
                                },
                                label = "composer_mic_send_swap"
                            ) { isSend ->
                                Icon(
                                    painter = painterResource(
                                        id = if (isSend) R.drawable.ic_arrow_up else R.drawable.ic_mic
                                    ),
                                    contentDescription = if (isSend) stringResource(R.string.dm_send) else "Voice",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            rotationZ = if (isSend) iconRotation else iconRotation + ChatMotion.ActionSwapRotationDeg
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatComposerContextBanner(
    title: String,
    preview: String,
    accentColor: Color,
    onCancel: () -> Unit
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceMuted.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview,
                color = colors.secondaryText,
                style = TextStyle(
                    fontFamily = BuildXFontFamily,
                    fontSize = 13.sp,
                    textDirection = TextDirection.Content
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(colors.cardBackground.copy(alpha = 0.7f))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = stringResource(R.string.dm_cancel),
                tint = colors.secondaryText,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Composer - Light Empty")
@Composable
private fun PreviewChatComposerLightEmpty() {
    BuildXTheme(appearance = app.buildx.chat.data.model.AppAppearance.LIGHT) {
        ChatComposer(
            text = "",
            onTextChange = {},
            stagedAttachments = emptyList(),
            onRemoveAttachment = {},
            replyingTo = null,
            onCancelReply = {},
            editing = null,
            onCancelEdit = {},
            onAttachClick = {},
            onSendClick = {},
            onVoiceDictationClick = {},
            chatColors = BuildXTheme.colors.toChatColors(),
            reduceMotion = true
        )
    }
}

@Preview(showBackground = true, name = "Composer - Dark Typing Arabic")
@Composable
private fun PreviewChatComposerDarkTypingRtl() {
    BuildXTheme(appearance = app.buildx.chat.data.model.AppAppearance.DARK) {
        val chatColors = BuildXTheme.colors.toChatColors()
        Box(modifier = Modifier.background(chatColors.pageBackground)) {
            ChatComposer(
                text = "مرحباً، كيف حالك اليوم؟",
                onTextChange = {},
                stagedAttachments = emptyList(),
                onRemoveAttachment = {},
                replyingTo = null,
                onCancelReply = {},
                editing = null,
                onCancelEdit = {},
                onAttachClick = {},
                onSendClick = {},
                onVoiceDictationClick = {},
                chatColors = chatColors,
                reduceMotion = true
            )
        }
    }
}
