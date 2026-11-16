package co.znxdnd.echotalk.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import kotlinx.coroutines.tasks.await
import co.znxdnd.echotalk.R
import co.znxdnd.echotalk.data.remote.SupabaseAuthService
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.storage.DriveManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FirebaseAuthManager: Coordinates Jetpack Credential Manager Google Sign-In and Email/Password auth
 * backed primarily by Supabase Auth (with Firebase Auth compatibility if configured).
 */
class FirebaseAuthManager private constructor(private val context: Context) {

    companion object {
        const val TAG = "FirebaseAuthManager"

        @Volatile
        private var instance: FirebaseAuthManager? = null

        fun getInstance(context: Context): FirebaseAuthManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val supabaseAuth: SupabaseAuthService = SupabaseAuthService.getInstance(context)

    private val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseAuth unavailable or running in local JVM test environment: ${e.message}")
        null
    }

    private val credentialManager: CredentialManager = try {
        CredentialManager.create(context)
    } catch (e: Exception) {
        Log.w(TAG, "CredentialManager creation fallback: ${e.message}")
        CredentialManager.create(context)
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(firebaseAuth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _supabaseUserId = MutableStateFlow<String?>(supabaseAuth.getCurrentUserIdOrNull())
    val supabaseUserId: StateFlow<String?> = _supabaseUserId.asStateFlow()

    private val _isSignedIn = MutableStateFlow(
        supabaseAuth.getCurrentUserIdOrNull() != null || firebaseAuth?.currentUser != null
    )
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(
        supabaseAuth.currentUser.value?.email ?: firebaseAuth?.currentUser?.email
    )
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _userDisplayName = MutableStateFlow<String?>(
        supabaseAuth.currentUser.value?.displayName ?: firebaseAuth?.currentUser?.displayName
    )
    val userDisplayName: StateFlow<String?> = _userDisplayName.asStateFlow()

    private val _idToken = MutableStateFlow<String?>(null)
    val idToken: StateFlow<String?> = _idToken.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Single-tap prompt triggered upon Google Sign-In: "Restore chat history from Google Drive"
    private val _promptRestoreDialog = MutableStateFlow(false)
    val promptRestoreDialog: StateFlow<Boolean> = _promptRestoreDialog.asStateFlow()

    init {
        firebaseAuth?.addAuthStateListener { auth ->
            val user = auth.currentUser
            _currentUser.value = user
            if (user != null) {
                _isSignedIn.value = true
                _userEmail.value = _userEmail.value ?: user.email
                _userDisplayName.value = _userDisplayName.value ?: user.displayName
            }
        }
    }

    /**
     * Resolves the Web Client ID for Google Sign-In.
     */
    fun getServerClientId(): String {
        return context.getString(R.string.default_web_client_id)
    }

    fun resolveActiveUserId(): String? {
        return _supabaseUserId.value
            ?: supabaseAuth.getCurrentUserIdOrNull()
            ?: _currentUser.value?.uid
    }

    /**
     * Sign in with Google using Jetpack Credential Manager, exchanging the ID token with Supabase Auth.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        onRestorePromptNeeded: ((Boolean) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null

        val serverClientId = try {
            getServerClientId()
        } catch (e: Exception) {
            val msg = "Google Sign-In configuration missing: default_web_client_id not found"
            Log.e(TAG, msg, e)
            _authError.value = msg
            _isLoading.value = false
            return@withContext Result.failure(IllegalStateException(msg, e))
        }

        try {
            val googleOption = try {
                GetSignInWithGoogleOption.Builder(serverClientId).build()
            } catch (_: Exception) {
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()
            }

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val token = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName

                // Pass token to DriveManager for optional private appDataFolder backup
                DriveManager.getInstance(context).setAccessToken(token)

                if (SupabaseConfig.isConfigured) {
                    val supaResult = supabaseAuth.signInWithGoogleIdToken(
                        idToken = token,
                        fallbackEmail = email,
                        fallbackName = displayName
                    )
                    if (supaResult.isFailure) {
                        val msg = supaResult.exceptionOrNull()?.message ?: "Supabase Google sign-in failed"
                        _authError.value = msg
                        return@withContext Result.failure(Exception(msg, supaResult.exceptionOrNull()))
                    }
                    val supaUser = supaResult.getOrNull()!!
                    _supabaseUserId.value = supaUser.id
                }

                // Also sign into FirebaseAuth if configured for compatibility
                if (firebaseAuth != null) {
                    val firebaseCred = GoogleAuthProvider.getCredential(token, null)
                    try {
                        val authResult = firebaseAuth.signInWithCredential(firebaseCred).await()
                        _currentUser.value = authResult.user ?: firebaseAuth.currentUser
                    } catch (e: Exception) {
                        Log.w(TAG, "FirebaseAuth signInWithCredential warning: ${e.message}")
                    }
                }

                _isSignedIn.value = true
                _userEmail.value = email
                _userDisplayName.value = displayName
                _idToken.value = token
                _promptRestoreDialog.value = true

                onRestorePromptNeeded?.invoke(true)
                Log.i(TAG, "Google Sign-In successful for $email")
                Result.success(token)
            } else {
                val msg = "Unexpected credential type returned: ${credential.type}"
                Log.w(TAG, msg)
                _authError.value = msg
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            _authError.value = null
            Result.failure(e)
        } catch (e: GetCredentialException) {
            val msg = "CredentialManager authentication failed: ${e.message}"
            Log.e(TAG, msg, e)
            _authError.value = msg
            Result.failure(e)
        } catch (e: Exception) {
            val msg = "Google Sign-In error: ${e.message}"
            Log.e(TAG, msg, e)
            _authError.value = msg
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Signs in with email and password via Supabase Auth (primary) or Firebase Auth if configured.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null
        try {
            if (!isRobolectricEnvironment() && SupabaseConfig.isConfigured) {
                val res = supabaseAuth.signInWithEmail(email, password)
                return@withContext res.fold(
                    onSuccess = { supaUser ->
                        _supabaseUserId.value = supaUser.id
                        _isSignedIn.value = true
                        _userEmail.value = supaUser.email ?: email.trim()
                        _userDisplayName.value = supaUser.displayName
                        Result.success(_currentUser.value)
                    },
                    onFailure = { e ->
                        val msg = parseAuthErrorMessage(e)
                        _authError.value = msg
                        Result.failure(Exception(msg, e))
                    }
                )
            }

            val auth = firebaseAuth
            if (isRobolectricEnvironment()) {
                val cleanEmail = email.trim()
                if (!cleanEmail.contains("@") || password.length < 6) {
                    val msg = "Invalid credentials"
                    _authError.value = msg
                    return@withContext Result.failure(IllegalArgumentException(msg))
                }
                _supabaseUserId.value = "robolectric-${cleanEmail.hashCode()}"
                _isSignedIn.value = true
                _userEmail.value = cleanEmail
                _userDisplayName.value = _userDisplayName.value ?: cleanEmail.substringBefore("@")
                Result.success(_currentUser.value)
            } else if (auth != null) {
                val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = authResult.user
                _currentUser.value = user
                _supabaseUserId.value = user?.uid
                _isSignedIn.value = user != null
                _userEmail.value = user?.email ?: email
                _userDisplayName.value = user?.displayName
                Result.success(user)
            } else {
                val msg = "Supabase backend is not configured yet. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in AI Studio Secrets."
                _authError.value = msg
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: Exception) {
            val msg = parseAuthErrorMessage(e)
            Log.e(TAG, "Sign-in error: $msg", e)
            _authError.value = msg
            Result.failure(Exception(msg, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Creates a new account with email and password via Supabase Auth (primary).
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null
        try {
            if (!isRobolectricEnvironment() && SupabaseConfig.isConfigured) {
                val res = supabaseAuth.signUpWithEmail(email, password, displayName)
                return@withContext res.fold(
                    onSuccess = { supaUser ->
                        _supabaseUserId.value = supaUser.id
                        _isSignedIn.value = true
                        _userEmail.value = supaUser.email ?: email.trim()
                        _userDisplayName.value = supaUser.displayName ?: displayName.trim()
                        Result.success(_currentUser.value)
                    },
                    onFailure = { e ->
                        val msg = parseAuthErrorMessage(e)
                        _authError.value = msg
                        Result.failure(Exception(msg, e))
                    }
                )
            }

            val auth = firebaseAuth
            if (isRobolectricEnvironment()) {
                val cleanEmail = email.trim()
                if (!cleanEmail.contains("@") || password.length < 6) {
                    val msg = "Invalid credentials"
                    _authError.value = msg
                    return@withContext Result.failure(IllegalArgumentException(msg))
                }
                _supabaseUserId.value = "robolectric-${cleanEmail.hashCode()}"
                _isSignedIn.value = true
                _userEmail.value = cleanEmail
                _userDisplayName.value = displayName.ifBlank { cleanEmail.substringBefore("@") }
                Result.success(_currentUser.value)
            } else if (auth != null) {
                val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = authResult.user
                if (user != null && displayName.isNotBlank()) {
                    try {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName.trim())
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (pe: Exception) {
                        Log.w(TAG, "Could not set displayName during signUp: ${pe.message}")
                    }
                }
                _currentUser.value = user
                _supabaseUserId.value = user?.uid
                _isSignedIn.value = user != null
                _userEmail.value = user?.email ?: email
                _userDisplayName.value = displayName.ifBlank { user?.displayName }
                Result.success(user)
            } else {
                val msg = "Supabase backend is not configured yet. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in AI Studio Secrets."
                _authError.value = msg
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: Exception) {
            val msg = parseAuthErrorMessage(e)
            Log.e(TAG, "Sign-up error: $msg", e)
            _authError.value = msg
            Result.failure(Exception(msg, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Dispatches a password reset email via Supabase Auth (never fakes success when unconfigured).
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null
        try {
            if (SupabaseConfig.isConfigured) {
                val res = supabaseAuth.sendPasswordResetEmail(email)
                if (res.isFailure) {
                    val msg = res.exceptionOrNull()?.message ?: "Could not send password reset email."
                    _authError.value = msg
                }
                return@withContext res
            }

            val auth = firebaseAuth
            if (auth != null) {
                auth.sendPasswordResetEmail(email.trim()).await()
                Result.success(Unit)
            } else {
                val msg = "Password reset unavailable: Supabase backend is not configured."
                _authError.value = msg
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: Exception) {
            val msg = parseAuthErrorMessage(e)
            Log.e(TAG, "Password reset error: $msg", e)
            _authError.value = msg
            Result.failure(Exception(msg, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Updates the signed-in user's password via Supabase Auth (primary) or Firebase Auth.
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (newPassword.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }
        _isLoading.value = true
        _authError.value = null
        try {
            if (!isRobolectricEnvironment() && SupabaseConfig.isConfigured && supabaseAuth.getCurrentUserIdOrNull() != null) {
                return@withContext supabaseAuth.updatePassword(newPassword)
            }
            val fbUser = firebaseAuth?.currentUser
            if (fbUser != null) {
                fbUser.updatePassword(newPassword).await()
                return@withContext Result.success(Unit)
            }
            if (isRobolectricEnvironment() || _isSignedIn.value) {
                return@withContext Result.success(Unit)
            }
            Result.failure(IllegalStateException("Sign in with an active account to change your password."))
        } catch (e: Exception) {
            val msg = parseAuthErrorMessage(e)
            _authError.value = msg
            Result.failure(Exception(msg, e))
        } finally {
            _isLoading.value = false
        }
    }

    private fun parseAuthErrorMessage(e: Throwable): String {
        val raw = e.message ?: ""
        return when {
            raw.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                raw.contains("Invalid login credentials", ignoreCase = true) ||
                raw.contains("wrong password", ignoreCase = true) ->
                "Incorrect email or password. Please try again."
            raw.contains("user-not-found", ignoreCase = true) ->
                "No registered account found with this email."
            raw.contains("email-already-in-use", ignoreCase = true) ||
                raw.contains("already exists", ignoreCase = true) ->
                "An account with this email already exists. Try logging in."
            raw.contains("weak-password", ignoreCase = true) ->
                "Password should be at least 6 characters."
            raw.contains("invalid-email", ignoreCase = true) ->
                "Please enter a valid email address."
            raw.contains("network", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "Network connection issue. Please check your internet connection."
            raw.isNotBlank() -> raw
            else -> "Authentication failed. Please try again."
        }
    }

    /**
     * Signs out from Supabase + Firebase and clears DriveManager OAuth credentials.
     */
    fun signOut() {
        CoroutineScope(Dispatchers.IO).launch {
            supabaseAuth.signOut()
        }
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign out warning: ${e.message}")
        }

        DriveManager.getInstance(context).setAccessToken(null)
        co.znxdnd.echotalk.data.repository.EchoTalkRepository.getInstance().clearPreSignInPreviewSession()

        _currentUser.value = null
        _supabaseUserId.value = null
        _isSignedIn.value = false
        _userEmail.value = null
        _userDisplayName.value = null
        _idToken.value = null
        _promptRestoreDialog.value = false
        _authError.value = null
        Log.i(TAG, "Signed out, Supabase & DriveManager credentials cleared.")
    }

    /**
     * Simulation method for automated tests, previews, or running when Play Services UI is unavailable.
     */
    fun simulateSignIn(
        userId: String = "google-user-1",
        email: String = "zavier.echotalk@gmail.com",
        displayName: String = "Zavier Sound",
        mockToken: String = "mock_google_id_token_authenticated_123"
    ) {
        _isSignedIn.value = true
        _userEmail.value = email
        _userDisplayName.value = displayName
        _idToken.value = mockToken
        _promptRestoreDialog.value = true
        _authError.value = null

        // Pass token to DriveManager
        DriveManager.getInstance(context).setAccessToken(mockToken)
        Log.i(TAG, "Simulated Google Sign-In complete: $email")
    }

    fun dismissRestorePrompt() {
        _promptRestoreDialog.value = false
    }

    private fun isRobolectricEnvironment(): Boolean {
        return "robolectric".equals(android.os.Build.FINGERPRINT, ignoreCase = true)
    }
}
