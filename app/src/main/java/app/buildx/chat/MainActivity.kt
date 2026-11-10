package app.buildx.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.buildx.chat.data.model.MainSurfacePane
import app.buildx.chat.ui.components.UsernameReviewSheet
import app.buildx.chat.ui.screens.AppAppearanceScreen
import app.buildx.chat.ui.screens.AppInfoScreen
import app.buildx.chat.ui.screens.AppLanguageScreen
import app.buildx.chat.ui.screens.AuthScreen
import app.buildx.chat.ui.screens.ChatAndDrawerScreen
import app.buildx.chat.ui.screens.DataPrivacyScreen
import app.buildx.chat.ui.screens.DeleteChatDialog
import app.buildx.chat.ui.screens.EditProfileScreen
import app.buildx.chat.ui.screens.FollowListsScreen
import app.buildx.chat.ui.screens.FullScreenSearchScreen
import app.buildx.chat.ui.screens.HelpSupportScreen
import app.buildx.chat.ui.screens.LegalSafetyScreen
import app.buildx.chat.ui.screens.MediaScreen
import app.buildx.chat.ui.screens.NotificationsScreen
import app.buildx.chat.ui.screens.ProfileEditScreen
import app.buildx.chat.ui.screens.ProfileScreen
import app.buildx.chat.ui.screens.PublicNoteSheet
import app.buildx.chat.ui.screens.RecentUploadsDialog
import app.buildx.chat.ui.screens.RenameChatDialog
import app.buildx.chat.ui.screens.SettingsScreen
import app.buildx.chat.ui.screens.ShareLinkDialog
import app.buildx.chat.ui.screens.VerificationRequestScreen
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.viewmodel.BuildXViewModel
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private var vm: BuildXViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BuildXViewModel = viewModel()
            vm = viewModel
            LaunchedEffect(intent) {
                intent?.let { viewModel.handleAuthDeeplink(it) }
            }
            BuildXRoot(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm?.handleAuthDeeplink(intent)
    }
}

