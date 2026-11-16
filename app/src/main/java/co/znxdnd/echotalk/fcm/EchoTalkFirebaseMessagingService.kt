package co.znxdnd.echotalk.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import co.znxdnd.echotalk.MainActivity
import co.znxdnd.echotalk.R
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * EchoTalkFirebaseMessagingService: Handles Firebase Cloud Messaging (FCM) events
 * for incoming chat messages and token refreshes.
 */
class EchoTalkFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val TAG = "EchoTalkFCM"
        const val CHANNEL_ID = "echotalk_messages_channel"
        const val CHANNEL_NAME = "EchoTalk Chat Messages"
        const val CHANNEL_DESCRIPTION = "Real-time push notifications for incoming chat messages and 1v1 challenges"
        const val CHANNEL_CALLS_ID = "echotalk_calls_channel"
        const val CHANNEL_CALLS_NAME = "EchoTalks Voice & Video Calls"
        const val CHANNEL_CALLS_DESCRIPTION = "Incoming and missed 1-to-1 voice and video call alerts"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return

                val msgChannel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 150, 100, 200)
                    setShowBadge(true)
                }

                val callsChannel = NotificationChannel(
                    CHANNEL_CALLS_ID,
                    CHANNEL_CALLS_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_CALLS_DESCRIPTION
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 250, 400)
                    setShowBadge(true)
                }

                notificationManager.createNotificationChannel(msgChannel)
                notificationManager.createNotificationChannel(callsChannel)
                Log.d(TAG, "Notification channels created: $CHANNEL_ID, $CHANNEL_CALLS_ID")
            }
        }

        fun showNotification(
            context: Context,
            title: String,
            message: String,
            senderId: String? = null
        ) {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("ACTIVE_PARTNER_ID", senderId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 150, 100, 200))
                .build()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            try {
                notificationManager?.notify(notificationId, notification)
                Log.d(TAG, "Notification posted: $title - $message")
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification skipped (POST_NOTIFICATIONS permission not granted): ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Notification display fallback: ${e.message}")
            }
        }

        fun showIncomingCallNotification(
            context: Context,
            title: String,
            message: String,
            callId: String,
            callType: String,
            callerId: String?,
            callerName: String?
        ) {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("CALL_ID", callId)
                putExtra("CALL_TYPE", callType)
                putExtra("CALLER_ID", callerId)
                putExtra("CALLER_NAME", callerName)
                putExtra("ACTIVE_PARTNER_ID", callerId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                callId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 400, 250, 400))
                .build()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            try {
                notificationManager?.notify(callId.hashCode(), notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Call notification skipped (POST_NOTIFICATIONS not granted): ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Call notification fallback: ${e.message}")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Registration Token: $token")
        EchoTalkRepository.getInstance().setFcmToken(token)
        if (co.znxdnd.echotalk.data.remote.SupabaseConfig.isConfigured) {
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() +
                    kotlinx.coroutines.Dispatchers.IO +
                    co.znxdnd.echotalk.utils.ResilienceGuardian.safeScopeExceptionHandler(TAG)
            ).launch {
                co.znxdnd.echotalk.data.remote.SupabaseSocialRepository.getInstance().registerDeviceToken(token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val notificationType = remoteMessage.data["notification_type"] ?: ""
        val callId = remoteMessage.data["call_id"]
        val callType = remoteMessage.data["call_type"] ?: "audio"

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: remoteMessage.data["sender_name"]
            ?: "New Message on EchoTalk"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["text"]
            ?: "You have a new message waiting."

        val senderId = remoteMessage.data["sender_id"] ?: "u-unknown"
        val senderName = remoteMessage.data["sender_name"] ?: title

        if (notificationType == "incoming_call" && !callId.isNullOrBlank()) {
            co.znxdnd.echotalk.calling.CallController.initialize(applicationContext)
            co.znxdnd.echotalk.calling.CallController.getInstance().handleNotificationOrDeepLinkIntent(
                callId = callId,
                callTypeWire = callType,
                callerId = senderId.takeIf { it != "u-unknown" },
                callerName = senderName
            )
            showIncomingCallNotification(
                context = applicationContext,
                title = title,
                message = body,
                callId = callId,
                callType = callType,
                callerId = senderId.takeIf { it != "u-unknown" },
                callerName = senderName
            )
            return
        }

        if (notificationType == "missed_call") {
            co.znxdnd.echotalk.utils.EchoHapticManager.getInstance(applicationContext).performAlertReceived()
            showIncomingCallNotification(
                context = applicationContext,
                title = title,
                message = body,
                callId = callId ?: "missed-${System.currentTimeMillis()}",
                callType = callType,
                callerId = senderId.takeIf { it != "u-unknown" },
                callerName = senderName
            )
            return
        }

        val msgId = remoteMessage.data["message_id"] ?: "fcm-${System.currentTimeMillis()}"
        val chatMsg = co.znxdnd.echotalk.data.model.ChatMessage(
            id = msgId,
            sender = senderName,
            text = body,
            timestamp = "Just now",
            status = "delivered"
        )
        if (senderId.isNotBlank() && senderId != "u-unknown") {
            EchoTalkRepository.getInstance().cacheIncomingMessage(senderId, chatMsg)
        }

        co.znxdnd.echotalk.utils.EchoHapticManager.getInstance(applicationContext).performAlertReceived()
        showNotification(applicationContext, title, body, senderId)
    }
}
