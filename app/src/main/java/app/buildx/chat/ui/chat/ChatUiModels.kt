package app.buildx.chat.ui.chat

import androidx.compose.runtime.Immutable
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.DmReplyPreview

enum class ChatBubblePosition {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST
}

enum class ChatDeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

sealed interface ChatListItem {
    val stableKey: String

    @Immutable
    data class MessageItem(
        override val stableKey: String,
        val message: DmMessage,
        val isOwn: Boolean,
        val isGroupChat: Boolean,
        val isDeleted: Boolean,
        val isEdited: Boolean,
        val isRtlContent: Boolean,
        val position: ChatBubblePosition,
        val isGroupedWithPrevious: Boolean,
        val showSenderName: Boolean,
        val showSenderAvatar: Boolean,
        val formattedTime: String,
        val deliveryStatus: ChatDeliveryStatus,
        val replyPreview: DmReplyPreview?,
        val seenParticipants: List<DmParticipant> = emptyList(),
        val seenByNames: List<String> = emptyList(),
        val isSeenByPeer: Boolean = false,
        val reactions: Map<String, Int> = emptyMap(),
        val myReaction: String? = null,
        val resolvedSenderName: String? = null,
        val resolvedSenderHandle: String? = null,
        val senderIsVerified: Boolean = false,
        val isNewlyInserted: Boolean = false,
        val isFirstInCluster: Boolean = (position == ChatBubblePosition.SINGLE || position == ChatBubblePosition.FIRST),
        val isLastInCluster: Boolean = (position == ChatBubblePosition.SINGLE || position == ChatBubblePosition.LAST),
        val topCornerDp: Float = if (isFirstInCluster) ChatDimens.BubbleCornerLarge.value else ChatDimens.BubbleCornerGrouped.value,
        val bottomCornerDp: Float = if (isLastInCluster) ChatDimens.BubbleCornerLarge.value else ChatDimens.BubbleCornerGrouped.value,
        val isolatedSenderName: String? = null,
        val isolatedFormattedTime: String = formattedTime,
        val isolatedReplySenderName: String? = null,
        val isolatedReplyContent: String? = null
    ) : ChatListItem {
        val isGroupedWithNext: Boolean
            get() = position == ChatBubblePosition.FIRST || position == ChatBubblePosition.MIDDLE
    }

    @Immutable
    data class SystemEventItem(
        override val stableKey: String,
        val message: DmMessage,
        val text: String,
        val createdAtIso: String,
        val isolatedText: String = text
    ) : ChatListItem

    @Immutable
    data class DateSeparator(
        override val stableKey: String,
        val dayKey: String,
        val createdAtIso: String
    ) : ChatListItem

    @Immutable
    data class PeerIntroItem(
        override val stableKey: String,
        val intro: DmPeerIntro
    ) : ChatListItem
}

@Immutable
data class ChatHeaderUiState(
    val title: String,
    val subtitle: String,
    val avatarUrl: String?,
    val isDirect: Boolean,
    val isVerified: Boolean,
    val isMuted: Boolean,
    val isPinned: Boolean,
    val isTyping: Boolean,
    val isOnline: Boolean,
    val peerHandle: String?,
    val isolatedTitle: String = title,
    val isolatedSubtitle: String = subtitle
)

/**
 * Canonical DM inbox badge counts derived from the unfiltered conversation list.
 */
@Immutable
data class ChatInboxCounters(
    val totalUnreadMessages: Int = 0,
    val unreadConversationsCount: Int = 0,
    val groupsCount: Int = 0
)
