package co.znxdnd.echotalk.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import co.znxdnd.echotalk.data.local.AppRoomDatabase
import co.znxdnd.echotalk.data.local.OfflineQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * SyncWorker: WorkManager background worker that synchronizes queued offline operations
 * with network constraints, bounded retries, and exponential backoff.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "echotalk_offline_sync_work"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun enqueueImmediateSync(context: Context): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .addTag("immediate_sync")
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            return oneTimeRequest
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting background sync job (attempt: $runAttemptCount)...")
        if (runAttemptCount > 5) {
            Log.w(TAG, "Exceeded maximum retry attempts (5). Aborting worker.")
            return@withContext Result.failure()
        }

        return@withContext try {
            val queueManager = OfflineQueueManager.getInstance(applicationContext)
            if (!co.znxdnd.echotalk.data.remote.SupabaseConfig.isConfigured ||
                co.znxdnd.echotalk.utils.ResilienceGuardian.isSchemaCircuitOpen()
            ) {
                return@withContext Result.success()
            }
            val flushedCount = queueManager.flushQueue()
            val remaining = queueManager.getPendingCount()
            if (remaining > 0) {
                Log.w(TAG, "SyncWorker flushed $flushedCount, $remaining still pending — scheduling retry.")
                Result.retry()
            } else {
                Log.d(TAG, "SyncWorker completed successfully: flushed $flushedCount operations.")
                Result.success()
            }
        } catch (e: Exception) {
            co.znxdnd.echotalk.utils.ResilienceGuardian.reportNonFatal(TAG, e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
