package app.buildx.chat.ui.chat

import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.data.model.DmParticipant
import app.buildx.chat.data.model.DmSendStatus
import app.buildx.chat.ui.theme.isRtlText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max

/**
 * Result of computing inline-vs-trailing metadata placement inside a message bubble.
 */
data class BubbleMetaLayoutSpec(
    val widthPx: Int,
    val heightPx: Int,
    val textX: Int,
    val textY: Int,
    val metaX: Int,
    val metaY: Int,
    val isInline: Boolean
)

/**
 * Shared link extracted from a message body.
 */
data class ChatSharedLink(
    val messageId: String,
    val url: String,
    val senderName: String?,
    val createdAt: String
)

/**
 * Pure mapper converting raw Supabase/Repository DM models into immutable UI items
 * ready for a `reverseLayout = true` LazyColumn (index 0 = newest at bottom), plus
 * pure geometry & link extraction helpers.
 */
object ChatMessageMapper {

    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /**
     * Computes bubble content dimensions and disjoint coordinates for text + metadata.
     *
     * Overlap is impossible by construction:
     * - When `(lastLineWidthPx + inlineGapPx + metaWidthPx <= maxWidthPx)`:
     *   - For 1-line text, bubble width = `max(textWidthPx, lastLineWidthPx + inlineGapPx + metaWidthPx)` (<= maxWidthPx).
     *   - For multi-line text, bubble width = `max(textWidthPx, lastLineWidthPx + inlineGapPx + metaWidthPx)` (<= maxWidthPx).
     *   - For LTR text (`isRtlContent = false`), text starts at X=0 and meta is anchored bottom-right (`widthPx - metaWidthPx`).
     *   - For RTL text (`isRtlContent = true`), text is anchored bottom/top-right (`widthPx - textWidthPx`) and meta is anchored bottom-left (`X=0`).
     * - Otherwise meta wraps below text (`metaY = textHeightPx + wrapTopGapPx`) anchored to the bubble's text-direction end edge.
     */
    fun computeBubbleMetaPlacement(
        textWidthPx: Int,
        textHeightPx: Int,
        lineCount: Int,
        lastLineWidthPx: Int,
        metaWidthPx: Int,
        metaHeightPx: Int,
        maxWidthPx: Int,
        inlineGapPx: Int,
        wrapTopGapPx: Int,
        isRtlContent: Boolean
    ): BubbleMetaLayoutSpec {
        val safeMaxWidth = max(maxWidthPx, max(textWidthPx, metaWidthPx)).coerceAtLeast(1)
        val requiredInlineWidth = lastLineWidthPx + inlineGapPx + metaWidthPx
        val fitsInline = lineCount > 0 && requiredInlineWidth <= safeMaxWidth

        return if (fitsInline) {
            val bubbleWidth = max(textWidthPx, requiredInlineWidth).coerceAtMost(safeMaxWidth)
            val textX = if (isRtlContent) (bubbleWidth - textWidthPx).coerceAtLeast(0) else 0
            val metaX = if (isRtlContent) 0 else (bubbleWidth - metaWidthPx).coerceAtLeast(0)
            val metaY = (textHeightPx - metaHeightPx).coerceAtLeast(0)
            val bubbleHeight = max(textHeightPx, metaY + metaHeightPx)
            BubbleMetaLayoutSpec(
                widthPx = bubbleWidth,
                heightPx = bubbleHeight,
                textX = textX,
                textY = 0,
                metaX = metaX,
                metaY = metaY,
                isInline = true
            )
        } else {
            val bubbleWidth = max(textWidthPx, metaWidthPx).coerceAtMost(safeMaxWidth)
            val textX = if (isRtlContent) (bubbleWidth - textWidthPx).coerceAtLeast(0) else 0
            val metaX = if (isRtlContent) 0 else (bubbleWidth - metaWidthPx).coerceAtLeast(0)
            val metaY = textHeightPx + wrapTopGapPx
            BubbleMetaLayoutSpec(
                widthPx = bubbleWidth,
                heightPx = metaY + metaHeightPx,
                textX = textX,
                textY = 0,
                metaX = metaX,
                metaY = metaY,
                isInline = false
            )
        }
    }

