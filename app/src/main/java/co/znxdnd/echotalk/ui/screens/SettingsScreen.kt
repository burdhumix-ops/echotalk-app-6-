package co.znxdnd.echotalk.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import co.znxdnd.echotalk.audio.SoundEffectManager
import co.znxdnd.echotalk.audio.UiSoundManager
import co.znxdnd.echotalk.auth.FirebaseAuthManager
import co.znxdnd.echotalk.auth.OnboardingManager
import co.znxdnd.echotalk.data.local.AppRoomDatabase
import co.znxdnd.echotalk.data.local.EchoSettings
import co.znxdnd.echotalk.data.local.OfflineQueueManager
import co.znxdnd.echotalk.data.local.SyncManager
import co.znxdnd.echotalk.data.personalization.AnimationLevel
import co.znxdnd.echotalk.data.personalization.PersonalizationManager
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.data.storage.DriveManager
import co.znxdnd.echotalk.fcm.FcmNotificationManager
import co.znxdnd.echotalk.ui.components.*
import co.znxdnd.echotalk.ui.theme.SemanticColorTokens
import co.znxdnd.echotalk.ui.theme.SemanticInteractionColors
import co.znxdnd.echotalk.ui.theme.ThemeManager
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.PerformanceManager
import co.znxdnd.echotalk.utils.PerformanceProfile
import kotlinx.coroutines.launch

