package app.buildx.chat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    var selectedActionItem by remember { mutableStateOf<DmInboxItem?>(null) }
    var noteToReply by remember { mutableStateOf<FeedNoteItem?>(null) }

    // Text selection colors tailored to Build X to avoid native turquoise/blue cursor flash
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

                // Search Bar (44dp height, 22dp corner, real-time input)
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

                // Dynamic Body: Search Results Mode vs Normal Inbox Mode
                if (uiState.dmSearchQuery.isNotBlank()) {
                    // Search Mode
                    SearchResultsView(
                        query = uiState.dmSearchQuery,
                        peopleResults = uiState.dmSearchPeopleResults,
                        isPeopleLoading = uiState.isDmSearchPeopleLoading,
                        conversations = uiState.dmInboxItems,
                        onPersonClick = { personId -> onOpenDirectChatWithUser(personId) },
                        onConversationClick = onConversationClick,
                        onOpenProfile = onOpenProfile
                    )
                } else {
                    // Normal Inbox Mode
                    // 1. Notes Tray Section
                    NotesTraySection(
                        notes = uiState.inboxNotesTray,
                        myAvatarUrl = uiState.profile?.avatarUrl,
                        myActiveNoteContent = uiState.activeOwnNote?.content,
                        onAddOwnNoteClick = onOpenNoteSheet,
                        onFollowedNoteClick = { noteToReply = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Section Divider
                    HorizontalDivider(
                        thickness = 0.7.dp,
                        color = colors.hairlineBorder,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Filter Chips Bar (All, Unread, Groups, Requests)
                    FilterChipsBar(
                        selectedTab = uiState.selectedDmFilterTab,
                        unreadCount = uiState.totalUnreadDmCount,
                        onSelectTab = onSelectFilterTab
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 4. Filtered Conversations List
                    val filteredList = remember(uiState.dmInboxItems, uiState.selectedDmFilterTab) {
                        when (uiState.selectedDmFilterTab) {
                            DmFilterTab.ALL -> uiState.dmInboxItems
                            DmFilterTab.UNREAD -> uiState.dmInboxItems.filter { it.unreadCount > 0 }
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chat_bubble),
                                    contentDescription = null,
                                    tint = colors.secondaryText.copy(alpha = 0.35f),
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (uiState.selectedDmFilterTab == DmFilterTab.UNREAD) {
                                        stringResource(R.string.dm_filter_unread)
                                    } else {
                                        stringResource(R.string.dm_empty_inbox_title)
                                    },
                                    color = colors.primaryText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.dm_empty_inbox_subtitle),
                                    color = colors.secondaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
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
                                    onClick = { onConversationClick(item.id) },
                                    onLongClick = { selectedActionItem = item },
                                    onOpenProfile = onOpenProfile
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
                    onDeleteOrLeave = { onDeleteOrLeaveConversation(item.id) }
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
    unreadCount: Int,
    onSelectTab: (DmFilterTab) -> Unit
) {
    val colors = BuildXTheme.colors

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tabs = listOf(
            DmFilterTab.ALL to R.string.dm_filter_all,
            DmFilterTab.UNREAD to R.string.dm_filter_unread,
            DmFilterTab.GROUPS to R.string.dm_filter_groups,
            DmFilterTab.REQUESTS to R.string.dm_filter_requests
        )

        items(tabs, key = { it.first.name }) { (tab, stringRes) ->
            val isSelected = selectedTab == tab
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) colors.primaryText else colors.surfaceMuted)
                    .then(
                        if (!isSelected) {
                            Modifier.border(0.7.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                        } else Modifier
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = stringRes),
                    color = if (isSelected) colors.pageBackground else colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )

                if (tab == DmFilterTab.UNREAD && unreadCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.pageBackground else colors.primaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
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
            .padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Own Note Item (74dp avatar container)
        item(key = "own_note") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(86.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAddOwnNoteClick
                    )
            ) {
                Box(
                    modifier = Modifier.width(86.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Thought Bubble above avatar
                    if (!myActiveNoteContent.isNullOrBlank()) {
                        NoteThoughtBubble(
                            text = myActiveNoteContent,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else {
                        // Subtle hint bubble for empty note
                        Box(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.cardBackground)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dm_leave_a_note),
                                fontSize = 10.5.sp,
                                color = colors.secondaryText,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // 74dp Avatar with Plus Badge
                    Box(
                        modifier = Modifier
                            .padding(top = 26.dp)
                            .size(74.dp)
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Plus Badge at BottomEnd (24dp circle, 2dp border, primaryText background, 13dp ic_plus)
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

                Spacer(modifier = Modifier.height(5.dp))
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

        // Followed Users' Notes (74dp avatar container)
        items(notes.filter { !it.isOwn }, key = { it.id }) { note ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(86.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFollowedNoteClick(note) }
                    )
            ) {
                Box(
                    modifier = Modifier.width(86.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NoteThoughtBubble(
                        text = note.content,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .padding(top = 26.dp)
                            .size(74.dp)
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.cardBackground)
                    .softShadow(colors.cardBackground.copy(alpha = 0.22f), cornerRadius = 14.dp, blurRadius = 8.dp)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
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

            // Two thought tail dots
            Box(
                modifier = Modifier
                    .size(6.5.dp)
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    val hasUnread = item.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (56dp)
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
                // Composite dual-avatar stack for groups (36dp overlapping circles)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(colors.primaryBlue.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = (item.title?.firstOrNull() ?: 'G').uppercaseChar().toString()
                    Text(
                        text = initial,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryBlue
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .border(2.dp, colors.pageBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.memberCount.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.secondaryText
                    )
                }
            } else {
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
                                } else Modifier
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

                if (item.peer?.isVerified == true) {
                    VerifiedBadge(
                        size = 15.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Titles & Last Message
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayName = if (item.kind == "direct") {
                    item.peer?.displayName ?: item.peer?.handle ?: "User"
                } else {
                    item.title ?: stringResource(R.string.dm_new_group)
                }

                Text(
                    text = displayName,
                    color = colors.primaryText,
                    fontSize = 15.5.sp,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (item.kind == "direct" && item.peer?.isVerified == true) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                }

                // Inline relative timestamp: " · 12m"
                val timeStr = formatRelativeTime(item.lastMessageAt)
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = " · $timeStr",
                        color = if (hasUnread) colors.primaryBlue else colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val isDeleted = item.lastMessage?.deletedAt != null
                val subtitleText = when {
                    isDeleted -> stringResource(R.string.dm_message_unsent)
                    item.lastMessage?.messageType == "image" || item.lastMessage?.messageType == "photo" -> stringResource(R.string.dm_media_attachment)
                    !item.lastMessageText.isNullOrBlank() -> item.lastMessageText
                    item.kind == "group" -> stringResource(R.string.dm_group_members_format, item.memberCount ?: 0)
                    else -> stringResource(R.string.dm_tap_to_chat)
                }

                val isItalic = isDeleted

                Text(
                    text = subtitleText,
                    color = if (hasUnread) colors.primaryText else colors.secondaryText,
                    fontSize = 13.5.sp,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (item.isPinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pin),
                        contentDescription = "Pinned",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (item.isMuted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_volume_off),
                        contentDescription = "Muted",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (hasUnread) {
                    Spacer(modifier = Modifier.width(6.dp))
                    if (item.unreadCount > 1) {
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.primaryBlue)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
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

@Composable
private fun SearchResultsView(
    query: String,
    peopleResults: List<PersonSearchResult>,
    isPeopleLoading: Boolean,
    conversations: List<DmInboxItem>,
    onPersonClick: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onOpenProfile: (String) -> Unit
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
                            if (person.isVerified) {
                                VerifiedBadge(
                                    size = 14.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 2.dp, y = 2.dp)
                                )
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
                                    VerifiedBadge(size = 13.dp)
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
                        onClick = { onConversationClick(conv.id) },
                        onLongClick = {},
                        onOpenProfile = onOpenProfile
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
                                .border(1.dp, colors.subtleSurfaceBorder, CircleShape)
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
                    if (note.author.isVerified) {
                        VerifiedBadge(
                            size = 15.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp)
                        )
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
            seconds < 60 -> "now"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
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
