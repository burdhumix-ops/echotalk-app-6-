package co.znxdnd.echotalk.calling

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.AudioOptions
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoPreset169
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CallMediaEvent {
    data object Connected : CallMediaEvent()
    data object Reconnecting : CallMediaEvent()
    data object Reconnected : CallMediaEvent()
    data class Disconnected(val reason: String?) : CallMediaEvent()
    data object RemoteParticipantLeft : CallMediaEvent()
    data class ConnectionQualityUpdated(val quality: NetworkQualityLevel) : CallMediaEvent()
    data class MediaError(val message: String) : CallMediaEvent()
}

interface CallMediaClient {
    val events: SharedFlow<CallMediaEvent>
    val availableAudioRoutes: StateFlow<List<AudioOutputRoute>>
    val activeAudioRoute: StateFlow<AudioOutputRoute>
    val remoteVideoTrack: StateFlow<VideoTrack?>
    val localVideoTrack: StateFlow<VideoTrack?>

    suspend fun connect(
        wsUrl: String,
        token: String,
        enableMic: Boolean,
        enableCamera: Boolean,
        preferredSpeakerOn: Boolean
    ): Result<Unit>

    suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit>
    suspend fun setCameraEnabled(enabled: Boolean, frontCamera: Boolean = true): Result<Unit>
    suspend fun switchCamera(frontCamera: Boolean): Result<Unit>
    fun selectAudioRoute(route: AudioOutputRoute)
    fun initRenderer(renderer: TextureViewRenderer)
    suspend fun disconnectAndRelease()
}

/**
 * Production LiveKit Cloud WebRTC client optimized for ordinary & lower-powered Android phones:
 * - AdaptiveStream + Dynacast so only visible, needed resolution layers are received
 * - 540p capture preset (VideoPreset169.H540) to keep thermals, CPU, and bandwidth low
 * - Hardware-friendly TextureViewRenderer initialization
 * - Explicit AudioSwitchHandler routing across Bluetooth, Wired Headset, Speakerphone, and Earpiece
 */
