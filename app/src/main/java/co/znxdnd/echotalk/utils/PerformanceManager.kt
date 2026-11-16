package co.znxdnd.echotalk.utils

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.util.Log
import co.znxdnd.echotalk.ui.components.EchoAnimations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PerformanceManager: Controls adaptive visual rendering fidelity, memory consumption,
 * and animation complexity based on selected profile or device conditions.
 */
class PerformanceManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PerformanceManager"
        private const val PREFS_NAME = "echotalk_perf_prefs"
        private const val KEY_PROFILE = "active_perf_profile"

        @Volatile
        private var instance: PerformanceManager? = null

        fun getInstance(context: Context): PerformanceManager {
            return instance ?: synchronized(this) {
                instance ?: PerformanceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeProfile = MutableStateFlow(loadSavedProfile())
    val activeProfile: StateFlow<PerformanceProfile> = _activeProfile.asStateFlow()

    private val _metrics = MutableStateFlow(computeMetrics(_activeProfile.value))
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    init {
        applyProfileEffects(_activeProfile.value)
    }

    private fun loadSavedProfile(): PerformanceProfile {
        val savedName = prefs.getString(KEY_PROFILE, PerformanceProfile.BALANCED.name)
        return try {
            PerformanceProfile.valueOf(savedName ?: PerformanceProfile.BALANCED.name)
        } catch (_: Exception) {
            PerformanceProfile.BALANCED
        }
    }

    fun setProfile(profile: PerformanceProfile) {
        _activeProfile.value = profile
        prefs.edit().putString(KEY_PROFILE, profile.name).apply()
        applyProfileEffects(profile)
    }

    private fun applyProfileEffects(profile: PerformanceProfile) {
        val resolvedProfile = if (profile == PerformanceProfile.AUTOMATIC) {
            resolveAutomaticProfile()
        } else {
            profile
        }

        // Apply motion reduction to global animation physics
        EchoAnimations.reduceMotionGlobal = (resolvedProfile == PerformanceProfile.LITE)

        _metrics.value = computeMetrics(resolvedProfile)
        Log.d(TAG, "Applied performance profile: $profile (Resolved: $resolvedProfile)")
    }

    private fun resolveAutomaticProfile(): PerformanceProfile {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isPowerSaveMode == true) {
                return PerformanceProfile.LITE
            }

            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)

            if (memInfo.lowMemory || memInfo.availMem < 250 * 1024 * 1024L) {
                PerformanceProfile.LITE
            } else {
                PerformanceProfile.BALANCED
            }
        } catch (_: Exception) {
            PerformanceProfile.BALANCED
        }
    }

    fun computeMetrics(profile: PerformanceProfile): PerformanceMetrics {
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
        val isLowMem = usedMemMb > (maxHeapMb * 0.85)

        val resolved = if (profile == PerformanceProfile.AUTOMATIC) resolveAutomaticProfile() else profile

        return PerformanceMetrics(
            profile = profile,
            enableBlurs = resolved == PerformanceProfile.HIGH,
            enableCardShadows = resolved != PerformanceProfile.LITE,
            enableComplexSprings = resolved != PerformanceProfile.LITE,
            imageScaleFactor = when (resolved) {
                PerformanceProfile.HIGH -> 1.0f
                PerformanceProfile.BALANCED -> 0.85f
                PerformanceProfile.LITE, PerformanceProfile.AUTOMATIC -> 0.70f
            },
            memoryUsageMb = usedMemMb,
            maxHeapMb = maxHeapMb,
            isLowMemory = isLowMem
        )
    }

    fun refreshMetrics() {
        _metrics.value = computeMetrics(_activeProfile.value)
    }
}
