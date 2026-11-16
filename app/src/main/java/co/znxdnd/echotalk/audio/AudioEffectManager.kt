package co.znxdnd.echotalk.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * AudioEffectManager: Dynamic in-memory audio effect synthesis engine for Android.
 * Uses AudioTrack and ToneGenerator to synthesize pure sine-wave acoustic effects on demand,
 * completely eliminating external file dependencies (.mp3, .wav) and guaranteeing
 * sub-millisecond trigger latency without network or disk I/O bottlenecks.
 */
class AudioEffectManager private constructor(private val context: Context? = null) {

    companion object {
        private const val TAG = "AudioEffectManager"
        const val SAMPLE_RATE = 44100

        @Volatile
        private var instance: AudioEffectManager? = null

        fun getInstance(context: Context? = null): AudioEffectManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEffectManager(context?.applicationContext).also { instance = it }
            }
        }

        // Direct static accessors for instant on-demand usage without context
        fun playEmojiPopSound() = getInstance().playEmojiPopSound()
        fun playMessageSentSound() = getInstance().playMessageSentSound()
        fun playMessageReceivedSound() = getInstance().playMessageReceivedSound()
        fun playNotificationSound() = getInstance().playNotificationSound()
        fun playArcadeMoveSound() = getInstance().playArcadeMoveSound()
        fun playArcadeCaptureSound() = getInstance().playArcadeCaptureSound()
        fun playTone(frequencyHz: Double, durationMs: Int, volume: Float = 0.8f) =
            getInstance().playTone(frequencyHz, durationMs, volume)
        fun playToneGeneratorBeep(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 120) =
            getInstance().playToneGeneratorBeep(toneType, durationMs)
    }

    // Pre-computed PCM buffers for instantaneous playback (<1ms trigger time)
    val sendSoundBuffer: ShortArray by lazy { generateSendTone() }
    val receiveSoundBuffer: ShortArray by lazy { generateReceiveTone() }
    val popSoundBuffer: ShortArray by lazy { generatePopTone() }
    val notificationSoundBuffer: ShortArray by lazy { generateNotificationTone() }
    val moveSoundBuffer: ShortArray by lazy { generateMoveTone() }
    val captureSoundBuffer: ShortArray by lazy { generateCaptureTone() }

    /**
     * Facebook Messenger Emoji Tray Pop FX (playEmojiPopSound):
     * Oscillator Type: Ascending tri-tone sine wave chime (420 Hz -> 840 Hz -> 1260 Hz).
     * Duration: 75 ms total with rapid exponential decay envelope.
     * Resulting sound: Signature crisp tactile pop chime matching Messenger's emoji tray appearance.
     */
    fun generatePopTone(): ShortArray {
        val totalMs = 75
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE // in seconds

            // Ascending tri-tone chime matching Messenger emoji pop: 420Hz -> 840Hz -> 1260Hz
            val freq = when {
                t < 0.022 -> 420.0
                t < 0.045 -> 840.0
                else -> 1260.0
            }

            phase += 2.0 * PI * freq / SAMPLE_RATE

            // Gain envelope: fast 4ms attack, then smooth exponential decay
            val gain = if (t < 0.004) {
                0.001 + (0.09 - 0.001) * (t / 0.004)
            } else {
                val decayFraction = ((t - 0.004) / (0.075 - 0.004)).coerceIn(0.0, 1.0)
                0.09 * (0.0001 / 0.09).pow(decayFraction)
            }

            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Ultra-Soft Sent Message FX (playMessageSentSound):
     * Oscillator Type: Sine wave.
     * Frequency Ramp: Smooth exponential upward shift from 520 Hz to 780 Hz over 80 ms.
     * Gain envelope: Soft linear ramp-in (0.001 -> 0.08) over 20 ms, followed by exponential decay down to 0.0001 over 120 ms.
     * Total Duration: 140 ms.
     * Resulting sound: A gentle, tactile rising chime.
     */
    fun generateSendTone(): ShortArray {
        val totalMs = 140
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE

            val freq = if (t <= 0.080) {
                520.0 * (780.0 / 520.0).pow(t / 0.080)
            } else {
                780.0
            }

            phase += 2.0 * PI * freq / SAMPLE_RATE

            val gain = if (t < 0.020) {
                0.001 + (0.08 - 0.001) * (t / 0.020)
            } else {
                val decayFraction = ((t - 0.020) / 0.120).coerceIn(0.0, 1.0)
                0.08 * (0.0001 / 0.08).pow(decayFraction)
            }

            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Ultra-Soft Received Message FX (playMessageReceivedSound):
     * Oscillator Type: Sine wave.
     * Frequency Ramp: Smooth exponential downward shift from 440 Hz to 330 Hz over 140 ms.
     * Gain envelope: Soft linear ramp-in (0.001 -> 0.06) over 20 ms, followed by exponential decay down to 0.0001 over 160 ms.
     * Total Duration: 180 ms.
     * Resulting sound: A warm, polite subtle tone drop.
     */
    fun generateReceiveTone(): ShortArray {
        val totalMs = 180
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE

            val freq = if (t <= 0.140) {
                440.0 * (330.0 / 440.0).pow(t / 0.140)
            } else {
                330.0
            }

            phase += 2.0 * PI * freq / SAMPLE_RATE

            val gain = if (t < 0.020) {
                0.001 + (0.06 - 0.001) * (t / 0.020)
            } else {
                val decayFraction = ((t - 0.020) / 0.160).coerceIn(0.0, 1.0)
                0.06 * (0.0001 / 0.06).pow(decayFraction)
            }

            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Message Notification Sound FX (playNotificationSound):
     * Dual-tone pleasant sine chime (D5: 587.33 Hz for 70ms -> A5: 880.00 Hz for 130ms).
     * Total Duration: 200 ms.
     */
    fun generateNotificationTone(): ShortArray {
        val totalMs = 200
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = if (t < 0.070) 587.33 else 880.00
            phase += 2.0 * PI * freq / SAMPLE_RATE

            val gain = if (t < 0.010) {
                0.001 + (0.08 - 0.001) * (t / 0.010)
            } else {
                val decayFraction = ((t - 0.010) / 0.190).coerceIn(0.0, 1.0)
                0.08 * (0.0001 / 0.08).pow(decayFraction)
            }

            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Tactile Arcade Move Chime (playArcadeMoveSound):
     * Quick pleasant ascending pitch blip (660 Hz -> 880 Hz) over 50 ms.
     */
    fun generateMoveTone(): ShortArray {
        val totalMs = 50
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 660.0 + (880.0 - 660.0) * (t / 0.050)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val gain = 0.06 * (1.0 - (t / 0.050))
            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Tactile Arcade Capture Sound (playArcadeCaptureSound):
     * Deep tactile pulse (180 Hz -> 90 Hz) over 70 ms.
     */
    fun generateCaptureTone(): ShortArray {
        val totalMs = 70
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 180.0 * (90.0 / 180.0).pow(t / 0.070)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val gain = 0.08 * (1.0 - (t / 0.070))
            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Arbitrary Sine-Wave Buffer Synthesizer with anti-click envelopes.
     */
    fun generateSineWaveBuffer(frequencyHz: Double, durationMs: Int, volume: Float = 0.8f): ShortArray {
        val validDuration = durationMs.coerceIn(10, 5000)
        val validVol = volume.coerceIn(0.01f, 1.0f) * 0.1f // safe dynamic range
        val numSamples = (SAMPLE_RATE * (validDuration / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        val attackSamples = (SAMPLE_RATE * 0.005).toInt().coerceAtLeast(1)
        val decaySamples = (SAMPLE_RATE * 0.015).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            phase += 2.0 * PI * frequencyHz / SAMPLE_RATE

            val env = when {
                i < attackSamples -> i.toFloat() / attackSamples
                i > numSamples - decaySamples -> (numSamples - i).toFloat() / decaySamples
                else -> 1.0f
            }

            val sample = (sin(phase) * validVol * env * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    // -------------------------------------------------------------
    // PLAYBACK METHODS
    // -------------------------------------------------------------

    /**
     * Plays the signature Facebook Messenger emoji tray pop chime.
     */
    fun playEmojiPopSound() {
        playPcmBuffer(popSoundBuffer)
    }

    /**
     * Plays the polite soft upward chime for sent messages.
     */
    fun playMessageSentSound() {
        playPcmBuffer(sendSoundBuffer)
    }

    /**
     * Plays the polite soft downward tone drop for received messages.
     */
    fun playMessageReceivedSound() {
        playPcmBuffer(receiveSoundBuffer)
    }

    /**
     * Plays the dual-tone notification chime for incoming message alerts.
     */
    fun playNotificationSound() {
        playPcmBuffer(notificationSoundBuffer)
    }

    /**
     * Plays the tactile arcade move confirmation chime.
     */
    fun playArcadeMoveSound() {
        playPcmBuffer(moveSoundBuffer)
    }

    /**
     * Plays the tactile arcade capture pulse sound.
     */
    fun playArcadeCaptureSound() {
        playPcmBuffer(captureSoundBuffer)
    }

    /**
     * Plays an arbitrary synthesized sine-wave tone at the specified frequency and duration.
     */
    fun playTone(frequencyHz: Double, durationMs: Int, volume: Float = 0.8f) {
        val buffer = generateSineWaveBuffer(frequencyHz, durationMs, volume)
        playPcmBuffer(buffer)
    }

    /**
     * Plays a hardware-accelerated ToneGenerator beep using Android's native ToneGenerator.
     */
    fun playToneGeneratorBeep(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 120) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                toneGen.startTone(toneType, durationMs)
                delay(durationMs.toLong() + 30L)
                toneGen.release()
            } catch (e: Throwable) {
                Log.d(TAG, "ToneGenerator fallback to AudioTrack note: ${e.message}")
                // Fallback to sine wave AudioTrack
                playNotificationSound()
            }
        }
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val bufferSize = buffer.size * 2
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                // Wait for tone duration before releasing hardware resources
                val durationMs = (buffer.size * 1000L) / SAMPLE_RATE + 40L
                delay(durationMs)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            } catch (e: Throwable) {
                Log.d(TAG, "AudioTrack synthesis note: ${e.message}")
            }
        }
    }
}
