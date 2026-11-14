package app.buildx.chat.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.buildx.chat.R
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.ui.chat.ChatColors
import app.buildx.chat.ui.chat.ChatDimens
import app.buildx.chat.ui.chat.ChatGlassCapsuleContainer
import app.buildx.chat.ui.chat.ChatGlassTokens
import app.buildx.chat.ui.chat.ChatMotion
import app.buildx.chat.ui.chat.rememberReduceMotion
import app.buildx.chat.ui.chat.toChatColors
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class DmPopupView {
    object Main : DmPopupView()
    object MuteSelector : DmPopupView()
    object ConfirmDelete : DmPopupView()
    data class ConfirmLeave(val isSoleAdmin: Boolean, val isLastMember: Boolean) : DmPopupView()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DmConversationPopupCard(
    item: DmInboxItem,
    onPinToggle: () -> Unit,
    onMuteSelect: (String?) -> Unit,
    onReadUnreadToggle: () -> Unit,
    onViewProfile: () -> Unit,
    onGroupInfo: () -> Unit,
    onDeleteChat: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val reduceMotion = rememberReduceMotion()
    var currentView by remember { mutableStateOf<DmPopupView>(DmPopupView.Main) }

    val glassCardColor = colors.cardBackground.copy(
        alpha = if (colors.isDark) 0.92f else 0.96f
    )

    ChatGlassCapsuleContainer(
        shape = RoundedCornerShape(16.dp),
        glassColor = glassCardColor,
        borderColor = colors.subtleSurfaceBorder,
        shadowColor = colors.composerShadowColor,
        cornerRadius = 16.dp,
        shadowBlur = 22.dp,
        shadowOffsetY = 6.dp,
        blurRadius = ChatGlassTokens.MenuBlurRadius.coerceAtMost(18.dp),
        isDark = colors.isDark,
        borderWidth = 1.dp,
        modifier = modifier.width(232.dp)
    ) {
        AnimatedContent(
            targetState = currentView,
            transitionSpec = {
                val enter = if (reduceMotion) {
                    fadeIn(tween(0))
                } else {
                    fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                        initialScale = 0.90f,
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f)
                    )
                }
                val exit = if (reduceMotion) {
                    fadeOut(tween(0))
                } else {
                    fadeOut(spring(stiffness = 550f)) + scaleOut(
                        targetScale = 0.94f,
                        animationSpec = spring(stiffness = 550f)
                    )
                }
                (enter togetherWith exit).using(
                    SizeTransform(
                        clip = true,
                        sizeAnimationSpec = { _, _ ->
                            if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring()
                        }
                    )
                )
            },
            label = "popup_morph"
        ) { view ->
            when (view) {
                DmPopupView.Main -> {
                    MainActionsView(
                        item = item,
                        onPin = {
                            onPinToggle()
                            onDismiss()
                        },
                        onMuteClick = {
                            if (item.effectiveIsMuted) {
                                onMuteSelect(null)
                                onDismiss()
                            } else {
                                currentView = DmPopupView.MuteSelector
                            }
                        },
                        onReadUnread = {
                            onReadUnreadToggle()
                            onDismiss()
                        },
                        onViewProfile = {
                            onViewProfile()
                            onDismiss()
                        },
                        onGroupInfo = {
                            onGroupInfo()
                            onDismiss()
                        },
                        onDeleteClick = {
                            currentView = DmPopupView.ConfirmDelete
                        },
                        onLeaveClick = {
                            val isLast = (item.memberCount ?: 2) <= 1
                            currentView = DmPopupView.ConfirmLeave(isSoleAdmin = false, isLastMember = isLast)
                        }
                    )
                }

                DmPopupView.MuteSelector -> {
                    MuteSelectorView(
                        onBack = { currentView = DmPopupView.Main },
                        onSelectDuration = { duration ->
                            onMuteSelect(duration)
                            onDismiss()
                        }
                    )
                }

                DmPopupView.ConfirmDelete -> {
                    ConfirmDeleteView(
                        onCancel = onDismiss,
                        onConfirm = {
                            onDeleteChat()
                            onDismiss()
                        }
                    )
                }

                is DmPopupView.ConfirmLeave -> {
                    ConfirmLeaveView(
                        isSoleAdmin = view.isSoleAdmin,
                        isLastMember = view.isLastMember,
                        onCancel = onDismiss,
                        onConfirm = {
                            onLeaveGroup()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainActionsView(
    item: DmInboxItem,
    onPin: () -> Unit,
    onMuteClick: () -> Unit,
    onReadUnread: () -> Unit,
    onViewProfile: () -> Unit,
    onGroupInfo: () -> Unit,
    onDeleteClick: () -> Unit,
    onLeaveClick: () -> Unit
) {
    val isGroup = item.kind == "group"
    val isMuted = item.effectiveIsMuted

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        // 1. Pin / Unpin
        PopupActionRow(
            label = if (item.isPinned) stringResource(R.string.dm_action_unpin) else stringResource(R.string.dm_action_pin_to_top),
            iconRes = if (item.isPinned) R.drawable.ic_unpin else R.drawable.ic_tg_pin,
            onClick = onPin
        )

        // 2. Mute / Unmute
        PopupActionRow(
            label = if (isMuted) stringResource(R.string.dm_action_unmute) else stringResource(R.string.dm_action_mute),
            iconRes = if (isMuted) R.drawable.ic_tg_unmute else R.drawable.ic_tg_mute,
            onClick = onMuteClick
        )

        // 3. Mark Read / Unread
        val isUnread = item.effectiveIsUnread
        PopupActionRow(
            label = if (isUnread) stringResource(R.string.dm_action_mark_read) else stringResource(R.string.dm_action_mark_unread),
            iconRes = if (isUnread) R.drawable.ic_mark_read else R.drawable.ic_mark_unread,
            onClick = onReadUnread
        )

        // 4. View profile (Direct) or Group info (Group)
        if (isGroup) {
            PopupActionRow(
                label = stringResource(R.string.dm_action_group_info),
                iconRes = R.drawable.ic_info,
                onClick = onGroupInfo
            )
        } else {
            PopupActionRow(
                label = stringResource(R.string.dm_action_view_profile),
                iconRes = R.drawable.ic_user,
                onClick = onViewProfile
            )
        }

        // 5. Destructive: Delete chat (Direct) or Leave group (Group)
        if (isGroup) {
            PopupActionRow(
                label = stringResource(R.string.dm_action_leave_group),
                iconRes = R.drawable.ic_tg_leave,
                isDestructive = true,
                onClick = onLeaveClick
            )
        } else {
            PopupActionRow(
                label = stringResource(R.string.dm_action_delete_chat),
                iconRes = R.drawable.ic_tg_delete,
                isDestructive = true,
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun MuteSelectorView(
    onBack: () -> Unit,
    onSelectDuration: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = stringResource(R.string.dm_back),
                tint = colors.primaryText,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.dm_mute_notifications_title),
                color = colors.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(textDirection = TextDirection.ContentOrLtr)
            )
        }

        HorizontalDivider(
            color = colors.subtleSurfaceBorder,
            thickness = 0.6.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        PopupActionRow(
            label = stringResource(R.string.dm_mute_1_hour),
            iconRes = R.drawable.ic_tg_mute,
            onClick = { onSelectDuration("1h") }
        )
        PopupActionRow(
            label = stringResource(R.string.dm_mute_8_hours),
            iconRes = R.drawable.ic_tg_mute,
            onClick = { onSelectDuration("8h") }
        )
        PopupActionRow(
            label = stringResource(R.string.dm_mute_1_week),
            iconRes = R.drawable.ic_tg_mute,
            onClick = { onSelectDuration("1w") }
        )
        PopupActionRow(
            label = stringResource(R.string.dm_mute_until_turned_on),
            iconRes = R.drawable.ic_tg_mute,
            onClick = { onSelectDuration("infinity") }
        )
    }
}

@Composable
private fun ConfirmDeleteView(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.dm_confirm_delete_title),
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(textDirection = TextDirection.ContentOrLtr)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.dm_confirm_delete_body),
            color = colors.secondaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            style = TextStyle(textDirection = TextDirection.ContentOrLtr)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .pressScaleClickable(onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_confirm_cancel),
                    color = colors.secondaryText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.destructiveRed)
                    .pressScaleClickable(onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_confirm_delete_action),
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ConfirmLeaveView(
    isSoleAdmin: Boolean,
    isLastMember: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = if (isLastMember) {
                stringResource(R.string.dm_confirm_leave_last_member_title)
            } else {
                stringResource(R.string.dm_confirm_leave_group_title)
            },
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(textDirection = TextDirection.ContentOrLtr)
        )

        Spacer(modifier = Modifier.height(6.dp))

        val bodyText = if (isLastMember) {
            stringResource(R.string.dm_confirm_leave_last_member_body)
        } else {
            val base = stringResource(R.string.dm_confirm_leave_group_body)
            if (isSoleAdmin) {
                base + "\n\n" + stringResource(R.string.dm_confirm_leave_admin_note)
            } else base
        }

        Text(
            text = bodyText,
            color = colors.secondaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            style = TextStyle(textDirection = TextDirection.ContentOrLtr)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .pressScaleClickable(onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_confirm_cancel),
                    color = colors.secondaryText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.destructiveRed)
                    .pressScaleClickable(onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isLastMember) {
                        stringResource(R.string.dm_confirm_delete_group_action)
                    } else {
                        stringResource(R.string.dm_confirm_leave_action)
                    },
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PopupActionRow(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val colors = BuildXTheme.colors
    val tint = if (isDestructive) colors.destructiveRed else colors.primaryText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.ContentOrLtr),
            modifier = Modifier.weight(1f)
        )
    }
}

val TelegramQuickReactions: List<String> = listOf("👍", "❤️", "🔥", "😍", "👏", "🤔")

/**
 * Floating horizontal Telegram Emoji Reaction Pill Bar (`44.dp` height, `22.dp` radius)
 * with staggered spring micro-entrance and tap burst physics.
 */
@Composable
fun TelegramReactionPillBar(
    emojis: List<String> = TelegramQuickReactions,
    selectedEmoji: String? = null,
    chatColors: ChatColors = BuildXTheme.colors.toChatColors(),
    progressProvider: () -> Float = { 1f },
    pivotX: Float = 0.5f,
    reduceMotion: Boolean = rememberReduceMotion(),
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
            val isSelected = emoji == selectedEmoji

            LaunchedEffect(Unit) {
                if (!reduceMotion) {
                    delay(index * 22L)
                    emojiAnim.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 480f))
                } else {
                    emojiAnim.snapTo(1f)
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
                                tapBurst.animateTo(1.32f, spring(dampingRatio = 0.5f, stiffness = 700f))
                                tapBurst.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 480f))
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

