package app.buildx.chat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState
import app.buildx.chat.ui.viewmodel.DmFilterTab
import coil.compose.AsyncImage

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
    onSelectFilterTab: (DmFilterTab) -> Unit = {},
    onToggleReadUnreadConversation: (String) -> Unit = {},
    onDeleteOrLeaveConversation: (String) -> Unit = {},
    onOpenDirectChatWithUser: (String) -> Unit = {},
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    currentUserId: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    var selectedActionItem by remember { mutableStateOf<DmInboxItem?>(null) }
    var selectedGroupInfoItem by remember { mutableStateOf<DmInboxItem?>(null) }
    var noteToReply by remember { mutableStateOf<FeedNoteItem?>(null) }

    // Text selection styling to prevent default native blue cursor flash at (0,0)
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
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header Bar (strict 18dp horizontal, 10dp vertical padding)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = "Back",
                        onClick = onBackClick
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = stringResource(R.string.dm_nav_messages),
                        color = colors.primaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    HeaderCircleButton(
                        iconRes = R.drawable.ic_plus,
                        contentDescription = stringResource(R.string.dm_new_chat),
                        onClick = onCreateGroupClick
                    )
                }

                // Search Bar (44dp height, 22dp corner, real-time query)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 14.dp),
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
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.dmSearchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.dm_search_placeholder),
                                    color = colors.secondaryText,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            BasicTextField(
                                value = uiState.dmSearchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.primaryText,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
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

                // Dynamic Body: Search Results vs Normal Inbox
                if (uiState.dmSearchQuery.isNotBlank()) {
                    SearchResultsView(
                        query = uiState.dmSearchQuery,
                        peopleResults = uiState.dmSearchPeopleResults,
                        isPeopleLoading = uiState.isDmSearchPeopleLoading,
                        conversations = uiState.dmInboxItems,
                        onPersonClick = { personId -> onOpenDirectChatWithUser(personId) },
                        onConversationClick = onConversationClick,
                        onOpenProfile = onOpenProfile,
                        currentUserId = currentUserId
                    )
                } else {
                    // Normal Inbox Mode
                    // 1. Upgraded Notes Tray Section (Fixed Geometry: 88dp x 118dp)
                    NotesTraySection(
                        notes = uiState.inboxNotesTray,
                        myAvatarUrl = uiState.profile?.avatarUrl,
                        myActiveNoteContent = uiState.activeOwnNote?.content,
                        onAddOwnNoteClick = onOpenNoteSheet,
                        onFollowedNoteClick = { noteToReply = it }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Filter Chips Bar with Live Counts
                    val unreadThreadsCount = remember(uiState.dmInboxItems) {
                        uiState.dmInboxItems.count { it.unreadCount > 0 || it.isMarkedUnread }
                    }
                    val groupThreadsCount = remember(uiState.dmInboxItems) {
                        uiState.dmInboxItems.count { it.kind == "group" }
                    }

                    FilterChipsBar(
                        selectedTab = uiState.selectedDmFilterTab,
                        allCount = uiState.dmInboxItems.size,
                        unreadCount = unreadThreadsCount,
                        groupsCount = groupThreadsCount,
                        requestsCount = 0,
                        onSelectTab = onSelectFilterTab
                    )

                    // 0.6dp Hairline Divider below chips
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = colors.hairlineBorder,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Filtered Conversations List
                    val filteredList = remember(uiState.dmInboxItems, uiState.selectedDmFilterTab) {
                        when (uiState.selectedDmFilterTab) {
                            DmFilterTab.ALL -> uiState.dmInboxItems
                            DmFilterTab.UNREAD -> uiState.dmInboxItems.filter { it.unreadCount > 0 || it.isMarkedUnread }
                            DmFilterTab.GROUPS -> uiState.dmInboxItems.filter { it.kind == "group" }
                            DmFilterTab.REQUESTS -> emptyList()
                        }
                    }

                    if (uiState.isDmInboxLoading && uiState.dmInboxItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.primaryBlue,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else if (filteredList.isEmpty()) {
                        // Contextual Empty States per Tab
                        ContextualEmptyState(
                            tab = uiState.selectedDmFilterTab,
                            onShowAllClick = { onSelectFilterTab(DmFilterTab.ALL) },
                            onCreateGroupClick = onCreateGroupClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(filteredList, key = { it.id }) { item ->
                                val hasActiveNote = uiState.inboxNotesTray.any {
                                    it.author.id == item.peer?.id && !it.content.isNullOrBlank()
                                }
                                DmInboxRow(
                                    item = item,
                                    hasActiveNote = hasActiveNote,
                                    currentUserId = currentUserId,
                                    onClick = { onConversationClick(item.id) },
                                    onLongClick = { selectedActionItem = item },
                                    onOpenProfile = onOpenProfile
                                )
                                // Indented Hairline Divider between rows aligned with text column
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 88.dp, end = 18.dp),
                                    thickness = 0.6.dp,
                                    color = colors.hairlineBorder
                                )
                            }
                        }
                    }
                }
            }

            // Conversation Actions Bottom Sheet
            selectedActionItem?.let { item ->
                DmConversationActionsSheet(
                    item = item,
                    onDismiss = { selectedActionItem = null },
                    onTogglePin = { onTogglePinConversation(item.id) },
                    onToggleMute = { onToggleMuteConversation(item.id) },
                    onToggleReadUnread = { onToggleReadUnreadConversation(item.id) },
                    onViewProfile = if (item.kind == "direct" && item.peer?.handle != null) {
                        { handle -> onOpenProfile(handle) }
                    } else null,
                    onViewGroupInfo = if (item.kind == "group") {
                        {
                            selectedActionItem = null
                            selectedGroupInfoItem = item
                        }
                    } else null,
                    onDeleteOrLeave = { onDeleteOrLeaveConversation(item.id) }
                )
            }

            // Group Info & Members Bottom Sheet
            selectedGroupInfoItem?.let { groupItem ->
                GroupInfoSheet(
                    item = groupItem,
                    participants = emptyList(),
                    isLoadingParticipants = false,
                    onRenameGroup = { newTitle -> onRenameGroup(groupItem.id, newTitle) },
                    onAddMembersClick = {
                        selectedGroupInfoItem = null
                        onCreateGroupClick()
                    },
                    onLeaveGroupClick = {
                        selectedGroupInfoItem = null
                        onDeleteOrLeaveConversation(groupItem.id)
                    },
                    onOpenProfile = onOpenProfile,
                    onDismiss = { selectedGroupInfoItem = null }
                )
            }

            // Note Reply Bottom Sheet
            noteToReply?.let { note ->
                NoteReplySheetDialog(
                    note = note,
                    onDismiss = { noteToReply = null },
                    onSendReply = { text ->
                        onReplyToNote(note, text)
                        noteToReply = null
                    },
                    onOpenProfile = onOpenProfile
                )
            }
        }
    }
}

