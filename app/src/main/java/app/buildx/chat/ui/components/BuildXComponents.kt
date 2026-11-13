package app.buildx.chat.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.buildx.chat.R
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Press-scale modifier (0.97x over 100ms, no Material ripple).
 * Applied BEFORE clip/background/border so borders are never clipped or invalidated by layer scaling.
 */
@Composable
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "pressScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * 1. MarkLoader: Lume Studio rosette logo with serene, graceful rotation (slower than medium speed).
 * Color: black in light mode, white in dark mode (matching colors.primaryText).
 */
@Composable
fun MarkLoader(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    isSpinning: Boolean = false,
    useBrandGradient: Boolean = false,
    tint: Color = BuildXTheme.colors.primaryText,
    manualRotationDeg: Float? = null,
    contentDescription: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "markRotation")
    val autoAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpinning) 1500 else 14000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "markAngle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isSpinning) 0.95f else 0.985f,
        targetValue = if (isSpinning) 1.03f else 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpinning) 900 else 2400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "markPulse"
    )
    val effectiveScale = if (manualRotationDeg != null) 1f else pulseScale
    val effectiveRotation = manualRotationDeg ?: autoAngle

    Icon(
        painter = painterResource(id = R.drawable.ic_buildx_mark),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .scale(effectiveScale)
            .rotate(effectiveRotation)
    )
}

/**
 * 2. HeaderCircleButton: 44dp circular icon button with real multi-layer soft halo shadow
 * and whisper-soft surface contour matching f_018.png and t_015.png.
 */
@Composable
fun HeaderCircleButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = BuildXTheme.colors.cardBackground,
    iconTint: Color = BuildXTheme.colors.primaryText
) {
    val colors = BuildXTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mirrorIcon = isRtl && (iconRes == R.drawable.ic_back || iconRes == R.drawable.ic_chevron_right)
    Box(
        modifier = modifier
            .size(44.dp)
            .pressScaleClickable(onClick = onClick)
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 22.dp,
                blurRadius = 14.dp,
                offsetY = 2.5.dp
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .scale(scaleX = if (mirrorIcon) -1f else 1f, scaleY = 1f)
        )
    }
}

/**
 * 3. ActionCapsule: 92dp x 44dp header action capsule token with soft halo shadow.
 */
