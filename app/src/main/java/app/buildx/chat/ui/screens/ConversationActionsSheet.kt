package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.ui.theme.BuildXTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationActionsSheet(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleLock: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = BuildXTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            // Header: Title with status badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (conversation.effectivePinned) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pin),
                        contentDescription = "Pinned",
                        tint = colors.primaryBlue,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (conversation.isLocked) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lock),
                        contentDescription = "Locked",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = conversation.cleanTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(
                color = colors.hairlineBorder,
                thickness = 0.8.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Action 1: Pin / Unpin
            ActionRowItem(
                iconRes = R.drawable.ic_pin,
                title = if (conversation.effectivePinned) {
                    stringResource(R.string.chat_action_unpin)
                } else {
                    stringResource(R.string.chat_action_pin)
                },
                tint = if (conversation.effectivePinned) colors.primaryBlue else colors.primaryText,
                onClick = {
                    onDismiss()
                    onTogglePin()
                }
            )

            // Action 2: Lock / Unlock
            ActionRowItem(
                iconRes = R.drawable.ic_lock,
                title = if (conversation.isLocked) {
                    stringResource(R.string.chat_action_unlock)
                } else {
                    stringResource(R.string.chat_action_lock)
                },
                subtitle = stringResource(R.string.chat_action_lock_subtitle),
                tint = if (conversation.isLocked) colors.primaryBlue else colors.primaryText,
                onClick = {
                    onDismiss()
                    onToggleLock()
                }
            )

            // Action 3: Rename
            ActionRowItem(
                iconRes = R.drawable.ic_pencil_edit,
                title = stringResource(R.string.chat_action_rename),
                tint = colors.primaryText,
                onClick = {
                    onDismiss()
                    onRename()
                }
            )

            // Action 4: Delete
            ActionRowItem(
                iconRes = R.drawable.ic_trash,
                title = stringResource(R.string.chat_action_delete),
                tint = colors.destructiveRed,
                isDestructive = true,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ActionRowItem(
    iconRes: Int,
    title: String,
    tint: Color,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isDestructive) {
                        colors.destructiveRed.copy(alpha = 0.12f)
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) colors.destructiveRed else colors.primaryText
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = colors.secondaryText
                )
            }
        }
    }
}
