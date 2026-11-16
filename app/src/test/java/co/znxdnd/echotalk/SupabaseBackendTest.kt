package co.znxdnd.echotalk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import co.znxdnd.echotalk.auth.OnboardingManager
import co.znxdnd.echotalk.auth.OnboardingState
import co.znxdnd.echotalk.data.local.AppRoomDatabase
import co.znxdnd.echotalk.data.local.OfflineQueueManager
import co.znxdnd.echotalk.data.local.entity.PendingQueueEntity
import co.znxdnd.echotalk.data.model.User
import co.znxdnd.echotalk.data.remote.ArcadeSessionDto
import co.znxdnd.echotalk.data.remote.DirectMessageDto
import co.znxdnd.echotalk.data.remote.ImageCompressor
import co.znxdnd.echotalk.data.remote.ProfileDto
import co.znxdnd.echotalk.data.remote.ProfilePresenceSnapshot
import co.znxdnd.echotalk.data.remote.PushNotificationRequest
import co.znxdnd.echotalk.data.remote.RoomMessageDto
import co.znxdnd.echotalk.data.remote.SupabaseClientProvider
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseMappers
import co.znxdnd.echotalk.data.remote.SupabaseMappers.toDomain
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.ui.components.formatLastSeenLabel
import co.znxdnd.echotalk.utils.BackendSchemaState
import co.znxdnd.echotalk.utils.ErrorCategory
import co.znxdnd.echotalk.utils.ErrorRecoveryManager
import co.znxdnd.echotalk.utils.ResilienceGuardian
import co.znxdnd.echotalk.utils.SchemaNotInitializedException
import io.github.jan.supabase.realtime.Presence
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import co.znxdnd.echotalk.audio.AudioSignalProcessor
import co.znxdnd.echotalk.auth.FirebaseAuthManager
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.image.AspectPreset
import co.znxdnd.echotalk.image.ColorAdjustments
import co.znxdnd.echotalk.image.CropTransformState
import co.znxdnd.echotalk.image.EditorBrushStyle
import co.znxdnd.echotalk.image.EditorStroke
import co.znxdnd.echotalk.image.EditorTextOverlay
import co.znxdnd.echotalk.image.FilterPreset
import co.znxdnd.echotalk.image.ProImageEngine
import co.znxdnd.echotalk.audio.VoiceNoteRecorder
import co.znxdnd.echotalk.ui.components.DoodleCanvasDialog
import co.znxdnd.echotalk.ui.components.MediaLightboxDialog
import co.znxdnd.echotalk.ui.components.PhotoEditorDialog
import co.znxdnd.echotalk.ui.components.ViewOnceViewerDialog
import co.znxdnd.echotalk.ui.components.VoiceNotePlayer
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import co.znxdnd.echotalk.ui.screens.AuthScreen
import co.znxdnd.echotalk.ui.screens.ChatScreen
import co.znxdnd.echotalk.ui.screens.ChessBoardManager
import co.znxdnd.echotalk.ui.screens.ChessGameView
import co.znxdnd.echotalk.ui.theme.EchoTalkTheme
import co.znxdnd.echotalk.utils.ConversationSearchEngine
import co.znxdnd.echotalk.utils.RateLimiter
import co.znxdnd.echotalk.utils.SmartReplyRanker
import org.junit.Rule
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseBackendTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `supabase config rejects blank and placeholder credentials and accepts valid values`() {
        assertFalse(SupabaseConfig.isValidConfig("", ""))
        assertFalse(SupabaseConfig.isValidConfig("https://YOUR_PROJECT.supabase.co", "YOUR_KEY"))
        assertFalse(SupabaseConfig.isValidConfig("https://placeholder.supabase.co", "anon_key_1234567890123456"))
        assertFalse(SupabaseConfig.isValidConfig("ftp://myproj.supabase.co", "sb_publishable_1234567890123456"))
        assertFalse(SupabaseConfig.isValidConfig("https://myproj.supabase.co", "short"))

        assertTrue(
            SupabaseConfig.isValidConfig(
                "https://abcdefg.supabase.co",
                "sb_publishable_valid_test_key_1234567890"
            )
        )
    }

    @Test
    fun `dto serialization matches postgres snake_case schema columns`() {
        val json = SupabaseClientProvider.json

        val dmDto = DirectMessageDto(
            id = "11111111-1111-1111-1111-111111111111",
            clientMsgId = "client-123",
            conversationId = "22222222-2222-2222-2222-222222222222",
            senderId = "33333333-3333-3333-3333-333333333333",
            senderName = "Zavier",
            content = "Hello live Postgres",
            messageType = "text",
            isViewOnce = false,
            deliveryStatus = "sent"
        )
        val encodedDm = json.encodeToString(DirectMessageDto.serializer(), dmDto)
        assertTrue(encodedDm.contains("\"client_msg_id\":\"client-123\""))
        assertTrue(encodedDm.contains("\"conversation_id\":\"22222222-2222-2222-2222-222222222222\""))
        assertTrue(encodedDm.contains("\"delivery_status\":\"sent\""))

        val decodedDm = json.decodeFromString(DirectMessageDto.serializer(), encodedDm)
        assertEquals(dmDto.clientMsgId, decodedDm.clientMsgId)
        assertEquals("Zavier", decodedDm.toDomain().sender)

        val pushReq = PushNotificationRequest(
            recipientId = "44444444-4444-4444-4444-444444444444",
            conversationId = "22222222-2222-2222-2222-222222222222",
            messageId = "11111111-1111-1111-1111-111111111111",
            previewText = "Hey!",
            notificationType = "dm"
        )
        val encodedPush = json.encodeToString(PushNotificationRequest.serializer(), pushReq)
        assertTrue(encodedPush.contains("\"recipient_id\""))
        assertTrue(encodedPush.contains("\"preview_text\":\"Hey!\""))
        assertTrue(encodedPush.contains("\"notification_type\":\"dm\""))

        val profileDto = ProfileDto(
            id = "33333333-3333-3333-3333-333333333333",
            username = "zavier",
            displayName = "Zavier",
            isOnline = true,
            likesCount = 10,
            profileCompleted = true,
            onboardingCompleted = true
        )
        val domainUser = profileDto.toDomain(isFriend = true)
        assertEquals("@zavier", domainUser.handle)
        assertTrue(domainUser.online)
        assertEquals(10, domainUser.likesCount)

        val roomMsg = RoomMessageDto(
            clientMsgId = "rm-c1",
            roomId = "r-chat1",
            senderId = "33333333-3333-3333-3333-333333333333",
            senderName = "Zavier",
            content = "Room test"
        )
        val encodedRoom = json.encodeToString(RoomMessageDto.serializer(), roomMsg)
        assertTrue(encodedRoom.contains("\"room_id\":\"r-chat1\""))

        val arcade = ArcadeSessionDto(
            id = "chess-1",
            gameType = "chess",
            challengerName = "Zavier",
            opponentName = "Lucas"
        )
        val encodedArcade = json.encodeToString(ArcadeSessionDto.serializer(), arcade)
        assertTrue(encodedArcade.contains("\"game_type\":\"chess\""))
    }

    @Test
    fun `image compressor scales oversized dimensions and compresses under 300kb cap`() {
        val (w1, h1) = ImageCompressor.calculateTargetDimensions(2400, 1200, 720)
        assertEquals(720, w1)
        assertEquals(360, h1)

        val (w2, h2) = ImageCompressor.calculateTargetDimensions(500, 1000, 720)
        assertEquals(360, w2)
        assertEquals(720, h2)

        val (w3, h3) = ImageCompressor.calculateTargetDimensions(400, 300, 720)
        assertEquals(400, w3)
        assertEquals(300, h3)

        assertTrue(ImageCompressor.isSupportedMimeType("image/jpeg"))
        assertTrue(ImageCompressor.isSupportedMimeType("image/png"))
        assertTrue(ImageCompressor.isSupportedMimeType("image/webp"))
        assertFalse(ImageCompressor.isSupportedMimeType("application/pdf"))

        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(64, 128, 220))
        val compressed = ImageCompressor.compressBitmapToLimit(bitmap, ImageCompressor.TARGET_MAX_BYTES)
        bitmap.recycle()
        assertTrue(compressed.isNotEmpty())
        assertTrue(compressed.size <= ImageCompressor.TARGET_MAX_BYTES)
    }

    @Test
    fun `room message retention distinguishes 24h standard vs 48h special rooms and expiration`() {
        assertEquals(24, SupabaseMappers.computeRoomRetentionHours(isSpecial = false))
        assertEquals(48, SupabaseMappers.computeRoomRetentionHours(isSpecial = true))

        val now = 1_700_000_000_000L
        assertTrue(SupabaseMappers.isExpired("2020-01-01T00:00:00Z", nowMillis = now))
        assertFalse(SupabaseMappers.isExpired("2099-01-01T00:00:00Z", nowMillis = now))
    }

    @Test
    fun `real offline queue does not mark items delivered when backend unconfigured or offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppRoomDatabase.createInMemory(context)
        val queueMgr = OfflineQueueManager.getInstance(context)
        queueMgr.clearQueue()

        queueMgr.enqueueMessage(
            recipientId = "u-lucas",
            senderName = "Zavier",
            text = "Must remain queued when offline"
        )
        assertTrue(queueMgr.getPendingCount() >= 1)

        var deliveredCallbackCalled = false
        val flushed = queueMgr.flushQueue { _, _ ->
            deliveredCallbackCalled = true
        }
        assertEquals(0, flushed)
        assertFalse(deliveredCallbackCalled)
        assertTrue(queueMgr.getPendingCount() >= 1)

        queueMgr.clearQueue()
        db.close()
    }

    @Test
    fun `repository offline send marks status sending never delivered and crew keeps online users first`() {
        val repo = EchoTalkRepository.getInstance()
        val offlineUser = User(
            id = "u-off",
            name = "Offline",
            handle = "@off",
            country = "Global",
            countryCode = "GL",
            gender = "Non-Binary",
            online = false
        )
        val onlineUser = User(
            id = "u-on",
            name = "Online",
            handle = "@on",
            country = "Global",
            countryCode = "GL",
            gender = "Non-Binary",
            online = true
        )
        val sorted = repo.syncCrewOnlineFirst(listOf(offlineUser, onlineUser))
        assertTrue(sorted.first().online)
        assertFalse(sorted.last().online)

        if (!repo.isOffline.value) {
            repo.toggleOfflineMode()
        }
        val (ok, err) = repo.sendDM("u-lucas", "Offline truthful test")
        assertTrue(ok)
        assertEquals(null, err)
        val lastMsg = repo.dms.value["u-lucas"]?.last()
        assertEquals("sending", lastMsg?.status)
        assertNotEquals("delivered", lastMsg?.status)

        // Restore online state for subsequent tests
        if (repo.isOffline.value) {
            repo.toggleOfflineMode()
        }
    }

    @Test
    fun `migration sql defines all core tables, rls, and 1mb storage bucket`() {
        val candidates = listOf(
            File("supabase/migrations/0001_initial_schema.sql"),
            File("../supabase/migrations/0001_initial_schema.sql")
        )
        val schemaFile = candidates.firstOrNull { it.exists() }
        if (schemaFile != null) {
            val sql = schemaFile.readText().lowercase()
            assertTrue(sql.contains("create table if not exists public.profiles"))
            assertTrue(sql.contains("create table if not exists public.direct_messages"))
            assertTrue(sql.contains("create table if not exists public.room_messages"))
            assertTrue(sql.contains("create table if not exists public.device_tokens"))
            assertTrue(sql.contains("enable row level security"))
            assertTrue(sql.contains("1048576"))
            assertTrue(sql.contains("profile-images"))
        }
    }

    @Test
    fun `supabase realtime presence tracker handles joins, multi-device partial leaves, and offline disconnect`() {
        val socialRepo = SupabaseSocialRepository.getInstance()
        socialRepo.clearPresenceTrackerForTesting()

        val p1DeviceA = Presence("ref-a1", buildJsonObject { put("user_id", "u-lucas") })
        val p1DeviceB = Presence("ref-a2", buildJsonObject { put("user_id", "u-lucas") })
        val p2DeviceA = Presence("ref-b1", buildJsonObject { put("user_id", "u-matteo") })

        socialRepo.applyPresenceDiff(
            joins = mapOf("k1" to p1DeviceA, "k2" to p1DeviceB, "k3" to p2DeviceA),
            leaves = emptyMap()
        )
        assertTrue(socialRepo.onlineUserIds.value.contains("u-lucas"))
        assertTrue(socialRepo.onlineUserIds.value.contains("u-matteo"))

        // Closing one device keeps u-lucas online because ref-a2 is still active
        socialRepo.applyPresenceDiff(
            joins = emptyMap(),
            leaves = mapOf("k1" to p1DeviceA, "k3" to p2DeviceA)
        )
        assertTrue(socialRepo.onlineUserIds.value.contains("u-lucas"))
        assertFalse(socialRepo.onlineUserIds.value.contains("u-matteo"))

        // Closing final device transitions u-lucas offline
        socialRepo.applyPresenceDiff(
            joins = emptyMap(),
            leaves = mapOf("k2" to p1DeviceB)
        )
        assertFalse(socialRepo.onlineUserIds.value.contains("u-lucas"))
        socialRepo.clearPresenceTrackerForTesting()
    }

    @Test
    fun `repository updates chat contact online offline status and respects privacy snapshots`() {
        val repo = EchoTalkRepository.getInstance()
        val fixedNow = 1_750_000_000_000L

        // Matteo starts offline in seed data -> joins Realtime Presence -> goes online
        repo.applyRealtimePresenceUpdate(
            realtimeOnlineIds = setOf("u-matteo", "u-lucas"),
            profileSnapshots = emptyMap(),
            nowMillis = fixedNow
        )
        assertTrue(repo.users.value.first { it.id == "u-matteo" }.online)

        // Matteo leaves Realtime Presence -> goes offline with lastSeenAtMillis recorded
        repo.applyRealtimePresenceUpdate(
            realtimeOnlineIds = setOf("u-lucas"),
            profileSnapshots = emptyMap(),
            nowMillis = fixedNow + 60_000L
        )
        val offlineMatteo = repo.users.value.first { it.id == "u-matteo" }
        assertFalse(offlineMatteo.online)
        assertEquals(fixedNow + 60_000L, offlineMatteo.lastSeenAtMillis)

        // Ghost mode hides user presence and suppresses lastSeenAtMillis
        val ghostSnapshot = mapOf(
            "u-lucas" to ProfilePresenceSnapshot(
                userId = "u-lucas",
                isOnline = true,
                showOnlineStatus = true,
                ghostMode = true,
                lastSeenAtMillis = fixedNow
            )
        )
        repo.applyRealtimePresenceUpdate(
            realtimeOnlineIds = setOf("u-lucas"),
            profileSnapshots = ghostSnapshot,
            nowMillis = fixedNow + 120_000L
        )
        val ghostLucas = repo.users.value.first { it.id == "u-lucas" }
        assertFalse(ghostLucas.online)
        assertNull(ghostLucas.lastSeenAtMillis)

        // Restore seeded presence state for other tests
        repo.applyRealtimePresenceUpdate(
            realtimeOnlineIds = setOf("u-lucas"),
            profileSnapshots = mapOf(
                "u-lucas" to ProfilePresenceSnapshot(
                    userId = "u-lucas",
                    isOnline = true,
                    showOnlineStatus = true,
                    ghostMode = false,
                    lastSeenAtMillis = fixedNow
                )
            ),
            nowMillis = fixedNow + 180_000L
        )
    }

    @Test
    fun `last seen formatter produces readable relative labels`() {
        val now = 1_750_000_000_000L
        assertEquals("last seen recently", formatLastSeenLabel(null, now))
        assertEquals("last seen just now", formatLastSeenLabel(now - 20_000L, now))
        assertEquals("last seen 5m ago", formatLastSeenLabel(now - 5 * 60_000L, now))
        assertEquals("last seen 3h ago", formatLastSeenLabel(now - 3 * 3_600_000L, now))
        assertEquals("last seen yesterday", formatLastSeenLabel(now - 30 * 3_600_000L, now))
    }

    @Test
    fun `live supabase config from dot env initializes single client provider`() {
        SupabaseClientProvider.resetForTesting()
        if (SupabaseConfig.isConfigured) {
            assertFalse(SupabaseConfig.url.contains("YOUR_SUPABASE_URL"))
            assertFalse(SupabaseConfig.publishableKey.contains("YOUR_SUPABASE_PUBLISHABLE_KEY"))
            val clientA = SupabaseClientProvider.getClientOrNull()
            val clientB = SupabaseClientProvider.getClientOrNull()
            assertTrue("Expected non-null SupabaseClient, initError=${SupabaseClientProvider.lastInitError}", clientA != null)
            assertTrue(clientA === clientB)
        } else {
            val client = SupabaseClientProvider.getClientOrNull()
            assertNull(client)
        }
    }

    @Test
    fun `repository prevents client role escalation and requires both server role and pin`() = runBlocking {
        val repo = EchoTalkRepository.getInstance()
        repo.lockRoleGateway()

        // Default / fallback user must never be owner or admin
        assertEquals("member", repo.currentUser.value.role)
        assertFalse(repo.isRoleGatewayUnlocked.value)
        assertFalse(repo.isAdminOrOwnerUnlocked())

        // Attempting client-side role overwrite cannot escalate a member
        repo.updateUserRole("owner")
        assertEquals("member", repo.currentUser.value.role)
        assertFalse(repo.isAdminOrOwnerUnlocked())

        // Entering valid PIN as a server 'member' must NOT unlock admin/owner privileges
        val memberCopy = repo.currentUser.value.copy(role = "member")
        repo.setServerVerifiedUserForTesting(memberCopy)
        val memberUnlock = repo.verifyAndUnlockRoleGateway("8888")
        assertTrue(memberUnlock.isFailure)
        assertFalse(repo.isRoleGatewayUnlocked.value)
        assertFalse(repo.isAdminOrOwnerUnlocked())

        // Locked user cannot invoke moderation or verification actions
        assertFalse(repo.lockRoom("r-special"))
        assertFalse(repo.adminSetVerification(memberCopy.id, "u-lucas", true))

        // Only when server role is 'owner'/'admin' AND valid PIN is entered does gateway unlock
        repo.setServerVerifiedUserForTesting(memberCopy.copy(role = "owner"))
        assertFalse(repo.isOwnerUnlocked()) // Still hidden without PIN

        val badPin = repo.verifyAndUnlockRoleGateway("1234")
        assertTrue(badPin.isFailure)
        assertFalse(repo.isOwnerUnlocked())

        // Note: verifyAndUnlockRoleGateway will check remote if authenticated; without session it uses currentUser
        val goodPin = repo.verifyAndUnlockRoleGateway("8888")
        assertTrue(goodPin.isSuccess)
        assertTrue(repo.isOwnerUnlocked())
        assertTrue(repo.isAdminOrOwnerUnlocked())

        // Locking immediately hides privileges again
        repo.lockRoleGateway()
        assertFalse(repo.isOwnerUnlocked())
        assertFalse(repo.isAdminOrOwnerUnlocked())

        // Restore clean baseline member state for subsequent tests
        repo.setServerVerifiedUserForTesting(memberCopy.copy(role = "member"))
    }

    @Test
    fun `migration 0002 enforces column privileges, insert and update trigger, and server-only promotion`() {
        val migrationCandidates = listOf(
            File("supabase/migrations/0002_admin_role_security.sql"),
            File("../supabase/migrations/0002_admin_role_security.sql")
        )
        val migrationFile = migrationCandidates.firstOrNull { it.exists() }
        assertTrue("Expected 0002_admin_role_security.sql to exist", migrationFile != null)
        val sql = migrationFile!!.readText().lowercase()
        assertTrue(sql.contains("revoke insert, update, delete on table public.profiles from authenticated"))
        assertTrue(sql.contains("before insert or update on public.profiles"))
        assertTrue(sql.contains("role = 'member'"))
        assertTrue(sql.contains("revoke all on function public.server_set_profile_role(uuid, text) from public, anon, authenticated"))
        assertTrue(sql.contains("grant execute on function public.server_set_profile_role(uuid, text) to service_role"))

        val scriptCandidates = listOf(
            File("supabase/scripts/promote_user_role.sql"),
            File("../supabase/scripts/promote_user_role.sql")
        )
        assertTrue("Expected promote_user_role.sql to exist", scriptCandidates.any { it.exists() })
    }

    @Test
    fun `unauthenticated profile upsert returns Not authenticated without calling PostgREST anon`() = runBlocking {
        SupabaseClientProvider.resetForTesting()
        val repo = EchoTalkRepository.getInstance()
        val socialRepo = SupabaseSocialRepository.getInstance()
        val result = socialRepo.upsertProfile(repo.currentUser.value, repo.settings.value)
        assertTrue(result.isFailure)
        val expectedMsg = if (SupabaseConfig.isConfigured) "Not authenticated" else "Supabase not configured"
        assertEquals(expectedMsg, result.exceptionOrNull()?.message)
    }

    @Test
    fun `resilience guardian circuit breaks PGRST205 schema cache errors and resets cleanly`() = runBlocking {
        ResilienceGuardian.resetForTesting()
        var callCount = 0

        val err = IllegalStateException("Could not find the table 'public.profiles' in the schema cache (Code: PGRST205)")
        assertEquals(ErrorCategory.SCHEMA_MISSING, ErrorRecoveryManager.classifyError(err))

        val first = ResilienceGuardian.executeSupabaseCall("testSchemaCall") {
            callCount++
            throw err
        }
        assertTrue(first.isFailure)
        assertEquals(1, callCount)
        assertTrue(ResilienceGuardian.isSchemaCircuitOpen())
        assertEquals(BackendSchemaState.SCHEMA_MISSING, ResilienceGuardian.status.value.schemaState)

        // Second call during cooldown short-circuits without hitting network again
        val second = ResilienceGuardian.executeSupabaseCall("testSchemaCall") {
            callCount++
            "should-not-run"
        }
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is SchemaNotInitializedException)
        assertEquals(1, callCount)

        // Resetting circuit breaker allows recovery and marks schema healthy on success
        ResilienceGuardian.resetSchemaCircuitBreaker()
        val recovered = ResilienceGuardian.executeSupabaseCall("testSchemaCall") {
            callCount++
            "ok"
        }
        assertTrue(recovered.isSuccess)
        assertEquals("ok", recovered.getOrNull())
        assertEquals(2, callCount)
        assertEquals(BackendSchemaState.HEALTHY, ResilienceGuardian.status.value.schemaState)
        ResilienceGuardian.resetForTesting()
    }

    @Test
    fun `offline queue recovers stuck PROCESSING and retryable FAILED tasks automatically`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppRoomDatabase.createInMemory(context)
        val dao = db.pendingQueueDao()
        val queueMgr = OfflineQueueManager.createForTesting(context, dao)

        dao.insert(
            PendingQueueEntity(
                id = "stuck-proc-1",
                taskType = OfflineQueueManager.TASK_MESSAGE,
                recipientId = "u-lucas",
                senderName = "Zavier",
                text = "recover me",
                timestamp = 1000L,
                status = "PROCESSING",
                retryCount = 1
            )
        )
        dao.insert(
            PendingQueueEntity(
                id = "retry-fail-1",
                taskType = OfflineQueueManager.TASK_PROFILE_UPSERT,
                recipientId = "u-zavier",
                senderName = "Zavier",
                text = "profile_sync",
                timestamp = 2000L,
                status = "FAILED",
                retryCount = 2
            )
        )

        val recovered = queueMgr.recoverStuckTasks()
        assertEquals(2, recovered)
        assertEquals("PENDING", dao.getById("stuck-proc-1")?.status)
        assertEquals("PENDING", dao.getById("retry-fail-1")?.status)

        queueMgr.setSimulatedConnected(true)
        val flushed = queueMgr.flushQueue()
        assertEquals(2, flushed)
        assertEquals(0, queueMgr.getPendingCount())
        db.close()
    }

    @Test
    fun `onboarding treats unauthenticated placeholder u-zavier as AuthenticationRequired and repairs invalid user state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val onboardingManager = OnboardingManager.getInstance(context)
        onboardingManager.evaluateOnboarding("u-zavier")
        assertTrue(onboardingManager.state.value is OnboardingState.AuthenticationRequired)

        val repo = EchoTalkRepository.getInstance()
        val snapshot = repo.currentUser.value
        repo.setServerVerifiedUserForTesting(
            snapshot.copy(name = "", age = 12, role = "superuser_invalid")
        )
        assertTrue(repo.sanitizeCurrentUserInvariants())
        assertEquals("EchoTalker", repo.currentUser.value.name)
        assertEquals(18, repo.currentUser.value.age)
        assertEquals("member", repo.currentUser.value.role)

        // Restore clean baseline state
        repo.setServerVerifiedUserForTesting(snapshot)
    }

    @Test
    fun `pre-sign-in PIN 1209 is isolated from in-app gateway and unlocks local preview cleanly`() = runBlocking {
        RateLimiter.resetForTesting()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EchoTalkRepository.getInstance()
        val onboardingManager = OnboardingManager.getInstance(context)
        val authManager = FirebaseAuthManager.getInstance(context)
        repo.clearPreSignInPreviewSession()

        // In-app server gateway must reject 1209 even if user were member
        val inAppAttempt = repo.verifyAndUnlockRoleGateway("1209")
        assertTrue(inAppAttempt.isFailure)
        assertFalse(repo.isRoleGatewayUnlocked.value)

        // Pre-sign-in gateway accepts 1209 and enters local preview without cloud writes
        assertTrue(repo.verifyPreSignInPin("1209"))
        assertFalse(repo.verifyPreSignInPin("8888"))

        val ownerRes = repo.unlockPreSignInPreviewSession("1209", "owner")
        assertTrue(ownerRes.isSuccess)
        assertTrue(repo.isPreSignInPreviewSession.value)
        assertEquals("owner", repo.currentUser.value.role)
        assertTrue(repo.isOwnerUnlocked())

        onboardingManager.enterPreSignInPreview(ownerRes.getOrNull()!!.id)
        assertTrue(onboardingManager.state.value is OnboardingState.Completed)

        // Re-evaluating with preview active retains Completed instead of dropping to AuthRequired
        onboardingManager.evaluateOnboarding(repo.currentUser.value.id)
        assertTrue(onboardingManager.state.value is OnboardingState.Completed)

        // Sign out clears preview flags and returns to clean member baseline
        authManager.signOut()
        assertFalse(repo.isPreSignInPreviewSession.value)
        assertFalse(repo.isRoleGatewayUnlocked.value)
        assertEquals("member", repo.currentUser.value.role)

        onboardingManager.evaluateOnboarding(null)
        assertTrue(onboardingManager.state.value is OnboardingState.AuthenticationRequired)
        RateLimiter.resetForTesting()
    }

    @Test
    fun `auth screen 15-tap silent logo trigger opens PIN 1209 modal and role selector with zero hints`() {
        RateLimiter.resetForTesting()
        val repo = EchoTalkRepository.getInstance()
        repo.clearPreSignInPreviewSession()
        var grantedUserId: String? = null

        composeRule.setContent {
            EchoTalkTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    onPreSignInPreviewSuccess = { uid -> grantedUserId = uid }
                )
            }
        }
        composeRule.waitForIdle()

        // 14 consecutive taps remain silent and keep modal closed
        repeat(14) {
            composeRule.onNodeWithTag("auth_logo_gateway_trigger").performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Security Access").assertDoesNotExist()
        composeRule.onNodeWithText("1209").assertDoesNotExist()

        // 15th tap reveals hidden PIN modal cleanly
        composeRule.onNodeWithTag("auth_logo_gateway_trigger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Security Access").assertIsDisplayed()
        composeRule.onNodeWithText("1209").assertDoesNotExist()

        // Wrong PIN does not advance to role selector
        listOf("9", "9", "9", "9").forEach { d ->
            composeRule.onNodeWithTag("presignin_pin_digit_$d").performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select Session Role").assertDoesNotExist()
        repeat(4) { composeRule.onNodeWithTag("presignin_pin_del").performClick() }

        // Entering 1209 transitions to in-modal role picker
        listOf("1", "2", "0", "9").forEach { d ->
            composeRule.onNodeWithTag("presignin_pin_digit_$d").performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select Session Role").assertIsDisplayed()

        // Choose Admin role and enter app directly
        composeRule.onNodeWithTag("presignin_role_admin").performClick()
        composeRule.onNodeWithTag("presignin_enter_app_button").performClick()
        composeRule.waitForIdle()

        assertEquals("u-owner-preview", grantedUserId)
        assertTrue(repo.isPreSignInPreviewSession.value)
        assertEquals("admin", repo.currentUser.value.role)

        repo.clearPreSignInPreviewSession()
        RateLimiter.resetForTesting()
    }

    @Test
    fun `fuzzy search, rate limiter, smart reply ranker, and audio DSP behave deterministically`() {
        val repo = EchoTalkRepository.getInstance()
        val allUsers = repo.users.value

        // Typo-tolerant Damerau-Levenshtein user search
        assertEquals(1, ConversationSearchEngine.damerauLevenshtein("lucas", "lcuas"))
        val typoMatches = ConversationSearchEngine.searchUsers(allUsers, "lcuas")
        assertTrue(typoMatches.isNotEmpty())
        assertEquals("u-lucas", typoMatches.first().id)

        // Multi-token + message content search across DM partners
        val msgMatches = ConversationSearchEngine.searchDmPartners(
            partners = allUsers,
            dms = repo.dms.value,
            query = "Tokyo studio"
        )
        assertTrue(msgMatches.any { it.id == "u-kenji" })

        // Token-bucket burst + refill
        RateLimiter.resetForTesting()
        var nowMs = 10_000L
        assertTrue(RateLimiter.allowAction("test_bucket", capacity = 2.0, refillPerSecond = 1.0, nowMs = nowMs))
        assertTrue(RateLimiter.allowAction("test_bucket", capacity = 2.0, refillPerSecond = 1.0, nowMs = nowMs))
        assertFalse(RateLimiter.allowAction("test_bucket", capacity = 2.0, refillPerSecond = 1.0, nowMs = nowMs))
        nowMs += 1_000L
        assertTrue(RateLimiter.allowAction("test_bucket", capacity = 2.0, refillPerSecond = 1.0, nowMs = nowMs))

        // 5-strike PIN lockout guard
        repeat(4) { idx ->
            assertEquals(0, RateLimiter.recordFailedPinAttempt("pin_test", nowMs = 50_000L + idx))
        }
        val lockout = RateLimiter.recordFailedPinAttempt("pin_test", nowMs = 50_010L)
        assertEquals(30, lockout)
        assertTrue(RateLimiter.remainingPinLockoutSeconds("pin_test", nowMs = 55_000L) >= 25)
        RateLimiter.recordSuccessfulPin("pin_test")
        assertEquals(0, RateLimiter.remainingPinLockoutSeconds("pin_test", nowMs = 55_000L))

        // SmartReplyRanker contextual suggestions
        val audioReplies = SmartReplyRanker.suggestReplies(
            thread = listOf(ChatMessage(id = "a1", sender = "Chloe Dubois", text = "Voice Note", isAudio = true)),
            currentUserName = "Zavier"
        )
        assertTrue(audioReplies.any { it.contains("Sounds amazing") })
        val challengeReplies = SmartReplyRanker.suggestReplies(
            thread = listOf(ChatMessage(id = "c1", sender = "Lucas Silva", text = "Chess Duel!", isChallenge = true)),
            currentUserName = "Zavier"
        )
        assertTrue(challengeReplies.any { it.contains("Accept Challenge") })

        // AudioSignalProcessor noise gate + scaled waveform bounds
        val silent = ShortArray(256) { 0 }
        val loud = ShortArray(256) { (Short.MAX_VALUE / 2).toShort() }
        val silentBars = AudioSignalProcessor.extractNormalizedWaveform(silent, barCount = 8)
        val loudBars = AudioSignalProcessor.extractNormalizedWaveform(loud, barCount = 8)
        assertEquals(8, silentBars.size)
        assertEquals(8, loudBars.size)
        assertTrue(silentBars.all { it <= 0.1f })
        assertTrue(loudBars.all { it > 0.7f && it <= 1.0f })
        assertTrue(AudioSignalProcessor.calculateRmsDb(loud) > AudioSignalProcessor.calculateRmsDb(silent))

        RateLimiter.resetForTesting()
    }

    @Test
    fun `pro image engine color matrix, crop aspect, histogram, auto enhance, mosaic redaction, and render export work end-to-end`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Color matrix math: Noir produces balanced grayscale coefficients, warmth boosts red channel
        val noirMat = ProImageEngine.buildColorMatrix(ColorAdjustments(filterPreset = FilterPreset.NOIR))
        assertEquals(noirMat[0], noirMat[5], 0.01f)
        val warmMat = ProImageEngine.buildColorMatrix(ColorAdjustments(warmth = 0.6f))
        assertTrue(warmMat[0] > warmMat[12])

        // 2. Crop aspect enforcement stays strictly within [0f..1f]
        val squareCrop = ProImageEngine.enforceAspectRatio(CropTransformState(), AspectPreset.SQUARE, imageAspectRatio = 1.5f)
        assertTrue(squareCrop.left in 0f..1f && squareCrop.right in 0f..1f)
        assertTrue(squareCrop.top in 0f..1f && squareCrop.bottom in 0f..1f)
        assertTrue(squareCrop.right > squareCrop.left && squareCrop.bottom > squareCrop.top)

        // 3. Generate striped 120x80 test image for histogram, clarity, mosaic redaction, and final render
        val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        for (y in 0 until 80) {
            for (x in 0 until 120) {
                val c = if ((x / 4 + y / 4) % 2 == 0) Color.rgb(110, 120, 130) else Color.rgb(140, 145, 150)
                bmp.setPixel(x, y, c)
            }
        }
        val hist = ProImageEngine.computeHistogram(bmp, bins = 32)
        assertEquals(32, hist.lumaBins.size)
        assertTrue(hist.lumaBins.maxOrNull()!! <= 1.0f)

        val autoAdj = ProImageEngine.computeAutoEnhance(bmp)
        assertTrue(autoAdj.contrast > 1.0f)
        assertTrue(autoAdj.clarity > 0f)

        val sharpened = ProImageEngine.applyClarityKernel(bmp, 0.7f)
        assertEquals(bmp.width, sharpened.width)
        sharpened.recycle()

        // 4. Real pixel mosaic redaction modifies center region under stroke while preserving untouched corner
        val redactTarget = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val untouchedCornerBefore = redactTarget.getPixel(2, 2)
        ProImageEngine.applyRedactMosaic(
            target = redactTarget,
            strokes = listOf(
                EditorStroke(
                    normalizedPoints = listOf(Offset(0.5f, 0.5f), Offset(0.55f, 0.55f)),
                    color = androidx.compose.ui.graphics.Color.Black,
                    strokeWidthFraction = 0.2f,
                    style = EditorBrushStyle.REDACT_MOSAIC
                )
            ),
            blockSizePx = 12
        )
        assertEquals(untouchedCornerBefore, redactTarget.getPixel(2, 2))
        redactTarget.recycle()

        // 5. Render final composite bitmap + persist to cache
        val rendered = ProImageEngine.renderFinalBitmap(
            source = bmp,
            adjustments = ColorAdjustments(filterPreset = FilterPreset.VIVID, clarity = 0.4f, vignette = 0.35f),
            transform = CropTransformState(rotationDegrees = 90),
            strokes = listOf(
                EditorStroke(
                    normalizedPoints = listOf(Offset(0.1f, 0.2f), Offset(0.8f, 0.7f)),
                    color = androidx.compose.ui.graphics.Color.Cyan,
                    strokeWidthFraction = 0.04f,
                    style = EditorBrushStyle.NEON
                )
            ),
            textOverlays = listOf(
                EditorTextOverlay(id = "t1", text = "EchoPro", xFraction = 0.5f, yFraction = 0.5f)
            )
        )
        // Rotated 90 degrees swaps 120x80 -> 80x120
        assertEquals(80, rendered.width)
        assertEquals(120, rendered.height)

        val savedRes = ProImageEngine.saveBitmapToCache(context, rendered, prefix = "test_pro_")
        assertTrue(savedRes.isSuccess)
        val savedFile = File(savedRes.getOrThrow())
        assertTrue(savedFile.exists() && savedFile.length() > 100L)

        savedFile.delete()
        rendered.recycle()
        bmp.recycle()
    }

    @Test
    fun `photo editor dialog studio tabs, histogram toggle, text layer creation, and view-once work smoothly`() {
        var sentViewOnce: Boolean? = null

        composeRule.setContent {
            EchoTalkTheme {
                PhotoEditorDialog(
                    onSend = { sentViewOnce = it },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Histogram HUD starts visible and toggles cleanly
        composeRule.onNodeWithTag("photo_editor_histogram_hud").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_editor_histogram_toggle").performClick()
        composeRule.onNodeWithTag("photo_editor_histogram_hud").assertDoesNotExist()

        // Switch through Pro Studio tabs
        composeRule.onNodeWithTag("photo_editor_tab_adjust").performClick()
        composeRule.onAllNodesWithText("Exposure").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("photo_editor_tab_crop").performClick()
        composeRule.onNodeWithText("Reset Crop").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_editor_tab_draw").performClick()
        composeRule.onNodeWithText("Mosaic Blur").assertIsDisplayed()

        // Add a text overlay in Text tab
        composeRule.onNodeWithTag("photo_editor_tab_text").performClick()
        composeRule.onNodeWithTag("photo_editor_add_text").performClick()
        composeRule.onNodeWithTag("photo_editor_text_input").performTextInput("Studio Shot")
        composeRule.onNodeWithTag("photo_editor_text_confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Studio Shot").assertIsDisplayed()

        // Toggle View-Once & Auto-Enhance cleanly
        composeRule.onNodeWithTag("photo_editor_auto_enhance").performClick()
        composeRule.onNodeWithTag("photo_editor_view_once").performClick()
        composeRule.onNodeWithText("Once").assertIsDisplayed()
        assertNull(sentViewOnce)
    }

    @Test
    fun `doodle studio exposes brush styles, backdrops, view-once toggle, and triggers send cleanly`() {
        var capturedViewOnce = false
        var sendInvoked = false

        composeRule.setContent {
            EchoTalkTheme {
                DoodleCanvasDialog(
                    recipientName = "Lucas",
                    onDismiss = {},
                    onSendDoodle = { viewOnce, _ ->
                        capturedViewOnce = viewOnce
                        sendInvoked = true
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Pro Sketch Studio").assertIsDisplayed()
        composeRule.onNodeWithTag("doodle_brush_neon").performClick()
        composeRule.onNodeWithTag("doodle_brush_eraser").performClick()
        composeRule.onNodeWithTag("doodle_bg_studio_grid").performClick()
        composeRule.onNodeWithTag("doodle_view_once_toggle").performClick()
        composeRule.onNodeWithText("View-Once: ON").assertIsDisplayed()

        composeRule.onNodeWithTag("doodle_send_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { sendInvoked }
        assertTrue(capturedViewOnce)
    }

    @Test
    fun `media lightbox toggles zoom and invokes edit in studio callback`() {
        var editedUrl: String? = null
        val msg = ChatMessage(
            id = "m-light-1",
            sender = "Elena",
            text = "Studio Edit",
            timestamp = "10:15 AM",
            mediaUrl = "/tmp/sample_studio.jpg"
        )

        composeRule.setContent {
            EchoTalkTheme {
                MediaLightboxDialog(
                    message = msg,
                    onDismiss = {},
                    onEditInStudio = { editedUrl = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2x Zoom").performClick()
        composeRule.onNodeWithText("Reset").assertIsDisplayed()
        composeRule.onNodeWithTag("media_lightbox_save").assertIsDisplayed()
        composeRule.onNodeWithTag("media_lightbox_edit_studio").performClick()
        assertEquals("/tmp/sample_studio.jpg", editedUrl)
    }

    @Test
    fun `view-once viewer burns once on manual destroy and dismisses cleanly`() {
        var burnCount = 0
        var dismissed = false

        composeRule.setContent {
            EchoTalkTheme {
                ViewOnceViewerDialog(
                    senderName = "Marcus",
                    mediaUrl = "/tmp/secret.jpg",
                    onBurned = { burnCount += 1 },
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("DESTROY & DISMISS NOW").performClick()
        composeRule.waitForIdle()
        assertEquals(1, burnCount)
        assertTrue(dismissed)
    }

    @Test
    fun `safety and diagnostic report submission validate input and succeed`() {
        val repo = EchoTalkRepository.getInstance()
        assertFalse(repo.submitSafetyReport("", "Spam"))
        assertTrue(repo.submitSafetyReport("spammer_user", "Unsolicited links"))
        assertNull(repo.submitDiagnosticReport(""))
        val diagSummary = repo.submitDiagnosticReport("Image export completed cleanly")
        assertTrue(!diagSummary.isNullOrBlank() && diagSummary.contains("schema="))
    }

    @Test
    fun `voice note player cycles playback speed 1x 1_5x 2x and voice recorder cancels cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = VoiceNoteRecorder(context)
        recorder.cancelRecording()
        assertEquals(0f, recorder.liveLevel.value, 0.001f)

        composeRule.setContent {
            EchoTalkTheme {
                VoiceNotePlayer(
                    duration = "0:14",
                    waveform = listOf(0.4f, 0.8f, 0.6f, 0.9f),
                    isSentByMe = true,
                    audioSeed = "vn_test_1"
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1x").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_note_speed_button").performClick()
        composeRule.onNodeWithText("1.5x").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_note_speed_button").performClick()
        composeRule.onNodeWithText("2x").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_note_speed_button").performClick()
        composeRule.onNodeWithText("1x").assertIsDisplayed()
    }

    @Test
    fun `ios inspired material3 chat screen displays messages toggles attachment drawer and sends messages`() {
        val repo = EchoTalkRepository.getInstance()
        val target = repo.users.value.firstOrNull { it.isFriend } ?: repo.users.value.first()

        composeRule.setContent {
            EchoTalkTheme {
                ChatScreen(
                    repository = repo,
                    recipientId = target.id,
                    recipientName = target.name,
                    recipientAvatar = target.avatarUrl
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chat_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attachment_button").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_message_input").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_send_button").assertIsNotEnabled()

        // Attachment icon opens expandable drawer with Photo, Studio, Sketch, View-Once
        composeRule.onNodeWithTag("chat_attachment_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_attachment_drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attach_photo").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attach_studio").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attach_sketch").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attach_view_once").assertIsDisplayed()

        // Toggle attachment drawer closed and send a message
        composeRule.onNodeWithTag("chat_attachment_button").performClick()
        composeRule.waitForIdle()

        val uniqueText = "Hello iOS M3 ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("chat_message_input").performTextInput(uniqueText)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_send_button").assertIsEnabled()
        composeRule.onNodeWithTag("chat_send_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chat_messages_lazy_column").assertIsDisplayed()
        composeRule.onNodeWithText(uniqueText).assertIsDisplayed()
    }

    @Test
    fun `chess engine supports pawn promotion detection, underpromotion, material score, and country flag emojis`() {
        // Country flags map ISO codes cleanly and fall back to globe
        assertEquals("🇺🇸", ChessBoardManager.countryCodeToFlagEmoji("US"))
        assertEquals("🇯🇵", ChessBoardManager.countryCodeToFlagEmoji("jp"))
        assertEquals("🌍", ChessBoardManager.countryCodeToFlagEmoji("invalid"))

        // Starting material score is balanced 39 vs 39 (8P + 2N + 2B + 2R + 1Q)
        val startState = ChessBoardManager.parseFen(ChessBoardManager.STARTING_FEN)
        val (wStartMat, bStartMat) = ChessBoardManager.computeMaterialScore(startState.board)
        assertEquals(39, wStartMat)
        assertEquals(39, bStartMat)

        // Pawn promotion detection + Knight underpromotion
        val promoFen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val promoState = ChessBoardManager.parseFen(promoFen)
        assertTrue(ChessBoardManager.needsPawnPromotion(promoState, 1, 0, 0, 0))
        assertFalse(ChessBoardManager.needsPawnPromotion(promoState, 7, 4, 6, 4))

        val knightPromo = ChessBoardManager.executeMove(promoState, 1, 0, 0, 0, promotionPiece = "N")
        assertTrue(knightPromo != null)
        assertEquals("N", knightPromo!!.newState.board[0][0])
        assertEquals("a8=N", knightPromo.moveNotation)

        val (wPromoMat, bPromoMat) = ChessBoardManager.computeMaterialScore(knightPromo.newState.board)
        assertEquals(3, wPromoMat)
        assertEquals(0, bPromoMat)
    }

    @Test
    fun `grandmaster chess arena renders slate board, player flags, clocks, move log, and working floating pill controls`() {
        val repo = EchoTalkRepository.getInstance()

        composeRule.setContent {
            EchoTalkTheme {
                ChessGameView(
                    mode = "pass_and_play",
                    opponent = "Kenji",
                    repository = repo
                )
            }
        }
        composeRule.waitForIdle()

        // Full-width board + player HUDs with flags and 05:00 clocks
        composeRule.onNodeWithTag("tournament_chess_board").assertIsDisplayed()
        composeRule.onNodeWithTag("chess_black_player_hud").assertIsDisplayed()
        composeRule.onNodeWithTag("chess_black_player_flag").assertIsDisplayed()
        composeRule.onNodeWithTag("chess_black_player_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("chess_white_player_hud").assertIsDisplayed()
        composeRule.onNodeWithTag("chess_white_player_flag").assertIsDisplayed()
        composeRule.onNodeWithTag("chess_white_player_clock").assertIsDisplayed()

        composeRule.onNodeWithTag("chess_move_history_strip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("chess_bottom_control_pill").performScrollTo().assertIsDisplayed()

        // Play 1. e4 by tapping e2 (row 6, col 4) then e4 (row 4, col 4)
        composeRule.onNodeWithTag("chess_square_6_4").performScrollTo().performClick()
        composeRule.onNodeWithTag("chess_square_4_4").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Algebraic move notation appears in move history and last-move highlight updates
        composeRule.onNodeWithTag("last_move_square_4_4").assertExists()
        composeRule.onNodeWithTag("chess_move_history_strip").performScrollTo()
        composeRule.onNodeWithText("e4").assertIsDisplayed()

        // Flip board, trigger Hint, Undo, and Reset from minimalist floating pill
        composeRule.onNodeWithTag("chess_flip_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chess_hint_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chess_undo_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("White to move").assertIsDisplayed()

        composeRule.onNodeWithTag("chess_reset_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("White to move").assertIsDisplayed()
    }
}
