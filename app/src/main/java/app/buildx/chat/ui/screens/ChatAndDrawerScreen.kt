package app.buildx.chat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import app.buildx.chat.R
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.MainSurfacePane
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.ui.chat.ChatBidiFormatter
import app.buildx.chat.ui.chat.ChatDimens
import app.buildx.chat.ui.chat.ChatGlassTokens
import app.buildx.chat.ui.chat.ChatMotion
import app.buildx.chat.ui.chat.compatRenderEffectBlur
import app.buildx.chat.ui.chat.rememberReduceMotion
import app.buildx.chat.ui.chat.toChatColors
import app.buildx.chat.ui.components.ActionCapsule
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.ActionMenuPopup
import app.buildx.chat.ui.components.ActionRow
import app.buildx.chat.ui.components.AttachmentSheetContent
import app.buildx.chat.ui.components.BubblePopupPositionProvider
import app.buildx.chat.ui.components.Composer
import app.buildx.chat.ui.components.DrawerChatRow
import app.buildx.chat.ui.components.DrawerNavRow
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.ModeSelectorPopup
import app.buildx.chat.ui.components.SelectTextSheetContent
import app.buildx.chat.ui.components.StepsSheetContent
import app.buildx.chat.ui.components.ThinkingRow
import app.buildx.chat.ui.components.UserBubble
import app.buildx.chat.ui.components.VoiceDictationBar
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState

/**
 * Hosts the main surface (S1 Home empty state, S2 Composer states + ModeSelectorPopup banner,
 * S3 Chat, S4 Steps sheet, S5 Overflow menu, S6 Attachment sheet, S8 Notifications pane,
 * Media pane, S9 Incognito chat, S11 Voice input bar) AND the S7 Slide-Under Navigation Drawer
 * (~80% screen width, sliding the main surface by 80% with a 30% scrim, zero white flash).
 */
