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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
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
 */
@Composable
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
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
 * 1. MarkLoader: Original 8-petal ring mark with purple-to-blue gradient (#C24DFB -> #8842FA -> #4B3EF6).
 * Supports idle breathing/slow rotation (size ~68dp on Home) and fast loader rotation (size ~22dp during reasoning).
 */
@Composable
fun MarkLoader(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    isSpinning: Boolean = false
) {
    val colors = BuildXTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "markRotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpinning) 1400 else 9000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "markAngle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "markPulse"
    )

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            colors.markPurpleStart,
            colors.markVioletMid,
            colors.markBlueEnd
        )
    )

    Canvas(
        modifier = modifier
            .size(size)
            .scale(if (isSpinning) 1f else pulseScale)
            .rotate(angle)
    ) {
        val canvasSize = this.size.minDimension
        val scaleFactor = canvasSize / 96f
        val ringR = 26.5f * scaleFactor
        val petalW = 14.2f * scaleFactor
        val petalL = 24.5f * scaleFactor
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        for (i in 0 until 8) {
            val thetaDeg = i * 45f - 90f
            val thetaRad = Math.toRadians(thetaDeg.toDouble())
            val px = cx + (ringR * cos(thetaRad)).toFloat()
            val py = cy + (ringR * sin(thetaRad)).toFloat()
            val tiltDeg = thetaDeg + 52f

            rotate(degrees = tiltDeg, pivot = Offset(px, py)) {
                drawRoundRect(
                    brush = gradientBrush,
                    topLeft = Offset(px - petalW / 2f, py - petalL / 2f),
                    size = Size(petalW, petalL),
                    cornerRadius = CornerRadius(petalW / 2f, petalW / 2f)
                )
            }
        }
    }
}

/**
 * 2. HeaderCircleButton: 44dp circular icon button with faint soft halo shadow and 1dp hairline border.
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
    Box(
        modifier = modifier
            .size(44.dp)
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 22.dp,
                blurRadius = 10.dp,
                offsetY = 2.dp
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, colors.hairlineBorder, CircleShape)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 3. ActionCapsule: 91dp x 44dp header action capsule with New Chat (edit icon) + More (3-dots icon).
 */
@Composable
fun ActionCapsule(
    onNewChatClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = modifier
            .width(91.dp)
            .height(44.dp)
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 22.dp,
                blurRadius = 10.dp,
                offsetY = 2.dp
            )
            .clip(RoundedCornerShape(22.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(22.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pressScaleClickable(onClick = onNewChatClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_edit_compose),
                contentDescription = "New chat",
                tint = colors.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pressScaleClickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_horizontal),
                contentDescription = "More options",
                tint = colors.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 4. Composer: Multi-state floating composer card (radius 28dp, min height ~100dp, soft diffuse shadow).
 * Handles:
 * - S1/S2 Empty state (Plus circle, Thinking/Instant pill, Mic circle)
 * - S2 Non-empty state (Mic crossfades in 150ms to primaryBlue #0063D3 circular Send button)
 * - S2 Streaming state ("Reply..." hint, Mic stays, Stop button = 20dp rounded blue square inside 36dp light halo)
 * - S9 Incognito state ("Ask Build X privately...", minimal controls, disabled/active send circle)
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
    focusRequester: FocusRequester? = null
) {
    val colors = BuildXTheme.colors
    val hasContent = text.isNotBlank() || stagedAttachments.isNotEmpty()
    val hintText = when {
        isIncognito -> "Ask Build X privately..."
        isStreaming || hasMessages -> "Reply..."
        else -> "Ask Build X..."
    }
    val isRtl = remember(text) { isRtlText(text) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 28.dp,
                blurRadius = 24.dp,
                offsetY = 4.dp
            )
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.hairlineBorder.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Staged attachments preview strip
        if (stagedAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
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

        // Text field area
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp, max = 120.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    ),
                textStyle = TextStyle(
                    fontFamily = BuildXFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.primaryText,
                    lineHeight = 22.sp,
                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                ),
                cursorBrush = SolidColor(colors.primaryBlue),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isRtl) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = hintText,
                                color = colors.secondaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = BuildXFontFamily
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom control row
        if (isIncognito) {
            // S9 Incognito composer row: right-aligned send circle (disabled gray when empty, blue when non-empty)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (enabled) colors.primaryBlue else colors.surfaceMuted
                            )
                            .pressScaleClickable(enabled = enabled, onClick = onSendClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_up),
                            contentDescription = "Send",
                            tint = if (enabled) colors.onPrimaryBlue else colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Plus circle button (36dp, 1dp hairline border)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .border(1.dp, colors.hairlineBorder, CircleShape)
                        .pressScaleClickable(onClick = onPlusClick),
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

                // Thinking / Instant pill (height 36dp, ~101dp wide, 1dp hairline border)
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 100.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.cardBackground)
                        .border(1.dp, colors.hairlineBorder, RoundedCornerShape(18.dp))
                        .pressScaleClickable(onClick = onToggleMode)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mode == ChatMode.THINKING) "Thinking" else "Instant",
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BuildXFontFamily
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Trailing action: Streaming -> Mic + Stop; Non-Streaming -> 150ms crossfade Mic <-> Send
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .border(1.dp, colors.hairlineBorder, CircleShape)
                            .pressScaleClickable(onClick = onMicClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_mic),
                            contentDescription = "Voice input",
                            tint = colors.primaryText,
                            modifier = Modifier.size(18.dp)
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryBlue)
                                    .pressScaleClickable(onClick = onSendClick),
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardBackground)
                                    .border(1.dp, colors.hairlineBorder, CircleShape)
                                    .pressScaleClickable(onClick = onMicClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = "Voice input",
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stop button during streaming: 20dp rounded blue (#0063D3) square inside a 36dp light halo circle.
 */
@Composable
fun StopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colors.stopHaloBackground)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.primaryBlue)
        )
    }
}

