package app.buildx.chat.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.chat.ChatBidiFormatter
import app.buildx.chat.ui.chat.ChatComposer
import app.buildx.chat.ui.chat.ChatFocusedMessageMenuOverlay
import app.buildx.chat.ui.chat.ChatHeader
import app.buildx.chat.ui.chat.ChatListItem
import app.buildx.chat.ui.chat.ChatMessageList
import app.buildx.chat.ui.chat.ChatMessageMapper
import app.buildx.chat.ui.chat.ChatMotion
import app.buildx.chat.ui.chat.ChatTypography
import app.buildx.chat.ui.chat.ChatWallpaperLayer
import app.buildx.chat.ui.chat.DmMediaViewerDialog
import app.buildx.chat.ui.chat.FocusedMessageMenuState
import app.buildx.chat.ui.chat.rememberReduceMotion
import app.buildx.chat.ui.chat.toChatColors
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.AttachmentSheetContent
import app.buildx.chat.ui.components.SelectTextSheetContent
import app.buildx.chat.ui.components.VoiceDictationBar
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenWallpaperClick: (() -> Unit)? = null,
    jumpToMessageId: String? = null,
    onConsumeJumpToMessage: () -> Unit = {},
    openSearchRequested: Boolean = false,
    onConsumeOpenSearchRequest: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {},
    onToggleReaction: (String, String) -> Unit = { _, _ -> },
    todayOverride: java.time.LocalDate? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val chatColors = remember(colors) { colors.toChatColors() }
    val reduceMotion = rememberReduceMotion()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val detail = uiState.activeDmDetail
    val fallbackInboxItem = remember(uiState.dmInboxItems, conversationId) {
        uiState.dmInboxItems.firstOrNull { it.effectiveId == conversationId }
    }
    val conversation = detail?.conversation
    val isDirect = (conversation?.kind ?: fallbackInboxItem?.kind) != "group"
    val peerIntro = detail?.peerIntro
    val currentUserId = uiState.profile?.id ?: ""

    var isAttachmentSheetOpen by remember { mutableStateOf(false) }
    var selectedAttachmentUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeMenuMessageId by remember { mutableStateOf<String?>(null) }
    var activeMenuAnchorInWindow by remember { mutableStateOf<Rect?>(null) }
    var activeMenuMaxBubbleWidth by remember { mutableStateOf(280.dp) }
    var rootBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var selectTextContent by remember { mutableStateOf<String?>(null) }
    var viewerMessage by remember { mutableStateOf<DmMessage?>(null) }
    var isChatInfoSheetOpen by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var composerBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var inChatSearchQuery by remember { mutableStateOf("") }
    var searchMatchIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(openSearchRequested) {
        if (openSearchRequested) {
            isSearchActive = true
            inChatSearchQuery = ""
            searchMatchIndex = 0
            onConsumeOpenSearchRequest()
        }
    }

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

    val newlyInsertedIds = remember(uiState.justSentClientTempIds, uiState.lastOptimisticMessageId) {
        buildSet {
            addAll(uiState.justSentClientTempIds)
            uiState.lastOptimisticMessageId?.let(::add)
        }
    }

    val listItems = remember(
        uiState.dmMessages,
        currentUserId,
        isDirect,
        detail?.participants,
        peerIntro,
        strings.isRtl,
        newlyInsertedIds,
        uiState.dmMessageReactions,
        uiState.dmMyMessageReactions
    ) {
        ChatMessageMapper.mapToReversedListItems(
            messages = uiState.dmMessages,
            currentUserId = currentUserId,
            isDirect = isDirect,
            participants = detail?.participants.orEmpty(),
            peerIntro = peerIntro,
            defaultRtl = strings.isRtl,
            newlyInsertedIds = newlyInsertedIds,
            reactionsByMessageId = uiState.dmMessageReactions,
            myReactionsByMessageId = uiState.dmMyMessageReactions
        )
    }

    val activeMenuItem = remember(listItems, activeMenuMessageId) {
        activeMenuMessageId?.let { id ->
            listItems.firstOrNull { it is ChatListItem.MessageItem && it.message.id == id } as? ChatListItem.MessageItem
        }
    }
    LaunchedEffect(activeMenuMessageId, activeMenuItem) {
        if (activeMenuMessageId != null && activeMenuItem == null) {
            activeMenuMessageId = null
            activeMenuAnchorInWindow = null
        }
    }
    BackHandler(enabled = activeMenuItem != null) {
        activeMenuMessageId = null
        activeMenuAnchorInWindow = null
    }

    val searchMatchMessageIds = remember(listItems, inChatSearchQuery) {
        val trimmed = inChatSearchQuery.trim()
        if (trimmed.isBlank()) {
            emptyList()
        } else {
            listItems.mapNotNull { item ->
                if (item is ChatListItem.MessageItem &&
                    !item.isDeleted &&
                    item.message.content.contains(trimmed, ignoreCase = true)
                ) {
                    item.message.id
                } else {
                    null
                }
            }
        }
    }

    LaunchedEffect(searchMatchMessageIds) {
        searchMatchIndex = 0
        val firstMatchId = searchMatchMessageIds.firstOrNull() ?: return@LaunchedEffect
        val targetIdx = listItems.indexOfFirst {
            it is ChatListItem.MessageItem && it.message.id == firstMatchId
        }
        if (targetIdx >= 0) {
            listState.animateScrollToItem(targetIdx)
        }
    }

    val activeSearchHighlightId = if (isSearchActive && searchMatchMessageIds.isNotEmpty()) {
        searchMatchMessageIds.getOrNull(searchMatchIndex.coerceIn(0, searchMatchMessageIds.lastIndex))
    } else {
        null
    }

    val membersCountTemplate = stringResource(R.string.dm_members_count, 0)
    val headerState = remember(detail, fallbackInboxItem, currentUserId, membersCountTemplate, uiState.dmTypingUserIds) {
        ChatMessageMapper.mapHeaderState(
            detail = detail,
            currentUserId = currentUserId,
            typingUserIds = uiState.dmTypingUserIds,
            membersFormat = { count ->
                context.getString(R.string.dm_members_count, count)
            },
            fallbackInboxItem = fallbackInboxItem
        )
    }

    val isListScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    val density = LocalDensity.current
    val navAndImeInsets = WindowInsets.navigationBars.union(WindowInsets.ime)
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val extraTopBannerDp = (if (isSearchActive) 48.dp else 0.dp) +
        (if (detail?.pinnedMessage != null) 46.dp else 0.dp)
    val stableListTopPadding = statusBarTopDp + 72.dp + extraTopBannerDp + 8.dp
    // Message viewport applies navAndImeInsets directly; keep bottom content padding stable
    // against transient reply/edit/typing bars so sending never causes a LazyColumn jump.
    val stableListBottomPadding = 72.dp
    val fabBottomOffset = 78.dp
    val openChatInfoAction = remember(onOpenChatInfo) {
        {
            if (onOpenChatInfo != null) {
                onOpenChatInfo()
            } else {
                isChatInfoSheetOpen = true
            }
        }
    }

    val menuFocusProgress by animateFloatAsState(
        targetValue = if (activeMenuItem != null) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
        label = "dm_menu_focus_blur"
    )
    val dismissActiveMenu = {
        activeMenuMessageId = null
        activeMenuAnchorInWindow = null
    }
    val buildMenuForItem: (ChatListItem.MessageItem) -> List<ActionMenuItem> = { item ->
        buildDmMessageMenuItems(
            item = item,
            isPinned = detail?.pinnedMessage?.id == item.message.id,
            context = context,
            onDismiss = dismissActiveMenu,
            onReply = {
                onReplyToMessage(item.message)
                dismissActiveMenu()
            },
            onSelectText = {
                selectTextContent = item.message.content
                dismissActiveMenu()
            },
            onEdit = {
                onEditMessage(item.message)
                dismissActiveMenu()
            },
            onTogglePin = {
                onTogglePinMessage(item.message.id)
                dismissActiveMenu()
            },
            onDelete = {
                onDeleteMessage(item.message.id)
                dismissActiveMenu()
            },
            onReport = {
                onReportMessage(item.message)
                dismissActiveMenu()
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(chatColors.pageBackground)
            .onGloballyPositioned { coords ->
                rootBoundsInWindow = coords.boundsInWindow()
            }
    ) {
        // Single-pass background scaffold (wallpaper + clipped list + top/bottom chrome);
        // blurred as a single layer on API 31+ when the sharp message context overlay is active.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && menuFocusProgress > 0.01f) {
                        val radiusPx = with(density) { (18.dp * menuFocusProgress).toPx() }.coerceAtLeast(0.5f)
                        Modifier.graphicsLayer {
                            renderEffect = RenderEffect.createBlurEffect(
                                radiusPx,
                                radiusPx,
                                Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            ChatWallpaperLayer(
                config = uiState.activeDmWallpaper,
                isDark = colors.isDark,
                fallbackBackground = chatColors.pageBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .windowInsetsPadding(navAndImeInsets)
                    .clipToBounds()
            ) {
                ChatMessageList(
                    items = listItems,
                    listState = listState,
                    chatColors = chatColors,
                    isLoadingInitial = uiState.isDmMessagesLoading,
                    isPaginating = uiState.isDmPaginating,
                    hasMoreMessages = uiState.hasMoreDmMessages,
                    activeMenuMessageId = activeMenuItem?.message?.id,
                    justSentClientTempIds = uiState.justSentClientTempIds,
                    reduceMotion = reduceMotion,
                    buildMenuItems = buildMenuForItem,
                    onLoadOlderMessages = onLoadOlderMessages,
                    onConsumeJustSent = onConsumeJustSent,
                    onMessageTap = { msg ->
                        if (activeMenuMessageId != null) {
                            dismissActiveMenu()
                        } else if (msg.localSendStatus == DmSendStatus.FAILED) {
                            onRetryFailedMessage(msg)
                        }
                    },
                    onMessageLongPress = { msg ->
                        val hasMenu = msg.deletedAt == null || msg.senderId != currentUserId
                        if (hasMenu) {
                            activeMenuMessageId = msg.id
                            if (activeMenuAnchorInWindow == null) {
                                val fallbackBounds = rootBoundsInWindow ?: Rect(0f, 0f, 1080f, 1920f)
                                val centerY = (fallbackBounds.top + fallbackBounds.bottom) * 0.5f
                                val centerX = (fallbackBounds.left + fallbackBounds.right) * 0.5f
                                activeMenuAnchorInWindow = Rect(centerX - 120f, centerY - 32f, centerX + 120f, centerY + 32f)
                            }
                        }
                    },
                    onMessageLongPressWithBounds = { msgItem, anchorInWindow, maxBubbleWidth ->
                        val msg = msgItem.message
                        val hasMenu = msg.deletedAt == null || msg.senderId != currentUserId
                        if (hasMenu) {
                            activeMenuMessageId = msg.id
                            activeMenuAnchorInWindow = anchorInWindow
                            activeMenuMaxBubbleWidth = maxBubbleWidth
                        }
                    },
                    onToggleReaction = onToggleReaction,
                    onImageClick = { msg -> viewerMessage = msg },
                    onRetryFailedMessage = onRetryFailedMessage,
                    onDismissMenu = dismissActiveMenu,
                    onSwipeToReply = onReplyToMessage,
                    onViewProfileClick = onProfileClick,
                    topContentPadding = stableListTopPadding,
                    bottomContentPadding = stableListBottomPadding,
                    scrollToBottomFabBottomOffset = fabBottomOffset,
                    jumpToMessageId = jumpToMessageId,
                    onConsumeJumpToMessage = onConsumeJumpToMessage,
                    highlightedMessageId = activeSearchHighlightId,
                    todayOverride = todayOverride,
                    composerFlightBoundsInWindow = composerBoundsInWindow,
                    lastOptimisticMessageId = uiState.lastOptimisticMessageId,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(10f)
                    .background(
                        Brush.verticalGradient(
                            0.0f to chatColors.pageBackground.copy(alpha = 0.95f),
                            0.72f to chatColors.pageBackground.copy(alpha = 0.78f),
                            1.0f to Color.Transparent
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = 12.dp)
            ) {
                ChatHeader(
                    state = headerState,
                    chatColors = chatColors,
                    isListScrolled = isListScrolled,
                    reduceMotion = reduceMotion,
                    onBackClick = onBackClick,
                    onPillClick = openChatInfoAction,
                    onViewProfileClick = {
                        val handle = peerIntro?.handle ?: headerState.peerHandle
                        if (isDirect && !handle.isNullOrBlank()) {
                            onProfileClick(handle)
                        }
                    },
                    onOpenInfoClick = openChatInfoAction,
                    onOpenWallpaperClick = onOpenWallpaperClick,
                    onTogglePinConversation = onTogglePinConversation,
                    onToggleMuteConversation = onToggleMuteConversation
                )

                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = expandVertically(ChatMotion.layoutGestureSpring()) + fadeIn(ChatMotion.interactiveSpring()),
                    exit = shrinkVertically(ChatMotion.layoutGestureSpring()) + fadeOut(ChatMotion.fastOutBezierTween(140))
                ) {
                    DmInChatSearchBar(
                        query = inChatSearchQuery,
                        onQueryChange = { inChatSearchQuery = it },
                        matchCount = searchMatchMessageIds.size,
                        currentMatchIndex = searchMatchIndex,
                        onPrevMatch = {
                            if (searchMatchMessageIds.isNotEmpty()) {
                                val nextIdx = (searchMatchIndex + 1) % searchMatchMessageIds.size
                                searchMatchIndex = nextIdx
                                val targetId = searchMatchMessageIds[nextIdx]
                                val rowIdx = listItems.indexOfFirst {
                                    it is ChatListItem.MessageItem && it.message.id == targetId
                                }
                                if (rowIdx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(rowIdx) }
                                }
                            }
                        },
                        onNextMatch = {
                            if (searchMatchMessageIds.isNotEmpty()) {
                                val prevIdx = if (searchMatchIndex - 1 < 0) searchMatchMessageIds.lastIndex else searchMatchIndex - 1
                                searchMatchIndex = prevIdx
                                val targetId = searchMatchMessageIds[prevIdx]
                                val rowIdx = listItems.indexOfFirst {
                                    it is ChatListItem.MessageItem && it.message.id == targetId
                                }
                                if (rowIdx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(rowIdx) }
                                }
                            }
                        },
                        onClose = {
                            isSearchActive = false
                            inChatSearchQuery = ""
                            searchMatchIndex = 0
                        }
                    )
                }

                detail?.pinnedMessage?.let { pinned ->
                    DmPinnedMessageBanner(
                        pinnedMessage = pinned,
                        onUnpinClick = { onTogglePinMessage(pinned.id) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(10f)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.32f to chatColors.pageBackground.copy(alpha = 0.78f),
                            1.0f to chatColors.pageBackground.copy(alpha = 0.95f)
                        )
                    )
                    .padding(top = 12.dp)
                    .windowInsetsPadding(navAndImeInsets)
            ) {
                AnimatedVisibility(
                    visible = uiState.dmTypingUserIds.isNotEmpty() && !uiState.isVoiceListening,
                    enter = fadeIn(ChatMotion.interactiveSpring()) + expandVertically(ChatMotion.layoutGestureSpring()),
                    exit = fadeOut(ChatMotion.fastOutBezierTween(140)) + shrinkVertically(ChatMotion.layoutGestureSpring())
                ) {
                    DmBottomTypingPill(
                        chatColors = chatColors,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                    )
                }

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
                        reduceMotion = reduceMotion,
                        onInputBoundsChanged = { composerBoundsInWindow = it }
                    )
                }
            }
        }

        val anchorInRoot = remember(activeMenuAnchorInWindow, rootBoundsInWindow) {
            val windowRect = activeMenuAnchorInWindow ?: return@remember null
            val rootOffset = rootBoundsInWindow?.topLeft ?: Offset.Zero
            windowRect.translate(-rootOffset)
        }
        val focusedMenuState = remember(
            activeMenuItem,
            anchorInRoot,
            activeMenuMaxBubbleWidth,
            context,
            currentUserId,
            detail?.pinnedMessage?.id
        ) {
            val item = activeMenuItem ?: return@remember null
            val anchor = anchorInRoot ?: return@remember null
            FocusedMessageMenuState(
                item = item,
                anchorBoundsInRoot = anchor,
                maxBubbleWidth = activeMenuMaxBubbleWidth,
                menuItems = buildMenuForItem(item)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(30f)
        ) {
            ChatFocusedMessageMenuOverlay(
                state = focusedMenuState,
                chatColors = chatColors,
                reduceMotion = reduceMotion,
                onSelectReaction = { messageId, emoji ->
                    onToggleReaction(messageId, emoji)
                },
                onDismiss = dismissActiveMenu
            )
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
                onOpenWallpaper = onOpenWallpaperClick,
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
        if (selectTextContent != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectTextContent = null },
                sheetState = sheetState,
                containerColor = colors.cardBackground,
                scrimColor = colors.scrimColor,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.grabHandle)
                    )
                }
            ) {
                SelectTextSheetContent(
                    text = selectTextContent ?: "",
                    onClose = { selectTextContent = null },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }

        viewerMessage?.let { mediaMsg ->
            DmMediaViewerDialog(
                message = mediaMsg,
                onDismiss = { viewerMessage = null },
                onShowMessage = onShowSnackbar
            )
        }
    }
}

