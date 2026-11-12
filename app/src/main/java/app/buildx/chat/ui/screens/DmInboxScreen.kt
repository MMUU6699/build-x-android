package app.buildx.chat.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.buildx.chat.R
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmMemberAvatar
import app.buildx.chat.data.model.DmPeerSummary
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.ui.components.BubblePopupPositionProvider
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState
import app.buildx.chat.ui.viewmodel.DmFilterTab
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
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
    onCreateGroupClick: () -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onToggleMuteConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSelectFilterTab: (DmFilterTab) -> Unit = {},
    onToggleReadUnreadConversation: (String) -> Unit = {},
    onDeleteOrLeaveConversation: (String) -> Unit = {},
    onOpenDirectChatWithUser: (String) -> Unit = {},
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onSetConversationPin: (String, Boolean) -> Unit = { id, pin -> onTogglePinConversation(id) },
    onSetConversationMute: (String, String?) -> Unit = { id, _ -> onToggleMuteConversation(id) },
    onDeleteConversationForMe: (String) -> Unit = { id -> onDeleteOrLeaveConversation(id) },
    onLeaveGroup: (String) -> Unit = { id -> onDeleteOrLeaveConversation(id) },
    onLoadGroupDetail: (String) -> Unit = {},
    onUpdateGroupAvatar: (String, android.net.Uri) -> Unit = { _, _ -> },
    onAddGroupMembers: (String, List<String>) -> Unit = { _, _ -> },
    onSearchGroupMembers: (String) -> Unit = {},
    currentUserId: String? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var activePopupItem by remember { mutableStateOf<DmInboxItem?>(null) }
    var activeRowBounds by remember { mutableStateOf<IntRect?>(null) }
    var selectedGroupInfoItem by remember { mutableStateOf<DmInboxItem?>(null) }
    var selectedReplyNote by remember { mutableStateOf<FeedNoteItem?>(null) }

    // Text selection styling
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
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
                        modifier = Modifier.weight(1f)
                    )

                    HeaderCircleButton(
                        iconRes = R.drawable.ic_edit_compose,
                        contentDescription = stringResource(R.string.dm_compose_message),
                        onClick = onCreateGroupClick
                    )
                }

                // 2. Search Field (48dp pill on #F1F3F0, 16dp horizontal insets, 8dp below header)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val isRtl = isRtlText(uiState.dmSearchQuery, strings.isRtl)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            if (uiState.dmSearchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.dm_search_hint),
                                    color = colors.secondaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            BasicTextField(
                                value = uiState.dmSearchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.dmSearchQuery.isNotEmpty()) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Clear",
                                tint = colors.secondaryText,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onSearchQueryChange("") }
                            )
                        }
                    }
                }

                val isSearching = uiState.dmSearchQuery.trim().isNotEmpty()

                // 3. Notes Strip (Hidden during search query)
                if (!isSearching) {
                    val ownNote = uiState.inboxNotesTray.firstOrNull { it.isOwn || it.userId == currentUserId }
                        ?: uiState.activeOwnNote?.let { pn ->
                            FeedNoteItem(
                                id = pn.id,
                                userId = pn.userId,
                                content = pn.content,
                                createdAt = pn.createdAt,
                                expiresAt = pn.expiresAt,
                                isOwn = true,
                                author = DmPeerSummary(
                                    id = pn.userId,
                                    displayName = uiState.profile?.displayName ?: "You",
                                    handle = uiState.profile?.handle,
                                    avatarUrl = uiState.profile?.avatarUrl,
                                    isVerified = uiState.profile?.isVerified ?: false
                                )
                            )
                        }
                    val friendsNotes = uiState.inboxNotesTray.filterNot { it.isOwn || it.userId == currentUserId }

                    DmNotesStrip(
                        ownNote = ownNote,
                        friendsNotes = friendsNotes,
                        currentUserId = currentUserId,
                        onOpenNoteSheet = onOpenNoteSheet,
                        onNoteClick = { note -> selectedReplyNote = note }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Filter Chips (All, Unread with badge, Groups — No Requests)
                    val unreadCount = uiState.dmInboxItems.count { it.unreadCount > 0 || it.isMarkedUnread }
                    val groupsCount = uiState.dmInboxItems.count { it.kind == "group" }

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
                            onClick = { onSelectFilterTab(DmFilterTab.ALL) }
                        )

                        DmFilterChip(
                            label = stringResource(R.string.dm_filter_unread),
                            count = unreadCount,
                            isSelected = uiState.selectedDmFilterTab == DmFilterTab.UNREAD,
                            onClick = { onSelectFilterTab(DmFilterTab.UNREAD) }
                        )

                        DmFilterChip(
                            label = stringResource(R.string.dm_filter_groups),
                            count = groupsCount,
                            isSelected = uiState.selectedDmFilterTab == DmFilterTab.GROUPS,
                            onClick = { onSelectFilterTab(DmFilterTab.GROUPS) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 5. Main Conversation List & Contextual States
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isSearching) {
                        // Search Results List
                        DmSearchResultsContent(
                            query = uiState.dmSearchQuery.trim(),
                            conversations = uiState.dmInboxItems,
                            people = uiState.dmSearchPeopleResults,
                            isLoadingPeople = uiState.isDmSearchPeopleLoading,
                            onConversationClick = onConversationClick,
                            onPersonClick = onOpenProfile
                        )
                    } else if (uiState.isDmInboxLoading && uiState.dmInboxItems.isEmpty()) {
                        // Loading Skeletons
                        DmInboxSkeletonList()
                    } else {
                        // Filtered Conversation List
                        val filteredList = when (uiState.selectedDmFilterTab) {
                            DmFilterTab.ALL -> uiState.dmInboxItems
                            DmFilterTab.UNREAD -> uiState.dmInboxItems.filter { it.unreadCount > 0 || it.isMarkedUnread }
                            DmFilterTab.GROUPS -> uiState.dmInboxItems.filter { it.kind == "group" }
                            else -> uiState.dmInboxItems
                        }

                        if (filteredList.isEmpty()) {
                            DmContextualEmptyState(
                                tab = uiState.selectedDmFilterTab,
                                onCreateGroupClick = onCreateGroupClick,
                                onShowAllChats = { onSelectFilterTab(DmFilterTab.ALL) }
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(filteredList, key = { it.effectiveId }) { item ->
                                    var itemCoordinates by remember { mutableStateOf<IntRect?>(null) }

                                    DmConversationRow(
                                        item = item,
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
                                        color = colors.subtleSurfaceBorder,
                                        thickness = 0.6.dp,
                                        modifier = Modifier.padding(start = 84.dp, end = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Long-Press Row Lift & Anchored Popup Menu Overlay
            activePopupItem?.let { item ->
                val density = LocalDensity.current

                // 30% Dimming Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.30f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                activePopupItem = null
                                activeRowBounds = null
                            }
                        )
                )

                // Lifted Row Overlay
                activeRowBounds?.let { bounds ->
                    val topDp = with(density) { bounds.top.toDp() }
                    Box(
                        modifier = Modifier
                            .offset(y = topDp)
                            .fillMaxWidth()
                            .scale(1.02f)
                            .softShadow(colors.cardBackground.copy(alpha = 0.35f), cornerRadius = 16.dp, blurRadius = 18.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.pageBackground)
                    ) {
                        DmConversationRow(
                            item = item,
                            onClick = {},
                            onLongClick = {}
                        )
                    }

                    // Anchored Popup Attached to Row
                    Popup(
                        popupPositionProvider = BubblePopupPositionProvider(isRtl = strings.isRtl),
                        onDismissRequest = {
                            activePopupItem = null
                            activeRowBounds = null
                        }
                    ) {
                        DmConversationPopupCard(
                            item = item,
                            onPinToggle = { onSetConversationPin(item.effectiveId, !item.isPinned) },
                            onMuteSelect = { duration -> onSetConversationMute(item.effectiveId, duration) },
                            onReadUnreadToggle = { onToggleReadUnreadConversation(item.effectiveId) },
                            onViewProfile = {
                                val handle = item.handle
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

            // 8. Friend Note Reply Sheet
            selectedReplyNote?.let { note ->
                DmNoteReplySheet(
                    note = note,
                    onViewProfile = {
                        val handle = note.author.handle
                        if (!handle.isNullOrBlank()) {
                            selectedReplyNote = null
                            onOpenProfile(handle)
                        }
                    },
                    onSendReply = { replyText ->
                        selectedReplyNote = null
                        onReplyToNote(note, replyText)
                    },
                    onDismiss = { selectedReplyNote = null }
                )
            }
        }
    }
}

/**
 * Filter Chip (36dp height, full radius, 14dp padding, 8dp gap)
 * Selected: #F1F3F0 fill, bold primary text, no border.
 * Unselected: Transparent, 1dp hairline border, medium gray text.
 */
@Composable
private fun DmFilterChip(
    label: String,
    isSelected: Boolean,
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
            .clickable(onClick = onClick)
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

            if (count != null && count > 0) {
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
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        color = if (isSelected) Color.White else colors.primaryText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
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
    friendsNotes: List<FeedNoteItem>,
    currentUserId: String?,
    onOpenNoteSheet: () -> Unit,
    onNoteClick: (FeedNoteItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "own_note_slot") {
            NoteStripCell(
                avatarUrl = ownNote?.author?.avatarUrl,
                label = stringResource(R.string.dm_your_note),
                bubbleText = ownNote?.content?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.dm_share_a_note),
                showAddBadge = true,
                isVerified = false,
                onClick = onOpenNoteSheet
            )
        }

        items(friendsNotes, key = { it.id }) { note ->
            NoteStripCell(
                avatarUrl = note.author.avatarUrl,
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
 * A single 88x124dp note cell: 68dp avatar 22dp above the baseline, the thought
 * bubble floating over its top rim, and an always-on "+" affordance for the
 * viewer's own note.
 */
@Composable
private fun NoteStripCell(
    avatarUrl: String?,
    label: String,
    bubbleText: String,
    showAddBadge: Boolean,
    isVerified: Boolean,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors

    Box(
        modifier = Modifier
            .width(88.dp)
            .height(124.dp)
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
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
        }

        ThoughtBubbleCard(
            content = bubbleText,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (showAddBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 20.dp)
                    .size(24.dp)
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
                text = label,
                color = colors.secondaryText,
                fontSize = 11.5.sp,
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
        modifier = modifier.zIndex(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 48.dp, max = 92.dp)
                .softShadow(
                    color = colors.headerHaloShadowColor,
                    cornerRadius = 16.dp,
                    blurRadius = 12.dp,
                    offsetY = 2.5.dp
                )
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBackground)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = content,
                color = colors.primaryText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 14.5.sp
            )
        }

        // Two visible tail dots bridging the bubble into the avatar rim.
        Spacer(modifier = Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .size(6.5.dp)
                .clip(CircleShape)
                .background(colors.cardBackground)
                .border(0.6.dp, colors.subtleSurfaceBorder, CircleShape)
        )
        Spacer(modifier = Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(colors.cardBackground)
        )
    }
}

/**
 * Bottom sheet shown when a friend's note is tapped: author identity, a
 * "View profile" pill, the note itself, and a reply composer that starts a
 * direct conversation through [onSendReply].
 */
@Composable
private fun DmNoteReplySheet(
    note: FeedNoteItem,
    onViewProfile: () -> Unit,
    onSendReply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    var replyText by remember(note.id) { mutableStateOf("") }
    val canSend = replyText.trim().isNotEmpty()
    val authorName = note.author.displayName ?: note.author.handle ?: ""

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
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(colors.cardBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                ) {
                    if (!note.author.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = note.author.avatarUrl,
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
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = authorName,
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (note.author.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 14.dp)
                        }
                    }
                    if (!note.author.handle.isNullOrBlank()) {
                        Text(
                            text = "@${note.author.handle}",
                            color = colors.secondaryText,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceMuted)
                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(999.dp))
                        .pressScaleClickable(onClick = onViewProfile)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dm_view_profile),
                        color = colors.primaryText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp, max = 320.dp)
                    .softShadow(
                        color = colors.headerHaloShadowColor,
                        cornerRadius = 18.dp,
                        blurRadius = 12.dp,
                        offsetY = 2.5.dp
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceMuted)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = note.content,
                    color = colors.primaryText,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.surfaceMuted)
                    .border(1.dp, colors.subtleSurfaceBorder, RoundedCornerShape(26.dp))
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dm_reply_to_note, authorName),
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
                            textAlign = if (isRtlText(replyText, strings.isRtl)) TextAlign.Right else TextAlign.Left
                        ),
                        cursorBrush = SolidColor(colors.primaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
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

/**
 * Conversation Row (72dp min, 16dp horizontal, 8dp vertical, press feedback = soft highlight without ripple)
 */
@Composable
private fun DmConversationRow(
    item: DmInboxItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightQuery: String = ""
) {
    val colors = BuildXTheme.colors
    val isUnread = item.unreadCount > 0 || item.isMarkedUnread

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .pointerInput(Unit) {
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
            // 56dp Avatar Container (Single or Composite 2-4 members, never showing member count)
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
                // Line 1: Name + inline VerifiedBadge | Localized Timestamp (blue when unread)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val titleText = "\u2068${item.effectiveTitle}\u2069"
                        Text(
                            text = titleText,
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
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

                    // Localized short timestamp
                    val timeText = formatLocalizedTime(item.lastMessageAt ?: item.lastActivityAt)
                    Text(
                        text = timeText,
                        color = if (isUnread) colors.primaryBlue else colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Line 2: Sender Prefix + Preview Text | Trailing Status (pin, mute, unread badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview segment
                    val previewString = buildConversationPreview(item)
                    Text(
                        text = previewString,
                        color = if (isUnread) colors.primaryText else colors.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Trailing indicators (pin 14dp, bell-off 14dp, unread badge pill)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isPinned) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pin),
                                contentDescription = "Pinned",
                                tint = colors.secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (item.isMuted) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bell_off),
                                contentDescription = "Muted",
                                tint = colors.secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (isUnread) {
                            val badgeCount = item.unreadCount
                            val countText = if (badgeCount > 99) "99+" else if (badgeCount > 0) badgeCount.toString() else "•"
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .defaultMinSize(minWidth = 20.dp)
                                    .clip(CircleShape)
                                    .background(if (item.isMuted) colors.secondaryText else colors.primaryBlue)
                                    .padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
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
    val photo = item.avatarUrl

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
    val fallbackInitial = item.effectiveTitle.firstOrNull()?.uppercase() ?: "G"

    Box(modifier = modifier) {
        GroupAvatarCircle(
            member = members.getOrNull(0),
            fallbackInitial = fallbackInitial,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(38.dp)
                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
        )
        GroupAvatarCircle(
            member = members.getOrNull(1),
            fallbackInitial = fallbackInitial,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(40.dp)
                .border(2.5.dp, colors.pageBackground, CircleShape)
        )
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
 * Builds preview string with Bidi isolation, prefixes ("You: " or "Ali: "), and types.
 */
@Composable
private fun buildConversationPreview(item: DmInboxItem): String {
    val isGroup = item.kind == "group"
    val lastText = item.lastMessageText

    if (lastText == null && item.lastMessageAt == null) {
        return stringResource(R.string.dm_no_messages_yet)
    }

    val prefix = when {
        item.senderFirstName == "You" -> stringResource(R.string.dm_you_prefix)
        isGroup && !item.senderFirstName.isNullOrBlank() -> "${item.senderFirstName}: "
        else -> ""
    }

    val content = when (item.lastMessageType) {
        "image" -> stringResource(R.string.dm_media_photo)
        else -> lastText ?: stringResource(R.string.dm_no_messages_yet)
    }

    return "\u2068$prefix$content\u2069"
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

/**
 * Contextual Empty States per Tab
 */
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
            .fillMaxSize()
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
                    .clickable(onClick = actionClick)
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

/**
 * Skeleton Loading rows on #F1F3F0
 */
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

/**
 * Search results with live text query highlighting
 */
@Composable
private fun DmSearchResultsContent(
    query: String,
    conversations: List<DmInboxItem>,
    people: List<PersonSearchResult>,
    isLoadingPeople: Boolean,
    onConversationClick: (String) -> Unit,
    onPersonClick: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    val matchedConvs = conversations.filter {
        it.effectiveTitle.contains(query, ignoreCase = true) ||
        (it.lastMessageText?.contains(query, ignoreCase = true) == true)
    }

    if (matchedConvs.isEmpty() && people.isEmpty() && !isLoadingPeople) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
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
            contentPadding = PaddingValues(vertical = 8.dp)
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
                                    text = person.displayName.ifBlank { person.username },
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (person.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerifiedBadge(size = 14.dp)
                                }
                            }
                            Text(
                                text = "@${person.username}",
                                color = colors.secondaryText,
                                fontSize = 13.sp
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
