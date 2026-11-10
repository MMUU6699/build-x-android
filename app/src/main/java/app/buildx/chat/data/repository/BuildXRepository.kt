package app.buildx.chat.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import app.buildx.chat.BuildConfig
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.ChatSseEvent
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.FollowUserItem
import app.buildx.chat.data.model.NotificationRow
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.data.model.VerificationStatusInfo
import app.buildx.chat.util.AuthValidation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
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

    private val prefs = context.getSharedPreferences("buildx_prefs", Context.MODE_PRIVATE)

    fun getSavedLanguage(): AppLanguage =
        AppLanguage.fromCode(prefs.getString("app_language", AppLanguage.SYSTEM.code))

    fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString("app_language", language.code).apply()
    }

    fun getSavedAppearance(): AppAppearance =
        AppAppearance.fromDb(prefs.getString("app_appearance", AppAppearance.AUTOMATIC.dbValue))

    fun saveAppearanceLocal(appearance: AppAppearance) {
        prefs.edit().putString("app_appearance", appearance.dbValue).apply()
    }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private class SharedPreferencesSessionManager(
        private val prefs: SharedPreferences,
        private val json: Json
    ) : SessionManager {
        private val sessionKey = "buildx_auth_session_json"

        override suspend fun saveSession(session: UserSession) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val str = json.encodeToString(UserSession.serializer(), session)
                    prefs.edit().putString(sessionKey, str).commit()
                }
            }
        }

        override suspend fun loadSession(): UserSession? {
            return withContext(Dispatchers.IO) {
                runCatching {
                    val str = prefs.getString(sessionKey, null) ?: return@runCatching null
                    json.decodeFromString(UserSession.serializer(), str)
                }.getOrNull()
            }
        }

        override suspend fun deleteSession() {
            withContext(Dispatchers.IO) {
                runCatching {
                    prefs.edit().remove(sessionKey).commit()
                }
            }
        }
    }

    val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            scheme = "app.buildx.chat"
            host = "login-callback"
            defaultRedirectUrl = "app.buildx.chat://login-callback"
            sessionManager = SharedPreferencesSessionManager(prefs, json)
            autoLoadFromStorage = true
            autoSaveToStorage = true
            alwaysAutoRefresh = true
        }
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

    suspend fun awaitAuthInitialization() {
        runCatching {
            supabase.auth.awaitInitialization()
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

    suspend fun signInWithIdentifier(identifier: String, password: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = identifier.trim()
                if (trimmed.isEmpty()) error("Identifier cannot be empty")
                if (password.isEmpty()) error("Password cannot be empty")

                val targetEmail: String = if (trimmed.contains("@") && !trimmed.startsWith("@") && trimmed.contains(".")) {
                    trimmed
                } else {
                    val clean = trimmed.removePrefix("@").lowercase()
                    val resolved = runCatching {
                        val params = buildJsonObject { put("p_identifier", clean) }
                        val resp = supabase.postgrest.rpc("get_login_email", params)
                        resp.data.trim().trim('"')
                    }.getOrNull()

                    if (resolved.isNullOrBlank() || resolved == "null") {
                        error("No account found for username @$clean")
                    }
                    resolved
                }

                supabase.auth.signInWith(Email) {
                    this.email = targetEmail
                    this.password = password
                }
                ensureUserProfile()
            }
        }

    suspend fun signInWithGoogle(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.auth.signInWith(
                provider = Google,
                redirectUrl = "app.buildx.chat://login-callback"
            )
        }
    }


    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        handle: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUsername = handle.trim().removePrefix("@").lowercase()
            val cleanEmail = email.trim()

            // Validate anti-fake / disposable email
            val emailError = AuthValidation.validateEmail(cleanEmail)
            if (emailError != null) error(emailError)

            // Validate username
            val userError = AuthValidation.validateUsername(cleanUsername)
            if (userError != null) error(userError)

            // Validate password
            val passError = AuthValidation.validatePassword(password)
            if (passError != null) error(passError)

            // Check availability
            val isAvail = checkUsernameAvailable(cleanUsername).getOrDefault(true)
            if (!isAvail) error("Username @$cleanUsername is already taken")

            supabase.auth.signUpWith(Email) {
                this.email = cleanEmail
                this.password = password
                this.data = buildJsonObject {
                    put("display_name", displayName.trim().ifBlank { cleanUsername })
                    put("username", cleanUsername)
                    put("handle", cleanUsername)
                }
            }
            if (supabase.auth.currentSessionOrNull() == null) {
                supabase.auth.signInWith(Email) {
                    this.email = cleanEmail
                    this.password = password
                }
            }
            ensureUserProfile(
                fallbackDisplayName = displayName.trim().ifBlank { cleanUsername },
                fallbackHandle = cleanUsername
            )
        }
    }

    suspend fun getMyVerificationStatus(): Result<VerificationStatusInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val response = supabase.postgrest.rpc("get_my_verification_status")
            response.decodeAs<VerificationStatusInfo>()
        }
    }

    suspend fun submitVerificationRequest(
        fullName: String,
        category: String,
        links: String,
        reason: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildJsonObject {
                put("full_name", fullName.trim())
                put("category", category.trim())
                put("links", links.trim())
                put("reason", reason.trim())
            }
            val response = supabase.postgrest.rpc("request_verification", params)
            val obj = response.decodeAs<JsonObject>()
            val success = obj["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            if (!success) {
                val err = obj["error"]?.jsonPrimitive?.contentOrNull ?: "Failed to submit request"
                error(err)
            }
            true
        }
    }

    suspend fun withdrawVerificationRequest(requestId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val params = if (!requestId.isNullOrBlank()) {
                buildJsonObject { put("p_request_id", requestId) }
            } else {
                buildJsonObject {}
            }
            val res = supabase.postgrest.rpc("withdraw_verification_request", params)
            val obj = res.decodeAs<JsonObject>()
            obj["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
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
            val cleanName = fallbackHandle.ifBlank { "user_${uid.take(6)}" }
            val created = UserProfile(
                id = uid,
                username = cleanName,
                handle = cleanName,
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
        updateProfileDetails(displayName, null, avatarUrl)
    }

    suspend fun getOwnProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            ensureUserProfile()
        }
    }

    suspend fun getPublicProfile(targetUsername: String): Result<PublicProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = targetUsername.trim().removePrefix("@")
            val params = buildJsonObject { put("target_username", clean) }
            val response = supabase.postgrest.rpc("get_public_profile", params)
            response.decodeAs<PublicProfile>()
        }
    }

    suspend fun searchPeople(query: String, limit: Int = 30, offset: Int = 0): Result<List<PersonSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim()
            val params = buildJsonObject {
                put("query", clean)
                put("p_limit", limit)
                put("p_offset", offset)
            }
            val response = supabase.postgrest.rpc("search_people", params)
            response.decodeList<PersonSearchResult>()
        }
    }

    suspend fun toggleFollow(targetUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildJsonObject { put("target_user_id", targetUserId) }
            val response = supabase.postgrest.rpc("toggle_follow", params)
            val jsonObject = response.decodeAs<JsonObject>()
            jsonObject["is_following"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: jsonObject["following"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: false
        }
    }

    suspend fun checkUsernameAvailable(candidate: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = candidate.trim().removePrefix("@")
            val params = buildJsonObject { put("candidate", clean) }
            val response = supabase.postgrest.rpc("check_username_available", params)
            response.decodeAs<Boolean>()
        }
    }

    suspend fun changeUsername(newUsername: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = newUsername.trim().removePrefix("@")
            val params = buildJsonObject { put("new_username", clean) }
            val response = supabase.postgrest.rpc("change_username", params)
            val obj = response.decodeAs<JsonObject>()
            val success = obj["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            if (!success) {
                val errorMsg = obj["error"]?.jsonPrimitive?.contentOrNull ?: "Failed to change username"
                error(errorMsg)
            }
            obj["username"]?.jsonPrimitive?.contentOrNull
                ?: obj["new_username"]?.jsonPrimitive?.contentOrNull
                ?: clean
        }
    }

    suspend fun markUsernameReviewed(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc("mark_username_reviewed")
            Unit
        }
    }

    suspend fun updateProfileDetails(
        displayName: String,
        bio: String?,
        avatarUrl: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = currentUserId() ?: error("Not authenticated")
            val payload = buildJsonObject {
                put("display_name", displayName.trim())
                if (bio != null) {
                    put("bio", bio.trim())
                } else {
                    put("bio", JsonNull)
                }
                if (avatarUrl != null) {
                    put("avatar_url", avatarUrl)
                }
                put("updated_at", Instant.now().toString())
            }
            supabase.from("profiles").update(payload) {
                filter { eq("id", uid) }
            }
            ensureUserProfile()
        }
    }

    suspend fun getMyActiveNote(): Result<PublicNote?> = withContext(Dispatchers.IO) {
        runCatching {
            val response = supabase.postgrest.rpc("get_my_active_note")
            val raw = runCatching { response.decodeAs<JsonObject>() }.getOrNull() ?: return@runCatching null
            if (raw.isEmpty()) return@runCatching null
            json.decodeFromJsonElement(PublicNote.serializer(), raw)
        }
    }

    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc("delete_my_account")
            supabase.auth.signOut()
            Unit
        }
    }

    suspend fun createNote(content: String): Result<PublicNote> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildJsonObject { put("note_content", content.trim()) }
            val response = supabase.postgrest.rpc("create_note", params)
            response.decodeAs<PublicNote>()
        }
    }

    suspend fun deleteNote(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc("delete_note")
            Unit
        }
    }

    suspend fun getUserFollowers(targetUserId: String): Result<List<FollowUserItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildJsonObject { put("target_user_id", targetUserId) }
            val response = supabase.postgrest.rpc("get_user_followers", params)
            response.decodeList<FollowUserItem>()
        }
    }

    suspend fun getUserFollowing(targetUserId: String): Result<List<FollowUserItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildJsonObject { put("target_user_id", targetUserId) }
            val response = supabase.postgrest.rpc("get_user_following", params)
            response.decodeList<FollowUserItem>()
        }
    }

    suspend fun fetchNotifications(limit: Int = 50, offset: Int = 0): Result<List<NotificationRow>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
            }
            val response = supabase.postgrest.rpc("get_user_notifications", params)
            response.decodeList<NotificationRow>()
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = currentUserId() ?: return@runCatching
            supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) {
                filter {
                    eq("id", notificationId)
                    eq("user_id", uid)
                }
            }
            Unit
        }
    }

    suspend fun markAllNotificationsRead(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = currentUserId() ?: return@runCatching
            supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) {
                filter {
                    eq("user_id", uid)
                    eq("is_read", false)
                }
            }
            Unit
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

    /**
     * Atomically edits a user message, updates edited_at timestamp,
     * and deletes subsequent messages in the same conversation.
     */
    suspend fun editUserMessage(messageId: String, newContent: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val params = buildJsonObject {
                    put("p_message_id", messageId)
                    put("p_new_content", newContent)
                }
                supabase.postgrest.rpc("edit_user_message", params)
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
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot read avatar image")
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: error("Failed to decode avatar bitmap")

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            val rotated = if (!matrix.isIdentity) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else decoded

            val minDim = minOf(rotated.width, rotated.height)
            val cropped = Bitmap.createBitmap(rotated, (rotated.width - minDim) / 2, (rotated.height - minDim) / 2, minDim, minDim)
            val scaled = if (minDim > 512) {
                Bitmap.createScaledBitmap(cropped, 512, 512, true)
            } else cropped

            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val jpegBytes = stream.toByteArray()

            val objectPath = "$uid/avatar_${System.currentTimeMillis()}.jpg"
            val bucket = supabase.storage.from("avatars")
            bucket.upload(objectPath, jpegBytes) {
                upsert = true
            }
            val url = bucket.publicUrl(objectPath) + "?v=${System.currentTimeMillis()}"

            val payload = buildJsonObject {
                put("avatar_url", url)
                put("updated_at", Instant.now().toString())
            }
            supabase.from("profiles").update(payload) {
                filter { eq("id", uid) }
            }
            ensureUserProfile()
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
