package co.znxdnd.echotalk.fcm

import android.content.Context
import android.util.Log
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * FcmNotificationManager: Manages FCM registration, Supabase device_tokens sync,
 * and local notification previews.
 */
object FcmNotificationManager {

    private const val TAG = "FcmNotificationManager"
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val _fcmToken = MutableStateFlow<String?>("fcm_token_${System.currentTimeMillis()}")
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    fun initialize(context: Context) {
        EchoTalkFirebaseMessagingService.createNotificationChannel(context)
        co.znxdnd.echotalk.calling.EchoCallForegroundService.createChannel(context)

        try {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrBlank()) {
                            _fcmToken.value = token
                            EchoTalkRepository.getInstance().setFcmToken(token)
                            syncTokenToBackend(token)
                            Log.i(TAG, "Fetched FCM Token: $token")
                        }
                    } else {
                        Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseMessaging not initialized in current environment: ${e.message}")
        }
    }

    fun syncCurrentTokenToBackend() {
        val token = _fcmToken.value ?: return
        if (!token.startsWith("fcm_token_")) {
            syncTokenToBackend(token)
        }
    }

    private fun syncTokenToBackend(token: String) {
        if (!SupabaseConfig.isConfigured) return
        ioScope.launch {
            SupabaseSocialRepository.getInstance().registerDeviceToken(token)
        }
    }

    /**
     * Sends a local notification to simulate an incoming push message.
     */
    fun simulateIncomingPush(
        context: Context,
        senderName: String,
        message: String,
        senderId: String? = null
    ) {
        EchoTalkFirebaseMessagingService.showNotification(
            context = context,
            title = "New message from $senderName",
            message = message,
            senderId = senderId
        )
    }
}
