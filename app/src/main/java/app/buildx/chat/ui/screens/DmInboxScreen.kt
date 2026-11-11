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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import app.buildx.chat.R
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.ActionMenuPopup
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXUiState
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
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    var activeMenuConvId by remember { mutableStateOf<String?>(null) }
    var noteToReply by remember { mutableStateOf<FeedNoteItem?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    contentDescription = stringResource(R.string.dm_new_group),
                    onClick = onCreateGroupClick
                )
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
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
                                fontWeight = FontWeight.Normal
                            )
                        }
                        BasicTextField(
                            value = uiState.dmSearchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.primaryText,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium
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

            // Notes Tray
            NotesTraySection(
                notes = uiState.inboxNotesTray,
                myAvatarUrl = uiState.profile?.avatarUrl,
                myActiveNoteContent = uiState.activeOwnNote?.content,
                onAddOwnNoteClick = onOpenNoteSheet,
                onFollowedNoteClick = { noteToReply = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Conversations List
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
            } else if (uiState.dmInboxItems.isEmpty()) {
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
                            tint = colors.secondaryText.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.dm_empty_inbox_title),
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
                    items(uiState.dmInboxItems, key = { it.id }) { item ->
                        DmInboxRow(
                            item = item,
                            onClick = { onConversationClick(item.id) },
                            onLongClick = { activeMenuConvId = item.id },
                            onOpenProfile = { handle -> onOpenProfile(handle) }
                        )
                    }
                }
            }
        }

        // Conversation Long-press Popup Menu
        activeMenuConvId?.let { convId ->
            val conv = uiState.dmInboxItems.firstOrNull { it.id == convId }
            if (conv != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrimColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { activeMenuConvId = null }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ActionMenuPopup(
                        items = listOf(
                            ActionMenuItem(
                                label = if (conv.isPinned) stringResource(R.string.dm_unpin_chat) else stringResource(R.string.dm_pin_chat),
                                iconRes = R.drawable.ic_pin,
                                onClick = {
                                    onTogglePinConversation(convId)
                                    activeMenuConvId = null
                                }
                            ),
                            ActionMenuItem(
                                label = if (conv.isMuted) stringResource(R.string.dm_unmute_chat) else stringResource(R.string.dm_mute_chat),
                                iconRes = R.drawable.ic_volume_off,
                                onClick = {
                                    onToggleMuteConversation(convId)
                                    activeMenuConvId = null
                                }
                            )
                        )
                    )
                }
            }
        }

        // Note Reply Sheet
        noteToReply?.let { note ->
            NoteReplySheetDialog(
                note = note,
                onDismiss = { noteToReply = null },
                onSendReply = { text ->
                    onReplyToNote(note, text)
                    noteToReply = null
                }
            )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Own Note Item
            item(key = "own_note") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clickable(onClick = onAddOwnNoteClick)
                ) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        // Thought Bubble
                        if (!myActiveNoteContent.isNullOrBlank()) {
                            NoteThoughtBubble(
                                text = myActiveNoteContent,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        } else {
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
                                    fontSize = 10.sp,
                                    color = colors.secondaryText,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // Avatar
                        Box(
                            modifier = Modifier
                                .padding(top = 28.dp)
                                .size(58.dp)
                        ) {
                            if (!myAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = myAvatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .border(1.5.dp, colors.pageBackground, CircleShape)
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

                            // Plus Badge if no active note
                            if (myActiveNoteContent.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(colors.primaryBlue)
                                        .border(1.5.dp, colors.pageBackground, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_plus),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dm_you),
                        color = colors.secondaryText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Followed Users' Notes
            items(notes.filter { !it.isOwn }, key = { it.id }) { note ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clickable { onFollowedNoteClick(note) }
                ) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        NoteThoughtBubble(
                            text = note.content,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .padding(top = 28.dp)
                                .size(58.dp)
                        ) {
                            if (!note.author.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = note.author.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .border(1.5.dp, colors.pageBackground, CircleShape)
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
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.author.displayName ?: note.author.handle ?: "",
                        color = colors.secondaryText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    .softShadow(colors.cardBackground.copy(alpha = 0.2f), cornerRadius = 14.dp, blurRadius = 8.dp)
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = colors.primaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Two thought tail dots matching Build X profile design
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.6.dp, colors.subtleSurfaceBorder, CircleShape)
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            Box(
                modifier = Modifier
                    .size(3.dp)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(54.dp)
                .clickable {
                    if (item.kind == "direct" && item.peer?.handle != null) {
                        onOpenProfile(item.peer.handle)
                    } else {
                        onClick()
                    }
                }
        ) {
            val avatarUrl = if (item.kind == "direct") item.peer?.avatarUrl else item.avatarUrl
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
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
                        painter = painterResource(id = if (item.kind == "group") R.drawable.ic_user else R.drawable.ic_user),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            if (item.kind == "direct" && item.peer?.isVerified == true) {
                VerifiedBadge(
                    size = 15.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(13.dp))

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

                Spacer(modifier = Modifier.width(8.dp))

                // Timestamp
                val timeStr = formatRelativeTime(item.lastMessageAt)
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = timeStr,
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
                val preview = item.lastMessageText ?: ""
                Text(
                    text = preview,
                    color = if (hasUnread) colors.primaryText else colors.secondaryText,
                    fontSize = 13.5.sp,
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

@Composable
private fun NoteReplySheetDialog(
    note: FeedNoteItem,
    onDismiss: () -> Unit,
    onSendReply: (String) -> Unit
) {
    val colors = BuildXTheme.colors
    var replyText by remember { mutableStateOf("") }

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
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.cardBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Pill drag handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surfaceMuted)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Author and note content
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!note.author.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = note.author.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = note.author.displayName ?: note.author.handle ?: "",
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (note.author.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 14.dp)
                        }
                    }
                    Text(
                        text = note.content,
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input field & Send button
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.primaryBlue)
                            .clickable { onSendReply(replyText) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_up),
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
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
