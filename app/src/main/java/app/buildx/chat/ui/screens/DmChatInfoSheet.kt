package app.buildx.chat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.buildx.chat.R
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.ui.chat.DmMediaViewerDialog
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val DestructiveRed = Color(0xFFE53935)

/**
 * Unified Chat Info sheet.
 *
 * Direct conversations show the peer's identity, quick actions, shared media and
 * destructive account actions. Group conversations additionally manage the
 * photo, title, participants and membership.
 */
@Composable
fun DmChatInfoSheet(
    detail: DmConversationDetail,
    isDirect: Boolean,
    currentUserId: String,
    messages: List<DmMessage>,
    isGroupAvatarUploading: Boolean,
    isAddingGroupMembers: Boolean,
    memberCandidates: List<PersonSearchResult>,
    isSearchingMembers: Boolean,
    isSubmittingReport: Boolean,
    reportError: String?,
    isBlocking: Boolean,
    onSearchMembers: (String) -> Unit,
    onViewProfile: (String) -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onUpdateGroupAvatar: (Uri) -> Unit,
    onRenameGroup: (String) -> Unit,
    onAddMembers: (List<String>) -> Unit,
    onLeaveGroup: () -> Unit,
    onClearHistory: () -> Unit,
    onReport: (ReportReason, String?) -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
    onOpenWallpaper: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors

    val conversation = detail.conversation
    val peer = detail.peerIntro
    val myParticipant = detail.participants.firstOrNull { it.userId == currentUserId }
    val activeParticipants = detail.participants.filter { it.status == "active" }
    val sharedMedia = messages.filter { !it.attachmentPath.isNullOrBlank() && it.deletedAt == null }

    var isRenaming by remember { mutableStateOf(false) }
    var titleDraft by remember(conversation?.id) { mutableStateOf(conversation?.title ?: "") }
    var isAddingMembersOpen by remember { mutableStateOf(false) }
    var memberQuery by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf(emptySet<String>()) }
    var showReport by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUpdateGroupAvatar(uri)
    }

    LaunchedEffect(isAddingMembersOpen) {
        if (isAddingMembersOpen) onSearchMembers("")
    }

    LaunchedEffect(memberQuery) {
        if (isAddingMembersOpen) {
            delay(300)
            onSearchMembers(memberQuery)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(colors.cardBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dm_chat_info),
                    color = colors.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .pressScaleClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.dm_cancel),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                if (isDirect) {
                    DirectIdentity(
                        name = peer?.displayName ?: peer?.handle ?: "",
                        handle = peer?.handle,
                        avatarUrl = peer?.avatarUrl,
                        isVerified = peer?.isVerified == true,
                        bio = peer?.bio
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val handle = peer?.handle
                        InfoPill(
                            iconRes = R.drawable.ic_user,
                            label = stringResource(R.string.dm_view_profile),
                            onClick = { if (!handle.isNullOrBlank()) onViewProfile(handle) },
                            modifier = Modifier.weight(1f)
                        )
                        InfoPill(
                            iconRes = R.drawable.ic_bell_off,
                            label = if (myParticipant?.isMuted == true) {
                                stringResource(R.string.dm_unmute_chat)
                            } else {
                                stringResource(R.string.dm_mute_chat)
                            },
                            onClick = onToggleMute,
                            modifier = Modifier.weight(1f)
                        )
                        InfoPill(
                            iconRes = R.drawable.ic_pin,
                            label = if (myParticipant?.isPinned == true) {
                                stringResource(R.string.dm_unpin_chat)
                            } else {
                                stringResource(R.string.dm_pin_chat)
                            },
                            onClick = onTogglePin,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    GroupIdentity(
                        title = conversation?.title ?: "",
                        avatarUrl = conversation?.avatarUrl,
                        memberCount = activeParticipants.size,
                        isRenaming = isRenaming,
                        titleDraft = titleDraft,
                        isUploadingPhoto = isGroupAvatarUploading,
                        onTitleDraftChange = { titleDraft = it },
                        onStartRename = {
                            titleDraft = conversation?.title ?: ""
                            isRenaming = true
                        },
                        onCancelRename = { isRenaming = false },
                        onConfirmRename = {
                            val clean = titleDraft.trim()
                            if (clean.isNotEmpty()) {
                                onRenameGroup(clean)
                                isRenaming = false
                            }
                        },
                        onPhotoClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dm_members),
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surfaceMuted)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(999.dp))
                                .pressScaleClickable {
                                    isAddingMembersOpen = !isAddingMembersOpen
                                    selectedMemberIds = emptySet()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dm_add_members),
                                color = colors.primaryText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    activeParticipants.forEach { participant ->
                        ParticipantRow(
                            participant = participant,
                            onClick = {
                                if (!participant.handle.isNullOrBlank()) onViewProfile(participant.handle)
                            }
                        )
                    }

                    if (isAddingMembersOpen) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AddMembersPicker(
                            query = memberQuery,
                            onQueryChange = { memberQuery = it },
                            candidates = memberCandidates.filterNot { candidate ->
                                activeParticipants.any { it.userId == candidate.id } || candidate.id == currentUserId
                            },
                            selectedIds = selectedMemberIds,
                            isSearching = isSearchingMembers,
                            isSubmitting = isAddingGroupMembers,
                            onToggle = { id ->
                                selectedMemberIds = if (id in selectedMemberIds) {
                                    selectedMemberIds - id
                                } else {
                                    selectedMemberIds + id
                                }
                            },
                            onCancel = {
                                isAddingMembersOpen = false
                                selectedMemberIds = emptySet()
                            },
                            onConfirm = {
                                if (selectedMemberIds.isNotEmpty()) {
                                    onAddMembers(selectedMemberIds.toList())
                                    isAddingMembersOpen = false
                                    selectedMemberIds = emptySet()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = stringResource(R.string.dm_shared_media),
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                SharedMediaGrid(media = sharedMedia)

                Spacer(modifier = Modifier.height(22.dp))

                HorizontalDivider(color = colors.hairlineBorder)

                Spacer(modifier = Modifier.height(6.dp))

                if (onOpenWallpaper != null) {
                    InfoRow(
                        iconRes = R.drawable.ic_wallpaper,
                        label = stringResource(R.string.dm_wallpaper),
                        onClick = {
                            onDismiss()
                            onOpenWallpaper()
                        }
                    )
                }

                InfoRow(
                    iconRes = R.drawable.ic_trash,
                    label = stringResource(R.string.dm_clear_history),
                    onClick = { showClearConfirm = true }
                )

                if (isDirect) {
                    InfoRow(
                        iconRes = R.drawable.ic_flag,
                        label = stringResource(
                            R.string.dm_report_user,
                            peer?.displayName ?: peer?.handle ?: ""
                        ),
                        onClick = { showReport = true }
                    )
                    InfoRow(
                        iconRes = R.drawable.ic_block,
                        label = stringResource(
                            R.string.dm_block_user,
                            peer?.displayName ?: peer?.handle ?: ""
                        ),
                        tint = DestructiveRed,
                        onClick = { showBlockConfirm = true }
                    )
                } else {
                    InfoRow(
                        iconRes = R.drawable.ic_leave_group,
                        label = stringResource(R.string.dm_leave_group),
                        tint = DestructiveRed,
                        onClick = onLeaveGroup
                    )
                }
            }
        }
    }

    if (showReport) {
        ReportAccountSheet(
            username = peer?.username ?: peer?.handle ?: "",
            isSubmitting = isSubmittingReport,
            errorMessage = reportError,
            onSubmit = { reason, details -> onReport(reason, details) },
            onBlock = {
                showReport = false
                showBlockConfirm = true
            },
            onDismiss = { showReport = false }
        )
    }

    if (showBlockConfirm) {
        BlockAccountDialog(
            username = peer?.username ?: peer?.handle ?: "",
            isBlocking = isBlocking,
            onConfirm = {
                onBlock()
                showBlockConfirm = false
                onDismiss()
            },
            onDismiss = { showBlockConfirm = false }
        )
    }

    if (showClearConfirm) {
        InfoConfirmDialog(
            title = stringResource(R.string.dm_clear_history_title),
            body = stringResource(R.string.dm_clear_history_body),
            confirmLabel = stringResource(R.string.dm_clear_history),
            onConfirm = {
                showClearConfirm = false
                onClearHistory()
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun DirectIdentity(
    name: String,
    handle: String?,
    avatarUrl: String?,
    isVerified: Boolean,
    bio: String?
) {
    val colors = BuildXTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_user),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                color = colors.primaryText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isVerified) {
                Spacer(modifier = Modifier.width(5.dp))
                VerifiedBadge(size = 16.dp)
            }
        }

        if (!handle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "@$handle",
                color = colors.secondaryText,
                fontSize = 13.sp,
                maxLines = 1
            )
        }

        if (!bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = bio,
                color = colors.secondaryText,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }
    }
}

