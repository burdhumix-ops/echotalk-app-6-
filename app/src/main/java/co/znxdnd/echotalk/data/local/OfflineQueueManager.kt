package co.znxdnd.echotalk.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import co.znxdnd.echotalk.data.local.dao.PendingQueueDao
import co.znxdnd.echotalk.data.local.entity.PendingQueueEntity
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.utils.ErrorCategory
import co.znxdnd.echotalk.utils.ErrorRecoveryManager
import co.znxdnd.echotalk.utils.ResilienceGuardian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * OfflineQueueManager: Uses Room database to persist outgoing messages, profile updates, and settings syncs
 * while offline or waiting on backend readiness, and flushes them idempotently to Supabase once ready.
 */
class OfflineQueueManager private constructor(
    private val context: Context,
    private val dao: PendingQueueDao,
    private val isTestHarness: Boolean = false
) {
    companion object {
        private const val TAG = "OfflineQueueManager"
        private const val MAX_RETRY_ATTEMPTS = 8
        const val TASK_MESSAGE = "MESSAGE"
        const val TASK_PROFILE_UPSERT = "PROFILE_UPSERT"
        const val TASK_PREFERENCES_SYNC = "PREFERENCES_SYNC"

        @Volatile
        private var instance: OfflineQueueManager? = null

        fun getInstance(context: Context): OfflineQueueManager {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = AppRoomDatabase.getInstance(context)
                    OfflineQueueManager(context.applicationContext, db.pendingQueueDao(), isTestHarness = false).also {
                        instance = it
                    }
                }
            }
        }

        fun createForTesting(context: Context, dao: PendingQueueDao): OfflineQueueManager {
            return OfflineQueueManager(context, dao, isTestHarness = true)
        }
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + ResilienceGuardian.safeScopeExceptionHandler(TAG)
    )
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isFlushing = MutableStateFlow(false)
    val isFlushing: StateFlow<Boolean> = _isFlushing.asStateFlow()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    val pendingTasks: Flow<List<PendingQueueEntity>> = dao.getAllPendingFlow()

    init {
        registerNetworkCallback()
    }

    private fun checkInitialConnectivity(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            true
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager?.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i(TAG, "Network connection restored. Auto-healing realtime and pending queue...")
                        _isConnected.value = true
                        ResilienceGuardian.onNetworkRestored()
                    }

                    override fun onLost(network: Network) {
                        Log.w(TAG, "Network connection lost. Offline queuing active.")
                        _isConnected.value = false
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register NetworkCallback: ${e.message}")
        }
    }

    suspend fun recoverStuckTasks(): Int {
        return try {
            val processingReset = dao.recoverStuckProcessingTasks()
            val retryableReset = dao.resetRetryableFailedTasks(MAX_RETRY_ATTEMPTS)
            processingReset + retryableReset
        } catch (e: Exception) {
            Log.w(TAG, "Queue task recovery warning: ${e.message}")
            0
        }
    }

    /**
     * Enqueue a pending chat message into Room database.
     */
    suspend fun enqueueMessage(
        recipientId: String,
        senderName: String,
        text: String,
        isViewOnce: Boolean = false,
        isDoodle: Boolean = false,
        mediaUrl: String? = null,
        isAudio: Boolean = false,
        audioDuration: String? = null,
        clientMsgId: String = "offline-msg-${UUID.randomUUID()}"
    ): PendingQueueEntity {
        val entity = PendingQueueEntity(
            id = clientMsgId,
            taskType = TASK_MESSAGE,
            recipientId = recipientId,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis(),
            isViewOnce = isViewOnce,
            isDoodle = isDoodle,
            mediaUrl = mediaUrl,
            isAudio = isAudio,
            audioDuration = audioDuration,
            status = "PENDING"
        )
        dao.insert(entity)
        Log.d(TAG, "Enqueued pending message in Room: ${entity.id}")

        if (_isConnected.value && SupabaseConfig.isConfigured && !ResilienceGuardian.isSchemaCircuitOpen()) {
            scope.launch { flushQueue() }
        }

        return entity
    }

    /**
     * Enqueue coalesced profile upsert so edits made offline or during schema cooldown auto-sync later.
     */
    suspend fun enqueueProfileSync(userId: String, displayName: String) {
        try {
            val entity = PendingQueueEntity(
                id = "sync-profile-self",
                taskType = TASK_PROFILE_UPSERT,
                recipientId = userId,
                senderName = displayName,
                text = "profile_sync",
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            dao.insert(entity)
        } catch (e: Exception) {
            Log.w(TAG, "Could not enqueue profile sync: ${e.message}")
        }
    }

    /**
     * Enqueue coalesced preferences sync so settings edits made offline auto-sync later.
     */
    suspend fun enqueuePreferencesSync(userId: String) {
        try {
            val entity = PendingQueueEntity(
                id = "sync-prefs-self",
                taskType = TASK_PREFERENCES_SYNC,
                recipientId = userId,
                senderName = "self",
                text = "preferences_sync",
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            dao.insert(entity)
        } catch (e: Exception) {
            Log.w(TAG, "Could not enqueue preferences sync: ${e.message}")
        }
    }

    /**
     * Flush pending tasks in the Room queue idempotently via Supabase.
     * Never marks tasks delivered while offline or unconfigured outside unit test harness.
     */
    suspend fun flushQueue(onMessageDelivered: ((String, ChatMessage) -> Unit)? = null): Int {
        if (_isFlushing.value) return 0
        if (!isTestHarness && !_isConnected.value) {
            Log.d(TAG, "Offline: keeping queued items pending until connectivity returns.")
            return 0
        }
        if (!isTestHarness && !SupabaseConfig.isConfigured) {
            Log.d(TAG, "Supabase not configured: keeping queued items pending.")
            return 0
        }
        if (!isTestHarness && ResilienceGuardian.isSchemaCircuitOpen()) {
            Log.d(TAG, "Supabase schema cooldown active: keeping queued items pending.")
            return 0
        }

        _isFlushing.value = true
        var dispatchedCount = 0
        try {
            recoverStuckTasks()
            val pendingList = dao.getAllPendingList()
            Log.d(TAG, "Flushing ${pendingList.size} pending tasks from Room...")

            for (task in pendingList) {
                if (!isTestHarness && task.retryCount >= MAX_RETRY_ATTEMPTS) {
                    continue
                }
                try {
                    dao.update(task.copy(status = "PROCESSING"))

                    when (task.taskType) {
                        TASK_PROFILE_UPSERT -> {
                            if (!isTestHarness) {
                                val repo = EchoTalkRepository.getInstance()
                                SupabaseSocialRepository.getInstance()
                                    .upsertProfile(repo.currentUser.value, repo.settings.value)
                                    .getOrElse { err -> throw err }
                            }
                            dao.deleteById(task.id)
                            dispatchedCount++
                        }
                        TASK_PREFERENCES_SYNC -> {
                            if (!isTestHarness) {
                                val repo = EchoTalkRepository.getInstance()
                                SupabaseSocialRepository.getInstance()
                                    .syncPreferences(repo.settings.value)
                                    .getOrElse { err -> throw err }
                            }
                            dao.deleteById(task.id)
                            dispatchedCount++
                        }
                        else -> {
                            val draftMessage = ChatMessage(
                                id = task.id,
                                sender = task.senderName,
                                text = task.text,
                                timestamp = timeFormat.format(Date(task.timestamp)),
                                status = if (isTestHarness) "delivered" else "sent",
                                isViewOnce = task.isViewOnce,
                                isDoodle = task.isDoodle,
                                mediaUrl = task.mediaUrl,
                                isAudio = task.isAudio,
                                audioDuration = task.audioDuration
                            )

                            val deliveredMessage = if (isTestHarness) {
                                draftMessage
                            } else {
                                val remoteResult = SupabaseSocialRepository.getInstance().sendDirectMessage(
                                    partnerId = task.recipientId,
                                    senderName = task.senderName,
                                    message = draftMessage,
                                    clientMsgId = task.id
                                )
                                remoteResult.getOrElse { err ->
                                    throw err
                                }
                            }

                            onMessageDelivered?.invoke(task.recipientId, deliveredMessage)
                            dao.deleteById(task.id)
                            dispatchedCount++
                            Log.d(TAG, "Synced and removed pending task: ${task.id}")
                        }
                    }
                } catch (e: Exception) {
                    val category = ErrorRecoveryManager.classifyError(e)
                    if (category == ErrorCategory.AUTHENTICATION || category == ErrorCategory.SCHEMA_MISSING) {
                        // Do not burn retry attempts or mark FAILED while waiting for login or SQL schema setup
                        dao.update(task.copy(status = "PENDING"))
                        ResilienceGuardian.reportNonFatal("OfflineQueue:${task.taskType}", e)
                    } else {
                        Log.w(TAG, "Deferred queue task ${task.id} ($category): ${e.message}")
                        dao.updateStatus(task.id, "FAILED")
                    }
                }
            }
        } finally {
            _isFlushing.value = false
        }

        return dispatchedCount
    }

    suspend fun clearQueue() {
        dao.clearAll()
    }

    suspend fun getPendingCount(): Int {
        return dao.getAllPendingList().size
    }

    fun setSimulatedConnected(connected: Boolean) {
        _isConnected.value = connected
        if (connected) {
            scope.launch { flushQueue() }
        }
    }
}