@Composable
fun BuildXRoot(
    viewModel: BuildXViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val navController = rememberNavController()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Audio recording permission launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceDictation()
        } else {
            viewModel.showSnackbar("Microphone permission is required for voice dictation.")
        }
    }

    // Notification permission launcher (Android 13+)
    val postNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    // MediaStore read permission launcher
    val readMediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.openAttachmentSheet(true)
    }

    // System Photo Picker (See all)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.attachPickedUris(uris)
        }
    }

    // Avatar Photo Picker
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadProfileAvatar(uri)
        }
    }

    // Document picker (Files row in Attachment sheet)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachPickedUris(listOf(uri))
        }
    }

    // Camera capture launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        if (success && capturedUri != null) {
            viewModel.attachPickedUris(listOf(capturedUri))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            viewModel.showSnackbar("Camera permission is required to take a photo.")
        }
    }

    // Auto-dismiss snackbar after 2.6 seconds
    LaunchedEffect(uiState.snackbarMessage) {
        if (uiState.snackbarMessage != null) {
            delay(2600L)
            viewModel.clearSnackbar()
        }
    }

    BuildXTheme(
        appearance = uiState.appearance,
        language = uiState.language
    ) {
        val colors = BuildXTheme.colors
        val strings = BuildXTheme.strings

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
        ) {
            if (!uiState.isAuthenticated) {
                AuthScreen(
                    isLoading = uiState.isAuthLoading,
                    errorMessage = uiState.authError,
                    onSignIn = { identifier, password -> viewModel.signInWithIdentifier(identifier, password) },
                    onSignUp = { email, password, displayName, handle ->
                        viewModel.signUp(email, password, displayName, handle)
                    },
                    onGoogleSignIn = { viewModel.signInWithGoogle() },
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onToggleAppearance = { viewModel.toggleAppearance() },
                    onDismissError = { viewModel.clearSnackbar() }
                )
            } else {
                // Opaque horizontal slide transitions eliminate the default washed-out white crossfade
                NavHost(
                    navController = navController,
                    startDestination = "chat",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.pageBackground),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            targetOffset = { fullWidth -> fullWidth / 4 }
                        )
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            initialOffset = { fullWidth -> fullWidth / 4 }
                        )
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        )
                    }
                ) {
                    composable(
                        route = "chat",
                        enterTransition = { EnterTransition.None },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                initialOffset = { fullWidth -> fullWidth / 4 }
                            )
                        }
                    ) {
                        ChatAndDrawerScreen(
                            uiState = uiState,
                            onToggleDrawer = { open -> viewModel.setDrawerOpen(open) },
                            onNewChat = { viewModel.startNewChat(incognito = false) },
                            onStartIncognito = { viewModel.startNewChat(incognito = true) },
                            onSelectConversation = { id -> viewModel.selectConversation(id) },
                            onSearchQueryChange = { q -> viewModel.onSearchQueryChange(q) },
                            onNavigateToNotifications = {
                                viewModel.setDrawerOpen(false)
                                navController.navigate("notifications")
                            },
                            onNavigateToSettings = {
                                viewModel.setDrawerOpen(false)
                                navController.navigate("settings")
                            },
                            onNavigateToMedia = {
                                viewModel.openSurfacePane(MainSurfacePane.MEDIA)
                            },
                            onNavigateToProfile = {
                                viewModel.setDrawerOpen(false)
                                navController.navigate("profile")
                            },
                            onNavigateToSearch = {
                                viewModel.setDrawerOpen(false)
                                navController.navigate("search")
                            },
                            onComposerTextChange = { text -> viewModel.onComposerTextChange(text) },
                            onToggleMode = { viewModel.toggleMode() },
                            onPlusClick = {
                                val readPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                                if (ContextCompat.checkSelfPermission(context, readPerm) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.openAttachmentSheet(true)
                                } else {
                                    readMediaPermissionLauncher.launch(readPerm)
                                }
                            },
                            onMicClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.startVoiceDictation()
                                } else {
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onSendClick = { viewModel.sendMessage() },
                            onStopClick = { viewModel.stopGeneration() },
                            onRemoveStagedAttachment = { uriStr -> viewModel.removeStagedAttachment(uriStr) },
                            onRateMessage = { msgId, rating -> viewModel.rateMessage(msgId, rating) },
                            onCopyMessage = { content ->
                                copyToClipboard(context, content)
                                viewModel.showSnackbar(strings.copiedToClipboard)
                            },
                            onShareMessage = { content ->
                                sharePlainText(context, content)
                            },
                            onOpenStepsSheet = { steps -> viewModel.openStepsSheet(steps) },
                            onCloseStepsSheet = { viewModel.openStepsSheet(null) },
                            onToggleOverflowMenu = { open -> viewModel.toggleOverflowMenu(open) },
                            onShareConversationLink = { viewModel.createShareLinkForActiveConversation() },
                            onTogglePinConversation = { viewModel.togglePinActiveConversation() },
                            onOpenRenameDialog = { viewModel.openRenameDialog(true) },
                            onOpenDeleteDialog = { viewModel.openDeleteConfirmDialog(true) },
                            onCloseAttachmentSheet = { viewModel.openAttachmentSheet(false) },
                            onSeeAllPhotos = {
                                viewModel.openAttachmentSheet(false)
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCameraCapture = {
                                viewModel.openAttachmentSheet(false)
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    pendingCameraUri = uri
                                    takePictureLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onToggleGallerySelection = { uriStr -> viewModel.toggleGallerySelection(uriStr) },
                            onConfirmGallerySelection = { viewModel.confirmSelectedGalleryAttachments() },
                            onOpenRecentUploads = { viewModel.openRecentUploadsPicker(true) },
                            onPickFileDocument = {
                                viewModel.openAttachmentSheet(false)
                                documentPickerLauncher.launch(arrayOf("*/*"))
                            },
                            onConfirmVoiceTranscript = { viewModel.confirmVoiceTranscript() },
                            onCancelVoiceDictation = { viewModel.cancelVoiceDictation() },
                            onToggleUserMessageMenu = { id -> viewModel.toggleUserMessageMenu(id) },
                            onToggleAssistantMessageMenu = { id -> viewModel.toggleAssistantMessageMenu(id) },
                            onDismissAllMessageMenus = { viewModel.dismissAllMessageMenus() },
                            onStartEditMessage = { id -> viewModel.startEditingMessage(id) },
                            onCancelEditMessage = { viewModel.cancelEditingMessage() },
                            onSendEditMessage = { id, text -> viewModel.sendEditedMessage(id, text) },
                            onResendUserMessage = { msg -> viewModel.resendUserMessage(msg) },
                            onRegenerateAssistant = { msg -> viewModel.regenerateLastAssistantMessage(msg) },
                            onOpenSelectText = { text -> viewModel.openSelectText(text) },
                            onCloseSelectText = { viewModel.closeSelectText() },
                            onToggleModePopup = { open -> viewModel.toggleModePopup(open) },
                            onSelectMode = { mode -> viewModel.selectMode(mode) },
                            onTurnOnNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.onNotificationPermissionResult(true)
                                }
                            },
                            onDismissNotificationPermissionCard = {
                                viewModel.dismissNotificationPermissionCard()
                            },
                            onSelectMediaAttachment = { att ->
                                viewModel.attachExistingUploadedItem(att)
                                viewModel.openSurfacePane(MainSurfacePane.CHAT)
                            }
                        )
                    }

                    composable("media") {
                        MediaScreen(
                            attachments = uiState.userUploadedAttachments,
                            onBackClick = { navController.popBackStack() },
                            onSelectAttachmentForChat = { att ->
                                viewModel.attachExistingUploadedItem(att)
                                navController.popBackStack("chat", inclusive = false)
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            profile = uiState.profile,
                            appearance = uiState.appearance,
                            language = uiState.language,
                            onCloseClick = { navController.popBackStack() },
                            onViewProfileClick = { navController.navigate("profile") },
                            onNotificationsClick = { navController.navigate("notifications") },
                            onAppAppearanceClick = { navController.navigate("appearance") },
                            onAppLanguageClick = { navController.navigate("language") },
                            onHelpSupportClick = { navController.navigate("help_support") },
                            onDataPrivacyClick = { navController.navigate("data_privacy") },
                            onAppInfoClick = { navController.navigate("app_info") },
                            onLegalSafetyClick = { navController.navigate("legal_safety") },
                            onRequestVerificationClick = { navController.navigate("verification") },
                            onSignOutClick = {
                                viewModel.signOut()
                            }
                        )
                    }

                    composable("verification") {
                        VerificationRequestScreen(
                            verificationStatus = uiState.verificationStatus,
                            isLoading = uiState.isVerificationLoading,
                            isSubmitting = uiState.isVerificationSubmitting,
                            errorMessage = uiState.verificationError,
                            onLoadStatus = { viewModel.loadVerificationStatus() },
                            onSubmitRequest = { fullName, category, links, reason ->
                                viewModel.submitVerificationRequest(fullName, category, links, reason) {
                                    viewModel.showSnackbar("Verification request submitted successfully.")
                                }
                            },
                            onWithdrawRequest = { requestId ->
                                viewModel.withdrawVerificationRequest(requestId)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("appearance") {
                        AppAppearanceScreen(
                            selectedAppearance = uiState.appearance,
                            onSelectAppearance = { appearance -> viewModel.setAppearance(appearance) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("language") {
                        AppLanguageScreen(
                            selectedLanguage = uiState.language,
                            onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("app_info") {
                        AppInfoScreen(
                            language = uiState.language,
                            onLanguageClick = { navController.navigate("language") },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("profile") {
                        ProfileScreen(
                            currentProfile = uiState.profile,
                            targetUsername = null,
                            publicProfile = null,
                            isLoading = false,
                            errorMessage = null,
                            activeNote = uiState.activeOwnNote,
                            onBackClick = { navController.popBackStack() },
                            onEditProfileClick = { navController.navigate("edit_profile") },
                            onOpenNoteSheet = { viewModel.openNoteSheet(true) },
                            onReviewUsernameClick = { viewModel.confirmUsernameChange(uiState.profile?.effectiveUsername ?: "user") },
                            onFollowClick = {},
                            onFollowingClick = {},
                            onFollowersListClick = { userId, initialTab ->
                                navController.navigate("follow_lists/$userId/$initialTab")
                            },
                            onRetry = {}
                        )
                    }

                    composable("profile/{username}") { backStackEntry ->
                        val usernameArg = backStackEntry.arguments?.getString("username") ?: ""
                        LaunchedEffect(usernameArg) {
                            viewModel.loadPublicProfile(usernameArg)
                        }
                        ProfileScreen(
                            currentProfile = uiState.profile,
                            targetUsername = usernameArg,
                            publicProfile = uiState.viewedPublicProfile,
                            isLoading = uiState.isPublicProfileLoading,
                            errorMessage = uiState.publicProfileError,
                            activeNote = uiState.viewedPublicProfile?.activeNote,
                            onBackClick = { navController.popBackStack() },
                            onEditProfileClick = { navController.navigate("edit_profile") },
                            onOpenNoteSheet = {},
                            onReviewUsernameClick = {},
                            onFollowClick = { targetId ->
                                viewModel.toggleFollow(targetId, usernameArg)
                            },
                            onFollowingClick = { targetId ->
                                viewModel.toggleFollow(targetId, usernameArg)
                            },
                            onFollowersListClick = { userId, initialTab ->
                                navController.navigate("follow_lists/$userId/$initialTab")
                            },
                            onRetry = { viewModel.loadPublicProfile(usernameArg) }
                        )
                    }

                    composable("edit_profile") {
                        EditProfileScreen(
                            profile = uiState.profile,
                            onSaveProfile = { displayName, newUsername, bio ->
                                viewModel.updateProfile(displayName, newUsername, bio)
                                navController.popBackStack()
                            },
                            onPickAvatarClick = {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onBackClick = { navController.popBackStack() },
                            checkUsernameAvailable = { candidate ->
                                viewModel.repository.checkUsernameAvailable(candidate).getOrDefault(false)
                            }
                        )
                    }

                    composable(
                        "follow_lists/{userId}/{initialTab}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.StringType },
                            navArgument("initialTab") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: ""
                        val tab = backStackEntry.arguments?.getInt("initialTab") ?: 0
                        LaunchedEffect(userId, tab) {
                            viewModel.loadFollowLists(userId, tab)
                        }
                        FollowListsScreen(
                            currentUserId = uiState.profile?.id,
                            targetUserId = userId,
                            initialTab = tab,
                            followingList = uiState.followingListUsers,
                            followersList = uiState.followersListUsers,
                            isLoading = uiState.isFollowListsLoading,
                            errorMessage = uiState.followListsError,
                            onTabSelected = { selectedTab ->
                                viewModel.loadFollowLists(userId, selectedTab)
                            },
                            onUserClick = { userHandle ->
                                navController.navigate("profile/$userHandle")
                            },
                            onToggleFollow = { targetId ->
                                viewModel.toggleFollow(targetId)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("search") {
                        FullScreenSearchScreen(
                            currentUserId = uiState.profile?.id,
                            searchQuery = uiState.globalSearchQuery,
                            onQueryChange = { q -> viewModel.onGlobalSearchQueryChange(q) },
                            activeTab = uiState.searchActiveTab,
                            onTabSelected = { tab -> viewModel.setSearchActiveTab(tab) },
                            peopleResults = uiState.searchPeopleResults,
                            chatResults = uiState.filteredConversations,
                            recentSearches = uiState.recentSearchesList,
                            isLoading = uiState.isSearchPeopleLoading,
                            errorMessage = uiState.searchPeopleError,
                            onSelectPerson = { userHandle ->
                                viewModel.addRecentSearch(userHandle)
                                navController.navigate("profile/$userHandle")
                            },
                            onSelectConversation = { convId ->
                                viewModel.selectConversation(convId)
                                navController.popBackStack("chat", inclusive = false)
                            },
                            onToggleFollow = { targetId ->
                                viewModel.toggleFollow(targetId)
                            },
                            onClearRecentSearches = { viewModel.clearRecentSearches() },
                            onRetry = { viewModel.onGlobalSearchQueryChange(uiState.globalSearchQuery) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("notifications") {
                        LaunchedEffect(Unit) {
                            viewModel.loadInAppNotifications()
                        }
                        NotificationsScreen(
                            showPermissionCard = false,
                            notifications = uiState.inAppNotifications,
                            onMenuOrBackClick = { navController.popBackStack() },
                            onNotificationClick = { notif ->
                                viewModel.markNotificationRead(notif.id)
                                if (!notif.actorUsername.isNullOrBlank()) {
                                    navController.navigate("profile/${notif.actorUsername}")
                                }
                            },
                            onMarkAllAsRead = { viewModel.markAllNotificationsRead() }
                        )
                    }

                    composable("data_privacy") {
                        DataPrivacyScreen(
                            onExportDataClick = {
                                viewModel.exportMyData { json ->
                                    sharePlainText(context, json)
                                }
                            },
                            onDeleteAllChatsClick = {
                                viewModel.deleteAllChats()
                                navController.popBackStack("chat", inclusive = false)
                            },
                            onDeleteAccountClick = {
                                viewModel.deleteAccount()
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("help_support") {
                        HelpSupportScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("legal_safety") {
                        LegalSafetyScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }

            // Dialogs
            if (uiState.showRenameDialog) {
                val activeConv = uiState.conversations.firstOrNull { it.id == uiState.activeConversationId }
                RenameChatDialog(
                    initialTitle = activeConv?.cleanTitle ?: "",
                    onConfirm = { newTitle -> viewModel.renameActiveConversation(newTitle) },
                    onDismiss = { viewModel.openRenameDialog(false) }
                )
            }

            if (uiState.showDeleteConfirmDialog) {
                DeleteChatDialog(
                    onConfirm = { viewModel.deleteActiveConversation() },
                    onDismiss = { viewModel.openDeleteConfirmDialog(false) }
                )
            }

            val shareUrl = uiState.activeShareLink
            if (shareUrl != null) {
                ShareLinkDialog(
                    shareUrl = shareUrl,
                    onCopyLink = { url ->
                        copyToClipboard(context, url)
                        viewModel.showSnackbar(strings.linkCopiedToClipboard)
                    },
                    onShareSystem = { url ->
                        sharePlainText(context, url)
                    },
                    onRevokeLink = { viewModel.revokeShareLinkForActiveConversation() },
                    onDismiss = { viewModel.dismissShareLinkDialog() }
                )
            }

            if (uiState.showRecentUploadsPicker) {
                RecentUploadsDialog(
                    attachments = uiState.userUploadedAttachments,
                    onSelect = { item -> viewModel.attachExistingUploadedItem(item) },
                    onDismiss = { viewModel.openRecentUploadsPicker(false) }
                )
            }

            // Public Note Bottom Sheet
            PublicNoteSheet(
                profile = uiState.profile,
                activeNote = uiState.activeOwnNote,
                isOpen = uiState.isNoteSheetOpen,
                isLoading = uiState.isCreatingNote,
                onDismiss = { viewModel.openNoteSheet(false) },
                onCreateNote = { content -> viewModel.createNote(content) },
                onDeleteNote = { viewModel.deleteNote() }
            )

            // One-time Username Review Bottom Sheet
            UsernameReviewSheet(
                currentUsername = uiState.profile?.effectiveUsername ?: "user",
                isOpen = uiState.showUsernameReviewSheet,
                onDismiss = { viewModel.dismissUsernameReviewSheet() },
                onKeepCurrent = { viewModel.markUsernameReviewed() },
                onConfirmNewUsername = { newUsername ->
                    viewModel.confirmUsernameChange(newUsername)
                },
                checkUsernameAvailable = { candidate ->
                    viewModel.repository.checkUsernameAvailable(candidate).getOrDefault(false)
                }
            )

            // Subtle pill snackbar confirmation at bottom center
            AnimatedVisibility(
                visible = uiState.snackbarMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 112.dp, start = 24.dp, end = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.primaryText.copy(alpha = 0.92f))
                        .clickable { viewModel.clearSnackbar() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = uiState.snackbarMessage ?: "",
                        color = colors.pageBackground,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Build X", text))
}

private fun sharePlainText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}
