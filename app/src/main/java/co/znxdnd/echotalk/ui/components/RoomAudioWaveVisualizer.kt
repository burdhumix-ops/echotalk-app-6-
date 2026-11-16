package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Visualizer modes supported by RoomAudioWaveVisualizer.
 */
enum class RoomWaveVisualizerMode(val label: String) {
    FLUID_WAVE("Fluid Wave"),
    SPECTRUM_BARS("Spectrum"),
    ENERGY_ORBITAL("Orbital")
}

/**
 * Speaker activity state for real-time visualization.
 */
data class SpeakerActivityState(
    val speakerName: String = "Chloe Martin",
    val isSpeaking: Boolean = true,
    val amplitude: Float = 0.82f, // 0.0f to 1.0f
    val dBLevel: Int = -16, // e.g. -12 dB to -48 dB
    val dominantFrequencyHz: Int = 340
)

/**
 * Real-time dynamic wave visualizer component for audio rooms.
 * Uses Compose Canvas hardware-accelerated animations to render multi-layer fluid waves,
 * harmonic frequency spectrum columns, and radiating orbital sound energy based on speaker activity.
 */
@Composable
fun RoomAudioWaveVisualizer(
    speakerState: SpeakerActivityState,
    modifier: Modifier = Modifier,
    initialMode: RoomWaveVisualizerMode = RoomWaveVisualizerMode.FLUID_WAVE,
    onSpeakerClick: (() -> Unit)? = null
) {
    var visualizerMode by remember { mutableStateOf(initialMode) }

    // Infinite transition drives continuous phase drifting and fluid wave movement
    val infiniteTransition = rememberInfiniteTransition(label = "RoomWaveInfiniteTransition")

    // Phase angle for primary fluid wave
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase1"
    )

    // Phase angle for secondary harmonic wave
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (-2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase2"
    )

    // Phase angle for tertiary micro-ripple wave
    val wavePhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (4 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase3"
    )

    // Dynamic amplitude based on active speech vs ambient baseline
    val targetAmp = if (speakerState.isSpeaking) speakerState.amplitude.coerceIn(0.25f, 1.0f) else 0.08f
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "visualizerAmplitude"
    )

    // Animated glow color transitions based on activity
    val primaryWaveColor by animateColorAsState(
        targetValue = if (speakerState.isSpeaking) Color(0xFF22D3EE) else Color(0xFF64748B),
        animationSpec = tween(durationMillis = 350),
        label = "primaryWaveColor"
    )
    val secondaryWaveColor by animateColorAsState(
        targetValue = if (speakerState.isSpeaking) Color(0xFF818CF8) else Color(0xFF475569),
        animationSpec = tween(durationMillis = 350),
        label = "secondaryWaveColor"
    )
    val tertiaryWaveColor by animateColorAsState(
        targetValue = if (speakerState.isSpeaking) Color(0xFFA855F7) else Color(0xFF334155),
        animationSpec = tween(durationMillis = 350),
        label = "tertiaryWaveColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("room_audio_wave_visualizer"),
        color = Color(0xF00A111E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Live Speaker Status, dB Indicator, and Visualizer Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker pill with pulsing indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = onSpeakerClick != null) { onSpeakerClick?.invoke() }
                        .background(Color(0xFF131F33))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Pulsing status dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (speakerState.isSpeaking) Color(0xFF10B981) else Color(0xFF94A3B8))
                    )

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (speakerState.isSpeaking) "Speaking:" else "Idle / Standby",
                                fontSize = 10.sp,
                                color = if (speakerState.isSpeaking) Color(0xFF38BDF8) else Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = speakerState.speakerName,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Mode Selector Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("visualizer_mode_selector")
                ) {
                    RoomWaveVisualizerMode.entries.forEach { mode ->
                        val isSelected = visualizerMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF1E3A8A) else Color(0x331E293B),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { visualizerMode = mode }
                                .testTag("visualizer_mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF67E8F9) else Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Canvas Animation Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF040812))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (visualizerMode) {
                    RoomWaveVisualizerMode.FLUID_WAVE -> {
                        FluidWaveCanvas(
                            phase1 = wavePhase1,
                            phase2 = wavePhase2,
                            phase3 = wavePhase3,
                            amplitude = animatedAmplitude,
                            primaryColor = primaryWaveColor,
                            secondaryColor = secondaryWaveColor,
                            tertiaryColor = tertiaryWaveColor,
                            isSpeaking = speakerState.isSpeaking
                        )
                    }
                    RoomWaveVisualizerMode.SPECTRUM_BARS -> {
                        SpectrumBarsCanvas(
                            phase = wavePhase1,
                            amplitude = animatedAmplitude,
                            primaryColor = primaryWaveColor,
                            secondaryColor = secondaryWaveColor,
                            isSpeaking = speakerState.isSpeaking
                        )
                    }
                    RoomWaveVisualizerMode.ENERGY_ORBITAL -> {
                        OrbitalEnergyCanvas(
                            phase = wavePhase1,
                            amplitude = animatedAmplitude,
                            primaryColor = primaryWaveColor,
                            secondaryColor = secondaryWaveColor,
                            isSpeaking = speakerState.isSpeaking
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Telemetry metrics (Loudness dB, Frequency Hz, Audio Quality)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Level: ${speakerState.dBLevel} dB",
                        fontSize = 10.sp,
                        color = if (speakerState.isSpeaking) Color(0xFF22D3EE) else Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Pitch: ${speakerState.dominantFrequencyHz} Hz",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "HD Spatial • 48kHz Opus",
                        fontSize = 10.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Multi-layer fluid Bézier sine wave canvas with glowing gradient shader fills.
 */
@Composable
private fun FluidWaveCanvas(
    phase1: Float,
    phase2: Float,
    phase3: Float,
    amplitude: Float,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    isSpeaking: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("canvas_fluid_wave")
    ) {
        val width = size.width
        val height = size.height
        val midY = height * 0.52f
        val maxAmp = (height * 0.40f) * amplitude

        // Background subtle grid lines
        val gridLines = 5
        for (i in 1..gridLines) {
            val y = (height / (gridLines + 1)) * i
            drawLine(
                color = Color(0x1538BDF8),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Layer 3: Tertiary background wave with soft violet fill
        val path3 = Path().apply {
            moveTo(0f, midY)
            val steps = 40
            val stepWidth = width / steps
            for (i in 0..steps) {
                val x = i * stepWidth
                val normX = x / width
                val y = midY + sin(normX * 3.5 * Math.PI + phase3).toFloat() * (maxAmp * 0.55f)
                lineTo(x, y)
            }
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = path3,
            brush = Brush.verticalGradient(
                colors = listOf(tertiaryColor.copy(alpha = 0.22f), Color.Transparent),
                startY = midY - maxAmp,
                endY = height
            )
        )

        // Layer 2: Secondary harmonic wave with blue-indigo gradient
        val path2 = Path().apply {
            moveTo(0f, midY)
            val steps = 50
            val stepWidth = width / steps
            for (i in 0..steps) {
                val x = i * stepWidth
                val normX = x / width
                val y = midY + cos(normX * 2.8 * Math.PI + phase2).toFloat() * (maxAmp * 0.75f)
                lineTo(x, y)
            }
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = path2,
            brush = Brush.verticalGradient(
                colors = listOf(secondaryColor.copy(alpha = 0.35f), Color.Transparent),
                startY = midY - maxAmp,
                endY = height
            )
        )

        // Layer 1: Primary sharp glowing crest wave with gradient stroke
        val crestPath = Path().apply {
            val steps = 60
            val stepWidth = width / steps
            for (i in 0..steps) {
                val x = i * stepWidth
                val normX = x / width
                // Combined dual-harmonic function for organic wave motion
                val y1 = sin(normX * 2.4 * Math.PI + phase1).toFloat() * maxAmp
                val y2 = sin(normX * 4.8 * Math.PI + phase2 * 1.5f).toFloat() * (maxAmp * 0.25f)
                val finalY = midY + (y1 + y2)
                if (i == 0) moveTo(x, finalY) else lineTo(x, finalY)
            }
        }

        // Draw primary wave glow
        drawPath(
            path = crestPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.4f),
                    Color(0xFF38BDF8),
                    Color(0xFFA855F7),
                    primaryColor.copy(alpha = 0.4f)
                )
            ),
            style = Stroke(
                width = if (isSpeaking) 4.5f else 2f,
                cap = StrokeCap.Round
            )
        )

        // Draw center energetic crest line
        drawPath(
            path = crestPath,
            color = Color.White.copy(alpha = if (isSpeaking) 0.85f else 0.4f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
        )

        // Floating particle points along the crest when speaking
        if (isSpeaking) {
            val particleCount = 8
            for (p in 1..particleCount) {
                val normX = (p.toFloat() / (particleCount + 1))
                val x = normX * width
                val y = midY + sin(normX * 2.4 * Math.PI + phase1).toFloat() * maxAmp
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.45f),
                    radius = 5.5f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * Dancing frequency equalizer spectrum bars with peak caps.
 */
@Composable
private fun SpectrumBarsCanvas(
    phase: Float,
    amplitude: Float,
    primaryColor: Color,
    secondaryColor: Color,
    isSpeaking: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("canvas_spectrum_bars")
    ) {
        val barCount = 28
        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing - 24.dp.toPx()) / barCount).coerceAtLeast(2.dp.toPx())
        val startX = 12.dp.toPx()
        val maxBarHeight = size.height * 0.78f

        for (i in 0 until barCount) {
            val normIndex = i.toFloat() / barCount
            // Harmonic wave formula to simulate frequency distribution (bass on left, treble on right)
            val harmonic1 = sin(normIndex * 3.14f * 2.5f + phase).toFloat().absoluteValue
            val harmonic2 = cos(normIndex * 3.14f * 4.0f + phase * 1.3f).toFloat().absoluteValue
            val combined = (harmonic1 * 0.65f + harmonic2 * 0.35f)

            val currentHeight = if (isSpeaking) {
                (maxBarHeight * combined * amplitude).coerceIn(4.dp.toPx(), maxBarHeight)
            } else {
                (maxBarHeight * 0.08f * (1f + sin(normIndex * 6f + phase).toFloat() * 0.3f)).coerceAtLeast(3.dp.toPx())
            }

            val x = startX + i * (barWidth + spacing)
            val y = (size.height - currentHeight) / 2f

            // Draw rounded frequency bar
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor,
                        secondaryColor,
                        Color(0xFF1E3A8A)
                    ),
                    startY = y,
                    endY = y + currentHeight
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, currentHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            // Draw floating peak cap
            if (isSpeaking && currentHeight > 14.dp.toPx()) {
                val capY = y - 4.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(x, capY),
                    size = Size(barWidth, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }
        }
    }
}

/**
 * Concentric orbital sound radiation rings with energy spokes.
 */
@Composable
private fun OrbitalEnergyCanvas(
    phase: Float,
    amplitude: Float,
    primaryColor: Color,
    secondaryColor: Color,
    isSpeaking: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("canvas_orbital_energy")
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (size.height / 2f) * 0.85f

        // Center speaker core
        drawCircle(
            color = if (isSpeaking) primaryColor else Color(0xFF475569),
            radius = 12.dp.toPx() * (if (isSpeaking) 1f + amplitude * 0.2f else 1f),
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = center
        )

        // Multiple radiating sound waves expanding outward
        val ringCount = 4
        for (r in 1..ringCount) {
            val ringProgress = ((phase / (2 * Math.PI).toFloat()) + (r.toFloat() / ringCount)) % 1f
            val radius = 16.dp.toPx() + (maxRadius - 16.dp.toPx()) * ringProgress
            val alpha = ((1f - ringProgress) * (if (isSpeaking) 0.85f else 0.25f)).coerceIn(0f, 1f)

            drawCircle(
                color = if (r % 2 == 0) primaryColor.copy(alpha = alpha) else secondaryColor.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(
                    width = if (isSpeaking) 2.5f else 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), phase * 8f)
                )
            )
        }

        // Radiant sound rays when actively speaking
        if (isSpeaking) {
            val rayCount = 12
            for (i in 0 until rayCount) {
                val angle = (i.toFloat() / rayCount) * (2 * Math.PI) + (phase * 0.5)
                val rayLength = maxRadius * (0.6f + (amplitude * 0.4f * sin(phase + i).toFloat().absoluteValue))
                val endX = center.x + (cos(angle) * rayLength).toFloat()
                val endY = center.y + (sin(angle) * rayLength).toFloat()

                drawLine(
                    color = primaryColor.copy(alpha = 0.35f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}

/**
 * Speaker Avatar Wave Halo:
 * Renders real-time radiating audio wave rings around an avatar to visually show who is currently speaking on stage.
 */
@Composable
fun SpeakerAvatarWaveHalo(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    haloColor: Color = Color(0xFF10B981),
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SpeakerHaloTransition")

    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale1"
    )

    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale2"
    )

    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha2"
    )

    Box(
        modifier = modifier.testTag("speaker_avatar_wave_halo"),
        contentAlignment = Alignment.Center
    ) {
        if (isSpeaking) {
            // Outer Wave 2
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulseScale2
                        scaleY = pulseScale2
                        alpha = pulseAlpha2
                    }
                    .clip(CircleShape)
                    .border(2.dp, haloColor, CircleShape)
            )

            // Outer Wave 1
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulseScale1
                        scaleY = pulseScale1
                        alpha = pulseAlpha1
                    }
                    .clip(CircleShape)
                    .border(2.5.dp, haloColor, CircleShape)
            )
        }

        // Inner Content (Avatar)
        content()
    }
}
