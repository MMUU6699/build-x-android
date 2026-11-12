package app.buildx.chat.data.model

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val username: String = "user",
    val handle: String = "user",
    @SerialName("display_name") val displayName: String = "User",
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("username_changed_at") val usernameChangedAt: String? = null,
    @SerialName("username_reviewed_at") val usernameReviewedAt: String? = null,
    @SerialName("theme_mode") val appearance: String = "automatic",
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    val effectiveUsername: String
        get() = username.ifBlank { handle.ifBlank { "user" } }
}

@Serializable
data class PublicNote(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class PublicProfile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("is_following") val isFollowing: Boolean = false,
    @SerialName("follows_you") val followsYou: Boolean = false,
    @SerialName("active_note") val activeNote: PublicNote? = null,
    @SerialName("note_subscribed") val noteSubscribed: Boolean = false,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("blocked_by_me") val blockedByMe: Boolean = false
)

/**
 * Real account metadata returned by the `get_account_info` RPC.
 * Rows that are null/absent are hidden by the About-this-account sheet.
 */
@Serializable
data class AccountInfo(
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("username_change_count") val usernameChangeCount: Int = 0,
    @SerialName("follows_you") val followsYou: Boolean = false,
    @SerialName("following_since") val followingSince: String? = null,
    @SerialName("is_self") val isSelf: Boolean = false
)

/**
 * Row shown on the Blocked accounts screen (`get_blocked_users` RPC).
 */
@Serializable
data class BlockedUser(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("blocked_at") val blockedAt: String? = null
)

enum class ReportReason(val dbValue: String) {
    SPAM("spam"),
    IMPERSONATION("impersonation"),
    FAKE_ACCOUNT("fake_account"),
    INAPPROPRIATE_CONTENT("inappropriate_content"),
    HARASSMENT("harassment"),
    OTHER("other")
}

@Serializable
data class FollowUserItem(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_following") val isFollowing: Boolean = false,
    @SerialName("follows_you") val followsYou: Boolean = false
)

@Serializable
data class PersonSearchResult(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("is_following") val isFollowing: Boolean = false
)

@Serializable
data class NotificationRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("actor_id") val actorId: String,
    val type: String, // "follow", "note"
    @SerialName("reference_id") val referenceId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("actor_username") val actorUsername: String? = null,
    @SerialName("actor_display_name") val actorDisplayName: String? = null,
    @SerialName("actor_avatar_url") val actorAvatarUrl: String? = null,
    @SerialName("actor_is_verified") val actorIsVerified: Boolean = false,
    @SerialName("note_content") val noteContent: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String = "New chat",
    val pinned: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("share_token") val shareToken: String? = null,
    @SerialName("last_read_at") val lastReadAt: String = "1970-01-01T00:00:00Z",
    @SerialName("updated_at") val updatedAt: String = "1970-01-01T00:00:00Z",
    @SerialName("created_at") val createdAt: String? = null
) {
    val effectivePinned: Boolean get() = pinned || isPinned
    val cleanTitle: String
        get() = title
            .removePrefix("محادثة:")
            .removePrefix("محادثة :")
            .trim()
            .ifEmpty { title }
}

@Serializable
data class ReasoningStep(
    val step: Int = 1,
    val title: String,
    val summary: String = ""
)

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    val role: String, // "user" or "assistant"
    val content: String = "",
    @SerialName("reasoning") val reasoningSteps: List<ReasoningStep> = emptyList(),
    val status: String = "done", // "streaming", "done", "stopped", "error"
    val rating: Int? = null,     // -1, 1, or null
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("edited_at") val editedAt: String? = null
) {
    val isEdited: Boolean
        get() = !editedAt.isNullOrBlank()
}

@Serializable
data class AttachmentRow(
    val id: String,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("path") val storagePath: String,
    @SerialName("mime") val mimeType: String,
    @SerialName("size") val sizeBytes: Long = 0L,
    @SerialName("created_at") val createdAt: String? = null,
    val publicUrl: String? = null
) {
    val fileName: String
        get() = storagePath.substringAfterLast('/')
}

/**
 * Local attachment item staged in the composer or loaded from MediaStore.
 */
data class StagedAttachment(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val isImage: Boolean = mimeType.startsWith("image/"),
    val uploadedRow: AttachmentRow? = null
)

enum class ChatMode(val apiValue: String) {
    THINKING("thinking"),
    INSTANT("instant")
}

enum class AppAppearance(val dbValue: String) {
    AUTOMATIC("automatic"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromDb(value: String?): AppAppearance = when (value?.lowercase()) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> AUTOMATIC
        }
    }
}

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    ARABIC("ar");

    companion object {
        fun fromCode(code: String?): AppLanguage = when (code?.lowercase()) {
            "en", "english" -> ENGLISH
            "ar", "arabic" -> ARABIC
            else -> SYSTEM
        }
    }
}

enum class MainSurfacePane {
    CHAT,
    NOTIFICATIONS,
    MEDIA
}

