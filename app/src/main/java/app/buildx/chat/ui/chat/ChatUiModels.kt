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
        val seenParticipants: List<DmParticipant>
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
    val peerHandle: String?
)
