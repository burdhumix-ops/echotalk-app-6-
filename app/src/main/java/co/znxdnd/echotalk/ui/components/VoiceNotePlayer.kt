package co.znxdnd.echotalk.ui.components

import android.media.MediaPlayer
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

/**
 * Interactive Voice Note Player with scrubbable waveform, playhead dragging,
 * real MediaPlayer playback (with speed toggle 1x/1.5x/2x), and synthesized
 * harmonic fallback for seeded demo voice notes without a local audio file.
 */
@Composable
fun VoiceNotePlayer(
    duration: String = "0:05",
    waveform: List<Float> = emptyList(),
    isSentByMe: Boolean = false,
    audioSeed: String = "voice_note",
    audioFilePath: String? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    val totalSeconds = remember(duration) {
        parseDurationToSeconds(duration)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var activePlaybackHandle by remember { mutableStateOf<PoliteSoundSynthesizer.VoicePlaybackHandle?>(null) }
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val bars = remember(waveform) {
        if (waveform.isNotEmpty()) waveform
        else listOf(0.35f, 0.65f, 0.95f, 0.45f, 0.75f, 1.0f, 0.55f, 0.85f, 0.45f, 0.9f, 0.65f, 0.4f, 0.75f, 0.55f, 0.85f, 0.4f, 0.7f, 0.35f)
    }

    fun releaseMediaPlayer() {
        try {
            activeMediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Throwable) {}
        activeMediaPlayer = null
    }

    DisposableEffect(Unit) {
        onDispose {
            activePlaybackHandle?.stop()
            releaseMediaPlayer()
        }
    }

    LaunchedEffect(isPlaying, activeMediaPlayer, playbackSpeed) {
        val mp = activeMediaPlayer ?: return@LaunchedEffect
        while (isPlaying && activeMediaPlayer === mp) {
            try {
                if (mp.isPlaying && mp.duration > 0 && !isDragging) {
                    progress = (mp.currentPosition.toFloat() / mp.duration.toFloat()).coerceIn(0f, 1f)
                }
            } catch (_: Throwable) {
                break
            }
            delay(80)
        }
    }

    fun stopPlayback() {
        activePlaybackHandle?.stop()
        activePlaybackHandle = null
        releaseMediaPlayer()
        isPlaying = false
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun startPlayback(fromFraction: Float) {
        activePlaybackHandle?.stop()
        activePlaybackHandle = null
        releaseMediaPlayer()

        val hasRealAudioFile = !audioFilePath.isNullOrBlank() && (
            (audioFilePath.startsWith("/") && File(audioFilePath).exists()) ||
                audioFilePath.startsWith("http://") ||
                audioFilePath.startsWith("https://")
            )

        if (hasRealAudioFile) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(audioFilePath)
                    setOnPreparedListener { player ->
                        val targetMs = (player.duration * fromFraction.coerceIn(0f, 0.98f)).roundToInt()
                        if (targetMs > 0) player.seekTo(targetMs)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
                            } catch (_: Throwable) {}
                        }
                        player.start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        progress = 0f
                        releaseMediaPlayer()
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    setOnErrorListener { _, _, _ ->
                        releaseMediaPlayer()
                        isPlaying = false
                        true
                    }
                    prepareAsync()
                }
                activeMediaPlayer = mp
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return
            } catch (_: Throwable) {
                releaseMediaPlayer()
            }
        }

        isPlaying = true
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        activePlaybackHandle = PoliteSoundSynthesizer.playSimulatedVoiceNote(
            seed = audioSeed,
            durationSeconds = (totalSeconds / playbackSpeed).coerceAtLeast(1f),
            startFraction = fromFraction,
            onProgress = { currentProg ->
                if (!isDragging) {
                    progress = currentProg
                }
            },
            onCompletion = {
                isPlaying = false
                progress = 0f
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val activeBarColor = if (isSentByMe) Color.White else primaryColor
    val inactiveBarColor = if (isSentByMe) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSentByMe) primaryColor.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("voice_note_player"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play / Pause Circle Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isSentByMe) Color.White.copy(alpha = 0.9f) else primaryColor)
                    .clickable {
                        if (isPlaying) {
                            stopPlayback()
                        } else {
                            val startFrom = if (progress >= 0.98f) 0f else progress
                            startPlayback(startFrom)
                        }
                    }
                    .testTag("voice_note_play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    tint = if (isSentByMe) primaryColor else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Interactive Waveform & Scrub Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 108.dp, max = 148.dp)
                    .height(34.dp)
                    .pointerInput(totalSeconds) {
                        detectTapGestures { offset ->
                            val seekFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            progress = seekFraction
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (isPlaying) {
                                val mp = activeMediaPlayer
                                if (mp != null && mp.duration > 0) {
                                    mp.seekTo((mp.duration * seekFraction).roundToInt())
                                } else {
                                    startPlayback(seekFraction)
                                }
                            }
                        }
                    }
                    .pointerInput(totalSeconds) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val seekFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                progress = seekFraction
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                            onDragEnd = {
                                isDragging = false
                                if (isPlaying) {
                                    val mp = activeMediaPlayer
                                    if (mp != null && mp.duration > 0) {
                                        mp.seekTo((mp.duration * progress).roundToInt())
                                    } else {
                                        startPlayback(progress)
                                    }
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val seekFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                progress = seekFraction
                            }
                        )
                    }
                    .testTag("voice_note_waveform_track")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = bars.size
                    val barWidth = 3.5.dp.toPx()
                    val totalSpacing = size.width - (barWidth * barCount)
                    val gap = (totalSpacing / (barCount - 1).coerceAtLeast(1)).coerceAtLeast(1.5f)

                    bars.forEachIndexed { index, amp ->
                        val x = index * (barWidth + gap)
                        val barProgress = index / barCount.toFloat()
                        val isPlayed = progress >= barProgress
                        val barHeight = (size.height * 0.75f * amp).coerceAtLeast(5.dp.toPx())
                        val topY = (size.height - barHeight) / 2f

                        drawRoundRect(
                            color = if (isPlayed) activeBarColor else inactiveBarColor,
                            topLeft = Offset(x, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }

                    val playheadX = size.width * progress
                    drawCircle(
                        color = if (isSentByMe) Color.White else primaryColor,
                        radius = if (isDragging) 4.5.dp.toPx() else 3.dp.toPx(),
                        center = Offset(playheadX, size.height / 2f)
                    )
                }
            }

            // Variable Speed Pill (1x / 1.5x / 2x)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        playbackSpeed = when (playbackSpeed) {
                            1f -> 1.5f
                            1.5f -> 2f
                            else -> 1f
                        }
                        if (isPlaying && activeMediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                activeMediaPlayer?.let { mp ->
                                    mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                    .testTag("voice_note_speed_button"),
                shape = RoundedCornerShape(8.dp),
                color = if (isSentByMe) Color.White.copy(alpha = 0.22f) else primaryColor.copy(alpha = 0.14f)
            ) {
                val speedText = when (playbackSpeed) {
                    1.5f -> "1.5x"
                    2f -> "2x"
                    else -> "1x"
                }
                Text(
                    text = speedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSentByMe) Color.White else primaryColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // Bottom Metadata Row: Duration + Seeking Track
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        if (isSentByMe) Color.White.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                        .background(if (isSentByMe) Color.White else primaryColor)
                )
            }

            val currentElapsedSeconds = (progress * totalSeconds).roundToInt()
            val formattedElapsed = formatSecondsToTime(currentElapsedSeconds)
            val durationLabel = if (isPlaying || progress > 0f) {
                "$formattedElapsed / $duration"
            } else {
                duration
            }

            Text(
                text = durationLabel,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = if (isSentByMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("voice_note_duration_text")
            )
        }
    }
}

