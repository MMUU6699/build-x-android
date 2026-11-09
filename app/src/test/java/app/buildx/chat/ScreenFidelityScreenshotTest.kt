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
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.screens.AppAppearanceScreen
import app.buildx.chat.ui.screens.AppInfoScreen
import app.buildx.chat.ui.screens.AppLanguageScreen
import app.buildx.chat.ui.screens.ChatAndDrawerScreen
import app.buildx.chat.ui.screens.NotificationsScreen
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
