package app.buildx.chat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow

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
    var currentView by remember { mutableStateOf<DmPopupView>(DmPopupView.Main) }

    Box(
        modifier = modifier
            .width(232.dp)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 16.dp,
                blurRadius = 22.dp,
                offsetY = 6.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
    ) {
        AnimatedContent(
            targetState = currentView,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
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
                            if (item.isMuted) {
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

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        // 1. Pin / Unpin
        PopupActionRow(
            label = if (item.isPinned) stringResource(R.string.dm_action_unpin) else stringResource(R.string.dm_action_pin_to_top),
            iconRes = if (item.isPinned) R.drawable.ic_unpin else R.drawable.ic_pin,
            onClick = onPin
        )

        // 2. Mute / Unmute
        PopupActionRow(
            label = if (item.isMuted) stringResource(R.string.dm_action_unmute) else stringResource(R.string.dm_action_mute),
            iconRes = if (item.isMuted) R.drawable.ic_bell else R.drawable.ic_bell_off,
            onClick = onMuteClick
        )

        // 3. Mark Read / Unread
        val isUnread = item.unreadCount > 0 || item.isMarkedUnread
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
                iconRes = R.drawable.ic_leave_group,
                isDestructive = true,
                onClick = onLeaveClick
            )
        } else {
            PopupActionRow(
                label = stringResource(R.string.dm_action_delete_chat),
                iconRes = R.drawable.ic_trash,
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
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(
            color = colors.subtleSurfaceBorder,
            thickness = 0.6.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        PopupActionRow(
            label = stringResource(R.string.dm_mute_1_hour),
            iconRes = R.drawable.ic_bell_off,
            onClick = { onSelectDuration("1h") }
        )
        PopupActionRow(
            label = stringResource(R.string.dm_mute_8_hours),
            iconRes = R.drawable.ic_bell_off,
            onClick = { onSelectDuration("8h") }
        )
        PopupActionRow(
            label = stringResource(R.string.dm_mute_1_week),
            iconRes = R.drawable.ic_bell_off,
            onClick = { onSelectDuration("1w") }
        )
        PopupActionRow(
            label = stringResource(R.string.dm_mute_until_turned_on),
            iconRes = R.drawable.ic_bell_off,
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
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.dm_confirm_delete_body),
            color = colors.secondaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp
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
                    .clickable(onClick = onCancel)
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
                    .clickable(onClick = onConfirm)
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
            fontWeight = FontWeight.Bold
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
            lineHeight = 18.sp
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
                    .clickable(onClick = onCancel)
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
                    .clickable(onClick = onConfirm)
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
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}
