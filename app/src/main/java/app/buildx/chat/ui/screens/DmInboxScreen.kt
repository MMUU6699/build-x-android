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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.zIndex
import app.buildx.chat.R
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmMemberAvatar
import app.buildx.chat.data.model.DmPeerSummary
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.ui.chat.ChatBidiFormatter
import app.buildx.chat.ui.chat.ChatColors
import app.buildx.chat.ui.chat.ChatDimens
import app.buildx.chat.ui.chat.ChatGlassCapsuleContainer
import app.buildx.chat.ui.chat.ChatGlassTokens
import app.buildx.chat.ui.chat.ChatMotion
import app.buildx.chat.ui.chat.ChatTypography
import app.buildx.chat.ui.chat.UpdateStatusBarAppearance
import app.buildx.chat.ui.chat.compatRenderEffectBlur
import app.buildx.chat.ui.chat.rememberReduceMotion
import app.buildx.chat.ui.chat.toChatColors
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState
import app.buildx.chat.ui.viewmodel.DmFilterTab
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DmInboxScreen(
    uiState: BuildXUiState,
    onBackClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenNoteSheet: () -> Unit,
    onReplyToNote: (FeedNoteItem, String) -> Unit,
    onToggleNoteLike: (String) -> Unit = {},
    onCreateGroupClick: () -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onToggleMuteConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSelectFilterTab: (DmFilterTab) -> Unit = {},
    onToggleReadUnreadConversation: (String) -> Unit = {},
    onDeleteOrLeaveConversation: (String) -> Unit = {},
    onOpenDirectChatWithUser: (String) -> Unit = {},
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onSetConversationPin: (String, Boolean) -> Unit = { id, _ -> onTogglePinConversation(id) },
    onSetConversationMute: (String, String?) -> Unit = { id, _ -> onToggleMuteConversation(id) },
    onDeleteConversationForMe: (String) -> Unit = { id -> onDeleteOrLeaveConversation(id) },
    onLeaveGroup: (String) -> Unit = { id -> onDeleteOrLeaveConversation(id) },
    onLoadGroupDetail: (String) -> Unit = {},
    onUpdateGroupAvatar: (String, android.net.Uri) -> Unit = { _, _ -> },
    onAddGroupMembers: (String, List<String>) -> Unit = { _, _ -> },
    onSearchGroupMembers: (String) -> Unit = {},
    currentUserId: String? = null
) {
    DmInboxScreenImpl(
        uiState = uiState,
        onBackClick = onBackClick,
        onSearchQueryChange = onSearchQueryChange,
        onConversationClick = onConversationClick,
        onOpenProfile = onOpenProfile,
        onOpenNoteSheet = onOpenNoteSheet,
        onReplyToNote = onReplyToNote,
        onToggleNoteLike = onToggleNoteLike,
        onCreateGroupClick = onCreateGroupClick,
        onTogglePinConversation = onTogglePinConversation,
        onToggleMuteConversation = onToggleMuteConversation,
        modifier = modifier,
        onSelectFilterTab = onSelectFilterTab,
        onToggleReadUnreadConversation = onToggleReadUnreadConversation,
        onDeleteOrLeaveConversation = onDeleteOrLeaveConversation,
        onOpenDirectChatWithUser = onOpenDirectChatWithUser,
        onRenameGroup = onRenameGroup,
        onSetConversationPin = onSetConversationPin,
        onSetConversationMute = onSetConversationMute,
        onDeleteConversationForMe = onDeleteConversationForMe,
        onLeaveGroup = onLeaveGroup,
        onLoadGroupDetail = onLoadGroupDetail,
        onUpdateGroupAvatar = onUpdateGroupAvatar,
        onAddGroupMembers = onAddGroupMembers,
        onSearchGroupMembers = onSearchGroupMembers,
        currentUserId = currentUserId
    )
}

