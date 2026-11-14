package app.buildx.chat

import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmConversationMetadata
import app.buildx.chat.data.model.DmInboxItem
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmReplyPreview
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.ui.chat.ChatBidiFormatter
import app.buildx.chat.ui.chat.ChatBubblePosition
import app.buildx.chat.ui.chat.ChatDeliveryStatus
import app.buildx.chat.ui.chat.ChatListItem
import app.buildx.chat.ui.chat.ChatMessageMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `computeBubbleMetaPlacement keeps short Arabic RTL text and meta strictly disjoint at bottom right`() {
        // Simulates 1-word Arabic text (narrow text, wider meta "23:01 + check")
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
        // Meta sits at bottom-right [46, 118); 1-line text sits cleanly before gap [0, 36)
        assertEquals(rtlSpec.widthPx - 72, rtlSpec.metaX)
        assertEquals(0, rtlSpec.textX)
        assertTrue("RTL metaX (${rtlSpec.metaX}) must clear text + gap", rtlSpec.metaX >= rtlSpec.textX + 36 + 10)
        assertEquals(42 - 20, rtlSpec.metaY)
    }

    @Test
    fun `computeBubbleMetaPlacement wraps multi-line RTL below text at bottom right`() {
        val multiRtl = ChatMessageMapper.computeBubbleMetaPlacement(
            textWidthPx = 240,
            textHeightPx = 88,
            lineCount = 2,
            lastLineWidthPx = 90,
            metaWidthPx = 64,
            metaHeightPx = 18,
            maxWidthPx = 420,
            inlineGapPx = 10,
            wrapTopGapPx = 4,
            isRtlContent = true
        )
        assertFalse(multiRtl.isInline)
        assertEquals(240, multiRtl.widthPx)
        assertEquals(88 + 4 + 18, multiRtl.heightPx)
        assertEquals(240 - 64, multiRtl.metaX)
        assertEquals(88 + 4, multiRtl.metaY)
    }

    @Test
    fun `formatShortTime outputs 24-hour HH mm without AM or PM`() {
        assertEquals("09:05", ChatMessageMapper.formatShortTime("2026-11-12T09:05:00Z", ZoneOffset.UTC, Locale.US))
        assertEquals("23:01", ChatMessageMapper.formatShortTime("2026-11-12T23:01:00Z", ZoneOffset.UTC, Locale.US))
        assertEquals("00:00", ChatMessageMapper.formatShortTime("2026-11-12T00:00:00Z", ZoneOffset.UTC, Locale.US))
    }

    @Test
    fun `mapToReversedListItems resolves display name strictly and never leaks handle into name`() {
        val participants = listOf(
            DmParticipant(
                userId = "u-display",
                displayName = "Ali Hassan",
                handle = "alihassan",
                username = "ali_user",
                isVerified = true
            ),
            DmParticipant(
                userId = "u-handle-only",
                displayName = null,
                handle = "ghosthandle",
                username = "ghostuser",
                isVerified = false
            )
        )
        val messages = listOf(
            msg("m1", "u-display", "2026-11-12T23:00:00Z"),
            msg("m2", "u-handle-only", "2026-11-12T23:01:00Z")
        )

        val items = ChatMessageMapper.mapToReversedListItems(
            messages = messages,
            currentUserId = "me",
            isDirect = false,
            participants = participants,
            peerIntro = null,
            defaultRtl = false,
            zoneId = ZoneOffset.UTC
        ).filterIsInstance<ChatListItem.MessageItem>().reversed()

        assertEquals("Ali Hassan", items[0].resolvedSenderName)
        assertEquals("alihassan", items[0].resolvedSenderHandle)
        assertTrue(items[0].senderIsVerified)

        assertNull(items[1].resolvedSenderName)
        assertEquals("ghosthandle", items[1].resolvedSenderHandle)
        assertFalse(items[1].senderIsVerified)
    }

    @Test
    fun `mergeParticipantReadState and mergeMonotonicParticipants never regress read watermarks`() {
        val initial = listOf(
            DmParticipant(
                userId = "peer",
                lastReadAt = "2026-11-12T23:05:00Z",
                lastReadMessageId = "msg-5"
            )
        )

        // Older read receipt must be ignored
        val afterOlder = ChatMessageMapper.mergeParticipantReadState(
            currentParticipants = initial,
            readerUserId = "peer",
            readAtIso = "2026-11-12T23:01:00Z",
            lastReadMessageId = "msg-2"
        )
        assertEquals("2026-11-12T23:05:00Z", afterOlder.single().lastReadAt)
        assertEquals("msg-5", afterOlder.single().lastReadMessageId)

        // Newer read receipt with blank message id advances timestamp and preserves msg-5
        val afterNewerBlankId = ChatMessageMapper.mergeParticipantReadState(
            currentParticipants = afterOlder,
            readerUserId = "peer",
            readAtIso = "2026-11-12T23:08:00Z",
            lastReadMessageId = null
        )
        assertEquals("2026-11-12T23:08:00Z", afterNewerBlankId.single().lastReadAt)
        assertEquals("msg-5", afterNewerBlankId.single().lastReadMessageId)

        // Stale server fetch must not roll back newer in-memory read state
        val staleServer = listOf(
            DmParticipant(
                userId = "peer",
                lastReadAt = "2026-11-12T23:02:00Z",
                lastReadMessageId = "msg-3"
            )
        )
        val merged = ChatMessageMapper.mergeMonotonicParticipants(
            existing = afterNewerBlankId,
            incoming = staleServer
        )
        assertEquals("2026-11-12T23:08:00Z", merged.single().lastReadAt)
        assertEquals("msg-5", merged.single().lastReadMessageId)
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

    @Test
    fun `clusterFlagsAndCornerDpReflectBubblePositionsAndSystemBreaks`() {
        val messages = listOf(
            msg("m1", "me", "2026-11-12T10:00:00Z"),
            msg("m2", "me", "2026-11-12T10:00:25Z"),
            msg("m3", "me", "2026-11-12T10:00:50Z"),
            DmMessage(
                id = "sys1",
                conversationId = "c1",
                senderId = "me",
                messageType = "system",
                content = "Pinned a message",
                createdAt = "2026-11-12T10:01:05Z"
            ),
            msg("m4", "me", "2026-11-12T10:01:20Z")
        )
        val reversed = ChatMessageMapper.mapToReversedListItems(
            messages = messages,
            currentUserId = "me",
            isDirect = true,
            participants = emptyList(),
            peerIntro = null,
            defaultRtl = false,
            zoneId = ZoneOffset.UTC,
            locale = Locale.US
        ).filterIsInstance<ChatListItem.MessageItem>()

        // Reversed order: m4, sys1, m3, m2, m1
        val byId = reversed.associateBy { it.message.id }
        val m1 = byId.getValue("m1")
        val m2 = byId.getValue("m2")
        val m3 = byId.getValue("m3")
        val m4 = byId.getValue("m4")

        assertEquals(ChatBubblePosition.FIRST, m1.position)
        assertTrue(m1.isFirstInCluster)
        assertFalse(m1.isLastInCluster)
        assertEquals(18f, m1.topCornerDp, 0.01f)
        assertEquals(4f, m1.bottomCornerDp, 0.01f)

        assertEquals(ChatBubblePosition.MIDDLE, m2.position)
        assertFalse(m2.isFirstInCluster)
        assertFalse(m2.isLastInCluster)
        assertEquals(4f, m2.topCornerDp, 0.01f)
        assertEquals(4f, m2.bottomCornerDp, 0.01f)

        assertEquals(ChatBubblePosition.LAST, m3.position)
        assertFalse(m3.isFirstInCluster)
        assertTrue(m3.isLastInCluster)
        assertEquals(4f, m3.topCornerDp, 0.01f)
        assertEquals(18f, m3.bottomCornerDp, 0.01f)

        // System message broke the cluster so m4 starts a fresh SINGLE bubble
        assertEquals(ChatBubblePosition.SINGLE, m4.position)
        assertTrue(m4.isFirstInCluster)
        assertTrue(m4.isLastInCluster)
        assertEquals(18f, m4.topCornerDp, 0.01f)
        assertEquals(18f, m4.bottomCornerDp, 0.01f)
    }

    @Test
    fun `newlyInsertedTrackingMarksOptimisticAndServerMatchedIds`() {
        val messages = listOf(
            msg("old", "me", "2026-11-12T10:00:00Z"),
            msg("temp-uuid-1", "me", "2026-11-12T10:00:30Z", clientTempId = "client-1", status = DmSendStatus.SENDING)
        )
        val items = ChatMessageMapper.mapToReversedListItems(
            messages = messages,
            currentUserId = "me",
            isDirect = true,
            participants = emptyList(),
            peerIntro = null,
            defaultRtl = false,
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
            newlyInsertedIds = setOf("client-1")
        ).filterIsInstance<ChatListItem.MessageItem>().associateBy { it.message.id }

        assertFalse(items.getValue("old").isNewlyInserted)
        assertTrue(items.getValue("temp-uuid-1").isNewlyInserted)
    }

    @Test
    fun `bidiFormatterIsolatesMixedArabicAndEnglishInboxPreviews`() {
        assertTrue(ChatBidiFormatter.firstStrongIsRtl("مرحبا BuildX"))
        assertFalse(ChatBidiFormatter.firstStrongIsRtl("BuildX مرحبا"))

        val ltrPreview = ChatBidiFormatter.formatInboxPreview(
            prefix = "You",
            body = "مرحبا بالعالم!",
            isRtlContext = false
        )
        assertTrue(ltrPreview.startsWith("${ChatBidiFormatter.FSI}You${ChatBidiFormatter.PDI}:${ChatBidiFormatter.LRM} "))
        assertTrue(ltrPreview.endsWith("${ChatBidiFormatter.FSI}مرحبا بالعالم!${ChatBidiFormatter.PDI}"))

        val rtlPreview = ChatBidiFormatter.formatInboxPreview(
            prefix = "أحمد",
            body = "Deploy build v2?",
            isRtlContext = true
        )
        assertTrue(rtlPreview.contains("${ChatBidiFormatter.FSI}أحمد${ChatBidiFormatter.PDI}:${ChatBidiFormatter.RLM}"))
        assertTrue(rtlPreview.endsWith("${ChatBidiFormatter.FSI}Deploy build v2?${ChatBidiFormatter.PDI}"))

        val detail = DmConversationDetail(
            success = true,
            conversation = DmConversationMetadata(id = "g1", kind = "group", title = "فريق BuildX"),
            participants = listOf(DmParticipant(userId = "me", status = "active"))
        )
        val header = ChatMessageMapper.mapHeaderState(
            detail = detail,
            currentUserId = "me",
            typingUserIds = emptySet(),
            membersFormat = { "$it members" }
        )
        assertEquals("فريق BuildX", header.title)
        assertEquals("${ChatBidiFormatter.FSI}فريق BuildX${ChatBidiFormatter.PDI}", header.isolatedTitle)
    }

    @Test
    fun `editAndDeleteHelpersUpdateMessagesAndReplyPreviewsAtomically`() {
        val original = DmMessage(
            id = "m1",
            conversationId = "c1",
            senderId = "me",
            content = "Original",
            createdAt = "2026-11-12T10:00:00Z"
        )
        val reply = DmMessage(
            id = "m2",
            conversationId = "c1",
            senderId = "peer",
            content = "Reply",
            replyTo = DmReplyPreview(id = "m1", senderId = "me", content = "Original"),
            createdAt = "2026-11-12T10:01:00Z"
        )

        val afterEdit = ChatMessageMapper.applyMessageEditToHistory(
            messages = listOf(original, reply),
            messageId = "m1",
            newContent = "Edited text",
            editedAtIso = "2026-11-12T10:02:00Z"
        )
        assertEquals("Edited text", afterEdit[0].content)
        assertTrue(afterEdit[0].isEdited)
        assertEquals("Edited text", afterEdit[1].replyTo?.content)

        val afterDelete = ChatMessageMapper.applyMessageDeleteToHistory(
            messages = afterEdit,
            messageId = "m1",
            deletedAtIso = "2026-11-12T10:03:00Z"
        )
        assertTrue(afterDelete[0].isDeleted)
        assertFalse(afterDelete[0].isEdited)
        assertEquals("", afterDelete[0].content)
        assertTrue(afterDelete[1].replyTo?.isDeleted == true)
        assertEquals("", afterDelete[1].replyTo?.content)
    }

    @Test
    fun `computeInboxCountersIncludesMarkedUnreadAndGroups`() {
        val items = listOf(
            DmInboxItem(id = "c1", kind = "direct", unreadCount = 3),
            DmInboxItem(id = "c2", kind = "group", unreadCount = 0, isMarkedUnread = true),
            DmInboxItem(id = "c3", kind = "group", unreadCount = 2)
        )
        val counters = ChatMessageMapper.computeInboxCounters(items)
        assertEquals(6, counters.totalUnreadMessages)
        assertEquals(3, counters.unreadConversationsCount)
        assertEquals(2, counters.groupsCount)
    }
}
