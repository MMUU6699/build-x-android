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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.buildx.chat.R
import app.buildx.chat.data.model.FollowUserItem
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

enum class NewChatMode {
    DIRECT,
    GROUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageOrGroupSheet(
    candidateUsers: List<FollowUserItem>,
    searchResults: List<PersonSearchResult>,
    isSearchLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectDirectUser: (String) -> Unit,
    groupTitle: String,
    onGroupTitleChange: (String) -> Unit,
    selectedGroupUserIds: Set<String>,
    onToggleSelectGroupUser: (String) -> Unit,
    onCreateGroup: () -> Unit,
    isCreatingGroup: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialMode: NewChatMode = NewChatMode.DIRECT
) {
    val colors = BuildXTheme.colors
    var currentMode by remember(initialMode) { mutableStateOf(initialMode) }
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
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Mode Switcher Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceMuted)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direct Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (currentMode == NewChatMode.DIRECT) colors.primaryText else Color.Transparent)
                        .clickable { currentMode = NewChatMode.DIRECT }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dm_mode_direct),
                        color = if (currentMode == NewChatMode.DIRECT) colors.pageBackground else colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Group Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (currentMode == NewChatMode.GROUP) colors.primaryText else Color.Transparent)
                        .clickable { currentMode = NewChatMode.GROUP }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dm_mode_group),
                        color = if (currentMode == NewChatMode.GROUP) colors.pageBackground else colors.secondaryText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (currentMode == NewChatMode.DIRECT) {
                // Direct Message Mode
                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
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
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.dm_search_people),
                                    color = colors.secondaryText,
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
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
                        if (searchQuery.isNotEmpty()) {
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

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearchLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.primaryBlue,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    val usersList: List<SelectableUser> = if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                        searchResults.map {
                            SelectableUser(
                                id = it.id,
                                username = it.username,
                                displayName = it.displayName,
                                avatarUrl = it.avatarUrl,
                                isVerified = it.isVerified
                            )
                        }
                    } else {
                        candidateUsers.map {
                            SelectableUser(
                                id = it.id,
                                username = it.username,
                                displayName = it.displayName,
                                avatarUrl = it.avatarUrl,
                                isVerified = it.isVerified
                            )
                        }
                    }

                    if (usersList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No users found" else "No followed users yet",
                                color = colors.secondaryText,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(usersList, key = { it.id }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onSelectDirectUser(user.id)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(44.dp)) {
                                        if (!user.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = user.avatarUrl,
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
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        if (user.isVerified) {
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
                                                text = user.displayName.ifBlank { user.username },
                                                color = colors.primaryText,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (user.isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                VerifiedBadge(size = 13.dp)
                                            }
                                        }
                                        Text(
                                            text = "@${user.username}",
                                            color = colors.secondaryText,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // New Group Mode
                // Group Name Input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (groupTitle.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dm_group_name_hint),
                            color = colors.secondaryText,
                            fontSize = 14.5.sp
                        )
                    }
                    BasicTextField(
                        value = groupTitle,
                        onValueChange = onGroupTitleChange,
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

                Spacer(modifier = Modifier.height(10.dp))

                // Selected Members Strip
                if (selectedGroupUserIds.isNotEmpty()) {
                    val allUsers = candidateUsers.associateBy { it.id }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(selectedGroupUserIds.toList(), key = { it }) { uid ->
                            val user = allUsers[uid]
                            val name = user?.displayName ?: user?.username ?: "User"
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surfaceMuted)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                                    .clickable { onToggleSelectGroupUser(uid) }
                                    .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(colors.primaryBlue.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.take(1).uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryBlue
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primaryText,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Remove",
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Member Selection List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(candidateUsers, key = { it.id }) { user ->
                        val isSelected = selectedGroupUserIds.contains(user.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleSelectGroupUser(user.id) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(40.dp)) {
                                if (!user.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = user.avatarUrl,
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
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.displayName.ifBlank { user.username },
                                        color = colors.primaryText,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (user.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        VerifiedBadge(size = 12.dp)
                                    }
                                }
                                Text(
                                    text = "@${user.username}",
                                    color = colors.secondaryText,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Selection Check Pill
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colors.primaryBlue else Color.Transparent)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) colors.primaryBlue else colors.subtleSurfaceBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check),
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Create Group Button
                val canCreate = groupTitle.isNotBlank() && selectedGroupUserIds.isNotEmpty() && !isCreatingGroup
                Button(
                    onClick = onCreateGroup,
                    enabled = canCreate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryBlue,
                        disabledContainerColor = colors.primaryBlue.copy(alpha = 0.4f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    if (isCreatingGroup) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.dm_create_group),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

private data class SelectableUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val isVerified: Boolean
)
