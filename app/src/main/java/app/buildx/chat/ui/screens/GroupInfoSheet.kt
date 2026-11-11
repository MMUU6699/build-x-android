package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoSheet(
    item: DmInboxItem,
    participants: List<DmParticipant>,
    isLoadingParticipants: Boolean,
    onRenameGroup: (String) -> Unit,
    onAddMembersClick: () -> Unit,
    onLeaveGroupClick: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEditingName by remember { mutableStateOf(false) }
    var editedTitle by remember(item.title) { mutableStateOf(item.title ?: "") }

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
            // Group Avatar & Title Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dual Member Avatar (64dp box)
                Box(modifier = Modifier.size(64.dp)) {
                    val m1 = item.memberAvatars.getOrNull(0)
                    val m2 = item.memberAvatars.getOrNull(1)

                    // Back Circle (42dp Top-Start)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
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
                            Text(text = initial, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primaryBlue)
                        }
                    }

                    // Front Circle (44dp Bottom-End) with cutout
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .padding(2.5.dp)
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
                            Text(text = initial, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.secondaryText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isEditingName) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceMuted)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = stringResource(R.string.dm_save),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryBlue,
                            modifier = Modifier
                                .clickable {
                                    if (editedTitle.isNotBlank()) {
                                        onRenameGroup(editedTitle.trim())
                                        isEditingName = false
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.title ?: stringResource(R.string.dm_new_group),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pencil_edit),
                            contentDescription = stringResource(R.string.dm_rename_group),
                            tint = colors.secondaryText,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { isEditingName = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.dm_group_members_format, item.memberCount ?: participants.size),
                    fontSize = 13.sp,
                    color = colors.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Actions: Add Members
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceMuted)
                    .clickable(onClick = onAddMembersClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.primaryBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = null,
                        tint = colors.primaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.dm_add_members),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.7.dp, color = colors.hairlineBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.dm_select_members),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            // Participants List
            if (isLoadingParticipants && participants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.primaryBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(participants, key = { it.userId }) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (!member.handle.isNullOrBlank()) {
                                        onDismiss()
                                        onOpenProfile(member.handle)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(38.dp)) {
                                if (!member.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = member.avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(colors.surfaceMuted),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_user),
                                            contentDescription = null,
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = member.displayName ?: member.handle ?: "Member",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (member.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        VerifiedBadge(size = 12.dp)
                                    }
                                }
                                if (!member.handle.isNullOrBlank()) {
                                    Text(
                                        text = "@${member.handle}",
                                        fontSize = 12.sp,
                                        color = colors.secondaryText
                                    )
                                }
                            }

                            if (member.role == "admin" || member.role == "owner") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceMuted)
                                        .border(0.7.dp, colors.subtleSurfaceBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = member.role.replaceFirstChar { it.uppercase() },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Leave Group Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onLeaveGroupClick)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_trash),
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.dm_leave_group),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE53935)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
