package co.znxdnd.echotalk.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import co.znxdnd.echotalk.audio.SoundEffectManager
import co.znxdnd.echotalk.audio.VoiceNoteRecorder
import co.znxdnd.echotalk.calling.CallController
import co.znxdnd.echotalk.calling.CallType
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.ui.components.DoodleCanvasDialog
import co.znxdnd.echotalk.ui.components.ForwardMessageDialog
import co.znxdnd.echotalk.ui.components.IosChatBubble
import co.znxdnd.echotalk.ui.components.IosMessageInputDock
import co.znxdnd.echotalk.ui.components.MediaLightboxDialog
import co.znxdnd.echotalk.ui.components.MessengerFloatingTray
import co.znxdnd.echotalk.ui.components.PhotoEditorDialog
import co.znxdnd.echotalk.ui.components.ViewOnceViewerDialog
import co.znxdnd.echotalk.utils.EchoHapticManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * iOS-inspired Material 3 ChatScreen with grouped message bubbles, translucent frosted header,
 * keyword search pill, attachment icon & drawer (Photo Picker, Pro Studio, Sketch, View-Once),
 * voice note recording, media lightbox, and conversation drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: EchoTalkRepository = EchoTalkRepository.getInstance(),
    recipientId: String = "u-lucas",
    recipientName: String = "Lucas Silva",
    recipientAvatar: String = "",
    onBack: (() -> Unit)? = null,
    onViewProfile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val soundEffectManager = remember(context) { SoundEffectManager.getInstance(context) }
    val hapticManager = remember(context) { EchoHapticManager.getInstance(context) }
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val liveVoiceLevel by voiceRecorder.liveLevel.collectAsState()

    val currentUser by repository.currentUser.collectAsState()
    val users by repository.users.collectAsState()
    val localDms by repository.dms.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedMessageForMenu by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToForward by remember { mutableStateOf<ChatMessage?>(null) }
    var lightboxMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var viewOnceMessage by remember { mutableStateOf<ChatMessage?>(null) }

    var messageText by remember { mutableStateOf("") }
    var isViewOnce by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var pendingAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var photoEditorInitialUri by remember { mutableStateOf<Uri?>(null) }
    var showSketchDialog by remember { mutableStateOf(false) }

    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    var isRecording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }

    var showChatDrawer by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSoundMuted by remember { mutableStateOf(soundEffectManager.isMuted()) }

    var lastSeenIncomingMessageId by remember { mutableStateOf<String?>(null) }
    var hasInitializedList by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancelRecording()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordSeconds = 0
            while (isRecording) {
                delay(1000)
                recordSeconds += 1
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            hapticManager.performPixelClick(view)
            pendingAttachmentUri = uri
            showAttachmentMenu = false
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (voiceRecorder.startRecording()) {
                isRecording = true
                hapticManager.performPixelClick(view)
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Unable to start microphone recording")
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission required for voice notes")
            }
        }
    }

    BackHandler(enabled = showAttachmentMenu || isSearchActive || showChatDrawer || onBack != null) {
        when {
            showAttachmentMenu -> showAttachmentMenu = false
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            showChatDrawer -> showChatDrawer = false
            else -> onBack?.invoke()
        }
    }

    val localMessages = localDms[recipientId] ?: emptyList()
    val combinedMessages = remember(localMessages) {
        localMessages.sortedBy { it.timestamp }
    }

    LaunchedEffect(combinedMessages) {
        if (combinedMessages.isNotEmpty()) {
            val latest = combinedMessages.last()
            if (hasInitializedList && latest.sender != currentUser.name && latest.id != lastSeenIncomingMessageId) {
                soundEffectManager.playReceiveMessage()
            }
            if (latest.sender != currentUser.name) {
                lastSeenIncomingMessageId = latest.id
            }
            hasInitializedList = true
        }
    }

    val displayedMessages = remember(combinedMessages, searchQuery) {
        if (searchQuery.isBlank()) combinedMessages
        else combinedMessages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(displayedMessages.size) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.size - 1)
        }
    }

    fun sendCurrentMessage() {
        val trimmed = messageText.trim()
        val stagedUri = pendingAttachmentUri
        if ((trimmed.isBlank() && stagedUri == null) || isSending) return

        val editingTarget = editingMessage
        if (editingTarget != null && trimmed.isNotBlank()) {
            repository.updateDMText(recipientId, editingTarget.id, trimmed)
            hapticManager.performPixelConfirm(view)
            messageText = ""
            editingMessage = null
            return
        }

        isSending = true
        val replyPrefix = replyingToMessage?.let { target ->
            "Replying to ${target.sender}: \"${target.text.take(28)}\"\n"
        }.orEmpty()
        val outgoingText = if (trimmed.isNotBlank()) {
            "$replyPrefix$trimmed"
        } else if (stagedUri != null) {
            if (isViewOnce) "View-Once Photo" else "Photo"
        } else {
            ""
        }

        coroutineScope.launch {
            val resolvedMediaPath = if (stagedUri != null) {
                persistAttachmentUri(context, stagedUri)
            } else {
                null
            }
            val (ok, err) = repository.sendDM(
                recipientId = recipientId,
                text = outgoingText,
                isViewOnce = isViewOnce,
                mediaUrl = resolvedMediaPath
            )
            isSending = false
            if (ok) {
                hapticManager.performMessageSent(view)
                soundEffectManager.playSendMessage()
                messageText = ""
                pendingAttachmentUri = null
                replyingToMessage = null
                isViewOnce = false
                showAttachmentMenu = false
            } else if (!err.isNullOrBlank()) {
                snackbarHostState.showSnackbar(err)
            }
        }
    }

    val primaryGlow = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onViewProfile?.invoke(recipientId) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(38.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (recipientAvatar.isNotBlank()) {
                                        SubcomposeAsyncImage(
                                            model = recipientAvatar,
                                            contentDescription = recipientName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            error = {
                                                Text(
                                                    text = recipientName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 15.sp
                                                )
                                            }
                                        )
                                    } else {
                                        Text(
                                            text = recipientName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(1.5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34C759))
                                )
                            }

                            Column {
                                Text(
                                    text = recipientName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Active now • iMessage Sync",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val partnerUser = users.firstOrNull { it.id == recipientId } ?: User(
                                    id = recipientId,
                                    name = recipientName,
                                    handle = "@${recipientName.lowercase().replace(Regex("[^a-z0-9_]"), "").take(10).ifBlank { "user" }}",
                                    country = "Global",
                                    countryCode = "GL",
                                    gender = "Non-Binary",
                                    age = 24,
                                    online = true,
                                    isFriend = true,
                                    avatarUrl = recipientAvatar
                                )
                                CallController.getInstance().startOutgoingCall(partnerUser, CallType.AUDIO)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_audio_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Audio Call",
                                tint = Color(0xFF10B981)
                            )
                        }

                        IconButton(
                            onClick = {
                                val partnerUser = users.firstOrNull { it.id == recipientId } ?: User(
                                    id = recipientId,
                                    name = recipientName,
                                    handle = "@${recipientName.lowercase().replace(Regex("[^a-z0-9_]"), "").take(10).ifBlank { "user" }}",
                                    country = "Global",
                                    countryCode = "GL",
                                    gender = "Non-Binary",
                                    age = 24,
                                    online = true,
                                    isFriend = true,
                                    avatarUrl = recipientAvatar
                                )
                                CallController.getInstance().startOutgoingCall(partnerUser, CallType.VIDEO)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_video_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_search_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Messages",
                                tint = if (isSearchActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextState = !showChatDrawer
                                showChatDrawer = nextState
                                if (nextState) {
                                    soundEffectManager.playOpenDrawer()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_drawer_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Chat Drawer",
                                tint = if (showChatDrawer) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text("Search in conversation...", fontSize = 13.sp)
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_search_input"),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                                if (searchQuery.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${displayedMessages.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        },
        bottomBar = {
            IosMessageInputDock(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendMessage = { sendCurrentMessage() },
                showAttachmentMenu = showAttachmentMenu,
                onToggleAttachmentMenu = {
                    hapticManager.performTelegramDmContextMenu(view)
                    showAttachmentMenu = !showAttachmentMenu
                },
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenPhotoStudio = {
                    showAttachmentMenu = false
                    photoEditorInitialUri = pendingAttachmentUri
                    showPhotoEditor = true
                },
                onOpenSketchCanvas = {
                    showAttachmentMenu = false
                    showSketchDialog = true
                },
                isViewOnce = isViewOnce,
                onToggleViewOnce = { isViewOnce = !isViewOnce },
                pendingAttachmentUri = pendingAttachmentUri,
                onEditPendingAttachment = {
                    photoEditorInitialUri = pendingAttachmentUri
                    showPhotoEditor = true
                },
                onClearPendingAttachment = { pendingAttachmentUri = null },
                replyingTo = replyingToMessage,
                onCancelReply = { replyingToMessage = null },
                editingMessage = editingMessage,
                onCancelEdit = {
                    editingMessage = null
                    messageText = ""
                },
                isRecording = isRecording,
                recordSeconds = recordSeconds,
                liveVoiceLevel = liveVoiceLevel,
                onStartVoiceRecording = {
                    showAttachmentMenu = false
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        if (voiceRecorder.startRecording()) {
                            isRecording = true
                            hapticManager.performPixelClick(view)
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Unable to start microphone")
                            }
                        }
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopAndSendVoiceRecording = {
                    isRecording = false
                    coroutineScope.launch {
                        val recorded = voiceRecorder.stopAndSave()
                        if (recorded != null) {
                            val (ok, err) = repository.sendDM(
                                recipientId = recipientId,
                                text = "Voice message (${recorded.formattedDuration})",
                                mediaUrl = recorded.filePath,
                                isAudio = true,
                                audioDuration = recorded.formattedDuration,
                                audioWaveform = recorded.waveform
                            )
                            if (ok) {
                                hapticManager.performMessageSent(view)
                                soundEffectManager.playSendMessage()
                            } else if (!err.isNullOrBlank()) {
                                snackbarHostState.showSnackbar(err)
                            }
                        } else {
                            snackbarHostState.showSnackbar("Voice note was too short")
                        }
                    }
                },
                onCancelVoiceRecording = {
                    isRecording = false
                    voiceRecorder.cancelRecording()
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.8f, size.height * 0.14f),
                            radius = size.maxDimension * 0.55f
                        )
                    )
                }
        ) {
            if (displayedMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching messages" else "Start a conversation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No messages contain \"$searchQuery\"."
                            } else {
                                "Send a message or tap the attachment icon to share photos, sketches, or voice notes with $recipientName."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_messages_lazy_column"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    item(key = "session_header_pill") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "Today • Private Thread",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = displayedMessages,
                        key = { index, it -> it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}_$index" } }
                    ) { index, msg ->
                        val isMe = msg.sender == currentUser.name
                        val prevSame = index > 0 && displayedMessages[index - 1].sender == msg.sender
                        val nextSame = index < displayedMessages.lastIndex && displayedMessages[index + 1].sender == msg.sender

                        IosChatBubble(
                            message = msg,
                            isMe = isMe,
                            isFirstInGroup = !prevSame,
                            isLastInGroup = !nextSame,
                            onLongPress = { selectedMessageForMenu = msg },
                            onOpenMedia = { lightboxMessage = it },
                            onOpenViewOnce = { viewOnceMessage = it },
                            onReactionTap = { emoji ->
                                repository.addReactionToDM(recipientId, msg.id, emoji)
                            }
                        )
                    }
                }
            }
        }
    }

    if (selectedMessageForMenu != null) {
        val menuMsg = selectedMessageForMenu!!
        val isMenuMe = menuMsg.sender == currentUser.name
        MessengerFloatingTray(
            message = menuMsg,
            isMe = isMenuMe,
            onReactionSelect = { emoji ->
                repository.addReactionToDM(recipientId, menuMsg.id, emoji)
                selectedMessageForMenu = null
            },
            onReply = {
                replyingToMessage = menuMsg
                editingMessage = null
                selectedMessageForMenu = null
            },
            onForward = {
                messageToForward = menuMsg
                selectedMessageForMenu = null
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Message", menuMsg.text))
                selectedMessageForMenu = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Copied to clipboard")
                }
            },
            onEdit = {
                if (isMenuMe && menuMsg.text.isNotBlank()) {
                    editingMessage = menuMsg
                    replyingToMessage = null
                    messageText = menuMsg.text
                }
                selectedMessageForMenu = null
            },
            onUnsend = {
                repository.deleteDM(recipientId, menuMsg.id)
                selectedMessageForMenu = null
            },
            onDelete = {
                repository.deleteDM(recipientId, menuMsg.id)
                selectedMessageForMenu = null
            },
            onTranslate = {
                selectedMessageForMenu = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Original (${menuMsg.sender}): ${menuMsg.text.take(60)}")
                }
            },
            onDismiss = { selectedMessageForMenu = null }
        )
    }

    if (messageToForward != null) {
        val forwardingMsg = messageToForward!!
        ForwardMessageDialog(
            message = forwardingMsg,
            users = users,
            currentUserId = currentUser.id,
            onForwardTo = { targetUser ->
                val (success, err) = repository.forwardMessage(
                    targetRecipientId = targetUser.id,
                    originalMessage = forwardingMsg
                )
                if (success) {
                    hapticManager.performMessageSent(view)
                    soundEffectManager.playSendMessage()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Message forwarded to ${targetUser.name}")
                    }
                } else if (err != null) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(err)
                    }
                }
                messageToForward = null
            },
            onDismiss = { messageToForward = null }
        )
    }

    if (showPhotoEditor) {
        PhotoEditorDialog(
            initialImageUri = photoEditorInitialUri,
            onSend = { _ -> showPhotoEditor = false },
            onSendRendered = { viewOnce, renderedPath ->
                showPhotoEditor = false
                if (!renderedPath.isNullOrBlank()) {
                    val caption = messageText.trim().ifBlank {
                        if (viewOnce) "View-Once Studio Photo" else "Studio Photo"
                    }
                    val (ok, err) = repository.sendDM(
                        recipientId = recipientId,
                        text = caption,
                        isViewOnce = viewOnce,
                        mediaUrl = renderedPath
                    )
                    if (ok) {
                        hapticManager.performMessageSent(view)
                        soundEffectManager.playSendMessage()
                        messageText = ""
                        pendingAttachmentUri = null
                        photoEditorInitialUri = null
                    } else if (!err.isNullOrBlank()) {
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                }
            },
            onDismiss = {
                showPhotoEditor = false
                photoEditorInitialUri = null
            }
        )
    }

    if (showSketchDialog) {
        DoodleCanvasDialog(
            recipientName = recipientName,
            onDismiss = { showSketchDialog = false },
            onSendDoodle = { _, _ -> showSketchDialog = false },
            onSendRenderedDoodle = { viewOnce, _, renderedPath ->
                showSketchDialog = false
                if (!renderedPath.isNullOrBlank()) {
                    val (ok, err) = repository.sendDM(
                        recipientId = recipientId,
                        text = if (viewOnce) "View-Once Sketch" else "Sketch",
                        isViewOnce = viewOnce,
                        isDoodle = true,
                        mediaUrl = renderedPath
                    )
                    if (ok) {
                        hapticManager.performMessageSent(view)
                        soundEffectManager.playSendMessage()
                    } else if (!err.isNullOrBlank()) {
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                }
            }
        )
    }

    if (lightboxMessage != null) {
        val mediaMsg = lightboxMessage!!
        MediaLightboxDialog(
            message = mediaMsg,
            onDismiss = { lightboxMessage = null },
            onEditInStudio = { path ->
                lightboxMessage = null
                photoEditorInitialUri = if (path.startsWith("http") || path.startsWith("content://") || path.startsWith("file://")) {
                    Uri.parse(path)
                } else {
                    Uri.fromFile(File(path))
                }
                showPhotoEditor = true
            }
        )
    }

    if (viewOnceMessage != null) {
        val secretMsg = viewOnceMessage!!
        ViewOnceViewerDialog(
            senderName = secretMsg.sender,
            mediaUrl = secretMsg.mediaUrl,
            onBurned = {
                repository.openViewOnce(recipientId, secretMsg.id)
            },
            onDismiss = { viewOnceMessage = null }
        )
    }

    if (showChatDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showChatDrawer = false },
            modifier = Modifier.testTag("chat_drawer"),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = recipientName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = recipientName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Conversation Options",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showChatDrawer = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Drawer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (!isSoundMuted) {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    },
                                    contentDescription = null,
                                    tint = if (!isSoundMuted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Sound Effects",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Low-latency SoundPool chimes",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = !isSoundMuted,
                                onCheckedChange = { enabled ->
                                    soundEffectManager.setMuted(!enabled)
                                    isSoundMuted = !enabled
                                    if (enabled) soundEffectManager.playSendMessage()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = { soundEffectManager.playSendMessage() },
                                label = { Text("Send Clip", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { soundEffectManager.playReceiveMessage() },
                                label = { Text("Receive Clip", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { soundEffectManager.playOpenDrawer() },
                                label = { Text("Drawer Clip", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showChatDrawer = false
                                isSearchActive = true
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Search Conversation by Keyword",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                repository.clearDMs(recipientId)
                                showChatDrawer = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Clear Local Chat Messages",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private suspend fun persistAttachmentUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
        return@withContext uri.path!!
    }
    return@withContext try {
        val dir = File(context.cacheDir, "chat_attachments").apply { mkdirs() }
        val outFile = File(dir, "att_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        if (outFile.exists() && outFile.length() > 0L) outFile.absolutePath else uri.toString()
    } catch (_: Exception) {
        uri.toString()
    }
}
