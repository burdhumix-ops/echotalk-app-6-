package co.znxdnd.echotalk.data.remote

import co.znxdnd.echotalk.data.model.ArcadeGameState
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.ChatRoom
import co.znxdnd.echotalk.data.model.GameChallengeData
import co.znxdnd.echotalk.data.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class ProfileDto(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String = "EchoTalker",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    val photos: List<String> = emptyList(),
    val bio: String = "",
    val country: String = "Global",
    @SerialName("country_code") val countryCode: String = "GL",
    val gender: String = "Non-Binary",
    val age: Int = 24,
    @SerialName("visibility_public") val visibilityPublic: Boolean = true,
    @SerialName("show_online_status") val showOnlineStatus: Boolean = true,
    @SerialName("ghost_mode") val ghostMode: Boolean = false,
    @SerialName("dm_permissions") val dmPermissions: String = "everyone",
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("verification_status") val verificationStatus: String = "none",
    val role: String = "member",
    @SerialName("audio_preview_title") val audioPreviewTitle: String = "Ambient Chillout (5s)",
    @SerialName("profile_completed") val profileCompleted: Boolean = false,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ProfileUpsertDto(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("banner_url") val bannerUrl: String,
    val photos: List<String>,
    val bio: String,
    val country: String,
    @SerialName("country_code") val countryCode: String,
    val gender: String,
    val age: Int,
    @SerialName("visibility_public") val visibilityPublic: Boolean = true,
    @SerialName("show_online_status") val showOnlineStatus: Boolean = true,
    @SerialName("ghost_mode") val ghostMode: Boolean = false,
    @SerialName("dm_permissions") val dmPermissions: String = "everyone",
    @SerialName("is_online") val isOnline: Boolean = true,
    @SerialName("audio_preview_title") val audioPreviewTitle: String = "Ambient Chillout (5s)",
    @SerialName("profile_completed") val profileCompleted: Boolean = true,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = true
)

@Serializable
data class ProfilePresencePatchDto(
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("last_seen_at") val lastSeenAt: String
)

data class ProfilePresenceSnapshot(
    val userId: String,
    val isOnline: Boolean,
    val showOnlineStatus: Boolean,
    val ghostMode: Boolean,
    val lastSeenAtMillis: Long?
) {
    val effectiveOnline: Boolean
        get() = isOnline && showOnlineStatus && !ghostMode
}

@Serializable
data class UserBlockDto(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ProfileLikeDto(
    @SerialName("liker_id") val likerId: String,
    @SerialName("liked_id") val likedId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FriendshipDto(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("requester_id") val requesterId: String,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class RoomDto(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val description: String = "",
    @SerialName("room_type") val roomType: String = "text",
    val badge: String = "Global",
    @SerialName("is_special") val isSpecial: Boolean = false,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("is_open") val isOpen: Boolean = true,
    @SerialName("retention_hours") val retentionHours: Int = 24,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class RoomModerationPatchDto(
    @SerialName("is_locked") val isLocked: Boolean? = null,
    @SerialName("is_open") val isOpen: Boolean? = null
)

@Serializable
data class RoomKickDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("kicked_by") val kickedBy: String? = null,
    @SerialName("kicked_until") val kickedUntil: String? = null
)

@Serializable
data class RoomMessageDto(
    val id: String? = null,
    @SerialName("client_msg_id") val clientMsgId: String? = null,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    val content: String,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("audio_duration") val audioDuration: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_to_sender") val replyToSender: String? = null,
    @SerialName("reply_to_text") val replyToText: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    @SerialName("user_reactions") val userReactions: Map<String, List<String>> = emptyMap(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class RoomMessageReactionsPatchDto(
    val reactions: Map<String, Int>,
    @SerialName("user_reactions") val userReactions: Map<String, List<String>>
)

@Serializable
data class DirectConversationDto(
    val id: String,
    @SerialName("canonical_key") val canonicalKey: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null
)

@Serializable
data class ConversationParticipantDto(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false
)

@Serializable
data class ChallengePayloadDto(
    @SerialName("game_id") val gameId: String = "",
    @SerialName("game_title") val gameTitle: String = "",
    val challenger: String = "",
    val opponent: String = "",
    val status: String = "pending"
)

@Serializable
data class DirectMessageDto(
    val id: String? = null,
    @SerialName("client_msg_id") val clientMsgId: String? = null,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    val content: String,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("audio_duration") val audioDuration: String? = null,
    @SerialName("audio_waveform") val audioWaveform: List<Float> = emptyList(),
    @SerialName("challenge_data") val challengeData: ChallengePayloadDto? = null,
    @SerialName("sticker_pack_id") val stickerPackId: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_to_sender") val replyToSender: String? = null,
    @SerialName("reply_to_text") val replyToText: String? = null,
    @SerialName("is_view_once") val isViewOnce: Boolean = false,
    val viewed: Boolean = false,
    @SerialName("is_forwarded") val isForwarded: Boolean = false,
    @SerialName("forwarded_from") val forwardedFrom: String? = null,
    @SerialName("delivery_status") val deliveryStatus: String = "sent",
    val reactions: Map<String, Int> = emptyMap(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("unsent_at") val unsentAt: String? = null
)

@Serializable
data class UserPreferencesDto(
    @SerialName("user_id") val userId: String,
    @SerialName("theme_id") val themeId: String = "ios_glass",
    @SerialName("chat_wallpaper") val chatWallpaper: String = "default",
    @SerialName("chat_text_size") val chatTextSize: String = "medium",
    @SerialName("notify_direct_messages") val notifyDirectMessages: Boolean = true,
    @SerialName("notify_message_requests") val notifyMessageRequests: Boolean = true,
    @SerialName("notify_connection_requests") val notifyConnectionRequests: Boolean = true,
    @SerialName("notify_room_invitations") val notifyRoomInvitations: Boolean = true,
    @SerialName("notify_room_mentions") val notifyRoomMentions: Boolean = true,
    @SerialName("notify_incoming_calls") val notifyIncomingCalls: Boolean = true,
    @SerialName("notify_missed_calls") val notifyMissedCalls: Boolean = true,
    @SerialName("notify_system") val notifySystem: Boolean = true,
    @SerialName("notification_sound") val notificationSound: Boolean = true,
    @SerialName("notification_vibration") val notificationVibration: Boolean = true,
    @SerialName("read_receipts") val readReceipts: Boolean = true,
    @SerialName("typing_indicators") val typingIndicators: Boolean = true,
    @SerialName("personalization_enabled") val personalizationEnabled: Boolean = true,
    @SerialName("allow_incoming_calls") val allowIncomingCalls: Boolean = true,
    @SerialName("extra_json") val extraJson: JsonObject = JsonObject(emptyMap()),
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class DeviceTokenUpsertDto(
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ArcadeSessionDto(
    val id: String,
    @SerialName("game_type") val gameType: String = "chess",
    @SerialName("challenger_id") val challengerId: String? = null,
    @SerialName("opponent_id") val opponentId: String? = null,
    @SerialName("challenger_name") val challengerName: String = "",
    @SerialName("opponent_name") val opponentName: String = "",
    @SerialName("current_turn") val currentTurn: String = "White",
    @SerialName("chess_board") val chessBoard: List<String> = emptyList(),
    @SerialName("captured_white") val capturedWhite: List<String> = emptyList(),
    @SerialName("captured_black") val capturedBlack: List<String> = emptyList(),
    @SerialName("last_move_description") val lastMoveDescription: String = "",
    val winner: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class GetOrCreateConversationParams(
    @SerialName("partner_id") val partnerId: String
)

@Serializable
data class PruneExpiredRoomMessagesParams(
    @SerialName("max_batch") val maxBatch: Int = 200
)

@Serializable
data class CleanupRoomMessagesParams(
    @SerialName("target_room_id") val targetRoomId: String
)

@Serializable
data class PushNotificationRequest(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("client_msg_id") val clientMsgId: String? = null,
    @SerialName("call_id") val callId: String? = null,
    @SerialName("call_type") val callType: String? = null,
    @SerialName("preview_text") val previewText: String,
    @SerialName("notification_type") val notificationType: String = "dm"
)

@Serializable
data class PushNotificationResponse(
    val sent: Int = 0,
    val skipped: Boolean = false,
    val reason: String? = null,
    @SerialName("cleaned_stale_tokens") val cleanedStaleTokens: Int = 0,
    val error: String? = null
)

@Serializable
data class CallSessionDto(
    val id: String,
    @SerialName("room_name") val roomName: String,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("call_type") val callType: String,
    val status: String,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CallSessionPatchDto(
    val status: String,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null
)

@Serializable
data class LiveKitTokenRequest(
    @SerialName("call_id") val callId: String
)

@Serializable
data class LiveKitTokenResponse(
    val token: String? = null,
    @SerialName("ws_url") val wsUrl: String? = null,
    @SerialName("room_name") val roomName: String? = null,
    @SerialName("call_id") val callId: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 300,
    val plan: String? = null,
    val error: String? = null,
    val message: String? = null
)

object SupabaseMappers {

    private val displayTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun parseIsoMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            try {
                val clean = iso.replace(" ", "T").let {
                    if (!it.endsWith("Z") && !it.contains("+")) "${it}Z" else it
                }
                Instant.parse(clean).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun formatIsoTimestamp(millis: Long = System.currentTimeMillis()): String {
        return Instant.ofEpochMilli(millis).toString()
    }

    fun formatDisplayTime(iso: String?): String {
        val millis = parseIsoMillis(iso) ?: return displayTimeFormat.format(Date())
        return synchronized(displayTimeFormat) {
            displayTimeFormat.format(Date(millis))
        }
    }

    fun isExpired(expiresAtIso: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = parseIsoMillis(expiresAtIso) ?: return false
        return exp <= nowMillis
    }

    fun computeRoomRetentionHours(isSpecial: Boolean): Int = if (isSpecial) 48 else 24

    fun ProfileDto.toDomain(
        isFriend: Boolean = false,
        friendStatus: String = if (isFriend) "accepted" else "none",
        isLiked: Boolean = false
    ): User {
        val cleanHandle = username?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("@")) it else "@$it"
        } ?: "@${displayName.lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "").take(12).ifBlank { id.take(8) }}"
        val visibleOnline = isOnline && showOnlineStatus && !ghostMode
        val visibleLastSeen = if (showOnlineStatus && !ghostMode) parseIsoMillis(lastSeenAt) else null
        return User(
            id = id,
            name = displayName.ifBlank { "EchoTalker" },
            handle = cleanHandle,
            country = country.ifBlank { "Global" },
            countryCode = countryCode.ifBlank { "GL" },
            gender = gender.ifBlank { "Non-Binary" },
            age = age.coerceIn(18, 120),
            online = visibleOnline,
            isFriend = isFriend,
            reqCount = 0,
            bio = bio,
            avatarUrl = avatarUrl.ifBlank { photos.firstOrNull().orEmpty() },
            role = role,
            photos = photos.ifEmpty { if (avatarUrl.isNotBlank()) listOf(avatarUrl) else emptyList() },
            likesCount = likesCount.coerceAtLeast(0),
            isLiked = isLiked,
            friendStatus = friendStatus,
            bannerUrl = bannerUrl,
            audioPreviewTitle = audioPreviewTitle,
            ipVerified = true,
            isVerified = isVerified,
            verificationStatus = verificationStatus,
            onboardingCompleted = onboardingCompleted,
            profileCompleted = profileCompleted,
            lastSeenAtMillis = visibleLastSeen
        )
    }

    fun RoomDto.toDomain(messages: List<ChatMessage> = emptyList()): ChatRoom {
        val seconds = computeRoomRetentionHours(isSpecial) * 3600
        return ChatRoom(
            id = id,
            name = name,
            subtitle = subtitle,
            description = description,
            type = roomType,
            badge = badge,
            isSpecial = isSpecial,
            locked = isLocked,
            isOpen = isOpen,
            memberCount = 0,
            messages = messages,
            cleanupTimerSeconds = if (isSpecial) seconds else null,
            cleanupTimerExpiresAt = if (isSpecial) System.currentTimeMillis() + seconds * 1000L else null
        )
    }

    fun RoomMessageDto.toDomain(): ChatMessage {
        return ChatMessage(
            id = id ?: clientMsgId ?: "rm-${System.currentTimeMillis()}",
            sender = senderName,
            text = content,
            timestamp = formatDisplayTime(createdAt),
            status = "delivered",
            mediaUrl = mediaUrl,
            isAudio = messageType == "audio",
            audioDuration = audioDuration,
            reactions = reactions,
            isSticker = messageType == "sticker",
            isGif = messageType == "gif",
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToText = replyToText,
            userReactions = userReactions
        )
    }

    fun DirectMessageDto.toDomain(): ChatMessage {
        val mappedStatus = when (deliveryStatus.lowercase(Locale.US)) {
            "read" -> "seen"
            "delivered" -> "delivered"
            else -> "sent"
        }
        return ChatMessage(
            id = id ?: clientMsgId ?: "dm-${System.currentTimeMillis()}",
            sender = senderName,
            text = content,
            timestamp = formatDisplayTime(createdAt),
            status = mappedStatus,
            isViewOnce = isViewOnce,
            viewed = viewed,
            isDoodle = messageType == "doodle",
            mediaUrl = mediaUrl,
            isAudio = messageType == "audio",
            audioDuration = audioDuration,
            audioWaveform = audioWaveform,
            isChallenge = messageType == "challenge" || challengeData != null,
            challengeData = challengeData?.let {
                GameChallengeData(
                    gameId = it.gameId,
                    gameTitle = it.gameTitle,
                    challenger = it.challenger,
                    opponent = it.opponent,
                    status = it.status
                )
            },
            reactions = reactions,
            isSticker = messageType == "sticker",
            isGif = messageType == "gif",
            stickerPackId = stickerPackId,
            isForwarded = isForwarded,
            forwardedFrom = forwardedFrom,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToText = replyToText
        )
    }

    fun ArcadeSessionDto.toDomain(): ArcadeGameState {
        return ArcadeGameState(
            gameId = id,
            gameType = gameType,
            challenger = challengerName,
            opponent = opponentName,
            currentTurn = currentTurn,
            chessBoard = chessBoard,
            capturedWhite = capturedWhite,
            capturedBlack = capturedBlack,
            lastMoveDescription = lastMoveDescription,
            winner = winner,
            updatedAt = parseIsoMillis(updatedAt) ?: System.currentTimeMillis()
        )
    }
}
