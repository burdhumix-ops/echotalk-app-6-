package co.znxdnd.echotalk.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import co.znxdnd.echotalk.utils.ResilienceGuardian
import co.znxdnd.echotalk.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

enum class ConflictStrategy {
    SERVER_WINS,
    CLIENT_WINS,
    LATEST_TIMESTAMP_WINS
}

data class QueuedOperation(
    val id: String,
    val operationType: String,
    val payload: String,
    val timestamp: Long,
    val status: String = "PENDING",
    val retryCount: Int = 0
)

/**
 * SyncManager: Coordinates offline synchronization, WorkManager job dispatch,
 * server timestamp conflict resolution, and sync status observation.
 */
class SyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private const val PREFS_NAME = "echotalk_sync_prefs"
        private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"

        @Volatile
        private var instance: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + ResilienceGuardian.safeScopeExceptionHandler(TAG)
    )
    private val queueManager = OfflineQueueManager.getInstance(context)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_TIME, 0L))
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val pendingCount: Flow<Int> = queueManager.pendingTasks.map { it.size }

    init {
        // Schedule WorkManager periodic sync to run reliably in background
        try {
            SyncWorker.schedulePeriodicSync(context)
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager periodic sync setup fallback: ${e.message}")
        }
    }

    fun triggerSyncNow() {
        if (_syncState.value == SyncState.SYNCING) return
        _syncState.value = SyncState.SYNCING
        _lastError.value = null

        scope.launch {
            try {
                try {
                    SyncWorker.enqueueImmediateSync(context)
                } catch (e: Exception) {
                    Log.w(TAG, "SyncWorker immediate queue fallback: ${e.message}")
                }

                if (!co.znxdnd.echotalk.data.remote.SupabaseConfig.isConfigured) {
                    _lastError.value = "Supabase backend not configured yet"
                    _syncState.value = SyncState.IDLE
                    return@launch
                }

                if (!queueManager.isConnected.value) {
                    _lastError.value = "Offline — pending messages remain queued"
                    _syncState.value = SyncState.ERROR
                    return@launch
                }

                val count = queueManager.flushQueue()
                val remaining = queueManager.getPendingCount()
                if (remaining == 0) {
                    val now = System.currentTimeMillis()
                    _lastSyncTimestamp.value = now
                    prefs.edit().putLong(KEY_LAST_SYNC_TIME, now).apply()
                    _syncState.value = SyncState.SUCCESS
                    Log.d(TAG, "Sync finished: $count operations processed at $now")
                } else {
                    _lastError.value = "$remaining queued item(s) still waiting to sync"
                    _syncState.value = SyncState.ERROR
                }
            } catch (e: Exception) {
                ResilienceGuardian.reportNonFatal(TAG, e)
                _lastError.value = e.message ?: "Unknown sync error"
                _syncState.value = SyncState.ERROR
            }
        }
    }

    /**
     * Resolves potential data conflict based on server and client timestamps.
     */
    fun resolveConflict(
        clientTimestamp: Long,
        serverTimestamp: Long,
        strategy: ConflictStrategy = ConflictStrategy.LATEST_TIMESTAMP_WINS
    ): Boolean {
        return when (strategy) {
            ConflictStrategy.SERVER_WINS -> false // Server wins, ignore client
            ConflictStrategy.CLIENT_WINS -> true  // Client overrides
            ConflictStrategy.LATEST_TIMESTAMP_WINS -> clientTimestamp >= serverTimestamp
        }
    }
}