private fun buildDmMessageMenuItems(
    item: ChatListItem.MessageItem,
    isPinned: Boolean,
    context: Context,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onSelectText: () -> Unit,
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
    val selectTextLabel = context.getString(R.string.action_select_text)
    val editLabel = context.getString(R.string.dm_action_edit)
    val pinLabel = context.getString(if (isPinned) R.string.dm_action_unpin else R.string.dm_pin_message)
    val forwardLabel = context.getString(R.string.dm_action_forward)
    val deleteLabel = context.getString(R.string.dm_action_delete)
    val reportLabel = context.getString(R.string.dm_action_report)

    return buildList {
        if (!isDeleted) {
            add(ActionMenuItem(label = replyLabel, iconRes = R.drawable.ic_tg_reply, onClick = onReply))
            if (message.content.isNotBlank()) {
                add(
                    ActionMenuItem(
                        label = copyLabel,
                        iconRes = R.drawable.ic_tg_copy,
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("message", message.content))
                            onDismiss()
                        }
                    )
                )
                add(
                    ActionMenuItem(
                        label = selectTextLabel,
                        iconRes = R.drawable.ic_select_text,
                        onClick = onSelectText
                    )
                )
            }
            if (isOwn && message.messageType == "text") {
                add(ActionMenuItem(label = editLabel, iconRes = R.drawable.ic_tg_edit, onClick = onEdit))
            }
            add(ActionMenuItem(label = pinLabel, iconRes = R.drawable.ic_tg_pin, onClick = onTogglePin))
            add(
                ActionMenuItem(
                    label = forwardLabel,
                    iconRes = R.drawable.ic_tg_forward,
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
                    iconRes = R.drawable.ic_tg_delete,
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
private fun DmInChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit
) {
    val colors = BuildXTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .softShadow(colors.headerHaloShadowColor, cornerRadius = 18.dp, blurRadius = 10.dp, offsetY = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search),
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.dm_search_chat_hint),
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    fontFamily = BuildXFontFamily,
                    style = TextStyle(textDirection = TextDirection.ContentOrLtr),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = BuildXFontFamily,
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    textDirection = TextDirection.ContentOrLtr
                ),
                cursorBrush = SolidColor(colors.primaryBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
        if (query.isNotBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (matchCount > 0) {
                    stringResource(R.string.dm_search_matches_counter, currentMatchIndex + 1, matchCount)
                } else {
                    stringResource(R.string.dm_no_search_results)
                },
                color = colors.secondaryText,
                fontSize = 12.sp,
                fontFamily = BuildXFontFamily,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = matchCount > 0, onClick = onPrevMatch),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_down),
                contentDescription = stringResource(R.string.dm_search_prev_match),
                tint = if (matchCount > 0) colors.primaryText else colors.secondaryText.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = matchCount > 0, onClick = onNextMatch),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_down),
                contentDescription = stringResource(R.string.dm_search_next_match),
                tint = if (matchCount > 0) colors.primaryText else colors.secondaryText.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
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
}

