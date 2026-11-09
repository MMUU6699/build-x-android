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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.ui.components.ActionCapsule
import app.buildx.chat.ui.components.ActionRow
import app.buildx.chat.ui.components.AttachmentSheetContent
import app.buildx.chat.ui.components.Composer
import app.buildx.chat.ui.components.DrawerChatRow
import app.buildx.chat.ui.components.DrawerNavRow
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.HeaderOverflowPopup
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.StepsSheetContent
import app.buildx.chat.ui.components.ThinkingRow
import app.buildx.chat.ui.components.UserBubble
import app.buildx.chat.ui.components.VoiceDictationBar
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.viewmodel.BuildXUiState
import kotlin.math.roundToInt

/**
 * Hosts the main Chat surface (S1 Home empty state, S2 Composer states, S3 Chat, S4 Steps sheet,
 * S5 Overflow menu, S6 Attachment sheet, S9 Incognito chat, S11 Voice input bar) AND the S7
 * Slide-Under Navigation Drawer (~80% screen width, sliding the main surface right by 80% with a 30% scrim).
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
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val focusManager = LocalFocusManager.current

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
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "drawerSlide"
        )
        val drawerScrimAlpha by animateFloatAsState(
            targetValue = if (uiState.isDrawerOpen) 0.30f else 0f,
            animationSpec = tween(durationMillis = 280),
            label = "drawerScrim"
        )

        // S7 Left Navigation Drawer (~80% width)
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
                activeConversationId = uiState.activeConversationId,
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
                }
            )
        }

        // Main Chat Surface (slides right by drawerOffsetDp when drawer opens)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(drawerOffsetDp.roundToPx(), 0) }
                .background(colors.pageBackground)
        ) {
            MainChatSurface(
                uiState = uiState,
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
                onCancelVoiceDictation = onCancelVoiceDictation
            )

            // Scrim over main surface when drawer is open (30% black -> #B2B2B2 over white)
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
                )
            }

            // S5 Anchored Header Overflow Menu Popup
            if (uiState.isOverflowMenuOpen && !uiState.isIncognito) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onToggleOverflowMenu(false)
                        }
                ) {
                    val activeConv = uiState.conversations.firstOrNull { it.id == uiState.activeConversationId }
                    HeaderOverflowPopup(
                        isPinned = activeConv?.pinned == true,
                        onShareLink = onShareConversationLink,
                        onTogglePin = onTogglePinConversation,
                        onRename = onOpenRenameDialog,
                        onDelete = onOpenDeleteDialog,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 62.dp, end = 20.dp)
                    )
                }
            }
        }

        // S4 Steps Bottom Sheet over 30% scrim
        val stepsForSheet = uiState.activeStepsSheetMessage
        AnimatedVisibility(
            visible = stepsForSheet != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200))
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
            enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
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
    }
}

@Composable
private fun MainChatSurface(
    uiState: BuildXUiState,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onMoreClick: () -> Unit,
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
    onConfirmVoiceTranscript: () -> Unit,
    onCancelVoiceDictation: () -> Unit
) {
    val colors = BuildXTheme.colors
    val hasMessages = uiState.messages.isNotEmpty()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Header bar (inset 20dp horizontal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                AnimatedContent(
                    targetState = if (hasMessages) uiState.activeConversationTitle else "Build X",
                    transitionSpec = {
                        fadeIn(tween(220)).togetherWith(fadeOut(tween(180)))
                    },
                    label = "headerTitleCrossfade"
                ) { titleText ->
                    val isRtl = remember(titleText) { isRtlText(titleText) }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Text(
                            text = titleText,
                            color = colors.primaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (uiState.isIncognito) {
                    Text(
                        text = "Incognito chat",
                        color = colors.secondaryText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
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
                        // S1 Home empty state: right side intentionally left clean
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }
        }

        // Center content area: S1/S9 Empty State Mark OR S3 Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // S1/S9 Center Brand Mark (fades out in 250ms when chat starts)
            androidx.compose.animation.AnimatedVisibility(
                visible = !hasMessages,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250)),
                modifier = Modifier.align(Alignment.Center)
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
                            text = "Temporary chats won't appear in your history and Build X won't save or remember anything you talk about.",
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // S3 Conversation Messages
            if (hasMessages) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        if (msg.role == "user") {
                            UserBubble(text = msg.content)
                        } else {
                            AssistantMessageBlock(
                                message = msg,
                                onOpenStepsSheet = {
                                    if (msg.reasoningSteps.isNotEmpty()) {
                                        onOpenStepsSheet(msg.reasoningSteps)
                                    }
                                },
                                onThumbUp = { onRateMessage(msg.id, 1) },
                                onThumbDown = { onRateMessage(msg.id, -1) },
                                onCopy = { onCopyMessage(msg.content) },
                                onShare = { onShareMessage(msg.content) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom composer or S11 Voice Dictation bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 4.dp)
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

/**
 * Assistant message item in S3 Chat:
 * - NO bubble: plain full-width text on pageBackground.
 * - Directly after send, small rotating petal mark + reasoning step title & 2-line summary with purple rail.
 * - When answer starts, collapses to "Show thinking >" and streams text with soft fade-in.
 * - Resolves text direction per message from first strong character (Arabic aligns right/RTL, Latin aligns left/LTR).
 * - After completion, displays ActionRow (thumbs-up, thumbs-down, copy, share).
 */
