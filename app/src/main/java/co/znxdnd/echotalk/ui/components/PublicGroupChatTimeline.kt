package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Deterministically generates a subtle, tasteful sender identity color
 * to distinguish participants without loud or distracting bubbles.
 */
fun getSenderIdentityColor(senderName: String): Color {
    val palette = listOf(
        Color(0xFF38BDF8), // Sky Blue
        Color(0xFF34D399), // Mint Green
        Color(0xFFF472B6), // Soft Rose
        Color(0xFFA78BFA), // Lavender Purple
        Color(0xFFFBBF24), // Amber Gold
        Color(0xFF818CF8), // Indigo
        Color(0xFF2DD4BF), // Teal
        Color(0xFFFB923C)  // Peach
    )
    val hash = senderName.fold(0) { acc, c -> acc * 31 + c.code }
    return palette[hash.absoluteValue % palette.size]
}

/**
 * Dedicated Public Group Chat Message Component.
 * Visually distinct from private DMs:
 * - Left-aligned clean community timeline.
 * - Consecutively sent messages grouped together (single avatar & header).
 * - Subtle sender accents, neutral theme-aware surfaces.
 * - Full swipe-to-reply and long-press action menu support.
 * - Inline quotes referencing parent message with tap-to-scroll & flash highlight.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PublicGroupChatMessageItem(
    message: ChatMessage,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    isHighlighted: Boolean,
    allMessages: List<ChatMessage>,
    currentUserName: String,
    onReply: (ChatMessage) -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onJumpToMessage: (String) -> Unit,
    onCopySuccess: () -> Unit = {},
    onRetryFailed: () -> Unit = {},
    messageTextSizeSp: Float = 13.5f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showActionMenu by remember { mutableStateOf(false) }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val isMe = message.sender == currentUserName
    val senderColor = remember(message.sender) { getSenderIdentityColor(message.sender) }

    // Swipe-to-reply horizontal gesture physics
    val draggableState = rememberDraggableState { delta ->
        val newOffset = (swipeOffset + delta).coerceIn(0f, 120f)
        swipeOffset = newOffset
    }

    LaunchedEffect(swipeOffset) {
        if (swipeOffset >= 80f) {
            EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
            PoliteSoundSynthesizer.playPopSound()
            onReply(message)
            swipeOffset = 0f
        }
    }

    // Animated highlight flash when user jumps to this message
    val animatedHighlightAlpha by animateColorAsState(
        targetValue = if (isHighlighted) Color(0x44FBBF24) else Color.Transparent,
        animationSpec = tween(durationMillis = EchoAnimations.scaledDuration(350)),
        label = "highlightFlash"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(animatedHighlightAlpha)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (swipeOffset < 80f) {
                        swipeOffset = 0f
                    }
                }
            )
            .combinedClickable(
                onClick = { showActionMenu = !showActionMenu },
                onDoubleClick = {
                    onReact(message, "❤️")
                    EchoHapticManager.getInstance(context).performMessengerEmojiSelect(view, "❤️")
                },
                onLongClick = {
                    showActionMenu = true
                    EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                }
            )
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = if (isFirstInGroup) 6.dp else 1.5.dp,
                bottom = if (isLastInGroup) 6.dp else 1.5.dp
            )
            .testTag("public_msg_${message.id}")
    ) {
        // Swipe to reply indicator visible when dragging
        if (swipeOffset > 10f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((swipeOffset * 0.4f).roundToInt(), 0) }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(senderColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Swipe Reply",
                    tint = senderColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffset.roundToInt(), 0) },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Group Avatar: Shown only for the first message in consecutive group
            if (isFirstInGroup) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(senderColor.copy(alpha = 0.2f))
                        .border(1.dp, senderColor.copy(alpha = 0.45f), CircleShape)
                        .testTag("public_avatar_${message.sender}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.sender.take(1).uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = senderColor
                    )
                }
            } else {
                // Spacer reserving avatar column width for aligned indentation
                Spacer(modifier = Modifier.size(34.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Main Message Content Block
            Column(modifier = Modifier.weight(1f)) {
                // Header (Sender Name + Role Badge + Timestamp) - only for first message in group
                if (isFirstInGroup) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = message.sender,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = senderColor,
                            maxLines = 1
                        )
                        if (isMe) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "YOU",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (message.status == "sending") "Sending..." else message.timestamp,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        )
                    }
                }

                // Quoted Parent Message Snippet (if this message is a reply)
                if (!message.replyToId.isNullOrBlank()) {
                    val parentMsg = remember(message.replyToId, allMessages) {
                        allMessages.find { it.id == message.replyToId }
                    }
                    val replyAuthor = message.replyToSender ?: parentMsg?.sender ?: "Someone"
                    val replyText = parentMsg?.text ?: message.replyToText ?: "[Original message unavailable]"
                    val quoteColor = remember(replyAuthor) { getSenderIdentityColor(replyAuthor) }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                message.replyToId?.let { targetId ->
                                    onJumpToMessage(targetId)
                                }
                            }
                            .testTag("quoted_reply_snippet_${message.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Vertical accent line
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(quoteColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = replyAuthor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = quoteColor,
                                    maxLines = 1
                                )
                                Text(
                                    text = replyText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Message Body Surface (Neutral, theme-aware, restrained)
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isFirstInGroup) 4.dp else 12.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isLastInGroup) 14.dp else 4.dp,
                        bottomEnd = 14.dp
                    ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        if (message.isAudio) {
                            AudioWaveformBar(
                                duration = message.audioDuration ?: "0:05",
                                isSentByMe = false,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        } else {
                            Text(
                                text = message.text,
                                fontSize = messageTextSizeSp.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = (messageTextSizeSp + 5.5f).sp
                            )
                        }

                        // Subtle right-aligned timestamp for subsequent messages in a group
                        if (!isFirstInGroup) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = if (message.status == "sending") "Sending..." else message.timestamp,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                if (isMe && message.status == "failed") {
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
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

                // Tasteful Compact Reaction Badges
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag("reactions_row_${message.id}")
                    ) {
                        message.reactions.forEach { (emoji, count) ->
                            if (count > 0) {
                                val hasReacted = message.userReactions[emoji]?.contains(currentUserName) == true
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (hasReacted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    border = if (hasReacted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onReact(message, emoji) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(text = emoji, fontSize = 11.sp)
                                        Text(
                                            text = "$count",
                                            fontSize = 10.sp,
                                            fontWeight = if (hasReacted) FontWeight.Bold else FontWeight.Medium,
                                            color = if (hasReacted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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

    // iOS-Style Long-Press Action Menu with Messenger Emoji Bar
    if (showActionMenu) {
        MessengerFloatingTray(
            message = message,
            isMe = isMe,
            onReactionSelect = { emoji ->
                onReact(message, emoji)
                showActionMenu = false
            },
            onReply = {
                showActionMenu = false
                onReply(message)
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(message.text))
                onCopySuccess()
                showActionMenu = false
            },
            onUnsend = {
                showActionMenu = false
                onDelete(message)
            },
            onDelete = {
                showActionMenu = false
                onDelete(message)
            },
            onTranslate = {
                showActionMenu = false
            },
            onDismiss = {
                showActionMenu = false
            }
        )
    }
}

/**
 * Dedicated Public Room Message Composer.
 * Fixed at bottom with smooth input, emoji shortcut, reply preview banner, and send button.
 */
