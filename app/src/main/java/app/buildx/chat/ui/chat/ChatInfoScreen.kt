package app.buildx.chat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.buildx.chat.R
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmConversationMetadata
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.screens.BlockAccountDialog
import app.buildx.chat.ui.screens.ReportAccountSheet
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Full-screen Chat Details screen (`dm/{conversationId}/info`).
 * Features a clean centered hero header, four circular quick actions,
 * strictly functional account/group controls, and a 3-column shared photo grid
 * opening [DmMediaViewerDialog].
 */
@Composable
fun ChatInfoScreen(
    detail: DmConversationDetail?,
    currentUserId: String,
    messages: List<DmMessage>,
    isGroupAvatarUploading: Boolean,
    isAddingGroupMembers: Boolean,
    memberCandidates: List<PersonSearchResult>,
    isSearchingMembers: Boolean,
    isSubmittingReport: Boolean,
    reportError: String?,
    isBlocking: Boolean,
    onBackClick: () -> Unit,
    onViewProfile: (String) -> Unit,
    onJumpToMessage: (String) -> Unit,
    onSearchMembers: (String) -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onUpdateGroupAvatar: (Uri) -> Unit,
    onRenameGroup: (String) -> Unit,
    onAddMembers: (List<String>) -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onClearHistory: () -> Unit,
    onReport: (ReportReason, String?) -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchInChat: (() -> Unit)? = null,
    onCreateGroupWithPeer: ((String) -> Unit)? = null,
    onShowSnackbar: (String) -> Unit = {},
    activeWallpaper: ChatWallpaperConfig = ChatWallpaperConfig(),
    onOpenWallpaper: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val chatColors = remember(colors) { colors.toChatColors() }

    val conversation = detail?.conversation
    val isDirect = conversation?.kind != "group"
    val peer = detail?.peerIntro
    val myParticipant = detail?.participants?.firstOrNull { it.userId == currentUserId }
    val isAdmin = myParticipant?.role == "admin"
    val isMuted = myParticipant?.isMuted == true
    val isPinned = myParticipant?.isPinned == true
    val activeParticipants = remember(detail?.participants) {
        detail?.participants?.filter { it.status == "active" }.orEmpty()
    }

    val sharedMedia = remember(messages) {
        ChatMessageMapper.extractSharedMedia(messages).asReversed()
    }

    var viewerMediaMessage by remember { mutableStateOf<DmMessage?>(null) }
    var isRenaming by remember { mutableStateOf(false) }
    var titleDraft by remember(conversation?.title) { mutableStateOf(conversation?.title ?: "") }
    var isAddingMembersOpen by remember { mutableStateOf(false) }
    var isMembersExpanded by remember { mutableStateOf(true) }
    var memberQuery by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var memberToRemove by remember { mutableStateOf<DmParticipant?>(null) }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }

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
            delay(280)
            onSearchMembers(memberQuery)
        }
    }

    val displayTitle = if (isDirect) {
        peer?.displayName?.takeIf { it.isNotBlank() }
            ?: peer?.username?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.dm_chat_info)
    } else {
        conversation?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.dm_group_info)
    }
    val heroAvatarUrl = if (isDirect) peer?.avatarUrl else conversation?.avatarUrl
    val peerFirstLabel = peer?.displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        ?: peer?.handle?.takeIf { it.isNotBlank() }
        ?: displayTitle

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(chatColors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Minimal floating top navigation bar matching Build X HeaderCircleButton grammar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = stringResource(R.string.dm_back),
                onClick = onBackClick,
                backgroundColor = chatColors.headerGlassBg,
                iconTint = colors.primaryText
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Centered Hero Identity Block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .softShadow(
                            color = chatColors.headerShadow,
                            cornerRadius = 46.dp,
                            blurRadius = 16.dp,
                            offsetY = 3.dp
                        )
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .border(1.dp, colors.subtleSurfaceBorder, CircleShape)
                        .then(
                            if (!isDirect && isAdmin && !isGroupAvatarUploading) {
                                Modifier.pressScaleClickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            } else if (isDirect && !peer?.handle.isNullOrBlank()) {
                                Modifier.pressScaleClickable {
                                    peer?.handle?.let(onViewProfile)
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!heroAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = heroAvatarUrl,
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = if (isDirect) R.drawable.ic_user else R.drawable.ic_plus),
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    if (isGroupAvatarUploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            MarkLoader(
                                size = 26.dp,
                                isSpinning = true,
                                tint = Color.White
                            )
                        }
                    }
                }

                if (!isDirect && isAdmin) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .border(1.dp, colors.subtleSurfaceBorder, CircleShape)
                            .pressScaleClickable(enabled = !isGroupAvatarUploading) {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera),
                            contentDescription = stringResource(R.string.dm_change_group_photo),
                            tint = colors.primaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayTitle,
                    color = colors.primaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDirect && peer?.isVerified == true) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VerifiedBadge(size = 18.dp)
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            val heroSubtitle = if (isDirect) {
                peer?.handle?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
            } else {
                stringResource(R.string.dm_members_count, activeParticipants.size)
            }
            if (heroSubtitle.isNotBlank()) {
                Text(
                    text = heroSubtitle,
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    fontFamily = BuildXFontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = !isDirect && isRenaming && isAdmin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceMuted)
                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = BuildXFontFamily,
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textDirection = TextDirection.Content
                        ),
                        cursorBrush = SolidColor(colors.primaryBlue),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.primaryBlue)
                            .clickable {
                                val clean = titleDraft.trim()
                                if (clean.isNotBlank()) {
                                    onRenameGroup(clean)
                                    isRenaming = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = stringResource(R.string.dm_save),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .clickable { isRenaming = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(R.string.dm_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 4 Centered Circular Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isDirect) {
                val peerHandle = peer?.handle?.takeIf { it.isNotBlank() }
                ChatInfoQuickCircleAction(
                    iconRes = R.drawable.ic_user,
                    label = stringResource(R.string.dm_info_profile_label),
                    enabled = peerHandle != null,
                    onClick = { peerHandle?.let(onViewProfile) }
                )
            } else if (isAdmin) {
                ChatInfoQuickCircleAction(
                    iconRes = R.drawable.ic_plus,
                    label = stringResource(R.string.dm_info_add_label),
                    isActive = isAddingMembersOpen,
                    onClick = {
                        isMembersExpanded = true
                        isAddingMembersOpen = !isAddingMembersOpen
                        selectedMemberIds = emptySet()
                    }
                )
            } else {
                ChatInfoQuickCircleAction(
                    iconRes = R.drawable.ic_user,
                    label = stringResource(R.string.dm_members),
                    isActive = isMembersExpanded,
                    onClick = { isMembersExpanded = !isMembersExpanded }
                )
            }

            ChatInfoQuickCircleAction(
                iconRes = R.drawable.ic_search,
                label = stringResource(R.string.dm_info_search_label),
                enabled = onSearchInChat != null || messages.any { it.deletedAt == null && it.content.isNotBlank() },
                onClick = {
                    if (onSearchInChat != null) {
                        onSearchInChat()
                    } else {
                        messages.lastOrNull { it.deletedAt == null }?.id?.let(onJumpToMessage)
                    }
                }
            )

            ChatInfoQuickCircleAction(
                iconRes = if (isMuted) R.drawable.ic_bell_off else R.drawable.ic_bell,
                label = stringResource(if (isMuted) R.string.dm_info_unmute_label else R.string.dm_info_mute_label),
                isActive = isMuted,
                onClick = onToggleMute
            )

            ChatInfoQuickCircleAction(
                iconRes = R.drawable.ic_pin,
                label = stringResource(if (isPinned) R.string.dm_info_unpin_label else R.string.dm_info_pin_label),
                isActive = isPinned,
                onClick = onTogglePin
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Clean Edge-to-Edge Vertical Controls List (Real backend actions only)
        if (onOpenWallpaper != null) {
            val customLabel = stringResource(R.string.dm_wallpaper_custom_label)
            val resolvedWallpaper = remember(detail, activeWallpaper) {
                if (!activeWallpaper.isDefault) {
                    activeWallpaper
                } else {
                    detail?.resolvedWallpaper ?: activeWallpaper
                }
            }
            ChatInfoControlRow(
                iconRes = R.drawable.ic_wallpaper,
                title = stringResource(R.string.dm_wallpaper),
                subtitle = resolvedWallpaper.displayTitle(customLabel),
                onClick = onOpenWallpaper
            )
        }

        if (isDirect) {
            val peerId = peer?.id
            if (!peerId.isNullOrBlank() && onCreateGroupWithPeer != null) {
                ChatInfoControlRow(
                    iconRes = R.drawable.ic_plus,
                    title = stringResource(R.string.dm_create_group_with, peerFirstLabel),
                    onClick = { onCreateGroupWithPeer(peerId) }
                )
            }
            ChatInfoControlRow(
                iconRes = R.drawable.ic_trash,
                title = stringResource(R.string.dm_clear_history),
                subtitle = stringResource(R.string.dm_info_clear_history_subtitle),
                onClick = { showClearConfirm = true }
            )
            ChatInfoControlRow(
                iconRes = R.drawable.ic_flag,
                title = stringResource(R.string.dm_info_report_account),
                subtitle = stringResource(R.string.dm_info_report_account_subtitle),
                onClick = { showReportSheet = true }
            )
            ChatInfoControlRow(
                iconRes = R.drawable.ic_block,
                title = stringResource(R.string.dm_info_block_name, peerFirstLabel),
                isDestructive = true,
                onClick = { showBlockConfirm = true }
            )
        } else {
            if (isAdmin) {
                ChatInfoControlRow(
                    iconRes = R.drawable.ic_pencil_edit,
                    title = stringResource(R.string.dm_info_edit_group_name),
                    onClick = {
                        titleDraft = conversation?.title.orEmpty()
                        isRenaming = !isRenaming
                    }
                )
                ChatInfoControlRow(
                    iconRes = R.drawable.ic_plus,
                    title = stringResource(R.string.dm_add_members),
                    onClick = {
                        isMembersExpanded = true
                        isAddingMembersOpen = !isAddingMembersOpen
                        selectedMemberIds = emptySet()
                    }
                )
            }

            val chevronRotation by animateFloatAsState(
                targetValue = if (isMembersExpanded) 180f else 0f,
                animationSpec = tween(180),
                label = "members_chevron_rotation"
            )
            ChatInfoControlRow(
                iconRes = R.drawable.ic_user,
                title = stringResource(R.string.dm_group_members_format, activeParticipants.size),
                trailing = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                },
                onClick = { isMembersExpanded = !isMembersExpanded }
            )

            AnimatedVisibility(
                visible = isMembersExpanded,
                enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    activeParticipants.forEach { participant ->
                        ChatInfoMemberRow(
                            participant = participant,
                            currentUserId = currentUserId,
                            isViewerAdmin = isAdmin,
                            onViewProfile = onViewProfile,
                            onRequestRemove = { memberToRemove = participant }
                        )
                    }

                    AnimatedVisibility(visible = isAddingMembersOpen && isAdmin) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceMuted)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            BasicTextField(
                                value = memberQuery,
                                onValueChange = { memberQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    textDirection = TextDirection.Content
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                decorationBox = { inner ->
                                    Box {
                                        if (memberQuery.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.dm_search_people),
                                                color = colors.secondaryText,
                                                fontSize = 14.sp
                                            )
                                        }
                                        inner()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.cardBackground)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val available = memberCandidates.filterNot { candidate ->
                                activeParticipants.any { it.userId == candidate.id } || candidate.id == currentUserId
                            }
                            if (isSearchingMembers) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MarkLoader(
                                        size = 20.dp,
                                        isSpinning = true,
                                        tint = colors.primaryBlue
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.heightIn(max = 180.dp)) {
                                    available.take(6).forEach { person ->
                                        val isPicked = person.id in selectedMemberIds
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    selectedMemberIds = if (isPicked) {
                                                        selectedMemberIds - person.id
                                                    } else {
                                                        selectedMemberIds + person.id
                                                    }
                                                }
                                                .padding(vertical = 7.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = person.displayName.takeIf { it.isNotBlank() } ?: "@${person.username}",
                                                color = colors.primaryText,
                                                fontSize = 13.5.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isPicked) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_check),
                                                    contentDescription = null,
                                                    tint = colors.primaryBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.dm_cancel),
                                    color = colors.secondaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            isAddingMembersOpen = false
                                            selectedMemberIds = emptySet()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selectedMemberIds.isNotEmpty() && !isAddingGroupMembers) {
                                                colors.primaryBlue
                                            } else {
                                                colors.cardBackground
                                            }
                                        )
                                        .clickable(enabled = selectedMemberIds.isNotEmpty() && !isAddingGroupMembers) {
                                            onAddMembers(selectedMemberIds.toList())
                                            isAddingMembersOpen = false
                                            selectedMemberIds = emptySet()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.dm_save),
                                        color = if (selectedMemberIds.isNotEmpty()) Color.White else colors.secondaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ChatInfoControlRow(
                iconRes = R.drawable.ic_leave_group,
                title = stringResource(R.string.dm_leave_group),
                subtitle = stringResource(R.string.dm_info_leave_group_subtitle),
                isDestructive = true,
                onClick = { showLeaveConfirm = true }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Shared Media Section: Single clean media icon tab + underline + 3-col square grid
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_image),
                            contentDescription = stringResource(R.string.dm_shared_media),
                            tint = colors.primaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(2.dp)
                            .background(colors.primaryText)
                    )
                }
                Spacer(modifier = Modifier.weight(2f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.7.dp)
                    .background(colors.hairlineBorder)
            )

            if (sharedMedia.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dm_no_shared_photos),
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontFamily = BuildXFontFamily,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    sharedMedia.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowItems.forEach { mediaMsg ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(colors.surfaceMuted)
                                        .clickable { viewerMediaMessage = mediaMsg }
                                ) {
                                    AsyncImage(
                                        model = mediaMsg.attachmentPath,
                                        contentDescription = mediaMsg.content.takeIf { it.isNotBlank() },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    viewerMediaMessage?.let { msg ->
        DmMediaViewerDialog(
            message = msg,
            onDismiss = { viewerMediaMessage = null },
            onShowMessage = onShowSnackbar
        )
    }

    if (showReportSheet) {
        ReportAccountSheet(
            username = peer?.username ?: peer?.handle ?: "",
            isSubmitting = isSubmittingReport,
            errorMessage = reportError,
            onSubmit = { reason, details ->
                onReport(reason, details)
                showReportSheet = false
            },
            onBlock = {
                showReportSheet = false
                showBlockConfirm = true
            },
            onDismiss = { showReportSheet = false }
        )
    }

    if (showBlockConfirm) {
        BlockAccountDialog(
            username = peer?.username ?: peer?.handle ?: "",
            isBlocking = isBlocking,
            onConfirm = {
                showBlockConfirm = false
                onBlock()
            },
            onDismiss = { showBlockConfirm = false }
        )
    }

    if (showClearConfirm) {
        ChatInfoConfirmDialog(
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

    if (showLeaveConfirm) {
        ChatInfoConfirmDialog(
            title = stringResource(R.string.dm_leave_group_title),
            body = stringResource(R.string.dm_leave_group_message),
            confirmLabel = stringResource(R.string.dm_leave_group),
            onConfirm = {
                showLeaveConfirm = false
                onLeaveGroup()
            },
            onDismiss = { showLeaveConfirm = false }
        )
    }

    memberToRemove?.let { target ->
        val name = target.displayName?.takeIf { it.isNotBlank() } ?: target.handle.orEmpty()
        ChatInfoConfirmDialog(
            title = stringResource(R.string.dm_remove_member_title, name),
            body = stringResource(R.string.dm_remove_member_body),
            confirmLabel = stringResource(R.string.dm_remove_member),
            onConfirm = {
                memberToRemove = null
                onRemoveMember(target.userId)
            },
            onDismiss = { memberToRemove = null }
        )
    }
}

@Composable
private fun ChatInfoQuickCircleAction(
    iconRes: Int,
    label: String,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val circleBg by animateColorAsState(
        targetValue = if (isActive) colors.primaryBlue.copy(alpha = 0.14f) else colors.surfaceMuted,
        animationSpec = tween(160),
        label = "quick_action_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled -> colors.secondaryText.copy(alpha = 0.45f)
            isActive -> colors.primaryBlue
            else -> colors.primaryText
        },
        animationSpec = tween(160),
        label = "quick_action_tint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleBg)
                .border(
                    width = 0.8.dp,
                    color = if (isActive) colors.primaryBlue.copy(alpha = 0.42f) else colors.subtleSurfaceBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) colors.primaryBlue else if (enabled) colors.primaryText else colors.secondaryText.copy(alpha = 0.55f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = BuildXFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChatInfoControlRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val contentTint = if (isDestructive) Color(0xFFE53935) else colors.primaryText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = contentTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = contentTint,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BuildXFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = colors.secondaryText,
                    fontSize = 12.5.sp,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun ChatInfoMemberRow(
    participant: DmParticipant,
    currentUserId: String,
    isViewerAdmin: Boolean,
    onViewProfile: (String) -> Unit,
    onRequestRemove: () -> Unit
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !participant.handle.isNullOrBlank()) {
                participant.handle?.takeIf { it.isNotBlank() }?.let(onViewProfile)
            }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape),
            contentAlignment = Alignment.Center
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
                    text = (participant.displayName ?: participant.handle ?: "U").first().uppercase(),
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = participant.displayName?.takeIf { it.isNotBlank() } ?: participant.handle.orEmpty(),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (participant.role == "admin") {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_admin),
                    color = colors.primaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (isViewerAdmin && participant.userId != currentUserId) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.destructiveRed.copy(alpha = 0.12f))
                    .pressScaleClickable(onClick = onRequestRemove)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.dm_remove_member),
                    color = colors.destructiveRed,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ChatInfoConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardBackground)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(24.dp))
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
                    .background(colors.destructiveRed)
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

