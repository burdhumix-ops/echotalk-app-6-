package co.znxdnd.echotalk.data.remote

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SupabaseAuthUser(
    val id: String,
    val email: String?,
    val displayName: String?
)

/**
 * Coordinates Supabase Authentication (Email + Password, Google ID Token, Session Restore, Password Reset).
 * Never stores service_role credentials and never fabricates online success when unconfigured/offline.
 */
class SupabaseAuthService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SupabaseAuthService"

        @Volatile
        private var instance: SupabaseAuthService? = null

        fun getInstance(context: Context): SupabaseAuthService {
            return instance ?: synchronized(this) {
                instance ?: SupabaseAuthService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _currentUser = MutableStateFlow<SupabaseAuthUser?>(null)
    val currentUser: StateFlow<SupabaseAuthUser?> = _currentUser.asStateFlow()

    val isConfigured: Boolean
        get() = SupabaseConfig.isConfigured

    suspend fun restoreSession(): SupabaseAuthUser? = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull() ?: return@withContext null
        try {
            client.auth.awaitInitialization()
            val session = client.auth.currentSessionOrNull()
            val userInfo = session?.user ?: client.auth.currentUserOrNull()
            val mapped = userInfo?.let {
                SupabaseAuthUser(
                    id = it.id,
                    email = it.email,
                    displayName = it.userMetadata?.get("full_name")?.toString()?.trim('"')
                )
            }
            _currentUser.value = mapped
            mapped
        } catch (e: Exception) {
            Log.w(TAG, "Supabase session restore warning: ${e.message}")
            null
        }
    }

    fun getCurrentUserIdOrNull(): String? {
        val client = SupabaseClientProvider.getClientOrNull() ?: return _currentUser.value?.id
        return try {
            client.auth.currentUserOrNull()?.id ?: _currentUser.value?.id
        } catch (_: Exception) {
            _currentUser.value?.id
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<SupabaseAuthUser> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Backend is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets."))
        try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            val user = client.auth.currentUserOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Authentication completed without an active user session."))
            val mapped = SupabaseAuthUser(
                id = user.id,
                email = user.email ?: email.trim(),
                displayName = user.userMetadata?.get("full_name")?.toString()?.trim('"')
            )
            _currentUser.value = mapped
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e), e))
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<SupabaseAuthUser> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Backend is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets."))
        try {
            val cleanName = displayName.trim().ifBlank { email.substringBefore("@") }
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                data = buildJsonObject {
                    put("full_name", cleanName)
                    put("name", cleanName)
                }
            }
            val user = client.auth.currentUserOrNull()
                ?: return@withContext Result.failure(
                    IllegalStateException("Check your email inbox to confirm your account before signing in.")
                )
            val mapped = SupabaseAuthUser(
                id = user.id,
                email = user.email ?: email.trim(),
                displayName = cleanName
            )
            _currentUser.value = mapped
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e), e))
        }
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        fallbackEmail: String? = null,
        fallbackName: String? = null
    ): Result<SupabaseAuthUser> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Backend is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets."))
        try {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
            }
            val user = client.auth.currentUserOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Google sign-in did not establish a Supabase session."))
            val mapped = SupabaseAuthUser(
                id = user.id,
                email = user.email ?: fallbackEmail,
                displayName = user.userMetadata?.get("full_name")?.toString()?.trim('"') ?: fallbackName
            )
            _currentUser.value = mapped
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e), e))
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(
                IllegalStateException("Password reset requires a configured Supabase project with email delivery enabled.")
            )
        try {
            client.auth.resetPasswordForEmail(email.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e), e))
        }
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.getClientOrNull()
            ?: return@withContext Result.failure(
                IllegalStateException("Backend is not configured. Password change requires an active Supabase session.")
            )
        try {
            client.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthError(e), e))
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.getClientOrNull()?.auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Supabase signOut warning: ${e.message}")
        } finally {
            _currentUser.value = null
        }
    }

    private fun mapAuthError(e: Throwable): String {
        val raw = e.message ?: ""
        return when {
            raw.contains("Invalid login credentials", ignoreCase = true) ->
                "Incorrect email or password."
            raw.contains("Email not confirmed", ignoreCase = true) ->
                "Please confirm your email address before signing in."
            raw.contains("User already registered", ignoreCase = true) ->
                "An account with this email already exists."
            raw.contains("rate limit", ignoreCase = true) || raw.contains("over_email_send_rate_limit", ignoreCase = true) ->
                "Email rate limit reached on free tier. Please wait a moment and try again."
            raw.contains("Unable to resolve host", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "Network unavailable. Check your connection and try again."
            raw.isNotBlank() -> raw
            else -> "Authentication failed. Please try again."
        }
    }
}
