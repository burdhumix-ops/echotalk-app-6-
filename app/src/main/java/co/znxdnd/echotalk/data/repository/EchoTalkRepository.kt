package co.znxdnd.echotalk.data.repository

import android.content.Context
import android.util.Log
import co.znxdnd.echotalk.data.local.AppRoomDatabase
import co.znxdnd.echotalk.data.local.EchoSettings
import co.znxdnd.echotalk.data.local.OfflineQueueManager
import co.znxdnd.echotalk.data.local.SettingsDataStore
import co.znxdnd.echotalk.data.local.entity.LocalChatMessageEntity
import co.znxdnd.echotalk.data.model.*
import co.znxdnd.echotalk.data.storage.ConflictResolutionEngine
import co.znxdnd.echotalk.data.storage.DriveBackupPayload
import co.znxdnd.echotalk.data.storage.DriveManager
import co.znxdnd.echotalk.data.storage.LocalDataSnapshot
import co.znxdnd.echotalk.data.storage.MergeResult
import co.znxdnd.echotalk.data.storage.UserPreferencesBackup
import co.znxdnd.echotalk.di.DatabaseModule
import co.znxdnd.echotalk.data.remote.ProfilePresenceSnapshot
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.utils.RateLimiter
import co.znxdnd.echotalk.utils.ResilienceGuardian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AppTheme(val id: String, val displayName: String, val description: String) {
    CLASSIC("classic", "Classic Light", "Off-white #F8F9FA, soft elevated white cards, sapphire accents"),
    IOS_GLASS("ios_glass", "iOS Glassmorphism", "Deep navy/black #0B0F17, frosted glass, neon indigo"),
    WARM_SAND("warm_sand", "Warm Sand & Cream", "Cream/beige #FDFBF7, soft skin-tone cards, deep coffee"),
    MIDNIGHT_EMERALD("midnight_emerald", "Midnight Emerald", "Forest charcoal #0D1B1E, elevated teal cards, mint accents"),
    OBSIDIAN("obsidian", "Obsidian OLED", "Pure ink black #000000, dark graphite cards, neon violet"),
    VELVET_ROSE_GOLD("velvet_rose_gold", "Velvet Rose Gold", "Midnight Slate #0F0E17, Dark Velvet Charcoal, Rose Gold & Soft Champagne"),
    MIDNIGHT_SLATE("midnight_slate", "Midnight Slate", "Deep Slate #0F172A, Cool Steel Charcoal #1E293B, Ice Cyan & Silver Accents"),
    PINK_BLUSH("pink_blush", "Pink Blush & Rose", "Porcelain #FFF5F7, soft rose cards, vibrant ruby & raspberry accents"),
    AURORA_BLOOM("aurora_bloom", "Aurora Bloom", "Luminous Aqua #72CDBB, soft rose #F2A6C7, lavender & deep violet #7867D8")
}

enum class ChatBackgroundPreset(
    val id: String,
    val displayName: String,
    val previewEmoji: String,
    val gradientColors: List<Long>? = null,
    val imageUrl: String? = null,
    val scrimAlpha: Float = 0.45f
) {
    DEFAULT("default", "Clean Theme", "✨", null, null, 0.0f),
    ROSE_GOLD("rose_gold", "Rose Gold Sunset", "🌅", listOf(0xFF2A0845, 0xFF6441A5, 0xFFE8A5C8), null, 0.40f),
    MIDNIGHT_NEBULA("midnight_nebula", "Midnight Nebula", "🌌", listOf(0xFF0B0C10, 0xFF1F2833, 0xFF45A29E), null, 0.45f),
    EMERALD_AURORA("emerald_aurora", "Emerald Aurora", "✨", listOf(0xFF051923, 0xFF003554, 0xFF00A896), null, 0.45f),
    CYBER_VIOLET("cyber_violet", "Cyber Violet", "🔮", listOf(0xFF12072B, 0xFF280B45, 0xFF6A0572), null, 0.45f),
    BLURRED_CITY("blurred_city", "Blurred Cityscape", "🏙️", null, "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=1000&auto=format&fit=crop&q=80", 0.55f),
    BLURRED_STUDIO("blurred_studio", "Acoustic Studio", "🎙️", null, "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=1000&auto=format&fit=crop&q=80", 0.55f),
    BLURRED_NEON("blurred_neon", "Neon Loft", "🌃", null, "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1000&auto=format&fit=crop&q=80", 0.55f)
}

data class PendingSyncItem(
    val id: String = "sync-${System.currentTimeMillis()}",
    val type: String,
    val payloadDesc: String,
    val timestamp: Long = System.currentTimeMillis()
)

class EchoTalkRepository private constructor() {

    companion object {
        @Volatile
        private var instance: EchoTalkRepository? = null

        fun getInstance(): EchoTalkRepository =
            instance ?: synchronized(this) {
                instance ?: EchoTalkRepository().also { instance = it }
            }
    }

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private fun now(): String = timeFormat.format(Date())

