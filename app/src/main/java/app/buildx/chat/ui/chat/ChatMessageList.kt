package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reversed message list (`reverseLayout = true`, index 0 = newest at bottom):
 * - Smooth item placement spring when new messages push older ones up
 * - Bottom-pinned when at bottom; never yanks the user if scrolled up
 * - Floating "Scroll to bottom" button with unread badge
 * - Top pagination trigger without scroll jump
 */
@Composable
fun ChatMessageList(
    items: List<ChatListItem>,
    listState: LazyListState,
    chatColors: ChatColors,
    isLoadingInitial: Boolean,
    isPaginating: Boolean,
    hasMoreMessages: Boolean,
    activeMenuMessageId: String?,
    justSentClientTempIds: Set<String>,
    reduceMotion: Boolean,
    buildMenuItems: (ChatListItem.MessageItem) -> List<ActionMenuItem>,
    onLoadOlderMessages: () -> Unit,
    onConsumeJustSent: (String) -> Unit,
    onMessageTap: (DmMessage) -> Unit,
    onRetryFailedMessage: (DmMessage) -> Unit,
    onDismissMenu: () -> Unit,
    onSwipeToReply: (DmMessage) -> Unit,
    onViewProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    topContentPadding: Dp = ChatDimens.ListTopPadding,
    bottomContentPadding: Dp = ChatDimens.ListBottomPadding,
    scrollToBottomFabBottomOffset: Dp = bottomContentPadding + 8.dp,
    jumpToMessageId: String? = null,
    onConsumeJumpToMessage: () -> Unit = {},
    todayOverride: java.time.LocalDate? = null,
    onImageClick: (DmMessage) -> Unit = onMessageTap,
    onMessageLongPress: (DmMessage) -> Unit = onMessageTap,
    highlightedMessageId: String? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var transientHighlightId by remember { mutableStateOf<String?>(null) }

    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 36
        }
    }

    val shouldPrefetchOlder by remember(hasMoreMessages, isPaginating, items.size) {
        derivedStateOf {
            if (!hasMoreMessages || isPaginating || items.isEmpty()) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (items.lastIndex - ChatDimens.PaginationPrefetchThreshold).coerceAtLeast(0)
        }
    }

    LaunchedEffect(shouldPrefetchOlder) {
        if (shouldPrefetchOlder) {
            onLoadOlderMessages()
        }
    }

    // Track newly arrived received messages to animate them only when user is viewing bottom
    // (never animate initial load or paginated older history).
    var seenKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newlyReceivedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }
    var unreadWhileScrolledUp by remember { mutableIntStateOf(0) }

    val currentMessageKeys = remember(items) {
        items.filterIsInstance<ChatListItem.MessageItem>().map { it.stableKey }
    }

    LaunchedEffect(currentMessageKeys) {
        if (!initialized) {
            if (currentMessageKeys.isNotEmpty()) {
                seenKeys = currentMessageKeys.toHashSet()
                initialized = true
            }
            return@LaunchedEffect
        }

        val newestItem = items.firstOrNull() as? ChatListItem.MessageItem
        if (newestItem != null && newestItem.stableKey !in seenKeys) {
            if (!newestItem.isOwn) {
                if (isAtBottom) {
                    newlyReceivedKeys = newlyReceivedKeys + newestItem.stableKey
                } else {
                    unreadWhileScrolledUp += 1
                }
            }
        }
        seenKeys = seenKeys + currentMessageKeys
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom && unreadWhileScrolledUp > 0) {
            unreadWhileScrolledUp = 0
        }
    }

    // Scroll to bottom only when user isn't already pinned at bottom so item placement and send lift don't fight
    LaunchedEffect(justSentClientTempIds) {
        if (justSentClientTempIds.isNotEmpty() && items.isNotEmpty()) {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24) {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Jump to a specific message requested by in-chat Search or quoted reply tap
    LaunchedEffect(jumpToMessageId, items.size) {
        if (!jumpToMessageId.isNullOrBlank() && items.isNotEmpty()) {
            val targetIdx = items.indexOfFirst {
                it is ChatListItem.MessageItem && it.message.id == jumpToMessageId
            }
            if (targetIdx >= 0) {
                listState.animateScrollToItem(targetIdx)
                transientHighlightId = jumpToMessageId
                onConsumeJumpToMessage()
            }
        }
    }

    LaunchedEffect(transientHighlightId) {
        val currentTarget = transientHighlightId ?: return@LaunchedEffect
        delay(950L)
        if (transientHighlightId == currentTarget) {
            transientHighlightId = null
        }
    }

    val hasActiveMenu = activeMenuMessageId != null
    val menuBackdropColor by animateColorAsState(
        targetValue = if (hasActiveMenu) BuildXTheme.colors.scrimColor else Color.Transparent,
        animationSpec = tween(if (reduceMotion) 0 else 150),
        label = "message_list_menu_scrim"
    )
    val nonMessageDimAlpha by animateFloatAsState(
        targetValue = if (hasActiveMenu) 0.42f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else 150),
        label = "non_message_dim_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(menuBackdropColor)
            .then(
                if (hasActiveMenu) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissMenu
                    )
                } else {
                    Modifier
                }
            )
    ) {
        if (isLoadingInitial && items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BuildXTheme.colors.primaryBlue,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(30.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ChatDimens.ListHorizontalPadding,
                    end = ChatDimens.ListHorizontalPadding,
                    top = topContentPadding,
                    bottom = bottomContentPadding
                )
            ) {
                items(
                    items = items,
                    key = { it.stableKey },
                    contentType = { item ->
                        when (item) {
                            is ChatListItem.MessageItem -> "message"
                            is ChatListItem.DateSeparator -> "date_separator"
                            is ChatListItem.PeerIntroItem -> "peer_intro"
                        }
                    }
                ) { item ->
                    val placementModifier = if (reduceMotion) {
                        Modifier
                    } else {
                        Modifier.animateItem(
                            fadeInSpec = if (item is ChatListItem.MessageItem) null else tween(160),
                            placementSpec = spring(
                                dampingRatio = ChatMotion.ItemPlacementDamping,
                                stiffness = ChatMotion.ItemPlacementStiffness
                            ),
                            fadeOutSpec = null
                        )
                    }

                    when (item) {
                        is ChatListItem.MessageItem -> {
                            val tempId = item.message.clientTempId
                            val isJustSent = tempId != null && tempId in justSentClientTempIds
                            val isNewlyReceived = item.stableKey in newlyReceivedKeys

                            ChatMessageRow(
                                item = item,
                                chatColors = chatColors,
                                isMenuOpen = activeMenuMessageId == item.message.id,
                                menuItems = if (activeMenuMessageId == item.message.id) buildMenuItems(item) else emptyList(),
                                isJustSent = isJustSent,
                                isNewlyReceived = isNewlyReceived,
                                reduceMotion = reduceMotion,
                                onConsumeJustSent = {
                                    if (tempId != null) onConsumeJustSent(tempId)
                                },
                                onConsumeNewlyReceived = {
                                    newlyReceivedKeys = newlyReceivedKeys - item.stableKey
                                },
                                onBubbleTap = { onMessageTap(item.message) },
                                onBubbleLongPress = { onMessageLongPress(item.message) },
                                onImageClick = { onImageClick(item.message) },
                                onRetryClick = { onRetryFailedMessage(item.message) },
                                onReplyPreviewClick = { targetId ->
                                    val targetIdx = items.indexOfFirst {
                                        it is ChatListItem.MessageItem && it.message.id == targetId
                                    }
                                    if (targetIdx >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIdx)
                                            transientHighlightId = targetId
                                        }
                                    }
                                },
                                onDismissMenu = onDismissMenu,
                                onSwipeToReply = { onSwipeToReply(item.message) },
                                isHighlighted = item.message.id == highlightedMessageId || item.message.id == transientHighlightId,
                                isDimmed = hasActiveMenu,
                                modifier = placementModifier
                            )
                        }

                        is ChatListItem.DateSeparator -> {
                            ChatDateSeparatorChip(
                                createdAtIso = item.createdAtIso,
                                chatColors = chatColors,
                                modifier = placementModifier.graphicsLayer { alpha = nonMessageDimAlpha },
                                todayOverride = todayOverride
                            )
                        }

                        is ChatListItem.PeerIntroItem -> {
                            ChatCompactPeerIntroCard(
                                intro = item.intro,
                                chatColors = chatColors,
                                onViewProfileClick = {
                                    item.intro.handle?.takeIf { it.isNotBlank() }?.let(onViewProfileClick)
                                },
                                modifier = placementModifier.graphicsLayer { alpha = nonMessageDimAlpha }
                            )
                        }
                    }
                }

                if (isPaginating) {
                    item(key = "pagination_loader", contentType = "loader") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = BuildXTheme.colors.primaryBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Scroll-to-Bottom button with unread count badge
        AnimatedVisibility(
            visible = !isAtBottom && items.isNotEmpty(),
            enter = fadeIn(tween(if (reduceMotion) 0 else ChatMotion.ScrollToBottomAnimMs)) +
                scaleIn(tween(if (reduceMotion) 0 else ChatMotion.ScrollToBottomAnimMs), initialScale = 0.8f),
            exit = fadeOut(tween(if (reduceMotion) 0 else ChatMotion.ScrollToBottomAnimMs)) +
                scaleOut(tween(if (reduceMotion) 0 else ChatMotion.ScrollToBottomAnimMs), targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ChatDimens.ScrollToBottomMargin, bottom = scrollToBottomFabBottomOffset)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, end = 2.dp)
                        .size(ChatDimens.ScrollToBottomSize)
                        .pressScaleClickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                        .softShadow(
                            color = chatColors.scrollToBottomShadow,
                            cornerRadius = ChatDimens.ScrollToBottomSize / 2,
                            blurRadius = 12.dp,
                            offsetY = 2.dp
                        )
                        .clip(CircleShape)
                        .background(chatColors.scrollToBottomBg)
                        .border(0.8.dp, chatColors.scrollToBottomBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_down),
                        contentDescription = stringResource(R.string.dm_scroll_to_bottom),
                        tint = BuildXTheme.colors.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (unreadWhileScrolledUp > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(chatColors.unreadBadgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadWhileScrolledUp > 99) "99+" else unreadWhileScrolledUp.toString(),
                            color = chatColors.unreadBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