@Composable
private fun DmInboxScreenImpl(
    uiState: BuildXUiState,
    onBackClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenNoteSheet: () -> Unit,
    onReplyToNote: (FeedNoteItem, String) -> Unit,
    onToggleNoteLike: (String) -> Unit,
    onCreateGroupClick: () -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onToggleMuteConversation: (String) -> Unit,
    modifier: Modifier,
    onSelectFilterTab: (DmFilterTab) -> Unit,
    onToggleReadUnreadConversation: (String) -> Unit,
    onDeleteOrLeaveConversation: (String) -> Unit,
    onOpenDirectChatWithUser: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onSetConversationPin: (String, Boolean) -> Unit,
    onSetConversationMute: (String, String?) -> Unit,
    onDeleteConversationForMe: (String) -> Unit,
    onLeaveGroup: (String) -> Unit,
    onLoadGroupDetail: (String) -> Unit,
    onUpdateGroupAvatar: (String, android.net.Uri) -> Unit,
    onAddGroupMembers: (String, List<String>) -> Unit,
    onSearchGroupMembers: (String) -> Unit,
    currentUserId: String?
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val chatColors = remember(colors) { colors.toChatColors() }
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    UpdateStatusBarAppearance(
        isLightSurface = colors.pageBackground.luminance() > 0.5f
    )

    var activePopupItem by remember { mutableStateOf<DmInboxItem?>(null) }
    var activeRowBounds by remember { mutableStateOf<IntRect?>(null) }
    var rootLeftPx by remember { mutableIntStateOf(0) }
    var rootTopPx by remember { mutableIntStateOf(0) }
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    var selectedGroupInfoItem by remember { mutableStateOf<DmInboxItem?>(null) }
    var selectedReplyNote by remember { mutableStateOf<FeedNoteItem?>(null) }

    val isPopupOpen = activePopupItem != null
    val popupFocusProgress by animateFloatAsState(
        targetValue = if (isPopupOpen) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else ChatMotion.interactiveSpring(),
        label = "inbox_popup_focus_progress"
    )

    val isListScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }

    val topOverlayDp = with(density) { topOverlayHeightPx.toDp() }
    val statusTopFallback = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 124.dp
    val listTopPadding = if (topOverlayHeightPx > 0) topOverlayDp + 4.dp else statusTopFallback
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val customTextSelectionColors = remember(colors.primaryBlue) {
        TextSelectionColors(
            handleColor = colors.primaryBlue,
            backgroundColor = colors.primaryBlue.copy(alpha = 0.25f)
        )
    }

    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .onGloballyPositioned { coords ->
                    val rootBounds = coords.boundsInWindow()
                    rootLeftPx = rootBounds.left.roundToInt()
                    rootTopPx = rootBounds.top.roundToInt()
                }
        ) {
            val isSearching = uiState.dmSearchQuery.trim().isNotEmpty()
            val unreadCount = uiState.dmInboxCounters.unreadConversationsCount.takeIf {
                uiState.dmInboxItems.isNotEmpty() || it > 0
            } ?: uiState.dmInboxItems.count { it.effectiveIsUnread }
            val groupsCount = uiState.dmInboxCounters.groupsCount.takeIf {
                uiState.dmInboxItems.isNotEmpty() || it > 0
            } ?: uiState.dmInboxItems.count { it.kind == "group" }

            // Layer 0 + 1: Main Inbox Content (receives real-time RenderEffect blur when popup opens)
            val contentBlurDp = if (!reduceMotion && popupFocusProgress > 0.01f) {
                (10f * popupFocusProgress).dp
            } else {
                0.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .compatRenderEffectBlur(blurRadius = contentBlurDp)
            ) {
                // Scrollable Inbox Body underneath floating glass top chrome
                if (isSearching) {
                    DmSearchResultsContent(
                        query = uiState.dmSearchQuery.trim(),
                        conversations = uiState.dmInboxItems,
                        people = uiState.dmSearchPeopleResults,
                        isLoadingPeople = uiState.isDmSearchPeopleLoading,
                        activeDmDetail = uiState.activeDmDetail,
                        currentUserId = currentUserId,
                        chatColors = chatColors,
                        reduceMotion = reduceMotion,
                        contentTopPadding = listTopPadding,
                        contentBottomPadding = navBottomPadding + 24.dp,
                        onConversationClick = onConversationClick,
                        onPersonClick = onOpenProfile
                    )
                } else if (uiState.isDmInboxLoading && uiState.dmInboxItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = listTopPadding, bottom = navBottomPadding)
                    ) {
                        DmInboxSkeletonList()
                    }
                } else {
                    val filteredList = when (uiState.selectedDmFilterTab) {
                        DmFilterTab.ALL -> uiState.dmInboxItems
                        DmFilterTab.UNREAD -> uiState.dmInboxItems.filter { it.effectiveIsUnread }
                        DmFilterTab.GROUPS -> uiState.dmInboxItems.filter { it.kind == "group" }
                        else -> uiState.dmInboxItems
                    }

                    val ownNote = uiState.inboxNotesTray.firstOrNull { it.isOwn || it.userId == currentUserId }
                        ?: uiState.activeOwnNote?.let { pn ->
                            FeedNoteItem(
                                id = pn.id,
                                userId = pn.userId,
                                content = pn.content,
                                createdAt = pn.createdAt,
                                expiresAt = pn.expiresAt,
                                isOwn = true,
                                likesCount = pn.likesCount,
                                isLikedByMe = pn.isLikedByMe,
                                author = DmPeerSummary(
                                    id = pn.userId,
                                    displayName = uiState.profile?.displayName ?: "You",
                                    handle = uiState.profile?.handle,
                                    avatarUrl = uiState.profile?.avatarUrl,
                                    isVerified = uiState.profile?.isVerified ?: false
                                )
                            )
                        }
                    val ownAvatarUrl = ownNote?.author?.avatarUrl?.takeIf { it.isNotBlank() }
                        ?: uiState.profile?.avatarUrl
                    val ownDisplayName = uiState.profile?.displayName
                    val friendsNotes = uiState.inboxNotesTray.filterNot { it.isOwn || it.userId == currentUserId }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = listTopPadding,
                            bottom = navBottomPadding + 24.dp
                        )
                    ) {
                        // Notes Strip
                        item(key = "inbox_notes_strip") {
                            DmNotesStrip(
                                ownNote = ownNote,
                                ownAvatarUrl = ownAvatarUrl,
                                ownDisplayName = ownDisplayName,
                                friendsNotes = friendsNotes,
                                onOpenNoteSheet = onOpenNoteSheet,
                                onNoteClick = { note -> selectedReplyNote = note }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Filter Chips (All, Unread with badge, Groups)
                        item(key = "inbox_filter_chips") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DmFilterChip(
                                    label = stringResource(R.string.dm_filter_all),
                                    isSelected = uiState.selectedDmFilterTab == DmFilterTab.ALL,
                                    reduceMotion = reduceMotion,
                                    onClick = { onSelectFilterTab(DmFilterTab.ALL) }
                                )

                                DmFilterChip(
                                    label = stringResource(R.string.dm_filter_unread),
                                    count = unreadCount,
                                    isSelected = uiState.selectedDmFilterTab == DmFilterTab.UNREAD,
                                    reduceMotion = reduceMotion,
                                    onClick = { onSelectFilterTab(DmFilterTab.UNREAD) }
                                )

                                DmFilterChip(
                                    label = stringResource(R.string.dm_filter_groups),
                                    count = groupsCount,
                                    isSelected = uiState.selectedDmFilterTab == DmFilterTab.GROUPS,
                                    reduceMotion = reduceMotion,
                                    onClick = { onSelectFilterTab(DmFilterTab.GROUPS) }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (filteredList.isEmpty()) {
                            item(key = "inbox_empty_state") {
                                DmContextualEmptyState(
                                    tab = uiState.selectedDmFilterTab,
                                    onCreateGroupClick = onCreateGroupClick,
                                    onShowAllChats = { onSelectFilterTab(DmFilterTab.ALL) }
                                )
                            }
                        } else {
                            items(filteredList, key = { it.effectiveId }) { item ->
                                var itemCoordinates by remember { mutableStateOf<IntRect?>(null) }

                                Column(
                                    modifier = Modifier.animateItem(
                                        placementSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring()
                                    )
                                ) {
                                    DmConversationRow(
                                        item = item,
                                        currentUserId = currentUserId,
                                        activeDmDetail = uiState.activeDmDetail,
                                        chatColors = chatColors,
                                        reduceMotion = reduceMotion,
                                        onClick = { onConversationClick(item.effectiveId) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeRowBounds = itemCoordinates
                                            activePopupItem = item
                                        },
                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                            val bounds = coordinates.boundsInWindow()
                                            itemCoordinates = IntRect(
                                                left = bounds.left.roundToInt(),
                                                top = bounds.top.roundToInt(),
                                                right = bounds.right.roundToInt(),
                                                bottom = bounds.bottom.roundToInt()
                                            )
                                        }
                                    )

                                    HorizontalDivider(
                                        color = chatColors.divider,
                                        thickness = ChatDimens.DividerThickness,
                                        modifier = Modifier.padding(start = 84.dp, end = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Layer 2: Floating Glassmorphic Top Chrome (Status Bar Offset + Header + Search Pill)
                DmInboxFloatingTopBar(
                    searchQuery = uiState.dmSearchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onBackClick = onBackClick,
                    onCreateGroupClick = onCreateGroupClick,
                    isListScrolled = isListScrolled,
                    chatColors = chatColors,
                    reduceMotion = reduceMotion,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { topOverlayHeightPx = it.height }
                )
            }

            // 6. Long-Press Row Lift & Anchored Spring-Overshoot Popup Menu Overlay
            activePopupItem?.let { item ->
                var liftVisible by remember(item.effectiveId) { mutableStateOf(false) }
                var placedAbove by remember(item.effectiveId) { mutableStateOf(false) }
                LaunchedEffect(item.effectiveId) { liftVisible = true }

                val scrimAlpha by animateFloatAsState(
                    targetValue = if (liftVisible) 0.30f else 0f,
                    animationSpec = if (reduceMotion) tween(0) else ChatMotion.interactiveSpring(),
                    label = "popup_scrim_alpha"
                )
                val liftScale by animateFloatAsState(
                    targetValue = if (liftVisible) 1.02f else 1f,
                    animationSpec = if (reduceMotion) tween(0) else spring(
                        dampingRatio = 0.70f,
                        stiffness = 480f
                    ),
                    label = "popup_row_lift_scale"
                )

                // Dimming Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                activePopupItem = null
                                activeRowBounds = null
                            }
                        )
                )

                // Lifted Row Overlay aligned cleanly against parent window origin
                activeRowBounds?.let { bounds ->
                    val offsetX = bounds.left - rootLeftPx
                    val offsetY = bounds.top - rootTopPx
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .width(with(density) { bounds.width.toDp() })
                            .graphicsLayer {
                                scaleX = liftScale
                                scaleY = liftScale
                            }
                            .softShadow(colors.cardBackground.copy(alpha = 0.35f), cornerRadius = 16.dp, blurRadius = 18.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.pageBackground)
                    ) {
                        DmConversationRow(
                            item = item,
                            currentUserId = currentUserId,
                            activeDmDetail = uiState.activeDmDetail,
                            chatColors = chatColors,
                            reduceMotion = reduceMotion,
                            onClick = {},
                            onLongClick = {}
                        )
                    }

                    val gapPx = with(density) { 10.dp.roundToPx() }
                    val edgeMarginPx = with(density) { 16.dp.roundToPx() }
                    val positionProvider = remember(bounds, strings.isRtl, gapPx, edgeMarginPx) {
                        RowAnchoredPopupPositionProvider(
                            rowBounds = bounds,
                            isRtl = strings.isRtl,
                            gapPx = gapPx,
                            edgeMarginPx = edgeMarginPx,
                            onDirectionResolved = { above -> placedAbove = above }
                        )
                    }

                    // Anchored Popup Attached to Row with spring overshoot
                    Popup(
                        popupPositionProvider = positionProvider,
                        onDismissRequest = {
                            activePopupItem = null
                            activeRowBounds = null
                        }
                    ) {
                        AnimatedVisibility(
                            visible = liftVisible,
                            enter = if (reduceMotion) {
                                fadeIn(tween(0))
                            } else {
                                fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                                    initialScale = 0.84f,
                                    transformOrigin = TransformOrigin(
                                        pivotFractionX = if (strings.isRtl) 0.92f else 0.08f,
                                        pivotFractionY = if (placedAbove) 1f else 0f
                                    ),
                                    animationSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = 480f
                                    )
                                )
                            },
                            exit = fadeOut(spring(stiffness = 550f)) + scaleOut(targetScale = 0.92f)
                        ) {
                            DmConversationPopupCard(
                                item = item,
                                onPinToggle = { onSetConversationPin(item.effectiveId, !item.isPinned) },
                                onMuteSelect = { duration -> onSetConversationMute(item.effectiveId, duration) },
                                onReadUnreadToggle = { onToggleReadUnreadConversation(item.effectiveId) },
                                onViewProfile = {
                                    val handle = item.handle ?: item.peer?.handle
                                    if (!handle.isNullOrBlank()) onOpenProfile(handle)
                                },
                                onGroupInfo = {
                                    selectedGroupInfoItem = item
                                    onLoadGroupDetail(item.effectiveId)
                                },
                                onDeleteChat = { onDeleteConversationForMe(item.effectiveId) },
                                onLeaveGroup = { onLeaveGroup(item.effectiveId) },
                                onDismiss = {
                                    activePopupItem = null
                                    activeRowBounds = null
                                }
                            )
                        }
                    }
                }
            }

            // 7. Chat Info Sheet (group management)
            selectedGroupInfoItem?.let { groupItem ->
                val groupDetail = uiState.activeDmDetail?.takeIf {
                    it.conversation?.id == groupItem.effectiveId
                }
                DmChatInfoSheet(
                    detail = groupDetail ?: app.buildx.chat.data.model.DmConversationDetail(
                        conversation = app.buildx.chat.data.model.DmConversationMetadata(
                            id = groupItem.effectiveId,
                            kind = "group",
                            title = groupItem.title
                        )
                    ),
                    isDirect = false,
                    currentUserId = currentUserId ?: "",
                    messages = if (groupDetail != null) uiState.dmMessages else emptyList(),
                    isGroupAvatarUploading = uiState.isGroupAvatarUploading,
                    isAddingGroupMembers = uiState.isAddingGroupMembers,
                    memberCandidates = uiState.dmSearchPeopleResults,
                    isSearchingMembers = uiState.isDmSearchPeopleLoading,
                    isSubmittingReport = false,
                    reportError = null,
                    isBlocking = false,
                    onSearchMembers = onSearchGroupMembers,
                    onViewProfile = onOpenProfile,
                    onToggleMute = { onToggleMuteConversation(groupItem.effectiveId) },
                    onTogglePin = {
                        onSetConversationPin(groupItem.effectiveId, !groupItem.isPinned)
                    },
                    onUpdateGroupAvatar = { uri ->
                        onUpdateGroupAvatar(groupItem.effectiveId, uri)
                    },
                    onRenameGroup = { newTitle ->
                        onRenameGroup(groupItem.effectiveId, newTitle)
                    },
                    onAddMembers = { ids ->
                        onAddGroupMembers(groupItem.effectiveId, ids)
                    },
                    onLeaveGroup = {
                        onLeaveGroup(groupItem.effectiveId)
                        selectedGroupInfoItem = null
                    },
                    onClearHistory = {
                        onDeleteConversationForMe(groupItem.effectiveId)
                        selectedGroupInfoItem = null
                    },
                    onReport = { _, _ -> },
                    onBlock = {},
                    onDismiss = { selectedGroupInfoItem = null }
                )
            }

            // 8. Friend Note Interaction Sheet (View Profile, Like & Reply)
            selectedReplyNote?.let { note ->
                val liveNote = uiState.inboxNotesTray.firstOrNull {
                    it.effectiveNoteId == note.effectiveNoteId || it.id == note.id
                } ?: note
                DmNoteReplySheet(
                    note = liveNote,
                    onViewProfile = {
                        val handle = liveNote.author.handle
                        if (!handle.isNullOrBlank()) {
                            selectedReplyNote = null
                            onOpenProfile(handle)
                        }
                    },
                    onToggleLike = {
                        onToggleNoteLike(liveNote.effectiveNoteId)
                    },
                    onSendReply = { replyText ->
                        selectedReplyNote = null
                        onReplyToNote(liveNote, replyText)
                    },
                    onDismiss = { selectedReplyNote = null }
                )
            }
        }
    }
}