@Composable
fun ActionCapsule(
    @DrawableRes primaryIconRes: Int,
    primaryContentDescription: String,
    onPrimaryClick: () -> Unit,
    @DrawableRes secondaryIconRes: Int,
    secondaryContentDescription: String,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = modifier
            .width(92.dp)
            .height(44.dp)
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 22.dp,
                blurRadius = 14.dp,
                offsetY = 2.5.dp
            )
            .clip(RoundedCornerShape(22.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(22.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pressScaleClickable(onClick = onPrimaryClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = primaryIconRes),
                contentDescription = primaryContentDescription,
                tint = colors.primaryText,
                modifier = Modifier.size(21.dp)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pressScaleClickable(onClick = onSecondaryClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = secondaryIconRes),
                contentDescription = secondaryContentDescription,
                tint = colors.primaryText,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/**
 * 3b. ActionCapsule convenience overload for Chat: New Chat (edit icon) + More (3-dots icon).
 */
@Composable
fun ActionCapsule(
    onNewChatClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = BuildXTheme.strings
    ActionCapsule(
        primaryIconRes = R.drawable.ic_edit_compose,
        primaryContentDescription = strings.newChat,
        onPrimaryClick = onNewChatClick,
        secondaryIconRes = R.drawable.ic_more_horizontal,
        secondaryContentDescription = "More options",
        onSecondaryClick = onMoreClick,
        modifier = modifier
    )
}

/**
 * 3b. ModeSelectorPopup: The floating 2-option banner ("Instant" / "Thinking") observed in
 * reference frames f_018.png..f_026.png (t=30.0s..32.6s) anchored directly above the Composer's
 * bottom control row when the user taps the Thinking/Instant pill.
 */
@Composable
fun ModeSelectorPopup(
    selectedMode: ChatMode,
    onSelectMode: (ChatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 18.dp,
                blurRadius = 22.dp,
                offsetY = 6.dp
            )
            .clip(RoundedCornerShape(18.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
    ) {
        // Row 1: Instant
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectMode(ChatMode.INSTANT) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.instantMode,
                    color = colors.primaryText,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = strings.instantDescription,
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily
                )
            }
            if (selectedMode == ChatMode.INSTANT) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = colors.subtleSurfaceBorder
        )

        // Row 2: Thinking
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectMode(ChatMode.THINKING) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.thinkingMode,
                    color = colors.primaryText,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = strings.thinkingDescription,
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily
                )
            }
            if (selectedMode == ChatMode.THINKING) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 4. Composer: Multi-state floating ChatBox card matching media_1794255314046_f725ed15.png:
 * - Radius 28dp, min height ~104dp, rich diffuse downward drop shadow.
 * - Placeholder "Ask Build X..." in 17sp Bold #6E7274.
 * - Full RTL/LTR support: Arabic input starts flush at the right edge; English starts flush at the left edge.
 * - Bottom controls: 38dp Plus circle, 38dp Thinking/Instant pill (with 1.2dp hairline border + subtle shadow),
 *   and 38dp Mic/Send circle.
 * - Tapping the Thinking/Instant pill opens the floating ModeSelectorPopup banner right above the bottom row.
 */
@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    mode: ChatMode,
    isStreaming: Boolean,
    isIncognito: Boolean,
    stagedAttachments: List<StagedAttachment>,
    onToggleMode: () -> Unit,
    onPlusClick: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onRemoveStagedAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasMessages: Boolean = false,
    focusRequester: FocusRequester? = null,
    isModePopupOpen: Boolean = false,
    onModePillClick: (() -> Unit)? = null,
    onSelectMode: ((ChatMode) -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val hasContent = text.isNotBlank() || stagedAttachments.isNotEmpty()
    val hintText = when {
        isIncognito -> strings.askPrivately
        isStreaming || hasMessages -> strings.replyPlaceholder
        else -> strings.askBuildX
    }
    val isInputRtl = remember(text, strings.isRtl) {
        isRtlText(text, defaultRtl = strings.isRtl)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 28.dp,
                blurRadius = 24.dp,
                offsetY = 5.dp
            )
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(28.dp))
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)
    ) {
        // Staged attachments preview strip
        if (stagedAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stagedAttachments.forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceMuted)
                    ) {
                        if (item.isImage) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_paperclip),
                                    contentDescription = null,
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = item.fileName,
                                    color = colors.primaryText,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clickable { onRemoveStagedAttachment(item.uri.toString()) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Remove attachment",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }

        // Text field area with true per-message/per-locale RTL/LTR alignment
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isInputRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp, max = 128.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    ),
                textStyle = TextStyle(
                    fontFamily = BuildXFontFamily,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.primaryText,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Start,
                    textDirection = if (isInputRtl) TextDirection.Rtl else TextDirection.Ltr
                ),
                cursorBrush = SolidColor(colors.primaryBlue),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = hintText,
                                color = colors.secondaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BuildXFontFamily,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            innerTextField()
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom control row (kept LTR so + stays left and Thinking + Mic stay right as in reference image)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (isIncognito) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isStreaming) {
                        StopButton(onClick = onStopClick)
                    } else {
                        val enabled = hasContent
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .pressScaleClickable(enabled = enabled, onClick = onSendClick)
                                .clip(CircleShape)
                                .background(
                                    if (enabled) colors.primaryBlue else colors.surfaceMuted
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_up),
                                contentDescription = "Send",
                                tint = if (enabled) colors.onPrimaryBlue else colors.secondaryText,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Plus circle button (38dp, 1.2dp hairline border + subtle shadow)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .pressScaleClickable(onClick = onPlusClick)
                            .softShadow(
                                color = colors.innerControlShadowColor,
                                cornerRadius = 19.dp,
                                blurRadius = 6.dp,
                                offsetY = 1.5.dp
                            )
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .border(1.2.dp, colors.hairlineBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_plus),
                            contentDescription = "Add attachment",
                            tint = colors.primaryText,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Thinking / Instant pill (height 38dp, ~104dp wide, 1.2dp hairline border + subtle shadow)
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .widthIn(min = 104.dp)
                            .pressScaleClickable(
                                onClick = {
                                    if (onModePillClick != null) {
                                        onModePillClick()
                                    } else {
                                        onToggleMode()
                                    }
                                }
                            )
                            .softShadow(
                                color = colors.innerControlShadowColor,
                                cornerRadius = 19.dp,
                                blurRadius = 6.dp,
                                offsetY = 1.5.dp
                            )
                            .clip(RoundedCornerShape(19.dp))
                            .background(colors.cardBackground)
                            .border(1.2.dp, colors.hairlineBorder, RoundedCornerShape(19.dp))
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (mode == ChatMode.THINKING) strings.thinkingMode else strings.instantMode,
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BuildXFontFamily
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Trailing action: Streaming -> Mic + Stop; Non-Streaming -> 150ms crossfade Mic <-> Send
                    if (isStreaming) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .pressScaleClickable(onClick = onMicClick)
                                .softShadow(
                                    color = colors.innerControlShadowColor,
                                    cornerRadius = 19.dp,
                                    blurRadius = 6.dp,
                                    offsetY = 1.5.dp
                                )
                                .clip(CircleShape)
                                .background(colors.cardBackground)
                                .border(1.2.dp, colors.hairlineBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_mic),
                                contentDescription = "Voice input",
                                tint = colors.primaryText,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StopButton(onClick = onStopClick)
                    } else {
                        AnimatedContent(
                            targetState = hasContent,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f))
                                    .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f))
                            },
                            label = "micSendSwap"
                        ) { showSend ->
                            if (showSend) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pressScaleClickable(onClick = onSendClick)
                                        .softShadow(
                                            color = colors.primaryBlue.copy(alpha = 0.28f),
                                            cornerRadius = 19.dp,
                                            blurRadius = 8.dp,
                                            offsetY = 2.dp
                                        )
                                        .clip(CircleShape)
                                        .background(colors.primaryBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_arrow_up),
                                        contentDescription = "Send message",
                                        tint = colors.onPrimaryBlue,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pressScaleClickable(onClick = onMicClick)
                                        .softShadow(
                                            color = colors.innerControlShadowColor,
                                            cornerRadius = 19.dp,
                                            blurRadius = 6.dp,
                                            offsetY = 1.5.dp
                                        )
                                        .clip(CircleShape)
                                        .background(colors.cardBackground)
                                        .border(1.2.dp, colors.hairlineBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_mic),
                                        contentDescription = "Voice input",
                                        tint = colors.primaryText,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stop button during streaming: rounded blue (#0063D3) square inside a 38dp light halo circle.
 */
@Composable
fun StopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = modifier
            .size(38.dp)
            .pressScaleClickable(onClick = onClick)
            .clip(CircleShape)
            .background(colors.stopHaloBackground),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.5.dp))
                .background(colors.primaryBlue)
        )
    }
}

