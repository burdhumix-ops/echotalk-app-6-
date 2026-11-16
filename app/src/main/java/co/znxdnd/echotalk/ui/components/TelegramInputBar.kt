package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.znxdnd.echotalk.utils.EchoHapticManager

/**
 * TelegramInputBar:
 * Modern Telegram-styled chat input dock featuring paperclip attachments menu,
 * dedicated sticker/GIF drawer toggle (🎭 / 😊), view-once flame indicator (🔥),
 * voice note recording morph, and Telegram paper-airplane send button.
 */
@Composable
fun TelegramInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendLike: () -> Unit,
    onOpenStickerDrawer: () -> Unit,
    isSendViewOnce: Boolean,
    onToggleViewOnce: () -> Unit,
    onOpenPhotoEditor: () -> Unit,
    onOpenDoodle: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    isRecording: Boolean,
    onOpenGameChallenge: () -> Unit,
    isInputBlocked: Boolean,
    placeholder: String = "Message...",
    replyingToText: String? = null,
    onCancelReply: () -> Unit = {},
    editingMessageText: String? = null,
    onCancelEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hapticManager = remember(context) { EchoHapticManager.getInstance(context) }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Slim Single-Line Edit Message Strip
        if (editingMessageText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(Color(0xFF38BDF8), RoundedCornerShape(1.dp))
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editing",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Edit: $editingMessageText",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Slim Single-Line Reply Quote Strip
        if (replyingToText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = "Reply • $replyingToText",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCancelReply, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel Reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Expandable Attachment Drawer
        AnimatedVisibility(
            visible = showAttachmentMenu,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo Editor
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            onOpenPhotoEditor()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Photo Editor", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Photo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // Doodle Canvas
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            onOpenDoodle()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEC4899)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = "Doodle Canvas", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Doodle", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // 1v1 Game Challenge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            onOpenGameChallenge()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SportsEsports, contentDescription = "Game Challenge", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Game 1v1", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // Sticker & GIF Package Store
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            onOpenStickerDrawer()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EmojiEmotions, contentDescription = "Stickers & GIFs", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Stickers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Main Telegram Input Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Telegram Paperclip (📎) Attachment Button
            IconButton(
                onClick = {
                    hapticManager.performTelegramDmContextMenu(view)
                    showAttachmentMenu = !showAttachmentMenu
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attachments",
                    tint = if (showAttachmentMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Telegram Sticker / GIF Drawer Toggle Button (😊 / 🎭)
            IconButton(
                onClick = {
                    hapticManager.performTelegramDmContextMenu(view)
                    onOpenStickerDrawer()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Text(text = "🎭", fontSize = 20.sp)
            }

            // View-Once Flame (🔥) Toggle Button
            IconButton(
                onClick = {
                    hapticManager.performTelegramDmContextMenu(view)
                    onToggleViewOnce()
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSendViewOnce) Color(0xFFEF4444).copy(alpha = 0.2f) else Color.Transparent
                    )
            ) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = "View Once Flame",
                    tint = if (isSendViewOnce) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Telegram Input Text Box
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = onMessageChange,
                        placeholder = {
                            Text(
                                text = if (isInputBlocked) "3-message limit reached..." else placeholder,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        enabled = !isInputBlocked,
                        maxLines = 4
                    )
                }
            }

            // Morphing Action: Send Airplane (✈️) or Microphone (🎤) / Like (👍)
            if (messageText.isNotBlank()) {
                // Telegram Paper Airplane Send Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onSendMessage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Voice Note Recording
                    IconButton(
                        onClick = onToggleVoiceRecording,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color(0xFFEF4444) else Color.Transparent)
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice Note",
                            tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Quick Send Like (👍)
                    IconButton(
                        onClick = onSendLike,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(text = "👍", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
