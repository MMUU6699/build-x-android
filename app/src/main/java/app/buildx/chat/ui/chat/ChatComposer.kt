package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
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
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Single-pill glassmorphic DM Composer (48dp min height, 24dp capsule radius -> 20dp expanded):
 * - Hardware-accelerated glassmorphic backdrop (`RenderEffect.createBlurEffect` on Android 12+ with translucent gradient fallback)
 * - Optional frame-by-frame `WindowInsets.ime` synchronization
 * - Multi-line text field up to 6 lines with smooth spring-driven `animateContentSize`
 * - Auto-detects Arabic/RTL vs English/LTR text direction using `TextDirection.ContentOrLtr`
 * - Attach button slides/fades smoothly out when typing to give maximum text room
 * - Primary circular button continuously morphs between Mic and Send using simultaneous scale (0f..1f)
 *   and rotation (-90f..0f / 0f..90f) spring physics (`spring(dampingRatio = 0.65f, stiffness = 500f)`)
 * - Captures window bounds for Input-to-Chat flight transition
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
    modifier: Modifier = Modifier,
    onInputBoundsChanged: ((Rect) -> Unit)? = null,
    applyImeInsets: Boolean = false
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    var lastSendClickMs by remember { mutableLongStateOf(0L) }
    var lineCount by remember { mutableIntStateOf(1) }

    val composerMinHeight = 48.dp
    val composerDefaultCorner = 24.dp
    val composerExpandedCorner = ChatDimens.ComposerExpandedCorner

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
        animationSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring(),
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
        .defaultMinSize(minHeight = composerMinHeight)
        .onGloballyPositioned { coords ->
            onInputBoundsChanged?.invoke(coords.boundsInWindow())
        }

    // Glassmorphic capsule container owns shadow -> clip -> backdrop blur -> border
    // so animateContentSize on the inner Column never clips or tears the outer shadow.
    ChatGlassCapsuleContainer(
        shape = composerShape,
        glassColor = composerGlassColor,
        borderColor = chatColors.composerBorder,
        shadowColor = chatColors.composerShadow,
        cornerRadius = targetCorner,
        shadowBlur = ChatDimens.ComposerShadowBlur,
        shadowOffsetY = ChatDimens.ComposerShadowOffsetY,
        blurRadius = ChatGlassTokens.ComposerBlurRadius,
        isDark = colors.isDark,
        borderWidth = ChatDimens.ComposerBorderWidth,
        modifier = containerModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = if (reduceMotion) {
                        tween(0)
                    } else {
                        ChatMotion.layoutGestureSpring()
                    }
                )
        ) {
            AnimatedVisibility(
                visible = replyingTo != null || editing != null,
                enter = if (reduceMotion) {
                    fadeIn(tween(0))
                } else {
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = 460f
                        ),
                        initialOffsetY = { fullHeight -> fullHeight / 2 }
                    ) + expandVertically(
                        animationSpec = ChatMotion.layoutGestureSpring(),
                        expandFrom = Alignment.Bottom
                    ) + fadeIn(ChatMotion.interactiveSpring())
                },
                exit = if (reduceMotion) {
                    fadeOut(tween(0))
                } else {
                    slideOutVertically(
                        animationSpec = ChatMotion.layoutGestureSpring(),
                        targetOffsetY = { fullHeight -> fullHeight / 2 }
                    ) + shrinkVertically(
                        animationSpec = ChatMotion.layoutGestureSpring(),
                        shrinkTowards = Alignment.Bottom
                    ) + fadeOut(spring(stiffness = 550f))
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ChatComposerContextBanner(
                        title = activeBannerTitle ?: lastBannerTitle,
                        preview = ChatBidiFormatter.isolateFirstStrong(activeBannerPreview ?: lastBannerPreview),
                        accentColor = colors.primaryBlue,
                        onCancel = if (lastBannerIsEdit) onCancelEdit else onCancelReply
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(ChatDimens.DividerThickness)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 40.dp)
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

                    // Right action tray around 40dp attach & 40dp/42dp morphing action circle
                    Row(
                        modifier = Modifier.heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        val actionBg = if (text.isNotEmpty() && !hasSendableContent) {
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
                                    enabled = true
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
                            val micScale = (1f - sendMorphProgress).coerceAtLeast(0f)
                            val micRotation = -90f * clamped
                            val micAlpha = (1f - clamped).coerceIn(0f, 1f)

                            val sendScale = sendMorphProgress.coerceAtLeast(0f)
                            val sendRotation = 90f * (1f - sendMorphProgress)
                            val sendAlpha = clamped

                            if (micAlpha > 0.005f) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = "Voice",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
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
                                    painter = painterResource(id = R.drawable.ic_arrow_up),
                                    contentDescription = stringResource(R.string.dm_send),
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
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
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceMuted.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(ChatDimens.QuoteAccentBarWidth)
                .height(36.dp)
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
                .size(26.dp)
                .clip(CircleShape)
                .background(colors.cardBackground.copy(alpha = 0.7f))
                .pressScaleClickable(onClick = onCancel),
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
