package app.buildx.chat.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Window
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import app.buildx.chat.R
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.ActionMenuPopup
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Floating glass-like pill header matching the Telegram-grade 56dp capsule layout:
 * [Back circle]  [Wide 56dp identity capsule: 40dp avatar + title + animated subtitle/typing]  [Overflow circle]
 */
@Composable
fun ChatHeader(
    state: ChatHeaderUiState,
    chatColors: ChatColors,
    isListScrolled: Boolean,
    reduceMotion: Boolean,
    onBackClick: () -> Unit,
    onPillClick: () -> Unit,
    onViewProfileClick: () -> Unit,
    onOpenInfoClick: () -> Unit,
    onTogglePinConversation: () -> Unit,
    onToggleMuteConversation: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenWallpaperClick: (() -> Unit)? = null,
    applyStatusBarsPadding: Boolean = false
) {
    val colors = BuildXTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var isMoreMenuOpen by remember { mutableStateOf(false) }

    UpdateStatusBarAppearance(
        isLightSurface = chatColors.headerGlassBg.luminance() > 0.5f
    )

    val capsuleHeight = 56.dp
    val sideButtonSize = 48.dp
    val avatarSize = 40.dp
    val capsuleShape = RoundedCornerShape(capsuleHeight / 2)

    val shadowBlur by animateDpAsState(
        targetValue = if (isListScrolled) ChatDimens.HeaderShadowBlurScrolled else ChatDimens.HeaderShadowBlurBase,
        animationSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring(),
        label = "header_shadow_blur"
    )
    val shadowOffsetY by animateDpAsState(
        targetValue = if (isListScrolled) ChatDimens.HeaderShadowOffsetScrolled else ChatDimens.HeaderShadowOffsetBase,
        animationSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring(),
        label = "header_shadow_offset"
    )

    val glassAlpha = if (colors.isDark) {
        ChatGlassTokens.HeaderGlassAlphaDark
    } else {
        ChatGlassTokens.HeaderGlassAlphaLight
    }
    val glassSurfaceColor = chatColors.headerGlassBg.copy(
        alpha = if (isListScrolled) glassAlpha else (glassAlpha - 0.04f).coerceAtLeast(0.72f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyStatusBarsPadding) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .padding(
                horizontal = ChatDimens.HeaderHorizontalPadding,
                vertical = ChatDimens.HeaderVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChatDimens.HeaderItemGap)
        ) {
            // Back circle button (48dp floating glass pill)
            ChatGlassCapsuleContainer(
                shape = CircleShape,
                glassColor = glassSurfaceColor,
                borderColor = chatColors.headerGlassBorder,
                shadowColor = chatColors.headerShadow,
                cornerRadius = sideButtonSize / 2,
                shadowBlur = shadowBlur,
                shadowOffsetY = shadowOffsetY,
                blurRadius = ChatGlassTokens.HeaderBlurRadius,
                isDark = colors.isDark,
                modifier = Modifier
                    .size(sideButtonSize)
                    .pressScaleClickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = stringResource(R.string.dm_back),
                    tint = colors.primaryText,
                    modifier = Modifier
                        .size(21.dp)
                        .then(if (isRtl) Modifier.graphicsLayer(scaleX = -1f) else Modifier)
                )
            }

            // Wide 56dp identity pill with 40dp avatar
            ChatGlassCapsuleContainer(
                shape = capsuleShape,
                glassColor = glassSurfaceColor,
                borderColor = chatColors.headerGlassBorder,
                shadowColor = chatColors.headerShadow,
                cornerRadius = capsuleHeight / 2,
                shadowBlur = shadowBlur,
                shadowOffsetY = shadowOffsetY,
                blurRadius = ChatGlassTokens.HeaderBlurRadius,
                isDark = colors.isDark,
                modifier = Modifier
                    .weight(1f)
                    .height(capsuleHeight)
                    .pressScaleClickable(onClick = onPillClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChatHeaderAvatar(
                        avatarUrl = state.avatarUrl,
                        title = state.title,
                        isDirect = state.isDirect,
                        isOnline = state.isOnline,
                        chatColors = chatColors,
                        size = avatarSize
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.isolatedTitle.ifBlank { state.title },
                                color = colors.primaryText,
                                style = ChatTypography.headerTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (state.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerifiedBadge(size = 14.dp)
                            }
                            if (state.isMuted) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bell_off),
                                    contentDescription = stringResource(R.string.dm_action_mute),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = state.isTyping,
                            transitionSpec = {
                                if (reduceMotion) {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                } else {
                                    fadeIn(tween(ChatMotion.HeaderStateAnimMs)) togetherWith
                                        fadeOut(tween(ChatMotion.HeaderStateAnimMs))
                                }
                            },
                            label = "header_subtitle"
                        ) { typing ->
                            val sub = state.isolatedSubtitle.ifBlank { state.subtitle }
                            if (typing) {
                                ChatTypingSubtitle(chatColors = chatColors, reduceMotion = reduceMotion)
                            } else if (sub.isNotBlank()) {
                                val rawSub = state.subtitle
                                val isHandle = rawSub.startsWith("@")
                                val displaySub = if (isHandle) {
                                    ChatBidiFormatter.isolateLtr(rawSub)
                                } else {
                                    sub
                                }
                                Text(
                                    text = displaySub,
                                    color = colors.secondaryText,
                                    style = ChatTypography.headerSubtitle.copy(
                                        textDirection = if (isHandle) TextDirection.Ltr else TextDirection.ContentOrLtr
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Overflow circle button (48dp floating glass pill)
            ChatGlassCapsuleContainer(
                shape = CircleShape,
                glassColor = glassSurfaceColor,
                borderColor = chatColors.headerGlassBorder,
                shadowColor = chatColors.headerShadow,
                cornerRadius = sideButtonSize / 2,
                shadowBlur = shadowBlur,
                shadowOffsetY = shadowOffsetY,
                blurRadius = ChatGlassTokens.HeaderBlurRadius,
                isDark = colors.isDark,
                modifier = Modifier
                    .size(sideButtonSize)
                    .pressScaleClickable { isMoreMenuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vertical),
                    contentDescription = stringResource(R.string.dm_chat_info),
                    tint = colors.primaryText,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        if (isMoreMenuOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { isMoreMenuOpen = false },
                properties = PopupProperties(focusable = true)
            ) {
                var menuVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { menuVisible = true }
                Box(modifier = Modifier.padding(top = 66.dp, end = 16.dp)) {
                    AnimatedVisibility(
                        visible = menuVisible,
                        enter = if (reduceMotion) {
                            fadeIn(tween(0))
                        } else {
                            fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                                initialScale = 0.84f,
                                transformOrigin = TransformOrigin(if (isRtl) 0.1f else 0.9f, 0f),
                                animationSpec = spring(
                                    dampingRatio = 0.68f,
                                    stiffness = 480f
                                )
                            )
                        },
                        exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.92f)
                    ) {
                        ActionMenuPopup(
                            transformOrigin = TransformOrigin(if (isRtl) 0.1f else 0.9f, 0f),
                            items = buildList {
                                add(
                                    ActionMenuItem(
                                        label = if (state.isPinned) {
                                            stringResource(R.string.dm_unpin_chat)
                                        } else {
                                            stringResource(R.string.dm_pin_chat)
                                        },
                                        iconRes = R.drawable.ic_pin,
                                        onClick = {
                                            isMoreMenuOpen = false
                                            onTogglePinConversation()
                                        }
                                    )
                                )
                                add(
                                    ActionMenuItem(
                                        label = if (state.isMuted) {
                                            stringResource(R.string.dm_unmute_chat)
                                        } else {
                                            stringResource(R.string.dm_mute_chat)
                                        },
                                        iconRes = R.drawable.ic_bell_off,
                                        onClick = {
                                            isMoreMenuOpen = false
                                            onToggleMuteConversation()
                                        }
                                    )
                                )
                                add(
                                    ActionMenuItem(
                                        label = stringResource(R.string.dm_chat_info),
                                        iconRes = R.drawable.ic_info,
                                        onClick = {
                                            isMoreMenuOpen = false
                                            onOpenInfoClick()
                                        }
                                    )
                                )
                                if (onOpenWallpaperClick != null) {
                                    add(
                                        ActionMenuItem(
                                            label = stringResource(R.string.dm_wallpaper),
                                            iconRes = R.drawable.ic_wallpaper,
                                            onClick = {
                                                isMoreMenuOpen = false
                                                onOpenWallpaperClick()
                                            }
                                        )
                                    )
                                }
                                if (state.isDirect && !state.peerHandle.isNullOrBlank()) {
                                    add(
                                        ActionMenuItem(
                                            label = stringResource(R.string.dm_view_profile),
                                            iconRes = R.drawable.ic_user,
                                            onClick = {
                                                isMoreMenuOpen = false
                                                onViewProfileClick()
                                            }
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeaderAvatar(
    avatarUrl: String?,
    title: String,
    isDirect: Boolean,
    isOnline: Boolean,
    chatColors: ChatColors,
    size: Dp = 40.dp
) {
    val colors = BuildXTheme.colors
    val context = LocalContext.current

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(chatColors.receivedBubbleBg),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!isDirect) {
                Text(
                    text = title.firstOrNull()?.uppercase() ?: "",
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_user),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        if (isDirect && isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(ChatDimens.OnlineDotSize + ChatDimens.OnlineDotRingWidth)
                    .clip(CircleShape)
                    .background(chatColors.onlineDotRing)
                    .padding(ChatDimens.OnlineDotRingWidth / 2)
                    .clip(CircleShape)
                    .background(chatColors.onlineDot)
            )
        }
    }
}

@Composable
private fun ChatTypingSubtitle(
    chatColors: ChatColors,
    reduceMotion: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = stringResource(R.string.dm_typing),
            color = chatColors.typingDot,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = BuildXFontFamily
        )
        if (!reduceMotion) {
            val transition = rememberInfiniteTransition(label = "typing_dots")
            for (index in 0..2) {
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 520, delayMillis = index * 140, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "typing_dot_$index"
                )
                Box(
                    modifier = Modifier
                        .size(ChatDimens.TypingDotSize)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(CircleShape)
                        .background(chatColors.typingDot)
                )
            }
        }
    }
}

/**
 * Hardware-accelerated glassmorphic container:
 * Renders a clipped backdrop layer using `RenderEffect.createBlurEffect` via `Modifier.graphicsLayer`
 * on Android 12+ (API 31+) over a translucent frosted gradient + specular highlight border, while
 * keeping foreground children razor-sharp. Falls back to crisp translucent gradient + border on API < 31.
 */
@Composable
internal fun ChatGlassCapsuleContainer(
    shape: Shape,
    glassColor: Color,
    borderColor: Color,
    shadowColor: Color,
    cornerRadius: Dp,
    shadowBlur: Dp,
    shadowOffsetY: Dp,
    blurRadius: Dp,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    borderWidth: Dp = ChatDimens.GlassSurfaceBorderWidth,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val topHighlight = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.42f)
    val bottomTint = if (isDark) Color.Black.copy(alpha = 0.14f) else glassColor.copy(alpha = (glassColor.alpha * 0.94f).coerceIn(0f, 1f))
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.80f),
            borderColor
        )
    )

    Box(
        modifier = modifier
            .softShadow(
                color = shadowColor,
                cornerRadius = cornerRadius,
                blurRadius = shadowBlur,
                offsetY = shadowOffsetY
            )
            .clip(shape),
        contentAlignment = contentAlignment
    ) {
        // Dedicated glass backdrop layer so RenderEffect blur softens the frosted gradient
        // without blurring crisp foreground text, avatars, or icons.
        Box(
            modifier = Modifier
                .matchParentSize()
                .compatRenderEffectBlur(blurRadius = blurRadius, clipShape = shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glassColor,
                            bottomTint
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            topHighlight,
                            Color.Transparent
                        )
                    )
                )
        )

        // Crisp refractive rim border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(borderWidth, borderBrush, shape)
        )

        content()
    }
}

/**
 * Applies hardware-accelerated `android.graphics.RenderEffect.createBlurEffect` via `Modifier.graphicsLayer`
 * on Android 12+ (`Build.VERSION_CODES.S`), safely no-oping on API 26–30 where translucent fallbacks render.
 */
internal fun Modifier.compatRenderEffectBlur(
    blurRadius: Dp,
    clipShape: Shape? = null
): Modifier = this.graphicsLayer {
    if (clipShape != null) {
        shape = clipShape
        clip = true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val px = blurRadius.toPx()
        renderEffect = if (px > 0.5f) {
            RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else {
            null
        }
    }
}

/**
 * Synchronizes status bar icon contrast (`isAppearanceLightStatusBars`) with the live surface luminance
 * underneath the status bar (e.g. wallpaper/header luminance changes across chat, inbox, and dark/light modes).
 */
@Composable
internal fun UpdateStatusBarAppearance(isLightSurface: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = findWindow(view.context)
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightSurface
            }
        }
    }
}

