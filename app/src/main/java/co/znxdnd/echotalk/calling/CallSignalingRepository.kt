package co.znxdnd.echotalk.calling

import android.util.Log
import co.znxdnd.echotalk.data.remote.CallSessionDto
import co.znxdnd.echotalk.data.remote.CallSessionPatchDto
import co.znxdnd.echotalk.data.remote.LiveKitTokenRequest
import co.znxdnd.echotalk.data.remote.LiveKitTokenResponse
import co.znxdnd.echotalk.data.remote.SupabaseClientProvider
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseMappers
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

interface CallSignalingGateway {
    fun currentUserId(): String?
    suspend fun createCallInvitation(calleeId: String, callType: CallType): Result<CallSessionRecord>
    suspend fun fetchCallSession(callId: String): Result<CallSessionRecord?>
    suspend fun updateCallSessionStatus(
        callId: String,
        status: String,
        endReason: String? = null,
        durationSeconds: Int? = null
    ): Result<Unit>
    suspend fun requestLiveKitToken(callId: String): Result<LiveKitTokenPayload>
    suspend fun sendCallPushNotification(
        recipientId: String,
        callId: String,
        callType: CallType,
        notificationType: String,
        previewText: String
    )
    fun observeIncomingCallsForUser(
        userId: String,
        onCallEvent: (CallSessionRecord) -> Unit
    )
    fun observeActiveCallSession(
        callId: String,
        onCallUpdated: (CallSessionRecord) -> Unit
    )
    fun stopActiveCallObserver()
    fun stopIncomingCallObserver()
}

/**
 * Pure domain authorization & transition validator so tests can assert security invariants locally
 * in addition to PostgreSQL RLS and Edge Function verification.
 */
object CallSecurityValidator {
    private val activeStatuses = setOf("initiating", "ringing", "accepted", "connecting", "connected")
    private val terminalStatuses = setOf("ended", "rejected", "missed", "busy", "unavailable", "failed")

    fun isUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return runCatching { UUID.fromString(value) }.isSuccess
    }

    fun canJoinCallSession(userId: String?, session: CallSessionRecord): Boolean {
        if (userId.isNullOrBlank()) return false
        if (userId != session.callerId && userId != session.calleeId) return false
        return session.status in activeStatuses
    }

    fun isValidTransition(fromStatus: String, toStatus: String): Boolean {
        if (fromStatus == toStatus) return true
        if (fromStatus in terminalStatuses) return false
        return true
    }
}

