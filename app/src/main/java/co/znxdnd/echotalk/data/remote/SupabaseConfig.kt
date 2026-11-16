package co.znxdnd.echotalk.data.remote

import co.znxdnd.echotalk.BuildConfig

/**
 * Central configuration holder for Supabase Free-Tier backend.
 * Reads client-safe URL and publishable/anon key injected via Secrets Gradle Plugin (.env / .env.example).
 * Never stores privileged service_role keys, database passwords, or FCM private keys on-device.
 */
object SupabaseConfig {

    val url: String
        get() = try {
            BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        } catch (_: Throwable) {
            ""
        }

    val publishableKey: String
        get() = try {
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
        } catch (_: Throwable) {
            ""
        }

    val isConfigured: Boolean
        get() = isValidConfig(url, publishableKey)

    fun isValidConfig(candidateUrl: String, candidateKey: String): Boolean {
        val cleanUrl = candidateUrl.trim()
        val cleanKey = candidateKey.trim()
        if (cleanUrl.isBlank() || cleanKey.isBlank()) return false
        if (cleanUrl.startsWith("YOUR_", ignoreCase = true) || cleanKey.startsWith("YOUR_", ignoreCase = true)) {
            return false
        }
        if (cleanUrl.contains("placeholder", ignoreCase = true) || cleanKey.contains("placeholder", ignoreCase = true)) {
            return false
        }
        val validScheme = cleanUrl.startsWith("https://", ignoreCase = true) ||
            cleanUrl.startsWith("http://localhost", ignoreCase = true) ||
            cleanUrl.startsWith("http://10.0.2.2", ignoreCase = true)
        return validScheme && cleanKey.length >= 20
    }

    const val STORAGE_BUCKET_PROFILE_IMAGES = "profile-images"
    const val EDGE_FN_SEND_PUSH = "send-push-notification"
    const val EDGE_FN_LIVEKIT_TOKEN = "livekit-token"
    const val DM_PAGE_SIZE = 30
    const val ROOM_PAGE_SIZE = 50
    const val PRESENCE_HEARTBEAT_MIN_INTERVAL_MS = 25_000L
    const val ROOM_CLEANUP_MIN_INTERVAL_MS = 120_000L
    const val CALL_RINGING_TIMEOUT_MS = 35_000L
    const val LIVEKIT_FREE_MONTHLY_PARTICIPANT_MINUTES = 5000
}