private tailrec fun findWindow(context: Context?): Window? = when (context) {
    null -> null
    is Activity -> context.window
    is ContextWrapper -> findWindow(context.baseContext)
    else -> null
}

@Preview(showBackground = true, name = "Header - Light")
@Composable
private fun PreviewChatHeaderLight() {
    BuildXTheme(appearance = app.buildx.chat.data.model.AppAppearance.LIGHT) {
        ChatHeader(
            state = ChatHeaderUiState(
                title = "Voxin",
                subtitle = "@shopramahoo",
                avatarUrl = null,
                isDirect = true,
                isVerified = true,
                isMuted = false,
                isPinned = false,
                isTyping = false,
                isOnline = true,
                peerHandle = "shopramahoo"
            ),
            chatColors = BuildXTheme.colors.toChatColors(),
            isListScrolled = false,
            reduceMotion = true,
            onBackClick = {},
            onPillClick = {},
            onViewProfileClick = {},
            onOpenInfoClick = {},
            onTogglePinConversation = {},
            onToggleMuteConversation = {}
        )
    }
}

@Preview(showBackground = true, name = "Header - Dark Typing")
@Composable
private fun PreviewChatHeaderDarkTyping() {
    BuildXTheme(appearance = app.buildx.chat.data.model.AppAppearance.DARK) {
        val chatColors = BuildXTheme.colors.toChatColors()
        Box(modifier = Modifier.background(chatColors.pageBackground)) {
            ChatHeader(
                state = ChatHeaderUiState(
                    title = "Build X Alpha",
                    subtitle = "4 members",
                    avatarUrl = null,
                    isDirect = false,
                    isVerified = false,
                    isMuted = true,
                    isPinned = true,
                    isTyping = true,
                    isOnline = false,
                    peerHandle = null
                ),
                chatColors = chatColors,
                isListScrolled = true,
                reduceMotion = true,
                onBackClick = {},
                onPillClick = {},
                onViewProfileClick = {},
                onOpenInfoClick = {},
                onTogglePinConversation = {},
                onToggleMuteConversation = {}
            )
        }
    }
}
