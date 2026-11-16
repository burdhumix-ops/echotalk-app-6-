package co.znxdnd.echotalk.ui.components

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.SubcomposeAsyncImage
import co.znxdnd.echotalk.R
import co.znxdnd.echotalk.utils.EchoHapticManager
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun ViewOnceViewerDialog(
    senderName: String,
    mediaUrl: String? = null,
    onBurned: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }

    var secondsLeft by remember { mutableIntStateOf(10) }
    var isBurning by remember { mutableStateOf(false) }
    var hasBurned by remember { mutableStateOf(false) }

    fun triggerBurnOnce() {
        if (!hasBurned) {
            hasBurned = true
            haptic.performTelegramMediaDelete(view)
            onBurned()
        }
    }

    LaunchedEffect(Unit) {
        haptic.performTelegramMediaOpen(view)
    }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        } else {
            isBurning = true
            delay(450)
            triggerBurnOnce()
            onDismiss()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (secondsLeft / 10f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850),
        label = "viewOnceCountdown"
    )

    Dialog(
        onDismissRequest = {
            triggerBurnOnce()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        AnimatedVisibility(
            visible = !isBurning,
            exit = fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.85f)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xF5000000)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFBBF24)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column {
                                Text(
                                    text = "View-Once Media from $senderName",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Self-destructs upon closing or countdown expiry",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFCD34D)
                                )
                            }
                        }

                        // Circular Countdown Badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FBBF24))
                                .border(2.dp, Color(0xFFFBBF24), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${secondsLeft}s",
                                fontSize = 13.sp,
                                color = Color(0xFFFBBF24),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFFBBF24),
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )

                    // Responsive Media Display Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val hasRealMedia = !mediaUrl.isNullOrBlank() &&
                            (mediaUrl.startsWith("/") || mediaUrl.startsWith("http") || mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://"))
                        if (hasRealMedia) {
                            SubcomposeAsyncImage(
                                model = if (mediaUrl!!.startsWith("/")) File(mediaUrl) else mediaUrl,
                                contentDescription = "View-once secret media",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFFFBBF24))
                                    }
                                },
                                error = {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_echotalk_logo_1792796066052),
                                        contentDescription = "View-once secret media",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_echotalk_logo_1792796066052),
                                contentDescription = "View-once secret media",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Screen capture security overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "SCREEN CAPTURE PROHIBITED",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Bottom Burn & Destroy Action
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "This media will disappear permanently once dismissed.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Button(
                            onClick = {
                                isBurning = true
                                triggerBurnOnce()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Destroy and Burn",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DESTROY & DISMISS NOW",
                                fontSize = 13.sp,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
