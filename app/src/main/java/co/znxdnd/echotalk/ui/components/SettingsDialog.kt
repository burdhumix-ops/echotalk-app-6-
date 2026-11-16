package co.znxdnd.echotalk.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import co.znxdnd.echotalk.auth.FirebaseAuthManager
import co.znxdnd.echotalk.data.local.AppRoomDatabase
import co.znxdnd.echotalk.data.local.OfflineQueueManager
import co.znxdnd.echotalk.data.repository.AppTheme
import co.znxdnd.echotalk.data.repository.ChatBackgroundPreset
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.data.storage.DriveManager
import co.znxdnd.echotalk.fcm.FcmNotificationManager
import co.znxdnd.echotalk.ui.theme.ThemeManager
import kotlinx.coroutines.launch
import co.znxdnd.echotalk.audio.UiSoundManager
import co.znxdnd.echotalk.utils.PerformanceManager
import co.znxdnd.echotalk.utils.PerformanceProfile
import co.znxdnd.echotalk.data.local.SyncManager
import co.znxdnd.echotalk.data.local.SyncState
import co.znxdnd.echotalk.auth.OnboardingManager
import co.znxdnd.echotalk.ui.screens.UserGuideScreen
import co.znxdnd.echotalk.utils.EchoHapticManager

@Composable
fun SettingsDialog(
    repository: EchoTalkRepository,
    onTriggerBackdoor: () -> Unit,
    onOpenVault: () -> Unit,
    onDismiss: () -> Unit,
    onOpenEditProfile: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onOpenUserGuide: () -> Unit = {}
) {
    co.znxdnd.echotalk.ui.screens.SettingsScreen(
        repository = repository,
        onTriggerBackdoor = onTriggerBackdoor,
        onOpenVault = onOpenVault,
        onDismiss = onDismiss,
        onOpenEditProfile = onOpenEditProfile,
        onReplayOnboarding = onReplayOnboarding,
        onOpenUserGuide = onOpenUserGuide
    )
}