/**
 * 5. UserBubble: Right-aligned pill bubble (#F1F3F0, min height 40dp, full pill radius).
 * - Resolves text direction per message from the first strong character (Bidi fix).
 * - Tap to open anchored popup (Copy, Edit, Resend, Select text) with subtle pressed tint.
 * - In-place inline editor when isEditing is true with Cancel/Send pills and replacement hint.
 * - Shows small gray "Edited" label when message has been edited.
 */
@Composable
fun UserBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    UserBubble(
        message = ChatMessage(id = "", conversationId = "", role = "user", content = text),
        modifier = modifier
    )
}

@Composable
fun UserBubble(
    message: ChatMessage,
    isMenuOpen: Boolean = false,
    isEditing: Boolean = false,
    editingText: String = message.content,
    isStreaming: Boolean = false,
    onBubbleClick: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onCopy: () -> Unit = {},
    onStartEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onSendEdit: (String) -> Unit = {},
    onResend: () -> Unit = {},
    onSelectText: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    if (isEditing) {
        var textInput by remember(editingText) { mutableStateOf(editingText) }
        val focusRequester = remember { FocusRequester() }
        val isChanged = textInput.trim() != message.content.trim()
        val canSend = textInput.trim().isNotEmpty() && isChanged

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceMuted)
                    .border(1.dp, colors.subtleSurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        textStyle = TextStyle(
                            fontFamily = BuildXFontFamily,
                            fontSize = 16.sp,
                            color = colors.primaryText,
                            lineHeight = 22.sp,
                            textDirection = TextDirection.Content
                        ),
                        cursorBrush = SolidColor(colors.primaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.editReplacesLaterHint,
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        style = TextStyle(textDirection = TextDirection.Content)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .border(1.dp, colors.subtleSurfaceBorder, CircleShape)
                        .clickable(onClick = onCancelEdit)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.cancelButton,
                        color = colors.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // Send pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (canSend) colors.primaryBlue else colors.primaryBlue.copy(alpha = 0.35f))
                        .clickable(enabled = canSend) { onSendEdit(textInput.trim()) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.sendButton,
                        color = if (canSend) colors.onPrimaryBlue else colors.onPrimaryBlue.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        val isRtl = remember(message.content, strings.isRtl) {
            isRtlText(message.content, defaultRtl = strings.isRtl)
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 48.dp, max = 308.dp)
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (isMenuOpen) colors.surfaceMuted.copy(alpha = 0.82f) else colors.surfaceMuted
                                )
                                .border(
                                    width = if (isMenuOpen) 1.dp else 0.dp,
                                    color = if (isMenuOpen) colors.primaryText.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onBubbleClick
                                )
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = message.content,
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Start,
                                style = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                                )
                            )

                            if (isMenuOpen) {
                                val popupProvider = remember(isRtl) { BubblePopupPositionProvider(isRtl) }
                                Popup(
                                    popupPositionProvider = popupProvider,
                                    onDismissRequest = onDismissMenu,
                                    properties = PopupProperties(
                                        focusable = true,
                                        dismissOnClickOutside = true,
                                        dismissOnBackPress = true
                                    )
                                ) {
                                    var isVisible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { isVisible = true }
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = isVisible,
                                        enter = scaleIn(tween(160)) + fadeIn(tween(160)),
                                        exit = scaleOut(tween(160)) + fadeOut(tween(160))
                                    ) {
                                        val menuItems = buildList {
                                            if (message.content.isNotBlank()) {
                                                add(
                                                    ActionMenuItem(
                                                        label = strings.copyAction,
                                                        iconRes = R.drawable.ic_copy,
                                                        onClick = onCopy
                                                    )
                                                )
                                            }
                                            if (!isStreaming) {
                                                add(
                                                    ActionMenuItem(
                                                        label = strings.editAction,
                                                        iconRes = R.drawable.ic_pencil,
                                                        onClick = onStartEdit
                                                    )
                                                )
                                                add(
                                                    ActionMenuItem(
                                                        label = strings.resendAction,
                                                        iconRes = R.drawable.ic_refresh,
                                                        onClick = onResend
                                                    )
                                                )
                                            }
                                            if (message.content.isNotBlank()) {
                                                add(
                                                    ActionMenuItem(
                                                        label = strings.selectTextAction,
                                                        iconRes = R.drawable.ic_select_text,
                                                        onClick = onSelectText
                                                    )
                                                )
                                            }
                                        }
                                        ActionMenuPopup(items = menuItems)
                                    }
                                }
                            }
                        }
                    }
                }

                if (message.isEdited) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.editedLabel,
                        color = colors.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 6. ThinkingRow:
 * - While reasoning (before answer text arrives): 22dp rotating MarkLoader + bold gray step title +
 *   2-line bold gray summary truncated with ellipsis + 2dp vertical rail (purple at top fading to gray).
 * - Once answer starts / completes (if reasoningSteps is non-empty): collapses to "Show thinking >"
 *   with small static MarkLoader + bold gray label + chevron-right.
 */
