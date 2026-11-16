package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import co.znxdnd.echotalk.audio.SoundEffectManager
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.utils.EchoHapticManager

/**
 * Centered Telegram-style floating date divider capsule.
 */
@Composable
fun TelegramDateDivider(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.38f),
            border = null
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Telegram status checkmarks indicator (✓ / ✓✓).
 */
@Composable
fun TelegramStatusTicks(
    status: String,
    isOutgoing: Boolean,
    tint: Color = if (status == "seen") Color(0xFF34B7F1) else Color.White.copy(alpha = 0.78f),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when (status) {
            "sending" -> {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Sending",
                    tint = tint,
                    modifier = Modifier.size(11.dp)
                )
            }
            "failed" -> {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Failed to send",
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(12.dp)
                )
            }
            "seen" -> {
                Text(
                    text = "✓✓",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = tint,
                    letterSpacing = (-1.5).sp
                )
            }
            else -> {
                Text(
                    text = "✓",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
        }
    }
}

/**
 * TelegramChatBubble:
 * Renders private messages with authentic Telegram visual geometry,
 * lower-right embedded timestamps, cyan status checkmarks,
 * and borderless sticker/GIF presentations.
 */
@Composable
fun TelegramChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    bubbleColor: Color,
    displayedText: String,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onViewOnceClick: () -> Unit,
    onLaunchGame: (String, String) -> Unit,
    onReactionClick: (String) -> Unit = {},
    messageTextSizeSp: Float = 14f,
    onRetryFailed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Telegram Asymmetrical Bubble Shapes
    val outgoingShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 4.dp // Telegram sharp tail on bottom-right
    )
    val incomingShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 4.dp, // Telegram sharp tail on bottom-left
        bottomEnd = 16.dp
    )
    val bubbleShape = if (isMe) outgoingShape else incomingShape

    // Base background colors
    val finalBubbleColor = if (isMe) {
        bubbleColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    }

    val textColor = if (isMe) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val metaColor = if (isMe) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectTapGestures(
                    onLongPress = {
                        EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                        onLongPress()
                    },
                    onTap = {
                        onTap()
                    }
                )
            },
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {

            // 1. STICKER MESSAGE (Telegram borderless sticker presentation)
            if (message.isSticker) {
                if (message.isForwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forwarded",
                            tint = metaColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = if (!message.forwardedFrom.isNullOrBlank()) "Forwarded from ${message.forwardedFrom}" else "Forwarded",
                            fontSize = 10.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = metaColor
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!message.mediaUrl.isNullOrBlank() && (message.mediaUrl.startsWith("http://") || message.mediaUrl.startsWith("https://"))) {
                        AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = message.text,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // High quality fallback vector/emoji card
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = message.text.take(2).ifEmpty { "🦆" },
                                fontSize = 64.sp
                            )
                        }
                    }

                    // Floating Telegram micro-timestamp pill for stickers
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.45f),
                        border = null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = message.timestamp,
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            if (isMe) {
                                TelegramStatusTicks(
                                    status = message.status,
                                    isOutgoing = true,
                                    tint = if (message.status == "seen") Color(0xFF34B7F1) else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. GIF MESSAGE (Telegram animated looping GIF card)
            else if (message.isGif) {
                if (message.isForwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forwarded",
                            tint = metaColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = if (!message.forwardedFrom.isNullOrBlank()) "Forwarded from ${message.forwardedFrom}" else "Forwarded",
                            fontSize = 10.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = metaColor
                        )
                    }
                }
                Surface(
                    shape = bubbleShape,
                    color = finalBubbleColor,
                    shadowElevation = 1.dp,
                    modifier = Modifier.width(220.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        ) {
                            if (!message.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = message.text,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🎬 GIF", color = Color.White, fontSize = 24.sp)
                                }
                            }

                            // Telegram "GIF" badge pill at top-left
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = null
                            ) {
                                Text(
                                    text = "GIF",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            // Lower-right timestamp on GIF
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = message.timestamp,
                                        fontSize = 9.sp,
                                        color = Color.White
                                    )
                                    if (isMe) {
                                        TelegramStatusTicks(
                                            status = message.status,
                                            isOutgoing = true,
                                            tint = if (message.status == "seen") Color(0xFF34B7F1) else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        if (message.text.isNotBlank() && !message.text.startsWith("GIF:")) {
                            Text(
                                text = message.text,
                                color = textColor,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 3. STANDARD BUBBLE: TEXT, AUDIO, DOODLE, GAME, VIEW-ONCE
            else {
                Surface(
                    shape = bubbleShape,
                    color = finalBubbleColor,
                    shadowElevation = 1.dp,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {

                        // FORWARDED HEADER (Telegram Style)
                        if (message.isForwarded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forwarded",
                                    tint = if (isMe) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (!message.forwardedFrom.isNullOrBlank()) "Forwarded from ${message.forwardedFrom}" else "Forwarded message",
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMe) Color.White.copy(alpha = 0.90f) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // VIEW-ONCE ENCRYPTED FLAME
                        if (message.isViewOnce) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onViewOnceClick() }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (message.viewed) Color.Gray else Color(0xFFEF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = "View Once",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (message.viewed) "Burned Photo" else "View Once Photo",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = textColor
                                    )
                                    Text(
                                        text = if (message.viewed) "Opened" else "Tap to reveal (self-destructs)",
                                        fontSize = 10.sp,
                                        color = metaColor
                                    )
                                }
                            }
                        }

                        // DOODLE CANVAS ARTWORK
                        else if (message.isDoodle) {
                            Column(
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Brush,
                                        contentDescription = null,
                                        tint = if (isMe) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "🎨 Studio Artwork",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val doodleUrl = message.mediaUrl
                                val hasRealDoodle = !doodleUrl.isNullOrBlank() &&
                                    (doodleUrl.startsWith("/") || doodleUrl.startsWith("http") || doodleUrl.startsWith("content://") || doodleUrl.startsWith("file://"))
                                Box(
                                    modifier = Modifier
                                        .size(width = 210.dp, height = 140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasRealDoodle) {
                                        AsyncImage(
                                            model = if (doodleUrl!!.startsWith("/")) java.io.File(doodleUrl) else doodleUrl,
                                            contentDescription = message.text,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = "🖌️ Custom Drawing",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 1v1 GAME CHALLENGE INLINE CARD
                        else if (message.isChallenge && message.challengeData != null) {
                            val challenge = message.challengeData
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .widthIn(min = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFBBF24)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("♟️", fontSize = 16.sp)
                                    }
                                    Column {
                                        Text(
                                            text = challenge.gameTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = textColor
                                        )
                                        Text(
                                            text = "1v1 Grandmaster Duel",
                                            fontSize = 10.sp,
                                            color = metaColor
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onLaunchGame(challenge.gameId, challenge.challenger) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMe) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("⚔️ Play Match", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        // AUDIO WAVEFORM NOTE
                        else if (message.isAudio) {
                            VoiceNotePlayer(
                                duration = message.audioDuration ?: "0:12",
                                waveform = message.audioWaveform,
                                isSentByMe = isMe,
                                audioSeed = message.id,
                                audioFilePath = message.mediaUrl,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // STANDARD / STUDIO PHOTO + TEXT MESSAGE WITH EMBEDDED TELEGRAM TIMESTAMP & TICKS
                        else {
                            val photoUrl = message.mediaUrl
                            val hasRealPhoto = !photoUrl.isNullOrBlank() &&
                                (photoUrl.startsWith("/") || photoUrl.startsWith("http") || photoUrl.startsWith("content://") || photoUrl.startsWith("file://"))
                            if (hasRealPhoto) {
                                AsyncImage(
                                    model = if (photoUrl!!.startsWith("/")) java.io.File(photoUrl) else photoUrl,
                                    contentDescription = message.text,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .widthIn(max = 240.dp)
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = displayedText,
                                color = textColor,
                                fontSize = messageTextSizeSp.sp,
                                lineHeight = (messageTextSizeSp + 5f).sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Telegram Embedded Lower-Right Metadata (Timestamp + Checkmarks)
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (message.status == "sending") "Sending..." else message.timestamp,
                                fontSize = 10.sp,
                                color = metaColor
                            )
                            if (isMe) {
                                TelegramStatusTicks(
                                    status = message.status,
                                    isOutgoing = true,
                                    tint = if (message.status == "seen") Color(0xFF34B7F1) else metaColor
                                )
                            }
                        }
                    }
                }
            }

            // Accessible Retry Action when a message failed to send
            if (isMe && message.status == "failed") {
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRetryFailed() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry sending message",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Failed • Tap to retry",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Attached Emoji Reactions Row (Telegram bubble footer)
            if (message.reactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    message.reactions.forEach { (emoji, count) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { onReactionClick(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(text = emoji, fontSize = 12.sp)
                                if (count > 1) {
                                    Text(
                                        text = "$count",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
