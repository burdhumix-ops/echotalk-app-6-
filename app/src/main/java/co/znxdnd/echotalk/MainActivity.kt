package co.znxdnd.echotalk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import co.znxdnd.echotalk.ai.GeminiChatService
import co.znxdnd.echotalk.ai.configureAppCheck
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.data.remote.SupabaseAuthService
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.di.DatabaseModule
import co.znxdnd.echotalk.fcm.FcmNotificationManager
import co.znxdnd.echotalk.ui.components.*
import co.znxdnd.echotalk.ui.screens.*
import co.znxdnd.echotalk.ui.theme.EchoTalkTheme

class MainActivity : ComponentActivity() {

    private val repository = EchoTalkRepository.getInstance()
    private val aiService = GeminiChatService()
    private val soundscapePlayer = SoundscapePlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch Intent before UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Initialize Firestore client via DatabaseModule for real-time chat data storage
        DatabaseModule.provideFirestore(this)

        // STEP 3: Initialize Room database for local chat message caching and offline queue
        val roomDb = co.znxdnd.echotalk.data.local.AppRoomDatabase.getInstance(this)
        repository.setRoomDatabase(roomDb)
        val queueMgr = co.znxdnd.echotalk.data.local.OfflineQueueManager.getInstance(this)
        repository.setOfflineQueueManager(queueMgr)
        co.znxdnd.echotalk.utils.ResilienceGuardian.runStartupSelfRepair(this, repository, queueMgr)
        repository.initSettings(this)
        co.znxdnd.echotalk.calling.CallController.initialize(this)
        co.znxdnd.echotalk.fcm.FcmNotificationManager.initialize(this)
        co.znxdnd.echotalk.audio.SoundEffectManager.getInstance(this)
        co.znxdnd.echotalk.audio.UiSoundManager.getInstance(this)
        co.znxdnd.echotalk.utils.PerformanceManager.getInstance(this)
        val hapticManager = co.znxdnd.echotalk.utils.EchoHapticManager.initialize(this)
        val personalizationManager = co.znxdnd.echotalk.data.personalization.PersonalizationManager.getInstance(this)
        val soundEffectMgr = co.znxdnd.echotalk.audio.SoundEffectManager.getInstance(this)
        val uiSoundMgr = co.znxdnd.echotalk.audio.UiSoundManager.getInstance(this)

        fun applyFeedbackSettings(
            repoSettings: co.znxdnd.echotalk.data.local.EchoSettings,
            pConfig: co.znxdnd.echotalk.data.personalization.PersonalizationConfig
        ) {
            hapticManager.isVibrationEnabled = repoSettings.notificationVibration && pConfig.hapticFeedbackEnabled
            hapticManager.setIntensityLevel(
                if (pConfig.hapticIntensity == co.znxdnd.echotalk.data.personalization.HapticIntensity.FIRM_EXPRESSIVE) 1.35f else 0.9f
            )
            val muteAudio = !repoSettings.notificationSound || !pConfig.soundEffectsEnabled
            soundEffectMgr.setMuted(muteAudio)
            uiSoundMgr.setMuted(muteAudio)
            EchoAnimations.animationLevelGlobal = pConfig.animationLevel
            EchoAnimations.reduceMotionGlobal = repoSettings.reduceAnimations ||
                pConfig.animationLevel == co.znxdnd.echotalk.data.personalization.AnimationLevel.OFF ||
                pConfig.performanceMode == co.znxdnd.echotalk.data.personalization.PerformanceMode.BATTERY_SAVER
        }

        applyFeedbackSettings(repository.settings.value, personalizationManager.config.value)
        lifecycleScope.launch {
            repository.settings.collect { s ->
                applyFeedbackSettings(s, personalizationManager.config.value)
            }
        }
        lifecycleScope.launch {
            personalizationManager.config.collect { cfg ->
                applyFeedbackSettings(repository.settings.value, cfg)
            }
        }
        co.znxdnd.echotalk.data.local.SyncManager.getInstance(this)

        enableEdgeToEdge()