@Composable
fun ThinkingRow(
    reasoningSteps: List<ReasoningStep>,
    isStreamingReasoning: Boolean,
    onOpenStepsSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val currentStep = reasoningSteps.lastOrNull()
    val stepRtl = remember(currentStep?.title, currentStep?.summary, strings.isRtl) {
        isRtlText(currentStep?.title ?: currentStep?.summary ?: "", defaultRtl = strings.isRtl)
    }

    if (isStreamingReasoning) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (stepRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MarkLoader(
                        size = 22.dp,
                        isSpinning = true
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentStep?.title ?: strings.thinkingMode,
                        color = colors.secondaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                }
                if (!currentStep?.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(10.dp))
                        // Vertical purple-to-gray gradient rail
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(1.25.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            colors.markVioletMid,
                                            colors.secondaryText.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = currentStep?.summary.orEmpty(),
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    } else if (reasoningSteps.isNotEmpty()) {
        Row(
            modifier = modifier
                .pressScaleClickable(onClick = onOpenStepsSheet)
                .clip(RoundedCornerShape(12.dp))
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkLoader(
                size = 20.dp,
                isSpinning = false
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.showThinking,
                color = colors.secondaryText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = strings.showThinking,
                tint = colors.secondaryText,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (strings.isRtl) 180f else 0f)
            )
        }
    }
}

/**
 * 7. ActionRow: Thumbs-up, thumbs-down, copy, three-dots more button (24dp icon, 48dp touch target).
 */
