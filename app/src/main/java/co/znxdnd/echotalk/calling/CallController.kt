package co.znxdnd.echotalk.calling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.util.Log
import androidx.core.content.ContextCompat
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.data.remote.ProfileDto
import co.znxdnd.echotalk.data.remote.SupabaseClientProvider
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseMappers.toDomain
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.utils.EchoHapticManager
import io.github.jan.supabase.postgrest.postgrest
import io.livekit.android.renderer.TextureViewRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface CallPermissionChecker {
    fun hasPermission(permission: String): Boolean
}

class AndroidCallPermissionChecker(private val context: Context) : CallPermissionChecker {
    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Coordinates the complete 1-to-1 voice and video calling lifecycle across
 * Supabase signaling, FCM notifications, Android runtime permissions, ForegroundService,
 * and LiveKit WebRTC media transport.
 */
class CallController internal constructor(
    private val signaling: CallSignalingGateway,
    private val mediaClientFactory: () -> CallMediaClient,
    private val permissionChecker: CallPermissionChecker,
    private val appContextProvider: () -> Context?,
    private val repository: EchoTalkRepository = EchoTalkRepository.getInstance(),
    private val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    companion object {
        private const val TAG = "CallController"

        @Volatile
        private var appContext: Context? = null

        @Volatile
        private var instance: CallController? = null

        fun initialize(context: Context) {
            appContext = context.applicationContext
            getInstance()
        }

        fun getInstance(): CallController {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val ctx = appContext
                    CallController(
                        signaling = SupabaseCallSignalingGateway(),
                        mediaClientFactory = {
                            val c = appContext
                                ?: throw IllegalStateException("CallController.initialize(context) must be called before LiveKit calls")
                            LiveKitRoomCallClient(c)
                        },
                        permissionChecker = CallPermissionChecker { perm ->
                            val c = appContext ?: return@CallPermissionChecker false
                            ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED
                        },
                        appContextProvider = { appContext }
                    ).also { instance = it }
                }
            }
        }

