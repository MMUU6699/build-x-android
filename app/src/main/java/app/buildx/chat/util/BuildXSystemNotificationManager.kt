package app.buildx.chat.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.buildx.chat.MainActivity
import app.buildx.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * Owns every Android system notification surface for Build X.
 *
 * Three channels mirror the product's information architecture:
 *  - [CHANNEL_DIRECT_MESSAGES] for 1-to-1 chats, group chats and note replies
 *  - [CHANNEL_SOCIAL_ACTIVITY] for follows, note likes and note broadcasts
 *  - [CHANNEL_AI_ASSISTANT] for finished assistant replies
 *
 * Every notification carries a real circular large icon (remote avatar or a
 * generated initial), a MessagingStyle body for conversations, and a deep link
 * that lands the user exactly on the referenced conversation or profile.
 */
object BuildXSystemNotificationManager {

    const val CHANNEL_DIRECT_MESSAGES = "buildx_messages"
    const val CHANNEL_SOCIAL_ACTIVITY = "buildx_social"
    const val CHANNEL_AI_ASSISTANT = "buildx_ai_assistant"

    const val EXTRA_NAVIGATE_DM_CONVERSATION_ID = "app.buildx.chat.extra.DM_CONVERSATION_ID"
    const val EXTRA_NAVIGATE_PROFILE_USERNAME = "app.buildx.chat.extra.PROFILE_USERNAME"
    const val EXTRA_NAVIGATE_ROUTE = "app.buildx.chat.extra.ROUTE"

    private const val GROUP_DIRECT_MESSAGES = "buildx.group.messages"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val messages = NotificationChannel(
            CHANNEL_DIRECT_MESSAGES,
            context.getString(R.string.notif_channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_messages_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 120L, 80L, 120L)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            setShowBadge(true)
        }

        val social = NotificationChannel(
            CHANNEL_SOCIAL_ACTIVITY,
            context.getString(R.string.notif_channel_social_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_social_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 90L)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            setShowBadge(true)
        }

        val assistant = NotificationChannel(
            CHANNEL_AI_ASSISTANT,
            context.getString(R.string.notif_channel_ai_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_ai_desc)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            setShowBadge(true)
        }