/**
 * Floating Glassmorphic Inbox Top Bar:
 * - Sits cleanly below `WindowInsets.statusBars`
 * - Uses hardware-accelerated `RenderEffect.createBlurEffect` on Android 12+ via `ChatGlassCapsuleContainer`
 *   with translucent frosted gradient + hairline divider fallback when scrolling
 * - Hosts the 56dp navigation row (44dp Back & Compose circles + bold title) and 48dp Search Pill
 */
@Composable
private fun DmInboxFloatingTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    isListScrolled: Boolean,
    chatColors: ChatColors,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    val shadowBlur by animateDpAsState(
        targetValue = if (isListScrolled) 18.dp else 0.dp,
        animationSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring(),
        label = "inbox_top_shadow_blur"
    )
    val shadowOffsetY by animateDpAsState(
        targetValue = if (isListScrolled) 4.dp else 0.dp,
        animationSpec = if (reduceMotion) tween(0) else ChatMotion.layoutGestureSpring(),
        label = "inbox_top_shadow_offset"
    )

    val topGlassColor = chatColors.headerGlassBg.copy(
        alpha = if (colors.isDark) {
            if (isListScrolled) ChatGlassTokens.HeaderGlassAlphaDark else 0.94f
        } else {
            if (isListScrolled) ChatGlassTokens.HeaderGlassAlphaLight else 0.96f
        }
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Real-time frosted glass backdrop behind header & search bar when content scrolls under
        Box(
            modifier = Modifier
                .matchParentSize()
                .compatRenderEffectBlur(blurRadius = if (isListScrolled) ChatGlassTokens.HeaderBlurRadius else 0.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to topGlassColor,
                        0.82f to topGlassColor.copy(alpha = (topGlassColor.alpha * 0.94f).coerceIn(0f, 1f)),
                        1.0f to colors.pageBackground.copy(alpha = if (isListScrolled) 0.72f else 0f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // 1. Header Bar (56dp row, 44dp circles at 20dp inset, 12dp spacing to 22sp bold title)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCircleButton(
                    iconRes = R.drawable.ic_back,
                    contentDescription = stringResource(R.string.dm_back),
                    onClick = onBackClick
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.dm_nav_messages),
                    color = colors.primaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(textDirection = TextDirection.ContentOrLtr),
                    modifier = Modifier.weight(1f)
                )

                HeaderCircleButton(
                    iconRes = R.drawable.ic_edit_compose,
                    contentDescription = stringResource(R.string.dm_compose_message),
                    onClick = onCreateGroupClick
                )
            }

            // 2. Floating Glass Search Field (48dp pill, 24dp radius, 16dp horizontal insets)
            ChatGlassCapsuleContainer(
                shape = RoundedCornerShape(24.dp),
                glassColor = colors.surfaceMuted.copy(alpha = if (colors.isDark) 0.88f else 0.92f),
                borderColor = chatColors.headerGlassBorder,
                shadowColor = if (isListScrolled) chatColors.headerShadow else Color.Transparent,
                cornerRadius = 24.dp,
                shadowBlur = shadowBlur,
                shadowOffsetY = shadowOffsetY,
                blurRadius = ChatGlassTokens.FloatingChipBlurRadius,
                isDark = colors.isDark,
                borderWidth = if (isListScrolled) ChatDimens.GlassSurfaceBorderWidth else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val isRtl = isRtlText(searchQuery, strings.isRtl)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.dm_search_hint),
                                color = colors.secondaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(textDirection = TextDirection.ContentOrLtr)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                                textDirection = TextDirection.ContentOrLtr
                            ),
                            cursorBrush = SolidColor(colors.primaryBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Clear",
                            tint = colors.secondaryText,
                            modifier = Modifier
                                .size(18.dp)
                                .pressScaleClickable { onSearchQueryChange("") }
                        )
                    }
                }
            }
        }

        if (isListScrolled) {
            HorizontalDivider(
                color = chatColors.divider.copy(alpha = 0.7f),
                thickness = ChatDimens.DividerThickness,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Filter Chip (36dp height, full radius, 14dp padding, 8dp gap) with spring-animated count badge.
 */
@Composable
private fun DmFilterChip(
    label: String,
    isSelected: Boolean,
    reduceMotion: Boolean,
    count: Int? = null,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) colors.surfaceMuted else Color.Transparent)
            .then(
                if (!isSelected) {
                    Modifier.border(1.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
                } else Modifier
            )
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (isSelected) colors.primaryText else colors.secondaryText,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            AnimatedVisibility(
                visible = count != null && count > 0,
                enter = if (reduceMotion) {
                    fadeIn(tween(0))
                } else {
                    fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                        initialScale = 0.45f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 500f)
                    )
                },
                exit = fadeOut(spring(stiffness = 550f)) + scaleOut(targetScale = 0.45f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .defaultMinSize(minWidth = 18.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.primaryBlue else colors.surfaceMuted)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val safeCount = count ?: 0
                        Text(
                            text = if (safeCount > 99) "99+" else safeCount.toString(),
                            color = if (isSelected) Color.White else colors.primaryText,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Notes Strip: a 124dp rail of 88dp cells. Each cell stacks a floating thought
 * bubble over a 68dp avatar. The bubble and its two tail dots draw above the
 * avatar rim (zIndex) so the note reads as a single speech balloon.
 */
@Composable
private fun DmNotesStrip(
    ownNote: FeedNoteItem?,
    ownAvatarUrl: String?,
    ownDisplayName: String?,
    friendsNotes: List<FeedNoteItem>,
    onOpenNoteSheet: () -> Unit,
    onNoteClick: (FeedNoteItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "own_note_slot") {
            NoteStripCell(
                avatarUrl = ownAvatarUrl,
                avatarContentDescription = ownDisplayName,
                label = stringResource(R.string.dm_your_note),
                bubbleText = ownNote?.content?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.dm_share_a_note),
                showAddBadge = true,
                isVerified = false,
                onClick = onOpenNoteSheet
            )
        }

        items(friendsNotes, key = { it.effectiveNoteId }) { note ->
            NoteStripCell(
                avatarUrl = note.author.avatarUrl,
                avatarContentDescription = note.author.displayName ?: note.author.handle,
                label = note.author.displayName ?: note.author.handle ?: "",
                bubbleText = note.content,
                showAddBadge = false,
                isVerified = note.author.isVerified,
                onClick = { onNoteClick(note) }
            )
        }
    }
}

/**
 * A single 90x130dp note cell: 68dp avatar 22dp above the baseline, a fixed-height
 * 48dp top bubble zone aligning every 1-line and 2-line thought bubble cleanly above
 * the avatar's top rim, and two distinct tail dots above the upper-start rim.
 */
@Composable
private fun NoteStripCell(
    avatarUrl: String?,
    avatarContentDescription: String?,
    label: String,
    bubbleText: String,
    showAddBadge: Boolean,
    isVerified: Boolean,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Box(
        modifier = Modifier
            .width(90.dp)
            .height(130.dp)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .size(68.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = avatarContentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_user),
                    contentDescription = avatarContentDescription,
                    tint = colors.secondaryText,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .zIndex(2f),
            contentAlignment = Alignment.BottomCenter
        ) {
            ThoughtBubbleCard(content = bubbleText)
        }

        if (showAddBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 20.dp)
                    .size(24.dp)
                    .zIndex(3f)
                    .clip(CircleShape)
                    .background(colors.pageBackground)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(colors.primaryText),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_plus),
                    contentDescription = null,
                    tint = colors.pageBackground,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ChatBidiFormatter.isolateFirstStrong(label),
                color = colors.secondaryText,
                fontSize = 11.5.sp,
                style = TextStyle(textDirection = TextDirection.ContentOrLtr),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isVerified) {
                Spacer(modifier = Modifier.width(3.dp))
                VerifiedBadge(size = 12.dp)
            }
        }
    }
}

