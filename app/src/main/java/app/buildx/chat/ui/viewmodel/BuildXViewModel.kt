package app.buildx.chat.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.buildx.chat.R
import app.buildx.chat.data.model.AccountInfo
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import app.buildx.chat.data.model.AppNotificationItem
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.BlockedUser
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.ChatSseEvent
import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.FollowUserItem
import app.buildx.chat.data.model.MainSurfacePane
import app.buildx.chat.data.model.NotificationRow
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.data.model.VerificationStatusInfo
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmConversationMetadata
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmReplyPreview
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.repository.BuildXRepository
import app.buildx.chat.ui.chat.ChatInboxCounters
import app.buildx.chat.ui.chat.ChatMessageMapper
import app.buildx.chat.util.AppForegroundTracker
import app.buildx.chat.util.AuthValidation
import app.buildx.chat.util.AvatarImageProcessor
import app.buildx.chat.util.BuildXSystemNotificationManager
import app.buildx.chat.data.util.ErrorMapper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID

enum class SessionState {
    Loading,
    SignedIn,
    SignedOut
}

enum class DmFilterTab {
    ALL,
    UNREAD,
    GROUPS,
    REQUESTS
}

data class BuildXUiState(
    val isAuthenticated: Boolean = false,
    val sessionState: SessionState = if (isAuthenticated) SessionState.SignedIn else SessionState.Loading,
    val isAuthLoading: Boolean = false,
    val authError: String? = null,
    val profile: UserProfile? = null,
    val isUploadingAvatar: Boolean = false,
    val isUploadingBanner: Boolean = false,
    val appearance: AppAppearance = AppAppearance.AUTOMATIC,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val activeSurfacePane: MainSurfacePane = MainSurfacePane.CHAT,
    val conversations: List<Conversation> = emptyList(),
    val filteredConversations: List<Conversation> = emptyList(),
    val unreadConversationIds: Set<String> = emptySet(),
    val activeConversationId: String? = null,
    val activeConversationTitle: String = "Build X",
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val isIncognito: Boolean = false,
    val mode: ChatMode = ChatMode.THINKING,
    val isModePopupOpen: Boolean = false,
    val composerText: String = "",
    val stagedAttachments: List<StagedAttachment> = emptyList(),
    val recentDeviceImages: List<StagedAttachment> = emptyList(),
    val userUploadedAttachments: List<AttachmentRow> = emptyList(),
    val selectedGalleryUris: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isDrawerOpen: Boolean = false,
    val isOverflowMenuOpen: Boolean = false,
    val isAttachmentSheetOpen: Boolean = false,
    val activeStepsSheetMessage: List<ReasoningStep>? = null,
    val showRenameDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val selectedConversationForActions: Conversation? = null,
    val conversationToRename: Conversation? = null,
    val conversationToDelete: Conversation? = null,
    val unlockedConversationIds: Set<String> = emptySet(),
    val showRecentUploadsPicker: Boolean = false,
    val activeShareLink: String? = null,
    val snackbarMessage: String? = null,
    val isVoiceListening: Boolean = false,
    val voiceAmplitudes: List<Float> = List(28) { 0.15f },
    val voicePartialTranscript: String = "",
    val showNotificationPermissionCard: Boolean = false,
    val notifications: List<AppNotificationItem> = emptyList(),
    // Phase E Profile, Follow, Note, Search, and Realtime Notifications additions
    val viewedPublicProfile: PublicProfile? = null,
    val isSubscribedToNotes: Boolean = false,
    val isPublicProfileLoading: Boolean = false,
    val isPublicProfileRefreshing: Boolean = false,
    val isOwnProfileRefreshing: Boolean = false,
    val publicProfileError: String? = null,
    val activeOwnNote: PublicNote? = null,
    val isNoteSheetOpen: Boolean = false,
    val isCreatingNote: Boolean = false,
    val followingListUsers: List<FollowUserItem> = emptyList(),
    val followersListUsers: List<FollowUserItem> = emptyList(),
    val isFollowListsLoading: Boolean = false,
    val followListsError: String? = null,
    val globalSearchQuery: String = "",
    val searchActiveTab: Int = 0, // 0: People, 1: Chats
    val searchPeopleResults: List<PersonSearchResult> = emptyList(),
    val isSearchPeopleLoading: Boolean = false,
    val searchPeopleError: String? = null,
    val recentSearchesList: List<String> = listOf("mustafa", "sara", "buildx"),
    val inAppNotifications: List<NotificationRow> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val isNotificationsLoading: Boolean = false,
    val showUsernameReviewSheet: Boolean = false,
    // Task A Message Actions additions
    val activeUserMessageMenuId: String? = null,
    val activeAssistantMessageMenuId: String? = null,
    val editingMessageId: String? = null,
    val editingMessageText: String = "",
    val activeSelectTextMessage: String? = null,
    // Verification system
    val verificationStatus: VerificationStatusInfo? = null,
    val isVerificationLoading: Boolean = false,
    val verificationError: String? = null,
    val isVerificationSubmitting: Boolean = false,
    // Profile v2: block/report/about/blocked-accounts
    val isProfileUnavailable: Boolean = false,
    val isFollowPending: Boolean = false,
    val accountInfo: AccountInfo? = null,
    val isAccountInfoLoading: Boolean = false,
    val accountInfoError: String? = null,
    val blockedUsers: List<BlockedUser> = emptyList(),
    val isBlockedUsersLoading: Boolean = false,
    val blockedUsersError: String? = null,
    val hasMoreBlockedUsers: Boolean = false,
    val isSubmittingReport: Boolean = false,
    val reportError: String? = null,
    val isBlocking: Boolean = false,
    // Direct & Group Messaging System (DMs, Group Chats, Notes Tray, Seen Receipts)
    val dmInboxItems: List<DmInboxItem> = emptyList(),
    val dmSearchQuery: String = "",
    val isDmInboxLoading: Boolean = false,
    val totalUnreadDmCount: Int = 0,
    val dmInboxCounters: ChatInboxCounters = ChatInboxCounters(),
    val selectedDmFilterTab: DmFilterTab = DmFilterTab.ALL,
    val dmSearchPeopleResults: List<PersonSearchResult> = emptyList(),
    val isDmSearchPeopleLoading: Boolean = false,
    val inboxNotesTray: List<FeedNoteItem> = emptyList(),
    val activeReplyTargetNote: FeedNoteItem? = null,
    val activeDmConversationId: String? = null,
    val activeDmDetail: DmConversationDetail? = null,
    val dmMessages: List<DmMessage> = emptyList(),
    val dmTypingUserIds: Set<String> = emptySet(),
    val isDmMessagesLoading: Boolean = false,
    val isDmPaginating: Boolean = false,
    val hasMoreDmMessages: Boolean = true,
    val dmComposerText: String = "",
    val dmDraftsByConversation: Map<String, String> = emptyMap(),
    val justSentClientTempIds: Set<String> = emptySet(),
    val lastOptimisticMessageId: String? = null,
    val dmStagedAttachments: List<StagedAttachment> = emptyList(),
    val dmReplyingToMessage: DmMessage? = null,
    val dmEditingMessage: DmMessage? = null,
    val activeDmMessageMenuId: String? = null,
    val forwardingDmMessage: DmMessage? = null,
    val dmMessageReactions: Map<String, Map<String, Int>> = emptyMap(),
    val dmMyMessageReactions: Map<String, String> = emptyMap(),
    val isCreateGroupSheetOpen: Boolean = false,
    val groupCreationSelectedUserIds: Set<String> = emptySet(),
    val groupCreationTitle: String = "",
    val isCreatingGroup: Boolean = false,
    val isGroupAvatarUploading: Boolean = false,
    val isAddingGroupMembers: Boolean = false,
    val defaultChatWallpaper: ChatWallpaperConfig = ChatWallpaperConfig(),
    val activeDmWallpaper: ChatWallpaperConfig = ChatWallpaperConfig(),
    val isWallpaperUpdating: Boolean = false
)

class BuildXViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BuildXRepository(application.applicationContext)

    private fun hasStoredSession(): Boolean {
        return repository.prefs.contains("buildx_auth_session_json") ||
            repository.prefs.contains("supabase_session")
    }

    private fun clearStoredSession() {
        repository.prefs.edit()
            .remove("buildx_auth_session_json")
            .remove("supabase_session")
            .remove("cached_user_profile")
            .apply()
    }

    private fun cacheUserProfile(profile: UserProfile) {
        runCatching {
            val str = repository.json.encodeToString(UserProfile.serializer(), profile)
            repository.prefs.edit().putString("cached_user_profile", str).apply()
        }
    }

    private fun getCachedUserProfile(): UserProfile? {
        val str = repository.prefs.getString("cached_user_profile", null) ?: return null
        return runCatching { repository.json.decodeFromString(UserProfile.serializer(), str) }.getOrNull()
    }

    private val _uiState = MutableStateFlow(
        run {
            val hasSession = repository.prefs.contains("buildx_auth_session_json") ||
                repository.prefs.contains("supabase_session")
            val cachedProfile = runCatching {
                repository.prefs.getString("cached_user_profile", null)?.let {
                    repository.json.decodeFromString(UserProfile.serializer(), it)
                }
            }.getOrNull()
            BuildXUiState(
                isAuthenticated = hasSession,
                sessionState = if (hasSession) SessionState.SignedIn else SessionState.Loading,
                profile = cachedProfile,
                appearance = repository.getSavedAppearance(),
                language = repository.getSavedLanguage()
            )
        }
    )
    val uiState: StateFlow<BuildXUiState> = _uiState.asStateFlow()

    private var activeStreamJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var realtimeNotificationChannel: RealtimeChannel? = null

    init {
        _uiState.update { it.copy(defaultChatWallpaper = repository.getSavedDefaultWallpaper()) }
        viewModelScope.launch {
            repository.supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _uiState.update {
                            it.copy(
                                sessionState = SessionState.SignedIn,
                                isAuthenticated = true,
                                isAuthLoading = false,
                                authError = null
                            )
                        }
                        checkExistingSession()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        if (!hasStoredSession() || status.isSignOut) {
                            if (status.isSignOut) {
                                clearStoredSession()
                            }
                            _uiState.update {
                                it.copy(
                                    sessionState = SessionState.SignedOut,
                                    isAuthenticated = false,
                                    isAuthLoading = false
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isAuthLoading = false) }
                        }
                    }
                    is SessionStatus.RefreshFailure -> {
                        val isNetwork = isNetworkRefreshFailure(status.cause)
                        if (!isNetwork && !hasStoredSession()) {
                            clearStoredSession()
                            _uiState.update {
                                it.copy(
                                    sessionState = SessionState.SignedOut,
                                    isAuthenticated = false,
                                    isAuthLoading = false
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isAuthLoading = false) }
                        }
                    }
                    is SessionStatus.Initializing -> {}
                }
            }
        }
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            repository.awaitAuthInitialization()
            val uid = repository.currentUserId()
            if (uid != null) {
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.SignedIn,
                        isAuthenticated = true,
                        isAuthLoading = false
                    )
                }
                val profileResult = runCatching { repository.ensureUserProfile() }
                if (profileResult.isSuccess) {
                    val profile = profileResult.getOrThrow()
                    cacheUserProfile(profile)
                    val resolvedAppearance = AppAppearance.fromDb(profile.appearance)
                    repository.saveAppearanceLocal(resolvedAppearance)
                    val resolvedDefaultWallpaper = if (!profile.defaultWallpaper.isDefault) {
                        repository.saveDefaultWallpaperLocal(profile.defaultWallpaper)
                        profile.defaultWallpaper
                    } else {
                        repository.getSavedDefaultWallpaper()
                    }
                    val showReview = profile.usernameReviewedAt == null
                    _uiState.update {
                        it.copy(
                            sessionState = SessionState.SignedIn,
                            isAuthenticated = true,
                            isAuthLoading = false,
                            authError = null,
                            profile = profile,
                            appearance = resolvedAppearance,
                            defaultChatWallpaper = resolvedDefaultWallpaper,
                            showUsernameReviewSheet = showReview
                        )
                    }
                    loadActiveOwnNote()
                    refreshConversationsAndAttachments()
                    loadInAppNotifications()
                    subscribeToRealtimeNotifications(uid)
                    loadDmInbox()
                    loadInboxNotesTray()
                    subscribeToRealtimeInbox(uid)
                } else {
                    val cached = getCachedUserProfile()
                    if (cached != null) {
                        val resolvedAppearance = AppAppearance.fromDb(cached.appearance)
                        _uiState.update {
                            it.copy(
                                sessionState = SessionState.SignedIn,
                                isAuthenticated = true,
                                isAuthLoading = false,
                                profile = cached,
                                appearance = resolvedAppearance
                            )
                        }
                    }
                    refreshConversationsAndAttachments()
                }
            } else {
                if (!hasStoredSession()) {
                    _uiState.update {
                        it.copy(
                            sessionState = SessionState.SignedOut,
                            isAuthenticated = false,
                            isAuthLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun subscribeToRealtimeNotifications(uid: String) {
        viewModelScope.launch {
            try {
                realtimeNotificationChannel?.unsubscribe()
                val channel = repository.supabase.channel("user-notifications-$uid")
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "notifications"
                    filter("user_id", FilterOperator.EQ, uid)
                }
                realtimeNotificationChannel = channel
                channel.subscribe()
                changeFlow.collect { action ->
                    loadInAppNotifications()
                    if (action is PostgresAction.Insert) {
                        dispatchSocialNotification(action.record)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun dispatchIncomingDmNotification(record: JsonObject) {
        val conversationId = record["conversation_id"]?.jsonPrimitive?.contentOrNull ?: return
        val senderId = record["sender_id"]?.jsonPrimitive?.contentOrNull ?: return
        val currentUserId = repository.currentUserId()
        if (!currentUserId.isNullOrBlank() && senderId == currentUserId) return

        val state = _uiState.value
        val inboxItem = state.dmInboxItems.firstOrNull { it.effectiveId == conversationId }
        val detail = state.activeDmDetail?.takeIf { it.conversation?.id == conversationId }
        val isGroup = inboxItem?.kind == "group" || detail?.conversation?.kind == "group"
        val conversationTitle = inboxItem?.effectiveTitle
            ?: detail?.conversation?.title?.takeIf { it.isNotBlank() }
            ?: "Build X"
        val isMuted = inboxItem?.isMuted == true ||
            (inboxItem?.mutedUntil?.let { runCatching { Instant.parse(it).isAfter(Instant.now()) }.getOrDefault(false) } == true)
        val isVisible = state.activeDmConversationId == conversationId &&
            AppForegroundTracker.isViewing("dm/")

        val messageType = record["message_type"]?.jsonPrimitive?.contentOrNull ?: "text"
        val content = record["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val preview = when {
            record["deleted_at"] != null && record["deleted_at"]?.jsonPrimitive?.contentOrNull != null ->
                getApplication<Application>().getString(R.string.notif_message_deleted)
            content.isNotBlank() -> content
            messageType == "image" -> getApplication<Application>().getString(R.string.notif_message_photo)
            messageType == "voice" || messageType == "audio" ->
                getApplication<Application>().getString(R.string.notif_message_voice)
            messageType == "file" || messageType == "document" ->
                getApplication<Application>().getString(R.string.notif_message_file)
            else -> getApplication<Application>().getString(R.string.notif_message_generic)
        }

        viewModelScope.launch {
            var senderName = ""
            var senderAvatar: String? = null
            if (isGroup) {
                val participant = detail?.participants?.firstOrNull { it.userId == senderId }
                senderName = participant?.displayName
                    ?: participant?.username
                    ?: repository.getPublicProfile(senderId).getOrNull()?.let { it.displayName.ifBlank { it.username } }
                    ?: ""
                senderAvatar = participant?.avatarUrl
                    ?: repository.getPublicProfile(senderId).getOrNull()?.avatarUrl
            } else {
                senderName = inboxItem?.effectiveTitle ?: conversationTitle
                senderAvatar = inboxItem?.effectiveAvatarUrl
            }

            BuildXSystemNotificationManager.notifyConversationMessage(
                context = getApplication(),
                conversationId = conversationId,
                conversationTitle = conversationTitle,
                isGroup = isGroup,
                senderId = senderId,
                senderName = senderName,
                senderAvatarUrl = senderAvatar,
                messagePreview = preview,
                currentUserId = currentUserId,
                isMuted = isMuted,
                isConversationVisible = isVisible
            )
        }
    }

    private fun dispatchSocialNotification(record: JsonObject) {
        val type = record["type"]?.jsonPrimitive?.contentOrNull ?: return
        if (type !in setOf("follow", "note", "note_like")) return
        val actorId = record["actor_id"]?.jsonPrimitive?.contentOrNull ?: return
        val body = record["body"]?.jsonPrimitive?.contentOrNull
        viewModelScope.launch {
            val actor = repository.getPublicProfile(actorId).getOrNull()
            val actorName = actor?.displayName?.takeIf { it.isNotBlank() }
                ?: actor?.username?.takeIf { it.isNotBlank() }
                ?: return@launch
            BuildXSystemNotificationManager.notifySocialActivity(
                context = getApplication(),
                type = type,
                actorName = actorName,
                actorUsername = actor?.username,
                actorAvatarUrl = actor?.avatarUrl,
                preview = body ?: actor?.activeNote?.content,
                currentUserId = repository.currentUserId()
            )
        }
    }

    fun loadActiveOwnNote() {
        viewModelScope.launch {
            repository.getMyActiveNote().onSuccess { note ->
                _uiState.update { it.copy(activeOwnNote = note) }
            }
        }
    }

    fun signIn(email: String, password: String) {
        signInWithIdentifier(email, password)
    }

    fun signInWithIdentifier(identifier: String, password: String) {
        val isArabic = _uiState.value.language == AppLanguage.ARABIC
        if (identifier.isBlank() || password.isBlank()) {
            val msg = if (isArabic) "يرجى إدخال اسم المستخدم أو البريد الإلكتروني وكلمة المرور."
            else "Please enter your username or email and password."
            _uiState.update { it.copy(authError = msg) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = repository.signInWithIdentifier(identifier, password)
            result.onSuccess { profile ->
                cacheUserProfile(profile)
                val resolvedAppearance = AppAppearance.fromDb(profile.appearance)
                repository.saveAppearanceLocal(resolvedAppearance)
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.SignedIn,
                        isAuthenticated = true,
                        isAuthLoading = false,
                        authError = null,
                        profile = profile,
                        appearance = resolvedAppearance
                    )
                }
                refreshConversationsAndAttachments()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = ErrorMapper.map(err, isArabic)
                    )
                }
            }
        }
    }

    fun signUp(email: String, password: String, displayName: String, handle: String) {
        val isArabic = _uiState.value.language == AppLanguage.ARABIC
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = repository.signUpWithEmail(email, password, displayName, handle)
            result.onSuccess { profile ->
                cacheUserProfile(profile)
                val resolvedAppearance = AppAppearance.fromDb(profile.appearance)
                repository.saveAppearanceLocal(resolvedAppearance)
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.SignedIn,
                        isAuthenticated = true,
                        isAuthLoading = false,
                        authError = null,
                        profile = profile,
                        appearance = resolvedAppearance
                    )
                }
                refreshConversationsAndAttachments()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = ErrorMapper.map(err, isArabic)
                    )
                }
            }
        }
    }

    private var isWaitingForAuthCallback = false

    fun signInWithGoogle() {
        val isArabic = _uiState.value.language == AppLanguage.ARABIC
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            isWaitingForAuthCallback = true
            val result = repository.signInWithGoogle()
            result.onFailure { err ->
                isWaitingForAuthCallback = false
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = ErrorMapper.map(err, isArabic)
                    )
                }
            }
        }
    }

    fun onActivityResumed() {
        if (isWaitingForAuthCallback) {
            viewModelScope.launch {
                delay(600L)
                if (isWaitingForAuthCallback && _uiState.value.isAuthLoading && !_uiState.value.isAuthenticated) {
                    isWaitingForAuthCallback = false
                    _uiState.update { it.copy(isAuthLoading = false) }
                }
            }
        }
    }

    fun loadVerificationStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerificationLoading = true, verificationError = null) }
            val res = repository.getMyVerificationStatus()
            res.onSuccess { info ->
                _uiState.update { it.copy(verificationStatus = info, isVerificationLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isVerificationLoading = false, verificationError = mapError(err)) }
            }
        }
    }

    fun submitVerificationRequest(
        fullName: String,
        category: String,
        links: String,
        reason: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerificationSubmitting = true, verificationError = null) }
            val res = repository.submitVerificationRequest(fullName, category, links, reason)
            res.onSuccess {
                _uiState.update { it.copy(isVerificationSubmitting = false) }
                loadVerificationStatus()
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isVerificationSubmitting = false, verificationError = mapError(err)) }
            }
        }
    }

    fun withdrawVerificationRequest(requestId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerificationSubmitting = true, verificationError = null) }
            val res = repository.withdrawVerificationRequest(requestId)
            res.onSuccess {
                _uiState.update { it.copy(isVerificationSubmitting = false) }
                loadVerificationStatus()
            }.onFailure { err ->
                _uiState.update { it.copy(isVerificationSubmitting = false, verificationError = mapError(err)) }
            }
        }
    }

    fun handleAuthDeeplink(intent: Intent) {
        viewModelScope.launch {
            val uri = intent.data ?: return@launch
            if (uri.scheme != "app.buildx.chat" || uri.host != "login-callback") {
                return@launch
            }
            isWaitingForAuthCallback = false

            val queryParams = buildMap {
                uri.query?.split("&")?.forEach { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.isNotEmpty()) put(parts[0], if (parts.size > 1) runCatching { java.net.URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault(parts[1]) else "")
                }
            }
            val fragmentParams = buildMap {
                uri.fragment?.split("&")?.forEach { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.isNotEmpty()) put(parts[0], if (parts.size > 1) runCatching { java.net.URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault(parts[1]) else "")
                }
            }

            val error = queryParams["error"] ?: fragmentParams["error"]
            val errorCode = queryParams["error_code"] ?: fragmentParams["error_code"]
            val errorDescription = queryParams["error_description"] ?: fragmentParams["error_description"]

            val isCancellation = AuthValidation.isAuthCancellation(error, errorCode, errorDescription)

            if (isCancellation) {
                // Cancellation returns quietly to auth screen without error alerts
                _uiState.update { it.copy(isAuthLoading = false, authError = null) }
                return@launch
            }

            if (!errorDescription.isNullOrBlank() || !error.isNullOrBlank()) {
                val isArabic = _uiState.value.language == AppLanguage.ARABIC
                val mappedError = ErrorMapper.mapMessage(errorDescription ?: error, isArabic)
                _uiState.update { it.copy(isAuthLoading = false, authError = mappedError) }
                return@launch
            }

            runCatching {
                repository.supabase.handleDeeplinks(intent) {
                    checkExistingSession()
                }
            }.onFailure { err ->
                val isArabic = _uiState.value.language == AppLanguage.ARABIC
                val mappedError = ErrorMapper.map(err, isArabic)
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = mappedError
                    )
                }
            }
        }
    }

    fun signOut() {
        activeStreamJob?.cancel()
        clearStoredSession()
        _uiState.value = BuildXUiState(
            isAuthenticated = false,
            sessionState = SessionState.SignedOut,
            appearance = repository.getSavedAppearance(),
            language = repository.getSavedLanguage()
        )
        viewModelScope.launch {
            try {
                realtimeNotificationChannel?.unsubscribe()
                dmInboxChannel?.unsubscribe()
                dmRoomChannel?.unsubscribe()
            } catch (_: Exception) {}
            realtimeNotificationChannel = null
            dmInboxChannel = null
            dmRoomChannel = null
            repository.signOut()
        }
    }

    fun refreshConversationsAndAttachments() {
        viewModelScope.launch {
            val convs = repository.fetchConversations().getOrDefault(emptyList())
            val unreadIds = repository.fetchUnreadConversationIds(convs)
            val uploaded = repository.fetchUserUploadedAttachments().getOrDefault(emptyList())
            _uiState.update { state ->
                val activeConv = convs.firstOrNull { it.id == state.activeConversationId }
                state.copy(
                    conversations = convs,
                    filteredConversations = if (state.searchQuery.isBlank()) convs else state.filteredConversations,
                    unreadConversationIds = unreadIds - setOfNotNull(state.activeConversationId),
                    activeConversationTitle = activeConv?.cleanTitle ?: "Build X",
                    userUploadedAttachments = uploaded
                )
            }
        }
    }

    fun onComposerTextChange(newText: String) {
        _uiState.update { it.copy(composerText = newText) }
    }

    fun toggleMode() {
        _uiState.update { state ->
            val nextMode = if (state.mode == ChatMode.THINKING) ChatMode.INSTANT else ChatMode.THINKING
            state.copy(mode = nextMode, isModePopupOpen = false)
        }
    }

    fun toggleModePopup(open: Boolean? = null) {
        _uiState.update { state ->
            val nextOpen = open ?: !state.isModePopupOpen
            state.copy(
                isModePopupOpen = nextOpen,
                isOverflowMenuOpen = false
            )
        }
    }

    fun selectMode(mode: ChatMode) {
        _uiState.update { state ->
            state.copy(
                mode = mode,
                isModePopupOpen = false
            )
        }
    }

    fun setDrawerOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isDrawerOpen = open,
                isOverflowMenuOpen = false,
                isModePopupOpen = false
            )
        }
        if (open) {
            refreshConversationsAndAttachments()
        }
    }

    fun openSurfacePane(pane: MainSurfacePane) {
        _uiState.update {
            it.copy(
                activeSurfacePane = pane,
                isDrawerOpen = false,
                isOverflowMenuOpen = false,
                isModePopupOpen = false
            )
        }
    }

    fun startNewChat(incognito: Boolean = false) {
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                activeSurfacePane = MainSurfacePane.CHAT,
                activeConversationId = null,
                activeConversationTitle = "Build X",
                messages = emptyList(),
                isStreaming = false,
                isIncognito = incognito,
                composerText = "",
                stagedAttachments = emptyList(),
                isDrawerOpen = false,
                isOverflowMenuOpen = false,
                isModePopupOpen = false
            )
        }
    }

    fun selectConversation(conversationId: String) {
        activeStreamJob?.cancel()
        viewModelScope.launch {
            val conv = _uiState.value.conversations.firstOrNull { it.id == conversationId }
            val msgs = repository.fetchMessages(conversationId).getOrDefault(emptyList())
            repository.markConversationRead(conversationId)
            _uiState.update { state ->
                state.copy(
                    activeSurfacePane = MainSurfacePane.CHAT,
                    activeConversationId = conversationId,
                    activeConversationTitle = conv?.cleanTitle ?: "Build X",
                    messages = msgs,
                    isStreaming = false,
                    isIncognito = false,
                    isDrawerOpen = false,
                    isOverflowMenuOpen = false,
                    isModePopupOpen = false,
                    unreadConversationIds = state.unreadConversationIds - conversationId
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(filteredConversations = it.conversations) }
                return@launch
            }
            delay(250L)
            val results = repository.searchConversations(query).getOrDefault(emptyList())
            _uiState.update { it.copy(filteredConversations = results) }
        }
    }

    /**
     * Sends the current user message (and uploads any staged attachments first) to /functions/v1/chat.
     * Handles real-time SSE streaming of reasoning_step, text_delta, title, and done events.
     */
    fun sendMessage() {
        val state = _uiState.value
        val text = state.composerText.trim()
        val attachmentsToUpload = state.stagedAttachments
        if (text.isEmpty() && attachmentsToUpload.isEmpty()) return
        if (state.isStreaming) return

        val promptText = if (text.isEmpty() && attachmentsToUpload.isNotEmpty()) {
            "Attached: ${attachmentsToUpload.joinToString(", ") { it.fileName }}"
        } else {
            text
        }

        val nowIso = Instant.now().toString()
        val tempUserMsgId = UUID.randomUUID().toString()
        val tempAssistantMsgId = UUID.randomUUID().toString()
        val currentConvId = state.activeConversationId
        val isIncognito = state.isIncognito
        val selectedMode = state.mode
        val historyForIncognito = state.messages

        val userMsg = ChatMessage(
            id = tempUserMsgId,
            conversationId = currentConvId ?: "pending",
            role = "user",
            content = promptText,
            status = "done",
            createdAt = nowIso
        )

        val pendingAssistantMsg = ChatMessage(
            id = tempAssistantMsgId,
            conversationId = currentConvId ?: "pending",
            role = "assistant",
            content = "",
            reasoningSteps = emptyList(),
            status = "streaming",
            createdAt = nowIso
        )

        _uiState.update {
            it.copy(
                composerText = "",
                stagedAttachments = emptyList(),
                messages = it.messages + userMsg + pendingAssistantMsg,
                isStreaming = true,
                isOverflowMenuOpen = false,
                isModePopupOpen = false
            )
        }

        activeStreamJob = viewModelScope.launch {
            try {
                // Upload any staged local attachments if not in incognito mode
                val uploadedRows = mutableListOf<AttachmentRow>()
                if (!isIncognito && attachmentsToUpload.isNotEmpty()) {
                    for (staged in attachmentsToUpload) {
                        val existingRow = staged.uploadedRow
                        if (existingRow != null) {
                            uploadedRows.add(existingRow)
                        } else {
                            repository.uploadAttachment(staged).onSuccess { row ->
                                uploadedRows.add(row)
                            }
                        }
                    }
                }

                var resolvedConvId: String? = currentConvId

                repository.streamChatCompletion(
                    conversationId = currentConvId,
                    userText = promptText,
                    mode = selectedMode,
                    incognito = isIncognito,
                    attachments = uploadedRows,
                    historyMessages = historyForIncognito
                ).collect { event ->
                    when (event) {
                        is ChatSseEvent.ReasoningStepEvent -> {
                            if (event.conversationId != null) {
                                resolvedConvId = event.conversationId
                            }
                            _uiState.update { s ->
                                val updatedMsgs = s.messages.map { msg ->
                                    if (msg.id == tempAssistantMsgId) {
                                        val existingSteps = msg.reasoningSteps.toMutableList()
                                        val idx = existingSteps.indexOfFirst { it.step == event.step.step }
                                        if (idx >= 0) {
                                            existingSteps[idx] = event.step
                                        } else {
                                            existingSteps.add(event.step)
                                        }
                                        msg.copy(
                                            conversationId = resolvedConvId ?: msg.conversationId,
                                            reasoningSteps = existingSteps
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                s.copy(
                                    activeConversationId = resolvedConvId ?: s.activeConversationId,
                                    messages = updatedMsgs
                                )
                            }
                        }

                        is ChatSseEvent.TextDeltaEvent -> {
                            if (event.conversationId != null) {
                                resolvedConvId = event.conversationId
                            }
                            _uiState.update { s ->
                                val updatedMsgs = s.messages.map { msg ->
                                    if (msg.id == tempAssistantMsgId) {
                                        msg.copy(
                                            conversationId = resolvedConvId ?: msg.conversationId,
                                            content = msg.content + event.text
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                s.copy(
                                    activeConversationId = resolvedConvId ?: s.activeConversationId,
                                    messages = updatedMsgs
                                )
                            }
                        }

                        is ChatSseEvent.TitleEvent -> {
                            val cleanTitle = event.title
                                .removePrefix("محادثة:")
                                .removePrefix("محادثة :")
                                .trim()
                                .ifEmpty { event.title }
                            _uiState.update { s ->
                                s.copy(
                                    activeConversationId = event.conversationId ?: s.activeConversationId,
                                    activeConversationTitle = cleanTitle
                                )
                            }
                        }

                        is ChatSseEvent.DoneEvent -> {
                            val finalConvId = event.conversationId ?: resolvedConvId
                            val finalMsgId = event.messageId ?: tempAssistantMsgId
                            _uiState.update { s ->
                                val updatedMsgs = s.messages.map { msg ->
                                    if (msg.id == tempAssistantMsgId) {
                                        msg.copy(
                                            id = finalMsgId,
                                            conversationId = finalConvId ?: msg.conversationId,
                                            status = event.status
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                s.copy(
                                    activeConversationId = finalConvId ?: s.activeConversationId,
                                    messages = updatedMsgs,
                                    isStreaming = false
                                )
                            }
                            if (!isIncognito && finalConvId != null) {
                                val viewingThisConversation =
                                    _uiState.value.activeConversationId == finalConvId &&
                                        !_uiState.value.isDrawerOpen &&
                                        _uiState.value.activeSurfacePane == MainSurfacePane.CHAT
                                if (viewingThisConversation) {
                                    repository.markConversationRead(finalConvId)
                                } else {
                                    _uiState.update { s ->
                                        s.copy(
                                            notifications = listOf(
                                                AppNotificationItem(
                                                    id = UUID.randomUUID().toString(),
                                                    conversationId = finalConvId,
                                                    title = "Response ready",
                                                    body = "Build X finished replying to your conversation.",
                                                    timestampMillis = System.currentTimeMillis()
                                                )
                                            ) + s.notifications
                                        )
                                    }
                                    val replyPreview = _uiState.value.messages
                                        .lastOrNull { it.role == "assistant" && it.conversationId == finalConvId }
                                        ?.content
                                        .orEmpty()
                                    viewModelScope.launch {
                                        BuildXSystemNotificationManager.notifyAssistantReply(
                                            context = getApplication(),
                                            conversationId = finalConvId,
                                            preview = replyPreview,
                                            isConversationVisible = AppForegroundTracker.isViewing("chat")
                                        )
                                    }
                                }
                                refreshConversationsAndAttachments()
                            }
                        }

                        is ChatSseEvent.ErrorEvent -> {
                            val safeError = mapErrorMessage(event.message)
                            _uiState.update { s ->
                                val updatedMsgs = s.messages.map { msg ->
                                    if (msg.id == tempAssistantMsgId) {
                                        msg.copy(
                                            content = if (msg.content.isBlank()) safeError else msg.content,
                                            status = "error"
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                s.copy(
                                    messages = updatedMsgs,
                                    isStreaming = false,
                                    snackbarMessage = safeError
                                )
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                _uiState.update { s ->
                    val updatedMsgs = s.messages.map { msg ->
                        if (msg.id == tempAssistantMsgId || msg.status == "streaming") {
                            msg.copy(status = "stopped")
                        } else {
                            msg
                        }
                    }
                    s.copy(
                        messages = updatedMsgs,
                        isStreaming = false
                    )
                }
                throw ce
            } catch (e: Exception) {
                val safeError = mapError(e)
                _uiState.update { s ->
                    val updatedMsgs = s.messages.map { msg ->
                        if (msg.id == tempAssistantMsgId) {
                            msg.copy(
                                content = if (msg.content.isBlank()) safeError else msg.content,
                                status = "error"
                            )
                        } else {
                            msg
                        }
                    }
                    s.copy(
                        messages = updatedMsgs,
                        isStreaming = false,
                        snackbarMessage = safeError
                    )
                }
            }
        }
    }

    /**
     * Cancels the active HTTP SSE stream and marks the streaming message as "stopped".
     */
    fun stopGeneration() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        _uiState.update { s ->
            val updatedMsgs = s.messages.map { msg ->
                if (msg.status == "streaming") {
                    msg.copy(status = "stopped")
                } else {
                    msg
                }
            }
            s.copy(
                messages = updatedMsgs,
                isStreaming = false
            )
        }
        val convId = _uiState.value.activeConversationId
        if (convId != null && !_uiState.value.isIncognito) {
            viewModelScope.launch {
                refreshConversationsAndAttachments()
            }
        }
    }

    fun rateMessage(messageId: String, rating: Int) {
        val currentMsg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val newRating = if (currentMsg.rating == rating) null else rating
        _uiState.update { s ->
            s.copy(
                messages = s.messages.map {
                    if (it.id == messageId) it.copy(rating = newRating) else it
                },
                snackbarMessage = when (newRating) {
                    1, -1 -> "Feedback saved"
                    else -> null
                }
            )
        }
        if (!_uiState.value.isIncognito) {
            viewModelScope.launch {
                repository.rateMessage(messageId, newRating)
            }
        }
    }

    // Task A: Message Actions state & handlers
    fun toggleUserMessageMenu(messageId: String?) {
        _uiState.update {
            it.copy(
                activeUserMessageMenuId = if (it.activeUserMessageMenuId == messageId) null else messageId,
                activeAssistantMessageMenuId = null
            )
        }
    }

    fun toggleAssistantMessageMenu(messageId: String?) {
        _uiState.update {
            it.copy(
                activeAssistantMessageMenuId = if (it.activeAssistantMessageMenuId == messageId) null else messageId,
                activeUserMessageMenuId = null
            )
        }
    }

    fun dismissAllMessageMenus() {
        if (_uiState.value.activeUserMessageMenuId != null || _uiState.value.activeAssistantMessageMenuId != null) {
            _uiState.update {
                it.copy(activeUserMessageMenuId = null, activeAssistantMessageMenuId = null)
            }
        }
    }

    fun startEditingMessage(messageId: String) {
        val msg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        _uiState.update {
            it.copy(
                editingMessageId = messageId,
                editingMessageText = msg.content,
                activeUserMessageMenuId = null,
                activeAssistantMessageMenuId = null
            )
        }
    }

    fun cancelEditingMessage() {
        _uiState.update {
            it.copy(editingMessageId = null, editingMessageText = "")
        }
    }

    fun sendEditedMessage(messageId: String, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val msgIndex = state.messages.indexOfFirst { it.id == messageId }
        if (msgIndex < 0) return

        val originalMsg = state.messages[msgIndex]
        if (trimmed == originalMsg.content.trim()) {
            cancelEditingMessage()
            return
        }

        val updatedMsg = originalMsg.copy(
            content = trimmed,
            editedAt = Instant.now().toString()
        )

        // Keep messages up to edited message, discard all messages after it
        val truncatedList = state.messages.subList(0, msgIndex).toMutableList().apply {
            add(updatedMsg)
        }

        val tempAssistantMsgId = UUID.randomUUID().toString()
        val pendingAssistantMsg = ChatMessage(
            id = tempAssistantMsgId,
            conversationId = state.activeConversationId ?: "pending",
            role = "assistant",
            content = "",
            reasoningSteps = emptyList(),
            status = "streaming",
            createdAt = Instant.now().toString()
        )

        _uiState.update {
            it.copy(
                editingMessageId = null,
                editingMessageText = "",
                activeUserMessageMenuId = null,
                messages = truncatedList + pendingAssistantMsg,
                isStreaming = true
            )
        }

        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            try {
                if (!state.isIncognito && state.activeConversationId != null && messageId.isNotBlank()) {
                    repository.editUserMessage(messageId, trimmed).onFailure { err ->
                        _uiState.update { it.copy(messages = state.messages, isStreaming = false) }
                        showSnackbar(mapError(err))
                        return@launch
                    }
                }

                var resolvedConvId: String? = state.activeConversationId
                repository.streamChatCompletion(
                    conversationId = state.activeConversationId,
                    userText = trimmed,
                    mode = state.mode,
                    incognito = state.isIncognito,
                    attachments = emptyList(),
                    historyMessages = truncatedList.dropLast(1)
                ).collect { event ->
                    when (event) {
                        is ChatSseEvent.ReasoningStepEvent -> {
                            if (event.conversationId != null) resolvedConvId = event.conversationId
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantMsgId) {
                                        val steps = m.reasoningSteps.toMutableList()
                                        val idx = steps.indexOfFirst { it.step == event.step.step }
                                        if (idx >= 0) steps[idx] = event.step else steps.add(event.step)
                                        m.copy(conversationId = resolvedConvId ?: m.conversationId, reasoningSteps = steps)
                                    } else m
                                }
                                s.copy(activeConversationId = resolvedConvId ?: s.activeConversationId, messages = updated)
                            }
                        }
                        is ChatSseEvent.TextDeltaEvent -> {
                            if (event.conversationId != null) resolvedConvId = event.conversationId
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantMsgId) {
                                        m.copy(conversationId = resolvedConvId ?: m.conversationId, content = m.content + event.text)
                                    } else m
                                }
                                s.copy(activeConversationId = resolvedConvId ?: s.activeConversationId, messages = updated)
                            }
                        }
                        is ChatSseEvent.DoneEvent -> {
                            val finalConvId = event.conversationId ?: resolvedConvId
                            val finalMsgId = event.messageId ?: tempAssistantMsgId
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantMsgId) {
                                        m.copy(id = finalMsgId, conversationId = finalConvId ?: m.conversationId, status = event.status)
                                    } else m
                                }
                                s.copy(isStreaming = false, activeConversationId = finalConvId ?: s.activeConversationId, messages = updated)
                            }
                        }
                        is ChatSseEvent.ErrorEvent -> {
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantMsgId) m.copy(status = "error") else m
                                }
                                s.copy(isStreaming = false, messages = updated, snackbarMessage = mapErrorMessage(event.message))
                            }
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                _uiState.update { s ->
                    val updated = s.messages.map { m ->
                        if (m.id == tempAssistantMsgId) m.copy(status = "error") else m
                    }
                    s.copy(isStreaming = false, messages = updated, snackbarMessage = mapError(e))
                }
            }
        }
    }

    fun resendUserMessage(message: ChatMessage) {
        _uiState.update { it.copy(activeUserMessageMenuId = null) }
        val state = _uiState.value
        val msgIndex = state.messages.indexOfFirst { it.id == message.id }
        if (msgIndex < 0) return

        val isFailureRetry = message.status == "error" || (msgIndex + 1 < state.messages.size && state.messages[msgIndex + 1].status == "error")

        if (isFailureRetry) {
            val keepList = if (msgIndex + 1 < state.messages.size && state.messages[msgIndex + 1].role == "assistant") {
                state.messages.toMutableList().apply { removeAt(msgIndex + 1) }
            } else {
                state.messages.toMutableList()
            }
            val tempAssistantMsgId = UUID.randomUUID().toString()
            val pendingAssistantMsg = ChatMessage(
                id = tempAssistantMsgId,
                conversationId = state.activeConversationId ?: "pending",
                role = "assistant",
                content = "",
                reasoningSteps = emptyList(),
                status = "streaming",
                createdAt = Instant.now().toString()
            )
            _uiState.update {
                it.copy(messages = keepList + pendingAssistantMsg, isStreaming = true)
            }
            streamFollowUp(message.content, tempAssistantMsgId, keepList.filter { it.content.isNotBlank() })
        } else {
            _uiState.update { it.copy(composerText = message.content) }
            sendMessage()
        }
    }

    fun regenerateLastAssistantMessage(assistantMessage: ChatMessage) {
        _uiState.update { it.copy(activeAssistantMessageMenuId = null) }
        val state = _uiState.value
        val idx = state.messages.indexOfFirst { it.id == assistantMessage.id }
        if (idx < 0) return

        val userMsg = state.messages.subList(0, idx).lastOrNull { it.role == "user" } ?: return
        val oldAnswer = assistantMessage.content
        val oldSteps = assistantMessage.reasoningSteps

        _uiState.update { s ->
            s.copy(
                isStreaming = true,
                messages = s.messages.map {
                    if (it.id == assistantMessage.id) it.copy(rating = null) else it
                }
            )
        }

        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            var newAnswer = ""
            val newSteps = mutableListOf<ReasoningStep>()
            var streamFailed = false

            try {
                repository.streamChatCompletion(
                    conversationId = state.activeConversationId,
                    userText = userMsg.content,
                    mode = state.mode,
                    incognito = state.isIncognito,
                    attachments = emptyList(),
                    historyMessages = state.messages.subList(0, idx)
                ).collect { event ->
                    when (event) {
                        is ChatSseEvent.ReasoningStepEvent -> {
                            val stepIdx = newSteps.indexOfFirst { it.step == event.step.step }
                            if (stepIdx >= 0) newSteps[stepIdx] = event.step else newSteps.add(event.step)
                        }
                        is ChatSseEvent.TextDeltaEvent -> {
                            newAnswer += event.text
                        }
                        is ChatSseEvent.DoneEvent -> {
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == assistantMessage.id) {
                                        m.copy(
                                            content = if (newAnswer.isNotEmpty()) newAnswer else oldAnswer,
                                            reasoningSteps = if (newSteps.isNotEmpty()) newSteps else oldSteps,
                                            status = "done",
                                            rating = null
                                        )
                                    } else m
                                }
                                s.copy(isStreaming = false, messages = updated)
                            }
                            if (!state.isIncognito && assistantMessage.id.isNotBlank()) {
                                repository.rateMessage(assistantMessage.id, null)
                            }
                        }
                        is ChatSseEvent.ErrorEvent -> {
                            streamFailed = true
                            _uiState.update { s ->
                                s.copy(isStreaming = false, snackbarMessage = mapErrorMessage(event.message))
                            }
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                streamFailed = true
                _uiState.update { s ->
                    s.copy(isStreaming = false, snackbarMessage = mapError(e))
                }
            }
            if (streamFailed) {
                _uiState.update { s ->
                    val updated = s.messages.map { m ->
                        if (m.id == assistantMessage.id) {
                            m.copy(content = oldAnswer, reasoningSteps = oldSteps, status = "done")
                        } else m
                    }
                    s.copy(isStreaming = false, messages = updated)
                }
            }
        }
    }

    private fun streamFollowUp(userText: String, tempAssistantId: String, history: List<ChatMessage>) {
        val state = _uiState.value
        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            try {
                repository.streamChatCompletion(
                    conversationId = state.activeConversationId,
                    userText = userText,
                    mode = state.mode,
                    incognito = state.isIncognito,
                    attachments = emptyList(),
                    historyMessages = history
                ).collect { event ->
                    when (event) {
                        is ChatSseEvent.ReasoningStepEvent -> {
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantId) {
                                        val steps = m.reasoningSteps.toMutableList()
                                        val idx = steps.indexOfFirst { it.step == event.step.step }
                                        if (idx >= 0) steps[idx] = event.step else steps.add(event.step)
                                        m.copy(reasoningSteps = steps)
                                    } else m
                                }
                                s.copy(messages = updated)
                            }
                        }
                        is ChatSseEvent.TextDeltaEvent -> {
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantId) m.copy(content = m.content + event.text) else m
                                }
                                s.copy(messages = updated)
                            }
                        }
                        is ChatSseEvent.DoneEvent -> {
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantId) m.copy(id = event.messageId ?: m.id, status = "done") else m
                                }
                                s.copy(isStreaming = false, messages = updated)
                            }
                        }
                        is ChatSseEvent.ErrorEvent -> {
                            _uiState.update { s ->
                                val updated = s.messages.map { m ->
                                    if (m.id == tempAssistantId) m.copy(status = "error") else m
                                }
                                s.copy(isStreaming = false, messages = updated, snackbarMessage = mapErrorMessage(event.message))
                            }
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                _uiState.update { s ->
                    val updated = s.messages.map { m ->
                        if (m.id == tempAssistantId) m.copy(status = "error") else m
                    }
                    s.copy(isStreaming = false, messages = updated, snackbarMessage = mapError(e))
                }
            }
        }
    }

    fun openSelectText(text: String) {
        _uiState.update {
            it.copy(
                activeSelectTextMessage = text,
                activeUserMessageMenuId = null,
                activeAssistantMessageMenuId = null
            )
        }
    }

    fun closeSelectText() {
        _uiState.update { it.copy(activeSelectTextMessage = null) }
    }

    fun openStepsSheet(steps: List<ReasoningStep>?) {
        _uiState.update { it.copy(activeStepsSheetMessage = steps) }
    }

    fun toggleOverflowMenu(open: Boolean) {
        _uiState.update { it.copy(isOverflowMenuOpen = open, isModePopupOpen = false) }
    }

    fun openConversationActions(conversation: Conversation?) {
        _uiState.update { it.copy(selectedConversationForActions = conversation) }
    }

    fun togglePinConversation(conversation: Conversation) {
        val nextPinned = !conversation.effectivePinned
        _uiState.update { state ->
            val updatedList = state.conversations.map {
                if (it.id == conversation.id) it.copy(pinned = nextPinned, isPinned = nextPinned) else it
            }.sortedWith(compareByDescending<Conversation> { it.effectivePinned }.thenByDescending { it.updatedAt })
            state.copy(
                conversations = updatedList,
                filteredConversations = if (state.searchQuery.isBlank()) updatedList else state.filteredConversations,
                selectedConversationForActions = null
            )
        }
        viewModelScope.launch {
            repository.togglePinConversation(conversation.id, nextPinned)
            refreshConversationsAndAttachments()
            showSnackbar(if (nextPinned) getLocalizedText(R.string.chat_pinned_toast) else getLocalizedText(R.string.chat_unpinned_toast))
        }
    }

    fun toggleLockConversation(conversation: Conversation) {
        val nextLocked = !conversation.isLocked
        _uiState.update { state ->
            val updatedList = state.conversations.map {
                if (it.id == conversation.id) it.copy(isLocked = nextLocked) else it
            }
            val nextUnlocked = if (nextLocked) state.unlockedConversationIds - conversation.id else state.unlockedConversationIds + conversation.id
            state.copy(
                conversations = updatedList,
                filteredConversations = if (state.searchQuery.isBlank()) updatedList else state.filteredConversations,
                unlockedConversationIds = nextUnlocked,
                selectedConversationForActions = null
            )
        }
        viewModelScope.launch {
            repository.toggleLockConversation(conversation.id, nextLocked)
            refreshConversationsAndAttachments()
            showSnackbar(if (nextLocked) getLocalizedText(R.string.chat_locked_toast) else getLocalizedText(R.string.chat_unlocked_toast))
        }
    }

    fun markConversationUnlocked(conversationId: String) {
        _uiState.update {
            it.copy(unlockedConversationIds = it.unlockedConversationIds + conversationId)
        }
    }

    fun togglePinActiveConversation() {
        val convId = _uiState.value.activeConversationId ?: return
        val conv = _uiState.value.conversations.firstOrNull { it.id == convId } ?: return
        togglePinConversation(conv)
    }

    fun openRenameDialog(open: Boolean, conversation: Conversation? = null) {
        _uiState.update {
            it.copy(
                showRenameDialog = open,
                conversationToRename = conversation ?: if (open) it.conversations.firstOrNull { c -> c.id == it.activeConversationId } else null,
                selectedConversationForActions = null,
                isOverflowMenuOpen = false
            )
        }
    }

    fun openRenameForConversation(conversation: Conversation) {
        openRenameDialog(true, conversation)
    }

    fun renameActiveConversation(newTitle: String) {
        confirmRenameConversation(newTitle)
    }

    fun confirmRenameConversation(newTitle: String) {
        val targetConv = _uiState.value.conversationToRename
            ?: _uiState.value.conversations.firstOrNull { it.id == _uiState.value.activeConversationId }
            ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val convId = targetConv.id
        val wasActive = convId == _uiState.value.activeConversationId
        _uiState.update { state ->
            val updatedList = state.conversations.map {
                if (it.id == convId) it.copy(title = trimmed) else it
            }
            state.copy(
                showRenameDialog = false,
                conversationToRename = null,
                conversations = updatedList,
                filteredConversations = if (state.searchQuery.isBlank()) updatedList else state.filteredConversations,
                activeConversationTitle = if (wasActive) trimmed else state.activeConversationTitle
            )
        }
        viewModelScope.launch {
            repository.renameConversation(convId, trimmed)
            refreshConversationsAndAttachments()
        }
    }

    fun openDeleteConfirmDialog(open: Boolean, conversation: Conversation? = null) {
        _uiState.update {
            it.copy(
                showDeleteConfirmDialog = open,
                conversationToDelete = conversation ?: if (open) it.conversations.firstOrNull { c -> c.id == it.activeConversationId } else null,
                selectedConversationForActions = null,
                isOverflowMenuOpen = false
            )
        }
    }

    fun openDeleteForConversation(conversation: Conversation) {
        openDeleteConfirmDialog(true, conversation)
    }

    fun deleteActiveConversation() {
        confirmDeleteConversation()
    }

    fun confirmDeleteConversation() {
        val targetConv = _uiState.value.conversationToDelete
            ?: _uiState.value.conversations.firstOrNull { it.id == _uiState.value.activeConversationId }
            ?: return
        val convId = targetConv.id
        val wasActive = convId == _uiState.value.activeConversationId
        _uiState.update { state ->
            val updatedList = state.conversations.filterNot { it.id == convId }
            state.copy(
                showDeleteConfirmDialog = false,
                conversationToDelete = null,
                conversations = updatedList,
                filteredConversations = if (state.searchQuery.isBlank()) updatedList else state.filteredConversations
            )
        }
        viewModelScope.launch {
            repository.deleteConversation(convId)
            if (wasActive) {
                startNewChat(incognito = false)
            }
            refreshConversationsAndAttachments()
            showSnackbar(getLocalizedText(R.string.chat_deleted_toast))
        }
    }

    fun createShareLinkForActiveConversation() {
        val convId = _uiState.value.activeConversationId ?: return
        _uiState.update { it.copy(isOverflowMenuOpen = false) }
        viewModelScope.launch {
            repository.createShareLink(convId).onSuccess { url ->
                _uiState.update { it.copy(activeShareLink = url) }
            }.onFailure { err ->
                showSnackbar(mapError(err))
            }
        }
    }

    fun revokeShareLinkForActiveConversation() {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            repository.revokeShareLinks(convId)
            _uiState.update { it.copy(activeShareLink = null) }
            showSnackbar("Shared link revoked")
        }
    }

    fun dismissShareLinkDialog() {
        _uiState.update { it.copy(activeShareLink = null) }
    }

    fun openAttachmentSheet(open: Boolean) {
        _uiState.update {
            it.copy(
                isAttachmentSheetOpen = open,
                selectedGalleryUris = emptySet(),
                isModePopupOpen = false
            )
        }
        if (open) {
            viewModelScope.launch {
                val recent = repository.queryRecentDeviceImages(limit = 12)
                val uploaded = repository.fetchUserUploadedAttachments().getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        recentDeviceImages = recent,
                        userUploadedAttachments = uploaded
                    )
                }
            }
        }
    }

    fun toggleGallerySelection(uriString: String) {
        _uiState.update { state ->
            val next = state.selectedGalleryUris.toMutableSet()
            if (!next.add(uriString)) {
                next.remove(uriString)
            }
            state.copy(selectedGalleryUris = next)
        }
    }

    fun confirmSelectedGalleryAttachments() {
        val state = _uiState.value
        val picked = state.recentDeviceImages.filter { state.selectedGalleryUris.contains(it.uri.toString()) }
        _uiState.update {
            it.copy(
                stagedAttachments = (it.stagedAttachments + picked).distinctBy { a -> a.uri.toString() },
                selectedGalleryUris = emptySet(),
                isAttachmentSheetOpen = false
            )
        }
    }

    fun attachPickedUris(uris: List<Uri>) {
        val resolved = uris.map { repository.resolveUriDetails(it) }
        _uiState.update {
            it.copy(
                stagedAttachments = (it.stagedAttachments + resolved).distinctBy { a -> a.uri.toString() },
                isAttachmentSheetOpen = false
            )
        }
    }

    fun attachExistingUploadedItem(row: AttachmentRow) {
        val staged = StagedAttachment(
            uri = Uri.parse(row.publicUrl ?: "supabase://${row.storagePath}"),
            fileName = row.fileName,
            mimeType = row.mimeType,
            sizeBytes = row.sizeBytes,
            isImage = row.mimeType.startsWith("image/"),
            uploadedRow = row
        )
        _uiState.update {
            it.copy(
                activeSurfacePane = MainSurfacePane.CHAT,
                isDrawerOpen = false,
                stagedAttachments = (it.stagedAttachments + staged).distinctBy { a -> a.fileName },
                showRecentUploadsPicker = false,
                isAttachmentSheetOpen = false
            )
        }
    }

    fun removeStagedAttachment(uriString: String) {
        _uiState.update { state ->
            state.copy(
                stagedAttachments = state.stagedAttachments.filterNot { it.uri.toString() == uriString }
            )
        }
    }

    fun openRecentUploadsPicker(open: Boolean) {
        _uiState.update {
            it.copy(
                showRecentUploadsPicker = open,
                isAttachmentSheetOpen = if (open) false else it.isAttachmentSheetOpen
            )
        }
    }

    fun setAppearance(appearance: AppAppearance) {
        repository.saveAppearanceLocal(appearance)
        _uiState.update { it.copy(appearance = appearance) }
        viewModelScope.launch {
            repository.updateAppearance(appearance)
        }
    }

    fun setLanguage(language: AppLanguage) {
        repository.saveLanguage(language)
        _uiState.update { it.copy(language = language) }
    }

    fun toggleLanguage() {
        val next = if (_uiState.value.language == AppLanguage.ARABIC) AppLanguage.ENGLISH else AppLanguage.ARABIC
        setLanguage(next)
    }

    fun toggleAppearance() {
        val current = _uiState.value.appearance
        val next = if (current == AppAppearance.DARK) AppAppearance.LIGHT else AppAppearance.DARK
        setAppearance(next)
    }

    fun updateProfile(displayName: String, handle: String) {
        updateProfile(displayName, handle, null, null)
    }

    fun updateProfile(
        displayName: String,
        username: String?,
        bio: String?,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val cleanUser = username?.trim()?.removePrefix("@")?.lowercase()
            val currentUsername = _uiState.value.profile?.effectiveUsername?.lowercase()

            if (!cleanUser.isNullOrBlank() && cleanUser != currentUsername) {
                val usernameResult = repository.changeUsername(cleanUser)
                if (usernameResult.isFailure) {
                    showSnackbar(mapError(usernameResult.exceptionOrNull()))
                    return@launch
                }
            }

            repository.updateProfileDetails(displayName.trim(), bio?.trim()?.ifEmpty { null }, null).onSuccess { updated ->
                cacheUserProfile(updated)
                _uiState.update {
                    it.copy(
                        profile = updated,
                        snackbarMessage = getLocalizedText(R.string.edit_profile_updated)
                    )
                }
                onSuccess?.invoke()
            }.onFailure { err ->
                showSnackbar(mapError(err))
            }
        }
    }

    private fun getLocalizedText(resId: Int, vararg formatArgs: Any): String {
        val isArabic = when (_uiState.value.language) {
            AppLanguage.ARABIC -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> ErrorMapper.isCurrentArabic()
        }
        return try {
            val app = getApplication<Application>()
            val config = android.content.res.Configuration(app.resources.configuration)
            config.setLocale(if (isArabic) java.util.Locale("ar") else java.util.Locale("en"))
            if (formatArgs.isNotEmpty()) {
                app.createConfigurationContext(config).resources.getString(resId, *formatArgs)
            } else {
                app.createConfigurationContext(config).resources.getString(resId)
            }
        } catch (_: Throwable) {
            when (resId) {
                R.string.edit_profile_photo_updated -> if (isArabic) "تم تحديث الصورة الشخصية" else "Profile photo updated"
                R.string.edit_profile_photo_removed -> if (isArabic) "تمت إزالة الصورة الشخصية" else "Profile photo removed"
                R.string.edit_profile_updated -> if (isArabic) "تم تحديث الملف الشخصي" else "Profile updated"
                R.string.edit_profile_banner_updated -> if (isArabic) "تم تحديث غلاف الملف الشخصي" else "Profile banner updated"
                R.string.edit_profile_banner_removed -> if (isArabic) "تمت إزالة غلاف الملف الشخصي" else "Profile banner removed"
                R.string.edit_profile_banner_video_too_long -> if (isArabic) "يجب ألا تتجاوز مدة الفيديو 10 ثوانٍ" else "Video must be 10 seconds or shorter"
                R.string.edit_profile_banner_file_too_large -> if (isArabic) "يجب ألا يتجاوز حجم الملف 15 ميجابايت" else "File size must be 15 MB or smaller"
                R.string.edit_profile_banner_load_error -> if (isArabic) "تعذر معالجة وسائط الغلاف" else "Could not process banner media"
                R.string.profile_notifications_enabled -> {
                    val user = formatArgs.firstOrNull()?.toString() ?: ""
                    if (isArabic) "تم تفعيل الإشعارات لـ @$user" else "Notifications turned on for @$user"
                }
                R.string.profile_notifications_disabled -> {
                    val user = formatArgs.firstOrNull()?.toString() ?: ""
                    if (isArabic) "تم إيقاف الإشعارات لـ @$user" else "Notifications turned off for @$user"
                }
                R.string.profile_notifications_not_following -> {
                    val user = formatArgs.firstOrNull()?.toString() ?: ""
                    if (isArabic) "تابع @$user أولاً لتفعيل الإشعارات" else "Follow @$user first to receive notifications"
                }
                else -> ""
            }
        }
    }

    fun uploadProfileAvatar(bytes: ByteArray, optimisticUri: Uri? = null) {
        viewModelScope.launch {
            val previousProfile = _uiState.value.profile
            val tempAvatarUrl = optimisticUri?.toString() ?: run {
                AvatarImageProcessor.saveToTempCache(getApplication(), bytes)?.toString()
            }
            _uiState.update { state ->
                state.copy(
                    profile = state.profile?.copy(avatarUrl = tempAvatarUrl ?: state.profile?.avatarUrl),
                    isUploadingAvatar = true
                )
            }
            repository.uploadAvatar(bytes).onSuccess {
                val updated = repository.ensureUserProfile()
                cacheUserProfile(updated)
                _uiState.update {
                    it.copy(
                        profile = updated,
                        isUploadingAvatar = false,
                        snackbarMessage = getLocalizedText(R.string.edit_profile_photo_updated)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        profile = previousProfile,
                        isUploadingAvatar = false
                    )
                }
                showSnackbar(mapError(err))
            }
        }
    }

    fun uploadProfileAvatar(uri: Uri) {
        viewModelScope.launch {
            val previousProfile = _uiState.value.profile
            _uiState.update { state ->
                state.copy(
                    profile = state.profile?.copy(avatarUrl = uri.toString()),
                    isUploadingAvatar = true
                )
            }
            repository.uploadAvatar(uri).onSuccess {
                val updated = repository.ensureUserProfile()
                cacheUserProfile(updated)
                _uiState.update {
                    it.copy(
                        profile = updated,
                        isUploadingAvatar = false,
                        snackbarMessage = getLocalizedText(R.string.edit_profile_photo_updated)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        profile = previousProfile,
                        isUploadingAvatar = false
                    )
                }
                showSnackbar(mapError(err))
            }
        }
    }

    fun removeProfileAvatar() {
        viewModelScope.launch {
            val previousProfile = _uiState.value.profile
            _uiState.update { state ->
                state.copy(
                    profile = state.profile?.copy(avatarUrl = null),
                    isUploadingAvatar = true
                )
            }
            repository.removeAvatar().onSuccess {
                val updated = repository.ensureUserProfile()
                cacheUserProfile(updated)
                _uiState.update {
                    it.copy(
                        profile = updated,
                        isUploadingAvatar = false,
                        snackbarMessage = getLocalizedText(R.string.edit_profile_photo_removed)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        profile = previousProfile,
                        isUploadingAvatar = false
                    )
                }
                showSnackbar(mapError(err))
            }
        }
    }

    fun uploadProfileBanner(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            val previousProfile = _uiState.value.profile
            _uiState.update { it.copy(isUploadingBanner = true) }

            val processedBytes = withContext(Dispatchers.IO) {
                if (isVideo) {
                    when (val res = AvatarImageProcessor.inspectAndReadBannerVideo(getApplication(), uri)) {
                        is AvatarImageProcessor.BannerVideoValidation.Valid -> Result.success(res.bytes)
                        is AvatarImageProcessor.BannerVideoValidation.TooLong ->
                            Result.failure(IllegalArgumentException("BANNER_VIDEO_TOO_LONG"))
                        is AvatarImageProcessor.BannerVideoValidation.TooLarge ->
                            Result.failure(IllegalArgumentException("BANNER_FILE_TOO_LARGE"))
                        AvatarImageProcessor.BannerVideoValidation.Unreadable ->
                            Result.failure(IllegalArgumentException("BANNER_LOAD_ERROR"))
                    }
                } else {
                    val bytes = AvatarImageProcessor.processBannerJpeg(getApplication(), uri)
                    if (bytes != null) {
                        Result.success(bytes)
                    } else {
                        Result.failure(IllegalArgumentException("BANNER_LOAD_ERROR"))
                    }
                }
            }

            val bytes = processedBytes.getOrElse { err ->
                _uiState.update { it.copy(isUploadingBanner = false) }
                val message = when (err.message) {
                    "BANNER_VIDEO_TOO_LONG" -> getLocalizedText(R.string.edit_profile_banner_video_too_long)
                    "BANNER_FILE_TOO_LARGE" -> getLocalizedText(R.string.edit_profile_banner_file_too_large)
                    else -> getLocalizedText(R.string.edit_profile_banner_load_error)
                }
                showSnackbar(message)
                return@launch
            }

            val optimisticType = if (isVideo) "video" else "image"
            val optimisticUri = withContext(Dispatchers.IO) {
                AvatarImageProcessor.saveTempBannerFile(getApplication(), bytes, isVideo)?.toString()
            } ?: uri.toString()

            _uiState.update { state ->
                state.copy(
                    profile = state.profile?.copy(
                        bannerUrl = optimisticUri,
                        bannerType = optimisticType
                    ),
                    isUploadingBanner = true
                )
            }

            repository.uploadProfileBannerBytes(bytes, isVideo).onSuccess { (remoteUrl, remoteType) ->
                val refreshed = runCatching { repository.ensureUserProfile() }.getOrNull()
                val merged = (refreshed ?: _uiState.value.profile)?.let { prof ->
                    if (prof.bannerUrl.isNullOrBlank()) {
                        prof.copy(bannerUrl = remoteUrl, bannerType = remoteType)
                    } else prof
                }
                if (merged != null) cacheUserProfile(merged)
                _uiState.update {
                    it.copy(
                        profile = merged ?: it.profile,
                        isUploadingBanner = false,
                        snackbarMessage = getLocalizedText(R.string.edit_profile_banner_updated)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        profile = previousProfile,
                        isUploadingBanner = false
                    )
                }
                showSnackbar(mapError(err))
            }
        }
    }

    fun removeProfileBanner() {
        viewModelScope.launch {
            val previousProfile = _uiState.value.profile
            _uiState.update { state ->
                state.copy(
                    profile = state.profile?.copy(bannerUrl = null, bannerType = "none"),
                    isUploadingBanner = true
                )
            }
            repository.removeProfileBanner().onSuccess {
                val updated = runCatching { repository.ensureUserProfile() }.getOrNull()
                    ?.copy(bannerUrl = null, bannerType = "none")
                    ?: previousProfile?.copy(bannerUrl = null, bannerType = "none")
                if (updated != null) cacheUserProfile(updated)
                _uiState.update {
                    it.copy(
                        profile = updated,
                        isUploadingBanner = false,
                        snackbarMessage = getLocalizedText(R.string.edit_profile_banner_removed)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        profile = previousProfile,
                        isUploadingBanner = false
                    )
                }
                showSnackbar(mapError(err))
            }
        }
    }

    fun deleteAllChats() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            startNewChat(incognito = false)
            refreshConversationsAndAttachments()
            showSnackbar("All chats deleted")
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                realtimeNotificationChannel?.unsubscribe()
            } catch (_: Exception) {}
            realtimeNotificationChannel = null
            repository.deleteAccount().onSuccess {
                clearStoredSession()
                _uiState.value = BuildXUiState(
                    isAuthenticated = false,
                    sessionState = SessionState.SignedOut,
                    appearance = repository.getSavedAppearance(),
                    language = repository.getSavedLanguage()
                )
            }.onFailure { err ->
                val isArabic = _uiState.value.language == AppLanguage.ARABIC
                showSnackbar(ErrorMapper.map(err, isArabic))
            }
        }
    }

    fun exportMyData(onJsonReady: (String) -> Unit) {
        viewModelScope.launch {
            repository.exportUserDataJson().onSuccess { jsonStr ->
                onJsonReady(jsonStr)
            }.onFailure { err ->
                showSnackbar(mapError(err))
            }
        }
    }

    fun dismissNotificationPermissionCard() {
        _uiState.update { it.copy(showNotificationPermissionCard = false) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                showNotificationPermissionCard = !granted,
                snackbarMessage = if (granted) "Notifications enabled" else "Notifications permission denied"
            )
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = ErrorMapper.sanitize(message)) }
    }

    fun mapError(throwable: Throwable?): String {
        val isArabic = when (_uiState.value.language) {
            AppLanguage.ARABIC -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> ErrorMapper.isCurrentArabic()
        }
        return ErrorMapper.map(throwable, isArabic)
    }

    fun mapErrorMessage(raw: String?): String {
        val isArabic = when (_uiState.value.language) {
            AppLanguage.ARABIC -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> ErrorMapper.isCurrentArabic()
        }
        return ErrorMapper.mapMessage(raw, isArabic)
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private var isDmVoiceActive: Boolean = false

    /**
     * S11 Voice Dictation via Android SpeechRecognizer.
     */
    fun startVoiceDictation(isDm: Boolean = false) {
        isDmVoiceActive = isDm
        val context = getApplication<Application>().applicationContext
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            showSnackbar("Speech recognition is not available on this device.")
            return
        }
        stopVoiceDictationInternal()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        _uiState.update {
            it.copy(
                isVoiceListening = true,
                voicePartialTranscript = "",
                voiceAmplitudes = List(28) { 0.15f }
            )
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.12f, 1.0f)
                _uiState.update { s ->
                    val shifted = (s.voiceAmplitudes.drop(1) + normalized)
                    s.copy(voiceAmplitudes = shifted)
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                _uiState.update { it.copy(isVoiceListening = false) }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull().orEmpty()
                _uiState.update { s ->
                    if (isDmVoiceActive) {
                        val combined = if (s.dmComposerText.isBlank()) transcript else "${s.dmComposerText} $transcript"
                        s.copy(
                            isVoiceListening = false,
                            voicePartialTranscript = transcript,
                            dmComposerText = combined.trim()
                        )
                    } else {
                        val combined = if (s.composerText.isBlank()) transcript else "${s.composerText} $transcript"
                        s.copy(
                            isVoiceListening = false,
                            voicePartialTranscript = transcript,
                            composerText = combined.trim()
                        )
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _uiState.update { it.copy(voicePartialTranscript = partial) }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val langTag = when (_uiState.value.language) {
            AppLanguage.ARABIC -> "ar"
            AppLanguage.ENGLISH -> "en-US"
            AppLanguage.SYSTEM -> Locale.getDefault().toLanguageTag()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        }
        recognizer.startListening(intent)
    }

    fun confirmVoiceTranscript() {
        val partial = _uiState.value.voicePartialTranscript.trim()
        stopVoiceDictationInternal()
        _uiState.update { s ->
            if (isDmVoiceActive) {
                val nextText = if (partial.isNotEmpty() && !s.dmComposerText.contains(partial)) {
                    if (s.dmComposerText.isBlank()) partial else "${s.dmComposerText} $partial"
                } else {
                    s.dmComposerText
                }
                s.copy(
                    isVoiceListening = false,
                    voicePartialTranscript = "",
                    dmComposerText = nextText.trim()
                )
            } else {
                val nextText = if (partial.isNotEmpty() && !s.composerText.contains(partial)) {
                    if (s.composerText.isBlank()) partial else "${s.composerText} $partial"
                } else {
                    s.composerText
                }
                s.copy(
                    isVoiceListening = false,
                    voicePartialTranscript = "",
                    composerText = nextText.trim()
                )
            }
        }
    }

    fun cancelVoiceDictation() {
        isDmVoiceActive = false
        stopVoiceDictationInternal()
        _uiState.update {
            it.copy(
                isVoiceListening = false,
                voicePartialTranscript = ""
            )
        }
    }

    private fun stopVoiceDictationInternal() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    override fun onCleared() {
        super.onCleared()
        stopVoiceDictationInternal()
    }

    fun refreshProfile() {
        refreshCurrentOrPublicProfile(null)
    }

    fun refreshCurrentOrPublicProfile(targetUsername: String? = null) {
        val current = _uiState.value
        val ownUsername = current.profile?.username
        val ownHandle = current.profile?.handle
        val isOwnTarget = targetUsername.isNullOrBlank() ||
            targetUsername.equals(ownUsername, ignoreCase = true) ||
            targetUsername.equals(ownHandle, ignoreCase = true)

        if (isOwnTarget) {
            if (current.isOwnProfileRefreshing) return
            _uiState.update { it.copy(isOwnProfileRefreshing = true) }
            viewModelScope.launch {
                try {
                    repository.getOwnProfile().onSuccess { p ->
                        cacheUserProfile(p)
                        _uiState.update { it.copy(profile = p) }
                    }
                    repository.getMyActiveNote().onSuccess { note ->
                        _uiState.update { it.copy(activeOwnNote = note) }
                    }
                } finally {
                    _uiState.update { it.copy(isOwnProfileRefreshing = false) }
                }
            }
            return
        }

        if (current.isPublicProfileRefreshing || current.isPublicProfileLoading) return
        _uiState.update { it.copy(isPublicProfileRefreshing = true) }
        viewModelScope.launch {
            try {
                repository.getPublicProfile(targetUsername!!).onSuccess { pub ->
                    _uiState.update {
                        it.copy(
                            viewedPublicProfile = pub,
                            isSubscribedToNotes = pub?.noteSubscribed == true,
                            isProfileUnavailable = pub == null,
                            publicProfileError = null
                        )
                    }
                }.onFailure { err ->
                    if (_uiState.value.viewedPublicProfile == null) {
                        _uiState.update {
                            it.copy(
                                publicProfileError = mapError(err),
                                isProfileUnavailable = false
                            )
                        }
                    } else {
                        showSnackbar(mapError(err))
                    }
                }
            } finally {
                _uiState.update { it.copy(isPublicProfileRefreshing = false) }
            }
        }
    }

    fun loadPublicProfile(username: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPublicProfileLoading = true,
                    isPublicProfileRefreshing = false,
                    publicProfileError = null,
                    isProfileUnavailable = false
                )
            }
            repository.getPublicProfile(username).onSuccess { pub ->
                _uiState.update {
                    it.copy(
                        viewedPublicProfile = pub,
                        isSubscribedToNotes = pub?.noteSubscribed == true,
                        isPublicProfileLoading = false,
                        publicProfileError = null,
                        isProfileUnavailable = pub == null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        viewedPublicProfile = null,
                        isSubscribedToNotes = false,
                        isPublicProfileLoading = false,
                        publicProfileError = mapError(err),
                        isProfileUnavailable = false
                    )
                }
            }
        }
    }

    fun clearPublicProfile() {
        _uiState.update {
            it.copy(
                viewedPublicProfile = null,
                isSubscribedToNotes = false,
                isPublicProfileLoading = false,
                isPublicProfileRefreshing = false,
                publicProfileError = null,
                isProfileUnavailable = false,
                isFollowPending = false
            )
        }
    }

    /**
     * Optimistic follow/unfollow. The label and counters flip immediately, a single
     * in-flight request is enforced, and every piece of state is rolled back on failure.
     */
    fun toggleFollow(targetUserId: String, targetUsername: String? = null) {
        if (_uiState.value.isFollowPending) return
        if (targetUserId.isBlank()) return

        val snapshot = _uiState.value
        val currentPub = snapshot.viewedPublicProfile
        val newIsFollowing = currentPub?.takeIf { it.id == targetUserId }?.isFollowing?.not()

        if (currentPub != null && currentPub.id == targetUserId && newIsFollowing != null) {
            val delta = if (newIsFollowing) 1 else -1
            _uiState.update {
                it.copy(
                    isFollowPending = true,
                    viewedPublicProfile = currentPub.copy(
                        isFollowing = newIsFollowing,
                        followersCount = (currentPub.followersCount + delta).coerceAtLeast(0),
                        noteSubscribed = if (newIsFollowing) currentPub.noteSubscribed else false
                    ),
                    isSubscribedToNotes = if (newIsFollowing) {
                        it.isSubscribedToNotes
                    } else false
                )
            }
        } else {
            _uiState.update { it.copy(isFollowPending = true) }
        }

        viewModelScope.launch {
            repository.toggleFollow(targetUserId).onSuccess { following ->
                _uiState.update { state ->
                    val pub = state.viewedPublicProfile
                    state.copy(
                        isFollowPending = false,
                        viewedPublicProfile = if (pub != null && pub.id == targetUserId) {
                            pub.copy(isFollowing = following)
                        } else pub,
                        searchPeopleResults = state.searchPeopleResults.map {
                            if (it.id == targetUserId) it.copy(isFollowing = following) else it
                        },
                        followingListUsers = state.followingListUsers.map {
                            if (it.id == targetUserId) it.copy(isFollowing = following) else it
                        },
                        followersListUsers = state.followersListUsers.map {
                            if (it.id == targetUserId) it.copy(isFollowing = following) else it
                        }
                    )
                }
                refreshProfile()
                if (targetUsername != null) loadPublicProfile(targetUsername)
            }.onFailure { err ->
                // Roll back to the pre-optimistic snapshot, then re-sync from the server.
                _uiState.update {
                    it.copy(
                        isFollowPending = false,
                        viewedPublicProfile = snapshot.viewedPublicProfile,
                        isSubscribedToNotes = snapshot.isSubscribedToNotes,
                        searchPeopleResults = snapshot.searchPeopleResults,
                        followingListUsers = snapshot.followingListUsers,
                        followersListUsers = snapshot.followersListUsers
                    )
                }
                showSnackbar(mapError(err))
                refreshProfile()
                if (targetUsername != null) loadPublicProfile(targetUsername)
            }
        }
    }

    fun blockUser(
        targetUserId: String,
        targetUsername: String? = null,
        onBlocked: () -> Unit = {}
    ) {
        if (targetUserId.isBlank() || _uiState.value.isBlocking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBlocking = true) }
            repository.blockUser(targetUserId).onSuccess {
                _uiState.update {
                    it.copy(
                        isBlocking = false,
                        viewedPublicProfile = null,
                        isProfileUnavailable = true,
                        blockedUsers = it.blockedUsers.filterNot { u -> u.id == targetUserId }
                    )
                }
                refreshProfile()
                if (!targetUsername.isNullOrBlank()) {
                    showSnackbar(getLocalizedText(R.string.pv2_blocked_snackbar, targetUsername))
                }
                onBlocked()
            }.onFailure { err ->
                _uiState.update { it.copy(isBlocking = false) }
                showSnackbar(mapError(err))
            }
        }
    }

    fun unblockUser(targetUserId: String) {
        if (targetUserId.isBlank()) return
        viewModelScope.launch {
            repository.unblockUser(targetUserId).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        blockedUsers = state.blockedUsers.filterNot { it.id == targetUserId },
                        viewedPublicProfile = state.viewedPublicProfile?.let { pub ->
                            if (pub.id == targetUserId) pub.copy(blockedByMe = false) else pub
                        }
                    )
                }
                showSnackbar(getLocalizedText(R.string.pv2_blocked_unblocked_snackbar))
            }.onFailure { err ->
                showSnackbar(mapError(err))
            }
        }
    }

    fun loadBlockedUsers(reset: Boolean = false) {
        if (_uiState.value.isBlockedUsersLoading) return
        val offset = if (reset) 0 else _uiState.value.blockedUsers.size
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBlockedUsersLoading = true,
                    blockedUsersError = null,
                    blockedUsers = if (reset) emptyList() else it.blockedUsers
                )
            }
            repository.getBlockedUsers(limit = 30, offset = offset).onSuccess { page ->
                _uiState.update { state ->
                    val merged = if (reset) page else state.blockedUsers + page
                    state.copy(
                        blockedUsers = merged,
                        isBlockedUsersLoading = false,
                        hasMoreBlockedUsers = page.size >= 30
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBlockedUsersLoading = false,
                        blockedUsersError = mapError(err)
                    )
                }
            }
        }
    }

    fun loadAccountInfo(targetUserId: String) {
        if (targetUserId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAccountInfoLoading = true, accountInfoError = null) }
            repository.getAccountInfo(targetUserId).onSuccess { info ->
                _uiState.update {
                    it.copy(accountInfo = info, isAccountInfoLoading = false)
                }
            }.onFailure { err ->
                val isMissingProfile = err.message == "PROFILE_NOT_FOUND"
                _uiState.update {
                    it.copy(
                        isAccountInfoLoading = false,
                        accountInfoError = if (isMissingProfile) null else mapError(err)
                    )
                }
            }
        }
    }

    fun submitReport(
        targetUserId: String,
        reason: ReportReason,
        details: String?,
        onSubmitted: () -> Unit = {}
    ) {
        if (targetUserId.isBlank() || _uiState.value.isSubmittingReport) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReport = true, reportError = null) }
            repository.submitReport(targetUserId, reason, details).onSuccess {
                _uiState.update { it.copy(isSubmittingReport = false, reportError = null) }
                onSubmitted()
            }.onFailure { err ->
                val raw = err.message.orEmpty()
                _uiState.update {
                    it.copy(
                        isSubmittingReport = false,
                        reportError = if (raw.contains("ALREADY_REPORTED", true)) {
                            getLocalizedText(R.string.pv2_report_already_submitted)
                        } else {
                            mapError(err)
                        }
                    )
                }
            }
        }
    }

    fun updateCountry(countryCode: String?) {
        viewModelScope.launch {
            repository.updateCountry(countryCode).onSuccess {
                _uiState.update { state ->
                    val updated = state.profile?.copy(countryCode = countryCode)
                    if (updated != null) cacheUserProfile(updated)
                    state.copy(profile = updated)
                }
            }.onFailure { err ->
                showSnackbar(mapError(err))
            }
        }
    }

    fun toggleNoteSubscription(targetUserId: String, targetUsername: String? = null) {
        viewModelScope.launch {
            val username = targetUsername ?: _uiState.value.viewedPublicProfile?.username ?: ""
            repository.toggleNoteSubscription(targetUserId).onSuccess { subscribed ->
                _uiState.update { state ->
                    val currentPub = state.viewedPublicProfile
                    if (currentPub != null && currentPub.id == targetUserId) {
                        state.copy(
                            viewedPublicProfile = currentPub.copy(noteSubscribed = subscribed),
                            isSubscribedToNotes = subscribed
                        )
                    } else {
                        state.copy(isSubscribedToNotes = subscribed)
                    }
                }
                val msg = if (subscribed) {
                    getLocalizedText(R.string.profile_notifications_enabled, username)
                } else {
                    getLocalizedText(R.string.profile_notifications_disabled, username)
                }
                showSnackbar(msg)
            }.onFailure { err ->
                val errorMsg = err.message ?: ""
                val msg = when {
                    errorMsg.contains("NOT_FOLLOWING", ignoreCase = true) -> {
                        getLocalizedText(R.string.profile_notifications_not_following, username)
                    }
                    errorMsg.contains("SELF", ignoreCase = true) -> {
                        val isArabic = when (_uiState.value.language) {
                            AppLanguage.ARABIC -> true
                            AppLanguage.ENGLISH -> false
                            AppLanguage.SYSTEM -> ErrorMapper.isCurrentArabic()
                        }
                        if (isArabic) "لا يمكن تفعيل الإشعارات لحسابك" else "Cannot subscribe to your own notifications"
                    }
                    else -> mapError(err)
                }
                showSnackbar(msg)
            }
        }
    }

    fun createNote(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingNote = true) }
            repository.createNote(content).onSuccess { note ->
                _uiState.update {
                    it.copy(
                        activeOwnNote = note,
                        isCreatingNote = false,
                        isNoteSheetOpen = false
                    )
                }
                loadInboxNotesTray()
                showSnackbar("Note shared")
            }.onFailure { err ->
                _uiState.update { it.copy(isCreatingNote = false) }
                showSnackbar(mapError(err))
            }
        }
    }

    fun deleteNote() {
        viewModelScope.launch {
            repository.deleteNote().onSuccess {
                _uiState.update { state ->
                    val currentUid = currentUserId()
                    state.copy(
                        activeOwnNote = null,
                        inboxNotesTray = if (currentUid != null) {
                            state.inboxNotesTray.filterNot { it.userId == currentUid }
                        } else {
                            state.inboxNotesTray
                        },
                        isNoteSheetOpen = false
                    )
                }
                loadInboxNotesTray()
                showSnackbar("Note deleted")
            }.onFailure { err ->
                showSnackbar(mapError(err))
            }
        }
    }

    fun openNoteSheet(open: Boolean) {
        _uiState.update { it.copy(isNoteSheetOpen = open) }
        if (open) {
            loadActiveOwnNote()
        }
    }

    fun loadFollowLists(targetUserId: String, tabIndex: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFollowListsLoading = true, followListsError = null) }
            if (tabIndex == 0) {
                repository.getUserFollowing(targetUserId).onSuccess { list ->
                    _uiState.update { it.copy(followingListUsers = list, isFollowListsLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isFollowListsLoading = false, followListsError = mapError(err)) }
                }
            } else {
                repository.getUserFollowers(targetUserId).onSuccess { list ->
                    _uiState.update { it.copy(followersListUsers = list, isFollowListsLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isFollowListsLoading = false, followListsError = mapError(err)) }
                }
            }
        }
    }

    fun onGlobalSearchQueryChange(query: String) {
        _uiState.update { it.copy(globalSearchQuery = query) }
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchPeopleResults = emptyList(), filteredConversations = it.conversations) }
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(300L)
            if (_uiState.value.searchActiveTab == 0) {
                _uiState.update { it.copy(isSearchPeopleLoading = true, searchPeopleError = null) }
                repository.searchPeople(query).onSuccess { results ->
                    _uiState.update { it.copy(searchPeopleResults = results, isSearchPeopleLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(searchPeopleError = mapError(err), isSearchPeopleLoading = false) }
                }
            } else {
                repository.searchConversations(query).onSuccess { convs ->
                    _uiState.update { it.copy(filteredConversations = convs) }
                }
            }
        }
    }

    fun setSearchActiveTab(tab: Int) {
        _uiState.update { it.copy(searchActiveTab = tab) }
        val q = _uiState.value.globalSearchQuery
        if (q.isNotBlank()) {
            onGlobalSearchQueryChange(q)
        }
    }

    fun clearRecentSearches() {
        _uiState.update { it.copy(recentSearchesList = emptyList()) }
    }

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            _uiState.update {
                val updated = (listOf(q) + it.recentSearchesList.filter { item -> item != q }).take(10)
                it.copy(recentSearchesList = updated)
            }
        }
    }

    fun loadInAppNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isNotificationsLoading = true) }
            repository.fetchNotifications().onSuccess { notifs ->
                val unread = notifs.count { !it.isRead }
                _uiState.update {
                    it.copy(
                        inAppNotifications = notifs,
                        unreadNotificationsCount = unread,
                        isNotificationsLoading = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isNotificationsLoading = false) }
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
            _uiState.update { state ->
                val updated = state.inAppNotifications.map {
                    if (it.id == id) it.copy(isRead = true) else it
                }
                state.copy(
                    inAppNotifications = updated,
                    unreadNotificationsCount = updated.count { !it.isRead }
                )
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
            _uiState.update { state ->
                val updated = state.inAppNotifications.map { it.copy(isRead = true) }
                state.copy(
                    inAppNotifications = updated,
                    unreadNotificationsCount = 0
                )
            }
        }
    }

    fun markUsernameReviewed() {
        viewModelScope.launch {
            repository.markUsernameReviewed()
            refreshProfile()
            _uiState.update { it.copy(showUsernameReviewSheet = false) }
        }
    }

    fun confirmUsernameChange(newUsername: String, onComplete: ((Result<String>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.changeUsername(newUsername)
            res.onSuccess { clean ->
                repository.markUsernameReviewed()
                refreshProfile()
                _uiState.update { it.copy(showUsernameReviewSheet = false) }
                showSnackbar("Username updated to @$clean")
                onComplete?.invoke(Result.success(clean))
            }.onFailure { err ->
                showSnackbar(mapError(err))
                onComplete?.invoke(Result.failure(err))
            }
        }
    }

    fun openUsernameReviewSheet() {
        _uiState.update { it.copy(showUsernameReviewSheet = true) }
    }

    fun dismissUsernameReviewSheet() {
        _uiState.update { it.copy(showUsernameReviewSheet = false) }
    }

    // =========================================================================
    // DIRECT & GROUP MESSAGING SYSTEM (DMs, Group Chats, Notes Tray, Seen Receipts)
    // =========================================================================

    private var dmInboxChannel: RealtimeChannel? = null
    private var dmInboxSubscriptionJob: Job? = null
    private var dmInboxDebounceJob: Job? = null
    private var dmRoomChannel: RealtimeChannel? = null
    private var dmInboxLoadJob: Job? = null
    private var dmSearchPeopleJob: Job? = null
    private var dmRoomSubscriptionJob: Job? = null
    private var dmRoomMsgRefreshJob: Job? = null
    private var dmRoomDetailRefreshJob: Job? = null
    private val dmTypingExpiryJobs = mutableMapOf<String, Job>()
    private var dmStopTypingJob: Job? = null
    private var lastDmTypingSentAtMs: Long = 0L
    private var dmAllInboxCache: List<DmInboxItem> = emptyList()
    private val dmDetailCache = mutableMapOf<String, DmConversationDetail>()
    private val dmMessagesCache = mutableMapOf<String, List<DmMessage>>()
    private val dmReactionsCache = mutableMapOf<String, Pair<Map<String, Map<String, Int>>, Map<String, String>>>()
    private val dmFailedStagedAttachmentByTempId = mutableMapOf<String, StagedAttachment>()
    private val inFlightClientTempIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val recentlySentClientTempIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun applyInboxMutation(
        extraStateTransform: (BuildXUiState) -> BuildXUiState = { it },
        listTransform: (List<DmInboxItem>) -> List<DmInboxItem>
    ) {
        if (dmAllInboxCache.isNotEmpty()) {
            dmAllInboxCache = listTransform(dmAllInboxCache)
        }
        _uiState.update { state ->
            val updatedItems = listTransform(state.dmInboxItems)
            val canonical = dmAllInboxCache.ifEmpty { updatedItems }
            val counters = ChatMessageMapper.computeInboxCounters(canonical)
            extraStateTransform(state).copy(
                dmInboxItems = updatedItems,
                dmInboxCounters = counters,
                totalUnreadDmCount = counters.totalUnreadMessages
            )
        }
    }

    private fun resolveInitialDmDetail(conversationId: String): DmConversationDetail? {
        val currentState = _uiState.value
        val currentUserId = currentState.profile?.id ?: repository.currentUserId().orEmpty()
        val inboxItem = currentState.dmInboxItems.firstOrNull { it.effectiveId == conversationId }
        val cached = dmDetailCache[conversationId]
            ?: currentState.activeDmDetail?.takeIf { it.conversation?.id == conversationId }

        if (cached != null) {
            if (inboxItem == null || currentUserId.isBlank()) return cached
            val syncedParticipants = cached.participants.map { p ->
                if (p.userId == currentUserId) {
                    p.copy(isMuted = inboxItem.isMuted, isPinned = inboxItem.isPinned)
                } else {
                    p
                }
            }
            return cached.copy(participants = syncedParticipants)
        }

        if (inboxItem == null) return null

        val selfParticipant = if (currentUserId.isNotBlank()) {
            DmParticipant(
                userId = currentUserId,
                username = currentState.profile?.username,
                handle = currentState.profile?.handle,
                displayName = currentState.profile?.displayName,
                avatarUrl = currentState.profile?.avatarUrl,
                isVerified = currentState.profile?.isVerified ?: false,
                isMuted = inboxItem.isMuted,
                isPinned = inboxItem.isPinned,
                lastReadAt = inboxItem.lastReadAt,
                lastReadMessageId = inboxItem.lastReadMessageId
            )
        } else {
            null
        }

        val participants = buildList {
            if (selfParticipant != null) add(selfParticipant)
            if (inboxItem.kind != "group") {
                val peerHandle = inboxItem.handle?.takeIf { it.isNotBlank() }
                    ?: inboxItem.peer?.handle?.takeIf { it.isNotBlank() }
                    ?: inboxItem.peer?.username?.takeIf { it.isNotBlank() }
                add(
                    DmParticipant(
                        userId = inboxItem.peer?.id ?: "peer_$conversationId",
                        username = inboxItem.peer?.username ?: peerHandle,
                        handle = peerHandle,
                        displayName = inboxItem.displayName ?: inboxItem.peer?.displayName ?: inboxItem.effectiveTitle,
                        avatarUrl = inboxItem.effectiveAvatarUrl,
                        isVerified = inboxItem.effectiveIsVerified
                    )
                )
            } else {
                inboxItem.memberAvatars.forEach { member ->
                    if (member.userId != currentUserId) {
                        add(
                            DmParticipant(
                                userId = member.userId,
                                displayName = member.displayName,
                                avatarUrl = member.avatarUrl
                            )
                        )
                    }
                }
                val targetCount = inboxItem.memberCount ?: size
                while (size < targetCount) {
                    add(DmParticipant(userId = "member_placeholder_$size"))
                }
            }
        }

        return DmConversationDetail(
            success = true,
            conversation = DmConversationMetadata(
                id = conversationId,
                kind = inboxItem.kind,
                title = inboxItem.effectiveTitle,
                avatarUrl = inboxItem.effectiveAvatarUrl
            ),
            participants = participants
        )
    }

    private fun scheduleRealtimeInboxRefresh() {
        dmInboxDebounceJob?.cancel()
        dmInboxDebounceJob = viewModelScope.launch {
            delay(160L)
            loadDmInbox()
        }
    }

    private fun subscribeToRealtimeInbox(uid: String) {
        dmInboxSubscriptionJob?.cancel()
        dmInboxSubscriptionJob = viewModelScope.launch {
            try {
                dmInboxChannel?.unsubscribe()
                val channel = repository.supabase.channel("dm-inbox-$uid")
                val partFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "dm_participants"
                    filter("user_id", FilterOperator.EQ, uid)
                }
                val msgFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "dm_messages"
                }
                val convFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "dm_conversations"
                }
                dmInboxChannel = channel
                channel.subscribe()
                launch {
                    partFlow.collect { scheduleRealtimeInboxRefresh() }
                }
                launch {
                    msgFlow.collect { action ->
                        val activeConvId = _uiState.value.activeDmConversationId
                        when (action) {
                            is PostgresAction.Insert -> {
                                val record = action.record
                                val convId = record["conversation_id"]?.jsonPrimitive?.contentOrNull
                                val senderId = record["sender_id"]?.jsonPrimitive?.contentOrNull
                                val clientTempId = record["client_temp_id"]?.jsonPrimitive?.contentOrNull
                                val msgId = record["id"]?.jsonPrimitive?.contentOrNull
                                val knownTemps = synchronized(inFlightClientTempIds) { inFlightClientTempIds.toSet() } +
                                    synchronized(recentlySentClientTempIds) { recentlySentClientTempIds.toSet() }
                                val isOwnActiveEcho = senderId == uid &&
                                    convId != null &&
                                    convId == activeConvId &&
                                    ChatMessageMapper.shouldSkipRealtimeMessageInsert(
                                        messageId = msgId,
                                        clientTempId = clientTempId,
                                        senderId = senderId,
                                        currentUserId = uid,
                                        currentMessages = dmMessagesCache[convId] ?: _uiState.value.dmMessages,
                                        knownClientTempIds = knownTemps
                                    )
                                if (!isOwnActiveEcho) {
                                    scheduleRealtimeInboxRefresh()
                                }
                                dispatchIncomingDmNotification(record)
                            }
                            is PostgresAction.Update -> {
                                val record = action.record
                                val convId = record["conversation_id"]?.jsonPrimitive?.contentOrNull
                                val msgId = record["id"]?.jsonPrimitive?.contentOrNull
                                val skip = convId != null && convId == activeConvId &&
                                    ChatMessageMapper.shouldSkipRealtimeMessageUpdate(
                                        messageId = msgId,
                                        content = record["content"]?.jsonPrimitive?.contentOrNull,
                                        editedAt = record["edited_at"]?.jsonPrimitive?.contentOrNull,
                                        deletedAt = record["deleted_at"]?.jsonPrimitive?.contentOrNull,
                                        currentMessages = dmMessagesCache[convId] ?: _uiState.value.dmMessages
                                    )
                                if (!skip) {
                                    scheduleRealtimeInboxRefresh()
                                }
                            }
                            else -> scheduleRealtimeInboxRefresh()
                        }
                    }
                }
                launch {
                    convFlow.collect { scheduleRealtimeInboxRefresh() }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadDmInbox(
        filter: String = uiState.value.selectedDmFilterTab.name.lowercase(),
        search: String = uiState.value.dmSearchQuery
    ) {
        dmInboxLoadJob?.cancel()
        dmInboxLoadJob = viewModelScope.launch {
            val cleanSearch = search.trim()
            val effectiveFilter = if (filter == "requests") "requests" else "all"
            if (cleanSearch.isNotEmpty()) {
                delay(150L)
            }
            _uiState.update { state ->
                if (state.dmInboxItems.isEmpty()) state.copy(isDmInboxLoading = true) else state
            }
            repository.getDmInbox(filter = effectiveFilter, search = cleanSearch).fold(
                onSuccess = { items ->
                    if (cleanSearch.isEmpty() && effectiveFilter == "all") {
                        dmAllInboxCache = items
                    }
                    val canonical = if (effectiveFilter == "all" && cleanSearch.isEmpty()) {
                        items
                    } else {
                        dmAllInboxCache.ifEmpty { items }
                    }
                    val counters = ChatMessageMapper.computeInboxCounters(canonical)
                    _uiState.update {
                        it.copy(
                            dmInboxItems = items,
                            dmInboxCounters = counters,
                            totalUnreadDmCount = counters.totalUnreadMessages,
                            isDmInboxLoading = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isDmInboxLoading = false) }
                }
            )
        }
    }

    fun selectDmFilterTab(tab: DmFilterTab) {
        _uiState.update { state ->
            val nextItems = if (state.dmSearchQuery.isBlank() && tab != DmFilterTab.REQUESTS && dmAllInboxCache.isNotEmpty()) {
                dmAllInboxCache
            } else {
                state.dmInboxItems
            }
            val canonical = dmAllInboxCache.ifEmpty { nextItems }
            val counters = ChatMessageMapper.computeInboxCounters(canonical)
            state.copy(
                selectedDmFilterTab = tab,
                dmInboxItems = nextItems,
                dmInboxCounters = counters,
                totalUnreadDmCount = counters.totalUnreadMessages
            )
        }
        if (tab == DmFilterTab.REQUESTS || dmAllInboxCache.isEmpty()) {
            loadDmInbox(filter = tab.name.lowercase())
        }
    }

    fun onDmSearchQueryChanged(query: String) {
        _uiState.update { state ->
            if (query.isBlank() && state.selectedDmFilterTab != DmFilterTab.REQUESTS && dmAllInboxCache.isNotEmpty()) {
                val counters = ChatMessageMapper.computeInboxCounters(dmAllInboxCache)
                state.copy(
                    dmSearchQuery = query,
                    dmInboxItems = dmAllInboxCache,
                    dmInboxCounters = counters,
                    totalUnreadDmCount = counters.totalUnreadMessages
                )
            } else {
                state.copy(dmSearchQuery = query)
            }
        }
        loadDmInbox(search = query)
        dmSearchPeopleJob?.cancel()
        if (query.trim().length >= 2) {
            dmSearchPeopleJob = viewModelScope.launch {
                delay(180L)
                _uiState.update { it.copy(isDmSearchPeopleLoading = true) }
                repository.searchPeople(query.trim()).fold(
                    onSuccess = { people ->
                        _uiState.update { it.copy(dmSearchPeopleResults = people, isDmSearchPeopleLoading = false) }
                    },
                    onFailure = {
                        _uiState.update { it.copy(isDmSearchPeopleLoading = false) }
                    }
                )
            }
        } else {
            _uiState.update { it.copy(dmSearchPeopleResults = emptyList(), isDmSearchPeopleLoading = false) }
        }
    }

    /**
     * Loads the conversation detail (and its media history) without entering the
     * room, so the inbox's Chat Info sheet can show real participants and media.
     */
    fun loadConversationInfo(conversationId: String) {
        val seededDetail = resolveInitialDmDetail(conversationId)
        val cachedMessages = dmMessagesCache[conversationId]
        val cachedWallpaper = repository.getEffectiveWallpaperLocal(conversationId)
        _uiState.update { state ->
            state.copy(
                activeDmWallpaper = cachedWallpaper,
                activeDmDetail = seededDetail ?: state.activeDmDetail,
                dmMessages = cachedMessages ?: state.dmMessages
            )
        }
        viewModelScope.launch {
            repository.getDmConversationDetail(conversationId).onSuccess { detail ->
                applyDetailMonotonic(conversationId, detail, allowWhenInactive = true)
            }
            repository.getDmMessages(conversationId).onSuccess { msgs ->
                dmMessagesCache[conversationId] = msgs
                _uiState.update { it.copy(dmMessages = msgs) }
            }
        }
    }

    /**
     * Search used by the group add-members picker. Unlike the inbox search this
     * also runs for a blank query so the picker can show suggestions immediately.
     */
    fun searchPeopleForGroup(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDmSearchPeopleLoading = true) }
            repository.searchPeople(query.trim()).fold(
                onSuccess = { people ->
                    _uiState.update { it.copy(dmSearchPeopleResults = people, isDmSearchPeopleLoading = false) }
                },
                onFailure = {
                    _uiState.update { it.copy(isDmSearchPeopleLoading = false) }
                }
            )
        }
    }

    fun leaveOrDeleteDmConversation(conversationId: String) {
        viewModelScope.launch {
            val isGroup = _uiState.value.dmInboxItems
                .firstOrNull { it.effectiveId == conversationId || it.id == conversationId }
                ?.kind == "group"
            dmMessagesCache.remove(conversationId)
            dmDetailCache.remove(conversationId)
            applyInboxMutation(
                extraStateTransform = { state ->
                    if (state.activeDmConversationId == conversationId) {
                        state.copy(dmMessages = emptyList(), activeDmDetail = null)
                    } else state
                }
            ) { list ->
                list.filterNot { item -> item.effectiveId == conversationId }
            }
            repository.leaveOrDeleteDmConversation(conversationId, isGroup = isGroup).onFailure { err ->
                showSnackbar(mapError(err))
                loadDmInbox()
            }
        }
    }

    fun markDmUnread(conversationId: String) {
        viewModelScope.launch {
            applyInboxMutation { list ->
                list.map { item ->
                    if (item.effectiveId == conversationId) {
                        item.copy(unreadCount = maxOf(1, item.unreadCount), isMarkedUnread = true)
                    } else item
                }
            }
            repository.markDmUnread(conversationId).onFailure { err ->
                showSnackbar(mapError(err))
                loadDmInbox()
            }
        }
    }

    fun markDmRead(conversationId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val conv = state.dmInboxItems.firstOrNull { it.effectiveId == conversationId }
            val lastMsgId = conv?.effectiveLastMessageId
                ?: state.dmMessages.lastOrNull {
                    it.conversationId == conversationId && it.localSendStatus == DmSendStatus.SENT
                }?.id
            applyInboxMutation { list ->
                list.map { item ->
                    if (item.effectiveId == conversationId) {
                        item.copy(unreadCount = 0, isMarkedUnread = false)
                    } else item
                }
            }
            repository.markDmRead(conversationId, lastMsgId).onFailure {
                loadDmInbox()
            }
            if (_uiState.value.activeDmConversationId == conversationId) {
                broadcastDmReadReceipt(conversationId, lastMsgId)
            }
        }
    }

    fun loadInboxNotesTray() {
        viewModelScope.launch {
            repository.getFollowedActiveNotes().onSuccess { notes ->
                _uiState.update { it.copy(inboxNotesTray = notes) }
            }
        }
    }

    fun toggleNoteLike(noteId: String) {
        viewModelScope.launch {
            val previousItem = _uiState.value.inboxNotesTray.firstOrNull {
                it.effectiveNoteId == noteId || it.id == noteId
            }
            val targetNoteId = previousItem?.effectiveNoteId ?: noteId
            val previousOwn = _uiState.value.activeOwnNote?.takeIf { it.id == targetNoteId || it.id == noteId }

            _uiState.update { state ->
                val updatedTray = state.inboxNotesTray.map { item ->
                    if (item.effectiveNoteId == targetNoteId || item.id == noteId) {
                        val nextLiked = !item.isLikedByMe
                        val nextCount = (item.likesCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
                        item.copy(isLikedByMe = nextLiked, likesCount = nextCount)
                    } else {
                        item
                    }
                }
                val updatedOwn = state.activeOwnNote?.let { own ->
                    if (own.id == targetNoteId || own.id == noteId) {
                        val nextLiked = !own.isLikedByMe
                        val nextCount = (own.likesCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
                        own.copy(isLikedByMe = nextLiked, likesCount = nextCount)
                    } else own
                }
                state.copy(inboxNotesTray = updatedTray, activeOwnNote = updatedOwn)
            }

            repository.toggleNoteLike(targetNoteId).fold(
                onSuccess = { result ->
                    val confirmedId = result.noteId?.takeIf { it.isNotBlank() } ?: targetNoteId
                    _uiState.update { state ->
                        val syncedTray = state.inboxNotesTray.map { item ->
                            if (item.effectiveNoteId == confirmedId || item.effectiveNoteId == targetNoteId || item.id == noteId) {
                                item.copy(isLikedByMe = result.liked, likesCount = result.likesCount)
                            } else {
                                item
                            }
                        }
                        val syncedOwn = state.activeOwnNote?.let { own ->
                            if (own.id == confirmedId || own.id == targetNoteId || own.id == noteId) {
                                own.copy(isLikedByMe = result.liked, likesCount = result.likesCount)
                            } else own
                        }
                        state.copy(inboxNotesTray = syncedTray, activeOwnNote = syncedOwn)
                    }
                    if (_uiState.value.activeOwnNote?.id == confirmedId || _uiState.value.activeOwnNote?.id == targetNoteId) {
                        loadActiveOwnNote()
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        val revertedTray = if (previousItem != null) {
                            state.inboxNotesTray.map {
                                if (it.effectiveNoteId == targetNoteId || it.id == noteId) previousItem else it
                            }
                        } else {
                            state.inboxNotesTray
                        }
                        val revertedOwn = previousOwn ?: state.activeOwnNote
                        state.copy(inboxNotesTray = revertedTray, activeOwnNote = revertedOwn)
                    }
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun openDirectChatWithUser(targetUserId: String, onResolved: (String) -> Unit) {
        viewModelScope.launch {
            repository.getOrCreateDirectConversation(targetUserId).fold(
                onSuccess = { convId ->
                    loadDmInbox()
                    onResolved(convId)
                },
                onFailure = { err ->
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun openCreateGroupSheet(
        open: Boolean,
        preselectedUserIds: Set<String> = emptySet()
    ) {
        _uiState.update {
            it.copy(
                isCreateGroupSheetOpen = open,
                groupCreationSelectedUserIds = if (open) preselectedUserIds else emptySet(),
                groupCreationTitle = ""
            )
        }
    }

    fun toggleGroupCreationUserSelection(userId: String) {
        _uiState.update {
            val selected = it.groupCreationSelectedUserIds.toMutableSet()
            if (selected.contains(userId)) {
                selected.remove(userId)
            } else {
                selected.add(userId)
            }
            it.copy(groupCreationSelectedUserIds = selected)
        }
    }

    fun onGroupCreationTitleChanged(title: String) {
        _uiState.update { it.copy(groupCreationTitle = title) }
    }

    fun submitCreateGroup(onCreated: (String) -> Unit) {
        val title = uiState.value.groupCreationTitle.trim()
        val members = uiState.value.groupCreationSelectedUserIds.toList()
        if (title.isBlank() || members.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingGroup = true) }
            repository.createGroupConversation(title, members).fold(
                onSuccess = { convId ->
                    _uiState.update {
                        it.copy(
                            isCreatingGroup = false,
                            isCreateGroupSheetOpen = false,
                            groupCreationSelectedUserIds = emptySet(),
                            groupCreationTitle = ""
                        )
                    }
                    loadDmInbox()
                    onCreated(convId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isCreatingGroup = false
                        )
                    }
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun enterDmRoom(conversationId: String) {
        val currentState = _uiState.value
        BuildXSystemNotificationManager.cancelConversation(getApplication(), conversationId)
        val isSameConversationAlreadyLoaded =
            currentState.activeDmConversationId == conversationId && currentState.dmMessages.isNotEmpty()
        val cachedWallpaper = repository.getEffectiveWallpaperLocal(conversationId)
        val seededDetail = resolveInitialDmDetail(conversationId)
        val cachedMessages = dmMessagesCache[conversationId].orEmpty()
        val cachedReactions = dmReactionsCache[conversationId]

        applyInboxMutation(
            extraStateTransform = { state ->
                if (!isSameConversationAlreadyLoaded) {
                    val savedDraft = state.dmDraftsByConversation[conversationId]
                        ?: repository.prefs.getString("dm_draft_$conversationId", "")
                        ?: ""
                    state.copy(
                        activeDmConversationId = conversationId,
                        isDmMessagesLoading = cachedMessages.isEmpty(),
                        isDmPaginating = false,
                        hasMoreDmMessages = true,
                        activeDmDetail = seededDetail,
                        activeDmWallpaper = cachedWallpaper,
                        dmMessages = cachedMessages,
                        dmComposerText = savedDraft,
                        dmStagedAttachments = emptyList(),
                        dmReplyingToMessage = null,
                        dmEditingMessage = null,
                        activeDmMessageMenuId = null,
                        lastOptimisticMessageId = null,
                        dmMessageReactions = cachedReactions?.first ?: emptyMap(),
                        dmMyMessageReactions = cachedReactions?.second ?: emptyMap()
                    )
                } else {
                    state.copy(
                        activeDmWallpaper = cachedWallpaper,
                        activeDmDetail = seededDetail ?: state.activeDmDetail
                    )
                }
            }
        ) { list ->
            list.map { item ->
                if (item.effectiveId == conversationId) {
                    item.copy(unreadCount = 0, isMarkedUnread = false)
                } else item
            }
        }

        viewModelScope.launch {
            repository.getDmConversationDetail(conversationId).onSuccess { detail ->
                applyDetailMonotonic(conversationId, detail, allowWhenInactive = true)
            }

            repository.getDmMessages(conversationId, limit = 50).fold(
                onSuccess = { msgs ->
                    _uiState.update { state ->
                        val reconciled = ChatMessageMapper.reconcileOptimisticMessages(
                            serverMessages = msgs,
                            currentLocalMessages = state.dmMessages
                        )
                        dmMessagesCache[conversationId] = reconciled
                        state.copy(
                            dmMessages = reconciled,
                            isDmMessagesLoading = false,
                            hasMoreDmMessages = msgs.size >= 50
                        )
                    }
                    val latest = msgs.lastOrNull()
                    if (latest != null) {
                        repository.markDmRead(conversationId, latest.id)
                    } else {
                        val fallbackId = _uiState.value.dmInboxItems
                            .firstOrNull { it.effectiveId == conversationId }
                            ?.effectiveLastMessageId
                        repository.markDmRead(conversationId, fallbackId)
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isDmMessagesLoading = false) }
                }
            )

            subscribeToDmRoom(conversationId)
        }
    }

    fun loadOlderDmMessages(conversationId: String) {
        val state = _uiState.value
        if (state.isDmPaginating || !state.hasMoreDmMessages || state.isDmMessagesLoading) return
        val oldestServerMsg = state.dmMessages.firstOrNull {
            it.localSendStatus == DmSendStatus.SENT
        } ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDmPaginating = true) }
            repository.getDmMessages(
                conversationId = conversationId,
                before = oldestServerMsg.createdAt,
                limit = 50
            ).fold(
                onSuccess = { olderMsgs ->
                    _uiState.update { current ->
                        val combined = ChatMessageMapper.reconcileOptimisticMessages(
                            serverMessages = olderMsgs + current.dmMessages.filter { it.localSendStatus == DmSendStatus.SENT },
                            currentLocalMessages = current.dmMessages
                        )
                        dmMessagesCache[conversationId] = combined
                        current.copy(
                            dmMessages = combined,
                            isDmPaginating = false,
                            hasMoreDmMessages = olderMsgs.size >= 50
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isDmPaginating = false) }
                }
            )
        }
    }

    private fun subscribeToDmRoom(conversationId: String) {
        dmRoomMsgRefreshJob?.cancel()
        dmRoomDetailRefreshJob?.cancel()
        dmRoomSubscriptionJob?.cancel()
        dmTypingExpiryJobs.values.forEach { it.cancel() }
        dmTypingExpiryJobs.clear()
        dmStopTypingJob?.cancel()
        lastDmTypingSentAtMs = 0L
        _uiState.update { it.copy(dmTypingUserIds = emptySet()) }

        dmRoomSubscriptionJob = viewModelScope.launch {
            try {
                dmRoomChannel?.unsubscribe()
                val channel = repository.supabase.channel("dm-room-$conversationId")

                val msgFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "dm_messages"
                    filter("conversation_id", FilterOperator.EQ, conversationId)
                }

                val partFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "dm_participants"
                    filter("conversation_id", FilterOperator.EQ, conversationId)
                }

                val convFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "dm_conversations"
                    filter("id", FilterOperator.EQ, conversationId)
                }

                val typingFlow = channel.broadcastFlow<JsonObject>(event = "typing")
                val readReceiptFlow = channel.broadcastFlow<JsonObject>(event = "read_receipt")
                val reactionFlow = channel.broadcastFlow<JsonObject>(event = "reaction")

                dmRoomChannel = channel
                channel.subscribe()

                val initialReadId = _uiState.value.dmMessages.lastOrNull {
                    it.localSendStatus == DmSendStatus.SENT
                }?.id
                if (!initialReadId.isNullOrBlank()) {
                    broadcastDmReadReceipt(conversationId, initialReadId)
                }

                var lastAutoMarkedReadId: String? = initialReadId
                val scheduleDetailRefresh: () -> Unit = {
                    dmRoomDetailRefreshJob?.cancel()
                    dmRoomDetailRefreshJob = launch {
                        delay(120L)
                        repository.getDmConversationDetail(conversationId).onSuccess { detail ->
                            applyDetailMonotonic(conversationId, detail)
                        }
                    }
                }

                launch {
                    msgFlow.collect { action ->
                        val myUid = repository.currentUserId()
                        val localMsgs = dmMessagesCache[conversationId] ?: _uiState.value.dmMessages
                        val knownTemps = synchronized(inFlightClientTempIds) { inFlightClientTempIds.toSet() } +
                            synchronized(recentlySentClientTempIds) { recentlySentClientTempIds.toSet() }
                        val shouldSkip = when (action) {
                            is PostgresAction.Insert -> ChatMessageMapper.shouldSkipRealtimeMessageInsert(
                                messageId = action.record["id"]?.jsonPrimitive?.contentOrNull,
                                clientTempId = action.record["client_temp_id"]?.jsonPrimitive?.contentOrNull,
                                senderId = action.record["sender_id"]?.jsonPrimitive?.contentOrNull,
                                currentUserId = myUid,
                                currentMessages = localMsgs,
                                knownClientTempIds = knownTemps
                            )
                            is PostgresAction.Update -> ChatMessageMapper.shouldSkipRealtimeMessageUpdate(
                                messageId = action.record["id"]?.jsonPrimitive?.contentOrNull,
                                content = action.record["content"]?.jsonPrimitive?.contentOrNull,
                                editedAt = action.record["edited_at"]?.jsonPrimitive?.contentOrNull,
                                deletedAt = action.record["deleted_at"]?.jsonPrimitive?.contentOrNull,
                                currentMessages = localMsgs
                            )
                            is PostgresAction.Delete -> {
                                val deletedId = action.oldRecord["id"]?.jsonPrimitive?.contentOrNull
                                deletedId != null && localMsgs.none { it.id == deletedId }
                            }
                            else -> false
                        }
                        if (shouldSkip) return@collect

                        dmRoomMsgRefreshJob?.cancel()
                        dmRoomMsgRefreshJob = launch {
                            delay(120L)
                            repository.getDmMessages(conversationId).onSuccess { msgs ->
                                applyInboxMutation(
                                    extraStateTransform = { state ->
                                        val earliestMs = msgs.firstOrNull()?.createdAt?.let {
                                            ChatMessageMapper.parseInstantMillis(it)
                                        }
                                        val serverIds = msgs.mapTo(HashSet(msgs.size)) { it.id }
                                        val preservedOlder = if (earliestMs != null) {
                                            state.dmMessages.filter { existing ->
                                                existing.localSendStatus == DmSendStatus.SENT &&
                                                    existing.id !in serverIds &&
                                                    (ChatMessageMapper.parseInstantMillis(existing.createdAt) ?: Long.MAX_VALUE) < earliestMs
                                            }
                                        } else {
                                            emptyList()
                                        }
                                        val reconciled = ChatMessageMapper.reconcileOptimisticMessages(
                                            serverMessages = preservedOlder + msgs,
                                            currentLocalMessages = state.dmMessages
                                        )
                                        dmMessagesCache[conversationId] = reconciled
                                        if (reconciled != state.dmMessages) {
                                            state.copy(dmMessages = reconciled)
                                        } else {
                                            state
                                        }
                                    }
                                ) { list ->
                                    list.map { item ->
                                        if (item.effectiveId == conversationId && (item.unreadCount != 0 || item.isMarkedUnread)) {
                                            item.copy(unreadCount = 0, isMarkedUnread = false)
                                        } else item
                                    }
                                }
                                val latest = msgs.lastOrNull()
                                if (latest != null && latest.senderId != myUid && latest.id != lastAutoMarkedReadId) {
                                    lastAutoMarkedReadId = latest.id
                                    repository.markDmRead(conversationId, latest.id)
                                    broadcastDmReadReceipt(conversationId, latest.id)
                                }
                            }
                        }
                    }
                }

                launch {
                    partFlow.collect { action ->
                        if (action is PostgresAction.Update) {
                            val record = action.record
                            val partUserId = record["user_id"]?.jsonPrimitive?.contentOrNull
                            val myUid = repository.currentUserId()
                            if (partUserId != null && partUserId == myUid) {
                                return@collect
                            }
                            val readAt = record["last_read_at"]?.jsonPrimitive?.contentOrNull
                            val readMsgId = record["last_read_message_id"]?.jsonPrimitive?.contentOrNull
                            if (!partUserId.isNullOrBlank() && !readAt.isNullOrBlank()) {
                                val existingDetail = _uiState.value.activeDmDetail
                                val hasParticipant = existingDetail?.participants?.any { it.userId == partUserId } == true
                                if (hasParticipant) {
                                    _uiState.update { state ->
                                        val detail = state.activeDmDetail ?: return@update state
                                        val updatedParticipants = ChatMessageMapper.mergeParticipantReadState(
                                            currentParticipants = detail.participants,
                                            readerUserId = partUserId,
                                            readAtIso = readAt,
                                            lastReadMessageId = readMsgId
                                        )
                                        val nextDetail = detail.copy(participants = updatedParticipants)
                                        dmDetailCache[conversationId] = nextDetail
                                        state.copy(activeDmDetail = nextDetail)
                                    }
                                    return@collect
                                }
                            }
                        }
                        scheduleDetailRefresh()
                    }
                }

                launch {
                    convFlow.collect { action ->
                        if (action is PostgresAction.Update) {
                            val record = action.record
                            val currentConv = _uiState.value.activeDmDetail?.conversation
                            if (currentConv != null) {
                                val nextTitle = record["title"]?.jsonPrimitive?.contentOrNull
                                val nextAvatar = record["avatar_url"]?.jsonPrimitive?.contentOrNull
                                val nextPinnedId = record["pinned_message_id"]?.jsonPrimitive?.contentOrNull
                                if (nextTitle == currentConv.title &&
                                    nextAvatar == currentConv.avatarUrl &&
                                    nextPinnedId == currentConv.pinnedMessageId
                                ) {
                                    return@collect
                                }
                            }
                        }
                        scheduleDetailRefresh()
                    }
                }

                launch {
                    reactionFlow.collect { payload ->
                        val senderId = payload["user_id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                        val targetConv = payload["conversation_id"]?.jsonPrimitive?.contentOrNull
                        if (!targetConv.isNullOrBlank() && targetConv != conversationId) return@collect
                        val myUid = repository.currentUserId()
                        if (senderId.isBlank() || senderId == myUid) return@collect
                        val messageId = payload["message_id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                        val emoji = payload["emoji"]?.jsonPrimitive?.contentOrNull ?: return@collect
                        val prevEmoji = payload["prev_emoji"]?.jsonPrimitive?.contentOrNull
                        val removed = payload["removed"]?.jsonPrimitive?.booleanOrNull ?: false
                        _uiState.update { state ->
                            val currentCounts = (state.dmMessageReactions[messageId] ?: emptyMap()).toMutableMap()
                            if (removed) {
                                val nextVal = (currentCounts[emoji] ?: 1) - 1
                                if (nextVal <= 0) currentCounts.remove(emoji) else currentCounts[emoji] = nextVal
                            } else {
                                if (!prevEmoji.isNullOrBlank() && prevEmoji != emoji) {
                                    val prevVal = (currentCounts[prevEmoji] ?: 1) - 1
                                    if (prevVal <= 0) currentCounts.remove(prevEmoji) else currentCounts[prevEmoji] = prevVal
                                }
                                currentCounts[emoji] = (currentCounts[emoji] ?: 0) + 1
                            }
                            val nextReactions = state.dmMessageReactions.toMutableMap()
                            if (currentCounts.isEmpty()) nextReactions.remove(messageId) else nextReactions[messageId] = currentCounts.toMap()
                            val immutable = nextReactions.toMap()
                            dmReactionsCache[conversationId] = immutable to state.dmMyMessageReactions
                            state.copy(dmMessageReactions = immutable)
                        }
                    }
                }

                launch {
                    readReceiptFlow.collect { payload ->
                        val readerId = payload["user_id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                        val targetConv = payload["conversation_id"]?.jsonPrimitive?.contentOrNull
                        if (!targetConv.isNullOrBlank() && targetConv != conversationId) return@collect
                        val myUid = repository.currentUserId()
                        if (readerId.isBlank() || readerId == myUid) return@collect

                        val readMsgId = payload["last_read_message_id"]?.jsonPrimitive?.contentOrNull
                        val readAtIso = payload["read_at"]?.jsonPrimitive?.contentOrNull ?: Instant.now().toString()
                        _uiState.update { state ->
                            val currentDetail = state.activeDmDetail ?: return@update state
                            val updatedParticipants = app.buildx.chat.ui.chat.ChatMessageMapper.mergeParticipantReadState(
                                currentParticipants = currentDetail.participants,
                                readerUserId = readerId,
                                readAtIso = readAtIso,
                                lastReadMessageId = readMsgId
                            )
                            state.copy(activeDmDetail = currentDetail.copy(participants = updatedParticipants))
                        }
                    }
                }

                launch {
                    typingFlow.collect { payload ->
                        val senderId = payload["user_id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                        val targetConv = payload["conversation_id"]?.jsonPrimitive?.contentOrNull
                        if (!targetConv.isNullOrBlank() && targetConv != conversationId) return@collect
                        val myUid = repository.currentUserId()
                        if (senderId.isBlank() || senderId == myUid) return@collect
                        val isTyping = payload["is_typing"]?.jsonPrimitive?.booleanOrNull ?: true

                        dmTypingExpiryJobs.remove(senderId)?.cancel()
                        if (!isTyping) {
                            _uiState.update { it.copy(dmTypingUserIds = it.dmTypingUserIds - senderId) }
                        } else {
                            _uiState.update { it.copy(dmTypingUserIds = it.dmTypingUserIds + senderId) }
                            dmTypingExpiryJobs[senderId] = launch {
                                delay(3600L)
                                _uiState.update { it.copy(dmTypingUserIds = it.dmTypingUserIds - senderId) }
                                dmTypingExpiryJobs.remove(senderId)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyDetailMonotonic(
        conversationId: String,
        detail: DmConversationDetail,
        allowWhenInactive: Boolean = false
    ) {
        _uiState.update { current ->
            val existingParticipants = (current.activeDmDetail
                ?.takeIf { it.conversation?.id == conversationId }
                ?: dmDetailCache[conversationId])
                ?.participants
                .orEmpty()
            val mergedParticipants = app.buildx.chat.ui.chat.ChatMessageMapper.mergeMonotonicParticipants(
                existing = existingParticipants,
                incoming = detail.participants
            )
            val resolvedDetail = detail.copy(participants = mergedParticipants)
            dmDetailCache[conversationId] = resolvedDetail
            if (!allowWhenInactive && current.activeDmConversationId != conversationId) {
                return@update current
            }
            current.copy(
                activeDmDetail = resolvedDetail,
                activeDmWallpaper = repository.getEffectiveWallpaperLocal(conversationId)
            )
        }
    }

    private fun broadcastDmReadReceipt(conversationId: String, lastReadMessageId: String?) {
        val channel = dmRoomChannel ?: return
        val uid = repository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                channel.broadcast(
                    event = "read_receipt",
                    message = buildJsonObject {
                        put("user_id", uid)
                        put("conversation_id", conversationId)
                        if (!lastReadMessageId.isNullOrBlank()) {
                            put("last_read_message_id", lastReadMessageId)
                        }
                        put("read_at", Instant.now().toString())
                    }
                )
            } catch (_: Exception) {}
        }
    }

    private fun sendDmTypingBroadcast(conversationId: String, isTyping: Boolean) {
        val channel = dmRoomChannel ?: return
        val uid = repository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                channel.broadcast(
                    event = "typing",
                    message = buildJsonObject {
                        put("user_id", uid)
                        put("conversation_id", conversationId)
                        put("is_typing", isTyping)
                    }
                )
            } catch (_: Exception) {}
        }
    }

    fun leaveDmRoom() {
        val prevConvId = _uiState.value.activeDmConversationId
        dmStopTypingJob?.cancel()
        dmTypingExpiryJobs.values.forEach { it.cancel() }
        dmTypingExpiryJobs.clear()
        dmRoomSubscriptionJob?.cancel()
        dmRoomSubscriptionJob = null
        lastDmTypingSentAtMs = 0L
        viewModelScope.launch {
            try {
                if (!prevConvId.isNullOrBlank()) {
                    dmRoomChannel?.broadcast(
                        event = "typing",
                        message = buildJsonObject {
                            put("user_id", repository.currentUserId() ?: "")
                            put("conversation_id", prevConvId)
                            put("is_typing", false)
                        }
                    )
                }
                dmRoomChannel?.unsubscribe()
            } catch (_: Exception) {}
            dmRoomChannel = null
            _uiState.update {
                it.copy(
                    activeDmConversationId = null,
                    activeDmDetail = null,
                    activeDmWallpaper = it.defaultChatWallpaper,
                    dmMessages = emptyList(),
                    dmTypingUserIds = emptySet(),
                    dmComposerText = "",
                    dmStagedAttachments = emptyList(),
                    dmReplyingToMessage = null,
                    dmEditingMessage = null,
                    activeDmMessageMenuId = null,
                    justSentClientTempIds = emptySet(),
                    lastOptimisticMessageId = null,
                    dmMessageReactions = emptyMap(),
                    dmMyMessageReactions = emptyMap()
                )
            }
            loadDmInbox()
        }
    }

    fun toggleDmMessageReaction(messageId: String, emoji: String) {
        if (messageId.isBlank() || emoji.isBlank()) return
        val convId = _uiState.value.activeDmConversationId
        val prevEmoji = _uiState.value.dmMyMessageReactions[messageId]
        val removed = prevEmoji == emoji
        _uiState.update { state ->
            val (nextReactions, nextMyReactions) = ChatMessageMapper.toggleMessageReaction(
                reactionsByMessageId = state.dmMessageReactions,
                myReactionsByMessageId = state.dmMyMessageReactions,
                messageId = messageId,
                emoji = emoji
            )
            if (!convId.isNullOrBlank()) {
                dmReactionsCache[convId] = nextReactions to nextMyReactions
            }
            state.copy(
                dmMessageReactions = nextReactions,
                dmMyMessageReactions = nextMyReactions
            )
        }
        if (!convId.isNullOrBlank()) {
            broadcastDmReaction(
                conversationId = convId,
                messageId = messageId,
                emoji = emoji,
                prevEmoji = prevEmoji,
                removed = removed
            )
        }
    }

    private fun broadcastDmReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
        prevEmoji: String?,
        removed: Boolean
    ) {
        val channel = dmRoomChannel ?: return
        val uid = repository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                channel.broadcast(
                    event = "reaction",
                    message = buildJsonObject {
                        put("user_id", uid)
                        put("conversation_id", conversationId)
                        put("message_id", messageId)
                        put("emoji", emoji)
                        if (!prevEmoji.isNullOrBlank()) {
                            put("prev_emoji", prevEmoji)
                        }
                        put("removed", removed)
                    }
                )
            } catch (_: Exception) {}
        }
    }

    fun onDmComposerTextChanged(text: String) {
        val convId = _uiState.value.activeDmConversationId
        val isEditing = _uiState.value.dmEditingMessage != null
        if (!convId.isNullOrBlank()) {
            if (!isEditing) {
                repository.prefs.edit().putString("dm_draft_$convId", text).apply()
            }
            dmStopTypingJob?.cancel()
            if (text.isNotBlank()) {
                val now = System.currentTimeMillis()
                if (now - lastDmTypingSentAtMs >= 1800L) {
                    lastDmTypingSentAtMs = now
                    sendDmTypingBroadcast(convId, isTyping = true)
                }
                dmStopTypingJob = viewModelScope.launch {
                    delay(2600L)
                    lastDmTypingSentAtMs = 0L
                    sendDmTypingBroadcast(convId, isTyping = false)
                }
            } else if (lastDmTypingSentAtMs != 0L) {
                lastDmTypingSentAtMs = 0L
                sendDmTypingBroadcast(convId, isTyping = false)
            }
        }
        _uiState.update { state ->
            val updatedDrafts = if (!convId.isNullOrBlank() && !isEditing) {
                state.dmDraftsByConversation + (convId to text)
            } else {
                state.dmDraftsByConversation
            }
            state.copy(dmComposerText = text, dmDraftsByConversation = updatedDrafts)
        }
    }

    fun consumeJustSentAnimation(clientTempId: String) {
        _uiState.update { state ->
            state.copy(
                justSentClientTempIds = state.justSentClientTempIds - clientTempId,
                lastOptimisticMessageId = if (state.lastOptimisticMessageId == clientTempId) null else state.lastOptimisticMessageId
            )
        }
    }

    fun addDmStagedAttachment(attachment: StagedAttachment) {
        _uiState.update { it.copy(dmStagedAttachments = it.dmStagedAttachments + attachment) }
    }

    fun removeDmStagedAttachment(attachment: StagedAttachment) {
        _uiState.update { it.copy(dmStagedAttachments = it.dmStagedAttachments - attachment) }
    }

    fun setDmReplyingTo(message: DmMessage?) {
        _uiState.update { it.copy(dmReplyingToMessage = message, dmEditingMessage = null) }
    }

    fun setDmEditing(message: DmMessage?) {
        _uiState.update { state ->
            val convId = state.activeDmConversationId
            val restoredComposerText = if (message != null) {
                message.content
            } else if (!convId.isNullOrBlank()) {
                state.dmDraftsByConversation[convId]
                    ?: repository.prefs.getString("dm_draft_$convId", "")
                    ?: ""
            } else {
                ""
            }
            state.copy(
                dmEditingMessage = message,
                dmReplyingToMessage = null,
                dmComposerText = restoredComposerText
            )
        }
    }

    fun setActiveDmMessageMenuId(messageId: String?) {
        _uiState.update { it.copy(activeDmMessageMenuId = messageId) }
    }

    fun openNoteReply(note: FeedNoteItem) {
        _uiState.update { it.copy(activeReplyTargetNote = note) }
    }

    fun closeNoteReply() {
        _uiState.update { it.copy(activeReplyTargetNote = null) }
    }

    fun sendDmMessage(conversationId: String) {
        val state = uiState.value
        val text = state.dmComposerText.trim()
        val staged = state.dmStagedAttachments
        val replyingTo = state.dmReplyingToMessage
        val editing = state.dmEditingMessage

        if (editing != null) {
            if (text.isBlank()) return
            viewModelScope.launch {
                val editedAtIso = Instant.now().toString()
                repository.editDmMessage(editing.id, text).fold(
                    onSuccess = {
                        applyInboxMutation(
                            extraStateTransform = { s ->
                                val updatedMsgs = ChatMessageMapper.applyMessageEditToHistory(
                                    messages = dmMessagesCache[conversationId] ?: s.dmMessages,
                                    messageId = editing.id,
                                    newContent = text,
                                    editedAtIso = editedAtIso
                                )
                                dmMessagesCache[conversationId] = updatedMsgs
                                val updatedDetail = s.activeDmDetail?.let { detail ->
                                    if (detail.pinnedMessage?.id == editing.id) {
                                        val next = detail.copy(
                                            pinnedMessage = detail.pinnedMessage.copy(content = text, editedAt = editedAtIso)
                                        )
                                        dmDetailCache[conversationId] = next
                                        next
                                    } else detail
                                }
                                val restoredDraft = s.dmDraftsByConversation[conversationId]
                                    ?: repository.prefs.getString("dm_draft_$conversationId", "")
                                    ?: ""
                                s.copy(
                                    dmEditingMessage = null,
                                    dmComposerText = restoredDraft,
                                    dmMessages = if (s.activeDmConversationId == conversationId) updatedMsgs else s.dmMessages,
                                    activeDmDetail = updatedDetail
                                )
                            }
                        ) { list ->
                            list.map { item ->
                                if (item.effectiveId == conversationId && item.effectiveLastMessageId == editing.id) {
                                    item.copy(
                                        lastMessageText = text,
                                        lastMessage = item.lastMessage?.copy(content = text)
                                    )
                                } else item
                            }
                        }
                    },
                    onFailure = { err ->
                        showSnackbar(mapError(err))
                    }
                )
            }
            return
        }

        if (text.isBlank() && staged.isEmpty()) return

        dmStopTypingJob?.cancel()
        if (lastDmTypingSentAtMs != 0L) {
            lastDmTypingSentAtMs = 0L
            sendDmTypingBroadcast(conversationId, isTyping = false)
        }

        val myProfile = state.profile
        val senderUid = myProfile?.id?.takeIf { it.isNotBlank() } ?: repository.currentUserId().orEmpty()
        val clientTempId = UUID.randomUUID().toString()
        val tempId = UUID.randomUUID().toString()
        inFlightClientTempIds.add(clientTempId)
        val lastKnownMsgMs = state.dmMessages.lastOrNull()?.createdAt?.let {
            ChatMessageMapper.parseInstantMillis(it)
        } ?: 0L
        val nowIso = Instant.ofEpochMilli(maxOf(System.currentTimeMillis(), lastKnownMsgMs + 1L)).toString()
        val primaryAttachment = staged.firstOrNull()
        if (primaryAttachment != null) {
            dmFailedStagedAttachmentByTempId[tempId] = primaryAttachment
        }

        val optimisticMsg = DmMessage(
            id = tempId,
            conversationId = conversationId,
            senderId = senderUid,
            senderUsername = myProfile?.username,
            senderHandle = myProfile?.handle,
            senderDisplayName = myProfile?.displayName,
            senderAvatarUrl = myProfile?.avatarUrl,
            senderIsVerified = myProfile?.isVerified ?: false,
            messageType = if (staged.isNotEmpty()) "media" else "text",
            content = text,
            replyTo = replyingTo?.let {
                DmReplyPreview(
                    id = it.id,
                    senderId = it.senderId,
                    senderDisplayName = it.senderDisplayName,
                    content = it.content,
                    attachmentMime = it.attachmentMime,
                    isDeleted = it.isDeleted
                )
            },
            attachmentPath = primaryAttachment?.uri?.toString(),
            attachmentMime = primaryAttachment?.mimeType,
            attachmentSize = primaryAttachment?.sizeBytes,
            clientTempId = clientTempId,
            createdAt = nowIso,
            localSendStatus = DmSendStatus.SENDING
        )

        repository.prefs.edit().remove("dm_draft_$conversationId").apply()
        val optimisticPreviewText = text.ifBlank {
            when {
                primaryAttachment?.mimeType?.startsWith("image/") == true -> "Photo"
                primaryAttachment?.mimeType?.startsWith("video/") == true -> "Video"
                primaryAttachment != null -> "Attachment"
                else -> ""
            }
        }
        applyInboxMutation(
            extraStateTransform = { s ->
                val baseMsgs = dmMessagesCache[conversationId] ?: s.dmMessages
                val nextMsgs = baseMsgs + optimisticMsg
                dmMessagesCache[conversationId] = nextMsgs
                s.copy(
                    dmMessages = if (s.activeDmConversationId == conversationId) nextMsgs else s.dmMessages,
                    dmComposerText = "",
                    dmDraftsByConversation = s.dmDraftsByConversation - conversationId,
                    justSentClientTempIds = s.justSentClientTempIds + clientTempId,
                    lastOptimisticMessageId = clientTempId,
                    dmStagedAttachments = emptyList(),
                    dmReplyingToMessage = null
                )
            }
        ) { list ->
            list.map { item ->
                if (item.effectiveId == conversationId) {
                    item.copy(
                        unreadCount = 0,
                        isMarkedUnread = false,
                        lastMessageId = tempId,
                        lastMessageText = optimisticPreviewText,
                        lastMessageType = optimisticMsg.messageType,
                        lastMessageSenderId = optimisticMsg.senderId,
                        lastMessageAt = nowIso,
                        lastActivityAt = nowIso
                    )
                } else item
            }
        }

        dispatchDmSend(
            conversationId = conversationId,
            text = text,
            replyToMessageId = replyingTo?.id,
            staged = staged,
            tempId = tempId,
            clientTempId = clientTempId
        )
    }

    fun retryFailedDmMessage(conversationId: String, failedMessage: DmMessage) {
        if (failedMessage.localSendStatus != DmSendStatus.FAILED) return
        val tempId = failedMessage.id
        val clientTempId = failedMessage.clientTempId ?: tempId
        inFlightClientTempIds.add(clientTempId)
        val retryStaged = dmFailedStagedAttachmentByTempId[tempId]?.let { listOf(it) }.orEmpty()

        _uiState.update { state ->
            val updated = state.dmMessages.map { m ->
                if (m.id == tempId || m.clientTempId == clientTempId) {
                    m.copy(localSendStatus = DmSendStatus.SENDING)
                } else m
            }
            dmMessagesCache[conversationId] = updated
            state.copy(dmMessages = updated)
        }

        dispatchDmSend(
            conversationId = conversationId,
            text = failedMessage.content,
            replyToMessageId = failedMessage.replyTo?.id,
            staged = retryStaged,
            tempId = tempId,
            clientTempId = clientTempId
        )
    }

    private fun markOptimisticMessageFailed(conversationId: String, tempId: String, clientTempId: String) {
        _uiState.update { state ->
            val base = dmMessagesCache[conversationId] ?: state.dmMessages
            val updated = base.map { m ->
                if (m.clientTempId == clientTempId || m.id == tempId) {
                    m.copy(localSendStatus = DmSendStatus.FAILED)
                } else m
            }
            dmMessagesCache[conversationId] = updated
            state.copy(
                dmMessages = if (state.activeDmConversationId == conversationId) updated else state.dmMessages
            )
        }
    }

    private fun dispatchDmSend(
        conversationId: String,
        text: String,
        replyToMessageId: String?,
        staged: List<StagedAttachment>,
        tempId: String,
        clientTempId: String
    ) {
        viewModelScope.launch {
            var uploadedUrl: String? = null
            var mime: String? = null
            var size: Long? = null
            if (staged.isNotEmpty()) {
                val first = staged.first()
                mime = first.mimeType
                size = first.sizeBytes
                val uploadResult = repository.uploadDmAttachment(first)
                if (uploadResult.isFailure) {
                    inFlightClientTempIds.remove(clientTempId)
                    markOptimisticMessageFailed(conversationId, tempId, clientTempId)
                    showSnackbar(mapError(uploadResult.exceptionOrNull() ?: IllegalStateException("Upload failed")))
                    return@launch
                }
                uploadedUrl = uploadResult.getOrNull()
            }

            repository.sendDmMessage(
                conversationId = conversationId,
                content = text,
                replyToMessageId = replyToMessageId,
                attachmentPath = uploadedUrl,
                attachmentMime = mime,
                attachmentSize = size,
                clientTempId = clientTempId
            ).fold(
                onSuccess = { realMsg ->
                    inFlightClientTempIds.remove(clientTempId)
                    recentlySentClientTempIds.add(clientTempId)
                    if (recentlySentClientTempIds.size > 64) {
                        synchronized(recentlySentClientTempIds) {
                            if (recentlySentClientTempIds.size > 64) {
                                recentlySentClientTempIds.clear()
                                recentlySentClientTempIds.add(clientTempId)
                            }
                        }
                    }
                    dmFailedStagedAttachmentByTempId.remove(tempId)
                    val finalized = realMsg.copy(
                        clientTempId = realMsg.clientTempId ?: clientTempId,
                        localSendStatus = DmSendStatus.SENT
                    )
                    val hadInboxRow = _uiState.value.dmInboxItems.any { it.effectiveId == conversationId }
                    applyInboxMutation(
                        extraStateTransform = { state ->
                            val base = dmMessagesCache[conversationId] ?: state.dmMessages
                            val updated = base.map { m ->
                                if (m.clientTempId == clientTempId || m.id == tempId) finalized else m
                            }
                            dmMessagesCache[conversationId] = updated
                            val migratedReactions = state.dmMessageReactions.toMutableMap().apply {
                                remove(tempId)?.let { putIfAbsent(finalized.id, it) }
                                remove(clientTempId)?.let { putIfAbsent(finalized.id, it) }
                            }.toMap()
                            val migratedMyReactions = state.dmMyMessageReactions.toMutableMap().apply {
                                remove(tempId)?.let { putIfAbsent(finalized.id, it) }
                                remove(clientTempId)?.let { putIfAbsent(finalized.id, it) }
                            }.toMap()
                            if (migratedReactions != state.dmMessageReactions || migratedMyReactions != state.dmMyMessageReactions) {
                                dmReactionsCache[conversationId] = migratedReactions to migratedMyReactions
                            }
                            state.copy(
                                dmMessages = if (state.activeDmConversationId == conversationId) updated else state.dmMessages,
                                dmMessageReactions = if (state.activeDmConversationId == conversationId) migratedReactions else state.dmMessageReactions,
                                dmMyMessageReactions = if (state.activeDmConversationId == conversationId) migratedMyReactions else state.dmMyMessageReactions
                            )
                        }
                    ) { list ->
                        list.map { item ->
                            if (item.effectiveId == conversationId) {
                                item.copy(
                                    lastMessageId = finalized.id,
                                    lastMessageText = finalized.content.ifBlank { item.lastMessageText },
                                    lastMessageType = finalized.messageType,
                                    lastMessageSenderId = finalized.senderId,
                                    lastMessageAt = finalized.createdAt,
                                    lastActivityAt = finalized.createdAt,
                                    lastMessage = item.lastMessage?.copy(
                                        id = finalized.id,
                                        senderId = finalized.senderId,
                                        messageType = finalized.messageType,
                                        content = finalized.content,
                                        createdAt = finalized.createdAt
                                    ),
                                    unreadCount = 0,
                                    isMarkedUnread = false
                                )
                            } else item
                        }
                    }
                    if (!hadInboxRow) {
                        loadDmInbox()
                    }
                },
                onFailure = {
                    inFlightClientTempIds.remove(clientTempId)
                    markOptimisticMessageFailed(conversationId, tempId, clientTempId)
                }
            )
        }
    }

    fun sendNoteReplyMessage(note: FeedNoteItem, replyText: String, onSent: (String) -> Unit) {
        val text = replyText.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.getOrCreateDirectConversation(note.author.id).fold(
                onSuccess = { convId ->
                    repository.sendDmMessage(
                        conversationId = convId,
                        content = text,
                        replyToNoteId = note.id
                    ).fold(
                        onSuccess = {
                            _uiState.update { it.copy(activeReplyTargetNote = null) }
                            loadDmInbox()
                            onSent(convId)
                        },
                        onFailure = { err ->
                            showSnackbar(mapError(err))
                        }
                    )
                },
                onFailure = { err ->
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun deleteDmMessage(messageId: String) {
        val stateSnapshot = _uiState.value
        val activeConvId = stateSnapshot.activeDmConversationId
        val targetLocal = stateSnapshot.dmMessages.firstOrNull { it.id == messageId }
        if (targetLocal != null && targetLocal.localSendStatus == DmSendStatus.FAILED) {
            dmFailedStagedAttachmentByTempId.remove(targetLocal.id)
            if (activeConvId != null) {
                val remaining = (dmMessagesCache[activeConvId] ?: stateSnapshot.dmMessages).filterNot { it.id == messageId }
                dmMessagesCache[activeConvId] = remaining
                _uiState.update { it.copy(dmMessages = remaining) }
            }
            return
        }

        viewModelScope.launch {
            repository.deleteDmMessage(messageId).fold(
                onSuccess = {
                    val deletedAtIso = Instant.now().toString()
                    val convId = targetLocal?.conversationId?.takeIf { it.isNotBlank() } ?: activeConvId
                    applyInboxMutation(
                        extraStateTransform = { s ->
                            val updatedMsgs = ChatMessageMapper.applyMessageDeleteToHistory(
                                messages = if (convId != null) (dmMessagesCache[convId] ?: s.dmMessages) else s.dmMessages,
                                messageId = messageId,
                                deletedAtIso = deletedAtIso
                            )
                            if (convId != null) {
                                dmMessagesCache[convId] = updatedMsgs
                            }
                            val updatedDetail = s.activeDmDetail?.let { detail ->
                                if (detail.pinnedMessage?.id == messageId) {
                                    val next = detail.copy(pinnedMessage = null)
                                    if (convId != null) dmDetailCache[convId] = next
                                    next
                                } else detail
                            }
                            s.copy(
                                dmMessages = if (convId == null || s.activeDmConversationId == convId) updatedMsgs else s.dmMessages,
                                activeDmDetail = updatedDetail,
                                dmEditingMessage = if (s.dmEditingMessage?.id == messageId) null else s.dmEditingMessage,
                                dmReplyingToMessage = if (s.dmReplyingToMessage?.id == messageId) null else s.dmReplyingToMessage
                            )
                        }
                    ) { list ->
                        list.map { item ->
                            if (item.effectiveLastMessageId == messageId) {
                                item.copy(
                                    lastMessageText = "",
                                    lastMessageType = "deleted",
                                    lastMessage = item.lastMessage?.copy(
                                        content = "",
                                        messageType = "deleted",
                                        deletedAt = deletedAtIso
                                    )
                                )
                            } else item
                        }
                    }
                    loadDmInbox()
                },
                onFailure = { err ->
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun togglePinDmMessage(conversationId: String, messageId: String?) {
        val prevDetail = _uiState.value.activeDmDetail
        val optimisticPinned = messageId?.let { targetId ->
            _uiState.value.dmMessages.firstOrNull { it.id == targetId }
                ?: dmMessagesCache[conversationId]?.firstOrNull { it.id == targetId }
        }
        _uiState.update { state ->
            val detail = state.activeDmDetail
            if (detail?.conversation?.id == conversationId) {
                val next = detail.copy(pinnedMessage = optimisticPinned)
                dmDetailCache[conversationId] = next
                state.copy(activeDmDetail = next)
            } else state
        }
        viewModelScope.launch {
            repository.togglePinDmMessage(conversationId, messageId).fold(
                onSuccess = {
                    repository.getDmConversationDetail(conversationId).onSuccess { detail ->
                        applyDetailMonotonic(conversationId, detail, allowWhenInactive = true)
                    }
                },
                onFailure = { err ->
                    if (prevDetail != null && prevDetail.conversation?.id == conversationId) {
                        dmDetailCache[conversationId] = prevDetail
                        _uiState.update { it.copy(activeDmDetail = prevDetail) }
                    }
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun toggleMuteDmConversation(conversationId: String) {
        val currentUserId = repository.currentUserId()
        val detailMuted = _uiState.value.activeDmDetail?.let { d ->
            if (d.conversation?.id == conversationId) {
                d.participants.firstOrNull { it.userId == currentUserId }?.isMuted
            } else null
        }
        val inboxMuted = _uiState.value.dmInboxItems.firstOrNull { it.effectiveId == conversationId }?.isMuted
        val currentlyMuted = detailMuted ?: inboxMuted ?: false
        val targetMuted = !currentlyMuted

        viewModelScope.launch {
            applyInboxMutation(
                extraStateTransform = { state ->
                    val updatedDetail = state.activeDmDetail?.let { d ->
                        if (d.conversation?.id == conversationId) {
                            val nextDetail = d.copy(
                                participants = d.participants.map { p ->
                                    if (p.userId == currentUserId) p.copy(isMuted = targetMuted) else p
                                }
                            )
                            dmDetailCache[conversationId] = nextDetail
                            nextDetail
                        } else d
                    }
                    state.copy(activeDmDetail = updatedDetail)
                }
            ) { list ->
                list.map { item ->
                    if (item.effectiveId == conversationId) {
                        item.copy(isMuted = targetMuted)
                    } else item
                }
            }
            showSnackbar(if (targetMuted) "Conversation muted" else "Conversation unmuted")
            repository.toggleMuteDmConversation(conversationId, isCurrentlyMuted = currentlyMuted).onFailure {
                loadDmInbox()
                if (_uiState.value.activeDmDetail?.conversation?.id == conversationId) {
                    repository.getDmConversationDetail(conversationId).onSuccess { detail ->
                        applyDetailMonotonic(conversationId, detail, allowWhenInactive = true)
                    }
                }
            }
        }
    }

    fun togglePinDmConversation(conversationId: String) {
        val currentUserId = repository.currentUserId()
        val detailPinned = _uiState.value.activeDmDetail?.let { d ->
            if (d.conversation?.id == conversationId) {
                d.participants.firstOrNull { it.userId == currentUserId }?.isPinned
            } else null
        }
        val inboxPinned = _uiState.value.dmInboxItems.firstOrNull { it.effectiveId == conversationId }?.isPinned
        setConversationPin(conversationId, !(detailPinned ?: inboxPinned ?: false))
    }

    fun setConversationPin(conversationId: String, pin: Boolean) {
        val canonicalList = dmAllInboxCache.ifEmpty { _uiState.value.dmInboxItems }
        val currentPinnedCount = canonicalList.count { it.isPinned }
        if (pin && currentPinnedCount >= 3) {
            showSnackbar("You can pin up to 3 conversations")
            return
        }
        val currentUserId = repository.currentUserId()
        viewModelScope.launch {
            val nowIso = Instant.now().toString()
            applyInboxMutation(
                extraStateTransform = { state ->
                    val updatedDetail = state.activeDmDetail?.let { d ->
                        if (d.conversation?.id == conversationId) {
                            val nextDetail = d.copy(
                                participants = d.participants.map { p ->
                                    if (p.userId == currentUserId) p.copy(isPinned = pin) else p
                                }
                            )
                            dmDetailCache[conversationId] = nextDetail
                            nextDetail
                        } else d
                    }
                    state.copy(activeDmDetail = updatedDetail)
                }
            ) { list ->
                list.map { item ->
                    if (item.effectiveId == conversationId) {
                        item.copy(isPinned = pin, pinnedAt = if (pin) nowIso else null)
                    } else item
                }.sortedWith(
                    compareByDescending<DmInboxItem> { it.isPinned }
                        .thenByDescending { it.pinnedAt ?: "" }
                        .thenByDescending { it.lastActivityAt ?: it.lastMessageAt ?: "" }
                )
            }
            repository.setConversationPin(conversationId, pin).fold(
                onSuccess = {
                    showSnackbar(if (pin) "Conversation pinned" else "Conversation unpinned")
                },
                onFailure = { err ->
                    if (err.message?.contains("PIN_LIMIT_REACHED") == true) {
                        showSnackbar("You can pin up to 3 conversations")
                    } else {
                        showSnackbar("Could not update pin status")
                    }
                    loadDmInbox()
                }
            )
        }
    }

    fun setConversationMute(conversationId: String, duration: String?) {
        val untilIso = when (duration) {
            "1h" -> java.time.Instant.now().plus(java.time.Duration.ofHours(1)).toString()
            "8h" -> java.time.Instant.now().plus(java.time.Duration.ofHours(8)).toString()
            "1w" -> java.time.Instant.now().plus(java.time.Duration.ofDays(7)).toString()
            "infinity" -> "infinity"
            else -> null
        }
        val isMuted = untilIso != null
        val currentUserId = repository.currentUserId()
        viewModelScope.launch {
            applyInboxMutation(
                extraStateTransform = { state ->
                    val updatedDetail = state.activeDmDetail?.let { d ->
                        if (d.conversation?.id == conversationId) {
                            val nextDetail = d.copy(
                                participants = d.participants.map { p ->
                                    if (p.userId == currentUserId) p.copy(isMuted = isMuted) else p
                                }
                            )
                            dmDetailCache[conversationId] = nextDetail
                            nextDetail
                        } else d
                    }
                    state.copy(activeDmDetail = updatedDetail)
                }
            ) { list ->
                list.map { item ->
                    if (item.effectiveId == conversationId) {
                        item.copy(isMuted = isMuted, mutedUntil = untilIso)
                    } else item
                }
            }
            repository.setConversationMute(conversationId, untilIso).fold(
                onSuccess = {
                    showSnackbar(if (isMuted) "Notifications muted" else "Notifications unmuted")
                },
                onFailure = {
                    loadDmInbox()
                }
            )
        }
    }

    fun deleteConversationForMe(conversationId: String) {
        viewModelScope.launch {
            dmMessagesCache.remove(conversationId)
            dmDetailCache.remove(conversationId)
            applyInboxMutation(
                extraStateTransform = { state ->
                    state.copy(
                        dmMessages = if (state.activeDmConversationId == conversationId) emptyList() else state.dmMessages,
                        activeDmDetail = if (state.activeDmDetail?.conversation?.id == conversationId) null else state.activeDmDetail
                    )
                }
            ) { list ->
                list.filterNot { it.effectiveId == conversationId }
            }
            repository.deleteConversationForMe(conversationId).fold(
                onSuccess = { showSnackbar("Chat deleted") },
                onFailure = { err ->
                    showSnackbar(mapError(err))
                    loadDmInbox()
                }
            )
        }
    }

    fun leaveGroup(conversationId: String) {
        viewModelScope.launch {
            dmMessagesCache.remove(conversationId)
            dmDetailCache.remove(conversationId)
            applyInboxMutation(
                extraStateTransform = { state ->
                    state.copy(
                        dmMessages = if (state.activeDmConversationId == conversationId) emptyList() else state.dmMessages,
                        activeDmDetail = if (state.activeDmDetail?.conversation?.id == conversationId) null else state.activeDmDetail
                    )
                }
            ) { list ->
                list.filterNot { it.effectiveId == conversationId }
            }
            repository.leaveGroup(conversationId).fold(
                onSuccess = { showSnackbar("Left group") },
                onFailure = { err ->
                    showSnackbar(mapError(err))
                    loadDmInbox()
                }
            )
        }
    }

    fun updateGroupTitle(conversationId: String, title: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    dmInboxItems = state.dmInboxItems.map { item ->
                        if (item.effectiveId == conversationId) item.copy(title = title) else item
                    },
                    activeDmDetail = state.activeDmDetail?.let { d ->
                        if (d.conversation?.id == conversationId) {
                            d.copy(conversation = d.conversation?.copy(title = title))
                        } else d
                    }
                )
            }
            repository.updateGroupInfo(conversationId, title).fold(
                onSuccess = { showSnackbar("Group name updated") },
                onFailure = { err -> showSnackbar(mapError(err)); loadDmInbox() }
            )
        }
    }

    /**
     * Uploads and applies a new group conversation photo, then refreshes both
     * the open conversation detail and the inbox row so the new image appears
     * immediately everywhere.
     */
    fun updateGroupAvatar(conversationId: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGroupAvatarUploading = true) }
            val currentTitle = _uiState.value.activeDmDetail?.conversation?.title
                ?: _uiState.value.dmInboxItems.firstOrNull { it.effectiveId == conversationId }?.title
                ?: ""
            repository.uploadGroupAvatar(conversationId, uri).fold(
                onSuccess = { url ->
                    repository.updateGroupInfo(conversationId, currentTitle, url).fold(
                        onSuccess = {
                            _uiState.update { state ->
                                state.copy(
                                    isGroupAvatarUploading = false,
                                    activeDmDetail = state.activeDmDetail?.copy(
                                        conversation = state.activeDmDetail?.conversation?.copy(avatarUrl = url)
                                    ),
                                    dmInboxItems = state.dmInboxItems.map { item ->
                                        if (item.effectiveId == conversationId) item.copy(avatarUrl = url) else item
                                    }
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(isGroupAvatarUploading = false) }
                            showSnackbar(mapError(err))
                        }
                    )
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isGroupAvatarUploading = false) }
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    /**
     * Adds members to a group and refreshes the open conversation detail so the
     * participant list updates immediately.
     */
    fun addGroupMembers(conversationId: String, memberIds: List<String>) {
        if (memberIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingGroupMembers = true) }
            repository.addGroupMembers(conversationId, memberIds).fold(
                onSuccess = {
                    repository.getDmConversationDetail(conversationId).onSuccess { detail ->
                        _uiState.update {
                            it.copy(
                                activeDmDetail = detail,
                                isAddingGroupMembers = false
                            )
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isAddingGroupMembers = false) }
                    }
                    loadDmInbox()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAddingGroupMembers = false) }
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun removeGroupMember(conversationId: String, targetUserId: String) {
        viewModelScope.launch {
            repository.removeOrLeaveGroupMember(conversationId, targetUserId).fold(
                onSuccess = {
                    repository.getDmConversationDetail(conversationId).onSuccess { detail ->
                        _uiState.update { it.copy(activeDmDetail = detail) }
                    }
                    loadDmInbox()
                    showSnackbar("Member removed")
                },
                onFailure = { err ->
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    /**
     * Lazily loads the most recent device photos for the DM attachment sheet.
     */
    fun loadRecentDeviceImagesForDm() {
        viewModelScope.launch {
            val recent = repository.queryRecentDeviceImages(limit = 12)
            _uiState.update { it.copy(recentDeviceImages = recent) }
        }
    }

    /**
     * Resolves an effective wallpaper for a chat room (or falls back to global default).
     */
    fun getEffectiveWallpaper(conversationId: String?): ChatWallpaperConfig =
        repository.getEffectiveWallpaperLocal(conversationId)

    /**
     * Uploads a custom image if provided and persists wallpaper preferences via
     * `set_chat_wallpaper`, updating UI state only when the backend RPC succeeds.
     */
    fun updateChatWallpaper(
        conversationId: String?,
        pickedImageUri: Uri?,
        config: ChatWallpaperConfig,
        applyToAll: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWallpaperUpdating = true) }
            var finalConfig = config.copy(
                wallpaperId = config.normalizedId,
                dim = config.dim.coerceIn(0f, 0.70f)
            )
            if (pickedImageUri != null) {
                val uploadResult = repository.uploadCustomWallpaper(conversationId, pickedImageUri)
                if (uploadResult.isFailure) {
                    _uiState.update { it.copy(isWallpaperUpdating = false) }
                    uploadResult.exceptionOrNull()?.let { showSnackbar(mapError(it)) }
                    return@launch
                }
                val (remoteUrl, _) = uploadResult.getOrThrow()
                finalConfig = finalConfig.copy(
                    wallpaperId = "custom",
                    customUrl = remoteUrl
                )
            }

            val targetConvId = conversationId?.takeIf { it.isNotBlank() } ?: _uiState.value.activeDmConversationId
            val effectiveApplyAll = applyToAll || targetConvId.isNullOrBlank()
            repository.setChatWallpaper(
                conversationId = targetConvId,
                wallpaperId = finalConfig.normalizedId,
                customUrl = finalConfig.customUrl,
                dim = finalConfig.dim,
                blur = finalConfig.blur,
                applyToAll = effectiveApplyAll
            ).fold(
                onSuccess = { saved ->
                    _uiState.update { state ->
                        val nextDefault = if (effectiveApplyAll) saved else state.defaultChatWallpaper
                        val nextActive = if (effectiveApplyAll || targetConvId == state.activeDmConversationId) {
                            saved
                        } else {
                            state.activeDmWallpaper
                        }
                        state.copy(
                            defaultChatWallpaper = nextDefault,
                            activeDmWallpaper = nextActive,
                            activeDmDetail = state.activeDmDetail?.let { d ->
                                if (d.conversation?.id == targetConvId || effectiveApplyAll) {
                                    d.copy(
                                        wallpaperId = saved.normalizedId,
                                        wallpaperCustomUrl = saved.customUrl,
                                        wallpaperDim = saved.dim,
                                        wallpaperBlur = saved.blur
                                    )
                                } else d
                            },
                            isWallpaperUpdating = false
                        )
                    }
                    val msgRes = if (effectiveApplyAll) {
                        R.string.dm_wallpaper_applied_all
                    } else {
                        R.string.dm_wallpaper_applied_chat
                    }
                    showSnackbar(getApplication<Application>().getString(msgRes))
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isWallpaperUpdating = false) }
                    showSnackbar(mapError(err))
                }
            )
        }
    }

    fun currentUserId(): String? = repository.currentUserId()

    companion object {
        fun isNetworkRefreshFailure(cause: RefreshFailureCause): Boolean {
            return cause is RefreshFailureCause.NetworkError ||
                cause is RefreshFailureCause.InternalServerError
        }
    }
}
