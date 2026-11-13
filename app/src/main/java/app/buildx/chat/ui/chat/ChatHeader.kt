package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
 * Floating glass-like pill header matching the reference design:
 * [Back circle]  [Wide identity capsule: avatar + title + animated subtitle/typing]  [Overflow circle]
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
    onOpenWallpaperClick: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var isMoreMenuOpen by remember { mutableStateOf(false) }

    val shadowBlur by animateDpAsState(
        targetValue = if (isListScrolled) ChatDimens.HeaderShadowBlurScrolled else ChatDimens.HeaderShadowBlurBase,
        animationSpec = tween(if (reduceMotion) 0 else ChatMotion.HeaderStateAnimMs),
        label = "header_shadow_blur"
    )
    val shadowOffsetY by animateDpAsState(
        targetValue = if (isListScrolled) ChatDimens.HeaderShadowOffsetScrolled else ChatDimens.HeaderShadowOffsetBase,
        animationSpec = tween(if (reduceMotion) 0 else ChatMotion.HeaderStateAnimMs),
        label = "header_shadow_offset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
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
            // Back circle button
            Box(
                modifier = Modifier
                    .size(ChatDimens.HeaderButtonSize)
                    .pressScaleClickable(onClick = onBackClick)
                    .softShadow(
                        color = chatColors.headerShadow,
                        cornerRadius = ChatDimens.HeaderButtonSize / 2,
                        blurRadius = shadowBlur,
                        offsetY = shadowOffsetY
                    )
                    .clip(CircleShape)
                    .background(chatColors.headerGlassBg)
                    .border(0.8.dp, chatColors.headerGlassBorder, CircleShape),
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

            // Wide identity pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(ChatDimens.HeaderCapsuleHeight)
                    .pressScaleClickable(onClick = onPillClick)
                    .softShadow(
                        color = chatColors.headerShadow,
                        cornerRadius = ChatDimens.HeaderCapsuleHeight / 2,
                        blurRadius = shadowBlur,
                        offsetY = shadowOffsetY
                    )
                    .clip(RoundedCornerShape(ChatDimens.HeaderCapsuleHeight / 2))
                    .background(chatColors.headerGlassBg)
                    .border(
                        0.8.dp,
                        chatColors.headerGlassBorder,
                        RoundedCornerShape(ChatDimens.HeaderCapsuleHeight / 2)
                    )
                    .padding(start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatHeaderAvatar(
                    avatarUrl = state.avatarUrl,
                    title = state.title,
                    isDirect = state.isDirect,
                    chatColors = chatColors
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.title,
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BuildXFontFamily,
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
                        targetState = state.isTyping to state.subtitle,
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                fadeIn(tween(ChatMotion.HeaderStateAnimMs)) togetherWith
                                    fadeOut(tween(ChatMotion.HeaderStateAnimMs))
                            }
                        },
                        label = "header_subtitle"
                    ) { (typing, sub) ->
                        if (typing) {
                            ChatTypingSubtitle(chatColors = chatColors, reduceMotion = reduceMotion)
                        } else if (sub.isNotBlank()) {
                            val displaySub = if (sub.startsWith("@")) "\u2066$sub\u2069" else sub
                            Text(
                                text = displaySub,
                                color = colors.secondaryText,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontFamily = BuildXFontFamily,
                                style = TextStyle(
                                    textDirection = if (sub.startsWith("@")) TextDirection.Ltr else TextDirection.Content
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Overflow circle button
            Box(
                modifier = Modifier
                    .size(ChatDimens.HeaderButtonSize)
                    .pressScaleClickable { isMoreMenuOpen = true }
                    .softShadow(
                        color = chatColors.headerShadow,
                        cornerRadius = ChatDimens.HeaderButtonSize / 2,
                        blurRadius = shadowBlur,
                        offsetY = shadowOffsetY
                    )
                    .clip(CircleShape)
                    .background(chatColors.headerGlassBg)
                    .border(0.8.dp, chatColors.headerGlassBorder, CircleShape),
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
                Box(modifier = Modifier.padding(top = 58.dp, end = 16.dp)) {
                    ActionMenuPopup(
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

@Composable
private fun ChatHeaderAvatar(
    avatarUrl: String?,
    title: String,
    isDirect: Boolean,
    chatColors: ChatColors
) {
    val colors = BuildXTheme.colors
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(ChatDimens.HeaderAvatarSize)
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_user),
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(18.dp)
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
