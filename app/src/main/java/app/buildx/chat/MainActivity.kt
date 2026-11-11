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
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.buildx.chat.data.model.MainSurfacePane
import app.buildx.chat.ui.components.UsernameReviewSheet
import android.app.Activity
import app.buildx.chat.ui.screens.AppAppearanceScreen
import app.buildx.chat.ui.screens.AppInfoScreen
import app.buildx.chat.ui.screens.AppLanguageScreen
import app.buildx.chat.ui.screens.AuthScreen
import app.buildx.chat.ui.screens.BlockedAccountsScreen
import app.buildx.chat.ui.screens.ChatAndDrawerScreen
import app.buildx.chat.ui.screens.DmInboxScreen
import app.buildx.chat.ui.screens.DmConversationScreen
import app.buildx.chat.ui.screens.NewMessageOrGroupSheet
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.ui.screens.ConversationActionsSheet
import app.buildx.chat.ui.screens.DataPrivacyScreen
import app.buildx.chat.ui.screens.DeleteChatDialog
import app.buildx.chat.ui.screens.EditProfileScreen
import app.buildx.chat.ui.screens.FollowListsScreen
import app.buildx.chat.ui.screens.FullScreenSearchScreen
import app.buildx.chat.ui.screens.HelpSupportScreen
import app.buildx.chat.ui.screens.LegalSafetyScreen
import app.buildx.chat.ui.screens.MediaScreen
import app.buildx.chat.ui.screens.NotificationsScreen
import app.buildx.chat.ui.screens.ProfileScreen
import app.buildx.chat.ui.screens.PublicNoteSheet
import app.buildx.chat.ui.screens.RecentUploadsDialog
import app.buildx.chat.ui.screens.RenameChatDialog
import app.buildx.chat.ui.screens.SettingsScreen
import app.buildx.chat.ui.screens.ShareLinkDialog
import app.buildx.chat.ui.screens.VerificationRequestScreen
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.ui.viewmodel.BuildXViewModel
import app.buildx.chat.ui.viewmodel.SessionState
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private var vm: BuildXViewModel? = null
    private var onDeepLink: ((Intent) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val viewModel: BuildXViewModel = viewModel()
            vm = viewModel
            val navController = rememberNavController()

            DisposableEffect(Unit) {
                onDeepLink = { incomingIntent ->
                    handleIntent(incomingIntent, viewModel, navController)
                }
                onDispose { onDeepLink = null }
            }

            LaunchedEffect(intent) {
                intent?.let { handleIntent(it, viewModel, navController) }
            }

            BuildXRoot(viewModel, navController)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        onDeepLink?.invoke(intent) ?: run {
            vm?.handleAuthDeeplink(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        vm?.onActivityResumed()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun handleIntent(incomingIntent: Intent, viewModel: BuildXViewModel, navController: NavHostController) {
        val uri = incomingIntent.data
        if (uri != null && uri.scheme == "buildx") {
            val u = if (uri.host == "u") {
                uri.pathSegments.firstOrNull()
            } else if (uri.pathSegments.firstOrNull() == "u") {
                uri.pathSegments.getOrNull(1)
            } else null
            if (!u.isNullOrBlank()) {
                navController.navigate("profile/$u")
                return
            }
        }
        viewModel.handleAuthDeeplink(incomingIntent)
    }
}

@Composable
fun BuildXRoot(
    viewModel: BuildXViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun authenticateWithDevice(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context as? Activity
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setAllowedAuthenticators(
                                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            @Suppress("DEPRECATION")
                            setDeviceCredentialAllowed(true)
                        } else {
                            setNegativeButton(activity.getString(android.R.string.cancel), activity.mainExecutor) { _, _ ->
                                onError(activity.getString(android.R.string.cancel))
                            }
                        }
                    }
                    .build()

                val signal = android.os.CancellationSignal()
                prompt.authenticate(
                    signal,
                    activity.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
                                errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                                errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT
                            ) {
                                onSuccess()
                            } else {
                                onError(errString?.toString() ?: "Authentication cancelled")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Authentication failed")
                        }
                    }
                )
            } catch (e: Exception) {
                onSuccess()
            }
        } else {
            onSuccess()
        }
    }

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
            val isUserSignedIn = uiState.sessionState == SessionState.SignedIn || uiState.isAuthenticated
            if (uiState.sessionState == SessionState.Loading && !uiState.isAuthenticated) {
                SessionLoadingSurface()
            } else if (!isUserSignedIn) {
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
                            onSelectConversation = { id ->
                                val conv = uiState.conversations.firstOrNull { it.id == id }
                                if (conv?.isLocked == true && !uiState.unlockedConversationIds.contains(id)) {
                                    authenticateWithDevice(
                                        title = context.getString(R.string.chat_locked_auth_title),
                                        subtitle = context.getString(R.string.chat_locked_auth_subtitle),
                                        onSuccess = {
                                            viewModel.markConversationUnlocked(id)
                                            viewModel.selectConversation(id)
                                        },
                                        onError = {
                                            viewModel.showSnackbar(context.getString(R.string.chat_locked_auth_error))
                                        }
                                    )
                                } else {
                                    viewModel.selectConversation(id)
                                }
                            },
                            onConversationLongClick = { conv ->
                                viewModel.openConversationActions(conv)
                            },
                            onConversationOptionsClick = { conv ->
                                viewModel.openConversationActions(conv)
                            },
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
                            onNavigateToMessages = {
                                viewModel.setDrawerOpen(false)
                                navController.navigate("messages")
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
                            onBlockedAccountsClick = { navController.navigate("blocked_accounts") },
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
                            onReviewUsernameClick = { viewModel.openUsernameReviewSheet() },
                            onFollowClick = {},
                            onFollowingClick = {},
                            onFollowersListClick = { userId, initialTab ->
                                navController.navigate("follow_lists/$userId/$initialTab")
                            },
                            onRetry = {},
                            onShareProfile = { uname ->
                                val text = String.format(strings.shareProfileText, uname, uname)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, context.getString(R.string.pv2_share_profile))
                                )
                            },
                            onCopyLink = { uname ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("profile", "https://buildx.chat/$uname")
                                )
                                viewModel.showSnackbar(strings.linkCopiedToClipboard)
                            },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenVerification = { navController.navigate("verification") },
                            onOpenBlockedAccounts = { navController.navigate("blocked_accounts") },
                            onMessageClick = {
                                navController.navigate("messages")
                            },
                            onAvatarPicked = { bytes, uri ->
                                viewModel.uploadProfileAvatar(bytes, uri)
                            },
                            accountInfo = uiState.accountInfo,
                            isAccountInfoLoading = uiState.isAccountInfoLoading,
                            accountInfoError = uiState.accountInfoError,
                            onLoadAccountInfo = { viewModel.loadAccountInfo(it) }
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
                            onRetry = { viewModel.loadPublicProfile(usernameArg) },
                            isSubscribedToNotes = uiState.viewedPublicProfile?.noteSubscribed == true,
                            onToggleNotification = { targetId ->
                                viewModel.toggleNoteSubscription(targetId, usernameArg)
                            },
                            isFollowPending = uiState.isFollowPending,
                            isProfileUnavailable = uiState.isProfileUnavailable,
                            accountInfo = uiState.accountInfo,
                            isAccountInfoLoading = uiState.isAccountInfoLoading,
                            accountInfoError = uiState.accountInfoError,
                            isSubmittingReport = uiState.isSubmittingReport,
                            reportError = uiState.reportError,
                            isBlocking = uiState.isBlocking,
                            onLoadAccountInfo = { viewModel.loadAccountInfo(it) },
                            onSubmitReport = { targetId, reason, details ->
                                viewModel.submitReport(targetId, reason, details)
                            },
                            onBlockUser = { targetId ->
                                viewModel.blockUser(targetId, usernameArg) {
                                    navController.popBackStack()
                                }
                            },
                            onUnblockUser = { targetId ->
                                viewModel.unblockUser(targetId)
                            },
                            onShareProfile = { uname ->
                                val text = String.format(strings.shareProfileText, uname, uname)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, context.getString(R.string.pv2_share_profile))
                                )
                            },
                            onCopyLink = { uname ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("profile", "https://buildx.chat/$uname")
                                )
                                viewModel.showSnackbar(strings.linkCopiedToClipboard)
                            },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenVerification = { navController.navigate("verification") },
                            onOpenBlockedAccounts = { navController.navigate("blocked_accounts") },
                            onMessageClick = {
                                val targetId = uiState.viewedPublicProfile?.id
                                if (targetId != null) {
                                    viewModel.openDirectChatWithUser(targetId) { convId ->
                                        navController.navigate("dm/$convId")
                                    }
                                }
                            }
                        )
                    }

                    composable("blocked_accounts") {
                        LaunchedEffect(Unit) {
                            viewModel.loadBlockedUsers(reset = true)
                        }
                        BlockedAccountsScreen(
                            blockedUsers = uiState.blockedUsers,
                            isLoading = uiState.isBlockedUsersLoading,
                            errorMessage = uiState.blockedUsersError,
                            hasMore = uiState.hasMoreBlockedUsers,
                            onBackClick = { navController.popBackStack() },
                            onUnblock = { targetId -> viewModel.unblockUser(targetId) },
                            onLoadMore = { viewModel.loadBlockedUsers(reset = false) },
                            onRetry = { viewModel.loadBlockedUsers(reset = true) }
                        )
                    }

                    composable("edit_profile") {
                        EditProfileScreen(
                            profile = uiState.profile,
                            isUploadingAvatar = uiState.isUploadingAvatar,
                            onSaveProfile = { displayName, newUsername, bio ->
                                viewModel.updateProfile(displayName, newUsername, bio) {
                                    navController.popBackStack()
                                }
                            },
                            onPickAvatarClick = {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onBackClick = { navController.popBackStack() },
                            checkUsernameAvailable = { candidate ->
                                viewModel.repository.checkUsernameAvailable(candidate).getOrDefault(false)
                            },
                            onCountrySelected = { code -> viewModel.updateCountry(code) }
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

                    composable("messages") {
                        LaunchedEffect(Unit) {
                            viewModel.loadDmInbox()
                            viewModel.loadInboxNotesTray()
                            val uid = viewModel.currentUserId()
                            if (uid != null) {
                                viewModel.loadFollowLists(uid, 0)
                            }
                        }
                        DmInboxScreen(
                            uiState = uiState,
                            onBackClick = { navController.popBackStack() },
                            onSearchQueryChange = { q -> viewModel.onDmSearchQueryChanged(q) },
                            onConversationClick = { convId -> navController.navigate("dm/$convId") },
                            onOpenProfile = { handle -> navController.navigate("profile/$handle") },
                            onOpenNoteSheet = { viewModel.openNoteSheet(true) },
                            onReplyToNote = { note, replyText ->
                                viewModel.sendNoteReplyMessage(note, replyText) { convId ->
                                    navController.navigate("dm/$convId")
                                }
                            },
                            onCreateGroupClick = { viewModel.openCreateGroupSheet(true) },
                            onTogglePinConversation = { convId -> viewModel.togglePinDmConversation(convId) },
                            onToggleMuteConversation = { convId -> viewModel.toggleMuteDmConversation(convId) },
                            onSelectFilterTab = { tab -> viewModel.selectDmFilterTab(tab) },
                            onToggleReadUnreadConversation = { convId ->
                                val conv = uiState.dmInboxItems.firstOrNull { it.id == convId }
                                if ((conv?.unreadCount ?: 0) > 0) {
                                    viewModel.markDmRead(convId)
                                } else {
                                    viewModel.markDmUnread(convId)
                                }
                            },
                            onDeleteOrLeaveConversation = { convId ->
                                viewModel.leaveOrDeleteDmConversation(convId)
                            },
                            onOpenDirectChatWithUser = { userId ->
                                viewModel.openDirectChatWithUser(userId) { convId ->
                                    navController.navigate("dm/$convId")
                                }
                            },
                            onRenameGroup = { convId, newTitle ->
                                viewModel.updateGroupTitle(convId, newTitle)
                            },
                            currentUserId = viewModel.currentUserId()
                        )
                    }

                    composable("dm/{conversationId}") { backStackEntry ->
                        val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                        LaunchedEffect(conversationId) {
                            viewModel.enterDmRoom(conversationId)
                        }
                        DisposableEffect(conversationId) {
                            onDispose {
                                viewModel.leaveDmRoom()
                            }
                        }
                        DmConversationScreen(
                            uiState = uiState,
                            conversationId = conversationId,
                            onBackClick = {
                                viewModel.leaveDmRoom()
                                navController.popBackStack()
                            },
                            onProfileClick = { handle -> navController.navigate("profile/$handle") },
                            onSendMessage = { viewModel.sendDmMessage(conversationId) },
                            onComposerTextChange = { text -> viewModel.onDmComposerTextChanged(text) },
                            onAddStagedAttachment = { att -> viewModel.addDmStagedAttachment(att) },
                            onRemoveStagedAttachment = { att -> viewModel.removeDmStagedAttachment(att) },
                            onReplyToMessage = { msg -> viewModel.setDmReplyingTo(msg) },
                            onCancelReply = { viewModel.setDmReplyingTo(null) },
                            onEditMessage = { msg -> viewModel.setDmEditing(msg) },
                            onCancelEdit = { viewModel.setDmEditing(null) },
                            onDeleteMessage = { msgId -> viewModel.deleteDmMessage(msgId) },
                            onTogglePinMessage = { msgId -> viewModel.togglePinDmMessage(conversationId, msgId) },
                            onTogglePinConversation = { viewModel.togglePinDmConversation(conversationId) },
                            onToggleMuteConversation = { viewModel.toggleMuteDmConversation(conversationId) },
                            onReportMessage = { msg ->
                                viewModel.submitReport(msg.senderId, ReportReason.SPAM, "Reported message: ${msg.id}") {
                                    viewModel.showSnackbar("Report submitted")
                                }
                            }
                        )
                    }
                }
            }

            // Dialogs
            if (uiState.showRenameDialog) {
                val targetConv = uiState.conversationToRename ?: uiState.conversations.firstOrNull { it.id == uiState.activeConversationId }
                RenameChatDialog(
                    initialTitle = targetConv?.cleanTitle ?: "",
                    onConfirm = { newTitle -> viewModel.confirmRenameConversation(newTitle) },
                    onDismiss = { viewModel.openRenameDialog(false) }
                )
            }

            if (uiState.showDeleteConfirmDialog) {
                DeleteChatDialog(
                    onConfirm = { viewModel.confirmDeleteConversation() },
                    onDismiss = { viewModel.openDeleteConfirmDialog(false) }
                )
            }

            val selectedActionsConv = uiState.selectedConversationForActions
            if (selectedActionsConv != null) {
                ConversationActionsSheet(
                    conversation = selectedActionsConv,
                    onDismiss = { viewModel.openConversationActions(null) },
                    onTogglePin = { viewModel.togglePinConversation(selectedActionsConv) },
                    onToggleLock = {
                        if (!selectedActionsConv.isLocked) {
                            authenticateWithDevice(
                                title = context.getString(R.string.chat_action_lock),
                                subtitle = context.getString(R.string.chat_action_lock_subtitle),
                                onSuccess = { viewModel.toggleLockConversation(selectedActionsConv) },
                                onError = { msg -> viewModel.showSnackbar(msg) }
                            )
                        } else {
                            authenticateWithDevice(
                                title = context.getString(R.string.chat_action_unlock),
                                subtitle = context.getString(R.string.chat_locked_auth_subtitle),
                                onSuccess = { viewModel.toggleLockConversation(selectedActionsConv) },
                                onError = { msg -> viewModel.showSnackbar(msg) }
                            )
                        }
                    },
                    onRename = { viewModel.openRenameForConversation(selectedActionsConv) },
                    onDelete = { viewModel.openDeleteForConversation(selectedActionsConv) }
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

            // New Message or Group Sheet
            if (uiState.isCreateGroupSheetOpen) {
                NewMessageOrGroupSheet(
                    candidateUsers = uiState.followingListUsers,
                    searchResults = uiState.dmSearchPeopleResults,
                    isSearchLoading = uiState.isDmSearchPeopleLoading,
                    searchQuery = uiState.dmSearchQuery,
                    onSearchQueryChange = { viewModel.onDmSearchQueryChanged(it) },
                    onSelectDirectUser = { userId ->
                        viewModel.openDirectChatWithUser(userId) { convId ->
                            navController.navigate("dm/$convId")
                        }
                    },
                    groupTitle = uiState.groupCreationTitle,
                    onGroupTitleChange = { viewModel.onGroupCreationTitleChanged(it) },
                    selectedGroupUserIds = uiState.groupCreationSelectedUserIds,
                    onToggleSelectGroupUser = { viewModel.toggleGroupCreationUserSelection(it) },
                    onCreateGroup = {
                        viewModel.submitCreateGroup { convId ->
                            navController.navigate("dm/$convId")
                        }
                    },
                    isCreatingGroup = uiState.isCreatingGroup,
                    onDismiss = { viewModel.openCreateGroupSheet(false) }
                )
            }

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

@Composable
fun SessionLoadingSurface() {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .softShadow(
                    color = colors.headerHaloShadowColor,
                    cornerRadius = 32.dp,
                    blurRadius = 16.dp,
                    offsetY = 4.dp
                )
                .clip(CircleShape)
                .background(colors.cardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_buildx_mark),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = colors.primaryText
            )
        }
    }
}