@Composable
private fun GroupIdentity(
    title: String,
    avatarUrl: String?,
    memberCount: Int,
    isRenaming: Boolean,
    titleDraft: String,
    isUploadingPhoto: Boolean,
    onTitleDraftChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onConfirmRename: () -> Unit,
    onPhotoClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(84.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colors.surfaceMuted)
                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = title.firstOrNull()?.uppercase() ?: "G",
                        color = colors.primaryText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (isUploadingPhoto) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.pageBackground.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MarkLoader(
                            size = 24.dp,
                            isSpinning = true,
                            tint = colors.primaryBlue
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primaryText)
                    .border(2.dp, colors.pageBackground, CircleShape)
                    .pressScaleClickable(enabled = !isUploadingPhoto, onClick = onPhotoClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = stringResource(R.string.dm_change_group_photo),
                    tint = colors.pageBackground,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isRenaming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceMuted)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (titleDraft.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dm_group_name_hint),
                            color = colors.secondaryText,
                            fontSize = 16.sp
                        )
                    }
                    BasicTextField(
                        value = titleDraft,
                        onValueChange = onTitleDraftChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = if (isRtlText(titleDraft, strings.isRtl)) TextAlign.Right else TextAlign.Left
                        ),
                        cursorBrush = SolidColor(colors.primaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .pressScaleClickable(enabled = titleDraft.isNotBlank(), onClick = onConfirmRename),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = stringResource(R.string.dm_save),
                        tint = colors.primaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .pressScaleClickable(onClick = onCancelRename),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.dm_cancel),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.ifBlank { stringResource(R.string.dm_new_group) },
                    color = colors.primaryText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .pressScaleClickable(onClick = onStartRename),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pencil),
                        contentDescription = stringResource(R.string.dm_rename_group),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.dm_members_count, memberCount),
            color = colors.secondaryText,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun InfoPill(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(colors.surfaceMuted)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(19.dp))
            .pressScaleClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colors.primaryText,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ParticipantRow(
    participant: DmParticipant,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
        ) {
            if (!participant.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = participant.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = participant.displayName?.firstOrNull()?.uppercase()
                        ?: participant.handle?.firstOrNull()?.uppercase()
                        ?: "U",
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = participant.displayName?.takeIf { it.isNotBlank() } ?: participant.handle ?: "",
                    color = colors.primaryText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (participant.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 13.dp)
                }
            }
            if (!participant.handle.isNullOrBlank()) {
                Text(
                    text = "@${participant.handle}",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }

        if (participant.role == "admin" || participant.role == "owner") {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_admin),
                    color = colors.secondaryText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AddMembersPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    candidates: List<PersonSearchResult>,
    selectedIds: Set<String>,
    isSearching: Boolean,
    isSubmitting: Boolean,
    onToggle: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceMuted)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBackground)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dm_search_people),
                            color = colors.secondaryText,
                            fontSize = 13.5.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.primaryText,
                            fontSize = 13.5.sp,
                            textAlign = if (isRtlText(query, strings.isRtl)) TextAlign.Right else TextAlign.Left
                        ),
                        cursorBrush = SolidColor(colors.primaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                MarkLoader(
                    size = 20.dp,
                    isSpinning = true,
                    tint = colors.primaryBlue
                )
            }
        } else if (candidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dm_no_search_results_title),
                    color = colors.secondaryText,
                    fontSize = 13.sp
                )
            }
        } else {
            candidates.take(6).forEach { candidate ->
                val selected = candidate.id in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggle(candidate.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.primaryBlue else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.primaryBlue else colors.subtleSurfaceBorder,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                    ) {
                        if (!candidate.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = candidate.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = candidate.displayName.firstOrNull()?.uppercase() ?: "U",
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = candidate.displayName,
                            color = colors.primaryText,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (candidate.username.isNotBlank()) {
                            Text(
                                text = "@${candidate.username}",
                                color = colors.secondaryText,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .pressScaleClickable(onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_cancel),
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selectedIds.isEmpty() || isSubmitting) {
                            colors.primaryBlue.copy(alpha = 0.35f)
                        } else {
                            colors.primaryBlue
                        }
                    )
                    .pressScaleClickable(
                        enabled = selectedIds.isNotEmpty() && !isSubmitting,
                        onClick = onConfirm
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isSubmitting) {
                    MarkLoader(
                        size = 14.dp,
                        isSpinning = true,
                        tint = Color.White
                    )
                } else {
                    Text(
                        text = stringResource(R.string.dm_add_members),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedMediaGrid(media: List<DmMessage>) {
    val colors = BuildXTheme.colors
    val context = LocalContext.current
    var viewingMediaMessage by remember { mutableStateOf<DmMessage?>(null) }

    if (media.isEmpty()) {
        Text(
            text = stringResource(R.string.dm_no_shared_media),
            color = colors.secondaryText,
            fontSize = 13.sp
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        media.take(9).chunked(3).forEach { rowMedia ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowMedia.forEach { message ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceMuted)
                            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(12.dp))
                            .pressScaleClickable {
                                viewingMediaMessage = message
                            }
                    ) {
                        AsyncImage(
                            model = message.attachmentPath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                repeat(3 - rowMedia.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    viewingMediaMessage?.let { mediaMessage ->
        DmMediaViewerDialog(
            message = mediaMessage,
            onDismiss = { viewingMediaMessage = null },
            onShowMessage = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun InfoRow(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    val colors = BuildXTheme.colors
    val contentColor = tint ?: colors.primaryText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .softShadow(
                    color = colors.composerShadowColor,
                    cornerRadius = 22.dp,
                    blurRadius = 30.dp,
                    offsetY = 10.dp
                )
                .clip(RoundedCornerShape(22.dp))
                .background(colors.cardBackground)
                .padding(22.dp)
        ) {
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                color = colors.secondaryText,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DestructiveRed)
                    .pressScaleClickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = confirmLabel,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceMuted)
                    .pressScaleClickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dm_cancel),
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
