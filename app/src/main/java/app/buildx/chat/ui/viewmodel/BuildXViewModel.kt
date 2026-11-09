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
import app.buildx.chat.data.model.AppNotificationItem
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.ChatSseEvent
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.data.repository.BuildXRepository
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
    val conversations: List<Conversation> = emptyList(),
    val filteredConversations: List<Conversation> = emptyList(),
    val unreadConversationIds: Set<String> = emptySet(),
    val activeConversationId: String? = null,
    val activeConversationTitle: String = "Build X",
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val isIncognito: Boolean = false,
    val mode: ChatMode = ChatMode.THINKING,
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
    val showNotificationPermissionCard: Boolean = true,
    val notifications: List<AppNotificationItem> = emptyList()
)

class BuildXViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BuildXRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(BuildXUiState())
    val uiState: StateFlow<BuildXUiState> = _uiState.asStateFlow()

    private var activeStreamJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            val uid = repository.currentUserId()
            if (uid != null) {
                runCatching {
                    val profile = repository.ensureUserProfile()
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            profile = profile,
                            appearance = AppAppearance.fromDb(profile.appearance)
                        )
                    }
                    refreshConversationsAndAttachments()
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "Please enter both email and password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = repository.signInWithEmail(email, password)
            result.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isAuthLoading = false,
                        authError = null,
                        profile = profile,
                        appearance = AppAppearance.fromDb(profile.appearance)
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
        if (email.isBlank() || password.length < 6) {
            _uiState.update {
                it.copy(authError = "Enter a valid email and a password of at least 6 characters.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = repository.signUpWithEmail(email, password, displayName, handle)
            result.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isAuthLoading = false,
                        authError = null,
                        profile = profile,
                        appearance = AppAppearance.fromDb(profile.appearance)
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

    fun signOut() {
        activeStreamJob?.cancel()
        viewModelScope.launch {
            repository.signOut()
            _uiState.value = BuildXUiState(isAuthenticated = false)
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
                    activeConversationTitle = activeConv?.title ?: "Build X",
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
            state.copy(mode = nextMode)
        }
    }

    fun setDrawerOpen(open: Boolean) {
        _uiState.update { it.copy(isDrawerOpen = open, isOverflowMenuOpen = false) }
        if (open) {
            refreshConversationsAndAttachments()
        }
    }

    fun startNewChat(incognito: Boolean = false) {
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                activeConversationId = null,
                activeConversationTitle = "Build X",
                messages = emptyList(),
                isStreaming = false,
                isIncognito = incognito,
                composerText = "",
                stagedAttachments = emptyList(),
                isDrawerOpen = false,
                isOverflowMenuOpen = false
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
                    activeConversationId = conversationId,
                    activeConversationTitle = conv?.title ?: "Build X",
                    messages = msgs,
                    isStreaming = false,
                    isIncognito = false,
                    isDrawerOpen = false,
                    isOverflowMenuOpen = false,
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
                isOverflowMenuOpen = false
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
                            _uiState.update { s ->
                                s.copy(
                                    activeConversationId = event.conversationId ?: s.activeConversationId,
                                    activeConversationTitle = event.title
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
                                    _uiState.value.activeConversationId == finalConvId && !_uiState.value.isDrawerOpen
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
                // User tapped Stop button: cancel stream and mark message status='stopped'
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
                    1 -> "Feedback saved"
                    -1 -> "Feedback saved"
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

    fun openStepsSheet(steps: List<ReasoningStep>?) {
        _uiState.update { it.copy(activeStepsSheetMessage = steps) }
    }

    fun toggleOverflowMenu(open: Boolean) {
        _uiState.update { it.copy(isOverflowMenuOpen = open) }
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
        _uiState.update { it.copy(isAttachmentSheetOpen = open, selectedGalleryUris = emptySet()) }
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
        _uiState.update { it.copy(appearance = appearance) }
        viewModelScope.launch {
            repository.updateAppearance(appearance)
        }
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
            _uiState.value = BuildXUiState(isAuthenticated = false)
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
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
}
