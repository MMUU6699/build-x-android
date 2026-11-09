package app.buildx.chat.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.buildx.chat.BuildConfig
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.ChatSseEvent
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.data.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

/**
 * Single real backend repository talking to Supabase Auth, PostgREST (RLS-protected),
 * Storage, and the deployed /functions/v1/chat SSE Edge Function.
 */
class BuildXRepository(private val context: Context) {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }

    private val sseHttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000L
            connectTimeoutMillis = 20_000L
            socketTimeoutMillis = 120_000L
        }
    }

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    fun currentAccessToken(): String? = supabase.auth.currentAccessTokenOrNull()

    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                ensureUserProfile()
            }
        }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        handle: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanHandle = handle.trim().removePrefix("@").lowercase()
            supabase.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                this.data = buildJsonObject {
                    put("display_name", displayName.trim())
                    put("handle", cleanHandle)
                }
            }
            if (supabase.auth.currentSessionOrNull() == null) {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
            }
            ensureUserProfile(
                fallbackDisplayName = displayName.trim(),
                fallbackHandle = cleanHandle
            )
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.auth.signOut()
        }
    }

    suspend fun ensureUserProfile(
        fallbackDisplayName: String = "User",
        fallbackHandle: String = "user"
    ): UserProfile = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: error("Not authenticated")
        val existing = supabase.from("profiles")
            .select {
                filter { eq("id", uid) }
            }
            .decodeSingleOrNull<UserProfile>()

        if (existing != null) {
            existing
        } else {
            val created = UserProfile(
                id = uid,
                handle = fallbackHandle.ifBlank { "user_${uid.take(6)}" },
                displayName = fallbackDisplayName.ifBlank { "User" },
                appearance = "automatic"
            )
            supabase.from("profiles").upsert(created)
            created
        }
    }

    suspend fun updateProfile(
        displayName: String,
        handle: String,
        avatarUrl: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = currentUserId() ?: error("Not authenticated")
            val cleanHandle = handle.trim().removePrefix("@")
            val updatePayload = buildJsonObject {
                put("display_name", displayName.trim())
                put("handle", cleanHandle)
                if (avatarUrl != null) {
                    put("avatar_url", avatarUrl)
                }
            }
            supabase.from("profiles").update(updatePayload) {
                filter { eq("id", uid) }
            }
            ensureUserProfile()
        }
    }

    suspend fun updateAppearance(appearance: AppAppearance): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = currentUserId() ?: return@runCatching
                val payload = buildJsonObject {
                    put("theme_mode", appearance.dbValue)
                }
                supabase.from("profiles").update(payload) {
                    filter { eq("id", uid) }
                }
                Unit
            }
        }

    suspend fun fetchConversations(): Result<List<Conversation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = currentUserId() ?: return@runCatching emptyList()
                supabase.from("conversations")
                    .select {
                        filter { eq("user_id", uid) }
                        order("pinned", Order.DESCENDING)
                        order("updated_at", Order.DESCENDING)
                    }
                    .decodeList<Conversation>()
            }
        }

    /**
     * Real full-text / ilike search across conversation titles AND message contents.
     */
    suspend fun searchConversations(query: String): Result<List<Conversation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = currentUserId() ?: return@runCatching emptyList()
                val trimmed = query.trim()
                if (trimmed.isEmpty()) {
                    return@runCatching fetchConversations().getOrThrow()
                }
                val pattern = "%$trimmed%"
                val byTitle = supabase.from("conversations")
                    .select {
                        filter {
                            eq("user_id", uid)
                            ilike("title", pattern)
                        }
                    }
                    .decodeList<Conversation>()

                val matchingMessages = supabase.from("messages")
                    .select {
                        filter {
                            ilike("content", pattern)
                        }
                    }
                    .decodeList<ChatMessage>()

                val convIdsFromMessages = matchingMessages.map { it.conversationId }.toSet()
                val allUserConvs = fetchConversations().getOrDefault(emptyList())
                val combinedIds = byTitle.map { it.id }.toSet() + convIdsFromMessages
                allUserConvs.filter { it.id in combinedIds }
            }
        }

    suspend fun fetchMessages(conversationId: String): Result<List<ChatMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("messages")
                    .select {
                        filter { eq("conversation_id", conversationId) }
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<ChatMessage>()
            }
        }

    /**
     * Computes which conversations have an unread completed assistant reply
     * (completed assistant message with created_at > conversation.last_read_at).
     */
    suspend fun fetchUnreadConversationIds(conversations: List<Conversation>): Set<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (conversations.isEmpty()) return@runCatching emptySet()
                val unreadIds = mutableSetOf<String>()
                val assistantMessages = supabase.from("messages")
                    .select {
                        filter {
                            eq("role", "assistant")
                            eq("status", "done")
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<ChatMessage>()

                val latestByConv = assistantMessages.groupBy { it.conversationId }
                    .mapValues { (_, list) -> list.maxByOrNull { it.createdAt } }

                for (conv in conversations) {
                    val latestMsg = latestByConv[conv.id] ?: continue
                    val msgInstant = runCatching { Instant.parse(latestMsg.createdAt) }.getOrNull()
                    val readInstant = runCatching { Instant.parse(conv.lastReadAt) }.getOrNull()
                    if (msgInstant != null && readInstant != null && msgInstant.isAfter(readInstant)) {
                        unreadIds.add(conv.id)
                    }
                }
                unreadIds
            }.getOrDefault(emptySet())
        }

    suspend fun markConversationRead(conversationId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val nowIso = Instant.now().toString()
                val payload = buildJsonObject {
                    put("last_read_at", nowIso)
                }
                supabase.from("conversations").update(payload) {
                    filter { eq("id", conversationId) }
                }
                Unit
            }
        }

    suspend fun togglePinConversation(conversationId: String, pinned: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("pinned", pinned)
                }
                supabase.from("conversations").update(payload) {
                    filter { eq("id", conversationId) }
                }
                Unit
            }
        }

    suspend fun renameConversation(conversationId: String, newTitle: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("title", newTitle.trim())
                }
                supabase.from("conversations").update(payload) {
                    filter { eq("id", conversationId) }
                }
                Unit
            }
        }

    suspend fun deleteConversation(conversationId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("conversations").delete {
                    filter { eq("id", conversationId) }
                }
                Unit
            }
        }

    suspend fun deleteAllConversations(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = currentUserId() ?: return@runCatching
                supabase.from("conversations").delete {
                    filter { eq("user_id", uid) }
                }
                Unit
            }
        }

    suspend fun rateMessage(messageId: String, rating: Int?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    if (rating == null) {
                        put("rating", JsonNull)
                    } else {
                        put("rating", rating)
                    }
                }
                supabase.from("messages").update(payload) {
                    filter { eq("id", messageId) }
                }
                Unit
            }
        }

    suspend fun createShareLink(conversationId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = UUID.randomUUID().toString().replace("-", "").take(20)
                val payload = buildJsonObject {
                    put("share_token", token)
                }
                supabase.from("conversations").update(payload) {
                    filter { eq("id", conversationId) }
                }
                "${BuildConfig.SUPABASE_URL}/functions/v1/chat?share_token=$token"
            }
        }

    suspend fun revokeShareLinks(conversationId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("share_token", JsonNull)
                }
                supabase.from("conversations").update(payload) {
                    filter { eq("id", conversationId) }
                }
                Unit
            }
        }

    /**
     * Uploads an attachment URI to Supabase Storage bucket "attachments" and inserts a row into public.attachments.
     */
    suspend fun uploadAttachment(staged: StagedAttachment): Result<AttachmentRow> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = currentUserId() ?: error("Not authenticated")
                val bytes = context.contentResolver.openInputStream(staged.uri)?.use { it.readBytes() }
                    ?: error("Cannot read file bytes")
                val safeName = staged.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val objectPath = "$uid/${System.currentTimeMillis()}_$safeName"

                val bucket = supabase.storage.from("attachments")
                bucket.upload(objectPath, bytes) {
                    upsert = true
                }
                val publicUrl = bucket.publicUrl(objectPath)

                val row = AttachmentRow(
                    id = UUID.randomUUID().toString(),
                    userId = uid,
                    storagePath = objectPath,
                    mimeType = staged.mimeType,
                    sizeBytes = bytes.size.toLong()
                )
                val inserted = supabase.from("attachments")
                    .insert(row) { select() }
                    .decodeSingle<AttachmentRow>()
                inserted.copy(publicUrl = publicUrl)
            }
        }

    suspend fun uploadAvatar(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = currentUserId() ?: error("Not authenticated")
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot read avatar image")
            val objectPath = "$uid/avatar_${System.currentTimeMillis()}.jpg"
            val bucket = supabase.storage.from("attachments")
            bucket.upload(objectPath, bytes) {
                upsert = true
            }
            val url = bucket.publicUrl(objectPath)
            updateProfile(
                displayName = ensureUserProfile().displayName,
                handle = ensureUserProfile().handle,
                avatarUrl = url
            )
            url
        }
    }

    suspend fun fetchUserUploadedAttachments(): Result<List<AttachmentRow>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = currentUserId() ?: return@runCatching emptyList()
                val bucket = supabase.storage.from("attachments")
                supabase.from("attachments")
                    .select {
                        filter { eq("user_id", uid) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<AttachmentRow>()
                    .map { it.copy(publicUrl = bucket.publicUrl(it.storagePath)) }
            }
        }

    /**
     * Queries recent device gallery images via Android MediaStore for the S6 Attachment sheet strip.
     */
    suspend fun queryRecentDeviceImages(limit: Int = 12): List<StagedAttachment> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<StagedAttachment>()
            runCatching {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.SIZE
                )
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    while (cursor.moveToNext() && results.size < limit) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "image_$id.jpg"
                        val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                        val size = cursor.getLong(sizeCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        results.add(
                            StagedAttachment(
                                uri = contentUri,
                                fileName = name,
                                mimeType = mime,
                                sizeBytes = size,
                                isImage = true
                            )
                        )
                    }
                }
            }
            results
        }

    fun resolveUriDetails(uri: Uri): StagedAttachment {
        val resolver: ContentResolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var fileName = "attachment_${System.currentTimeMillis()}"
        var sizeBytes = 0L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx) ?: fileName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return StagedAttachment(
            uri = uri,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            isImage = mimeType.startsWith("image/")
        )
    }

    /**
     * Streams SSE events from /functions/v1/chat.
     * Cancelling the collecting coroutine immediately aborts the underlying HTTP connection,
     * which triggers the Edge Function's abort listener and marks the message status='stopped'.
     */
    fun streamChatCompletion(
        conversationId: String?,
        userText: String,
        mode: ChatMode,
        incognito: Boolean,
        attachments: List<AttachmentRow>,
        historyMessages: List<ChatMessage>
    ): Flow<ChatSseEvent> = flow {
        val token = currentAccessToken() ?: error("Session expired. Please sign in again.")
        val url = "${BuildConfig.SUPABASE_URL}/functions/v1/chat"

        val payload = buildJsonObject {
            if (conversationId != null) {
                put("conversation_id", conversationId)
            }
            put("mode", mode.apiValue)
            put("incognito", incognito)
            put(
                "messages",
                buildJsonArray {
                    historyMessages.filter { it.content.isNotBlank() }.forEach { msg ->
                        add(
                            buildJsonObject {
                                put("role", msg.role)
                                put("content", msg.content)
                            }
                        )
                    }
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userText)
                        }
                    )
                }
            )
            if (attachments.isNotEmpty()) {
                put(
                    "attachments",
                    buildJsonArray {
                        attachments.forEach { att ->
                            add(
                                buildJsonObject {
                                    put("path", att.storagePath)
                                    put("mime", att.mimeType)
                                    put("size", att.sizeBytes)
                                }
                            )
                        }
                    }
                )
            }
        }

        val statement = sseHttpClient.preparePost(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setBody(json.encodeToString(JsonObject.serializer(), payload))
        }

        statement.execute { response ->
            val channel = response.bodyAsChannel()
            var currentEvent: String? = null

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.startsWith("event:") -> {
                        currentEvent = line.removePrefix("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        val dataStr = line.removePrefix("data:").trim()
                        if (dataStr.isNotEmpty()) {
                            val obj = runCatching {
                                json.parseToJsonElement(dataStr).jsonObject
                            }.getOrNull()

                            if (obj != null) {
                                when (currentEvent) {
                                    "reasoning_step" -> {
                                        val stepNum = (obj["step_index"]?.jsonPrimitive?.intOrNull ?: 0) + 1
                                        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Thinking"
                                        val summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: ""
                                        val convId = obj["conversation_id"]?.jsonPrimitive?.contentOrNull
                                        emit(
                                            ChatSseEvent.ReasoningStepEvent(
                                                step = ReasoningStep(
                                                    step = stepNum,
                                                    title = title,
                                                    summary = summary
                                                ),
                                                conversationId = convId
                                            )
                                        )
                                    }
                                    "text_delta" -> {
                                        val text = obj["delta"]?.jsonPrimitive?.contentOrNull
                                            ?: obj["text"]?.jsonPrimitive?.contentOrNull
                                            ?: ""
                                        val convId = obj["conversation_id"]?.jsonPrimitive?.contentOrNull
                                        emit(
                                            ChatSseEvent.TextDeltaEvent(
                                                text = text,
                                                conversationId = convId
                                            )
                                        )
                                    }
                                    "title" -> {
                                        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                                        val convId = obj["conversation_id"]?.jsonPrimitive?.contentOrNull
                                        if (title.isNotBlank()) {
                                            emit(
                                                ChatSseEvent.TitleEvent(
                                                    title = title,
                                                    conversationId = convId
                                                )
                                            )
                                        }
                                    }
                                    "done" -> {
                                        val convId = obj["conversation_id"]?.jsonPrimitive?.contentOrNull
                                        val msgId = obj["message_id"]?.jsonPrimitive?.contentOrNull
                                        val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "done"
                                        emit(
                                            ChatSseEvent.DoneEvent(
                                                conversationId = convId,
                                                messageId = msgId,
                                                status = status
                                            )
                                        )
                                    }
                                    "error" -> {
                                        val errMsg = obj["error"]?.jsonPrimitive?.contentOrNull
                                            ?: obj["message"]?.jsonPrimitive?.contentOrNull
                                            ?: "Stream error"
                                        emit(ChatSseEvent.ErrorEvent(errMsg))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun exportUserDataJson(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = ensureUserProfile()
            val convs = fetchConversations().getOrDefault(emptyList())
            val allMessages = mutableListOf<ChatMessage>()
            for (c in convs) {
                allMessages.addAll(fetchMessages(c.id).getOrDefault(emptyList()))
            }
            val exportObj = buildJsonObject {
                put("exported_at", Instant.now().toString())
                put("profile", json.encodeToJsonElement(UserProfile.serializer(), profile))
                put(
                    "conversations",
                    JsonArray(convs.map { json.encodeToJsonElement(Conversation.serializer(), it) })
                )
                put(
                    "messages",
                    JsonArray(allMessages.map { json.encodeToJsonElement(ChatMessage.serializer(), it) })
                )
            }
            json.encodeToString(JsonObject.serializer(), exportObj)
        }
    }
}
