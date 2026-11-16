package co.znxdnd.echotalk.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import co.znxdnd.echotalk.calling.AudioOutputRoute
import co.znxdnd.echotalk.calling.CallState
import co.znxdnd.echotalk.calling.CallType
import co.znxdnd.echotalk.calling.CallUiState
import co.znxdnd.echotalk.calling.NetworkQualityLevel
import co.znxdnd.echotalk.calling.PermissionNeed
import co.znxdnd.echotalk.utils.EchoHapticManager
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack

@Composable
fun CallModalDialog(
    uiState: CallUiState,
    remoteVideoTrack: VideoTrack?,
    localVideoTrack: VideoTrack?,
    onInitRenderer: (TextureViewRenderer) -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onSelectAudioRoute: (AudioOutputRoute) -> Unit,
    onRetryPermission: () -> Unit,
    onDismissPermissionRationale: () -> Unit,
    onEndCall: () -> Unit,
    onDismissTerminal: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }
    val partner = uiState.partner ?: return

    var showAudioMenu by remember { mutableStateOf(false) }

    val statusDotColor = when (uiState.state) {
        CallState.CONNECTED -> Color(0xFF10B981)
        CallState.RECONNECTING, CallState.CONNECTING, CallState.OUTGOING_RINGING -> Color(0xFFF59E0B)
        CallState.ENDED -> Color(0xFF94A3B8)
        else -> Color(0xFFE11D48)
    }

    val headerStatusText = remember(uiState.state, uiState.statusMessage, uiState.durationSeconds) {
        when (uiState.state) {
            CallState.CONNECTED -> "Connected • ${uiState.formattedDuration}"
            CallState.RECONNECTING -> "Reconnecting • ${uiState.formattedDuration}"
            else -> uiState.statusMessage.ifBlank { uiState.callType.label }
        }
    }

    Dialog(
        onDismissRequest = {
            if (uiState.state.isTerminal) onDismissTerminal() else onEndCall()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("call_modal_dialog"),
            color = Color(0xFF020617)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Call Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Text(
                                text = headerStatusText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.testTag("call_header_status")
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x336366F1),
                            border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (uiState.isPoorConnection) Color(0xFFF59E0B) else Color(0xFF10B981),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (uiState.state == CallState.CONNECTED) uiState.networkQuality.label else "LiveKit WebRTC",
                                    fontSize = 10.sp,
                                    color = Color(0xFF818CF8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Poor connectivity / Reconnecting banner
                    if (uiState.isPoorConnection || uiState.state == CallState.RECONNECTING) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_poor_network_banner"),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x33F59E0B),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SignalWifiStatusbarConnectedNoInternet4,
                                    contentDescription = "Poor internet connectivity",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (uiState.state == CallState.RECONNECTING) {
                                        "Connection interrupted. Attempting automatic recovery..."
                                    } else {
                                        "Poor internet connection — adapting video/audio quality"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFEF3C7)
                                )
                            }
                        }
                    }
                }

                // Center Stage: Error / Permission Card / Video Stream / Voice Stage
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 84.dp, bottom = 128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        uiState.state.isTerminal && uiState.state != CallState.ENDED -> {
                            TerminalCallStatusCard(
                                title = uiState.errorTitle ?: uiState.statusMessage,
                                detail = uiState.errorDetail ?: "The call could not continue.",
                                isLimitReached = uiState.state == CallState.LIMIT_REACHED,
                                onClose = onDismissTerminal
                            )
                        }
                        uiState.state == CallState.REQUESTING_PERMISSIONS || uiState.permissionRationaleVisible -> {
                            PermissionRationaleCard(
                                need = uiState.pendingPermissionNeed,
                                permanentlyDenied = uiState.permissionPermanentlyDenied,
                                isCallAlreadyActive = uiState.state == CallState.CONNECTED || uiState.state == CallState.CONNECTING,
                                onGrantClick = onRetryPermission,
                                onOpenSettingsClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                onCancel = {
                                    if (uiState.state == CallState.CONNECTED || uiState.state == CallState.CONNECTING) {
                                        onDismissPermissionRationale()
                                    } else {
                                        onEndCall()
                                    }
                                }
                            )
                        }
                        uiState.hasRemoteVideo && remoteVideoTrack != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.94f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(2.dp, Color(0xFF818CF8).copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                            ) {
                                LiveKitVideoView(
                                    track = remoteVideoTrack,
                                    mirror = false,
                                    onInitRenderer = onInitRenderer,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = partner.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            // Voice Call or Camera-off Stage
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Box(modifier = Modifier.size(124.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color(0xFF6366F1)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (partner.avatarUrl.isNotBlank()) {
                                            SubcomposeAsyncImage(
                                                model = partner.avatarUrl,
                                                contentDescription = partner.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                                error = {
                                                    Text(
                                                        text = partner.name.take(1).uppercase(),
                                                        fontSize = 48.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White
                                                    )
                                                }
                                            )
                                        } else {
                                            Text(
                                                text = partner.name.take(1).uppercase(),
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .border(3.dp, Color(0xFF818CF8).copy(alpha = 0.6f), CircleShape)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = partner.name,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    val subCaption = when {
                                        uiState.state == CallState.OUTGOING_RINGING -> uiState.statusMessage
                                        uiState.state == CallState.CONNECTING -> uiState.statusMessage
                                        uiState.state == CallState.ENDED -> uiState.statusMessage
                                        uiState.isMuted -> "Microphone Muted"
                                        else -> "${uiState.activeAudioRoute.label} Active • LiveKit Audio"
                                    }
                                    Text(
                                        text = subCaption,
                                        fontSize = 12.sp,
                                        color = if (uiState.isMuted) Color(0xFFFB7185) else Color(0xFF38BDF8),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Floating Self-View PiP
                    if (uiState.isCameraEnabled && uiState.hasLocalVideo && localVideoTrack != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 24.dp, top = 16.dp)
                                .size(width = 100.dp, height = 142.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .testTag("call_local_pip_preview"),
                            color = Color(0xFF0F172A),
                            tonalElevation = 8.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LiveKitVideoView(
                                    track = localVideoTrack,
                                    mirror = uiState.isFrontCamera,
                                    onInitRenderer = onInitRenderer,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = if (uiState.isFrontCamera) "You • Front" else "You • Back",
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Call Controls Dock (shown during active/ringing/connecting states)
                if (!uiState.state.isTerminal) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                            .testTag("call_controls_dock"),
                        shape = RoundedCornerShape(32.dp),
                        color = Color(0xFF0B1329).copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Mic Mute
                            IconButton(
                                onClick = {
                                    haptic.performPixelClick(view)
                                    onToggleMute()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isMuted) Color(0xFFE11D48) else Color.White.copy(alpha = 0.15f))
                                    .testTag("call_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (uiState.isMuted) "Unmute Microphone" else "Mute Microphone",
                                    tint = Color.White
                                )
                            }

                            // 2. Audio Output Route (Earpiece / Speaker / Bluetooth / Headset)
                            Box {
                                val audioIcon = when (uiState.activeAudioRoute) {
                                    AudioOutputRoute.SPEAKERPHONE -> Icons.Default.VolumeUp
                                    AudioOutputRoute.BLUETOOTH -> Icons.Default.BluetoothAudio
                                    AudioOutputRoute.WIRED_HEADSET -> Icons.Default.Headset
                                    AudioOutputRoute.EARPIECE -> Icons.Default.Hearing
                                }
                                val isBoostedRoute = uiState.activeAudioRoute != AudioOutputRoute.EARPIECE
                                IconButton(
                                    onClick = {
                                        haptic.performPixelClick(view)
                                        showAudioMenu = true
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isBoostedRoute) Color(0xFF10B981) else Color.White.copy(alpha = 0.15f))
                                        .testTag("call_audio_route_button")
                                ) {
                                    Icon(
                                        imageVector = audioIcon,
                                        contentDescription = "Select Audio Output (${uiState.activeAudioRoute.label})",
                                        tint = if (isBoostedRoute) Color.Black else Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showAudioMenu,
                                    onDismissRequest = { showAudioMenu = false },
                                    modifier = Modifier.testTag("call_audio_route_menu")
                                ) {
                                    uiState.availableAudioRoutes.forEach { route ->
                                        DropdownMenuItem(
                                            text = { Text(route.label, fontWeight = FontWeight.SemiBold) },
                                            trailingIcon = if (route == uiState.activeAudioRoute) {
                                                { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                            } else null,
                                            onClick = {
                                                showAudioMenu = false
                                                onSelectAudioRoute(route)
                                            }
                                        )
                                    }
                                }
                            }

                            // 3. Camera Toggle
                            IconButton(
                                onClick = {
                                    haptic.performPixelClick(view)
                                    onToggleCamera()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isCameraEnabled) Color(0xFF6366F1) else Color.White.copy(alpha = 0.15f))
                                    .testTag("call_camera_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = if (uiState.isCameraEnabled) "Turn Camera Off" else "Turn Camera On",
                                    tint = Color.White
                                )
                            }

                            // 4. Flip Camera (when local camera enabled)
                            if (uiState.isCameraEnabled) {
                                IconButton(
                                    onClick = {
                                        haptic.performPixelTick(view)
                                        onFlipCamera()
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .testTag("call_flip_camera_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlipCameraAndroid,
                                        contentDescription = "Switch Front/Back Camera",
                                        tint = Color.White
                                    )
                                }
                            }

                            // 5. End Call
                            IconButton(
                                onClick = {
                                    haptic.performPixelHeavyClick(view)
                                    onEndCall()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE11D48))
                                    .testTag("call_end_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "End Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveKitVideoView(
    track: VideoTrack,
    mirror: Boolean,
    onInitRenderer: (TextureViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(context) {
        TextureViewRenderer(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onInitRenderer(this)
        }
    }

    DisposableEffect(track, mirror, renderer) {
        runCatching { renderer.setMirror(mirror) }
        runCatching { track.addRenderer(renderer) }
        onDispose {
            runCatching { track.removeRenderer(renderer) }
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
private fun PermissionRationaleCard(
    need: PermissionNeed,
    permanentlyDenied: Boolean,
    isCallAlreadyActive: Boolean,
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onCancel: () -> Unit
) {
    val message = when (need) {
        PermissionNeed.MICROPHONE -> "EchoTalks needs microphone access so the other person can hear you on this call."
        PermissionNeed.CAMERA -> "EchoTalks needs camera access to share your video on this call."
        PermissionNeed.MICROPHONE_AND_CAMERA -> "EchoTalks needs microphone and camera permissions for this video call."
        PermissionNeed.NONE -> "Grant audio/video permissions to continue."
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .testTag("call_permission_rationale_card"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (need == PermissionNeed.CAMERA) Icons.Default.Videocam else Icons.Default.Mic,
                contentDescription = null,
                tint = Color(0xFF818CF8),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (permanentlyDenied) "Permission Disabled in Settings" else "Permission Required",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (permanentlyDenied) {
                    "$message Open App Settings -> Permissions and enable access to restore calling."
                } else {
                    message
                },
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )

            if (permanentlyDenied) {
                Button(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("call_open_settings_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Open App Settings", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("call_grant_permission_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = onCancel,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = if (isCallAlreadyActive) "Continue Audio Only" else "Cancel Call",
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun TerminalCallStatusCard(
    title: String,
    detail: String,
    isLimitReached: Boolean,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .testTag("call_terminal_status_card"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(
            1.dp,
            if (isLimitReached) Color(0xFFF59E0B).copy(alpha = 0.6f) else Color(0xFFE11D48).copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (isLimitReached) Color(0xFFFBBF24) else Color(0xFFFB7185),
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = detail,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("call_error_close_button"),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}
