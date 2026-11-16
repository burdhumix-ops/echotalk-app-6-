package co.znxdnd.echotalk.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class OperationConfig(
    val operationKey: String,
    val requiresConfirmation: Boolean = false,
    val isConfirmed: Boolean = true,
    val maxRetries: Int = 3,
    val validator: (() -> ValidationResult)? = null,
    val postVerification: ((Any?) -> Boolean)? = null
)

/**
 * OperationManager: Reliability orchestrator that coordinates pre-validation, concurrency locks,
 * destructive operation guards, exponential backoff execution, and health monitoring.
 */
class OperationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "OperationManager"

        @Volatile
        private var instance: OperationManager? = null

        fun getInstance(context: Context): OperationManager {
            return instance ?: synchronized(this) {
                instance ?: OperationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val healthMonitor = AppHealthMonitor.getInstance(context)
    private val activeLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> executeSafely(
        config: OperationConfig,
        action: suspend () -> T
    ): Result<T> {
        val key = config.operationKey

        // 1. Destructive guard check
        if (config.requiresConfirmation && !config.isConfirmed) {
            val msg = "Operation [$key] blocked: destructive action requires explicit confirmation"
            Log.w(TAG, msg)
            return Result.failure(IllegalStateException(msg))
        }

        // 2. Pre-validation check
        config.validator?.let { validate ->
            val result = validate()
            if (result is ValidationResult.Invalid) {
                Log.w(TAG, "Pre-validation failed for [$key]: ${result.reason}")
                return Result.failure(IllegalArgumentException(result.reason))
            }
        }

        // 3. Mutex deduplication check
        val mutex = activeLocks.computeIfAbsent(key) { Mutex() }
        if (mutex.isLocked) {
            Log.w(TAG, "Operation [$key] is already executing. Duplicate request suppressed.")
            return Result.failure(IllegalStateException("Operation is already in progress"))
        }

        return mutex.withLock {
            val recoveryResult = ErrorRecoveryManager.executeWithRetry(
                maxAttempts = config.maxRetries,
                operationName = key
            ) {
                action()
            }

            if (recoveryResult.isSuccess) {
                val value = recoveryResult.getOrNull()
                // 4. Post-execution verification
                val verified = config.postVerification?.invoke(value) ?: true
                if (verified) {
                    healthMonitor.recordOperation(true, key)
                    Result.success(value as T)
                } else {
                    val verificationError = IllegalStateException("Post-execution verification failed for [$key]")
                    healthMonitor.recordOperation(false, key)
                    Result.failure(verificationError)
                }
            } else {
                val exception = recoveryResult.exceptionOrNull() ?: RuntimeException("Unknown execution error")
                healthMonitor.recordException(key, exception)
                Result.failure(exception)
            }
        }
    }
}
