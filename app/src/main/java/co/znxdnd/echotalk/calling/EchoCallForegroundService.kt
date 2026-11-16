package co.znxdnd.echotalk.calling

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import co.znxdnd.echotalk.MainActivity
import co.znxdnd.echotalk.R

/**
 * Keeps active 1-to-1 voice/video call audio & camera transport alive across screen lock or multitasking.
 * Strictly guards foregroundServiceType against currently granted runtime permissions (Android 14+ compliance).
 */
class EchoCallForegroundService : Service() {

    companion object {
        private const val TAG = "EchoCallFGS"
        const val CHANNEL_ID = "echotalk_ongoing_call_channel"
        const val NOTIFICATION_ID = 4109

        const val ACTION_START = "co.znxdnd.echotalk.action.START_CALL_SERVICE"
        const val ACTION_UPDATE = "co.znxdnd.echotalk.action.UPDATE_CALL_SERVICE"
        const val ACTION_END_CALL = "co.znxdnd.echotalk.action.END_CALL_FROM_NOTIFICATION"

        const val EXTRA_PARTNER_NAME = "extra_partner_name"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_CALL_ID = "extra_call_id"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ongoing Voice & Video Calls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active call controls while in a voice or video conversation"
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }
        }

        fun startOrUpdate(
            context: Context,
            callId: String?,
            partnerName: String,
            callType: CallType,
            statusText: String
        ) {
            val appCtx = context.applicationContext
            val hasMic = ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                // Do not start a microphone foreground service before RECORD_AUDIO is granted
                return
            }

            createChannel(appCtx)
            val intent = Intent(appCtx, EchoCallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PARTNER_NAME, partnerName)
                putExtra(EXTRA_CALL_TYPE, callType.wireValue)
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to start ongoing call foreground service: ${t.message}")
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, EchoCallForegroundService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "Stop call service ignored: ${t.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_CALL) {
            CallController.getInstance().endCall()
            stopSelfCompat()
            return START_NOT_STICKY
        }

        val partnerName = intent?.getStringExtra(EXTRA_PARTNER_NAME).orEmpty().ifBlank { "EchoTalks Call" }
        val callType = CallType.fromWire(intent?.getStringExtra(EXTRA_CALL_TYPE))
        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT).orEmpty().ifBlank {
            if (callType == CallType.VIDEO) "Video call active" else "Voice call active"
        }
        val callId = intent?.getStringExtra(EXTRA_CALL_ID)

        createChannel(this)

        val openCallIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_ID", callId)
            putExtra("ACTION_OPEN_CALL", true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            901,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, EchoCallForegroundService::class.java).apply {
            action = ACTION_END_CALL
        }
        val endPendingIntent = PendingIntent.getService(
            this,
            902,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${callType.label} • $partnerName")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "End Call", endPendingIntent)
            .build()

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            stopSelf()
            return START_NOT_STICKY
        }

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (hasCamera && callType == CallType.VIDEO) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            type
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed cleanly: ${t.message}", t)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSelfCompat()
    }

    private fun stopSelfCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        runCatching { stopSelf() }
    }
}
