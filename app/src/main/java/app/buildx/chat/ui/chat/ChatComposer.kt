package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Telegram-grade unified ChatBox (46dp min capsule height, 23dp radius -> 20dp expanded) +
 * external 48dp circular morphing Mic / Paper-Plane Send button:
 * - Integrated top reply/edit bar inside the capsule (`42dp` height, `3dp` blue vertical accent bar,
 *   sender title, 1-line preview, close `✕`) using `UnifiedReplyCollapseContainer` (120fps GPU collapse)
 * - Left inside capsule: Emoji toggle (`ic_tg_emoji`)
 * - Center: Multi-line `BasicTextField` with `TextDirection.ContentOrLtr` and safe horizontal padding
 * - Right inside capsule (when empty): Attachment paperclip (`ic_tg_attach`)
 * - External right circular `48dp` button morphing smoothly (`scale` + `rotationZ` spring)
 *   between `ic_tg_mic` (empty) and Telegram's `ic_tg_send` paper-plane (non-empty)
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
    modifier: Modifier = Modifier,
    onInputBoundsChanged: ((Rect) -> Unit)? = null,
    applyImeInsets: Boolean = false,
    onEmojiClick: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var lastSendClickMs by remember { mutableLongStateOf(0L) }
    var lineCount by remember { mutableIntStateOf(1) }

    val composerMinHeight = ChatDimens.ComposerCapsuleMinHeight // 46.dp
    val composerDefaultCorner = 23.dp
    val composerExpandedCorner = ChatDimens.ComposerExpandedCorner // 20.dp

    val hasSendableContent = text.isNotBlank() || stagedAttachments.isNotEmpty()
    val showSendMode = hasSendableContent
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
            composerExpandedCorner
        } else {
            composerDefaultCorner
        },
        animationSpec = if (reduceMotion) tween(0) else ChatMotion.sendFlightSpring(),
        label = "composer_corner"
    )
    val composerShape = RoundedCornerShape(targetCorner)

    val activeBannerTitle = when {
        replyingTo != null -> stringResource(
            R.string.dm_replying_to,
            ChatBidiFormatter.isolateFirstStrong(
                replyingTo.senderDisplayName ?: stringResource(R.string.dm_you)
            )
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

    val composerGlassColor = chatColors.composerBg.copy(
        alpha = if (colors.isDark) ChatGlassTokens.ComposerGlassAlphaDark else ChatGlassTokens.ComposerGlassAlphaLight
    )

    val containerModifier = modifier
        .fillMaxWidth()
        .then(
            if (applyImeInsets) {
                Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            } else {
                Modifier
            }
        )
        .padding(
            start = ChatDimens.ComposerOuterPaddingH,
            end = ChatDimens.ComposerOuterPaddingH,
            top = ChatDimens.ComposerOuterPaddingTop,
            bottom = ChatDimens.ComposerOuterPaddingBottom
        )
        .onGloballyPositioned { coords ->
            onInputBoundsChanged?.invoke(coords.boundsInWindow())
        }

    val sendPulseScale = remember { Animatable(1f) }
    val micToSendSpring = remember(reduceMotion) {
        if (reduceMotion) tween<Float>(0)
        else spring(dampingRatio = 0.65f, stiffness = 500f)
    }
    val sendMorphProgress by animateFloatAsState(
        targetValue = if (hasSendableContent) 1f else 0f,
        animationSpec = micToSendSpring,
        label = "mic_to_send_morph_progress"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = containerModifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Unified Telegram ChatBox Capsule (Reply/Edit banner + Emoji + TextField + Attach)
            ChatGlassCapsuleContainer(
                shape = composerShape,
                glassColor = composerGlassColor,
                borderColor = chatColors.composerBorder,
                shadowColor = chatColors.composerShadow,
                cornerRadius = targetCorner,
                shadowBlur = ChatDimens.ComposerShadowBlur,
                shadowOffsetY = ChatDimens.ComposerShadowOffsetY,
                blurRadius = ChatGlassTokens.ComposerBlurRadius.coerceAtMost(18.dp),
                isDark = colors.isDark,
                borderWidth = ChatDimens.ComposerBorderWidth,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = composerMinHeight)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Integrated Reply / Edit Banner inside capsule
                    UnifiedReplyCollapseContainer(
                        visible = replyingTo != null || editing != null,
                        reduceMotion = reduceMotion
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val isCurrentlyEdit = if (replyingTo != null) {
                                false
                            } else if (editing != null) {
                                true
                            } else {
                                lastBannerIsEdit
                            }
                            ChatComposerContextBanner(
                                title = activeBannerTitle ?: lastBannerTitle,
                                preview = ChatBidiFormatter.isolateFirstStrong(
                                    activeBannerPreview ?: lastBannerPreview
                                ),
                                accentColor = colors.primaryBlue,
                                onCancel = if (isCurrentlyEdit) onCancelEdit else onCancelReply
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(ChatDimens.DividerThickness)
                                    .background(colors.hairlineBorder)
                            )
                        }
                    }

                    // Staged media attachments inside capsule
                    UnifiedReplyCollapseContainer(
                        visible = stagedAttachments.isNotEmpty(),
                        reduceMotion = reduceMotion
                    ) {
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
                    }

                    // Bottom Input Row: [ic_tg_emoji] [Multi-line BasicTextField] [ic_tg_attach when empty]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = composerMinHeight)
                            .padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Left: Emoji toggle icon (ic_tg_emoji)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .pressScaleClickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (onEmojiClick != null) {
                                        onEmojiClick()
                                    } else {
                                        focusRequester.requestFocus()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tg_emoji),
                                contentDescription = "Emoji",
                                tint = chatColors.composerIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Center: Multi-line text field with automatic TextDirection.ContentOrLtr
                        // and safe horizontal padding so Arabic text never collides with icons
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 40.dp)
                                .padding(
                                    start = 4.dp,
                                    end = if (text.isEmpty()) 4.dp else 12.dp,
                                    top = 9.dp,
                                    bottom = 9.dp
                                ),
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
                                    style = ChatTypography.composerPlaceholder,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = placeholderAlpha },
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
                                    textStyle = ChatTypography.composerInput.copy(
                                        color = colors.primaryText,
                                        textAlign = if (effectiveInputRtl) TextAlign.Right else TextAlign.Left,
                                        textDirection = TextDirection.ContentOrLtr
                                    ),
                                    cursorBrush = SolidColor(colors.primaryBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 22.dp, max = ChatDimens.ComposerTextMaxHeight)
                                        .focusRequester(focusRequester)
                                )
                            }
                        }

                        // Right inside capsule (when text is empty): Attachment paperclip (ic_tg_attach)
                        AnimatedVisibility(
                            visible = text.isEmpty(),
                            enter = fadeIn(
                                animationSpec = if (reduceMotion) tween(0) else spring(
                                    dampingRatio = 0.65f,
                                    stiffness = 500f
                                )
                            ) + expandHorizontally(
                                animationSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring()
                            ),
                            exit = fadeOut(
                                animationSpec = if (reduceMotion) tween(0) else tween(ChatMotion.AttachSlideDurationMs)
                            ) + shrinkHorizontally(
                                animationSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring()
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .pressScaleClickable(onClick = onAttachClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tg_attach),
                                    contentDescription = "Attach",
                                    tint = chatColors.composerIconTint,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }

            // External 48dp circular Action Button (outside right edge of capsule)
            // Morphs smoothly between ic_tg_mic (empty) and Telegram paper-plane ic_tg_send (non-empty)
            val actionBg = if (text.isNotEmpty() && !hasSendableContent) {
                chatColors.composerActionDisabledBg
            } else {
                chatColors.composerActionBg
            }
            val externalBtnSize = ChatDimens.ComposerExternalButtonSize // 48.dp

            Box(
                modifier = Modifier
                    .size(externalBtnSize)
                    .graphicsLayer {
                        scaleX = sendPulseScale.value
                        scaleY = sendPulseScale.value
                    }
                    .softShadow(
                        color = chatColors.composerShadow,
                        cornerRadius = externalBtnSize / 2,
                        blurRadius = ChatDimens.ComposerShadowBlur,
                        offsetY = ChatDimens.ComposerShadowOffsetY
                    )
                    .clip(CircleShape)
                    .background(actionBg)
                    .pressScaleClickable(enabled = true) {
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
                                                dampingRatio = 0.65f,
                                                stiffness = 500f
                                            )
                                        )
                                    }
                                }
                                onSendClick()
                            }
                        } else if (text.isEmpty()) {
                            onVoiceDictationClick()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val clamped = sendMorphProgress.coerceIn(0f, 1f)
                val micScale = (1f - clamped).coerceAtLeast(0f)
                val micRotation = -90f * clamped
                val micAlpha = (1f - clamped).coerceIn(0f, 1f)

                val sendScale = clamped.coerceAtLeast(0f)
                val sendRotation = 90f * (1f - clamped)
                val sendAlpha = clamped

                if (micAlpha > 0.005f) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tg_mic),
                        contentDescription = "Voice",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = micScale
                                scaleY = micScale
                                rotationZ = micRotation
                                alpha = micAlpha
                            }
                    )
                }

                if (sendAlpha > 0.005f) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tg_send),
                        contentDescription = stringResource(R.string.dm_send),
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = sendScale
                                scaleY = sendScale
                                rotationZ = sendRotation
                                alpha = sendAlpha
                            }
                    )
                }
            }
        }
    }
}

