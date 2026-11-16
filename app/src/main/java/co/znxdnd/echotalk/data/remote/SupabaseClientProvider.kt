package co.znxdnd.echotalk.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.Json

/**
 * Provides a single shared SupabaseClient configured for Ktor CIO + Kotlinx Serialization.
 * Returns null safely when Supabase credentials are not yet configured in Secrets / .env.
 */
object SupabaseClientProvider {

    private const val TAG = "SupabaseClientProvider"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    @Volatile
    private var cachedClient: SupabaseClient? = null

    @Volatile
    private var cachedUrl: String = ""

    @Volatile
    private var cachedKey: String = ""

    @Volatile
    var lastInitError: String? = null
        private set

    private fun buildClient(
        url: String,
        key: String,
        useInMemoryAuthStorage: Boolean
    ): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            defaultSerializer = KotlinXSerializer(json)
            httpEngine = CIO.create()
            install(Auth) {
                if (useInMemoryAuthStorage) {
                    sessionManager = MemorySessionManager()
                    codeVerifierCache = MemoryCodeVerifierCache()
                }
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }

    fun getClientOrNull(): SupabaseClient? {
        if (!SupabaseConfig.isConfigured) {
            return null
        }
        val url = SupabaseConfig.url
        val key = SupabaseConfig.publishableKey
        val existing = cachedClient
        if (existing != null && cachedUrl == url && cachedKey == key) {
            return existing
        }

        return synchronized(this) {
            val recheck = cachedClient
            if (recheck != null && cachedUrl == url && cachedKey == key) {
                return@synchronized recheck
            }
            try {
                val client = try {
                    buildClient(url, key, useInMemoryAuthStorage = false)
                } catch (settingsErr: IllegalStateException) {
                    if (settingsErr.message?.contains("SettingsSessionManager", ignoreCase = true) == true) {
                        buildClient(url, key, useInMemoryAuthStorage = true)
                    } else {
                        throw settingsErr
                    }
                }
                client.also {
                    cachedClient = it
                    cachedUrl = url
                    cachedKey = key
                    lastInitError = null
                    Log.i(TAG, "SupabaseClient initialized for $url")
                }
            } catch (t: Throwable) {
                lastInitError = "${t::class.java.simpleName}: ${t.message}"
                Log.e(TAG, "Failed to initialize SupabaseClient: ${t.message}", t)
                null
            }
        }
    }

    fun requireClient(): SupabaseClient {
        return getClientOrNull()
            ?: throw IllegalStateException("Supabase is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in AI Studio Secrets or .env.")
    }

    fun resetForTesting() {
        synchronized(this) {
            cachedClient = null
            cachedUrl = ""
            cachedKey = ""
            lastInitError = null
        }
    }
}
