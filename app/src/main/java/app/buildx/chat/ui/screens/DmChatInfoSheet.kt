package app.buildx.chat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.buildx.chat.R
import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.ui.chat.ChatDimens
import app.buildx.chat.ui.chat.ChatInfoConfirmDialog
import app.buildx.chat.ui.chat.ChatInfoDetailsCard
import app.buildx.chat.ui.chat.ChatInfoFourSquircleRow
import app.buildx.chat.ui.chat.ChatInfoInlineSearchCard
import app.buildx.chat.ui.chat.ChatInfoOverflowPopup
import app.buildx.chat.ui.chat.ChatInfoRenameGroupDialog
import app.buildx.chat.ui.chat.ChatInfoStickyTabBar
import app.buildx.chat.ui.chat.ChatInfoTab
import app.buildx.chat.ui.chat.ChatInfoTabContent
import app.buildx.chat.ui.chat.ChatMessageMapper
import app.buildx.chat.ui.chat.DmMediaViewerDialog
import app.buildx.chat.ui.chat.buildOverflowMenuItems
import app.buildx.chat.ui.chat.toChatColors
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Unified Telegram-grade Chat Info sheet.
 *
 * Mirrors [app.buildx.chat.ui.chat.ChatInfoScreen] inside a status-bar-safe modal bottom sheet:
 * - Pinned `56.dp` top bar (`zIndex(10f)`) with Close/Back `<`, Edit `✎`, and More `⋮`.
 * - Smooth `88.dp -> 36.dp` GPU-accelerated collapsing header.
 * - 4 equal-width Telegram squircle actions (`Add`/`Message`, `Mute`, `Search`, `Leave`/`More`).
 * - Clean `16.dp` rounded info card + sticky `Members | Media | Files | Links` tab bar.
 */