/**
 * Real-time SSE events emitted by the Supabase Edge Function /functions/v1/chat.
 */
sealed class ChatSseEvent {
    data class ReasoningStepEvent(val step: ReasoningStep, val conversationId: String?) : ChatSseEvent()
    data class TextDeltaEvent(val text: String, val conversationId: String?) : ChatSseEvent()
    data class TitleEvent(val title: String, val conversationId: String?) : ChatSseEvent()
    data class DoneEvent(
        val conversationId: String?,
        val messageId: String?,
        val status: String
    ) : ChatSseEvent()
    data class ErrorEvent(val message: String) : ChatSseEvent()
}

data class AppNotificationItem(
    val id: String,
    val conversationId: String?,
    val title: String,
    val body: String,
    val timestampMillis: Long
)

@Serializable
data class VerificationStatusInfo(
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("has_request") val hasRequest: Boolean = false,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("links") val links: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("decision_reason") val decisionReason: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null
)

enum class DmSendStatus { SENDING, SENT, FAILED }

@Serializable
data class DmPeerSummary(
    val id: String,
    val username: String? = null,
    val handle: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false
)

@Serializable
data class DmMemberAvatar(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class DmLastMessageSummary(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("message_type") val messageType: String = "text",
    val content: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class DmInboxItem(
    @SerialName("conversation_id") val conversationId: String? = null,
    val id: String = conversationId ?: "",
    val kind: String = "direct",
    val title: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val handle: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val status: String = "active",
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_muted") val isMuted: Boolean = false,
    @SerialName("muted_until") val mutedUntil: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("pinned_at") val pinnedAt: String? = null,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    @SerialName("last_read_message_id") val lastReadMessageId: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("last_message_id") val lastMessageId: String? = null,
    @SerialName("last_message_text") val lastMessageText: String? = null,
    @SerialName("last_message_type") val lastMessageType: String? = null,
    @SerialName("last_message_sender_id") val lastMessageSenderId: String? = null,
    @SerialName("sender_first_name") val senderFirstName: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("last_message") val lastMessage: DmLastMessageSummary? = null,
    val peer: DmPeerSummary? = null,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("member_avatars") val memberAvatars: List<DmMemberAvatar> = emptyList(),
    @SerialName("is_marked_unread") val isMarkedUnread: Boolean = false
) {
    val effectiveId: String get() = conversationId ?: id
    val effectiveTitle: String get() = displayName ?: title ?: peer?.displayName ?: "Chat"
    val effectiveAvatarUrl: String? get() = avatarUrl ?: peer?.avatarUrl
    val effectiveIsVerified: Boolean get() = isVerified || peer?.isVerified == true
}

@Serializable
data class DmParticipant(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    val handle: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    val role: String = "member",
    val status: String = "active",
    @SerialName("is_muted") val isMuted: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    @SerialName("last_read_message_id") val lastReadMessageId: String? = null
)

@Serializable
data class DmPeerIntro(
    val id: String,
    val username: String? = null,
    val handle: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("posts_count") val postsCount: Int = 0,
    @SerialName("following_since") val followingSince: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DmConversationMetadata(
    val id: String,
    val kind: String = "direct",
    val title: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("pinned_message_id") val pinnedMessageId: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DmConversationDetail(
    val success: Boolean = true,
    val conversation: DmConversationMetadata? = null,
    @SerialName("pinned_message") val pinnedMessage: DmMessage? = null,
    val participants: List<DmParticipant> = emptyList(),
    @SerialName("peer_intro") val peerIntro: DmPeerIntro? = null,
    val error: String? = null
)

@Serializable
data class DmReplyPreview(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_display_name") val senderDisplayName: String? = null,
    val content: String = "",
    @SerialName("attachment_mime") val attachmentMime: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class DmMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_username") val senderUsername: String? = null,
    @SerialName("sender_handle") val senderHandle: String? = null,
    @SerialName("sender_display_name") val senderDisplayName: String? = null,
    @SerialName("sender_avatar_url") val senderAvatarUrl: String? = null,
    @SerialName("sender_is_verified") val senderIsVerified: Boolean = false,
    @SerialName("message_type") val messageType: String = "text",
    val content: String = "",
    @SerialName("reply_to") val replyTo: DmReplyPreview? = null,
    @SerialName("reply_to_note_id") val replyToNoteId: String? = null,
    @SerialName("note_snapshot_text") val noteSnapshotText: String? = null,
    @SerialName("attachment_path") val attachmentPath: String? = null,
    @SerialName("attachment_mime") val attachmentMime: String? = null,
    @SerialName("attachment_size") val attachmentSize: Long? = null,
    @SerialName("client_temp_id") val clientTempId: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    val localSendStatus: DmSendStatus = DmSendStatus.SENT
)

@Serializable
data class FeedNoteItem(
    val id: String,
    @SerialName("user_id") val userId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("is_own") val isOwn: Boolean = false,
    val author: DmPeerSummary
)