@Composable
private fun FilterChipsBar(
    selectedTab: DmFilterTab,
    allCount: Int,
    unreadCount: Int,
    groupsCount: Int,
    requestsCount: Int,
    onSelectTab: (DmFilterTab) -> Unit
) {
    val colors = BuildXTheme.colors

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tabs = listOf(
            FilterChipData(DmFilterTab.ALL, R.string.dm_filter_all, allCount),
            FilterChipData(DmFilterTab.UNREAD, R.string.dm_filter_unread, unreadCount),
            FilterChipData(DmFilterTab.GROUPS, R.string.dm_filter_groups, groupsCount),
            FilterChipData(DmFilterTab.REQUESTS, R.string.dm_filter_requests, requestsCount)
        )

        items(tabs, key = { it.tab.name }) { item ->
            val isSelected = selectedTab == item.tab
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) colors.primaryText else colors.surfaceMuted)
                    .then(
                        if (!isSelected) {
                            Modifier.border(0.7.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                        } else Modifier
                    )
                    .clickable { onSelectTab(item.tab) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = item.titleRes),
                    color = if (isSelected) colors.pageBackground else colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )

                if (item.count > 0 && item.tab != DmFilterTab.ALL) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 17.dp)
                            .height(17.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.pageBackground else colors.primaryBlue)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.count > 99) "99+" else item.count.toString(),
                            color = if (isSelected) colors.primaryText else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private data class FilterChipData(
    val tab: DmFilterTab,
    val titleRes: Int,
    val count: Int
)

