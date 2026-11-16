package co.znxdnd.echotalk.calling

import co.znxdnd.echotalk.data.model.User

enum class CallType(val wireValue: String, val label: String) {
    AUDIO("audio", "Voice Call"),
    VIDEO("video", "Video Call");

    companion object {
        fun fromWire(raw: String?): CallType =
            if (raw.equals("video", ignoreCase = true)) VIDEO else AUDIO
    }
}

enum class CallDirection {
    OUTGOING,
    INCOMING
}

enum class CallState {
    IDLE,
    REQUESTING_PERMISSIONS,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED,
    REJECTED,
    MISSED,
    BUSY,
    UNAVAILABLE,
    LIMIT_REACHED,
    FAILED;

    val isTerminal: Boolean
        get() = this in setOf(ENDED, REJECTED, MISSED, BUSY, UNAVAILABLE, LIMIT_REACHED, FAILED)

    val isActiveSession: Boolean
        get() = this in setOf(REQUESTING_PERMISSIONS, OUTGOING_RINGING, INCOMING_RINGING, CONNECTING, CONNECTED, RECONNECTING)
}

enum class AudioOutputRoute(val label: String) {
    EARPIECE("Earpiece"),
    SPEAKERPHONE("Speaker"),
    BLUETOOTH("Bluetooth"),
    WIRED_HEADSET("Headset")
}

enum class NetworkQualityLevel(val label: String) {
    EXCELLENT("HD Signal"),
    GOOD("Good Signal"),
    POOR("Poor Connection"),
    LOST("Reconnecting..."),
    UNKNOWN("Connecting")
}

enum class PermissionNeed {
    NONE,
    MICROPHONE,
    CAMERA,
    MICROPHONE_AND_CAMERA
}

data class CallSessionRecord(
    val id: String,
    val roomName: String,
    val callerId: String,
    val calleeId: String,
    val callType: CallType,
    val status: String,
    val endReason: String? = null,
    val durationSeconds: Int = 0
)

data class LiveKitTokenPayload(
    val token: String,
    val wsUrl: String,
    val roomName: String,
    val callId: String,
    val expiresInSeconds: Int = 300,
    val plan: String = "build_free"
)

data class IncomingCallInvite(
    val callId: String,
    val roomName: String,
    val callerId: String,
    val calleeId: String,
    val caller: User,
    val callType: CallType,
    val receivedAtMillis: Long = System.currentTimeMillis()
)

sealed class TokenFetchFailure(message: String) : Exception(message) {
    class LiveKitNotConfigured(message: String) : TokenFetchFailure(message)
    class FreeLimitReached(message: String) : TokenFetchFailure(message)
    class Unauthorized(message: String) : TokenFetchFailure(message)
    class CallInactive(message: String) : TokenFetchFailure(message)
    class NetworkOrServerError(message: String) : TokenFetchFailure(message)
}

data class CallUiState(
    val callId: String? = null,
    val roomName: String? = null,
    val partner: User? = null,
    val callType: CallType = CallType.AUDIO,
    val direction: CallDirection = CallDirection.OUTGOING,
    val state: CallState = CallState.IDLE,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val activeAudioRoute: AudioOutputRoute = AudioOutputRoute.EARPIECE,
    val availableAudioRoutes: List<AudioOutputRoute> = listOf(AudioOutputRoute.EARPIECE, AudioOutputRoute.SPEAKERPHONE),
    val durationSeconds: Int = 0,
    val networkQuality: NetworkQualityLevel = NetworkQualityLevel.UNKNOWN,
    val isPoorConnection: Boolean = false,
    val pendingPermissionNeed: PermissionNeed = PermissionNeed.NONE,
    val permissionRationaleVisible: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val hasLocalVideo: Boolean = false,
    val statusMessage: String = "",
    val errorTitle: String? = null,
    val errorDetail: String? = null,
    val incomingInvite: IncomingCallInvite? = null
) {
    val isModalVisible: Boolean
        get() = state != CallState.IDLE && state != CallState.INCOMING_RINGING

    val isIncomingVisible: Boolean
        get() = state == CallState.INCOMING_RINGING && incomingInvite != null

    val formattedDuration: String
        get() {
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            return "%d:%02d".format(mins, secs)
        }
}
