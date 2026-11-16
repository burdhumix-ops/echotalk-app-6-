package co.znxdnd.echotalk.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import co.znxdnd.echotalk.data.repository.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "echotalk_settings")

/**
 * Immutable data snapshot representing all persistent user settings.
 */
data class EchoSettings(
    val theme: AppTheme = AppTheme.IOS_GLASS,
    val ghostMode: Boolean = false,
    val profileVisibility: Boolean = true,
    val onlineStatus: Boolean = true,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val dmPermissions: String = "Everyone", // "Everyone" or "Approved Connections"
    val blockedUsers: Set<String> = emptySet(),
    val notifyDirectMessages: Boolean = true,
    val notifyMessageRequests: Boolean = true,
    val notifyConnectionRequests: Boolean = true,
    val notifyRoomInvitations: Boolean = true,
    val notifyRoomMentions: Boolean = true,
    val notifyIncomingCalls: Boolean = true,
    val notifyMissedCalls: Boolean = true,
    val notifySystem: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val chatWallpaper: String = "default",
    val chatTextSize: String = "Medium", // "Small" (14sp), "Medium" (16sp), "Large" (18sp)
    val reduceAnimations: Boolean = false,
    val enterKeySends: Boolean = false,
    val showTimestamps: Boolean = true,
    val autoDownloadImages: Boolean = true,
    val autoDownloadAudio: Boolean = true,
    val allowRoomInvitations: Boolean = true,
    val allowIncomingCalls: Boolean = true,
    val muteRoomNotifications: Boolean = false
) {
    fun getTextSizeSp(): Float {
        return when (chatTextSize.lowercase()) {
            "small" -> 14f
            "large" -> 18f
            else -> 16f
        }
    }
}

