package co.znxdnd.echotalk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import co.znxdnd.echotalk.data.model.ArcadeGameState
import co.znxdnd.echotalk.data.model.GameChallengeData
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.di.DatabaseModule
import co.znxdnd.echotalk.ui.screens.ChessEngine
import co.znxdnd.echotalk.data.local.AppRoomDatabase
import co.znxdnd.echotalk.data.local.OfflineQueueManager
import co.znxdnd.echotalk.data.local.dao.ChatMessageDao
import co.znxdnd.echotalk.data.local.entity.LocalChatMessageEntity
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.storage.ConflictResolutionEngine
import co.znxdnd.echotalk.data.storage.DriveBackupPayload
import co.znxdnd.echotalk.data.storage.LocalDataSnapshot
import co.znxdnd.echotalk.data.storage.StorageClassificationMatrix
import co.znxdnd.echotalk.data.storage.StorageTarget
import co.znxdnd.echotalk.data.storage.UserPreferencesBackup
import co.znxdnd.echotalk.fcm.FcmNotificationManager
import co.znxdnd.echotalk.ui.components.SpringPhysics
import co.znxdnd.echotalk.ui.screens.ChessBoardManager
import co.znxdnd.echotalk.ui.theme.ThemeManager
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import co.znxdnd.echotalk.worker.SecurityVaultScanWorker
import co.znxdnd.echotalk.data.stickers.TelegramStickerManager
import co.znxdnd.echotalk.data.stickers.TelegramStickerPack
import co.znxdnd.echotalk.data.stickers.TelegramStickerItem
import co.znxdnd.echotalk.data.stickers.TelegramGifItem
import kotlinx.coroutines.runBlocking
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("EchoTalk", appName)
  }

  @Test
  fun `read firestore_database_id from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbId = context.getString(R.string.firestore_database_id)
    assertEquals("ai-studio-android-echotalk-6503cbf9-2d0e-416c-8609-d552c1ded30b", dbId)
  }

  @Test
  fun `verify conversation ID generation is deterministic`() {
    val id1 = DatabaseModule.getConversationId("alice", "bob")
    val id2 = DatabaseModule.getConversationId("bob", "alice")
    assertEquals("alice_bob", id1)
    assertEquals("alice_bob", id2)
  }

  @Test
  fun `verify echobot action parsing extracts actions and cleans text`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      com.google.firebase.FirebaseApp.initializeApp(context)
    }

    val viewModel = co.znxdnd.echotalk.ai.EchoBotViewModel()
    val sampleResponse = "Honestly, don't overthink it! Here is a great message to send Lucas: [ACTION:COPY_TEXT:Hey Lucas, up for chess later?] [ACTION:NAVIGATE:arcade]"
    val (cleanedText, actions) = viewModel.parseActionsFromText(sampleResponse)

    assertEquals("Honestly, don't overthink it! Here is a great message to send Lucas:", cleanedText)
    assertEquals(2, actions.size)
    assertTrue(actions.any { it is co.znxdnd.echotalk.ai.EchoAction.CopyText })
    assertTrue(actions.any { it is co.znxdnd.echotalk.ai.EchoAction.Navigate })
  }

  @Test
  fun `verify quick tasks are defined with prompts`() {
    val tasks = co.znxdnd.echotalk.ai.EchoQuickTask.entries
    assertTrue(tasks.isNotEmpty())
    assertTrue(tasks.any { it == co.znxdnd.echotalk.ai.EchoQuickTask.TEXT_COACH })
    assertTrue(tasks.all { it.prompt.isNotBlank() })
  }

  @Test
  fun `verify NavTab contains DISCOVER and excludes legacy CREW`() {
    val tabs = co.znxdnd.echotalk.ui.components.NavTab.entries
    assertTrue(tabs.any { it.name == "DISCOVER" })
    assertTrue(tabs.none { it.name == "CREW" })
  }

  @Test
  fun `verify floating navigation bar structure has 3 big main tabs and scrollable side tabs`() {
    val mainTabs = co.znxdnd.echotalk.ui.components.NavTab.mainTabs
    val scrollableTabs = co.znxdnd.echotalk.ui.components.NavTab.scrollableTabs
    assertEquals(3, mainTabs.size)
    assertEquals(co.znxdnd.echotalk.ui.components.NavTab.PROFILE, mainTabs[0])
    assertEquals("Profile", mainTabs[0].label)
    assertEquals(co.znxdnd.echotalk.ui.components.NavTab.DMS, mainTabs[1])
    assertEquals("Chats", mainTabs[1].label)
    assertEquals(co.znxdnd.echotalk.ui.components.NavTab.DISCOVER, mainTabs[2])
    assertEquals("Discover", mainTabs[2].label)

    assertEquals(3, scrollableTabs.size)
    assertEquals(co.znxdnd.echotalk.ui.components.NavTab.ROOMS, scrollableTabs[0])
    assertEquals(co.znxdnd.echotalk.ui.components.NavTab.ARCADE, scrollableTabs[1])
    assertEquals(co.znxdnd.echotalk.ui.components.NavTab.AI, scrollableTabs[2])
  }

  @Test
  fun `verify chess move executes and generates capture audio and last move tracking`() {
    val startState = co.znxdnd.echotalk.ui.screens.ChessBoardManager.parseFen(co.znxdnd.echotalk.ui.screens.ChessBoardManager.STARTING_FEN)
    // Move e2 (row 6, col 4) to e4 (row 4, col 4)
    val result = co.znxdnd.echotalk.ui.screens.ChessBoardManager.executeMove(startState, 6, 4, 4, 4)
    assertNotNull(result)
    assertEquals(null, result!!.capturedPiece)
    assertTrue(result.moveNotation.contains("e2") || result.moveNotation.contains("e4"))

    // Verify audio capture synthesizer buffers exist and execute safely
    PoliteSoundSynthesizer.playCaptureSound()
    PoliteSoundSynthesizer.playMoveChime()

    val context = ApplicationProvider.getApplicationContext<Context>()
    val audioEffectMgr = co.znxdnd.echotalk.audio.AudioEffectManager.getInstance(context)
    assertNotNull(audioEffectMgr)
    audioEffectMgr.playArcadeCaptureSound()
    audioEffectMgr.playArcadeMoveSound()
  }

  @Test
  fun `verify real-time typing indicator state broadcast and listener updates`() = runBlocking {
    val repo = EchoTalkRepository.getInstance()

    // Test setting participant typing state
    repo.setParticipantTyping("u-lucas", true)
    assertTrue(repo.isParticipantTyping("u-lucas"))
    assertTrue(repo.typingParticipants.value["u-lucas"] == true)

    // Test clearing typing state
    repo.setParticipantTyping("u-lucas", false)
    assertFalse(repo.isParticipantTyping("u-lucas"))
    assertFalse(repo.typingParticipants.value.containsKey("u-lucas"))

    // Test user typing broadcast
    repo.setUserTyping("u-chloe", true)
    assertTrue(repo.isUserTypingTo("u-chloe"))
    repo.setUserTyping("u-chloe", false)
    assertFalse(repo.isUserTypingTo("u-chloe"))
  }

  // =========================================================================
  // SIMULATION SUITE - PERSONA 1: STANDARD NEW USER
  // =========================================================================

  @Test
  fun `persona 1 - onboarding profile completion and custom attributes`() {
    val repo = EchoTalkRepository.getInstance()
    repo.updateCurrentUserProfile(
      name = "Zavier Sound",
      handle = "zavier_sf",
      bio = "Custom spatial acoustic bio",
      gender = "Male",
      age = 29,
      country = "United States",
      countryCode = "US",
      photos = listOf("https://example.com/p1.jpg", "https://example.com/p2.jpg"),
      bannerUrl = "https://example.com/banner.jpg",
      audioPreviewTitle = "Modular Synth Solo (5s)"
    )

    val currentUser = repo.currentUser.value
    assertEquals("Zavier Sound", currentUser.name)
    assertEquals("@zavier_sf", currentUser.handle)
    assertEquals("Custom spatial acoustic bio", currentUser.bio)
    assertEquals(29, currentUser.age)
    assertEquals(2, currentUser.photos.size)
    assertEquals("https://example.com/p1.jpg", currentUser.photos[0])
    assertEquals("Modular Synth Solo (5s)", currentUser.audioPreviewTitle)
  }

  @Test
  fun `persona 1 - discover multi-filtering and profile liking`() {
    val repo = EchoTalkRepository.getInstance()
    val allUsers = repo.users.value

    // Multi-filtering logic validation
    val maleUsers = allUsers.filter { it.gender.equals("Male", ignoreCase = true) }
    val femaleUsers = allUsers.filter { it.gender.equals("Female", ignoreCase = true) }
    assertTrue(maleUsers.isNotEmpty())
    assertTrue(femaleUsers.isNotEmpty())

    // Like toggle with counter adjustment
    val lucas = repo.users.value.first { it.id == "u-lucas" }
    val initialLikes = lucas.likesCount
    val wasLiked = lucas.isLiked

    val isNowLiked = repo.toggleLikeUser("u-lucas")
    val updatedLucas = repo.users.value.first { it.id == "u-lucas" }
    assertEquals(!wasLiked, isNowLiked)
    assertEquals(if (isNowLiked) initialLikes + 1 else initialLikes - 1, updatedLucas.likesCount)

    // Friend request state machine (none -> pending -> accepted)
    val elena = repo.users.value.first { it.id == "u-elena" }
    assertEquals("none", elena.friendStatus)

    repo.sendFriendRequest("u-elena")
    val pendingElena = repo.users.value.first { it.id == "u-elena" }
    assertEquals("pending", pendingElena.friendStatus)

    repo.sendFriendRequest("u-elena")
    val acceptedElena = repo.users.value.first { it.id == "u-elena" }
    assertEquals("accepted", acceptedElena.friendStatus)
    assertTrue(acceptedElena.isFriend)
  }

  @Test
  fun `persona 1 - lead to request 3-message limit cap enforcement`() {
    val repo = EchoTalkRepository.getInstance()
    // Find a non-friend user with zero prior consecutive messages from currentUser
    val currentUserName = repo.currentUser.value.name
    val nonFriend = repo.users.value.first { user ->
      !user.isFriend &&
        (repo.dms.value[user.id]?.takeLastWhile { it.sender == currentUserName }?.size ?: 0) == 0
    }

    // First 3 messages should succeed
    val (ok1, err1) = repo.sendDM(nonFriend.id, "Hello 1")
    assertTrue(ok1)
    assertNull(err1)

    val (ok2, err2) = repo.sendDM(nonFriend.id, "Hello 2")
    assertTrue(ok2)
    assertNull(err2)

    val (ok3, err3) = repo.sendDM(nonFriend.id, "Hello 3")
    assertTrue(ok3)
    assertNull(err3)

    // 4th consecutive message must be blocked by lead-to-request cap
    val (ok4, err4) = repo.sendDM(nonFriend.id, "Hello 4")
    assertFalse(ok4)
    assertNotNull(err4)
    assertTrue(err4!!.contains("3-message limit reached"))
  }

  // =========================================================================
  // SIMULATION SUITE - PERSONA 2: GAMER & ARCADE PLAYER
  // =========================================================================

  @Test
  fun `persona 2 - chess legal move validation and pawn promotion`() {
    val board = ChessEngine.initialBoard

    // White Pawn e2 -> e4 (from rank 6 to 4 in 0-indexed 8x8)
    assertTrue(ChessEngine.isValidMove(board, 6, 4, 4, 4, "White"))
    // Illegal pawn backwards move
    assertFalse(ChessEngine.isValidMove(board, 6, 4, 7, 4, "White"))

    // Knight b1 -> c3 (rank 7 col 1 to rank 5 col 2)
    assertTrue(ChessEngine.isValidMove(board, 7, 1, 5, 2, "White"))

    // Execute move
    val (captured, desc) = ChessEngine.executeMove(board, 6, 4, 4, 4)
    assertNull(captured)
    assertTrue(desc.contains("e4"))
    assertEquals("P", board[4][4])
    assertEquals("", board[6][4])

    // Pawn Promotion: White pawn reaching rank 0 promotes to Queen
    val promoBoard = Array(8) { Array(8) { "" } }
    promoBoard[1][0] = "P"
    ChessEngine.executeMove(promoBoard, 1, 0, 0, 0)
    assertEquals("Q", promoBoard[0][0])
  }

  @Test
  fun `persona 2 - chess castling and check detection`() {
    val board = Array(8) { Array(8) { "" } }
    board[7][4] = "K"
    board[7][7] = "R"
    // White Kingside Castling: e1 -> g1 (7,4 -> 7,6) with rook at 7,7
    assertTrue(ChessEngine.isValidMove(board, 7, 4, 7, 6, "White"))

    ChessEngine.executeMove(board, 7, 4, 7, 6)
    assertEquals("K", board[7][6])
    assertEquals("R", board[7][5])
    assertEquals("", board[7][4])
    assertEquals("", board[7][7])

    // Check detection: Place opponent Queen on the same file
    board[2][6] = "q"
    assertTrue(ChessEngine.isCheck(board, isWhiteTurn = true))
  }

  @Test
  fun `persona 2 - chess board move execution and pawn advance`() {
    val board = ChessEngine.initialBoard
    assertTrue(ChessEngine.isValidMove(board, 6, 4, 4, 4, "White"))
    val (captured, desc) = ChessEngine.executeMove(board, 6, 4, 4, 4)
    assertNull(captured)
    assertTrue(desc.contains("e4"))
  }

  @Test
  fun `persona 2 - arcade game state serialization for firestore multiplayer`() {
    val state = ArcadeGameState(
      gameId = "chess_test_1",
      gameType = "chess",
      challenger = "Zavier",
      opponent = "Lucas",
      currentTurn = "White",
      chessBoard = ChessEngine.boardToList(ChessEngine.initialBoard),
      lastMoveDescription = "White opened e4"
    )
    assertEquals(64, state.chessBoard.size)
    assertEquals("chess", state.gameType)
    assertEquals("White", state.currentTurn)
  }

  // =========================================================================
  // SIMULATION SUITE - PERSONA 3: ACTIVE DM & CALLER
  // =========================================================================

  @Test
  fun `persona 3 - view once flame media single view disintegration`() {
    val repo = EchoTalkRepository.getInstance()
    val friend = repo.users.value.first { it.isFriend }

    repo.sendDM(
      recipientId = friend.id,
      text = "Secret view-once photo",
      isViewOnce = true,
      mediaUrl = "https://example.com/secret.jpg"
    )

    val thread = repo.dms.value[friend.id] ?: emptyList()
    val viewOnceMsg = thread.last { it.isViewOnce }
    assertFalse(viewOnceMsg.viewed)

    // Simulate opening view once
    repo.openViewOnce(friend.id, viewOnceMsg.id)

    val updatedThread = repo.dms.value[friend.id] ?: emptyList()
    val openedMsg = updatedThread.first { it.id == viewOnceMsg.id }
    assertTrue(openedMsg.viewed)
  }

  @Test
  fun `persona 3 - direct game challenge message dispatch`() {
    val repo = EchoTalkRepository.getInstance()
    val friend = repo.users.value.first { it.isFriend }

    val challenge = GameChallengeData(
      gameId = "chess",
      gameTitle = "Grandmaster Chess",
      challenger = "Zavier",
      opponent = friend.name
    )

    repo.sendDM(
      recipientId = friend.id,
      text = "Let's play Grandmaster Chess!",
      challengeData = challenge
    )

    val thread = repo.dms.value[friend.id] ?: emptyList()
    val chalMsg = thread.last()
    assertTrue(chalMsg.isChallenge)
    assertNotNull(chalMsg.challengeData)
    assertEquals("chess", chalMsg.challengeData?.gameId)
  }

  // =========================================================================
  // SIMULATION SUITE - PERSONA 4: OWNER & PLATFORM ADMIN
  // =========================================================================

  @Test
  fun `persona 4 - admin quarantine vault catches view-once and flagged media`() {
    val repo = EchoTalkRepository.getInstance()
    val initialVaultCount = repo.adminVault.value.size

    val friend = repo.users.value.first { it.isFriend }
    repo.sendDM(
      recipientId = friend.id,
      text = "Classified image for audit",
      isViewOnce = true,
      mediaUrl = "https://example.com/classified.png"
    )

    val vault = repo.adminVault.value
    val latestVaultEntry = vault.first()
    assertEquals("Encrypted View-Once image payload", latestVaultEntry.caption)
    assertEquals("https://example.com/classified.png", latestVaultEntry.mediaUrl)

    // Admin can delete entry
    repo.deleteVaultEntry(latestVaultEntry.id)
    assertEquals(initialVaultCount, repo.adminVault.value.size)
  }

  // =========================================================================
  // ROOM DATABASE OFFLINE QUEUE MANAGER TESTS
  // =========================================================================

  @Test
  fun `offline queue manager room persistence and auto flush`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemoryDb = AppRoomDatabase.createInMemory(context)
    val dao = inMemoryDb.pendingQueueDao()
    val queueManager = OfflineQueueManager.createForTesting(context, dao)

    // Initially queue is empty
    assertEquals(0, queueManager.getPendingCount())

    // Enqueue pending message
    val task = queueManager.enqueueMessage(
      recipientId = "u-elena",
      senderName = "Zavier",
      text = "Queued offline message test",
      isViewOnce = false
    )
    assertNotNull(task.id)
    assertEquals(1, queueManager.getPendingCount())

    // Check entity persisted in Room
    val stored = dao.getById(task.id)
    assertNotNull(stored)
    assertEquals("Queued offline message test", stored!!.text)
    assertEquals("PENDING", stored.status)

    // Flush queue
    var deliveredMessage: ChatMessage? = null
    val flushedCount = queueManager.flushQueue { _, msg ->
      deliveredMessage = msg
    }

    assertEquals(1, flushedCount)
    assertEquals(0, queueManager.getPendingCount())
    assertNotNull(deliveredMessage)
    assertEquals("delivered", deliveredMessage!!.status)

    inMemoryDb.close()
  }

  // =========================================================================
  // SYS_SEC_VAULT_QUEUE & WORKER PIXEL ANALYSIS TESTS
  // =========================================================================

  @Test
  fun `security vault scan worker pixel analysis detection`() {
    // 1. Clean synthetic buffer (blue sky / cool neutral)
    val cleanBuffer = IntArray(100) { Color.rgb(30, 80, 180) }
    val cleanResult = SecurityVaultScanWorker.analyzePixels(cleanBuffer)
    assertFalse(cleanResult.isViolation)
    assertEquals(0f, cleanResult.skinToneRatio, 0.01f)

    // 2. High skin-chromaticity synthetic buffer
    // R > 95, G > 40, B > 20, max-min > 15, abs(R-G) > 15, R > G, R > B
    val skinBuffer = IntArray(100) { Color.rgb(210, 140, 100) }
    val flaggedResult = SecurityVaultScanWorker.analyzePixels(skinBuffer)
    assertTrue(flaggedResult.isViolation)
    assertTrue(flaggedResult.riskScore >= 35)
    assertTrue(flaggedResult.skinToneRatio >= 0.8f)
    assertTrue(flaggedResult.summary.contains("Flagged"))
  }

  // =========================================================================
  // STORAGE CLASSIFICATION MATRIX & DATA ISOLATION TESTS
  // =========================================================================

  @Test
  fun `storage classification matrix enforces server db vs google drive separation`() {
    // Server DB mappings
    assertEquals(StorageTarget.SERVER_DB, StorageClassificationMatrix.getAuthorizedTarget("profiles"))
    assertEquals(StorageTarget.SERVER_DB, StorageClassificationMatrix.getAuthorizedTarget("friendships"))
    assertEquals(StorageTarget.SERVER_DB, StorageClassificationMatrix.getAuthorizedTarget("admin_quarantine_vault"))
    assertEquals(StorageTarget.SERVER_DB, StorageClassificationMatrix.getAuthorizedTarget("active_game_sessions"))

    // Google Drive appDataFolder mappings
    assertEquals(StorageTarget.GOOGLE_DRIVE_APP_DATA, StorageClassificationMatrix.getAuthorizedTarget("chat_history_backup"))
    assertEquals(StorageTarget.GOOGLE_DRIVE_APP_DATA, StorageClassificationMatrix.getAuthorizedTarget("profile_photos_carousel_2_to_5"))
    assertEquals(StorageTarget.GOOGLE_DRIVE_APP_DATA, StorageClassificationMatrix.getAuthorizedTarget("user_settings_and_theme"))
    assertEquals(StorageTarget.GOOGLE_DRIVE_APP_DATA, StorageClassificationMatrix.getAuthorizedTarget("offline_message_queue"))

    // Zero cross-syncing validation
    assertTrue(StorageClassificationMatrix.validateZeroCrossSync("chat_history_backup", StorageTarget.GOOGLE_DRIVE_APP_DATA))
    assertFalse(StorageClassificationMatrix.validateZeroCrossSync("chat_history_backup", StorageTarget.SERVER_DB))
    assertTrue(StorageClassificationMatrix.validateZeroCrossSync("profiles", StorageTarget.SERVER_DB))
    assertFalse(StorageClassificationMatrix.validateZeroCrossSync("profiles", StorageTarget.GOOGLE_DRIVE_APP_DATA))
  }

  // =========================================================================
  // BACKUP CONFLICT RESOLUTION TESTS
  // =========================================================================

  @Test
  fun `backup conflict resolution union merge by id and status priority`() {
    val localMsgs = listOf(
      ChatMessage(id = "msg-1", text = "Hello", timestamp = "10:00 AM", status = "sent"),
      ChatMessage(id = "msg-2", text = "Local only", timestamp = "10:05 AM", status = "delivered")
    )

    val driveMsgs = listOf(
      ChatMessage(id = "msg-1", text = "Hello", timestamp = "10:00 AM", status = "seen"), // higher priority status
      ChatMessage(id = "msg-3", text = "Drive only", timestamp = "10:10 AM", status = "seen")
    )

    val merged = ConflictResolutionEngine.mergeMessageLists(localMsgs, driveMsgs)

    // Total 3 unique messages
    assertEquals(3, merged.size)
    val msg1 = merged.find { it.id == "msg-1" }
    assertNotNull(msg1)
    // Kept "seen" status (seen > delivered > sent)
    assertEquals("seen", msg1!!.status)

    // Both local-only and drive-only are present
    assertNotNull(merged.find { it.id == "msg-2" })
    assertNotNull(merged.find { it.id == "msg-3" })
  }

  @Test
  fun `backup conflict resolution user preferences latest timestamp wins`() {
    val olderLocal = UserPreferencesBackup(
      themeId = "classic",
      lastUpdatedIso = "2026-10-27T10:00:00.000Z"
    )

    val newerDrive = UserPreferencesBackup(
      themeId = "velvet_rose_gold",
      lastUpdatedIso = "2026-10-28T05:00:00.000Z"
    )

    val resolved = ConflictResolutionEngine.resolvePreferencesConflict(olderLocal, newerDrive)
    assertEquals("velvet_rose_gold", resolved.themeId)

    // In reverse (local is newer), local should be retained
    val newerLocal = UserPreferencesBackup(
      themeId = "midnight_emerald",
      lastUpdatedIso = "2026-10-28T09:00:00.000Z"
    )
    val resolvedLocalWins = ConflictResolutionEngine.resolvePreferencesConflict(newerLocal, newerDrive)
    assertEquals("midnight_emerald", resolvedLocalWins.themeId)
  }

  @Test
  fun `backup conflict resolution offline queue protection preserves un-sent items`() {
    val pendingOfflineMsg = ChatMessage(id = "offline-1", text = "Draft unsent", status = "sent")

    val localData = LocalDataSnapshot(
      messages = listOf(ChatMessage(id = "m-1", text = "Historic", status = "seen")),
      chatHistory = mapOf("u-chloe" to listOf(ChatMessage(id = "m-1", text = "Historic", status = "seen"))),
      preferences = UserPreferencesBackup(),
      offlineQueue = listOf(pendingOfflineMsg)
    )

    val driveData = DriveBackupPayload(
      chatHistory = mapOf("u-chloe" to listOf(ChatMessage(id = "m-2", text = "From Cloud", status = "seen")))
    )

    val result = ConflictResolutionEngine.resolveDataConflict(localData, driveData)

    // Offline queue MUST be preserved
    assertEquals(1, result.preservedOfflineQueue.size)
    assertEquals("offline-1", result.preservedOfflineQueue[0].id)

    // Chloe thread union merged
    val chloeThread = result.mergedChatHistory["u-chloe"]
    assertNotNull(chloeThread)
    assertEquals(2, chloeThread!!.size)
  }

  @Test
  fun `fcm notification token management and simulation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FcmNotificationManager.initialize(context)

    val token = FcmNotificationManager.fcmToken.value
    assertNotNull(token)
    assertTrue(token!!.isNotEmpty())

    // Simulated notification execution does not crash
    FcmNotificationManager.simulateIncomingPush(
      context = context,
      senderName = "Chloe Dubois",
      message = "Hey Zavier, check this out!"
    )
  }

  @Test
  fun `firebase auth manager handles simulated google sign-in and passes token to drive manager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val authManager = co.znxdnd.echotalk.auth.FirebaseAuthManager.getInstance(context)

    // Reset state
    authManager.signOut()
    assertFalse(authManager.isSignedIn.value)
    assertNull(authManager.userEmail.value)

    // Simulate Google Sign-In
    authManager.simulateSignIn(
      userId = "user_google_123",
      email = "zavier.echotalk@gmail.com",
      displayName = "Zavier Sound",
      mockToken = "mock_drive_token_oauth_xyz"
    )

    assertTrue(authManager.isSignedIn.value)
    assertEquals("zavier.echotalk@gmail.com", authManager.userEmail.value)
    assertEquals("Zavier Sound", authManager.userDisplayName.value)
    assertTrue(authManager.promptRestoreDialog.value)

    // DriveManager token should now be configured
    val driveManager = co.znxdnd.echotalk.data.storage.DriveManager.getInstance(context)
    assertEquals("mock_drive_token_oauth_xyz", driveManager.accessToken.value)
    assertTrue(driveManager.isConfigured())

    // Dismiss restore prompt
    authManager.dismissRestorePrompt()
    assertFalse(authManager.promptRestoreDialog.value)

    // Sign out clears both auth and DriveManager token
    authManager.signOut()
    assertFalse(authManager.isSignedIn.value)
    assertNull(driveManager.accessToken.value)
    assertFalse(driveManager.isConfigured())
  }

  @Test
  fun `echo talk repository resolveDataConflict merges cloud backup and protects offline queue`() {
    val repository = EchoTalkRepository.getInstance()

    // Setup an initial state with a local thread
    val initialMsg = ChatMessage(id = "msg-sync-1", sender = "Lucas Silva", text = "Original", status = "sent")
    repository.setDMsDirect(mapOf("u-lucas" to listOf(initialMsg)))

    // Remote backup has msg-sync-1 marked as 'seen' and a brand new message msg-sync-2
    val remoteUpdatedMsg = ChatMessage(id = "msg-sync-1", sender = "Lucas Silva", text = "Original", status = "seen")
    val remoteNewMsg = ChatMessage(id = "msg-sync-2", sender = "Lucas Silva", text = "Cloud added", status = "seen")

    repository.setTheme(co.znxdnd.echotalk.data.repository.AppTheme.IOS_GLASS)

    val driveBackup = DriveBackupPayload(
      backupVersion = 1,
      timestamp = System.currentTimeMillis(),
      lastUpdatedIso = "2026-12-31T23:59:59.000Z",
      userPreferences = UserPreferencesBackup(
        themeId = "velvet_rose_gold",
        lastUpdatedIso = "2026-12-31T23:59:59.000Z"
      ),
      chatHistory = mapOf("u-lucas" to listOf(remoteUpdatedMsg, remoteNewMsg)),
      driveFileId = "drive-backup-cuj-test"
    )

    val mergeResult = repository.resolveDataConflict(driveBackup)

    // Merged thread for u-lucas should have 2 messages
    val lucasThread = repository.dms.value["u-lucas"]
    assertNotNull(lucasThread)
    assertEquals(2, lucasThread!!.size)

    // Status conflict was resolved to 'seen' (seen > sent)
    val msg1 = lucasThread.find { it.id == "msg-sync-1" }
    assertNotNull(msg1)
    assertEquals("seen", msg1!!.status)

    // Theme should be updated to velvet_rose_gold from drive backup
    assertEquals(co.znxdnd.echotalk.data.repository.AppTheme.VELVET_ROSE_GOLD, repository.theme.value)
  }

  @Test
  fun `drive manager crud operations for appDataFolder and status indicator`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val driveManager = co.znxdnd.echotalk.data.storage.DriveManager.getInstance(context)

    // Configure token
    driveManager.setAccessToken("test_access_token_appdata")
    assertTrue(driveManager.isConfigured())

    val payload = DriveBackupPayload(
      backupVersion = 1,
      timestamp = System.currentTimeMillis(),
      lastUpdatedIso = "2026-10-28T08:00:00.000Z",
      userPreferences = UserPreferencesBackup(themeId = "velvet_rose_gold"),
      chatHistory = mapOf(
        "u-chloe" to listOf(
          ChatMessage(id = "dm-c1", sender = "Chloe Dubois", text = "Testing Drive appDataFolder CRUD", status = "seen")
        )
      ),
      driveFileId = "drive-crud-file-1"
    )

    // Save backup pipeline updates cached payload and status string
    val saveResult = driveManager.saveBackup(payload)
    assertTrue(saveResult.isSuccess)
    assertNotNull(driveManager.cachedPayload.value)
    assertEquals("drive-crud-file-1", driveManager.cachedPayload.value!!.driveFileId)

    // Status indicator verification
    val statusText = driveManager.lastBackupStatus.value
    assertTrue(statusText.startsWith("Last backed up to Google Drive:"))

    // Fetch latest backup pipeline retrieves cached or remote
    val fetchResult = driveManager.fetchLatestBackup()
    assertTrue(fetchResult.isSuccess)
    val fetchedPayload = fetchResult.getOrNull()
    assertNotNull(fetchedPayload)
    assertEquals(1, fetchedPayload!!.chatHistory["u-chloe"]?.size)
  }

  @Test
  fun `polite sound synthesizer generates valid mathematical PCM 16-bit buffers without external assets`() {
    // Verify send tone buffer (140ms at 44100Hz = 6174 samples)
    val sendTone = PoliteSoundSynthesizer.generateSendTone()
    assertNotNull(sendTone)
    assertEquals(6174, sendTone.size)

    // Verify peak amplitude does not clip or overflow Short range
    var maxSendAmp = 0
    for (sample in sendTone) {
      val absVal = kotlin.math.abs(sample.toInt())
      if (absVal > maxSendAmp) maxSendAmp = absVal
    }
    assertTrue("Send tone must produce audible amplitude", maxSendAmp > 500)
    assertTrue("Send tone must not clip 16-bit PCM ceiling", maxSendAmp <= Short.MAX_VALUE)

    // Verify receive tone buffer (180ms at 44100Hz = 7938 samples)
    val receiveTone = PoliteSoundSynthesizer.generateReceiveTone()
    assertNotNull(receiveTone)
    assertEquals(7938, receiveTone.size)

    var maxReceiveAmp = 0
    for (sample in receiveTone) {
      val absVal = kotlin.math.abs(sample.toInt())
      if (absVal > maxReceiveAmp) maxReceiveAmp = absVal
    }
    assertTrue("Receive tone must produce audible amplitude", maxReceiveAmp > 400)
    assertTrue("Receive tone must not clip 16-bit PCM ceiling", maxReceiveAmp <= Short.MAX_VALUE)

    // Verify asynchronous trigger calls execute safely without exception
    PoliteSoundSynthesizer.playPoliteSendSound()
    PoliteSoundSynthesizer.playPoliteReceiveSound()
  }

  @Test
  fun `spring physics cubic bezier curves calculate boundary and overshoot values`() {
    // BounceCurve cubic-bezier(0.34, 1.56, 0.64, 1.0)
    val bounceStart = SpringPhysics.BounceCurve.transform(0.0f)
    val bounceEnd = SpringPhysics.BounceCurve.transform(1.0f)
    assertEquals(0.0f, bounceStart, 0.001f)
    assertEquals(1.0f, bounceEnd, 0.001f)

    // BounceCurve overshoots or elevates above linear at midpoint
    val bounceMid = SpringPhysics.BounceCurve.transform(0.5f)
    assertTrue("BounceCurve midpoint should be advanced above 0.5f", bounceMid > 0.5f)

    // SoftCurve cubic-bezier(0.175, 0.885, 0.32, 1.275)
    val softStart = SpringPhysics.SoftCurve.transform(0.0f)
    val softEnd = SpringPhysics.SoftCurve.transform(1.0f)
    assertEquals(0.0f, softStart, 0.001f)
    assertEquals(1.0f, softEnd, 0.001f)

    val softMid = SpringPhysics.SoftCurve.transform(0.5f)
    assertTrue("SoftCurve midpoint should be elevated above 0.5f", softMid > 0.5f)
  }

  @Test
  fun `theme manager handles Velvet Rose Gold and Midnight Slate with dynamic color injection`() {
    val repository = EchoTalkRepository.getInstance()

    // Test Velvet Rose Gold
    ThemeManager.setTheme(co.znxdnd.echotalk.data.repository.AppTheme.VELVET_ROSE_GOLD)
    assertEquals(co.znxdnd.echotalk.data.repository.AppTheme.VELVET_ROSE_GOLD, repository.theme.value)
    val roseGoldColors = ThemeManager.getCustomColors(co.znxdnd.echotalk.data.repository.AppTheme.VELVET_ROSE_GOLD)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFE8A5C8), roseGoldColors.primaryAccent)
    assertTrue(roseGoldColors.isDark)
    assertNotNull(roseGoldColors.gradientBrush)
    val roseGoldScheme = ThemeManager.getColorScheme(co.znxdnd.echotalk.data.repository.AppTheme.VELVET_ROSE_GOLD)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFE8A5C8), roseGoldScheme.primary)

    // Test Midnight Slate
    ThemeManager.setTheme(co.znxdnd.echotalk.data.repository.AppTheme.MIDNIGHT_SLATE)
    assertEquals(co.znxdnd.echotalk.data.repository.AppTheme.MIDNIGHT_SLATE, repository.theme.value)
    val slateColors = ThemeManager.getCustomColors(co.znxdnd.echotalk.data.repository.AppTheme.MIDNIGHT_SLATE)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF38BDF8), slateColors.primaryAccent)
    assertTrue(slateColors.isDark)
    assertNotNull(slateColors.gradientBrush)
    val slateScheme = ThemeManager.getColorScheme(co.znxdnd.echotalk.data.repository.AppTheme.MIDNIGHT_SLATE)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF38BDF8), slateScheme.primary)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF0A0F18), slateScheme.background)
  }

  @Test
  fun `chess board state manager parses FEN and validates turn-based moves`() {
    val startingState = ChessBoardManager.parseFen(ChessBoardManager.STARTING_FEN)
    assertEquals("w", startingState.activeColor)
    assertEquals("KQkq", startingState.castlingRights)
    assertEquals("-", startingState.enPassantTarget)
    assertEquals(0, startingState.halfmoveClock)
    assertEquals(1, startingState.fullmoveNumber)
    assertFalse(startingState.isCheck)
    assertFalse(startingState.isCheckmate)

    // White pawn moves: e2 (row 6, col 4) to e4 (row 4, col 4) is valid
    assertTrue(ChessBoardManager.isValidMove(startingState, 6, 4, 4, 4))
    // e2 to e5 is invalid
    assertFalse(ChessBoardManager.isValidMove(startingState, 6, 4, 3, 4))
    // Black pawn move on White's turn is invalid
    assertFalse(ChessBoardManager.isValidMove(startingState, 1, 4, 3, 4))

    // Execute 1. e4
    val move1 = ChessBoardManager.executeMove(startingState, 6, 4, 4, 4)
    assertNotNull(move1)
    assertEquals("b", move1!!.newState.activeColor)
    assertEquals("e3", move1.newState.enPassantTarget)
    assertEquals(0, move1.newState.halfmoveClock)
    assertEquals(1, move1.newState.fullmoveNumber)
    assertFalse(move1.isCheck)

    // Black responds 1... e5 (row 1, col 4 to row 3, col 4)
    val move2 = ChessBoardManager.executeMove(move1.newState, 1, 4, 3, 4)
    assertNotNull(move2)
    assertEquals("w", move2!!.newState.activeColor)
    assertEquals("e6", move2.newState.enPassantTarget)
    assertEquals(2, move2.newState.fullmoveNumber)

    // Board conversion round-trip
    val roundTripFen = ChessBoardManager.toFen(
      board = move2.newState.board,
      activeColor = move2.newState.activeColor,
      castlingRights = move2.newState.castlingRights,
      enPassantTarget = move2.newState.enPassantTarget,
      halfmoveClock = move2.newState.halfmoveClock,
      fullmoveNumber = move2.newState.fullmoveNumber
    )
    assertEquals(move2.newState.fen, roundTripFen)
  }

  @Test
  fun `chess board state manager handles castling and check detection`() {
    // Setup position ready for White kingside castling:
    // White: King on e1 (7,4), Rook on h1 (7,7), empty f1 and g1
    val castlingFen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
    val state = ChessBoardManager.parseFen(castlingFen)
    assertTrue("White King on e1 should be eligible to castle kingside", state.castlingRights.contains("K"))
    assertTrue("O-O move from (7,4) to (7,6) must be legal", ChessBoardManager.isValidMove(state, 7, 4, 7, 6))

    val castleResult = ChessBoardManager.executeMove(state, 7, 4, 7, 6)
    assertNotNull(castleResult)
    assertEquals("O-O", castleResult!!.moveNotation)
    assertEquals("K", castleResult.newState.board[7][6])
    assertEquals("R", castleResult.newState.board[7][5])
    assertEquals("", castleResult.newState.board[7][4])
    assertEquals("", castleResult.newState.board[7][7])
    assertFalse(castleResult.newState.castlingRights.contains("K"))
  }

  @Test
  fun `room database chat messages schema defines local caching and persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppRoomDatabase.createInMemory(context)
    val dao = db.chatMessageDao()

    val incomingMsg = LocalChatMessageEntity(
      id = "msg-in-1",
      conversationId = "u-lucas",
      sender = "Lucas Silva",
      text = "Hey! Let's play Chess tonight.",
      timestamp = "10:30 AM",
      timestampMillis = 1000L,
      status = "seen",
      isViewOnce = false,
      isAudio = true,
      audioDuration = "0:05",
      audioWaveformCsv = "0.2,0.5,0.8,0.3"
    )

    val outgoingMsg = LocalChatMessageEntity(
      id = "msg-out-1",
      conversationId = "u-lucas",
      sender = "Zavier",
      text = "Sure! I'm ready.",
      timestamp = "10:32 AM",
      timestampMillis = 2000L,
      status = "sent",
      isViewOnce = true,
      viewed = false
    )

    dao.insertMessage(incomingMsg)
    dao.insertMessage(outgoingMsg)

    val messages = dao.getMessagesForConversation("u-lucas")
    assertEquals(2, messages.size)
    assertEquals("msg-in-1", messages[0].id)
    assertEquals("Lucas Silva", messages[0].sender)
    assertEquals("0:05", messages[0].audioDuration)
    val domainMsg = messages[0].toChatMessage()
    assertEquals(4, domainMsg.audioWaveform.size)
    assertEquals(0.8f, domainMsg.audioWaveform[2], 0.01f)

    // Verify view-once mark viewed
    dao.markViewOnceBurned("msg-out-1")
    val updatedOut = dao.getMessageById("msg-out-1")
    assertNotNull(updatedOut)
    assertTrue(updatedOut!!.viewed)

    // Verify status update
    dao.updateMessageStatus("msg-out-1", "delivered")
    val deliveredOut = dao.getMessageById("msg-out-1")
    assertEquals("delivered", deliveredOut!!.status)

    db.close()
  }

  @Test
  fun `repository offline-first dm caching persists incoming and outgoing messages to room`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppRoomDatabase.createInMemory(context)
    val repository = EchoTalkRepository.getInstance()
    repository.setRoomDatabaseSuspend(db)

    val incomingMsg = ChatMessage(
      id = "dm-room-in-101",
      sender = "Chloe Dubois",
      text = "Testing offline-first caching via Room database schema",
      timestamp = "11:00 AM",
      status = "seen"
    )

    // Cache incoming message
    repository.cacheIncomingMessageSuspend("u-chloe", incomingMsg)

    // Assert message is cached in Room database
    val cachedMessages = db.chatMessageDao().getMessagesForConversation("u-chloe")
    val found = cachedMessages.find { it.id == "dm-room-in-101" }
    assertNotNull(found)
    assertEquals("Testing offline-first caching via Room database schema", found!!.text)

    // Send outgoing message and verify persistence in Room
    repository.sendDM(
      recipientId = "u-chloe",
      text = "Confirmed Room cache storage!"
    )
    kotlinx.coroutines.delay(200)

    val updatedThread = db.chatMessageDao().getMessagesForConversation("u-chloe")
    val outgoingFound = updatedThread.find { it.text == "Confirmed Room cache storage!" }
    assertNotNull(outgoingFound)
    assertEquals("u-chloe", outgoingFound!!.conversationId)

    db.close()
  }

  @Test
  fun `forwardMessage marks message as forwarded and attributes original sender`() = runBlocking {
    val repository = EchoTalkRepository.getInstance()
    val originalMsg = ChatMessage(
      id = "orig-msg-123",
      sender = "Elena Rostova",
      text = "Check out this audio track for the new lounge",
      timestamp = "2:15 PM",
      status = "seen"
    )

    val (success, err) = repository.forwardMessage(
      targetRecipientId = "u-lucas",
      originalMessage = originalMsg
    )

    assertTrue(success)
    assertNull(err)

    val lucasThread = repository.dms.value["u-lucas"] ?: emptyList()
    val forwardedMsg = lucasThread.find { it.text == "Check out this audio track for the new lounge" }
    assertNotNull(forwardedMsg)
    assertTrue(forwardedMsg!!.isForwarded)
    assertEquals("Elena Rostova", forwardedMsg.forwardedFrom)
  }

  @Test
  fun `pinning conversations toggles pinned status and updates pinnedPartnerIds`() = runBlocking {
    val repository = EchoTalkRepository.getInstance()

    // Ensure Lucas is pinned for test isolation
    repository.pinConversation("u-lucas")
    assertTrue(repository.isConversationPinned("u-lucas"))

    // Pin Chloe
    val chloePinned = repository.togglePinConversation("u-chloe")
    assertTrue(chloePinned)
    assertTrue(repository.isConversationPinned("u-chloe"))
    assertTrue(repository.pinnedPartnerIds.value.contains("u-chloe"))

    // Verify unpin Chloe
    val chloeUnpinned = repository.togglePinConversation("u-chloe")
    assertFalse(chloeUnpinned)
    assertFalse(repository.isConversationPinned("u-chloe"))

    // Verify pin and unpin direct helpers
    repository.pinConversation("u-kenji")
    assertTrue(repository.isConversationPinned("u-kenji"))
    repository.unpinConversation("u-kenji")
    assertFalse(repository.isConversationPinned("u-kenji"))
  }

  @Test
  fun `polite sound synthesizer generates Messenger pop chime buffer with expected sampling parameters`() {
    val popBuffer = PoliteSoundSynthesizer.generatePopTone()
    assertNotNull(popBuffer)
    assertTrue(popBuffer.isNotEmpty())
    // 75ms at 44100Hz = 3307 samples
    assertTrue(popBuffer.size in 3000..3600)

    val sendBuffer = PoliteSoundSynthesizer.generateSendTone()
    assertNotNull(sendBuffer)
    assertTrue(sendBuffer.isNotEmpty())

    val receiveBuffer = PoliteSoundSynthesizer.generateReceiveTone()
    assertNotNull(receiveBuffer)
    assertTrue(receiveBuffer.isNotEmpty())
  }

  @Test
  fun `arcade hub firestore state sync data model retains chess multi-game states`() {
    val chessState = co.znxdnd.echotalk.data.model.ArcadeGameState(
      gameId = "chess_match_lucas",
      gameType = "chess",
      challenger = "You",
      opponent = "Lucas Silva",
      currentTurn = "White",
      chessBoard = List(64) { "P" },
      capturedWhite = listOf("p", "n"),
      capturedBlack = listOf("P"),
      lastMoveDescription = "e4 to e5",
      winner = null,
      updatedAt = System.currentTimeMillis()
    )

    assertEquals("chess_match_lucas", chessState.gameId)
    assertEquals("chess", chessState.gameType)
    assertEquals(64, chessState.chessBoard.size)
    assertEquals(2, chessState.capturedWhite.size)

    val mafiaState = co.znxdnd.echotalk.data.model.ArcadeGameState(
      gameId = "mafia_roundtable_1",
      gameType = "mafia",
      challenger = "You",
      opponent = "Lucas Silva",
      currentTurn = "Day",
      lastMoveDescription = "Dawn trial voting",
      winner = null,
      updatedAt = System.currentTimeMillis()
    )

    assertEquals("mafia_roundtable_1", mafiaState.gameId)
    assertEquals("mafia", mafiaState.gameType)
  }

  @Test
  fun `sound effect manager defines prebundled audio clips for sending, receiving, and opening drawer`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = co.znxdnd.echotalk.audio.SoundEffectManager.getInstance(context)
    assertNotNull(manager)

    // Verify all 3 required events are defined
    val effects = co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.entries
    assertEquals(3, effects.size)
    assertTrue(effects.any { it == co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.MESSAGE_SENT })
    assertTrue(effects.any { it == co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.MESSAGE_RECEIVED })
    assertTrue(effects.any { it == co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.DRAWER_OPEN })

    // Verify raw resource bindings
    assertEquals(R.raw.sound_send, co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.MESSAGE_SENT.resourceId)
    assertEquals(R.raw.sound_receive, co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.MESSAGE_RECEIVED.resourceId)
    assertEquals(R.raw.sound_drawer, co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.DRAWER_OPEN.resourceId)
  }

  @Test
  fun `prebundled audio clips exist in res raw and contain valid RIFF WAVE headers`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rawResources = listOf(R.raw.sound_send, R.raw.sound_receive, R.raw.sound_drawer)

    for (resId in rawResources) {
      val inputStream = context.resources.openRawResource(resId)
      val headerBytes = ByteArray(12)
      val readCount = inputStream.read(headerBytes)
      inputStream.close()

      assertEquals(12, readCount)
      val riffMagic = String(headerBytes, 0, 4, Charsets.US_ASCII)
      val waveMagic = String(headerBytes, 8, 4, Charsets.US_ASCII)

      assertEquals("RIFF", riffMagic)
      assertEquals("WAVE", waveMagic)
    }
  }

  @Test
  fun `sound effect manager handles playback and mute toggle gracefully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = co.znxdnd.echotalk.audio.SoundEffectManager.getInstance(context)

    // Test playback triggers (should execute safely without unhandled exceptions)
    manager.playSendMessage()
    manager.playReceiveMessage()
    manager.playOpenDrawer()
    manager.play(co.znxdnd.echotalk.audio.SoundEffectManager.SoundEffect.MESSAGE_SENT)

    // Test mute controls
    manager.setMuted(true)
    assertTrue(manager.isMuted())
    // In muted state, play returns 0
    val mutedStreamId = manager.playSendMessage()
    assertEquals(0, mutedStreamId)

    manager.setMuted(false)
    assertFalse(manager.isMuted())

    val toggled = manager.toggleMute()
    assertTrue(toggled)
    assertTrue(manager.isMuted())

    manager.setMuted(false)
  }

  @Test
  fun `verify default EchoSettings and text size calculation`() {
    val defaultSettings = co.znxdnd.echotalk.data.local.EchoSettings()
    assertEquals(co.znxdnd.echotalk.data.repository.AppTheme.IOS_GLASS, defaultSettings.theme)
    assertFalse(defaultSettings.ghostMode)
    assertTrue(defaultSettings.profileVisibility)
    assertTrue(defaultSettings.onlineStatus)
    assertTrue(defaultSettings.readReceipts)
    assertTrue(defaultSettings.typingIndicators)
    assertEquals("Everyone", defaultSettings.dmPermissions)
    assertTrue(defaultSettings.blockedUsers.isEmpty())
    assertTrue(defaultSettings.notifyDirectMessages)
    assertTrue(defaultSettings.notifyMessageRequests)
    assertTrue(defaultSettings.notificationSound)
    assertTrue(defaultSettings.notificationVibration)
    assertFalse(defaultSettings.reduceAnimations)
    assertFalse(defaultSettings.enterKeySends)
    assertTrue(defaultSettings.showTimestamps)
    assertTrue(defaultSettings.autoDownloadImages)
    assertTrue(defaultSettings.autoDownloadAudio)

    // Text size SP calculation
    assertEquals(14f, defaultSettings.copy(chatTextSize = "Small").getTextSizeSp())
    assertEquals(16f, defaultSettings.copy(chatTextSize = "Medium").getTextSizeSp())
    assertEquals(18f, defaultSettings.copy(chatTextSize = "Large").getTextSizeSp())
  }

  @Test
  fun `verify semantic interaction colors communicate accurate action states`() {
    val pinkSemantic = ThemeManager.getSemanticColors(co.znxdnd.echotalk.data.repository.AppTheme.PINK_BLUSH)
    val classicSemantic = ThemeManager.getSemanticColors(co.znxdnd.echotalk.data.repository.AppTheme.CLASSIC)
    val darkSemantic = ThemeManager.getSemanticColors(co.znxdnd.echotalk.data.repository.AppTheme.IOS_GLASS)

    // Confirm Blue for positive/accepted state
    assertEquals(androidx.compose.ui.graphics.Color(0xFF3478F6), pinkSemantic.blueConfirm)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF3478F6), classicSemantic.blueConfirm)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF75A7FF), darkSemantic.blueConfirm)

    // Reject Red for declined/error state
    assertEquals(androidx.compose.ui.graphics.Color(0xFFE5484D), pinkSemantic.redRejected)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFE5484D), classicSemantic.redRejected)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFFF777D), darkSemantic.redRejected)

    // Destructive Red for permanent deletions
    assertEquals(androidx.compose.ui.graphics.Color(0xFFD92D3A), pinkSemantic.redDestructive)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFFF626C), darkSemantic.redDestructive)

    // Success Green
    assertEquals(androidx.compose.ui.graphics.Color(0xFF239B69), pinkSemantic.greenSuccess)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF55D6A0), darkSemantic.greenSuccess)
  }

  @Test
  fun `verify Pink Blush and Rose color palette adheres to exact hex specifications`() {
    assertEquals(androidx.compose.ui.graphics.Color(0xFFFFF4F8), co.znxdnd.echotalk.ui.theme.PinkBlushBg)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFFFEAF2), co.znxdnd.echotalk.ui.theme.PinkBlushBgSecondary)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), co.znxdnd.echotalk.ui.theme.PinkBlushSurface)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFFFF0F5), co.znxdnd.echotalk.ui.theme.PinkBlushCardSecondary)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFE85D9E), co.znxdnd.echotalk.ui.theme.PinkBlushPrimary)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFC83E7D), co.znxdnd.echotalk.ui.theme.PinkBlushDeepRose)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFF3A6C8), co.znxdnd.echotalk.ui.theme.PinkBlushSoftRose)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFF5C5D9), co.znxdnd.echotalk.ui.theme.PinkBlushBorder)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFFCE0EC), co.znxdnd.echotalk.ui.theme.PinkBlushSelectedBg)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF382331), co.znxdnd.echotalk.ui.theme.PinkBlushText)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF806777), co.znxdnd.echotalk.ui.theme.PinkBlushTextSecondary)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFB8A5AF), co.znxdnd.echotalk.ui.theme.PinkBlushTextDisabled)
    assertEquals(androidx.compose.ui.graphics.Color(0xFFA78BFA), co.znxdnd.echotalk.ui.theme.PinkBlushLavender)
  }

  @Test
  fun `verify repository updates and blocks users from state`() {
    val repo = EchoTalkRepository.getInstance()
    repo.blockUser("test-blocked-user-1")
    assertTrue(repo.settings.value.blockedUsers.contains("test-blocked-user-1"))

    repo.unblockUser("test-blocked-user-1")
    assertFalse(repo.settings.value.blockedUsers.contains("test-blocked-user-1"))

    // Ghost Mode synchronization
    repo.updateGhostMode(true)
    assertTrue(repo.settings.value.ghostMode)
    assertFalse(repo.currentUser.value.online)

    repo.updateGhostMode(false)
    assertFalse(repo.settings.value.ghostMode)
    assertTrue(repo.currentUser.value.online)
  }

  @Test
  fun `verify offline minimax chess engine computes legal best move`() {
    val state = co.znxdnd.echotalk.ui.screens.ChessBoardManager.parseFen(co.znxdnd.echotalk.ui.screens.ChessBoardManager.STARTING_FEN)
    val bestMove = co.znxdnd.echotalk.ui.screens.ChessBoardManager.findBestMove(state, depth = 2)
    assertNotNull(bestMove)
    val legalMoves = co.znxdnd.echotalk.ui.screens.ChessBoardManager.getAllLegalMoves(state)
    assertTrue(legalMoves.contains(bestMove))
  }

  @Test
  fun `verify validation manager handles username, message and bio constraints`() {
    // Username constraints
    assertTrue(co.znxdnd.echotalk.utils.ValidationManager.validateUsername("valid_user").isValid)
    assertTrue(co.znxdnd.echotalk.utils.ValidationManager.validateUsername("abc").isValid)
    assertFalse(co.znxdnd.echotalk.utils.ValidationManager.validateUsername("ab").isValid) // < 3 chars
    assertFalse(co.znxdnd.echotalk.utils.ValidationManager.validateUsername("invalid user with space").isValid)
    assertFalse(co.znxdnd.echotalk.utils.ValidationManager.validateUsername("user!@#$").isValid)

    // Message constraints
    assertTrue(co.znxdnd.echotalk.utils.ValidationManager.validateMessageText("Hello world!").isValid)
    assertFalse(co.znxdnd.echotalk.utils.ValidationManager.validateMessageText("").isValid)
    assertFalse(co.znxdnd.echotalk.utils.ValidationManager.validateMessageText("   ").isValid)

    // Bio constraints
    assertTrue(co.znxdnd.echotalk.utils.ValidationManager.validateBio("Short bio").isValid)
    assertFalse(co.znxdnd.echotalk.utils.ValidationManager.validateBio("a".repeat(151)).isValid)

    // Text sanitization
    val dirty = "Hello\u0000\u0007World"
    val clean = co.znxdnd.echotalk.utils.ValidationManager.sanitizeText(dirty)
    assertEquals("HelloWorld", clean)
  }

  @Test
  fun `verify error recovery manager classifies error categories and recoverability`() {
    val netEx = java.net.UnknownHostException("Unable to resolve host")
    val secEx = SecurityException("Permission denied")
    val valEx = IllegalArgumentException("Bad input")

    assertEquals(co.znxdnd.echotalk.utils.ErrorCategory.NETWORK, co.znxdnd.echotalk.utils.ErrorRecoveryManager.classifyError(netEx))
    assertEquals(co.znxdnd.echotalk.utils.ErrorCategory.PERMISSION, co.znxdnd.echotalk.utils.ErrorRecoveryManager.classifyError(secEx))
    assertEquals(co.znxdnd.echotalk.utils.ErrorCategory.VALIDATION, co.znxdnd.echotalk.utils.ErrorRecoveryManager.classifyError(valEx))

    assertTrue(co.znxdnd.echotalk.utils.ErrorRecoveryManager.isRecoverable(co.znxdnd.echotalk.utils.ErrorCategory.NETWORK))
    assertFalse(co.znxdnd.echotalk.utils.ErrorRecoveryManager.isRecoverable(co.znxdnd.echotalk.utils.ErrorCategory.PERMISSION))
    assertFalse(co.znxdnd.echotalk.utils.ErrorRecoveryManager.isRecoverable(co.znxdnd.echotalk.utils.ErrorCategory.VALIDATION))
  }

  @Test
  fun `verify app health monitor tracks success rates and levels`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val monitor = co.znxdnd.echotalk.utils.AppHealthMonitor.getInstance(context)
    monitor.resetMetrics()

    assertEquals(co.znxdnd.echotalk.utils.HealthLevel.HEALTHY, monitor.healthStatus.value.level)
    assertEquals(100, monitor.healthStatus.value.successRatePercent)

    monitor.recordOperation(true, "test-op")
    monitor.recordOperation(true, "test-op")
    assertEquals(100, monitor.healthStatus.value.successRatePercent)

    monitor.recordOperation(false, "test-op")
    monitor.recordOperation(false, "test-op")
    monitor.recordOperation(false, "test-op")
    assertEquals(co.znxdnd.echotalk.utils.HealthLevel.DEGRADED, monitor.healthStatus.value.level)
  }

  @Test
  fun `verify performance manager profiles and motion reduction`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val perfManager = co.znxdnd.echotalk.utils.PerformanceManager.getInstance(context)

    perfManager.setProfile(co.znxdnd.echotalk.utils.PerformanceProfile.LITE)
    assertEquals(co.znxdnd.echotalk.utils.PerformanceProfile.LITE, perfManager.activeProfile.value)
    assertTrue(co.znxdnd.echotalk.ui.components.EchoAnimations.reduceMotionGlobal)

    perfManager.setProfile(co.znxdnd.echotalk.utils.PerformanceProfile.HIGH)
    assertEquals(co.znxdnd.echotalk.utils.PerformanceProfile.HIGH, perfManager.activeProfile.value)
    assertFalse(co.znxdnd.echotalk.ui.components.EchoAnimations.reduceMotionGlobal)
    assertTrue(perfManager.metrics.value.enableBlurs)

    perfManager.setProfile(co.znxdnd.echotalk.utils.PerformanceProfile.BALANCED)
    assertEquals(co.znxdnd.echotalk.utils.PerformanceProfile.BALANCED, perfManager.activeProfile.value)
  }

  @Test
  fun `verify ui sound manager controls volume and mute state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val soundManager = co.znxdnd.echotalk.audio.UiSoundManager.getInstance(context)

    soundManager.setMuted(true)
    assertTrue(soundManager.isMuted.value)

    soundManager.setMuted(false)
    assertFalse(soundManager.isMuted.value)

    soundManager.setVolume(0.5f)
    assertEquals(0.5f, soundManager.volume.value, 0.01f)

    // Clamp behavior
    soundManager.setVolume(1.5f)
    assertEquals(1.0f, soundManager.volume.value, 0.01f)

    soundManager.setVolume(-0.2f)
    assertEquals(0.0f, soundManager.volume.value, 0.01f)
  }

  @Test
  fun `verify sync manager conflict resolution strategies`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val syncManager = co.znxdnd.echotalk.data.local.SyncManager.getInstance(context)

    val clientTime = 1000L
    val serverTime = 2000L

    // LATEST_TIMESTAMP_WINS
    assertFalse(syncManager.resolveConflict(clientTime, serverTime, co.znxdnd.echotalk.data.local.ConflictStrategy.LATEST_TIMESTAMP_WINS))
    assertTrue(syncManager.resolveConflict(serverTime, clientTime, co.znxdnd.echotalk.data.local.ConflictStrategy.LATEST_TIMESTAMP_WINS))

    // SERVER_WINS
    assertFalse(syncManager.resolveConflict(clientTime, serverTime, co.znxdnd.echotalk.data.local.ConflictStrategy.SERVER_WINS))

    // CLIENT_WINS
    assertTrue(syncManager.resolveConflict(clientTime, serverTime, co.znxdnd.echotalk.data.local.ConflictStrategy.CLIENT_WINS))
  }

  @Test
  fun `verify operation manager validation and destructive guard`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val opManager = co.znxdnd.echotalk.utils.OperationManager.getInstance(context)

    // 1. Destructive guard blocks unconfirmed operation
    val destructiveConfig = co.znxdnd.echotalk.utils.OperationConfig(
      operationKey = "delete-account-test",
      requiresConfirmation = true,
      isConfirmed = false
    )
    val blockedResult = opManager.executeSafely(destructiveConfig) { "deleted" }
    assertTrue(blockedResult.isFailure)

    // 2. Pre-validation failure blocks operation
    val validationConfig = co.znxdnd.echotalk.utils.OperationConfig(
      operationKey = "invalid-input-test",
      validator = { co.znxdnd.echotalk.utils.ValidationManager.validateUsername("x") } // Too short
    )
    val invalidResult = opManager.executeSafely(validationConfig) { "done" }
    assertTrue(invalidResult.isFailure)

    // 3. Valid operation succeeds and verifies post condition
    val validConfig = co.znxdnd.echotalk.utils.OperationConfig(
      operationKey = "valid-op-test",
      validator = { co.znxdnd.echotalk.utils.ValidationResult.Valid },
      postVerification = { it == "success_result" }
    )
    val successResult = opManager.executeSafely(validConfig) { "success_result" }
    assertTrue(successResult.isSuccess)
    assertEquals("success_result", successResult.getOrNull())
  }

  @Test
  fun `verify onboarding manager local progress and replay`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val onboardingManager = co.znxdnd.echotalk.auth.OnboardingManager.getInstance(context)

    val testUser = "test_onboard_user_123"
    onboardingManager.setLocalOnboardingCompleted(testUser, false)
    assertFalse(onboardingManager.isLocalOnboardingCompleted(testUser))

    onboardingManager.saveProgress(testUser, co.znxdnd.echotalk.auth.OnboardingStep.PROFILE_PICTURE)
    assertEquals(co.znxdnd.echotalk.auth.OnboardingStep.PROFILE_PICTURE, onboardingManager.currentStep.value)

    onboardingManager.setLocalOnboardingCompleted(testUser, true)
    assertTrue(onboardingManager.isLocalOnboardingCompleted(testUser))

    // Replay resets progress
    onboardingManager.replayOnboarding(testUser)
    assertEquals(co.znxdnd.echotalk.auth.OnboardingStep.WELCOME, onboardingManager.currentStep.value)
    assertFalse(onboardingManager.isLocalOnboardingCompleted(testUser))
  }

  @Test
  fun `verify aurora bloom theme definition and palette tokens`() {
    val allThemes = co.znxdnd.echotalk.data.repository.AppTheme.entries
    assertTrue(allThemes.contains(co.znxdnd.echotalk.data.repository.AppTheme.AURORA_BLOOM))
    assertEquals(9, allThemes.size)

    // Verify Aurora Bloom is configured as a bright light theme
    assertFalse(ThemeManager.isDark(co.znxdnd.echotalk.data.repository.AppTheme.AURORA_BLOOM))

    // Verify custom colors for Aurora Bloom
    val customColors = ThemeManager.getCustomColors(co.znxdnd.echotalk.data.repository.AppTheme.AURORA_BLOOM)
    assertNotNull(customColors)
    assertEquals(co.znxdnd.echotalk.ui.theme.AuroraLightPrimary, customColors.primaryAccent)
    assertEquals(co.znxdnd.echotalk.ui.theme.AuroraLightMainSurface, customColors.cardBackground)

    // Verify semantic tokens
    val semanticColors = ThemeManager.getSemanticColors(co.znxdnd.echotalk.data.repository.AppTheme.AURORA_BLOOM)
    assertEquals(co.znxdnd.echotalk.ui.theme.AuroraLightPrimary, semanticColors.pinkHighlight)
  }

  @Test
  fun `verify firebase auth email sign in and sign up flow`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val authManager = co.znxdnd.echotalk.auth.FirebaseAuthManager.getInstance(context)

    // Sign up flow
    val signUpResult = authManager.signUpWithEmail(
      email = "aurora_tester@example.com",
      password = "secretPassword123",
      displayName = "Aurora Tester"
    )
    assertTrue(signUpResult.isSuccess)
    assertTrue(authManager.isSignedIn.value)
    assertEquals("aurora_tester@example.com", authManager.userEmail.value)

    // Sign out
    authManager.signOut()
    assertFalse(authManager.isSignedIn.value)
    assertNull(authManager.userEmail.value)

    // Sign in flow
    val signInResult = authManager.signInWithEmail("aurora_tester@example.com", "secretPassword123")
    assertTrue(signInResult.isSuccess)
    assertTrue(authManager.isSignedIn.value)
  }

  @Test
  fun `verify onboarding deterministic state transitions and emergency exit`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val onboardingManager = co.znxdnd.echotalk.auth.OnboardingManager.getInstance(context)

    // 1. Unauthenticated / empty userId evaluates to AuthenticationRequired
    onboardingManager.evaluateOnboarding(null)
    assertTrue(onboardingManager.state.value is co.znxdnd.echotalk.auth.OnboardingState.AuthenticationRequired)

    // 2. Emergency enter immediately sets completed state
    val targetUser = "user_emergency_999"
    onboardingManager.emergencyEnterApp(targetUser)
    val state = onboardingManager.state.value
    assertTrue(state is co.znxdnd.echotalk.auth.OnboardingState.Completed)
    assertEquals(targetUser, (state as co.znxdnd.echotalk.auth.OnboardingState.Completed).userId)
    assertTrue(onboardingManager.isProfileCompleted(targetUser))
    assertTrue(onboardingManager.isGuideCompleted(targetUser))
  }

  @Test
  fun `verify onboarding recovery from stuck state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val onboardingManager = co.znxdnd.echotalk.auth.OnboardingManager.getInstance(context)

    val stuckUser = "user_stuck_456"
    onboardingManager.setLocalProfileCompleted(stuckUser, false)
    onboardingManager.setLocalGuideCompleted(stuckUser, false)

    // First recovery attempt with incomplete profile directs to ProfileSetup
    onboardingManager.recoverFromStuckState(stuckUser)
    assertTrue(onboardingManager.state.value is co.znxdnd.echotalk.auth.OnboardingState.ProfileSetup)

    // Mark profile complete, then recover directs to Completed
    onboardingManager.setLocalProfileCompleted(stuckUser, true)
    onboardingManager.recoverFromStuckState(stuckUser)
    assertTrue(onboardingManager.state.value is co.znxdnd.echotalk.auth.OnboardingState.Completed)
  }

  @Test
  fun `verify chess piece symbols and board representation`() {
    val symbols = ChessEngine.pieceSymbols
    assertEquals("♙", symbols["P"])
    assertEquals("♟", symbols["p"])
    assertEquals("♔", symbols["K"])
    assertEquals("♚", symbols["k"])
    assertEquals("♕", symbols["Q"])
    assertEquals("♛", symbols["q"])

    val board = ChessEngine.initialBoard
    assertEquals(8, board.size)
    assertEquals(8, board[0].size)
    assertEquals("r", board[0][0])
    assertEquals("R", board[7][0])
  }

  // =========================================================================
  // ECHO HAPTIC MANAGER - GOOGLE PIXEL, TELEGRAM, & MESSENGER TEST SUITE
  // =========================================================================

  @Test
  fun `echo haptic manager handles google pixel style clicks and ticks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val haptic = EchoHapticManager.getInstance(context)
    assertNotNull(haptic)
    assertTrue(haptic.isVibrationEnabled)

    // Execute Google Pixel style haptics (sharp micro-clicks and ticks)
    haptic.performPixelClick()
    haptic.performPixelTick()
    haptic.performPixelHeavyClick()
    haptic.performPixelDoubleClick()

    // Verify predefined timings are defined with expected microsecond scale
    assertTrue(EchoHapticManager.TIMINGS_PIXEL_CLICK.isNotEmpty())
    assertTrue(EchoHapticManager.TIMINGS_PIXEL_TICK.isNotEmpty())
    assertTrue(EchoHapticManager.TIMINGS_PIXEL_HEAVY.isNotEmpty())
    assertTrue(EchoHapticManager.TIMINGS_PIXEL_DOUBLE.isNotEmpty())
    assertEquals(2, EchoHapticManager.TIMINGS_PIXEL_CLICK.size)
    assertEquals(16L, EchoHapticManager.TIMINGS_PIXEL_CLICK[1])
  }

  @Test
  fun `echo haptic manager handles telegram style dm, photo editor, and weighted deletion waveforms`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val haptic = EchoHapticManager.getInstance(context)

    // Telegram DM send/receive
    haptic.performTelegramMessageSent()
    haptic.performTelegramMessageLongPress()
    haptic.performTelegramMediaOpen()

    // Telegram Photo Editor tool switch, brush strokes, color swatch
    haptic.performTelegramPhotoToolSwitch()
    haptic.performTelegramPhotoBrushStroke()
    haptic.performTelegramPhotoColorSelect()

    // Telegram deletion weighted destruction decay pattern
    haptic.performTelegramMediaDelete()
    assertTrue(EchoHapticManager.TIMINGS_TELEGRAM_DELETE.isNotEmpty())
    assertEquals(4, EchoHapticManager.TIMINGS_TELEGRAM_DELETE.size)
    assertEquals(255, EchoHapticManager.AMPLITUDES_TELEGRAM_DELETE[3])
  }

  @Test
  fun `echo haptic manager handles messenger emoji package, hold-to-grow swell, and burst`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val haptic = EchoHapticManager.getInstance(context)

    // Tap on emoji reaction pop
    haptic.performMessengerEmojiSelect(emoji = "❤️")
    haptic.performMessengerEmojiSelect(emoji = "👍")

    // Hold-to-grow progressive ticks as reaction button swells
    haptic.performMessengerEmojiHoldGrow(scaleRatio = 1.3f)
    haptic.performMessengerEmojiHoldGrow(scaleRatio = 1.8f)
    haptic.performMessengerEmojiHoldGrow(scaleRatio = 2.2f)

    // Big burst confirmation on release
    haptic.performMessengerEmojiBurst()
    assertTrue(EchoHapticManager.TIMINGS_MESSENGER_BURST.isNotEmpty())

    // Flame view-once sizzle
    haptic.performMessengerFlameTap()
    assertTrue(EchoHapticManager.TIMINGS_MESSENGER_FLAME.isNotEmpty())
  }

  @Test
  fun `echo haptic manager handles arcade hub game moves, peeks, and win-loss cadences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val haptic = EchoHapticManager.getInstance(context)

    // Game selection and zero-delay peeking
    haptic.performArcadeSelect()
    haptic.performArcadePeek()

    // Standard chess move vs capture move
    haptic.performArcadeMove(isCapture = false)
    haptic.performArcadeMove(isCapture = true)

    // Rejection / invalid move buzz
    haptic.performArcadeInvalid()

    // Win vs Loss cadences
    haptic.performArcadeWin()
    haptic.performArcadeLoss()

    // High priority alert
    haptic.performAlertReceived()
    assertTrue(EchoHapticManager.TIMINGS_ARCADE_WIN.isNotEmpty())
    assertTrue(EchoHapticManager.TIMINGS_ARCADE_LOSS.isNotEmpty())
  }

  @Test
  fun `echo haptic manager respects master settings toggle and disables all vibrations when false`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val haptic = EchoHapticManager.getInstance(context)

    // Turn OFF in settings
    haptic.isVibrationEnabled = false
    assertFalse(haptic.isVibrationEnabled)

    // When disabled, all methods must execute cleanly without error or motor triggers (pure NO-OP)
    haptic.performPixelClick()
    haptic.performPixelTick()
    haptic.performTelegramMessageSent()
    haptic.performTelegramMediaDelete()
    haptic.performMessengerEmojiBurst()
    haptic.performMessengerFlameTap()
    haptic.performArcadeMove(isCapture = true)
    haptic.performArcadeWin()

    // Re-enable
    haptic.isVibrationEnabled = true
    assertTrue(haptic.isVibrationEnabled)
  }

  @Test
  fun `telegram sticker manager provides curated packs and default offline pack`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = TelegramStickerManager.getInstance(context)

    val allPacks = manager.getAllPacks()
    assertTrue(allPacks.isNotEmpty())
    assertEquals(4, allPacks.size)

    val duckPack = allPacks.find { it.id == "duck_classics" }
    assertNotNull(duckPack)
    assertEquals("Duck Classics", duckPack!!.name)
    assertEquals("🦆", duckPack.iconEmoji)
    assertTrue(duckPack.stickers.size >= 8)
    // Default installed
    assertTrue(manager.isPackDownloaded("duck_classics"))

    val installed = manager.getInstalledPacks()
    assertTrue(installed.any { it.id == "duck_classics" })
  }

  @Test
  fun `telegram sticker manager simulates download pack and persists in app library`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = TelegramStickerManager.getInstance(context)

    // Initially cyber_crew is not downloaded
    val progressEvents = mutableListOf<Float>()
    val success = manager.downloadPack("cyber_crew") { p ->
      progressEvents.add(p)
    }

    assertTrue(success)
    assertTrue(manager.isPackDownloaded("cyber_crew"))
    assertTrue(progressEvents.isNotEmpty())
    assertTrue(progressEvents.last() >= 1.0f)

    // Installed packs should now include cyber_crew
    val installed = manager.getInstalledPacks()
    assertTrue(installed.any { it.id == "cyber_crew" })

    // Test delete downloaded pack
    manager.deleteDownloadedPack("cyber_crew")
    assertFalse(manager.isPackDownloaded("cyber_crew"))
  }

  @Test
  fun `telegram sticker manager provides curated looping gifs by category`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = TelegramStickerManager.getInstance(context)

    val allGifs = manager.getCuratedGifs()
    assertTrue(allGifs.isNotEmpty())

    val gamingGifs = manager.getCuratedGifs("Gaming")
    assertTrue(gamingGifs.isNotEmpty())
    assertTrue(gamingGifs.all { it.category == "Gaming" })

    val reactionsGifs = manager.getCuratedGifs("Reactions")
    assertTrue(reactionsGifs.isNotEmpty())
    assertTrue(reactionsGifs.all { it.category == "Reactions" })
  }

  @Test
  fun `send dm supports telegram stickers and gifs in local-first repository and room cache`() = runBlocking {
    val repo = EchoTalkRepository.getInstance()

    // Send sticker DM to existing friend
    val (okSticker, errSticker) = repo.sendDM(
      recipientId = "u-elena",
      text = "🦆",
      mediaUrl = "https://example.com/duck.png",
      isSticker = true,
      stickerPackId = "duck_classics"
    )
    assertTrue(okSticker)
    assertNull(errSticker)

    val elenaThread = repo.dms.value["u-elena"] ?: emptyList()
    assertTrue(elenaThread.isNotEmpty())
    val lastMsg = elenaThread.last()
    assertTrue(lastMsg.isSticker)
    assertEquals("duck_classics", lastMsg.stickerPackId)
    assertEquals("https://example.com/duck.png", lastMsg.mediaUrl)

    // Send GIF DM
    val (okGif, errGif) = repo.sendDM(
      recipientId = "u-elena",
      text = "Victory Dance",
      mediaUrl = "https://example.com/dance.gif",
      isGif = true
    )
    assertTrue(okGif)
    assertNull(errGif)

    val updatedThread = repo.dms.value["u-elena"] ?: emptyList()
    val gifMsg = updatedThread.last()
    assertTrue(gifMsg.isGif)
    assertEquals("Victory Dance", gifMsg.text)
    assertEquals("https://example.com/dance.gif", gifMsg.mediaUrl)
  }

  @Test
  fun `swipe to archive and unarchive conversation updates repository state and folders`() {
    val repo = EchoTalkRepository.getInstance()

    // Initially u-lucas is not archived
    assertFalse(repo.isConversationArchived("u-lucas"))
    assertFalse(repo.archivedPartnerIds.value.contains("u-lucas"))

    // Swipe right to archive
    repo.archiveConversation("u-lucas")
    assertTrue(repo.isConversationArchived("u-lucas"))
    assertTrue(repo.archivedPartnerIds.value.contains("u-lucas"))

    // Swipe right to unarchive
    repo.unarchiveConversation("u-lucas")
    assertFalse(repo.isConversationArchived("u-lucas"))
    assertFalse(repo.archivedPartnerIds.value.contains("u-lucas"))
  }

  @Test
  fun `swipe to delete conversation clears thread and supports undo restore`() = runBlocking {
    val repo = EchoTalkRepository.getInstance()

    // Ensure thread has messages
    repo.sendDM(recipientId = "u-lucas", text = "Test message for swipe deletion")
    val threadBefore = repo.dms.value["u-lucas"] ?: emptyList()
    assertTrue(threadBefore.isNotEmpty())

    // Swipe left to delete conversation
    val deletedMsgs = repo.deleteConversation("u-lucas")
    assertEquals(threadBefore.size, deletedMsgs.size)
    val threadAfterDelete = repo.dms.value["u-lucas"] ?: emptyList()
    assertTrue(threadAfterDelete.isEmpty())

    // Restore via Undo action
    repo.restoreConversation("u-lucas", deletedMsgs)
    val threadRestored = repo.dms.value["u-lucas"] ?: emptyList()
    assertEquals(threadBefore.size, threadRestored.size)
    assertEquals(deletedMsgs.last().text, threadRestored.last().text)
  }

  @Test
  fun `verify personalization manager enforces strict limit of max 10 custom themes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = co.znxdnd.echotalk.data.personalization.PersonalizationManager.getInstance(context)

    // Restore defaults first
    manager.restoreDefaultCustomThemes()
    val initialCount = manager.config.value.customThemes.size
    assertTrue(initialCount <= co.znxdnd.echotalk.data.personalization.PersonalizationManager.MAX_CUSTOM_THEMES)

    // Add up to 10 themes
    for (i in (initialCount + 1)..10) {
      val success = manager.saveCustomTheme(
        co.znxdnd.echotalk.data.personalization.CustomTheme(
          id = "test_theme_$i",
          name = "Theme $i",
          colors = co.znxdnd.echotalk.data.personalization.SemanticColorRoles.defaultPalette()
        )
      )
      assertTrue(success)
    }

    assertEquals(10, manager.config.value.customThemes.size)

    // 11th theme must be rejected to enforce strict customization boundaries
    val overflowSuccess = manager.saveCustomTheme(
      co.znxdnd.echotalk.data.personalization.CustomTheme(
        id = "test_theme_11_overflow",
        name = "Overflow Theme",
        colors = co.znxdnd.echotalk.data.personalization.SemanticColorRoles.defaultPalette()
      )
    )
    assertFalse(overflowSuccess)
    assertEquals(10, manager.config.value.customThemes.size)
  }

  @Test
  fun `verify contrast protector automatically fixes low contrast text combinations`() {
    val black = androidx.compose.ui.graphics.Color(0xFF000000)
    val darkBg = androidx.compose.ui.graphics.Color(0xFF0F172A)
    val white = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val brightBg = androidx.compose.ui.graphics.Color(0xFFFFFFFF)

    // Black text on dark bg fails WCAG
    val initialRatio = co.znxdnd.echotalk.data.personalization.ContrastProtector.getContrastRatio(black, darkBg)
    assertTrue(initialRatio < 2.0f)

    // Protected text is guaranteed >= 4.0:1
    val safeText = co.znxdnd.echotalk.data.personalization.ContrastProtector.ensureContrast(black, darkBg)
    val safeRatio = co.znxdnd.echotalk.data.personalization.ContrastProtector.getContrastRatio(safeText, darkBg)
    assertTrue(safeRatio >= 4.0f)

    // White text on white bg is also protected
    val safeDarkText = co.znxdnd.echotalk.data.personalization.ContrastProtector.ensureContrast(white, brightBg)
    val safeBrightRatio = co.znxdnd.echotalk.data.personalization.ContrastProtector.getContrastRatio(safeDarkText, brightBg)
    assertTrue(safeBrightRatio >= 4.0f)
  }

  @Test
  fun `verify curated fonts have exactly 8 choices and imported fonts max 2 slots`() {
    val curatedFonts = co.znxdnd.echotalk.data.personalization.CuratedFont.entries
    assertEquals(8, curatedFonts.size)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = co.znxdnd.echotalk.data.personalization.PersonalizationManager.getInstance(context)

    // Import slot 1 and slot 2
    assertTrue(manager.importCustomFont(1, "Custom Sans", "sans.ttf"))
    assertTrue(manager.importCustomFont(2, "Custom Serif", "serif.otf"))

    // Slot 3 must be rejected (max 2 limit)
    assertFalse(manager.importCustomFont(3, "Invalid Slot", "font.ttf"))

    val slots = manager.config.value.customFontSlots
    assertEquals(2, slots.size)
  }

  @Test
  fun `verify public room messages support replies and reactions`() {
    val repo = EchoTalkRepository.getInstance()
    val testRoomId = "r-chat1"

    // Send original message
    val sent1 = repo.sendRoomMessage(testRoomId, "Welcome everyone to EchoTalk community!")
    assertTrue(sent1)
    val room = repo.rooms.value.find { it.id == testRoomId }
    assertNotNull(room)
    val originalMsg = room!!.messages.last()
    assertEquals("Welcome everyone to EchoTalk community!", originalMsg.text)

    // Send reply referencing original message ID
    val sentReply = repo.sendRoomMessage(
      roomId = testRoomId,
      text = "Replying to welcome message",
      replyToId = originalMsg.id,
      replyToSender = originalMsg.sender,
      replyToText = originalMsg.text
    )
    assertTrue(sentReply)

    val updatedRoom = repo.rooms.value.find { it.id == testRoomId }!!
    val replyMsg = updatedRoom.messages.last()
    assertEquals("Replying to welcome message", replyMsg.text)
    assertEquals(originalMsg.id, replyMsg.replyToId)
    assertEquals(originalMsg.sender, replyMsg.replyToSender)
    assertEquals(originalMsg.text, replyMsg.replyToText)

    // Toggle reaction
    val react1 = repo.toggleRoomMessageReaction(testRoomId, replyMsg.id, "🔥", "Alex Mercer")
    assertTrue(react1)
    val roomAfterReact = repo.rooms.value.find { it.id == testRoomId }!!
    val reactedMsg = roomAfterReact.messages.find { it.id == replyMsg.id }!!
    assertEquals(1, reactedMsg.reactions["🔥"])
    assertTrue(reactedMsg.userReactions["🔥"]?.contains("Alex Mercer") == true)

    // Toggle again removes reaction
    val react2 = repo.toggleRoomMessageReaction(testRoomId, replyMsg.id, "🔥", "Alex Mercer")
    assertTrue(react2)
    val roomAfterUnreact = repo.rooms.value.find { it.id == testRoomId }!!
    val unreactedMsg = roomAfterUnreact.messages.find { it.id == replyMsg.id }!!
    assertNull(unreactedMsg.reactions["🔥"])

    // Delete message
    val deleted = repo.deleteRoomMessage(testRoomId, replyMsg.id)
    assertTrue(deleted)
    val roomAfterDelete = repo.rooms.value.find { it.id == testRoomId }!!
    assertNull(roomAfterDelete.messages.find { it.id == replyMsg.id })
  }

  @Test
  fun `verify getSenderIdentityColor produces deterministic color for same sender`() {
    val c1 = co.znxdnd.echotalk.ui.components.getSenderIdentityColor("Marcus Vance")
    val c2 = co.znxdnd.echotalk.ui.components.getSenderIdentityColor("Marcus Vance")
    assertEquals(c1, c2)

    val c3 = co.znxdnd.echotalk.ui.components.getSenderIdentityColor("Elena Rostova")
    assertNotNull(c3)
  }

  @Test
  fun `personalization manager recovers safely from corrupted shared preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("echotalk_personalization_prefs", Context.MODE_PRIVATE)
    prefs.edit()
      .clear()
      .putString("custom_themes", "{corrupted-json")
      .putString("recent_room_ids", "not-an-array")
      .putString("room_visit_counts", "{bad")
      .putString("dm_drafts", "broken")
      .putString("interface_text_scale", "wrong-type-string")
      .putString("personalization_enabled", "wrong-boolean-type")
      .commit()

    val manager = co.znxdnd.echotalk.data.personalization.PersonalizationManager.getInstance(context)
    manager.reloadForTesting()
    val cfg = manager.config.value

    assertEquals(1.0f, cfg.interfaceTextScale, 0.001f)
    assertTrue(cfg.personalizationEnabled)
    assertTrue(cfg.customThemes.isNotEmpty())
    assertTrue(cfg.recentRoomIds.isEmpty())
    assertTrue(cfg.dmDrafts.isEmpty())
  }

  @Test
  fun `personalization manager tracks visits, drafts, searches, opt-out and full reset`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = co.znxdnd.echotalk.data.personalization.PersonalizationManager.getInstance(context)
    manager.clearPersonalizationData()
    manager.setPersonalizationEnabled(true)

    manager.recordRoomVisit("r-chat2")
    manager.recordRoomVisit("r-chat2")
    manager.recordRoomVisit("r-voice1")
    manager.recordConversationOpened("u-lucas")
    manager.setSelectedInterests(listOf("Chess", "Music"))
    manager.setLastSelectedTab("ROOMS")
    manager.setLastRoomCategory("🎙️ Audio Rooms")
    manager.setLastDiscoverFilters("Female", "France", 20f, 30f)
    manager.recordDiscoverSearch("french")
    manager.recordDmSearch("lucas")
    manager.saveDmDraft("u-lucas", "Hey Lucas, almost ready")
    manager.saveRoomDraft("r-chat1", "Draft room msg")

    val cfg = manager.config.value
    assertEquals("r-voice1", cfg.recentRoomIds.first())
    assertEquals(2, cfg.roomVisitCounts["r-chat2"])
    assertEquals("u-lucas", cfg.recentConversationIds.first())
    assertEquals(listOf("Chess", "Music"), cfg.selectedInterests)
    assertEquals("ROOMS", cfg.lastSelectedTabId)
    assertEquals("🎙️ Audio Rooms", cfg.lastRoomCategory)
    assertEquals("Female", cfg.lastDiscoverGender)
    assertEquals("France", cfg.lastDiscoverCountry)
    assertEquals("Hey Lucas, almost ready", manager.getDmDraft("u-lucas"))
    assertEquals("Draft room msg", manager.getRoomDraft("r-chat1"))
    assertEquals(listOf("french"), cfg.recentDiscoverSearches)
    assertEquals(listOf("lucas"), cfg.recentDmSearches)

    // Disable personalization stops new adaptive tracking
    manager.setPersonalizationEnabled(false)
    manager.recordRoomVisit("r-special")
    manager.recordDiscoverSearch("blocked-when-off")
    assertFalse(manager.config.value.recentRoomIds.contains("r-special"))
    assertFalse(manager.config.value.recentDiscoverSearches.contains("blocked-when-off"))

    // Clear removes history/drafts/searches while respecting opt-out toggle
    manager.clearPersonalizationData()
    val cleared = manager.config.value
    assertFalse(cleared.personalizationEnabled)
    assertTrue(cleared.recentRoomIds.isEmpty())
    assertTrue(cleared.roomVisitCounts.isEmpty())
    assertTrue(cleared.dmDrafts.isEmpty())
    assertTrue(cleared.recentDmSearches.isEmpty())
  }

  @Test
  fun `smart personalization engine orders rooms, crew, and conversations safely`() {
    val engine = co.znxdnd.echotalk.data.personalization.SmartPersonalizationEngine
    val rooms = listOf(
      co.znxdnd.echotalk.data.model.ChatRoom(id = "r-1", name = "General Lounge", subtitle = "Open chat", type = "text", badge = "Open", description = "Casual"),
      co.znxdnd.echotalk.data.model.ChatRoom(id = "r-2", name = "Strategy & Chess", subtitle = "Board tactics", type = "text", badge = "Games", description = "Chess matches"),
      co.znxdnd.echotalk.data.model.ChatRoom(id = "r-3", name = "Acoustic Stage", subtitle = "Live music", type = "voice", badge = "Live", description = "Spatial stage")
    )

    val config = co.znxdnd.echotalk.data.personalization.PersonalizationConfig(
      personalizationEnabled = true,
      recentRoomIds = listOf("r-3"),
      roomVisitCounts = mapOf("r-3" to 4),
      selectedInterests = listOf("Chess")
    )
    val rankedRooms = engine.rankRooms(rooms, config)
    assertEquals(3, rankedRooms.size) // Never hides rooms
    assertEquals("r-3", rankedRooms.first().id) // Recent + frequent floats first
    assertEquals("r-2", rankedRooms[1].id) // Interest match rises above unvisited neutral room

    // Disabling personalization keeps original order untouched
    val disabledRooms = engine.rankRooms(rooms, config.copy(personalizationEnabled = false))
    assertEquals(listOf("r-1", "r-2", "r-3"), disabledRooms.map { it.id })

    // Crew ranking keeps ALL online users strictly before offline users and ignores popularity (likesCount)
    val currentUser = co.znxdnd.echotalk.data.model.User(id = "u-me", name = "Me", handle = "@me", country = "France", countryCode = "FR", gender = "Male")
    val users = listOf(
      co.znxdnd.echotalk.data.model.User(id = "u-offline-pop", name = "Offline Star", handle = "@star", country = "Brazil", countryCode = "BR", gender = "Female", online = false, likesCount = 9999, isFriend = true),
      co.znxdnd.echotalk.data.model.User(id = "u-online-neutral", name = "Online User", handle = "@online", country = "Japan", countryCode = "JP", gender = "Non-Binary", online = true, likesCount = 5),
      co.znxdnd.echotalk.data.model.User(id = "u-online-match", name = "Online Match", handle = "@match", country = "France", countryCode = "FR", gender = "Female", online = true, likesCount = 0, isFriend = true)
    )
    val rankedCrew = engine.rankCrewUsers(users, currentUser, config)
    assertTrue(rankedCrew[0].online && rankedCrew[1].online)
    assertFalse(rankedCrew[2].online) // Offline stays last regardless of 9999 likes
    assertEquals("u-online-match", rankedCrew[0].id)

    // Conversation ranking keeps pinned first, then recent conversations
    val convRanked = engine.rankConversations(
      partners = users,
      pinnedIds = setOf("u-offline-pop"),
      dms = emptyMap(),
      config = config.copy(recentConversationIds = listOf("u-online-match"))
    )
    assertEquals("u-offline-pop", convRanked.first().id)
    assertEquals("u-online-match", convRanked[1].id)
  }

  @Test
  fun `repository phase 2 helpers prevent duplicate submits, retry failed msgs, preserve scroll and restore deletes`() {
    val repo = EchoTalkRepository.getInstance()

    // Duplicate submission suppression
    assertFalse(repo.isDuplicateSubmission("dm:u-lucas", "Hello once"))
    assertTrue(repo.isDuplicateSubmission("dm:u-lucas", "Hello once"))
    assertFalse(repo.isDuplicateSubmission("dm:u-lucas", "Hello twice"))

    // Scroll position & unread boundary
    repo.saveScrollPosition("dm:u-lucas", 4, 32)
    assertEquals(4 to 32, repo.getScrollPosition("dm:u-lucas"))
    repo.markConversationRead("dm:u-lucas", "msg-last-read")
    assertEquals("msg-last-read", repo.getLastReadMessageId("dm:u-lucas"))

    // Failed DM recording and retry
    val failedDm = repo.recordFailedDM("u-lucas", "Failed retry message")
    assertEquals("failed", failedDm.status)
    assertTrue((repo.dms.value["u-lucas"] ?: emptyList()).any { it.id == failedDm.id })
    val (retryOk, retryErr) = repo.retryFailedDM("u-lucas", failedDm.id)
    assertTrue(retryOk)
    assertNull(retryErr)
    val lucasAfterRetry = repo.dms.value["u-lucas"] ?: emptyList()
    assertFalse(lucasAfterRetry.any { it.id == failedDm.id })
    assertTrue(lucasAfterRetry.any { it.text == "Failed retry message" && it.status != "failed" })

    // Failed room message and retry
    val failedRoom = repo.recordFailedRoomMessage("r-chat1", "Room retry text")
    assertNotNull(failedRoom)
    assertEquals("failed", failedRoom!!.status)
    assertTrue(repo.retryFailedRoomMessage("r-chat1", failedRoom.id))
    val roomAfterRetry = repo.rooms.value.first { it.id == "r-chat1" }.messages
    assertFalse(roomAfterRetry.any { it.id == failedRoom.id })
    assertTrue(roomAfterRetry.any { it.text == "Room retry text" && it.status != "failed" })

    // Undo / restore deleted single DM & room message
    val msgToUndo = ChatMessage(id = "undo-dm-1", sender = "You", text = "Undo me", timestamp = "Now", status = "sent")
    repo.restoreDMMessage("u-lucas", msgToUndo, 0)
    assertEquals("undo-dm-1", repo.dms.value["u-lucas"]?.firstOrNull()?.id)

    val roomUndo = ChatMessage(id = "undo-room-1", sender = "You", text = "Undo room msg", timestamp = "Now", status = "sent")
    repo.restoreRoomMessage("r-chat1", roomUndo, 0)
    assertEquals("undo-room-1", repo.rooms.value.first { it.id == "r-chat1" }.messages.firstOrNull()?.id)
  }
}