/**
 * Compact Telegram popup menu (`210.dp` width, `16.dp` radius) with leading `20.dp` icons
 * on the LEFT, `14.dp` gap, and crisp labels on the RIGHT.
 */
@Composable
fun TelegramContextMenuCard(
    items: List<ActionMenuItem>,
    modifier: Modifier = Modifier,
    chatColors: ChatColors = BuildXTheme.colors.toChatColors(),
    seenLabel: String? = null
) {
    val colors = BuildXTheme.colors
    val cardShape = RoundedCornerShape(ChatDimens.ContextMenuCornerRadius)

    Column(
        modifier = modifier
            .width(ChatDimens.ContextMenuWidth)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = ChatDimens.ContextMenuCornerRadius,
                blurRadius = 18.dp,
                offsetY = 6.dp
            )
            .clip(cardShape)
            .background(chatColors.contextMenuBg)
            .border(0.8.dp, chatColors.contextMenuBorder, cardShape)
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
                    painter = painterResource(id = R.drawable.ic_mark_read),
                    contentDescription = null,
                    tint = colors.primaryBlue,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = seenLabel,
                    color = colors.secondaryText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.ContentOrLtr)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(0.6.dp)
                    .background(colors.hairlineBorder)
            )
        }

        items.forEachIndexed { index, item ->
            val tint = if (item.isDestructive) colors.destructiveRed else colors.primaryText
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .pressScaleClickable(onClick = item.onClick)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.ContentOrLtr),
                    modifier = Modifier.weight(1f)
                )
            }
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(0.6.dp)
                        .background(colors.hairlineBorder)
                )
            }
        }
    }
}

