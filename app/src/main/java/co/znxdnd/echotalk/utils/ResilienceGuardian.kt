package co.znxdnd.echotalk.utils

import android.content.Context
import android.util.Log
import co.znxdnd.echotalk.data.local.OfflineQueueManager
import co.znxdnd.echotalk.data.remote.SupabaseClientProvider
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class BackendSchemaState {
    UNKNOWN,
    HEALTHY,
    SCHEMA_MISSING,
    DEGRADED
}

data class ResilienceStatus(
    val schemaState: BackendSchemaState = BackendSchemaState.UNKNOWN,
    val lastHealAction: String? = null,
    val autoHealedCount: Int = 0,
    val hadPreviousUncleanExit: Boolean = false,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

class SchemaNotInitializedException(
    message: String = "Supabase schema not initialized yet (PGRST205). Run 0001_initial_schema.sql and 0002_admin_role_security.sql in Supabase SQL Editor."
) : IllegalStateException(message)

/**
 * ResilienceGuardian: Unified self-healing body for EchoTalks.
 * Prevents cascading errors and automatically recovers from:
 * - Missing remote schema cache (PGRST205 circuit breaker + queued local continuity)
 * - Expired Supabase JWT sessions (coalesced auto-refresh + retry)
 * - Transient network / rate-limit errors (bounded exponential backoff)
 * - Dropped WebSocket / Realtime presence channels on network transitions
 * - Stuck Room offline-queue tasks & stale temp files on crash/restart
 * - Unhandled coroutine & thread exceptions
 */
object ResilienceGuardian {

    private const val TAG = "ResilienceGuardian"
    private const val PREFS_NAME = "echotalk_resilience_prefs"
    private const val KEY_UNCLEAN_EXIT = "unclean_exit_detected"
    private const val KEY_LAST_CRASH_MSG = "last_crash_message"
    private const val SCHEMA_COOLDOWN_MS = 60_000L
    private const val TEMP_FILE_TTL_MS = 24L * 60L * 60L * 1000L

    @Volatile
    private var appContext: Context? = null

    private val crashShieldInstalled = AtomicBoolean(false)
    private val schemaOpenedAtMs = AtomicLong(0L)
    private val refreshMutex = Mutex()
    private val repairMutex = Mutex()
    private val guardianScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + safeScopeExceptionHandler("ResilienceGuardian")
    )

    private val _status = MutableStateFlow(ResilienceStatus())
    val status: StateFlow<ResilienceStatus> = _status.asStateFlow()

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun safeScopeExceptionHandler(sourceTag: String): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            if (throwable is CancellationException) return@CoroutineExceptionHandler
            reportNonFatal(sourceTag, throwable)
        }
    }

    fun installCrashShield(context: Context) {
        init(context)
        if (!crashShieldInstalled.compareAndSet(false, true)) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousUnclean = prefs.getBoolean(KEY_UNCLEAN_EXIT, false)
        if (previousUnclean) {
            val prevMsg = prefs.getString(KEY_LAST_CRASH_MSG, null)
            Log.w(TAG, "Recovered from previous unclean exit: $prevMsg")
            _status.value = _status.value.copy(
                hadPreviousUncleanExit = true,
                lastHealAction = "Recovered state after unexpected termination",
                lastUpdatedMs = System.currentTimeMillis()
            )
            prefs.edit().putBoolean(KEY_UNCLEAN_EXIT, false).apply()
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                prefs.edit()
                    .putBoolean(KEY_UNCLEAN_EXIT, true)
                    .putString(KEY_LAST_CRASH_MSG, "${throwable.javaClass.simpleName}: ${throwable.message}")
                    .commit()
                appContext?.let { ctx ->
                    AppHealthMonitor.getInstance(ctx).recordException("Uncaught:${thread.name}", throwable)
                }
            } catch (_: Throwable) {
                // Never mask original exception inside handler cleanup
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun isSchemaCircuitOpen(nowMs: Long = System.currentTimeMillis()): Boolean {
        val openedAt = schemaOpenedAtMs.get()
        if (openedAt == 0L) return false
        val stillOpen = (nowMs - openedAt) < SCHEMA_COOLDOWN_MS
        if (!stillOpen) {
            schemaOpenedAtMs.compareAndSet(openedAt, 0L)
        }
        return stillOpen
    }

    fun markSchemaMissing(sourceTag: String, detail: String? = null) {
        val now = System.currentTimeMillis()
        val prev = schemaOpenedAtMs.getAndSet(now)
        if (prev == 0L || (now - prev) >= SCHEMA_COOLDOWN_MS) {
            Log.w(
                TAG,
                "[$sourceTag] Supabase schema not initialized yet (PGRST205). " +
                    "Pausing duplicate schema writes for ${SCHEMA_COOLDOWN_MS / 1000}s; local data & offline queue stay active. " +
                    (detail ?: "")
            )
        }
        recordHealStatus(
            schemaState = BackendSchemaState.SCHEMA_MISSING,
            action = "Schema cooldown active; edits queued locally"
        )
    }

    fun markSchemaHealthy() {
        schemaOpenedAtMs.set(0L)
        if (_status.value.schemaState != BackendSchemaState.HEALTHY) {
            _status.value = _status.value.copy(
                schemaState = BackendSchemaState.HEALTHY,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }
    }

    fun resetSchemaCircuitBreaker() {
        schemaOpenedAtMs.set(0L)
        if (_status.value.schemaState == BackendSchemaState.SCHEMA_MISSING ||
            _status.value.schemaState == BackendSchemaState.DEGRADED
        ) {
            _status.value = _status.value.copy(
                schemaState = BackendSchemaState.UNKNOWN,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }
    }

    fun reportNonFatal(sourceTag: String, throwable: Throwable, markHealed: Boolean = false) {
        val category = ErrorRecoveryManager.classifyError(throwable)
        when (category) {
            ErrorCategory.SCHEMA_MISSING -> markSchemaMissing(sourceTag, throwable.message)
            ErrorCategory.AUTHENTICATION -> {
                Log.w(TAG, "[$sourceTag] Auth precondition skipped: ${throwable.message}")
            }
            else -> {
                Log.w(TAG, "[$sourceTag] Handled $category: ${throwable.message}")
                appContext?.let { ctx ->
                    AppHealthMonitor.getInstance(ctx).recordException(sourceTag, throwable)
                }
            }
        }
        if (markHealed) {
            recordHealStatus(
                schemaState = _status.value.schemaState,
                action = "Auto-recovered [$sourceTag]"
            )
        }
    }

    /**
     * Executes a Supabase call with schema-cache circuit breaking, coalesced JWT auto-refresh on 401,
     * and bounded exponential backoff for transient network / rate-limit failures.
     */
    suspend fun <T> executeSupabaseCall(
        operationName: String,
        bypassSchemaCircuit: Boolean = false,
        maxTransientRetries: Int = 2,
        block: suspend () -> T
    ): Result<T> {
        if (!bypassSchemaCircuit && isSchemaCircuitOpen()) {
            return Result.failure(SchemaNotInitializedException())
        }

        var authRefreshed = false
        val attemptResult = ErrorRecoveryManager.executeWithRetry(
            maxAttempts = (maxTransientRetries + 1).coerceAtLeast(1),
            initialDelayMs = 350L,
            maxDelayMs = 2500L,
            operationName = operationName
        ) {
            try {
                val res = block()
                markSchemaHealthy()
                appContext?.let { ctx ->
                    AppHealthMonitor.getInstance(ctx).recordOperation(true, operationName)
                }
                res
            } catch (t: Throwable) {
                val category = ErrorRecoveryManager.classifyError(t)
                if (category == ErrorCategory.AUTH_EXPIRED && !authRefreshed) {
                    authRefreshed = true
                    val refreshed = tryRefreshSupabaseSession()
                    if (refreshed) {
                        recordHealStatus(
                            schemaState = _status.value.schemaState,
                            action = "Refreshed expired Supabase session automatically"
                        )
                        val retryAfterRefresh = block()
                        markSchemaHealthy()
                        return@executeWithRetry retryAfterRefresh
                    }
                }
                if (category == ErrorCategory.SCHEMA_MISSING) {
                    markSchemaMissing(operationName, t.message)
                }
                throw t
            }
        }

        if (attemptResult.isFailure) {
            attemptResult.exceptionOrNull()?.let { err ->
                reportNonFatal(operationName, err)
            }
        }
        return attemptResult
    }

    private suspend fun tryRefreshSupabaseSession(): Boolean = refreshMutex.withLock {
        val client = SupabaseClientProvider.getClientOrNull() ?: return@withLock false
        return@withLock try {
            client.auth.refreshCurrentSession()
            client.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            Log.w(TAG, "Auto session refresh skipped: ${e.message}")
            false
        }
    }

    fun runStartupSelfRepair(
        context: Context,
        repository: EchoTalkRepository,
        queueManager: OfflineQueueManager
    ) {
        init(context)
        installCrashShield(context)
        guardianScope.launch {
            repairMutex.withLock {
                try {
                    val sanitized = repository.sanitizeCurrentUserInvariants()
                    val recoveredTasks = queueManager.recoverStuckTasks()
                    val cleanedFiles = pruneStaleTempFiles(context.cacheDir)
                    if (sanitized || recoveredTasks > 0 || cleanedFiles > 0) {
                        recordHealStatus(
                            schemaState = _status.value.schemaState,
                            action = "Startup self-repair: tasks=$recoveredTasks, cacheFiles=$cleanedFiles, userSanitized=$sanitized"
                        )
                    }
                } catch (t: Throwable) {
                    reportNonFatal("StartupSelfRepair", t)
                }
            }
        }
    }

    fun onAppForegrounded(
        context: Context,
        repository: EchoTalkRepository,
        queueManager: OfflineQueueManager
    ) {
        init(context)
        guardianScope.launch {
            repairMutex.withLock {
                try {
                    val recovered = queueManager.recoverStuckTasks()
                    val socialRepo = SupabaseSocialRepository.getInstance()
                    if (SupabaseConfig.isConfigured && socialRepo.currentUid() != null) {
                        val s = repository.settings.value
                        socialRepo.ensureRealtimePresenceConnected(
                            ghostMode = s.ghostMode,
                            showOnlineStatus = s.onlineStatus
                        )
                        if (recovered > 0 || !isSchemaCircuitOpen()) {
                            queueManager.flushQueue()
                        }
                    }
                } catch (t: Throwable) {
                    reportNonFatal("OnAppForegrounded", t)
                }
            }
        }
    }

    fun onNetworkRestored(
        repository: EchoTalkRepository = EchoTalkRepository.getInstance(),
        socialRepo: SupabaseSocialRepository = SupabaseSocialRepository.getInstance()
    ) {
        guardianScope.launch {
            try {
                resetSchemaCircuitBreaker()
                if (SupabaseConfig.isConfigured && socialRepo.currentUid() != null) {
                    val s = repository.settings.value
                    socialRepo.ensureRealtimePresenceConnected(
                        ghostMode = s.ghostMode,
                        showOnlineStatus = s.onlineStatus
                    )
                    socialRepo.updatePresenceHeartbeat(
                        online = s.onlineStatus,
                        ghostMode = s.ghostMode,
                        force = true
                    )
                    repository.flushPendingQueue()
                }
                recordHealStatus(
                    schemaState = _status.value.schemaState,
                    action = "Network restored: reconnected realtime & flushed offline queue"
                )
            } catch (t: Throwable) {
                reportNonFatal("OnNetworkRestored", t)
            }
        }
    }

    private fun pruneStaleTempFiles(cacheDir: File?): Int {
        if (cacheDir == null || !cacheDir.exists()) return 0
        val cutoff = System.currentTimeMillis() - TEMP_FILE_TTL_MS
        var deleted = 0
        val prefixes = listOf("echotalk_temp_", "compressed_", "voice_tmp_")
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile &&
                prefixes.any { file.name.startsWith(it) } &&
                file.lastModified() in 1 until cutoff
            ) {
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    deleted++
                }
            }
        }
        return deleted
    }

    private fun recordHealStatus(schemaState: BackendSchemaState, action: String) {
        val current = _status.value
        _status.value = current.copy(
            schemaState = schemaState,
            lastHealAction = action,
            autoHealedCount = current.autoHealedCount + 1,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    fun resetForTesting() {
        schemaOpenedAtMs.set(0L)
        _status.value = ResilienceStatus()
    }
}
