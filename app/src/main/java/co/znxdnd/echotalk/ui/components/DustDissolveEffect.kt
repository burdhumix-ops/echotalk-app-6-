package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import kotlin.random.Random

data class DustParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val color: Color,
    val delayMs: Int = 0
)

/**
 * Telegram-Style Thanos Particle Dust Dissolve Canvas.
 * Disintegrates elements into fine sparkling dust that drifts with horizontal wind and upward thermal draft.
 */
@Composable
fun DustDissolveEffect(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    particleCount: Int = 180,
    durationMs: Int = 450
) {
    val particles = remember {
        val colors = listOf(
            Color(0xFF2AABEE), // Telegram Cyan
            Color(0xFF60A5FA), // Sky Blue
            Color(0xFF818CF8), // Indigo
            Color(0xFFA78BFA), // Lavender
            Color(0xFFF43F5E), // Rose Dust
            Color(0xFFF8FAFC), // White sparkle
            Color(0xFFCBD5E1), // Slate smoke
            Color(0xFF38BDF8)  // Electric Cyan
        )
        List(particleCount) {
            DustParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.35f) * 320f + if (Random.nextBoolean()) 120f else -60f,
                vy = -Random.nextFloat() * 220f - 30f,
                radius = Random.nextFloat() * 3.2f + 1.2f,
                color = colors.random(),
                delayMs = Random.nextInt(0, 100)
            )
        }
    }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
        )
        onComplete()
    }

    Canvas(modifier = modifier.fillMaxSize().testTag("dust_dissolve_canvas")) {
        val t = progress.value
        val alpha = (1f - t).coerceIn(0f, 1f)

        particles.forEach { p ->
            val particleT = ((t * durationMs - p.delayMs) / (durationMs - p.delayMs).toFloat()).coerceIn(0f, 1f)
            val px = (p.x * size.width) + (p.vx * particleT)
            val py = (p.y * size.height) + (p.vy * particleT) + (particleT * particleT * 90f)
            val currentRadius = p.radius * (1f - particleT * 0.45f)

            if (alpha > 0f && particleT > 0f) {
                // Outer glowing halo
                drawCircle(
                    color = p.color.copy(alpha = alpha * 0.45f),
                    radius = currentRadius * 1.8f,
                    center = Offset(px, py)
                )
                // Inner bright dust core
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = currentRadius,
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * TelegramDustDisintegrator:
 * Wraps any composable (chat bubble, conversation row, room card).
 * When isDissolving becomes true, simultaneously dissolves the target item
 * and spawns a cloud of sparkling dust particles, playing haptics and swoosh chimes,
 * before triggering onDissolved().
 */
@Composable
fun TelegramDustDisintegrator(
    isDissolving: Boolean,
    modifier: Modifier = Modifier,
    onDissolved: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(isDissolving) {
        if (isDissolving) {
            EchoHapticManager.getInstance(context).performTelegramMediaDelete(view)
            PoliteSoundSynthesizer.playPopSound()
        }
    }

    val dissolutionAlpha by animateFloatAsState(
        targetValue = if (isDissolving) 0f else 1f,
        animationSpec = tween(durationMillis = 280, easing = LinearEasing),
        label = "dissolutionAlpha"
    )

    val dissolutionScale by animateFloatAsState(
        targetValue = if (isDissolving) 0.94f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutLinearInEasing),
        label = "dissolutionScale"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Content being dissolved
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = dissolutionAlpha
                    scaleX = dissolutionScale
                    scaleY = dissolutionScale
                }
        ) {
            content()
        }

        // Particle Vaporizer Overlay
        if (isDissolving) {
            DustDissolveEffect(
                onComplete = onDissolved,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/**
 * Confirmation dialog before deleting a Private Message (PM) / Conversation.
 * Strict visual design requirement:
 * - If user says "NO" -> prominent GREEN visual button with shield & reassurance.
 * - If user says "YES" -> prominent RED visual button with trash & vaporization.
 */
@Composable
fun DeletePmConfirmationDialog(
    partnerName: String,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showGreenVisualFlash by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF20F172A),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            tonalElevation = 12.dp,
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("delete_pm_confirmation_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon: Vaporizer Trash
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0x33F43F5E), Color(0x11F43F5E))
                            )
                        )
                        .border(1.5.dp, Color(0xFFF43F5E).copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoDelete,
                        contentDescription = "Delete PM",
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Delete Chat with $partnerName?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This will delete all private messages into dust. This action is irreversible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons: NO (GREEN VISUAL) vs YES (RED VISUAL)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // NO BUTTON -> GREEN VISUAL (Reassurance / Safe)
                    Button(
                        onClick = {
                            showGreenVisualFlash = true
                            EchoHapticManager.getInstance(context).performPixelClick(view)
                            PoliteSoundSynthesizer.playPopSound()
                            onCancel()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981), // Emerald Green
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("delete_pm_cancel_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No, Keep Chat Safe",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // YES BUTTON -> RED VISUAL (Destructive / Delete)
                    Button(
                        onClick = {
                            EchoHapticManager.getInstance(context).performTelegramMediaDelete(view)
                            PoliteSoundSynthesizer.playPopSound()
                            onConfirmDelete()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE11D48), // Rose Red
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("delete_pm_confirm_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Yes, Delete Messages",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
