package app.buildx.chat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.ActionMenuPopup
import app.buildx.chat.ui.components.AttachmentSheetContent
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.VoiceDictationBar
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
    onVoiceDictationClick: () -> Unit = {},
    onCancelVoiceDictation: () -> Unit = {},
    onConfirmVoiceTranscript: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = BuildXTheme.colors
    val listState = rememberLazyListState()

    val detail = uiState.activeDmDetail
    val conversation = detail?.conversation
    val isDirect = conversation?.kind != "group"
    val peerIntro = detail?.peerIntro
    val currentUserId = uiState.profile?.id ?: ""

    var isAttachmentSheetOpen by remember { mutableStateOf(false) }
    var activeMenuMessage by remember { mutableStateOf<DmMessage?>(null) }
    var isMoreOptionsMenuOpen by remember { mutableStateOf(false) }

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
                onBackClick = onBackClick,
                onHeaderClick = {
                    if (isDirect && peerIntro?.handle != null) {
                        onProfileClick(peerIntro.handle)
                    }
                },
                onMoreClick = { isMoreOptionsMenuOpen = true }
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
                                    onLongClick = { activeMenuMessage = message }
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
                    onPlusClick = { isAttachmentSheetOpen = true },
                    onSendClick = onSendMessage,
                    onVoiceDictationClick = onVoiceDictationClick
                )
            }
        }

        // Action Menu Popup on message long-press
        activeMenuMessage?.let { msg ->
            val isOwn = msg.senderId == currentUserId
            val isDeleted = msg.deletedAt != null

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { activeMenuMessage = null }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val menuItems = mutableListOf<ActionMenuItem>()

                if (!isDeleted) {
                    menuItems.add(
                        ActionMenuItem(
                            label = stringResource(R.string.dm_action_reply),
                            iconRes = R.drawable.ic_reply,
                            onClick = {
                                onReplyToMessage(msg)
                                activeMenuMessage = null
                            }
                        )
                    )
                    if (msg.content.isNotBlank()) {
                        menuItems.add(
                            ActionMenuItem(
                                label = stringResource(R.string.dm_action_copy),
                                iconRes = R.drawable.ic_copy,
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("message", msg.content))
                                    activeMenuMessage = null
                                }
                            )
                        )
                    }
                    if (isOwn && msg.messageType == "text") {
                        menuItems.add(
                            ActionMenuItem(
                                label = stringResource(R.string.dm_action_edit),
                                iconRes = R.drawable.ic_pencil,
                                onClick = {
                                    onEditMessage(msg)
                                    activeMenuMessage = null
                                }
                            )
                        )
                    }
                    menuItems.add(
                        ActionMenuItem(
                            label = stringResource(R.string.dm_pin_message),
                            iconRes = R.drawable.ic_pin,
                            onClick = {
                                onTogglePinMessage(msg.id)
                                activeMenuMessage = null
                            }
                        )
                    )
                }

                if (isOwn && !isDeleted) {
                    menuItems.add(
                        ActionMenuItem(
                            label = stringResource(R.string.dm_action_delete),
                            iconRes = R.drawable.ic_trash,
                            isDestructive = true,
                            onClick = {
                                onDeleteMessage(msg.id)
                                activeMenuMessage = null
                            }
                        )
                    )
                }

                if (!isOwn) {
                    menuItems.add(
                        ActionMenuItem(
                            label = stringResource(R.string.dm_action_report),
                            iconRes = R.drawable.ic_flag,
                            isDestructive = true,
                            onClick = {
                                onReportMessage(msg)
                                activeMenuMessage = null
                            }
                        )
                    )
                }

                ActionMenuPopup(items = menuItems)
            }
        }

        // More Options Popup Menu
        if (isMoreOptionsMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isMoreOptionsMenuOpen = false }
                    ),
                contentAlignment = Alignment.Center
            ) {
                ActionMenuPopup(
                    items = listOf(
                        ActionMenuItem(
                            label = stringResource(R.string.dm_pin_chat),
                            iconRes = R.drawable.ic_pin,
                            onClick = {
                                onTogglePinConversation()
                                isMoreOptionsMenuOpen = false
                            }
                        ),
                        ActionMenuItem(
                            label = stringResource(R.string.dm_mute_chat),
                            iconRes = R.drawable.ic_volume_off,
                            onClick = {
                                onToggleMuteConversation()
                                isMoreOptionsMenuOpen = false
                            }
                        ),
                        ActionMenuItem(
                            label = stringResource(R.string.dm_view_profile),
                            iconRes = R.drawable.ic_user,
                            onClick = {
                                isMoreOptionsMenuOpen = false
                                if (isDirect && peerIntro?.handle != null) {
                                    onProfileClick(peerIntro.handle)
                                }
                            }
                        )
                    )
                )
            }
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
    onBackClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val conversation = detail?.conversation
    val peerIntro = detail?.peerIntro

    val avatarUrl = if (isDirect) peerIntro?.avatarUrl else conversation?.avatarUrl
    val title = if (isDirect) peerIntro?.displayName ?: peerIntro?.handle ?: "" else conversation?.title ?: ""
    val isVerified = isDirect && peerIntro?.isVerified == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCircleButton(
            iconRes = R.drawable.ic_back,
            contentDescription = "Back",
            onClick = onBackClick
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Center Identity
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onHeaderClick)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = colors.primaryText,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 14.dp)
                    }
                }

                if (isDirect && !peerIntro?.handle.isNullOrBlank()) {
                    Text(
                        text = "@${peerIntro?.handle}",
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                } else if (!isDirect) {
                    Text(
                        text = stringResource(R.string.dm_members_count, detail?.participants?.size ?: 0),
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }

        HeaderCircleButton(
            iconRes = R.drawable.ic_more_horizontal,
            contentDescription = "Options",
            onClick = onMoreClick
        )
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
    onLongClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val isDeleted = message.deletedAt != null

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
            Text(
                text = replyLabel,
                color = colors.secondaryText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // Quoted bubble snippet
            Box(
                modifier = Modifier
                    .padding(bottom = 3.dp)
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
        } else if (message.messageType == "note_reply" && !message.noteSnapshotText.isNullOrBlank() && !isDeleted) {
            // Note Reply pill
            Box(
                modifier = Modifier
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

        // Main Bubble
        Box(
            modifier = Modifier
                .clickable(onClick = onLongClick)
                .clip(
                    if (isOwn) {
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                    } else {
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
                    }
                )
                .background(if (isOwn) colors.primaryBlue else colors.surfaceMuted)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Column {
                if (isDeleted) {
                    Text(
                        text = stringResource(R.string.dm_message_deleted),
                        color = if (isOwn) Color.White.copy(alpha = 0.65f) else colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    if (!message.attachmentPath.isNullOrBlank()) {
                        AsyncImage(
                            model = message.attachmentPath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (message.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            color = if (isOwn) Color.White else colors.primaryText,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
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
                color = Color.Red,
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
            .animateContentSize()
            .softShadow(colors.composerShadowColor, cornerRadius = 26.dp, blurRadius = 16.dp, offsetY = 3.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
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

        // Main Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Start Icon: 40dp circular touch target with 22dp ic_emoji
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { focusRequester.requestFocus() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_emoji),
                    contentDescription = "Emoji",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Center Expanding Text Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
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
                        textAlign = TextAlign.Start
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
                            .heightIn(min = 24.dp, max = 136.dp)
                            .focusRequester(focusRequester)
                    )
                }
            }

            // Paperclip Attachment Button: 40dp touch target with 22dp ic_paperclip
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPlusClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_paperclip),
                    contentDescription = "Attach",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // End Circular Blue Action Button (42dp CircleShape, background(colors.primaryBlue))
            Box(
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 3.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.primaryBlue)
                    .clickable {
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
                        (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
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
