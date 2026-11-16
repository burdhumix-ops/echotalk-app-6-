package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TelegramTypingDots:
 * Authentic Telegram-style three-dot bouncing waveform animation.
 * Features staggered physics timing for fluid wave motion beside the typing indicator label.
 */
@Composable
fun TelegramTypingDots(
    dotColor: Color = Color(0xFF2AABEE),
    dotSize: Dp = 3.5.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingDotsAnimation")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0)
        ),
        label = "dot1Offset"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(125)
        ),
        label = "dot2Offset"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(250)
        ),
        label = "dot3Offset"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer { translationY = dot1Offset.dp.toPx() }
                .clip(CircleShape)
                .background(dotColor)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer { translationY = dot2Offset.dp.toPx() }
                .clip(CircleShape)
                .background(dotColor)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer { translationY = dot3Offset.dp.toPx() }
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

fun formatLastSeenLabel(
    lastSeenAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    if (lastSeenAtMillis == null || lastSeenAtMillis <= 0L) return "last seen recently"
    val diffMs = (nowMillis - lastSeenAtMillis).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1L -> "last seen just now"
        mins < 60L -> "last seen ${mins}m ago"
        hours < 24L -> "last seen ${hours}h ago"
        days == 1L -> "last seen yesterday"
        days < 7L -> "last seen ${days}d ago"
        else -> "last seen recently"
    }
}

/**
 * TelegramChatHeaderSubtitle:
 * Dynamically switches between the user's real-time Supabase presence status ("online" / formatted last seen)
 * and the live real-time "typing..." indicator with bouncing wave dots when the other participant
 * is actively composing text.
 */
@Composable
fun TelegramChatHeaderSubtitle(
    isTyping: Boolean,
    isOnline: Boolean,
    partnerName: String? = null,
    lastSeenAtMillis: Long? = null,
    activeColor: Color = Color(0xFF2AABEE),
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = if (isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 220),
        label = "PresenceSubtitleColor"
    )

    AnimatedContent(
        targetState = isTyping,
        transitionSpec = {
            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
        },
        label = "HeaderSubtitleTransition",
        modifier = modifier
    ) { typing ->
        if (typing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.testTag("chat_header_typing_indicator")
            ) {
                Text(
                    text = "typing",
                    fontSize = 11.5.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    color = activeColor
                )
                TelegramTypingDots(dotColor = activeColor)
            }
        } else {
            Text(
                text = if (isOnline) "online" else formatLastSeenLabel(lastSeenAtMillis),
                fontSize = 11.sp,
                color = statusColor,
                modifier = Modifier.testTag("chat_header_online_status")
            )
        }
    }
}
