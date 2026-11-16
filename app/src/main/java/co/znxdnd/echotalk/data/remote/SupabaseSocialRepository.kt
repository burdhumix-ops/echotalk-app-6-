package co.znxdnd.echotalk.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import co.znxdnd.echotalk.data.local.EchoSettings
import co.znxdnd.echotalk.data.model.ArcadeGameState
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.ChatRoom
import co.znxdnd.echotalk.data.model.GameChallengeData
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.data.remote.SupabaseMappers.toDomain
import co.znxdnd.echotalk.utils.ResilienceGuardian
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Supabase Free-Tier Backend Repository.
 * Handles PostgreSQL queries, Zero-Trust RLS-compliant writes, Realtime Postgres Changes + Presence,
 * Storage uploads (compressed <=300 KB), FCM device token registration, and push Edge Function invocation.
 */
class SupabaseSocialRepository private constructor() {

    companion object {
        private const val TAG = "SupabaseSocialRepo"

        @Volatile
        private var instance: SupabaseSocialRepository? = null

        fun getInstance(): SupabaseSocialRepository =
            instance ?: synchronized(this) {
                instance ?: SupabaseSocialRepository().also { instance = it }
            }
    }

    private val ioScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + ResilienceGuardian.safeScopeExceptionHandler(TAG)
    )

    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineUserIds: StateFlow<Set<String>> = _onlineUserIds.asStateFlow()

    private val _profilePresenceSnapshots = MutableStateFlow<Map<String, ProfilePresenceSnapshot>>(emptyMap())
    val profilePresenceSnapshots: StateFlow<Map<String, ProfilePresenceSnapshot>> = _profilePresenceSnapshots.asStateFlow()

    // Tracks active Realtime Presence refs per userId so multi-device joins/leaves stay exact
    private val userPresenceRefs = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile
    private var lastPresenceHeartbeatMs: Long = 0L

    @Volatile
    private var lastRoomCleanupMs: Long = 0L

    private var presenceChannel: RealtimeChannel? = null
    private var presenceJob: Job? = null
    private var profilesRealtimeChannel: RealtimeChannel? = null
    private var profilesRealtimeJob: Job? = null
    private var roomRealtimeChannel: RealtimeChannel? = null
    private var roomRealtimeJob: Job? = null
    private var dmRealtimeChannel: RealtimeChannel? = null
    private var dmRealtimeJob: Job? = null

    fun currentUid(): String? {
        val client = SupabaseClientProvider.getClientOrNull() ?: return null
        return try {
            client.auth.currentUserOrNull()?.id
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 1. PROFILES & CREW DISCOVERY
    // =========================================================================

    suspend fun fetchCurrentProfile(): Result<User?> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        ResilienceGuardian.executeSupabaseCall("fetchCurrentProfile") {
            val profile = client.postgrest.from("profiles")
                .select {
                    filter { eq("id", uid) }
                    limit(1)
                }
                .decodeSingleOrNull<ProfileDto>()
            profile?.toDomain(isFriend = true, friendStatus = "accepted")
        }
    }

    suspend fun isUsernameAvailable(username: String, currentUserId: String?): Boolean = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return@withContext true
        try {
            val clean = username.trim().lowercase()
            val matches = client.postgrest.from("profiles")
                .select {
                    filter { eq("username", clean) }
                    limit(1)
                }
                .decodeList<ProfileDto>()
            matches.isEmpty() || matches.all { it.id == currentUserId }
        } catch (e: Exception) {
            Log.w(TAG, "Username availability check error: ${e.message}")
            true
        }
    }

    suspend fun upsertProfile(user: User, settings: EchoSettings? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val cleanUsername = user.handle.removePrefix("@").trim().lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .take(20)
            .takeIf { it.length >= 3 }

        val dto = ProfileUpsertDto(
            id = uid,
            username = cleanUsername,
            displayName = user.name.ifBlank { "EchoTalker" },
            avatarUrl = user.avatarUrl,
            bannerUrl = user.bannerUrl,
            photos = user.photos.ifEmpty { if (user.avatarUrl.isNotBlank()) listOf(user.avatarUrl) else emptyList() },
            bio = user.bio,
            country = user.country.ifBlank { "Global" },
            countryCode = user.countryCode.ifBlank { "GL" },
            gender = user.gender.ifBlank { "Non-Binary" },
            age = user.age.coerceIn(18, 120),
            visibilityPublic = settings?.profileVisibility ?: true,
            showOnlineStatus = settings?.onlineStatus ?: true,
            ghostMode = settings?.ghostMode ?: false,
            dmPermissions = settings?.dmPermissions ?: "everyone",
            isOnline = user.online && !(settings?.ghostMode ?: false),
            audioPreviewTitle = user.audioPreviewTitle,
            profileCompleted = user.profileCompleted,
            onboardingCompleted = user.onboardingCompleted
        )
        ResilienceGuardian.executeSupabaseCall("upsertProfile") {
            client.postgrest.from("profiles").upsert(dto)
            Unit
        }
    }

    suspend fun fetchDiscoverCrew(): Result<List<User>> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        ResilienceGuardian.executeSupabaseCall("fetchDiscoverCrew") {
            val profiles = client.postgrest.from("profiles")
                .select {
                    order("is_online", Order.DESCENDING)
                    order("last_seen_at", Order.DESCENDING)
                    limit(120)
                }
                .decodeList<ProfileDto>()

            val friendships = client.postgrest.from("friendships")
                .select {
                    filter {
                        or {
                            eq("user_a", uid)
                            eq("user_b", uid)
                        }
                    }
                }
                .decodeList<FriendshipDto>()

            val likes = client.postgrest.from("profile_likes")
                .select {
                    filter { eq("liker_id", uid) }
                }
                .decodeList<ProfileLikeDto>()
            val likedIds = likes.map { it.likedId }.toSet()

            val friendMap = friendships.associateBy { f ->
                if (f.userA == uid) f.userB else f.userA
            }

            val snapshotMap = mutableMapOf<String, ProfilePresenceSnapshot>()
            val mapped = profiles.map { dto ->
                val f = friendMap[dto.id]
                val status = f?.status ?: "none"
                val isFriend = dto.id == uid || status == "accepted"
                val privacyAllowed = dto.showOnlineStatus && !dto.ghostMode
                val isRealtimeOnline = privacyAllowed && _onlineUserIds.value.contains(dto.id)
                val lastSeenMillis = if (privacyAllowed) SupabaseMappers.parseIsoMillis(dto.lastSeenAt) else null
                snapshotMap[dto.id] = ProfilePresenceSnapshot(
                    userId = dto.id,
                    isOnline = dto.isOnline || isRealtimeOnline,
                    showOnlineStatus = dto.showOnlineStatus,
                    ghostMode = dto.ghostMode,
                    lastSeenAtMillis = lastSeenMillis
                )
                dto.copy(isOnline = dto.isOnline || isRealtimeOnline).toDomain(
                    isFriend = isFriend,
                    friendStatus = status,
                    isLiked = likedIds.contains(dto.id)
                )
            }
            _profilePresenceSnapshots.value = _profilePresenceSnapshots.value + snapshotMap
            mapped
        }
    }

    suspend fun toggleProfileLike(targetUserId: String, currentlyLiked: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            if (currentlyLiked) {
                client.postgrest.from("profile_likes").delete {
                    filter {
                        eq("liker_id", uid)
                        eq("liked_id", targetUserId)
                    }
                }
                Result.success(false)
            } else {
                client.postgrest.from("profile_likes").upsert(
                    ProfileLikeDto(likerId = uid, likedId = targetUserId)
                )
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOrAcceptFriendship(partnerId: String): Result<String> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val (a, b) = if (uid < partnerId) uid to partnerId else partnerId to uid
            val fId = "${a}_$b"
            val existing = client.postgrest.from("friendships")
                .select {
                    filter { eq("id", fId) }
                    limit(1)
                }
                .decodeSingleOrNull<FriendshipDto>()

            if (existing == null) {
                client.postgrest.from("friendships").insert(
                    FriendshipDto(
                        id = fId,
                        userA = a,
                        userB = b,
                        requesterId = uid,
                        status = "pending"
                    )
                )
                dispatchPushNotification(
                    recipientId = partnerId,
                    previewText = "Sent you a connection request",
                    notificationType = "conn_req"
                )
                Result.success("pending")
            } else {
                client.postgrest.from("friendships").update(
                    buildJsonObject { put("status", "accepted") }
                ) {
                    filter { eq("id", fId) }
                }
                Result.success("accepted")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid() ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            client.postgrest.from("user_blocks").upsert(UserBlockDto(blockerId = uid, blockedId = targetId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid() ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            client.postgrest.from("user_blocks").delete {
                filter {
                    eq("blocker_id", uid)
                    eq("blocked_id", targetId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. PRESENCE & PREFERENCES
    // =========================================================================

    internal fun applyPresenceDiff(
        joins: Map<String, Presence>,
        leaves: Map<String, Presence>
    ) {
        leaves.forEach { (presenceKey, presence) ->
            val userId = extractPresenceUserId(presenceKey, presence)
            val ref = presence.presenceRef.ifBlank { presenceKey }
            userPresenceRefs[userId]?.let { refs ->
                refs.remove(ref)
                if (refs.isEmpty()) {
                    userPresenceRefs.remove(userId)
                }
            }
        }
        joins.forEach { (presenceKey, presence) ->
            val userId = extractPresenceUserId(presenceKey, presence)
            val ref = presence.presenceRef.ifBlank { presenceKey }
            val refs = userPresenceRefs.getOrPut(userId) { java.util.Collections.synchronizedSet(mutableSetOf()) }
            refs.add(ref)
        }
        _onlineUserIds.value = userPresenceRefs.keys.toSet()
    }

    internal fun clearPresenceTrackerForTesting() {
        userPresenceRefs.clear()
        _onlineUserIds.value = emptySet()
        _profilePresenceSnapshots.value = emptyMap()
    }

    private fun extractPresenceUserId(presenceKey: String, presence: Presence): String {
        val stateUserId = presence.state["user_id"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
        return stateUserId ?: presenceKey
    }

    suspend fun updatePresenceHeartbeat(online: Boolean, ghostMode: Boolean, force: Boolean = false) = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return@withContext
        val uid = currentUid() ?: return@withContext
        if (ResilienceGuardian.isSchemaCircuitOpen()) return@withContext
        val nowMs = System.currentTimeMillis()
        if (!force && (nowMs - lastPresenceHeartbeatMs) < SupabaseConfig.PRESENCE_HEARTBEAT_MIN_INTERVAL_MS) {
            return@withContext
        }
        lastPresenceHeartbeatMs = nowMs
        val visibleOnline = online && !ghostMode
        ResilienceGuardian.executeSupabaseCall("updatePresenceHeartbeat", maxTransientRetries = 0) {
            client.postgrest.from("profiles").update(
                ProfilePresencePatchDto(
                    isOnline = visibleOnline,
                    lastSeenAt = SupabaseMappers.formatIsoTimestamp(nowMs)
                )
            ) {
                filter { eq("id", uid) }
            }
            setPresenceSharingEnabled(visibleOnline)
        }
    }

    suspend fun setPresenceSharingEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext
        val ch = presenceChannel ?: return@withContext
        try {
            if (enabled) {
                ch.track(buildJsonObject {
                    put("user_id", uid)
                    put("online_at", SupabaseMappers.formatIsoTimestamp())
                })
            } else {
                ch.untrack()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Presence track/untrack warning: ${e.message}")
        }
    }

    fun ensureRealtimePresenceConnected(ghostMode: Boolean, showOnlineStatus: Boolean = true) {
        val isHealthy = presenceChannel != null &&
            presenceJob?.isActive == true &&
            profilesRealtimeChannel != null &&
            profilesRealtimeJob?.isActive == true
        if (isHealthy) {
            ioScope.launch { setPresenceSharingEnabled(showOnlineStatus && !ghostMode) }
            return
        }
        if (presenceChannel != null && presenceJob?.isActive != true) {
            presenceChannel = null
        }
        if (profilesRealtimeChannel != null && profilesRealtimeJob?.isActive != true) {
            profilesRealtimeChannel = null
        }
        startGlobalPresence(ghostMode = ghostMode, showOnlineStatus = showOnlineStatus)
    }

    fun startGlobalPresence(ghostMode: Boolean, showOnlineStatus: Boolean = true) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return
        val uid = currentUid() ?: return
        val visibleOnline = showOnlineStatus && !ghostMode

        if (presenceChannel != null && presenceJob?.isActive == true) {
            ioScope.launch { setPresenceSharingEnabled(visibleOnline) }
            if (profilesRealtimeChannel == null || profilesRealtimeJob?.isActive != true) {
                startProfilesPresenceRealtime()
            }
            return
        }

        presenceJob?.cancel()
        presenceJob = ioScope.launch {
            try {
                val ch = client.realtime.channel("global-crew-presence") {
                    presence {
                        key = uid
                    }
                }
                presenceChannel = ch
                val presenceFlow = ch.presenceChangeFlow()
                launch {
                    presenceFlow.collect { action ->
                        applyPresenceDiff(action.joins, action.leaves)
                    }
                }
                ch.subscribe(blockUntilSubscribed = true)
                if (visibleOnline) {
                    ch.track(buildJsonObject {
                        put("user_id", uid)
                        put("online_at", SupabaseMappers.formatIsoTimestamp())
                    })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Global presence setup warning: ${e.message}")
            }
        }

        startProfilesPresenceRealtime()
    }

    private fun startProfilesPresenceRealtime() {
        val client = SupabaseClientProvider.getClientOrNull() ?: return
        if (profilesRealtimeChannel != null) return

        profilesRealtimeJob?.cancel()
        profilesRealtimeJob = ioScope.launch {
            try {
                val ch = client.realtime.channel("profiles-presence-stream")
                profilesRealtimeChannel = ch
                val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "profiles"
                }
                launch {
                    flow.collect { action ->
                        val record = when (action) {
                            is PostgresAction.Insert -> action.record
                            is PostgresAction.Update -> action.record
                            else -> null
                        } ?: return@collect

                        runCatching {
                            val dto = SupabaseClientProvider.json.decodeFromString(
                                ProfileDto.serializer(),
                                record.toString()
                            )
                            val privacyAllowed = dto.showOnlineStatus && !dto.ghostMode
                            val snapshot = ProfilePresenceSnapshot(
                                userId = dto.id,
                                isOnline = dto.isOnline,
                                showOnlineStatus = dto.showOnlineStatus,
                                ghostMode = dto.ghostMode,
                                lastSeenAtMillis = if (privacyAllowed) SupabaseMappers.parseIsoMillis(dto.lastSeenAt) else null
                            )
                            _profilePresenceSnapshots.value = _profilePresenceSnapshots.value + (dto.id to snapshot)
                        }
                    }
                }
                ch.subscribe()
            } catch (e: Exception) {
                Log.w(TAG, "Profiles realtime presence warning: ${e.message}")
            }
        }
    }

    fun stopGlobalPresence() {
        presenceJob?.cancel()
        presenceJob = null
        val ch = presenceChannel
        presenceChannel = null
        profilesRealtimeJob?.cancel()
        profilesRealtimeJob = null
        val profilesCh = profilesRealtimeChannel
        profilesRealtimeChannel = null
        userPresenceRefs.clear()
        _onlineUserIds.value = emptySet()

        if (ch != null || profilesCh != null) {
            ioScope.launch {
                runCatching {
                    ch?.untrack()
                    ch?.unsubscribe()
                }
                runCatching {
                    profilesCh?.unsubscribe()
                }
            }
        }
    }

    suspend fun syncPreferences(settings: EchoSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid() ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val dto = UserPreferencesDto(
            userId = uid,
            themeId = settings.theme.id,
            chatWallpaper = settings.chatWallpaper,
            chatTextSize = settings.chatTextSize,
            notifyDirectMessages = settings.notifyDirectMessages,
            notifyMessageRequests = settings.notifyMessageRequests,
            notifyConnectionRequests = settings.notifyConnectionRequests,
            notifyRoomInvitations = settings.notifyRoomInvitations,
            notifyRoomMentions = settings.notifyRoomMentions,
            notifyIncomingCalls = settings.notifyIncomingCalls,
            notifyMissedCalls = settings.notifyMissedCalls,
            notifySystem = settings.notifySystem,
            notificationSound = settings.notificationSound,
            notificationVibration = settings.notificationVibration,
            readReceipts = settings.readReceipts,
            typingIndicators = settings.typingIndicators,
            allowIncomingCalls = settings.allowIncomingCalls
        )
        ResilienceGuardian.executeSupabaseCall("syncPreferences") {
            client.postgrest.from("user_preferences").upsert(dto)
            Unit
        }
    }

    // =========================================================================
    // 3. PUBLIC ROOMS, REALTIME & 24h / 48h LAZY EXPIRATION
    // =========================================================================

    suspend fun fetchRoomsAndActiveMessages(): Result<List<ChatRoom>> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        ResilienceGuardian.executeSupabaseCall("fetchRoomsAndActiveMessages") {
            maybePruneExpiredRoomMessages()
            val roomDtos = client.postgrest.from("rooms")
                .select { order("id", Order.ASCENDING) }
                .decodeList<RoomDto>()

            val nowIso = SupabaseMappers.formatIsoTimestamp()
            val nowMillis = System.currentTimeMillis()
            roomDtos.map { r ->
                val msgs = client.postgrest.from("room_messages")
                    .select {
                        filter {
                            eq("room_id", r.id)
                            gt("expires_at", nowIso)
                        }
                        order("created_at", Order.ASCENDING)
                        limit(SupabaseConfig.ROOM_PAGE_SIZE.toLong())
                    }
                    .decodeList<RoomMessageDto>()
                    .filterNot { SupabaseMappers.isExpired(it.expiresAt, nowMillis) }
                    .map { it.toDomain() }
                r.toDomain(messages = msgs)
            }
        }
    }

    suspend fun maybePruneExpiredRoomMessages() {
        val now = System.currentTimeMillis()
        if ((now - lastRoomCleanupMs) < SupabaseConfig.ROOM_CLEANUP_MIN_INTERVAL_MS) return
        val client = SupabaseClientProvider.getClientOrNull() ?: return
        lastRoomCleanupMs = now
        try {
            client.postgrest.rpc(
                function = "prune_expired_room_messages",
                parameters = buildJsonObject { put("max_batch", 200) }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Lazy room message prune skipped: ${e.message}")
        }
    }

    suspend fun sendRoomMessage(
        roomId: String,
        clientMsgId: String,
        senderName: String,
        text: String,
        isAudio: Boolean = false,
        duration: String? = null,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToText: String? = null
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Sign in required to post in rooms"))
        try {
            val dto = RoomMessageDto(
                clientMsgId = clientMsgId,
                roomId = roomId,
                senderId = uid,
                senderName = senderName,
                content = text,
                messageType = if (isAudio) "audio" else "text",
                audioDuration = duration,
                replyToId = replyToId?.takeIf { isUuid(it) },
                replyToSender = replyToSender,
                replyToText = replyToText
            )
            val inserted = client.postgrest.from("room_messages")
                .upsert(dto) {
                    onConflict = "sender_id,room_id,client_msg_id"
                    select()
                }
                .decodeSingle<RoomMessageDto>()
            Result.success(inserted.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRoomModeration(roomId: String, locked: Boolean? = null, isOpen: Boolean? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        try {
            client.postgrest.from("rooms").update(
                RoomModerationPatchDto(isLocked = locked, isOpen = isOpen)
            ) {
                filter { eq("id", roomId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cleanupRoomMessagesAdmin(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        try {
            client.postgrest.rpc(
                function = "cleanup_room_messages",
                parameters = buildJsonObject { put("target_room_id", roomId) }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminSetProfileVerification(targetUserId: String, approved: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        if (!isUuid(targetUserId)) return@withContext Result.success(Unit)
        try {
            client.postgrest.rpc(
                function = "admin_set_profile_verification",
                parameters = buildJsonObject {
                    put("target_user_id", targetUserId)
                    put("approved", approved)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoomMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        if (!isUuid(messageId)) return@withContext Result.success(Unit)
        try {
            client.postgrest.from("room_messages").delete {
                filter { eq("id", messageId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRoomRealtime(roomId: String, onMessageUpsert: (ChatMessage) -> Unit, onMessageDelete: (String) -> Unit) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return
        stopRoomRealtime()
        roomRealtimeJob = ioScope.launch {
            try {
                val ch = client.realtime.channel("room-stream-$roomId")
                roomRealtimeChannel = ch
                val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "room_messages"
                    filter("room_id", FilterOperator.EQ, roomId)
                }
                launch {
                    flow.collect { action ->
                        when (action) {
                            is PostgresAction.Insert -> {
                                runCatching {
                                    val dto = SupabaseClientProvider.json.decodeFromString(
                                        RoomMessageDto.serializer(),
                                        action.record.toString()
                                    )
                                    if (!SupabaseMappers.isExpired(dto.expiresAt)) {
                                        onMessageUpsert(dto.toDomain())
                                    }
                                }
                            }
                            is PostgresAction.Update -> {
                                runCatching {
                                    val dto = SupabaseClientProvider.json.decodeFromString(
                                        RoomMessageDto.serializer(),
                                        action.record.toString()
                                    )
                                    onMessageUpsert(dto.toDomain())
                                }
                            }
                            is PostgresAction.Delete -> {
                                val deletedId = action.oldRecord["id"]?.toString()?.trim('"')
                                if (!deletedId.isNullOrBlank()) {
                                    onMessageDelete(deletedId)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
                ch.subscribe()
            } catch (e: Exception) {
                Log.w(TAG, "Room realtime warning: ${e.message}")
            }
        }
    }

    fun stopRoomRealtime() {
        roomRealtimeJob?.cancel()
        roomRealtimeJob = null
        val ch = roomRealtimeChannel
        roomRealtimeChannel = null
        if (ch != null) {
            ioScope.launch { runCatching { ch.unsubscribe() } }
        }
    }

    // =========================================================================
    // 4. DIRECT MESSAGES, PAGINATION, REALTIME & IDEMPOTENCY
    // =========================================================================

    suspend fun getOrCreateConversationId(partnerId: String): Result<String> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        try {
            val convId = client.postgrest.rpc(
                function = "get_or_create_direct_conversation",
                parameters = buildJsonObject { put("partner_id", partnerId) }
            ).decodeAs<String>().trim('"')
            Result.success(convId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDirectMessagesPage(
        partnerId: String,
        beforeIso: String? = null,
        limit: Int = SupabaseConfig.DM_PAGE_SIZE
    ): Result<Pair<String, List<ChatMessage>>> = withContext(Dispatchers.IO) {
        val convResult = getOrCreateConversationId(partnerId)
        val convId = convResult.getOrElse { return@withContext Result.failure(it) }
        val client = SupabaseClientProvider.requireClient()
        try {
            val dtos = client.postgrest.from("direct_messages")
                .select {
                    filter {
                        eq("conversation_id", convId)
                        if (!beforeIso.isNullOrBlank()) {
                            lt("created_at", beforeIso)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<DirectMessageDto>()
            val ordered = dtos.reversed().map { it.toDomain() }
            Result.success(convId to ordered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendDirectMessage(
        partnerId: String,
        senderName: String,
        message: ChatMessage,
        clientMsgId: String = message.id.ifBlank { UUID.randomUUID().toString() }
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Sign in required to send messages"))
        val convId = getOrCreateConversationId(partnerId)
            .getOrElse { return@withContext Result.failure(it) }

        val msgType = when {
            message.isAudio -> "audio"
            message.isDoodle -> "doodle"
            message.isSticker -> "sticker"
            message.isGif -> "gif"
            message.isChallenge -> "challenge"
            !message.mediaUrl.isNullOrBlank() -> "image"
            else -> "text"
        }

        try {
            val dto = DirectMessageDto(
                clientMsgId = clientMsgId,
                conversationId = convId,
                senderId = uid,
                senderName = senderName,
                content = message.text.ifBlank { " " },
                messageType = msgType,
                mediaUrl = message.mediaUrl,
                audioDuration = message.audioDuration,
                audioWaveform = message.audioWaveform,
                challengeData = message.challengeData?.let {
                    ChallengePayloadDto(
                        gameId = it.gameId,
                        gameTitle = it.gameTitle,
                        challenger = it.challenger,
                        opponent = it.opponent,
                        status = it.status
                    )
                },
                stickerPackId = message.stickerPackId,
                replyToId = message.replyToId?.takeIf { isUuid(it) },
                replyToSender = message.replyToSender,
                replyToText = message.replyToText,
                isViewOnce = message.isViewOnce,
                viewed = message.viewed,
                isForwarded = message.isForwarded,
                forwardedFrom = message.forwardedFrom,
                deliveryStatus = "sent"
            )
            val saved = client.postgrest.from("direct_messages")
                .upsert(dto) {
                    onConflict = "sender_id,conversation_id,client_msg_id"
                    select()
                }
                .decodeSingle<DirectMessageDto>()

            val domainMsg = saved.toDomain()
            dispatchPushNotification(
                recipientId = partnerId,
                conversationId = convId,
                messageId = domainMsg.id,
                clientMsgId = clientMsgId,
                previewText = if (message.isViewOnce) "Sent a view-once photo" else message.text.take(120),
                notificationType = "dm"
            )
            Result.success(domainMsg)
        } catch (e: Exception) {
            Log.w(TAG, "sendDirectMessage error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun unsendDirectMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        if (!isUuid(messageId)) return@withContext Result.success(Unit)
        try {
            client.postgrest.from("direct_messages").update(
                buildJsonObject { put("unsent_at", SupabaseMappers.formatIsoTimestamp()) }
            ) {
                filter { eq("id", messageId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editDirectMessage(messageId: String, newText: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        if (!isUuid(messageId)) return@withContext Result.success(Unit)
        try {
            client.postgrest.from("direct_messages").update(
                buildJsonObject {
                    put("content", newText)
                    put("edited_at", SupabaseMappers.formatIsoTimestamp())
                }
            ) {
                filter { eq("id", messageId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeDirectConversationRealtime(
        conversationId: String,
        onMessageUpsert: (ChatMessage) -> Unit,
        onMessageUnsentOrDeleted: (String) -> Unit
    ) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return
        stopDirectConversationRealtime()
        dmRealtimeJob = ioScope.launch {
            try {
                val ch = client.realtime.channel("dm-conv-$conversationId")
                dmRealtimeChannel = ch
                val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "direct_messages"
                    filter("conversation_id", FilterOperator.EQ, conversationId)
                }
                launch {
                    flow.collect { action ->
                        when (action) {
                            is PostgresAction.Insert -> {
                                runCatching {
                                    val dto = SupabaseClientProvider.json.decodeFromString(
                                        DirectMessageDto.serializer(),
                                        action.record.toString()
                                    )
                                    if (dto.unsentAt == null) {
                                        onMessageUpsert(dto.toDomain())
                                    }
                                }
                            }
                            is PostgresAction.Update -> {
                                runCatching {
                                    val dto = SupabaseClientProvider.json.decodeFromString(
                                        DirectMessageDto.serializer(),
                                        action.record.toString()
                                    )
                                    if (dto.unsentAt != null && dto.id != null) {
                                        onMessageUnsentOrDeleted(dto.id)
                                    } else {
                                        onMessageUpsert(dto.toDomain())
                                    }
                                }
                            }
                            is PostgresAction.Delete -> {
                                val deletedId = action.oldRecord["id"]?.toString()?.trim('"')
                                if (!deletedId.isNullOrBlank()) {
                                    onMessageUnsentOrDeleted(deletedId)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
                ch.subscribe()
            } catch (e: Exception) {
                Log.w(TAG, "DM realtime warning: ${e.message}")
            }
        }
    }

    fun stopDirectConversationRealtime() {
        dmRealtimeJob?.cancel()
        dmRealtimeJob = null
        val ch = dmRealtimeChannel
        dmRealtimeChannel = null
        if (ch != null) {
            ioScope.launch { runCatching { ch.unsubscribe() } }
        }
    }

    // =========================================================================
    // 5. STORAGE UPLOAD (COMPRESSED PHOTO PICKER -> profile-images/{uid}/...)
    // =========================================================================

    suspend fun uploadCompressedProfileImage(
        context: Context,
        uri: Uri,
        folderTag: String = "avatar"
    ): Result<String> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(
                IllegalStateException("Configure Supabase first to upload photos to cloud storage.")
            )
        val uid = currentUid()
            ?: return@withContext Result.failure(IllegalStateException("Sign in required before uploading photos."))

        val compressed = ImageCompressor.compressUri(context, uri)
            .getOrElse { return@withContext Result.failure(it) }

        val objectPath = "$uid/${folderTag}_${System.currentTimeMillis()}.${compressed.extension}"
        ResilienceGuardian.executeSupabaseCall("uploadProfileImage", bypassSchemaCircuit = true) {
            val bucket = client.storage.from(SupabaseConfig.STORAGE_BUCKET_PROFILE_IMAGES)
            bucket.upload(objectPath, compressed.bytes) {
                upsert = true
            }
            bucket.publicUrl(objectPath)
        }
    }

    // =========================================================================
    // 6. FCM DEVICE TOKENS & EDGE FUNCTION PUSH DISPATCH
    // =========================================================================

    suspend fun registerDeviceToken(fcmToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (fcmToken.isBlank()) return@withContext Result.success(Unit)
        val client = SupabaseClientProvider.getClientOrNull() ?: return@withContext Result.success(Unit)
        val uid = currentUid() ?: return@withContext Result.success(Unit)
        ResilienceGuardian.executeSupabaseCall("registerDeviceToken", maxTransientRetries = 1) {
            client.postgrest.from("device_tokens").upsert(
                DeviceTokenUpsertDto(
                    userId = uid,
                    token = fcmToken.trim(),
                    platform = "android",
                    updatedAt = SupabaseMappers.formatIsoTimestamp()
                )
            ) {
                onConflict = "user_id,token"
            }
            Unit
        }
    }

    suspend fun dispatchPushNotification(
        recipientId: String,
        previewText: String,
        conversationId: String? = null,
        roomId: String? = null,
        messageId: String? = null,
        clientMsgId: String? = null,
        callId: String? = null,
        callType: String? = null,
        notificationType: String = "dm"
    ) = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return@withContext
        currentUid() ?: return@withContext
        if (!isUuid(recipientId)) return@withContext
        try {
            client.functions.invoke(
                function = SupabaseConfig.EDGE_FN_SEND_PUSH,
                body = PushNotificationRequest(
                    recipientId = recipientId,
                    conversationId = conversationId,
                    roomId = roomId,
                    messageId = messageId,
                    clientMsgId = clientMsgId,
                    callId = callId,
                    callType = callType,
                    previewText = previewText,
                    notificationType = notificationType
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Push dispatch skipped: ${e.message}")
        }
    }

    // =========================================================================
    // 7. ARCADE MULTIPLAYER SESSIONS
    // =========================================================================

    suspend fun upsertArcadeSession(gameState: ArcadeGameState, challengerId: String?, opponentId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        try {
            val dto = ArcadeSessionDto(
                id = gameState.gameId,
                gameType = gameState.gameType.ifBlank { "chess" },
                challengerId = challengerId?.takeIf { isUuid(it) },
                opponentId = opponentId?.takeIf { isUuid(it) },
                challengerName = gameState.challenger,
                opponentName = gameState.opponent,
                currentTurn = gameState.currentTurn,
                chessBoard = gameState.chessBoard,
                capturedWhite = gameState.capturedWhite,
                capturedBlack = gameState.capturedBlack,
                lastMoveDescription = gameState.lastMoveDescription,
                winner = gameState.winner
            )
            client.postgrest.from("arcade_sessions").upsert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return runCatching { UUID.fromString(value) }.isSuccess
    }
}