@Composable
fun ActionRow(
    rating: Int?,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    popupContent: (@Composable () -> Unit)? = null
) {
    val colors = BuildXTheme.colors

    Row(
        modifier = modifier.padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_thumbs_up),
            contentDescription = "Helpful",
            tint = if (rating == 1) colors.primaryBlue else colors.secondaryText,
            modifier = Modifier
                .size(20.dp)
                .pressScaleClickable(onClick = onThumbUp)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_thumbs_down),
            contentDescription = "Not helpful",
            tint = if (rating == -1) colors.primaryBlue else colors.secondaryText,
            modifier = Modifier
                .size(20.dp)
                .pressScaleClickable(onClick = onThumbDown)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_copy),
            contentDescription = "Copy response",
            tint = colors.secondaryText,
            modifier = Modifier
                .size(20.dp)
                .pressScaleClickable(onClick = onCopy)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressScaleClickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_horizontal),
                contentDescription = "More options",
                tint = colors.secondaryText,
                modifier = Modifier.size(24.dp)
            )
            popupContent?.invoke()
        }
    }
}

/**
 * 8. GroupedCard: Rounded 20dp white card surface used in Settings, Appearance, App Info, and Attachment sheet.
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 20.dp,
                blurRadius = 10.dp,
                offsetY = 2.dp
            )
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cardBackground),
        content = content
    )
}

/**
 * 9. SettingsRow: Icon (22dp) + Title (16sp SemiBold) + optional subtitle + trailing chevron.
 */
@Composable
fun SettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    showChevron: Boolean = true,
    showDivider: Boolean = false,
    iconTint: Color? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val tint = when {
        isDestructive -> colors.destructiveRed
        iconTint != null -> iconTint
        else -> colors.primaryText
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = tint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start
                    )
                }
            }
            if (showChevron && !isDestructive) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (strings.isRtl) 180f else 0f)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                thickness = 1.dp,
                color = colors.subtleSurfaceBorder
            )
        }
    }
}

/**
 * 10. AppearanceRadioRow: Label left + custom radio circle right (t=64s App appearance & App language).
 */
@Composable
fun AppearanceRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = false,
    subtitle: String? = null
) {
    val colors = BuildXTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 6.dp else 1.75.dp,
                        color = if (selected) colors.primaryBlue else colors.secondaryText,
                        shape = CircleShape
                    )
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                thickness = 1.dp,
                color = colors.subtleSurfaceBorder
            )
        }
    }
}

/**
 * 11. DrawerNavRow & DrawerChatRow: Used in S7 Navigation Drawer.
 * Redesigned for clean, logical alignment matching t_015.png:
 * - All titles align consistently in a single vertical column.
 * - Pinned conversations show a crisp angled pin icon right next to the title.
 * - Unread conversations show bold text + a trailing 8dp #0063D3 blue dot.
 */
@Composable
fun DrawerNavRow(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    badgeCount: Int = 0
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) colors.surfaceMuted else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colors.primaryText,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(15.dp))
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primaryBlue)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerChatRow(
    title: String,
    isUnread: Boolean,
    isPinned: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onOptionsClick: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val cleanDisplayTitle = remember(title) {
        title.removePrefix("محادثة:").removePrefix("محادثة :").trim().ifEmpty { title }
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) colors.surfaceMuted.copy(alpha = 0.85f) else Color.Transparent
            )
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPinned) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pin),
                contentDescription = "Pinned",
                tint = colors.primaryBlue,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (isLocked) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lock),
                contentDescription = "Locked",
                tint = colors.secondaryText,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = cleanDisplayTitle,
            color = colors.primaryText,
            fontSize = 15.5.sp,
            fontWeight = if (isUnread || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
        if (isUnread) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primaryBlue)
            )
        }
        if (onOptionsClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOptionsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_horizontal),
                    contentDescription = "Options",
                    tint = colors.secondaryText.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 12. NotificationPermissionCard: Used in S8 Notifications screen.
 */
@Composable
fun NotificationPermissionCard(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    body: String? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val resolvedTitle = title ?: strings.turnOnNotificationsTitle
    val resolvedBody = body ?: strings.turnOnNotificationsBody

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceMuted)
            .border(1.dp, colors.subtleSurfaceBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bell),
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Dismiss",
                tint = colors.secondaryText,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDismiss)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = resolvedTitle,
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = resolvedBody,
            color = colors.secondaryText,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .pressScaleClickable(onClick = onTurnOn)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.primaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.turnOnButton,
                color = colors.onPrimaryBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