@Composable
fun DmChatInfoSheet(
    detail: DmConversationDetail,
    isDirect: Boolean,
    currentUserId: String,
    messages: List<DmMessage>,
    isGroupAvatarUploading: Boolean,
    isAddingGroupMembers: Boolean,
    memberCandidates: List<PersonSearchResult>,
    isSearchingMembers: Boolean,
    isSubmittingReport: Boolean,
    reportError: String?,
    isBlocking: Boolean,
    onSearchMembers: (String) -> Unit,
    onViewProfile: (String) -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onUpdateGroupAvatar: (Uri) -> Unit,
    onRenameGroup: (String) -> Unit,
    onAddMembers: (List<String>) -> Unit,
    onLeaveGroup: () -> Unit,
    onClearHistory: () -> Unit,
    onReport: (ReportReason, String?) -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
    onOpenWallpaper: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val chatColors = remember(colors) { colors.toChatColors() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scrollState = rememberScrollState()

    val conversation = detail.conversation
    val peer = detail.peerIntro
    val myParticipant = detail.participants.firstOrNull { it.userId == currentUserId }
    val isAdmin = myParticipant?.role == "admin" ||
        myParticipant?.role == "owner" ||
        (!isDirect && conversation?.createdBy != null && conversation.createdBy == currentUserId) ||
        (!isDirect && myParticipant == null && detail.participants.isEmpty())
    val isMuted = myParticipant?.isMuted == true
    val isPinned = myParticipant?.isPinned == true
    val activeParticipants = remember(detail.participants) {
        detail.participants.filter { it.status == "active" }
    }

    val sharedMedia = remember(messages) { ChatMessageMapper.extractSharedMedia(messages) }
    val sharedFiles = remember(messages) { ChatMessageMapper.extractSharedFiles(messages) }
    val sharedLinks = remember(messages) { ChatMessageMapper.extractSharedLinks(messages) }

    var selectedTab by remember(isDirect) {
        mutableStateOf(if (isDirect) ChatInfoTab.MEDIA else ChatInfoTab.MEMBERS)
    }
    var viewerMediaMessage by remember { mutableStateOf<DmMessage?>(null) }
    var isRenaming by remember { mutableStateOf(false) }
    var titleDraft by remember(conversation?.id, conversation?.title) {
        mutableStateOf(conversation?.title ?: "")
    }
    var isAddingMembersOpen by remember { mutableStateOf(false) }
    var memberQuery by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf(emptySet<String>()) }
    var isInlineSearchOpen by remember { mutableStateOf(false) }
    var inlineSearchQuery by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var showReport by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

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
            ?: peer?.handle?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.dm_chat_info)
    } else {
        conversation?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.dm_new_group)
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
        onViewProfile = { handle ->
            onDismiss()
            onViewProfile(handle)
        },
        onTogglePin = onTogglePin,
        onOpenWallpaper = onOpenWallpaper?.let { open ->
            {
                onDismiss()
                open()
            }
        },
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
        onReport = { showReport = true },
        onBlock = { showBlockConfirm = true },
        onLeaveGroup = { showLeaveConfirm = true }
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
                .statusBarsPadding()
                .padding(top = 8.dp)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(colors.cardBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
                    .align(Alignment.CenterHorizontally)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val viewportWidthPx = with(density) { maxWidth.toPx() }
                var tabBarTopInColumnPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }

                // Scrollable Body (zIndex 1f), strictly clipped inside sheet below drag handle
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .clipToBounds()
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(topBarHeightDp + 174.dp))

                    ChatInfoFourSquircleRow(
                        isDirect = isDirect,
                        isAdmin = isAdmin,
                        isMuted = isMuted,
                        isRtl = strings.isRtl,
                        hasSearchAction = messages.any { it.deletedAt == null && it.content.isNotBlank() },
                        onPrimaryClick = {
                            if (isDirect) {
                                onDismiss()
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
                            isInlineSearchOpen = !isInlineSearchOpen
                            if (!isInlineSearchOpen) inlineSearchQuery = ""
                        },
                        onSecondaryEndClick = {
                            if (isDirect) {
                                showOverflowMenu = true
                            } else {
                                showLeaveConfirm = true
                            }
                        }
                    )

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
                            onSelectMessage = {
                                isInlineSearchOpen = false
                                inlineSearchQuery = ""
                            },
                            onClose = {
                                isInlineSearchOpen = false
                                inlineSearchQuery = ""
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                        activeWallpaper = ChatWallpaperConfig(),
                        onViewProfile = { handle ->
                            onDismiss()
                            onViewProfile(handle)
                        },
                        onTogglePin = onTogglePin,
                        onOpenWallpaper = onOpenWallpaper?.let { open ->
                            {
                                onDismiss()
                                open()
                            }
                        },
                        onCreateGroupWithPeer = null,
                        onChangeGroupPhoto = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

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
                            pageBackground = colors.cardBackground,
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
                        onViewProfile = { handle ->
                            onDismiss()
                            onViewProfile(handle)
                        },
                        onRequestRemoveMember = null,
                        onOpenMedia = { viewerMediaMessage = it },
                        onJumpToMessage = null
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Pinned 56.dp Sheet Top Bar (zIndex 10f)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(10f)
                        .height(topBarHeightDp)
                        .drawBehind {
                            val f = (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
                            drawRect(color = colors.cardBackground)
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
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderCircleButton(
                            iconRes = R.drawable.ic_close,
                            contentDescription = stringResource(R.string.dm_cancel),
                            onClick = onDismiss,
                            backgroundColor = colors.surfaceMuted,
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
                                backgroundColor = colors.surfaceMuted,
                                iconTint = colors.primaryText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box {
                            HeaderCircleButton(
                                iconRes = R.drawable.ic_more_vertical,
                                contentDescription = if (strings.isRtl) "المزيد" else "More",
                                onClick = { showOverflowMenu = true },
                                backgroundColor = colors.surfaceMuted,
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

                // Collapsing Hero Avatar + Title + Subtitle (zIndex 11f)
                val expandedCenterX = viewportWidthPx / 2f
                val expandedCenterY = topBarHeightPx + expandedTopGapPx + (avatarExpandedPx / 2f)
                val collapsedCenterX = if (isRtl) {
                    viewportWidthPx - (backButtonEndPx + avatarCollapsedPx / 2f)
                } else {
                    backButtonEndPx + (avatarCollapsedPx / 2f)
                }
                val collapsedCenterY = topBarHeightPx / 2f
                val targetScale = avatarCollapsedPx / avatarExpandedPx

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(11f)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (topBarHeightPx + expandedTopGapPx).roundToInt()
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
                                        peer?.handle?.let { h ->
                                            onDismiss()
                                            onViewProfile(h)
                                        }
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
                                    size = 24.dp,
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

                // Expanded Centered Hero Title + Subtitle
                val heroTextExpandedTopPx = topBarHeightPx + expandedTopGapPx + avatarExpandedPx + titleTopGapPx
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

                // Collapsed Top-Bar Title + Subtitle
                val collapsedTitleStartDp = with(density) {
                    (backButtonEndPx + avatarCollapsedPx + collapsedAvatarToTitleGapPx).toDp()
                }
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .zIndex(11f)
                        .fillMaxWidth()
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
        }
    }

    viewerMediaMessage?.let { mediaMessage ->
        DmMediaViewerDialog(
            message = mediaMessage,
            onDismiss = { viewerMediaMessage = null },
            onShowMessage = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
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

    if (showReport) {
        ReportAccountSheet(
            username = peer?.username ?: peer?.handle ?: "",
            isSubmitting = isSubmittingReport,
            errorMessage = reportError,
            onSubmit = { reason, details -> onReport(reason, details) },
            onBlock = {
                showReport = false
                showBlockConfirm = true
            },
            onDismiss = { showReport = false }
        )
    }

    if (showBlockConfirm) {
        BlockAccountDialog(
            username = peer?.username ?: peer?.handle ?: "",
            isBlocking = isBlocking,
            onConfirm = {
                onBlock()
                showBlockConfirm = false
                onDismiss()
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
}
