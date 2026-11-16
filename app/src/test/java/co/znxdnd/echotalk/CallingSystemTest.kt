package co.znxdnd.echotalk

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import co.znxdnd.echotalk.calling.AudioOutputRoute
import co.znxdnd.echotalk.calling.CallController
import co.znxdnd.echotalk.calling.CallDirection
import co.znxdnd.echotalk.calling.CallMediaClient
import co.znxdnd.echotalk.calling.CallMediaEvent
import co.znxdnd.echotalk.calling.CallPermissionChecker
import co.znxdnd.echotalk.calling.CallSecurityValidator
import co.znxdnd.echotalk.calling.CallSessionRecord
import co.znxdnd.echotalk.calling.CallSignalingGateway
import co.znxdnd.echotalk.calling.CallState
import co.znxdnd.echotalk.calling.CallType
import co.znxdnd.echotalk.calling.CallUiState
import co.znxdnd.echotalk.calling.IncomingCallInvite
import co.znxdnd.echotalk.calling.LiveKitTokenPayload
import co.znxdnd.echotalk.calling.NetworkQualityLevel
import co.znxdnd.echotalk.calling.PermissionNeed
import co.znxdnd.echotalk.calling.TokenFetchFailure
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.ui.components.CallModalDialog
import co.znxdnd.echotalk.ui.components.IncomingCallOverlay
import co.znxdnd.echotalk.ui.theme.EchoTalkTheme
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallingSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCallSignalingGateway : CallSignalingGateway {
        var uid: String? = "11111111-1111-1111-1111-111111111111"
        var invitationResultStatus: String = "ringing"
        var tokenResult: Result<LiveKitTokenPayload> = Result.success(
            LiveKitTokenPayload(
                token = "jwt.test.token",
                wsUrl = "wss://test.livekit.cloud",
                roomName = "echocall_test_room",
                callId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            )
        )
        var createCount = 0
        val statusUpdates = mutableListOf<Pair<String, String>>()
        val sentPushes = mutableListOf<String>()
        private var onActiveCallUpdate: ((CallSessionRecord) -> Unit)? = null

        override fun currentUserId(): String? = uid

        override suspend fun createCallInvitation(calleeId: String, callType: CallType): Result<CallSessionRecord> {
            createCount++
            return Result.success(
                CallSessionRecord(
                    id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    roomName = "echocall_test_room",
                    callerId = uid ?: "",
                    calleeId = calleeId,
                    callType = callType,
                    status = invitationResultStatus
                )
            )
        }

        override suspend fun fetchCallSession(callId: String): Result<CallSessionRecord?> = Result.success(null)

        override suspend fun updateCallSessionStatus(
            callId: String,
            status: String,
            endReason: String?,
            durationSeconds: Int?
        ): Result<Unit> {
            statusUpdates += callId to status
            return Result.success(Unit)
        }

        override suspend fun requestLiveKitToken(callId: String): Result<LiveKitTokenPayload> = tokenResult

        override suspend fun sendCallPushNotification(
            recipientId: String,
            callId: String,
            callType: CallType,
            notificationType: String,
            previewText: String
        ) {
            sentPushes += "$notificationType:$callId"
        }

        override fun observeIncomingCallsForUser(userId: String, onCallEvent: (CallSessionRecord) -> Unit) {}

        override fun observeActiveCallSession(callId: String, onCallUpdated: (CallSessionRecord) -> Unit) {
            onActiveCallUpdate = onCallUpdated
        }

        fun emitActiveCallUpdate(record: CallSessionRecord) {
            onActiveCallUpdate?.invoke(record)
        }

        override fun stopActiveCallObserver() {
            onActiveCallUpdate = null
        }

        override fun stopIncomingCallObserver() {}
    }

    private class FakeCallMediaClient : CallMediaClient {
        private val _events = MutableSharedFlow<CallMediaEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<CallMediaEvent> = _events.asSharedFlow()

        private val _availableAudioRoutes = MutableStateFlow(
            listOf(AudioOutputRoute.EARPIECE, AudioOutputRoute.SPEAKERPHONE, AudioOutputRoute.BLUETOOTH)
        )
        override val availableAudioRoutes: StateFlow<List<AudioOutputRoute>> = _availableAudioRoutes.asStateFlow()

        private val _activeAudioRoute = MutableStateFlow(AudioOutputRoute.EARPIECE)
        override val activeAudioRoute: StateFlow<AudioOutputRoute> = _activeAudioRoute.asStateFlow()

        private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
        override val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

        private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
        override val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

        var connected = false
        var released = false
        var lastMicEnabled: Boolean? = null
        var lastCameraEnabled: Boolean? = null
        var lastFrontCamera: Boolean = true

        override suspend fun connect(
            wsUrl: String,
            token: String,
            enableMic: Boolean,
            enableCamera: Boolean,
            preferredSpeakerOn: Boolean
        ): Result<Unit> {
            connected = true
            released = false
            lastMicEnabled = enableMic
            lastCameraEnabled = enableCamera
            _activeAudioRoute.value = if (preferredSpeakerOn) AudioOutputRoute.SPEAKERPHONE else AudioOutputRoute.EARPIECE
            return Result.success(Unit)
        }

        override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
            lastMicEnabled = enabled
            return Result.success(Unit)
        }

        override suspend fun setCameraEnabled(enabled: Boolean, frontCamera: Boolean): Result<Unit> {
            lastCameraEnabled = enabled
            lastFrontCamera = frontCamera
            return Result.success(Unit)
        }

        override suspend fun switchCamera(frontCamera: Boolean): Result<Unit> {
            lastFrontCamera = frontCamera
            return Result.success(Unit)
        }

        override fun selectAudioRoute(route: AudioOutputRoute) {
            _activeAudioRoute.value = route
        }

        override fun initRenderer(renderer: TextureViewRenderer) {}

        override suspend fun disconnectAndRelease() {
            connected = false
            released = true
        }

        fun emitEvent(ev: CallMediaEvent) {
            _events.tryEmit(ev)
        }
    }

    private fun testPartner(id: String = "22222222-2222-2222-2222-222222222222"): User = User(
        id = id,
        name = "Lucas Silva",
        handle = "@lucas",
        country = "Brazil",
        countryCode = "BR",
        gender = "Male",
        age = 26,
        online = true,
        isFriend = true
    )

    @Test
    fun `outgoing voice call connects after callee accepts, toggles mute and audio route, and releases resources on end`() = runTest {
        val signaling = FakeCallSignalingGateway()
        val media = FakeCallMediaClient()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repo = EchoTalkRepository.getInstance()
        val controller = CallController.createForTesting(
            signaling = signaling,
            mediaClient = media,
            permissionChecker = CallPermissionChecker { true },
            repository = repo,
            scope = scope
        )

        val partner = testPartner()
        controller.startOutgoingCall(partner, CallType.AUDIO)
        advanceUntilIdle()

        assertEquals(CallState.OUTGOING_RINGING, controller.uiState.value.state)
        assertTrue(signaling.sentPushes.any { it.startsWith("incoming_call:") })
        assertFalse(media.connected) // Microphone/LiveKit not joined while merely ringing

        // Callee accepts
        signaling.emitActiveCallUpdate(
            CallSessionRecord(
                id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                roomName = "echocall_test_room",
                callerId = "11111111-1111-1111-1111-111111111111",
                calleeId = partner.id,
                callType = CallType.AUDIO,
                status = "accepted"
            )
        )
        advanceUntilIdle()

        assertEquals(CallState.CONNECTED, controller.uiState.value.state)
        assertTrue(media.connected)
        assertEquals(true, media.lastMicEnabled)
        assertEquals(false, media.lastCameraEnabled)

        // Mute / unmute microphone
        controller.toggleMute()
        advanceUntilIdle()
        assertTrue(controller.uiState.value.isMuted)
        assertEquals(false, media.lastMicEnabled)

        // Audio output routing
        controller.selectAudioRoute(AudioOutputRoute.BLUETOOTH)
        assertEquals(AudioOutputRoute.BLUETOOTH, controller.uiState.value.activeAudioRoute)

        // Network interruption & automatic recovery
        media.emitEvent(CallMediaEvent.Reconnecting)
        advanceUntilIdle()
        assertEquals(CallState.RECONNECTING, controller.uiState.value.state)
        assertTrue(controller.uiState.value.isPoorConnection)

        media.emitEvent(CallMediaEvent.Reconnected)
        advanceUntilIdle()
        assertEquals(CallState.CONNECTED, controller.uiState.value.state)
        assertFalse(controller.uiState.value.isPoorConnection)

        // End call releases hardware & updates signaling
        controller.endCall()
        advanceUntilIdle()
        assertEquals(CallState.ENDED, controller.uiState.value.state)
        assertTrue(media.released)
        assertTrue(signaling.statusUpdates.any { it.second == "ended" })

        scope.cancel()
    }

    @Test
    fun `video call connects with camera enabled, switches front-back camera, and prevents duplicate sessions`() = runTest {
        val signaling = FakeCallSignalingGateway()
        val media = FakeCallMediaClient()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val controller = CallController.createForTesting(
            signaling = signaling,
            mediaClient = media,
            permissionChecker = CallPermissionChecker { true },
            scope = scope
        )

        val partner = testPartner()
        controller.startOutgoingCall(partner, CallType.VIDEO)
        advanceUntilIdle()

        // Duplicate call trigger while already active must be ignored cleanly
        controller.startOutgoingCall(partner, CallType.AUDIO)
        advanceUntilIdle()
        assertEquals(1, signaling.createCount)
        assertEquals(CallType.VIDEO, controller.uiState.value.callType)

        signaling.emitActiveCallUpdate(
            CallSessionRecord(
                id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                roomName = "echocall_test_room",
                callerId = "11111111-1111-1111-1111-111111111111",
                calleeId = partner.id,
                callType = CallType.VIDEO,
                status = "accepted"
            )
        )
        advanceUntilIdle()

        assertEquals(CallState.CONNECTED, controller.uiState.value.state)
        assertTrue(controller.uiState.value.isCameraEnabled)
        assertTrue(controller.uiState.value.isFrontCamera)
        assertEquals(AudioOutputRoute.SPEAKERPHONE, controller.uiState.value.activeAudioRoute)

        controller.flipCamera()
        advanceUntilIdle()
        assertFalse(controller.uiState.value.isFrontCamera)
        assertFalse(media.lastFrontCamera)

        controller.toggleCamera()
        advanceUntilIdle()
        assertFalse(controller.uiState.value.isCameraEnabled)
        assertEquals(false, media.lastCameraEnabled)

        controller.endCall()
        advanceUntilIdle()
        assertTrue(media.released)
        scope.cancel()
    }

    @Test
    fun `incoming call can be accepted or rejected cleanly`() = runTest {
        val signaling = FakeCallSignalingGateway()
        val media = FakeCallMediaClient()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val controller = CallController.createForTesting(
            signaling = signaling,
            mediaClient = media,
            permissionChecker = CallPermissionChecker { true },
            scope = scope
        )

        val invite = IncomingCallInvite(
            callId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            roomName = "echocall_incoming",
            callerId = "22222222-2222-2222-2222-222222222222",
            calleeId = "11111111-1111-1111-1111-111111111111",
            caller = testPartner(),
            callType = CallType.AUDIO
        )

        // Test Reject
        controller.receiveIncomingCall(invite)
        advanceUntilIdle()
        assertTrue(controller.uiState.value.isIncomingVisible)
        controller.rejectIncomingCall()
        advanceUntilIdle()
        assertEquals(CallState.IDLE, controller.uiState.value.state)
        assertTrue(signaling.statusUpdates.contains("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb" to "rejected"))

        // Test Accept
        controller.receiveIncomingCall(invite)
        advanceUntilIdle()
        controller.acceptIncomingCall()
        advanceUntilIdle()
        assertEquals(CallState.CONNECTED, controller.uiState.value.state)
        assertTrue(media.connected)
        assertTrue(signaling.statusUpdates.contains("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb" to "accepted"))

        controller.endCall()
        advanceUntilIdle()
        scope.cancel()
    }

    @Test
    fun `permissions requested only when missing, remembered when granted, and rationale shown when revoked`() = runTest {
        var micGranted = false
        val signaling = FakeCallSignalingGateway()
        val media = FakeCallMediaClient()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val controller = CallController.createForTesting(
            signaling = signaling,
            mediaClient = media,
            permissionChecker = CallPermissionChecker { perm ->
                when (perm) {
                    Manifest.permission.RECORD_AUDIO -> micGranted
                    else -> true
                }
            },
            scope = scope
        )

        controller.startOutgoingCall(testPartner(), CallType.AUDIO)
        advanceUntilIdle()

        // First call requests microphone only and does not start signaling yet
        assertEquals(CallState.REQUESTING_PERMISSIONS, controller.uiState.value.state)
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), controller.permissionPromptTrigger.value)
        assertEquals(0, signaling.createCount)

        // Denied permanently -> explains how to restore in Settings
        controller.onPermissionsResult(
            grantResults = mapOf(Manifest.permission.RECORD_AUDIO to false),
            permanentlyDeniedMap = mapOf(Manifest.permission.RECORD_AUDIO to true)
        )
        advanceUntilIdle()
        assertTrue(controller.uiState.value.permissionRationaleVisible)
        assertTrue(controller.uiState.value.permissionPermanentlyDenied)

        // Once permission is granted -> proceeds automatically and never re-prompts on subsequent calls
        micGranted = true
        controller.onPermissionsResult(
            grantResults = mapOf(Manifest.permission.RECORD_AUDIO to true),
            permanentlyDeniedMap = emptyMap()
        )
        advanceUntilIdle()
        assertEquals(CallState.OUTGOING_RINGING, controller.uiState.value.state)
        assertEquals(1, signaling.createCount)

        controller.endCall()
        advanceUntilIdle()

        // Second call starts immediately without permission prompt because OS remembers grant
        controller.startOutgoingCall(testPartner(), CallType.AUDIO)
        advanceUntilIdle()
        assertNull(controller.permissionPromptTrigger.value)
        assertEquals(CallState.OUTGOING_RINGING, controller.uiState.value.state)

        controller.endCall()
        advanceUntilIdle()
        scope.cancel()
    }

    @Test
    fun `busy, unavailable, unconfigured LiveKit, and free-limit errors never fake connected state`() = runTest {
        val signaling = FakeCallSignalingGateway()
        val media = FakeCallMediaClient()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val controller = CallController.createForTesting(
            signaling = signaling,
            mediaClient = media,
            permissionChecker = CallPermissionChecker { true },
            scope = scope
        )

        // 1. Busy
        signaling.invitationResultStatus = "busy"
        controller.startOutgoingCall(testPartner(), CallType.AUDIO)
        advanceUntilIdle()
        assertEquals(CallState.BUSY, controller.uiState.value.state)
        controller.dismissModal()

        // 2. Unavailable
        signaling.invitationResultStatus = "unavailable"
        controller.startOutgoingCall(testPartner(), CallType.AUDIO)
        advanceUntilIdle()
        assertEquals(CallState.UNAVAILABLE, controller.uiState.value.state)
        controller.dismissModal()

        // 3. Unconfigured LiveKit server secrets
        signaling.invitationResultStatus = "ringing"
        signaling.tokenResult = Result.failure(
            TokenFetchFailure.LiveKitNotConfigured("Missing LIVEKIT_API_SECRET")
        )
        controller.startOutgoingCall(testPartner(), CallType.AUDIO)
        advanceUntilIdle()
        assertEquals(CallState.FAILED, controller.uiState.value.state)
        assertFalse(media.connected)
        controller.dismissModal()

        // 4. Free Build Plan Monthly Limit Reached
        signaling.tokenResult = Result.failure(
            TokenFetchFailure.FreeLimitReached("5,000 participant-minutes reached")
        )
        controller.startOutgoingCall(testPartner(), CallType.VIDEO)
        advanceUntilIdle()
        assertEquals(CallState.LIMIT_REACHED, controller.uiState.value.state)
        assertFalse(media.connected)

        controller.dismissModal()
        scope.cancel()
    }

    @Test
    fun `unauthorized third party cannot join another call session`() {
        val session = CallSessionRecord(
            id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            roomName = "echocall_private",
            callerId = "alice-uid",
            calleeId = "bob-uid",
            callType = CallType.VIDEO,
            status = "ringing"
        )
        assertTrue(CallSecurityValidator.canJoinCallSession("alice-uid", session))
        assertTrue(CallSecurityValidator.canJoinCallSession("bob-uid", session))
        assertFalse(CallSecurityValidator.canJoinCallSession("eve-attacker-uid", session))
        assertFalse(CallSecurityValidator.canJoinCallSession(null, session))

        val terminated = session.copy(status = "ended")
        assertFalse(CallSecurityValidator.canJoinCallSession("alice-uid", terminated))
        assertFalse(CallSecurityValidator.isValidTransition("ended", "connected"))
    }

    @Test
    fun `IncomingCallOverlay and CallModalDialog render interactive controls and error states`() {
        val partner = testPartner()
        var accepted = false
        var rejected = false
        var muted = false
        var ended = false

        composeRule.setContent {
            EchoTalkTheme {
                IncomingCallOverlay(
                    invite = IncomingCallInvite(
                        callId = "call-ui-1",
                        roomName = "room-1",
                        callerId = partner.id,
                        calleeId = "me",
                        caller = partner,
                        callType = CallType.VIDEO
                    ),
                    onAccept = { accepted = true },
                    onReject = { rejected = true }
                )
            }
        }

        composeRule.onNodeWithTag("incoming_call_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Incoming Video Call").assertIsDisplayed()
        composeRule.onNodeWithTag("incoming_call_accept_button").performClick()
        assertTrue(accepted)
        composeRule.onNodeWithTag("incoming_call_reject_button").performClick()
        assertTrue(rejected)

        val stateFlow = MutableStateFlow(
            CallUiState(
                callId = "call-ui-2",
                partner = partner,
                callType = CallType.AUDIO,
                direction = CallDirection.OUTGOING,
                state = CallState.RECONNECTING,
                isPoorConnection = true,
                networkQuality = NetworkQualityLevel.POOR,
                durationSeconds = 42,
                statusMessage = "Reconnecting"
            )
        )

        composeRule.setContent {
            EchoTalkTheme {
                CallModalDialog(
                    uiState = stateFlow.value,
                    remoteVideoTrack = null,
                    localVideoTrack = null,
                    onInitRenderer = {},
                    onToggleMute = { muted = !muted },
                    onToggleCamera = {},
                    onFlipCamera = {},
                    onSelectAudioRoute = {},
                    onRetryPermission = {},
                    onDismissPermissionRationale = {},
                    onEndCall = { ended = true },
                    onDismissTerminal = {}
                )
            }
        }

        composeRule.onNodeWithTag("call_modal_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("call_poor_network_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("call_mute_button").performClick()
        assertTrue(muted)
        composeRule.onNodeWithTag("call_end_button").performClick()
        assertTrue(ended)
    }
}