@Composable
fun PublicGroupChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    replyingTo: ChatMessage?,
    onCancelReply: () -> Unit,
    isLocked: Boolean,
    onTapQuoted: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var swipeDismissOffset by remember(replyingTo?.id) { mutableFloatStateOf(0f) }
    val dismissThreshold = 70f
    val draggableState = rememberDraggableState { delta ->
        swipeDismissOffset = (swipeDismissOffset + delta).coerceIn(-160f, 160f)
    }

    LaunchedEffect(swipeDismissOffset) {
        if (kotlin.math.abs(swipeDismissOffset) >= dismissThreshold) {
            EchoHapticManager.getInstance(context).performPixelClick(view)
            PoliteSoundSynthesizer.playPopSound()
            onCancelReply()
            swipeDismissOffset = 0f
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Reply Preview Banner with Swipe-to-Dismiss and Tap-to-Scroll
            if (replyingTo != null) {
                val authorColor = remember(replyingTo.sender) { getSenderIdentityColor(replyingTo.sender) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(swipeDismissOffset.roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = (1f - (kotlin.math.abs(swipeDismissOffset) / 90f)).coerceIn(0.2f, 1f)
                        }
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                if (kotlin.math.abs(swipeDismissOffset) < dismissThreshold) {
                                    swipeDismissOffset = 0f
                                }
                            }
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onTapQuoted?.invoke(replyingTo.id)
                        }
                        .testTag("quoted_reply_preview_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(authorColor)
                            )
                            Text(
                                text = "${replyingTo.sender}: ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = authorColor,
                                maxLines = 1
                            )
                            Text(
                                text = replyingTo.text,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel Reply",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = if (isLocked) "Room is locked by administrator" else "Message public room...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    enabled = !isLocked,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 110.dp)
                        .testTag("public_room_input_field")
                )

                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !isLocked,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (text.isNotBlank() && !isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (text.isNotBlank() && !isLocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("public_room_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tasteful subtle date separator for the public group chat timeline.
 */
@Composable
fun PublicRoomDateDivider(dateText: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = dateText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )
    }
}

/**
 * Complete Public Group Chat Timeline with Message List and Integrated Composer.
 * Visually distinct from private DMs, with consecutive message grouping, swipe-to-reply,
 * parent reference quotes, reactions, tap-to-scroll & flash highlight.
 */
@Composable
fun PublicGroupChatTimelineView(
    messages: List<ChatMessage>,
    currentUserName: String,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: (replyTo: ChatMessage?) -> Unit,
    onReactMessage: (ChatMessage, String) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit,
    isLocked: Boolean,
    roomKey: String = "",
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> },
    lastReadMessageId: String? = null,
    onMarkRead: (String) -> Unit = {},
    onCopySuccess: () -> Unit = {},
    onRetryFailed: (ChatMessage) -> Unit = {},
    messageTextSizeSp: Float = 13.5f,
    modifier: Modifier = Modifier
) {
    val startIndex = remember(roomKey) {
        when {
            initialScrollIndex > 0 -> initialScrollIndex.coerceAtMost(messages.size.coerceAtLeast(0))
            messages.isNotEmpty() -> messages.size
            else -> 0
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startIndex,
        initialFirstVisibleItemScrollOffset = if (initialScrollIndex > 0) initialScrollOffset else 0
    )
    val coroutineScope = rememberCoroutineScope()
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    val isNearBottom by remember(messages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 1) true
            else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 2
            }
        }
    }

    var unreadWhileScrolledUp by remember(roomKey) { mutableIntStateOf(0) }
    var previousMessageCount by remember(roomKey) { mutableIntStateOf(messages.size) }

    val newMessagesDividerIndex = remember(messages, lastReadMessageId) {
        if (lastReadMessageId.isNullOrBlank()) -1
        else {
            val idx = messages.indexOfFirst { it.id == lastReadMessageId }
            if (idx >= 0 && idx < messages.lastIndex) idx + 1 else -1
        }
    }

    // Save scroll position on dispose or change
    DisposableEffect(roomKey) {
        onDispose {
            onScrollPositionChanged(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
            messages.lastOrNull()?.id?.let { onMarkRead(it) }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        onScrollPositionChanged(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset
        )
        if (isNearBottom) {
            unreadWhileScrolledUp = 0
        }
    }

    // Only auto-scroll if user is already near bottom or sent the latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val latest = messages.last()
            val addedCount = (messages.size - previousMessageCount).coerceAtLeast(0)
            previousMessageCount = messages.size
            if (isNearBottom || latest.sender == currentUserName) {
                if (EchoAnimations.isMotionReduced()) {
                    listState.scrollToItem(messages.size)
                } else {
                    listState.animateScrollToItem(messages.size)
                }
                unreadWhileScrolledUp = 0
            } else if (addedCount > 0) {
                unreadWhileScrolledUp += addedCount
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Timeline LazyColumn + Jump-to-latest overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    PublicRoomDateDivider(dateText = "Today")
                }

                itemsIndexed(
                    items = messages,
                    key = { _, msg -> msg.id }
                ) { index, msg ->
                    if (index == newMessagesDividerIndex) {
                        ChatNewMessagesDivider()
                    }
                    val isFirstInGroup = index == 0 || messages[index - 1].sender != msg.sender || index == newMessagesDividerIndex
                    val isLastInGroup = index == messages.lastIndex || messages[index + 1].sender != msg.sender

                    PublicGroupChatMessageItem(
                        message = msg,
                        isFirstInGroup = isFirstInGroup,
                        isLastInGroup = isLastInGroup,
                        isHighlighted = msg.id == highlightedMessageId,
                        allMessages = messages,
                        currentUserName = currentUserName,
                        onReply = { targetMsg ->
                            replyingToMessage = targetMsg
                        },
                        onReact = { targetMsg, emoji ->
                            onReactMessage(targetMsg, emoji)
                        },
                        onDelete = { targetMsg ->
                            onDeleteMessage(targetMsg)
                        },
                        onJumpToMessage = { targetId ->
                            val targetIndex = messages.indexOfFirst { it.id == targetId }
                            if (targetIndex >= 0) {
                                coroutineScope.launch {
                                    // Center the original message in the visible viewport (+1 for header item)
                                    listState.animateScrollToItem(index = targetIndex + 1, scrollOffset = -220)
                                    highlightedMessageId = targetId
                                    delay(1600)
                                    highlightedMessageId = null
                                }
                            }
                        },
                        onCopySuccess = onCopySuccess,
                        onRetryFailed = { onRetryFailed(msg) },
                        messageTextSizeSp = messageTextSizeSp
                    )
                }
            }

            JumpToLatestButton(
                visible = !isNearBottom && messages.isNotEmpty(),
                unreadCount = unreadWhileScrolledUp,
                onClick = {
                    coroutineScope.launch {
                        if (messages.isNotEmpty()) {
                            if (EchoAnimations.isMotionReduced()) {
                                listState.scrollToItem(messages.size)
                            } else {
                                listState.animateScrollToItem(messages.size)
                            }
                            unreadWhileScrolledUp = 0
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 10.dp)
            )
        }

        // Dedicated Public Chat Composer with swipe-to-dismiss & tap-to-scroll
        PublicGroupChatComposer(
            text = messageText,
            onTextChange = onMessageTextChange,
            onSend = {
                if (messageText.isNotBlank()) {
                    onSendMessage(replyingToMessage)
                    replyingToMessage = null
                }
            },
            replyingTo = replyingToMessage,
            onCancelReply = { replyingToMessage = null },
            isLocked = isLocked,
            onTapQuoted = { targetId ->
                val targetIndex = messages.indexOfFirst { it.id == targetId }
                if (targetIndex >= 0) {
                    coroutineScope.launch {
                        // Smoothly animate and center the original quoted message in the viewport
                        listState.animateScrollToItem(index = targetIndex + 1, scrollOffset = -220)
                        highlightedMessageId = targetId
                        delay(1600)
                        highlightedMessageId = null
                    }
                }
            }
        )
    }
}