        setContent {
            val activeTheme by repository.theme.collectAsState()
            val currentUser by repository.currentUser.collectAsState()
            val isRoleGatewayUnlocked by repository.isRoleGatewayUnlocked.collectAsState()
            val activeRoomId by repository.activeRoomId.collectAsState()
            val activePartnerId by repository.activePartnerId.collectAsState()
            val users by repository.users.collectAsState()
            val vaultEntries by repository.adminVault.collectAsState()

            val personalizationConfig by personalizationManager.config.collectAsState()
            var currentTab by remember {
                mutableStateOf(
                    if (personalizationConfig.personalizationEnabled) {
                        NavTab.entries.find { it.id == personalizationConfig.lastSelectedTabId } ?: NavTab.DISCOVER
                    } else {
                        NavTab.DISCOVER
                    }
                )
            }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var showSoundscapeDialog by remember { mutableStateOf(false) }
            var showVaultDialog by remember { mutableStateOf(false) }
            var showPasscodeDialog by remember { mutableStateOf(false) }
            var showEditProfileDialog by remember { mutableStateOf(false) }
            var selectedProfileUser by remember { mutableStateOf<User?>(null) }

            // Challenge launcher state from DMs or Discover to Arcade
            var challengeGameId by remember { mutableStateOf<String?>(null) }
            var challengeOpponent by remember { mutableStateOf<String?>(null) }

            // Hide bars when in 100% full-screen room or DM chat
            val isImmersive = activeRoomId != null || activePartnerId != null

            // DM Badge: count pending message requests
            val pendingCount = users.count { !it.isFriend }

            val onboardingManager = remember { co.znxdnd.echotalk.auth.OnboardingManager.getInstance(this) }
            val onboardingState by onboardingManager.state.collectAsState()
            var showUserGuideScreen by remember { mutableStateOf(false) }

            val callController = remember { co.znxdnd.echotalk.calling.CallController.getInstance() }
            val callUiState by callController.uiState.collectAsState()
            val pendingCallPermissions by callController.permissionPromptTrigger.collectAsState()
            val callRemoteTrack by (callController.mediaClient?.remoteVideoTrack?.collectAsState() ?: remember { mutableStateOf(null) })
            val callLocalTrack by (callController.mediaClient?.localVideoTrack?.collectAsState() ?: remember { mutableStateOf(null) })

            val callPermissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                val permanentlyDenied = results.mapValues { (perm, granted) ->
                    !granted && !ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, perm)
                }
                callController.onPermissionsResult(results, permanentlyDenied)
            }

            LaunchedEffect(pendingCallPermissions) {
                val perms = pendingCallPermissions
                if (!perms.isNullOrEmpty()) {
                    callController.onPermissionPromptLaunched()
                    callPermissionsLauncher.launch(perms.toTypedArray())
                }
            }

            LaunchedEffect(currentUser.id) {
                onboardingManager.evaluateOnboarding(currentUser.id)
            }

            LaunchedEffect(onboardingState) {
                if (onboardingState is co.znxdnd.echotalk.auth.OnboardingState.Completed &&
                    SupabaseConfig.isConfigured &&
                    !repository.isPreSignInPreviewSession.value
                ) {
                    repository.hydrateFromSupabase()
                    FcmNotificationManager.syncCurrentTokenToBackend()
                    SupabaseSocialRepository.getInstance().startGlobalPresence(
                        ghostMode = repository.settings.value.ghostMode,
                        showOnlineStatus = repository.settings.value.onlineStatus
                    )
                    callController.startIncomingCallListenerIfSignedIn()
                    handleLaunchIntentExtras(intent)
                }
            }

            LaunchedEffect(currentTab) {
                personalizationManager.setLastSelectedTab(currentTab.id)
            }

            LaunchedEffect(activePartnerId) {
                activePartnerId?.let { partnerId ->
                    personalizationManager.recordConversationOpened(partnerId)
                }
            }

            EchoTalkTheme(theme = activeTheme) {
                when {
                    showUserGuideScreen -> {
                        UserGuideScreen(onBack = { showUserGuideScreen = false })
                    }
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.AuthenticationRequired -> {
                        AuthScreen(
                            onAuthSuccess = { uid ->
                                val sbUser = SupabaseAuthService.getInstance(this@MainActivity).currentUser.value
                                val fbUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                                when {
                                    sbUser != null -> {
                                        repository.setCurrentUser(
                                            currentUser.copy(
                                                id = sbUser.id,
                                                name = sbUser.displayName?.ifBlank { currentUser.name } ?: currentUser.name
                                            )
                                        )
                                        repository.hydrateFromSupabase()
                                    }
                                    fbUser != null -> {
                                        repository.setCurrentUser(
                                            currentUser.copy(
                                                id = fbUser.uid,
                                                name = fbUser.displayName?.ifBlank { currentUser.name } ?: currentUser.name
                                            )
                                        )
                                    }
                                }
                                onboardingManager.evaluateOnboarding(uid)
                            },
                            onPreSignInPreviewSuccess = { uid ->
                                onboardingManager.enterPreSignInPreview(uid)
                            }
                        )
                    }
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.CheckingAccount ||
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Connecting to EchoTalks...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.ProfileSetup ||
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.NeedsProfileSetup -> {
                        val targetUid = when (val s = onboardingState) {
                            is co.znxdnd.echotalk.auth.OnboardingState.ProfileSetup -> s.userId
                            is co.znxdnd.echotalk.auth.OnboardingState.NeedsProfileSetup -> s.userId
                            else -> currentUser.id
                        }
                        ProfileSetupScreen(
                            repository = repository,
                            onComplete = {
                                onboardingManager.setLocalProfileCompleted(targetUid, true)
                                onboardingManager.evaluateOnboarding(targetUid)
                            }
                        )
                    }
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.Guide ||
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.InProgress -> {
                        val targetUid = when (val s = onboardingState) {
                            is co.znxdnd.echotalk.auth.OnboardingState.Guide -> s.userId
                            is co.znxdnd.echotalk.auth.OnboardingState.InProgress -> s.userId
                            else -> currentUser.id
                        }
                        val onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                        OnboardingScreen(
                            viewModel = onboardingViewModel,
                            onFinish = {
                                onboardingManager.evaluateOnboarding(targetUid)
                            }
                        )
                    }
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.Completing -> {
                        val targetUid = (onboardingState as co.znxdnd.echotalk.auth.OnboardingState.Completing).userId
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Finalizing your setup...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                TextButton(
                                    onClick = { onboardingManager.emergencyEnterApp(targetUid) }
                                ) {
                                    Text(
                                        text = "Taking too long? Enter EchoTalks",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                    onboardingState is co.znxdnd.echotalk.auth.OnboardingState.RecoverableError -> {
                        val errorState = onboardingState as co.znxdnd.echotalk.auth.OnboardingState.RecoverableError
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(16.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Setup Notice",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = errorState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = { onboardingManager.recoverFromStuckState(errorState.userId) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Resume Setup")
                                    }
                                    if (errorState.canContinueToApp) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { onboardingManager.emergencyEnterApp(errorState.userId) },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Enter EchoTalks")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val floatingBarClearance = if (isImmersive) 0.dp else (78.dp + navBarBottomInset)

                        CompositionLocalProvider(LocalFloatingBottomBarClearance provides floatingBarClearance) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = MaterialTheme.colorScheme.background,
                                    topBar = {
                                        if (!isImmersive) {
                                            TopBar(
                                                currentUser = currentUser,
                                                onOpenSettings = { showSettingsDialog = true },
                                                onOpenSoundscapes = { showSoundscapeDialog = true },
                                                onOpenVault = { showVaultDialog = true },
                                                onTriggerBackdoor = { showPasscodeDialog = true },
                                                modifier = Modifier.statusBarsPadding()
                                            )
                                        }
                                    }
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(
                                                top = if (isImmersive) {
                                                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                                                } else {
                                                    innerPadding.calculateTopPadding()
                                                },
                                                bottom = if (isImmersive) {
                                                    navBarBottomInset
                                                } else {
                                                    0.dp // Content extends full-bleed under the floating BottomNavBar
                                                }
                                            )
                                    ) {
                                        Crossfade(targetState = currentTab, label = "TabSwitch") { tab ->
                                            when (tab) {
                                                NavTab.DISCOVER -> {
                                                    DiscoverScreen(
                                                        repository = repository,
                                                        onStartDM = { user ->
                                                            repository.setActivePartner(user.id)
                                                            currentTab = NavTab.DMS
                                                        },
                                                        onViewProfile = { user ->
                                                            selectedProfileUser = user
                                                        },
                                                        onLaunchGameChallenge = { gameId, oppName ->
                                                            challengeGameId = gameId
                                                            challengeOpponent = oppName
                                                            currentTab = NavTab.ARCADE
                                                        }
                                                    )
                                                }
                                                NavTab.ROOMS -> {
                                                    RoomsScreen(
                                                        repository = repository,
                                                        onOpenDoodle = {}
                                                    )
                                                }
                                                NavTab.DMS -> {
                                                    DMsScreen(
                                                        repository = repository,
                                                        onLaunchGameChallenge = { gameId, oppName ->
                                                            challengeGameId = gameId
                                                            challengeOpponent = oppName
                                                            currentTab = NavTab.ARCADE
                                                        },
                                                        onViewProfile = { user ->
                                                            selectedProfileUser = user
                                                        },
                                                        aiService = aiService
                                                    )
                                                }
                                                NavTab.AI -> {
                                                    GeminiChatScreen(
                                                        onNavigateToTab = { tab ->
                                                            currentTab = tab
                                                            challengeGameId = null
                                                        },
                                                        onStartDMWithDraft = { userId, draft ->
                                                            repository.setActivePartner(userId)
                                                            currentTab = NavTab.DMS
                                                        },
                                                        onOpenSoundscapes = { showSoundscapeDialog = true },
                                                        onOpenVault = { showVaultDialog = true },
                                                        onLaunchGame = { gameId ->
                                                            challengeGameId = gameId
                                                            challengeOpponent = "Friend"
                                                            currentTab = NavTab.ARCADE
                                                        }
                                                    )
                                                }
                                                NavTab.ARCADE -> {
                                                    GamesScreen(
                                                        repository = repository,
                                                        initialGameId = challengeGameId,
                                                        opponentName = challengeOpponent
                                                    )
                                                }
                                                NavTab.PROFILE -> {
                                                    ProfileScreen(
                                                        repository = repository,
                                                        onOpenVault = { showVaultDialog = true },
                                                        onOpenSoundscapes = { showSoundscapeDialog = true },
                                                        onTriggerBackdoor = { showPasscodeDialog = true }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Overlapping frosted glass floating BottomNavBar
                                if (!isImmersive) {
                                    BottomNavBar(
                                        activeTab = currentTab,
                                        onTabSelected = { tab ->
                                            currentTab = tab
                                            challengeGameId = null
                                        },
                                        dmBadgeCount = pendingCount,
                                        currentUser = currentUser,
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }

                // Global Modals
                if (showPasscodeDialog) {
                    PasscodeDialog(
                        onDismiss = { showPasscodeDialog = false },
                        onRoleGranted = { _ -> },
                        repository = repository,
                        isCurrentlyUnlocked = isRoleGatewayUnlocked,
                        serverRole = currentUser.role
                    )
                }

                if (showVaultDialog && isRoleGatewayUnlocked && (currentUser.role == "owner" || currentUser.role == "admin")) {
                    SecretVaultDialog(
                        vaultEntries = vaultEntries,
                        onDeleteEntry = { id -> repository.deleteVaultEntry(id) },
                        onClearAll = { repository.clearVault() },
                        onDismiss = { showVaultDialog = false }
                    )
                }

                if (showSoundscapeDialog) {
                    SoundscapeDialog(
                        player = soundscapePlayer,
                        onDismiss = { showSoundscapeDialog = false }
                    )
                }

                if (showSettingsDialog) {
                    SettingsScreen(
                        repository = repository,
                        onTriggerBackdoor = { showPasscodeDialog = true },
                        onOpenVault = { showVaultDialog = true },
                        onDismiss = { showSettingsDialog = false },
                        onOpenEditProfile = { showEditProfileDialog = true },
                        onReplayOnboarding = {
                            onboardingManager.replayOnboarding(currentUser.id)
                        },
                        onOpenUserGuide = {
                            showUserGuideScreen = true
                        }
                    )
                }

                if (showEditProfileDialog) {
                    EditProfileDialog(
                        currentUser = currentUser,
                        repository = repository,
                        onDismiss = { showEditProfileDialog = false }
                    )
                }

                if (selectedProfileUser != null) {
                    FullBleedProfileDialog(
                        user = selectedProfileUser!!,
                        onStartDM = { user ->
                            repository.setActivePartner(user.id)
                            currentTab = NavTab.DMS
                        },
                        onDismiss = { selectedProfileUser = null },
                        onLaunchGameChallenge = { gameId, oppName ->
                            challengeGameId = gameId
                            challengeOpponent = oppName
                            currentTab = NavTab.ARCADE
                        }
                    )
                }
                    }
                }

                // Global 1-to-1 Voice & Video Calling Overlays (Phase 1)
                if (callUiState.isIncomingVisible && callUiState.incomingInvite != null) {
                    IncomingCallOverlay(
                        invite = callUiState.incomingInvite!!,
                        onAccept = { callController.acceptIncomingCall() },
                        onReject = { callController.rejectIncomingCall() }
                    )
                }

                if (callUiState.isModalVisible) {
                    CallModalDialog(
                        uiState = callUiState,
                        remoteVideoTrack = callRemoteTrack,
                        localVideoTrack = callLocalTrack,
                        onInitRenderer = { renderer -> callController.initVideoRenderer(renderer) },
                        onToggleMute = { callController.toggleMute() },
                        onToggleCamera = { callController.toggleCamera() },
                        onFlipCamera = { callController.flipCamera() },
                        onSelectAudioRoute = { route -> callController.selectAudioRoute(route) },
                        onRetryPermission = { callController.retryPendingPermissions() },
                        onDismissPermissionRationale = { callController.dismissPermissionRationale() },
                        onEndCall = { callController.endCall() },
                        onDismissTerminal = { callController.dismissModal() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntentExtras(intent)
    }

    private fun handleLaunchIntentExtras(launchIntent: Intent?) {
        val extras = launchIntent?.extras ?: return
        val callId = extras.getString("CALL_ID")
        if (!callId.isNullOrBlank()) {
            val callType = extras.getString("CALL_TYPE")
            val callerId = extras.getString("CALLER_ID")
            val callerName = extras.getString("CALLER_NAME")
            co.znxdnd.echotalk.calling.CallController.getInstance().handleNotificationOrDeepLinkIntent(
                callId = callId,
                callTypeWire = callType,
                callerId = callerId,
                callerName = callerName
            )
            launchIntent.removeExtra("CALL_ID")
        }
    }

    override fun onPause() {
        super.onPause()
        if (SupabaseConfig.isConfigured && !repository.isPreSignInPreviewSession.value) {
            lifecycleScope.launch {
                val repo = SupabaseSocialRepository.getInstance()
                repo.setPresenceSharingEnabled(false)
                repo.updatePresenceHeartbeat(
                    online = false,
                    ghostMode = repository.settings.value.ghostMode,
                    force = true
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val queueMgr = co.znxdnd.echotalk.data.local.OfflineQueueManager.getInstance(this)
        co.znxdnd.echotalk.utils.ResilienceGuardian.onAppForegrounded(this, repository, queueMgr)
        if (SupabaseConfig.isConfigured && !repository.isPreSignInPreviewSession.value) {
            lifecycleScope.launch {
                val repo = SupabaseSocialRepository.getInstance()
                val visibleOnline = repository.settings.value.onlineStatus && !repository.settings.value.ghostMode
                repo.setPresenceSharingEnabled(visibleOnline)
                repo.updatePresenceHeartbeat(
                    online = repository.settings.value.onlineStatus,
                    ghostMode = repository.settings.value.ghostMode,
                    force = true
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (SupabaseConfig.isConfigured) {
            SupabaseSocialRepository.getInstance().stopGlobalPresence()
        }
        soundscapePlayer.stop()
        co.znxdnd.echotalk.audio.SoundEffectManager.getInstance().release()
        co.znxdnd.echotalk.audio.UiSoundManager.getInstance().release()
    }
}
