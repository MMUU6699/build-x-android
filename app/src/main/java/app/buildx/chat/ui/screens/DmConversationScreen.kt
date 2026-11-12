package app.buildx.chat.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.chat.ChatComposer
import app.buildx.chat.ui.chat.ChatHeader
import app.buildx.chat.ui.chat.ChatListItem
import app.buildx.chat.ui.chat.ChatMessageList
import app.buildx.chat.ui.chat.ChatMessageMapper
import app.buildx.chat.ui.chat.rememberReduceMotion
import app.buildx.chat.ui.chat.toChatColors
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.AttachmentSheetContent
import app.buildx.chat.ui.components.VoiceDictationBar
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState
import java.io.File

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
    onRetryFailedMessage: (DmMessage) -> Unit = {},
    onLoadOlderMessages: () -> Unit = {},
    onConsumeJustSent: (String) -> Unit = {},
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
    onSearchPeopleForGroup: (String) -> Unit = {},
    onOpenChatInfo: (() -> Unit)? = null,
    jumpToMessageId: String? = null,
    onConsumeJumpToMessage: () -> Unit = {},
    todayOverride: java.time.LocalDate? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val chatColors = remember(colors) { colors.toChatColors() }
    val reduceMotion = rememberReduceMotion()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val detail = uiState.activeDmDetail
    val conversation = detail?.conversation
    val isDirect = conversation?.kind != "group"
    val peerIntro = detail?.peerIntro
    val currentUserId = uiState.profile?.id ?: ""

    var isAttachmentSheetOpen by remember { mutableStateOf(false) }
    var selectedAttachmentUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeMenuMessage by remember { mutableStateOf<DmMessage?>(null) }
    var isChatInfoSheetOpen by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        uris.forEach { uri ->
            onAddStagedAttachment(uri.asDmStagedAttachment(context))
        }
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onAddStagedAttachment(uri.asDmStagedAttachment(context))
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        pendingCameraUri?.takeIf { success }?.let { uri ->
            onAddStagedAttachment(uri.asDmStagedAttachment(context, "image/jpeg"))
        }
        pendingCameraUri = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "dm_capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    val listItems = remember(
        uiState.dmMessages,
        currentUserId,
        isDirect,
        detail?.participants,
        peerIntro,
        strings.isRtl
    ) {
        ChatMessageMapper.mapToReversedListItems(
            messages = uiState.dmMessages,
            currentUserId = currentUserId,
            isDirect = isDirect,
            participants = detail?.participants.orEmpty(),
            peerIntro = peerIntro,
            defaultRtl = strings.isRtl
        )
    }

    val membersCountTemplate = stringResource(R.string.dm_members_count, 0)
    val headerState = remember(detail, currentUserId, membersCountTemplate) {
        ChatMessageMapper.mapHeaderState(
            detail = detail,
            currentUserId = currentUserId,
            typingUserIds = emptySet(),
            membersFormat = { count ->
                context.getString(R.string.dm_members_count, count)
            }
        )
    }

    val isListScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    val density = LocalDensity.current
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val navAndImeInsets = WindowInsets.navigationBars.union(WindowInsets.ime)
    val fallbackBottomInsetDp = navAndImeInsets.asPaddingValues().calculateBottomPadding()
    val topOverlayDp = with(density) { topOverlayHeightPx.toDp() }
    val bottomOverlayDp = with(density) { bottomOverlayHeightPx.toDp() }
    val listTopPadding = if (topOverlayHeightPx > 0) topOverlayDp + 8.dp else 96.dp
    val listBottomPadding = if (bottomOverlayHeightPx > 0) bottomOverlayDp + 8.dp else fallbackBottomInsetDp + 76.dp
    val fabBottomOffset = if (bottomOverlayHeightPx > 0) bottomOverlayDp + 12.dp else fallbackBottomInsetDp + 78.dp
    val openChatInfoAction = remember(onOpenChatInfo) {
        {
            if (onOpenChatInfo != null) {
                onOpenChatInfo()
            } else {
                isChatInfoSheetOpen = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(chatColors.pageBackground)
    ) {
        ChatMessageList(
            items = listItems,
            listState = listState,
            chatColors = chatColors,
            isLoadingInitial = uiState.isDmMessagesLoading,
            isPaginating = uiState.isDmPaginating,
            hasMoreMessages = uiState.hasMoreDmMessages,
            activeMenuMessageId = activeMenuMessage?.id,
            justSentClientTempIds = uiState.justSentClientTempIds,
            reduceMotion = reduceMotion,
            buildMenuItems = { item ->
                buildDmMessageMenuItems(
                    item = item,
                    context = context,
                    onDismiss = { activeMenuMessage = null },
                    onReply = {
                        onReplyToMessage(item.message)
                        activeMenuMessage = null
                    },
                    onEdit = {
                        onEditMessage(item.message)
                        activeMenuMessage = null
                    },
                    onTogglePin = {
                        onTogglePinMessage(item.message.id)
                        activeMenuMessage = null
                    },
                    onDelete = {
                        onDeleteMessage(item.message.id)
                        activeMenuMessage = null
                    },
                    onReport = {
                        onReportMessage(item.message)
                        activeMenuMessage = null
                    }
                )
            },
            onLoadOlderMessages = onLoadOlderMessages,
            onConsumeJustSent = onConsumeJustSent,
            onMessageTap = { msg -> activeMenuMessage = msg },
            onRetryFailedMessage = onRetryFailedMessage,
            onDismissMenu = { activeMenuMessage = null },
            onSwipeToReply = onReplyToMessage,
            onViewProfileClick = onProfileClick,
            topContentPadding = listTopPadding,
            bottomContentPadding = listBottomPadding,
            scrollToBottomFabBottomOffset = fabBottomOffset,
            jumpToMessageId = jumpToMessageId,
            onConsumeJumpToMessage = onConsumeJumpToMessage,
            todayOverride = todayOverride,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to chatColors.pageBackground.copy(alpha = 0.96f),
                        0.72f to chatColors.pageBackground.copy(alpha = 0.84f),
                        1.0f to Color.Transparent
                    )
                )
                .onSizeChanged { topOverlayHeightPx = it.height }
                .statusBarsPadding()
        ) {
            ChatHeader(
                state = headerState,
                chatColors = chatColors,
                isListScrolled = isListScrolled,
                reduceMotion = reduceMotion,
                onBackClick = onBackClick,
                onPillClick = openChatInfoAction,
                onViewProfileClick = {
                    val handle = peerIntro?.handle
                    if (isDirect && !handle.isNullOrBlank()) {
                        onProfileClick(handle)
                    }
                },
                onOpenInfoClick = openChatInfoAction,
                onTogglePinConversation = onTogglePinConversation,
                onToggleMuteConversation = onToggleMuteConversation
            )

            detail?.pinnedMessage?.let { pinned ->
                DmPinnedMessageBanner(
                    pinnedMessage = pinned,
                    onUnpinClick = { onTogglePinMessage(pinned.id) }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomOverlayHeightPx = it.height }
                .windowInsetsPadding(navAndImeInsets)
        ) {
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
                ChatComposer(
                    text = uiState.dmComposerText,
                    onTextChange = onComposerTextChange,
                    stagedAttachments = uiState.dmStagedAttachments,
                    onRemoveAttachment = onRemoveStagedAttachment,
                    replyingTo = uiState.dmReplyingToMessage,
                    onCancelReply = onCancelReply,
                    editing = uiState.dmEditingMessage,
                    onCancelEdit = onCancelEdit,
                    onAttachClick = {
                        selectedAttachmentUris = emptySet()
                        isAttachmentSheetOpen = true
                        onLoadRecentImages()
                    },
                    onSendClick = onSendMessage,
                    onVoiceDictationClick = onVoiceDictationClick,
                    chatColors = chatColors,
                    reduceMotion = reduceMotion
                )
            }
        }

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
                onViewProfile = onProfileClick,
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
                    selectedUris = selectedAttachmentUris,
                    onSeeAllClick = {
                        isAttachmentSheetOpen = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCameraTileClick = {
                        isAttachmentSheetOpen = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val file = File(context.cacheDir, "dm_capture_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onToggleSelectGalleryImage = { uriStr ->
                        selectedAttachmentUris = selectedAttachmentUris.toggle(uriStr)
                    },
                    onConfirmSelectedGalleryImages = {
                        uiState.recentDeviceImages
                            .filter { it.uri.toString() in selectedAttachmentUris }
                            .forEach(onAddStagedAttachment)
                        selectedAttachmentUris = emptySet()
                        isAttachmentSheetOpen = false
                    },
                    onRecentlyUploadedClick = {
                        isAttachmentSheetOpen = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onFilesClick = {
                        isAttachmentSheetOpen = false
                        documentPickerLauncher.launch(arrayOf("image/*", "application/pdf", "text/plain"))
                    },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    }
}

private fun buildDmMessageMenuItems(
    item: ChatListItem.MessageItem,
    context: Context,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit
): List<ActionMenuItem> {
    val message = item.message
    val isOwn = item.isOwn
    val isDeleted = item.isDeleted

    val replyLabel = context.getString(R.string.dm_action_reply)
    val copyLabel = context.getString(R.string.dm_action_copy)
    val editLabel = context.getString(R.string.dm_action_edit)
    val pinLabel = context.getString(R.string.dm_pin_message)
    val forwardLabel = context.getString(R.string.dm_action_forward)
    val deleteLabel = context.getString(R.string.dm_action_delete)
    val reportLabel = context.getString(R.string.dm_action_report)

    return buildList {
        if (!isDeleted) {
            add(ActionMenuItem(label = replyLabel, iconRes = R.drawable.ic_reply, onClick = onReply))
            if (message.content.isNotBlank()) {
                add(
                    ActionMenuItem(
                        label = copyLabel,
                        iconRes = R.drawable.ic_copy,
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("message", message.content))
                            onDismiss()
                        }
                    )
                )
            }
            if (isOwn && message.messageType == "text") {
                add(ActionMenuItem(label = editLabel, iconRes = R.drawable.ic_pencil, onClick = onEdit))
            }
            add(ActionMenuItem(label = pinLabel, iconRes = R.drawable.ic_pin, onClick = onTogglePin))
            add(
                ActionMenuItem(
                    label = forwardLabel,
                    iconRes = R.drawable.ic_forward,
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message.content)
                        }
                        context.startActivity(Intent.createChooser(share, null))
                        onDismiss()
                    }
                )
            )
        }
        if (isOwn && !isDeleted) {
            add(
                ActionMenuItem(
                    label = deleteLabel,
                    iconRes = R.drawable.ic_trash,
                    isDestructive = true,
                    onClick = onDelete
                )
            )
        }
        if (!isOwn) {
            add(
                ActionMenuItem(
                    label = reportLabel,
                    iconRes = R.drawable.ic_flag,
                    isDestructive = true,
                    onClick = onReport
                )
            )
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .softShadow(colors.headerHaloShadowColor, cornerRadius = 14.dp, blurRadius = 8.dp, offsetY = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
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
                    style = TextStyle(textDirection = TextDirection.Content),
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

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun android.net.Uri.asDmStagedAttachment(
    context: Context,
    fallbackMimeType: String? = null
): StagedAttachment {
    val fileName = lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "attachment_${System.currentTimeMillis()}"
    return StagedAttachment(
        uri = this,
        fileName = fileName,
        mimeType = fallbackMimeType ?: context.contentResolver.getType(this) ?: "application/octet-stream",
        sizeBytes = 0L
    )
}
