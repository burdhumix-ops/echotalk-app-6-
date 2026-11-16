package co.znxdnd.echotalk.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import co.znxdnd.echotalk.data.remote.SupabaseAuthService
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.di.DatabaseModule
import co.znxdnd.echotalk.utils.ResilienceGuardian
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OnboardingStep defines the 7 sequential stages of the EchoTalk onboarding experience.
 */
enum class OnboardingStep(val stepIndex: Int, val title: String) {
    WELCOME(0, "Welcome to EchoTalks"),
    USERNAME(1, "Choose Username"),
    PROFILE_PICTURE(2, "Profile Picture"),
    BASIC_PROFILE(3, "Personal Details"),
    PRIVACY_DISCOVERABILITY(4, "Privacy & Discovery"),
    INTRO_DISCOVER_DMS(5, "Explore EchoTalks"),
    COMPLETED(6, "All Set!");

    companion object {
        fun fromIndex(index: Int): OnboardingStep {
            return entries.find { it.stepIndex == index } ?: WELCOME
        }
    }
}

/**
 * Deterministic Onboarding State Machine with explicit operational states.
 */
sealed class OnboardingState {
    object CheckingAccount : OnboardingState()
    object AuthenticationRequired : OnboardingState()
    data class ProfileSetup(val userId: String) : OnboardingState()
    data class Guide(val currentStep: OnboardingStep, val userId: String) : OnboardingState()
    data class Completing(val userId: String) : OnboardingState()
    data class Completed(val userId: String) : OnboardingState()
    data class RecoverableError(
        val message: String,
        val userId: String,
        val lastValidState: OnboardingState,
        val canContinueToApp: Boolean = true
    ) : OnboardingState()

    // Backward-compatibility aliases for existing callers
    data class InProgress(val currentStep: OnboardingStep, val userId: String) : OnboardingState()
    data class NeedsProfileSetup(val userId: String) : OnboardingState()
    object Idle : OnboardingState()
    object Loading : OnboardingState()
}

/**
 * OnboardingManager: Coordinates first-time user detection, persistent onboarding progress,
 * and profile readiness using Supabase Free backend and local fast preferences.
 */
class OnboardingManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "OnboardingManager"
        private const val PREFS_NAME = "echotalk_onboarding_prefs"
        private const val KEY_PROFILE_DONE_PREFIX = "profile_done_"
        private const val KEY_GUIDE_DONE_PREFIX = "guide_done_"
        private const val KEY_LAST_STEP_PREFIX = "last_step_"
        private const val KEY_RECOVERY_ATTEMPTS = "recovery_attempts_"

        @Volatile
        private var instance: OnboardingManager? = null

        fun getInstance(context: Context): OnboardingManager {
            return instance ?: synchronized(this) {
                instance ?: OnboardingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + ResilienceGuardian.safeScopeExceptionHandler(TAG)
    )
    private val isCompletingGuard = AtomicBoolean(false)

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.CheckingAccount)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _currentStep = MutableStateFlow(OnboardingStep.WELCOME)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    /**
     * Inspects current authentication state and evaluates onboarding status deterministically.
     */
    fun evaluateOnboarding(userId: String? = null) {
        val repo = EchoTalkRepository.getInstance()
        if (repo.isPreSignInPreviewSession.value) {
            _state.value = OnboardingState.Completed(repo.currentUser.value.id)
            return
        }
        val syncFirebaseUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
        val explicitNonPlaceholderUid = userId?.takeIf { it.isNotBlank() && it != "u-zavier" }
        val syncTargetUid = explicitNonPlaceholderUid
            ?: SupabaseAuthService.getInstance(context).getCurrentUserIdOrNull()
            ?: syncFirebaseUid

        val isRobolectric = "robolectric".equals(android.os.Build.FINGERPRINT, ignoreCase = true)
        if ((!SupabaseConfig.isConfigured || isRobolectric) && syncTargetUid.isNullOrBlank()) {
            _state.value = OnboardingState.AuthenticationRequired
            return
        }

        scope.launch {
            _state.value = OnboardingState.CheckingAccount

            val restoredSupaUser = if (SupabaseConfig.isConfigured) {
                SupabaseAuthService.getInstance(context).restoreSession()
            } else null

            val targetUid = explicitNonPlaceholderUid
                ?: restoredSupaUser?.id
                ?: syncFirebaseUid

            if (targetUid.isNullOrBlank()) {
                _state.value = OnboardingState.AuthenticationRequired
                return@launch
            }
            if (restoredSupaUser != null) {
                ResilienceGuardian.resetSchemaCircuitBreaker()
            }

            // 1. Check local fast-path state
            val localProfileDone = prefs.getBoolean(KEY_PROFILE_DONE_PREFIX + targetUid, false)
            val localGuideDone = prefs.getBoolean(KEY_GUIDE_DONE_PREFIX + targetUid, false)
            val savedStepIndex = prefs.getInt(KEY_LAST_STEP_PREFIX + targetUid, 0)

            if (localProfileDone && localGuideDone) {
                _state.value = OnboardingState.Completed(targetUid)
                return@launch
            }

            // 2. Check Supabase profile for returning accounts
            var cloudProfileDone = false
            var cloudGuideDone = false
            if (SupabaseConfig.isConfigured && restoredSupaUser != null) {
                try {
                    val profile = SupabaseSocialRepository.getInstance().fetchCurrentProfile().getOrNull()
                    if (profile != null) {
                        cloudProfileDone = profile.profileCompleted
                        cloudGuideDone = profile.onboardingCompleted
                        EchoTalkRepository.getInstance().setAuthoritativeCurrentUser(profile)
                        if (cloudProfileDone && cloudGuideDone) {
                            setLocalProfileCompleted(targetUid, true)
                            setLocalGuideCompleted(targetUid, true)
                            _state.value = OnboardingState.Completed(targetUid)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Supabase onboarding fetch warning: ${e.message}")
                }
            } else {
                // Legacy Firestore fallback if present
                try {
                    val firestore = DatabaseModule.getInstance()
                    val userDoc = firestore.collection("users").document(targetUid).get().await()
                    if (userDoc != null && userDoc.exists()) {
                        cloudProfileDone = userDoc.getBoolean("profileCompleted") ?: false
                        cloudGuideDone = userDoc.getBoolean("onboardingCompleted") ?: false
                        if (cloudProfileDone && cloudGuideDone) {
                            setLocalProfileCompleted(targetUid, true)
                            setLocalGuideCompleted(targetUid, true)
                            _state.value = OnboardingState.Completed(targetUid)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Onboarding profile fetch warning: ${e.message}")
                }
            }

            // 3. Resolve deterministic step based on state consistency
            val isProfileComplete = localProfileDone || cloudProfileDone
            if (!isProfileComplete) {
                _state.value = OnboardingState.ProfileSetup(targetUid)
            } else {
                val step = OnboardingStep.fromIndex(savedStepIndex)
                _currentStep.value = step
                _state.value = OnboardingState.Guide(step, targetUid)
            }
        }
    }

    fun saveProgress(userId: String, step: OnboardingStep) {
        _currentStep.value = step
        prefs.edit().putInt(KEY_LAST_STEP_PREFIX + userId, step.stepIndex).apply()
        _state.value = OnboardingState.Guide(step, userId)
    }

    suspend fun completeStepAndAdvance(userId: String, current: OnboardingStep) {
        val nextIndex = current.stepIndex + 1
        if (nextIndex >= OnboardingStep.COMPLETED.stepIndex) {
            completeOnboarding(userId)
        } else {
            val nextStep = OnboardingStep.fromIndex(nextIndex)
            saveProgress(userId, nextStep)
        }
    }

    /**
     * Completion Watchdog: Enforces bounded timeout (7s), persists profile completion to Supabase,
     * and guards against duplicate execution or infinite spinners.
     */
    suspend fun completeOnboarding(
        userId: String,
        additionalData: Map<String, Any> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isCompletingGuard.compareAndSet(false, true)) {
            Log.w(TAG, "Completion already in progress for $userId. Suppressing duplicate call.")
            return@withContext Result.success(Unit)
        }

        _state.value = OnboardingState.Completing(userId)

        try {
            val completionResult = withTimeoutOrNull(7000L) {
                setLocalProfileCompleted(userId, true)
                setLocalGuideCompleted(userId, true)

                val supaUid = if (SupabaseConfig.isConfigured) {
                    SupabaseAuthService.getInstance(context).getCurrentUserIdOrNull()
                } else null
                if (supaUid != null) {
                    val repo = EchoTalkRepository.getInstance()
                    val user = repo.currentUser.value.copy(
                        id = supaUid,
                        profileCompleted = true,
                        onboardingCompleted = true
                    )
                    SupabaseSocialRepository.getInstance().upsertProfile(user, repo.settings.value)
                    SupabaseSocialRepository.getInstance().syncPreferences(repo.settings.value)
                }
                true
            }

            if (completionResult == true) {
                _state.value = OnboardingState.Completed(userId)
                Result.success(Unit)
            } else {
                Log.w(TAG, "Completion watchdog timeout triggered for $userId. Applying safe fallback.")
                if (isProfileCompleted(userId)) {
                    _state.value = OnboardingState.Completed(userId)
                    Result.success(Unit)
                } else {
                    _state.value = OnboardingState.RecoverableError(
                        message = "Account setup took longer than expected.",
                        userId = userId,
                        lastValidState = OnboardingState.ProfileSetup(userId),
                        canContinueToApp = true
                    )
                    Result.failure(java.util.concurrent.TimeoutException("Completion timed out"))
                }
            }
        } finally {
            isCompletingGuard.set(false)
        }
    }

    /**
     * Emergency exit from the guide directly into the main application.
     */
    fun emergencyEnterApp(userId: String) {
        setLocalProfileCompleted(userId, true)
        setLocalGuideCompleted(userId, true)
        _state.value = OnboardingState.Completed(userId)
        Log.i(TAG, "Emergency enter app triggered for $userId")
    }

    /**
     * Direct local preview entry triggered by pre-sign-in PIN gateway.
     * Bypasses login and onboarding cleanly in memory with zero cloud DB writes.
     */
    fun enterPreSignInPreview(userId: String = "u-owner-preview") {
        _state.value = OnboardingState.Completed(userId)
    }

    /**
     * Automatic stuck-state recovery on launch or foreground return.
     */
    fun recoverFromStuckState(userId: String) {
        val attempts = prefs.getInt(KEY_RECOVERY_ATTEMPTS + userId, 0)
        prefs.edit().putInt(KEY_RECOVERY_ATTEMPTS + userId, attempts + 1).apply()

        if (attempts > 3) {
            Log.w(TAG, "Multiple stuck-state recoveries detected for $userId. Applying emergency unlock.")
            emergencyEnterApp(userId)
            return
        }

        val hasProfile = isProfileCompleted(userId)
        if (hasProfile) {
            _state.value = OnboardingState.Completed(userId)
        } else {
            _state.value = OnboardingState.ProfileSetup(userId)
        }
    }

    fun replayOnboarding(userId: String) {
        setLocalGuideCompleted(userId, false)
        prefs.edit().putInt(KEY_LAST_STEP_PREFIX + userId, 0).apply()
        _currentStep.value = OnboardingStep.WELCOME
        _state.value = OnboardingState.Guide(OnboardingStep.WELCOME, userId)
    }

    fun setLocalProfileCompleted(userId: String, completed: Boolean) {
        prefs.edit().putBoolean(KEY_PROFILE_DONE_PREFIX + userId, completed).apply()
    }

    fun setLocalGuideCompleted(userId: String, completed: Boolean) {
        prefs.edit().putBoolean(KEY_GUIDE_DONE_PREFIX + userId, completed).apply()
    }

    fun isProfileCompleted(userId: String): Boolean {
        return prefs.getBoolean(KEY_PROFILE_DONE_PREFIX + userId, false)
    }

    fun isGuideCompleted(userId: String): Boolean {
        return prefs.getBoolean(KEY_GUIDE_DONE_PREFIX + userId, false)
    }

    fun setLocalOnboardingCompleted(userId: String, completed: Boolean) {
        setLocalProfileCompleted(userId, completed)
        setLocalGuideCompleted(userId, completed)
    }

    fun isLocalOnboardingCompleted(userId: String): Boolean {
        return isProfileCompleted(userId) && isGuideCompleted(userId)
    }
}