class LiveKitRoomCallClient(
    private val appContext: Context
) : CallMediaClient {

    companion object {
        private const val TAG = "LiveKitRoomCallClient"
    }

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<CallMediaEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<CallMediaEvent> = _events.asSharedFlow()

    private val _availableAudioRoutes = MutableStateFlow(
        listOf(AudioOutputRoute.EARPIECE, AudioOutputRoute.SPEAKERPHONE)
    )
    override val availableAudioRoutes: StateFlow<List<AudioOutputRoute>> = _availableAudioRoutes.asStateFlow()

    private val _activeAudioRoute = MutableStateFlow(AudioOutputRoute.EARPIECE)
    override val activeAudioRoute: StateFlow<AudioOutputRoute> = _activeAudioRoute.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    override val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    override val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private var room: Room? = null
    private var audioHandler: AudioSwitchHandler? = null
    private var eventJob: Job? = null
    private var isFront: Boolean = true

    override suspend fun connect(
        wsUrl: String,
        token: String,
        enableMic: Boolean,
        enableCamera: Boolean,
        preferredSpeakerOn: Boolean
    ): Result<Unit> {
        disconnectAndRelease()

        return try {
            val handler = AudioSwitchHandler(appContext).apply {
                preferredDeviceList = if (preferredSpeakerOn) {
                    listOf(
                        AudioDevice.BluetoothHeadset::class.java,
                        AudioDevice.WiredHeadset::class.java,
                        AudioDevice.Speakerphone::class.java,
                        AudioDevice.Earpiece::class.java
                    )
                } else {
                    listOf(
                        AudioDevice.BluetoothHeadset::class.java,
                        AudioDevice.WiredHeadset::class.java,
                        AudioDevice.Earpiece::class.java,
                        AudioDevice.Speakerphone::class.java
                    )
                }
                audioDeviceChangeListener = { devices, selected ->
                    syncAudioDeviceState(devices, selected)
                }
            }
            audioHandler = handler

            val roomOptions = RoomOptions(
                adaptiveStream = true,
                dynacast = true,
                videoTrackCaptureDefaults = LocalVideoTrackOptions(
                    position = CameraPosition.FRONT,
                    captureParams = VideoPreset169.H540.capture
                )
            )
            val createdRoom = LiveKit.create(
                appContext = appContext,
                options = roomOptions,
                overrides = LiveKitOverrides(
                    audioOptions = AudioOptions(
                        audioHandler = handler
                    )
                )
            )
            room = createdRoom
            isFront = true

            eventJob = clientScope.launch {
                createdRoom.events.collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            _events.tryEmit(CallMediaEvent.Connected)
                            refreshTracksFromRoom(createdRoom)
                        }
                        is RoomEvent.Reconnecting -> {
                            _events.tryEmit(CallMediaEvent.Reconnecting)
                        }
                        is RoomEvent.Reconnected -> {
                            _events.tryEmit(CallMediaEvent.Reconnected)
                            refreshTracksFromRoom(createdRoom)
                        }
                        is RoomEvent.Disconnected -> {
                            _remoteVideoTrack.value = null
                            _localVideoTrack.value = null
                            _events.tryEmit(CallMediaEvent.Disconnected(event.reason?.name))
                        }
                        is RoomEvent.TrackSubscribed -> {
                            if (event.track is VideoTrack) {
                                _remoteVideoTrack.value = event.track as VideoTrack
                            }
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            if (event.track == _remoteVideoTrack.value) {
                                _remoteVideoTrack.value = null
                            }
                        }
                        is RoomEvent.TrackPublished -> {
                            refreshTracksFromRoom(createdRoom)
                        }
                        is RoomEvent.TrackUnpublished -> {
                            refreshTracksFromRoom(createdRoom)
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            if (createdRoom.remoteParticipants.isEmpty()) {
                                _remoteVideoTrack.value = null
                                _events.tryEmit(CallMediaEvent.RemoteParticipantLeft)
                            }
                        }
                        is RoomEvent.ConnectionQualityChanged -> {
                            if (event.participant == createdRoom.localParticipant || createdRoom.remoteParticipants.containsValue(event.participant)) {
                                _events.tryEmit(
                                    CallMediaEvent.ConnectionQualityUpdated(mapQuality(event.quality))
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            createdRoom.connect(
                url = wsUrl,
                token = token,
                options = ConnectOptions(
                    audio = enableMic,
                    video = enableCamera
                )
            )

            createdRoom.localParticipant.setMicrophoneEnabled(enableMic)
            if (enableCamera) {
                createdRoom.localParticipant.setCameraEnabled(true)
            }
            refreshTracksFromRoom(createdRoom)
            if (preferredSpeakerOn) {
                selectAudioRoute(AudioOutputRoute.SPEAKERPHONE)
            } else {
                syncAudioDeviceState(handler.availableAudioDevices, handler.selectedAudioDevice)
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "LiveKit connect failure: ${t.message}", t)
            disconnectAndRelease()
            Result.failure(t)
        }
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
        val activeRoom = room ?: return Result.failure(IllegalStateException("Call is not connected"))
        return runCatching {
            activeRoom.localParticipant.setMicrophoneEnabled(enabled)
            Unit
        }
    }

    override suspend fun setCameraEnabled(enabled: Boolean, frontCamera: Boolean): Result<Unit> {
        val activeRoom = room ?: return Result.failure(IllegalStateException("Call is not connected"))
        return runCatching {
            isFront = frontCamera
            activeRoom.localParticipant.setCameraEnabled(enabled)
            refreshTracksFromRoom(activeRoom)
            if (!enabled) {
                _localVideoTrack.value = null
            }
            Unit
        }
    }

    override suspend fun switchCamera(frontCamera: Boolean): Result<Unit> {
        val activeRoom = room ?: return Result.failure(IllegalStateException("Call is not connected"))
        return runCatching {
            isFront = frontCamera
            val pub = activeRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)
            val localTrack = pub?.track as? LocalVideoTrack
            localTrack?.switchCamera(
                position = if (frontCamera) CameraPosition.FRONT else CameraPosition.BACK
            )
            Unit
        }
    }

    override fun selectAudioRoute(route: AudioOutputRoute) {
        val handler = audioHandler
        if (handler != null) {
            val match = handler.availableAudioDevices.firstOrNull { device ->
                mapAudioDevice(device) == route
            }
            if (match != null) {
                handler.selectDevice(match)
                _activeAudioRoute.value = route
                return
            }
        }
        // Fallback via Android AudioManager when AudioSwitch device list isn't populated
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = when (route) {
                AudioOutputRoute.SPEAKERPHONE -> android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                AudioOutputRoute.EARPIECE -> android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                AudioOutputRoute.BLUETOOTH -> android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                AudioOutputRoute.WIRED_HEADSET -> android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            val deviceInfo = am.availableCommunicationDevices.firstOrNull { it.type == targetType }
            if (deviceInfo != null) {
                am.setCommunicationDevice(deviceInfo)
            }
        } else {
            am.isSpeakerphoneOn = (route == AudioOutputRoute.SPEAKERPHONE)
        }
        _activeAudioRoute.value = route
    }

    override fun initRenderer(renderer: TextureViewRenderer) {
        val activeRoom = room ?: return
        runCatching {
            activeRoom.initVideoRenderer(renderer)
        }
    }

    override suspend fun disconnectAndRelease() {
        eventJob?.cancel()
        eventJob = null

        _remoteVideoTrack.value = null
        _localVideoTrack.value = null

        val activeRoom = room
        room = null
        if (activeRoom != null) {
            runCatching { activeRoom.localParticipant.setCameraEnabled(false) }
            runCatching { activeRoom.localParticipant.setMicrophoneEnabled(false) }
            runCatching { activeRoom.disconnect() }
            runCatching { activeRoom.release() }
        }

        val handler = audioHandler
        audioHandler = null
        runCatching {
            handler?.audioDeviceChangeListener = null
            handler?.stop()
        }

        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        runCatching {
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    private fun refreshTracksFromRoom(activeRoom: Room) {
        val localPub = activeRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)
        _localVideoTrack.value = (localPub?.track as? VideoTrack)?.takeIf { !localPub.muted }

        val firstRemoteTrack = activeRoom.remoteParticipants.values
            .firstOrNull()
            ?.getTrackPublication(Track.Source.CAMERA)
            ?.let { pub -> if (!pub.muted) pub.track as? VideoTrack else null }
        _remoteVideoTrack.value = firstRemoteTrack
    }

    private fun syncAudioDeviceState(devices: List<AudioDevice>, selected: AudioDevice?) {
        val mapped = devices.map(::mapAudioDevice).distinct()
            .ifEmpty { listOf(AudioOutputRoute.EARPIECE, AudioOutputRoute.SPEAKERPHONE) }
        _availableAudioRoutes.value = mapped
        if (selected != null) {
            _activeAudioRoute.value = mapAudioDevice(selected)
        }
    }

    private fun mapAudioDevice(device: AudioDevice): AudioOutputRoute {
        return when (device) {
            is AudioDevice.BluetoothHeadset -> AudioOutputRoute.BLUETOOTH
            is AudioDevice.WiredHeadset -> AudioOutputRoute.WIRED_HEADSET
            is AudioDevice.Speakerphone -> AudioOutputRoute.SPEAKERPHONE
            else -> AudioOutputRoute.EARPIECE
        }
    }

    private fun mapQuality(quality: ConnectionQuality): NetworkQualityLevel {
        return when (quality) {
            ConnectionQuality.EXCELLENT -> NetworkQualityLevel.EXCELLENT
            ConnectionQuality.GOOD -> NetworkQualityLevel.GOOD
            ConnectionQuality.POOR -> NetworkQualityLevel.POOR
            ConnectionQuality.LOST -> NetworkQualityLevel.LOST
            ConnectionQuality.UNKNOWN -> NetworkQualityLevel.UNKNOWN
        }
    }
}
