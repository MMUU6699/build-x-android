package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmConversationActionsSheet(
    item: DmInboxItem,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleReadUnread: () -> Unit,
    onViewProfile: ((String) -> Unit)? = null,
    onViewGroupInfo: (() -> Unit)? = null,
    onDeleteOrLeave: () -> Unit
) {
    val colors = BuildXTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        scrimColor = colors.scrimColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Header: 48dp Avatar + Title + Status Subtitle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 48dp Avatar
                Box(modifier = Modifier.size(48.dp)) {
                    if (item.kind == "group") {
                        // Real Overlapping Dual-Member Avatar
                        val m1 = item.memberAvatars.getOrNull(0)
                        val m2 = item.memberAvatars.getOrNull(1)

                        // Back circle (32dp at Top-Start)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.TopStart)
                                .clip(CircleShape)
                                .background(colors.surfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!m1?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = m1?.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                val initial = (m1?.displayName?.firstOrNull() ?: item.title?.firstOrNull() ?: 'G').uppercaseChar().toString()
                                Text(text = initial, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primaryBlue)
                            }
                        }

                        // Front circle (34dp at Bottom-End) with 2dp cutout ring
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(colors.cardBackground)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!m2?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = m2?.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                val initial = (m2?.displayName?.firstOrNull() ?: 'U').uppercaseChar().toString()
                                Text(text = initial, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.secondaryText)
                            }
                        }
                    } else {
                        // Direct Chat 48dp Clean Avatar (NO verified badge on avatar!)
                        val avatarUrl = item.peer?.avatarUrl
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(colors.surfaceMuted)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user),
                                    contentDescription = null,
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (item.kind == "direct") {
                        item.peer?.displayName ?: item.peer?.handle ?: "User"
                    } else {
                        item.title ?: stringResource(R.string.dm_new_group)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.kind == "direct" && item.peer?.isVerified == true) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 15.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val subtitle = if (item.kind == "direct") {
                        val statusList = mutableListOf<String>()
                        if (item.isPinned) statusList.add(stringResource(R.string.dm_pinned_conversations))
                        if (item.isMuted) statusList.add(stringResource(R.string.dm_mute_chat))
                        if (!item.peer?.handle.isNullOrBlank()) {
                            if (statusList.isEmpty()) "@${item.peer.handle}" else "${statusList.joinToString(" · ")} • @${item.peer.handle}"
                        } else {
                            statusList.joinToString(" · ")
                        }
                    } else {
                        stringResource(R.string.dm_group_members_format, item.memberCount ?: 0)
                    }

                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            fontSize = 13.sp,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                color = colors.hairlineBorder,
                thickness = 0.7.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Action 1: Pin / Unpin Conversation
            ActionRow(
                iconRes = R.drawable.ic_pin,
                title = if (item.isPinned) stringResource(R.string.dm_unpin_chat) else stringResource(R.string.dm_pin_chat),
                tint = if (item.isPinned) colors.primaryBlue else colors.primaryText,
                onClick = {
                    onDismiss()
                    onTogglePin()
                }
            )

            // Action 2: Mute / Unmute Notifications
            ActionRow(
                iconRes = if (item.isMuted) R.drawable.ic_bell else R.drawable.ic_volume_off,
                title = if (item.isMuted) stringResource(R.string.dm_unmute_chat) else stringResource(R.string.dm_mute_chat),
                tint = if (item.isMuted) colors.primaryBlue else colors.primaryText,
                onClick = {
                    onDismiss()
                    onToggleMute()
                }
            )

            // Action 3: Mark as Read / Mark as Unread
            val isUnread = item.unreadCount > 0 || item.isMarkedUnread
            ActionRow(
                iconRes = if (isUnread) R.drawable.ic_check else R.drawable.ic_chat_bubble,
                title = if (isUnread) stringResource(R.string.dm_mark_read) else stringResource(R.string.dm_mark_unread),
                tint = colors.primaryText,
                onClick = {
                    onDismiss()
                    onToggleReadUnread()
                }
            )

            // Action 4: Profile / Group Management
            if (item.kind == "direct" && !item.peer?.handle.isNullOrBlank() && onViewProfile != null) {
                ActionRow(
                    iconRes = R.drawable.ic_user,
                    title = stringResource(R.string.dm_view_profile),
                    tint = colors.primaryText,
                    onClick = {
                        onDismiss()
                        onViewProfile(item.peer.handle)
                    }
                )
            } else if (item.kind == "group" && onViewGroupInfo != null) {
                ActionRow(
                    iconRes = R.drawable.ic_user,
                    title = stringResource(R.string.dm_group_info),
                    tint = colors.primaryText,
                    onClick = {
                        onDismiss()
                        onViewGroupInfo()
                    }
                )
            }

            // Action 5: Delete or Leave (destructive red with confirmation dialog)
            ActionRow(
                iconRes = R.drawable.ic_trash,
                title = if (item.kind == "group") stringResource(R.string.dm_leave_group) else stringResource(R.string.dm_delete_chat),
                tint = Color(0xFFE53935),
                isDestructive = true,
                onClick = {
                    showDeleteConfirmDialog = true
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    // Confirmation Dialog before deletion
    if (showDeleteConfirmDialog) {
        val isGroup = item.kind == "group"
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = colors.cardBackground,
            title = {
                Text(
                    text = if (isGroup) stringResource(R.string.dm_leave_group_title) else stringResource(R.string.dm_delete_conv_title),
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            },
            text = {
                Text(
                    text = if (isGroup) stringResource(R.string.dm_leave_group_message) else stringResource(R.string.dm_delete_conv_message),
                    fontSize = 14.5.sp,
                    color = colors.secondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDismiss()
                        onDeleteOrLeave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isGroup) stringResource(R.string.dm_leave_group) else stringResource(R.string.dm_action_delete),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(
                        text = stringResource(R.string.dm_cancel),
                        color = colors.secondaryText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
}

@Composable
private fun ActionRow(
    iconRes: Int,
    title: String,
    tint: Color,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isDestructive) {
                        Color(0xFFE53935).copy(alpha = 0.12f)
                    } else {
                        colors.surfaceMuted
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) Color(0xFFE53935) else colors.primaryText,
            modifier = Modifier.weight(1f)
        )
    }
}