class SupabaseCallSignalingGateway(
    private val socialRepo: SupabaseSocialRepository = SupabaseSocialRepository.getInstance()
) : CallSignalingGateway {

    companion object {
        private const val TAG = "CallSignalingGateway"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var incomingChannel: RealtimeChannel? = null
    private var incomingJob: Job? = null
    private var observedIncomingUid: String? = null

    private var activeCallChannel: RealtimeChannel? = null
    private var activeCallJob: Job? = null

    override fun currentUserId(): String? = socialRepo.currentUid()

    override suspend fun createCallInvitation(calleeId: String, callType: CallType): Result<CallSessionRecord> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.getClientOrNull()
                ?: return@withContext Result.failure(
                    IllegalStateException("Cloud backend is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY first.")
                )
            val callerId = currentUserId()
                ?: return@withContext Result.failure(
                    IllegalStateException("Sign in with an EchoTalks account to place calls.")
                )
            if (!CallSecurityValidator.isUuid(calleeId)) {
                return@withContext Result.failure(
                    IllegalStateException("Calling requires the recipient to have a real registered EchoTalks account.")
                )
            }
            if (calleeId == callerId) {
                return@withContext Result.failure(IllegalStateException("You cannot call yourself."))
            }

            try {
                val dto = client.postgrest.rpc(
                    function = "create_call_invitation",
                    parameters = buildJsonObject {
                        put("p_callee_id", calleeId)
                        put("p_call_type", callType.wireValue)
                    }
                ).decodeAs<CallSessionDto>()
                Result.success(dto.toDomain())
            } catch (e: Exception) {
                Log.w(TAG, "createCallInvitation failed: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun fetchCallSession(callId: String): Result<CallSessionRecord?> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.getClientOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
            try {
                val dto = client.postgrest.from("call_sessions")
                    .select {
                        filter { eq("id", callId) }
                        limit(1)
                    }
                    .decodeSingleOrNull<CallSessionDto>()
                Result.success(dto?.toDomain())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun updateCallSessionStatus(
        callId: String,
        status: String,
        endReason: String?,
        durationSeconds: Int?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        if (!CallSecurityValidator.isUuid(callId)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid call ID"))
        }
        try {
            val nowIso = SupabaseMappers.formatIsoTimestamp()
            val patch = CallSessionPatchDto(
                status = status,
                endReason = endReason,
                answeredAt = if (status == "connected") nowIso else null,
                endedAt = if (status in setOf("ended", "rejected", "missed", "busy", "unavailable", "failed")) nowIso else null,
                durationSeconds = durationSeconds
            )
            client.postgrest.from("call_sessions").update(patch) {
                filter { eq("id", callId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "updateCallSessionStatus($status) warning: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun requestLiveKitToken(callId: String): Result<LiveKitTokenPayload> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.getClientOrNull()
                ?: return@withContext Result.failure(
                    TokenFetchFailure.LiveKitNotConfigured("Supabase backend is not configured.")
                )
            val session = client.auth.currentSessionOrNull()
            if (session == null) {
                return@withContext Result.failure(
                    TokenFetchFailure.Unauthorized("Sign in required before connecting voice/video.")
                )
            }

            try {
                val httpResp = client.functions.invoke(
                    function = SupabaseConfig.EDGE_FN_LIVEKIT_TOKEN,
                    body = LiveKitTokenRequest(callId = callId)
                )
                val parsed = httpResp.body<LiveKitTokenResponse>()
                if (!parsed.token.isNullOrBlank() && !parsed.wsUrl.isNullOrBlank()) {
                    return@withContext Result.success(
                        LiveKitTokenPayload(
                            token = parsed.token,
                            wsUrl = parsed.wsUrl,
                            roomName = parsed.roomName ?: "",
                            callId = parsed.callId ?: callId,
                            expiresInSeconds = parsed.expiresIn,
                            plan = parsed.plan ?: "build_free"
                        )
                    )
                }
                Result.failure(mapErrorCodeToFailure(parsed.error, parsed.message))
            } catch (respEx: ResponseException) {
                val status = respEx.response.status
                val parsedErr = runCatching { respEx.response.body<LiveKitTokenResponse>() }.getOrNull()
                val mapped = when {
                    parsedErr?.error != null -> mapErrorCodeToFailure(parsedErr.error, parsedErr.message)
                    status == HttpStatusCode.ServiceUnavailable -> TokenFetchFailure.LiveKitNotConfigured(
                        "LiveKit server secrets (LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET) are not configured on Supabase."
                    )
                    status == HttpStatusCode.TooManyRequests -> TokenFetchFailure.FreeLimitReached(
                        "Free Build plan monthly allowance (5,000 participant-minutes) reached."
                    )
                    status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
                        TokenFetchFailure.Unauthorized("Unauthorized to join this call session.")
                    status == HttpStatusCode.Conflict ->
                        TokenFetchFailure.CallInactive("Call is no longer active.")
                    status == HttpStatusCode.NotFound -> TokenFetchFailure.LiveKitNotConfigured(
                        "Edge Function livekit-token is not deployed on Supabase yet."
                    )
                    else -> TokenFetchFailure.NetworkOrServerError(
                        respEx.message ?: "Call server returned HTTP ${status.value}"
                    )
                }
                Result.failure(mapped)
            } catch (e: Exception) {
                val msg = e.message ?: "Unable to fetch secure call token"
                val mapped = when {
                    msg.contains("livekit_not_configured", ignoreCase = true) ->
                        TokenFetchFailure.LiveKitNotConfigured(msg)
                    msg.contains("free_limit_reached", ignoreCase = true) ->
                        TokenFetchFailure.FreeLimitReached(msg)
                    msg.contains("404", ignoreCase = true) || msg.contains("NOT_FOUND", ignoreCase = true) ->
                        TokenFetchFailure.LiveKitNotConfigured("Deploy Supabase Edge Function `livekit-token` and set LiveKit Build secrets.")
                    else -> TokenFetchFailure.NetworkOrServerError(msg)
                }
                Result.failure(mapped)
            }
        }

    private fun mapErrorCodeToFailure(code: String?, message: String?): TokenFetchFailure {
        val text = message ?: "Unable to join call"
        return when (code) {
            "livekit_not_configured" -> TokenFetchFailure.LiveKitNotConfigured(text)
            "free_limit_reached" -> TokenFetchFailure.FreeLimitReached(text)
            "unauthorized", "forbidden_participant", "user_blocked" -> TokenFetchFailure.Unauthorized(text)
            "call_inactive", "call_not_found" -> TokenFetchFailure.CallInactive(text)
            else -> TokenFetchFailure.NetworkOrServerError(text)
        }
    }

    override suspend fun sendCallPushNotification(
        recipientId: String,
        callId: String,
        callType: CallType,
        notificationType: String,
        previewText: String
    ) {
        socialRepo.dispatchPushNotification(
            recipientId = recipientId,
            callId = callId,
            callType = callType.wireValue,
            previewText = previewText,
            notificationType = notificationType
        )
    }

    override fun observeIncomingCallsForUser(
        userId: String,
        onCallEvent: (CallSessionRecord) -> Unit
    ) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return
        if (observedIncomingUid == userId && incomingJob?.isActive == true) return
        stopIncomingCallObserver()
        observedIncomingUid = userId

        incomingJob = ioScope.launch {
            try {
                val ch = client.realtime.channel("incoming-calls-$userId")
                incomingChannel = ch
                val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "call_sessions"
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
                                CallSessionDto.serializer(),
                                record.toString()
                            )
                            val domain = dto.toDomain()
                            if (domain.calleeId == userId || domain.callerId == userId) {
                                onCallEvent(domain)
                            }
                        }
                    }
                }
                ch.subscribe()
            } catch (e: Exception) {
                Log.w(TAG, "Incoming call realtime stream warning: ${e.message}")
            }
        }
    }

    override fun observeActiveCallSession(
        callId: String,
        onCallUpdated: (CallSessionRecord) -> Unit
    ) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return
        stopActiveCallObserver()
        activeCallJob = ioScope.launch {
            try {
                val ch = client.realtime.channel("call-session-$callId")
                activeCallChannel = ch
                val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "call_sessions"
                }
                launch {
                    flow.collect { action ->
                        val record = when (action) {
                            is PostgresAction.Update -> action.record
                            is PostgresAction.Insert -> action.record
                            else -> null
                        } ?: return@collect
                        runCatching {
                            val dto = SupabaseClientProvider.json.decodeFromString(
                                CallSessionDto.serializer(),
                                record.toString()
                            )
                            if (dto.id == callId) {
                                onCallUpdated(dto.toDomain())
                            }
                        }
                    }
                }
                ch.subscribe()
            } catch (e: Exception) {
                Log.w(TAG, "Active call observer warning: ${e.message}")
            }
        }
    }

    override fun stopActiveCallObserver() {
        activeCallJob?.cancel()
        activeCallJob = null
        val ch = activeCallChannel
        activeCallChannel = null
        if (ch != null) {
            ioScope.launch { runCatching { ch.unsubscribe() } }
        }
    }

    override fun stopIncomingCallObserver() {
        observedIncomingUid = null
        incomingJob?.cancel()
        incomingJob = null
        val ch = incomingChannel
        incomingChannel = null
        if (ch != null) {
            ioScope.launch { runCatching { ch.unsubscribe() } }
        }
    }

    private fun CallSessionDto.toDomain(): CallSessionRecord {
        return CallSessionRecord(
            id = id,
            roomName = roomName,
            callerId = callerId,
            calleeId = calleeId,
            callType = CallType.fromWire(callType),
            status = status,
            endReason = endReason,
            durationSeconds = durationSeconds
        )
    }
}
