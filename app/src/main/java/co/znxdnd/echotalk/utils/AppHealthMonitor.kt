package co.znxdnd.echotalk.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

enum class HealthLevel {
    HEALTHY,
    DEGRADED,
    CRITICAL
}

data class AppHealthStatus(
    val level: HealthLevel,
    val totalOperations: Int,
    val failureCount: Int,
    val successRatePercent: Int,
    val lastExceptionMessage: String?
)

/**
 * AppHealthMonitor: Continuously monitors reliability metrics, operation success rates,
 * and system health to initiate safe fallbacks when anomalies are detected.
 */
class AppHealthMonitor private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppHealthMonitor"

        @Volatile
        private var instance: AppHealthMonitor? = null

        fun getInstance(context: Context): AppHealthMonitor {
            return instance ?: synchronized(this) {
                instance ?: AppHealthMonitor(context.applicationContext).also { instance = it }
            }
        }
    }

    private val totalOps = AtomicInteger(0)
    private val failureOps = AtomicInteger(0)

    private val _healthStatus = MutableStateFlow(
        AppHealthStatus(
            level = HealthLevel.HEALTHY,
            totalOperations = 0,
            failureCount = 0,
            successRatePercent = 100,
            lastExceptionMessage = null
        )
    )
    val healthStatus: StateFlow<AppHealthStatus> = _healthStatus.asStateFlow()

    fun recordOperation(success: Boolean, tag: String = "general") {
        val total = totalOps.incrementAndGet()
        val failures = if (!success) failureOps.incrementAndGet() else failureOps.get()

        val successRate = if (total > 0) {
            (((total - failures).toDouble() / total) * 100).toInt()
        } else 100

        val level = when {
            failures > 5 && successRate < 70 -> HealthLevel.CRITICAL
            failures > 2 && successRate < 90 -> HealthLevel.DEGRADED
            else -> HealthLevel.HEALTHY
        }

        _healthStatus.value = _healthStatus.value.copy(
            level = level,
            totalOperations = total,
            failureCount = failures,
            successRatePercent = successRate
        )

        if (!success) {
            Log.w(TAG, "Recorded operation failure in [$tag]. Current success rate: $successRate%")
        }
    }

    fun recordException(tag: String, e: Throwable) {
        recordOperation(false, tag)
        _healthStatus.value = _healthStatus.value.copy(
            lastExceptionMessage = "${e.javaClass.simpleName}: ${e.message}"
        )
    }

    fun resetMetrics() {
        totalOps.set(0)
        failureOps.set(0)
        _healthStatus.value = AppHealthStatus(
            level = HealthLevel.HEALTHY,
            totalOperations = 0,
            failureCount = 0,
            successRatePercent = 100,
            lastExceptionMessage = null
        )
    }
}