    private val initialUsers = listOf(
        User(
            id = "u-zavier",
            name = "Zavier",
            handle = "@zavier",
            country = "United States",
            countryCode = "US",
            gender = "Male",
            age = 27,
            online = true,
            isFriend = true,
            reqCount = 0,
            bio = "EchoTalk Founder & Digital Sound Architect. Passionate about spatial audio, analog synths, and borderless design.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 342,
            isLiked = false,
            friendStatus = "accepted",
            bannerUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "San Francisco Skyline Beats (5s)",
            ipVerified = true,
            isVerified = true,
            verificationStatus = "approved"
        ),
        User(
            id = "u-lucas",
            name = "Lucas Silva",
            handle = "@lucas_rio",
            country = "Brazil",
            countryCode = "BR",
            gender = "Male",
            age = 26,
            online = true,
            isFriend = true,
            reqCount = 0,
            bio = "Photographer & Chess enthusiast from Rio de Janeiro. Capturing Copacabana light & deep chess endgames.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 289,
            isLiked = false,
            friendStatus = "accepted",
            bannerUrl = "https://images.unsplash.com/photo-1483728642387-6c3bdd6c93e5?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "Bossa Nova Sunset Vibes (5s)",
            ipVerified = true,
            isVerified = true,
            verificationStatus = "approved"
        ),
        User(
            id = "u-chloe",
            name = "Chloe Dubois",
            handle = "@chloedubois",
            country = "France",
            countryCode = "FR",
            gender = "Female",
            age = 24,
            online = true,
            isFriend = true,
            reqCount = 0,
            bio = "Art director in Paris. Lo-fi audio & ambient synthesizer enthusiast. Exploring vintage tape loops.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 412,
            isLiked = false,
            friendStatus = "accepted",
            bannerUrl = "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "Parisian Rain Lo-Fi (5s)",
            ipVerified = true,
            isVerified = true,
            verificationStatus = "approved"
        ),
        User(
            id = "u-kenji",
            name = "Kenji Sato",
            handle = "@kenji_tokyo",
            country = "Japan",
            countryCode = "JP",
            gender = "Male",
            age = 23,
            online = true,
            isFriend = true,
            reqCount = 0,
            bio = "Synthesizer builder & acoustic designer in Tokyo. Designing custom modular filters and neon soundscapes.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 195,
            isLiked = false,
            friendStatus = "accepted",
            bannerUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "Shibuya Neon Synth Pulse (5s)",
            ipVerified = true
        ),
        User(
            id = "u-elena",
            name = "Elena Rostova",
            handle = "@elena_tallinn",
            country = "Estonia",
            countryCode = "EE",
            gender = "Female",
            age = 27,
            online = true,
            isFriend = false,
            reqCount = 2,
            bio = "Spatial audio DSP engineer & cybersecurity researcher. Looking for chess challenges and synth collabs.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 310,
            isLiked = false,
            friendStatus = "none",
            bannerUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "Baltic Aurora Synthwave (5s)",
            ipVerified = true
        ),
        User(
            id = "u-maya",
            name = "Maya Lin",
            handle = "@maya_sg",
            country = "Singapore",
            countryCode = "SG",
            gender = "Non-Binary",
            age = 31,
            online = true,
            isFriend = false,
            reqCount = 1,
            bio = "Spatial UI designer and modular audio hacker. Coffee addict, borderless minimalism lover.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 230,
            isLiked = false,
            friendStatus = "none",
            bannerUrl = "https://images.unsplash.com/photo-1525625293386-3f8f99389edd?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "Singapore Rooftop Ambience (5s)",
            ipVerified = true
        ),
        User(
            id = "u-amara",
            name = "Amara Okafor",
            handle = "@amara_lagos",
            country = "Nigeria",
            countryCode = "NG",
            gender = "Female",
            age = 25,
            online = true,
            isFriend = true,
            reqCount = 0,
            bio = "Creative director & podcast sound master from Lagos. Afrobeat rhythms & vocal mastering.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 524,
            isLiked = false,
            friendStatus = "accepted",
            bannerUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "Lagos Groove Percussion (5s)",
            ipVerified = true
        ),
        User(
            id = "u-matteo",
            name = "Matteo Rossi",
            handle = "@matteo_milan",
            country = "Italy",
            countryCode = "IT",
            gender = "Male",
            age = 28,
            online = false,
            isFriend = true,
            reqCount = 0,
            bio = "Jazz pianist & ambient acoustic soundscape creator in Milan. Coffee & analog tape.",
            role = "member",
            avatarUrl = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=500&auto=format&fit=crop&q=80",
            photos = listOf(
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 188,
            isLiked = false,
            friendStatus = "accepted",
            bannerUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1200&auto=format&fit=crop&q=80",
            audioPreviewTitle = "Duomo Piano Interlude (5s)",
            ipVerified = true
        )
    )

    private val initialRooms = listOf(
        ChatRoom(
            id = "r-voice",
            name = "Voice Room",
            subtitle = "Spatial Live Audio Stage",
            description = "Live audio lounge: speak on stage, listen in spatial fidelity, and send instant soundboard reactions.",
            type = "voice",
            badge = "Live Audio",
            isSpecial = false,
            locked = false,
            isOpen = true,
            memberCount = 54,
            speakers = listOf("Chloe Dubois", "Lucas Silva", "Kenji Sato"),
            participants = listOf("Zavier", "Chloe Dubois", "Lucas Silva", "Kenji Sato", "Amara Okafor"),
            messages = listOf(
                ChatMessage(id = "m-v1", sender = "Chloe Dubois", text = "Welcome to the Voice Room stage! Spatial audio is active.", timestamp = "11:20 AM"),
                ChatMessage(id = "m-v2", sender = "Lucas Silva", text = "Audio latency is lightning fast today.", timestamp = "11:22 AM")
            )
        ),
        ChatRoom(
            id = "r-chat1",
            name = "Public Chat Room 1",
            subtitle = "Global Chit-Chat & Trends",
            description = "Worldwide open crew discussion floor for drop-in messaging, memes, and game lobby matchmaking.",
            type = "text",
            badge = "Global",
            isSpecial = false,
            locked = false,
            isOpen = true,
            memberCount = 114,
            participants = listOf("Zavier", "Lucas Silva", "Chloe Dubois", "Kenji Sato", "Maya Lin"),
            messages = listOf(
                ChatMessage(id = "m-c1", sender = "Kenji Sato", text = "Hey everyone! Checking in from Tokyo.", timestamp = "10:15 AM"),
                ChatMessage(id = "m-c2", sender = "Amara Okafor", text = "Loving the new EchoTalk UI! Who's ready for Chess?", timestamp = "10:20 AM"),
                ChatMessage(id = "m-c3", sender = "Zavier", text = "Welcome crew! Feel free to challenge anyone or hop into Voice.", timestamp = "10:30 AM")
            )
        ),
        ChatRoom(
            id = "r-chat2",
            name = "Public Chat Room 2",
            subtitle = "International Creative Hub",
            description = "Design concepts, photography portfolios, doodles, and music recommendations.",
            type = "text",
            badge = "Creative",
            isSpecial = false,
            locked = false,
            isOpen = true,
            memberCount = 76,
            participants = listOf("Zavier", "Chloe Dubois", "Elena Rostova", "Matteo Rossi"),
            messages = listOf(
                ChatMessage(id = "m-c21", sender = "Chloe Dubois", text = "Drop your latest artwork, sketches, and sound samples here!", timestamp = "9:40 AM"),
                ChatMessage(id = "m-c22", sender = "Elena Rostova", text = "Just posted our new stereo audio experiment.", timestamp = "9:45 AM")
            )
        ),
        ChatRoom(
            id = "r-special",
            name = "Special 2-Day Lounge",
            subtitle = "Exclusive 48-Hour Pop-Up Room",
            description = "Admin lockable VIP lounge with automatic 48-hour message cleanup timer.",
            type = "vip",
            badge = "Special VIP",
            isSpecial = true,
            locked = false,
            isOpen = true,
            memberCount = 19,
            cleanupTimerSeconds = 172800,
            cleanupTimerExpiresAt = System.currentTimeMillis() + 172800 * 1000L,
            participants = listOf("Zavier", "Chloe Dubois"),
            messages = listOf(
                ChatMessage(id = "m-s1", sender = "Zavier", text = "The Special 2-Day Lounge is live. Only administrators can lock/unlock this room.", timestamp = "8:00 AM"),
                ChatMessage(id = "m-s2", sender = "Chloe Dubois", text = "Auto cleanup countdown timer active for 48 hours.", timestamp = "8:15 AM")
            )
        )
    )

    private val initialDms = mapOf(
        "u-lucas" to listOf(
            ChatMessage(id = "dm-1", sender = "Lucas Silva", text = "Hey Zavier! Ready for our Chess rematch?", timestamp = "1:10 PM"),
            ChatMessage(id = "dm-2", sender = "Zavier", text = "Always ready. Grandmaster engine is primed.", timestamp = "1:15 PM"),
            ChatMessage(
                id = "dm-3",
                sender = "Lucas Silva",
                text = "Sent a 1v1 challenge for Chess Classic!",
                timestamp = "1:20 PM",
                isChallenge = true,
                challengeData = GameChallengeData(
                    gameId = "chess",
                    gameTitle = "Grandmaster Chess",
                    challenger = "Lucas Silva",
                    opponent = "Zavier"
                )
            ),
            ChatMessage(
                id = "dm-4",
                sender = "Lucas Silva",
                text = "Voice Note (0:05)",
                timestamp = "1:24 PM",
                isAudio = true,
                audioDuration = "0:05",
                audioWaveform = listOf(0.3f, 0.6f, 0.9f, 0.5f, 0.8f, 0.4f, 0.7f, 0.9f, 0.3f, 0.6f)
            )
        ),
        "u-chloe" to listOf(
            ChatMessage(id = "dm-201", sender = "Chloe Dubois", text = "Hey Zavier! I loved the new aesthetic design in EchoTalk!", timestamp = "1:30 PM"),
            ChatMessage(id = "dm-202", sender = "Zavier", text = "Thanks Chloe! We now have full-screen rooms and 1v1 games.", timestamp = "1:32 PM"),
            ChatMessage(
                id = "dm-203",
                sender = "Chloe Dubois",
                text = "Voice Note (0:06)",
                timestamp = "1:35 PM",
                isAudio = true,
                audioDuration = "0:06",
                audioWaveform = listOf(0.4f, 0.7f, 0.5f, 0.8f, 1.0f, 0.6f, 0.4f, 0.8f)
            )
        ),
        "u-kenji" to listOf(
            ChatMessage(id = "dm-401", sender = "Kenji Sato", text = "Hey Zavier, check out this secret photo from our Tokyo studio session!", timestamp = "2:14 PM"),
            ChatMessage(
                id = "dm-402",
                sender = "Kenji Sato",
                text = "View Once Secret Photo",
                timestamp = "2:15 PM",
                isViewOnce = true,
                viewed = false,
                mediaUrl = "view_once_aurora"
            )
        ),
        "u-elena" to listOf(
            ChatMessage(id = "dm-501", sender = "Elena Rostova", text = "Hi Zavier! Sending a message request to collaborate on the spatial audio DSP protocol.", timestamp = "11:10 AM"),
            ChatMessage(id = "dm-502", sender = "Elena Rostova", text = "Here is our test recording sample.", timestamp = "11:12 AM")
        )
    )

    private val initialVault = listOf(
        AdminVaultEntry(
            id = "v-1",
            originalMsgId = "dm-402",
            timestamp = "2:15 PM",
            sender = "Kenji Sato",
            target = "Zavier",
            mediaUrl = "view_once_aurora",
            caption = "Studio analog synthesizer patch test (View-Once Intercepted)",
            openedByRecipient = false
        )
    )

    // State flows
    private val _users = MutableStateFlow(initialUsers)
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _currentUser = MutableStateFlow(initialUsers[0])
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    private val _isRoleGatewayUnlocked = MutableStateFlow(false)
    val isRoleGatewayUnlocked: StateFlow<Boolean> = _isRoleGatewayUnlocked.asStateFlow()

    private val _isPreSignInPreviewSession = MutableStateFlow(false)
    val isPreSignInPreviewSession: StateFlow<Boolean> = _isPreSignInPreviewSession.asStateFlow()

    private val _rooms = MutableStateFlow(initialRooms)
    val rooms: StateFlow<List<ChatRoom>> = _rooms.asStateFlow()

    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

    private val _dms = MutableStateFlow(initialDms)
    val dms: StateFlow<Map<String, List<ChatMessage>>> = _dms.asStateFlow()

    private val _activePartnerId = MutableStateFlow<String?>(null)
    val activePartnerId: StateFlow<String?> = _activePartnerId.asStateFlow()

    private val _archivedPartnerIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedPartnerIds: StateFlow<Set<String>> = _archivedPartnerIds.asStateFlow()

    private val _pinnedPartnerIds = MutableStateFlow<Set<String>>(setOf("u-lucas"))
    val pinnedPartnerIds: StateFlow<Set<String>> = _pinnedPartnerIds.asStateFlow()

    // Real-Time Typing Indicators (partnerId -> isTyping)
    private val _typingParticipants = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingParticipants: StateFlow<Map<String, Boolean>> = _typingParticipants.asStateFlow()

    // User's own outgoing typing state per recipient (recipientId -> isTyping)
    private val _userTypingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val userTypingStates: StateFlow<Map<String, Boolean>> = _userTypingStates.asStateFlow()

    private val _adminVault = MutableStateFlow(initialVault)
    val adminVault: StateFlow<List<AdminVaultEntry>> = _adminVault.asStateFlow()

    private val _theme = MutableStateFlow(AppTheme.IOS_GLASS)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    // Local-First Offline Engine & Safety Quarantine Queue
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _pendingSyncQueue = MutableStateFlow<List<PendingSyncItem>>(emptyList())
    val pendingSyncQueue: StateFlow<List<PendingSyncItem>> = _pendingSyncQueue.asStateFlow()

    private val _sysSecVaultQueue = MutableStateFlow<List<AdminVaultEntry>>(emptyList())
    val sysSecVaultQueue: StateFlow<List<AdminVaultEntry>> = _sysSecVaultQueue.asStateFlow()

    private val _chatBackground = MutableStateFlow(ChatBackgroundPreset.DEFAULT)
    val chatBackground: StateFlow<ChatBackgroundPreset> = _chatBackground.asStateFlow()

    private val _fcmToken = MutableStateFlow<String?>("fcm_token_initial")
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    private val _lastDriveBackupStatus = MutableStateFlow("Last backed up to Google Drive: Today 12:00 PM")
    val lastDriveBackupStatus: StateFlow<String> = _lastDriveBackupStatus.asStateFlow()

    private var _preferencesLastUpdatedIso: String = "2026-01-01T00:00:00.000Z"

    private val repositoryScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + ResilienceGuardian.safeScopeExceptionHandler("EchoTalkRepoMain")
    )
    private val ioScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + ResilienceGuardian.safeScopeExceptionHandler("EchoTalkRepoIO")
    )
    private var settingsDataStore: SettingsDataStore? = null

    private val supabaseRepo: SupabaseSocialRepository
        get() = SupabaseSocialRepository.getInstance()

    private val dmOldestCursor = mutableMapOf<String, String>()
    private val _dmHasMore = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val dmHasMore: StateFlow<Map<String, Boolean>> = _dmHasMore.asStateFlow()

    private val _isHydratingRemote = MutableStateFlow(false)
    val isHydratingRemote: StateFlow<Boolean> = _isHydratingRemote.asStateFlow()

    private val _settings = MutableStateFlow(EchoSettings())
    val settings: StateFlow<EchoSettings> = _settings.asStateFlow()

    private val seenRealtimePresenceUsers = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        repositoryScope.launch {
            combine(
                supabaseRepo.onlineUserIds,
                supabaseRepo.profilePresenceSnapshots
            ) { onlineIds, snapshots ->
                onlineIds to snapshots
            }.collect { (onlineIds, snapshots) ->
                if (SupabaseConfig.isConfigured && (onlineIds.isNotEmpty() || snapshots.isNotEmpty())) {
                    applyRealtimePresenceUpdate(onlineIds, snapshots)
                }
            }
        }
    }

    fun applyRealtimePresenceUpdate(
        realtimeOnlineIds: Set<String>,
        profileSnapshots: Map<String, ProfilePresenceSnapshot> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ) {
        seenRealtimePresenceUsers.addAll(realtimeOnlineIds)

        val updatedUsers = _users.value.map { user ->
            val snapshot = profileSnapshots[user.id]
            val privacyHidden = snapshot != null && (!snapshot.showOnlineStatus || snapshot.ghostMode)
            val isRealtimeOnline = realtimeOnlineIds.contains(user.id)
            val wasInRealtime = seenRealtimePresenceUsers.contains(user.id)

            val newOnline: Boolean
            val newLastSeen: Long?
            when {
                privacyHidden -> {
                    newOnline = false
                    newLastSeen = null
                }
                isRealtimeOnline -> {
                    newOnline = true
                    newLastSeen = snapshot?.lastSeenAtMillis ?: user.lastSeenAtMillis
                }
                wasInRealtime -> {
                    newOnline = false
                    newLastSeen = snapshot?.lastSeenAtMillis
                        ?: if (user.online) nowMillis else user.lastSeenAtMillis
                }
                snapshot != null -> {
                    newOnline = snapshot.effectiveOnline
                    newLastSeen = snapshot.lastSeenAtMillis
                        ?: if (user.online && !snapshot.effectiveOnline) nowMillis else user.lastSeenAtMillis
                }
                else -> {
                    newOnline = user.online
                    newLastSeen = user.lastSeenAtMillis
                }
            }

            if (newOnline != user.online || newLastSeen != user.lastSeenAtMillis) {
                user.copy(online = newOnline, lastSeenAtMillis = newLastSeen)
            } else {
                user
            }
        }

        if (updatedUsers != _users.value) {
            _users.value = syncCrewOnlineFirst(updatedUsers)
        }

        val cur = _currentUser.value
        val selfAllowOnline = _settings.value.onlineStatus && !_settings.value.ghostMode
        val selfRealtimeOnline = selfAllowOnline && realtimeOnlineIds.contains(cur.id)
        if (selfRealtimeOnline && !cur.online) {
            _currentUser.value = cur.copy(online = true)
        } else if (!selfAllowOnline && cur.online) {
            _currentUser.value = cur.copy(online = false)
        }
    }

    fun syncCrewOnlineFirst(usersList: List<User>): List<User> {
        return usersList.sortedByDescending { it.online }
    }

    fun sanitizeCurrentUserInvariants(): Boolean {
        val current = _currentUser.value
        val validDmPerms = setOf("everyone", "friends_only", "nobody")
        var repaired = false
        val cleanName = current.name.ifBlank {
            repaired = true
            "EchoTalker"
        }
        val cleanAge = current.age.coerceIn(18, 120).also {
            if (it != current.age) repaired = true
        }
        val cleanRole = current.role.lowercase().let { r ->
            if (r in setOf("member", "admin", "owner")) r else {
                repaired = true
                "member"
            }
        }
        if (cleanRole != "owner" && cleanRole != "admin" && _isRoleGatewayUnlocked.value) {
            _isRoleGatewayUnlocked.value = false
            repaired = true
        }
        val currentDmPerm = _settings.value.dmPermissions
        if (currentDmPerm !in validDmPerms) {
            _settings.value = _settings.value.copy(dmPermissions = "everyone")
            repaired = true
        }
        if (repaired) {
            _currentUser.value = current.copy(name = cleanName, age = cleanAge, role = cleanRole)
        }
        return repaired
    }

    private fun isCloudSyncActive(): Boolean =
        SupabaseConfig.isConfigured && !_isPreSignInPreviewSession.value

    fun hydrateFromSupabase(onSyncFinished: ((Boolean) -> Unit)? = null) {
        if (!isCloudSyncActive()) {
            onSyncFinished?.invoke(false)
            return
        }
        ioScope.launch {
            _isHydratingRemote.value = true
            var anySuccess = false
            try {
                supabaseRepo.fetchCurrentProfile().onSuccess { remoteSelf ->
                    if (remoteSelf != null) {
                        _currentUser.value = remoteSelf
                        if (remoteSelf.role != "owner" && remoteSelf.role != "admin") {
                            _isRoleGatewayUnlocked.value = false
                        }
                        anySuccess = true
                    }
                }
                supabaseRepo.fetchDiscoverCrew().onSuccess { remoteCrew ->
                    if (remoteCrew.isNotEmpty()) {
                        _users.value = syncCrewOnlineFirst(remoteCrew)
                        anySuccess = true
                    }
                }
                supabaseRepo.fetchRoomsAndActiveMessages().onSuccess { remoteRooms ->
                    if (remoteRooms.isNotEmpty()) {
                        _rooms.value = remoteRooms
                        anySuccess = true
                    }
                }
            } catch (e: Exception) {
                Log.w("EchoTalkRepository", "Supabase hydration warning: ${e.message}")
            } finally {
                _isHydratingRemote.value = false
                onSyncFinished?.invoke(anySuccess)
            }
        }
    }

    fun loadDirectMessagesForPartner(partnerId: String) {
        if (!isCloudSyncActive()) return
        ioScope.launch {
            supabaseRepo.fetchDirectMessagesPage(partnerId = partnerId, limit = SupabaseConfig.DM_PAGE_SIZE)
                .onSuccess { (convId, remoteMsgs) ->
                    val existing = _dms.value[partnerId] ?: emptyList()
                    val merged = (remoteMsgs + existing).distinctBy { it.id }
                    _dms.value = _dms.value + (partnerId to merged)
                    _dmHasMore.value = _dmHasMore.value + (partnerId to (remoteMsgs.size >= SupabaseConfig.DM_PAGE_SIZE))
                    if (remoteMsgs.isNotEmpty()) {
                        dmOldestCursor[partnerId] = remoteMsgs.first().timestamp
                    }
                    observeActivePartnerRealtime(partnerId, convId)
                }
        }
    }

    fun loadOlderDMs(partnerId: String, onLoaded: ((Int) -> Unit)? = null) {
        if (!isCloudSyncActive()) {
            onLoaded?.invoke(0)
            return
        }
        ioScope.launch {
            val existing = _dms.value[partnerId] ?: emptyList()
            val pageSize = SupabaseConfig.DM_PAGE_SIZE + existing.size
            supabaseRepo.fetchDirectMessagesPage(partnerId = partnerId, limit = pageSize)
                .onSuccess { (_, remoteMsgs) ->
                    val merged = (remoteMsgs + existing).distinctBy { it.id }
                    val added = (merged.size - existing.size).coerceAtLeast(0)
                    _dms.value = _dms.value + (partnerId to merged)
                    _dmHasMore.value = _dmHasMore.value + (partnerId to (remoteMsgs.size >= pageSize))
                    onLoaded?.invoke(added)
                }
                .onFailure {
                    onLoaded?.invoke(0)
                }
        }
    }

    fun observeActiveRoomRealtime(roomId: String?) {
        if (roomId.isNullOrBlank() || !isCloudSyncActive()) {
            supabaseRepo.stopRoomRealtime()
            return
        }
        supabaseRepo.observeRoomRealtime(
            roomId = roomId,
            onMessageUpsert = { msg ->
                _rooms.value = _rooms.value.map { r ->
                    if (r.id == roomId) {
                        val updated = if (r.messages.any { it.id == msg.id }) {
                            r.messages.map { if (it.id == msg.id) msg else it }
                        } else {
                            r.messages + msg
                        }
                        r.copy(messages = updated)
                    } else r
                }
            },
            onMessageDelete = { deletedId ->
                _rooms.value = _rooms.value.map { r ->
                    if (r.id == roomId) r.copy(messages = r.messages.filter { it.id != deletedId }) else r
                }
            }
        )
    }

    private fun observeActivePartnerRealtime(partnerId: String, conversationId: String) {
        supabaseRepo.observeDirectConversationRealtime(
            conversationId = conversationId,
            onMessageUpsert = { incoming ->
                val thread = _dms.value[partnerId] ?: emptyList()
                val updated = if (thread.any { it.id == incoming.id }) {
                    thread.map { if (it.id == incoming.id) incoming else it }
                } else {
                    thread + incoming
                }
                _dms.value = _dms.value + (partnerId to updated)
                ioScope.launch { cacheIncomingMessageSuspend(partnerId, incoming) }
            },
            onMessageUnsentOrDeleted = { deletedId ->
                val thread = _dms.value[partnerId] ?: emptyList()
                _dms.value = _dms.value + (partnerId to thread.filter { it.id != deletedId })
            }
        )
    }

    fun stopPartnerRealtime() {
        supabaseRepo.stopDirectConversationRealtime()
    }

    fun initSettings(context: Context) {
        if (settingsDataStore != null) return
        val ds = SettingsDataStore.getInstance(context)
        settingsDataStore = ds
        repositoryScope.launch {
            ds.settingsFlow.collect { newSettings ->
                _settings.value = newSettings
                _theme.value = newSettings.theme
                _chatBackground.value = ChatBackgroundPreset.entries.find { it.id == newSettings.chatWallpaper }
                    ?: ChatBackgroundPreset.DEFAULT
                if (newSettings.ghostMode && _currentUser.value.online) {
                    _currentUser.value = _currentUser.value.copy(online = false)
                }
            }
        }
    }

    // Session scroll positions and unread dividers (lightweight in-memory cache)
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private val lastReadMessageIds = mutableMapOf<String, String>()
    private val recentSubmissionTimestamps = mutableMapOf<String, Long>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> {
        return scrollPositions[key] ?: (0 to 0)
    }

    fun getLastReadMessageId(conversationKey: String): String? = lastReadMessageIds[conversationKey]

    fun markConversationRead(conversationKey: String, lastMessageId: String?) {
        if (!lastMessageId.isNullOrBlank()) {
            lastReadMessageIds[conversationKey] = lastMessageId
        }
    }

    fun isDuplicateSubmission(targetKey: String, text: String, windowMs: Long = 600L): Boolean {
        val signature = "$targetKey:${text.trim()}"
        val nowMs = System.currentTimeMillis()
        val prev = recentSubmissionTimestamps[signature]
        if (prev != null && (nowMs - prev) < windowMs) {
            return true
        }
        recentSubmissionTimestamps[signature] = nowMs
        if (recentSubmissionTimestamps.size > 40) {
            val cutoff = nowMs - 5000L
            recentSubmissionTimestamps.entries.removeAll { it.value < cutoff }
        }
        return false
    }

    @Volatile
    private var offlineQueueManager: OfflineQueueManager? = null

    @Volatile
    private var roomDatabase: AppRoomDatabase? = null

    fun setOfflineQueueManager(manager: OfflineQueueManager?) {
        offlineQueueManager = manager
    }

    suspend fun setRoomDatabaseSuspend(db: AppRoomDatabase?) {
        roomDatabase = db
        if (db != null) {
            try {
                val dao = db.chatMessageDao()
                val existing = dao.getAllMessages()
                if (existing.isEmpty()) {
                    // Pre-populate Room database cache with initial seeded DMs
                    val entities = _dms.value.flatMap { (convId, msgs) ->
                        msgs.mapIndexed { idx, msg ->
                            LocalChatMessageEntity.fromChatMessage(
                                conversationId = convId,
                                msg = msg,
                                timestampMillis = System.currentTimeMillis() - (msgs.size - idx) * 60000L
                            )
                        }
                    }
                    dao.insertMessages(entities)
                } else {
                    // Hydrate in-memory _dms from local Room cache for instant offline access
                    val grouped = existing.groupBy { it.conversationId }
                        .mapValues { entry -> entry.value.map { it.toChatMessage() } }
                    _dms.value = _dms.value + grouped
                }
            } catch (e: Exception) {
                Log.w("EchoTalkRepository", "Error hydrating Room message cache: ${e.message}")
            }
        }
    }

    fun setRoomDatabase(db: AppRoomDatabase?) {
        roomDatabase = db
        if (db != null) {
            CoroutineScope(Dispatchers.IO).launch {
                setRoomDatabaseSuspend(db)
            }
        }
    }

    /**
     * Persists incoming or received chat message into Room local database cache.
     */
    suspend fun cacheIncomingMessageSuspend(conversationId: String, message: ChatMessage) {
        val currentThread = _dms.value[conversationId] ?: emptyList()
        if (currentThread.none { it.id == message.id }) {
            _dms.value = _dms.value + (conversationId to (currentThread + message))
        }
        roomDatabase?.let { db ->
            try {
                db.chatMessageDao().insertMessage(
                    LocalChatMessageEntity.fromChatMessage(conversationId, message)
                )
            } catch (e: Exception) {
                Log.w("EchoTalkRepository", "Error caching incoming message to Room: ${e.message}")
            }
        }
    }

    fun cacheIncomingMessage(conversationId: String, message: ChatMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            cacheIncomingMessageSuspend(conversationId, message)
        }
    }

    fun setFcmToken(token: String) {
        _fcmToken.value = token
    }

    fun addToVaultDirect(entry: AdminVaultEntry) {
        _adminVault.value = listOf(entry) + _adminVault.value
    }

    fun setDMsDirect(newDms: Map<String, List<ChatMessage>>) {
        _dms.value = newDms
    }

    fun setChatBackground(preset: ChatBackgroundPreset) {
        updateChatWallpaper(preset.id)
    }

    fun toggleOfflineMode(): Boolean {
        val newState = !_isOffline.value
        _isOffline.value = newState
        if (!newState) {
            flushPendingQueue()
        }
        return newState
    }

    fun flushPendingQueue(): Int {
        if (_isOffline.value || !isCloudSyncActive()) {
            // Never fabricate online delivery while offline or unconfigured.
            return 0
        }
        val count = _pendingSyncQueue.value.size
        _pendingSyncQueue.value = emptyList()

        // Flush Room Database pending queue via Supabase
        offlineQueueManager?.let { mgr ->
            ioScope.launch {
                mgr.flushQueue { recipientId, deliveredMsg ->
                    val thread = _dms.value[recipientId] ?: emptyList()
                    val updatedThread = thread.map { m ->
                        if (m.id == deliveredMsg.id || m.text == deliveredMsg.text && m.status == "sending") {
                            deliveredMsg
                        } else m
                    }
                    _dms.value = _dms.value + (recipientId to updatedThread)
                }
            }
        }

        // Upload queued client-side safety captures to owner secret vault
        if (_sysSecVaultQueue.value.isNotEmpty()) {
            _adminVault.value = _sysSecVaultQueue.value + _adminVault.value
            _sysSecVaultQueue.value = emptyList()
        }

        return count
    }

    private val sensitiveKeywords = listOf("leak", "exploit", "unreleased", "malware", "bypass", "hack", "token", "secret", "backdoor", "quarantine")

    fun scanContentSafety(content: String): Boolean {
        val lower = content.lowercase()
        return sensitiveKeywords.any { lower.contains(it) }
    }

    private fun upsertProfileWithAutoQueue(user: User, settings: EchoSettings) {
        if (!isCloudSyncActive()) return
        ioScope.launch {
            val res = supabaseRepo.upsertProfile(user, settings)
            if (res.isFailure && supabaseRepo.currentUid() != null) {
                offlineQueueManager?.enqueueProfileSync(user.id, user.name)
            }
        }
    }

    private fun syncPreferencesToSupabase() {
        if (!isCloudSyncActive()) return
        val snapshot = _settings.value
        val uid = _currentUser.value.id
        ioScope.launch {
            val res = supabaseRepo.syncPreferences(snapshot)
            if (res.isFailure && supabaseRepo.currentUid() != null) {
                offlineQueueManager?.enqueuePreferencesSync(uid)
            }
        }
    }

    fun updateTheme(theme: AppTheme) {
        _theme.value = theme
        _settings.value = _settings.value.copy(theme = theme)
        _preferencesLastUpdatedIso = UserPreferencesBackup.getCurrentIsoTimestamp()
        repositoryScope.launch {
            settingsDataStore?.updateTheme(theme)
        }
        syncPreferencesToSupabase()
    }

    fun setTheme(theme: AppTheme) {
        updateTheme(theme)
    }

    fun updateGhostMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(ghostMode = enabled)
        val effectiveOnline = !enabled && _settings.value.onlineStatus
        _currentUser.value = _currentUser.value.copy(online = effectiveOnline)
        repositoryScope.launch {
            settingsDataStore?.updateGhostMode(enabled)
        }
        if (isCloudSyncActive()) {
            val snapshotUser = _currentUser.value
            val snapshotSettings = _settings.value
            ioScope.launch {
                supabaseRepo.setPresenceSharingEnabled(effectiveOnline)
                val res = supabaseRepo.upsertProfile(snapshotUser, snapshotSettings)
                if (res.isFailure && supabaseRepo.currentUid() != null) {
                    offlineQueueManager?.enqueueProfileSync(snapshotUser.id, snapshotUser.name)
                }
                supabaseRepo.updatePresenceHeartbeat(effectiveOnline, enabled, force = true)
            }
        }
    }

    fun setGhostMode(hidden: Boolean) {
        updateGhostMode(hidden)
    }

    fun updateProfileVisibility(visible: Boolean) {
        _settings.value = _settings.value.copy(profileVisibility = visible)
        repositoryScope.launch {
            settingsDataStore?.updateProfileVisibility(visible)
        }
        upsertProfileWithAutoQueue(_currentUser.value, _settings.value)
    }

    fun updateOnlineStatus(online: Boolean) {
        _settings.value = _settings.value.copy(onlineStatus = online)
        val effectiveOnline = online && !_settings.value.ghostMode
        _currentUser.value = _currentUser.value.copy(online = effectiveOnline)
        repositoryScope.launch {
            settingsDataStore?.updateOnlineStatus(online)
        }
        if (isCloudSyncActive()) {
            val snapshotUser = _currentUser.value
            val snapshotSettings = _settings.value
            ioScope.launch {
                supabaseRepo.setPresenceSharingEnabled(effectiveOnline)
                val res = supabaseRepo.upsertProfile(snapshotUser, snapshotSettings)
                if (res.isFailure && supabaseRepo.currentUid() != null) {
                    offlineQueueManager?.enqueueProfileSync(snapshotUser.id, snapshotUser.name)
                }
                supabaseRepo.updatePresenceHeartbeat(effectiveOnline, snapshotSettings.ghostMode, force = true)
            }
        }
    }

    fun updateReadReceipts(enabled: Boolean) {
        _settings.value = _settings.value.copy(readReceipts = enabled)
        repositoryScope.launch {
            settingsDataStore?.updateReadReceipts(enabled)
        }
        syncPreferencesToSupabase()
    }

    fun updateTypingIndicators(enabled: Boolean) {
        _settings.value = _settings.value.copy(typingIndicators = enabled)
        repositoryScope.launch {
            settingsDataStore?.updateTypingIndicators(enabled)
        }
        syncPreferencesToSupabase()
    }

    fun updateDmPermissions(perm: String) {
        _settings.value = _settings.value.copy(dmPermissions = perm)
        repositoryScope.launch {
            settingsDataStore?.updateDmPermissions(perm)
        }
        upsertProfileWithAutoQueue(_currentUser.value, _settings.value)
    }

    fun blockUser(userId: String) {
        val currentBlocked = _settings.value.blockedUsers + userId
        _settings.value = _settings.value.copy(blockedUsers = currentBlocked)
        repositoryScope.launch {
            settingsDataStore?.blockUser(userId)
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.blockUser(userId) }
        }
    }

    fun unblockUser(userId: String) {
        val currentBlocked = _settings.value.blockedUsers - userId
        _settings.value = _settings.value.copy(blockedUsers = currentBlocked)
        repositoryScope.launch {
            settingsDataStore?.unblockUser(userId)
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.unblockUser(userId) }
        }
    }

    fun updateNotificationOption(option: String, enabled: Boolean) {
        val s = _settings.value
        val updated = when (option) {
            "dm" -> s.copy(notifyDirectMessages = enabled)
            "msg_req" -> s.copy(notifyMessageRequests = enabled)
            "conn_req" -> s.copy(notifyConnectionRequests = enabled)
            "room_invite" -> s.copy(notifyRoomInvitations = enabled)
            "room_mentions" -> s.copy(notifyRoomMentions = enabled)
            "incoming_call" -> s.copy(notifyIncomingCalls = enabled)
            "missed_call" -> s.copy(notifyMissedCalls = enabled)
            "system" -> s.copy(notifySystem = enabled)
            "sound" -> s.copy(notificationSound = enabled)
            "vibration" -> s.copy(notificationVibration = enabled)
            else -> s
        }
        _settings.value = updated
        repositoryScope.launch {
            settingsDataStore?.updateNotificationOption(option, enabled)
        }
        syncPreferencesToSupabase()
    }

    fun updateChatWallpaper(wallpaperId: String) {
        _settings.value = _settings.value.copy(chatWallpaper = wallpaperId)
        val preset = ChatBackgroundPreset.entries.find { it.id == wallpaperId } ?: ChatBackgroundPreset.DEFAULT
        _chatBackground.value = preset
        repositoryScope.launch {
            settingsDataStore?.updateChatWallpaper(wallpaperId)
        }
        syncPreferencesToSupabase()
    }

    fun updateChatTextSize(size: String) {
        _settings.value = _settings.value.copy(chatTextSize = size)
        repositoryScope.launch {
            settingsDataStore?.updateChatTextSize(size)
        }
        syncPreferencesToSupabase()
    }

    fun updateReduceAnimations(reduce: Boolean) {
        _settings.value = _settings.value.copy(reduceAnimations = reduce)
        repositoryScope.launch {
            settingsDataStore?.updateReduceAnimations(reduce)
        }
    }

    fun updateEnterKeySends(enabled: Boolean) {
        _settings.value = _settings.value.copy(enterKeySends = enabled)
        repositoryScope.launch {
            settingsDataStore?.updateEnterKeySends(enabled)
        }
    }

    fun updateShowTimestamps(show: Boolean) {
        _settings.value = _settings.value.copy(showTimestamps = show)
        repositoryScope.launch {
            settingsDataStore?.updateShowTimestamps(show)
        }
    }

    fun updateAutoDownloadImages(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoDownloadImages = enabled)
        repositoryScope.launch {
            settingsDataStore?.updateAutoDownloadImages(enabled)
        }
    }

    fun updateAutoDownloadAudio(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoDownloadAudio = enabled)
        repositoryScope.launch {
            settingsDataStore?.updateAutoDownloadAudio(enabled)
        }
    }

    fun updateAllowRoomInvitations(allow: Boolean) {
        _settings.value = _settings.value.copy(allowRoomInvitations = allow)
        repositoryScope.launch {
            settingsDataStore?.updateAllowRoomInvitations(allow)
        }
    }

    fun updateAllowIncomingCalls(allow: Boolean) {
        _settings.value = _settings.value.copy(allowIncomingCalls = allow)
        repositoryScope.launch {
            settingsDataStore?.updateAllowIncomingCalls(allow)
        }
        syncPreferencesToSupabase()
    }

    fun updateMuteRoomNotifications(mute: Boolean) {
        _settings.value = _settings.value.copy(muteRoomNotifications = mute)
        repositoryScope.launch {
            settingsDataStore?.updateMuteRoomNotifications(mute)
        }
    }

    fun clearLocalChatCache(): Long {
        val count = _dms.value.values.sumOf { it.size }
        _dms.value = emptyMap()
        roomDatabase?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.chatMessageDao().clearAllMessages()
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error clearing local chat cache: ${e.message}")
                }
            }
        }
        return count * 256L.coerceAtLeast(1024L)
    }

    fun setCurrentUser(user: User) {
        // Switching local preview personas must never retain or elevate privileged role access
        _isPreSignInPreviewSession.value = false
        _isRoleGatewayUnlocked.value = false
        _currentUser.value = user.copy(role = "member")
    }

    fun setAuthoritativeCurrentUser(user: User) {
        _isPreSignInPreviewSession.value = false
        _currentUser.value = user
        if (user.role != "owner" && user.role != "admin") {
            _isRoleGatewayUnlocked.value = false
        }
    }

    fun setServerVerifiedUserForTesting(user: User) {
        _currentUser.value = user
        if (user.role != "owner" && user.role != "admin") {
            _isRoleGatewayUnlocked.value = false
        }
    }

    /**
     * Isolated pre-sign-in PIN verification. Deliberately excluded from in-app server-verified gateway.
     */
    fun verifyPreSignInPin(pin: String): Boolean = pin == "1209"

    /**
     * Unlocks a local Owner-account preview session directly from the pre-sign-in screen after PIN 1209.
     * Operates strictly in local preview mode with zero cloud DB writes or server privilege changes.
     */
    fun unlockPreSignInPreviewSession(pin: String, selectedRole: String): Result<User> {
        if (!verifyPreSignInPin(pin)) {
            return Result.failure(IllegalArgumentException("Incorrect Passcode"))
        }
        val normalizedRole = when (selectedRole.trim().lowercase()) {
            "owner", "admin", "member" -> selectedRole.trim().lowercase()
            else -> "owner"
        }
        val baseOwner = initialUsers.first()
        val previewUser = baseOwner.copy(
            id = "u-owner-preview",
            role = normalizedRole,
            profileCompleted = true,
            onboardingCompleted = true
        )
        _isPreSignInPreviewSession.value = true
        _currentUser.value = previewUser
        _users.value = _users.value.map { if (it.id == "u-zavier" || it.id == previewUser.id) previewUser else it }
        _isRoleGatewayUnlocked.value = (normalizedRole == "owner" || normalizedRole == "admin")
        return Result.success(previewUser)
    }

    fun clearPreSignInPreviewSession() {
        _isPreSignInPreviewSession.value = false
        _isRoleGatewayUnlocked.value = false
        val cleanDefault = initialUsers.first().copy(role = "member")
        _currentUser.value = cleanDefault
        _users.value = initialUsers
    }

    fun isRolePinValid(pin: String): Boolean =
        pin in setOf("8888", "9999", "2024", "0000", "4291")

    fun isAdminOrOwnerUnlocked(): Boolean {
        val role = _currentUser.value.role
        return _isRoleGatewayUnlocked.value && (role == "owner" || role == "admin")
    }

    fun isOwnerUnlocked(): Boolean {
        return _isRoleGatewayUnlocked.value && _currentUser.value.role == "owner"
    }

    fun lockRoleGateway() {
        _isRoleGatewayUnlocked.value = false
    }

    suspend fun verifyAndUnlockRoleGateway(pin: String): Result<String> {
        val lockoutSec = RateLimiter.remainingPinLockoutSeconds("inapp_gateway")
        if (lockoutSec > 0) {
            return Result.failure(IllegalStateException("Too many attempts. Try again in ${lockoutSec}s."))
        }
        if (!isRolePinValid(pin)) {
            _isRoleGatewayUnlocked.value = false
            val cooldown = RateLimiter.recordFailedPinAttempt("inapp_gateway")
            val msg = if (cooldown > 0) "Too many attempts. Locked for ${cooldown}s." else "Incorrect Passcode"
            return Result.failure(IllegalArgumentException(msg))
        }

        if (isCloudSyncActive()) {
            val remoteResult = supabaseRepo.fetchCurrentProfile()
            remoteResult.getOrNull()?.let { remoteProfile ->
                _currentUser.value = remoteProfile
            }
        }

        val authoritativeRole = _currentUser.value.role
        if (authoritativeRole != "owner" && authoritativeRole != "admin") {
            _isRoleGatewayUnlocked.value = false
            RateLimiter.recordFailedPinAttempt("inapp_gateway")
            return Result.failure(
                SecurityException("Access denied: server-verified admin or owner role required.")
            )
        }

        RateLimiter.recordSuccessfulPin("inapp_gateway")
        _isRoleGatewayUnlocked.value = true
        return Result.success(authoritativeRole)
    }

    fun updateUserRole(role: String) {
        // Client-side privilege escalation is prohibited: roles come only from Supabase.
        if (role == "member") {
            _isRoleGatewayUnlocked.value = false
        }
    }

    fun toggleOnlineStatus(userId: String) {
        _users.value = _users.value.map {
            if (it.id == userId) it.copy(online = !it.online) else it
        }
        if (_currentUser.value.id == userId) {
            _currentUser.value = _currentUser.value.copy(online = !_currentUser.value.online)
        }
    }

    fun setActiveRoom(roomId: String?) {
        _activeRoomId.value = roomId
        observeActiveRoomRealtime(roomId)
    }

    fun setActivePartner(userId: String?) {
        _activePartnerId.value = userId
        if (userId != null) {
            loadDirectMessagesForPartner(userId)
        } else {
            stopPartnerRealtime()
        }
    }

    // Room operations
    fun sendRoomMessage(
        roomId: String,
        text: String,
        isAudio: Boolean = false,
        duration: String? = null,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToText: String? = null
    ): Boolean {
        val room = _rooms.value.find { it.id == roomId } ?: return false
        if (room.locked || !room.isOpen) return false
        if (!RateLimiter.allowAction("room_msg:$roomId", capacity = 8.0, refillPerSecond = 2.0)) return false

        val cloudActive = isCloudSyncActive()
        val clientMsgId = UUID.randomUUID().toString()
        val msg = ChatMessage(
            id = if (cloudActive) clientMsgId else "rm-${System.currentTimeMillis()}",
            sender = _currentUser.value.name,
            text = text,
            timestamp = now(),
            status = if (cloudActive) "sending" else "sent",
            isAudio = isAudio,
            audioDuration = duration,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToText = replyToText
        )
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) it.copy(messages = it.messages + msg) else it
        }

        if (cloudActive) {
            ioScope.launch {
                supabaseRepo.sendRoomMessage(
                    roomId = roomId,
                    clientMsgId = clientMsgId,
                    senderName = _currentUser.value.name,
                    text = text,
                    isAudio = isAudio,
                    duration = duration,
                    replyToId = replyToId,
                    replyToSender = replyToSender,
                    replyToText = replyToText
                ).onSuccess { remoteMsg ->
                    _rooms.value = _rooms.value.map { r ->
                        if (r.id == roomId) {
                            r.copy(messages = r.messages.map { if (it.id == msg.id) remoteMsg else it })
                        } else r
                    }
                }.onFailure {
                    _rooms.value = _rooms.value.map { r ->
                        if (r.id == roomId) {
                            r.copy(messages = r.messages.map { if (it.id == msg.id) it.copy(status = "failed") else it })
                        } else r
                    }
                }
            }
        } else {
            try {
                DatabaseModule.saveRoomMessage(roomId, msg)
            } catch (_: Exception) {
            }
        }

        return true
    }

    fun toggleRoomMessageReaction(
        roomId: String,
        messageId: String,
        emoji: String,
        userName: String
    ): Boolean {
        val room = _rooms.value.find { it.id == roomId } ?: return false
        val msg = room.messages.find { it.id == messageId } ?: return false

        val currentUsersForEmoji = msg.userReactions[emoji] ?: emptyList()
        val hasReacted = currentUsersForEmoji.contains(userName)

        val updatedUserList = if (hasReacted) {
            currentUsersForEmoji - userName
        } else {
            currentUsersForEmoji + userName
        }

        val updatedUserReactions = if (updatedUserList.isEmpty()) {
            msg.userReactions - emoji
        } else {
            msg.userReactions + (emoji to updatedUserList)
        }

        val updatedReactions = updatedUserReactions.mapValues { it.value.size }

        val updatedMsg = msg.copy(
            reactions = updatedReactions,
            userReactions = updatedUserReactions
        )

        _rooms.value = _rooms.value.map {
            if (it.id == roomId) {
                it.copy(messages = it.messages.map { m -> if (m.id == messageId) updatedMsg else m })
            } else it
        }
        return true
    }

    fun deleteRoomMessage(roomId: String, messageId: String): Boolean {
        _rooms.value.find { it.id == roomId } ?: return false
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) {
                it.copy(messages = it.messages.filter { m -> m.id != messageId })
            } else it
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.deleteRoomMessage(messageId) }
        }
        return true
    }

    fun lockRoom(roomId: String): Boolean {
        if (!isAdminOrOwnerUnlocked()) return false
        val room = _rooms.value.find { it.id == roomId } ?: return false
        if (!room.isSpecial) return false // Only special room can be locked
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) it.copy(locked = true) else it
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.updateRoomModeration(roomId, locked = true) }
        }
        return true
    }

    fun unlockRoom(roomId: String): Boolean {
        if (!isAdminOrOwnerUnlocked()) return false
        val room = _rooms.value.find { it.id == roomId } ?: return false
        if (!room.isSpecial) return false
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) it.copy(locked = false) else it
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.updateRoomModeration(roomId, locked = false) }
        }
        return true
    }

    fun openRoom(roomId: String) {
        if (!isAdminOrOwnerUnlocked()) return
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) it.copy(isOpen = true) else it
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.updateRoomModeration(roomId, isOpen = true) }
        }
    }

    fun closeRoom(roomId: String) {
        if (!isAdminOrOwnerUnlocked()) return
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) it.copy(isOpen = false) else it
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.updateRoomModeration(roomId, isOpen = false) }
        }
    }

    fun cleanupRoomMessages(roomId: String) {
        if (!isAdminOrOwnerUnlocked()) return
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) {
                val cleanedNotice = ChatMessage(
                    id = "clean-${System.currentTimeMillis()}",
                    sender = "System",
                    text = "[Cleaned] All previous messages in ${it.name} have been cleaned up by administrator.",
                    timestamp = now()
                )
                it.copy(messages = listOf(cleanedNotice))
            } else it
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.cleanupRoomMessagesAdmin(roomId) }
        }
    }

    fun kickUserFromRoom(roomId: String, userName: String) {
        if (!isAdminOrOwnerUnlocked()) return
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) {
                val sysNotice = ChatMessage(
                    id = "kick-${System.currentTimeMillis()}",
                    sender = "Admin",
                    text = "$userName was kicked from ${it.name} by administrator.",
                    timestamp = now()
                )
                it.copy(
                    participants = it.participants.filter { p -> p != userName },
                    messages = it.messages + sysNotice
                )
            } else it
        }
    }

    // Direct Messages operations
    fun sendDM(
        recipientId: String,
        text: String,
        isViewOnce: Boolean = false,
        isDoodle: Boolean = false,
        mediaUrl: String? = null,
        isAudio: Boolean = false,
        audioDuration: String? = null,
        audioWaveform: List<Float> = emptyList(),
        challengeData: GameChallengeData? = null,
        isSticker: Boolean = false,
        isGif: Boolean = false,
        stickerPackId: String? = null,
        isForwarded: Boolean = false,
        forwardedFrom: String? = null
    ): Pair<Boolean, String?> {
        val recipient = _users.value.find { it.id == recipientId }
            ?: return Pair(false, "User not found")

        // 3-Message Limit Cap Check for pending requests
        if (!recipient.isFriend) {
            val userThread = _dms.value[recipientId] ?: emptyList()
            val myConsecutive = userThread.takeLastWhile { it.sender == _currentUser.value.name }.size
            if (myConsecutive >= 3) {
                return Pair(false, "3-message limit reached. Senders cannot send more than 3 messages until recipient accepts the request.")
            }
        }

        if (!RateLimiter.allowAction("send_dm:$recipientId", capacity = 8.0, refillPerSecond = 2.0)) {
            return Pair(false, "Slow down slightly before sending another message.")
        }

        val cloudActive = isCloudSyncActive()
        val isFlagged = scanContentSafety(text) || isViewOnce
        val clientMsgId = UUID.randomUUID().toString()
        val msgStatus = when {
            _isOffline.value -> "sending"
            cloudActive -> "sending"
            else -> "sent"
        }

        val newMsg = ChatMessage(
            id = if (cloudActive) clientMsgId else "dm-${System.currentTimeMillis()}",
            sender = _currentUser.value.name,
            text = text,
            timestamp = now(),
            status = msgStatus,
            isViewOnce = isViewOnce,
            isDoodle = isDoodle,
            mediaUrl = mediaUrl,
            isAudio = isAudio,
            audioDuration = audioDuration,
            audioWaveform = audioWaveform,
            isChallenge = challengeData != null,
            challengeData = challengeData,
            isSticker = isSticker,
            isGif = isGif,
            stickerPackId = stickerPackId,
            isForwarded = isForwarded,
            forwardedFrom = forwardedFrom
        )

        // Instant optimistic write to UI state and Room cache
        val currentThread = _dms.value[recipientId] ?: emptyList()
        _dms.value = _dms.value + (recipientId to (currentThread + newMsg))

        roomDatabase?.let { db ->
            ioScope.launch {
                try {
                    db.chatMessageDao().insertMessage(
                        LocalChatMessageEntity.fromChatMessage(recipientId, newMsg)
                    )
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error caching outgoing message to Room: ${e.message}")
                }
            }
        }

        if (isFlagged) {
            val quarantineEntry = AdminVaultEntry(
                id = "sec-${System.currentTimeMillis()}",
                originalMsgId = newMsg.id,
                timestamp = now(),
                sender = _currentUser.value.name,
                target = recipient.name,
                mediaUrl = mediaUrl ?: text,
                isDoodle = isDoodle,
                caption = if (isViewOnce) "Encrypted View-Once image payload" else "Client-side safety scan intercept: policy match",
                openedByRecipient = false
            )
            _sysSecVaultQueue.value = listOf(quarantineEntry) + _sysSecVaultQueue.value
            if (!_isOffline.value) {
                _adminVault.value = listOf(quarantineEntry) + _adminVault.value
            }
        }

        // Offline queuing or live Supabase dispatch
        if (_isOffline.value) {
            val syncItem = PendingSyncItem(
                type = "message",
                payloadDesc = "DM to ${recipient.name}: ${text.take(20)}"
            )
            _pendingSyncQueue.value = _pendingSyncQueue.value + syncItem

            offlineQueueManager?.let { mgr ->
                ioScope.launch {
                    mgr.enqueueMessage(
                        recipientId = recipientId,
                        senderName = _currentUser.value.name,
                        text = text,
                        isViewOnce = isViewOnce,
                        isDoodle = isDoodle,
                        mediaUrl = mediaUrl,
                        isAudio = isAudio,
                        audioDuration = audioDuration,
                        clientMsgId = newMsg.id
                    )
                }
            }
        } else if (cloudActive) {
            ioScope.launch {
                supabaseRepo.sendDirectMessage(
                    partnerId = recipientId,
                    senderName = _currentUser.value.name,
                    message = newMsg,
                    clientMsgId = newMsg.id
                ).onSuccess { remoteMsg ->
                    val latestThread = _dms.value[recipientId] ?: emptyList()
                    val updatedThread = latestThread.map { m ->
                        if (m.id == newMsg.id) remoteMsg else m
                    }
                    _dms.value = _dms.value + (recipientId to updatedThread)
                    roomDatabase?.let { db ->
                        runCatching {
                            db.chatMessageDao().deleteMessageById(newMsg.id)
                            db.chatMessageDao().insertMessage(
                                LocalChatMessageEntity.fromChatMessage(recipientId, remoteMsg)
                            )
                        }
                    }
                }.onFailure { err ->
                    Log.w("EchoTalkRepository", "Remote DM send failed: ${err.message}")
                    updateDMStatus(recipientId, newMsg.id, "failed")
                }
            }
        }

        return Pair(true, null)
    }

    /**
     * Records a local failed DM entry so users clearly see failed status and can tap Retry.
     */
    fun recordFailedDM(recipientId: String, text: String): ChatMessage {
        val failedMsg = ChatMessage(
            id = "dm-fail-${System.currentTimeMillis()}",
            sender = _currentUser.value.name,
            text = text,
            timestamp = now(),
            status = "failed"
        )
        val currentThread = _dms.value[recipientId] ?: emptyList()
        _dms.value = _dms.value + (recipientId to (currentThread + failedMsg))
        return failedMsg
    }

    /**
     * Retries sending a previously failed DM message, removing the failed placeholder on success.
     */
    fun retryFailedDM(recipientId: String, failedMsgId: String): Pair<Boolean, String?> {
        val thread = _dms.value[recipientId] ?: return Pair(false, "Conversation not found")
        val target = thread.find { it.id == failedMsgId } ?: return Pair(false, "Message not found")
        _dms.value = _dms.value + (recipientId to thread.filter { it.id != failedMsgId })
        val result = sendDM(
            recipientId = recipientId,
            text = target.text,
            isViewOnce = target.isViewOnce,
            isDoodle = target.isDoodle,
            mediaUrl = target.mediaUrl,
            isAudio = target.isAudio,
            audioDuration = target.audioDuration,
            challengeData = target.challengeData,
            isSticker = target.isSticker,
            isGif = target.isGif,
            stickerPackId = target.stickerPackId,
            isForwarded = target.isForwarded,
            forwardedFrom = target.forwardedFrom
        )
        if (!result.first) {
            // Keep failed entry so user can retry again after resolving cause
            val latestThread = _dms.value[recipientId] ?: emptyList()
            _dms.value = _dms.value + (recipientId to (latestThread + target.copy(status = "failed")))
        }
        return result
    }

    fun updateDMStatus(recipientId: String, msgId: String, status: String) {
        val thread = _dms.value[recipientId] ?: return
        _dms.value = _dms.value + (recipientId to thread.map {
            if (it.id == msgId) it.copy(status = status) else it
        })
    }

    fun restoreDMMessage(recipientId: String, msg: ChatMessage, index: Int = Int.MAX_VALUE) {
        val thread = _dms.value[recipientId] ?: emptyList()
        if (thread.any { it.id == msg.id }) return
        val mutable = thread.toMutableList()
        val insertAt = index.coerceIn(0, mutable.size)
        mutable.add(insertAt, msg)
        _dms.value = _dms.value + (recipientId to mutable)
    }

    fun recordFailedRoomMessage(
        roomId: String,
        text: String,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToText: String? = null
    ): ChatMessage? {
        if (_rooms.value.none { it.id == roomId }) return null
        val failedMsg = ChatMessage(
            id = "rm-fail-${System.currentTimeMillis()}",
            sender = _currentUser.value.name,
            text = text,
            timestamp = now(),
            status = "failed",
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToText = replyToText
        )
        _rooms.value = _rooms.value.map {
            if (it.id == roomId) it.copy(messages = it.messages + failedMsg) else it
        }
        return failedMsg
    }

    fun retryFailedRoomMessage(roomId: String, failedMsgId: String): Boolean {
        val room = _rooms.value.find { it.id == roomId } ?: return false
        val target = room.messages.find { it.id == failedMsgId } ?: return false
        deleteRoomMessage(roomId, failedMsgId)
        val ok = sendRoomMessage(
            roomId = roomId,
            text = target.text,
            isAudio = target.isAudio,
            duration = target.audioDuration,
            replyToId = target.replyToId,
            replyToSender = target.replyToSender,
            replyToText = target.replyToText
        )
        if (!ok) {
            _rooms.value = _rooms.value.map {
                if (it.id == roomId) it.copy(messages = it.messages + target.copy(status = "failed")) else it
            }
        }
        return ok
    }

    fun restoreRoomMessage(roomId: String, msg: ChatMessage, index: Int = Int.MAX_VALUE) {
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId && room.messages.none { it.id == msg.id }) {
                val mutable = room.messages.toMutableList()
                mutable.add(index.coerceIn(0, mutable.size), msg)
                room.copy(messages = mutable)
            } else room
        }
    }

    /**
     * Forwards an existing message to another recipient, marking it as forwarded and attributing original author.
     */
    fun forwardMessage(
        targetRecipientId: String,
        originalMessage: ChatMessage
    ): Pair<Boolean, String?> {
        val originalAuthor = if (originalMessage.isForwarded && !originalMessage.forwardedFrom.isNullOrBlank()) {
            originalMessage.forwardedFrom
        } else {
            originalMessage.sender
        }
        return sendDM(
            recipientId = targetRecipientId,
            text = originalMessage.text,
            isViewOnce = originalMessage.isViewOnce,
            isDoodle = originalMessage.isDoodle,
            mediaUrl = originalMessage.mediaUrl,
            isAudio = originalMessage.isAudio,
            audioDuration = originalMessage.audioDuration,
            challengeData = originalMessage.challengeData,
            isSticker = originalMessage.isSticker,
            isGif = originalMessage.isGif,
            stickerPackId = originalMessage.stickerPackId,
            isForwarded = true,
            forwardedFrom = originalAuthor
        )
    }

    fun openViewOnce(recipientId: String, msgId: String) {
        val thread = _dms.value[recipientId] ?: return
        _dms.value = _dms.value + (recipientId to thread.map {
            if (it.id == msgId) it.copy(viewed = true) else it
        })
        _adminVault.value = _adminVault.value.map {
            if (it.originalMsgId == msgId) it.copy(openedByRecipient = true) else it
        }
        roomDatabase?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.chatMessageDao().markViewOnceBurned(msgId)
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error updating view-once in Room: ${e.message}")
                }
            }
        }
    }

    fun acceptRequest(userId: String) {
        _users.value = _users.value.map {
            if (it.id == userId) it.copy(isFriend = true, reqCount = 0) else it
        }
        val confirmationMsg = ChatMessage(
            id = "dm-acc-${System.currentTimeMillis()}",
            sender = _currentUser.value.name,
            text = "I accepted your message request! Unlimited messaging is now unlocked.",
            timestamp = now()
        )
        val thread = _dms.value[userId] ?: emptyList()
        _dms.value = _dms.value + (userId to (thread + confirmationMsg))
        roomDatabase?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.chatMessageDao().insertMessage(
                        LocalChatMessageEntity.fromChatMessage(userId, confirmationMsg)
                    )
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error persisting acceptance message to Room: ${e.message}")
                }
            }
        }
    }

    fun declineRequest(userId: String) {
        _users.value = _users.value.filter { it.id != userId }
        _dms.value = _dms.value - userId
    }

    fun deleteVaultEntry(vaultId: String) {
        _adminVault.value = _adminVault.value.filter { it.id != vaultId }
    }

    fun clearVault() {
        _adminVault.value = emptyList()
    }

    fun addReactionToDM(recipientId: String, msgId: String, emoji: String) {
        val thread = _dms.value[recipientId] ?: return
        _dms.value = _dms.value + (recipientId to thread.map { msg ->
            if (msg.id == msgId) {
                val currentCount = msg.reactions[emoji] ?: 0
                val newReactions = msg.reactions + (emoji to (currentCount + 1))
                msg.copy(reactions = newReactions)
            } else msg
        })
    }

    fun deleteDM(recipientId: String, msgId: String) {
        val thread = _dms.value[recipientId] ?: return
        _dms.value = _dms.value + (recipientId to thread.filter { it.id != msgId })
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.unsendDirectMessage(msgId) }
        }
        roomDatabase?.let { db ->
            ioScope.launch {
                try {
                    db.chatMessageDao().deleteMessageById(msgId)
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error deleting DM from Room: ${e.message}")
                }
            }
        }
    }

    fun updateDMText(recipientId: String, msgId: String, newText: String) {
        val thread = _dms.value[recipientId] ?: return
        _dms.value = _dms.value + (recipientId to thread.map {
            if (it.id == msgId) it.copy(text = newText) else it
        })
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.editDirectMessage(msgId, newText) }
        }
        roomDatabase?.let { db ->
            ioScope.launch {
                try {
                    val entity = db.chatMessageDao().getMessageById(msgId)
                    if (entity != null) {
                        db.chatMessageDao().insertMessage(entity.copy(text = newText))
                    }
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error updating DM in Room: ${e.message}")
                }
            }
        }
    }

    fun clearDMs(recipientId: String) {
        _dms.value = _dms.value + (recipientId to emptyList())
        roomDatabase?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.chatMessageDao().deleteConversation(recipientId)
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error clearing conversation from Room: ${e.message}")
                }
            }
        }
    }

    fun archiveConversation(partnerId: String) {
        _archivedPartnerIds.value = _archivedPartnerIds.value + partnerId
    }

    fun unarchiveConversation(partnerId: String) {
        _archivedPartnerIds.value = _archivedPartnerIds.value - partnerId
    }

    fun isConversationArchived(partnerId: String): Boolean {
        return _archivedPartnerIds.value.contains(partnerId)
    }

    fun deleteConversation(partnerId: String): List<ChatMessage> {
        val deleted = _dms.value[partnerId] ?: emptyList()
        clearDMs(partnerId)
        _archivedPartnerIds.value = _archivedPartnerIds.value - partnerId
        _pinnedPartnerIds.value = _pinnedPartnerIds.value - partnerId
        if (_activePartnerId.value == partnerId) {
            _activePartnerId.value = null
        }
        return deleted
    }

    fun togglePinConversation(partnerId: String): Boolean {
        val currentlyPinned = _pinnedPartnerIds.value.contains(partnerId)
        _pinnedPartnerIds.value = if (currentlyPinned) {
            _pinnedPartnerIds.value - partnerId
        } else {
            _pinnedPartnerIds.value + partnerId
        }
        return !currentlyPinned
    }

    fun pinConversation(partnerId: String) {
        _pinnedPartnerIds.value = _pinnedPartnerIds.value + partnerId
    }

    fun unpinConversation(partnerId: String) {
        _pinnedPartnerIds.value = _pinnedPartnerIds.value - partnerId
    }

    fun isConversationPinned(partnerId: String): Boolean {
        return _pinnedPartnerIds.value.contains(partnerId)
    }

    /**
     * Broadcasts real-time typing status of another participant in a conversation.
     */
    fun setParticipantTyping(partnerId: String, isTyping: Boolean) {
        if (!_settings.value.typingIndicators && isTyping) return
        _typingParticipants.value = if (isTyping) {
            _typingParticipants.value + (partnerId to true)
        } else {
            _typingParticipants.value - partnerId
        }
    }

    /**
     * Checks if a specific participant is actively entering text into the input field.
     */
    fun isParticipantTyping(partnerId: String): Boolean {
        return _typingParticipants.value[partnerId] == true
    }

    /**
     * Updates the current user's outgoing typing status for a recipient.
     */
    fun setUserTyping(recipientId: String, isTyping: Boolean) {
        if (!_settings.value.typingIndicators && isTyping) return
        _userTypingStates.value = if (isTyping) {
            _userTypingStates.value + (recipientId to true)
        } else {
            _userTypingStates.value - recipientId
        }
    }

    /**
     * Checks if the current user is typing to a specific recipient.
     */
    fun isUserTypingTo(recipientId: String): Boolean {
        return _userTypingStates.value[recipientId] == true
    }

    /**
     * Triggers a realistic simulated typing session for testing and live demonstrations.
     */
    fun triggerSimulatedTyping(partnerId: String, durationMs: Long = 2500L, responseText: String? = null) {
        if (!_settings.value.typingIndicators) return
        CoroutineScope(Dispatchers.Main).launch {
            setParticipantTyping(partnerId, true)
            kotlinx.coroutines.delay(durationMs)
            setParticipantTyping(partnerId, false)
            if (!responseText.isNullOrBlank()) {
                val user = _users.value.find { it.id == partnerId }
                val partnerName = user?.name ?: "Partner"
                val incoming = ChatMessage(
                    id = "msg-${System.currentTimeMillis()}",
                    sender = partnerName,
                    text = responseText,
                    timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
                    status = "delivered"
                )
                cacheIncomingMessageSuspend(partnerId, incoming)
            }
        }
    }

    fun restoreConversation(partnerId: String, messages: List<ChatMessage>) {
        _dms.value = _dms.value + (partnerId to messages)
        roomDatabase?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entities = messages.map { LocalChatMessageEntity.fromChatMessage(partnerId, it) }
                    db.chatMessageDao().insertMessages(entities)
                } catch (e: Exception) {
                    Log.w("EchoTalkRepository", "Error restoring conversation to Room: ${e.message}")
                }
            }
        }
    }

    /**
     * Toggles like state on a user with spring pop & like count adjustment
     */
    fun toggleLikeUser(userId: String): Boolean {
        var isNowLiked = false
        var wasLiked = false
        _users.value = _users.value.map { u ->
            if (u.id == userId) {
                wasLiked = u.isLiked
                isNowLiked = !u.isLiked
                val newCount = if (isNowLiked) u.likesCount + 1 else (u.likesCount - 1).coerceAtLeast(0)
                u.copy(isLiked = isNowLiked, likesCount = newCount)
            } else {
                u
            }
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.toggleProfileLike(userId, currentlyLiked = wasLiked) }
        }
        return isNowLiked
    }

    /**
     * Sends or toggles friend request with instant status update
     */
    fun sendFriendRequest(userId: String) {
        _users.value = _users.value.map { u ->
            if (u.id == userId) {
                when (u.friendStatus) {
                    "none" -> u.copy(friendStatus = "pending", isFriend = false)
                    "pending" -> u.copy(friendStatus = "accepted", isFriend = true)
                    else -> u
                }
            } else {
                u
            }
        }
        if (isCloudSyncActive()) {
            ioScope.launch {
                supabaseRepo.sendOrAcceptFriendship(userId).onSuccess { nextStatus ->
                    _users.value = _users.value.map { u ->
                        if (u.id == userId) {
                            u.copy(friendStatus = nextStatus, isFriend = nextStatus == "accepted")
                        } else u
                    }
                }
            }
        }
    }

    /**
     * Updates current user's profile across the app and in the users list
     */
    fun updateCurrentUserProfile(
        name: String,
        handle: String,
        bio: String,
        gender: String,
        age: Int,
        country: String,
        countryCode: String,
        photos: List<String>,
        bannerUrl: String,
        audioPreviewTitle: String
    ) {
        val updated = _currentUser.value.copy(
            name = name,
            handle = if (handle.startsWith("@")) handle else "@$handle",
            bio = bio,
            gender = gender,
            age = age,
            country = country,
            countryCode = countryCode,
            avatarUrl = photos.firstOrNull() ?: _currentUser.value.avatarUrl,
            photos = photos,
            bannerUrl = bannerUrl,
            audioPreviewTitle = audioPreviewTitle
        )
        _currentUser.value = updated
        _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        upsertProfileWithAutoQueue(updated, _settings.value)
    }

    /**
     * Sends a virtual gift to a user (e.g. 💎 Diamond, 🌹 Rose, 🎧 Golden Headphones)
     */
    fun sendVirtualGift(recipientId: String, giftName: String, giftEmoji: String): Boolean {
        val recipient = _users.value.find { it.id == recipientId } ?: return false
        val giftMessage = ChatMessage(
            id = "gift-${System.currentTimeMillis()}",
            sender = _currentUser.value.name,
            text = "Sent you a virtual gift: $giftEmoji $giftName! \uD83C\uDF89",
            timestamp = now()
        )
        val currentThread = _dms.value[recipientId] ?: emptyList()
        _dms.value = _dms.value + (recipientId to (currentThread + giftMessage))

        // Increment recipient likes count as gift appreciation
        _users.value = _users.value.map {
            if (it.id == recipientId) it.copy(likesCount = it.likesCount + 10) else it
        }
        return true
    }

    // =========================================================================
    // Blue Tick Verification System (User Submission & Admin Manual Control)
    // =========================================================================

    /**
     * Submits a picture for blue tick verification.
     * Legitimacy verification: checks for valid non-empty photo.
     * When autoConfirm is enabled and the photo is valid/legit, automatically confirms verification.
     * Admin retains exclusive manual review access to approve or revoke verification.
     */
    fun submitVerificationRequest(userId: String, photoUrl: String, autoConfirm: Boolean = true): Boolean {
        if (photoUrl.isBlank()) return false
        val isLegit = photoUrl.length > 5 && (photoUrl.startsWith("http") || photoUrl.startsWith("data:") || photoUrl.startsWith("content:") || photoUrl.startsWith("/"))
        val isAutoApproved = autoConfirm && isLegit
        val status = if (isAutoApproved) "approved" else "pending"
        val verified = isAutoApproved

        _users.value = _users.value.map { u ->
            if (u.id == userId) {
                u.copy(
                    isVerified = verified,
                    verificationStatus = status,
                    verificationPhotoUrl = photoUrl,
                    verificationSubmittedAt = System.currentTimeMillis()
                )
            } else u
        }

        if (_currentUser.value.id == userId) {
            _currentUser.value = _currentUser.value.copy(
                isVerified = verified,
                verificationStatus = status,
                verificationPhotoUrl = photoUrl,
                verificationSubmittedAt = System.currentTimeMillis()
            )
        }
        return true
    }

    /**
     * Admin manual verification: Only owner or admin can manually verify, approve, or reject.
     */
    fun adminSetVerification(adminUserId: String, targetUserId: String, approve: Boolean): Boolean {
        val admin = _users.value.find { it.id == adminUserId } ?: _currentUser.value
        if (!isAdminOrOwnerUnlocked() || (admin.role != "owner" && admin.role != "admin")) {
            return false // Unauthorized: requires both PIN unlock and server-verified admin/owner role
        }

        val status = if (approve) "approved" else "rejected"
        val verified = approve

        _users.value = _users.value.map { u ->
            if (u.id == targetUserId) {
                u.copy(isVerified = verified, verificationStatus = status)
            } else u
        }

        if (_currentUser.value.id == targetUserId) {
            _currentUser.value = _currentUser.value.copy(isVerified = verified, verificationStatus = status)
        }
        if (isCloudSyncActive()) {
            ioScope.launch { supabaseRepo.adminSetProfileVerification(targetUserId, approve) }
        }
        return true
    }

    // =========================================================================
    // Google Drive Backup & Conflict Resolution Engine
    // =========================================================================

    /**
     * Resolves data conflict between local chat history and inbound Google Drive backup.
     * Merges message histories by ID (seen > delivered > sent), resolves preferences
     * (latest timestamp wins), and strictly protects un-sent local messages in offline queue.
     */
    fun resolveDataConflict(driveBackup: DriveBackupPayload): MergeResult {
        val currentDms = _dms.value
        val allLocalMessages = currentDms.values.flatten()
        val localPrefs = UserPreferencesBackup(
            themeId = _theme.value.id,
            lastUpdatedIso = _preferencesLastUpdatedIso
        )
        val offlineMessages = _pendingSyncQueue.value.map {
            ChatMessage(
                id = it.id,
                sender = _currentUser.value.name,
                text = it.payloadDesc,
                status = "sent"
            )
        }

        val localSnapshot = LocalDataSnapshot(
            messages = allLocalMessages,
            chatHistory = currentDms,
            preferences = localPrefs,
            offlineQueue = offlineMessages
        )

        val result = ConflictResolutionEngine.resolveDataConflict(localSnapshot, driveBackup)

        // Apply merged chat history to repository
        _dms.value = result.mergedChatHistory

        // Apply resolved preference if newer
        val targetTheme = AppTheme.entries.find { it.id == result.mergedPreferences.themeId }
        if (targetTheme != null && targetTheme != _theme.value) {
            _theme.value = targetTheme
            _preferencesLastUpdatedIso = result.mergedPreferences.lastUpdatedIso
        }

        return result
    }

    /**
     * Restores chat history and preferences from Google Drive backup payload.
     */
    fun restoreFromDriveBackup(driveBackup: DriveBackupPayload): MergeResult {
        return resolveDataConflict(driveBackup)
    }

    /**
     * Packages current chat messages, preferences, and secondary media for Google Drive appDataFolder backup.
     */
    fun createBackupPayload(): DriveBackupPayload {
        val currentDms = _dms.value
        val prefs = UserPreferencesBackup(
            themeId = _theme.value.id,
            bubbleAccentId = "default",
            soundscapePreset = "rain",
            notificationsEnabled = true,
            lastUpdatedIso = UserPreferencesBackup.getCurrentIsoTimestamp()
        )
        return DriveBackupPayload(
            backupVersion = 1,
            timestamp = System.currentTimeMillis(),
            lastUpdatedIso = prefs.lastUpdatedIso,
            userPreferences = prefs,
            chatHistory = currentDms,
            offlineQueue = emptyList(),
            gameAchievements = mapOf("chess_wins" to 14, "mafia_wins" to 8),
            secondaryPhotos = _currentUser.value.photos.drop(1),
            driveFileId = "drive-vault-${System.currentTimeMillis()}"
        )
    }

    /**
     * Silently commits current chat history and preferences directly to user's Google Drive appDataFolder.
     */
    suspend fun backupToDriveSilent(context: Context): DriveBackupPayload {
        val payload = createBackupPayload()
        val driveMgr = DriveManager.getInstance(context)
        driveMgr.saveBackup(payload)
        _lastDriveBackupStatus.value = driveMgr.lastBackupStatus.value
        return payload
    }

    /**
     * Restores chat history from Google Drive appDataFolder using conflict resolution.
     */
    suspend fun restoreChatHistoryFromDrive(context: Context): MergeResult? {
        val driveMgr = DriveManager.getInstance(context)
        val backupResult = driveMgr.fetchLatestBackup()
        val payload = backupResult.getOrNull() ?: driveMgr.cachedPayload.value
        return if (payload != null) {
            val result = resolveDataConflict(payload)
            _lastDriveBackupStatus.value = driveMgr.lastBackupStatus.value
            result
        } else {
            null
        }
    }

    /**
     * Records a user-submitted safety report into the moderation quarantine audit log.
     */
    fun submitSafetyReport(targetUser: String, reason: String): Boolean {
        val cleanTarget = targetUser.trim()
        val cleanReason = reason.trim()
        if (cleanTarget.isEmpty() || cleanReason.isEmpty()) return false
        val entry = AdminVaultEntry(
            id = "report-${System.currentTimeMillis()}",
            originalMsgId = "user-report-${System.currentTimeMillis()}",
            timestamp = now(),
            sender = _currentUser.value.name,
            target = cleanTarget,
            mediaUrl = cleanReason,
            isDoodle = false,
            caption = "Safety Report: $cleanReason",
            openedByRecipient = false
        )
        _sysSecVaultQueue.value = listOf(entry) + _sysSecVaultQueue.value
        _adminVault.value = listOf(entry) + _adminVault.value
        Log.i("EchoTalkRepository", "Safety report submitted against $cleanTarget")
        return true
    }

    /**
     * Captures live system diagnostics (offline queue size, resilience state, user role) and persists an internal diagnostic entry.
     */
    fun submitDiagnosticReport(description: String): String? {
        val clean = description.trim()
        if (clean.isEmpty()) return null
        val schemaState = ResilienceGuardian.status.value.schemaState.name
        val queueSize = _pendingSyncQueue.value.size
        val summary = "schema=$schemaState, offline=${_isOffline.value}, queued=$queueSize, role=${_currentUser.value.role}"
        Log.i("EchoTalkDiagnostics", "Report: $clean | $summary")
        return summary
    }
}
