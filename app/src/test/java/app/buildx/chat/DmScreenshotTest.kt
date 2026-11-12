package app.buildx.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmConversationMetadata
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmMemberAvatar
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmPeerIntro
import app.buildx.chat.data.model.DmPeerSummary
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.screens.DmChatInfoSheet
import app.buildx.chat.ui.screens.DmConversationScreen
import app.buildx.chat.ui.screens.DmInboxScreen
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

    @androidx.compose.runtime.Composable
    private fun ConversationScreen(state: BuildXUiState) {
        DmConversationScreen(
            uiState = state,
            conversationId = "conv-direct",
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
            onReportMessage = {}
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
                    DmConversationScreen(
                        uiState = state,
                        conversationId = "conv-group",
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
                        onReportMessage = {}
                    )
                }
            }
        }
        captureScreen("dm_group_chat_light.png")
    }

    @androidx.compose.runtime.Composable
    private fun InfoSheet(detail: DmConversationDetail, isDirect: Boolean, messages: List<DmMessage>) {
        DmChatInfoSheet(
            detail = detail,
            isDirect = isDirect,
            currentUserId = meId,
            messages = messages,
            isGroupAvatarUploading = false,
            isAddingGroupMembers = false,
            memberCandidates = emptyList(),
            isSearchingMembers = false,
            isSubmittingReport = false,
            reportError = null,
            isBlocking = false,
            onSearchMembers = {},
            onViewProfile = {},
            onToggleMute = {},
            onTogglePin = {},
            onUpdateGroupAvatar = {},
            onRenameGroup = {},
            onAddMembers = {},
            onLeaveGroup = {},
            onClearHistory = {},
            onReport = { _: ReportReason, _: String? -> },
            onBlock = {},
            onDismiss = {}
        )
    }

    @Test
    fun capture_DmInfoDirect_Light() {
        composeRule.setContent {
            BuildXTheme(appearance = AppAppearance.LIGHT) {
                Box(modifier = Modifier.fillMaxSize().background(BuildXTheme.colors.pageBackground)) {
                    InfoSheet(directDetail, true, directMessages)
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
                    InfoSheet(directDetail, true, directMessages)
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
                    InfoSheet(groupDetail, false, groupMessages)
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
                    InfoSheet(groupDetail, false, groupMessages)
                }
            }
        }
        captureScreen("dm_info_group_dark.png")
    }
}
