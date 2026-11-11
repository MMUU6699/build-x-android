package app.buildx.chat

import app.buildx.chat.data.model.Conversation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationActionsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testConversationSerializationWithLockAndPin() {
        val rawJson = """
            {
                "id": "c1",
                "user_id": "u1",
                "title": "محادثة : الذكاء الاصطناعي",
                "pinned": true,
                "is_pinned": true,
                "is_locked": true,
                "updated_at": "2026-11-11T12:00:00Z"
            }
        """.trimIndent()

        val conv = json.decodeFromString<Conversation>(rawJson)

        assertEquals("c1", conv.id)
        assertEquals("u1", conv.userId)
        assertTrue(conv.effectivePinned)
        assertTrue(conv.isLocked)
        assertEquals("الذكاء الاصطناعي", conv.cleanTitle)
    }

    @Test
    fun testConversationDefaults() {
        val conv = Conversation(
            id = "c2",
            userId = "u2",
            title = "Test Chat"
        )

        assertFalse(conv.pinned)
        assertFalse(conv.isPinned)
        assertFalse(conv.effectivePinned)
        assertFalse(conv.isLocked)
        assertEquals("Test Chat", conv.cleanTitle)
    }

    @Test
    fun testConversationSortingWithPinnedAndLocked() {
        val c1 = Conversation(id = "1", userId = "u", title = "Chat 1", pinned = false, updatedAt = "2026-11-11T10:00:00Z")
        val c2 = Conversation(id = "2", userId = "u", title = "Chat 2", pinned = true, isPinned = true, updatedAt = "2026-11-11T09:00:00Z")
        val c3 = Conversation(id = "3", userId = "u", title = "Chat 3", pinned = false, isLocked = true, updatedAt = "2026-11-11T11:00:00Z")
        val c4 = Conversation(id = "4", userId = "u", title = "Chat 4", pinned = true, isLocked = true, updatedAt = "2026-11-11T08:00:00Z")

        val sorted = listOf(c1, c2, c3, c4).sortedWith(
            compareByDescending<Conversation> { it.effectivePinned }.thenByDescending { it.updatedAt }
        )

        // Pinned chats come first: c2 (09:00) then c4 (08:00)
        assertEquals("2", sorted[0].id)
        assertEquals("4", sorted[1].id)
        // Then unpinned chats sorted by updatedAt desc: c3 (11:00) then c1 (10:00)
        assertEquals("3", sorted[2].id)
        assertEquals("1", sorted[3].id)
    }
}