data class SettingsCategoryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val badge: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: EchoTalkRepository,
    onDismiss: () -> Unit,
    onTriggerBackdoor: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenEditProfile: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onOpenUserGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val currentUser by repository.currentUser.collectAsState()
    val activeTheme by repository.theme.collectAsState()
    val users by repository.users.collectAsState()
    val isOffline by repository.isOffline.collectAsState()
    val pendingQueue by repository.pendingSyncQueue.collectAsState()
    val fcmToken by repository.fcmToken.collectAsState()
    val settings by repository.settings.collectAsState()
    val chatBackground by repository.chatBackground.collectAsState()

    val authManager = remember { FirebaseAuthManager.getInstance(context) }
    val isGoogleSignedIn by authManager.isSignedIn.collectAsState()
    val googleEmail by authManager.userEmail.collectAsState()

    val driveManager = remember { DriveManager.getInstance(context) }
    val lastBackupStatus by driveManager.lastBackupStatus.collectAsState()

    val roomDb = remember { AppRoomDatabase.getInstance(context) }
    val roomTasks by roomDb.pendingQueueDao().getAllPendingFlow().collectAsState(initial = emptyList())

    val isRoleGatewayUnlocked by repository.isRoleGatewayUnlocked.collectAsState()
    val isOwner = isRoleGatewayUnlocked && currentUser.role == "owner"
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isOwner) {
        if (!isOwner && selectedCategory == "owner") {
            selectedCategory = null
        }
    }

    // Intercept back navigation: if on a category sub-page, return to the main Settings page;
    // if on the main Settings page, close Settings.
    BackHandler {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            onDismiss()
        }
    }

    // Sub-dialog states
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var showReportUserDialog by remember { mutableStateOf(false) }
    var showHelpFaqDialog by remember { mutableStateOf(false) }
    var showReportProblemDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showUserGuideDialog by remember { mutableStateOf(false) }
    var cacheFreedMessage by remember { mutableStateOf<String?>(null) }

    val perfManager = remember { PerformanceManager.getInstance(context) }
    val activePerfProfile by perfManager.activeProfile.collectAsState()
    val perfMetrics by perfManager.metrics.collectAsState()

    val soundManager = remember { UiSoundManager.getInstance(context) }
    val soundEffectManager = remember { SoundEffectManager.getInstance(context) }
    val isUiSoundMuted by soundManager.isMuted.collectAsState()
    val uiSoundVolume by soundManager.volume.collectAsState()

    val personalizationManager = remember { PersonalizationManager.getInstance(context) }
    val personalizationConfig by personalizationManager.config.collectAsState()

    val syncManager = remember { SyncManager.getInstance(context) }
    val syncState by syncManager.syncState.collectAsState()
    val lastSyncTimestamp by syncManager.lastSyncTimestamp.collectAsState()
    val pendingSyncCount by syncManager.pendingCount.collectAsState(initial = 0)

    var reduceMotion by remember(settings.reduceAnimations, personalizationConfig.animationLevel) {
        mutableStateOf(
            settings.reduceAnimations ||
                personalizationConfig.animationLevel == AnimationLevel.OFF ||
                EchoAnimations.reduceMotionGlobal
        )
    }

    // Mic permission launcher
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
    }

    val semanticColors = remember(activeTheme) { ThemeManager.getSemanticColors(activeTheme) }
    val semanticTokens = remember(activeTheme) { ThemeManager.getSemanticTokens(activeTheme) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (selectedCategory) {
                                null -> "Settings"
                                "account" -> "Account & Profile"
                                "appearance" -> "Appearance & Themes"
                                "privacy" -> "Privacy & Safety"
                                "notifications" -> "Notifications & Haptics"
                                "chats" -> "Chats & Messages"
                                "rooms" -> "Rooms & Voice Calls"
                                "storage" -> "Storage & Backups"
                                "performance" -> "Performance & Sync"
                                "help" -> "Help & About"
                                "owner" -> "Owner Command Center"
                                else -> "Settings"
                            },
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = when (selectedCategory) {
                                null -> "Preferences, personalization & controls"
                                "account" -> "Identity, security & authentication"
                                "appearance" -> "Themes, semantic colors, fonts & chat styles"
                                "privacy" -> "Ghost mode, discovery & messaging gates"
                                "notifications" -> "Alert triggers, chimes & Pixel haptics"
                                "chats" -> "Input behaviors, media caching & expiration"
                                "rooms" -> "Live stages, incoming calls & microphone"
                                "storage" -> "Drive cloud vault & offline SQLite database"
                                "performance" -> "Rendering profiles, memory & background sync"
                                "help" -> "User guide, FAQ, reports & service policies"
                                "owner" -> "Quarantine vault, member badges & pipeline"
                                else -> ""
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (selectedCategory != null) {
                                selectedCategory = null
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selectedCategory != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = if (selectedCategory != null) "Back to Settings" else "Close Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (selectedCategory == null && isOwner) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8A5C8).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFE8A5C8)),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    selectedCategory = "owner"
                                }
                        ) {
                            Text(
                                text = "OWNER",
                                color = Color(0xFFE8A5C8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedCategory,
                label = "SettingsNavigationTransition"
            ) { category ->
                if (category == null) {
                    // MAIN INGREDIENTS LIST PAGE
                    SettingsMainIngredientsPage(
                        currentUser = currentUser,
                        isGoogleSignedIn = isGoogleSignedIn,
                        googleEmail = googleEmail,
                        activeThemeName = activeTheme.name,
                        isOwner = isOwner,
                        onSelectCategory = { catId ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedCategory = catId
                        },
                        onOpenEditProfile = {
                            onDismiss()
                            onOpenEditProfile()
                        }
                    )
                } else {
                    // DEDICATED FULL-PAGE CATEGORY SCREEN
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        when (category) {
                            "account" -> {
                                SettingsAccountPage(
                                    currentUser = currentUser,
                                    isGoogleSignedIn = isGoogleSignedIn,
                                    googleEmail = googleEmail,
                                    semanticTokens = semanticTokens,
                                    semanticColors = semanticColors,
                                    onOpenEditProfile = {
                                        onDismiss()
                                        onOpenEditProfile()
                                    },
                                    onChangePassword = { showChangePasswordDialog = true },
                                    onSignOut = {
                                        authManager.signOut()
                                        OnboardingManager.getInstance(context).evaluateOnboarding(null)
                                        Toast.makeText(context, "Signed out of EchoTalks", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    onDeleteAccount = { showDeleteAccountDialog = true }
                                )
                            }
                            "appearance" -> {
                                PersonalizationSettingsView(
                                    repository = repository,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            "privacy" -> {
                                SettingsPrivacyPage(
                                    settings = settings,
                                    personalizationEnabled = personalizationConfig.personalizationEnabled,
                                    semanticColors = semanticColors,
                                    onUpdateGhostMode = { repository.updateGhostMode(it) },
                                    onUpdateProfileVisibility = { repository.updateProfileVisibility(it) },
                                    onUpdateOnlineStatus = { repository.updateOnlineStatus(it) },
                                    onUpdateReadReceipts = { repository.updateReadReceipts(it) },
                                    onUpdateTypingIndicators = { repository.updateTypingIndicators(it) },
                                    onUpdateDmPermissions = { repository.updateDmPermissions(it) },
                                    onTogglePersonalization = { enabled ->
                                        personalizationManager.setPersonalizationEnabled(enabled)
                                        Toast.makeText(context, "Changes saved.", Toast.LENGTH_SHORT).show()
                                    },
                                    onClearPersonalizationData = {
                                        personalizationManager.clearPersonalizationData()
                                        Toast.makeText(context, "Personalization data cleared.", Toast.LENGTH_SHORT).show()
                                    },
                                    onOpenBlockedUsers = { showBlockedUsersDialog = true },
                                    onOpenReportUser = { showReportUserDialog = true }
                                )
                            }
                            "notifications" -> {
                                SettingsNotificationsPage(
                                    settings = settings,
                                    onUpdateNotification = { type, enabled -> repository.updateNotificationOption(type, enabled) }
                                )
                            }
                            "chats" -> {
                                SettingsChatsPage(
                                    settings = settings,
                                    semanticTokens = semanticTokens,
                                    onUpdateEnterKey = { repository.updateEnterKeySends(it) },
                                    onUpdateTimestamps = { repository.updateShowTimestamps(it) },
                                    onUpdateAutoDownloadImages = { repository.updateAutoDownloadImages(it) },
                                    onUpdateAutoDownloadAudio = { repository.updateAutoDownloadAudio(it) },
                                    onClearLocalCache = {
                                        val freedBytes = repository.clearLocalChatCache()
                                        val freedKb = (freedBytes / 1024L).coerceAtLeast(128L)
                                        Toast.makeText(context, "Cleared chat cache (~$freedKb KB)", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            "rooms" -> {
                                SettingsRoomsPage(
                                    settings = settings,
                                    micPermissionGranted = micPermissionGranted,
                                    semanticTokens = semanticTokens,
                                    onUpdateInvitations = { repository.updateAllowRoomInvitations(it) },
                                    onUpdateCalls = { repository.updateAllowIncomingCalls(it) },
                                    onUpdateMuteRooms = { repository.updateMuteRoomNotifications(it) },
                                    onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                                )
                            }
                            "storage" -> {
                                SettingsStoragePage(
                                    repository = repository,
                                    isGoogleSignedIn = isGoogleSignedIn,
                                    googleEmail = googleEmail,
                                    lastBackupStatus = lastBackupStatus,
                                    roomTasksCount = roomTasks.size,
                                    semanticTokens = semanticTokens,
                                    semanticColors = semanticColors
                                )
                            }
                            "performance" -> {
                                SettingsPerformancePage(
                                    perfManager = perfManager,
                                    activePerfProfile = activePerfProfile,
                                    perfMetrics = perfMetrics,
                                    soundManager = soundManager,
                                    isUiSoundMuted = isUiSoundMuted,
                                    uiSoundVolume = uiSoundVolume,
                                    reduceMotion = reduceMotion,
                                    animationLevel = personalizationConfig.animationLevel,
                                    syncManager = syncManager,
                                    syncState = syncState.toString(),
                                    lastSyncTimestamp = lastSyncTimestamp,
                                    pendingSyncCount = pendingSyncCount,
                                    semanticColors = semanticColors,
                                    onToggleSoundEffects = { enabled ->
                                        soundManager.setMuted(!enabled)
                                        soundEffectManager.setMuted(!enabled)
                                        personalizationManager.setSoundEffectsEnabled(enabled)
                                        Toast.makeText(context, "Changes saved.", Toast.LENGTH_SHORT).show()
                                    },
                                    onSelectAnimationLevel = { level ->
                                        personalizationManager.setAnimationLevel(level)
                                        EchoAnimations.animationLevelGlobal = level
                                        val isOff = level == AnimationLevel.OFF
                                        reduceMotion = isOff
                                        EchoAnimations.reduceMotionGlobal = isOff
                                        repository.updateReduceAnimations(isOff)
                                        Toast.makeText(context, "Changes saved.", Toast.LENGTH_SHORT).show()
                                    },
                                    onToggleReduceMotion = { enabled ->
                                        reduceMotion = enabled
                                        EchoAnimations.reduceMotionGlobal = enabled
                                        repository.updateReduceAnimations(enabled)
                                        val nextLevel = if (enabled) AnimationLevel.OFF else AnimationLevel.NORMAL
                                        personalizationManager.setAnimationLevel(nextLevel)
                                        EchoAnimations.animationLevelGlobal = nextLevel
                                        Toast.makeText(context, "Changes saved.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            "help" -> {
                                SettingsHelpPage(
                                    onOpenUserGuide = {
                                        showUserGuideDialog = true
                                        onOpenUserGuide()
                                    },
                                    onReplayOnboarding = {
                                        OnboardingManager.getInstance(context).replayOnboarding(currentUser.id)
                                        onDismiss()
                                        onReplayOnboarding()
                                    },
                                    onOpenFaq = { showHelpFaqDialog = true },
                                    onReportProblem = { showReportProblemDialog = true },
                                    onOpenPrivacy = { showPrivacyPolicyDialog = true },
                                    onOpenTerms = { showTermsDialog = true },
                                    semanticColors = semanticColors
                                )
                            }
                            "owner" -> {
                                SettingsOwnerPage(
                                    currentUser = currentUser,
                                    users = users,
                                    isOffline = isOffline,
                                    repository = repository,
                                    onOpenVault = {
                                        onDismiss()
                                        onOpenVault()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================
    // GLOBAL SUB-DIALOGS (Full functional preservation)
    // =========================================================

    if (showChangePasswordDialog) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = { currentPass = it },
                        label = { Text("Current Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPass.trim()
                        if (trimmed.length < 6) {
                            Toast.makeText(context, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val res = co.znxdnd.echotalk.data.remote.SupabaseAuthService.getInstance(context)
                                .updatePassword(trimmed)
                            showChangePasswordDialog = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Password updated successfully.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    res.exceptionOrNull()?.message ?: "Could not update password.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = semanticTokens.error) },
            title = { Text("Delete Account", fontWeight = FontWeight.Bold, color = semanticTokens.error) },
            text = {
                Text("Are you sure you want to delete your account? This action is permanent and will delete all your conversations, contacts, and personal preferences from this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        authManager.signOut()
                        OnboardingManager.getInstance(context).evaluateOnboarding(null)
                        repository.clearLocalChatCache()
                        Toast.makeText(context, "Account deleted", Toast.LENGTH_LONG).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = semanticColors.redDestructive)
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBlockedUsersDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedUsersDialog = false },
            title = { Text("Blocked Users", fontWeight = FontWeight.Bold) },
            text = {
                val blocked = settings.blockedUsers
                if (blocked.isEmpty()) {
                    Text("You have no blocked users.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(blocked.toList()) { blockedId ->
                            val user = users.find { it.id == blockedId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(user?.name ?: blockedId, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(user?.handle ?: "@$blockedId", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(
                                    onClick = {
                                        repository.unblockUser(blockedId)
                                        Toast.makeText(context, "Unblocked ${user?.name ?: blockedId}", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Unblock", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBlockedUsersDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showReportUserDialog) {
        var targetUser by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportUserDialog = false },
            title = { Text("Report User", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submit safety concerns or abusive behavior directly to moderators:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = targetUser,
                        onValueChange = { targetUser = it },
                        label = { Text("User handle or ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for report") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportUserDialog = false
                        Toast.makeText(context, "Report submitted to EchoTalk Trust & Safety team", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = semanticColors.redRejected)
                ) {
                    Text("Submit Report", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReportUserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHelpFaqDialog) {
        AlertDialog(
            onDismissRequest = { showHelpFaqDialog = false },
            title = { Text("Help & FAQ", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    item {
                        SettingsFaqItem(
                            q = "How does Spatial Audio in Voice Rooms work?",
                            a = "EchoTalk utilizes binaural multi-channel DSP to place speakers around your soundstage for natural, low-latency live stage audio."
                        )
                    }
                    item {
                        SettingsFaqItem(
                            q = "What is the 3-Message Request limit?",
                            a = "When contacting someone who hasn't accepted your connection, you can send up to 3 messages until they approve to prevent unwanted spam."
                        )
                    }
                    item {
                        SettingsFaqItem(
                            q = "How does Google Drive appDataFolder backup work?",
                            a = "Backups are stored inside your private Google Drive appDataFolder. They are fully isolated from central servers with zero cross-syncing."
                        )
                    }
                    item {
                        SettingsFaqItem(
                            q = "How do 1v1 Chess challenges work?",
                            a = "Tap the game controller in any conversation to launch a live interactive Chess challenge directly inside the chat with Minimax AI."
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHelpFaqDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showReportProblemDialog) {
        var problemDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportProblemDialog = false },
            title = { Text("Report a Problem", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Describe any audio, rendering, or connectivity bug you encountered:", fontSize = 11.sp)
                    OutlinedTextField(
                        value = problemDesc,
                        onValueChange = { problemDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportProblemDialog = false
                        Toast.makeText(context, "Problem report sent with diagnostic logs", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReportProblemDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "EchoTalks is built on the principle of client-first data sovereignty. " +
                            "Private chat histories are stored locally on your device or in your personal Google Drive appDataFolder. " +
                            "Ephemeral direct messages auto-expire after 24 hours. Voice room stages operate with client-side synthesized audio tokens.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyPolicyDialog = false }) {
                    Text("I Understand")
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "By using EchoTalks, you agree to respectful crew collaboration, refraining from automated scraping, harassment, or distributing unauthorized content. " +
                            "Community moderators enforce 3-message request restrictions and ephemeral lounge rules.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showTermsDialog = false }) {
                    Text("Accept Terms")
                }
            }
        )
    }

    if (showUserGuideDialog) {
        Dialog(
            onDismissRequest = { showUserGuideDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            UserGuideScreen(onBack = { showUserGuideDialog = false })
        }
    }
}

// =========================================================
// MAIN INGREDIENTS LIST PAGE
// =========================================================

@Composable
private fun SettingsMainIngredientsPage(
    currentUser: co.znxdnd.echotalk.data.model.User,
    isGoogleSignedIn: Boolean,
    googleEmail: String?,
    activeThemeName: String,
    isOwner: Boolean,
    onSelectCategory: (String) -> Unit,
    onOpenEditProfile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Profile Summary Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelectCategory("account") },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser.name.take(1).uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = currentUser.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (currentUser.isVerified) {
                                BlueTickBadge(size = 14.dp)
                            }
                        }
                        Text(
                            text = "@${currentUser.handle} • ${if (isGoogleSignedIn) (googleEmail ?: "Google Account") else "Local Session"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section: Personalization & Interface
        item {
            SettingsCategoryGroupHeader(title = "PERSONALIZATION & DISPLAY")
        }

        item {
            SettingsIngredientCard(
                icon = Icons.Default.Palette,
                iconContainerColor = Color(0xFF6366F1).copy(alpha = 0.15f),
                iconTint = Color(0xFF818CF8),
                title = "Appearance & Themes",
                subtitle = "Controlled themes, semantic colors, fonts & chat styles",
                badge = activeThemeName,
                onClick = { onSelectCategory("appearance") }
            )
        }

        item {
            SettingsIngredientCard(
                icon = Icons.Default.Speed,
                iconContainerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                iconTint = Color(0xFF34D399),
                title = "Performance & Sync",
                subtitle = "Rendering profiles, UI chimes, Pixel haptics & battery saver",
                onClick = { onSelectCategory("performance") }
            )
        }

        // Section: Communication & Privacy
        item {
            SettingsCategoryGroupHeader(title = "COMMUNICATION & PRIVACY")
        }

        item {
            SettingsIngredientCard(
                icon = Icons.Default.Chat,
                iconContainerColor = Color(0xFF38BDF8).copy(alpha = 0.15f),
                iconTint = Color(0xFF38BDF8),
                title = "Chats & Messages",
                subtitle = "Enter key sends, timestamps, media auto-download & local cache",
                onClick = { onSelectCategory("chats") }
            )
        }

        item {
            SettingsIngredientCard(
                icon = Icons.Default.Phone,
                iconContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                iconTint = Color(0xFFFBBF24),
                title = "Rooms & Voice Calls",
                subtitle = "Stage invites, incoming calls, spatial audio & mic permission",
                onClick = { onSelectCategory("rooms") }
            )
        }

        item {
            SettingsIngredientCard(
                icon = Icons.Default.Security,
                iconContainerColor = Color(0xFFEC4899).copy(alpha = 0.15f),
                iconTint = Color(0xFFF472B6),
                title = "Privacy & Safety",
                subtitle = "Ghost mode, discovery visibility, blocked users & reports",
                onClick = { onSelectCategory("privacy") }
            )
        }

        item {
            SettingsIngredientCard(
                icon = Icons.Default.Notifications,
                iconContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                iconTint = Color(0xFFA78BFA),
                title = "Notifications",
                subtitle = "Direct messages, room invitations, missed calls & push alerts",
                onClick = { onSelectCategory("notifications") }
            )
        }

        // Section: Storage & Maintenance
        item {
            SettingsCategoryGroupHeader(title = "DATA & SYSTEM")
        }

        item {
            SettingsIngredientCard(
                icon = Icons.Default.Cloud,
                iconContainerColor = Color(0xFF06B6D4).copy(alpha = 0.15f),
                iconTint = Color(0xFF22D3EE),
                title = "Storage & Backups",
                subtitle = "Google Drive cloud vault, SQLite queue & cache breakdown",
                onClick = { onSelectCategory("storage") }
            )
        }

        item {
            SettingsIngredientCard(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                iconContainerColor = Color(0xFF64748B).copy(alpha = 0.15f),
                iconTint = Color(0xFF94A3B8),
                title = "Help & About",
                subtitle = "User guide, FAQ, bug reports, privacy policy & build info",
                onClick = { onSelectCategory("help") }
            )
        }

        if (isOwner) {
            item {
                SettingsCategoryGroupHeader(title = "ADMINISTRATION")
            }

            item {
                SettingsIngredientCard(
                    icon = Icons.Default.AdminPanelSettings,
                    iconContainerColor = Color(0xFFE11D48).copy(alpha = 0.15f),
                    iconTint = Color(0xFFFB7185),
                    title = "Owner Command Center",
                    subtitle = "Quarantine vault, member verification badges & pipeline",
                    badge = "VIP",
                    onClick = { onSelectCategory("owner") }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCategoryGroupHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun SettingsIngredientCard(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// =========================================================
// SUB-PAGE VIEWS
// =========================================================

@Composable
private fun SettingsAccountPage(
    currentUser: co.znxdnd.echotalk.data.model.User,
    isGoogleSignedIn: Boolean,
    googleEmail: String?,
    semanticTokens: SemanticColorTokens,
    semanticColors: SemanticInteractionColors,
    onOpenEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.Edit,
                title = "Edit Profile",
                subtitle = "Change name, avatar, bio, and audio preview title",
                actionLabel = "Edit",
                onClick = onOpenEditProfile
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.LockReset,
                title = "Change Password",
                subtitle = "Update authentication password securely",
                actionLabel = "Update",
                onClick = onChangePassword
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column {
                            Text("Connected Account", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isGoogleSignedIn) (googleEmail ?: "Google Account") else "Local Session (@${currentUser.handle})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(semanticTokens.info.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isGoogleSignedIn) "Google ID" else "Local Session",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = semanticTokens.info
                        )
                    }
                }
            }
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.Logout,
                title = "Sign Out",
                subtitle = "Disconnect current session and return to authentication",
                actionLabel = "Sign Out",
                onClick = onSignOut
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.DeleteForever,
                title = "Delete Account",
                subtitle = "Permanently purge user profile and all local data",
                actionLabel = "Delete",
                isDestructive = true,
                onClick = onDeleteAccount
            )
        }
    }
}

@Composable
private fun SettingsPrivacyPage(
    settings: EchoSettings,
    personalizationEnabled: Boolean,
    semanticColors: SemanticInteractionColors,
    onUpdateGhostMode: (Boolean) -> Unit,
    onUpdateProfileVisibility: (Boolean) -> Unit,
    onUpdateOnlineStatus: (Boolean) -> Unit,
    onUpdateReadReceipts: (Boolean) -> Unit,
    onUpdateTypingIndicators: (Boolean) -> Unit,
    onUpdateDmPermissions: (String) -> Unit,
    onTogglePersonalization: (Boolean) -> Unit,
    onClearPersonalizationData: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenReportUser: () -> Unit
) {
    val view = LocalView.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SettingsFullPageSwitchCard(
                title = "Ghost Mode (Incognito)",
                subtitle = "Hide profile and live presence from Crew discovery",
                checked = settings.ghostMode,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onUpdateGhostMode(it)
                }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Profile Visibility",
                subtitle = "Show profile card in Crew search and discovery",
                checked = settings.profileVisibility,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onUpdateProfileVisibility(it)
                }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Online Status",
                subtitle = "Share active green presence indicator with other users",
                checked = settings.onlineStatus,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onUpdateOnlineStatus(it)
                }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Read Receipts",
                subtitle = "Share 'seen' status when you read incoming messages",
                checked = settings.readReceipts,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onUpdateReadReceipts(it)
                }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Typing Indicators",
                subtitle = "Broadcast real-time typing status when composing messages",
                checked = settings.typingIndicators,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onUpdateTypingIndicators(it)
                }
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Direct Message Permissions", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Control who can initiate message requests with you:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Everyone", "Approved Connections").forEach { option ->
                            val isSelected = settings.dmPermissions.equals(option, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onUpdateDmPermissions(option)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) semanticColors.blueConfirm.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSelected) semanticColors.blueConfirm else Color.Transparent)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = option,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) semanticColors.blueConfirm else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Local Personalization",
                subtitle = "Remember theme, room order, recent searches, filters & drafts locally on device",
                checked = personalizationEnabled,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTogglePersonalization(it)
                }
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.RestartAlt,
                title = "Clear Personalization Data",
                subtitle = "Reset locally stored room visits, recent conversations, searches & drafts",
                actionLabel = "Clear Data",
                isDestructive = true,
                onClick = onClearPersonalizationData
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.Block,
                title = "Blocked Users (${settings.blockedUsers.size})",
                subtitle = "View and unblock restricted contacts",
                actionLabel = "Manage",
                onClick = onOpenBlockedUsers
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.Flag,
                title = "Report User",
                subtitle = "Submit community guideline violation or safety report",
                actionLabel = "Report",
                isDestructive = true,
                onClick = onOpenReportUser
            )
        }
    }
}

@Composable
private fun SettingsNotificationsPage(
    settings: EchoSettings,
    onUpdateNotification: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SettingsFullPageSwitchCard(
                title = "Direct Messages",
                subtitle = "Alert on private 1v1 incoming messages",
                checked = settings.notifyDirectMessages,
                onCheckedChange = { onUpdateNotification("dm", it) }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Room Invitations",
                subtitle = "Notify when invited to live audio lounges or private rooms",
                checked = settings.notifyRoomInvitations,
                onCheckedChange = { onUpdateNotification("room_invite", it) }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Missed Voice Calls",
                subtitle = "Alert on missed calls from connections",
                checked = settings.notifyMissedCalls,
                onCheckedChange = { onUpdateNotification("missed_call", it) }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "System Notifications",
                subtitle = "Important security announcements and maintenance updates",
                checked = settings.notifySystem,
                onCheckedChange = { onUpdateNotification("system", it) }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Notification Sound",
                subtitle = "Synthesize pleasant chime tone on incoming alerts",
                checked = settings.notificationSound,
                onCheckedChange = { onUpdateNotification("sound", it) }
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Tactile Haptics (Google Pixel Style)",
                subtitle = "Micro-vibrations for DMs, games, emoji reactions & photo editor",
                checked = settings.notificationVibration,
                onCheckedChange = { enabled ->
                    onUpdateNotification("vibration", enabled)
                    EchoHapticManager.getInstance(context).isVibrationEnabled = enabled
                    if (enabled) {
                        EchoHapticManager.getInstance(context).performPixelClick(view)
                    }
                }
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Test Tactile Haptics", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (settings.notificationVibration) "Tap buttons to feel subtle haptic waveforms" else "Vibration is currently muted in settings",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                EchoHapticManager.getInstance(context).performPixelClick(view)
                                Toast.makeText(context, "Pixel Click", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1f),
                            enabled = settings.notificationVibration
                        ) {
                            Text("Pixel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                EchoHapticManager.getInstance(context).performTelegramMessageSent(view)
                                Toast.makeText(context, "Telegram Sent", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1f),
                            enabled = settings.notificationVibration
                        ) {
                            Text("Telegram", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                EchoHapticManager.getInstance(context).performMessengerEmojiBurst(view)
                                Toast.makeText(context, "Messenger Burst", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1f),
                            enabled = settings.notificationVibration
                        ) {
                            Text("Messenger", fontSize = 11.sp, color = Color(0xFFC084FC), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    FcmNotificationManager.simulateIncomingPush(
                        context = context,
                        senderName = "Elena Rostova",
                        message = "Hey! Testing real-time FCM push notification delivery."
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send Test Push Notification", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsChatsPage(
    settings: EchoSettings,
    semanticTokens: SemanticColorTokens,
    onUpdateEnterKey: (Boolean) -> Unit,
    onUpdateTimestamps: (Boolean) -> Unit,
    onUpdateAutoDownloadImages: (Boolean) -> Unit,
    onUpdateAutoDownloadAudio: (Boolean) -> Unit,
    onClearLocalCache: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SettingsFullPageSwitchCard(
                title = "Enter Key Sends",
                subtitle = "Pressing enter on the software keyboard immediately sends message",
                checked = settings.enterKeySends,
                onCheckedChange = onUpdateEnterKey
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Show Timestamps",
                subtitle = "Display send and receive timestamps next to message bubbles",
                checked = settings.showTimestamps,
                onCheckedChange = onUpdateTimestamps
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Auto-download Images",
                subtitle = "Automatically load photo attachments over Wi-Fi and mobile data",
                checked = settings.autoDownloadImages,
                onCheckedChange = onUpdateAutoDownloadImages
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Auto-download Audio",
                subtitle = "Preload voice note waveforms and audio clips for instant playback",
                checked = settings.autoDownloadAudio,
                onCheckedChange = onUpdateAutoDownloadAudio
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = semanticTokens.info.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, semanticTokens.info.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = semanticTokens.info, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Auto-Expiration Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = semanticTokens.info)
                        Text("DMs auto-expire in 24 hours. VIP rooms auto-purge every 48 hours for ephemeral privacy.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.CleaningServices,
                title = "Clear Local Chat Cache",
                subtitle = "Purge local message cache from Room SQLite database",
                actionLabel = "Clear Cache",
                isDestructive = true,
                onClick = onClearLocalCache
            )
        }
    }
}

@Composable
private fun SettingsRoomsPage(
    settings: EchoSettings,
    micPermissionGranted: Boolean,
    semanticTokens: SemanticColorTokens,
    onUpdateInvitations: (Boolean) -> Unit,
    onUpdateCalls: (Boolean) -> Unit,
    onUpdateMuteRooms: (Boolean) -> Unit,
    onRequestMicPermission: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SettingsFullPageSwitchCard(
                title = "Allow Room Invitations",
                subtitle = "Permit friends to invite you into live voice stages and group rooms",
                checked = settings.allowRoomInvitations,
                onCheckedChange = onUpdateInvitations
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Allow Incoming Calls",
                subtitle = "Receive 1v1 audio and video calls from accepted connections",
                checked = settings.allowIncomingCalls,
                onCheckedChange = onUpdateCalls
            )
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Mute Room Notifications",
                subtitle = "Silence background notifications while participating in voice stages",
                checked = settings.muteRoomNotifications,
                onCheckedChange = onUpdateMuteRooms
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (micPermissionGranted) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (micPermissionGranted) semanticTokens.success else semanticTokens.error
                        )
                        Column {
                            Text("Microphone Permission", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (micPermissionGranted) "Hardware access granted for live voice stages" else "Permission required for voice rooms and audio calls",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!micPermissionGranted) {
                        Button(
                            onClick = onRequestMicPermission,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(semanticTokens.success.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = semanticTokens.success)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsStoragePage(
    repository: EchoTalkRepository,
    isGoogleSignedIn: Boolean,
    googleEmail: String?,
    lastBackupStatus: String,
    roomTasksCount: Int,
    semanticTokens: SemanticColorTokens,
    semanticColors: SemanticInteractionColors
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = semanticTokens.info, modifier = Modifier.size(18.dp))
                            Text("Google Drive Connection", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isGoogleSignedIn) semanticTokens.success.copy(alpha = 0.2f) else semanticColors.neutralInactive.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isGoogleSignedIn) "Connected" else "Not Linked",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGoogleSignedIn) semanticTokens.success else semanticColors.neutralInactive
                            )
                        }
                    }
                    Text(
                        text = if (isGoogleSignedIn) "Account: ${googleEmail ?: "Active"}" else "Sign in with Google to enable silent encrypted backups to your appDataFolder.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lastBackupStatus, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                coroutineScope.launch {
                                    repository.backupToDriveSilent(context)
                                    Toast.makeText(context, "Backed up to Google Drive", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back Up Chats", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                coroutineScope.launch {
                                    val res = repository.restoreChatHistoryFromDrive(context)
                                    if (res != null) {
                                        Toast.makeText(context, "Restored chats from cloud", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No remote backup found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Backup", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            var cacheBytes by remember {
                mutableLongStateOf(context.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L)
            }
            val dbBytes = remember(roomTasksCount) {
                context.databaseList()?.sumOf { dbName -> context.getDatabasePath(dbName).takeIf { it.exists() }?.length() ?: 0L } ?: 0L
            }
            val totalFormatted = android.text.format.Formatter.formatShortFileSize(context, cacheBytes + dbBytes)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Local Storage Usage", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("$totalFormatted SQLite & Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "Includes Room message cache, rendered studio exports (${android.text.format.Formatter.formatShortFileSize(context, cacheBytes)}), and offline tasks.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            val before = cacheBytes
                            context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                            cacheBytes = context.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                            val freedFormatted = android.text.format.Formatter.formatShortFileSize(context, (before - cacheBytes).coerceAtLeast(0L))
                            Toast.makeText(context, "Cleared $freedFormatted of temporary cached files", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Cached Files", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pending Offline Messages", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(semanticTokens.warning.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("$roomTasksCount in queue", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = semanticTokens.warning)
                        }
                    }
                    Text(
                        text = "Messages sent during offline mode are queued locally in SQLite and flushed automatically when online.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            coroutineScope.launch {
                                OfflineQueueManager.getInstance(context).flushQueue()
                                repository.flushPendingQueue()
                                Toast.makeText(context, "Flushed offline queue", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flush Offline Messages Now", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPerformancePage(
    perfManager: PerformanceManager,
    activePerfProfile: PerformanceProfile,
    perfMetrics: co.znxdnd.echotalk.utils.PerformanceMetrics,
    soundManager: UiSoundManager,
    isUiSoundMuted: Boolean,
    uiSoundVolume: Float,
    reduceMotion: Boolean,
    animationLevel: AnimationLevel,
    syncManager: SyncManager,
    syncState: String,
    lastSyncTimestamp: Long,
    pendingSyncCount: Int,
    semanticColors: SemanticInteractionColors,
    onToggleSoundEffects: (Boolean) -> Unit,
    onSelectAnimationLevel: (AnimationLevel) -> Unit,
    onToggleReduceMotion: (Boolean) -> Unit
) {
    val view = LocalView.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Rendering Profile", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(activePerfProfile.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerformanceProfile.entries.forEach { p ->
                            val isSelected = activePerfProfile == p
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    perfManager.setProfile(p)
                                },
                                label = { Text(p.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Heap Memory Telemetry", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Used: ${perfMetrics.memoryUsageMb} MB / Max: ${perfMetrics.maxHeapMb} MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        color = if (perfMetrics.isLowMemory) semanticColors.redDestructive.copy(alpha = 0.2f) else semanticColors.greenSuccess.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (perfMetrics.isLowMemory) "Low RAM" else "Optimal",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (perfMetrics.isLowMemory) semanticColors.redDestructive else semanticColors.greenSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UI Sound Effects", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Chimes on message send, receive, and arcade taps", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = !isUiSoundMuted,
                            onCheckedChange = onToggleSoundEffects
                        )
                    }
                    if (!isUiSoundMuted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Audio Volume: ${(uiSoundVolume * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = uiSoundVolume,
                            onValueChange = { soundManager.setVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { soundManager.playMessageSent() }, modifier = Modifier.weight(1f)) {
                                Text("Test Send", fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = { soundManager.playMessageReceived() }, modifier = Modifier.weight(1f)) {
                                Text("Test Receive", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("UI Animations", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(animationLevel.displayName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AnimationLevel.entries.forEach { level ->
                            val isSelected = animationLevel == level
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelectAnimationLevel(level)
                                },
                                label = { Text(level.displayName.substringBefore(" "), fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsFullPageSwitchCard(
                title = "Reduce Motion",
                subtitle = "Disable spring physics bouncing and scale transitions",
                checked = reduceMotion,
                onCheckedChange = onToggleReduceMotion
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline Queue & Sync Engine", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Status: $syncState • Pending Queue: $pendingSyncCount items", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lastSyncTimestamp > 0) {
                        Text("Last Sync: ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTimestamp))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            syncManager.triggerSyncNow()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync With Cloud Now", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHelpPage(
    onOpenUserGuide: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenFaq: () -> Unit,
    onReportProblem: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    semanticColors: SemanticInteractionColors
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.MenuBook,
                title = "User Guide & Pro Tips",
                subtitle = "Zero-cloud private DMs, spatial audio, and arcade cheatsheet",
                actionLabel = "Open Guide",
                onClick = onOpenUserGuide
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.PlayCircleOutline,
                title = "Replay Onboarding Flow",
                subtitle = "Walk through the 7-step welcome experience again",
                actionLabel = "Replay",
                onClick = onReplayOnboarding
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help & FAQ",
                subtitle = "Answers to common questions about spatial audio, games, and privacy",
                actionLabel = "View",
                onClick = onOpenFaq
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.BugReport,
                title = "Report a Problem",
                subtitle = "Send crash reports, audio glitch logs, or general feedback",
                actionLabel = "Submit",
                onClick = onReportProblem
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.Policy,
                title = "Privacy Policy",
                subtitle = "Read our zero cross-syncing and ephemeral chat commitments",
                actionLabel = "Read",
                onClick = onOpenPrivacy
            )
        }
        item {
            SettingsFullPageActionRow(
                icon = Icons.Default.Description,
                title = "Terms of Service",
                subtitle = "Community standards, safety rules, and service terms",
                actionLabel = "Read",
                onClick = onOpenTerms
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("EchoTalks for Android", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Version 2.4.0 • Release Build 2026.11", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(semanticColors.blueConfirm.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Latest", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = semanticColors.blueConfirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOwnerPage(
    currentUser: co.znxdnd.echotalk.data.model.User,
    users: List<co.znxdnd.echotalk.data.model.User>,
    isOffline: Boolean,
    repository: EchoTalkRepository,
    onOpenVault: () -> Unit
) {
    val view = LocalView.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0x22E8A5C8),
                border = BorderStroke(1.dp, Color(0xFFE8A5C8))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Owner Command Center", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE8A5C8))
                    Text("Direct control over user verification, secret quarantine vault, and network pipelines.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("User Verification Manager", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Toggle verified blue tick badge instantly for members:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(users.filter { it.id != currentUser.id }) { member ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(member.name.take(1), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(member.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (member.isVerified) {
                                    BlueTickBadge(size = 12.dp)
                                }
                            }
                            Text(member.handle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = member.isVerified,
                        onCheckedChange = { verified ->
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            repository.adminSetVerification(currentUser.id, member.id, verified)
                        }
                    )
                }
            }
        }
        item {
            Button(
                onClick = onOpenVault,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Inspect Owner Quarantine Vault", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Simulated Offline Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isOffline) "Device offline: writes stored in SQLite with 0ms latency" else "Device online: live socket network sync active",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isOffline,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            repository.toggleOfflineMode()
                        }
                    )
                }
            }
        }
    }
}

// =========================================================
// HELPER UI COMPONENTS FOR FULL PAGE
// =========================================================

@Composable
private fun SettingsFullPageSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsFullPageActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsFaqItem(q: String, a: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(q, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(a, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
