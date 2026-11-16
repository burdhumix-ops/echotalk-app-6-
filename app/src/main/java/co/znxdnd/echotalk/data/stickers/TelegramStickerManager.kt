package co.znxdnd.echotalk.data.stickers

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Domain model representing an individual Telegram sticker.
 */
data class TelegramStickerItem(
    val id: String,
    val packId: String,
    val emoji: String,
    val label: String,
    val mediaUrl: String,
    val visualCode: String
)

/**
 * Domain model representing a Telegram sticker package.
 */
data class TelegramStickerPack(
    val id: String,
    val name: String,
    val author: String,
    val iconEmoji: String,
    val stickers: List<TelegramStickerItem>,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 1.0f,
    val sizeKb: Int = 380
)

/**
 * Domain model representing a Telegram curated GIF item.
 */
data class TelegramGifItem(
    val id: String,
    val title: String,
    val category: String, // "Trending", "Reactions", "Gaming", "Laughter", "Celebration"
    val mediaUrl: String,
    val previewEmoji: String
)

/**
 * TelegramStickerManager:
 * Manages curated Telegram sticker packages, GIF catalogs, and local in-app offline storage.
 * Follows Telegram-style one-tap quick-send, offline downloads, and library caching.
 */
class TelegramStickerManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _downloadedPackIds = MutableStateFlow<Set<String>>(loadDownloadedPacks())
    val downloadedPackIds: StateFlow<Set<String>> = _downloadedPackIds.asStateFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private fun loadDownloadedPacks(): Set<String> {
        val saved = prefs.getStringSet(KEY_DOWNLOADED_PACKS, null)
        // Duck Classics is pre-installed offline by default
        return saved ?: setOf("duck_classics")
    }

    private fun saveDownloadedPacks(packIds: Set<String>) {
        prefs.edit().putStringSet(KEY_DOWNLOADED_PACKS, packIds).apply()
        _downloadedPackIds.value = packIds
    }

    fun isPackDownloaded(packId: String): Boolean {
        return _downloadedPackIds.value.contains(packId)
    }

    /**
     * Downloads a sticker pack into the in-app offline sticker library.
     * Simulates network chunk transfer with progress callbacks and writes
     * offline cache manifest files in app-private storage.
     */
    suspend fun downloadPack(packId: String, onProgress: ((Float) -> Unit)? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _downloadProgressMap.value = _downloadProgressMap.value + (packId to 0.1f)
                onProgress?.invoke(0.1f)
                delay(120)

                _downloadProgressMap.value = _downloadProgressMap.value + (packId to 0.35f)
                onProgress?.invoke(0.35f)
                delay(150)

                _downloadProgressMap.value = _downloadProgressMap.value + (packId to 0.70f)
                onProgress?.invoke(0.70f)
                delay(120)

                // Write metadata marker to app internal storage cache
                val stickersDir = File(context.filesDir, "telegram_stickers/$packId")
                if (!stickersDir.exists()) {
                    stickersDir.mkdirs()
                }
                File(stickersDir, "manifest.json").writeText(
                    """{"packId":"$packId","installedAt":${System.currentTimeMillis()},"version":1}"""
                )

                _downloadProgressMap.value = _downloadProgressMap.value + (packId to 1.0f)
                onProgress?.invoke(1.0f)
                delay(60)

                val updated = _downloadedPackIds.value + packId
                saveDownloadedPacks(updated)

                val currentProgress = _downloadProgressMap.value.toMutableMap()
                currentProgress.remove(packId)
                _downloadProgressMap.value = currentProgress

                true
            } catch (e: Exception) {
                val currentProgress = _downloadProgressMap.value.toMutableMap()
                currentProgress.remove(packId)
                _downloadProgressMap.value = currentProgress
                false
            }
        }
    }

    fun deleteDownloadedPack(packId: String) {
        if (packId == "duck_classics") return // keep default pack
        val updated = _downloadedPackIds.value - packId
        saveDownloadedPacks(updated)

        try {
            val stickersDir = File(context.filesDir, "telegram_stickers/$packId")
            if (stickersDir.exists()) {
                stickersDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    /**
     * Retrieves all curated Telegram sticker packages with updated download status.
     */
    fun getAllPacks(): List<TelegramStickerPack> {
        val downloaded = _downloadedPackIds.value
        val progressMap = _downloadProgressMap.value
        return CURATED_PACKS.map { pack ->
            val isDl = downloaded.contains(pack.id)
            val prog = progressMap[pack.id] ?: if (isDl) 1.0f else 0.0f
            pack.copy(
                isDownloaded = isDl,
                downloadProgress = prog
            )
        }
    }

    /**
     * Retrieves only currently downloaded offline packs.
     */
    fun getInstalledPacks(): List<TelegramStickerPack> {
        return getAllPacks().filter { it.isDownloaded }
    }

    /**
     * Retrieves curated Telegram looping GIFs by category.
     */
    fun getCuratedGifs(category: String? = null): List<TelegramGifItem> {
        return if (category.isNullOrBlank() || category.equals("All", ignoreCase = true) || category.equals("Trending", ignoreCase = true)) {
            CURATED_GIFS
        } else {
            CURATED_GIFS.filter { it.category.equals(category, ignoreCase = true) }
        }
    }

    companion object {
        private const val PREFS_NAME = "echotalk_telegram_stickers"
        private const val KEY_DOWNLOADED_PACKS = "installed_sticker_packs"

        @Volatile
        private var instance: TelegramStickerManager? = null

        fun getInstance(context: Context): TelegramStickerManager {
            return instance ?: synchronized(this) {
                instance ?: TelegramStickerManager(context.applicationContext).also { instance = it }
            }
        }

        val CURATED_PACKS = listOf(
            TelegramStickerPack(
                id = "duck_classics",
                name = "Duck Classics",
                author = "Telegram Official",
                iconEmoji = "🦆",
                sizeKb = 320,
                stickers = listOf(
                    TelegramStickerItem("duck_cool", "duck_classics", "🦆", "Cool Duck", "https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=200", "DUCK_COOL"),
                    TelegramStickerItem("duck_wave", "duck_classics", "👋", "Waving Duck", "https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=200", "DUCK_WAVE"),
                    TelegramStickerItem("duck_heart", "duck_classics", "❤️", "Heart Duck", "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=200", "DUCK_HEART"),
                    TelegramStickerItem("duck_shock", "duck_classics", "😱", "Mindblown Duck", "https://images.unsplash.com/photo-1555685812-4b943f1cb0eb?w=200", "DUCK_SHOCK"),
                    TelegramStickerItem("duck_coffee", "duck_classics", "☕", "Morning Coffee", "https://images.unsplash.com/photo-1517256064527-09c73fc73e38?w=200", "DUCK_COFFEE"),
                    TelegramStickerItem("duck_sleep", "duck_classics", "😴", "Sleeping Duck", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=200", "DUCK_SLEEP"),
                    TelegramStickerItem("duck_party", "duck_classics", "🎉", "Party Duck", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=200", "DUCK_PARTY"),
                    TelegramStickerItem("duck_flex", "duck_classics", "💪", "Muscular Duck", "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=200", "DUCK_FLEX")
                )
            ),
            TelegramStickerPack(
                id = "cyber_crew",
                name = "Cyber Echo Crew",
                author = "EchoTalk Labs",
                iconEmoji = "🤖",
                sizeKb = 450,
                stickers = listOf(
                    TelegramStickerItem("cyber_bot", "cyber_crew", "🤖", "Neon Bot", "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=200", "CYBER_BOT"),
                    TelegramStickerItem("cyber_cat", "cyber_crew", "🐱", "Glitch Kitty", "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=200", "CYBER_CAT"),
                    TelegramStickerItem("cyber_highfive", "cyber_crew", "✋", "Holo High-Five", "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=200", "CYBER_HIGHFIVE"),
                    TelegramStickerItem("cyber_matrix", "cyber_crew", "💾", "Matrix Data", "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=200", "CYBER_MATRIX"),
                    TelegramStickerItem("cyber_laser", "cyber_crew", "⚡", "Laser Eyes", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=200", "CYBER_LASER"),
                    TelegramStickerItem("cyber_rocket", "cyber_crew", "🚀", "To The Moon", "https://images.unsplash.com/photo-1517976487507-5b3b4a4d4506?w=200", "CYBER_ROCKET"),
                    TelegramStickerItem("cyber_heart", "cyber_crew", "💜", "Neon Violet Heart", "https://images.unsplash.com/photo-1518895949257-7621c3c786d7?w=200", "CYBER_HEART"),
                    TelegramStickerItem("cyber_shield", "cyber_crew", "🛡️", "Encrypted Shield", "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=200", "CYBER_SHIELD")
                )
            ),
            TelegramStickerPack(
                id = "meme_express",
                name = "Meme Express",
                author = "Community Viral",
                iconEmoji = "🔥",
                sizeKb = 410,
                stickers = listOf(
                    TelegramStickerItem("meme_fine", "meme_express", "🔥", "This Is Fine", "https://images.unsplash.com/photo-1587300003388-59208cc962cb?w=200", "MEME_FINE"),
                    TelegramStickerItem("meme_deal", "meme_express", "🕶️", "Deal With It", "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=200", "MEME_DEAL"),
                    TelegramStickerItem("meme_popcorn", "meme_express", "🍿", "Popcorn Time", "https://images.unsplash.com/photo-1585647347483-22b66260dfff?w=200", "MEME_POPCORN"),
                    TelegramStickerItem("meme_brain", "meme_express", "🧠", "Galaxy Brain", "https://images.unsplash.com/photo-1507413245164-6160d8298b31?w=200", "MEME_BRAIN"),
                    TelegramStickerItem("meme_stonks", "meme_express", "📈", "Stonks Up", "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=200", "MEME_STONKS"),
                    TelegramStickerItem("meme_cat_shock", "meme_express", "🙀", "Cat Gasp", "https://images.unsplash.com/photo-1533738363-b7f9aef128ce?w=200", "MEME_CAT_SHOCK"),
                    TelegramStickerItem("meme_facepalm", "meme_express", "🤦", "Classic Facepalm", "https://images.unsplash.com/photo-1544717305-2782549b5136?w=200", "MEME_FACEPALM"),
                    TelegramStickerItem("meme_hundred", "meme_express", "💯", "100 Percent Pure", "https://images.unsplash.com/photo-1513151233558-d860c5398176?w=200", "MEME_HUNDRED")
                )
            ),
            TelegramStickerPack(
                id = "pixel_animals",
                name = "Pixel Animals",
                author = "Retro Pixel Art",
                iconEmoji = "🦊",
                sizeKb = 290,
                stickers = listOf(
                    TelegramStickerItem("pixel_fox", "pixel_animals", "🦊", "Pixel Fox", "https://images.unsplash.com/photo-1474511320723-9a56873867b5?w=200", "PIXEL_FOX"),
                    TelegramStickerItem("pixel_panda", "pixel_animals", "🐼", "Gamer Panda", "https://images.unsplash.com/photo-1564349683136-77e08dba1ef7?w=200", "PIXEL_PANDA"),
                    TelegramStickerItem("pixel_frog", "pixel_animals", "🐸", "Chill Frog", "https://images.unsplash.com/photo-1579380656108-f98e4df8ea62?w=200", "PIXEL_FROG"),
                    TelegramStickerItem("pixel_doge", "pixel_animals", "🐶", "Happy Doge", "https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?w=200", "PIXEL_DOGE"),
                    TelegramStickerItem("pixel_unicorn", "pixel_animals", "🦄", "Cosmic Horn", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=200", "PIXEL_UNICORN"),
                    TelegramStickerItem("pixel_sloth", "pixel_animals", "🦥", "Slow & Steady", "https://images.unsplash.com/photo-1502082553048-f009c37129b9?w=200", "PIXEL_SLOTH"),
                    TelegramStickerItem("pixel_owl", "pixel_animals", "🦉", "Midnight Owl", "https://images.unsplash.com/photo-1516467508483-a7212febe31a?w=200", "PIXEL_OWL"),
                    TelegramStickerItem("pixel_hamster", "pixel_animals", "🐹", "Speed Runner", "https://images.unsplash.com/photo-1425082661705-1834bfd09dca?w=200", "PIXEL_HAMSTER")
                )
            )
        )

        val CURATED_GIFS = listOf(
            TelegramGifItem("gif_dance_vibe", "Dancing Cat Vibe", "Reactions", "https://images.unsplash.com/photo-1543852786-1cf6624b9987?w=400", "🕺"),
            TelegramGifItem("gif_popcorn_gasp", "Dramatic Popcorn", "Reactions", "https://images.unsplash.com/photo-1578849278619-e73505e9610f?w=400", "🍿"),
            TelegramGifItem("gif_gg_trophy", "Victory GG!", "Gaming", "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=400", "🏆"),
            TelegramGifItem("gif_headshot_win", "Epic Gaming Play", "Gaming", "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=400", "🎮"),
            TelegramGifItem("gif_laugh_tears", "Can't Stop Laughing", "Laughter", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400", "😂"),
            TelegramGifItem("gif_chuckle_roll", "Rolling on Floor", "Laughter", "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400", "🤣"),
            TelegramGifItem("gif_fireworks_champ", "Confetti Explosion", "Celebration", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=400", "🎉"),
            TelegramGifItem("gif_champagne_cheers", "Golden Cheers", "Celebration", "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=400", "🥂"),
            TelegramGifItem("gif_mindblown_stars", "Cosmic Mindblown", "Reactions", "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=400", "🤯"),
            TelegramGifItem("gif_hype_train", "Full Hype Train", "Trending", "https://images.unsplash.com/photo-1519751138087-5bf79df62d5b?w=400", "🚂"),
            TelegramGifItem("gif_fire_hot", "Lit & On Fire", "Trending", "https://images.unsplash.com/photo-1527489377706-5bf97e608852?w=400", "🔥"),
            TelegramGifItem("gif_speedy_run", "Sprint Rush", "Gaming", "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=400", "⚡")
        )
    }
}
