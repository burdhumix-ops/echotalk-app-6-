package co.znxdnd.echotalk.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import co.znxdnd.echotalk.ui.theme.ThemeManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import co.znxdnd.echotalk.audio.SoundEffectManager
import co.znxdnd.echotalk.audio.VoiceNoteRecorder
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import android.view.HapticFeedbackConstants
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import co.znxdnd.echotalk.R
import co.znxdnd.echotalk.ai.GeminiChatService
import co.znxdnd.echotalk.calling.CallController
import co.znxdnd.echotalk.calling.CallType
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.GameChallengeData
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.data.personalization.PersonalizationManager
import co.znxdnd.echotalk.data.personalization.SmartPersonalizationEngine
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.repository.ChatBackgroundPreset
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.data.stickers.TelegramGifItem
import co.znxdnd.echotalk.data.stickers.TelegramStickerItem
import co.znxdnd.echotalk.data.stickers.TelegramStickerManager
import co.znxdnd.echotalk.ui.components.*
import co.znxdnd.echotalk.utils.ConversationSearchEngine
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.SmartReplyRanker
import co.znxdnd.echotalk.utils.getCountryFlag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BubbleAccent(val id: String, val title: String, val color: Color) {
    DEFAULT("default", "Classic Theme", Color(0xFF6366F1)),
    SUNSET_PINK("sunset", "Sunset Pink", Color(0xFFF43F5E)),
    ELECTRIC_CYAN("cyan", "Electric Cyan", Color(0xFF06B6D4)),
    EMERALD_GLOW("emerald", "Emerald Glow", Color(0xFF10B981)),
    MIDNIGHT_PURPLE("purple", "Midnight Purple", Color(0xFF8B5CF6))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMsScreen(
    repository: EchoTalkRepository,
    onLaunchGameChallenge: (String, String) -> Unit,
    onViewProfile: (User) -> Unit,
    aiService: GeminiChatService? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val users by repository.users.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val settings by repository.settings.collectAsState()
    val dms by repository.dms.collectAsState()
    val activePartnerId by repository.activePartnerId.collectAsState()
    val archivedPartnerIds by repository.archivedPartnerIds.collectAsState()
    val pinnedPartnerIds by repository.pinnedPartnerIds.collectAsState()
    val typingParticipants by repository.typingParticipants.collectAsState()
    val chatBackground by repository.chatBackground.collectAsState()
    val activeTheme by repository.theme.collectAsState()
    val semanticColors = remember(activeTheme) { ThemeManager.getSemanticColors(activeTheme) }
    val personalizationManager = remember(context) { PersonalizationManager.getInstance(context) }
    val personalizationConfig by personalizationManager.config.collectAsState()

    // Telegram folder tabs state
    var selectedFolderTab by remember {
        mutableStateOf(
            if (personalizationConfig.personalizationEnabled) personalizationConfig.lastDmFolderTab else "all"
        )
    }
    var searchQuery by remember {
        mutableStateOf(
            if (personalizationConfig.personalizationEnabled) personalizationConfig.lastDmSearchQuery else ""
        )
    }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val confirmationController = rememberEchoConfirmationController(snackbarHostState)

    // Active conversation states
    var messageText by remember(activePartnerId) {
        mutableStateOf(
            if (activePartnerId != null) personalizationManager.getDmDraft(activePartnerId!!) else ""
        )
    }
    var isSendViewOnce by remember { mutableStateOf(false) }
    var replyingToMessage by remember(activePartnerId) { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember(activePartnerId) { mutableStateOf<ChatMessage?>(null) }
    var showDoodleDialog by remember { mutableStateOf(false) }
    var showPhotoEditorDialog by remember { mutableStateOf(false) }
    var editorInitialUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var activeViewOnceMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var activeMediaLightboxMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showGamePicker by remember { mutableStateOf(false) }
    var sendErrorMessage by remember(activePartnerId) { mutableStateOf<String?>(null) }
    var showStickerDrawer by remember { mutableStateOf(false) }

    val callController = remember { CallController.getInstance() }

    // One-Tap Page Translation via Gemini AI
    var isPageTranslationOn by remember { mutableStateOf(false) }
    val translatedCache = remember { mutableStateMapOf<String, String>() }

    // Standalone Cloud Firestore Chat View toggle
    var showFirestoreChatScreen by remember { mutableStateOf(false) }

    // Custom Chat Bubble Accent synced with PersonalizationManager
    val activeBubbleColor = personalizationConfig.personalDmBubbleColor
    var showAccentPicker by remember { mutableStateOf(false) }

    // Message selection context menu & dissolve animation
    var selectedMessageForMenu by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToForward by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedPartnerForActions by remember { mutableStateOf<User?>(null) }
    val dissolvingMessageIds = remember { mutableStateListOf<String>() }
    val dissolvingConversationIds = remember { mutableStateListOf<String>() }
    var partnerPendingDeletion by remember { mutableStateOf<User?>(null) }

    // Voice recording & in-chat search
    var isRecording by remember { mutableStateOf(false) }
    var recordDuration by remember { mutableIntStateOf(0) }
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val liveVoiceLevel by voiceRecorder.liveLevel.collectAsState()
    var dmChatSearchActive by remember(activePartnerId) { mutableStateOf(false) }
    var dmChatSearchQuery by remember(activePartnerId) { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancelRecording()
        }
    }

    val activePartner = users.find { it.id == activePartnerId }
    val isPartnerTyping = activePartnerId?.let { typingParticipants[it] == true } ?: false
    val currentThread = activePartnerId?.let { dms[it] } ?: emptyList()

    val acceptedPartners = users.filter { it.id != currentUser.id && it.isFriend }
    val requestPartners = users.filter { it.id != currentUser.id && !it.isFriend }

    val unreadPartners = acceptedPartners.filter { partner ->
        val thread = dms[partner.id] ?: emptyList()
        val last = thread.lastOrNull()
        last != null && last.sender != currentUser.name && last.status != "seen"
    }

    val archivedPartners = users.filter { archivedPartnerIds.contains(it.id) }

    // Telegram Folder Tab Filtering: active tabs exclude archived conversations
    val displayedPartners = when (selectedFolderTab) {
        "personal" -> acceptedPartners.filter { !archivedPartnerIds.contains(it.id) }
        "unread" -> unreadPartners.filter { !archivedPartnerIds.contains(it.id) }
        "requests" -> requestPartners.filter { !archivedPartnerIds.contains(it.id) }
        "archive" -> archivedPartners
        else -> {
            // "all" - friends plus active requests with conversation threads, excluding archived
            val withThreads = requestPartners.filter { dms.containsKey(it.id) }
            (acceptedPartners + withThreads).filter { !archivedPartnerIds.contains(it.id) }
        }
    }

    // Pinned conversations float to the top, with lightweight recent-conversation personalization
    val sortedPartners = remember(displayedPartners, pinnedPartnerIds, dms, personalizationConfig) {
        SmartPersonalizationEngine.rankConversations(
            partners = displayedPartners,
            pinnedIds = pinnedPartnerIds,
            dms = dms,
            config = personalizationConfig
        )
    }

    val filteredPartners = remember(sortedPartners, dms, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedPartners
        } else {
            ConversationSearchEngine.searchDmPartners(sortedPartners, dms, searchQuery)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (voiceRecorder.startRecording()) {
                isRecording = true
                EchoHapticManager.getInstance(context).performPixelClick(view)
            } else {
                confirmationController.show("Microphone unavailable right now.")
            }
        } else {
            confirmationController.show("Microphone permission is needed for voice messages.")
        }
    }
    var lastKnownIncomingDmId by remember(activePartnerId) { mutableStateOf<String?>(null) }
    var dmHistoryInitialized by remember(activePartnerId) { mutableStateOf(false) }

    // Persist DM search state & settled searches locally
    LaunchedEffect(searchQuery, selectedFolderTab) {
        personalizationManager.setLastDmSearchState(searchQuery, selectedFolderTab)
        if (searchQuery.trim().length >= 2) {
            delay(700)
            personalizationManager.recordDmSearch(searchQuery)
        }
    }

    LaunchedEffect(activePartnerId) {
        if (activePartnerId != null) {
            val savedDraft = personalizationManager.getDmDraft(activePartnerId!!)
            if (savedDraft.isNotBlank()) {
                confirmationController.show("Draft restored.")
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordDuration = 0
            while (isRecording) {
                delay(1000)
                recordDuration += 1
            }
        }
    }

    // -------------------------------------------------------------
    // TELEGRAM 1-ON-1 PRIVATE CHAT (PM) SCREEN
    // -------------------------------------------------------------
    if (activePartner != null) {
        if (showFirestoreChatScreen) {
            ChatScreen(
                repository = repository,
                recipientId = activePartner.id,
                recipientName = activePartner.name,
                recipientAvatar = activePartner.avatarUrl,
                onBack = { showFirestoreChatScreen = false },
                onViewProfile = { onViewProfile(activePartner) }
            )
            return
        }

        val partnerThread = dms[activePartner.id] ?: emptyList()
        val dmHasMoreMap by repository.dmHasMore.collectAsState()
        val hasMoreOlderDms = SupabaseConfig.isConfigured && (dmHasMoreMap[activePartner.id] == true)
        var isLoadingOlderDms by remember(activePartner.id) { mutableStateOf(false) }
        val consecutiveCount = partnerThread.takeLastWhile { it.sender != currentUser.name }.size
        val myConsecutiveCount = partnerThread.takeLastWhile { it.sender == currentUser.name }.size
        val isPending = !activePartner.isFriend
        val isInputBlocked = isPending && myConsecutiveCount >= 3

        val dmScrollKey = "dm:${activePartner.id}"
        val savedDmScroll = remember(dmScrollKey) { repository.getScrollPosition(dmScrollKey) }
        val startIdx = remember(dmScrollKey) {
            when {
                savedDmScroll.first > 0 -> savedDmScroll.first.coerceAtMost(partnerThread.size)
                partnerThread.isNotEmpty() -> partnerThread.size
                else -> 0
            }
        }
        val listState = key(dmScrollKey) {
            rememberLazyListState(
                initialFirstVisibleItemIndex = startIdx,
                initialFirstVisibleItemScrollOffset = if (savedDmScroll.first > 0) savedDmScroll.second else 0
            )
        }
        val lastReadMessageId = remember(dmScrollKey) { repository.getLastReadMessageId(dmScrollKey) }
        val newMessagesDividerIndex = remember(partnerThread, lastReadMessageId) {
            if (lastReadMessageId.isNullOrBlank()) -1
            else {
                val idx = partnerThread.indexOfFirst { it.id == lastReadMessageId }
                if (idx >= 0 && idx < partnerThread.lastIndex) idx + 1 else -1
            }
        }
        val isNearBottom by remember(partnerThread.size, dmScrollKey) {
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
        var unreadWhileScrolledUp by remember(dmScrollKey) { mutableIntStateOf(0) }
        var previousThreadSize by remember(dmScrollKey) { mutableIntStateOf(partnerThread.size) }

        DisposableEffect(dmScrollKey) {
            onDispose {
                repository.saveScrollPosition(
                    dmScrollKey,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset
                )
                partnerThread.lastOrNull()?.id?.let {
                    repository.markConversationRead(dmScrollKey, it)
                }
            }
        }

        LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
            repository.saveScrollPosition(
                dmScrollKey,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
            if (isNearBottom) {
                unreadWhileScrolledUp = 0
            }
        }

        LaunchedEffect(partnerThread.size, activePartner.id) {
            if (partnerThread.isNotEmpty()) {
                val latest = partnerThread.last()
                val addedCount = (partnerThread.size - previousThreadSize).coerceAtLeast(0)
                previousThreadSize = partnerThread.size

                if (isNearBottom || latest.sender == currentUser.name) {
                    if (EchoAnimations.isMotionReduced()) {
                        listState.scrollToItem(partnerThread.size)
                    } else {
                        listState.animateScrollToItem(partnerThread.size)
                    }
                    unreadWhileScrolledUp = 0
                } else if (addedCount > 0) {
                    unreadWhileScrolledUp += addedCount
                }

                if (dmHistoryInitialized && latest.sender != currentUser.name && latest.id != lastKnownIncomingDmId) {
                    SoundEffectManager.getInstance(context).playReceiveMessage()
                }
                if (latest.sender != currentUser.name) {
                    lastKnownIncomingDmId = latest.id
                }
                dmHistoryInitialized = true
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Slim Borderless Telegram PM Top App Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { repository.setActivePartner(null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Telegram Partner Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onViewProfile(activePartner) }
                        ) {
                            if (activePartner.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = activePartner.avatarUrl,
                                    contentDescription = activePartner.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activePartner.name.take(1),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            val headerDotColor by animateColorAsState(
                                targetValue = if (activePartner.online) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                animationSpec = tween(220),
                                label = "HeaderAvatarPresenceColor"
                            )
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(headerDotColor)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    .align(Alignment.BottomEnd)
                                    .semantics {
                                        contentDescription = if (activePartner.online) "Online" else "Offline"
                                    }
                                    .testTag("chat_header_presence_dot_${activePartner.id}")
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onViewProfile(activePartner) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = activePartner.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                if (activePartner.isVerified) {
                                    BlueTickBadge(size = 14.dp)
                                }
                                Text(text = getCountryFlag(activePartner.countryCode), fontSize = 12.sp)
                            }
                            TelegramChatHeaderSubtitle(
                                isTyping = isPartnerTyping,
                                isOnline = activePartner.online,
                                partnerName = activePartner.name,
                                lastSeenAtMillis = activePartner.lastSeenAtMillis,
                                modifier = Modifier.testTag("chat_header_status_${activePartner.id}")
                            )
                        }
                    }

                    // Telegram Top Action Icons
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // In-Chat Search Toggle
                        IconButton(
                            onClick = {
                                dmChatSearchActive = !dmChatSearchActive
                                if (!dmChatSearchActive) dmChatSearchQuery = ""
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("dm_chat_search_toggle")
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search Conversation",
                                tint = if (dmChatSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Translation Toggle
                        IconButton(
                            onClick = {
                                isPageTranslationOn = !isPageTranslationOn
                                if (isPageTranslationOn && aiService != null) {
                                    coroutineScope.launch {
                                        partnerThread.forEach { m ->
                                            if (!translatedCache.containsKey(m.id)) {
                                                val translated = aiService.generateQuickReply("Translate to English keeping natural chat tone: ${m.text}")
                                                translatedCache[m.id] = translated
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "Page Translation",
                                tint = if (isPageTranslationOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Audio Call (📞)
                        val isPartnerBlocked = settings.blockedUsers.contains(activePartner.id)
                        IconButton(
                            onClick = {
                                if (!isPartnerBlocked) {
                                    callController.startOutgoingCall(activePartner, CallType.AUDIO)
                                }
                            },
                            enabled = !isPartnerBlocked,
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer { alpha = if (!isPartnerBlocked) 1.0f else 0.4f }
                                .testTag("dm_audio_call_button")
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Audio Call",
                                tint = if (!isPartnerBlocked) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Video Call (📹)
                        IconButton(
                            onClick = {
                                if (!isPartnerBlocked) {
                                    callController.startOutgoingCall(activePartner, CallType.VIDEO)
                                }
                            },
                            enabled = !isPartnerBlocked,
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer { alpha = if (!isPartnerBlocked) 1.0f else 0.4f }
                                .testTag("dm_video_call_button")
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = if (!isPartnerBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Wallpaper selector
                        IconButton(
                            onClick = { showBackgroundDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Wallpaper,
                                contentDescription = "Wallpaper",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Accent color selector
                        IconButton(
                            onClick = { showAccentPicker = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = "Accent",
                                tint = activeBubbleColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Delete PM Chat (triggers Telegram confirmation dialog)
                        IconButton(
                            onClick = { partnerPendingDeletion = activePartner },
                            modifier = Modifier.size(34.dp).testTag("delete_pm_header_btn")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Chat",
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                AnimatedVisibility(visible = dmChatSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        OutlinedTextField(
                            value = dmChatSearchQuery,
                            onValueChange = { dmChatSearchQuery = it },
                            placeholder = { Text("Search messages in chat...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dm_chat_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        val matchCount = if (dmChatSearchQuery.isBlank()) {
                            partnerThread.size
                        } else {
                            partnerThread.count {
                                it.text.contains(dmChatSearchQuery, ignoreCase = true) ||
                                    it.sender.contains(dmChatSearchQuery, ignoreCase = true)
                            }
                        }
                        if (dmChatSearchQuery.isNotBlank()) {
                            Text(
                                text = "$matchCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                if (dmChatSearchQuery.isNotEmpty()) dmChatSearchQuery = ""
                                else dmChatSearchActive = false
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // Slim Single-Line Message Request Strip (3-message limit)
            if (isPending) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xE61E293B))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (myConsecutiveCount >= 3) {
                            "Request ($myConsecutiveCount/3) • Wait for ${activePartner.name}"
                        } else {
                            "Request ($myConsecutiveCount/3) • Accept to unlock chat"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFBBF24),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                EchoHapticManager.getInstance(context).performMessageSent(view)
                                repository.acceptRequest(activePartner.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = semanticColors.blueConfirm),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Accept", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = {
                                EchoHapticManager.getInstance(context).performAlertReceived(view)
                                repository.declineRequest(activePartner.id)
                                repository.setActivePartner(null)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = semanticColors.redRejected),
                            border = BorderStroke(0.8.dp, semanticColors.redRejected),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Decline", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = semanticColors.redRejected)
                        }
                    }
                }
            }

            // 1v1 Game Challenge Drawer
            AnimatedVisibility(visible = showGamePicker) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Challenge ${activePartner.name} to a 1v1 Match:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                            IconButton(onClick = { showGamePicker = false }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }

                        Button(
                            onClick = {
                                val chal = GameChallengeData(
                                    gameId = "chess",
                                    gameTitle = "Grandmaster Chess",
                                    challenger = currentUser.name,
                                    opponent = activePartner.name
                                )
                                repository.sendDM(
                                    recipientId = activePartner.id,
                                    text = "Sent a 1v1 challenge for Grandmaster Chess!",
                                    challengeData = chal
                                )
                                showGamePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("♟️ Challenge to Grandmaster Chess Duel")
                        }
                    }
                }
            }

            // Telegram PM Message Stream Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Background Layer (Gradient, Image, or Default Telegram pattern)
                if (chatBackground.gradientColors != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = chatBackground.gradientColors!!.map { Color(it) }
                                )
                            )
                    )
                } else if (chatBackground.imageUrl != null) {
                    AsyncImage(
                        model = chatBackground.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Default Telegram Dark Slate Wallpaper
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E1621))
                    )
                }

                if (chatBackground.scrimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = chatBackground.scrimAlpha))
                    )
                }

                // Telegram Stream of Chat Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasMoreOlderDms) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    enabled = !isLoadingOlderDms,
                                    onClick = {
                                        isLoadingOlderDms = true
                                        repository.loadOlderDMs(activePartner.id) {
                                            isLoadingOlderDms = false
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (isLoadingOlderDms) "Loading..." else "Load older messages",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        TelegramDateDivider(text = "Today")
                    }

                    val displayedMessages = if (dmChatSearchActive && dmChatSearchQuery.isNotBlank()) {
                        partnerThread.filter {
                            it.text.contains(dmChatSearchQuery, ignoreCase = true) ||
                                it.sender.contains(dmChatSearchQuery, ignoreCase = true)
                        }
                    } else {
                        partnerThread
                    }

                    itemsIndexed(displayedMessages, key = { _, msg -> msg.id }) { index, msg ->
                        if (index == newMessagesDividerIndex && dmChatSearchQuery.isBlank()) {
                            ChatNewMessagesDivider()
                        }
                        val isMe = msg.sender == currentUser.name
                        val isDissolving = dissolvingMessageIds.contains(msg.id)
                        val bubbleColor = if (isMe) activeBubbleColor else MaterialTheme.colorScheme.surfaceVariant
                        val displayedText = if (isPageTranslationOn && translatedCache.containsKey(msg.id)) {
                            translatedCache[msg.id] ?: msg.text
                        } else msg.text

                        TelegramDustDisintegrator(
                            isDissolving = isDissolving,
                            onDissolved = {
                                val deletedIdx = partnerThread.indexOfFirst { it.id == msg.id }.coerceAtLeast(0)
                                repository.deleteDM(activePartner.id, msg.id)
                                dissolvingMessageIds.remove(msg.id)
                                confirmationController.show(
                                    message = "Message deleted.",
                                    actionLabel = "Undo",
                                    onAction = {
                                        repository.restoreDMMessage(activePartner.id, msg, deletedIdx)
                                    }
                                )
                            }
                        ) {
                            TelegramChatBubble(
                                message = msg,
                                isMe = isMe,
                                bubbleColor = bubbleColor,
                                displayedText = displayedText,
                                onLongPress = {
                                    selectedMessageForMenu = msg
                                },
                                onTap = {
                                    if (msg.isViewOnce) {
                                        activeViewOnceMessage = msg
                                    } else {
                                        val mUrl = msg.mediaUrl
                                        val isOpenableMedia = msg.isDoodle || (!mUrl.isNullOrBlank() &&
                                            (mUrl.startsWith("/") || mUrl.startsWith("http") || mUrl.startsWith("content://") || mUrl.startsWith("file://")))
                                        if (isOpenableMedia) {
                                            activeMediaLightboxMessage = msg
                                        }
                                    }
                                },
                                onViewOnceClick = {
                                    activeViewOnceMessage = msg
                                },
                                onLaunchGame = { gameId, challenger ->
                                    onLaunchGameChallenge(gameId, challenger)
                                },
                                onReactionClick = { emoji ->
                                    repository.addReactionToDM(activePartner.id, msg.id, emoji)
                                    confirmationController.show("Reaction added.")
                                },
                                messageTextSizeSp = personalizationConfig.messageTextSizeSp,
                                onRetryFailed = {
                                    val (ok, err) = repository.retryFailedDM(activePartner.id, msg.id)
                                    if (ok) {
                                        EchoHapticManager.getInstance(context).performMessageSent(view)
                                        SoundEffectManager.getInstance(context).playSendMessage()
                                    } else {
                                        sendErrorMessage = err
                                        confirmationController.show("Something went wrong. Try again.")
                                    }
                                }
                            )
                        }
                    }
                }

                JumpToLatestButton(
                    visible = !isNearBottom && partnerThread.isNotEmpty(),
                    unreadCount = unreadWhileScrolledUp,
                    onClick = {
                        coroutineScope.launch {
                            if (partnerThread.isNotEmpty()) {
                                if (EchoAnimations.isMotionReduced()) {
                                    listState.scrollToItem(partnerThread.size)
                                } else {
                                    listState.animateScrollToItem(partnerThread.size)
                                }
                                unreadWhileScrolledUp = 0
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 12.dp)
                )

                EchoConfirmationSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // Slim Single-Line Error Strip
            if (sendErrorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x28E11D48))
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sendErrorMessage ?: "",
                        fontSize = 11.sp,
                        color = Color(0xFFFB7185),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { sendErrorMessage = null }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = Color.Gray, modifier = Modifier.size(13.dp))
                    }
                }
            }

            // Contextual Smart Reply Chips
            val smartReplyChips = remember(partnerThread, currentUser.name, activePartner.id, activePartner.online) {
                SmartReplyRanker.suggestReplies(partnerThread, currentUser.name, activePartner)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                smartReplyChips.forEach { chip ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (!repository.isDuplicateSubmission("dm:${activePartner.id}", chip)) {
                                    val (ok, err) = repository.sendDM(recipientId = activePartner.id, text = chip)
                                    if (ok) {
                                        EchoHapticManager.getInstance(context).performMessageSent(view)
                                        SoundEffectManager.getInstance(context).playSendMessage()
                                    } else {
                                        sendErrorMessage = err
                                        confirmationController.show("Something went wrong. Try again.")
                                    }
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(text = chip, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            // Live Voice Recording Studio Bar
            AnimatedVisibility(visible = isRecording) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1B2E),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            val mins = recordDuration / 60
                            val secs = recordDuration % 60
                            Text(
                                text = "REC $mins:${secs.toString().padStart(2, '0')}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(liveVoiceLevel.coerceIn(0.08f, 1f))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isRecording = false
                                    voiceRecorder.cancelRecording()
                                    confirmationController.show("Voice note discarded.")
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Cancel Recording", tint = Color(0xFFFB7185), modifier = Modifier.size(18.dp))
                            }
                            Button(
                                onClick = {
                                    isRecording = false
                                    coroutineScope.launch {
                                        val recorded = voiceRecorder.stopAndSave()
                                        if (recorded != null) {
                                            val (ok, err) = repository.sendDM(
                                                recipientId = activePartner.id,
                                                text = "Voice message (${recorded.formattedDuration})",
                                                mediaUrl = recorded.filePath,
                                                isAudio = true,
                                                audioDuration = recorded.formattedDuration,
                                                audioWaveform = recorded.waveform
                                            )
                                            if (ok) {
                                                EchoHapticManager.getInstance(context).performMessageSent(view)
                                                SoundEffectManager.getInstance(context).playSendMessage()
                                            } else {
                                                sendErrorMessage = err
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Telegram Chat Input Bar
            TelegramInputBar(
                messageText = messageText,
                onMessageChange = { newText ->
                    messageText = newText
                    personalizationManager.saveDmDraft(activePartner.id, newText)
                    repository.setUserTyping(activePartner.id, newText.isNotBlank())
                },
                onSendMessage = {
                    if (messageText.isNotBlank()) {
                        repository.setUserTyping(activePartner.id, false)
                        val editingTarget = editingMessage
                        if (editingTarget != null) {
                            // Saving edited message
                            repository.updateDMText(activePartner.id, editingTarget.id, messageText.trim())
                            EchoHapticManager.getInstance(context).performPixelConfirm(view)
                            messageText = ""
                            personalizationManager.saveDmDraft(activePartner.id, "")
                            editingMessage = null
                            confirmationController.show("Changes saved.")
                        } else {
                            val trimmedBody = messageText.trim()
                            val textToSend = if (replyingToMessage != null) {
                                "Replying to ${replyingToMessage!!.sender}: \"${replyingToMessage!!.text.take(30)}\"\n$trimmedBody"
                            } else trimmedBody

                            if (!repository.isDuplicateSubmission("dm:${activePartner.id}", textToSend)) {
                                if (isSendViewOnce) {
                                    co.znxdnd.echotalk.worker.SecurityVaultScanWorker.enqueueScan(
                                        context = context,
                                        entryId = "sec-${System.currentTimeMillis()}",
                                        mediaUrl = "view_once_flame_capture",
                                        sender = currentUser.name,
                                        target = activePartner.name,
                                        isViewOnce = true
                                    )
                                }

                                val (ok, err) = repository.sendDM(
                                    recipientId = activePartner.id,
                                    text = textToSend,
                                    isViewOnce = isSendViewOnce
                                )
                                if (ok) {
                                    EchoHapticManager.getInstance(context).performMessageSent(view)
                                    SoundEffectManager.getInstance(context).playSendMessage()
                                    messageText = ""
                                    personalizationManager.saveDmDraft(activePartner.id, "")
                                    isSendViewOnce = false
                                    replyingToMessage = null
                                    sendErrorMessage = null
                                } else {
                                    EchoHapticManager.getInstance(context).performAlertReceived(view)
                                    val failed = repository.recordFailedDM(activePartner.id, textToSend)
                                    messageText = ""
                                    personalizationManager.saveDmDraft(activePartner.id, "")
                                    isSendViewOnce = false
                                    replyingToMessage = null
                                    sendErrorMessage = err
                                    confirmationController.show(
                                        message = "Something went wrong. Try again.",
                                        actionLabel = "Retry",
                                        onAction = {
                                            repository.retryFailedDM(activePartner.id, failed.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                onSendLike = {
                    if (!repository.isDuplicateSubmission("dm:${activePartner.id}", "👍")) {
                        val (ok, err) = repository.sendDM(
                            recipientId = activePartner.id,
                            text = "👍"
                        )
                        if (ok) {
                            EchoHapticManager.getInstance(context).performMessageSent(view)
                            SoundEffectManager.getInstance(context).playSendMessage()
                        } else {
                            sendErrorMessage = err
                            confirmationController.show("Something went wrong. Try again.")
                        }
                    }
                },
                onOpenStickerDrawer = {
                    showStickerDrawer = true
                },
                isSendViewOnce = isSendViewOnce,
                onToggleViewOnce = { isSendViewOnce = !isSendViewOnce },
                onOpenPhotoEditor = { showPhotoEditorDialog = true },
                onOpenDoodle = { showDoodleDialog = true },
                onToggleVoiceRecording = {
                    if (isRecording) {
                        isRecording = false
                        coroutineScope.launch {
                            val recorded = voiceRecorder.stopAndSave()
                            if (recorded != null) {
                                val (ok, err) = repository.sendDM(
                                    recipientId = activePartner.id,
                                    text = "Voice message (${recorded.formattedDuration})",
                                    mediaUrl = recorded.filePath,
                                    isAudio = true,
                                    audioDuration = recorded.formattedDuration,
                                    audioWaveform = recorded.waveform
                                )
                                if (ok) {
                                    EchoHapticManager.getInstance(context).performMessageSent(view)
                                    SoundEffectManager.getInstance(context).playSendMessage()
                                } else {
                                    sendErrorMessage = err
                                }
                            } else {
                                confirmationController.show("Voice note too short.")
                            }
                        }
                    } else {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            if (voiceRecorder.startRecording()) {
                                isRecording = true
                                EchoHapticManager.getInstance(context).performPixelClick(view)
                            } else {
                                confirmationController.show("Unable to access microphone.")
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                isRecording = isRecording,
                onOpenGameChallenge = {
                    val next = !showGamePicker
                    showGamePicker = next
                    if (next) {
                        SoundEffectManager.getInstance(context).playOpenDrawer()
                    }
                },
                isInputBlocked = isInputBlocked,
                placeholder = if (editingMessage != null) "Edit message..." else "Message ${activePartner.name}...",
                replyingToText = replyingToMessage?.let { "${it.sender}: ${it.text.take(40)}" },
                onCancelReply = { replyingToMessage = null },
                editingMessageText = editingMessage?.let { it.text.take(40) },
                onCancelEdit = {
                    editingMessage = null
                    messageText = ""
                    personalizationManager.saveDmDraft(activePartner.id, "")
                }
            )

            // Telegram Sticker & GIF Drawer Modal
            if (showStickerDrawer) {
                TelegramStickerDrawer(
                    onSendSticker = { sticker ->
                        val (ok, err) = repository.sendDM(
                            recipientId = activePartner.id,
                            text = sticker.emoji,
                            mediaUrl = sticker.mediaUrl,
                            isSticker = true,
                            stickerPackId = sticker.packId
                        )
                        if (ok) {
                            EchoHapticManager.getInstance(context).performMessageSent(view)
                            SoundEffectManager.getInstance(context).playSendMessage()
                        } else {
                            sendErrorMessage = err
                        }
                    },
                    onSendGif = { gif ->
                        val (ok, err) = repository.sendDM(
                            recipientId = activePartner.id,
                            text = gif.title,
                            mediaUrl = gif.mediaUrl,
                            isGif = true
                        )
                        if (ok) {
                            EchoHapticManager.getInstance(context).performMessageSent(view)
                            SoundEffectManager.getInstance(context).playSendMessage()
                        } else {
                            sendErrorMessage = err
                        }
                    },
                    onSendEmoji = { emoji ->
                        val (ok, err) = repository.sendDM(
                            recipientId = activePartner.id,
                            text = emoji
                        )
                        if (ok) {
                            EchoHapticManager.getInstance(context).performMessageSent(view)
                            SoundEffectManager.getInstance(context).playSendMessage()
                        } else {
                            sendErrorMessage = err
                        }
                    },
                    onDismiss = { showStickerDrawer = false }
                )
            }

            // Message Context Menu Tray
            if (selectedMessageForMenu != null) {
                val menuMsg = selectedMessageForMenu!!
                val isMsgFromMe = (menuMsg.sender == currentUser.name)
                MessengerFloatingTray(
                    message = menuMsg,
                    isMe = isMsgFromMe,
                    onReactionSelect = { emoji ->
                        repository.addReactionToDM(activePartner.id, menuMsg.id, emoji)
                        confirmationController.show("Reaction added.")
                        selectedMessageForMenu = null
                    },
                    onReply = {
                        replyingToMessage = menuMsg
                        selectedMessageForMenu = null
                    },
                    onForward = {
                        messageToForward = menuMsg
                        selectedMessageForMenu = null
                    },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(menuMsg.text))
                        confirmationController.show("Message copied.")
                        selectedMessageForMenu = null
                    },
                    onEdit = {
                        editingMessage = menuMsg
                        messageText = menuMsg.text
                        selectedMessageForMenu = null
                    },
                    onUnsend = {
                        // Disappear completely with dust animation
                        dissolvingMessageIds.add(menuMsg.id)
                        selectedMessageForMenu = null
                    },
                    onDelete = {
                        dissolvingMessageIds.add(menuMsg.id)
                        selectedMessageForMenu = null
                    },
                    onTranslate = {
                        if (aiService != null) {
                            coroutineScope.launch {
                                val tr = aiService.generateQuickReply("Translate to English keeping natural tone: ${menuMsg.text}")
                                translatedCache[menuMsg.id] = tr
                                isPageTranslationOn = true
                            }
                        }
                        selectedMessageForMenu = null
                    },
                    onDismiss = {
                        selectedMessageForMenu = null
                    }
                )
            }

            // FORWARD MESSAGE DIALOG
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
                            EchoHapticManager.getInstance(context).performMessageSent(view)
                            SoundEffectManager.getInstance(context).playSendMessage()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Message forwarded to ${targetUser.name}",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        } else if (err != null) {
                            sendErrorMessage = err
                        }
                        messageToForward = null
                    },
                    onDismiss = {
                        messageToForward = null
                    }
                )
            }

            // PHOTO EDITOR DIALOG
            if (showPhotoEditorDialog) {
                PhotoEditorDialog(
                    initialImageUri = editorInitialUri,
                    onSend = { viewOnce ->
                        repository.sendDM(
                            recipientId = activePartner.id,
                            text = if (viewOnce) "View Once Edited Photo" else "Edited Photo",
                            mediaUrl = "edited_photo",
                            isViewOnce = viewOnce
                        )
                        EchoHapticManager.getInstance(context).performMessageSent(view)
                        SoundEffectManager.getInstance(context).playSendMessage()
                    },
                    onSendRendered = { viewOnce, renderedPath ->
                        repository.sendDM(
                            recipientId = activePartner.id,
                            text = if (viewOnce) "View Once Studio Photo" else "Studio Edited Photo",
                            mediaUrl = renderedPath ?: "edited_photo",
                            isViewOnce = viewOnce
                        )
                        EchoHapticManager.getInstance(context).performMessageSent(view)
                        SoundEffectManager.getInstance(context).playSendMessage()
                    },
                    onDismiss = {
                        showPhotoEditorDialog = false
                        editorInitialUri = null
                    }
                )
            }

            // FULL-SCREEN ZOOMABLE MEDIA LIGHTBOX
            if (activeMediaLightboxMessage != null) {
                MediaLightboxDialog(
                    message = activeMediaLightboxMessage!!,
                    onDismiss = { activeMediaLightboxMessage = null },
                    onEditInStudio = { pathOrUrl ->
                        activeMediaLightboxMessage = null
                        editorInitialUri = if (pathOrUrl.startsWith("/")) {
                            android.net.Uri.fromFile(java.io.File(pathOrUrl))
                        } else {
                            android.net.Uri.parse(pathOrUrl)
                        }
                        showPhotoEditorDialog = true
                    }
                )
            }

            // DOODLE CANVAS DIALOG
            if (showDoodleDialog) {
                DoodleCanvasDialog(
                    recipientName = activePartner.name,
                    onDismiss = { showDoodleDialog = false },
                    onSendDoodle = { viewOnce, strokeCount ->
                        repository.sendDM(
                            recipientId = activePartner.id,
                            text = if (viewOnce) "View Once Doodle Artwork" else "Doodle Artwork ($strokeCount strokes)",
                            isDoodle = true,
                            isViewOnce = viewOnce
                        )
                        EchoHapticManager.getInstance(context).performMessageSent(view)
                        SoundEffectManager.getInstance(context).playSendMessage()
                    },
                    onSendRenderedDoodle = { viewOnce, strokeCount, renderedPath ->
                        repository.sendDM(
                            recipientId = activePartner.id,
                            text = if (viewOnce) "View Once Studio Artwork" else "Studio Artwork ($strokeCount strokes)",
                            mediaUrl = renderedPath,
                            isDoodle = true,
                            isViewOnce = viewOnce
                        )
                        EchoHapticManager.getInstance(context).performMessageSent(view)
                        SoundEffectManager.getInstance(context).playSendMessage()
                    }
                )
            }

            // VIEW ONCE VIEWER DIALOG
            if (activeViewOnceMessage != null) {
                ViewOnceViewerDialog(
                    senderName = activeViewOnceMessage!!.sender,
                    mediaUrl = activeViewOnceMessage!!.mediaUrl,
                    onBurned = {
                        repository.openViewOnce(activePartner.id, activeViewOnceMessage!!.id)
                    },
                    onDismiss = { activeViewOnceMessage = null }
                )
            }

            // BUBBLE ACCENT SELECTOR DIALOG
            if (showAccentPicker) {
                Dialog(onDismissRequest = { showAccentPicker = false }) {
                    Surface(
                        modifier = Modifier.width(280.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Chat Bubble Color Accent", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            BubbleAccent.entries.forEach { accent ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            personalizationManager.setPersonalDmBubbleColor(accent.color)
                                            showAccentPicker = false
                                            confirmationController.show("Changes saved.")
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(accent.color))
                                    Text(accent.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // BACKGROUND SELECTOR DIALOG
            if (showBackgroundDialog) {
                ChatBackgroundDialog(
                    current = chatBackground,
                    onSelect = {
                        repository.setChatBackground(it)
                        showBackgroundDialog = false
                    },
                    onDismiss = { showBackgroundDialog = false }
                )
            }
        }
    } else {
        // -------------------------------------------------------------
        // TELEGRAM DMS LIST SCREEN (CHATS, FOLDERS, STORIES, COMPACT ROWS)
        // -------------------------------------------------------------
        val floatingBottomClearance = LocalFloatingBottomBarClearance.current

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Slim Borderless Chats Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Chats",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${acceptedPartners.size} • ${requestPartners.size} req",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showBackgroundDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = "Theme",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = { showFirestoreChatScreen = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Cloud Chats",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // Telegram Folder Filter Tabs Row
                val folderTabs = listOf(
                    "all" to "All Chats",
                    "personal" to "Personal",
                    "unread" to "Unread",
                    "requests" to "Requests",
                    "archive" to "Archive"
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(folderTabs) { (id, title) ->
                        val isSelected = selectedFolderTab == id
                        val count = when (id) {
                            "unread" -> unreadPartners.count { !archivedPartnerIds.contains(it.id) }
                            "requests" -> requestPartners.count { !archivedPartnerIds.contains(it.id) }
                            "archive" -> archivedPartners.size
                            else -> 0
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF229ED9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                                    selectedFolderTab = id
                                    personalizationManager.setLastDmSearchState(searchQuery, id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else Color(0xFFFBBF24)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Telegram Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search chats and messages...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        personalizationManager.setLastDmSearchState("", selectedFolderTab)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Local Recent DM Searches Row (optional & clearable)
                if (personalizationConfig.personalizationEnabled && personalizationConfig.recentDmSearches.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(personalizationConfig.recentDmSearches) { recent ->
                            Surface(
                                onClick = { searchQuery = recent },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = recent,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                        item {
                            TextButton(
                                onClick = { personalizationManager.clearDmRecentSearches() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                            ) {
                                Text(
                                    text = "Clear",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Telegram Stories / Contacts Avatar Carousel
                val onlinePartners = acceptedPartners.filter { it.online }
                if (onlinePartners.isNotEmpty() || acceptedPartners.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Current User Story Slot
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(56.dp)
                            ) {
                                Box(modifier = Modifier.size(52.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(currentUser.name.take(1), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF229ED9))
                                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            .align(Alignment.BottomEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("My Story", fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Online / Recent Contacts
                        items(acceptedPartners, key = { "carousel_${it.id}" }) { partner ->
                            val isOnline = partner.online
                            val carouselDotColor by animateColorAsState(
                                targetValue = if (isOnline) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                animationSpec = tween(220),
                                label = "CarouselPresenceColor_${partner.id}"
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { repository.setActivePartner(partner.id) }
                            ) {
                                Box(modifier = Modifier.size(52.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (isOnline) {
                                                    Modifier.border(
                                                        width = 2.dp,
                                                        brush = Brush.sweepGradient(listOf(Color(0xFF229ED9), Color(0xFF8B5CF6), Color(0xFF10B981), Color(0xFF229ED9))),
                                                        shape = CircleShape
                                                    )
                                                } else Modifier
                                            )
                                            .padding(if (isOnline) 2.5.dp else 0.dp)
                                    ) {
                                        if (partner.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = partner.avatarUrl,
                                                contentDescription = partner.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(partner.name.take(1), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(carouselDotColor)
                                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            .align(Alignment.BottomEnd)
                                            .semantics {
                                                contentDescription = if (isOnline) "${partner.name} is online" else "${partner.name} is offline"
                                            }
                                            .testTag("contact_carousel_presence_${partner.id}")
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = partner.name.split(" ").firstOrNull() ?: partner.name,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), modifier = Modifier.padding(top = 4.dp))

                val dmListSavedScroll = remember { repository.getScrollPosition("tab:dms") }
                val dmListState = rememberLazyListState(
                    initialFirstVisibleItemIndex = dmListSavedScroll.first,
                    initialFirstVisibleItemScrollOffset = dmListSavedScroll.second
                )

                DisposableEffect(Unit) {
                    onDispose {
                        repository.saveScrollPosition(
                            "tab:dms",
                            dmListState.firstVisibleItemIndex,
                            dmListState.firstVisibleItemScrollOffset
                        )
                    }
                }

                LaunchedEffect(dmListState.firstVisibleItemIndex, dmListState.firstVisibleItemScrollOffset) {
                    repository.saveScrollPosition(
                        "tab:dms",
                        dmListState.firstVisibleItemIndex,
                        dmListState.firstVisibleItemScrollOffset
                    )
                }

                // Telegram Conversation Rows List (scrolls smoothly under floating BottomNavBar)
                LazyColumn(
                    state = dmListState,
                    contentPadding = PaddingValues(bottom = floatingBottomClearance + 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredPartners.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (selectedFolderTab) {
                                        "unread" -> "No unread messages"
                                        "requests" -> "No pending message requests"
                                        "archive" -> "No archived chats.\nSwipe right on any conversation in All Chats to archive it."
                                        else -> "No conversations found"
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredPartners, key = { it.id }) { partner ->
                            val thread = dms[partner.id] ?: emptyList()
                            val lastMsg = thread.lastOrNull()
                            val isRequest = !partner.isFriend
                            val hasUnread = lastMsg != null && lastMsg.sender != currentUser.name && lastMsg.status != "seen"
                            val isLastFromMe = lastMsg != null && lastMsg.sender == currentUser.name
                            val isArchived = repository.isConversationArchived(partner.id)
                            val isPinned = repository.isConversationPinned(partner.id)
                            val isArchivedTab = selectedFolderTab == "archive"
                            val isDissolvingConv = dissolvingConversationIds.contains(partner.id)

                            TelegramDustDisintegrator(
                                isDissolving = isDissolvingConv,
                                onDissolved = {
                                    val deletedMessages = repository.deleteConversation(partner.id)
                                    dissolvingConversationIds.remove(partner.id)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Conversation with ${partner.name} deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            repository.restoreConversation(partner.id, deletedMessages)
                                        }
                                    }
                                }
                            ) {
                                // Swipe-to-Delete (Left) & Swipe-to-Archive (Right) Gestures
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        when (dismissValue) {
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                // Ask for confirmation before deleting PM!
                                                EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                                                partnerPendingDeletion = partner
                                                false
                                            }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            // Swipe Right: Archive or Unarchive Conversation
                                            EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                                            if (isArchived || isArchivedTab) {
                                                repository.unarchiveConversation(partner.id)
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "${partner.name} moved to All Chats",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        repository.archiveConversation(partner.id)
                                                    }
                                                }
                                            } else {
                                                repository.archiveConversation(partner.id)
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "${partner.name} archived",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        repository.unarchiveConversation(partner.id)
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val isSwipingToDelete = direction == SwipeToDismissBoxValue.EndToStart
                                    val isSwipingToArchive = direction == SwipeToDismissBoxValue.StartToEnd

                                    val bgColor = when {
                                        isSwipingToDelete -> Color(0xFFEF4444)
                                        isSwipingToArchive -> if (isArchivedTab) Color(0xFF10B981) else Color(0xFF229ED9)
                                        else -> Color.Transparent
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(bgColor)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = if (isSwipingToDelete) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        if (isSwipingToDelete) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Delete",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        } else if (isSwipingToArchive) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    if (isArchivedTab) Icons.Default.Unarchive else Icons.Default.Archive,
                                                    contentDescription = if (isArchivedTab) "Unarchive" else "Archive",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Text(
                                                    text = if (isArchivedTab) "Unarchive" else "Archive",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                                Surface(
                                    color = if (isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else MaterialTheme.colorScheme.background,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dm_conversation_row_${partner.id}")
                                ) {
                                    // Telegram Compact Chat Row
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(partner.id) {
                                                detectTapGestures(
                                                    onTap = {
                                                        if (hasUnread) {
                                                            PoliteSoundSynthesizer.playPoliteReceiveSound()
                                                        }
                                                        repository.setActivePartner(partner.id)
                                                    },
                                                    onLongPress = {
                                                        EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                                                        selectedPartnerForActions = partner
                                                    }
                                                )
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 52dp Telegram Avatar with optional pinned primary border
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .then(
                                                        if (isPinned) Modifier
                                                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                            .padding(2.dp)
                                                        else Modifier
                                                    )
                                            ) {
                                                if (partner.avatarUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = partner.avatarUrl,
                                                        contentDescription = partner.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = partner.name.take(1),
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                val rowDotColor by animateColorAsState(
                                                    targetValue = if (partner.online) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                                    animationSpec = tween(220),
                                                    label = "RowPresenceColor_${partner.id}"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(13.dp)
                                                        .clip(CircleShape)
                                                        .background(rowDotColor)
                                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                                        .align(Alignment.BottomEnd)
                                                        .semantics {
                                                            contentDescription = if (partner.online) "${partner.name} online" else "${partner.name} offline"
                                                        }
                                                        .testTag("dm_contact_presence_dot_${partner.id}")
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Content Block: Title + Subtitle Snippet
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = partner.name,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                    if (partner.isVerified) {
                                                        BlueTickBadge(size = 14.dp)
                                                    }
                                                    Text(text = getCountryFlag(partner.countryCode), fontSize = 11.sp)

                                                    // Visual Indicator: PINNED Micro-Badge
                                                    if (isPinned) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                                            modifier = Modifier.testTag("pinned_tag_${partner.id}")
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PushPin,
                                                                    contentDescription = "Pinned",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier
                                                                        .size(10.dp)
                                                                        .graphicsLayer { rotationZ = 35f }
                                                                )
                                                                Text(
                                                                    text = "PINNED",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    letterSpacing = 0.4.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(3.dp))

                                                // Preview snippet with Telegram checkmarks
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (isLastFromMe && lastMsg != null) {
                                                        TelegramStatusTicks(
                                                            status = lastMsg.status,
                                                            isOutgoing = true,
                                                            tint = if (lastMsg.status == "seen") Color(0xFF34B7F1) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    }

                                                    val isPartnerRowTyping = typingParticipants[partner.id] == true
                                                    val snippetText = when {
                                                        isPartnerRowTyping -> "typing..."
                                                        lastMsg == null -> "${partner.country} • @${partner.handle}"
                                                        lastMsg.isSticker -> "Sticker ${lastMsg.text}"
                                                        lastMsg.isGif -> "GIF 🎬 ${lastMsg.text}"
                                                        lastMsg.isAudio -> "🎙️ Voice Note (${lastMsg.audioDuration ?: "0:12"})"
                                                        lastMsg.isViewOnce -> "🔥 View Once Photo"
                                                        lastMsg.isDoodle -> "🎨 Doodle Artwork"
                                                        lastMsg.isChallenge -> "⚔️ 1v1 Challenge"
                                                        else -> lastMsg.text
                                                    }

                                                    Text(
                                                        text = snippetText,
                                                        fontSize = 13.sp,
                                                        color = if (isPartnerRowTyping) Color(0xFF2AABEE) else if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (hasUnread || isPartnerRowTyping) FontWeight.SemiBold else FontWeight.Normal,
                                                        fontStyle = if (isPartnerRowTyping) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // Trailing: Timestamp & Telegram Unread Count Badge & Angled Pin Icon
                                            Column(
                                                horizontalAlignment = Alignment.End,
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val displayTimestamp = lastMsg?.timestamp ?: "12:00"
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (isPinned) {
                                                        Icon(
                                                            imageVector = Icons.Default.PushPin,
                                                            contentDescription = "Pinned Chat",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier
                                                                .size(13.dp)
                                                                .graphicsLayer { rotationZ = 45f }
                                                                .testTag("pinned_icon_${partner.id}")
                                                        )
                                                    }
                                                    Text(
                                                        text = displayTimestamp,
                                                        fontSize = 11.sp,
                                                        color = if (hasUnread) Color(0xFF229ED9) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }

                                                if (hasUnread) {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = Color(0xFF229ED9)
                                                    ) {
                                                        Text(
                                                            text = "1",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Divider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                            modifier = Modifier.padding(start = 78.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                }
            }

            // Telegram Circular FAB positioned comfortably above the floating BottomNavBar
            FloatingActionButton(
                onClick = {
                    val firstPartner = acceptedPartners.firstOrNull() ?: requestPartners.firstOrNull()
                    if (firstPartner != null) {
                        repository.setActivePartner(firstPartner.id)
                    }
                },
                containerColor = Color(0xFF229ED9),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = floatingBottomClearance + 14.dp)
                    .size(50.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Compose Message", modifier = Modifier.size(22.dp))
            }

            // Snackbar Host for Swipe-to-Delete and Swipe-to-Archive Undo feedback
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = floatingBottomClearance + 12.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    actionColor = Color(0xFF38BDF8)
                )
            }
        }

        // BACKGROUND SELECTOR DIALOG
        if (showBackgroundDialog) {
            ChatBackgroundDialog(
                current = chatBackground,
                onSelect = {
                    repository.setChatBackground(it)
                    showBackgroundDialog = false
                },
                onDismiss = { showBackgroundDialog = false }
            )
        }

        // CONVERSATION ACTIONS MODAL (Pin to top, Archive, View Profile)
        if (selectedPartnerForActions != null) {
            val targetPartner = selectedPartnerForActions!!
            val isCurrentlyPinned = repository.isConversationPinned(targetPartner.id)
            val isCurrentlyArchived = repository.isConversationArchived(targetPartner.id)

            ModalBottomSheet(
                onDismissRequest = { selectedPartnerForActions = null },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("conversation_actions_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with partner info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(46.dp)) {
                            if (targetPartner.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = targetPartner.avatarUrl,
                                    contentDescription = targetPartner.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = targetPartner.name.take(1),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = targetPartner.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isCurrentlyPinned) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "PINNED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "@${targetPartner.handle} • ${targetPartner.country}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { selectedPartnerForActions = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Action 1: Pin / Unpin Conversation (Primary Action)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrentlyPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrentlyPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                val isNowPinned = repository.togglePinConversation(targetPartner.id)
                                EchoHapticManager.getInstance(context).performPixelClick(view)
                                SoundEffectManager.getInstance(context).playSendMessage()
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = if (isNowPinned) "${targetPartner.name} pinned to top" else "${targetPartner.name} unpinned",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        repository.togglePinConversation(targetPartner.id)
                                    }
                                }
                                selectedPartnerForActions = null
                            }
                            .testTag("modal_action_pin_${targetPartner.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = if (isCurrentlyPinned) "Unpin" else "Pin",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = if (isCurrentlyPinned) 0f else 35f }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCurrentlyPinned) "Unpin from Top" else "Pin to Top",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isCurrentlyPinned) "Move this chat back to its normal position" else "Keep this conversation at the top of your list",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Action 2: Archive / Unarchive
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (isCurrentlyArchived) {
                                    repository.unarchiveConversation(targetPartner.id)
                                } else {
                                    repository.archiveConversation(targetPartner.id)
                                }
                                EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = if (isCurrentlyArchived) "${targetPartner.name} unarchived" else "${targetPartner.name} archived",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        if (isCurrentlyArchived) repository.archiveConversation(targetPartner.id)
                                        else repository.unarchiveConversation(targetPartner.id)
                                    }
                                }
                                selectedPartnerForActions = null
                            }
                            .testTag("modal_action_archive_${targetPartner.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF229ED9).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlyArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = if (isCurrentlyArchived) "Unarchive" else "Archive",
                                    tint = Color(0xFF229ED9),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCurrentlyArchived) "Unarchive Chat" else "Archive Chat",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isCurrentlyArchived) "Move back to All Chats" else "Hide conversation from the main list",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Action 3: View Full Profile
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedPartnerForActions = null
                                onViewProfile(targetPartner)
                            }
                            .testTag("modal_action_profile_${targetPartner.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "View Profile",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "View audio previews, bio, and social portfolio",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Action 4: Delete Private Chat (triggers Telegram confirmation dialog)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x22F43F5E),
                        border = BorderStroke(1.dp, Color(0x44F43F5E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                partnerPendingDeletion = targetPartner
                                selectedPartnerForActions = null
                            }
                            .testTag("modal_action_delete_${targetPartner.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE11D48)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delete Private Chat",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF43F5E)
                                )
                                Text(
                                    text = "Clear conversation with dust particle animation",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFDA4AF)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Confirmation Dialog before deleting PM (No = Green visual, Yes = Red visual)
    if (partnerPendingDeletion != null) {
        val target = partnerPendingDeletion!!
        DeletePmConfirmationDialog(
            partnerName = target.name,
            onConfirmDelete = {
                partnerPendingDeletion = null
                if (activePartnerId == target.id) {
                    coroutineScope.launch {
                        dissolvingConversationIds.add(target.id)
                        delay(400)
                        repository.deleteConversation(target.id)
                        repository.setActivePartner(null)
                        dissolvingConversationIds.remove(target.id)
                    }
                } else {
                    dissolvingConversationIds.add(target.id)
                }
            },
            onCancel = {
                partnerPendingDeletion = null
            }
        )
    }
}

/**
 * ChatBackgroundDialog: Allows users to customize the chat background
 * with gradients (Rose Gold, Nebula, Aurora, Violet) or blurred imagery
 * with dynamic contrast scrim overlays.
 */
@Composable
fun ChatBackgroundDialog(
    current: ChatBackgroundPreset,
    onSelect: (ChatBackgroundPreset) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Chat Wallpaper",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vibrant gradients & blurred scenery",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ChatBackgroundPreset.entries.toList(), key = { it.id }) { preset ->
                        val isSelected = preset == current
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelect(preset) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                ) {
                                    if (preset.gradientColors != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = preset.gradientColors!!.map { Color(it) }
                                                    )
                                                )
                                        )
                                    } else if (preset.imageUrl != null) {
                                        AsyncImage(
                                            model = preset.imageUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surface)
                                        )
                                    }

                                    if (preset.scrimAlpha > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = preset.scrimAlpha))
                                        )
                                    }

                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(preset.previewEmoji, fontSize = 16.sp)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (preset.gradientColors != null) "Vibrant Gradient Scrim" else if (preset.imageUrl != null) "Blurred Photo Scrim" else "Minimal Clean",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
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

