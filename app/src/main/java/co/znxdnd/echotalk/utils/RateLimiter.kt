package co.znxdnd.echotalk.utils

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * RateLimiter: Thread-safe continuous Token-Bucket anti-spam limiter paired with an
 * exponential-backoff PIN brute-force lockout guard.
 */
object RateLimiter {

    private data class BucketState(
        var tokens: Double,
        var lastRefillMs: Long
    )

    private data class PinFailureState(
        var consecutiveFailures: Int = 0,
        var lockedUntilMs: Long = 0L
    )

    private const val DEFAULT_MAX_PIN_ATTEMPTS = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 300_000L

    private val buckets = ConcurrentHashMap<String, BucketState>()
    private val pinFailures = ConcurrentHashMap<String, PinFailureState>()

    /**
     * Sliding token-bucket check allowing controlled bursts up to [capacity]
     * with smooth refill at [refillPerSecond].
     */
    fun allowAction(
        bucketKey: String,
        capacity: Double = 6.0,
        refillPerSecond: Double = 1.5,
        cost: Double = 1.0,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val bucket = buckets.computeIfAbsent(bucketKey) {
            BucketState(tokens = capacity, lastRefillMs = nowMs)
        }
        synchronized(bucket) {
            val elapsedSec = ((nowMs - bucket.lastRefillMs).coerceAtLeast(0L)) / 1000.0
            if (elapsedSec > 0.0) {
                bucket.tokens = min(capacity, bucket.tokens + elapsedSec * refillPerSecond)
                bucket.lastRefillMs = nowMs
            }
            return if (bucket.tokens >= cost) {
                bucket.tokens -= cost
                true
            } else {
                false
            }
        }
    }

    /**
     * Returns remaining PIN lockout seconds (> 0 if locked out, 0 if attempts allowed).
     */
    fun remainingPinLockoutSeconds(
        guardKey: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val state = pinFailures[guardKey] ?: return 0
        synchronized(state) {
            val remainingMs = state.lockedUntilMs - nowMs
            return if (remainingMs > 0L) ((remainingMs + 999L) / 1000L).toInt() else 0
        }
    }

    /**
     * Records a failed PIN attempt and triggers exponential cooldown after 5 consecutive failures.
     * Returns the lockout duration in seconds if locked out, or 0 if still within free tries.
     */
    fun recordFailedPinAttempt(
        guardKey: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val state = pinFailures.computeIfAbsent(guardKey) { PinFailureState() }
        synchronized(state) {
            state.consecutiveFailures += 1
            if (state.consecutiveFailures >= DEFAULT_MAX_PIN_ATTEMPTS) {
                val multiplier = 1L shl (state.consecutiveFailures - DEFAULT_MAX_PIN_ATTEMPTS).coerceAtMost(4)
                val lockoutDurationMs = (BASE_LOCKOUT_MS * multiplier).coerceAtMost(MAX_LOCKOUT_MS)
                state.lockedUntilMs = nowMs + lockoutDurationMs
                return ((lockoutDurationMs + 999L) / 1000L).toInt()
            }
            return 0
        }
    }

    fun recordSuccessfulPin(guardKey: String) {
        pinFailures.remove(guardKey)
    }

    fun resetForTesting() {
        buckets.clear()
        pinFailures.clear()
    }
}