@Composable
fun ChatAndDrawerScreen(
    uiState: BuildXUiState,
    onToggleDrawer: (Boolean) -> Unit,
    onNewChat: () -> Unit,
    onStartIncognito: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onComposerTextChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onPlusClick: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onRemoveStagedAttachment: (String) -> Unit,
    onRateMessage: (String, Int) -> Unit,
    onCopyMessage: (String) -> Unit,
    onShareMessage: (String) -> Unit,
    onOpenStepsSheet: (List<ReasoningStep>) -> Unit,
    onCloseStepsSheet: () -> Unit,
    onToggleOverflowMenu: (Boolean) -> Unit,
    onShareConversationLink: () -> Unit,
    onTogglePinConversation: () -> Unit,
    onOpenRenameDialog: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
    onCloseAttachmentSheet: () -> Unit,
    onSeeAllPhotos: () -> Unit,
    onCameraCapture: () -> Unit,
    onToggleGallerySelection: (String) -> Unit,
    onConfirmGallerySelection: () -> Unit,
    onOpenRecentUploads: () -> Unit,
    onPickFileDocument: () -> Unit,
    onConfirmVoiceTranscript: () -> Unit,
    onCancelVoiceDictation: () -> Unit,
    onToggleUserMessageMenu: (String?) -> Unit = {},
    onToggleAssistantMessageMenu: (String?) -> Unit = {},
    onDismissAllMessageMenus: () -> Unit = {},
    onStartEditMessage: (String) -> Unit = {},
    onCancelEditMessage: () -> Unit = {},
    onSendEditMessage: (String, String) -> Unit = { _, _ -> },
    onResendUserMessage: (ChatMessage) -> Unit = {},
    onRegenerateAssistant: (ChatMessage) -> Unit = {},
    onOpenSelectText: (String) -> Unit = {},
    onCloseSelectText: () -> Unit = {},
    modifier: Modifier = Modifier,
    onToggleModePopup: ((Boolean?) -> Unit)? = null,
    onSelectMode: ((ChatMode) -> Unit)? = null,
    onTurnOnNotifications: (() -> Unit)? = null,
    onDismissNotificationPermissionCard: (() -> Unit)? = null,
    onSelectMediaAttachment: ((AttachmentRow) -> Unit)? = null,
    onConversationLongClick: (Conversation) -> Unit = {},
    onConversationOptionsClick: (Conversation) -> Unit = {}
) {
    val colors = BuildXTheme.colors
    val focusManager = LocalFocusManager.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduceMotion = rememberReduceMotion()

    val isOverflowVisible = uiState.isOverflowMenuOpen &&
        !uiState.isIncognito &&
        uiState.activeSurfacePane == MainSurfacePane.CHAT
    val hasModalOverlay = isOverflowVisible ||
        (uiState.isModePopupOpen && !uiState.isIncognito && uiState.activeSurfacePane == MainSurfacePane.CHAT) ||
        uiState.activeStepsSheetMessage != null ||
        uiState.isAttachmentSheetOpen ||
        uiState.activeSelectTextMessage != null
    val modalBackdropBlurDp by animateDpAsState(
        targetValue = if (hasModalOverlay && !reduceMotion) {
            (ChatGlassTokens.MenuBlurRadius * 0.45f).coerceAtMost(18.dp)
        } else {
            0.dp
        },
        animationSpec = ChatMotion.fastOutBezierTween(200),
        label = "chatModalBackdropBlur"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
    ) {
        val screenWidthDp = maxWidth
        val drawerWidthDp = screenWidthDp * 0.80f

        val drawerOffsetDp by animateDpAsState(
            targetValue = if (uiState.isDrawerOpen) drawerWidthDp else 0.dp,
            animationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "drawerSlide"
        )
        val drawerScrimAlpha by animateFloatAsState(
            targetValue = if (uiState.isDrawerOpen) 0.30f else 0f,
            animationSpec = tween(durationMillis = 260),
            label = "drawerScrim"
        )

        // S7 Left/Start Navigation Drawer (~80% width)
        Box(
            modifier = Modifier
                .width(drawerWidthDp)
                .fillMaxHeight()
                .background(colors.pageBackground)
        ) {
            DrawerContent(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                conversations = uiState.filteredConversations,
                unreadIds = uiState.unreadConversationIds,
                unreadNotificationsCount = uiState.unreadNotificationsCount,
                activeConversationId = uiState.activeConversationId,
                activeSurfacePane = uiState.activeSurfacePane,
                onStartIncognito = {
                    focusManager.clearFocus()
                    onStartIncognito()
                },
                onNewChat = {
                    focusManager.clearFocus()
                    onNewChat()
                },
                onOpenMedia = {
                    focusManager.clearFocus()
                    onNavigateToMedia()
                },
                onOpenProfile = {
                    focusManager.clearFocus()
                    onNavigateToProfile()
                },
                onOpenMessages = {
                    focusManager.clearFocus()
                    onNavigateToMessages()
                },
                onOpenSearch = {
                    focusManager.clearFocus()
                    onNavigateToSearch()
                },
                onSelectConversation = { id ->
                    focusManager.clearFocus()
                    onSelectConversation(id)
                },
                onOpenNotifications = {
                    focusManager.clearFocus()
                    onNavigateToNotifications()
                },
                onOpenSettings = {
                    focusManager.clearFocus()
                    onNavigateToSettings()
                },
                onConversationLongClick = onConversationLongClick,
                onConversationOptionsClick = onConversationOptionsClick
            )
        }

        // Main Surface (slides by drawerOffsetDp when drawer opens, matching t_019.png..t_021.png)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    val px = drawerOffsetDp.roundToPx()
                    IntOffset(if (isRtl) -px else px, 0)
                }
                .background(colors.pageBackground)
        ) {
            when (uiState.activeSurfacePane) {
                MainSurfacePane.CHAT -> {
                    MainChatSurface(
                        uiState = uiState,
                        modalBlurDp = modalBackdropBlurDp,
                        onMenuClick = {
                            focusManager.clearFocus()
                            onToggleDrawer(true)
                        },
                        onNewChatClick = {
                            focusManager.clearFocus()
                            onNewChat()
                        },
                        onMoreClick = {
                            onToggleOverflowMenu(!uiState.isOverflowMenuOpen)
                        },
                        onComposerTextChange = onComposerTextChange,
                        onToggleMode = onToggleMode,
                        onToggleModePopup = onToggleModePopup,
                        onSelectMode = onSelectMode,
                        onPlusClick = {
                            focusManager.clearFocus()
                            onPlusClick()
                        },
                        onMicClick = {
                            focusManager.clearFocus()
                            onMicClick()
                        },
                        onSendClick = {
                            focusManager.clearFocus()
                            onSendClick()
                        },
                        onStopClick = onStopClick,
                        onRemoveStagedAttachment = onRemoveStagedAttachment,
                        onRateMessage = onRateMessage,
                        onCopyMessage = onCopyMessage,
                        onShareMessage = onShareMessage,
                        onOpenStepsSheet = onOpenStepsSheet,
                        onConfirmVoiceTranscript = onConfirmVoiceTranscript,
                        onCancelVoiceDictation = onCancelVoiceDictation,
                        onToggleUserMessageMenu = onToggleUserMessageMenu,
                        onToggleAssistantMessageMenu = onToggleAssistantMessageMenu,
                        onDismissAllMessageMenus = onDismissAllMessageMenus,
                        onStartEditMessage = onStartEditMessage,
                        onCancelEditMessage = onCancelEditMessage,
                        onSendEditMessage = onSendEditMessage,
                        onResendUserMessage = onResendUserMessage,
                        onRegenerateAssistant = onRegenerateAssistant,
                        onOpenSelectText = onOpenSelectText
                    )
                }

                MainSurfacePane.NOTIFICATIONS -> {
                    NotificationsScreen(
                        showPermissionCard = false,
                        notifications = uiState.inAppNotifications,
                        onMenuOrBackClick = {
                            focusManager.clearFocus()
                            onToggleDrawer(true)
                        },
                        onNotificationClick = { notif ->
                            if (!notif.actorUsername.isNullOrBlank()) {
                                onNavigateToProfile()
                            }
                        }
                    )
                }

                MainSurfacePane.MEDIA -> {
                    MediaScreen(
                        attachments = uiState.userUploadedAttachments,
                        onBackClick = {
                            focusManager.clearFocus()
                            onToggleDrawer(true)
                        },
                        onSelectAttachmentForChat = { att ->
                            onSelectMediaAttachment?.invoke(att)
                        }
                    )
                }
            }

            // Scrim over main surface when drawer is open (30% black -> #B2B2B2 over white) + subtle edge shadow
            if (drawerScrimAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = drawerScrimAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onToggleDrawer(false)
                        }
                ) {
                    // Subtle vertical depth shadow along the seam between Drawer and Main Surface
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(10.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.14f * (drawerScrimAlpha / 0.30f)),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // S5 Anchored Header Overflow Menu Popup (spring-overshoot + scrim)
            AnimatedVisibility(
                visible = isOverflowVisible,
                enter = fadeIn(ChatMotion.fastOutBezierTween(160)),
                exit = fadeOut(ChatMotion.fastOutBezierTween(140))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrimColor.copy(alpha = 0.16f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onToggleOverflowMenu(false)
                        }
                ) {
                    val activeConv = uiState.conversations.firstOrNull { it.id == uiState.activeConversationId }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 62.dp, end = 20.dp)
                            .animateEnterExit(
                                enter = scaleIn(
                                    animationSpec = if (reduceMotion) tween(120) else ChatMotion.interactiveSpring(),
                                    initialScale = 0.86f,
                                    transformOrigin = TransformOrigin(if (isRtl) 0f else 1f, 0f)
                                ),
                                exit = scaleOut(
                                    animationSpec = ChatMotion.fastOutBezierTween(130),
                                    targetScale = 0.90f,
                                    transformOrigin = TransformOrigin(if (isRtl) 0f else 1f, 0f)
                                )
                            )
                    ) {
                        val strings = BuildXTheme.strings
                        val isPinned = activeConv?.pinned == true
                        TelegramContextMenuCard(
                            items = listOf(
                                ActionMenuItem(
                                    label = strings.shareLink,
                                    iconRes = R.drawable.ic_tg_forward,
                                    onClick = onShareConversationLink
                                ),
                                ActionMenuItem(
                                    label = if (isPinned) strings.unpinChat else strings.pinChat,
                                    iconRes = R.drawable.ic_tg_pin,
                                    onClick = onTogglePinConversation
                                ),
                                ActionMenuItem(
                                    label = strings.renameChat,
                                    iconRes = R.drawable.ic_tg_edit,
                                    onClick = onOpenRenameDialog
                                ),
                                ActionMenuItem(
                                    label = strings.deleteChat,
                                    iconRes = R.drawable.ic_tg_delete,
                                    isDestructive = true,
                                    onClick = onOpenDeleteDialog
                                )
                            )
                        )
                    }
                }
            }
        }

        // S4 Steps Bottom Sheet over 30% scrim
        val stepsForSheet = uiState.activeStepsSheetMessage
        AnimatedVisibility(
            visible = stepsForSheet != null,
            enter = fadeIn(ChatMotion.fastOutBezierTween(200)) +
                slideInVertically(if (reduceMotion) tween(160) else ChatMotion.layoutGestureSpring()) { it / 3 },
            exit = fadeOut(ChatMotion.fastOutBezierTween(170)) +
                slideOutVertically(ChatMotion.fastOutBezierTween(190)) { it / 3 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCloseStepsSheet
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (stepsForSheet != null) {
                    StepsSheetContent(
                        steps = stepsForSheet,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .navigationBarsPadding()
                    )
                }
            }
        }

        // S6 Attachment Bottom Sheet over 30% scrim
        AnimatedVisibility(
            visible = uiState.isAttachmentSheetOpen,
            enter = fadeIn(ChatMotion.fastOutBezierTween(220)) +
                slideInVertically(if (reduceMotion) tween(160) else ChatMotion.layoutGestureSpring()) { it / 2 },
            exit = fadeOut(ChatMotion.fastOutBezierTween(180)) +
                slideOutVertically(ChatMotion.fastOutBezierTween(190)) { it / 2 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCloseAttachmentSheet
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                AttachmentSheetContent(
                    recentGalleryImages = uiState.recentDeviceImages,
                    selectedUris = uiState.selectedGalleryUris,
                    onSeeAllClick = onSeeAllPhotos,
                    onCameraTileClick = onCameraCapture,
                    onToggleSelectGalleryImage = onToggleGallerySelection,
                    onConfirmSelectedGalleryImages = onConfirmGallerySelection,
                    onRecentlyUploadedClick = onOpenRecentUploads,
                    onFilesClick = onPickFileDocument,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .navigationBarsPadding()
                )
            }
        }

        // Select Text Bottom Sheet over 30% scrim
        val selectText = uiState.activeSelectTextMessage
        AnimatedVisibility(
            visible = selectText != null,
            enter = fadeIn(ChatMotion.fastOutBezierTween(200)) +
                slideInVertically(if (reduceMotion) tween(160) else ChatMotion.layoutGestureSpring()) { it / 3 },
            exit = fadeOut(ChatMotion.fastOutBezierTween(170)) +
                slideOutVertically(ChatMotion.fastOutBezierTween(190)) { it / 3 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCloseSelectText
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (selectText != null) {
                    SelectTextSheetContent(
                        text = selectText,
                        onClose = onCloseSelectText,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun MainChatSurface(
    uiState: BuildXUiState,
    modalBlurDp: Dp = 0.dp,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onMoreClick: () -> Unit,
    onComposerTextChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onToggleModePopup: ((Boolean?) -> Unit)?,
    onSelectMode: ((ChatMode) -> Unit)?,
    onPlusClick: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onRemoveStagedAttachment: (String) -> Unit,
    onRateMessage: (String, Int) -> Unit,
    onCopyMessage: (String) -> Unit,
    onShareMessage: (String) -> Unit,
    onOpenStepsSheet: (List<ReasoningStep>) -> Unit,
    onConfirmVoiceTranscript: () -> Unit,
    onCancelVoiceDictation: () -> Unit,
    onToggleUserMessageMenu: (String?) -> Unit,
    onToggleAssistantMessageMenu: (String?) -> Unit,
    onDismissAllMessageMenus: () -> Unit,
    onStartEditMessage: (String) -> Unit,
    onCancelEditMessage: () -> Unit,
    onSendEditMessage: (String, String) -> Unit,
    onResendUserMessage: (ChatMessage) -> Unit,
    onRegenerateAssistant: (ChatMessage) -> Unit,
    onOpenSelectText: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    val chatColors = colors.toChatColors()
    val strings = BuildXTheme.strings
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val hasMessages = uiState.messages.isNotEmpty()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val headerShadowBlur by animateDpAsState(
        targetValue = if (isScrolled) ChatDimens.HeaderShadowBlurScrolled else 0.dp,
        animationSpec = ChatMotion.fastOutBezierTween(200),
        label = "mainChatHeaderShadow"
    )
    var messageAnchorBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var aiMessageReactions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val activeFocusedMessageId = uiState.activeUserMessageMenuId ?: uiState.activeAssistantMessageMenuId
    val activeFocusedMessage = remember(activeFocusedMessageId, uiState.messages) {
        if (activeFocusedMessageId == null) null
        else uiState.messages.firstOrNull { it.id == activeFocusedMessageId }
    }
    val focusedMenuBlurDp by animateDpAsState(
        targetValue = if (activeFocusedMessage != null && !reduceMotion) 14.dp else 0.dp,
        animationSpec = ChatMotion.fastOutBezierTween(160),
        label = "aiChatFocusedMenuBlur"
    )
    val effectiveSurfaceBlurDp = maxOf(modalBlurDp, focusedMenuBlurDp).coerceAtMost(18.dp)

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            onDismissAllMessageMenus()
        }
    }

    // Auto-scroll during streaming unless user scrolled up
    val lastMsgContentLen by remember(uiState.messages) {
        derivedStateOf { uiState.messages.lastOrNull()?.content?.length ?: 0 }
    }
    LaunchedEffect(uiState.messages.size, lastMsgContentLen) {
        if (uiState.messages.isNotEmpty()) {
            val lastIndex = uiState.messages.lastIndex
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val nearBottom = lastIndex - lastVisible <= 2
            if (nearBottom || uiState.messages.size <= 2) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
    ) {
        // Underlying content (edge-to-edge message scroll behind floating glass header & composer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .compatRenderEffectBlur(effectiveSurfaceBlurDp)
        ) {
            // Strictly clipped message viewport (prevents scrolling messages from bleeding over header/status bar)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .clipToBounds()
            ) {
                // S1/S9 Center Brand Mark (fades out in 250ms when chat starts)
                androidx.compose.animation.AnimatedVisibility(
                    visible = !hasMessages,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(250)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(top = 56.dp, bottom = 76.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        MarkLoader(
                            size = 74.dp,
                            isSpinning = false
                        )
                        if (uiState.isIncognito) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = strings.incognitoEmptyNotice,
                                color = colors.secondaryText,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // S3 Conversation Messages (scrolls smoothly beneath floating glass header & composer)
                if (hasMessages) {
                    val topInsetDp = with(density) { headerHeightPx.toDp() }.coerceAtLeast(68.dp)
                    val bottomInsetDp = with(density) { composerHeightPx.toDp() }.coerceAtLeast(80.dp)
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = topInsetDp + 12.dp,
                            bottom = bottomInsetDp + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val lastAssistantId = uiState.messages.lastOrNull { it.role != "user" }?.id
                        items(uiState.messages, key = { it.id }) { msg ->
                            val isFocusedHidden = activeFocusedMessage?.id == msg.id
                            val reactionEmoji = aiMessageReactions[msg.id]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        messageAnchorBounds = messageAnchorBounds + (msg.id to coords.boundsInRoot())
                                    }
                                    .graphicsLayer { alpha = if (isFocusedHidden) 0f else 1f }
                            ) {
                                if (msg.role == "user") {
                                    UserBubble(
                                        message = msg,
                                        isMenuOpen = false,
                                        isEditing = uiState.editingMessageId == msg.id,
                                        editingText = if (uiState.editingMessageId == msg.id) uiState.editingMessageText else msg.content,
                                        isStreaming = uiState.isStreaming,
                                        onBubbleClick = { onToggleUserMessageMenu(msg.id) },
                                        onDismissMenu = { onDismissAllMessageMenus() },
                                        onCopy = {
                                            onCopyMessage(msg.content)
                                            onDismissAllMessageMenus()
                                        },
                                        onStartEdit = {
                                            onStartEditMessage(msg.id)
                                            onDismissAllMessageMenus()
                                        },
                                        onCancelEdit = { onCancelEditMessage() },
                                        onSendEdit = { newText -> onSendEditMessage(msg.id, newText) },
                                        onResend = {
                                            onResendUserMessage(msg)
                                            onDismissAllMessageMenus()
                                        },
                                        onSelectText = {
                                            onOpenSelectText(msg.content)
                                            onDismissAllMessageMenus()
                                        }
                                    )
                                } else {
                                    AssistantMessageBlock(
                                        message = msg,
                                        isLastAssistantMessage = msg.id == lastAssistantId,
                                        isMenuOpen = false,
                                        onOpenStepsSheet = {
                                            if (msg.reasoningSteps.isNotEmpty()) {
                                                onOpenStepsSheet(msg.reasoningSteps)
                                            }
                                        },
                                        onThumbUp = { onRateMessage(msg.id, 1) },
                                        onThumbDown = { onRateMessage(msg.id, -1) },
                                        onCopy = { onCopyMessage(msg.content) },
                                        onShare = { onShareMessage(msg.content) },
                                        onToggleMenu = { onToggleAssistantMessageMenu(msg.id) },
                                        onDismissMenu = { onDismissAllMessageMenus() },
                                        onRegenerate = {
                                            onRegenerateAssistant(msg)
                                            onDismissAllMessageMenus()
                                        },
                                        onSelectText = {
                                            onOpenSelectText(msg.content)
                                            onDismissAllMessageMenus()
                                        }
                                    )
                                }
                                if (reactionEmoji != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.primaryBlue.copy(alpha = 0.14f))
                                                .clickable {
                                                    aiMessageReactions = aiMessageReactions - msg.id
                                                }
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = reactionEmoji, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top Floating Glassmorphic Header Bar (strict zIndex(10f) + top protective scrim below statusBars)
            val topHeaderScrim = remember(colors.pageBackground) {
                Brush.verticalGradient(
                    0.00f to colors.pageBackground.copy(alpha = 0.95f),
                    0.62f to colors.pageBackground.copy(alpha = 0.78f),
                    1.00f to Color.Transparent
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(10f)
                    .background(topHeaderScrim)
                    .onSizeChanged { headerHeightPx = it.height }
            ) {
                if (hasMessages || isScrolled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(
                                if (headerShadowBlur > 0.dp) {
                                    Modifier.softShadow(
                                        color = chatColors.headerShadow,
                                        cornerRadius = 0.dp,
                                        blurRadius = headerShadowBlur,
                                        offsetY = ChatDimens.HeaderShadowOffsetScrolled
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .compatRenderEffectBlur(ChatGlassTokens.HeaderBlurRadius.coerceAtMost(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        chatColors.headerGlassBg.copy(
                                            alpha = if (colors.isDark) 0.90f else 0.94f
                                        ),
                                        chatColors.headerGlassBg.copy(
                                            alpha = if (colors.isDark) 0.78f else 0.84f
                                        )
                                    )
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_menu,
                        contentDescription = "Menu",
                        onClick = onMenuClick
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title + optional "Incognito chat" subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        val displayTitle = if (hasMessages) {
                            uiState.activeConversationTitle
                                .removePrefix("محادثة:")
                                .removePrefix("محادثة :")
                                .trim()
                                .ifEmpty { uiState.activeConversationTitle }
                        } else {
                            strings.appName
                        }

                        AnimatedContent(
                            targetState = displayTitle,
                            transitionSpec = {
                                fadeIn(tween(220)).togetherWith(fadeOut(tween(180)))
                            },
                            label = "headerTitleCrossfade"
                        ) { titleText ->
                            Text(
                                text = ChatBidiFormatter.isolateFirstStrong(titleText),
                                color = colors.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(textDirection = TextDirection.ContentOrLtr),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start
                            )
                        }
                        if (uiState.isIncognito) {
                            Text(
                                text = strings.incognitoSubtitle,
                                color = colors.secondaryText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Right side: empty on S1 Home (unless conversation exists), crossfades into ActionCapsule on S3 Chat
                    AnimatedContent(
                        targetState = Pair(uiState.isIncognito, hasMessages),
                        transitionSpec = {
                            fadeIn(tween(220)).togetherWith(fadeOut(tween(180)))
                        },
                        label = "headerRightCrossfade"
                    ) { (incognito, inChat) ->
                        when {
                            incognito -> {
                                HeaderCircleButton(
                                    iconRes = R.drawable.ic_incognito,
                                    contentDescription = "Exit incognito",
                                    onClick = onNewChatClick,
                                    backgroundColor = colors.primaryText,
                                    iconTint = colors.pageBackground
                                )
                            }
                            inChat -> {
                                ActionCapsule(
                                    onNewChatClick = onNewChatClick,
                                    onMoreClick = onMoreClick
                                )
                            }
                            else -> {
                                Spacer(modifier = Modifier.size(44.dp))
                            }
                        }
                    }
                }
            }

            // Bottom floating composer or S11 Voice Dictation bar (frame-synced with WindowInsets.ime)
            if (uiState.editingMessageId == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(10f)
                        .onSizeChanged { composerHeightPx = it.height }
                ) {
                    if (hasMessages) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .compatRenderEffectBlur(ChatGlassTokens.ComposerBlurRadius.coerceAtMost(18.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            colors.pageBackground.copy(alpha = 0.72f),
                                            colors.pageBackground.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                            .padding(bottom = 12.dp, top = 6.dp)
                    ) {
                        if (uiState.isVoiceListening) {
                            VoiceDictationBar(
                                amplitudes = uiState.voiceAmplitudes,
                                recognizedText = uiState.voicePartialTranscript,
                                onCancel = onCancelVoiceDictation,
                                onConfirm = onConfirmVoiceTranscript
                            )
                        } else {
                            Composer(
                                text = uiState.composerText,
                                onTextChange = onComposerTextChange,
                                mode = uiState.mode,
                                isStreaming = uiState.isStreaming,
                                isIncognito = uiState.isIncognito,
                                stagedAttachments = uiState.stagedAttachments,
                                onToggleMode = onToggleMode,
                                onModePillClick = {
                                    if (onToggleModePopup != null) {
                                        onToggleModePopup(null)
                                    } else {
                                        onToggleMode()
                                    }
                                },
                                onSelectMode = onSelectMode,
                                isModePopupOpen = uiState.isModePopupOpen,
                                onPlusClick = onPlusClick,
                                onMicClick = onMicClick,
                                onSendClick = onSendClick,
                                onStopClick = onStopClick,
                                onRemoveStagedAttachment = onRemoveStagedAttachment,
                                hasMessages = hasMessages,
                                focusRequester = focusRequester
                            )
                        }
                    }
                }
            }
        }

        // Foreground-Sharp Telegram Context Menu + Floating Reaction Pill Bar (zIndex(30f), outside blur)
        val focusedMsg = activeFocusedMessage
        val lastAssistantId = uiState.messages.lastOrNull { it.role != "user" }?.id
        val focusedMenuItems = remember(focusedMsg, uiState.isStreaming, lastAssistantId) {
            if (focusedMsg == null) {
                emptyList()
            } else if (focusedMsg.role == "user") {
                buildList {
                    if (focusedMsg.content.isNotBlank()) {
                        add(
                            ActionMenuItem(
                                label = strings.copyAction,
                                iconRes = R.drawable.ic_tg_copy,
                                onClick = {
                                    onCopyMessage(focusedMsg.content)
                                    onDismissAllMessageMenus()
                                }
                            )
                        )
                    }
                    if (!uiState.isStreaming) {
                        add(
                            ActionMenuItem(
                                label = strings.editAction,
                                iconRes = R.drawable.ic_tg_edit,
                                onClick = {
                                    onStartEditMessage(focusedMsg.id)
                                    onDismissAllMessageMenus()
                                }
                            )
                        )
                        add(
                            ActionMenuItem(
                                label = strings.resendAction,
                                iconRes = R.drawable.ic_tg_forward,
                                onClick = {
                                    onResendUserMessage(focusedMsg)
                                    onDismissAllMessageMenus()
                                }
                            )
                        )
                    }
                    if (focusedMsg.content.isNotBlank()) {
                        add(
                            ActionMenuItem(
                                label = strings.selectTextAction,
                                iconRes = R.drawable.ic_select_text,
                                onClick = {
                                    onOpenSelectText(focusedMsg.content)
                                    onDismissAllMessageMenus()
                                }
                            )
                        )
                    }
                }
            } else {
                buildList {
                    if (focusedMsg.id == lastAssistantId) {
                        add(
                            ActionMenuItem(
                                label = strings.regenerateAction,
                                iconRes = R.drawable.ic_refresh,
                                onClick = {
                                    onRegenerateAssistant(focusedMsg)
                                    onDismissAllMessageMenus()
                                }
                            )
                        )
                    }
                    if (focusedMsg.content.isNotBlank()) {
                        add(
                            ActionMenuItem(
                                label = strings.copyAction,
                                iconRes = R.drawable.ic_tg_copy,
                                onClick = {
                                    onCopyMessage(focusedMsg.content)
                                    onDismissAllMessageMenus()
                                }
                            )
                        )
                    }
                    add(
                        ActionMenuItem(
                            label = strings.shareAction,
                            iconRes = R.drawable.ic_tg_forward,
                            onClick = {
                                onShareMessage(focusedMsg.content)
                                onDismissAllMessageMenus()
                            }
                        )
                    )
                    if (focusedMsg.content.isNotBlank()) {
                        add(
                            ActionMenuItem(
                                label = strings.selectTextAction,
                                iconRes = R.drawable.ic_select_text,
                                onClick = {
                                    onOpenSelectText(focusedMsg.content)
                                    onDismissAllMessageMenus()
                                }
                            )
                        )
                    }
                }
            }
        }

        TelegramFocusedMessageOverlay(
            visible = focusedMsg != null,
            anchorBoundsInRoot = focusedMsg?.let { messageAnchorBounds[it.id] },
            isOwn = focusedMsg?.role == "user",
            menuItems = focusedMenuItems,
            selectedReaction = focusedMsg?.let { aiMessageReactions[it.id] },
            chatColors = chatColors,
            reduceMotion = reduceMotion,
            onSelectReaction = { emoji ->
                focusedMsg?.let { msg ->
                    val current = aiMessageReactions[msg.id]
                    aiMessageReactions = if (current == emoji) {
                        aiMessageReactions - msg.id
                    } else {
                        aiMessageReactions + (msg.id to emoji)
                    }
                    if (msg.role != "user" && emoji == "👍") {
                        onRateMessage(msg.id, 1)
                    }
                }
                onDismissAllMessageMenus()
            },
            onDismiss = onDismissAllMessageMenus
        ) {
            if (focusedMsg != null) {
                if (focusedMsg.role == "user") {
                    UserBubble(
                        message = focusedMsg,
                        isMenuOpen = false,
                        isEditing = false,
                        isStreaming = uiState.isStreaming
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.cardBackground)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        AssistantMessageBlock(
                            message = focusedMsg,
                            isLastAssistantMessage = focusedMsg.id == lastAssistantId,
                            isMenuOpen = false,
                            onOpenStepsSheet = {},
                            onThumbUp = {},
                            onThumbDown = {},
                            onCopy = {},
                            onShare = {},
                            onToggleMenu = {},
                            onDismissMenu = {},
                            onRegenerate = {},
                            onSelectText = {}
                        )
                    }
                }
            }
        }

        // S2 Floating Instant / Thinking Mode Switcher Popup Banner
        if (uiState.isModePopupOpen && !uiState.isIncognito) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrimColor.copy(alpha = 0.14f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onToggleModePopup?.invoke(false)
                    }
            )
        }

        AnimatedVisibility(
            visible = uiState.isModePopupOpen && !uiState.isIncognito,
            enter = fadeIn(ChatMotion.fastOutBezierTween(150)) + scaleIn(
                animationSpec = if (reduceMotion) tween(120) else ChatMotion.interactiveSpring(),
                initialScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 1f)
            ),
            exit = fadeOut(ChatMotion.fastOutBezierTween(130)) + scaleOut(
                animationSpec = ChatMotion.fastOutBezierTween(130),
                targetScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 1f)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(bottom = 76.dp)
        ) {
            ModeSelectorPopup(
                selectedMode = uiState.mode,
                onSelectMode = { chosenMode ->
                    if (onSelectMode != null) {
                        onSelectMode(chosenMode)
                    } else if (chosenMode != uiState.mode) {
                        onToggleMode()
                    } else {
                        onToggleModePopup?.invoke(false)
                    }
                }
            )
        }
    }
}

/**
 * Assistant message item in S3 Chat:
 * - NO bubble: plain full-width text on pageBackground.
 * - Directly after send, small rotating petal mark + reasoning step title & 2-line summary with purple rail.
 * - When answer starts, collapses to "Show thinking >" and streams text with soft fade-in.
 * - Resolves text direction per message from first strong character (Arabic aligns right/RTL, Latin aligns left/LTR).
 * - After completion, displays ActionRow (thumbs-up, thumbs-down, copy, more popup).
 */
@Composable
private fun AssistantMessageBlock(
    message: ChatMessage,
    isLastAssistantMessage: Boolean,
    isMenuOpen: Boolean,
    onOpenStepsSheet: () -> Unit,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRegenerate: () -> Unit,
    onSelectText: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val isStreaming = message.status == "streaming"
    val isReasoningPhase = isStreaming && message.content.isEmpty()
    val isRtl = remember(message.content, strings.isRtl) {
        isRtlText(message.content, defaultRtl = strings.isRtl)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // Thinking / Reasoning header row
        ThinkingRow(
            reasoningSteps = message.reasoningSteps,
            isStreamingReasoning = isReasoningPhase,
            onOpenStepsSheet = onOpenStepsSheet
        )

        // Assistant response text with soft fade-in and per-message Bidi alignment
        AnimatedVisibility(
            visible = message.content.isNotEmpty(),
            enter = fadeIn(tween(200))
        ) {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.content,
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Start,
                        style = TextStyle(
                            fontFamily = BuildXFontFamily,
                            textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (message.status == "stopped") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.responseStopped,
                color = colors.secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Action row (thumbs-up, thumbs-down, copy, more options popup) once generation finishes
        if (!isStreaming && message.content.isNotEmpty()) {
            ActionRow(
                rating = message.rating,
                onThumbUp = onThumbUp,
                onThumbDown = onThumbDown,
                onCopy = onCopy,
                onShare = onShare,
                onMoreClick = onToggleMenu,
                popupContent = {
                    if (isMenuOpen) {
                        val popupProvider = remember(isRtl) { BubblePopupPositionProvider(isRtl) }
                        Popup(
                            popupPositionProvider = popupProvider,
                            onDismissRequest = onDismissMenu,
                            properties = PopupProperties(
                                focusable = true,
                                dismissOnClickOutside = true,
                                dismissOnBackPress = true
                            )
                        ) {
                            var isVisible by remember { mutableStateOf(false) }
                            val reduceMotion = rememberReduceMotion()
                            LaunchedEffect(Unit) { isVisible = true }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isVisible,
                                enter = scaleIn(
                                    animationSpec = if (reduceMotion) tween(120) else ChatMotion.interactiveSpring(),
                                    initialScale = 0.85f,
                                    transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 0f)
                                ) + fadeIn(ChatMotion.fastOutBezierTween(140)),
                                exit = scaleOut(
                                    animationSpec = ChatMotion.fastOutBezierTween(130),
                                    targetScale = 0.90f,
                                    transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 0f)
                                ) + fadeOut(ChatMotion.fastOutBezierTween(120))
                            ) {
                                val items = buildList {
                                    if (isLastAssistantMessage) {
                                        add(
                                            ActionMenuItem(
                                                label = strings.regenerateAction,
                                                iconRes = R.drawable.ic_refresh,
                                                onClick = onRegenerate
                                            )
                                        )
                                    }
                                    add(
                                        ActionMenuItem(
                                            label = strings.shareAction,
                                            iconRes = R.drawable.ic_share,
                                            onClick = onShare
                                        )
                                    )
                                    if (message.content.isNotBlank()) {
                                        add(
                                            ActionMenuItem(
                                                label = strings.selectTextAction,
                                                iconRes = R.drawable.ic_select_text,
                                                onClick = onSelectText
                                            )
                                        )
                                    }
                                }
                                ActionMenuPopup(items = items)
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * S7 Navigation Drawer Content (matching t_015.png):
 * - Top: Search pill (#F1F3F0, search icon, "Search") + pure white floating Incognito circle button
 * - Navigation rows (22dp icon + bold label): New chat, Media
 * - Section label: "Chats" / "المحادثات" (bold gray)
 * - Chat rows: Pinned first with crisp angled pin icon beside title, then updated_at desc;
 *   BOLD + trailing blue dot when unread; clean titles without artificial prefix.
 * - Bottom: two pure white floating circular buttons (Notifications bell and Settings gear)
 *   anchored together at the bottom-end (Arrangement.End, spacedBy(12.dp)) over a soft fade!
 */
@Composable
private fun DrawerContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    conversations: List<Conversation>,
    unreadIds: Set<String>,
    unreadNotificationsCount: Int = 0,
    activeConversationId: String?,
    activeSurfacePane: MainSurfacePane,
    onStartIncognito: () -> Unit,
    onNewChat: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
    unreadDmCount: Int = 0,
    onOpenSearch: () -> Unit = {},
    onSelectConversation: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onConversationLongClick: (Conversation) -> Unit = {},
    onConversationOptionsClick: (Conversation) -> Unit = {}
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val isSearchRtl = remember(searchQuery, strings.isRtl) {
        isRtlText(searchQuery, defaultRtl = strings.isRtl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top row: Search field pill + pure white floating circular Incognito button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceMuted)
                        .clickable { onOpenSearch() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = strings.searchPlaceholder,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.searchPlaceholder,
                        color = colors.secondaryText,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Pure white floating circular Incognito button (44dp, real soft shadow)
                HeaderCircleButton(
                    iconRes = R.drawable.ic_incognito,
                    contentDescription = "Start incognito chat",
                    onClick = onStartIncognito,
                    backgroundColor = colors.cardBackground
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isNotBlank() && conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noMatchingConversations,
                        color = colors.secondaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // Navigation rows: New chat, Messages, Media, Profile
                DrawerNavRow(
                    iconRes = R.drawable.ic_edit_compose,
                    label = strings.newChat,
                    onClick = onNewChat
                )
                Spacer(modifier = Modifier.height(2.dp))
                DrawerNavRow(
                    iconRes = R.drawable.ic_chat_bubble,
                    label = stringResource(R.string.dm_nav_messages),
                    badgeCount = unreadDmCount,
                    onClick = onOpenMessages
                )
                Spacer(modifier = Modifier.height(2.dp))
                DrawerNavRow(
                    iconRes = R.drawable.ic_image,
                    label = strings.mediaTitle,
                    isActive = activeSurfacePane == MainSurfacePane.MEDIA,
                    onClick = onOpenMedia
                )
                Spacer(modifier = Modifier.height(2.dp))
                DrawerNavRow(
                    iconRes = R.drawable.ic_user,
                    label = strings.profileTitle,
                    onClick = onOpenProfile
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.chatsSection,
                    color = colors.secondaryText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )

                if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 24.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = strings.noConversationsYet,
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(conversations, key = { it.id }) { conv ->
                            DrawerChatRow(
                                title = conv.cleanTitle,
                                isUnread = unreadIds.contains(conv.id),
                                isPinned = conv.effectivePinned,
                                isLocked = conv.isLocked,
                                isSelected = activeSurfacePane == MainSurfacePane.CHAT && conv.id == activeConversationId,
                                onClick = { onSelectConversation(conv.id) },
                                onLongClick = { onConversationLongClick(conv) },
                                onOptionsClick = { onConversationOptionsClick(conv) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom floating bar: two pure white 44dp circular buttons (Notifications bell + Settings gear)
        // anchored together at the bottom-end over a soft vertical fade, matching t_015.png!
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.pageBackground.copy(alpha = 0f),
                            colors.pageBackground.copy(alpha = 0.92f),
                            colors.pageBackground
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_bell,
                        contentDescription = strings.notificationsTitle,
                        onClick = onOpenNotifications,
                        backgroundColor = colors.cardBackground
                    )
                    if (unreadNotificationsCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp, end = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.primaryBlue)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                HeaderCircleButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = strings.settingsTitle,
                    onClick = onOpenSettings,
                    backgroundColor = colors.cardBackground
                )
            }
        }
    }
}