@Composable
private fun ThoughtBubbleCard(
    content: String,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors

    Column(
        modifier = modifier.zIndex(2f),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 56.dp, max = 90.dp)
                .defaultMinSize(minHeight = 32.dp)
                .softShadow(
                    color = colors.headerHaloShadowColor,
                    cornerRadius = 16.dp,
                    blurRadius = 12.dp,
                    offsetY = 2.5.dp
                )
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBackground)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ChatBidiFormatter.isolateFirstStrong(content),
                color = colors.primaryText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(textDirection = TextDirection.ContentOrLtr),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 13.5.sp
            )
        }

        Column(
            modifier = Modifier.padding(start = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
            )
            Spacer(modifier = Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
            )
        }
    }
}

/**
 * Note Interaction Bottom Sheet shown when a friend's note is tapped.
 */
@Composable
internal fun DmNoteReplySheet(
    note: FeedNoteItem,
    onViewProfile: () -> Unit,
    onToggleLike: () -> Unit,
    onSendReply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val haptic = LocalHapticFeedback.current
    var replyText by remember(note.effectiveNoteId) { mutableStateOf("") }
    var heartPulse by remember(note.effectiveNoteId) { mutableStateOf(false) }
    val canSend = replyText.trim().isNotEmpty()
    val authorName = note.author.displayName?.takeIf { it.isNotBlank() } ?: note.author.handle ?: ""
    val handleText = note.author.handle?.takeIf { it.isNotBlank() }
        ?: note.author.username?.takeIf { it.isNotBlank() }
    val rawTime = formatLocalizedTime(note.createdAt)
    val nowLabel = stringResource(R.string.dm_time_now)
    val activeStatusText = if (rawTime.isBlank() || rawTime == nowLabel) {
        stringResource(R.string.dm_note_active_now)
    } else {
        stringResource(R.string.dm_note_active_ago, rawTime)
    }
    val heartCrimson = Color(0xFFFF3040)

    val heartScale by animateFloatAsState(
        targetValue = if (heartPulse) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        finishedListener = { heartPulse = false },
        label = "note_like_heart_scale"
    )

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
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.cardBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!handleText.isNullOrBlank()) {
                        Text(
                            text = ChatBidiFormatter.isolateLtr("@$handleText"),
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(textDirection = TextDirection.Ltr),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = activeStatusText,
                        color = colors.secondaryText,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceMuted)
                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(999.dp))
                        .pressScaleClickable(onClick = onViewProfile)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dm_view_profile),
                        color = colors.primaryText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 96.dp, max = 240.dp)
                        .softShadow(
                            color = colors.headerHaloShadowColor,
                            cornerRadius = 22.dp,
                            blurRadius = 16.dp,
                            offsetY = 3.dp
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceMuted)
                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = note.content,
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        style = TextStyle(textDirection = TextDirection.ContentOrLtr)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                )
                Spacer(modifier = Modifier.height(2.5.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceMuted)
                            .border(1.dp, colors.subtleSurfaceBorder, CircleShape)
                    ) {
                        if (!note.author.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = note.author.avatarUrl,
                                contentDescription = authorName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user),
                                contentDescription = authorName,
                                tint = colors.secondaryText,
                                modifier = Modifier
                                    .size(34.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = if (note.likesCount > 0) 20.dp else 8.dp, y = 4.dp)
                            .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                            .softShadow(
                                color = colors.headerHaloShadowColor,
                                cornerRadius = 17.dp,
                                blurRadius = 10.dp,
                                offsetY = 2.dp
                            )
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.cardBackground)
                            .border(
                                width = 0.9.dp,
                                color = if (note.isLikedByMe) heartCrimson.copy(alpha = 0.45f) else colors.subtleSurfaceBorder,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .pressScaleClickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                heartPulse = true
                                onToggleLike()
                            }
                            .padding(horizontal = if (note.likesCount > 0) 9.dp else 7.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (note.isLikedByMe) R.drawable.ic_heart_filled else R.drawable.ic_heart
                            ),
                            contentDescription = stringResource(
                                if (note.isLikedByMe) R.string.dm_note_unlike else R.string.dm_note_like
                            ),
                            tint = if (note.isLikedByMe) heartCrimson else colors.primaryText,
                            modifier = Modifier
                                .size(18.dp)
                                .scale(heartScale)
                        )
                        if (note.likesCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = note.likesCount.toString(),
                                color = if (note.isLikedByMe) heartCrimson else colors.primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
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
                        text = ChatBidiFormatter.isolateFirstStrong(authorName),
                        color = colors.primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(textDirection = TextDirection.ContentOrLtr),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (note.author.isVerified) {
                        Spacer(modifier = Modifier.width(5.dp))
                        VerifiedBadge(size = 16.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(colors.surfaceMuted)
                    .border(1.dp, colors.subtleSurfaceBorder, RoundedCornerShape(25.dp))
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dm_reply_to_note_hint, authorName),
                            color = colors.secondaryText,
                            fontSize = 14.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        maxLines = 3,
                        textStyle = TextStyle(
                            color = colors.primaryText,
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp,
                            textDirection = TextDirection.ContentOrLtr,
                            textAlign = if (isRtlText(replyText, strings.isRtl)) TextAlign.Right else TextAlign.Left
                        ),
                        cursorBrush = SolidColor(colors.primaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) colors.primaryBlue else colors.primaryBlue.copy(alpha = 0.35f)
                        )
                        .pressScaleClickable(enabled = canSend) {
                            onSendReply(replyText.trim())
                            replyText = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_up),
                        contentDescription = stringResource(R.string.dm_send),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private enum class InboxOutgoingTickState {
    NONE,
    PENDING,
    SENT,
    READ
}

/**
 * Conversation Row (72dp min height, 16dp horizontal, 8dp vertical, 56dp avatar,
 * BiDi-safe previews, outgoing ✓/✓✓ read ticks, pinned/muted indicators, and spring-animated unread badge).
 */
@Composable
private fun DmConversationRow(
    item: DmInboxItem,
    currentUserId: String?,
    activeDmDetail: DmConversationDetail?,
    chatColors: ChatColors,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightQuery: String = ""
) {
    val colors = BuildXTheme.colors
    val isUnread = item.effectiveIsUnread
    val isMuted = item.effectiveIsMuted

    val isOutgoingLastMessage = remember(item.senderFirstName, item.effectiveLastMessageSenderId, currentUserId, item.effectiveLastMessageText, item.effectiveLastMessageAt) {
        val hasLastMessage = item.effectiveLastMessageText != null || item.effectiveLastMessageAt != null
        hasLastMessage && (
            item.senderFirstName == "You" ||
                (!currentUserId.isNullOrBlank() && item.effectiveLastMessageSenderId == currentUserId)
            )
    }

    val tickState = remember(item, activeDmDetail, currentUserId, isOutgoingLastMessage) {
        if (!isOutgoingLastMessage) {
            InboxOutgoingTickState.NONE
        } else {
            val lastId = item.effectiveLastMessageId
            if (lastId != null && lastId.startsWith("local_")) {
                InboxOutgoingTickState.PENDING
            } else {
                val detail = activeDmDetail?.takeIf { it.conversation?.id == item.effectiveId }
                val peerRead = detail?.participants?.any { p ->
                    p.userId != currentUserId && (
                        (lastId != null && p.lastReadMessageId == lastId) ||
                            (!p.lastReadAt.isNullOrBlank() && !item.effectiveLastMessageAt.isNullOrBlank() &&
                                p.lastReadAt >= item.effectiveLastMessageAt!!)
                        )
                } ?: false
                if (peerRead) InboxOutgoingTickState.READ else InboxOutgoingTickState.SENT
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .pointerInput(item.effectiveId) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 56dp Avatar Container (Single or Composite 2-member)
            DmConversationAvatar(
                item = item,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Two-axis text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Line 1: Name + VerifiedBadge | Outgoing Read Tick + Localized Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isolatedTitle = ChatBidiFormatter.isolateFirstStrong(item.effectiveTitle)
                        val titleAnnotated = rememberHighlightedText(
                            text = isolatedTitle,
                            query = highlightQuery,
                            highlightColor = colors.primaryBlue
                        )
                        Text(
                            text = titleAnnotated,
                            color = colors.primaryText,
                            style = ChatTypography.inboxTitle.copy(
                                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.effectiveIsVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 15.dp)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Locked LTR cluster for outgoing read tick (✓ / ✓✓) + tabular timestamp
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (tickState != InboxOutgoingTickState.NONE) {
                                AnimatedContent(
                                    targetState = tickState,
                                    transitionSpec = {
                                        if (reduceMotion) {
                                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                        } else {
                                            (fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                                                initialScale = 0.72f,
                                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f)
                                            )) togetherWith fadeOut(tween(90))
                                        }
                                    },
                                    label = "inbox_tick_transition"
                                ) { state ->
                                    when (state) {
                                        InboxOutgoingTickState.PENDING -> Icon(
                                            painter = painterResource(id = R.drawable.ic_dm_clock),
                                            contentDescription = null,
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        InboxOutgoingTickState.SENT -> Icon(
                                            painter = painterResource(id = R.drawable.ic_check_single),
                                            contentDescription = null,
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        InboxOutgoingTickState.READ -> Icon(
                                            painter = painterResource(id = R.drawable.ic_check_double),
                                            contentDescription = null,
                                            tint = chatColors.inboxCheckReadAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        InboxOutgoingTickState.NONE -> {}
                                    }
                                }
                            }

                            val timeText = formatLocalizedTime(item.effectiveLastMessageAt)
                            if (timeText.isNotEmpty()) {
                                Text(
                                    text = ChatBidiFormatter.isolateLtr(timeText),
                                    color = if (isUnread) colors.primaryBlue else colors.secondaryText,
                                    style = ChatTypography.inboxTime.copy(
                                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Line 2: BiDi-safe Preview Text | Trailing Status (pin, mute, spring unread badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isRtlLayout = LocalLayoutDirection.current == LayoutDirection.Rtl
                    val previewString = buildConversationPreview(item, isRtlLayout = isRtlLayout)
                    val previewAnnotated = rememberHighlightedText(
                        text = previewString,
                        query = highlightQuery,
                        highlightColor = colors.primaryBlue
                    )
                    Text(
                        text = previewAnnotated,
                        color = if (isUnread) colors.primaryText else colors.secondaryText,
                        style = ChatTypography.inboxPreview.copy(
                            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                            textDirection = if (isRtlLayout) TextDirection.Rtl else TextDirection.Ltr
                        ),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Trailing indicators (pin 14dp, bell-off 14dp, spring-animated unread badge pill)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = item.isPinned,
                            enter = if (reduceMotion) fadeIn(tween(0)) else fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                                initialScale = 0.6f,
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f)
                            ),
                            exit = fadeOut(spring(stiffness = 550f)) + scaleOut(targetScale = 0.6f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pin),
                                contentDescription = "Pinned",
                                tint = chatColors.pinnedBadgeTint,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isMuted,
                            enter = if (reduceMotion) fadeIn(tween(0)) else fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                                initialScale = 0.6f,
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f)
                            ),
                            exit = fadeOut(spring(stiffness = 550f)) + scaleOut(targetScale = 0.6f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bell_off),
                                contentDescription = "Muted",
                                tint = chatColors.pinnedBadgeTint,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isUnread,
                            enter = if (reduceMotion) {
                                fadeIn(tween(0))
                            } else {
                                fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                                    initialScale = 0.45f,
                                    animationSpec = spring(dampingRatio = 0.60f, stiffness = 500f)
                                )
                            },
                            exit = fadeOut(spring(stiffness = 550f)) + scaleOut(targetScale = 0.45f)
                        ) {
                            val badgeCount = item.unreadCount
                            val countText = if (badgeCount > 99) "99+" else if (badgeCount > 0) badgeCount.toString() else "•"
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .defaultMinSize(minWidth = 20.dp)
                                    .clip(CircleShape)
                                    .background(if (isMuted) chatColors.unreadBadgeMutedBg else chatColors.unreadBadgeBg)
                                    .padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = countText,
                                    transitionSpec = {
                                        if (reduceMotion) {
                                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                        } else {
                                            (fadeIn(ChatMotion.interactiveSpring()) + scaleIn(
                                                initialScale = 0.72f,
                                                animationSpec = spring(dampingRatio = 0.62f, stiffness = 500f)
                                            )) togetherWith (fadeOut(tween(80)) + scaleOut(targetScale = 0.8f))
                                        }
                                    },
                                    label = "unread_badge_count"
                                ) { targetCount ->
                                    Text(
                                        text = targetCount,
                                        color = chatColors.unreadBadgeText,
                                        style = ChatTypography.unreadBadge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString = remember(text, query, highlightColor) {
    if (query.isBlank()) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            append(text)
            var startIndex = text.indexOf(query, ignoreCase = true)
            while (startIndex >= 0) {
                val endIndex = startIndex + query.length
                addStyle(
                    style = SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold
                    ),
                    start = startIndex,
                    end = endIndex
                )
                startIndex = text.indexOf(query, startIndex = endIndex, ignoreCase = true)
            }
        }
    }
}

/**
 * 56dp avatar component:
 * Direct conversations (or groups with a custom photo) render a single clipped
 * circle. Groups without a photo render two overlapping member circles inside an
 * unclipped 56dp box: a 38dp back circle at TopStart and a 40dp front circle at
 * BottomEnd separated by a 2.5dp page-background cutout.
 */
@Composable
private fun DmConversationAvatar(
    item: DmInboxItem,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val isGroup = item.kind == "group"
    val photo = item.effectiveAvatarUrl

    if (!isGroup || !photo.isNullOrBlank()) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
        ) {
            if (!photo.isNullOrBlank()) {
                AsyncImage(
                    model = photo,
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
                        .size(30.dp)
                        .align(Alignment.Center)
                )
            }
        }
        return
    }

    val members = item.memberAvatars
    val firstInitial = members.getOrNull(0)?.displayName?.firstOrNull()?.uppercase()
        ?: item.effectiveTitle.firstOrNull()?.uppercase()
        ?: "G"
    val secondInitial = members.getOrNull(1)?.displayName?.firstOrNull()?.uppercase()
        ?: item.effectiveTitle.drop(1).firstOrNull()?.uppercase()
        ?: firstInitial

    Box(modifier = modifier) {
        GroupAvatarCircle(
            member = members.getOrNull(0),
            fallbackInitial = firstInitial,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(38.dp)
                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(45.dp)
                .background(colors.pageBackground, CircleShape)
                .padding(2.5.dp)
        ) {
            GroupAvatarCircle(
                member = members.getOrNull(1),
                fallbackInitial = secondInitial,
                modifier = Modifier
                    .fillMaxSize()
                    .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
            )
        }
    }
}

@Composable
private fun GroupAvatarCircle(
    member: DmMemberAvatar?,
    fallbackInitial: String,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        if (!member?.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = member?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = member?.displayName?.firstOrNull()?.uppercase() ?: fallbackInitial
            Text(
                text = initial,
                color = colors.primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Builds preview string with strict Unicode BiDi isolation via `ChatBidiFormatter`
 * so mixed-direction prefixes ("You: هلا" or "أنت: Hello") never invert the prefix or colon.
 */
@Composable
private fun buildConversationPreview(
    item: DmInboxItem,
    isRtlLayout: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl
): String {
    val isGroup = item.kind == "group"
    val lastText = item.effectiveLastMessageText

    if (lastText == null && item.effectiveLastMessageAt == null) {
        return stringResource(R.string.dm_no_messages_yet)
    }

    val rawPrefix = when {
        item.senderFirstName == "You" -> stringResource(R.string.dm_you_prefix).removeSuffix(":").trim()
        isGroup && !item.senderFirstName.isNullOrBlank() -> item.senderFirstName.trim()
        else -> null
    }

    val content = when (item.effectiveLastMessageType) {
        "image", "media" -> stringResource(R.string.dm_media_photo)
        else -> lastText ?: stringResource(R.string.dm_no_messages_yet)
    }

    return ChatBidiFormatter.formatInboxPreview(
        prefix = rawPrefix,
        body = content,
        isRtlContext = isRtlLayout
    )
}

/**
 * Anchors the conversation context-menu popup cleanly above or below the captured
 * conversation row bounds (in window space), keeping it separated by [gapPx] and
 * safely inside [edgeMarginPx] screen bounds for both LTR and RTL locales.
 */
internal class RowAnchoredPopupPositionProvider(
    private val rowBounds: IntRect,
    private val isRtl: Boolean,
    private val gapPx: Int,
    private val edgeMarginPx: Int = 24,
    private val onDirectionResolved: (placedAbove: Boolean) -> Unit = {}
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val rawX = if (isRtl) {
            rowBounds.right - edgeMarginPx - popupContentSize.width
        } else {
            rowBounds.left + edgeMarginPx
        }
        val x = rawX.coerceIn(edgeMarginPx, maxX)

        val maxY = (windowSize.height - popupContentSize.height - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val spaceBelow = (windowSize.height - edgeMarginPx) - rowBounds.bottom - gapPx
        val spaceAbove = rowBounds.top - edgeMarginPx - gapPx
        val placeBelow = spaceBelow >= popupContentSize.height || spaceBelow >= spaceAbove

        onDirectionResolved(!placeBelow)

        val rawY = if (placeBelow) {
            rowBounds.bottom + gapPx
        } else {
            rowBounds.top - gapPx - popupContentSize.height
        }
        val y = rawY.coerceIn(edgeMarginPx, maxY)

        return IntOffset(x, y)
    }
}

/**
 * Localized relative timestamp: "now", "5m", "3h", "Mon", "12 Nov"
 */
@Composable
private fun formatLocalizedTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    val instant = remember(isoString) {
        runCatching { Instant.parse(isoString) }.getOrNull()
    } ?: return ""
    val now = remember { Instant.now() }
    val diffSeconds = now.epochSecond - instant.epochSecond

    if (diffSeconds < 60) {
        return stringResource(R.string.dm_time_now)
    }

    return remember(instant, diffSeconds) {
        when {
            diffSeconds < 3600 -> "${diffSeconds / 60}m"
            diffSeconds < 86400 -> "${diffSeconds / 3600}h"
            diffSeconds < 604800 -> {
                val zdt = instant.atZone(ZoneId.systemDefault())
                DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(zdt)
            }
            else -> {
                val zdt = instant.atZone(ZoneId.systemDefault())
                DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(zdt)
            }
        }
    }
}

@Composable
private fun DmContextualEmptyState(
    tab: DmFilterTab,
    onCreateGroupClick: () -> Unit,
    onShowAllChats: () -> Unit
) {
    val colors = BuildXTheme.colors

    val (titleRes, subtitleRes, showAction, actionTextRes, actionClick) = when (tab) {
        DmFilterTab.UNREAD -> Tuple5(
            R.string.dm_empty_unread_title,
            R.string.dm_empty_unread_subtitle,
            true,
            R.string.dm_show_all_chats,
            onShowAllChats
        )
        DmFilterTab.GROUPS -> Tuple5(
            R.string.dm_empty_groups_title,
            R.string.dm_empty_groups_subtitle,
            true,
            R.string.dm_create_a_group,
            onCreateGroupClick
        )
        else -> Tuple5(
            R.string.dm_empty_inbox_title,
            R.string.dm_empty_inbox_subtitle,
            true,
            R.string.dm_start_a_chat,
            onCreateGroupClick
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_edit_compose),
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(titleRes),
            color = colors.primaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(subtitleRes),
            color = colors.secondaryText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        if (showAction) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.primaryBlue)
                    .pressScaleClickable(onClick = actionClick)
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(actionTextRes),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DmInboxSkeletonList() {
    val colors = BuildXTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceMuted)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(colors.surfaceMuted)
                    )
                }
            }
        }
    }
}

@Composable
private fun DmSearchResultsContent(
    query: String,
    conversations: List<DmInboxItem>,
    people: List<PersonSearchResult>,
    isLoadingPeople: Boolean,
    activeDmDetail: DmConversationDetail?,
    currentUserId: String?,
    chatColors: ChatColors,
    reduceMotion: Boolean,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    onConversationClick: (String) -> Unit,
    onPersonClick: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    val matchedConvs = conversations.filter {
        it.effectiveTitle.contains(query, ignoreCase = true) ||
            (it.effectiveLastMessageText?.contains(query, ignoreCase = true) == true)
    }

    if (matchedConvs.isEmpty() && people.isEmpty() && !isLoadingPeople) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = contentTopPadding + 32.dp,
                    bottom = contentBottomPadding + 32.dp,
                    start = 32.dp,
                    end = 32.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.dm_no_search_results_title),
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dm_no_search_results_subtitle),
                color = colors.secondaryText,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentTopPadding + 4.dp,
                bottom = contentBottomPadding
            )
        ) {
            if (matchedConvs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dm_conversations_header),
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                items(matchedConvs, key = { "conv_${it.effectiveId}" }) { item ->
                    DmConversationRow(
                        item = item,
                        currentUserId = currentUserId,
                        activeDmDetail = activeDmDetail,
                        chatColors = chatColors,
                        reduceMotion = reduceMotion,
                        onClick = { onConversationClick(item.effectiveId) },
                        onLongClick = {},
                        highlightQuery = query
                    )
                }
            }

            if (people.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dm_people_header),
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                items(people, key = { "person_${it.id}" }) { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPersonClick(person.username) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceMuted)
                        ) {
                            if (!person.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = person.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ChatBidiFormatter.isolateFirstStrong(
                                        person.displayName.ifBlank { person.username }
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(textDirection = TextDirection.ContentOrLtr)
                                )
                                if (person.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerifiedBadge(size = 14.dp)
                                }
                            }
                            Text(
                                text = ChatBidiFormatter.isolateLtr("@${person.username}"),
                                color = colors.secondaryText,
                                fontSize = 13.sp,
                                style = TextStyle(textDirection = TextDirection.Ltr)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E
)