/**
 * 5. UserBubble: Right-aligned pill bubble (#F1F3F0, min height 38dp, full pill radius).
 * Resolves text direction per message from the first strong character (Bidi fix).
 */
@Composable
fun UserBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val isRtl = remember(text) { isRtlText(text) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp, max = 300.dp)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp,
                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                )
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
    val currentStep = reasoningSteps.lastOrNull()

    if (isStreamingReasoning) {
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
                    text = currentStep?.title ?: "Thinking",
                    color = colors.secondaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                            .width(2.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colors.markVioletMid,
                                        colors.secondaryText.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Text(
                        text = currentStep?.summary.orEmpty(),
                        color = colors.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    } else if (reasoningSteps.isNotEmpty()) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .pressScaleClickable(onClick = onOpenStepsSheet)
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkLoader(
                size = 20.dp,
                isSpinning = false
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Show thinking",
                color = colors.secondaryText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = "View reasoning steps",
                tint = colors.secondaryText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 7. ActionRow: Thumbs-up, thumbs-down, copy, share (20dp gray outline icons, ~22dp spacing).
 * The share icon fades in ~350ms after the first three icons appear.
 */
@Composable
fun ActionRow(
    rating: Int?,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    var showShare by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(350L)
        showShare = true
    }

    Row(
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
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
        AnimatedVisibility(
            visible = showShare,
            enter = fadeIn(animationSpec = tween(200))
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share),
                contentDescription = "Share response",
                tint = colors.secondaryText,
                modifier = Modifier
                    .size(20.dp)
                    .pressScaleClickable(onClick = onShare)
            )
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
    showDivider: Boolean = false
) {
    val colors = BuildXTheme.colors
    val tint = if (isDestructive) colors.destructiveRed else colors.primaryText

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
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            if (showChevron && !isDestructive) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                thickness = 1.dp,
                color = colors.hairlineBorder
            )
        }
    }
}

/**
 * 10. AppearanceRadioRow: Label left + custom radio circle right (t=64s App appearance).
 */
@Composable
fun AppearanceRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = false
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
            Text(
                text = label,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
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
                color = colors.hairlineBorder
            )
        }
    }
}

/**
 * 11. DrawerNavRow & DrawerChatRow: Used in S7 Navigation Drawer.
 */
@Composable
fun DrawerNavRow(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colors.primaryText,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DrawerChatRow(
    title: String,
    isUnread: Boolean,
    isPinned: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val isRtl = remember(title) { isRtlText(title) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.surfaceMuted else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPinned) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pin),
                contentDescription = "Pinned",
                tint = colors.secondaryText,
                modifier = Modifier
                    .size(15.dp)
                    .padding(end = 2.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 15.5.sp,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (isUnread) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primaryBlue)
            )
        }
    }
}

/**
 * 12. NotificationPermissionCard: Used in S8 Notifications screen (and inline banner).
 */
@Composable
fun NotificationPermissionCard(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Turn on notifications",
    body: String = "Get personalised recommendations, never miss a response and stay up to date on the latest features."
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceMuted)
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
            text = title,
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            color = colors.secondaryText,
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.primaryBlue)
                .pressScaleClickable(onClick = onTurnOn),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Turn on",
                color = colors.onPrimaryBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