/**
 * Compact voice note snippet player for the borderless DM conversation cards.
 */
@Composable
fun VoiceNoteSnippetPreview(
    duration: String = "0:05",
    audioSeed: String = "dm_preview",
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val totalSeconds = remember(duration) { parseDurationToSeconds(duration) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var activeHandle by remember { mutableStateOf<PoliteSoundSynthesizer.VoicePlaybackHandle?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeHandle?.stop()
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    if (isPlaying) {
                        activeHandle?.stop()
                        activeHandle = null
                        isPlaying = false
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        isPlaying = true
                        activeHandle = PoliteSoundSynthesizer.playSimulatedVoiceNote(
                            seed = audioSeed,
                            durationSeconds = totalSeconds.toFloat(),
                            startFraction = 0f,
                            onProgress = { progress = it },
                            onCompletion = {
                                isPlaying = false
                                progress = 0f
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Preview voice note",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }

        Text(
            text = "Voice Note",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Mini progress or static duration
        Text(
            text = if (isPlaying) "${(progress * totalSeconds).roundToInt()}s / $duration" else duration,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Utility: Converts "mm:ss" or "ss" string into an integer number of seconds.
 */
fun parseDurationToSeconds(durationStr: String): Int {
    return try {
        val parts = durationStr.split(":")
        when (parts.size) {
            2 -> (parts[0].trim().toIntOrNull() ?: 0) * 60 + (parts[1].trim().toIntOrNull() ?: 5)
            1 -> parts[0].trim().toIntOrNull() ?: 5
            else -> 5
        }
    } catch (_: Exception) {
        5
    }.coerceIn(1, 600)
}

/**
 * Utility: Formats total seconds into "m:ss" time display.
 */
fun formatSecondsToTime(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "$mins:${if (secs < 10) "0$secs" else "$secs"}"
}