/**
 * SettingsDataStore: Android DataStore Preferences persistence engine.
 * Guarantees thread-safe, non-blocking asynchronous preference persistence
 * loaded on application startup and observed via StateFlow in Compose.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_THEME = stringPreferencesKey("app_theme")
        private val KEY_GHOST_MODE = booleanPreferencesKey("ghost_mode")
        private val KEY_PROFILE_VISIBILITY = booleanPreferencesKey("profile_visibility")
        private val KEY_ONLINE_STATUS = booleanPreferencesKey("online_status")
        private val KEY_READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        private val KEY_TYPING_INDICATORS = booleanPreferencesKey("typing_indicators")
        private val KEY_DM_PERMISSIONS = stringPreferencesKey("dm_permissions")
        private val KEY_BLOCKED_USERS = stringSetPreferencesKey("blocked_users")

        private val KEY_NOTIFY_DM = booleanPreferencesKey("notify_dm")
        private val KEY_NOTIFY_MSG_REQ = booleanPreferencesKey("notify_msg_req")
        private val KEY_NOTIFY_CONN_REQ = booleanPreferencesKey("notify_conn_req")
        private val KEY_NOTIFY_ROOM_INVITE = booleanPreferencesKey("notify_room_invite")
        private val KEY_NOTIFY_ROOM_MENTIONS = booleanPreferencesKey("notify_room_mentions")
        private val KEY_NOTIFY_INCOMING_CALL = booleanPreferencesKey("notify_incoming_call")
        private val KEY_NOTIFY_MISSED_CALL = booleanPreferencesKey("notify_missed_call")
        private val KEY_NOTIFY_SYSTEM = booleanPreferencesKey("notify_system")
        private val KEY_NOTIFY_SOUND = booleanPreferencesKey("notify_sound")
        private val KEY_NOTIFY_VIBRATION = booleanPreferencesKey("notify_vibration")

        private val KEY_CHAT_WALLPAPER = stringPreferencesKey("chat_wallpaper")
        private val KEY_CHAT_TEXT_SIZE = stringPreferencesKey("chat_text_size")
        private val KEY_REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")

        private val KEY_ENTER_KEY_SENDS = booleanPreferencesKey("enter_key_sends")
        private val KEY_SHOW_TIMESTAMPS = booleanPreferencesKey("show_timestamps")
        private val KEY_AUTO_DOWNLOAD_IMAGES = booleanPreferencesKey("auto_download_images")
        private val KEY_AUTO_DOWNLOAD_AUDIO = booleanPreferencesKey("auto_download_audio")

        private val KEY_ALLOW_ROOM_INVITES = booleanPreferencesKey("allow_room_invites")
        private val KEY_ALLOW_INCOMING_CALLS = booleanPreferencesKey("allow_incoming_calls")
        private val KEY_MUTE_ROOM_NOTIFICATIONS = booleanPreferencesKey("mute_room_notifications")

        @Volatile
        private var instance: SettingsDataStore? = null

        fun getInstance(context: Context): SettingsDataStore {
            return instance ?: synchronized(this) {
                instance ?: SettingsDataStore(context.applicationContext).also { instance = it }
            }
        }
    }

    val settingsFlow: Flow<EchoSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val themeStr = prefs[KEY_THEME] ?: AppTheme.IOS_GLASS.name
            val theme = try {
                AppTheme.valueOf(themeStr)
            } catch (_: Exception) {
                AppTheme.IOS_GLASS
            }

            EchoSettings(
                theme = theme,
                ghostMode = prefs[KEY_GHOST_MODE] ?: false,
                profileVisibility = prefs[KEY_PROFILE_VISIBILITY] ?: true,
                onlineStatus = prefs[KEY_ONLINE_STATUS] ?: true,
                readReceipts = prefs[KEY_READ_RECEIPTS] ?: true,
                typingIndicators = prefs[KEY_TYPING_INDICATORS] ?: true,
                dmPermissions = prefs[KEY_DM_PERMISSIONS] ?: "Everyone",
                blockedUsers = prefs[KEY_BLOCKED_USERS] ?: emptySet(),
                notifyDirectMessages = prefs[KEY_NOTIFY_DM] ?: true,
                notifyMessageRequests = prefs[KEY_NOTIFY_MSG_REQ] ?: true,
                notifyConnectionRequests = prefs[KEY_NOTIFY_CONN_REQ] ?: true,
                notifyRoomInvitations = prefs[KEY_NOTIFY_ROOM_INVITE] ?: true,
                notifyRoomMentions = prefs[KEY_NOTIFY_ROOM_MENTIONS] ?: true,
                notifyIncomingCalls = prefs[KEY_NOTIFY_INCOMING_CALL] ?: true,
                notifyMissedCalls = prefs[KEY_NOTIFY_MISSED_CALL] ?: true,
                notifySystem = prefs[KEY_NOTIFY_SYSTEM] ?: true,
                notificationSound = prefs[KEY_NOTIFY_SOUND] ?: true,
                notificationVibration = prefs[KEY_NOTIFY_VIBRATION] ?: true,
                chatWallpaper = prefs[KEY_CHAT_WALLPAPER] ?: "default",
                chatTextSize = prefs[KEY_CHAT_TEXT_SIZE] ?: "Medium",
                reduceAnimations = prefs[KEY_REDUCE_ANIMATIONS] ?: false,
                enterKeySends = prefs[KEY_ENTER_KEY_SENDS] ?: false,
                showTimestamps = prefs[KEY_SHOW_TIMESTAMPS] ?: true,
                autoDownloadImages = prefs[KEY_AUTO_DOWNLOAD_IMAGES] ?: true,
                autoDownloadAudio = prefs[KEY_AUTO_DOWNLOAD_AUDIO] ?: true,
                allowRoomInvitations = prefs[KEY_ALLOW_ROOM_INVITES] ?: true,
                allowIncomingCalls = prefs[KEY_ALLOW_INCOMING_CALLS] ?: true,
                muteRoomNotifications = prefs[KEY_MUTE_ROOM_NOTIFICATIONS] ?: false
            )
        }

    suspend fun updateTheme(theme: AppTheme) {
        context.settingsDataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun updateGhostMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_GHOST_MODE] = enabled }
    }

    suspend fun updateProfileVisibility(visible: Boolean) {
        context.settingsDataStore.edit { it[KEY_PROFILE_VISIBILITY] = visible }
    }

    suspend fun updateOnlineStatus(online: Boolean) {
        context.settingsDataStore.edit { it[KEY_ONLINE_STATUS] = online }
    }

    suspend fun updateReadReceipts(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_READ_RECEIPTS] = enabled }
    }

    suspend fun updateTypingIndicators(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_TYPING_INDICATORS] = enabled }
    }

    suspend fun updateDmPermissions(perm: String) {
        context.settingsDataStore.edit { it[KEY_DM_PERMISSIONS] = perm }
    }

    suspend fun blockUser(userId: String) {
        context.settingsDataStore.edit {
            val current = it[KEY_BLOCKED_USERS] ?: emptySet()
            it[KEY_BLOCKED_USERS] = current + userId
        }
    }

    suspend fun unblockUser(userId: String) {
        context.settingsDataStore.edit {
            val current = it[KEY_BLOCKED_USERS] ?: emptySet()
            it[KEY_BLOCKED_USERS] = current - userId
        }
    }

    suspend fun updateNotificationOption(option: String, enabled: Boolean) {
        context.settingsDataStore.edit {
            when (option) {
                "dm" -> it[KEY_NOTIFY_DM] = enabled
                "msg_req" -> it[KEY_NOTIFY_MSG_REQ] = enabled
                "conn_req" -> it[KEY_NOTIFY_CONN_REQ] = enabled
                "room_invite" -> it[KEY_NOTIFY_ROOM_INVITE] = enabled
                "room_mentions" -> it[KEY_NOTIFY_ROOM_MENTIONS] = enabled
                "incoming_call" -> it[KEY_NOTIFY_INCOMING_CALL] = enabled
                "missed_call" -> it[KEY_NOTIFY_MISSED_CALL] = enabled
                "system" -> it[KEY_NOTIFY_SYSTEM] = enabled
                "sound" -> it[KEY_NOTIFY_SOUND] = enabled
                "vibration" -> it[KEY_NOTIFY_VIBRATION] = enabled
            }
        }
    }

    suspend fun updateChatWallpaper(wallpaperId: String) {
        context.settingsDataStore.edit { it[KEY_CHAT_WALLPAPER] = wallpaperId }
    }

    suspend fun updateChatTextSize(size: String) {
        context.settingsDataStore.edit { it[KEY_CHAT_TEXT_SIZE] = size }
    }

    suspend fun updateReduceAnimations(reduce: Boolean) {
        context.settingsDataStore.edit { it[KEY_REDUCE_ANIMATIONS] = reduce }
    }

    suspend fun updateEnterKeySends(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_ENTER_KEY_SENDS] = enabled }
    }

    suspend fun updateShowTimestamps(show: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_TIMESTAMPS] = show }
    }

    suspend fun updateAutoDownloadImages(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_DOWNLOAD_IMAGES] = enabled }
    }

    suspend fun updateAutoDownloadAudio(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_DOWNLOAD_AUDIO] = enabled }
    }

    suspend fun updateAllowRoomInvitations(allow: Boolean) {
        context.settingsDataStore.edit { it[KEY_ALLOW_ROOM_INVITES] = allow }
    }

    suspend fun updateAllowIncomingCalls(allow: Boolean) {
        context.settingsDataStore.edit { it[KEY_ALLOW_INCOMING_CALLS] = allow }
    }

    suspend fun updateMuteRoomNotifications(mute: Boolean) {
        context.settingsDataStore.edit { it[KEY_MUTE_ROOM_NOTIFICATIONS] = mute }
    }
}
