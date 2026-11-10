package app.buildx.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import app.buildx.chat.data.model.ChatMessage
import app.buildx.chat.data.model.ChatMode
import app.buildx.chat.data.model.Conversation
import app.buildx.chat.data.model.FollowUserItem
import app.buildx.chat.data.model.NotificationRow
import app.buildx.chat.data.model.PersonSearchResult
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.screens.AppAppearanceScreen
import app.buildx.chat.ui.screens.AppInfoScreen
import app.buildx.chat.ui.screens.AppLanguageScreen
import app.buildx.chat.ui.screens.ChatAndDrawerScreen
import app.buildx.chat.ui.screens.EditProfileScreen
import app.buildx.chat.ui.screens.FollowListsScreen
import app.buildx.chat.ui.screens.FullScreenSearchScreen
import app.buildx.chat.ui.screens.NotificationsScreen
import app.buildx.chat.ui.screens.ProfileScreen
import app.buildx.chat.ui.screens.SettingsScreen
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.viewmodel.BuildXUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Phase 2 & 3 Visual Fidelity Screenshot Verification Suite (v1.2).
 * Captures every screen (S1..S11 + Mode Popup + Arabic RTL) at 411dp x 891dp portrait
 * and outputs PNGs into docs/design/screenshots/ for direct pixel-overlay comparison.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel6)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class ScreenFidelityScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir = File("../docs/design/screenshots").apply { mkdirs() }

    private fun captureScreen(fileName: String) {
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            file = File(outputDir, fileName),
            roborazziOptions = RoborazziOptions()
        )
    }

    @Test
    fun capture_S1_Home_Empty() {
        val state = BuildXUiState(
            isAuthenticated = true,
            mode = ChatMode.THINKING,
            composerText = ""
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s1_home_empty.png")
    }

    @Test
    fun capture_S2_Composer_NonEmpty_SendButton() {
        val state = BuildXUiState(
            isAuthenticated = true,
            mode = ChatMode.THINKING,
            composerText = "هلا"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s2_composer_non_empty.png")
    }

    @Test
    fun capture_S2_Composer_Mode_Popup_Banner() {
        val state = BuildXUiState(
            isAuthenticated = true,
            mode = ChatMode.INSTANT,
            isModePopupOpen = true,
            composerText = ""
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s2_composer_mode_popup.png")
    }

    @Test
    fun capture_S3_Chat_Reasoning_Streaming() {
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Build X",
            isStreaming = true,
            mode = ChatMode.THINKING,
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = "user",
                    content = "هلا",
                    status = "complete"
                ),
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = "assistant",
                    content = "",
                    reasoningSteps = listOf(
                        ReasoningStep(
                            step = 1,
                            title = "تحليل الرسالة والسياق",
                            summary = "فهم طلب المستخدم وتجهيز إجابة دقيقة باللغة العربية."
                        )
                    ),
                    status = "streaming"
                )
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s3_chat_reasoning.png")
    }

    @Test
    fun capture_S3_Chat_Completed_Bidi_Fixed() {
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "ترحيب وبدء المحادثة",
            isStreaming = false,
            mode = ChatMode.THINKING,
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = "user",
                    content = "هلا",
                    status = "complete"
                ),
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = "assistant",
                    content = "يا هلا ومرحبا بك! كيف أقدر أساعدك اليوم؟",
                    reasoningSteps = listOf(
                        ReasoningStep(
                            step = 1,
                            title = "تحليل الرسالة والسياق",
                            summary = "تجهيز رد ترحيبي واضح باللغة العربية."
                        )
                    ),
                    status = "complete"
                )
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s3_chat_completed.png")
    }

    @Test
    fun capture_S4_Steps_Sheet() {
        val steps = listOf(
            ReasoningStep(
                step = 1,
                title = "تحليل الرسالة والسياق",
                summary = "فهم طلب المستخدم وتحديد اللغة والسياق المناسب للرد."
            ),
            ReasoningStep(
                step = 2,
                title = "صياغة الإجابة وتنظيمها",
                summary = "كتابة رد ترحيبي طبيعي ومختصر باللغة العربية."
            )
        )
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "ترحيب وبدء المحادثة",
            activeStepsSheetMessage = steps,
            messages = listOf(
                ChatMessage(id = "m1", conversationId = "c1", role = "user", content = "هلا"),
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = "assistant",
                    content = "يا هلا ومرحبا بك! كيف أقدر أساعدك اليوم؟",
                    reasoningSteps = steps
                )
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s4_steps_sheet.png")
    }

    @Test
    fun capture_S5_Overflow_Menu() {
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "ترحيب وبدء المحادثة",
            isOverflowMenuOpen = true,
            messages = listOf(
                ChatMessage(id = "m1", conversationId = "c1", role = "user", content = "هلا"),
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = "assistant",
                    content = "يا هلا ومرحبا بك! كيف أقدر أساعدك اليوم؟"
                )
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s5_overflow_menu.png")
    }

    @Test
    fun capture_S6_Attachment_Sheet() {
        val state = BuildXUiState(
            isAuthenticated = true,
            isAttachmentSheetOpen = true
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s6_attachment_sheet.png")
    }

    @Test
    fun capture_S7_Drawer_Open() {
        val convs = listOf(
            Conversation(id = "c1", userId = "u1", title = "ترحيب وبدء المحادثة", pinned = true),
            Conversation(id = "c2", userId = "u1", title = "Kotlin Compose Architecture", pinned = false)
        )
        val state = BuildXUiState(
            isAuthenticated = true,
            isDrawerOpen = true,
            conversations = convs,
            filteredConversations = convs,
            unreadConversationIds = setOf("c2")
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s7_drawer_open.png")
    }

    @Test
    fun capture_S8_Notifications() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                NotificationsScreen(
                    showPermissionCard = true,
                    notifications = emptyList(),
                    onMenuOrBackClick = {},
                    onTurnOnNotifications = {},
                    onDismissPermissionCard = {},
                    onOpenConversation = {},
                    modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)
                )
            }
        }
        captureScreen("s8_notifications.png")
    }

    @Test
    fun capture_S9_Incognito_Chat() {
        val state = BuildXUiState(
            isAuthenticated = true,
            isIncognito = true
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s9_incognito.png")
    }

    @Test
    fun capture_S10_Settings_And_Subpages() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                SettingsScreen(
                    profile = UserProfile(
                        id = "u1",
                        handle = "alex_dev",
                        displayName = "Alex"
                    ),
                    appearance = AppAppearance.AUTOMATIC,
                    language = AppLanguage.SYSTEM,
                    onCloseClick = {},
                    onViewProfileClick = {},
                    onNotificationsClick = {},
                    onAppAppearanceClick = {},
                    onAppLanguageClick = {},
                    onHelpSupportClick = {},
                    onDataPrivacyClick = {},
                    onAppInfoClick = {},
                    onLegalSafetyClick = {},
                    onSignOutClick = {},
                    modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)
                )
            }
        }
        captureScreen("s10_settings.png")
    }

    @Test
    fun capture_S10_Settings_Arabic_RTL() {
        composeRule.setContent {
            BuildXTheme(
                appearance = AppAppearance.LIGHT,
                language = AppLanguage.ARABIC
            ) {
                SettingsScreen(
                    profile = UserProfile(
                        id = "u1",
                        handle = "musta_dev",
                        displayName = "Mustafa"
                    ),
                    appearance = AppAppearance.AUTOMATIC,
                    language = AppLanguage.ARABIC,
                    onCloseClick = {},
                    onViewProfileClick = {},
                    onNotificationsClick = {},
                    onAppAppearanceClick = {},
                    onAppLanguageClick = {},
                    onHelpSupportClick = {},
                    onDataPrivacyClick = {},
                    onAppInfoClick = {},
                    onLegalSafetyClick = {},
                    onSignOutClick = {},
                    modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)
                )
            }
        }
        captureScreen("s10_settings_arabic_rtl.png")
    }

    @Test
    fun capture_S10_Language_Picker() {
        composeRule.setContent {
            BuildXTheme(
                appearance = AppAppearance.LIGHT,
                language = AppLanguage.ARABIC
            ) {
                AppLanguageScreen(
                    selectedLanguage = AppLanguage.ARABIC,
                    onSelectLanguage = {},
                    onBackClick = {},
                    modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)
                )
            }
        }
        captureScreen("s10_language.png")
    }

    @Test
    fun capture_S10_Appearance() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                AppAppearanceScreen(
                    selectedAppearance = AppAppearance.AUTOMATIC,
                    onSelectAppearance = {},
                    onBackClick = {},
                    modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)
                )
            }
        }
        captureScreen("s10_appearance.png")
    }

    @Test
    fun capture_S10_App_Info() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                AppInfoScreen(
                    language = AppLanguage.SYSTEM,
                    onLanguageClick = {},
                    onBackClick = {},
                    modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)
                )
            }
        }
        captureScreen("s10_app_info.png")
    }

    @Test
    fun capture_S11_Voice_Input_Bar() {
        val state = BuildXUiState(
            isAuthenticated = true,
            isVoiceListening = true,
            voiceAmplitudes = listOf(
                0.2f, 0.35f, 0.6f, 0.85f, 0.45f, 0.25f, 0.7f, 0.9f, 0.5f, 0.3f,
                0.2f, 0.4f, 0.75f, 0.6f, 0.3f, 0.2f, 0.55f, 0.8f, 0.4f, 0.25f
            ),
            voicePartialTranscript = "Hello Build X"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("s11_voice_input.png")
    }

    @Test
    fun capture_P1_OwnProfile_Light() {
        val user = UserProfile(
            id = "u1",
            handle = "alex_dev",
            username = "alex_dev",
            displayName = "Alex Developer",
            bio = "Building intelligent systems with Kotlin & Supabase.\nhttps://github.com/alexdev",
            followingCount = 142,
            followersCount = 582,
            isVerified = false,
            usernameReviewedAt = null
        )
        val note = PublicNote(
            id = "n1",
            userId = "u1",
            content = "Excited for Build X 1.3!",
            expiresAt = "2026-11-11T12:00:00Z"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                ProfileScreen(
                    currentProfile = user,
                    targetUsername = null,
                    publicProfile = null,
                    isLoading = false,
                    errorMessage = null,
                    activeNote = note,
                    onBackClick = {},
                    onEditProfileClick = {},
                    onOpenNoteSheet = {},
                    onReviewUsernameClick = {},
                    onFollowClick = {},
                    onFollowingClick = {},
                    onFollowersListClick = { _, _ -> },
                    onRetry = {}
                )
            }
        }
        captureScreen("p1_own_profile_light.png")
    }

    @Test
    fun capture_P1_OwnProfile_Dark() {
        val user = UserProfile(
            id = "u1",
            handle = "alex_dev",
            username = "alex_dev",
            displayName = "Alex Developer",
            bio = "Building intelligent systems with Kotlin & Supabase.\nhttps://github.com/alexdev",
            followingCount = 142,
            followersCount = 582,
            isVerified = false,
            usernameReviewedAt = null
        )
        val note = PublicNote(
            id = "n1",
            userId = "u1",
            content = "Excited for Build X 1.3!",
            expiresAt = "2026-11-11T12:00:00Z"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                ProfileScreen(
                    currentProfile = user,
                    targetUsername = null,
                    publicProfile = null,
                    isLoading = false,
                    errorMessage = null,
                    activeNote = note,
                    onBackClick = {},
                    onEditProfileClick = {},
                    onOpenNoteSheet = {},
                    onReviewUsernameClick = {},
                    onFollowClick = {},
                    onFollowingClick = {},
                    onFollowersListClick = { _, _ -> },
                    onRetry = {}
                )
            }
        }
        captureScreen("p1_own_profile_dark.png")
    }

    @Test
    fun capture_P2_OtherProfile_Light() {
        val publicProfile = PublicProfile(
            id = "u_drgzo",
            username = "dr_gzo",
            displayName = "دكتور غزو",
            bio = "Dr Gzo\nدكتور غزو\nInstagram: dr.gzo0\nhttps://youtube.com/@drgzo0",
            avatarUrl = null,
            followingCount = 6,
            followersCount = 7600000,
            isVerified = true,
            isFollowing = false
        )
        val note = PublicNote(
            id = "n2",
            userId = "u_drgzo",
            content = "صوت وصورة من العيادة",
            expiresAt = "2026-11-11T12:00:00Z"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                ProfileScreen(
                    currentProfile = UserProfile(id = "me", username = "alex_dev", displayName = "Alex"),
                    targetUsername = "dr_gzo",
                    publicProfile = publicProfile,
                    isLoading = false,
                    errorMessage = null,
                    activeNote = note,
                    onBackClick = {},
                    onEditProfileClick = {},
                    onOpenNoteSheet = {},
                    onReviewUsernameClick = {},
                    onFollowClick = {},
                    onFollowingClick = {},
                    onFollowersListClick = { _, _ -> },
                    onRetry = {}
                )
            }
        }
        captureScreen("p2_other_profile_light.png")
    }

    @Test
    fun capture_P2_OtherProfile_Dark() {
        val publicProfile = PublicProfile(
            id = "u_drgzo",
            username = "dr_gzo",
            displayName = "دكتور غزو",
            bio = "Dr Gzo\nدكتور غزو\nInstagram: dr.gzo0\nhttps://youtube.com/@drgzo0",
            avatarUrl = null,
            followingCount = 6,
            followersCount = 7600000,
            isVerified = true,
            isFollowing = false
        )
        val note = PublicNote(
            id = "n2",
            userId = "u_drgzo",
            content = "صوت وصورة من العيادة",
            expiresAt = "2026-11-11T12:00:00Z"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                ProfileScreen(
                    currentProfile = UserProfile(id = "me", username = "alex_dev", displayName = "Alex"),
                    targetUsername = "dr_gzo",
                    publicProfile = publicProfile,
                    isLoading = false,
                    errorMessage = null,
                    activeNote = note,
                    onBackClick = {},
                    onEditProfileClick = {},
                    onOpenNoteSheet = {},
                    onReviewUsernameClick = {},
                    onFollowClick = {},
                    onFollowingClick = {},
                    onFollowersListClick = { _, _ -> },
                    onRetry = {}
                )
            }
        }
        captureScreen("p2_other_profile_dark.png")
    }

    @Test
    fun capture_P3_EditProfile_Light() {
        val user = UserProfile(
            id = "u1",
            handle = "alex_dev",
            username = "alex_dev",
            displayName = "Alex Developer",
            bio = "Building intelligent systems with Kotlin & Supabase.\nhttps://github.com/alexdev"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                EditProfileScreen(
                    profile = user,
                    onSaveProfile = { _, _, _ -> },
                    onPickAvatarClick = {},
                    onBackClick = {},
                    checkUsernameAvailable = { true }
                )
            }
        }
        captureScreen("p3_edit_profile_light.png")
    }

    @Test
    fun capture_P3_EditProfile_Dark() {
        val user = UserProfile(
            id = "u1",
            handle = "alex_dev",
            username = "alex_dev",
            displayName = "Alex Developer",
            bio = "Building intelligent systems with Kotlin & Supabase.\nhttps://github.com/alexdev"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                EditProfileScreen(
                    profile = user,
                    onSaveProfile = { _, _, _ -> },
                    onPickAvatarClick = {},
                    onBackClick = {},
                    checkUsernameAvailable = { true }
                )
            }
        }
        captureScreen("p3_edit_profile_dark.png")
    }

    @Test
    fun capture_P4_FollowLists_Light() {
        val followers = listOf(
            FollowUserItem(
                id = "u2",
                username = "sara_k",
                displayName = "Sara Khalil",
                isVerified = false,
                isFollowing = true
            ),
            FollowUserItem(
                id = "u3",
                username = "dr_gzo",
                displayName = "دكتور غزو",
                isVerified = true,
                isFollowing = false
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                FollowListsScreen(
                    currentUserId = "me",
                    targetUserId = "me",
                    initialTab = 1,
                    followingList = emptyList(),
                    followersList = followers,
                    isLoading = false,
                    errorMessage = null,
                    onTabSelected = {},
                    onUserClick = {},
                    onToggleFollow = {},
                    onBackClick = {}
                )
            }
        }
        captureScreen("p4_follow_lists_light.png")
    }

    @Test
    fun capture_P4_FollowLists_Dark() {
        val followers = listOf(
            FollowUserItem(
                id = "u2",
                username = "sara_k",
                displayName = "Sara Khalil",
                isVerified = false,
                isFollowing = true
            ),
            FollowUserItem(
                id = "u3",
                username = "dr_gzo",
                displayName = "دكتور غزو",
                isVerified = true,
                isFollowing = false
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                FollowListsScreen(
                    currentUserId = "me",
                    targetUserId = "me",
                    initialTab = 1,
                    followingList = emptyList(),
                    followersList = followers,
                    isLoading = false,
                    errorMessage = null,
                    onTabSelected = {},
                    onUserClick = {},
                    onToggleFollow = {},
                    onBackClick = {}
                )
            }
        }
        captureScreen("p4_follow_lists_dark.png")
    }

    @Test
    fun capture_P8_Search_Light() {
        val people = listOf(
            PersonSearchResult(
                id = "u_drgzo",
                username = "dr_gzo",
                displayName = "دكتور غزو",
                followersCount = 7600000,
                isVerified = true,
                isFollowing = false
            ),
            PersonSearchResult(
                id = "u_jddodd",
                username = "jddodd",
                displayName = "JD Dodd",
                followersCount = 1420,
                isVerified = false,
                isFollowing = true
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                FullScreenSearchScreen(
                    currentUserId = "me",
                    searchQuery = "غزو",
                    onQueryChange = {},
                    activeTab = 0,
                    onTabSelected = {},
                    peopleResults = people,
                    chatResults = emptyList(),
                    recentSearches = listOf("دكتور غزو", "kotlin coroutines"),
                    isLoading = false,
                    errorMessage = null,
                    onSelectPerson = {},
                    onSelectConversation = {},
                    onToggleFollow = {},
                    onClearRecentSearches = {},
                    onRetry = {},
                    onBackClick = {}
                )
            }
        }
        captureScreen("p8_search_light.png")
    }

    @Test
    fun capture_P8_Search_Dark() {
        val people = listOf(
            PersonSearchResult(
                id = "u_drgzo",
                username = "dr_gzo",
                displayName = "دكتور غزو",
                followersCount = 7600000,
                isVerified = true,
                isFollowing = false
            ),
            PersonSearchResult(
                id = "u_jddodd",
                username = "jddodd",
                displayName = "JD Dodd",
                followersCount = 1420,
                isVerified = false,
                isFollowing = true
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                FullScreenSearchScreen(
                    currentUserId = "me",
                    searchQuery = "غزو",
                    onQueryChange = {},
                    activeTab = 0,
                    onTabSelected = {},
                    peopleResults = people,
                    chatResults = emptyList(),
                    recentSearches = listOf("دكتور غزو", "kotlin coroutines"),
                    isLoading = false,
                    errorMessage = null,
                    onSelectPerson = {},
                    onSelectConversation = {},
                    onToggleFollow = {},
                    onClearRecentSearches = {},
                    onRetry = {},
                    onBackClick = {}
                )
            }
        }
        captureScreen("p8_search_dark.png")
    }

    @Test
    fun capture_P9_Notifications_Light() {
        val notifs = listOf(
            NotificationRow(
                id = "notif_1",
                userId = "me",
                actorId = "u_drgzo",
                type = "follow",
                actorUsername = "dr_gzo",
                actorDisplayName = "دكتور غزو",
                actorIsVerified = true,
                isRead = false,
                createdAt = "2026-11-10T11:00:00Z"
            ),
            NotificationRow(
                id = "notif_2",
                userId = "me",
                actorId = "u_sara",
                type = "note",
                actorUsername = "sara_k",
                actorDisplayName = "Sara Khalil",
                actorIsVerified = false,
                noteContent = "Hello Build X world!",
                isRead = true,
                createdAt = "2026-11-10T10:00:00Z"
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                NotificationsScreen(
                    showPermissionCard = false,
                    notifications = notifs,
                    onMenuOrBackClick = {},
                    onTurnOnNotifications = {},
                    onDismissPermissionCard = {},
                    onNotificationClick = {},
                    onMarkAllAsRead = {}
                )
            }
        }
        captureScreen("p9_notifications_light.png")
    }

    @Test
    fun capture_P9_Notifications_Dark() {
        val notifs = listOf(
            NotificationRow(
                id = "notif_1",
                userId = "me",
                actorId = "u_drgzo",
                type = "follow",
                actorUsername = "dr_gzo",
                actorDisplayName = "دكتور غزو",
                actorIsVerified = true,
                isRead = false,
                createdAt = "2026-11-10T11:00:00Z"
            ),
            NotificationRow(
                id = "notif_2",
                userId = "me",
                actorId = "u_sara",
                type = "note",
                actorUsername = "sara_k",
                actorDisplayName = "Sara Khalil",
                actorIsVerified = false,
                noteContent = "Hello Build X world!",
                isRead = true,
                createdAt = "2026-11-10T10:00:00Z"
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                NotificationsScreen(
                    showPermissionCard = false,
                    notifications = notifs,
                    onMenuOrBackClick = {},
                    onTurnOnNotifications = {},
                    onDismissPermissionCard = {},
                    onNotificationClick = {},
                    onMarkAllAsRead = {}
                )
            }
        }
        captureScreen("p9_notifications_dark.png")
    }

    @Test
    fun capture_A1_UserMessageMenu_Light() {
        val userMsg = ChatMessage(id = "msg_user_1", conversationId = "c1", role = "user", content = "Can you explain quantum computing?")
        val astMsg = ChatMessage(id = "msg_ast_1", conversationId = "c1", role = "assistant", content = "Quantum computing uses qubits...")
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Quantum computing",
            messages = listOf(userMsg, astMsg),
            activeUserMessageMenuId = "msg_user_1"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a1_user_message_menu_light.png")
    }

    @Test
    fun capture_A1_UserMessageMenu_Dark() {
        val userMsg = ChatMessage(id = "msg_user_1", conversationId = "c1", role = "user", content = "Can you explain quantum computing?")
        val astMsg = ChatMessage(id = "msg_ast_1", conversationId = "c1", role = "assistant", content = "Quantum computing uses qubits...")
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Quantum computing",
            messages = listOf(userMsg, astMsg),
            activeUserMessageMenuId = "msg_user_1"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a1_user_message_menu_dark.png")
    }

    @Test
    fun capture_A1_InlineEditor_Light() {
        val userMsg = ChatMessage(id = "msg_user_1", conversationId = "c1", role = "user", content = "Can you explain quantum computing?")
        val astMsg = ChatMessage(id = "msg_ast_1", conversationId = "c1", role = "assistant", content = "Quantum computing uses qubits...")
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Quantum computing",
            messages = listOf(userMsg, astMsg),
            editingMessageId = "msg_user_1",
            editingMessageText = "Can you explain quantum computing in simple terms?"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a1_inline_editor_light.png")
    }

    @Test
    fun capture_A1_InlineEditor_Dark() {
        val userMsg = ChatMessage(id = "msg_user_1", conversationId = "c1", role = "user", content = "Can you explain quantum computing?")
        val astMsg = ChatMessage(id = "msg_ast_1", conversationId = "c1", role = "assistant", content = "Quantum computing uses qubits...")
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Quantum computing",
            messages = listOf(userMsg, astMsg),
            editingMessageId = "msg_user_1",
            editingMessageText = "Can you explain quantum computing in simple terms?"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a1_inline_editor_dark.png")
    }

    @Test
    fun capture_A2_AssistantMoreMenu_Light() {
        val userMsg = ChatMessage(id = "msg_user_1", conversationId = "c1", role = "user", content = "Hello!")
        val astMsg = ChatMessage(id = "msg_ast_1", conversationId = "c1", role = "assistant", content = "Hello! How can I help you today?")
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Greeting",
            messages = listOf(userMsg, astMsg),
            activeAssistantMessageMenuId = "msg_ast_1"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a2_assistant_more_menu_light.png")
    }

    @Test
    fun capture_A2_AssistantMoreMenu_Dark() {
        val userMsg = ChatMessage(id = "msg_user_1", conversationId = "c1", role = "user", content = "Hello!")
        val astMsg = ChatMessage(id = "msg_ast_1", conversationId = "c1", role = "assistant", content = "Hello! How can I help you today?")
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Greeting",
            messages = listOf(userMsg, astMsg),
            activeAssistantMessageMenuId = "msg_ast_1"
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a2_assistant_more_menu_dark.png")
    }

    @Test
    fun capture_A1_SelectTextSheet_Light() {
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Sample chat",
            messages = listOf(
                ChatMessage(id = "msg_1", conversationId = "c1", role = "assistant", content = "This is a detailed paragraph of text that can be freely selected and copied by the user.")
            ),
            activeSelectTextMessage = "This is a detailed paragraph of text that can be freely selected and copied by the user."
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a1_select_text_sheet_light.png")
    }

    @Test
    fun capture_A1_SelectTextSheet_Dark() {
        val state = BuildXUiState(
            isAuthenticated = true,
            activeConversationId = "c1",
            activeConversationTitle = "Sample chat",
            messages = listOf(
                ChatMessage(id = "msg_1", conversationId = "c1", role = "assistant", content = "This is a detailed paragraph of text that can be freely selected and copied by the user.")
            ),
            activeSelectTextMessage = "This is a detailed paragraph of text that can be freely selected and copied by the user."
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderChatScreen(state)
                }
            }
        }
        captureScreen("a1_select_text_sheet_dark.png")
    }

    @androidx.compose.runtime.Composable
    private fun RenderChatScreen(state: BuildXUiState) {
        ChatAndDrawerScreen(
            uiState = state,
            onToggleDrawer = {},
            onNewChat = {},
            onStartIncognito = {},
            onSelectConversation = {},
            onSearchQueryChange = {},
            onNavigateToNotifications = {},
            onNavigateToSettings = {},
            onNavigateToMedia = {},
            onComposerTextChange = {},
            onToggleMode = {},
            onPlusClick = {},
            onMicClick = {},
            onSendClick = {},
            onStopClick = {},
            onRemoveStagedAttachment = {},
            onRateMessage = { _, _ -> },
            onCopyMessage = {},
            onShareMessage = {},
            onOpenStepsSheet = {},
            onCloseStepsSheet = {},
            onToggleOverflowMenu = {},
            onShareConversationLink = {},
            onTogglePinConversation = {},
            onOpenRenameDialog = {},
            onOpenDeleteDialog = {},
            onCloseAttachmentSheet = {},
            onSeeAllPhotos = {},
            onCameraCapture = {},
            onToggleGallerySelection = {},
            onConfirmGallerySelection = {},
            onOpenRecentUploads = {},
            onPickFileDocument = {},
            onConfirmVoiceTranscript = {},
            onCancelVoiceDictation = {},
            onToggleModePopup = {},
            onSelectMode = {},
            modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)
        )
    }
}
