package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val hasSendableContent = text.isNotBlank() || stagedAttachments.isNotEmpty()
    val showSendMode = text.isNotEmpty() || stagedAttachments.isNotEmpty()
    val isTextRtl = remember(text, strings.isRtl) {
        isRtlText(text, defaultRtl = strings.isRtl)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ChatDimens.ComposerOuterPaddingH,
                end = ChatDimens.ComposerOuterPaddingH,
                top = ChatDimens.ComposerOuterPaddingTop,
                bottom = ChatDimens.ComposerOuterPaddingBottom
            )
            .defaultMinSize(minHeight = ChatDimens.ComposerMinHeight)
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
            .softShadow(
                color = chatColors.composerShadow,
                cornerRadius = ChatDimens.ComposerCorner,
                blurRadius = ChatDimens.ComposerShadowBlur,
                offsetY = ChatDimens.ComposerShadowOffsetY
            )
            .clip(RoundedCornerShape(ChatDimens.ComposerCorner))
            .background(chatColors.composerBg)
            .border(
                ChatDimens.ComposerBorderWidth,
                chatColors.composerBorder,
                RoundedCornerShape(ChatDimens.ComposerCorner)
            )
            .padding(start = 6.dp, end = 3.dp, top = 3.dp, bottom = 3.dp)
    ) {
        if (replyingTo != null) {
            ChatComposerContextBanner(
                title = stringResource(R.string.dm_replying_to, replyingTo.senderDisplayName ?: stringResource(R.string.dm_you)),
                preview = replyingTo.content.ifBlank { stringResource(R.string.dm_media_attachment) },
                accentColor = colors.primaryBlue,
                onCancel = onCancelReply
            )
        } else if (editing != null) {
            ChatComposerContextBanner(
                title = stringResource(R.string.dm_editing_message),
                preview = editing.content.ifBlank { stringResource(R.string.dm_media_attachment) },
                accentColor = colors.primaryBlue,
                onCancel = onCancelEdit
            )
        }

        if (stagedAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Multi-line expanding text field aligned on shared 44dp centerline
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = ChatDimens.ComposerActionSize)
                        .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    this@Row.AnimatedVisibility(
                        visible = text.isEmpty(),
                        enter = fadeIn(tween(if (reduceMotion) 0 else ChatMotion.PlaceholderFadeMs)),
                        exit = fadeOut(tween(if (reduceMotion) 0 else ChatMotion.PlaceholderFadeMs))
                    ) {
                        Text(
                            text = stringResource(R.string.dm_composer_placeholder),
                            color = colors.secondaryText,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = BuildXFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isTextRtl) TextAlign.Right else TextAlign.Left
                        )
                    }

                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isTextRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            minLines = 1,
                            maxLines = ChatDimens.ComposerTextMaxLines,
                            textStyle = TextStyle(
                                fontFamily = BuildXFontFamily,
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Start,
                                textDirection = if (isTextRtl) TextDirection.Rtl else TextDirection.Ltr
                            ),
                            cursorBrush = SolidColor(colors.primaryBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 22.dp, max = ChatDimens.ComposerTextMaxHeight)
                                .focusRequester(focusRequester)
                        )
                    }
                }

                // Right action tray centered cleanly on 44dp axis and anchored to bottom on multiline
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
                                        onSendClick()
                                    }
                                } else {
                                    onVoiceDictationClick()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val iconRotation by androidx.compose.animation.core.animateFloatAsState(
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
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
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = stringResource(R.string.dm_cancel),
                tint = colors.secondaryText,
                modifier = Modifier.size(16.dp)
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
