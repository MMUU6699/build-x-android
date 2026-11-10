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
import app.buildx.chat.data.model.FollowUserItem
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

/**
 * P4 Followers and Following Two-Tab List Screen.
 */
@Composable
fun FollowListsScreen(
    currentUserId: String?,
    targetUserId: String,
    initialTab: Int, // 0: Following, 1: Followers
    followingList: List<FollowUserItem>,
    followersList: List<FollowUserItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onTabSelected: (Int) -> Unit,
    onUserClick: (username: String) -> Unit,
    onToggleFollow: (userId: String) -> Unit,
    onBackClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var filterQuery by remember { mutableStateOf("") }

    LaunchedEffect(selectedTab) {
        onTabSelected(selectedTab)
    }

    val rawList = if (selectedTab == 0) followingList else followersList
    val displayedList = remember(rawList, filterQuery) {
        if (filterQuery.isBlank()) rawList
        else {
            val q = filterQuery.trim().lowercase()
            rawList.filter {
                it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Back arrow & Screen title
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
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (selectedTab == 0) strings.followingLabel else strings.followersLabel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            }

            // Two-tab selector: Following (N) & Followers (N)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.pageBackground,
                contentColor = colors.primaryBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = colors.primaryBlue,
                        height = 2.5.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = colors.hairlineBorder)
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "${strings.followingLabel} (${followingList.size})",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) colors.primaryText else colors.secondaryText
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "${strings.followersLabel} (${followersList.size})",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) colors.primaryText else colors.secondaryText
                        )
                    }
                )
            }

            // Search filter pill inside list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = strings.searchPlaceholder,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = BuildXFontFamily,
                        fontSize = 14.5.sp,
                        color = colors.primaryText
                    ),
                    cursorBrush = SolidColor(colors.primaryBlue),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (filterQuery.isEmpty()) {
                                Text(
                                    text = strings.searchPlaceholder,
                                    fontSize = 14.5.sp,
                                    color = colors.secondaryText
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (filterQuery.isNotEmpty()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = colors.secondaryText,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { filterQuery = "" }
                    )
                }
            }

            // List Content
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
            } else if (displayedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                            tint = colors.secondaryText.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == 0) "Not following anyone yet" else "No followers yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.secondaryText
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(displayedList, key = { it.id }) { user ->
                        FollowUserRow(
                            user = user,
                            isSelf = user.id == currentUserId,
                            onClick = { onUserClick(user.username) },
                            onToggleFollow = { onToggleFollow(user.id) }
                        )
                        HorizontalDivider(
                            color = colors.hairlineBorder.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUserRow(
    user: FollowUserItem,
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
        // 44dp Avatar
        if (!user.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.displayName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
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
                    contentDescription = user.displayName,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and @username + verified
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.isVerified) {
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
                    text = "@${user.username}",
                    fontSize = 13.5.sp,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Follow Action Button (if not self)
        if (!isSelf) {
            Spacer(modifier = Modifier.width(8.dp))
            if (user.isFollowing) {
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
                val btnText = if (user.followsYou) strings.followBackAction else strings.followAction
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.primaryBlue)
                        .clickable { onToggleFollow() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = btnText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryBlue
                    )
                }
            }
        }
    }
}