/**
 * Foreground-sharp Telegram message focus overlay (`zIndex(30f)`):
 * Renders the selected message bubble razor-sharp above a dimmed/blurred backdrop,
 * paired with a floating Emoji Reaction Pill Bar directly above it and a compact
 * left-icon Telegram Context Menu directly below it.
 */
@Composable
fun TelegramFocusedMessageOverlay(
    visible: Boolean,
    anchorBoundsInRoot: Rect?,
    isOwn: Boolean,
    menuItems: List<ActionMenuItem>,
    selectedReaction: String? = null,
    reactions: List<String> = TelegramQuickReactions,
    seenLabel: String? = null,
    chatColors: ChatColors = BuildXTheme.colors.toChatColors(),
    reduceMotion: Boolean = rememberReduceMotion(),
    onSelectReaction: (String) -> Unit = {},
    onDismiss: () -> Unit,
    sharpMessageContent: @Composable () -> Unit
) {
    val focusAnim = remember { Animatable(0f) }
    var shouldRender by remember { mutableStateOf(visible) }

    LaunchedEffect(visible, reduceMotion) {
        if (visible) {
            shouldRender = true
            if (reduceMotion) {
                focusAnim.snapTo(1f)
            } else {
                focusAnim.animateTo(1f, ChatMotion.interactiveSpring())
            }
        } else {
            if (reduceMotion) {
                focusAnim.snapTo(0f)
            } else {
                focusAnim.animateTo(0f, ChatMotion.fastOutBezierTween(140))
            }
            shouldRender = false
        }
    }

    if (!visible && !shouldRender) return

    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current

    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBarBottomPx = WindowInsets.navigationBars.union(WindowInsets.ime).getBottom(density).toFloat()
    val pillHeightPx = with(density) { ChatDimens.ReactionPillHeight.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val edgeMarginPx = with(density) { 12.dp.toPx() }
    val horizontalMarginPx = with(density) { 16.dp.toPx() }
    val menuWidthPx = with(density) { ChatDimens.ContextMenuWidth.toPx() }

    var rootWidthPx by remember { mutableIntStateOf(0) }
    var rootHeightPx by remember { mutableIntStateOf(0) }
    var menuHeightPx by remember { mutableIntStateOf(with(density) { 210.dp.toPx() }.roundToInt()) }
    var pillWidthPx by remember { mutableIntStateOf(with(density) { 268.dp.toPx() }.roundToInt()) }
    var bubbleHeightPx by remember { mutableIntStateOf(0) }

    val fallbackAnchor = remember(rootWidthPx, rootHeightPx) {
        val w = (rootWidthPx * 0.72f).coerceAtLeast(220f)
        val left = if (isOwn xor isRtl) (rootWidthPx - horizontalMarginPx - w).coerceAtLeast(horizontalMarginPx) else horizontalMarginPx
        val top = (rootHeightPx * 0.42f).coerceAtLeast(statusBarTopPx + pillHeightPx + edgeMarginPx + gapPx)
        Rect(left, top, left + w, top + 64f)
    }
    val anchor = anchorBoundsInRoot ?: fallbackAnchor
    val effectiveBubbleHeightPx = if (bubbleHeightPx > 0) bubbleHeightPx.toFloat() else anchor.height

    val topSafePx = statusBarTopPx + edgeMarginPx + pillHeightPx + gapPx
    val bottomSafePx = (rootHeightPx - navBarBottomPx - edgeMarginPx - menuHeightPx - gapPx - effectiveBubbleHeightPx)
        .coerceAtLeast(topSafePx)
    val targetBubbleTopPx = if (rootHeightPx > 0) {
        anchor.top.coerceIn(topSafePx, bottomSafePx)
    } else {
        anchor.top
    }

    val bubbleAlignEnd = isOwn xor isRtl
    val maxPillX = (rootWidthPx - pillWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
    val pillX = if (bubbleAlignEnd) {
        (anchor.right - pillWidthPx).coerceIn(horizontalMarginPx, maxPillX)
    } else {
        anchor.left.coerceIn(horizontalMarginPx, maxPillX)
    }
    val maxMenuX = (rootWidthPx - menuWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
    val menuX = if (bubbleAlignEnd) {
        (anchor.right - menuWidthPx).coerceIn(horizontalMarginPx, maxMenuX)
    } else {
        anchor.left.coerceIn(horizontalMarginPx, maxMenuX)
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
        // 1. Protective backdrop dim scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = focusAnim.value.coerceIn(0f, 1f) }
                .background(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Color.Black.copy(alpha = 0.24f)
                    } else {
                        Color.Black.copy(alpha = 0.44f)
                    }
                )
        )

        // 2. Sharp foreground message bubble
        Box(
            modifier = Modifier
                .offset {
                    val p = focusAnim.value
                    val currentY = anchor.top + (targetBubbleTopPx - anchor.top) * p
                    IntOffset(anchor.left.roundToInt(), currentY.roundToInt())
                }
                .then(
                    if (anchor.width > 0f) {
                        Modifier.widthIn(max = with(density) { anchor.width.toDp() })
                    } else {
                        Modifier
                    }
                )
                .onSizeChanged { bubbleHeightPx = it.height }
                .graphicsLayer {
                    val p = focusAnim.value.coerceIn(0f, 1f)
                    val s = 1f + 0.02f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (bubbleAlignEnd) 1f else 0f, 0.5f)
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
        ) {
            sharpMessageContent()
        }

        // 3. Floating Emoji Reaction Pill Bar ABOVE the sharp bubble
        TelegramReactionPillBar(
            emojis = reactions,
            selectedEmoji = selectedReaction,
            chatColors = chatColors,
            progressProvider = { focusAnim.value },
            pivotX = if (bubbleAlignEnd) 0.85f else 0.15f,
            reduceMotion = reduceMotion,
            onEmojiClick = { emoji ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectReaction(emoji)
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

        // 4. Compact Left-Icon Context Menu BELOW the sharp bubble
        TelegramContextMenuCard(
            items = menuItems,
            chatColors = chatColors,
            seenLabel = seenLabel,
            modifier = Modifier
                .onSizeChanged { menuHeightPx = it.height }
                .offset {
                    val p = focusAnim.value
                    val currentBubbleTop = anchor.top + (targetBubbleTopPx - anchor.top) * p
                    val maxMenuTop = (rootHeightPx - navBarBottomPx - edgeMarginPx - menuHeightPx)
                        .coerceAtLeast(statusBarTopPx + edgeMarginPx + pillHeightPx + gapPx)
                    val menuTop = (currentBubbleTop + effectiveBubbleHeightPx + gapPx)
                        .coerceAtMost(maxMenuTop)
                    IntOffset(menuX.roundToInt(), menuTop.roundToInt())
                }
                .graphicsLayer {
                    val p = focusAnim.value.coerceIn(0f, 1f)
                    val s = 0.85f + 0.15f * p
                    scaleX = s
                    scaleY = s
                    alpha = p
                    translationY = (1f - p) * -6.dp.toPx()
                    transformOrigin = TransformOrigin(if (bubbleAlignEnd) 0.88f else 0.12f, 0f)
                }
        )
    }
}