@Composable
private fun LegacySettingsDialog(
    repository: EchoTalkRepository,
    onTriggerBackdoor: () -> Unit,
    onOpenVault: () -> Unit,
    onDismiss: () -> Unit,
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
    val isAuthenticating by authManager.isLoading.collectAsState()
    val authError by authManager.authError.collectAsState()

    val driveManager = remember { DriveManager.getInstance(context) }
    val lastBackupStatus by driveManager.lastBackupStatus.collectAsState()

    val roomDb = remember { AppRoomDatabase.getInstance(context) }
    val roomTasks by roomDb.pendingQueueDao().getAllPendingFlow().collectAsState(initial = emptyList())

    val isRoleGatewayUnlocked by repository.isRoleGatewayUnlocked.collectAsState()
    val isOwner = isRoleGatewayUnlocked && currentUser.role == "owner"
    var activeTab by remember { mutableStateOf("account") }

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
    val isUiSoundMuted by soundManager.isMuted.collectAsState()
    val uiSoundVolume by soundManager.volume.collectAsState()

    val syncManager = remember { SyncManager.getInstance(context) }
    val syncState by syncManager.syncState.collectAsState()
    val lastSyncTimestamp by syncManager.lastSyncTimestamp.collectAsState()
    val pendingSyncCount by syncManager.pendingCount.collectAsState(initial = 0)

    var reduceMotion by remember { mutableStateOf(EchoAnimations.reduceMotionGlobal) }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Settings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Preferences, privacy & theme controls",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Scrollable Category Tabs Row
                val tabScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tabScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = mutableListOf(
                        SettingsTab("account", "Account", Icons.Default.Person),
                        SettingsTab("privacy", "Privacy & Safety", Icons.Default.Security),
                        SettingsTab("notifications", "Notifications", Icons.Default.Notifications),
                        SettingsTab("appearance", "Appearance", Icons.Default.Palette),
                        SettingsTab("chats", "Chats & Messages", Icons.Default.Chat),
                        SettingsTab("rooms", "Rooms & Calls", Icons.Default.Phone),
                        SettingsTab("storage", "Storage & Backups", Icons.Default.Cloud),
                        SettingsTab("performance", "Performance & Sync", Icons.Default.Speed),
                        SettingsTab("help", "Help & About", Icons.AutoMirrored.Filled.HelpOutline)
                    )
                    if (isOwner) {
                        tabs.add(SettingsTab("owner", "Owner Center", Icons.Default.AdminPanelSettings))
                    }

                    tabs.forEach { tab ->
                        val isSelected = activeTab == tab.id
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    activeTab = tab.id
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Feedback Banner if cache was cleared
                AnimatedVisibility(visible = cacheFreedMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = semanticTokens.success.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, semanticTokens.success)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cacheFreedMessage ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = semanticTokens.success
                            )
                            IconButton(
                                onClick = { cacheFreedMessage = null },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = semanticTokens.success, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Tab Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        // =========================================================
                        // SECTION A: ACCOUNT
                        // =========================================================
                        "account" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(title = "Account Settings", subtitle = "Manage profile identity and authentication")
                                }

                                // 1. Edit Profile
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.Edit,
                                        title = "Edit Profile",
                                        subtitle = "Change name, avatar, bio, and audio preview title",
                                        actionLabel = "Edit",
                                        role = SemanticActionRole.ACCENT_PINK,
                                        onClick = {
                                            onDismiss()
                                            onOpenEditProfile()
                                        }
                                    )
                                }

                                // 2. Change Password
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.LockReset,
                                        title = "Change Password",
                                        subtitle = "Update authentication password securely",
                                        actionLabel = "Update",
                                        role = SemanticActionRole.ACCENT_PINK,
                                        onClick = { showChangePasswordDialog = true }
                                    )
                                }

                                // 3. Connected Account
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
                                                    Text("Connected Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = if (isGoogleSignedIn) (googleEmail ?: "Google Account") else "Demo Account (@${currentUser.handle})",
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

                                // 4. Sign Out
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.Logout,
                                        title = "Sign Out",
                                        subtitle = "Disconnect current session and return to authentication",
                                        actionLabel = "Sign Out",
                                        role = SemanticActionRole.CONFIRM_BLUE,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            authManager.signOut()
                                            OnboardingManager.getInstance(context).evaluateOnboarding(null)
                                            Toast.makeText(context, "Signed out of EchoTalks", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    )
                                }

                                // 5. Delete Account
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.DeleteForever,
                                        title = "Delete Account",
                                        subtitle = "Permanently purge user profile and all local data",
                                        actionLabel = "Delete",
                                        role = SemanticActionRole.DESTRUCTIVE_RED,
                                        onClick = { showDeleteAccountDialog = true }
                                    )
                                }
                            }
                        }

                        // =========================================================
                        // SECTION B: PRIVACY & SAFETY
                        // =========================================================
                        "privacy" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(title = "Privacy & Safety", subtitle = "Control profile discovery, presence, and messaging permissions")
                                }

                                // 1. Ghost Mode
                                item {
                                    SettingsSwitchCard(
                                        title = "Ghost Mode (Incognito)",
                                        subtitle = "Hide profile and live presence from Crew discovery",
                                        checked = settings.ghostMode,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            repository.updateGhostMode(it)
                                        }
                                    )
                                }

                                // 2. Profile Visibility
                                item {
                                    SettingsSwitchCard(
                                        title = "Profile Visibility",
                                        subtitle = "Show profile card in Crew search and discovery",
                                        checked = settings.profileVisibility,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            repository.updateProfileVisibility(it)
                                        }
                                    )
                                }

                                // 3. Online Status
                                item {
                                    SettingsSwitchCard(
                                        title = "Online Status",
                                        subtitle = "Share active green presence indicator with other users",
                                        checked = settings.onlineStatus,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            repository.updateOnlineStatus(it)
                                        }
                                    )
                                }

                                // 4. Read Receipts
                                item {
                                    SettingsSwitchCard(
                                        title = "Read Receipts",
                                        subtitle = "Share 'seen' status when you read incoming messages",
                                        checked = settings.readReceipts,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            repository.updateReadReceipts(it)
                                        }
                                    )
                                }

                                // 5. Typing Indicators
                                item {
                                    SettingsSwitchCard(
                                        title = "Typing Indicators",
                                        subtitle = "Broadcast real-time typing status when composing messages",
                                        checked = settings.typingIndicators,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            repository.updateTypingIndicators(it)
                                        }
                                    )
                                }

                                // 6. Direct Message Permissions
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Direct Message Permissions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Control who can initiate message requests with you:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf("Everyone", "Approved Connections").forEach { option ->
                                                    val isSelected = settings.dmPermissions.equals(option, ignoreCase = true)
                                                    Surface(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .clickable {
                                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                repository.updateDmPermissions(option)
                                                            },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isSelected) semanticColors.blueConfirm.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(1.dp, if (isSelected) semanticColors.blueConfirm else Color.Transparent)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = option,
                                                                fontSize = 11.sp,
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

                                // 7. Blocked Users
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.Block,
                                        title = "Blocked Users (${settings.blockedUsers.size})",
                                        subtitle = "View and unblock restricted contacts",
                                        actionLabel = "Manage",
                                        role = SemanticActionRole.ACCENT_PINK,
                                        onClick = { showBlockedUsersDialog = true }
                                    )
                                }

                                // 8. Report User
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.Flag,
                                        title = "Report User",
                                        subtitle = "Submit community guideline violation or safety report",
                                        actionLabel = "Report",
                                        role = SemanticActionRole.DESTRUCTIVE_RED,
                                        onClick = { showReportUserDialog = true }
                                    )
                                }
                            }
                        }

                        // =========================================================
                        // SECTION C: NOTIFICATIONS
                        // =========================================================
                        "notifications" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(title = "Notification Preferences", subtitle = "Configure push triggers, sounds, and vibration")
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Direct Messages",
                                        subtitle = "Alert on private 1v1 incoming messages",
                                        checked = settings.notifyDirectMessages,
                                        onCheckedChange = { repository.updateNotificationOption("dm", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Message Requests",
                                        subtitle = "Alert on new connection requests from unknown contacts",
                                        checked = settings.notifyMessageRequests,
                                        onCheckedChange = { repository.updateNotificationOption("msg_req", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Connection Requests",
                                        subtitle = "Alert when someone sends you a friend request",
                                        checked = settings.notifyConnectionRequests,
                                        onCheckedChange = { repository.updateNotificationOption("conn_req", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Room Invitations",
                                        subtitle = "Alert when invited to join a public or VIP audio room",
                                        checked = settings.notifyRoomInvitations,
                                        onCheckedChange = { repository.updateNotificationOption("room_invite", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Room Mentions",
                                        subtitle = "Alert when your handle is tagged in room chat",
                                        checked = settings.notifyRoomMentions,
                                        onCheckedChange = { repository.updateNotificationOption("room_mentions", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Incoming Calls",
                                        subtitle = "Full-screen alert on incoming audio or video calls",
                                        checked = settings.notifyIncomingCalls,
                                        onCheckedChange = { repository.updateNotificationOption("incoming_call", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Missed Calls",
                                        subtitle = "Summary notification when an incoming call was missed",
                                        checked = settings.notifyMissedCalls,
                                        onCheckedChange = { repository.updateNotificationOption("missed_call", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "System Notifications",
                                        subtitle = "Important security announcements and maintenance updates",
                                        checked = settings.notifySystem,
                                        onCheckedChange = { repository.updateNotificationOption("system", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Notification Sound",
                                        subtitle = "Synthesize pleasant chime tone on incoming alerts",
                                        checked = settings.notificationSound,
                                        onCheckedChange = { repository.updateNotificationOption("sound", it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Tactile Haptics (Google Pixel Style)",
                                        subtitle = "Micro-vibrations for DMs, games, emoji reactions & photo editor",
                                        checked = settings.notificationVibration,
                                        onCheckedChange = { enabled ->
                                            repository.updateNotificationOption("vibration", enabled)
                                            EchoHapticManager.getInstance(context).isVibrationEnabled = enabled
                                            if (enabled) {
                                                EchoHapticManager.getInstance(context).performPixelClick(view)
                                            }
                                        }
                                    )
                                }

                                // Interactive Tactile Test Row
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Test Tactile Haptics",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (settings.notificationVibration) "Tap buttons to feel subtle haptic waveforms" else "Vibration is currently muted in settings",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (settings.notificationVibration) Color(0x3310B981) else Color(0x33EF4444))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (settings.notificationVibration) "ACTIVE" else "OFF",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (settings.notificationVibration) Color(0xFF10B981) else Color(0xFFEF4444)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Pixel Click
                                                Button(
                                                    onClick = {
                                                        EchoHapticManager.getInstance(context).performPixelClick(view)
                                                        Toast.makeText(context, "Pixel Click", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f),
                                                    enabled = settings.notificationVibration
                                                ) {
                                                    Text("Pixel", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                                }

                                                // Telegram Send
                                                Button(
                                                    onClick = {
                                                        EchoHapticManager.getInstance(context).performTelegramMessageSent(view)
                                                        Toast.makeText(context, "Telegram Sent", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f),
                                                    enabled = settings.notificationVibration
                                                ) {
                                                    Text("Telegram", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                                                }

                                                // Messenger Burst
                                                Button(
                                                    onClick = {
                                                        EchoHapticManager.getInstance(context).performMessengerEmojiBurst(view)
                                                        Toast.makeText(context, "Messenger Burst", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f),
                                                    enabled = settings.notificationVibration
                                                ) {
                                                    Text("Messenger", fontSize = 10.sp, color = Color(0xFFC084FC), fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // FCM Test Push Notification
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
                                        Text("Send Test Push Notification", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // =========================================================
                        // SECTION D: APPEARANCE & CONTROLLED PERSONALIZATION
                        // =========================================================
                        "appearance" -> {
                            PersonalizationSettingsView(
                                repository = repository,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // =========================================================
                        // SECTION E: CHATS & MESSAGES
                        // =========================================================
                        "chats" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(title = "Chats & Messages", subtitle = "Message behavior, attachments, and cache lifecycle")
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Enter Key Sends",
                                        subtitle = "Pressing enter on the software keyboard immediately sends message",
                                        checked = settings.enterKeySends,
                                        onCheckedChange = { repository.updateEnterKeySends(it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Show Timestamps",
                                        subtitle = "Display send and receive timestamps next to message bubbles",
                                        checked = settings.showTimestamps,
                                        onCheckedChange = { repository.updateShowTimestamps(it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Auto-download Images",
                                        subtitle = "Automatically load photo attachments over Wi-Fi and mobile data",
                                        checked = settings.autoDownloadImages,
                                        onCheckedChange = { repository.updateAutoDownloadImages(it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Auto-download Audio",
                                        subtitle = "Preload voice note waveforms and audio clips for instant playback",
                                        checked = settings.autoDownloadAudio,
                                        onCheckedChange = { repository.updateAutoDownloadAudio(it) }
                                    )
                                }

                                // Expiration Notice Badge (24h DMs & 48h Special Rooms)
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
                                                Text("Auto-Expiration Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = semanticTokens.info)
                                                Text("DMs auto-expire in 24 hours. VIP rooms auto-purge every 48 hours for ephemeral privacy.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                // Clear Local Chat Cache
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.CleaningServices,
                                        title = "Clear Local Chat Cache",
                                        subtitle = "Purge local message cache from Room SQLite database",
                                        actionLabel = "Clear Cache",
                                        role = SemanticActionRole.DESTRUCTIVE_RED,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            val freedBytes = repository.clearLocalChatCache()
                                            val freedKb = (freedBytes / 1024L).coerceAtLeast(128L)
                                            cacheFreedMessage = "Successfully cleared local chat cache (Freed ~$freedKb KB)"
                                        }
                                    )
                                }
                            }
                        }

                        // =========================================================
                        // SECTION F: ROOMS & CALLS
                        // =========================================================
                        "rooms" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(title = "Rooms & Calls", subtitle = "Stage participation, calling gates, and hardware permissions")
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Allow Room Invitations",
                                        subtitle = "Permit friends to invite you into live voice stages and group rooms",
                                        checked = settings.allowRoomInvitations,
                                        onCheckedChange = { repository.updateAllowRoomInvitations(it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Allow Incoming Calls",
                                        subtitle = "Receive 1v1 audio and video calls from accepted connections",
                                        checked = settings.allowIncomingCalls,
                                        onCheckedChange = { repository.updateAllowIncomingCalls(it) }
                                    )
                                }

                                item {
                                    SettingsSwitchCard(
                                        title = "Mute Room Notifications",
                                        subtitle = "Silence background notifications while participating in voice stages",
                                        checked = settings.muteRoomNotifications,
                                        onCheckedChange = { repository.updateMuteRoomNotifications(it) }
                                    )
                                }

                                // Microphone Permission Status
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
                                                    Text("Microphone Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = if (micPermissionGranted) "Hardware access granted for live voice stages" else "Permission required for voice rooms and audio calls",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (!micPermissionGranted) {
                                                Button(
                                                    onClick = {
                                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Grant", fontSize = 10.sp)
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

                        // =========================================================
                        // SECTION G: STORAGE & BACKUPS
                        // =========================================================
                        "storage" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(title = "Storage & Backups", subtitle = "Google Account Vault, local SQLite cache, and conflict resolution")
                                }

                                // 1. Google Drive Connection
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
                                                    Icon(Icons.Default.Cloud, contentDescription = null, tint = semanticTokens.info, modifier = Modifier.size(16.dp))
                                                    Text("Google Drive Connection", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isGoogleSignedIn) semanticTokens.success.copy(alpha = 0.2f) else semanticColors.neutralInactive.copy(alpha = 0.2f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        if (isGoogleSignedIn) "Connected" else "Not Linked",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isGoogleSignedIn) semanticTokens.success else semanticColors.neutralInactive
                                                    )
                                                }
                                            }

                                            Text(
                                                text = if (isGoogleSignedIn) "Account: ${googleEmail ?: "Active"}" else "Sign in with Google to enable silent encrypted backups to your appDataFolder.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Last Backup Date
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(lastBackupStatus, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            // Action Buttons: Back Up & Restore
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
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
                                                    Text("Back Up Chats", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                        coroutineScope.launch {
                                                            val res = repository.restoreChatHistoryFromDrive(context)
                                                            if (res != null) {
                                                                Toast.makeText(context, "Restored and merged chats from cloud backup", Toast.LENGTH_SHORT).show()
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
                                                    Text("Restore Backup", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Local Storage Usage & Clear Cached Files
                                item {
                                    var cacheBytes by remember {
                                        mutableLongStateOf(context.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L)
                                    }
                                    val dbBytes = remember(roomTasks.size) {
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
                                                Text("Local Storage Usage", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("$totalFormatted SQLite & Cache", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Text(
                                                text = "Includes Room message cache, rendered studio exports (${android.text.format.Formatter.formatShortFileSize(context, cacheBytes)}), and offline tasks.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Button(
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                    val before = cacheBytes
                                                    context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                                                    cacheBytes = context.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                                                    val freedFormatted = android.text.format.Formatter.formatShortFileSize(context, (before - cacheBytes).coerceAtLeast(0L))
                                                    cacheFreedMessage = "Cleared $freedFormatted of temporary cached files"
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Clear Cached Files", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // 3. Pending Offline Messages Count with Flush Option
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
                                                Text("Pending Offline Messages", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(semanticTokens.warning.copy(alpha = 0.2f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("${roomTasks.size} in queue", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = semanticTokens.warning)
                                                }
                                            }
                                            Text(
                                                text = "Messages sent during offline mode are queued locally in SQLite and flushed automatically when online.",
                                                fontSize = 10.sp,
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
                                                Text("Flush Offline Messages Now", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // =========================================================
                        // SECTION: PERFORMANCE, SOUND & OFFLINE SYNC
                        // =========================================================
                        "performance" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(
                                        title = "Performance & Reliability Engine",
                                        subtitle = "Visual fidelity, tactile sounds, and background sync"
                                    )
                                }

                                // 1. Performance Profile Selection
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

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                PerformanceProfile.entries.forEach { p ->
                                                    val isSelected = activePerfProfile == p
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                            perfManager.setProfile(p)
                                                            reduceMotion = EchoAnimations.reduceMotionGlobal
                                                        },
                                                        label = { Text(p.name.take(4), fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Memory & Health Telemetry Card
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
                                                Text("Heap Memory Telemetry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Used: ${perfMetrics.memoryUsageMb} MB / Max: ${perfMetrics.maxHeapMb} MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Surface(
                                                color = if (perfMetrics.isLowMemory) semanticColors.redDestructive.copy(alpha = 0.2f) else semanticColors.greenSuccess.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (perfMetrics.isLowMemory) "Low RAM" else "Optimal",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (perfMetrics.isLowMemory) semanticColors.redDestructive else semanticColors.greenSuccess,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Tactile Sound Effects Suite
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
                                                    Text("UI Sound Effects", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text("Chimes on message send, receive, and arcade taps", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Switch(
                                                    checked = !isUiSoundMuted,
                                                    onCheckedChange = { soundManager.setMuted(!it) }
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

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { soundManager.playMessageSent() },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Test Send", fontSize = 11.sp)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { soundManager.playMessageReceived() },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Test Receive", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Accessibility: Reduce Motion
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Reduce Motion", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Disable spring physics bouncing and scale transitions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = reduceMotion,
                                                onCheckedChange = {
                                                    reduceMotion = it
                                                    EchoAnimations.reduceMotionGlobal = it
                                                }
                                            )
                                        }
                                    }
                                }

                                // 5. Offline-First Sync Status & Manual Flush
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Offline Queue & Sync Engine", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                        // =========================================================
                        // SECTION H: HELP & ABOUT
                        // =========================================================
                        "help" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    SettingsSectionHeader(title = "Help & About", subtitle = "Documentation, support, legal notices, and build info")
                                }

                                // User Guide
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.MenuBook,
                                        title = "User Guide & Pro Tips",
                                        subtitle = "Zero-cloud private DMs, spatial audio, and arcade cheatsheet",
                                        actionLabel = "Open Guide",
                                        role = SemanticActionRole.ACCENT_PINK,
                                        onClick = {
                                            showUserGuideDialog = true
                                            onOpenUserGuide()
                                        }
                                    )
                                }

                                // Replay Onboarding
                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.PlayCircleOutline,
                                        title = "Replay Onboarding Flow",
                                        subtitle = "Walk through the 7-step welcome experience again",
                                        actionLabel = "Replay",
                                        role = SemanticActionRole.CONFIRM_BLUE,
                                        onClick = {
                                            OnboardingManager.getInstance(context).replayOnboarding(currentUser.id)
                                            onDismiss()
                                            onReplayOnboarding()
                                        }
                                    )
                                }

                                item {
                                    SettingsActionRow(
                                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                                        title = "Help & FAQ",
                                        subtitle = "Answers to common questions about spatial audio, games, and privacy",
                                        actionLabel = "View",
                                        role = SemanticActionRole.CONFIRM_BLUE,
                                        onClick = { showHelpFaqDialog = true }
                                    )
                                }

                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.BugReport,
                                        title = "Report a Problem",
                                        subtitle = "Send crash reports, audio glitch logs, or general feedback",
                                        actionLabel = "Submit",
                                        role = SemanticActionRole.WARNING_YELLOW,
                                        onClick = { showReportProblemDialog = true }
                                    )
                                }

                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.Policy,
                                        title = "Privacy Policy",
                                        subtitle = "Read our zero cross-syncing and ephemeral chat commitments",
                                        actionLabel = "Read",
                                        role = SemanticActionRole.CONFIRM_BLUE,
                                        onClick = { showPrivacyPolicyDialog = true }
                                    )
                                }

                                item {
                                    SettingsActionRow(
                                        icon = Icons.Default.Description,
                                        title = "Terms of Service",
                                        subtitle = "Community standards, safety rules, and service terms",
                                        actionLabel = "Read",
                                        role = SemanticActionRole.CONFIRM_BLUE,
                                        onClick = { showTermsDialog = true }
                                    )
                                }

                                // App Version Card
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
                                                Text("EchoTalks for Android", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Version 2.4.0 • Release Build 2026.11", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(semanticColors.blueConfirm.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Latest", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = semanticColors.blueConfirm)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // =========================================================
                        // SECTION I: OWNER CENTER (Hidden, authenticated owner only)
                        // =========================================================
                        "owner" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
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

                                // 1. User Verification Manager
                                item {
                                    Text("User Verification Manager", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Toggle verified blue tick badge instantly for members:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                                // 2. Owner Quarantine Vault
                                item {
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenVault()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Inspect Owner Quarantine Vault", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 3. Network Pipeline Controls
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
                                                Text("Simulated Offline Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = if (isOffline) "Device offline: writes stored in SQLite with 0ms latency" else "Device online: live socket network sync active",
                                                    fontSize = 10.sp,
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
                    }
                }

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // =========================================================
    // SUB-DIALOGS (Change Password, Delete, Blocked, etc.)
    // =========================================================

    // Change Password Dialog
    if (showChangePasswordDialog) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var passError by remember { mutableStateOf<String?>(null) }
        var isUpdatingPass by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isUpdatingPass) showChangePasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = { currentPass = it; passError = null },
                        label = { Text("Current Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; passError = null },
                        label = { Text("New Password (min 6 chars)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (passError != null) {
                        Text(passError!!, color = semanticTokens.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUpdatingPass,
                    onClick = {
                        when {
                            newPass.length < 6 -> passError = "New password must be at least 6 characters."
                            currentPass.isNotBlank() && currentPass == newPass -> passError = "New password must differ from current."
                            else -> {
                                isUpdatingPass = true
                                coroutineScope.launch {
                                    val res = authManager.updatePassword(newPass)
                                    isUpdatingPass = false
                                    res.onSuccess {
                                        showChangePasswordDialog = false
                                        Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        passError = err.message ?: "Failed to update password."
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isUpdatingPass) "Saving..." else "Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { if (!isUpdatingPass) showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Account Confirmation Dialog
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
                        co.znxdnd.echotalk.auth.OnboardingManager.getInstance(context).evaluateOnboarding(null)
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

    // Blocked Users Dialog
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

    // Report User Dialog
    if (showReportUserDialog) {
        var targetUser by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        var reportError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showReportUserDialog = false },
            title = { Text("Report User", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submit safety concerns or abusive behavior directly to moderators:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = targetUser,
                        onValueChange = { targetUser = it; reportError = null },
                        label = { Text("User handle or ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it; reportError = null },
                        label = { Text("Reason for report") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    if (reportError != null) {
                        Text(reportError!!, color = semanticTokens.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (repository.submitSafetyReport(targetUser, reason)) {
                            showReportUserDialog = false
                            Toast.makeText(context, "Report logged to Trust & Safety quarantine", Toast.LENGTH_LONG).show()
                        } else {
                            reportError = "Enter both a user handle and a reason."
                        }
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

    // Help & FAQ Dialog
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
                        FaqItem(
                            q = "How does Spatial Audio in Voice Rooms work?",
                            a = "EchoTalk utilizes binaural multi-channel DSP to place speakers around your soundstage for natural, low-latency live stage audio."
                        )
                    }
                    item {
                        FaqItem(
                            q = "What is the 3-Message Request limit?",
                            a = "When contacting someone who hasn't accepted your connection, you can send up to 3 messages until they approve to prevent unwanted spam."
                        )
                    }
                    item {
                        FaqItem(
                            q = "How does Google Drive appDataFolder backup work?",
                            a = "Backups are stored inside your private Google Drive appDataFolder. They are fully isolated from central servers with zero cross-syncing."
                        )
                    }
                    item {
                        FaqItem(
                            q = "How do 1v1 Chess challenges work?",
                            a = "Tap the game controller in any conversation to launch a live interactive Chess challenge directly inside the chat."
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

    // Report a Problem Dialog
    if (showReportProblemDialog) {
        var problemDesc by remember { mutableStateOf("") }
        var problemError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showReportProblemDialog = false },
            title = { Text("Report a Problem", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Describe any audio, rendering, or connectivity bug you encountered:", fontSize = 11.sp)
                    OutlinedTextField(
                        value = problemDesc,
                        onValueChange = { problemDesc = it; problemError = null },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    if (problemError != null) {
                        Text(problemError!!, color = semanticTokens.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val diag = repository.submitDiagnosticReport(problemDesc)
                        if (diag != null) {
                            showReportProblemDialog = false
                            Toast.makeText(context, "Diagnostic report logged ($diag)", Toast.LENGTH_SHORT).show()
                        } else {
                            problemError = "Please describe the issue before submitting."
                        }
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

    // Privacy Policy Dialog
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

    // Terms of Service Dialog
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

    // User Guide Dialog
    if (showUserGuideDialog) {
        Dialog(
            onDismissRequest = { showUserGuideDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            UserGuideScreen(onBack = { showUserGuideDialog = false })
        }
    }
}

private data class SettingsTab(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchCard(
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    role: SemanticActionRole,
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.interactiveSemanticClick(
                    role = role,
                    onClick = onClick
                )
            ) {
                Text(actionLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(q, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(a, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
