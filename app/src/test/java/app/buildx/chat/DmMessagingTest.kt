package app.buildx.chat

import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmReplyPreview
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.data.model.FeedNoteItem
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
}
