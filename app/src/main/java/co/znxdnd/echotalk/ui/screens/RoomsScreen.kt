package co.znxdnd.echotalk.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyRow
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.ChatRoom
import co.znxdnd.echotalk.data.personalization.PersonalizationManager
import co.znxdnd.echotalk.data.personalization.SmartPersonalizationEngine
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.ui.components.AudioWaveformBar
import co.znxdnd.echotalk.ui.components.EchoConfirmationSnackbarHost
import co.znxdnd.echotalk.ui.components.InteractiveFilterPill
import co.znxdnd.echotalk.ui.components.InteractiveSemanticButton
import co.znxdnd.echotalk.ui.components.LocalFloatingBottomBarClearance
import co.znxdnd.echotalk.ui.components.SemanticActionRole
import co.znxdnd.echotalk.ui.components.RoomAudioWaveVisualizer
import co.znxdnd.echotalk.ui.components.SpeakerActivityState
import co.znxdnd.echotalk.ui.components.SpeakerAvatarWaveHalo
import co.znxdnd.echotalk.ui.components.PublicGroupChatTimelineView
import co.znxdnd.echotalk.ui.components.rememberEchoConfirmationController
import co.znxdnd.echotalk.ui.theme.ThemeManager
import co.znxdnd.echotalk.audio.SoundEffectManager
import co.znxdnd.echotalk.utils.EchoHapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RoomsScreen(
    repository: EchoTalkRepository,
    onOpenDoodle: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val rooms by repository.rooms.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val activeRoomId by repository.activeRoomId.collectAsState()
    val personalizationManager = remember(context) { PersonalizationManager.getInstance(context) }
    val personalizationConfig by personalizationManager.config.collectAsState()
    val confirmationController = rememberEchoConfirmationController()

    var showAdminPanel by remember { mutableStateOf(false) }
    var showMembersDrawer by remember { mutableStateOf(false) }
    var messageText by remember(activeRoomId) {
        mutableStateOf(
            if (activeRoomId != null) personalizationManager.getRoomDraft(activeRoomId!!) else ""
        )
    }
    var isMicMuted by remember { mutableStateOf(true) }
    var isHandRaised by remember { mutableStateOf(false) }
    var floatingReactions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showVoiceRoomChat by remember { mutableStateOf(false) }
    var showVipPasscodeDialog by remember { mutableStateOf(false) }
    var vipPasscodeInput by remember { mutableStateOf("") }
    var vipPasscodeError by remember { mutableStateOf("") }
    val isRoleGatewayUnlocked by repository.isRoleGatewayUnlocked.collectAsState()
    val isUserAdminOrOwner = isRoleGatewayUnlocked && (currentUser.role == "owner" || currentUser.role == "admin")
    var vipUnlocked by remember(isUserAdminOrOwner) { mutableStateOf(isUserAdminOrOwner) }
    var selectedCategory by remember {
        mutableStateOf(
            if (personalizationConfig.personalizationEnabled) personalizationConfig.lastRoomCategory
            else "All Spaces"
        )
    }

    val activeRoom = rooms.find { it.id == activeRoomId }

    val savedRoomsScroll = remember { repository.getScrollPosition("tab:rooms") }
    val roomsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedRoomsScroll.first,
        initialFirstVisibleItemScrollOffset = savedRoomsScroll.second
    )

    DisposableEffect(Unit) {
        onDispose {
            repository.saveScrollPosition(
                "tab:rooms",
                roomsListState.firstVisibleItemIndex,
                roomsListState.firstVisibleItemScrollOffset
            )
        }
    }

    LaunchedEffect(roomsListState.firstVisibleItemIndex, roomsListState.firstVisibleItemScrollOffset) {
        repository.saveScrollPosition(
            "tab:rooms",
            roomsListState.firstVisibleItemIndex,
            roomsListState.firstVisibleItemScrollOffset
        )
    }

    LaunchedEffect(activeRoomId) {
        if (activeRoomId != null) {
            val savedDraft = personalizationManager.getRoomDraft(activeRoomId!!)
            if (savedDraft.isNotBlank()) {
                confirmationController.show("Draft restored.")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    // -------------------------------------------------------------
    // FULL-SCREEN ROOM VIEW (Dedicated 100% immersive mode)
    // -------------------------------------------------------------
    if (activeRoom != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Slim borderless Room Header Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                repository.setActiveRoom(null)
                                showAdminPanel = false
                                showMembersDrawer = false
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Rooms", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = activeRoom.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                if (activeRoom.isSpecial) {
                                    Text(
                                        text = "VIP 48H",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x33FBBF24))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                if (activeRoom.locked) {
                                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                                }
                            }
                            Text(
                                text = "${activeRoom.participants.size} inside • ${if (activeRoom.isOpen) "Open" else "Closed"}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Top Controls: Members Drawer & Admin Button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val next = !showMembersDrawer
                                    showMembersDrawer = next
                                    if (next) {
                                        SoundEffectManager.getInstance(context).playOpenDrawer()
                                    }
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text(text = "${activeRoom.participants.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isUserAdminOrOwner) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showAdminPanel = !showAdminPanel },
                                color = if (showAdminPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = "Admin",
                                        tint = if (showAdminPanel) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Admin",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showAdminPanel) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)
            }

            // Collapsible Admin Panel
            AnimatedVisibility(visible = showAdminPanel && isUserAdminOrOwner) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Administrator Deck: ${activeRoom.name}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = "Enforcing app_logic rules", fontSize = 10.sp, color = Color.Gray)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Lock / Unlock Button (Only special room can be locked!)
                            InteractiveSemanticButton(
                                onClick = {
                                    if (activeRoom.locked) repository.unlockRoom(activeRoom.id)
                                    else repository.lockRoom(activeRoom.id)
                                },
                                enabled = activeRoom.isSpecial,
                                role = if (activeRoom.locked) SemanticActionRole.CONFIRM_BLUE else SemanticActionRole.NEUTRAL,
                                text = if (activeRoom.locked) "Unlock Room" else "Lock Room",
                                icon = if (activeRoom.locked) Icons.Default.LockOpen else Icons.Default.Lock,
                                fontSize = 11.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            )

                            // Open / Close Room
                            InteractiveSemanticButton(
                                onClick = {
                                    if (activeRoom.isOpen) repository.closeRoom(activeRoom.id)
                                    else repository.openRoom(activeRoom.id)
                                },
                                role = if (activeRoom.isOpen) SemanticActionRole.CONFIRM_BLUE else SemanticActionRole.NEUTRAL,
                                text = if (activeRoom.isOpen) "Close Room" else "Open Room",
                                fontSize = 11.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            )

                            // Cleanup Messages (Destructive Action)
                            InteractiveSemanticButton(
                                onClick = { repository.cleanupRoomMessages(activeRoom.id) },
                                role = SemanticActionRole.DESTRUCTIVE_RED,
                                text = "Cleanup",
                                icon = Icons.Default.Delete,
                                fontSize = 11.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Members Drawer
            AnimatedVisibility(visible = showMembersDrawer) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Active Room Participants (${activeRoom.participants.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeRoom.participants.forEach { person ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(text = person, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        if (isUserAdminOrOwner && person != currentUser.name) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Kick",
                                                tint = Color(0xFFFB7185),
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { repository.kickUserFromRoom(activeRoom.id, person) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Slim Single-Line Room Status Strips
            if (activeRoom.locked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22FBBF24))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                    Text(
                        text = "Room Locked • Messaging paused by admin",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFBBF24),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            if (!activeRoom.isOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22FB7185))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFB7185), modifier = Modifier.size(13.dp))
                    Text(
                        text = "Room Closed • Closed by administrator",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFB7185),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Voice Room Live Interactive Stage vs Text Chat Stream
            if (activeRoom.type == "voice") {
                // Voice Stage vs Room Chat Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { showVoiceRoomChat = false },
                        color = if (!showVoiceRoomChat) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "🎙️ Live Stage",
                                fontSize = 11.5.sp,
                                fontWeight = if (!showVoiceRoomChat) FontWeight.Bold else FontWeight.Medium,
                                color = if (!showVoiceRoomChat) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { showVoiceRoomChat = true },
                        color = if (showVoiceRoomChat) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "💬 Room Chat (${activeRoom.messages.size})",
                                fontSize = 11.5.sp,
                                fontWeight = if (showVoiceRoomChat) FontWeight.Bold else FontWeight.Medium,
                                color = if (showVoiceRoomChat) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (showVoiceRoomChat) {
                    val roomScrollKey = "room:${activeRoom.id}"
                    val savedRoomScroll = remember(roomScrollKey) { repository.getScrollPosition(roomScrollKey) }
                    val lastReadId = remember(roomScrollKey) { repository.getLastReadMessageId(roomScrollKey) }
                    PublicGroupChatTimelineView(
                        messages = activeRoom.messages,
                        currentUserName = currentUser.name,
                        messageText = messageText,
                        onMessageTextChange = { updated ->
                            messageText = updated
                            personalizationManager.saveRoomDraft(activeRoom.id, updated)
                        },
                        onSendMessage = { replyTo ->
                            val trimmed = messageText.trim()
                            if (trimmed.isNotEmpty() && !repository.isDuplicateSubmission(roomScrollKey, trimmed)) {
                                val sent = repository.sendRoomMessage(
                                    roomId = activeRoom.id,
                                    text = trimmed,
                                    replyToId = replyTo?.id,
                                    replyToSender = replyTo?.sender,
                                    replyToText = replyTo?.text
                                )
                                if (sent) {
                                    EchoHapticManager.getInstance(context).performMessageSent(view)
                                    SoundEffectManager.getInstance(context).playSendMessage()
                                    messageText = ""
                                    personalizationManager.saveRoomDraft(activeRoom.id, "")
                                } else {
                                    val failed = repository.recordFailedRoomMessage(
                                        roomId = activeRoom.id,
                                        text = trimmed,
                                        replyToId = replyTo?.id,
                                        replyToSender = replyTo?.sender,
                                        replyToText = replyTo?.text
                                    )
                                    messageText = ""
                                    personalizationManager.saveRoomDraft(activeRoom.id, "")
                                    EchoHapticManager.getInstance(context).performAlertReceived(view)
                                    confirmationController.show(
                                        message = "Something went wrong. Try again.",
                                        actionLabel = if (failed != null) "Retry" else null,
                                        onAction = if (failed != null) {
                                            { repository.retryFailedRoomMessage(activeRoom.id, failed.id) }
                                        } else null
                                    )
                                }
                            }
                        },
                        onReactMessage = { msg, emoji ->
                            val alreadyReacted = msg.userReactions[emoji]?.contains(currentUser.name) == true
                            repository.toggleRoomMessageReaction(activeRoom.id, msg.id, emoji, currentUser.name)
                            EchoHapticManager.getInstance(context).performPixelClick(view)
                            if (!alreadyReacted) {
                                confirmationController.show("Reaction added.")
                            }
                        },
                        onDeleteMessage = { msg ->
                            val idx = activeRoom.messages.indexOfFirst { it.id == msg.id }.coerceAtLeast(0)
                            repository.deleteRoomMessage(activeRoom.id, msg.id)
                            EchoHapticManager.getInstance(context).performTelegramMediaDelete(view)
                            confirmationController.show(
                                message = "Message deleted.",
                                actionLabel = "Undo",
                                onAction = {
                                    repository.restoreRoomMessage(activeRoom.id, msg, idx)
                                }
                            )
                        },
                        isLocked = activeRoom.locked || !activeRoom.isOpen,
                        roomKey = roomScrollKey,
                        initialScrollIndex = savedRoomScroll.first,
                        initialScrollOffset = savedRoomScroll.second,
                        onScrollPositionChanged = { idx, off ->
                            repository.saveScrollPosition(roomScrollKey, idx, off)
                        },
                        lastReadMessageId = lastReadId,
                        onMarkRead = { lastId ->
                            repository.markConversationRead(roomScrollKey, lastId)
                        },
                        onCopySuccess = {
                            confirmationController.show("Message copied.")
                        },
                        onRetryFailed = { failedMsg ->
                            val ok = repository.retryFailedRoomMessage(activeRoom.id, failedMsg.id)
                            if (!ok) {
                                confirmationController.show("Something went wrong. Try again.")
                            }
                        },
                        messageTextSizeSp = personalizationConfig.messageTextSizeSp,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // VOICE STAGE VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                    val defaultRemoteSpeaker = activeRoom.speakers.firstOrNull() ?: "Chloe Martin"
                    var activeSpeakerName by remember { mutableStateOf(defaultRemoteSpeaker) }
                    val currentSpeaker = if (!isMicMuted) "You" else activeSpeakerName
                    val isUserSpeaking = !isMicMuted
                    val isRemoteSpeaking = isMicMuted
                    val speakerActivity = remember(currentSpeaker, isMicMuted) {
                        SpeakerActivityState(
                            speakerName = currentSpeaker,
                            isSpeaking = if (currentSpeaker == "You") !isMicMuted else true,
                            amplitude = if (currentSpeaker == "You" && isMicMuted) 0.08f else 0.84f,
                            dBLevel = if (currentSpeaker == "You" && isMicMuted) -48 else -16,
                            dominantFrequencyHz = if (currentSpeaker == "You") 280 else 340
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Speakers on Stage",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "🔊 HD Spatial Audio",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Speakers Pods Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // User Pod
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        // Tap pod to toggle mic
                                        isMicMuted = !isMicMuted
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 3.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SpeakerAvatarWaveHalo(
                                        isSpeaking = isUserSpeaking,
                                        haloColor = Color(0xFF10B981),
                                        modifier = Modifier.size(58.dp)
                                    ) {
                                        Box(modifier = Modifier.size(54.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "You", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isMicMuted) Color(0xFFE11D48) else Color(0xFF10B981))
                                                    .align(Alignment.BottomEnd),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(text = "You", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (isUserSpeaking) "Speaking Live" else "Mic Muted",
                                        fontSize = 9.sp,
                                        color = if (isUserSpeaking) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                        fontWeight = if (isUserSpeaking) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            // Remote Speakers
                            activeRoom.speakers.take(2).forEach { spName ->
                                val isThisRemoteSpeaking = isRemoteSpeaking && (spName == activeSpeakerName)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            activeSpeakerName = spName
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 3.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SpeakerAvatarWaveHalo(
                                            isSpeaking = isThisRemoteSpeaking,
                                            haloColor = Color(0xFF38BDF8),
                                            modifier = Modifier.size(58.dp)
                                        ) {
                                            Box(modifier = Modifier.size(54.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                    .background(Color(0xFF818CF8)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = spName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                                    .align(Alignment.BottomEnd)
                                            )
                                        }
                                        }
                                        Text(text = spName.split(" ")[0], fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(
                                            text = if (isThisRemoteSpeaking) "Speaking" else "Listening",
                                            fontSize = 9.sp,
                                            color = if (isThisRemoteSpeaking) Color(0xFF38BDF8) else Color.Gray,
                                            fontWeight = if (isThisRemoteSpeaking) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Real-Time Dynamic Wave Visualizer Component (Fluid Wave, Spectrum, and Orbital)
                        RoomAudioWaveVisualizer(
                            speakerState = speakerActivity,
                            onSpeakerClick = {
                                val nextSpeaker = activeRoom.speakers.firstOrNull { it != activeSpeakerName } ?: "Chloe Martin"
                                activeSpeakerName = nextSpeaker
                            }
                        )
                    }

                    // Voice Controls Dock
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Emoji soundboard reactions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("🔥", "👏", "🎉", "❤️", "⚡", "✨").forEach { emoji ->
                                Surface(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { floatingReactions = floatingReactions + emoji },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        // Mic Toggle & Hand Raise
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { isMicMuted = !isMicMuted },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMicMuted) Color(0xFFE11D48) else Color(0xFF10B981)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = if (isMicMuted) "Unmute Mic" else "Mic Live", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { isHandRaised = !isHandRaised },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(text = if (isHandRaised) "✋ Raised" else "✋ Raise Hand", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                }
            } else {
                // DEDICATED PUBLIC GROUP CHAT TIMELINE (MAIN ROOM, ENGLISH PRACTICE, VIP, ETC.)
                val roomScrollKey = "room:${activeRoom.id}"
                val savedRoomScroll = remember(roomScrollKey) { repository.getScrollPosition(roomScrollKey) }
                val lastReadId = remember(roomScrollKey) { repository.getLastReadMessageId(roomScrollKey) }
                PublicGroupChatTimelineView(
                    messages = activeRoom.messages,
                    currentUserName = currentUser.name,
                    messageText = messageText,
                    onMessageTextChange = { updated ->
                        messageText = updated
                        personalizationManager.saveRoomDraft(activeRoom.id, updated)
                    },
                    onSendMessage = { replyTo ->
                        val trimmed = messageText.trim()
                        if (trimmed.isNotEmpty() && !repository.isDuplicateSubmission(roomScrollKey, trimmed)) {
                            val sent = repository.sendRoomMessage(
                                roomId = activeRoom.id,
                                text = trimmed,
                                replyToId = replyTo?.id,
                                replyToSender = replyTo?.sender,
                                replyToText = replyTo?.text
                            )
                            if (sent) {
                                EchoHapticManager.getInstance(context).performMessageSent(view)
                                SoundEffectManager.getInstance(context).playSendMessage()
                                messageText = ""
                                personalizationManager.saveRoomDraft(activeRoom.id, "")
                            } else {
                                val failed = repository.recordFailedRoomMessage(
                                    roomId = activeRoom.id,
                                    text = trimmed,
                                    replyToId = replyTo?.id,
                                    replyToSender = replyTo?.sender,
                                    replyToText = replyTo?.text
                                )
                                messageText = ""
                                personalizationManager.saveRoomDraft(activeRoom.id, "")
                                EchoHapticManager.getInstance(context).performAlertReceived(view)
                                confirmationController.show(
                                    message = "Something went wrong. Try again.",
                                    actionLabel = if (failed != null) "Retry" else null,
                                    onAction = if (failed != null) {
                                        { repository.retryFailedRoomMessage(activeRoom.id, failed.id) }
                                    } else null
                                )
                            }
                        }
                    },
                    onReactMessage = { msg, emoji ->
                        val alreadyReacted = msg.userReactions[emoji]?.contains(currentUser.name) == true
                        repository.toggleRoomMessageReaction(activeRoom.id, msg.id, emoji, currentUser.name)
                        EchoHapticManager.getInstance(context).performPixelClick(view)
                        if (!alreadyReacted) {
                            confirmationController.show("Reaction added.")
                        }
                    },
                    onDeleteMessage = { msg ->
                        val idx = activeRoom.messages.indexOfFirst { it.id == msg.id }.coerceAtLeast(0)
                        repository.deleteRoomMessage(activeRoom.id, msg.id)
                        EchoHapticManager.getInstance(context).performTelegramMediaDelete(view)
                        confirmationController.show(
                            message = "Message deleted.",
                            actionLabel = "Undo",
                            onAction = {
                                repository.restoreRoomMessage(activeRoom.id, msg, idx)
                            }
                        )
                    },
                    isLocked = activeRoom.locked || !activeRoom.isOpen,
                    roomKey = roomScrollKey,
                    initialScrollIndex = savedRoomScroll.first,
                    initialScrollOffset = savedRoomScroll.second,
                    onScrollPositionChanged = { idx, off ->
                        repository.saveScrollPosition(roomScrollKey, idx, off)
                    },
                    lastReadMessageId = lastReadId,
                    onMarkRead = { lastId ->
                        repository.markConversationRead(roomScrollKey, lastId)
                    },
                    onCopySuccess = {
                        confirmationController.show("Message copied.")
                    },
                    onRetryFailed = { failedMsg ->
                        val ok = repository.retryFailedRoomMessage(activeRoom.id, failedMsg.id)
                        if (!ok) {
                            confirmationController.show("Something went wrong. Try again.")
                        }
                    },
                    messageTextSizeSp = personalizationConfig.messageTextSizeSp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        // -------------------------------------------------------------
        // ROOMS LIST VIEW (Clean, 4 sleek room cards)
        // -------------------------------------------------------------
        val filteredRooms = when (selectedCategory) {
            "🎙️ Audio Rooms" -> rooms.filter { it.type == "voice" }
            "💬 Text Lounges" -> rooms.filter { it.type == "text" && !it.isSpecial }
            "👑 VIP Lounge" -> rooms.filter { it.isSpecial }
            else -> rooms
        }
        val orderedRooms = remember(filteredRooms, personalizationConfig) {
            SmartPersonalizationEngine.rankRooms(filteredRooms, personalizationConfig)
        }

        val floatingBottomClearance = LocalFloatingBottomBarClearance.current

        LazyColumn(
            state = roomsListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = floatingBottomClearance + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rooms",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${rooms.size} Spaces",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                val categories = listOf("All Spaces", "🎙️ Audio Rooms", "💬 Text Lounges", "👑 VIP Lounge")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(categories) { cat ->
                        InteractiveFilterPill(
                            label = cat,
                            isSelected = selectedCategory == cat,
                            onSelect = {
                                selectedCategory = cat
                                personalizationManager.setLastRoomCategory(cat)
                            }
                        )
                    }
                }
            }

            // Sleek room cards with theme-aware interaction styling
            items(orderedRooms, key = { it.id }) { room ->
                val isVoice = room.type == "voice"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                        .clickable {
                            if (room.id == "r-special" && !vipUnlocked && currentUser.role == "member") {
                                showVipPasscodeDialog = true
                            } else {
                                personalizationManager.recordRoomVisit(room.id)
                                repository.setActiveRoom(room.id)
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Icon Box
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isVoice) Color(0x33FB7185)
                                        else if (room.isSpecial) Color(0x33FBBF24)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isVoice) "🎙️" else if (room.isSpecial) "👑" else "💬",
                                    fontSize = 22.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = room.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (room.isSpecial) {
                                        Text(
                                            text = "Special 2-Day",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0x33FBBF24))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = room.subtitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = room.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Join Room",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Modal for VIP Passcode
    if (showVipPasscodeDialog) {
        Dialog(onDismissRequest = { showVipPasscodeDialog = false }) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "VIP Lounge Passcode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enter 4-digit VIP passkey to unlock (Try 7777 or Owner Code 9999)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = vipPasscodeInput,
                        onValueChange = { vipPasscodeInput = it },
                        placeholder = { Text("7777") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (vipPasscodeError.isNotEmpty()) {
                        Text(text = vipPasscodeError, fontSize = 11.sp, color = Color(0xFFFB7185))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showVipPasscodeDialog = false }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (vipPasscodeInput == "7777" || vipPasscodeInput == "9999" || vipPasscodeInput == "2024") {
                                    vipUnlocked = true
                                    showVipPasscodeDialog = false
                                    personalizationManager.recordRoomVisit("r-special")
                                    repository.setActiveRoom("r-special")
                                    vipPasscodeError = ""
                                } else {
                                    vipPasscodeError = "Invalid VIP Passcode (Try 7777 or 9999)"
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Unlock")
                        }
                    }
                }
            }
        }
    }

    val snackbarBottomOffset = if (activeRoom != null) 78.dp else (LocalFloatingBottomBarClearance.current + 10.dp)
    EchoConfirmationSnackbarHost(
        hostState = confirmationController.hostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = snackbarBottomOffset,
                start = 14.dp,
                end = 14.dp
            )
    )
    }
}