@Composable
private fun ContextualEmptyState(
    tab: DmFilterTab,
    onShowAllClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors

    val iconRes = when (tab) {
        DmFilterTab.UNREAD -> R.drawable.ic_check_circle
        DmFilterTab.GROUPS -> R.drawable.ic_user
        DmFilterTab.REQUESTS -> R.drawable.ic_chat_bubble
        DmFilterTab.ALL -> R.drawable.ic_chat_bubble
    }

    val title = when (tab) {
        DmFilterTab.UNREAD -> stringResource(R.string.dm_empty_unread_title)
        DmFilterTab.GROUPS -> stringResource(R.string.dm_empty_groups_title)
        DmFilterTab.REQUESTS -> stringResource(R.string.dm_empty_requests_title)
        DmFilterTab.ALL -> stringResource(R.string.dm_empty_inbox_title)
    }

    val subtitle = when (tab) {
        DmFilterTab.UNREAD -> stringResource(R.string.dm_empty_unread_subtitle)
        DmFilterTab.GROUPS -> stringResource(R.string.dm_empty_groups_subtitle)
        DmFilterTab.REQUESTS -> stringResource(R.string.dm_empty_requests_subtitle)
        DmFilterTab.ALL -> stringResource(R.string.dm_empty_inbox_subtitle)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = colors.secondaryText.copy(alpha = 0.35f),
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = colors.secondaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            when (tab) {
                DmFilterTab.UNREAD -> {
                    Button(
                        onClick = onShowAllClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceMuted,
                            contentColor = colors.primaryText
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dm_show_all_chats),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                DmFilterTab.GROUPS -> {
                    Button(
                        onClick = onCreateGroupClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dm_create_a_group),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun NotesTraySection(
    notes: List<FeedNoteItem>,
    myAvatarUrl: String?,
    myActiveNoteContent: String?,
    onAddOwnNoteClick: () -> Unit,
    onFollowedNoteClick: (FeedNoteItem) -> Unit
) {
    val colors = BuildXTheme.colors

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Own Note Item (Fixed geometry: 88dp x 118dp)
        item(key = "own_note") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAddOwnNoteClick
                    )
            ) {
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(98.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Floating Thought Bubble Card at TopCenter
                    if (!myActiveNoteContent.isNullOrBlank()) {
                        NoteThoughtBubble(
                            text = myActiveNoteContent,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    } else {
                        // Subtle prompt bubble so the layout height stays uniform
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.cardBackground)
                                .softShadow(colors.cardBackground.copy(alpha = 0.2f), cornerRadius = 16.dp, blurRadius = 8.dp)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.dm_leave_a_note),
                                fontSize = 11.5.sp,
                                color = colors.secondaryText,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // 72dp Circular Avatar at BottomCenter
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(72.dp)
                    ) {
                        if (!myAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = myAvatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.dp, colors.pageBackground, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(colors.surfaceMuted)
                                    .border(2.dp, colors.pageBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user),
                                    contentDescription = null,
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        // Plus Badge cleanly at Alignment.BottomEnd (24dp circle, 2dp border)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(colors.pageBackground)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(colors.primaryText),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_plus),
                                contentDescription = "Add note",
                                tint = colors.pageBackground,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dm_you),
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Followed Users' Notes (Fixed geometry: 88dp x 118dp)
        items(notes.filter { !it.isOwn }, key = { it.id }) { note ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFollowedNoteClick(note) }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(98.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NoteThoughtBubble(
                        text = note.content,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(72.dp)
                    ) {
                        if (!note.author.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = note.author.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.dp, colors.pageBackground, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(colors.surfaceMuted)
                                    .border(2.dp, colors.pageBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user),
                                    contentDescription = null,
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = note.author.displayName ?: note.author.handle ?: "",
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (note.author.isVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        VerifiedBadge(size = 12.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteThoughtBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBackground)
                    .softShadow(colors.cardBackground.copy(alpha = 0.22f), cornerRadius = 16.dp, blurRadius = 10.dp, offsetY = 2.dp)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = colors.primaryText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Two distinct circular tail dots connecting to top rim of avatar
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.6.dp, colors.subtleSurfaceBorder, CircleShape)
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.5.dp, colors.subtleSurfaceBorder, CircleShape)
            )
        }
    }
}

@Composable
private fun DmInboxRow(
    item: DmInboxItem,
    hasActiveNote: Boolean,
    currentUserId: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    val isUnread = item.unreadCount > 0 || item.isMarkedUnread

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading: 56dp Avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable {
                    if (item.kind == "direct" && item.peer?.handle != null) {
                        onOpenProfile(item.peer.handle)
                    } else {
                        onClick()
                    }
                }
        ) {
            if (item.kind == "group") {
                // Real Overlapping Dual-Member Avatar inside 56dp box
                val m1 = item.memberAvatars.getOrNull(0)
                val m2 = item.memberAvatars.getOrNull(1)

                // Back Circle (36dp at Alignment.TopStart)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    if (!m1?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = m1?.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        val initial = (m1?.displayName?.firstOrNull() ?: item.title?.firstOrNull() ?: 'G').uppercaseChar().toString()
                        Text(
                            text = initial,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryBlue
                        )
                    }
                }

                // Front Circle (38dp at Alignment.BottomEnd) with 2dp cutout ring in pageBackground
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.pageBackground)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    if (!m2?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = m2?.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        val initial = (m2?.displayName?.firstOrNull() ?: 'U').uppercaseChar().toString()
                        Text(
                            text = initial,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.secondaryText
                        )
                    }
                }
            } else {
                // 1-on-1 Clean 56dp Circular Avatar (NO verified checkmark on avatar!)
                val avatarUrl = item.peer?.avatarUrl
                val avatarModifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(
                        if (hasActiveNote) {
                            Modifier.border(2.dp, colors.primaryBlue, CircleShape)
                        } else {
                            Modifier.border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                        }
                    )

                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = avatarModifier
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(colors.surfaceMuted)
                            .then(
                                if (hasActiveNote) {
                                    Modifier.border(2.dp, colors.primaryBlue, CircleShape)
                                } else {
                                    Modifier.border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Center + Trailing Column (True Two-Axis Geometry: Start & End)
        Column(modifier = Modifier.weight(1f)) {
            // Top Line: Start (Title + VerifiedBadge) vs End (Timestamp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start: Title + VerifiedBadge
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayName = if (item.kind == "direct") {
                        item.peer?.displayName ?: item.peer?.handle ?: "User"
                    } else {
                        item.title ?: stringResource(R.string.dm_new_group)
                    }

                    Text(
                        text = displayName,
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Verified Badge ONLY next to Display Name (15dp)
                    if (item.kind == "direct" && item.peer?.isVerified == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 15.dp)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // End: Relative Time
                val timeStr = formatRelativeTime(item.lastMessageAt)
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = timeStr,
                        color = if (isUnread) colors.primaryBlue else colors.secondaryText,
                        fontSize = 12.5.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Bottom Line: Start (Subtitle Preview) vs End (Mute, Pin, Unread Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start: Subtitle Preview Text
                val isDeleted = item.lastMessage?.deletedAt != null
                val isSentByMe = currentUserId != null && item.lastMessage?.senderId == currentUserId

                val subtitleText = when {
                    isDeleted -> stringResource(R.string.dm_message_unsent)
                    item.lastMessage?.messageType == "image" || item.lastMessage?.messageType == "photo" -> stringResource(R.string.dm_sent_attachment)
                    !item.lastMessageText.isNullOrBlank() -> {
                        when {
                            isSentByMe -> stringResource(R.string.dm_you_prefix) + item.lastMessageText
                            item.kind == "group" && !item.lastMessage?.senderName.isNullOrBlank() -> {
                                val firstName = item.lastMessage!!.senderName!!.split(" ").firstOrNull() ?: item.lastMessage!!.senderName!!
                                "$firstName: ${item.lastMessageText}"
                            }
                            else -> item.lastMessageText
                        }
                    }
                    item.kind == "group" -> stringResource(R.string.dm_group_members_format, item.memberCount ?: 0)
                    else -> stringResource(R.string.dm_start_a_conversation)
                }

                val isItalic = isDeleted

                Text(
                    text = subtitleText,
                    color = if (isUnread) colors.primaryText else colors.secondaryText,
                    fontSize = 14.sp,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // End: Mute, Pin, Unread Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (item.isMuted) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_volume_off),
                            contentDescription = "Muted",
                            tint = colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (item.isPinned) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pin),
                            contentDescription = "Pinned",
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    if (isUnread) {
                        if (item.unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 20.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primaryBlue)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Marked unread with unreadCount == 0: 10dp blue circle
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryBlue)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsView(
    query: String,
    peopleResults: List<PersonSearchResult>,
    isPeopleLoading: Boolean,
    conversations: List<DmInboxItem>,
    onPersonClick: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    currentUserId: String?
) {
    val colors = BuildXTheme.colors
    val q = query.trim().lowercase()

    val matchingConversations = remember(conversations, q) {
        conversations.filter { item ->
            item.title?.lowercase()?.contains(q) == true ||
            item.peer?.displayName?.lowercase()?.contains(q) == true ||
            item.peer?.handle?.lowercase()?.contains(q) == true ||
            item.lastMessageText?.lowercase()?.contains(q) == true
        }
    }

    val hasAnyResults = peopleResults.isNotEmpty() || matchingConversations.isNotEmpty()

    if (!hasAnyResults && !isPeopleLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results found for \"$query\"",
                color = colors.secondaryText,
                fontSize = 14.5.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // People Section
            if (peopleResults.isNotEmpty() || isPeopleLoading) {
                item(key = "people_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dm_people_header),
                            color = colors.secondaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isPeopleLoading) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                color = colors.primaryBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                items(peopleResults, key = { "person_${it.id}" }) { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPersonClick(person.id) }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(46.dp)) {
                            if (!person.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = person.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(colors.surfaceMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_user),
                                        contentDescription = null,
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = person.displayName.ifBlank { person.username },
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (person.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerifiedBadge(size = 14.dp)
                                }
                            }
                            Text(
                                text = "@${person.username}",
                                color = colors.secondaryText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Conversations Section
            if (matchingConversations.isNotEmpty()) {
                item(key = "convs_header") {
                    Text(
                        text = stringResource(R.string.dm_conversations_header),
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }

                items(matchingConversations, key = { "conv_${it.id}" }) { conv ->
                    DmInboxRow(
                        item = conv,
                        hasActiveNote = false,
                        currentUserId = currentUserId,
                        onClick = { onConversationClick(conv.id) },
                        onLongClick = {},
                        onOpenProfile = onOpenProfile
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp, end = 18.dp),
                        thickness = 0.6.dp,
                        color = colors.hairlineBorder
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteReplySheetDialog(
    note: FeedNoteItem,
    onDismiss: () -> Unit,
    onSendReply: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    var replyText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        scrimColor = colors.scrimColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Friend's Profile Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable {
                            if (!note.author.handle.isNullOrBlank()) {
                                onDismiss()
                                onOpenProfile(note.author.handle)
                            }
                        }
                ) {
                    if (!note.author.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = note.author.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(0.8.dp, colors.subtleSurfaceBorder, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(colors.surfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user),
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = note.author.displayName ?: note.author.handle ?: "",
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
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
                            fontSize = 13.sp
                        )
                    }
                }

                // View profile pill button
                if (!note.author.handle.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceMuted)
                            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                onDismiss()
                                onOpenProfile(note.author.handle)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dm_view_profile),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Note Quote Card (Thought bubble style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceMuted)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        text = note.content,
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val timeStr = formatRelativeTime(note.createdAt)
                    Text(
                        text = stringResource(R.string.dm_shared_ago, timeStr),
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reply Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dm_reply_to_note, note.author.displayName ?: ""),
                            color = colors.secondaryText,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(colors.primaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (replyText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.primaryBlue)
                            .clickable { onSendReply(replyText) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_up),
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

private fun formatRelativeTime(isoStr: String?): String {
    if (isoStr.isNullOrBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(isoStr)
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(instant, now)
        val seconds = duration.seconds
        when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            seconds < 172800 -> "Yesterday"
            seconds < 604800 -> "${seconds / 86400}d"
            else -> {
                val zdt = instant.atZone(java.time.ZoneId.systemDefault())
                "${zdt.dayOfMonth}/${zdt.monthValue}"
            }
        }
    } catch (_: Exception) {
        ""
    }
}
