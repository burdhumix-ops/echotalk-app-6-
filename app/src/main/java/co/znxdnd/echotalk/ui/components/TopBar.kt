package co.znxdnd.echotalk.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.znxdnd.echotalk.data.model.User

@Composable
fun TopBar(
    currentUser: User,
    onOpenSettings: () -> Unit,
    onOpenSoundscapes: () -> Unit,
    onOpenVault: () -> Unit,
    onTriggerBackdoor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // 15x App Logo Tap Gateway Trigger logic
    var logoTapCount by remember { mutableIntStateOf(0) }
    var firstTapTime by remember { mutableLongStateOf(0L) }

    fun handleLogoTap() {
        val now = System.currentTimeMillis()
        if (now - firstTapTime > 5000L) {
            logoTapCount = 1
            firstTapTime = now
        } else {
            logoTapCount += 1
        }

        if (logoTapCount >= 15) {
            logoTapCount = 0
            firstTapTime = 0L
            // Double-pulse haptic feedback
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.postDelayed({
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } catch (_: Exception) {}
            }, 100)
            onTriggerBackdoor()
        }
    }

    // Theme-adaptive translucent bar with soft gradient fade and subtle 1px bottom hairline
    val headerBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.88f else 0.94f),
            MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.72f else 0.84f),
            MaterialTheme.colorScheme.background.copy(alpha = if (isDarkTheme) 0.48f else 0.62f)
        )
    )
    val hairlineColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    }
    val pillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.55f else 0.75f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(headerBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Sleek App Logo with 15x Secret Tap Gateway
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { handleLogoTap() }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillColor),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val barWidth = 2.dp.toPx()
                        val spacing = 2.dp.toPx()
                        val heights = listOf(0.40f, 0.95f, 0.65f, 0.85f)
                        val startX = (size.width - (heights.size * barWidth + (heights.size - 1) * spacing)) / 2f
                        heights.forEachIndexed { i, hRatio ->
                            val h = size.height * hRatio
                            val x = startX + i * (barWidth + spacing)
                            val y = (size.height - h) / 2f
                            drawRoundRect(
                                color = Color(0xFFE8A5C8),
                                topLeft = Offset(x, y),
                                size = Size(barWidth, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                            )
                        }
                    }
                }

                Text(
                    text = "EchoTalk",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.02).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Right Zone: Ambient Soundscapes, Clean Profile Pill, and Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onOpenSoundscapes,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Ambient Soundscapes",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pillColor,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser.name.take(1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Text(
                            text = currentUser.name.split(" ")[0],
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Subtle 1px hairline divider fading seamlessly into content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            hairlineColor,
                            hairlineColor,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