@Preview(showBackground = true, name = "Chat Info - Light Direct")
@Composable
private fun PreviewChatInfoDirectLight() {
    BuildXTheme(appearance = AppAppearance.LIGHT) {
        ChatInfoScreen(
            detail = DmConversationDetail(
                conversation = DmConversationMetadata(id = "c1", kind = "direct"),
                peerIntro = DmPeerIntro(
                    id = "u2",
                    username = "shopramahoo",
                    handle = "shopramahoo",
                    displayName = "Voxin",
                    isVerified = true
                ),
                participants = listOf(
                    DmParticipant(userId = "me", isMuted = false, isPinned = true),
                    DmParticipant(userId = "u2", handle = "shopramahoo", displayName = "Voxin")
                )
            ),
            currentUserId = "me",
            messages = listOf(
                DmMessage(
                    id = "m1",
                    conversationId = "c1",
                    senderId = "u2",
                    senderDisplayName = "Voxin",
                    content = "Check https://app.buildx.chat for updates",
                    createdAt = "2026-11-12T10:00:00Z"
                )
            ),
            isGroupAvatarUploading = false,
            isAddingGroupMembers = false,
            memberCandidates = emptyList(),
            isSearchingMembers = false,
            isSubmittingReport = false,
            reportError = null,
            isBlocking = false,
            onBackClick = {},
            onViewProfile = {},
            onJumpToMessage = {},
            onSearchMembers = {},
            onToggleMute = {},
            onTogglePin = {},
            onUpdateGroupAvatar = {},
            onRenameGroup = {},
            onAddMembers = {},
            onRemoveMember = {},
            onLeaveGroup = {},
            onClearHistory = {},
            onReport = { _, _ -> },
            onBlock = {},
            onSearchInChat = {},
            onCreateGroupWithPeer = {}
        )
    }
}