@Composable
private fun AssistantMessageBlock(
    message: ChatMessage,
    onOpenStepsSheet: () -> Unit,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val colors = BuildXTheme.colors
    val isStreaming = message.status == "streaming"
    val isReasoningPhase = isStreaming && message.content.isEmpty()
    val isRtl = remember(message.content) { isRtlText(message.content) }

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
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (message.status == "stopped") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Response stopped",
                color = colors.secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Action row (thumbs-up, thumbs-down, copy, share) once generation finishes
        if (!isStreaming && message.content.isNotEmpty()) {
            ActionRow(
                rating = message.rating,
                onThumbUp = onThumbUp,
                onThumbDown = onThumbDown,
                onCopy = onCopy,
                onShare = onShare
            )
        }
    }
}

/**
 * S7 Navigation Drawer Content:
 * - Top: Search pill (#F1F3F0, search icon, "Search") + circular Incognito button
 * - When Search is focused and empty: "No recent searches" centered in muted gray
 * - List rows (24dp icon + bold label): New chat, Media (NO dead entries)
 * - Section label: "Chats" (bold gray)
 * - Chat rows: Pinned first, then updated_at desc; BOLD + trailing blue dot when unread
 * - Bottom: two circular buttons: Notifications (bell) and Settings (gear)
 */
@Composable
private fun DrawerContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    conversations: List<Conversation>,
    unreadIds: Set<String>,
    activeConversationId: String?,
    onStartIncognito: () -> Unit,
    onNewChat: () -> Unit,
    onOpenMedia: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = BuildXTheme.colors
    var isSearchFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top row: Search field pill + circular Incognito button (or Cancel when searching)
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
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = "Search",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        isSearchFocused = true
                        onSearchQueryChange(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = BuildXFontFamily,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primaryText
                    ),
                    cursorBrush = SolidColor(colors.primaryBlue),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    color = colors.secondaryText,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear search",
                        tint = colors.secondaryText,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onSearchQueryChange("") }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Circular Incognito button (44dp)
            HeaderCircleButton(
                iconRes = R.drawable.ic_incognito,
                contentDescription = "Start incognito chat",
                onClick = onStartIncognito,
                backgroundColor = colors.surfaceMuted
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
                    text = "No matching conversations",
                    color = colors.secondaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Navigation rows: New chat, Media (only working items)
            DrawerNavRow(
                iconRes = R.drawable.ic_edit_compose,
                label = "New chat",
                onClick = onNewChat
            )
            DrawerNavRow(
                iconRes = R.drawable.ic_image,
                label = "Media",
                onClick = onOpenMedia
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Chats",
                color = colors.secondaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    DrawerChatRow(
                        title = conv.title,
                        isUnread = unreadIds.contains(conv.id),
                        isPinned = conv.pinned,
                        isSelected = conv.id == activeConversationId,
                        onClick = { onSelectConversation(conv.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom row: two circular buttons — Notifications (bell) and Settings (gear)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_bell,
                contentDescription = "Notifications",
                onClick = onOpenNotifications,
                backgroundColor = colors.surfaceMuted
            )
            HeaderCircleButton(
                iconRes = R.drawable.ic_settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
                backgroundColor = colors.surfaceMuted
            )
        }
    }
}
