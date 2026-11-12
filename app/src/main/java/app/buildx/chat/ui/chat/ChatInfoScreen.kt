package app.buildx.chat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.buildx.chat.R
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmConversationMetadata
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.screens.BlockAccountDialog
import app.buildx.chat.ui.screens.ReportAccountSheet
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Dedicated full-screen Chat Info destination (`dm/{conversationId}/info`).
 * Matches the Meta-AI-inspired light/dark visual language with edge-to-edge layout,
 * real backend wiring, and clean state restoration.
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
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val chatColors = remember(colors) { colors.toChatColors() }
    val uriHandler = LocalUriHandler.current
    val isScreenRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

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

    val sharedMedia = remember(messages) { ChatMessageMapper.extractSharedMedia(messages) }
    val sharedFiles = remember(messages) { ChatMessageMapper.extractSharedFiles(messages) }
    val sharedLinks = remember(messages) { ChatMessageMapper.extractSharedLinks(messages) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllMediaGallery by remember { mutableStateOf(false) }
    var previewMediaUrl by remember { mutableStateOf<String?>(null) }

    var isRenaming by remember { mutableStateOf(false) }
    var titleDraft by remember(conversation?.title) { mutableStateOf(conversation?.title ?: "") }
    var isAddingMembersOpen by remember { mutableStateOf(false) }
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

    val searchMatches = remember(messages, searchQuery) {
        val clean = searchQuery.trim()
        if (clean.isBlank()) {
            emptyList()
        } else {
            messages.filter {
                it.deletedAt == null && it.content.contains(clean, ignoreCase = true)
            }.asReversed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(chatColors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Floating pill top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChatDimens.HeaderHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(ChatDimens.HeaderButtonSize)
                    .pressScaleClickable(onClick = onBackClick)
                    .softShadow(
                        color = chatColors.headerShadow,
                        cornerRadius = ChatDimens.HeaderButtonSize / 2,
                        blurRadius = 12.dp,
                        offsetY = 2.dp
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
                        .then(if (isScreenRtl) Modifier.graphicsLayer(scaleX = -1f) else Modifier)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ChatDimens.HeaderCapsuleHeight)
                    .softShadow(
                        color = chatColors.headerShadow,
                        cornerRadius = ChatDimens.HeaderCapsuleHeight / 2,
                        blurRadius = 12.dp,
                        offsetY = 2.dp
                    )
                    .clip(RoundedCornerShape(ChatDimens.HeaderCapsuleHeight / 2))
                    .background(chatColors.headerGlassBg)
                    .border(0.8.dp, chatColors.headerGlassBorder, RoundedCornerShape(ChatDimens.HeaderCapsuleHeight / 2))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.dm_chat_info),
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Top Identity Card
            ChatInfoSectionCard {
                if (isDirect) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceMuted)
                                .border(1.dp, colors.subtleSurfaceBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!peer?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = peer?.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user),
                                    contentDescription = null,
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = peer?.displayName?.takeIf { it.isNotBlank() } ?: peer?.handle.orEmpty(),
                                color = colors.primaryText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (peer?.isVerified == true) {
                                Spacer(modifier = Modifier.width(5.dp))
                                VerifiedBadge(size = 16.dp)
                            }
                        }

                        if (!peer?.handle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "@${peer?.handle}",
                                color = colors.secondaryText,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                        }

                        if (isMuted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MutedStatusBadge()
                        }

                        if (!peer?.bio.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = peer?.bio.orEmpty(),
                                color = colors.secondaryText,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(88.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(colors.surfaceMuted)
                                    .border(1.dp, colors.subtleSurfaceBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!conversation?.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = conversation?.avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = (conversation?.title?.firstOrNull()?.uppercase() ?: "G"),
                                        color = colors.primaryText,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (isGroupAvatarUploading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.pageBackground.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = colors.primaryBlue,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            if (isAdmin) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(colors.primaryBlue)
                                        .border(2.dp, colors.cardBackground, CircleShape)
                                        .pressScaleClickable {
                                            photoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_camera),
                                        contentDescription = stringResource(R.string.dm_change_group_photo),
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isRenaming && isAdmin) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BasicTextField(
                                    value = titleDraft,
                                    onValueChange = { titleDraft = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = BuildXFontFamily,
                                        color = colors.primaryText,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textDirection = if (isRtlText(titleDraft, isScreenRtl)) TextDirection.Rtl else TextDirection.Ltr
                                    ),
                                    cursorBrush = SolidColor(colors.primaryBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surfaceMuted)
                                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                                SmallIconPill(
                                    iconRes = R.drawable.ic_check,
                                    bg = colors.primaryBlue,
                                    tint = Color.White,
                                    onClick = {
                                        val clean = titleDraft.trim()
                                        if (clean.isNotEmpty()) {
                                            onRenameGroup(clean)
                                            isRenaming = false
                                        }
                                    }
                                )
                                SmallIconPill(
                                    iconRes = R.drawable.ic_close,
                                    bg = colors.surfaceMuted,
                                    tint = colors.secondaryText,
                                    onClick = { isRenaming = false }
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = conversation?.title.orEmpty(),
                                    color = colors.primaryText,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isAdmin) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(colors.surfaceMuted)
                                            .pressScaleClickable {
                                                titleDraft = conversation?.title.orEmpty()
                                                isRenaming = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_pencil),
                                            contentDescription = stringResource(R.string.dm_rename_group),
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.dm_members_count, activeParticipants.size),
                            color = colors.secondaryText,
                            fontSize = 13.sp
                        )

                        if (isMuted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MutedStatusBadge()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDirect) {
                    val handle = peer?.handle
                    QuickActionTile(
                        iconRes = R.drawable.ic_user,
                        label = stringResource(R.string.dm_view_profile),
                        enabled = !handle.isNullOrBlank(),
                        onClick = { if (!handle.isNullOrBlank()) onViewProfile(handle) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    QuickActionTile(
                        iconRes = R.drawable.ic_pin,
                        label = if (isPinned) stringResource(R.string.dm_unpin_chat) else stringResource(R.string.dm_pin_chat),
                        enabled = true,
                        onClick = onTogglePin,
                        modifier = Modifier.weight(1f)
                    )
                }

                QuickActionTile(
                    iconRes = R.drawable.ic_search,
                    label = stringResource(R.string.dm_search_in_chat),
                    enabled = true,
                    onClick = { isSearchOpen = !isSearchOpen },
                    modifier = Modifier.weight(1f)
                )

                QuickActionTile(
                    iconRes = if (isMuted) R.drawable.ic_bell else R.drawable.ic_bell_off,
                    label = if (isMuted) stringResource(R.string.dm_unmute_chat) else stringResource(R.string.dm_mute_chat),
                    enabled = true,
                    onClick = onToggleMute,
                    modifier = Modifier.weight(1f)
                )
            }

            // Expandable Search in Chat panel
            AnimatedVisibility(visible = isSearchOpen) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    ChatInfoSectionCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceMuted)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.dm_search_chat_hint),
                                        color = colors.secondaryText,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = BuildXFontFamily,
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        textDirection = if (isRtlText(searchQuery, isScreenRtl)) TextDirection.Rtl else TextDirection.Ltr
                                    ),
                                    cursorBrush = SolidColor(colors.primaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.dm_cancel),
                                    tint = colors.secondaryText,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }

                        if (searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (searchMatches.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.dm_no_search_results),
                                    color = colors.secondaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            } else {
                                searchMatches.take(12).forEach { match ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onJumpToMessage(match.id) }
                                            .padding(horizontal = 6.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = match.senderDisplayName ?: stringResource(R.string.dm_you),
                                                color = colors.primaryBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = match.content,
                                                color = colors.primaryText,
                                                fontSize = 13.5.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = ChatMessageMapper.formatShortTime(match.createdAt),
                                            color = colors.secondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Group Members section
            if (!isDirect) {
                Spacer(modifier = Modifier.height(14.dp))
                ChatInfoSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.dm_members)} (${activeParticipants.size})",
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isAdmin) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.surfaceMuted)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(999.dp))
                                    .pressScaleClickable {
                                        isAddingMembersOpen = !isAddingMembersOpen
                                        selectedMemberIds = emptySet()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dm_add_members),
                                    color = colors.primaryText,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    activeParticipants.forEach { participant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !participant.handle.isNullOrBlank()) {
                                    participant.handle?.takeIf { it.isNotBlank() }?.let(onViewProfile)
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceMuted),
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
                                        text = (participant.displayName ?: participant.handle ?: "?").first().uppercase(),
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
                                        maxLines = 1
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
                            } else if (isAdmin && participant.userId != currentUserId) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.destructiveRed.copy(alpha = 0.12f))
                                        .pressScaleClickable { memberToRemove = participant }
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

                    AnimatedVisibility(visible = isAddingMembersOpen && isAdmin) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
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
                                    fontSize = 14.sp
                                ),
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
                                    CircularProgressIndicator(
                                        color = colors.primaryBlue,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
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
                                                .padding(vertical = 6.dp, horizontal = 4.dp),
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
                                        .background(if (selectedMemberIds.isNotEmpty() && !isAddingGroupMembers) colors.primaryBlue else colors.surfaceMuted)
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

            // Shared Media section with Grid Preview + See All
            Spacer(modifier = Modifier.height(14.dp))
            ChatInfoSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dm_shared_media),
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (sharedMedia.isNotEmpty()) {
                        Text(
                            text = "${stringResource(R.string.dm_see_all)} (${sharedMedia.size})",
                            color = colors.primaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showAllMediaGallery = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (sharedMedia.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dm_no_shared_media),
                        color = colors.secondaryText,
                        fontSize = 13.sp
                    )
                } else {
                    val previewItems = sharedMedia.take(6)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        previewItems.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { msg ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surfaceMuted)
                                            .clickable { previewMediaUrl = msg.attachmentPath }
                                    ) {
                                        AsyncImage(
                                            model = msg.attachmentPath,
                                            contentDescription = null,
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

            // Shared Links & Files section
            Spacer(modifier = Modifier.height(14.dp))
            ChatInfoSectionCard {
                Text(
                    text = stringResource(R.string.dm_shared_links_files),
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (sharedLinks.isEmpty() && sharedFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dm_no_shared_links_files),
                        color = colors.secondaryText,
                        fontSize = 13.sp
                    )
                } else {
                    sharedLinks.take(5).forEach { link ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { runCatching { uriHandler.openUri(link.url) } }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_link),
                                contentDescription = null,
                                tint = colors.primaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = link.url,
                                color = colors.primaryText,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    sharedFiles.take(5).forEach { fileMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    fileMsg.attachmentPath?.let { path -> runCatching { uriHandler.openUri(path) } }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_paperclip),
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = fileMsg.attachmentPath?.substringAfterLast('/') ?: stringResource(R.string.dm_media_attachment),
                                color = colors.primaryText,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Chat Settings section
            Spacer(modifier = Modifier.height(14.dp))
            ChatInfoSectionCard {
                Text(
                    text = stringResource(R.string.dm_chat_settings),
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                ChatInfoActionRow(
                    iconRes = R.drawable.ic_pin,
                    label = if (isPinned) stringResource(R.string.dm_unpin_chat) else stringResource(R.string.dm_pin_chat),
                    onClick = onTogglePin
                )
                HorizontalDivider(color = colors.hairlineBorder)
                ChatInfoActionRow(
                    iconRes = if (isMuted) R.drawable.ic_bell else R.drawable.ic_bell_off,
                    label = if (isMuted) stringResource(R.string.dm_unmute_chat) else stringResource(R.string.dm_mute_chat),
                    onClick = onToggleMute
                )
                HorizontalDivider(color = colors.hairlineBorder)
                ChatInfoActionRow(
                    iconRes = R.drawable.ic_trash,
                    label = stringResource(R.string.dm_clear_history),
                    onClick = { showClearConfirm = true }
                )
            }

            // Privacy & Safety / Danger Zone section
            Spacer(modifier = Modifier.height(14.dp))
            ChatInfoSectionCard {
                Text(
                    text = stringResource(R.string.dm_danger_zone),
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (isDirect) {
                    val peerLabel = peer?.displayName?.takeIf { it.isNotBlank() } ?: peer?.handle.orEmpty()
                    ChatInfoActionRow(
                        iconRes = R.drawable.ic_flag,
                        label = stringResource(R.string.dm_report_user, peerLabel),
                        tint = colors.destructiveRed,
                        onClick = { showReportSheet = true }
                    )
                    HorizontalDivider(color = colors.hairlineBorder)
                    ChatInfoActionRow(
                        iconRes = R.drawable.ic_block,
                        label = stringResource(R.string.dm_block_user, peerLabel),
                        tint = colors.destructiveRed,
                        onClick = { showBlockConfirm = true }
                    )
                } else {
                    ChatInfoActionRow(
                        iconRes = R.drawable.ic_leave_group,
                        label = stringResource(R.string.dm_leave_group),
                        tint = colors.destructiveRed,
                        onClick = { showLeaveConfirm = true }
                    )
                }
            }
        }
    }

    // Full shared media gallery dialog
    if (showAllMediaGallery) {
        Dialog(
            onDismissRequest = { showAllMediaGallery = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(chatColors.pageBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dm_shared_media),
                        color = colors.primaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceMuted)
                            .pressScaleClickable { showAllMediaGallery = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(R.string.dm_cancel),
                            tint = colors.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sharedMedia.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { msg ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surfaceMuted)
                                        .clickable { previewMediaUrl = msg.attachmentPath }
                                ) {
                                    AsyncImage(
                                        model = msg.attachmentPath,
                                        contentDescription = null,
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

    if (!previewMediaUrl.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { previewMediaUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewMediaUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = previewMediaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
private fun ChatInfoSectionCard(
    content: @Composable () -> Unit
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 20.dp,
                blurRadius = 10.dp,
                offsetY = 2.dp
            )
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun MutedStatusBadge() {
    val colors = BuildXTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceMuted)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_bell_off),
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = stringResource(R.string.dm_mute_chat),
            color = colors.secondaryText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun QuickActionTile(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = modifier
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 18.dp,
                blurRadius = 10.dp,
                offsetY = 2.dp
            )
            .clip(RoundedCornerShape(18.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = if (enabled) colors.primaryText else colors.secondaryText.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (enabled) colors.primaryText else colors.secondaryText.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallIconPill(
    iconRes: Int,
    bg: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ChatInfoActionRow(
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
            .padding(vertical = 12.dp, horizontal = 2.dp),
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
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
                    isVerified = true,
                    bio = "Designing Build X & mobile chat experiences."
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
            onBlock = {}
        )
    }
}
