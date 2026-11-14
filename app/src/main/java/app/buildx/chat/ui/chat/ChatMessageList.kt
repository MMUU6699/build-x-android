package app.buildx.chat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reversed message list (`reverseLayout = true`, index 0 = newest at bottom):
 * - Smooth 120fps item placement spring (`dampingRatio = 0.82f, stiffness = 420f`)
 * - Strictly clipped bounds (`Modifier.clipToBounds()`) so scrolled items never bleed into chrome
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
    scrollToBottomFabBottomOffset: Dp = bottomContentPadding + 14.dp,
    jumpToMessageId: String? = null,
    onConsumeJumpToMessage: () -> Unit = {},
    todayOverride: java.time.LocalDate? = null,
    onImageClick: (DmMessage) -> Unit = onMessageTap,
    onMessageLongPress: (DmMessage) -> Unit = onMessageTap,
    onMessageLongPressWithBounds: ((ChatListItem.MessageItem, Rect, Dp) -> Unit)? = null,
    onToggleReaction: (String, String) -> Unit = { _, _ -> },
    highlightedMessageId: String? = null,
    composerFlightBoundsInWindow: Rect? = null,
    lastOptimisticMessageId: String? = null
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
    LaunchedEffect(justSentClientTempIds, lastOptimisticMessageId) {
        if ((justSentClientTempIds.isNotEmpty() || !lastOptimisticMessageId.isNullOrBlank()) && items.isNotEmpty()) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        if (isLoadingInitial && items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MarkLoader(
                    size = 30.dp,
                    isSpinning = true,
                    tint = BuildXTheme.colors.primaryBlue
                )
            }
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
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
                            is ChatListItem.SystemEventItem -> "system_event"
                            is ChatListItem.DateSeparator -> "date_separator"
                            is ChatListItem.PeerIntroItem -> "peer_intro"
                        }
                    }
                ) { item ->
                    val placementModifier = if (reduceMotion || !initialized) {
                        Modifier
                    } else {
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = ChatMotion.sendFlightSpring(),
                            fadeOutSpec = null
                        )
                    }

                    when (item) {
                        is ChatListItem.MessageItem -> {
                            val tempId = item.message.clientTempId
                            val isJustSent = item.isOwn && (
                                item.isNewlyInserted ||
                                    (tempId != null && (tempId in justSentClientTempIds || tempId == lastOptimisticMessageId))
                                )
                            val isNewlyReceived = !item.isOwn && item.stableKey in newlyReceivedKeys
                            val isThisMenuOpen = activeMenuMessageId == item.message.id

                            ChatMessageRow(
                                item = item,
                                chatColors = chatColors,
                                isMenuOpen = isThisMenuOpen,
                                menuItems = if (isThisMenuOpen) buildMenuItems(item) else emptyList(),
                                isJustSent = isJustSent,
                                isNewlyReceived = isNewlyReceived,
                                reduceMotion = reduceMotion,
                                onConsumeJustSent = {
                                    val consumeId = tempId ?: lastOptimisticMessageId?.takeIf { it == item.message.id }
                                    if (consumeId != null) onConsumeJustSent(consumeId)
                                },
                                onConsumeNewlyReceived = {
                                    newlyReceivedKeys = newlyReceivedKeys - item.stableKey
                                },
                                onBubbleTap = { onMessageTap(item.message) },
                                onBubbleLongPress = { onMessageLongPress(item.message) },
                                onBubbleLongPressWithBounds = { boundsInWindow, maxBubbleWidth ->
                                    if (onMessageLongPressWithBounds != null) {
                                        onMessageLongPressWithBounds(item, boundsInWindow, maxBubbleWidth)
                                    } else {
                                        onMessageLongPress(item.message)
                                    }
                                },
                                onReactionClick = { emoji -> onToggleReaction(item.message.id, emoji) },
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
                                onSenderProfileClick = onViewProfileClick,
                                isHighlighted = item.message.id == highlightedMessageId || item.message.id == transientHighlightId,
                                isDimmed = false,
                                isHiddenForFocusOverlay = isThisMenuOpen,
                                composerFlightBoundsInWindow = composerFlightBoundsInWindow,
                                modifier = placementModifier
                            )
                        }

                        is ChatListItem.SystemEventItem -> {
                            ChatSystemEventPill(
                                text = item.isolatedText,
                                chatColors = chatColors,
                                modifier = placementModifier
                            )
                        }

                        is ChatListItem.DateSeparator -> {
                            ChatDateSeparatorChip(
                                createdAtIso = item.createdAtIso,
                                chatColors = chatColors,
                                modifier = placementModifier,
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
                                modifier = placementModifier
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
                            MarkLoader(
                                size = 22.dp,
                                isSpinning = true,
                                tint = BuildXTheme.colors.primaryBlue
                            )
                        }
                    }
                }
            }
        }

        // Floating Scroll-to-Bottom button with unread count badge
        AnimatedVisibility(
            visible = !isAtBottom && items.isNotEmpty(),
            enter = fadeIn(
                animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring()
            ) + scaleIn(
                animationSpec = if (reduceMotion) snap() else ChatMotion.interactiveSpring(),
                initialScale = 0.8f
            ),
            exit = fadeOut(
                animationSpec = if (reduceMotion) snap() else ChatMotion.fastOutBezierTween(ChatMotion.ScrollToBottomAnimMs)
            ) + scaleOut(
                animationSpec = if (reduceMotion) snap() else ChatMotion.fastOutBezierTween(ChatMotion.ScrollToBottomAnimMs),
                targetScale = 0.8f
            ),
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