    /**
     * Reconciles server-fetched/realtime messages with local optimistic (SENDING / FAILED) messages
     * by matching `clientTempId` or `id`, ensuring zero duplicates and chronological ordering.
     */
    fun reconcileOptimisticMessages(
        serverMessages: List<DmMessage>,
        currentLocalMessages: List<DmMessage>
    ): List<DmMessage> {
        val serverIds = serverMessages.mapTo(HashSet(serverMessages.size)) { it.id }
        val serverClientTempIds = serverMessages.mapNotNullTo(HashSet()) { it.clientTempId }

        val pendingLocal = currentLocalMessages.filter { local ->
            (local.localSendStatus == DmSendStatus.SENDING || local.localSendStatus == DmSendStatus.FAILED) &&
                local.id !in serverIds &&
                (local.clientTempId == null || local.clientTempId !in serverClientTempIds)
        }

        val merged = LinkedHashMap<String, DmMessage>(serverMessages.size + pendingLocal.size)
        for (msg in serverMessages) {
            merged[msg.id] = msg
        }
        for (local in pendingLocal) {
            merged[local.id] = local
        }

        return merged.values.sortedWith(
            compareBy<DmMessage> { parseInstantMillis(it.createdAt) ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )
    }

    /**
     * Maps chronological [messages] (oldest -> newest) into reversed UI items
     * (newest -> oldest) for `LazyColumn(reverseLayout = true)`.
     * Date separators appear ONLY when the calendar day changes in [zoneId].
     */
    fun mapToReversedListItems(
        messages: List<DmMessage>,
        currentUserId: String,
        isDirect: Boolean,
        participants: List<DmParticipant>,
        peerIntro: app.buildx.chat.data.model.DmPeerIntro?,
        defaultRtl: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<ChatListItem> {
        if (messages.isEmpty()) {
            return if (isDirect && peerIntro != null) {
                listOf(ChatListItem.PeerIntroItem(stableKey = "peer_intro_${peerIntro.id}", intro = peerIntro))
            } else {
                emptyList()
            }
        }

        val otherParticipants = participants.filter { it.userId != currentUserId }
        val maxPeerReadAtMillis = otherParticipants
            .mapNotNull { it.lastReadAt?.let(::parseInstantMillis) }
            .maxOrNull()

        val chronologicalItems = ArrayList<ChatListItem>(messages.size + 8)

        if (isDirect && peerIntro != null) {
            chronologicalItems.add(
                ChatListItem.PeerIntroItem(stableKey = "peer_intro_${peerIntro.id}", intro = peerIntro)
            )
        }

        for (i in messages.indices) {
            val msg = messages[i]
            val prev = messages.getOrNull(i - 1)
            val next = messages.getOrNull(i + 1)

            val msgMillis = parseInstantMillis(msg.createdAt)
            val prevMillis = prev?.let { parseInstantMillis(it.createdAt) }
            val nextMillis = next?.let { parseInstantMillis(it.createdAt) }

            val msgDay = msgMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            val prevDay = prevMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            val nextDay = nextMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }

            if (prev == null || msgDay != prevDay) {
                val dayKey = msgDay?.toString() ?: msg.createdAt.take(10)
                chronologicalItems.add(
                    ChatListItem.DateSeparator(
                        stableKey = "date_sep_$dayKey",
                        dayKey = dayKey,
                        createdAtIso = msg.createdAt
                    )
                )
            }

            val isDeleted = msg.deletedAt != null
            val isOwn = msg.senderId == currentUserId

            val groupedWithPrev = prev != null &&
                prev.deletedAt == null &&
                !isDeleted &&
                prev.senderId == msg.senderId &&
                msgDay == prevDay &&
                msgMillis != null && prevMillis != null &&
                (msgMillis - prevMillis) in 0L..ChatDimens.GroupMergeThresholdMillis

            val groupedWithNext = next != null &&
                next.deletedAt == null &&
                !isDeleted &&
                next.senderId == msg.senderId &&
                msgDay == nextDay &&
                msgMillis != null && nextMillis != null &&
                (nextMillis - msgMillis) in 0L..ChatDimens.GroupMergeThresholdMillis

            val position = when {
                groupedWithPrev && groupedWithNext -> ChatBubblePosition.MIDDLE
                groupedWithPrev -> ChatBubblePosition.LAST
                groupedWithNext -> ChatBubblePosition.FIRST
                else -> ChatBubblePosition.SINGLE
            }

            val deliveryStatus = computeDeliveryStatus(
                message = msg,
                isOwn = isOwn,
                msgMillis = msgMillis,
                otherParticipants = otherParticipants,
                maxPeerReadAtMillis = maxPeerReadAtMillis
            )

            val seenAtThisMessage = otherParticipants.filter {
                it.userId != msg.senderId && it.lastReadMessageId == msg.id
            }

            chronologicalItems.add(
                ChatListItem.MessageItem(
                    stableKey = msg.clientTempId ?: msg.id,
                    message = msg,
                    isOwn = isOwn,
                    isGroupChat = !isDirect,
                    isDeleted = isDeleted,
                    isEdited = msg.editedAt != null,
                    isRtlContent = isRtlText(msg.content, defaultRtl = defaultRtl),
                    position = position,
                    isGroupedWithPrevious = groupedWithPrev,
                    showSenderName = !isDirect && !isOwn && !isDeleted && !groupedWithPrev && !msg.senderDisplayName.isNullOrBlank(),
                    showSenderAvatar = !isDirect && !isOwn && !groupedWithNext,
                    formattedTime = formatShortTime(msg.createdAt, zoneId, locale),
                    deliveryStatus = deliveryStatus,
                    replyPreview = msg.replyTo,
                    seenParticipants = seenAtThisMessage
                )
            )
        }

        // Reverse for LazyColumn(reverseLayout = true) so index 0 is newest at bottom
        return chronologicalItems.asReversed()
    }

    fun computeDeliveryStatus(
        message: DmMessage,
        isOwn: Boolean,
        msgMillis: Long?,
        otherParticipants: List<DmParticipant>,
        maxPeerReadAtMillis: Long?
    ): ChatDeliveryStatus {
        if (!isOwn) return ChatDeliveryStatus.SENT
        return when (message.localSendStatus) {
            DmSendStatus.SENDING -> ChatDeliveryStatus.SENDING
            DmSendStatus.FAILED -> ChatDeliveryStatus.FAILED
            DmSendStatus.SENT -> {
                val isReadByPointer = otherParticipants.any { it.lastReadMessageId == message.id }
                val isReadByTimestamp = msgMillis != null && maxPeerReadAtMillis != null && maxPeerReadAtMillis >= msgMillis
                if (isReadByPointer || isReadByTimestamp) {
                    ChatDeliveryStatus.READ
                } else {
                    ChatDeliveryStatus.SENT
                }
            }
        }
    }

    fun mapHeaderState(
        detail: DmConversationDetail?,
        currentUserId: String,
        typingUserIds: Set<String>,
        membersFormat: (Int) -> String
    ): ChatHeaderUiState {
        val conv = detail?.conversation
        val isDirect = conv?.kind != "group"
        val peerIntro = detail?.peerIntro
        val myParticipant = detail?.participants?.firstOrNull { it.userId == currentUserId }
        val isTyping = typingUserIds.any { it != currentUserId }

        val title = if (isDirect) {
            peerIntro?.displayName?.takeIf { it.isNotBlank() }
                ?: peerIntro?.handle
                ?: ""
        } else {
            conv?.title ?: ""
        }

        val subtitle = if (isDirect) {
            peerIntro?.handle?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""
        } else {
            val activeCount = detail?.participants?.count { it.status == "active" } ?: 0
            membersFormat(activeCount)
        }

        return ChatHeaderUiState(
            title = title,
            subtitle = subtitle,
            avatarUrl = if (isDirect) peerIntro?.avatarUrl else conv?.avatarUrl,
            isDirect = isDirect,
            isVerified = isDirect && peerIntro?.isVerified == true,
            isMuted = myParticipant?.isMuted == true,
            isPinned = myParticipant?.isPinned == true,
            isTyping = isTyping,
            isOnline = false,
            peerHandle = peerIntro?.handle
        )
    }

    fun extractSharedMedia(messages: List<DmMessage>): List<DmMessage> =
        messages.filter {
            it.deletedAt == null &&
                !it.attachmentPath.isNullOrBlank() &&
                (it.attachmentMime == null || it.attachmentMime.startsWith("image/", ignoreCase = true) ||
                    it.attachmentMime.startsWith("video/", ignoreCase = true))
        }.asReversed()

    fun extractSharedFiles(messages: List<DmMessage>): List<DmMessage> =
        messages.filter {
            it.deletedAt == null &&
                !it.attachmentPath.isNullOrBlank() &&
                it.attachmentMime != null &&
                !it.attachmentMime.startsWith("image/", ignoreCase = true) &&
                !it.attachmentMime.startsWith("video/", ignoreCase = true)
        }.asReversed()

    fun extractSharedLinks(messages: List<DmMessage>): List<ChatSharedLink> {
        val out = mutableListOf<ChatSharedLink>()
        for (msg in messages.asReversed()) {
            if (msg.deletedAt != null || msg.content.isBlank()) continue
            URL_REGEX.findAll(msg.content).forEach { match ->
                val cleanUrl = match.value.trimEnd('.', ',', ';', ')', ']')
                if (cleanUrl.isNotBlank()) {
                    out.add(
                        ChatSharedLink(
                            messageId = msg.id,
                            url = cleanUrl,
                            senderName = msg.senderDisplayName,
                            createdAt = msg.createdAt
                        )
                    )
                }
            }
        }
        return out
    }

    fun parseInstantMillis(iso: String): Long? = runCatching {
        Instant.parse(iso).toEpochMilli()
    }.getOrNull()

    fun formatShortTime(
        iso: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = runCatching {
        val formatter = DateTimeFormatter.ofPattern("h:mm a", locale)
        Instant.parse(iso).atZone(zoneId).format(formatter)
    }.getOrDefault("")

    fun formatDayLabel(
        iso: String,
        todayLabel: String,
        yesterdayLabel: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        todayOverride: LocalDate? = null
    ): String {
        val date = runCatching { Instant.parse(iso).atZone(zoneId).toLocalDate() }.getOrNull()
            ?: return ""
        val today = todayOverride ?: LocalDate.now(zoneId)
        return when (date) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            else -> if (date.year == today.year) {
                date.format(DateTimeFormatter.ofPattern("d MMM", locale))
            } else {
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            }
        }
    }
}