        internal fun createForTesting(
            signaling: CallSignalingGateway,
            mediaClient: CallMediaClient,
            permissionChecker: CallPermissionChecker,
            repository: EchoTalkRepository = EchoTalkRepository.getInstance(),
            scope: CoroutineScope
        ): CallController {
            return CallController(
                signaling = signaling,
                mediaClientFactory = { mediaClient },
                permissionChecker = permissionChecker,
                appContextProvider = { null },
                repository = repository,
                mainScope = scope
            )
        }
    }

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _permissionPromptTrigger = MutableStateFlow<List<String>?>(null)
    val permissionPromptTrigger: StateFlow<List<String>?> = _permissionPromptTrigger.asStateFlow()

    val mediaClient: CallMediaClient?
        get() = activeMediaClient

    private val sessionMutex = Mutex()
    private var activeMediaClient: CallMediaClient? = null
    private var mediaEventsJob: Job? = null
    private var mediaRoutesJob: Job? = null
    private var mediaTracksJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var statusPollJob: Job? = null
    private var durationTickerJob: Job? = null
    private var autoDismissJob: Job? = null

    private var activeRingtone: Ringtone? = null
    private var preFetchedTokenPayload: LiveKitTokenPayload? = null
    private var pendingActionAfterPermissions: (() -> Unit)? = null
    private var pendingMidCallCameraEnable: Boolean = false
    private var isObservingIncomingForUid: String? = null

    fun startIncomingCallListenerIfSignedIn() {
        val uid = signaling.currentUserId() ?: return
        if (isObservingIncomingForUid == uid) return
        isObservingIncomingForUid = uid
        signaling.observeIncomingCallsForUser(uid) { record ->
            mainScope.launch {
                handleSignalingRecordForUser(uid, record)
            }
        }
    }

    fun stopIncomingCallListener() {
        isObservingIncomingForUid = null
        signaling.stopIncomingCallObserver()
    }

    fun handleNotificationOrDeepLinkIntent(
        callId: String?,
        callTypeWire: String?,
        callerId: String?,
        callerName: String?
    ) {
        if (callId.isNullOrBlank()) return
        val uid = signaling.currentUserId() ?: return
        mainScope.launch {
            val fetched = signaling.fetchCallSession(callId).getOrNull()
            if (fetched != null) {
                handleSignalingRecordForUser(uid, fetched)
            } else if (!callerId.isNullOrBlank()) {
                val fallbackCaller = resolveUser(callerId, callerName)
                receiveIncomingCall(
                    IncomingCallInvite(
                        callId = callId,
                        roomName = "",
                        callerId = callerId,
                        calleeId = uid,
                        caller = fallbackCaller,
                        callType = CallType.fromWire(callTypeWire)
                    )
                )
            }
        }
    }

    fun startOutgoingCall(partner: User, callType: CallType) {
        mainScope.launch {
            sessionMutex.withLock {
                if (_uiState.value.state.isActiveSession) {
                    // Do not launch duplicate call sessions
                    return@launch
                }

                val currentSettings = repository.settings.value
                if (currentSettings.blockedUsers.contains(partner.id)) {
                    _uiState.value = CallUiState(
                        partner = partner,
                        callType = callType,
                        direction = CallDirection.OUTGOING,
                        state = CallState.FAILED,
                        statusMessage = "Unblock user first",
                        errorTitle = "User Blocked",
                        errorDetail = "Unblock ${partner.name} in privacy settings before placing a call."
                    )
                    return@launch
                }

                val callerUid = signaling.currentUserId()
                if (callerUid == null) {
                    _uiState.value = CallUiState(
                        partner = partner,
                        callType = callType,
                        direction = CallDirection.OUTGOING,
                        state = CallState.FAILED,
                        statusMessage = "Account Required",
                        errorTitle = "Sign In Required",
                        errorDetail = "Live voice and video calls connect real authenticated EchoTalks accounts. Sign in first."
                    )
                    return@launch
                }

                val missing = missingPermissionsFor(callType)
                _uiState.value = CallUiState(
                    partner = partner,
                    callType = callType,
                    direction = CallDirection.OUTGOING,
                    state = if (missing.isEmpty()) CallState.OUTGOING_RINGING else CallState.REQUESTING_PERMISSIONS,
                    isCameraEnabled = callType == CallType.VIDEO && permissionChecker.hasPermission(Manifest.permission.CAMERA),
                    activeAudioRoute = if (callType == CallType.VIDEO) AudioOutputRoute.SPEAKERPHONE else AudioOutputRoute.EARPIECE,
                    pendingPermissionNeed = toPermissionNeed(missing),
                    statusMessage = if (missing.isEmpty()) "Starting ${callType.label.lowercase()}..." else "Permission needed"
                )

                if (missing.isNotEmpty()) {
                    pendingActionAfterPermissions = {
                        beginOutgoingInvitation(partner, callType)
                    }
                    _permissionPromptTrigger.value = missing
                } else {
                    beginOutgoingInvitation(partner, callType)
                }
            }
        }
    }

    fun receiveIncomingCall(invite: IncomingCallInvite) {
        mainScope.launch {
            sessionMutex.withLock {
                val current = _uiState.value
                if (current.callId == invite.callId && !current.state.isTerminal && current.state != CallState.IDLE) {
                    return@launch
                }

                val currentSettings = repository.settings.value
                if (!currentSettings.allowIncomingCalls) {
                    signaling.updateCallSessionStatus(invite.callId, "unavailable", endReason = "callee_unavailable")
                    return@launch
                }

                if (currentSettings.blockedUsers.contains(invite.callerId)) {
                    signaling.updateCallSessionStatus(invite.callId, "rejected", endReason = "blocked")
                    return@launch
                }

                if (current.state.isActiveSession) {
                    // Already on an active call -> inform caller we are busy
                    signaling.updateCallSessionStatus(invite.callId, "busy", endReason = "callee_busy")
                    return@launch
                }

                _uiState.value = CallUiState(
                    callId = invite.callId,
                    roomName = invite.roomName,
                    partner = invite.caller,
                    callType = invite.callType,
                    direction = CallDirection.INCOMING,
                    state = CallState.INCOMING_RINGING,
                    isCameraEnabled = false,
                    activeAudioRoute = if (invite.callType == CallType.VIDEO) AudioOutputRoute.SPEAKERPHONE else AudioOutputRoute.EARPIECE,
                    statusMessage = "Incoming ${invite.callType.label.lowercase()}...",
                    incomingInvite = invite
                )

                startIncomingRingtoneIfAllowed()
                observeCallUpdatesAndFallback(invite.callId)
            }
        }
    }

    fun acceptIncomingCall() {
        mainScope.launch {
            val invite = _uiState.value.incomingInvite ?: return@launch
            val callType = invite.callType
            val missing = missingPermissionsFor(callType)
            stopRingtone()

            if (missing.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        state = CallState.REQUESTING_PERMISSIONS,
                        pendingPermissionNeed = toPermissionNeed(missing),
                        permissionRationaleVisible = false,
                        statusMessage = "Permission needed to answer"
                    )
                }
                pendingActionAfterPermissions = {
                    proceedAcceptIncoming(invite)
                }
                _permissionPromptTrigger.value = missing
            } else {
                proceedAcceptIncoming(invite)
            }
        }
    }

    fun rejectIncomingCall(reason: String = "declined_by_callee") {
        mainScope.launch {
            val current = _uiState.value
            val callId = current.callId
            stopRingtone()
            cleanupMediaAndTimers()
            if (callId != null) {
                signaling.updateCallSessionStatus(callId, "rejected", endReason = reason)
            }
            _uiState.value = CallUiState()
        }
    }

    fun retryPendingPermissions() {
        val current = _uiState.value
        val perms = when (current.pendingPermissionNeed) {
            PermissionNeed.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
            PermissionNeed.CAMERA -> listOf(Manifest.permission.CAMERA)
            PermissionNeed.MICROPHONE_AND_CAMERA -> listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            PermissionNeed.NONE -> missingPermissionsFor(current.callType)
        }
        if (perms.isEmpty()) {
            onPermissionsResult(emptyMap(), emptyMap())
        } else {
            _permissionPromptTrigger.value = perms
        }
    }

    fun onPermissionPromptLaunched() {
        _permissionPromptTrigger.value = null
    }

    fun onPermissionsResult(
        grantResults: Map<String, Boolean>,
        permanentlyDeniedMap: Map<String, Boolean> = emptyMap()
    ) {
        mainScope.launch {
            val current = _uiState.value
            val hasMic = permissionChecker.hasPermission(Manifest.permission.RECORD_AUDIO) ||
                (grantResults[Manifest.permission.RECORD_AUDIO] == true)
            val hasCam = permissionChecker.hasPermission(Manifest.permission.CAMERA) ||
                (grantResults[Manifest.permission.CAMERA] == true)

            if (pendingMidCallCameraEnable) {
                pendingMidCallCameraEnable = false
                if (hasCam) {
                    _uiState.update {
                        it.copy(
                            pendingPermissionNeed = PermissionNeed.NONE,
                            permissionRationaleVisible = false,
                            permissionPermanentlyDenied = false
                        )
                    }
                    activeMediaClient?.setCameraEnabled(true, _uiState.value.isFrontCamera)
                    _uiState.update {
                        it.copy(isCameraEnabled = true, hasLocalVideo = true)
                    }
                    updateForegroundServiceNotification()
                } else {
                    val permDenied = permanentlyDeniedMap[Manifest.permission.CAMERA] == true
                    _uiState.update {
                        it.copy(
                            pendingPermissionNeed = PermissionNeed.CAMERA,
                            permissionRationaleVisible = true,
                            permissionPermanentlyDenied = permDenied,
                            statusMessage = "Camera permission needed for video"
                        )
                    }
                }
                return@launch
            }

            if (!hasMic) {
                val micPermDenied = permanentlyDeniedMap[Manifest.permission.RECORD_AUDIO] == true
                _uiState.update {
                    it.copy(
                        pendingPermissionNeed = PermissionNeed.MICROPHONE,
                        permissionRationaleVisible = true,
                        permissionPermanentlyDenied = micPermDenied,
                        statusMessage = "Microphone permission required"
                    )
                }
                return@launch
            }

            // Mic is granted. Check if video call was requested without camera permission.
            val nextAction = pendingActionAfterPermissions
            pendingActionAfterPermissions = null

            if (current.callType == CallType.VIDEO && !hasCam && grantResults.containsKey(Manifest.permission.CAMERA)) {
                // Continue gracefully as voice-only or show clear camera status rather than blocking urgent call
                val camPermDenied = permanentlyDeniedMap[Manifest.permission.CAMERA] == true
                _uiState.update {
                    it.copy(
                        isCameraEnabled = false,
                        pendingPermissionNeed = PermissionNeed.CAMERA,
                        permissionRationaleVisible = true,
                        permissionPermanentlyDenied = camPermDenied,
                        statusMessage = "Camera denied — continuing with voice only"
                    )
                }
                nextAction?.invoke()
                return@launch
            }

            _uiState.update {
                it.copy(
                    pendingPermissionNeed = PermissionNeed.NONE,
                    permissionRationaleVisible = false,
                    permissionPermanentlyDenied = false,
                    isCameraEnabled = current.callType == CallType.VIDEO && hasCam
                )
            }
            nextAction?.invoke()
        }
    }

    fun dismissPermissionRationale() {
        _uiState.update {
            it.copy(
                permissionRationaleVisible = false,
                pendingPermissionNeed = PermissionNeed.NONE
            )
        }
    }

    fun toggleMute() {
        mainScope.launch {
            val current = _uiState.value
            val nextMuted = !current.isMuted
            val res = activeMediaClient?.setMicrophoneEnabled(!nextMuted) ?: Result.success(Unit)
            if (res.isSuccess) {
                _uiState.update { it.copy(isMuted = nextMuted) }
            }
        }
    }

    fun toggleCamera() {
        mainScope.launch {
            val current = _uiState.value
            val nextEnabled = !current.isCameraEnabled
            if (nextEnabled && !permissionChecker.hasPermission(Manifest.permission.CAMERA)) {
                pendingMidCallCameraEnable = true
                _uiState.update {
                    it.copy(
                        pendingPermissionNeed = PermissionNeed.CAMERA,
                        permissionRationaleVisible = false
                    )
                }
                _permissionPromptTrigger.value = listOf(Manifest.permission.CAMERA)
                return@launch
            }

            val res = activeMediaClient?.setCameraEnabled(nextEnabled, current.isFrontCamera) ?: Result.success(Unit)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isCameraEnabled = nextEnabled,
                        hasLocalVideo = nextEnabled,
                        callType = if (nextEnabled) CallType.VIDEO else it.callType
                    )
                }
                updateForegroundServiceNotification()
            }
        }
    }

    fun flipCamera() {
        mainScope.launch {
            val current = _uiState.value
            if (!current.isCameraEnabled) return@launch
            val nextFront = !current.isFrontCamera
            val res = activeMediaClient?.switchCamera(nextFront) ?: Result.success(Unit)
            if (res.isSuccess) {
                _uiState.update { it.copy(isFrontCamera = nextFront) }
            }
        }
    }

    fun selectAudioRoute(route: AudioOutputRoute) {
        activeMediaClient?.selectAudioRoute(route)
        _uiState.update { it.copy(activeAudioRoute = route) }
    }

    fun toggleSpeakerQuick() {
        val current = _uiState.value.activeAudioRoute
        val target = if (current == AudioOutputRoute.SPEAKERPHONE) {
            if (AudioOutputRoute.BLUETOOTH in _uiState.value.availableAudioRoutes) {
                AudioOutputRoute.BLUETOOTH
            } else {
                AudioOutputRoute.EARPIECE
            }
        } else {
            AudioOutputRoute.SPEAKERPHONE
        }
        selectAudioRoute(target)
    }

    fun initVideoRenderer(renderer: TextureViewRenderer) {
        activeMediaClient?.initRenderer(renderer)
    }

    fun endCall(reason: String = "hangup") {
        mainScope.launch {
            val snapshot = _uiState.value
            val callId = snapshot.callId
            val duration = snapshot.durationSeconds
            val wasRingingOutgoing = snapshot.state == CallState.OUTGOING_RINGING &&
                snapshot.direction == CallDirection.OUTGOING

            stopRingtone()
            pendingActionAfterPermissions = null
            pendingMidCallCameraEnable = false
            cleanupMediaAndTimers()

            if (callId != null && !snapshot.state.isTerminal) {
                val targetStatus = if (wasRingingOutgoing) "missed" else "ended"
                val finalReason = if (wasRingingOutgoing && reason == "hangup") "cancelled_by_caller" else reason
                signaling.updateCallSessionStatus(
                    callId = callId,
                    status = targetStatus,
                    endReason = finalReason,
                    durationSeconds = duration
                )
                if (wasRingingOutgoing && snapshot.partner != null) {
                    signaling.sendCallPushNotification(
                        recipientId = snapshot.partner.id,
                        callId = callId,
                        callType = snapshot.callType,
                        notificationType = "missed_call",
                        previewText = "Missed ${snapshot.callType.label.lowercase()} from ${repository.currentUser.value.name}"
                    )
                }
            }

            if (snapshot.partner != null && duration > 0) {
                appendLocalCallLogMessage(
                    partnerId = snapshot.partner.id,
                    text = "${snapshot.callType.label} ended • ${snapshot.formattedDuration}"
                )
            }

            if (snapshot.state in setOf(CallState.CONNECTED, CallState.RECONNECTING, CallState.CONNECTING)) {
                _uiState.update {
                    it.copy(
                        state = CallState.ENDED,
                        statusMessage = if (duration > 0) "Call Ended • ${it.formattedDuration}" else "Call Ended",
                        hasRemoteVideo = false,
                        hasLocalVideo = false
                    )
                }
                scheduleAutoDismiss(1500L)
            } else {
                _uiState.value = CallUiState()
            }
        }
    }

    fun dismissModal() {
        val current = _uiState.value
        if (current.state.isActiveSession) {
            endCall()
        } else {
            autoDismissJob?.cancel()
            _uiState.value = CallUiState()
        }
    }

    private fun beginOutgoingInvitation(partner: User, callType: CallType) {
        mainScope.launch {
            _uiState.update {
                it.copy(
                    state = CallState.OUTGOING_RINGING,
                    pendingPermissionNeed = PermissionNeed.NONE,
                    permissionRationaleVisible = false,
                    statusMessage = "Calling ${partner.name}..."
                )
            }

            val inviteRes = signaling.createCallInvitation(partner.id, callType)
            if (inviteRes.isFailure) {
                val err = inviteRes.exceptionOrNull()?.message ?: "Unable to start call"
                _uiState.update {
                    it.copy(
                        state = CallState.FAILED,
                        statusMessage = "Call Failed",
                        errorTitle = "Unable to Place Call",
                        errorDetail = err
                    )
                }
                return@launch
            }

            val session = inviteRes.getOrThrow()
            _uiState.update {
                it.copy(
                    callId = session.id,
                    roomName = session.roomName
                )
            }

            when (session.status) {
                "busy" -> {
                    _uiState.update {
                        it.copy(
                            state = CallState.BUSY,
                            statusMessage = "${partner.name} is busy",
                            errorTitle = "User Busy",
                            errorDetail = "${partner.name} is currently on another call."
                        )
                    }
                    scheduleAutoDismiss(2800L)
                    return@launch
                }
                "unavailable" -> {
                    _uiState.update {
                        it.copy(
                            state = CallState.UNAVAILABLE,
                            statusMessage = "${partner.name} is unavailable",
                            errorTitle = "Call Unavailable",
                            errorDetail = "${partner.name} is not accepting incoming calls right now."
                        )
                    }
                    scheduleAutoDismiss(2800L)
                    return@launch
                }
            }

            // Pre-validate LiveKit configuration & Free-Plan monthly allowance before ringing callee endlessly
            val tokenRes = signaling.requestLiveKitToken(session.id)
            if (tokenRes.isFailure) {
                handleTokenFailure(session.id, tokenRes.exceptionOrNull())
                return@launch
            }
            preFetchedTokenPayload = tokenRes.getOrNull()

            _uiState.update { it.copy(statusMessage = "Ringing ${partner.name}...") }

            // Notify callee via FCM push + Supabase Realtime call_sessions listener
            signaling.sendCallPushNotification(
                recipientId = partner.id,
                callId = session.id,
                callType = callType,
                notificationType = "incoming_call",
                previewText = "Incoming ${callType.label.lowercase()} from ${repository.currentUser.value.name}"
            )

            observeCallUpdatesAndFallback(session.id)
            startOutgoingRingTimeout(session.id, partner, callType)
        }
    }

    private fun proceedAcceptIncoming(invite: IncomingCallInvite) {
        mainScope.launch {
            _uiState.update {
                it.copy(
                    state = CallState.CONNECTING,
                    incomingInvite = null,
                    pendingPermissionNeed = PermissionNeed.NONE,
                    permissionRationaleVisible = false,
                    statusMessage = "Connecting...",
                    isCameraEnabled = invite.callType == CallType.VIDEO &&
                        permissionChecker.hasPermission(Manifest.permission.CAMERA)
                )
            }

            val acceptRes = signaling.updateCallSessionStatus(invite.callId, "accepted")
            if (acceptRes.isFailure) {
                cleanupMediaAndTimers()
                _uiState.update {
                    it.copy(
                        state = CallState.FAILED,
                        statusMessage = "Call No Longer Active",
                        errorTitle = "Missed Call",
                        errorDetail = "The caller hung up before the call could connect."
                    )
                }
                scheduleAutoDismiss(2500L)
                return@launch
            }

            val tokenRes = signaling.requestLiveKitToken(invite.callId)
            if (tokenRes.isFailure) {
                handleTokenFailure(invite.callId, tokenRes.exceptionOrNull())
                return@launch
            }

            connectToMediaRoom(tokenRes.getOrThrow(), invite.callType)
        }
    }

    private suspend fun connectToMediaRoom(tokenPayload: LiveKitTokenPayload, callType: CallType) {
        val current = _uiState.value
        val hasCam = permissionChecker.hasPermission(Manifest.permission.CAMERA)
        val wantCamera = callType == CallType.VIDEO && current.isCameraEnabled && hasCam

        _uiState.update {
            it.copy(
                state = CallState.CONNECTING,
                statusMessage = "Joining encrypted media room..."
            )
        }

        val client = try {
            mediaClientFactory()
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(
                    state = CallState.FAILED,
                    statusMessage = "Media Engine Error",
                    errorTitle = "Audio/Video Engine Unavailable",
                    errorDetail = t.message ?: "Unable to initialize LiveKit room"
                )
            }
            return
        }
        activeMediaClient = client

        mediaEventsJob?.cancel()
        mediaEventsJob = mainScope.launch {
            client.events.collect { ev ->
                handleMediaEvent(ev)
            }
        }

        mediaRoutesJob?.cancel()
        mediaRoutesJob = mainScope.launch {
            launch {
                client.availableAudioRoutes.collect { routes ->
                    _uiState.update { it.copy(availableAudioRoutes = routes) }
                }
            }
            launch {
                client.activeAudioRoute.collect { route ->
                    _uiState.update { it.copy(activeAudioRoute = route) }
                }
            }
        }

        mediaTracksJob?.cancel()
        mediaTracksJob = mainScope.launch {
            launch {
                client.localVideoTrack.collect { track ->
                    _uiState.update { it.copy(hasLocalVideo = track != null) }
                }
            }
            launch {
                client.remoteVideoTrack.collect { track ->
                    _uiState.update { it.copy(hasRemoteVideo = track != null) }
                }
            }
        }

        val connectRes = client.connect(
            wsUrl = tokenPayload.wsUrl,
            token = tokenPayload.token,
            enableMic = !current.isMuted,
            enableCamera = wantCamera,
            preferredSpeakerOn = (callType == CallType.VIDEO)
        )

        if (connectRes.isFailure) {
            val errMsg = connectRes.exceptionOrNull()?.message ?: "Failed to connect to LiveKit media server"
            val callId = _uiState.value.callId
            cleanupMediaAndTimers()
            if (callId != null) {
                signaling.updateCallSessionStatus(callId, "failed", endReason = "media_connect_error")
            }
            _uiState.update {
                it.copy(
                    state = CallState.FAILED,
                    statusMessage = "Connection Failed",
                    errorTitle = "Call Connection Failed",
                    errorDetail = errMsg
                )
            }
            return
        }

        ringTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                state = CallState.CONNECTED,
                statusMessage = "Connected",
                networkQuality = NetworkQualityLevel.GOOD,
                isPoorConnection = false,
                isCameraEnabled = wantCamera
            )
        }
        tokenPayload.callId.let { cid ->
            signaling.updateCallSessionStatus(cid, "connected")
        }
        startDurationTicker()
        updateForegroundServiceNotification()
    }

    private fun handleMediaEvent(event: CallMediaEvent) {
        when (event) {
            is CallMediaEvent.Connected -> {
                _uiState.update {
                    if (it.state == CallState.CONNECTING || it.state == CallState.RECONNECTING) {
                        it.copy(state = CallState.CONNECTED, statusMessage = "Connected", isPoorConnection = false)
                    } else it
                }
            }
            is CallMediaEvent.Reconnecting -> {
                _uiState.update {
                    if (it.state == CallState.CONNECTED || it.state == CallState.RECONNECTING) {
                        it.copy(
                            state = CallState.RECONNECTING,
                            isPoorConnection = true,
                            networkQuality = NetworkQualityLevel.LOST,
                            statusMessage = "Network interrupted — reconnecting..."
                        )
                    } else it
                }
                updateForegroundServiceNotification()
            }
            is CallMediaEvent.Reconnected -> {
                _uiState.update {
                    if (it.state == CallState.RECONNECTING || it.state == CallState.CONNECTING) {
                        it.copy(
                            state = CallState.CONNECTED,
                            isPoorConnection = false,
                            networkQuality = NetworkQualityLevel.GOOD,
                            statusMessage = "Connected"
                        )
                    } else it
                }
                updateForegroundServiceNotification()
            }
            is CallMediaEvent.ConnectionQualityUpdated -> {
                val poor = event.quality == NetworkQualityLevel.POOR || event.quality == NetworkQualityLevel.LOST
                _uiState.update {
                    it.copy(
                        networkQuality = event.quality,
                        isPoorConnection = poor || it.state == CallState.RECONNECTING
                    )
                }
            }
            is CallMediaEvent.RemoteParticipantLeft -> {
                if (_uiState.value.state == CallState.CONNECTED || _uiState.value.state == CallState.RECONNECTING) {
                    endCall("remote_left")
                }
            }
            is CallMediaEvent.Disconnected -> {
                if (_uiState.value.state.isActiveSession && _uiState.value.state != CallState.OUTGOING_RINGING) {
                    endCall(event.reason ?: "disconnected")
                }
            }
            is CallMediaEvent.MediaError -> {
                _uiState.update {
                    it.copy(
                        statusMessage = event.message,
                        isPoorConnection = true
                    )
                }
            }
        }
    }

    private suspend fun handleTokenFailure(callId: String, throwable: Throwable?) {
        cleanupMediaAndTimers()
        when (throwable) {
            is TokenFetchFailure.LiveKitNotConfigured -> {
                signaling.updateCallSessionStatus(callId, "failed", endReason = "livekit_not_configured")
                _uiState.update {
                    it.copy(
                        state = CallState.FAILED,
                        statusMessage = "LiveKit Not Configured",
                        errorTitle = "Voice/Video Server Setup Needed",
                        errorDetail = throwable.message
                            ?: "Set LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET in Supabase Edge Function secrets and deploy `livekit-token`."
                    )
                }
            }
            is TokenFetchFailure.FreeLimitReached -> {
                signaling.updateCallSessionStatus(callId, "failed", endReason = "free_limit_reached")
                _uiState.update {
                    it.copy(
                        state = CallState.LIMIT_REACHED,
                        statusMessage = "Free Plan Limit Reached",
                        errorTitle = "Monthly Free Allowance Reached",
                        errorDetail = throwable.message
                            ?: "Monthly 5,000 participant-minute allowance on LiveKit Build ($0) plan has been reached. Paid upgrades are disabled."
                    )
                }
            }
            is TokenFetchFailure.Unauthorized -> {
                signaling.updateCallSessionStatus(callId, "failed", endReason = "unauthorized")
                _uiState.update {
                    it.copy(
                        state = CallState.FAILED,
                        statusMessage = "Unauthorized",
                        errorTitle = "Call Not Authorized",
                        errorDetail = throwable.message ?: "You are not authorized to join this call."
                    )
                }
            }
            else -> {
                signaling.updateCallSessionStatus(callId, "failed", endReason = "token_error")
                _uiState.update {
                    it.copy(
                        state = CallState.FAILED,
                        statusMessage = "Call Error",
                        errorTitle = "Unable to Connect Call",
                        errorDetail = throwable?.message ?: "Please check your internet connection and try again."
                    )
                }
            }
        }
    }

    private suspend fun handleSignalingRecordForUser(currentUid: String, record: CallSessionRecord) {
        val current = _uiState.value
        if (record.calleeId == currentUid && record.status == "ringing" && current.callId == null) {
            val caller = resolveUser(record.callerId, null)
            receiveIncomingCall(
                IncomingCallInvite(
                    callId = record.id,
                    roomName = record.roomName,
                    callerId = record.callerId,
                    calleeId = record.calleeId,
                    caller = caller,
                    callType = record.callType
                )
            )
            return
        }

        if (current.callId == record.id) {
            onActiveCallRecordUpdated(record)
        }
    }

    private fun onActiveCallRecordUpdated(record: CallSessionRecord) {
        val current = _uiState.value
        if (current.callId != record.id) return

        when (record.status) {
            "accepted" -> {
                if (current.direction == CallDirection.OUTGOING && current.state == CallState.OUTGOING_RINGING) {
                    ringTimeoutJob?.cancel()
                    mainScope.launch {
                        val tokenPayload = preFetchedTokenPayload
                            ?: signaling.requestLiveKitToken(record.id).getOrElse { err ->
                                handleTokenFailure(record.id, err)
                                return@launch
                            }
                        preFetchedTokenPayload = null
                        connectToMediaRoom(tokenPayload, current.callType)
                    }
                }
            }
            "rejected" -> {
                stopRingtone()
                cleanupMediaAndTimers()
                _uiState.update {
                    it.copy(
                        state = CallState.REJECTED,
                        incomingInvite = null,
                        statusMessage = "Call Declined",
                        errorTitle = "Call Declined",
                        errorDetail = "${current.partner?.name ?: "Recipient"} declined the call."
                    )
                }
                scheduleAutoDismiss(2200L)
            }
            "missed" -> {
                stopRingtone()
                cleanupMediaAndTimers()
                _uiState.update {
                    it.copy(
                        state = CallState.MISSED,
                        incomingInvite = null,
                        statusMessage = "Missed Call",
                        errorTitle = "No Answer",
                        errorDetail = "${current.partner?.name ?: "User"} did not answer."
                    )
                }
                scheduleAutoDismiss(2200L)
            }
            "busy" -> {
                stopRingtone()
                cleanupMediaAndTimers()
                _uiState.update {
                    it.copy(
                        state = CallState.BUSY,
                        statusMessage = "User Busy",
                        errorTitle = "User Busy",
                        errorDetail = "${current.partner?.name ?: "User"} is on another call."
                    )
                }
                scheduleAutoDismiss(2200L)
            }
            "unavailable" -> {
                stopRingtone()
                cleanupMediaAndTimers()
                _uiState.update {
                    it.copy(
                        state = CallState.UNAVAILABLE,
                        statusMessage = "User Unavailable",
                        errorTitle = "Call Unavailable",
                        errorDetail = "${current.partner?.name ?: "User"} is not accepting calls."
                    )
                }
                scheduleAutoDismiss(2200L)
            }
            "ended" -> {
                if (current.state.isActiveSession) {
                    stopRingtone()
                    cleanupMediaAndTimers()
                    _uiState.update {
                        it.copy(
                            state = CallState.ENDED,
                            incomingInvite = null,
                            statusMessage = if (it.durationSeconds > 0) "Call Ended • ${it.formattedDuration}" else "Call Ended",
                            hasRemoteVideo = false,
                            hasLocalVideo = false
                        )
                    }
                    scheduleAutoDismiss(1500L)
                }
            }
            "failed" -> {
                if (current.state.isActiveSession) {
                    stopRingtone()
                    cleanupMediaAndTimers()
                    _uiState.update {
                        it.copy(
                            state = CallState.FAILED,
                            incomingInvite = null,
                            statusMessage = "Call Failed",
                            errorTitle = "Call Ended Unexpectedly",
                            errorDetail = record.endReason ?: "Connection could not be maintained."
                        )
                    }
                    scheduleAutoDismiss(2500L)
                }
            }
        }
    }

    private fun observeCallUpdatesAndFallback(callId: String) {
        signaling.observeActiveCallSession(callId) { updated ->
            mainScope.launch {
                onActiveCallRecordUpdated(updated)
            }
        }

        statusPollJob?.cancel()
        statusPollJob = mainScope.launch {
            while (isActive && _uiState.value.callId == callId && _uiState.value.state.isActiveSession) {
                delay(2500L)
                val stateNow = _uiState.value.state
                if (stateNow == CallState.OUTGOING_RINGING || stateNow == CallState.INCOMING_RINGING) {
                    val fresh = signaling.fetchCallSession(callId).getOrNull()
                    if (fresh != null) {
                        onActiveCallRecordUpdated(fresh)
                    }
                }
            }
        }
    }

    private fun startOutgoingRingTimeout(callId: String, partner: User, callType: CallType) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = mainScope.launch {
            delay(SupabaseConfig.CALL_RINGING_TIMEOUT_MS)
            val current = _uiState.value
            if (current.callId == callId && current.state == CallState.OUTGOING_RINGING) {
                cleanupMediaAndTimers()
                signaling.updateCallSessionStatus(callId, "missed", endReason = "no_answer_timeout")
                signaling.sendCallPushNotification(
                    recipientId = partner.id,
                    callId = callId,
                    callType = callType,
                    notificationType = "missed_call",
                    previewText = "Missed ${callType.label.lowercase()} from ${repository.currentUser.value.name}"
                )
                appendLocalCallLogMessage(partner.id, "Missed ${callType.label.lowercase()}")
                _uiState.update {
                    it.copy(
                        state = CallState.MISSED,
                        statusMessage = "No Answer",
                        errorTitle = "No Answer",
                        errorDetail = "${partner.name} did not answer."
                    )
                }
                scheduleAutoDismiss(2400L)
            }
        }
    }

    private fun startDurationTicker() {
        durationTickerJob?.cancel()
        durationTickerJob = mainScope.launch {
            while (isActive) {
                delay(1000L)
                val st = _uiState.value.state
                if (st == CallState.CONNECTED || st == CallState.RECONNECTING) {
                    _uiState.update { it.copy(durationSeconds = it.durationSeconds + 1) }
                    if (_uiState.value.durationSeconds % 5 == 0) {
                        updateForegroundServiceNotification()
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun scheduleAutoDismiss(delayMs: Long) {
        autoDismissJob?.cancel()
        autoDismissJob = mainScope.launch {
            delay(delayMs)
            if (_uiState.value.state.isTerminal) {
                _uiState.value = CallUiState()
            }
        }
    }

    private fun cleanupMediaAndTimers() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        statusPollJob?.cancel()
        statusPollJob = null
        durationTickerJob?.cancel()
        durationTickerJob = null
        mediaEventsJob?.cancel()
        mediaEventsJob = null
        mediaRoutesJob?.cancel()
        mediaRoutesJob = null
        mediaTracksJob?.cancel()
        mediaTracksJob = null
        preFetchedTokenPayload = null
        signaling.stopActiveCallObserver()

        val clientToRelease = activeMediaClient
        activeMediaClient = null
        if (clientToRelease != null) {
            mainScope.launch {
                runCatching { clientToRelease.disconnectAndRelease() }
            }
        }

        appContextProvider()?.let { ctx ->
            EchoCallForegroundService.stop(ctx)
        }
    }

    private fun updateForegroundServiceNotification() {
        val ctx = appContextProvider() ?: return
        val s = _uiState.value
        val partnerName = s.partner?.name ?: "EchoTalks"
        val statusText = when (s.state) {
            CallState.RECONNECTING -> "Reconnecting • ${s.formattedDuration}"
            CallState.CONNECTED -> "Connected • ${s.formattedDuration}"
            else -> s.statusMessage.ifBlank { s.callType.label }
        }
        EchoCallForegroundService.startOrUpdate(
            context = ctx,
            callId = s.callId,
            partnerName = partnerName,
            callType = s.callType,
            statusText = statusText
        )
    }

    private fun startIncomingRingtoneIfAllowed() {
        val ctx = appContextProvider() ?: return
        val settings = repository.settings.value
        if (settings.notificationVibration) {
            runCatching { EchoHapticManager.getInstance(ctx).performAlertReceived() }
        }
        if (settings.notificationSound) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(ctx, uri)
                activeRingtone = ringtone
                ringtone?.play()
            }
        }
    }

    private fun stopRingtone() {
        runCatching {
            activeRingtone?.stop()
        }.onFailure {
            Log.w(TAG, "Ringtone stop warning: ${it.message}")
        }
        activeRingtone = null
    }

    private suspend fun resolveUser(userId: String, fallbackName: String?): User {
        repository.users.value.firstOrNull { it.id == userId }?.let { return it }
        val client = SupabaseClientProvider.getClientOrNull()
        if (client != null && CallSecurityValidator.isUuid(userId)) {
            val fetched = runCatching {
                client.postgrest.from("profiles")
                    .select {
                        filter { eq("id", userId) }
                        limit(1)
                    }
                    .decodeSingleOrNull<ProfileDto>()
                    ?.toDomain()
            }.getOrNull()
            if (fetched != null) return fetched
        }
        val name = fallbackName?.takeIf { it.isNotBlank() } ?: "EchoTalker"
        return User(
            id = userId,
            name = name,
            handle = "@${name.lowercase().replace(Regex("[^a-z0-9_]"), "").take(10).ifBlank { "user" }}",
            country = "Global",
            countryCode = "GL",
            gender = "Non-Binary",
            age = 24,
            online = true,
            isFriend = true
        )
    }

    private fun appendLocalCallLogMessage(partnerId: String, text: String) {
        val myName = repository.currentUser.value.name
        repository.cacheIncomingMessage(
            partnerId,
            ChatMessage(
                id = "call-log-${System.currentTimeMillis()}",
                sender = myName,
                text = text,
                timestamp = "Just now",
                status = "delivered"
            )
        )
    }

    private fun missingPermissionsFor(callType: CallType): List<String> {
        val list = mutableListOf<String>()
        if (!permissionChecker.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            list += Manifest.permission.RECORD_AUDIO
        }
        if (callType == CallType.VIDEO && !permissionChecker.hasPermission(Manifest.permission.CAMERA)) {
            list += Manifest.permission.CAMERA
        }
        return list
    }

    private fun toPermissionNeed(missing: List<String>): PermissionNeed {
        val needMic = Manifest.permission.RECORD_AUDIO in missing
        val needCam = Manifest.permission.CAMERA in missing
        return when {
            needMic && needCam -> PermissionNeed.MICROPHONE_AND_CAMERA
            needMic -> PermissionNeed.MICROPHONE
            needCam -> PermissionNeed.CAMERA
            else -> PermissionNeed.NONE
        }
    }
}