@Composable
private fun DmBottomTypingPill(
    chatColors: app.buildx.chat.ui.chat.ChatColors,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dm_typing_dots")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(chatColors.receivedBubbleBg)
            .border(0.7.dp, chatColors.headerGlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (i in 0..2) {
            val dotAlpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = ChatMotion.TypingDotCycleMs / 2,
                        delayMillis = i * ChatMotion.TypingDotStaggerMs,
                        easing = ChatMotion.FastOutCubicBezier
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "typing_dot_$i"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(chatColors.typingDot)
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = stringResource(R.string.dm_typing),
            color = chatColors.receivedMetaText,
            fontSize = 11.5.sp,
            fontFamily = BuildXFontFamily
        )
    }
}

@Composable
private fun DmPinnedMessageBanner(
    pinnedMessage: DmMessage,
    onUnpinClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val chatColors = remember(colors) { colors.toChatColors() }
    val previewText = ChatBidiFormatter.isolateFirstStrong(
        pinnedMessage.content.ifBlank { stringResource(R.string.dm_media_attachment) }
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .softShadow(colors.headerHaloShadowColor, cornerRadius = 14.dp, blurRadius = 8.dp, offsetY = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(chatColors.pinnedBannerBg)
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
                tint = chatColors.pinnedBannerAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dm_pinned_message),
                    style = ChatTypography.pinnedBannerTitle.copy(color = chatColors.pinnedBannerAccent)
                )
                Text(
                    text = previewText,
                    style = ChatTypography.pinnedBannerPreview.copy(color = colors.primaryText),
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
