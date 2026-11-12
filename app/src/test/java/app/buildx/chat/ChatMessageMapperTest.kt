package app.buildx.chat

import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.ui.chat.ChatBubblePosition
import app.buildx.chat.ui.chat.ChatDeliveryStatus
import app.buildx.chat.ui.chat.ChatListItem
import app.buildx.chat.ui.chat.ChatMessageMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ChatMessageMapperTest {

    private fun msg(
        id: String,
        senderId: String,
        createdAt: String,
        content: String = "msg $id",
        clientTempId: String? = null,
        deletedAt: String? = null,
        status: DmSendStatus = DmSendStatus.SENT
    ) = DmMessage(
        id = id,
        conversationId = "conv-1",
        senderId = senderId,
        content = content,
        createdAt = createdAt,
        clientTempId = clientTempId,
        deletedAt = deletedAt,
        localSendStatus = status
    )

    @Test
    fun `consecutive messages within 2 minutes form FIRST MIDDLE LAST group`() {
        val messages = listOf(
            msg("1", "me", "2026-11-12T10:00:00Z"),
            msg("2", "me", "2026-11-12T10:00:45Z"),
            msg("3", "me", "2026-11-12T10:01:30Z"),
            msg("4", "peer", "2026-11-12T10:01:40Z")
        )

        val reversed = ChatMessageMapper.mapToReversedListItems(
            messages = messages,
            currentUserId = "me",
            isDirect = true,
            participants = emptyList(),
            peerIntro = null,
            defaultRtl = false,
            zoneId = ZoneOffset.UTC
        )

        val msgItems = reversed.filterIsInstance<ChatListItem.MessageItem>().reversed()
        assertEquals(ChatBubblePosition.FIRST, msgItems[0].position)
        assertFalse(msgItems[0].isGroupedWithPrevious)

        assertEquals(ChatBubblePosition.MIDDLE, msgItems[1].position)
        assertTrue(msgItems[1].isGroupedWithPrevious)

        assertEquals(ChatBubblePosition.LAST, msgItems[2].position)
        assertTrue(msgItems[2].isGroupedWithPrevious)

        assertEquals(ChatBubblePosition.SINGLE, msgItems[3].position)
        assertFalse(msgItems[3].isGroupedWithPrevious)
    }

    @Test
    fun `messages more than 2 minutes apart do not group`() {
        val messages = listOf(
            msg("1", "me", "2026-11-12T10:00:00Z"),
            msg("2", "me", "2026-11-12T10:03:00Z")
        )

        val reversed = ChatMessageMapper.mapToReversedListItems(
            messages = messages,
            currentUserId = "me",
            isDirect = true,
            participants = emptyList(),
            peerIntro = null,
            defaultRtl = false,
            zoneId = ZoneOffset.UTC
        )

        val msgItems = reversed.filterIsInstance<ChatListItem.MessageItem>().reversed()
        assertEquals(ChatBubblePosition.SINGLE, msgItems[0].position)
        assertEquals(ChatBubblePosition.SINGLE, msgItems[1].position)
        assertFalse(msgItems[1].isGroupedWithPrevious)
    }

    @Test
    fun `day boundaries insert DateSeparator items and break groups`() {
        val messages = listOf(
            msg("1", "me", "2026-11-11T23:59:30Z"),
            msg("2", "me", "2026-11-12T00:00:10Z")
        )

        val reversed = ChatMessageMapper.mapToReversedListItems(
            messages = messages,
            currentUserId = "me",
            isDirect = true,
            participants = emptyList(),
            peerIntro = null,
            defaultRtl = false,
            zoneId = ZoneOffset.UTC
        )

        val separators = reversed.filterIsInstance<ChatListItem.DateSeparator>()
        assertEquals(2, separators.size)
        val msgItems = reversed.filterIsInstance<ChatListItem.MessageItem>().reversed()
        assertEquals(ChatBubblePosition.SINGLE, msgItems[0].position)
        assertEquals(ChatBubblePosition.SINGLE, msgItems[1].position)
    }

    @Test
    fun `delivery status transitions from SENDING to SENT to READ and FAILED`() {
        val peer = DmParticipant(
            userId = "peer",
            lastReadMessageId = "2",
            lastReadAt = "2026-11-12T10:01:00Z"
        )
        val messages = listOf(
            msg("1", "me", "2026-11-12T10:00:00Z", status = DmSendStatus.SENT),
            msg("2", "me", "2026-11-12T10:01:00Z", status = DmSendStatus.SENT),
            msg("3", "me", "2026-11-12T10:02:00Z", status = DmSendStatus.SENT),
            msg("4", "me", "2026-11-12T10:02:10Z", status = DmSendStatus.SENDING),
            msg("5", "me", "2026-11-12T10:02:20Z", status = DmSendStatus.FAILED)
        )

        val reversed = ChatMessageMapper.mapToReversedListItems(
            messages = messages,
            currentUserId = "me",
            isDirect = true,
            participants = listOf(peer),
            peerIntro = null,
            defaultRtl = false,
            zoneId = ZoneOffset.UTC
        )

        val msgItems = reversed.filterIsInstance<ChatListItem.MessageItem>().reversed()
        assertEquals(ChatDeliveryStatus.READ, msgItems[0].deliveryStatus)
        assertEquals(ChatDeliveryStatus.READ, msgItems[1].deliveryStatus)
        assertEquals(ChatDeliveryStatus.SENT, msgItems[2].deliveryStatus)
        assertEquals(ChatDeliveryStatus.SENDING, msgItems[3].deliveryStatus)
        assertEquals(ChatDeliveryStatus.FAILED, msgItems[4].deliveryStatus)
    }

    @Test
    fun `optimistic reconciliation removes echoed temp message and keeps failed or unsent ones`() {
        val localSendingEchoed = msg(
            id = "temp-1",
            senderId = "me",
            createdAt = "2026-11-12T10:00:00Z",
            clientTempId = "client-uuid-1",
            status = DmSendStatus.SENDING
        )
        val localFailed = msg(
            id = "temp-2",
            senderId = "me",
            createdAt = "2026-11-12T10:00:05Z",
            clientTempId = "client-uuid-2",
            status = DmSendStatus.FAILED
        )
        val serverEcho = msg(
            id = "server-uuid-99",
            senderId = "me",
            createdAt = "2026-11-12T10:00:01Z",
            clientTempId = "client-uuid-1",
            status = DmSendStatus.SENT
        )

        val reconciled = ChatMessageMapper.reconcileOptimisticMessages(
            serverMessages = listOf(serverEcho),
            currentLocalMessages = listOf(localSendingEchoed, localFailed)
        )

        assertEquals(2, reconciled.size)
        assertEquals("server-uuid-99", reconciled[0].id)
        assertEquals("temp-2", reconciled[1].id)
        assertEquals(DmSendStatus.FAILED, reconciled[1].localSendStatus)
    }

    @Test
    fun `computeBubbleMetaPlacement places meta inline at exact threshold and wraps on 1px overflow`() {
        // Exact boundary: lastLineWidth(100) + gap(8) + metaWidth(52) == maxWidth(160)
        val exactFit = ChatMessageMapper.computeBubbleMetaPlacement(
            textWidthPx = 100,
            textHeightPx = 44,
            lineCount = 1,
            lastLineWidthPx = 100,
            metaWidthPx = 52,
            metaHeightPx = 20,
            maxWidthPx = 160,
            inlineGapPx = 8,
            wrapTopGapPx = 4,
            isRtlContent = false
        )
        assertTrue(exactFit.isInline)
        assertEquals(160, exactFit.widthPx)
        assertEquals(44, exactFit.heightPx)
        assertTrue(exactFit.metaX >= exactFit.textX + 100 + 8)

        // 1px over threshold: wraps cleanly below text
        val overflow = ChatMessageMapper.computeBubbleMetaPlacement(
            textWidthPx = 101,
            textHeightPx = 44,
            lineCount = 1,
            lastLineWidthPx = 101,
            metaWidthPx = 52,
            metaHeightPx = 20,
            maxWidthPx = 160,
            inlineGapPx = 8,
            wrapTopGapPx = 4,
            isRtlContent = false
        )
        assertFalse(overflow.isInline)
        assertEquals(101, overflow.widthPx)
        assertEquals(44 + 4 + 20, overflow.heightPx)
        assertEquals(44 + 4, overflow.metaY)
    }

    @Test
    fun `computeBubbleMetaPlacement keeps short Arabic RTL text and meta strictly disjoint and snug`() {
        // Simulates 1-word Arabic "هلا" (narrow text, wider meta "7:07 PM + check")
        val rtlSpec = ChatMessageMapper.computeBubbleMetaPlacement(
            textWidthPx = 36,
            textHeightPx = 42,
            lineCount = 1,
            lastLineWidthPx = 36,
            metaWidthPx = 72,
            metaHeightPx = 20,
            maxWidthPx = 420,
            inlineGapPx = 10,
            wrapTopGapPx = 4,
            isRtlContent = true
        )
        assertTrue(rtlSpec.isInline)
        assertEquals(36 + 10 + 72, rtlSpec.widthPx)
        // Meta sits at physical left [0, 72); text sits at physical right [82, 118)
        assertEquals(0, rtlSpec.metaX)
        assertTrue("RTL textX (${rtlSpec.textX}) must be >= metaWidth + gap", rtlSpec.textX >= 72 + 10)
        assertEquals(42 - 20, rtlSpec.metaY)
    }

    @Test
    fun `formatDayLabel handles midnight across non-UTC timezones and DST without clock times`() {
        val today = LocalDate.of(2026, 11, 2)
        val nyZone = ZoneId.of("America/New_York")

        // 2026-11-01 in America/New_York is fall-back DST day (25-hour day):
        // 04:30Z is 2026-11-01 00:30 EDT (Yesterday), 03:30Z is 2026-10-31 23:30 EDT (same year day).
        assertEquals("Today", ChatMessageMapper.formatDayLabel("2026-11-02T05:15:00Z", "Today", "Yesterday", nyZone, Locale.US, today))
        assertEquals("Yesterday", ChatMessageMapper.formatDayLabel("2026-11-01T04:30:00Z", "Today", "Yesterday", nyZone, Locale.US, today))
        val prevDayLabel = ChatMessageMapper.formatDayLabel("2026-11-01T03:30:00Z", "Today", "Yesterday", nyZone, Locale.US, today)
        assertFalse("Date label must not contain clock time: $prevDayLabel", prevDayLabel.contains(":"))
        assertTrue(prevDayLabel.contains("Oct") && prevDayLabel.contains("31"))

        // Previous year includes year without clock time
        val priorYearLabel = ChatMessageMapper.formatDayLabel("2025-08-19T22:10:00Z", "Today", "Yesterday", nyZone, Locale.US, today)
        assertTrue(priorYearLabel.contains("2025"))
        assertFalse(priorYearLabel.contains(":"))

        // Spring-forward 2026-03-08 in America/New_York separates cleanly on day boundary
        val springMessages = listOf(
            msg("s1", "me", "2026-03-08T04:50:00Z"), // Mar 7 23:50 EST
            msg("s2", "me", "2026-03-08T05:10:00Z")  // Mar 8 00:10 EST
        )
        val springList = ChatMessageMapper.mapToReversedListItems(
            messages = springMessages,
            currentUserId = "me",
            isDirect = true,
            participants = emptyList(),
            peerIntro = null,
            defaultRtl = false,
            zoneId = nyZone,
            locale = Locale.US
        )
        val dateLabels = springList.filterIsInstance<ChatListItem.DateSeparator>().reversed().map {
            ChatMessageMapper.formatDayLabel(
                iso = it.createdAtIso,
                todayLabel = "Today",
                yesterdayLabel = "Yesterday",
                zoneId = nyZone,
                locale = Locale.US,
                todayOverride = LocalDate.of(2026, 3, 9)
            )
        }
        assertEquals(2, dateLabels.size)
        assertEquals("Yesterday", dateLabels[1])
        assertFalse(dateLabels[0].contains(":"))
    }
}
