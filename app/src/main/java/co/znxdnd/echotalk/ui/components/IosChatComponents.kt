package co.znxdnd.echotalk.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.utils.EchoHapticManager
import java.io.File

/**
 * iOS-inspired Material 3 chat bubble with sender grouping, asymmetrical tails,
 * media thumbnails, interactive voice notes, view-once lock cards, and reaction pills.
 */
@Composable
fun IosChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onLongPress: () -> Unit,
    onOpenMedia: (ChatMessage) -> Unit,
    onOpenViewOnce: (ChatMessage) -> Unit,
    onReactionTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    val bubbleShape = RoundedCornerShape(
        topStart = if (!isMe && !isFirstInGroup) 6.dp else 18.dp,
        topEnd = if (isMe && !isFirstInGroup) 6.dp else 18.dp,
        bottomStart = when {
            isMe -> 18.dp
            isLastInGroup -> 4.dp
            else -> 6.dp
        },
        bottomEnd = when {
            !isMe -> 18.dp
            isLastInGroup -> 4.dp
            else -> 6.dp
        }
    )

    val topSpacing = if (isFirstInGroup) 8.dp else 2.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topSpacing)
            .pointerInput(message.id) {
                detectTapGestures(
                    onLongPress = {
                        EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                        onLongPress()
                    }
                )
            },
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 292.dp)
        ) {
            if (isFirstInGroup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isMe) "You" else message.sender,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            val outgoingBrush = Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                )
            )

            Surface(
                shape = bubbleShape,
                color = if (isMe) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isMe) 0.dp else 1.dp,
                border = if (!isMe) {
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                } else null
            ) {
                Column(
                    modifier = Modifier
                        .then(
                            if (isMe) Modifier.background(outgoingBrush, bubbleShape)
                            else Modifier
                        )
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.isForwarded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forwarded",
                                tint = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (!message.forwardedFrom.isNullOrBlank()) {
                                    "Forwarded from ${message.forwardedFrom}"
                                } else {
                                    "Forwarded"
                                },
                                fontSize = 10.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (!message.replyToText.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isMe) Color.Black.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .height(22.dp)
                                    .background(
                                        if (isMe) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Column {
                                if (!message.replyToSender.isNullOrBlank()) {
                                    Text(
                                        text = message.replyToSender,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = message.replyToText,
                                    fontSize = 11.sp,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (message.isViewOnce) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !message.viewed) {
                                    onOpenViewOnce(message)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (message.viewed) {
                                Color.Black.copy(alpha = 0.12f)
                            } else if (isMe) {
                                Color.White.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (message.viewed) Icons.Default.Lock else Icons.Default.LocalFireDepartment,
                                    contentDescription = if (message.viewed) "Opened" else "View Once",
                                    tint = if (message.viewed) {
                                        if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        Color(0xFFFBBF24)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (message.viewed) "Opened • Disintegrated" else "Tap to View Once",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (message.isAudio) {
                        VoiceNotePlayer(
                            duration = message.audioDuration ?: "0:05",
                            waveform = message.audioWaveform,
                            isSentByMe = isMe,
                            audioSeed = message.id,
                            audioFilePath = message.mediaUrl
                        )
                    } else if (!message.mediaUrl.isNullOrBlank()) {
                        val model: Any = rememberMediaModel(message.mediaUrl)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.18f))
                                .clickable { onOpenMedia(message) }
                        ) {
                            SubcomposeAsyncImage(
                                model = model,
                                contentDescription = if (message.isDoodle) "Sketch attachment" else "Photo attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            )
                        }
                    }

                    if (message.text.isNotBlank() && !message.isAudio && !(message.isViewOnce && message.viewed)) {
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isMe && isLastInGroup) {
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = when (message.status) {
                                    "seen" -> "Read"
                                    "sending" -> "Sending"
                                    else -> "Delivered"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                            )
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = message.status,
                                tint = if (message.status == "seen") {
                                    Color(0xFF93C5FD)
                                } else {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                                },
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.entries.forEach { (emoji, count) ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onReactionTap(emoji) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(text = emoji, fontSize = 11.sp)
                                if (count > 1) {
                                    Text(
                                        text = count.toString(),
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

private fun rememberMediaModel(mediaUrl: String): Any {
    return if (mediaUrl.startsWith("http") || mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://")) {
        mediaUrl
    } else {
        File(mediaUrl)
    }
}

/**
 * iOS-inspired Material 3 composer bar featuring a leading attachment icon button,
 * expandable attachment drawer, pending photo preview strip, live voice recorder strip,
 * reply/edit indicator, and a capsule input field with a circular primary Send button.
 */
@Composable
fun IosMessageInputDock(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    showAttachmentMenu: Boolean,
    onToggleAttachmentMenu: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenPhotoStudio: () -> Unit,
    onOpenSketchCanvas: () -> Unit,
    isViewOnce: Boolean,
    onToggleViewOnce: () -> Unit,
    pendingAttachmentUri: Uri?,
    onEditPendingAttachment: () -> Unit,
    onClearPendingAttachment: () -> Unit,
    replyingTo: ChatMessage?,
    onCancelReply: () -> Unit,
    editingMessage: ChatMessage?,
    onCancelEdit: () -> Unit,
    isRecording: Boolean,
    recordSeconds: Int,
    liveVoiceLevel: Float,
    onStartVoiceRecording: () -> Unit,
    onStopAndSendVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = messageText.isNotBlank() || pendingAttachmentUri != null
    val attachRotation by animateFloatAsState(
        targetValue = if (showAttachmentMenu) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "attachRotation"
    )
    val sendBgColor by animateColorAsState(
        targetValue = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "sendBgColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Active Edit Banner
            AnimatedVisibility(
                visible = editingMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (editingMessage != null) {
                    ComposerContextStrip(
                        icon = Icons.Default.Edit,
                        title = "Editing Message",
                        subtitle = editingMessage.text,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onDismiss = onCancelEdit
                    )
                }
            }

            // Active Reply Quote Banner
            AnimatedVisibility(
                visible = replyingTo != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (replyingTo != null) {
                    ComposerContextStrip(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        title = "Replying to ${replyingTo.sender}",
                        subtitle = replyingTo.text.ifBlank { "Media attachment" },
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onDismiss = onCancelReply
                    )
                }
            }

            // View-Once Active Indicator Pill
            AnimatedVisibility(
                visible = isViewOnce,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "View-Once active • Disintegrates after opening",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onToggleViewOnce,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disable View-Once",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Pending Photo Attachment Preview Card
            AnimatedVisibility(
                visible = pendingAttachmentUri != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (pendingAttachmentUri != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("chat_pending_attachment_preview"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SubcomposeAsyncImage(
                                model = pendingAttachmentUri,
                                contentDescription = "Selected photo attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Photo Attachment Ready",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap Studio to grade or press Send",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onEditPendingAttachment() },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Edit in Studio",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Studio",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            IconButton(
                                onClick = onClearPendingAttachment,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Attachment",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Voice Note Recording Strip
            AnimatedVisibility(
                visible = isRecording,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x22EF4444),
                    border = BorderStroke(1.dp, Color(0x55EF4444))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        val mins = recordSeconds / 60
                        val secs = recordSeconds % 60
                        Text(
                            text = "REC $mins:${secs.toString().padStart(2, '0')}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(liveVoiceLevel.coerceIn(0.08f, 1f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFEF4444))
                            )
                        }
                        IconButton(
                            onClick = onCancelVoiceRecording,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Discard Voice Note",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Button(
                            onClick = onStopAndSendVoiceRecording,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Expandable iOS-style Attachment Grid
            AnimatedVisibility(
                visible = showAttachmentMenu,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_attachment_drawer"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IosAttachmentActionTile(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Photo",
                            tint = Color(0xFF0A84FF),
                            testTag = "chat_attach_photo",
                            onClick = onPickPhoto
                        )
                        IosAttachmentActionTile(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Studio",
                            tint = Color(0xFF5E5CE6),
                            testTag = "chat_attach_studio",
                            onClick = onOpenPhotoStudio
                        )
                        IosAttachmentActionTile(
                            icon = Icons.Default.Brush,
                            label = "Sketch",
                            tint = Color(0xFFFF375F),
                            testTag = "chat_attach_sketch",
                            onClick = onOpenSketchCanvas
                        )
                        IosAttachmentActionTile(
                            icon = Icons.Default.LocalFireDepartment,
                            label = "View-Once",
                            tint = if (isViewOnce) Color(0xFFFF9F0A) else Color(0xFF30D158),
                            testTag = "chat_attach_view_once",
                            onClick = onToggleViewOnce
                        )
                    }
                }
            }

            // Main iOS-Inspired Composer Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Attachment Icon Button (48.dp touch target)
                IconButton(
                    onClick = onToggleAttachmentMenu,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_attachment_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (showAttachmentMenu || pendingAttachmentUri != null) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach photo, sketch, or media",
                            tint = if (showAttachmentMenu || pendingAttachmentUri != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .size(19.dp)
                                .rotate(attachRotation)
                        )
                    }
                }

                // iOS Capsule Text Input Field + View-Once toggle inside pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            placeholder = {
                                Text(
                                    text = if (pendingAttachmentUri != null) "Add a caption..." else "iMessage...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input"),
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        IconButton(
                            onClick = onToggleViewOnce,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("chat_view_once_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Toggle View-Once",
                                tint = if (isViewOnce) Color(0xFFFF9F0A)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Voice Note Shortcut (visible when text & attachment are empty)
                AnimatedVisibility(visible = !canSend && !isRecording) {
                    IconButton(
                        onClick = onStartVoiceRecording,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_voice_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record Voice Note",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // iOS Circular Send Button (always present with enabled state for reliable UI semantics)
                IconButton(
                    onClick = onSendMessage,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_send_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(sendBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Send Message",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosAttachmentActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ComposerContextStrip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(26.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(15.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
