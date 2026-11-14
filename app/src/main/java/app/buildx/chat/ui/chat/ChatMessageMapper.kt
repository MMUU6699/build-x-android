package app.buildx.chat.ui.chat

import app.buildx.chat.data.model.DmConversationDetail
import app.buildx.chat.data.model.DmInboxItem
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
 * Pure Unicode BiDi isolation utilities safe for both Android UI and JVM unit tests.
 * Uses FSI (\u2068), LRI (\u2066), RLI (\u2067), and PDI (\u2069) plus directional marks
 * to prevent mixed Arabic/English strings, handles, timestamps, and prefixes from colliding.
 */
object ChatBidiFormatter {
    const val LRM: Char = '\u200E'
    const val RLM: Char = '\u200F'
    const val LRI: Char = '\u2066'
    const val RLI: Char = '\u2067'
    const val FSI: Char = '\u2068'
    const val PDI: Char = '\u2069'

    fun firstStrongIsRtl(text: String, defaultRtl: Boolean = false): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when (Character.getDirectionality(cp)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE -> return false
            }
            i += Character.charCount(cp)
        }
        return defaultRtl
    }

    /**
     * Wraps [text] in Unicode First-Strong Isolate (`FSI ... PDI`).
     */
    fun isolateFirstStrong(text: String): String {
        if (text.isBlank()) return text
        if (text.length >= 2 && text.first() == FSI && text.last() == PDI) return text
        return "$FSI$text$PDI"
    }

    /**
     * Wraps [text] in an explicit LTR (`LRI ... PDI`) or RTL (`RLI ... PDI`) isolate.
     */
    fun isolateWithDirection(text: String, isRtl: Boolean): String {
        if (text.isBlank()) return text
        val open = if (isRtl) RLI else LRI
        if (text.length >= 2 && text.first() == open && text.last() == PDI) return text
        return "$open$text$PDI"
    }

    fun isolateLtr(text: String): String = isolateWithDirection(text, isRtl = false)

    fun isolateRtl(text: String): String = isolateWithDirection(text, isRtl = true)

    /**
     * Formats an inbox preview with strict BiDi isolation around both an optional
     * sender/self prefix (e.g. `"You"`, `"أحمد"`) and the message body so mixed
     * Arabic/English fragments never reorder colons or punctuation.
     */
    fun formatInboxPreview(
        prefix: String?,
        body: String,
        isRtlContext: Boolean = false
    ): String {
        val cleanBody = body.trim()
        if (cleanBody.isEmpty()) return ""
        val isolatedBody = isolateFirstStrong(cleanBody)
        val cleanPrefix = prefix?.trim().orEmpty()
        if (cleanPrefix.isEmpty()) return isolatedBody
        val isolatedPrefix = isolateFirstStrong(cleanPrefix)
        val dirMark = if (isRtlContext) RLM else LRM
        return "$isolatedPrefix:$dirMark $isolatedBody"
    }
}

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
     * Metadata (`HH:mm` + status checkmark) always anchors to the bottom-right corner
     * (`metaX = bubbleWidth - metaWidthPx`), matching modern Telegram/Signal ergonomics
     * across both LTR and RTL content without ever overlapping text:
     * - LTR (`isRtlContent = false`): inline on the bottom line whenever
     *   `lastLineWidthPx + inlineGapPx + metaWidthPx <= safeMaxWidth`; otherwise wraps below.
     * - RTL (`isRtlContent = true`): inline to the right of 1-line text when
     *   `lineCount == 1 && lastLineWidthPx + inlineGapPx + metaWidthPx <= safeMaxWidth`;
     *   otherwise wraps cleanly below at bottom-right so multi-line RTL lines are never overlapped.
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
        val canInlineRtl = !isRtlContent || lineCount == 1
        val fitsInline = lineCount > 0 && canInlineRtl && requiredInlineWidth <= safeMaxWidth

        return if (fitsInline) {
            val bubbleWidth = max(textWidthPx, requiredInlineWidth).coerceAtMost(safeMaxWidth)
            val metaX = (bubbleWidth - metaWidthPx).coerceAtLeast(0)
            val textX = if (isRtlContent) (metaX - inlineGapPx - textWidthPx).coerceAtLeast(0) else 0
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
            val metaX = (bubbleWidth - metaWidthPx).coerceAtLeast(0)
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

    fun isSystemEventMessage(msg: DmMessage): Boolean {
        if (msg.isDeleted) return false
        if (msg.messageType.equals("system", ignoreCase = true)) return true
        val trimmed = msg.content.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.equals("created the group", ignoreCase = true) ||
            trimmed.equals("joined the group", ignoreCase = true) ||
            trimmed.equals("left the group", ignoreCase = true) ||
            trimmed.startsWith("created the group", ignoreCase = true) ||
            trimmed.endsWith("created the group", ignoreCase = true)
    }

    fun formatSystemEventText(
        msg: DmMessage,
        senderDisplayName: String?
    ): String {
        val trimmed = msg.content.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase(Locale.ROOT)
        val isBareAction = lower == "created the group" ||
            lower == "joined the group" ||
            lower == "left the group"
        val cleanSender = senderDisplayName?.trim()?.takeIf { it.isNotBlank() }
        return if (isBareAction && cleanSender != null && !trimmed.startsWith(cleanSender, ignoreCase = true)) {
            "$cleanSender $trimmed"
        } else {
            trimmed
        }
    }

    fun toggleMessageReaction(
        reactionsByMessageId: Map<String, Map<String, Int>>,
        myReactionsByMessageId: Map<String, String>,
        messageId: String,
        emoji: String
    ): Pair<Map<String, Map<String, Int>>, Map<String, String>> {
        val cleanEmoji = emoji.trim()
        if (messageId.isBlank() || cleanEmoji.isEmpty()) {
            return reactionsByMessageId to myReactionsByMessageId
        }
        val currentMy = myReactionsByMessageId[messageId]
        val nextCounts = (reactionsByMessageId[messageId] ?: emptyMap()).toMutableMap()
        val nextMyMap = myReactionsByMessageId.toMutableMap()

        if (currentMy == cleanEmoji) {
            val updated = (nextCounts[cleanEmoji] ?: 1) - 1
            if (updated <= 0) nextCounts.remove(cleanEmoji) else nextCounts[cleanEmoji] = updated
            nextMyMap.remove(messageId)
        } else {
            if (!currentMy.isNullOrBlank()) {
                val prevUpdated = (nextCounts[currentMy] ?: 1) - 1
                if (prevUpdated <= 0) nextCounts.remove(currentMy) else nextCounts[currentMy] = prevUpdated
            }
            nextCounts[cleanEmoji] = (nextCounts[cleanEmoji] ?: 0) + 1
            nextMyMap[messageId] = cleanEmoji
        }

        val nextReactions = reactionsByMessageId.toMutableMap()
        if (nextCounts.isEmpty()) {
            nextReactions.remove(messageId)
        } else {
            nextReactions[messageId] = nextCounts.toMap()
        }
        return nextReactions.toMap() to nextMyMap.toMap()
    }

    fun shouldSkipRealtimeMessageInsert(
        messageId: String?,
        clientTempId: String?,
        senderId: String?,
        currentUserId: String?,
        currentMessages: List<DmMessage>,
        knownClientTempIds: Set<String>
    ): Boolean {
        if (!clientTempId.isNullOrBlank() && clientTempId in knownClientTempIds) return true
        if (!messageId.isNullOrBlank() && currentMessages.any { it.id == messageId }) return true
        if (!currentUserId.isNullOrBlank() && senderId == currentUserId && !clientTempId.isNullOrBlank()) {
            if (currentMessages.any { it.clientTempId == clientTempId }) return true
        }
        return false
    }

    fun shouldSkipRealtimeMessageUpdate(
        messageId: String?,
        content: String?,
        editedAt: String?,
        deletedAt: String?,
        currentMessages: List<DmMessage>
    ): Boolean {
        if (messageId.isNullOrBlank()) return false
        val local = currentMessages.firstOrNull { it.id == messageId } ?: return false
        val isRemoteDeleted = !deletedAt.isNullOrBlank()
        if (isRemoteDeleted && local.isDeleted) return true
        val normalizedContent = content.orEmpty()
        return !isRemoteDeleted &&
            !local.isDeleted &&
            local.content == normalizedContent &&
            local.editedAt == editedAt
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
        locale: Locale = Locale.getDefault(),
        newlyInsertedIds: Set<String> = emptySet(),
        reactionsByMessageId: Map<String, Map<String, Int>> = emptyMap(),
        myReactionsByMessageId: Map<String, String> = emptyMap()
    ): List<ChatListItem> {
        if (messages.isEmpty()) {
            return if (isDirect && peerIntro != null) {
                listOf(ChatListItem.PeerIntroItem(stableKey = "peer_intro_${peerIntro.id}", intro = peerIntro))
            } else {
                emptyList()
            }
        }

        val participantLookup = participants.associateBy { it.userId }
        val otherParticipants = participants.filter { it.userId != currentUserId }
        val maxPeerReadAtMillis = otherParticipants
            .mapNotNull { it.lastReadAt?.let(::parseInstantMillis) }
            .maxOrNull()
        val messageIndexById = HashMap<String, Int>(messages.size)
        for (idx in messages.indices) {
            messageIndexById[messages[idx].id] = idx
        }
        val otherReadIndexByUserId = otherParticipants.associate { p ->
            p.userId to p.lastReadMessageId?.let { messageIndexById[it] }
        }
        val otherReadMillisByUserId = otherParticipants.associate { p ->
            p.userId to p.lastReadAt?.let(::parseInstantMillis)
        }

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

            val participant = participantLookup[msg.senderId]
            val resolvedSenderName = msg.senderDisplayName?.trim()?.takeIf { it.isNotBlank() }
                ?: participant?.displayName?.trim()?.takeIf { it.isNotBlank() }
            val isSystem = isSystemEventMessage(msg)
            if (isSystem) {
                val systemText = formatSystemEventText(msg, resolvedSenderName)
                chronologicalItems.add(
                    ChatListItem.SystemEventItem(
                        stableKey = "system_${msg.clientTempId ?: msg.id}",
                        message = msg,
                        text = systemText,
                        createdAtIso = msg.createdAt,
                        isolatedText = ChatBidiFormatter.isolateFirstStrong(systemText)
                    )
                )
                continue
            }

            val isDeleted = msg.isDeleted
            val isOwn = msg.senderId == currentUserId

            val groupedWithPrev = prev != null &&
                !prev.isDeleted &&
                !isSystemEventMessage(prev) &&
                !isDeleted &&
                prev.senderId == msg.senderId &&
                msgDay == prevDay &&
                msgMillis != null && prevMillis != null &&
                (msgMillis - prevMillis) in 0L..ChatDimens.GroupMergeThresholdMillis

            val groupedWithNext = next != null &&
                !next.isDeleted &&
                !isSystemEventMessage(next) &&
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
            val isFirstInCluster = position == ChatBubblePosition.SINGLE || position == ChatBubblePosition.FIRST
            val isLastInCluster = position == ChatBubblePosition.SINGLE || position == ChatBubblePosition.LAST
            val topCornerDp = if (isFirstInCluster) ChatDimens.BubbleCornerLarge.value else ChatDimens.BubbleCornerGrouped.value
            val bottomCornerDp = if (isLastInCluster) ChatDimens.BubbleCornerLarge.value else ChatDimens.BubbleCornerGrouped.value

            val deliveryStatus = computeDeliveryStatus(
                message = msg,
                isOwn = isOwn,
                msgMillis = msgMillis,
                otherParticipants = otherParticipants,
                maxPeerReadAtMillis = maxPeerReadAtMillis,
                messageIndex = i,
                messageIndexById = messageIndexById
            )

            val seenByParticipants = if (isOwn && msg.localSendStatus == DmSendStatus.SENT) {
                otherParticipants.filter { peer ->
                    peer.userId != msg.senderId && (
                        peer.lastReadMessageId == msg.id ||
                            ((otherReadIndexByUserId[peer.userId] ?: -1) >= i) ||
                            (msgMillis != null && (otherReadMillisByUserId[peer.userId] ?: Long.MIN_VALUE) >= msgMillis)
                        )
                }
            } else {
                emptyList()
            }
            val seenByNames = seenByParticipants.mapNotNull { peer ->
                peer.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: peer.username?.trim()?.takeIf { it.isNotBlank() }
                    ?: peer.handle?.trim()?.takeIf { it.isNotBlank() }
            }.distinct()
            val isSeenByPeer = isOwn && (deliveryStatus == ChatDeliveryStatus.READ || seenByParticipants.isNotEmpty())

            val resolvedSenderHandle = msg.senderHandle?.trim()?.takeIf { it.isNotBlank() }
                ?: msg.senderUsername?.trim()?.takeIf { it.isNotBlank() }
                ?: participant?.handle?.trim()?.takeIf { it.isNotBlank() }
                ?: participant?.username?.trim()?.takeIf { it.isNotBlank() }
            val senderIsVerified = msg.senderIsVerified || (participant?.isVerified == true)
            val formattedTime = formatShortTime(msg.createdAt, zoneId, locale)
            val isNewlyInserted = msg.id in newlyInsertedIds ||
                (msg.clientTempId != null && msg.clientTempId in newlyInsertedIds)
            val reactions = reactionsByMessageId[msg.id]
                ?: msg.clientTempId?.let { reactionsByMessageId[it] }
                ?: emptyMap()
            val myReaction = myReactionsByMessageId[msg.id]
                ?: msg.clientTempId?.let { myReactionsByMessageId[it] }

            chronologicalItems.add(
                ChatListItem.MessageItem(
                    stableKey = msg.clientTempId ?: msg.id,
                    message = msg,
                    isOwn = isOwn,
                    isGroupChat = !isDirect,
                    isDeleted = isDeleted,
                    isEdited = msg.isEdited,
                    isRtlContent = isRtlText(msg.content, defaultRtl = defaultRtl),
                    position = position,
                    isGroupedWithPrevious = groupedWithPrev,
                    showSenderName = !isDirect && !isOwn && !isDeleted && !groupedWithPrev && !resolvedSenderName.isNullOrBlank(),
                    showSenderAvatar = !isDirect && !isOwn && !groupedWithNext,
                    formattedTime = formattedTime,
                    deliveryStatus = deliveryStatus,
                    replyPreview = msg.replyTo,
                    seenParticipants = emptyList(),
                    seenByNames = seenByNames,
                    isSeenByPeer = isSeenByPeer,
                    reactions = reactions,
                    myReaction = myReaction,
                    resolvedSenderName = resolvedSenderName,
                    resolvedSenderHandle = resolvedSenderHandle,
                    senderIsVerified = senderIsVerified,
                    isNewlyInserted = isNewlyInserted,
                    isFirstInCluster = isFirstInCluster,
                    isLastInCluster = isLastInCluster,
                    topCornerDp = topCornerDp,
                    bottomCornerDp = bottomCornerDp,
                    isolatedSenderName = resolvedSenderName?.let(ChatBidiFormatter::isolateFirstStrong),
                    isolatedFormattedTime = ChatBidiFormatter.isolateLtr(formattedTime),
                    isolatedReplySenderName = msg.replyTo?.senderDisplayName
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(ChatBidiFormatter::isolateFirstStrong),
                    isolatedReplyContent = msg.replyTo?.content
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(ChatBidiFormatter::isolateFirstStrong)
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
        maxPeerReadAtMillis: Long?,
        messageIndex: Int = -1,
        messageIndexById: Map<String, Int> = emptyMap()
    ): ChatDeliveryStatus {
        if (!isOwn) return ChatDeliveryStatus.SENT
        return when (message.localSendStatus) {
            DmSendStatus.SENDING -> ChatDeliveryStatus.SENDING
            DmSendStatus.FAILED -> ChatDeliveryStatus.FAILED
            DmSendStatus.SENT -> {
                val isReadByPointer = otherParticipants.any { peer ->
                    peer.lastReadMessageId == message.id ||
                        (messageIndex >= 0 && peer.lastReadMessageId != null &&
                            (messageIndexById[peer.lastReadMessageId] ?: -1) >= messageIndex)
                }
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
        membersFormat: (Int) -> String,
        fallbackInboxItem: app.buildx.chat.data.model.DmInboxItem? = null
    ): ChatHeaderUiState {
        val conv = detail?.conversation
        val isDirect = when {
            conv != null -> conv.kind != "group"
            fallbackInboxItem != null -> fallbackInboxItem.kind != "group"
            else -> true
        }
        val peerIntro = detail?.peerIntro
        val myParticipant = detail?.participants?.firstOrNull { it.userId == currentUserId }
        val isTyping = typingUserIds.any { it != currentUserId }

        val resolvedPeerHandle = peerIntro?.handle?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
            ?: peerIntro?.username?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
            ?: fallbackInboxItem?.handle?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
            ?: fallbackInboxItem?.peer?.handle?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
            ?: fallbackInboxItem?.peer?.username?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }

        val title = if (isDirect) {
            peerIntro?.displayName?.takeIf { it.isNotBlank() }
                ?: fallbackInboxItem?.effectiveTitle?.takeIf { it.isNotBlank() }
                ?: resolvedPeerHandle
                ?: ""
        } else {
            conv?.title?.takeIf { it.isNotBlank() }
                ?: fallbackInboxItem?.effectiveTitle?.takeIf { it.isNotBlank() }
                ?: ""
        }

        val subtitle = if (isDirect) {
            resolvedPeerHandle?.let { "@$it" } ?: ""
        } else {
            val activeCount = detail?.participants
                ?.count { it.status == "active" }
                ?.takeIf { it > 0 }
                ?: fallbackInboxItem?.memberCount?.takeIf { it > 0 }
                ?: 0
            if (activeCount > 0) membersFormat(activeCount) else ""
        }

        val resolvedAvatarUrl = if (isDirect) {
            peerIntro?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: fallbackInboxItem?.peer?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: fallbackInboxItem?.avatarUrl?.takeIf { it.isNotBlank() }
        } else {
            conv?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: fallbackInboxItem?.avatarUrl?.takeIf { it.isNotBlank() }
        }

        val resolvedVerified = isDirect && (peerIntro?.isVerified == true || fallbackInboxItem?.effectiveIsVerified == true)
        val resolvedMuted = myParticipant?.isMuted ?: (fallbackInboxItem?.isMuted == true)
        val resolvedPinned = myParticipant?.isPinned ?: (fallbackInboxItem?.isPinned == true)

        return ChatHeaderUiState(
            title = title,
            subtitle = subtitle,
            avatarUrl = resolvedAvatarUrl,
            isDirect = isDirect,
            isVerified = resolvedVerified,
            isMuted = resolvedMuted,
            isPinned = resolvedPinned,
            isTyping = isTyping,
            isOnline = false,
            peerHandle = resolvedPeerHandle,
            isolatedTitle = ChatBidiFormatter.isolateFirstStrong(title),
            isolatedSubtitle = if (isDirect && subtitle.startsWith("@")) {
                ChatBidiFormatter.isolateLtr(subtitle)
            } else {
                ChatBidiFormatter.isolateFirstStrong(subtitle)
            }
        )
    }

    /**
     * Computes badge/tab counters from the canonical unfiltered inbox list so tab
     * switching never alters badge totals.
     */
    fun computeInboxCounters(items: List<DmInboxItem>): ChatInboxCounters {
        var totalUnread = 0
        var unreadConvs = 0
        var groups = 0
        for (item in items) {
            if (item.unreadCount > 0) {
                totalUnread += item.unreadCount
            } else if (item.isMarkedUnread) {
                totalUnread += 1
            }
            if (item.effectiveIsUnread) unreadConvs++
            if (item.kind == "group") groups++
        }
        return ChatInboxCounters(
            totalUnreadMessages = totalUnread,
            unreadConversationsCount = unreadConvs,
            groupsCount = groups
        )
    }

    /**
     * Applies a local message edit across both the message entry and any reply
     * previews in [messages] pointing to [messageId].
     */
    fun applyMessageEditToHistory(
        messages: List<DmMessage>,
        messageId: String,
        newContent: String,
        editedAtIso: String
    ): List<DmMessage> = messages.map { msg ->
        when {
            msg.id == messageId -> msg.copy(content = newContent, editedAt = editedAtIso)
            msg.replyTo?.id == messageId -> msg.copy(replyTo = msg.replyTo.copy(content = newContent))
            else -> msg
        }
    }

    /**
     * Applies a local soft-delete across both the message entry and any reply
     * previews in [messages] pointing to [messageId].
     */
    fun applyMessageDeleteToHistory(
        messages: List<DmMessage>,
        messageId: String,
        deletedAtIso: String
    ): List<DmMessage> = messages.map { msg ->
        when {
            msg.id == messageId -> msg.copy(
                deletedAt = deletedAtIso,
                content = "",
                messageType = "deleted"
            )
            msg.replyTo?.id == messageId -> msg.copy(
                replyTo = msg.replyTo.copy(isDeleted = true, content = "")
            )
            else -> msg
        }
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

    fun mergeParticipantReadState(
        currentParticipants: List<DmParticipant>,
        readerUserId: String,
        readAtIso: String,
        lastReadMessageId: String?
    ): List<DmParticipant> {
        val incomingMs = parseInstantMillis(readAtIso)
        return currentParticipants.map { participant ->
            if (participant.userId != readerUserId) {
                participant
            } else {
                val currentMs = participant.lastReadAt?.let(::parseInstantMillis) ?: Long.MIN_VALUE
                val resolvedAt = if (incomingMs == null || incomingMs >= currentMs) readAtIso else participant.lastReadAt
                val resolvedMsgId = when {
                    lastReadMessageId.isNullOrBlank() -> participant.lastReadMessageId
                    incomingMs == null || incomingMs >= currentMs -> lastReadMessageId
                    else -> participant.lastReadMessageId
                }
                participant.copy(
                    lastReadAt = resolvedAt,
                    lastReadMessageId = resolvedMsgId
                )
            }
        }
    }

    fun mergeMonotonicParticipants(
        existing: List<DmParticipant>,
        incoming: List<DmParticipant>
    ): List<DmParticipant> {
        if (existing.isEmpty()) return incoming
        val existingById = existing.associateBy { it.userId }
        return incoming.map { next ->
            val prev = existingById[next.userId] ?: return@map next
            val prevMs = prev.lastReadAt?.let(::parseInstantMillis) ?: Long.MIN_VALUE
            val nextMs = next.lastReadAt?.let(::parseInstantMillis) ?: Long.MIN_VALUE
            when {
                prevMs > nextMs -> next.copy(
                    lastReadAt = prev.lastReadAt,
                    lastReadMessageId = prev.lastReadMessageId ?: next.lastReadMessageId
                )
                prevMs == nextMs && next.lastReadMessageId.isNullOrBlank() && !prev.lastReadMessageId.isNullOrBlank() ->
                    next.copy(lastReadMessageId = prev.lastReadMessageId)
                else -> next
            }
        }
    }

    fun formatShortTime(
        iso: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = runCatching {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", locale)
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
