package co.znxdnd.echotalk.utils

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

enum class ErrorCategory {
    NETWORK,
    AUTHENTICATION,
    AUTH_EXPIRED,
    SCHEMA_MISSING,
    RATE_LIMITED,
    STORAGE,
    PERMISSION,
    VALIDATION,
    UNKNOWN
}

/**
 * ErrorRecoveryManager: Classifies execution errors and performs exponential backoff retries
 * and automatic fallbacks to ensure application resilience.
 */
object ErrorRecoveryManager {

    private const val TAG = "ErrorRecoveryManager"

    fun classifyError(throwable: Throwable): ErrorCategory {
        val msg = throwable.message?.lowercase() ?: ""
        if (msg.contains("pgrst205") ||
            msg.contains("schema cache") ||
            msg.contains("42p01") ||
            (msg.contains("relation") && msg.contains("does not exist"))
        ) {
            return ErrorCategory.SCHEMA_MISSING
        }
        if (msg.contains("jwt expired") ||
            msg.contains("pgrst301") ||
            msg.contains("401 unauthorized") ||
            msg.contains("token expired")
        ) {
            return ErrorCategory.AUTH_EXPIRED
        }
        if (msg.contains("429") ||
            msg.contains("rate limit") ||
            msg.contains("over_email_send_rate_limit")
        ) {
            return ErrorCategory.RATE_LIMITED
        }
        if (msg.contains("not authenticated") ||
            msg.contains("sign in required") ||
            msg.contains("auth") ||
            msg.contains("token") ||
            msg.contains("sign-in")
        ) {
            return ErrorCategory.AUTHENTICATION
        }

        return when (throwable) {
            is UnknownHostException, is SocketException, is IOException -> ErrorCategory.NETWORK
            is SecurityException -> ErrorCategory.PERMISSION
            is IllegalArgumentException, is IllegalStateException -> ErrorCategory.VALIDATION
            else -> when {
                msg.contains("network") ||
                    msg.contains("timeout") ||
                    msg.contains("timed out") ||
                    msg.contains("connection") ||
                    msg.contains("unable to resolve host") -> ErrorCategory.NETWORK
                msg.contains("sqlite") || msg.contains("disk") || msg.contains("storage") -> ErrorCategory.STORAGE
                else -> ErrorCategory.UNKNOWN
            }
        }
    }

    fun isRecoverable(errorCategory: ErrorCategory): Boolean {
        return when (errorCategory) {
            ErrorCategory.NETWORK,
            ErrorCategory.STORAGE,
            ErrorCategory.AUTH_EXPIRED,
            ErrorCategory.RATE_LIMITED,
            ErrorCategory.UNKNOWN -> true
            ErrorCategory.AUTHENTICATION,
            ErrorCategory.SCHEMA_MISSING,
            ErrorCategory.PERMISSION,
            ErrorCategory.VALIDATION -> false
        }
    }

    suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500L,
        maxDelayMs: Long = 4000L,
        factor: Double = 2.0,
        operationName: String = "operation",
        block: suspend (attempt: Int) -> T
    ): Result<T> {
        var currentDelay = initialDelayMs
        var lastThrowable: Throwable? = null

        for (attempt in 1..maxAttempts) {
            try {
                val result = block(attempt)
                if (attempt > 1) {
                    Log.i(TAG, "[$operationName] succeeded on attempt $attempt")
                }
                return Result.success(result)
            } catch (t: Throwable) {
                lastThrowable = t
                val category = classifyError(t)
                Log.w(TAG, "[$operationName] attempt $attempt failed ($category): ${t.message}")

                if (!isRecoverable(category) || attempt == maxAttempts) {
                    break
                }

                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }

        return Result.failure(lastThrowable ?: RuntimeException("Operation failed without exception"))
    }

    suspend fun <T> executeWithFallback(
        primary: suspend () -> T,
        fallback: suspend (Throwable) -> T,
        operationName: String = "operation"
    ): Result<T> {
        return try {
            Result.success(primary())
        } catch (t: Throwable) {
            Log.w(TAG, "[$operationName] primary failed, attempting fallback: ${t.message}")
            try {
                Result.success(fallback(t))
            } catch (fallbackError: Throwable) {
                Log.e(TAG, "[$operationName] fallback also failed: ${fallbackError.message}")
                Result.failure(fallbackError)
            }
        }
    }
}
