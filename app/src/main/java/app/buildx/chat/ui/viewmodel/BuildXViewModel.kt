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
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import app.buildx.chat.data.model.AppNotificationItem
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.ChatSseEvent
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.FollowUserItem
import app.buildx.chat.data.model.MainSurfacePane
import app.buildx.chat.data.model.NotificationRow
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.data.model.VerificationStatusInfo
import app.buildx.chat.data.repository.BuildXRepository
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class BuildXUiState(
    val isAuthenticated: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authError: String? = null,
    val profile: UserProfile? = null,
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
    val isPublicProfileLoading: Boolean = false,
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
    val isVerificationSubmitting: Boolean = false
)

class BuildXViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BuildXRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        BuildXUiState(
            appearance = repository.getSavedAppearance(),
            language = repository.getSavedLanguage()
        )
    )
    val uiState: StateFlow<BuildXUiState> = _uiState.asStateFlow()

    private var activeStreamJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            repository.awaitAuthInitialization()
            val uid = repository.currentUserId()
            if (uid != null) {
                runCatching {
                    val profile = repository.ensureUserProfile()
                    val resolvedAppearance = AppAppearance.fromDb(profile.appearance)
                    repository.saveAppearanceLocal(resolvedAppearance)
                    val showReview = profile.usernameReviewedAt == null
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            profile = profile,
                            appearance = resolvedAppearance,
                            showUsernameReviewSheet = showReview
                        )
                    }
                    refreshConversationsAndAttachments()
                    loadInAppNotifications()
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        signInWithIdentifier(email, password)
    }

    fun signInWithIdentifier(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "Please enter your username or email and password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = repository.signInWithIdentifier(identifier, password)
            result.onSuccess { profile ->
                val resolvedAppearance = AppAppearance.fromDb(profile.appearance)
                repository.saveAppearanceLocal(resolvedAppearance)
                _uiState.update {
                    it.copy(
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
                        authError = err.message ?: "Sign in failed. Check your credentials."
                    )
                }
            }
        }
    }

    fun signUp(email: String, password: String, displayName: String, handle: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = repository.signUpWithEmail(email, password, displayName, handle)
            result.onSuccess { profile ->
                val resolvedAppearance = AppAppearance.fromDb(profile.appearance)
                repository.saveAppearanceLocal(resolvedAppearance)
                _uiState.update {
                    it.copy(
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
                        authError = err.message ?: "Account creation failed."
                    )
                }
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = repository.signInWithGoogle()
            result.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = err.message ?: "Google sign in failed."
                    )
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
                _uiState.update { it.copy(isVerificationLoading = false, verificationError = err.message) }
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
                _uiState.update { it.copy(isVerificationSubmitting = false, verificationError = err.message) }
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
                _uiState.update { it.copy(isVerificationSubmitting = false, verificationError = err.message) }
            }
        }
    }

    fun handleAuthDeeplink(intent: Intent) {
        viewModelScope.launch {
            runCatching {
                repository.supabase.handleDeeplinks(intent) {
                    checkExistingSession()
                }
            }
        }
    }



    fun signOut() {
        activeStreamJob?.cancel()
        viewModelScope.launch {
            repository.signOut()
            _uiState.value = BuildXUiState(
                isAuthenticated = false,
                appearance = repository.getSavedAppearance(),
                language = repository.getSavedLanguage()
            )
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
                                }
                                refreshConversationsAndAttachments()
                            }
                        }

                        is ChatSseEvent.ErrorEvent -> {
                            _uiState.update { s ->
                                val updatedMsgs = s.messages.map { msg ->
                                    if (msg.id == tempAssistantMsgId) {
                                        msg.copy(
                                            content = if (msg.content.isBlank()) event.message else msg.content,
                                            status = "error"
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                s.copy(
                                    messages = updatedMsgs,
                                    isStreaming = false,
                                    snackbarMessage = event.message
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
                _uiState.update { s ->
                    val updatedMsgs = s.messages.map { msg ->
                        if (msg.id == tempAssistantMsgId) {
                            msg.copy(
                                content = if (msg.content.isBlank()) {
                                    "Unable to complete response: ${e.message ?: "network error"}"
                                } else {
                                    msg.content
                                },
                                status = "error"
                            )
                        } else {
                            msg
                        }
                    }
                    s.copy(
                        messages = updatedMsgs,
                        isStreaming = false,
                        snackbarMessage = e.message ?: "Connection error"
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
                        showSnackbar(err.message ?: "Failed to edit message")
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
                                s.copy(isStreaming = false, messages = updated, snackbarMessage = event.message)
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
                    s.copy(isStreaming = false, messages = updated, snackbarMessage = e.message)
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
                                s.copy(isStreaming = false, snackbarMessage = event.message)
                            }
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                streamFailed = true
                _uiState.update { s ->
                    s.copy(isStreaming = false, snackbarMessage = e.message ?: "Failed to regenerate")
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
                                s.copy(isStreaming = false, messages = updated, snackbarMessage = event.message)
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
                    s.copy(isStreaming = false, messages = updated, snackbarMessage = e.message)
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

    fun togglePinActiveConversation() {
        val convId = _uiState.value.activeConversationId ?: return
        val conv = _uiState.value.conversations.firstOrNull { it.id == convId } ?: return
        val nextPinned = !conv.pinned
        _uiState.update { it.copy(isOverflowMenuOpen = false) }
        viewModelScope.launch {
            repository.togglePinConversation(convId, nextPinned)
            refreshConversationsAndAttachments()
            showSnackbar(if (nextPinned) "Chat pinned" else "Chat unpinned")
        }
    }

    fun openRenameDialog(open: Boolean) {
        _uiState.update { it.copy(showRenameDialog = open, isOverflowMenuOpen = false) }
    }

    fun renameActiveConversation(newTitle: String) {
        val convId = _uiState.value.activeConversationId ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        _uiState.update {
            it.copy(
                showRenameDialog = false,
                activeConversationTitle = trimmed
            )
        }
        viewModelScope.launch {
            repository.renameConversation(convId, trimmed)
            refreshConversationsAndAttachments()
        }
    }

    fun openDeleteConfirmDialog(open: Boolean) {
        _uiState.update { it.copy(showDeleteConfirmDialog = open, isOverflowMenuOpen = false) }
    }

    fun deleteActiveConversation() {
        val convId = _uiState.value.activeConversationId ?: return
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
        viewModelScope.launch {
            repository.deleteConversation(convId)
            startNewChat(incognito = false)
            refreshConversationsAndAttachments()
            showSnackbar("Chat deleted")
        }
    }

    fun createShareLinkForActiveConversation() {
        val convId = _uiState.value.activeConversationId ?: return
        _uiState.update { it.copy(isOverflowMenuOpen = false) }
        viewModelScope.launch {
            repository.createShareLink(convId).onSuccess { url ->
                _uiState.update { it.copy(activeShareLink = url) }
            }.onFailure { err ->
                showSnackbar(err.message ?: "Failed to create share link")
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
        viewModelScope.launch {
            repository.updateProfile(displayName, handle).onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        profile = updated,
                        snackbarMessage = "Profile updated"
                    )
                }
            }.onFailure { err ->
                showSnackbar(err.message ?: "Could not update profile")
            }
        }
    }

    fun updateProfile(displayName: String, username: String?, bio: String?) {
        viewModelScope.launch {
            val cleanUser = username?.trim()?.removePrefix("@")?.lowercase()
            if (cleanUser != null && cleanUser != _uiState.value.profile?.effectiveUsername) {
                confirmUsernameChange(cleanUser)
            }
            repository.updateProfileDetails(displayName, bio, null).onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        profile = updated,
                        snackbarMessage = "Profile updated"
                    )
                }
            }.onFailure { err ->
                showSnackbar(err.message ?: "Failed to update profile")
            }
        }
    }

    fun uploadProfileAvatar(uri: Uri) {
        viewModelScope.launch {
            repository.uploadAvatar(uri).onSuccess {
                val updated = repository.ensureUserProfile()
                _uiState.update {
                    it.copy(
                        profile = updated,
                        snackbarMessage = "Profile photo updated"
                    )
                }
            }.onFailure { err ->
                showSnackbar(err.message ?: "Could not upload photo")
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
            repository.deleteAllConversations()
            repository.signOut()
            _uiState.value = BuildXUiState(
                isAuthenticated = false,
                appearance = repository.getSavedAppearance(),
                language = repository.getSavedLanguage()
            )
        }
    }

    fun exportMyData(onJsonReady: (String) -> Unit) {
        viewModelScope.launch {
            repository.exportUserDataJson().onSuccess { jsonStr ->
                onJsonReady(jsonStr)
            }.onFailure { err ->
                showSnackbar(err.message ?: "Failed to export data")
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
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * S11 Voice Dictation via Android SpeechRecognizer.
     */
    fun startVoiceDictation() {
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
                    val combined = if (s.composerText.isBlank()) transcript else "${s.composerText} $transcript"
                    s.copy(
                        isVoiceListening = false,
                        voicePartialTranscript = transcript,
                        composerText = combined.trim()
                    )
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

    fun cancelVoiceDictation() {
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
        viewModelScope.launch {
            repository.getOwnProfile().onSuccess { p ->
                _uiState.update { it.copy(profile = p) }
            }
        }
    }

    fun loadPublicProfile(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPublicProfileLoading = true, publicProfileError = null) }
            repository.getPublicProfile(username).onSuccess { pub ->
                _uiState.update {
                    it.copy(
                        viewedPublicProfile = pub,
                        isPublicProfileLoading = false,
                        publicProfileError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        viewedPublicProfile = null,
                        isPublicProfileLoading = false,
                        publicProfileError = err.message ?: "User not found"
                    )
                }
            }
        }
    }

    fun toggleFollow(targetUserId: String, targetUsername: String? = null) {
        viewModelScope.launch {
            val currentPub = _uiState.value.viewedPublicProfile
            if (currentPub != null && currentPub.id == targetUserId) {
                val newIsFollowing = !currentPub.isFollowing
                val delta = if (newIsFollowing) 1 else -1
                _uiState.update {
                    it.copy(
                        viewedPublicProfile = currentPub.copy(
                            isFollowing = newIsFollowing,
                            followersCount = (currentPub.followersCount + delta).coerceAtLeast(0)
                        )
                    )
                }
            }

            _uiState.update { state ->
                state.copy(
                    searchPeopleResults = state.searchPeopleResults.map {
                        if (it.id == targetUserId) it.copy(isFollowing = !it.isFollowing) else it
                    },
                    followingListUsers = state.followingListUsers.map {
                        if (it.id == targetUserId) it.copy(isFollowing = !it.isFollowing) else it
                    },
                    followersListUsers = state.followersListUsers.map {
                        if (it.id == targetUserId) it.copy(isFollowing = !it.isFollowing) else it
                    }
                )
            }

            repository.toggleFollow(targetUserId).onSuccess {
                refreshProfile()
                if (targetUsername != null) {
                    loadPublicProfile(targetUsername)
                }
            }.onFailure {
                refreshProfile()
                if (targetUsername != null) {
                    loadPublicProfile(targetUsername)
                }
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
                showSnackbar("Note shared")
            }.onFailure { err ->
                _uiState.update { it.copy(isCreatingNote = false) }
                showSnackbar(err.message ?: "Failed to share note")
            }
        }
    }

    fun deleteNote() {
        viewModelScope.launch {
            repository.deleteNote().onSuccess {
                _uiState.update {
                    it.copy(
                        activeOwnNote = null,
                        isNoteSheetOpen = false
                    )
                }
                showSnackbar("Note deleted")
            }.onFailure { err ->
                showSnackbar(err.message ?: "Failed to delete note")
            }
        }
    }

    fun openNoteSheet(open: Boolean) {
        _uiState.update { it.copy(isNoteSheetOpen = open) }
    }

    fun loadFollowLists(targetUserId: String, tabIndex: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFollowListsLoading = true, followListsError = null) }
            if (tabIndex == 0) {
                repository.getUserFollowing(targetUserId).onSuccess { list ->
                    _uiState.update { it.copy(followingListUsers = list, isFollowListsLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isFollowListsLoading = false, followListsError = err.message) }
                }
            } else {
                repository.getUserFollowers(targetUserId).onSuccess { list ->
                    _uiState.update { it.copy(followersListUsers = list, isFollowListsLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isFollowListsLoading = false, followListsError = err.message) }
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
                    _uiState.update { it.copy(searchPeopleError = err.message, isSearchPeopleLoading = false) }
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
                showSnackbar(err.message ?: "Failed to update username")
                onComplete?.invoke(Result.failure(err))
            }
        }
    }

    fun dismissUsernameReviewSheet() {
        _uiState.update { it.copy(showUsernameReviewSheet = false) }
    }
}
