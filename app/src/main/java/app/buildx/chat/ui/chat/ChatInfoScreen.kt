package app.buildx.chat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
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
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal enum class ChatInfoTab {
    MEMBERS,
    MEDIA,
    FILES,
    LINKS
}

internal data class ChatInfoOverflowItem(
    val iconRes: Int,
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Full-screen Telegram-grade Chat / Group Details screen (`dm/{conversationId}/info`).
 *
 * Features:
 * - Pinned status-bar-safe `56.dp` top bar (`zIndex(10f)`) with Back `<`, Edit `✎`, and More `⋮`.
 * - GPU-accelerated collapsing hero (`88.dp -> 36.dp` avatar, centered title + subtitle) that
 *   never pushes content into the system status bar.
 * - 4 equal-width Telegram squircle action cards (`Add`/`Message`, `Mute`, `Search`, `Leave`/`More`).
 * - Clean `16.dp` rounded surface info card + sticky `Members | Media | Files | Links` tab bar.
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
    val strings = BuildXTheme.strings
    val chatColors = remember(colors) { colors.toChatColors() }
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scrollState = rememberScrollState()

    val conversation = detail?.conversation
    val isDirect = conversation?.kind != "group"
    val peer = detail?.peerIntro
    val myParticipant = detail?.participants?.firstOrNull { it.userId == currentUserId }
    val isAdmin = myParticipant?.role == "admin" ||
        myParticipant?.role == "owner" ||
        (!isDirect && conversation?.createdBy != null && conversation.createdBy == currentUserId)
    val isMuted = myParticipant?.isMuted == true
    val isPinned = myParticipant?.isPinned == true
    val activeParticipants = remember(detail?.participants) {
        detail?.participants?.filter { it.status == "active" }.orEmpty()
    }

    val sharedMedia = remember(messages) { ChatMessageMapper.extractSharedMedia(messages) }
    val sharedFiles = remember(messages) { ChatMessageMapper.extractSharedFiles(messages) }
    val sharedLinks = remember(messages) { ChatMessageMapper.extractSharedLinks(messages) }

    var selectedTab by remember(isDirect) {
        mutableStateOf(if (isDirect) ChatInfoTab.MEDIA else ChatInfoTab.MEMBERS)
    }
    var viewerMediaMessage by remember { mutableStateOf<DmMessage?>(null) }
    var isRenaming by remember { mutableStateOf(false) }
    var titleDraft by remember(conversation?.title) { mutableStateOf(conversation?.title ?: "") }
    var isAddingMembersOpen by remember { mutableStateOf(false) }
    var memberQuery by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var memberToRemove by remember { mutableStateOf<DmParticipant?>(null) }

    var isInlineSearchOpen by remember { mutableStateOf(false) }
    var inlineSearchQuery by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

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
    val heroSubtitle = if (isDirect) {
        peer?.handle?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
    } else {
        stringResource(R.string.dm_members_count, activeParticipants.size)
    }

    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    val statusBarHeightDp = with(density) { statusBarHeightPx.toDp() }
    val topBarHeightDp = ChatDimens.ChatInfoTopBarHeight
    val avatarExpandedDp = ChatDimens.ChatInfoAvatarExpandedSize
    val avatarCollapsedDp = ChatDimens.ChatInfoAvatarCollapsedSize
    val collapseRangePx = with(density) { 136.dp.toPx() }

    val topBarHeightPx = with(density) { topBarHeightDp.toPx() }
    val avatarExpandedPx = with(density) { avatarExpandedDp.toPx() }
    val avatarCollapsedPx = with(density) { avatarCollapsedDp.toPx() }
    val expandedTopGapPx = with(density) { 8.dp.toPx() }
    val backButtonEndPx = with(density) { (16.dp + 44.dp + 12.dp).toPx() }
    val titleTopGapPx = with(density) { 12.dp.toPx() }
    val collapsedAvatarToTitleGapPx = with(density) { 10.dp.toPx() }
    val hasEditButton = !isDirect && isAdmin
    val collapsedTitleEndDp = if (hasEditButton) 114.dp else 68.dp

    val overflowItems = buildOverflowMenuItems(
        isDirect = isDirect,
        isAdmin = isAdmin,
        isPinned = isPinned,
        isRtl = strings.isRtl,
        peerHandle = peer?.handle,
        peerLabel = peerFirstLabel,
        hasWallpaper = onOpenWallpaper != null,
        onViewProfile = onViewProfile,
        onTogglePin = onTogglePin,
        onOpenWallpaper = onOpenWallpaper,
        onRenameGroup = {
            titleDraft = conversation?.title.orEmpty()
            isRenaming = true
        },
        onChangePhoto = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onClearHistory = { showClearConfirm = true },
        onReport = { showReportSheet = true },
        onBlock = { showBlockConfirm = true },
        onLeaveGroup = { showLeaveConfirm = true }
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(chatColors.pageBackground)
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        var tabBarTopInColumnPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }

        // Scrollable Body (zIndex 1f), strictly clipped below statusBars so scrolled content
        // can never collide with the system clock or battery icons.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .padding(top = statusBarHeightDp)
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
            ) {
                // Hero clearance inside scroll body (top bar + 8dp + 88dp avatar + 12dp + title/sub + 14dp)
                Spacer(modifier = Modifier.height(topBarHeightDp + 174.dp))

                // 4-Squircle Telegram Action Row
                ChatInfoFourSquircleRow(
                    isDirect = isDirect,
                    isAdmin = isAdmin,
                    isMuted = isMuted,
                    isRtl = strings.isRtl,
                    hasSearchAction = onSearchInChat != null || messages.any { it.deletedAt == null && it.content.isNotBlank() },
                    onPrimaryClick = {
                        if (isDirect) {
                            onBackClick()
                        } else if (isAdmin) {
                            selectedTab = ChatInfoTab.MEMBERS
                            isAddingMembersOpen = true
                            selectedMemberIds = emptySet()
                        } else {
                            selectedTab = ChatInfoTab.MEMBERS
                        }
                    },
                    onToggleMute = onToggleMute,
                    onSearchClick = {
                        if (onSearchInChat != null) {
                            onSearchInChat()
                        } else {
                            isInlineSearchOpen = !isInlineSearchOpen
                            if (!isInlineSearchOpen) inlineSearchQuery = ""
                        }
                    },
                    onSecondaryEndClick = {
                        if (isDirect) {
                            showOverflowMenu = true
                        } else {
                            showLeaveConfirm = true
                        }
                    }
                )

                // Inline Message Search Bar (for standalone navigation without external search handler)
                AnimatedVisibility(
                    visible = isInlineSearchOpen,
                    enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                ) {
                    ChatInfoInlineSearchCard(
                        query = inlineSearchQuery,
                        onQueryChange = { inlineSearchQuery = it },
                        messages = messages,
                        isRtl = strings.isRtl,
                        onSelectMessage = { msgId ->
                            isInlineSearchOpen = false
                            inlineSearchQuery = ""
                            onJumpToMessage(msgId)
                        },
                        onClose = {
                            isInlineSearchOpen = false
                            inlineSearchQuery = ""
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Clean Rounded Info / Settings Surface Card (16.dp radius)
                ChatInfoDetailsCard(
                    isDirect = isDirect,
                    isAdmin = isAdmin,
                    isPinned = isPinned,
                    isRtl = strings.isRtl,
                    peer = peer,
                    peerFirstLabel = peerFirstLabel,
                    groupTitle = displayTitle,
                    memberCount = activeParticipants.size,
                    detail = detail,
                    activeWallpaper = activeWallpaper,
                    onViewProfile = onViewProfile,
                    onTogglePin = onTogglePin,
                    onOpenWallpaper = onOpenWallpaper,
                    onCreateGroupWithPeer = onCreateGroupWithPeer,
                    onChangeGroupPhoto = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sticky Telegram Tab Bar (Members | Media | Files | Links)
                val tabs = remember(isDirect) {
                    if (isDirect) {
                        listOf(ChatInfoTab.MEDIA, ChatInfoTab.FILES, ChatInfoTab.LINKS)
                    } else {
                        listOf(ChatInfoTab.MEMBERS, ChatInfoTab.MEDIA, ChatInfoTab.FILES, ChatInfoTab.LINKS)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(4f)
                        .onGloballyPositioned { coords ->
                            tabBarTopInColumnPx = coords.positionInParent().y
                        }
                ) {
                    ChatInfoStickyTabBar(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        isRtl = strings.isRtl,
                        pageBackground = chatColors.pageBackground,
                        onSelectTab = { selectedTab = it },
                        modifier = Modifier.graphicsLayer {
                            val threshold = tabBarTopInColumnPx - topBarHeightPx
                            translationY = if (tabBarTopInColumnPx < Float.MAX_VALUE && scrollState.value > threshold) {
                                (scrollState.value - threshold).coerceAtLeast(0f)
                            } else {
                                0f
                            }
                        }
                    )
                }

                // Active Tab Content
                ChatInfoTabContent(
                    selectedTab = selectedTab,
                    isDirect = isDirect,
                    isAdmin = isAdmin,
                    isRtl = strings.isRtl,
                    currentUserId = currentUserId,
                    createdBy = conversation?.createdBy,
                    activeParticipants = activeParticipants,
                    isAddingMembersOpen = isAddingMembersOpen,
                    memberQuery = memberQuery,
                    memberCandidates = memberCandidates,
                    isSearchingMembers = isSearchingMembers,
                    isAddingGroupMembers = isAddingGroupMembers,
                    selectedMemberIds = selectedMemberIds,
                    sharedMedia = sharedMedia,
                    sharedFiles = sharedFiles,
                    sharedLinks = sharedLinks,
                    onOpenAddMembers = {
                        isAddingMembersOpen = true
                        selectedMemberIds = emptySet()
                    },
                    onMemberQueryChange = { memberQuery = it },
                    onToggleMemberSelection = { id ->
                        selectedMemberIds = if (id in selectedMemberIds) {
                            selectedMemberIds - id
                        } else {
                            selectedMemberIds + id
                        }
                    },
                    onCancelAddMembers = {
                        isAddingMembersOpen = false
                        selectedMemberIds = emptySet()
                    },
                    onConfirmAddMembers = {
                        if (selectedMemberIds.isNotEmpty()) {
                            onAddMembers(selectedMemberIds.toList())
                            isAddingMembersOpen = false
                            selectedMemberIds = emptySet()
                        }
                    },
                    onViewProfile = onViewProfile,
                    onRequestRemoveMember = { memberToRemove = it },
                    onOpenMedia = { viewerMediaMessage = it },
                    onJumpToMessage = onJumpToMessage
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // Pinned Status-Bar-Safe Top Bar Background & Actions (zIndex 10f)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .height(statusBarHeightDp + topBarHeightDp)
                .drawBehind {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    drawRect(color = chatColors.pageBackground)
                    if (f > 0.72f) {
                        val lineAlpha = ((f - 0.72f) / 0.28f).coerceIn(0f, 1f)
                        drawLine(
                            color = colors.hairlineBorder.copy(alpha = lineAlpha),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 0.7.dp.toPx()
                        )
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(topBarHeightDp)
                    .padding(horizontal = 16.dp),
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
                if (hasEditButton) {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_tg_edit,
                        contentDescription = stringResource(R.string.dm_info_edit_group_name),
                        onClick = {
                            titleDraft = conversation?.title.orEmpty()
                            isRenaming = true
                        },
                        backgroundColor = chatColors.headerGlassBg,
                        iconTint = colors.primaryText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_more_vertical,
                        contentDescription = if (strings.isRtl) "المزيد" else "More",
                        onClick = { showOverflowMenu = true },
                        backgroundColor = chatColors.headerGlassBg,
                        iconTint = colors.primaryText
                    )
                    if (showOverflowMenu) {
                        ChatInfoOverflowPopup(
                            items = overflowItems,
                            chatColors = chatColors,
                            onDismiss = { showOverflowMenu = false }
                        )
                    }
                }
            }
        }

        // Collapsing Hero Avatar + Title + Subtitle Overlay (zIndex 11f, strictly below status bar)
        val expandedCenterX = viewportWidthPx / 2f
        val expandedCenterY = statusBarHeightPx + topBarHeightPx + expandedTopGapPx + (avatarExpandedPx / 2f)
        val collapsedCenterX = if (isRtl) {
            viewportWidthPx - (backButtonEndPx + avatarCollapsedPx / 2f)
        } else {
            backButtonEndPx + (avatarCollapsedPx / 2f)
        }
        val collapsedCenterY = statusBarHeightPx + (topBarHeightPx / 2f)
        val targetScale = avatarCollapsedPx / avatarExpandedPx

        // Avatar centered horizontally via Alignment.TopCenter so LTR/RTL never double-flips offset
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(11f)
                .offset {
                    IntOffset(
                        x = 0,
                        y = (statusBarHeightPx + topBarHeightPx + expandedTopGapPx).roundToInt()
                    )
                }
                .size(avatarExpandedDp)
                .graphicsLayer {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    val s = 1f - f * (1f - targetScale)
                    scaleX = s
                    scaleY = s
                    translationX = (collapsedCenterX - expandedCenterX) * f
                    translationY = (collapsedCenterY - expandedCenterY) * f
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .softShadow(
                        color = chatColors.headerShadow,
                        cornerRadius = 44.dp,
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
                } else if (isDirect) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(34.dp)
                    )
                } else {
                    Text(
                        text = displayTitle.firstOrNull()?.uppercase() ?: "G",
                        color = colors.primaryText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BuildXFontFamily
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
                        .size(26.dp)
                        .graphicsLayer {
                            val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                            alpha = (1f - f * 3.5f).coerceIn(0f, 1f)
                        }
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .border(1.dp, colors.subtleSurfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = stringResource(R.string.dm_change_group_photo),
                        tint = colors.primaryText,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Expanded Centered Hero Title + Subtitle (fades & lifts as user scrolls)
        val heroTextExpandedTopPx = statusBarHeightPx + topBarHeightPx + expandedTopGapPx + avatarExpandedPx + titleTopGapPx
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .zIndex(11f)
                .fillMaxWidth()
                .offset { IntOffset(0, heroTextExpandedTopPx.roundToInt()) }
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    alpha = (1f - (f / 0.65f)).coerceIn(0f, 1f)
                    translationY = -f * with(density) { 44.dp.toPx() }
                    val s = 1f - 0.14f * f
                    scaleX = s
                    scaleY = s
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayTitle,
                    color = colors.primaryText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDirect && peer?.isVerified == true) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VerifiedBadge(size = 18.dp)
                }
            }
            if (heroSubtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = heroSubtitle,
                    color = colors.secondaryText,
                    fontSize = 13.5.sp,
                    fontFamily = BuildXFontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Collapsed Top-Bar Title + Subtitle (fades in cleanly beside collapsed 36.dp avatar)
        val collapsedTitleStartDp = with(density) {
            (backButtonEndPx + avatarCollapsedPx + collapsedAvatarToTitleGapPx).toDp()
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .zIndex(11f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(topBarHeightDp)
                .padding(start = collapsedTitleStartDp, end = collapsedTitleEndDp)
                .graphicsLayer {
                    val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                    val fadeIn = ((f - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    alpha = fadeIn
                    translationY = (1f - fadeIn) * with(density) { 10.dp.toPx() }
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayTitle,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDirect && peer?.isVerified == true) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                }
            }
            if (heroSubtitle.isNotBlank()) {
                Text(
                    text = heroSubtitle,
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

    if (isRenaming) {
        ChatInfoRenameGroupDialog(
            titleDraft = titleDraft,
            isRtl = strings.isRtl,
            onTitleDraftChange = { titleDraft = it },
            onConfirm = {
                val clean = titleDraft.trim()
                if (clean.isNotEmpty()) {
                    onRenameGroup(clean)
                    isRenaming = false
                }
            },
            onDismiss = { isRenaming = false }
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
internal fun buildOverflowMenuItems(
    isDirect: Boolean,
    isAdmin: Boolean,
    isPinned: Boolean,
    isRtl: Boolean,
    peerHandle: String?,
    peerLabel: String,
    hasWallpaper: Boolean,
    onViewProfile: (String) -> Unit,
    onTogglePin: () -> Unit,
    onOpenWallpaper: (() -> Unit)?,
    onRenameGroup: () -> Unit,
    onChangePhoto: () -> Unit,
    onClearHistory: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onLeaveGroup: () -> Unit
): List<ChatInfoOverflowItem> {
    val items = mutableListOf<ChatInfoOverflowItem>()
    if (isDirect && !peerHandle.isNullOrBlank()) {
        items.add(
            ChatInfoOverflowItem(
                iconRes = R.drawable.ic_user,
                label = stringResource(R.string.dm_view_profile),
                onClick = { onViewProfile(peerHandle) }
            )
        )
    }
    if (!isDirect && isAdmin) {
        items.add(
            ChatInfoOverflowItem(
                iconRes = R.drawable.ic_tg_edit,
                label = stringResource(R.string.dm_info_edit_group_name),
                onClick = onRenameGroup
            )
        )
        items.add(
            ChatInfoOverflowItem(
                iconRes = R.drawable.ic_camera,
                label = stringResource(R.string.dm_change_group_photo),
                onClick = onChangePhoto
            )
        )
    }
    items.add(
        ChatInfoOverflowItem(
            iconRes = R.drawable.ic_tg_pin,
            label = stringResource(if (isPinned) R.string.dm_unpin_chat else R.string.dm_pin_chat),
            onClick = onTogglePin
        )
    )
    if (hasWallpaper && onOpenWallpaper != null) {
        items.add(
            ChatInfoOverflowItem(
                iconRes = R.drawable.ic_wallpaper,
                label = stringResource(R.string.dm_wallpaper),
                onClick = onOpenWallpaper
            )
        )
    }
    items.add(
        ChatInfoOverflowItem(
            iconRes = R.drawable.ic_tg_delete,
            label = stringResource(R.string.dm_clear_history),
            onClick = onClearHistory
        )
    )
    if (isDirect) {
        items.add(
            ChatInfoOverflowItem(
                iconRes = R.drawable.ic_flag,
                label = stringResource(R.string.dm_info_report_account),
                onClick = onReport
            )
        )
        items.add(
            ChatInfoOverflowItem(
                iconRes = R.drawable.ic_block,
                label = stringResource(R.string.dm_info_block_name, peerLabel),
                isDestructive = true,
                onClick = onBlock
            )
        )
    } else {
        items.add(
            ChatInfoOverflowItem(
                iconRes = R.drawable.ic_tg_leave,
                label = stringResource(R.string.dm_leave_group),
                isDestructive = true,
                onClick = onLeaveGroup
            )
        )
    }
    return items
}

@Composable
internal fun ChatInfoOverflowPopup(
    items: List<ChatInfoOverflowItem>,
    chatColors: ChatColors,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(ChatDimens.ContextMenuWidth)
                .softShadow(
                    color = colors.composerShadowColor,
                    cornerRadius = ChatDimens.ContextMenuCornerRadius,
                    blurRadius = 24.dp,
                    offsetY = 8.dp
                )
                .clip(RoundedCornerShape(ChatDimens.ContextMenuCornerRadius))
                .background(chatColors.contextMenuBg)
                .border(0.8.dp, chatColors.contextMenuBorder, RoundedCornerShape(ChatDimens.ContextMenuCornerRadius))
                .padding(vertical = 4.dp)
        ) {
            items.forEachIndexed { index, item ->
                val tint = if (item.isDestructive) colors.destructiveRed else colors.primaryText
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            item.onClick()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = BuildXFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index < items.lastIndex && items[index + 1].isDestructive && !item.isDestructive) {
                    HorizontalDivider(
                        color = colors.hairlineBorder,
                        thickness = 0.6.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatInfoFourSquircleRow(
    isDirect: Boolean,
    isAdmin: Boolean,
    isMuted: Boolean,
    isRtl: Boolean,
    hasSearchAction: Boolean,
    onPrimaryClick: () -> Unit,
    onToggleMute: () -> Unit,
    onSearchClick: () -> Unit,
    onSecondaryEndClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isDirect) {
            ChatInfoSquircleButton(
                iconRes = R.drawable.ic_chat_bubble,
                label = stringResource(R.string.dm_composer_placeholder),
                onClick = onPrimaryClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            ChatInfoSquircleButton(
                iconRes = if (isAdmin) R.drawable.ic_plus else R.drawable.ic_user,
                label = stringResource(if (isAdmin) R.string.dm_info_add_label else R.string.dm_members),
                onClick = onPrimaryClick,
                modifier = Modifier.weight(1f)
            )
        }

        ChatInfoSquircleButton(
            iconRes = if (isMuted) R.drawable.ic_tg_unmute else R.drawable.ic_tg_mute,
            label = stringResource(if (isMuted) R.string.dm_info_unmute_label else R.string.dm_info_mute_label),
            isActive = isMuted,
            onClick = onToggleMute,
            modifier = Modifier.weight(1f)
        )

        ChatInfoSquircleButton(
            iconRes = R.drawable.ic_tg_search,
            label = stringResource(R.string.dm_info_search_label),
            enabled = hasSearchAction,
            onClick = onSearchClick,
            modifier = Modifier.weight(1f)
        )

        if (isDirect) {
            ChatInfoSquircleButton(
                iconRes = R.drawable.ic_more_vertical,
                label = if (isRtl) "المزيد" else "More",
                onClick = onSecondaryEndClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            ChatInfoSquircleButton(
                iconRes = R.drawable.ic_tg_leave,
                label = stringResource(R.string.dm_confirm_leave_action),
                isDestructive = true,
                onClick = onSecondaryEndClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChatInfoSquircleButton(
    iconRes: Int,
    label: String,
    isActive: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val containerBg by animateColorAsState(
        targetValue = when {
            isDestructive -> colors.destructiveRed.copy(alpha = 0.09f)
            isActive -> colors.primaryBlue.copy(alpha = 0.14f)
            else -> colors.surfaceMuted
        },
        animationSpec = tween(160),
        label = "squircle_bg"
    )
    val contentTint by animateColorAsState(
        targetValue = when {
            !enabled -> colors.secondaryText.copy(alpha = 0.45f)
            isDestructive -> colors.destructiveRed
            isActive -> colors.primaryBlue
            else -> colors.primaryText
        },
        animationSpec = tween(160),
        label = "squircle_tint"
    )
    val borderColor = when {
        isDestructive -> colors.destructiveRed.copy(alpha = 0.22f)
        isActive -> colors.primaryBlue.copy(alpha = 0.36f)
        else -> colors.subtleSurfaceBorder
    }

    val shape = RoundedCornerShape(ChatDimens.ChatInfoActionSquircleRadius)
    Column(
        modifier = modifier
            .height(60.dp)
            .clip(shape)
            .background(containerBg)
            .border(0.8.dp, borderColor, shape)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            color = contentTint,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = BuildXFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ChatInfoInlineSearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    messages: List<DmMessage>,
    isRtl: Boolean,
    onSelectMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = BuildXTheme.colors
    val matches = remember(query, messages) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            emptyList()
        } else {
            messages.asReversed().filter {
                it.deletedAt == null &&
                    it.messageType != "system" &&
                    it.content.contains(clean, ignoreCase = true)
            }.take(6)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceMuted)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBackground)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tg_search),
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dm_search_chat_hint),
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontFamily = BuildXFontFamily
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
                        textAlign = if (isRtlText(query, isRtl)) TextAlign.Right else TextAlign.Left,
                        textDirection = TextDirection.ContentOrLtr
                    ),
                    cursorBrush = SolidColor(colors.primaryBlue),
                    modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (query.trim().isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            if (matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.dm_no_search_results),
                    color = colors.secondaryText,
                    fontSize = 12.5.sp,
                    fontFamily = BuildXFontFamily,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            } else {
                matches.forEach { msg ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectMessage(msg.id) }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        val sender = msg.senderDisplayName?.takeIf { it.isNotBlank() }
                            ?: msg.senderHandle?.let { "@$it" }
                            ?: ""
                        if (sender.isNotBlank()) {
                            Text(
                                text = sender,
                                color = colors.primaryBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = BuildXFontFamily,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = msg.content,
                            color = colors.primaryText,
                            fontSize = 13.sp,
                            fontFamily = BuildXFontFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatInfoDetailsCard(
    isDirect: Boolean,
    isAdmin: Boolean,
    isPinned: Boolean,
    isRtl: Boolean,
    peer: DmPeerIntro?,
    peerFirstLabel: String,
    groupTitle: String,
    memberCount: Int,
    detail: DmConversationDetail?,
    activeWallpaper: ChatWallpaperConfig,
    onViewProfile: (String) -> Unit,
    onTogglePin: () -> Unit,
    onOpenWallpaper: (() -> Unit)?,
    onCreateGroupWithPeer: ((String) -> Unit)?,
    onChangeGroupPhoto: () -> Unit
) {
    val colors = BuildXTheme.colors
    val cardShape = RoundedCornerShape(16.dp)
    val customLabel = stringResource(R.string.dm_wallpaper_custom_label)
    val resolvedWallpaper = remember(detail, activeWallpaper) {
        if (!activeWallpaper.isDefault) {
            activeWallpaper
        } else {
            detail?.resolvedWallpaper ?: activeWallpaper
        }
    }

    val rows = mutableListOf<@Composable () -> Unit>()

    if (isDirect) {
        val handle = peer?.handle?.takeIf { it.isNotBlank() }
        if (handle != null) {
            rows.add {
                ChatInfoCardRow(
                    iconRes = R.drawable.ic_user,
                    title = "@$handle",
                    subtitle = stringResource(R.string.dm_view_profile),
                    onClick = { onViewProfile(handle) }
                )
            }
        }
        val bio = peer?.bio?.takeIf { it.isNotBlank() }
        if (bio != null) {
            rows.add {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (isRtl) "النبذة" else "Bio",
                        color = colors.secondaryText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = BuildXFontFamily
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = bio,
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontFamily = BuildXFontFamily
                    )
                }
            }
        }
        val peerId = peer?.id
        if (!peerId.isNullOrBlank() && onCreateGroupWithPeer != null) {
            rows.add {
                ChatInfoCardRow(
                    iconRes = R.drawable.ic_plus,
                    title = stringResource(R.string.dm_create_group_with, peerFirstLabel),
                    onClick = { onCreateGroupWithPeer(peerId) }
                )
            }
        }
    } else {
        rows.add {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isRtl) "معلومات المجموعة" else "Group settings",
                    color = colors.secondaryText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BuildXFontFamily
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$groupTitle · ${stringResource(R.string.dm_members_count, memberCount)}",
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isAdmin) {
            rows.add {
                ChatInfoCardRow(
                    iconRes = R.drawable.ic_camera,
                    title = stringResource(R.string.dm_change_group_photo),
                    onClick = onChangeGroupPhoto
                )
            }
        }
    }

    if (onOpenWallpaper != null) {
        rows.add {
            ChatInfoCardRow(
                iconRes = R.drawable.ic_wallpaper,
                title = stringResource(R.string.dm_wallpaper),
                subtitle = resolvedWallpaper.displayTitle(customLabel),
                onClick = onOpenWallpaper
            )
        }
    }

    rows.add {
        ChatInfoCardRow(
            iconRes = R.drawable.ic_tg_pin,
            title = stringResource(if (isPinned) R.string.dm_unpin_chat else R.string.dm_pin_chat),
            subtitle = if (isPinned) {
                if (isRtl) "مثبتة في أعلى المحادثات" else "Pinned to top of inbox"
            } else null,
            onClick = onTogglePin
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(cardShape)
            .background(colors.surfaceMuted)
            .border(0.8.dp, colors.subtleSurfaceBorder, cardShape)
    ) {
        rows.forEachIndexed { index, rowContent ->
            rowContent()
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    color = colors.hairlineBorder,
                    thickness = 0.6.dp,
                    modifier = Modifier.padding(start = 48.dp, end = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatInfoCardRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colors.primaryBlue,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = BuildXFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.secondaryText.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun ChatInfoStickyTabBar(
    tabs: List<ChatInfoTab>,
    selectedTab: ChatInfoTab,
    isRtl: Boolean,
    pageBackground: Color,
    onSelectTab: (ChatInfoTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(pageBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val label = when (tab) {
                    ChatInfoTab.MEMBERS -> stringResource(R.string.dm_members)
                    ChatInfoTab.MEDIA -> stringResource(R.string.media_label)
                    ChatInfoTab.FILES -> stringResource(R.string.files_label)
                    ChatInfoTab.LINKS -> if (isRtl) "الروابط" else "Links"
                }
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primaryBlue else colors.secondaryText,
                    animationSpec = tween(160),
                    label = "tab_text_color"
                )
                Column(
                    modifier = Modifier
                        .widthIn(min = 82.dp)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontFamily = BuildXFontFamily,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 11.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (isSelected) colors.primaryBlue else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = colors.hairlineBorder, thickness = 0.7.dp)
    }
}

@Composable
internal fun ChatInfoTabContent(
    selectedTab: ChatInfoTab,
    isDirect: Boolean,
    isAdmin: Boolean,
    isRtl: Boolean,
    currentUserId: String,
    createdBy: String?,
    activeParticipants: List<DmParticipant>,
    isAddingMembersOpen: Boolean,
    memberQuery: String,
    memberCandidates: List<PersonSearchResult>,
    isSearchingMembers: Boolean,
    isAddingGroupMembers: Boolean,
    selectedMemberIds: Set<String>,
    sharedMedia: List<DmMessage>,
    sharedFiles: List<DmMessage>,
    sharedLinks: List<ChatSharedLink>,
    onOpenAddMembers: () -> Unit,
    onMemberQueryChange: (String) -> Unit,
    onToggleMemberSelection: (String) -> Unit,
    onCancelAddMembers: () -> Unit,
    onConfirmAddMembers: () -> Unit,
    onViewProfile: (String) -> Unit,
    onRequestRemoveMember: ((DmParticipant) -> Unit)?,
    onOpenMedia: (DmMessage) -> Unit,
    onJumpToMessage: ((String) -> Unit)?
) {
    val colors = BuildXTheme.colors
    val uriHandler = LocalUriHandler.current

    when (selectedTab) {
        ChatInfoTab.MEMBERS -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (!isDirect && isAdmin && !isAddingMembersOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenAddMembers)
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.primaryBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_plus),
                                contentDescription = stringResource(R.string.dm_add_members),
                                tint = colors.primaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.dm_add_members),
                            color = colors.primaryBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BuildXFontFamily
                        )
                    }
                    HorizontalDivider(
                        color = colors.hairlineBorder,
                        thickness = 0.6.dp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 4.dp)
                    )
                }

                AnimatedVisibility(visible = isAddingMembersOpen && isAdmin) {
                    ChatInfoAddMembersPicker(
                        query = memberQuery,
                        onQueryChange = onMemberQueryChange,
                        candidates = memberCandidates.filterNot { candidate ->
                            activeParticipants.any { it.userId == candidate.id } || candidate.id == currentUserId
                        },
                        selectedIds = selectedMemberIds,
                        isSearching = isSearchingMembers,
                        isSubmitting = isAddingGroupMembers,
                        isRtl = isRtl,
                        onToggle = onToggleMemberSelection,
                        onCancel = onCancelAddMembers,
                        onConfirm = onConfirmAddMembers
                    )
                }

                if (activeParticipants.isEmpty()) {
                    ChatInfoTabEmptyState(
                        iconRes = R.drawable.ic_user,
                        message = if (isRtl) "لا يوجد أعضاء بعد" else "No members yet"
                    )
                } else {
                    activeParticipants.forEach { participant ->
                        ChatInfoMemberRow(
                            participant = participant,
                            currentUserId = currentUserId,
                            createdBy = createdBy,
                            isViewerAdmin = isAdmin,
                            isRtl = isRtl,
                            onViewProfile = onViewProfile,
                            onRequestRemove = onRequestRemoveMember?.let { remove ->
                                { remove(participant) }
                            }
                        )
                    }
                }
            }
        }

        ChatInfoTab.MEDIA -> {
            if (sharedMedia.isEmpty()) {
                ChatInfoTabEmptyState(
                    iconRes = R.drawable.ic_image,
                    message = stringResource(R.string.dm_no_shared_media)
                )
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
                                        .clickable { onOpenMedia(mediaMsg) }
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

        ChatInfoTab.FILES -> {
            if (sharedFiles.isEmpty()) {
                ChatInfoTabEmptyState(
                    iconRes = R.drawable.ic_tg_attach,
                    message = if (isRtl) "لا توجد ملفات مشتركة بعد" else "No shared files yet"
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    sharedFiles.forEachIndexed { index, fileMsg ->
                        val fileName = fileMsg.content.takeIf { it.isNotBlank() }
                            ?: fileMsg.attachmentPath?.substringAfterLast('/')
                            ?: (if (isRtl) "ملف" else "File")
                        val fileMeta = buildString {
                            fileMsg.attachmentMime?.let { append(it.substringAfter('/').uppercase()) }
                            fileMsg.attachmentSize?.let { bytes ->
                                if (isNotEmpty()) append(" · ")
                                append(formatReadableFileSize(bytes))
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (onJumpToMessage != null) {
                                        onJumpToMessage(fileMsg.id)
                                    } else {
                                        onOpenMedia(fileMsg)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.primaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tg_attach),
                                    contentDescription = null,
                                    tint = colors.primaryBlue,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileName,
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = BuildXFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (fileMeta.isNotBlank()) {
                                    Text(
                                        text = fileMeta,
                                        color = colors.secondaryText,
                                        fontSize = 12.sp,
                                        fontFamily = BuildXFontFamily,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (index < sharedFiles.lastIndex) {
                            HorizontalDivider(
                                color = colors.hairlineBorder,
                                thickness = 0.6.dp,
                                modifier = Modifier.padding(start = 58.dp)
                            )
                        }
                    }
                }
            }
        }

        ChatInfoTab.LINKS -> {
            if (sharedLinks.isEmpty()) {
                ChatInfoTabEmptyState(
                    iconRes = R.drawable.ic_link,
                    message = if (isRtl) "لا توجد روابط مشتركة بعد" else "No shared links yet"
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    sharedLinks.forEachIndexed { index, link ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    runCatching { uriHandler.openUri(link.url) }
                                        .onFailure { onJumpToMessage?.invoke(link.messageId) }
                                }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceMuted)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_link),
                                    contentDescription = null,
                                    tint = colors.primaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = link.url,
                                    color = colors.primaryBlue,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = BuildXFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!link.senderName.isNullOrBlank()) {
                                    Text(
                                        text = link.senderName,
                                        color = colors.secondaryText,
                                        fontSize = 12.sp,
                                        fontFamily = BuildXFontFamily,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (index < sharedLinks.lastIndex) {
                            HorizontalDivider(
                                color = colors.hairlineBorder,
                                thickness = 0.6.dp,
                                modifier = Modifier.padding(start = 58.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInfoTabEmptyState(
    iconRes: Int,
    message: String
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 44.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = colors.secondaryText,
            fontSize = 13.5.sp,
            fontFamily = BuildXFontFamily,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ChatInfoAddMembersPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    candidates: List<PersonSearchResult>,
    selectedIds: Set<String>,
    isSearching: Boolean,
    isSubmitting: Boolean,
    isRtl: Boolean,
    onToggle: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceMuted)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tg_search),
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
                        fontSize = 13.5.sp,
                        fontFamily = BuildXFontFamily
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
                        textAlign = if (isRtlText(query, isRtl)) TextAlign.Right else TextAlign.Left,
                        textDirection = TextDirection.ContentOrLtr
                    ),
                    cursorBrush = SolidColor(colors.primaryBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
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
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dm_no_search_results_title),
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    fontFamily = BuildXFontFamily
                )
            }
        } else {
            Column(modifier = Modifier.heightIn(max = 200.dp)) {
                candidates.take(6).forEach { person ->
                    val isPicked = person.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggle(person.id) }
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isPicked) colors.primaryBlue else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isPicked) colors.primaryBlue else colors.subtleSurfaceBorder,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPicked) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = person.displayName.takeIf { it.isNotBlank() } ?: "@${person.username}",
                            color = colors.primaryText,
                            fontSize = 13.5.sp,
                            fontFamily = BuildXFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
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
                fontFamily = BuildXFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selectedIds.isNotEmpty() && !isSubmitting) {
                            colors.primaryBlue
                        } else {
                            colors.cardBackground
                        }
                    )
                    .clickable(enabled = selectedIds.isNotEmpty() && !isSubmitting, onClick = onConfirm)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                if (isSubmitting) {
                    MarkLoader(size = 14.dp, isSpinning = true, tint = Color.White)
                } else {
                    Text(
                        text = stringResource(R.string.dm_save),
                        color = if (selectedIds.isNotEmpty()) Color.White else colors.secondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BuildXFontFamily
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatInfoMemberRow(
    participant: DmParticipant,
    currentUserId: String,
    createdBy: String?,
    isViewerAdmin: Boolean,
    isRtl: Boolean,
    onViewProfile: (String) -> Unit,
    onRequestRemove: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val isOwner = participant.role == "owner" || (createdBy != null && createdBy == participant.userId)
    val isAdminRole = participant.role == "admin"

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
                    fontWeight = FontWeight.Bold,
                    fontFamily = BuildXFontFamily
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
                    fontFamily = BuildXFontFamily,
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
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isOwner || isAdminRole) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.primaryBlue.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isOwner) {
                        if (isRtl) "المالك" else "Owner"
                    } else {
                        stringResource(R.string.dm_admin)
                    },
                    color = colors.primaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BuildXFontFamily
                )
            }
        } else if (isViewerAdmin && participant.userId != currentUserId && onRequestRemove != null) {
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
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BuildXFontFamily
                )
            }
        }
    }
}

@Composable
internal fun ChatInfoRenameGroupDialog(
    titleDraft: String,
    isRtl: Boolean,
    onTitleDraftChange: (String) -> Unit,
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
                    blurRadius = 28.dp,
                    offsetY = 10.dp
                )
                .clip(RoundedCornerShape(22.dp))
                .background(colors.cardBackground)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.dm_info_edit_group_name),
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BuildXFontFamily
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceMuted)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (titleDraft.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dm_group_name_hint),
                        color = colors.secondaryText,
                        fontSize = 15.sp,
                        fontFamily = BuildXFontFamily
                    )
                }
                BasicTextField(
                    value = titleDraft,
                    onValueChange = onTitleDraftChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = BuildXFontFamily,
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = if (isRtlText(titleDraft, isRtl)) TextAlign.Right else TextAlign.Left,
                        textDirection = TextDirection.ContentOrLtr
                    ),
                    cursorBrush = SolidColor(colors.primaryBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .pressScaleClickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dm_cancel),
                        color = colors.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BuildXFontFamily
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (titleDraft.isNotBlank()) colors.primaryBlue else colors.surfaceMuted
                        )
                        .pressScaleClickable(enabled = titleDraft.isNotBlank(), onClick = onConfirm)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dm_save),
                        color = if (titleDraft.isNotBlank()) Color.White else colors.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BuildXFontFamily
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatInfoConfirmDialog(
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
                fontWeight = FontWeight.Bold,
                fontFamily = BuildXFontFamily
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                color = colors.secondaryText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = BuildXFontFamily
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
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BuildXFontFamily
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
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BuildXFontFamily
                )
            }
        }
    }
}

private fun formatReadableFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
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