/**
 * 120fps GPU-driven collapse container for the unified reply/edit banner and attachment strip.
 * Measures the child at natural height while animating clip height and GPU transform via
 * `ChatMotion.sendFlightSpring()` (dampingRatio = 0.82f, stiffness = 420f).
 */
@Composable
private fun UnifiedReplyCollapseContainer(
    visible: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val expandAnim = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(visible, reduceMotion) {
        val target = if (visible) 1f else 0f
        if (reduceMotion) {
            expandAnim.snapTo(target)
        } else {
            expandAnim.animateTo(
                targetValue = target,
                animationSpec = ChatMotion.sendFlightSpring()
            )
        }
    }

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
                        placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer {
                    val p = expandAnim.value.coerceIn(0f, 1f)
                    alpha = p
                    translationY = (1f - p) * 12.dp.toPx()
                    scaleY = 0.92f + (0.08f * p)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            content()
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
            .padding(start = 8.dp, end = 8.dp, top = 6.dp)
            .heightIn(min = ChatDimens.UnifiedReplyBarHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceMuted.copy(alpha = 0.68f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(ChatDimens.UnifiedReplyAccentWidth)
                .height(30.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = accentColor,
                style = ChatTypography.quoteSender,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview,
                color = colors.secondaryText,
                style = ChatTypography.quoteBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.cardBackground.copy(alpha = 0.72f))
                .pressScaleClickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = stringResource(R.string.dm_cancel),
                tint = colors.secondaryText,
                modifier = Modifier.size(13.dp)
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
