package app.buildx.chat

import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmReplyPreview
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.data.model.FeedNoteItem
import app.buildx.chat.data.model.NoteLikeToggleResult
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.data.model.WallpaperCategory
import app.buildx.chat.ui.chat.ChatWallpaperCatalog
import app.buildx.chat.ui.screens.RowAnchoredPopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmMessagingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDmInboxItemDeserializationDirect() {
        val rawJson = """
            {
                "id": "11111111-2222-3333-4444-555555555555",
                "kind": "direct",
                "title": null,
                "avatar_url": null,
                "is_pinned": true,
                "is_muted": false,
                "unread_count": 3,
                "peer": {
                    "id": "99999999-8888-7777-6666-555555555555",
                    "display_name": "Mustafa",
                    "username": "xxx",
                    "avatar_url": "https://example.com/avatar.jpg",
                    "is_verified": true
                },
                "last_message": {
                    "id": "aaaa-bbbb-cccc-dddd",
                    "sender_id": "99999999-8888-7777-6666-555555555555",
                    "content": "Hello Build X",
                    "message_type": "text",
                    "has_attachment": false,
                    "created_at": "2026-11-11T12:00:00Z"
                }
            }
        """.trimIndent()

        val item = json.decodeFromString<DmInboxItem>(rawJson)

        assertEquals("11111111-2222-3333-4444-555555555555", item.id)
        assertEquals("direct", item.kind)
        assertTrue(item.isPinned)
        assertFalse(item.isMuted)
        assertEquals(3, item.unreadCount)
        assertNotNull(item.peer)
        assertEquals("Mustafa", item.peer?.displayName)
        assertEquals("xxx", item.peer?.username)
        assertTrue(item.peer?.isVerified == true)
        assertEquals("https://example.com/avatar.jpg", item.peer?.avatarUrl)
        assertNotNull(item.lastMessage)
        assertEquals("Hello Build X", item.lastMessage?.content)
    }

    @Test
    fun testDmInboxItemDeserializationGroup() {
        val rawJson = """
            {
                "id": "22222222-3333-4444-5555-666666666666",
                "kind": "group",
                "title": "Build X Core Team",
                "avatar_url": "https://example.com/group.jpg",
                "is_pinned": false,
                "is_muted": true,
                "unread_count": 0,
                "peer": null,
                "last_message": {
                    "id": "bbbb-cccc-dddd-eeee",
                    "sender_id": "11111111-0000-0000-0000-000000000000",
                    "content": "Meeting scheduled",
                    "message_type": "text",
                    "has_attachment": false,
                    "created_at": "2026-11-11T11:00:00Z"
                }
            }
        """.trimIndent()

        val item = json.decodeFromString<DmInboxItem>(rawJson)

        assertEquals("22222222-3333-4444-5555-666666666666", item.id)
        assertEquals("group", item.kind)
        assertEquals("Build X Core Team", item.title)
        assertFalse(item.isPinned)
        assertTrue(item.isMuted)
        assertEquals(0, item.unreadCount)
        assertNull(item.peer)
        assertEquals("Meeting scheduled", item.lastMessage?.content)
    }

    @Test
    fun testDmMessageWithReplyAndAttachment() {
        val rawJson = """
            {
                "id": "msg-123",
                "conversation_id": "conv-456",
                "sender_id": "user-789",
                "content": "This is a reply with photo",
                "message_type": "text",
                "attachment_path": "https://example.com/photo.png",
                "created_at": "2026-11-11T13:00:00Z",
                "sender_username": "john_doe",
                "sender_display_name": "John Doe",
                "sender_avatar_url": "https://example.com/john.jpg",
                "sender_is_verified": true,
                "reply_to": {
                    "id": "msg-100",
                    "sender_id": "user-000",
                    "content": "Original message text",
                    "sender_display_name": "Alice"
                }
            }
        """.trimIndent()

        val msg = json.decodeFromString<DmMessage>(rawJson)

        assertEquals("msg-123", msg.id)
        assertEquals("conv-456", msg.conversationId)
        assertEquals("This is a reply with photo", msg.content)
        assertEquals("https://example.com/photo.png", msg.attachmentPath)
        assertEquals(DmSendStatus.SENT, msg.localSendStatus)
        assertEquals("John Doe", msg.senderDisplayName)
        assertTrue(msg.senderIsVerified)
        assertNotNull(msg.replyTo)
        assertEquals("msg-100", msg.replyTo?.id)
        assertEquals("Original message text", msg.replyTo?.content)
        assertEquals("Alice", msg.replyTo?.senderDisplayName)
    }

    @Test
    fun testDmMessageNoteReply() {
        val rawJson = """
            {
                "id": "msg-note-reply-1",
                "conversation_id": "conv-789",
                "sender_id": "user-123",
                "content": "Totally agree with your note!",
                "message_type": "note_reply",
                "note_snapshot_text": "Building great software with Compose",
                "created_at": "2026-11-11T14:00:00Z"
            }
        """.trimIndent()

        val msg = json.decodeFromString<DmMessage>(rawJson)

        assertEquals("note_reply", msg.messageType)
        assertEquals("Building great software with Compose", msg.noteSnapshotText)
        assertEquals("Totally agree with your note!", msg.content)
        assertNull(msg.replyTo)
    }

    @Test
    fun testFeedNoteItemDeserialization() {
        val rawJson = """
            {
                "id": "note-1",
                "user_id": "user-mustafa",
                "content": "Just curious...",
                "created_at": "2026-11-11T15:00:00Z",
                "expires_at": "2026-11-12T15:00:00Z",
                "is_own": true,
                "author": {
                    "id": "user-mustafa",
                    "username": "mustafa",
                    "display_name": "Mustafa",
                    "avatar_url": "https://example.com/mustafa.png",
                    "is_verified": true
                }
            }
        """.trimIndent()

        val note = json.decodeFromString<FeedNoteItem>(rawJson)

        assertEquals("note-1", note.id)
        assertEquals("user-mustafa", note.userId)
        assertEquals("Just curious...", note.content)
        assertTrue(note.isOwn)
        assertEquals("mustafa", note.author.username)
        assertEquals("Mustafa", note.author.displayName)
        assertTrue(note.author.isVerified)
    }

    @Test
    fun testSeenParticipantsMatching() {
        val p1 = DmParticipant(
            userId = "u1",
            role = "member",
            lastReadMessageId = "msg-99",
            username = "user1",
            displayName = "User One",
            avatarUrl = "https://example.com/u1.png"
        )
        val p2 = DmParticipant(
            userId = "u2",
            role = "member",
            lastReadMessageId = "msg-100",
            username = "user2",
            displayName = "User Two",
            avatarUrl = "https://example.com/u2.png"
        )
        val p3 = DmParticipant(
            userId = "current-user",
            role = "owner",
            lastReadMessageId = "msg-100",
            username = "me",
            displayName = "Me",
            avatarUrl = null
        )

        val participants = listOf(p1, p2, p3)
        val currentUserId = "current-user"

        // Seen avatars on message "msg-100" (excluding current user)
        val seenForMsg100 = participants.filter { it.userId != currentUserId && it.lastReadMessageId == "msg-100" }
        assertEquals(1, seenForMsg100.size)
        assertEquals("u2", seenForMsg100.first().userId)

        // Seen avatars on message "msg-99" (excluding current user)
        val seenForMsg99 = participants.filter { it.userId != currentUserId && it.lastReadMessageId == "msg-99" }
        assertEquals(1, seenForMsg99.size)
        assertEquals("u1", seenForMsg99.first().userId)
    }

    @Test
    fun testNoteLikesAndLikersDeserialization() {
        val publicNoteJson = """
            {
                "id": "note-like-1",
                "user_id": "owner-1",
                "content": "Shipping Build X",
                "created_at": "2026-11-12T10:00:00Z",
                "expires_at": "2026-11-13T10:00:00Z",
                "is_active": true,
                "likes_count": 2,
                "is_liked_by_me": false,
                "likers": [
                    {
                        "id": "u-2",
                        "username": "amira",
                        "handle": "amira",
                        "display_name": "Amira",
                        "avatar_url": "https://example.com/amira.png",
                        "is_verified": true
                    }
                ]
            }
        """.trimIndent()

        val publicNote = json.decodeFromString<PublicNote>(publicNoteJson)
        assertEquals(2, publicNote.likesCount)
        assertFalse(publicNote.isLikedByMe)
        assertEquals(1, publicNote.likers.size)
        assertEquals("Amira", publicNote.likers.first().displayName)
        assertTrue(publicNote.likers.first().isVerified)

        val feedNoteJson = """
            {
                "id": "note-like-2",
                "user_id": "friend-1",
                "content": "Coffee first",
                "created_at": "2026-11-12T11:00:00Z",
                "expires_at": "2026-11-13T11:00:00Z",
                "is_own": false,
                "likes_count": 4,
                "is_liked_by_me": true,
                "author": {
                    "id": "friend-1",
                    "username": "tariq",
                    "display_name": "Tariq",
                    "is_verified": false
                }
            }
        """.trimIndent()

        val feedNote = json.decodeFromString<FeedNoteItem>(feedNoteJson)
        assertEquals(4, feedNote.likesCount)
        assertTrue(feedNote.isLikedByMe)
        assertFalse(feedNote.isOwn)

        val toggleJson = """{"success": true, "liked": true, "likes_count": 5}"""
        val toggleResult = json.decodeFromString<NoteLikeToggleResult>(toggleJson)
        assertTrue(toggleResult.success)
        assertTrue(toggleResult.liked)
        assertEquals(5, toggleResult.likesCount)
    }

    @Test
    fun testRowAnchoredPopupPositionProviderLtrAndRtlAndAboveFallback() {
        val window = IntSize(1080, 2400)
        val popup = IntSize(600, 720)
        val gap = 24
        val margin = 40

        // Row near top -> places BELOW row cleanly separated by gap
        val topRow = IntRect(left = 0, top = 400, right = 1080, bottom = 600)
        var placedAbove = false
        val ltrProvider = RowAnchoredPopupPositionProvider(
            rowBounds = topRow,
            isRtl = false,
            gapPx = gap,
            edgeMarginPx = margin,
            onDirectionResolved = { placedAbove = it }
        )
        val posBelow = ltrProvider.calculatePosition(topRow, window, LayoutDirection.Ltr, popup)
        assertFalse(placedAbove)
        assertEquals(margin, posBelow.x)
        assertEquals(topRow.bottom + gap, posBelow.y)

        // Row near bottom -> flips ABOVE row with gap above row top
        val bottomRow = IntRect(left = 0, top = 1900, right = 1080, bottom = 2100)
        var placedAboveRtl = false
        val rtlProvider = RowAnchoredPopupPositionProvider(
            rowBounds = bottomRow,
            isRtl = true,
            gapPx = gap,
            edgeMarginPx = margin,
            onDirectionResolved = { placedAboveRtl = it }
        )
        val posAboveRtl = rtlProvider.calculatePosition(bottomRow, window, LayoutDirection.Rtl, popup)
        assertTrue(placedAboveRtl)
        assertEquals(bottomRow.right - margin - popup.width, posAboveRtl.x)
        assertEquals(bottomRow.top - gap - popup.height, posAboveRtl.y)
    }

    @Test
    fun testChatWallpaperCatalogHas24PresetsAndExactCategoryCounts() {
        val artistic = ChatWallpaperCatalog.artisticPresets
        assertEquals(24, artistic.size)
        assertEquals(25, ChatWallpaperCatalog.allSelectablePresets.size)
        assertEquals(
            ChatWallpaperCatalog.allSelectablePresets.size,
            ChatWallpaperCatalog.allSelectablePresets.map { it.id }.toSet().size
        )

        val byCategory = artistic.groupBy { it.category }.mapValues { it.value.size }
        assertEquals(4, byCategory[WallpaperCategory.SIGNATURE])
        assertEquals(5, byCategory[WallpaperCategory.ROMANCE])
        assertEquals(5, byCategory[WallpaperCategory.CINEMA])
        assertEquals(4, byCategory[WallpaperCategory.NATURE])
        assertEquals(4, byCategory[WallpaperCategory.ABSTRACT])
        assertEquals(2, byCategory[WallpaperCategory.PATTERNS])

        assertEquals("default", ChatWallpaperCatalog.findById(null).id)
        assertEquals("default", ChatWallpaperCatalog.findById("non_existent_id").id)
        assertEquals("aurora_midnight", ChatWallpaperCatalog.findById("  AURORA_MIDNIGHT ").id)
    }

    @Test
    fun testFeedNoteItemEffectiveNoteIdFallsBackCleanly() {
        val withNoteId = """
            {
                "id": "row-1",
                "note_id": "note-canonical-2",
                "user_id": "u-1",
                "content": "Hoola",
                "created_at": "2026-11-12T10:00:00Z",
                "expires_at": "2026-11-13T10:00:00Z",
                "author": { "id": "u-1", "username": "voxin" }
            }
        """.trimIndent()
        val parsedWithNoteId = json.decodeFromString<FeedNoteItem>(withNoteId)
        assertEquals("note-canonical-2", parsedWithNoteId.effectiveNoteId)

        val legacyWithoutNoteId = """
            {
                "id": "row-only-1",
                "user_id": "u-1",
                "content": "Hoola",
                "created_at": "2026-11-12T10:00:00Z",
                "expires_at": "2026-11-13T10:00:00Z",
                "author": { "id": "u-1", "username": "voxin" }
            }
        """.trimIndent()
        val parsedLegacy = json.decodeFromString<FeedNoteItem>(legacyWithoutNoteId)
        assertEquals("row-only-1", parsedLegacy.effectiveNoteId)
    }

    @Test
    fun testWallpaperDeserializationOnConversationDetailAndUserProfile() {
        val detailPayload = """
            {
                "success": true,
                "conversation": {
                    "id": "conv-wp-1",
                    "kind": "direct",
                    "wallpaper_id": "telegram_doodle",
                    "wallpaper_dim": 0.25,
                    "wallpaper_blur": false
                },
                "wallpaper_id": "velvet_rose",
                "wallpaper_custom_url": null,
                "wallpaper_dim": 0.95,
                "wallpaper_blur": true
            }
        """.trimIndent()
        val detail = json.decodeFromString<DmConversationDetail>(detailPayload)
        val resolved = detail.resolvedWallpaper
        assertEquals("velvet_rose", resolved.normalizedId)
        assertNull(resolved.customUrl)
        assertEquals(0.70f, resolved.dim, 0.001f)
        assertTrue(resolved.blur)
        assertFalse(resolved.isDefault)

        val profilePayload = """
            {
                "id": "user-1",
                "username": "voxin",
                "display_name": "Voxin",
                "default_wallpaper_id": "sakura_breeze",
                "default_wallpaper_custom_url": "",
                "default_wallpaper_dim": 0.30,
                "default_wallpaper_blur": true
            }
        """.trimIndent()
        val profile = json.decodeFromString<UserProfile>(profilePayload)
        val defaultWp = profile.defaultWallpaper
        assertEquals("sakura_breeze", defaultWp.normalizedId)
        assertEquals(0.30f, defaultWp.dim, 0.001f)
        assertTrue(defaultWp.blur)
        assertFalse(defaultWp.isCustom)
    }

    @Test
    fun testPinnedMessageDeserializationWithoutConversationIdDoesNotCrash() {
        val payload = """
            {
                "success": true,
                "conversation": {
                    "id": "conv-pin-1",
                    "kind": "group",
                    "title": "BuildX Core",
                    "pinned_message_id": "msg-pinned-1"
                },
                "pinned_message": {
                    "id": "msg-pinned-1",
                    "sender_id": "u-1",
                    "sender_display_name": "Mustafa",
                    "content": "Release checklist locked",
                    "attachment_mime": null,
                    "created_at": "2026-11-12T14:20:00Z"
                }
            }
        """.trimIndent()
        val detail = json.decodeFromString<DmConversationDetail>(payload)
        assertTrue(detail.success)
        assertNotNull(detail.pinnedMessage)
        assertEquals("msg-pinned-1", detail.pinnedMessage?.id)
        assertEquals("Release checklist locked", detail.pinnedMessage?.content)
        assertEquals("", detail.pinnedMessage?.conversationId)
    }

    @Test
    fun testDmInboxItemNormalizedAndLegacyEffectiveHelpers() {
        val normalized = """
            {
                "conversation_id": "conv-norm",
                "kind": "direct",
                "display_name": "Sara",
                "unread_count": 0,
                "is_marked_unread": true,
                "muted_until": "infinity",
                "last_message_id": "m-99",
                "last_message_text": "See you soon",
                "last_message_type": "text",
                "last_message_sender_id": "u-2",
                "last_message_at": "2026-11-12T15:00:00Z"
            }
        """.trimIndent()
        val item = json.decodeFromString<DmInboxItem>(normalized)
        assertEquals("conv-norm", item.effectiveId)
        assertEquals("m-99", item.effectiveLastMessageId)
        assertEquals("See you soon", item.effectiveLastMessageText)
        assertEquals("u-2", item.effectiveLastMessageSenderId)
        assertTrue(item.effectiveIsUnread)
        assertTrue(item.effectiveIsMuted)
    }
}