        manager.createNotificationChannels(listOf(messages, social, assistant))
    }

    /**
     * Posts a conversation notification. Suppression happens before any work is
     * done so the caller can hand over every realtime insert unconditionally.
     */
    suspend fun notifyConversationMessage(
        context: Context,
        conversationId: String,
        conversationTitle: String,
        isGroup: Boolean,
        senderId: String,
        senderName: String,
        senderAvatarUrl: String?,
        messagePreview: String,
        currentUserId: String?,
        isMuted: Boolean,
        isConversationVisible: Boolean
    ) {
        if (conversationId.isBlank()) return
        if (!currentUserId.isNullOrBlank() && senderId == currentUserId) return
        if (isMuted) return
        if (isConversationVisible) return
        if (!areNotificationsEnabled(context)) return

        val safeName = senderName.ifBlank { conversationTitle.ifBlank { "Build X" } }
        val body = messagePreview.ifBlank { context.getString(R.string.notif_message_generic) }
        val avatar = loadCircularAvatar(context, senderAvatarUrl, safeName)

        val person = Person.Builder()
            .setName(safeName)
            .setKey(senderId.ifBlank { safeName })
            .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val style = NotificationCompat.MessagingStyle(
            Person.Builder().setName(context.getString(R.string.notif_me_label)).build()
        )
        style.setGroupConversation(isGroup)
        if (isGroup) {
            style.setConversationTitle(conversationTitle)
        }
        style.addMessage(body, System.currentTimeMillis(), person)

        val builder = NotificationCompat.Builder(context, CHANNEL_DIRECT_MESSAGES)
            .setSmallIcon(R.drawable.ic_buildx_mark)
            .setContentTitle(if (isGroup) conversationTitle else safeName)
            .setContentText(body)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setGroup(GROUP_DIRECT_MESSAGES)
            .setContentIntent(dmPendingIntent(context, conversationId))
            .apply { avatar?.let { setLargeIcon(it) } }

        post(context, notificationId("dm", conversationId), builder.build())
    }

    /**
     * Posts a social notification for a follow, note like or note broadcast.
     */
    suspend fun notifySocialActivity(
        context: Context,
        type: String,
        actorName: String,
        actorUsername: String?,
        actorAvatarUrl: String?,
        preview: String?,
        currentUserId: String?
    ) {
        if (type !in setOf("follow", "note", "note_like")) return
        if (actorName.isBlank()) return
        if (!areNotificationsEnabled(context)) return

        val title = when (type) {
            "follow" -> context.getString(R.string.notif_follow_title, actorName)
            "note_like" -> context.getString(R.string.notif_note_like_title, actorName)
            else -> context.getString(R.string.notif_note_title, actorName)
        }
        val body = when {
            !preview.isNullOrBlank() -> preview
            type == "follow" -> context.getString(R.string.notif_follow_body)
            else -> context.getString(R.string.notif_note_body)
        }

        val avatar = loadCircularAvatar(context, actorAvatarUrl, actorName)
        val builder = NotificationCompat.Builder(context, CHANNEL_SOCIAL_ACTIVITY)
            .setSmallIcon(R.drawable.ic_buildx_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(socialPendingIntent(context, type, actorUsername, currentUserId))
            .apply { avatar?.let { setLargeIcon(it) } }

        post(context, notificationId("social", type + (actorUsername ?: actorName)), builder.build())
    }

    /**
     * Posts the finished assistant answer when the user is no longer looking at
     * the assistant conversation.
     */
    suspend fun notifyAssistantReply(
        context: Context,
        conversationId: String?,
        preview: String,
        isConversationVisible: Boolean
    ) {
        if (isConversationVisible) return
        if (!areNotificationsEnabled(context)) return
        val body = preview.trim().ifBlank { return }
        val clipped = if (body.length > 320) body.take(317) + "..." else body

        val builder = NotificationCompat.Builder(context, CHANNEL_AI_ASSISTANT)
            .setSmallIcon(R.drawable.ic_buildx_mark)
            .setContentTitle(context.getString(R.string.notif_ai_title))
            .setContentText(clipped)
            .setStyle(NotificationCompat.BigTextStyle().bigText(clipped))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(aiPendingIntent(context, conversationId))

        post(context, notificationId("ai", conversationId ?: "assistant"), builder.build())
    }

    fun cancelConversation(context: Context, conversationId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId("dm", conversationId))
        } catch (_: SecurityException) {
            // Notifications are not permitted for this process.
        }
    }

    private fun areNotificationsEnabled(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(false)

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }

    private fun notificationId(scope: String, key: String): Int =
        31_000 + ((scope + ":" + key).hashCode() and 0x0FFF)

    private fun dmPendingIntent(context: Context, conversationId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_DM_CONVERSATION_ID, conversationId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId("dm", conversationId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun socialPendingIntent(
        context: Context,
        type: String,
        actorUsername: String?,
        currentUserId: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (type == "follow" && !actorUsername.isNullOrBlank()) {
                putExtra(EXTRA_NAVIGATE_PROFILE_USERNAME, actorUsername)
            } else {
                putExtra(EXTRA_NAVIGATE_ROUTE, "notifications")
            }
        }
        val key = type + (actorUsername ?: currentUserId.orEmpty())
        return PendingIntent.getActivity(
            context,
            notificationId("social", key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun aiPendingIntent(context: Context, conversationId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_ROUTE, "chat")
            if (!conversationId.isNullOrBlank()) {
                putExtra(EXTRA_NAVIGATE_DM_CONVERSATION_ID, "")
            }
        }
        return PendingIntent.getActivity(
            context,
            notificationId("ai", conversationId ?: "assistant"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private suspend fun loadCircularAvatar(
        context: Context,
        url: String?,
        fallbackName: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val remote = if (url.isNullOrBlank()) null else runCatching { downloadBitmap(url) }.getOrNull()
        val square = remote ?: return@withContext initialAvatar(context, fallbackName)
        circularCrop(square)
    }

    private fun downloadBitmap(rawUrl: String): Bitmap? {
        val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun circularCrop(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val src = Rect(
            (source.width - size) / 2,
            (source.height - size) / 2,
            (source.width - size) / 2 + size,
            (source.height - size) / 2 + size
        )
        canvas.drawBitmap(source, src, RectF(0f, 0f, size.toFloat(), size.toFloat()), paint)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, mask)
        return output
    }

    private fun initialAvatar(context: Context, name: String): Bitmap {
        val size = 192
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avatarColor(name) }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, background)

        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: context.getString(R.string.notif_initial_fallback)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val metrics = text.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(initial, size / 2f, baseline, text)
        return output
    }

    private fun avatarColor(name: String): Int {
        val palette = intArrayOf(
            Color.parseColor("#2563EB"),
            Color.parseColor("#0F766E"),
            Color.parseColor("#B45309"),
            Color.parseColor("#7C3AED"),
            Color.parseColor("#BE123C"),
            Color.parseColor("#1D4ED8")
        )
        if (name.isBlank()) return palette[0]
        return palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    }
}
