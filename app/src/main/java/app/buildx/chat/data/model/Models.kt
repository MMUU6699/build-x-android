package app.buildx.chat.data.model

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val handle: String = "user",
    @SerialName("display_name") val displayName: String = "User",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("theme_mode") val appearance: String = "automatic",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String = "New chat",
    val pinned: Boolean = false,
    @SerialName("share_token") val shareToken: String? = null,
    @SerialName("last_read_at") val lastReadAt: String = "1970-01-01T00:00:00Z",
    @SerialName("updated_at") val updatedAt: String = "1970-01-01T00:00:00Z",
    @SerialName("created_at") val createdAt: String? = null
) {
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
    @SerialName("created_at") val createdAt: String = ""
)

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
