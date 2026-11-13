package app.buildx.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmConversationMetadata
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmMemberAvatar
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.DmPeerSummary
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.chat.ChatComposer
import app.buildx.chat.ui.chat.ChatInfoScreen
import app.buildx.chat.ui.chat.ChatWallpaperPickerScreen
import app.buildx.chat.ui.chat.toChatColors
import app.buildx.chat.ui.screens.DmConversationScreen
import app.buildx.chat.ui.screens.DmInboxScreen
import app.buildx.chat.ui.screens.DmNoteReplySheet
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.viewmodel.BuildXUiState
import app.buildx.chat.ui.viewmodel.DmFilterTab
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
import java.time.LocalDate

/**
 * Direct-message visual regression suite.
 *
 * Captures the six redesigned DM surfaces in both appearances so the notes
 * tray, group avatars, floating chat header, chat info sheet, message bubbles
 * and composer capsule can be reviewed side by side:
 *
 *  - dm_inbox_{light,dark}.png          notes tray + conversation rows
 *  - dm_chat_{light,dark}.png           header, bubbles, attachments, composer
 *  - dm_info_direct_{light,dark}.png    one-to-one chat info sheet
 *  - dm_info_group_{light,dark}.png     group chat info sheet
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel6)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class DmScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir = File("../docs/design/screenshots").apply { mkdirs() }

    private val meId = "user-me"

    private fun captureScreen(fileName: String) {
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        val roots = composeRule.onAllNodes(isRoot())
        val nodes = roots.fetchSemanticsNodes()
        val largest = nodes.indices.maxBy { i ->
            val b = nodes[i].boundsInRoot
            b.width * b.height
        } ?: 0
        roots[largest].captureRoboImage(
            file = File(outputDir, fileName),
            roborazziOptions = RoborazziOptions()
        )
    }

    private val me = UserProfile(
        id = meId,
        username = "voxin",
        handle = "voxin",
        displayName = "Voxin",
        bio = "Building Build X.",
        avatarUrl = null,
        isVerified = false
    )

    private val peer = DmPeerIntro(
        id = "user-sara",
        username = "sara",
        handle = "sara",
        displayName = "Sara Ahmed",
        avatarUrl = null,
        isVerified = true,
        bio = "Product designer. Coffee, typography and long walks.",
        followersCount = 1280,
        followingCount = 341,
        postsCount = 12,
        followingSince = "2026-02-14T10:00:00Z"
    )

    private val directConversation = DmConversationMetadata(
        id = "conv-direct",
        kind = "direct",
        title = null,
        avatarUrl = null,
        pinnedMessageId = "m-pin",
        createdBy = meId
    )

    private val groupConversation = DmConversationMetadata(
        id = "conv-group",
        kind = "group",
        title = "Design Crew",
        avatarUrl = null,
        pinnedMessageId = null,
        createdBy = meId
    )

    private val myParticipant = DmParticipant(
        userId = meId,
        username = "voxin",
        handle = "voxin",
        displayName = "Voxin",
        role = "admin",
        status = "active"
    )

    private val peerParticipant = DmParticipant(
        userId = "user-sara",
        username = "sara",
        handle = "sara",
        displayName = "Sara Ahmed",
        role = "member",
        status = "active",
        isMuted = false,
        isPinned = true
    )

    private val directDetail = DmConversationDetail(
        success = true,
        conversation = directConversation,
        pinnedMessage = DmMessage(
            id = "m-pin",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "Pin this: launch is Friday 10:00.",
            createdAt = "2026-11-12T08:00:00Z"
        ),
        participants = listOf(myParticipant, peerParticipant),
        peerIntro = peer
    )

    private val groupDetail = DmConversationDetail(
        success = true,
        conversation = groupConversation,
        participants = listOf(
            myParticipant,
            peerParticipant,
            DmParticipant(
                userId = "user-omar",
                username = "omar",
                handle = "omar",
                displayName = "Omar Khalid",
                role = "member",
                status = "active"
            ),
            DmParticipant(
                userId = "user-lina",
                username = "lina",
                handle = "lina",
                displayName = "Lina Haddad",
                role = "member",
                status = "active"
            )
        )
    )

    private val directMessages = listOf(
        DmMessage(
            id = "m1",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "Hey! Did you get a chance to look at the new DM composer?",
            createdAt = "2026-11-12T09:10:00Z"
        ),
        DmMessage(
            id = "m2",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "Yes — the capsule shadow and the spring resize feel great.",
            createdAt = "2026-11-12T09:12:00Z"
        ),
        DmMessage(
            id = "m3",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            messageType = "media",
            content = "Here is the shot from the shoot.",
            attachmentPath = "https://example.invalid/photo.jpg",
            attachmentMime = "image/jpeg",
            createdAt = "2026-11-12T09:14:00Z"
        ),
        DmMessage(
            id = "m4",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "هلا والله، شلونك؟",
            createdAt = "2026-11-12T09:16:00Z"
        ),
        DmMessage(
            id = "m5",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "Edited this one.",
            editedAt = "2026-11-12T09:20:00Z",
            createdAt = "2026-11-12T09:18:00Z"
        ),
        DmMessage(
            id = "m6",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "This message was removed.",
            deletedAt = "2026-11-12T09:22:00Z",
            createdAt = "2026-11-12T09:21:00Z"
        ),
        DmMessage(
            id = "m7",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            messageType = "note_reply",
            content = "Replied to your note!",
            noteSnapshotText = "Launch week energy",
            createdAt = "2026-11-12T09:24:00Z"
        )
    )

    private val groupMessages = listOf(
        DmMessage(
            id = "g1",
            conversationId = "conv-group",
            senderId = "user-omar",
            senderDisplayName = "Omar Khalid",
            content = "Uploaded the final icons to the shared drive.",
            createdAt = "2026-11-12T09:30:00Z"
        ),
        DmMessage(
            id = "g2",
            conversationId = "conv-group",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "Perfect. I will wire them into the theme tokens tonight.",
            createdAt = "2026-11-12T09:32:00Z"
        )
    )

    private fun note(
        id: String,
        author: DmPeerSummary,
        content: String,
        isOwn: Boolean = false
    ) = FeedNoteItem(
        id = id,
        userId = author.id,
        content = content,
        createdAt = "2026-11-12T07:00:00Z",
        expiresAt = "2026-11-13T07:00:00Z",
        isOwn = isOwn,
        author = author
    )

    private val mySummary = DmPeerSummary(
        id = meId,
        username = "voxin",
        handle = "voxin",
        displayName = "Voxin",
        avatarUrl = null
    )

    private val saraSummary = DmPeerSummary(
        id = "user-sara",
        username = "sara",
        handle = "sara",
        displayName = "Sara Ahmed",
        avatarUrl = null,
        isVerified = true
    )

    private val omarSummary = DmPeerSummary(
        id = "user-omar",
        username = "omar",
        handle = "omar",
        displayName = "Omar Khalid",
        avatarUrl = null
    )

    private fun inboxState() = BuildXUiState(
        isAuthenticated = true,
        profile = me,
        inboxNotesTray = listOf(
            note("n-own", mySummary, "Shipping the DM redesign today", isOwn = true),
            note("n-sara", saraSummary, "Launch week energy"),
            note("n-omar", omarSummary, "Icons are done")
        ),
        dmInboxItems = listOf(
            DmInboxItem(
                conversationId = "conv-direct",
                id = "conv-direct",
                kind = "direct",
                title = "Sara Ahmed",
                displayName = "Sara Ahmed",
                handle = "sara",
                avatarUrl = null,
                isVerified = true,
                unreadCount = 2,
                isPinned = true,
                lastMessageText = "Replied to your note!",
                lastMessageType = "note_reply",
                senderFirstName = "Sara",
                lastMessageAt = "2026-11-12T09:24:00Z",
                lastActivityAt = "2026-11-12T09:24:00Z",
                memberCount = 2
            ),
            DmInboxItem(
                conversationId = "conv-group",
                id = "conv-group",
                kind = "group",
                title = "Design Crew",
                displayName = "Design Crew",
                avatarUrl = null,
                isMuted = true,
                unreadCount = 5,
                lastMessageText = "Perfect. I will wire them into the theme tokens tonight.",
                lastMessageType = "text",
                senderFirstName = "You",
                lastMessageAt = "2026-11-12T09:32:00Z",
                lastActivityAt = "2026-11-12T09:32:00Z",
                memberCount = 4,
                memberAvatars = listOf(
                    DmMemberAvatar(userId = "user-sara", displayName = "Sara Ahmed", avatarUrl = null),
                    DmMemberAvatar(userId = "user-omar", displayName = "Omar Khalid", avatarUrl = null)
                )
            ),
            DmInboxItem(
                conversationId = "conv-omar",
                id = "conv-omar",
                kind = "direct",
                title = "Omar Khalid",
                displayName = "Omar Khalid",
                handle = "omar",
                avatarUrl = null,
                unreadCount = 0,
                lastMessageText = "See you at standup.",
                lastMessageType = "text",
                senderFirstName = "Omar",
                lastMessageAt = "2026-11-11T18:05:00Z",
                lastActivityAt = "2026-11-11T18:05:00Z",
                memberCount = 2
            )
        ),
        selectedDmFilterTab = DmFilterTab.ALL,
        totalUnreadDmCount = 7
    )

    private fun chatState(detail: DmConversationDetail, messages: List<DmMessage>) = BuildXUiState(
        isAuthenticated = true,
        profile = me,
        activeDmDetail = detail,
        dmMessages = messages,
        dmComposerText = ""
    )

    @Test
    fun capture_DmInbox_Light() {
        val state = inboxState()
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    DmInboxScreen(
                        uiState = state,
                        onBackClick = {},
                        onSearchQueryChange = {},
                        onConversationClick = {},
                        onOpenProfile = {},
                        onOpenNoteSheet = {},
                        onReplyToNote = { _, _ -> },
                        onCreateGroupClick = {},
                        onTogglePinConversation = {},
                        onToggleMuteConversation = {},
                        currentUserId = meId
                    )
                }
            }
        }
        captureScreen("dm_inbox_light.png")
    }

    @Test
    fun capture_DmInbox_Dark() {
        val state = inboxState()
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    DmInboxScreen(
                        uiState = state,
                        onBackClick = {},
                        onSearchQueryChange = {},
                        onConversationClick = {},
                        onOpenProfile = {},
                        onOpenNoteSheet = {},
                        onReplyToNote = { _, _ -> },
                        onCreateGroupClick = {},
                        onTogglePinConversation = {},
                        onToggleMuteConversation = {},
                        currentUserId = meId
                    )
                }
            }
        }
        captureScreen("dm_inbox_dark.png")
    }

    private val fixedToday: LocalDate = LocalDate.of(2026, 11, 12)

    @androidx.compose.runtime.Composable
    private fun ConversationScreen(state: BuildXUiState, conversationId: String = "conv-direct") {
        DmConversationScreen(
            uiState = state,
            conversationId = conversationId,
            onBackClick = {},
            onProfileClick = {},
            onSendMessage = {},
            onComposerTextChange = {},
            onAddStagedAttachment = { _: StagedAttachment -> },
            onRemoveStagedAttachment = { _: StagedAttachment -> },
            onReplyToMessage = {},
            onCancelReply = {},
            onEditMessage = {},
            onCancelEdit = {},
            onDeleteMessage = {},
            onTogglePinMessage = {},
            onTogglePinConversation = {},
            onToggleMuteConversation = {},
            onReportMessage = {},
            todayOverride = fixedToday
        )
    }

    @Test
    fun capture_DmChat_Light() {
        val state = chatState(directDetail, directMessages)
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    ConversationScreen(state)
                }
            }
        }
        captureScreen("dm_chat_light.png")
    }

    @Test
    fun capture_DmChat_Dark() {
        val state = chatState(directDetail, directMessages)
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    ConversationScreen(state)
                }
            }
        }
        captureScreen("dm_chat_dark.png")
    }

    @Test
    fun capture_DmGroupChat_Light() {
        val state = chatState(groupDetail, groupMessages)
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    ConversationScreen(state, conversationId = "conv-group")
                }
            }
        }
        captureScreen("dm_group_chat_light.png")
    }

    @androidx.compose.runtime.Composable
    private fun InfoScreen(detail: DmConversationDetail, messages: List<DmMessage>) {
        ChatInfoScreen(
            detail = detail,
            currentUserId = meId,
            messages = messages,
            isGroupAvatarUploading = false,
            isAddingGroupMembers = false,
            memberCandidates = emptyList(),
            isSearchingMembers = false,
            isSubmittingReport = false,
            reportError = null,
            isBlocking = false,
            onBackClick = {},
            onViewProfile = {},
            onJumpToMessage = {},
            onSearchMembers = {},
            onToggleMute = {},
            onTogglePin = {},
            onUpdateGroupAvatar = {},
            onRenameGroup = {},
            onAddMembers = {},
            onRemoveMember = {},
            onLeaveGroup = {},
            onClearHistory = {},
            onReport = { _: ReportReason, _: String? -> },
            onBlock = {}
        )
    }

    @Test
    fun capture_DmInfoDirect_Light() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    InfoScreen(directDetail, directMessages)
                }
            }
        }
        captureScreen("dm_info_direct_light.png")
    }

    @Test
    fun capture_DmInfoDirect_Dark() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    InfoScreen(directDetail, directMessages)
                }
            }
        }
        captureScreen("dm_info_direct_dark.png")
    }

    @Test
    fun capture_DmInfoGroup_Light() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    InfoScreen(groupDetail, groupMessages)
                }
            }
        }
        captureScreen("dm_info_group_light.png")
    }

    @Test
    fun capture_DmInfoGroup_Dark() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    InfoScreen(groupDetail, groupMessages)
                }
            }
        }
        captureScreen("dm_info_group_dark.png")
    }

    private fun matrixMessages(): List<DmMessage> = listOf(
        // Yesterday messages across midnight + grouped
        DmMessage(
            id = "mx-1",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "هلا",
            createdAt = "2026-11-11T23:54:00Z"
        ),
        DmMessage(
            id = "mx-2",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "هلا",
            createdAt = "2026-11-11T23:55:00Z"
        ),
        // Today after midnight
        DmMessage(
            id = "mx-3",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "Hi",
            createdAt = "2026-11-12T00:05:00Z"
        ),
        DmMessage(
            id = "mx-4",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "مرحبا، هذه رسالة عربية طويلة نسبيا لنتأكد أن التاريخ والحالة ينتقلان لسطر جديد عند امتلاء العرض بدون أي تداخل.",
            createdAt = "2026-11-12T00:05:30Z"
        ),
        DmMessage(
            id = "mx-5",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "Check https://buildx.chat/docs/spec/v2?tab=layout#matrix",
            createdAt = "2026-11-12T00:06:00Z"
        ),
        DmMessage(
            id = "mx-6",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "Mixed Arabic + English: تحديث نظام الدردشة Build X v2 جاهز للمراجعة.",
            createdAt = "2026-11-12T09:10:00Z"
        ),
        DmMessage(
            id = "mx-6b",
            conversationId = "conv-direct",
            senderId = "user-sara",
            senderDisplayName = "Sara Ahmed",
            content = "Approved!",
            createdAt = "2026-11-12T09:10:10Z"
        ),
        DmMessage(
            id = "mx-7",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "Just near wrap threshold width line check here!",
            createdAt = "2026-11-12T09:10:25Z"
        ),
        DmMessage(
            id = "mx-8",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "This message was removed.",
            deletedAt = "2026-11-12T09:11:00Z",
            createdAt = "2026-11-12T09:10:40Z"
        ),
        DmMessage(
            id = "mx-9",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "Still sending this packet...",
            createdAt = "2026-11-12T09:15:00Z",
            localSendStatus = DmSendStatus.SENDING
        ),
        DmMessage(
            id = "mx-10",
            conversationId = "conv-direct",
            senderId = meId,
            senderDisplayName = "Voxin",
            content = "Offline retry state",
            createdAt = "2026-11-12T09:16:00Z",
            localSendStatus = DmSendStatus.FAILED
        )
    )

    @Test
    fun capture_DmMatrix_Light_Ltr() {
        val state = chatState(directDetail, matrixMessages())
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                        ConversationScreen(state)
                    }
                }
            }
        }
        captureScreen("dm_matrix_light_ltr.png")
    }

    @Test
    fun capture_DmMatrix_Dark_Rtl() {
        val state = chatState(directDetail, matrixMessages())
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                        ConversationScreen(state)
                    }
                }
            }
        }
        captureScreen("dm_matrix_dark_rtl.png")
    }

    @Test
    fun capture_DmPeerIntro_Light() {
        val shortMessages = listOf(
            DmMessage(
                id = "pi-1",
                conversationId = "conv-direct",
                senderId = "user-sara",
                senderDisplayName = "Sara Ahmed",
                content = "هلا",
                createdAt = "2026-11-12T09:16:00Z"
            )
        )
        val detailNoPin = directDetail.copy(
            conversation = directDetail.conversation?.copy(pinnedMessageId = null),
            pinnedMessage = null
        )
        val state = chatState(detailNoPin, shortMessages)
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    ConversationScreen(state)
                }
            }
        }
        captureScreen("dm_peer_intro_light.png")
    }

    @Test
    fun capture_DmComposerStates() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                val chatColors = BuildXTheme.colors.toChatColors()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(chatColors.pageBackground)
                        .padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. Empty placeholder state (mic visible)
                    ChatComposer(
                        text = "",
                        onTextChange = {},
                        stagedAttachments = emptyList(),
                        onRemoveAttachment = {},
                        replyingTo = null,
                        onCancelReply = {},
                        editing = null,
                        onCancelEdit = {},
                        onAttachClick = {},
                        onSendClick = {},
                        onVoiceDictationClick = {},
                        chatColors = chatColors,
                        reduceMotion = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 2. Single-line Arabic state (send visible)
                    ChatComposer(
                        text = "هلا",
                        onTextChange = {},
                        stagedAttachments = emptyList(),
                        onRemoveAttachment = {},
                        replyingTo = null,
                        onCancelReply = {},
                        editing = null,
                        onCancelEdit = {},
                        onAttachClick = {},
                        onSendClick = {},
                        onVoiceDictationClick = {},
                        chatColors = chatColors,
                        reduceMotion = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 3. Multiline (6+ lines) expanding composer state
                    ChatComposer(
                        text = "Line 1: Checking multiline height growth.\nLine 2: Border stays 1dp.\nLine 3: Buttons keep aligned on baseline.\nLine 4: Mixed Arabic مرحبا بكم.\nLine 5: Smooth cursor & padding.\nLine 6: Max 6 lines before scroll.\nLine 7: Scrollable overflow line.",
                        onTextChange = {},
                        stagedAttachments = emptyList(),
                        onRemoveAttachment = {},
                        replyingTo = null,
                        onCancelReply = {},
                        editing = null,
                        onCancelEdit = {},
                        onAttachClick = {},
                        onSendClick = {},
                        onVoiceDictationClick = {},
                        chatColors = chatColors,
                        reduceMotion = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        captureScreen("dm_composer_states.png")
    }

    @Test
    fun capture_DmNoteReplySheet_Light() {
        val sampleNote = note(
            id = "note-sara-1",
            author = saraSummary,
            content = "Coffee by the sea before coding"
        ).copy(
            likesCount = 14,
            isLikedByMe = true
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                DmNoteReplySheet(
                    note = sampleNote,
                    onViewProfile = {},
                    onToggleLike = {},
                    onSendReply = {},
                    onDismiss = {}
                )
            }
        }
        captureScreen("dm_note_reply_light.png")
    }

    @Test
    fun capture_DmNoteReplySheet_Dark() {
        val sampleNote = note(
            id = "note-sara-2",
            author = saraSummary,
            content = "Hoola — let us ship Build X tonight"
        ).copy(
            likesCount = 8,
            isLikedByMe = true
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                DmNoteReplySheet(
                    note = sampleNote,
                    onViewProfile = {},
                    onToggleLike = {},
                    onSendReply = {},
                    onDismiss = {}
                )
            }
        }
        captureScreen("dm_note_reply_dark.png")
    }

    @Test
    fun capture_ChatWallpaperPicker_Light() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                ChatWallpaperPickerScreen(
                    initialConfig = ChatWallpaperConfig(
                        wallpaperId = "sakura_breeze",
                        dim = 0.15f,
                        blur = false
                    ),
                    isSpecificChat = true,
                    isApplying = false,
                    onBackClick = {},
                    onApplyWallpaper = { _, _, _ -> }
                )
            }
        }
        captureScreen("dm_wallpaper_picker_light.png")
    }

    @Test
    fun capture_ChatWallpaperPicker_Dark() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                ChatWallpaperPickerScreen(
                    initialConfig = ChatWallpaperConfig(
                        wallpaperId = "aurora_midnight",
                        dim = 0.25f,
                        blur = false
                    ),
                    isSpecificChat = true,
                    isApplying = false,
                    onBackClick = {},
                    onApplyWallpaper = { _, _, _ -> }
                )
            }
        }
        captureScreen("dm_wallpaper_picker_dark.png")
    }

    @Test
    fun capture_DmChat_Wallpaper_Dark() {
        val state = chatState(directDetail, directMessages).copy(
            activeDmWallpaper = ChatWallpaperConfig(
                wallpaperId = "aurora_midnight",
                dim = 0.15f,
                blur = false
            )
        )
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.DARK) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    ConversationScreen(state)
                }
            }
        }
        captureScreen("dm_chat_wallpaper_dark.png")
    }
}
