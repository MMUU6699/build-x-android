package app.buildx.chat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.ActionMenuPopup
import app.buildx.chat.ui.components.AttachmentSheetContent
import app.buildx.chat.ui.components.BubblePopupPositionProvider
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.VoiceDictationBar
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun DmConversationScreen(
    uiState: BuildXUiState,
    conversationId: String,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onSendMessage: () -> Unit,
    onComposerTextChange: (String) -> Unit,
    onAddStagedAttachment: (StagedAttachment) -> Unit,
    onRemoveStagedAttachment: (StagedAttachment) -> Unit,
    onReplyToMessage: (DmMessage) -> Unit,
    onCancelReply: () -> Unit,
    onEditMessage: (DmMessage) -> Unit,
    onCancelEdit: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onTogglePinMessage: (String) -> Unit,
    onTogglePinConversation: () -> Unit,
    onToggleMuteConversation: () -> Unit,
    onReportMessage: (DmMessage) -> Unit,
    modifier: Modifier = Modifier,
    onVoiceDictationClick: () -> Unit = {},
    onCancelVoiceDictation: () -> Unit = {},
    onConfirmVoiceTranscript: () -> Unit = {},
    onLoadRecentImages: () -> Unit = {},
    onUpdateGroupAvatar: (android.net.Uri) -> Unit = {},
    onRenameGroup: (String) -> Unit = {},
    onAddGroupMembers: (List<String>) -> Unit = {},
    onLeaveGroup: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    onReportUser: (ReportReason, String?) -> Unit = { _, _ -> },
    onSearchPeopleForGroup: (String) -> Unit = {}
) {
    val colors = BuildXTheme.colors
    val listState = rememberLazyListState()

    val detail = uiState.activeDmDetail
    val conversation = detail?.conversation
    val isDirect = conversation?.kind != "group"
    val peerIntro = detail?.peerIntro
    val currentUserId = uiState.profile?.id ?: ""

    var isAttachmentSheetOpen by remember { mutableStateOf(false) }
    var activeMenuMessage by remember { mutableStateOf<DmMessage?>(null) }
    var isChatInfoSheetOpen by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val fileName = "photo_${System.currentTimeMillis()}.jpg"
            onAddStagedAttachment(
                StagedAttachment(
                    uri = uri,
                    fileName = fileName,
                    mimeType = "image/jpeg",
                    sizeBytes = 0L
                )
            )
        }
    }

    // Auto-scroll to bottom on new messages and keyboard elevation
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, uiState.dmMessages.size) {
        val totalCount = (if (isDirect && peerIntro != null) 1 else 0) + uiState.dmMessages.size
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            DmHeaderBar(
                detail = detail,
                isDirect = isDirect,
                currentUserId = currentUserId,
                onBackClick = onBackClick,
                onHeaderClick = { isChatInfoSheetOpen = true },
                onViewProfileClick = {
                    if (isDirect && !peerIntro?.handle.isNullOrBlank()) {
                        onProfileClick(peerIntro.handle)
                    }
                },
                onTogglePinConversation = onTogglePinConversation,
                onToggleMuteConversation = onToggleMuteConversation,
                onOpenInfoClick = { isChatInfoSheetOpen = true }
            )

            // Pinned Message Banner
            detail?.pinnedMessage?.let { pinned ->
                DmPinnedMessageBanner(
                    pinnedMessage = pinned,
                    onUnpinClick = { onTogglePinMessage(pinned.id) }
                )
            }

            // Message List & Intro Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.isDmMessagesLoading && uiState.dmMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.primaryBlue,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top-of-thread Profile Identity Intro Card (for 1-on-1 direct chats)
                        if (isDirect && peerIntro != null) {
                            item(key = "peer_intro_card") {
                                DmPeerIntroCard(
                                    intro = peerIntro,
                                    onViewProfileClick = {
                                        if (!peerIntro.handle.isNullOrBlank()) {
                                            onProfileClick(peerIntro.handle)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                            }
                        }

                        // Messages
                        items(uiState.dmMessages, key = { it.id }) { message ->
                            val isOwn = message.senderId == currentUserId
                            val seenParticipants = detail?.participants?.filter {
                                it.userId != message.senderId && it.lastReadMessageId == message.id
                            } ?: emptyList()

                            SwipeToReplyContainer(
                                onSwipeToReply = { onReplyToMessage(message) }
                            ) {
                                DmMessageBubbleRow(
                                    message = message,
                                    isOwn = isOwn,
                                    seenParticipants = seenParticipants,
                                    isMenuOpen = activeMenuMessage?.id == message.id,
                                    onLongClick = { activeMenuMessage = message },
                                    onDismissMenu = { activeMenuMessage = null },
                                    onReplyClick = {
                                        onReplyToMessage(message)
                                        activeMenuMessage = null
                                    },
                                    onEditClick = {
                                        onEditMessage(message)
                                        activeMenuMessage = null
                                    },
                                    onDeleteClick = {
                                        onDeleteMessage(message.id)
                                        activeMenuMessage = null
                                    },
                                    onTogglePinClick = {
                                        onTogglePinMessage(message.id)
                                        activeMenuMessage = null
                                    },
                                    onReportClick = {
                                        onReportMessage(message)
                                        activeMenuMessage = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Composer or Voice Dictation Bar
            if (uiState.isVoiceListening) {
                VoiceDictationBar(
                    amplitudes = uiState.voiceAmplitudes,
                    recognizedText = uiState.voicePartialTranscript,
                    onCancel = onCancelVoiceDictation,
                    onConfirm = onConfirmVoiceTranscript,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            } else {
                DmComposer(
                    text = uiState.dmComposerText,
                    onTextChange = onComposerTextChange,
                    stagedAttachments = uiState.dmStagedAttachments,
                    onRemoveAttachment = onRemoveStagedAttachment,
                    replyingTo = uiState.dmReplyingToMessage,
                    onCancelReply = onCancelReply,
                    editing = uiState.dmEditingMessage,
                    onCancelEdit = onCancelEdit,
                    onPlusClick = {
                        isAttachmentSheetOpen = true
                        onLoadRecentImages()
                    },
                    onSendClick = onSendMessage,
                    onVoiceDictationClick = onVoiceDictationClick
                )
            }
        }

        // Chat Info Sheet (1-on-1 identity or group management)
        if (isChatInfoSheetOpen && detail != null) {
            DmChatInfoSheet(
                detail = detail,
                isDirect = isDirect,
                currentUserId = currentUserId,
                messages = uiState.dmMessages,
                isGroupAvatarUploading = uiState.isGroupAvatarUploading,
                isAddingGroupMembers = uiState.isAddingGroupMembers,
                memberCandidates = uiState.dmSearchPeopleResults,
                isSearchingMembers = uiState.isDmSearchPeopleLoading,
                isSubmittingReport = uiState.isSubmittingReport,
                reportError = uiState.reportError,
                isBlocking = uiState.isBlocking,
                onSearchMembers = onSearchPeopleForGroup,
                onViewProfile = { handle -> onProfileClick(handle) },
                onToggleMute = onToggleMuteConversation,
                onTogglePin = onTogglePinConversation,
                onUpdateGroupAvatar = onUpdateGroupAvatar,
                onRenameGroup = onRenameGroup,
                onAddMembers = onAddGroupMembers,
                onLeaveGroup = {
                    isChatInfoSheetOpen = false
                    onLeaveGroup()
                },
                onClearHistory = {
                    isChatInfoSheetOpen = false
                    onClearHistory()
                },
                onReport = onReportUser,
                onBlock = onBlockUser,
                onDismiss = { isChatInfoSheetOpen = false }
            )
        }

        // Attachment Sheet
        if (isAttachmentSheetOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isAttachmentSheetOpen = false }
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                AttachmentSheetContent(
                    recentGalleryImages = uiState.recentDeviceImages,
                    selectedUris = emptySet(),
                    onSeeAllClick = {
                        isAttachmentSheetOpen = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCameraTileClick = {
                        isAttachmentSheetOpen = false
                    },
                    onToggleSelectGalleryImage = { uriStr ->
                        isAttachmentSheetOpen = false
                        onAddStagedAttachment(
                            StagedAttachment(
                                uri = android.net.Uri.parse(uriStr),
                                fileName = "image_${System.currentTimeMillis()}.jpg",
                                mimeType = "image/jpeg",
                                sizeBytes = 0L
                            )
                        )
                    },
                    onConfirmSelectedGalleryImages = {},
                    onRecentlyUploadedClick = { isAttachmentSheetOpen = false },
                    onFilesClick = { isAttachmentSheetOpen = false },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun DmHeaderBar(
    detail: app.buildx.chat.data.model.DmConversationDetail?,
    isDirect: Boolean,
    currentUserId: String,
    onBackClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onViewProfileClick: () -> Unit,
    onTogglePinConversation: () -> Unit,
    onToggleMuteConversation: () -> Unit,
    onOpenInfoClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val conversation = detail?.conversation
    val peerIntro = detail?.peerIntro
    val myParticipant = detail?.participants?.firstOrNull { it.userId == currentUserId }

    val avatarUrl = if (isDirect) peerIntro?.avatarUrl else conversation?.avatarUrl
    val title = if (isDirect) {
        peerIntro?.displayName ?: peerIntro?.handle ?: ""
    } else {
        conversation?.title ?: ""
    }
    val isVerified = isDirect && peerIntro?.isVerified == true
    val subtitle = if (isDirect) {
        peerIntro?.handle?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""
    } else {
        val activeCount = detail?.participants?.count { it.status == "active" } ?: 0
        stringResource(R.string.dm_members_count, activeCount)
    }

    var isMoreMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Start Card (46dp Circle)
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .pressScaleClickable(onClick = onBackClick)
                    .softShadow(
                        color = colors.headerHaloShadowColor,
                        cornerRadius = 23.dp,
                        blurRadius = 14.dp,
                        offsetY = 2.5.dp
                    )
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape),
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

            // Center Identity Capsule Card (46dp Height)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .pressScaleClickable(onClick = onHeaderClick)
                    .softShadow(
                        color = colors.headerHaloShadowColor,
                        cornerRadius = 23.dp,
                        blurRadius = 14.dp,
                        offsetY = 2.5.dp
                    )
                    .clip(RoundedCornerShape(23.dp))
                    .background(colors.cardBackground)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(23.dp))
                    .padding(start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!isDirect) {
                        Text(
                            text = title.firstOrNull()?.uppercase() ?: "",
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 14.dp)
                        }
                        if (myParticipant?.isMuted == true) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bell_off),
                                contentDescription = stringResource(R.string.dm_action_mute),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = colors.secondaryText,
                            fontSize = 12.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // End Card (46dp Circle with Vertical 3-Dots)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .pressScaleClickable(onClick = { isMoreMenuOpen = true })
                    .softShadow(
                        color = colors.headerHaloShadowColor,
                        cornerRadius = 23.dp,
                        blurRadius = 14.dp,
                        offsetY = 2.5.dp
                    )
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape),
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
                Box(modifier = Modifier.padding(top = 60.dp, end = 16.dp)) {
                    ActionMenuPopup(
                        items = buildList {
                            add(
                                ActionMenuItem(
                                    label = if (myParticipant?.isPinned == true) {
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
                                    label = if (myParticipant?.isMuted == true) {
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
                            if (isDirect) {
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
private fun DmPinnedMessageBanner(
    pinnedMessage: DmMessage,
    onUnpinClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceMuted)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pin),
                contentDescription = null,
                tint = colors.primaryBlue,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dm_pinned_message),
                    color = colors.primaryBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pinnedMessage.content.ifBlank { stringResource(R.string.dm_media_attachment) },
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Unpin",
                tint = colors.secondaryText,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onUnpinClick)
            )
        }
    }
}

@Composable
private fun DmPeerIntroCard(
    intro: DmPeerIntro,
    onViewProfileClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 92dp Avatar
        Box(
            modifier = Modifier
                .size(92.dp)
                .softShadow(colors.cardBackground.copy(alpha = 0.3f), cornerRadius = 46.dp, blurRadius = 14.dp)
                .border(1.2.dp, colors.subtleSurfaceBorder, CircleShape)
                .clip(CircleShape)
        ) {
            if (!intro.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = intro.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Display Name + Verified
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = intro.displayName ?: intro.handle ?: "",
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (intro.isVerified) {
                Spacer(modifier = Modifier.width(5.dp))
                VerifiedBadge(size = 17.dp)
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Handle
        Text(
            text = "@${intro.handle}",
            color = colors.secondaryText,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Followers & Posts
        Text(
            text = stringResource(
                R.string.dm_followers_posts,
                intro.followersCount.toString(),
                intro.postsCount.toString()
            ),
            color = colors.secondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Followed since
        val followedYear = intro.followingSince?.let {
            try {
                java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).year.toString()
            } catch (_: Exception) {
                null
            }
        }

        Text(
            text = if (followedYear != null) {
                stringResource(R.string.dm_followed_since, followedYear)
            } else {
                stringResource(R.string.dm_not_following)
            },
            color = colors.secondaryText,
            fontSize = 12.5.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // View Profile Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceMuted)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = onViewProfileClick)
                .padding(horizontal = 18.dp, vertical = 7.dp)
        ) {
            Text(
                text = stringResource(R.string.dm_view_profile),
                color = colors.primaryText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SwipeToReplyContainer(
    onSwipeToReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = BuildXTheme.colors
    var offsetX by remember { mutableFloatStateOf(0f) }

    val draggableState = rememberDraggableState { delta ->
        val newOffset = (offsetX + delta).coerceIn(0f, 90f)
        offsetX = newOffset
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (offsetX > 55f) {
                        onSwipeToReply()
                    }
                    offsetX = 0f
                }
            )
    ) {
        if (offsetX > 15f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_reply),
                    contentDescription = "Reply",
                    tint = colors.primaryBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun DmMessageBubbleRow(
    message: DmMessage,
    isOwn: Boolean,
    seenParticipants: List<DmParticipant>,
    isMenuOpen: Boolean,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onReplyClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTogglePinClick: () -> Unit,
    onReportClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val context = LocalContext.current
    val isDeleted = message.deletedAt != null
    val hasAttachment = !message.attachmentPath.isNullOrBlank() && !isDeleted
    val isRtl = remember(message.content, strings.isRtl) {
        isRtlText(message.content, strings.isRtl)
    }
    val bubbleShape = if (isOwn) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }

    val menuItems: List<ActionMenuItem> = if (!isMenuOpen) {
        emptyList()
    } else {
        buildList {
            if (!isDeleted) {
                add(
                    ActionMenuItem(
                        label = stringResource(R.string.dm_action_reply),
                        iconRes = R.drawable.ic_reply,
                        onClick = onReplyClick
                    )
                )
                if (message.content.isNotBlank()) {
                    add(
                        ActionMenuItem(
                            label = stringResource(R.string.dm_action_copy),
                            iconRes = R.drawable.ic_copy,
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("message", message.content))
                                onDismissMenu()
                            }
                        )
                    )
                }
                if (isOwn && message.messageType == "text") {
                    add(
                        ActionMenuItem(
                            label = stringResource(R.string.dm_action_edit),
                            iconRes = R.drawable.ic_pencil,
                            onClick = onEditClick
                        )
                    )
                }
                add(
                    ActionMenuItem(
                        label = stringResource(R.string.dm_pin_message),
                        iconRes = R.drawable.ic_pin,
                        onClick = onTogglePinClick
                    )
                )
                add(
                    ActionMenuItem(
                        label = stringResource(R.string.dm_action_forward),
                        iconRes = R.drawable.ic_forward,
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.content)
                            }
                            context.startActivity(Intent.createChooser(share, null))
                            onDismissMenu()
                        }
                    )
                )
            }
            if (isOwn && !isDeleted) {
                add(
                    ActionMenuItem(
                        label = stringResource(R.string.dm_action_delete),
                        iconRes = R.drawable.ic_trash,
                        isDestructive = true,
                        onClick = onDeleteClick
                    )
                )
            }
            if (!isOwn) {
                add(
                    ActionMenuItem(
                        label = stringResource(R.string.dm_action_report),
                        iconRes = R.drawable.ic_flag,
                        isDestructive = true,
                        onClick = onReportClick
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        // Quoted Header: "Name replied to Name"
        if (message.replyTo != null && !isDeleted) {
            val replyLabel = stringResource(
                R.string.dm_replied_to,
                message.senderDisplayName ?: "User",
                message.replyTo.senderDisplayName ?: "User"
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 285.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = replyLabel,
                    color = colors.secondaryText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceMuted.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = message.replyTo.content.ifBlank { stringResource(R.string.dm_media_attachment) },
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (message.messageType == "note_reply" && !message.noteSnapshotText.isNullOrBlank() && !isDeleted) {
            // Note Reply pill
            Box(
                modifier = Modifier
                    .widthIn(max = 285.dp)
                    .padding(bottom = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.dm_replied_to_note)}: \"${message.noteSnapshotText}\"",
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Image attachment: its own card, no blue bubble padding around it.
        if (hasAttachment) {
            Box(
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .heightIn(min = 120.dp, max = 280.dp)
                    .softShadow(
                        color = colors.headerHaloShadowColor,
                        cornerRadius = 18.dp,
                        blurRadius = 10.dp,
                        offsetY = 2.dp
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceMuted)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
                    .clickable(onClick = onLongClick)
            ) {
                AsyncImage(
                    model = message.attachmentPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isMenuOpen && message.content.isBlank()) {
                    DmMessageActionPopup(items = menuItems, onDismiss = onDismissMenu)
                }
            }

            if (message.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Text bubble
        if (isDeleted || message.content.isNotBlank()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 285.dp)
                    .clickable(onClick = onLongClick)
                    .clip(bubbleShape)
                    .background(
                        when {
                            isDeleted -> colors.surfaceMuted.copy(alpha = 0.45f)
                            isOwn -> colors.primaryBlue
                            else -> colors.surfaceMuted
                        }
                    )
                    .then(
                        if (isDeleted) {
                            Modifier.border(0.8.dp, colors.subtleSurfaceBorder, bubbleShape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Column {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Text(
                            text = if (isDeleted) {
                                stringResource(R.string.dm_message_deleted)
                            } else {
                                message.content
                            },
                            color = when {
                                isDeleted -> colors.secondaryText
                                isOwn -> Color.White
                                else -> colors.primaryText
                            },
                            fontSize = if (isDeleted) 13.sp else 14.5.sp,
                            lineHeight = if (isDeleted) 17.sp else 20.sp,
                            fontStyle = if (isDeleted) FontStyle.Italic else FontStyle.Normal,
                            textAlign = TextAlign.Start
                        )
                    }

                    if (!isDeleted && message.editedAt != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.dm_edited),
                            color = if (isOwn) Color.White.copy(alpha = 0.72f) else colors.secondaryText,
                            fontSize = 10.5.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                if (isMenuOpen && (isDeleted || message.content.isNotBlank())) {
                    DmMessageActionPopup(items = menuItems, onDismiss = onDismissMenu)
                }
            }
        }

        // Send Status / Time
        if (message.localSendStatus == DmSendStatus.SENDING) {
            Text(
                text = stringResource(R.string.dm_sending),
                color = colors.secondaryText,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        } else if (message.localSendStatus == DmSendStatus.FAILED) {
            Text(
                text = stringResource(R.string.dm_failed_to_send),
                color = colors.destructiveRed,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        }

        // Instagram-style Seen Avatars
        if (seenParticipants.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-4).dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 3.dp, end = 4.dp)
            ) {
                seenParticipants.forEach { participant ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.pageBackground, CircleShape)
                    ) {
                        if (!participant.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = participant.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colors.surfaceMuted)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Anchored message action menu. Rendered as a child of the bubble/card so
 * [BubblePopupPositionProvider] receives that surface's bounds as its anchor.
 */
@Composable
private fun DmMessageActionPopup(
    items: List<ActionMenuItem>,
    onDismiss: () -> Unit
) {
    val strings = BuildXTheme.strings
    Popup(
        popupPositionProvider = remember(strings.isRtl) { BubblePopupPositionProvider(strings.isRtl) },
        onDismissRequest = onDismiss
    ) {
        ActionMenuPopup(items = items)
    }
}

@Composable
private fun DmComposer(
    text: String,
    onTextChange: (String) -> Unit,
    stagedAttachments: List<StagedAttachment>,
    onRemoveAttachment: (StagedAttachment) -> Unit,
    replyingTo: DmMessage?,
    onCancelReply: () -> Unit,
    editing: DmMessage?,
    onCancelEdit: () -> Unit,
    onPlusClick: () -> Unit,
    onSendClick: () -> Unit,
    onVoiceDictationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val canSend = text.isNotBlank() || stagedAttachments.isNotEmpty()
    val focusRequester = remember { FocusRequester() }

    val isTextRtl = remember(text, strings.isRtl) {
        isRtlText(text, defaultRtl = strings.isRtl)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .defaultMinSize(minHeight = 50.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .softShadow(colors.headerHaloShadowColor, cornerRadius = 25.dp, blurRadius = 16.dp, offsetY = 3.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(25.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        // Docked Reply Banner
        if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(colors.primaryBlue)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dm_replying_to, replyingTo.senderDisplayName ?: "User"),
                        color = colors.primaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = replyingTo.content.ifBlank { stringResource(R.string.dm_media_attachment) },
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCancelReply),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Cancel",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else if (editing != null) {
            // Docked Editing Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(colors.primaryBlue)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dm_editing_message),
                        color = colors.primaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = editing.content.ifBlank { stringResource(R.string.dm_media_attachment) },
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCancelEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Cancel",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Staged Attachments Strip
        if (stagedAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stagedAttachments) { att ->
                    Box(modifier = Modifier.size(60.dp)) {
                        AsyncImage(
                            model = att.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onRemoveAttachment(att) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Main Input Row (pinned LTR so text -> attach -> mic/send flow matches reference)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Expanding Text Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dm_composer_placeholder),
                            color = colors.secondaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = BuildXFontFamily,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isTextRtl) TextAlign.Right else TextAlign.Left
                        )
                    }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isTextRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            minLines = 1,
                            maxLines = 6,
                            textStyle = TextStyle(
                                fontFamily = BuildXFontFamily,
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Start,
                                textDirection = if (isTextRtl) TextDirection.Rtl else TextDirection.Ltr
                            ),
                            cursorBrush = SolidColor(colors.primaryBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 24.dp, max = 132.dp)
                                .focusRequester(focusRequester)
                        )
                    }
                }

                // Paperclip Attachment Button (auto-hides when typing to maximize text field space)
                AnimatedVisibility(
                    visible = text.isEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 1.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .pressScaleClickable(onClick = onPlusClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_paperclip),
                            contentDescription = "Attach",
                            tint = colors.secondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // End Circular Blue Action Button (42dp CircleShape, morphs between mic and arrow_up)
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp, bottom = 1.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.primaryBlue)
                        .pressScaleClickable {
                            if (canSend) {
                                onSendClick()
                            } else {
                                onVoiceDictationClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = canSend,
                        transitionSpec = {
                            (scaleIn(tween(180)) + fadeIn(tween(180))) togetherWith (scaleOut(tween(150)) + fadeOut(tween(150)))
                        },
                        label = "dm_composer_action"
                    ) { isSending ->
                        if (isSending) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_up),
                                contentDescription = stringResource(R.string.dm_send),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_mic),
                                contentDescription = "Voice dictation",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
