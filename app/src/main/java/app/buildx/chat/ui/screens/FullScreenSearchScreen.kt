package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.ui.components.DrawerChatRow
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import coil.compose.AsyncImage

/**
 * P8 Full-Screen Search Screen:
 * - Opened from drawer search pill via container transform animation
 * - Top search field with instant clear and back navigation
 * - Tabs: People (pg_trgm ranked with Arabic normalization) | Chats
 * - Recent searches when query is empty
 * - Empty states and error retry
 */
@Composable
fun FullScreenSearchScreen(
    currentUserId: String?,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    activeTab: Int, // 0: People, 1: Chats
    onTabSelected: (Int) -> Unit,
    peopleResults: List<PersonSearchResult>,
    chatResults: List<Conversation>,
    recentSearches: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectPerson: (username: String) -> Unit,
    onSelectConversation: (id: String) -> Unit,
    onToggleFollow: (userId: String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val focusRequester = remember { FocusRequester() }

    val isSearchRtl = remember(searchQuery, strings.isRtl) {
        isRtlText(searchQuery, defaultRtl = strings.isRtl)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Search Bar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCircleButton(
                    iconRes = R.drawable.ic_back,
                    contentDescription = strings.cancelButton,
                    onClick = onBackClick
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Search Input Pill
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
                        contentDescription = strings.searchPlaceholder,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isSearchRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = BuildXFontFamily,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.primaryText,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(colors.primaryBlue),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = strings.searchPlaceholder,
                                            color = colors.secondaryText,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Clear search",
                            tint = colors.secondaryText,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onQueryChange("") }
                        )
                    }
                }
            }

            // Tabs: People | Chats
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = colors.pageBackground,
                contentColor = colors.primaryBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = colors.primaryBlue,
                        height = 2.5.dp
                    )
                },
                divider = { HorizontalDivider(color = colors.hairlineBorder) }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { onTabSelected(0) },
                    text = {
                        Text(
                            text = strings.peopleTab,
                            fontSize = 15.sp,
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeTab == 0) colors.primaryText else colors.secondaryText
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { onTabSelected(1) },
                    text = {
                        Text(
                            text = strings.chatsTab,
                            fontSize = 15.sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeTab == 1) colors.primaryText else colors.secondaryText
                        )
                    }
                )
            }

            // Content Area
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.primaryBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (errorMessage != null) {
                // Error with retry
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = strings.searchError,
                        fontSize = 15.sp,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onRetry) {
                        Text(
                            text = strings.retryButton,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryBlue
                        )
                    }
                }
            } else if (searchQuery.isBlank()) {
                // Show Recent Searches
                if (recentSearches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.recentSearches,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.secondaryText
                            )
                            Text(
                                text = strings.clearAll,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryBlue,
                                modifier = Modifier.clickable { onClearRecentSearches() }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn {
                            items(recentSearches) { term ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onQueryChange(term) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_clock_history),
                                        contentDescription = null,
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = term,
                                        fontSize = 15.sp,
                                        color = colors.primaryText
                                    )
                                }
                                HorizontalDivider(color = colors.hairlineBorder.copy(alpha = 0.5f))
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.searchPlaceholder,
                            fontSize = 15.sp,
                            color = colors.secondaryText.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                // Tab-specific results
                if (activeTab == 0) {
                    // People Tab Results
                    if (peopleResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.noPeopleFound,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.secondaryText
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(peopleResults, key = { it.id }) { person ->
                                PersonSearchRow(
                                    person = person,
                                    isSelf = person.id == currentUserId,
                                    onClick = { onSelectPerson(person.username) },
                                    onToggleFollow = { onToggleFollow(person.id) }
                                )
                                HorizontalDivider(
                                    color = colors.hairlineBorder.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 72.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Chats Tab Results
                    if (chatResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.noMatchingConversations,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.secondaryText
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            items(chatResults, key = { it.id }) { conv ->
                                DrawerChatRow(
                                    title = conv.cleanTitle,
                                    isUnread = false,
                                    isPinned = conv.pinned,
                                    isSelected = false,
                                    onClick = { onSelectConversation(conv.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonSearchRow(
    person: PersonSearchResult,
    isSelf: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!person.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = person.avatarUrl,
                contentDescription = person.displayName,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
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

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = person.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (person.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_verified_badge),
                        contentDescription = strings.verifiedAccountDescription,
                        tint = colors.primaryBlue,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "@${person.username}",
                    fontSize = 13.5.sp,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!isSelf) {
            Spacer(modifier = Modifier.width(8.dp))
            if (person.isFollowing) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceMuted)
                        .clickable { onToggleFollow() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.followingStatus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.primaryBlue)
                        .clickable { onToggleFollow() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.followAction,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryBlue
                    )
                }
            }
        }
    }
}
