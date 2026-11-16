package co.znxdnd.echotalk.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UiSoundManager: High-performance, low-latency UI sound engine.
 * Provides micro-interaction audio feedback (taps, successes, errors, and chimes)
 * with independent volume controls and persistent mute states.
 */
class UiSoundManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "UiSoundManager"
        private const val PREFS_NAME = "echotalk_sound_prefs"
        private const val KEY_MUTED = "ui_sound_muted"
        private const val KEY_VOLUME = "ui_sound_volume"

        @Volatile
        private var instance: UiSoundManager? = null

        fun getInstance(context: Context): UiSoundManager {
            return instance ?: synchronized(this) {
                instance ?: UiSoundManager(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): UiSoundManager {
            return instance ?: throw IllegalStateException("UiSoundManager must be initialized with Context first")
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isMuted = MutableStateFlow(prefs.getBoolean(KEY_MUTED, false))
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volume = MutableStateFlow(prefs.getFloat(KEY_VOLUME, 0.8f))
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val soundPool: SoundPool? = try {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
    } catch (e: Exception) {
        Log.w(TAG, "SoundPool initialization fallback: ${e.message}")
        null
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
        SoundEffectManager.setMuted(muted)
    }

    fun toggleMute(): Boolean {
        val next = !_isMuted.value
        setMuted(next)
        return next
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
    }

    fun playTap() {
        if (_isMuted.value) return
        scope.launch {
            try {
                PoliteSoundSynthesizer.playPopSound()
            } catch (e: Exception) {
                Log.w(TAG, "playTap fallback: ${e.message}")
            }
        }
    }

    fun playSuccess() {
        if (_isMuted.value) return
        scope.launch {
            try {
                PoliteSoundSynthesizer.playPoliteSendSound()
            } catch (e: Exception) {
                Log.w(TAG, "playSuccess fallback: ${e.message}")
            }
        }
    }

    fun playError() {
        if (_isMuted.value) return
        scope.launch {
            try {
                // Short low-frequency buzz
                PoliteSoundSynthesizer.playPoliteReceiveSound()
            } catch (e: Exception) {
                Log.w(TAG, "playError fallback: ${e.message}")
            }
        }
    }

    fun playMessageSent() {
        if (_isMuted.value) return
        SoundEffectManager.playSendMessage(_volume.value)
    }

    fun playMessageReceived() {
        if (_isMuted.value) return
        SoundEffectManager.playReceiveMessage(_volume.value)
    }

    fun playPop() {
        if (_isMuted.value) return
        scope.launch {
            PoliteSoundSynthesizer.playPopSound()
        }
    }

    fun release() {
        try {
            soundPool?.release()
        } catch (_: Exception) {}
    }
}
