package co.znxdnd.echotalk.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.znxdnd.echotalk.auth.OnboardingManager
import co.znxdnd.echotalk.auth.OnboardingStep
import co.znxdnd.echotalk.data.remote.SupabaseAuthService
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val userId: String = "",
    val username: String = "",
    val isUsernameValid: Boolean = false,
    val isCheckingAvailability: Boolean = false,
    val isUsernameAvailable: Boolean? = null,
    val usernameError: String? = null,
    val displayName: String = "",
    val avatarUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
    val bio: String = "",
    val age: Int = 24,
    val gender: String = "Non-Binary",
    val country: String = "Global",
    val ghostMode: Boolean = false,
    val profileVisibility: Boolean = true,
    val onlineStatusVisible: Boolean = true,
    val readReceipts: Boolean = true,
    val isSaving: Boolean = false,
    val completionCelebration: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val onboardingManager = OnboardingManager.getInstance(application)
    private val repository = EchoTalkRepository.getInstance()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        val uid = SupabaseAuthService.getInstance(application).getCurrentUserIdOrNull()
            ?: try {
                FirebaseAuth.getInstance().currentUser?.uid ?: "user_default"
            } catch (_: Exception) {
                "user_default"
            }
        val currentStep = onboardingManager.currentStep.value
        _uiState.value = _uiState.value.copy(userId = uid, currentStep = currentStep)
    }

    fun setStep(step: OnboardingStep) {
        _uiState.value = _uiState.value.copy(currentStep = step)
        onboardingManager.saveProgress(_uiState.value.userId, step)
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        val nextIndex = current.stepIndex + 1
        if (nextIndex < OnboardingStep.entries.size) {
            setStep(OnboardingStep.fromIndex(nextIndex))
        } else {
            finishOnboarding()
        }
    }

    fun prevStep() {
        val current = _uiState.value.currentStep
        val prevIndex = current.stepIndex - 1
        if (prevIndex >= 0) {
            setStep(OnboardingStep.fromIndex(prevIndex))
        }
    }

    fun updateUsername(input: String) {
        val cleaned = input.trim().lowercase()
        val regex = Regex("^[a-zA-Z0-9_]{3,20}$")
        val isValid = regex.matches(cleaned)
        _uiState.value = _uiState.value.copy(
            username = cleaned,
            isUsernameValid = isValid,
            isUsernameAvailable = null,
            usernameError = if (cleaned.isNotEmpty() && !isValid) {
                "3-20 chars: letters, numbers and underscores only"
            } else null
        )

        if (isValid) {
            checkUsernameAvailability(cleaned)
        }
    }

    fun checkUsernameAvailability(username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingAvailability = true)
            val isAvailable = withContext(Dispatchers.IO) {
                try {
                    if (SupabaseConfig.isConfigured) {
                        SupabaseSocialRepository.getInstance()
                            .isUsernameAvailable(username, _uiState.value.userId)
                    } else {
                        true
                    }
                } catch (e: Exception) {
                    Log.w("OnboardingViewModel", "Availability check fallback: ${e.message}")
                    true
                }
            }

            _uiState.value = _uiState.value.copy(
                isCheckingAvailability = false,
                isUsernameAvailable = isAvailable,
                usernameError = if (isAvailable) null else "Username already taken"
            )
        }
    }

    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun updateAvatarUrl(url: String) {
        _uiState.value = _uiState.value.copy(avatarUrl = url)
    }

    fun updateBio(bio: String) {
        if (bio.length <= 150) {
            _uiState.value = _uiState.value.copy(bio = bio)
        }
    }

    fun updateAge(age: Int) {
        _uiState.value = _uiState.value.copy(age = age.coerceIn(18, 99))
    }

    fun updateGender(gender: String) {
        _uiState.value = _uiState.value.copy(gender = gender)
    }

    fun updateCountry(country: String) {
        _uiState.value = _uiState.value.copy(country = country)
    }

    fun updateGhostMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(ghostMode = enabled)
    }

    fun updateProfileVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(profileVisibility = visible)
    }

    fun updateOnlineStatusVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(onlineStatusVisible = visible)
    }

    fun updateReadReceipts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(readReceipts = enabled)
    }

    fun finishOnboarding(onComplete: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.isSaving) return // Guard against repeated presses
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)

            val profileData = hashMapOf<String, Any>(
                "username" to if (state.username.isNotBlank()) state.username else "user_${state.userId.take(6)}",
                "displayName" to if (state.displayName.isNotBlank()) state.displayName else "EchoTalker",
                "photoURL" to state.avatarUrl,
                "bio" to state.bio,
                "age" to state.age,
                "gender" to state.gender,
                "country" to state.country,
                "ghostMode" to state.ghostMode,
                "profileVisibility" to state.profileVisibility,
                "onlineStatusVisible" to state.onlineStatusVisible,
                "readReceipts" to state.readReceipts,
                "onboardingCompleted" to true,
                "profileCompleted" to true
            )

            // Update in-memory user
            val current = repository.currentUser.value
            val updatedUser = current.copy(
                name = if (state.displayName.isNotBlank()) state.displayName else current.name,
                handle = if (state.username.isNotBlank()) "@${state.username}" else current.handle,
                avatarUrl = state.avatarUrl,
                bio = state.bio,
                age = state.age,
                gender = state.gender,
                country = state.country,
                onboardingCompleted = true,
                profileCompleted = true
            )
            repository.setCurrentUser(updatedUser)

            onboardingManager.completeOnboarding(state.userId, profileData)
            _uiState.value = _uiState.value.copy(isSaving = false, completionCelebration = true)
            onComplete?.invoke()
        }
    }

    fun emergencyEnter(onComplete: () -> Unit) {
        onboardingManager.emergencyEnterApp(_uiState.value.userId)
        onComplete()
    }
}
