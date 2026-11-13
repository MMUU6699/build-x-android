package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.BlockedUser
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.NameWithBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage

/**
 * Settings sub-page listing every account the current user has blocked.
 * Rows expose the real display name, verified badge, @username and an Unblock pill.
 */
@Composable
fun BlockedAccountsScreen(
    blockedUsers: List<BlockedUser>,
    isLoading: Boolean,
    errorMessage: String?,
    hasMore: Boolean,
    onBackClick: () -> Unit,
    onUnblock: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = BuildXTheme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(blockedUsers.size, hasMore, isLoading) {
        if (!hasMore || isLoading) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (blockedUsers.isNotEmpty() && lastVisible >= blockedUsers.size - 1) {
            onLoadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = stringResource(R.string.pv2_back),
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.pv2_blocked_accounts_title),
                    color = colors.primaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.pv2_blocked_accounts_subtitle),
                    color = colors.secondaryText,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading && blockedUsers.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MarkLoader(
                        size = 30.dp,
                        isSpinning = true,
                        tint = colors.primaryBlue
                    )
                }
            }

            errorMessage != null && blockedUsers.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user_x),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage,
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.surfaceMuted)
                            .pressScaleClickable(onClick = onRetry)
                            .padding(horizontal = 26.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_retry),
                            color = colors.primaryText,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            blockedUsers.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user_x),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.pv2_blocked_accounts_empty_title),
                        color = colors.primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.pv2_blocked_accounts_empty_body),
                        color = colors.secondaryText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blockedUsers, key = { it.id }) { user ->
                        BlockedAccountRow(user = user, onUnblock = { onUnblock(user.id) })
                    }
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MarkLoader(
                                    size = 22.dp,
                                    isSpinning = true,
                                    tint = colors.primaryBlue
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
private fun BlockedAccountRow(user: BlockedUser, onUnblock: () -> Unit) {
    val colors = BuildXTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 18.dp,
                blurRadius = 10.dp,
                offsetY = 2.dp
            )
            .clip(RoundedCornerShape(18.dp))
            .background(colors.cardBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            if (!user.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
            NameWithBadge(
                name = user.displayName,
                isVerified = user.isVerified,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                badgeSize = 14.dp,
                badgeSpacing = 5.dp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "@${user.username}",
                color = colors.secondaryText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(17.dp))
                .background(colors.surfaceMuted)
                .pressScaleClickable(onClick = onUnblock)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.pv2_unblock),
                color = colors.primaryText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
