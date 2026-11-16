package co.znxdnd.echotalk.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * PoliteSoundSynthesizer: Dynamic Web Audio API-style native audio synthesizer engine.
 * Generates tactile sound effects purely in memory with zero external asset dependencies (.mp3/.wav),
 * ensuring zero latency, zero network failures, and deterministic tone profiles.
 */
object PoliteSoundSynthesizer {

    private const val TAG = "PoliteSoundSynth"
    private const val SAMPLE_RATE = 44100

    // Pre-computed PCM buffers for instantaneous playback (<1ms trigger time)
    private val sendSoundBuffer: ShortArray by lazy { generateSendTone() }
    private val receiveSoundBuffer: ShortArray by lazy { generateReceiveTone() }
    private val popSoundBuffer: ShortArray by lazy { generatePopTone() }

    /**
     * Ultra-Soft Sent Message FX (playPoliteSendSound):
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
            val t = i.toDouble() / SAMPLE_RATE // in seconds

            // Smooth exponential frequency shift upward from 520Hz to 780Hz over 80ms
            val freq = if (t <= 0.080) {
                520.0 * (780.0 / 520.0).pow(t / 0.080)
            } else {
                780.0
            }

            phase += 2.0 * PI * freq / SAMPLE_RATE

            // Gain envelope: Soft linear ramp-in (0.001 -> 0.08) over 20ms,
            // followed by exponential decay down to 0.0001 over 120ms.
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
     * Ultra-Soft Received Message FX (playPoliteReceiveSound):
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

            // Smooth exponential frequency shift downward from 440Hz to 330Hz over 140ms
            val freq = if (t <= 0.140) {
                440.0 * (330.0 / 440.0).pow(t / 0.140)
            } else {
                330.0
            }

            phase += 2.0 * PI * freq / SAMPLE_RATE

            // Gain envelope: Soft linear ramp-in (0.001 -> 0.06) over 20ms,
            // followed by exponential decay down to 0.0001 over 160ms.
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
     * Facebook Messenger Emoji Tray Pop FX (playPopSound):
     * Oscillator Type: Ascending dual/tri-tone sine wave chime (420 Hz -> 840 Hz -> 1260 Hz).
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
     * Triggers the synthesized sent message chime asynchronously.
     */
    fun playPoliteSendSound() {
        playPcmBuffer(sendSoundBuffer)
    }

    /**
     * Triggers the synthesized received message tone drop asynchronously.
     */
    fun playPoliteReceiveSound() {
        playPcmBuffer(receiveSoundBuffer)
    }

    /**
     * Triggers the synthesized Messenger emoji pop chime asynchronously.
     */
    fun playPopSound() {
        playPcmBuffer(popSoundBuffer)
    }

    /**
     * Tactile Arcade Move Chime (playMoveChime):
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
     * Tactile Arcade Capture Sound (playCaptureSound):
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

    fun playMoveChime() {
        playPcmBuffer(generateMoveTone())
    }

    fun playCaptureSound() {
        playPcmBuffer(generateCaptureTone())
    }

    /**
     * Voice Note Playback Controller interface for interactive seek & pause control.
     */
    interface VoicePlaybackHandle {
        fun stop()
        fun isRunning(): Boolean
    }

    /**
     * Plays a synthesized voice note audio simulation with speech cadence,
     * reporting live playback progress and supporting instant pause / seek.
     */
    fun playSimulatedVoiceNote(
        seed: String = "echo",
        durationSeconds: Float = 5f,
        startFraction: Float = 0f,
        onProgress: (Float) -> Unit,
        onCompletion: () -> Unit
    ): VoicePlaybackHandle {
        val validDuration = durationSeconds.coerceIn(1f, 60f)
        val validStart = startFraction.coerceIn(0f, 0.99f)
        val remainingSeconds = validDuration * (1f - validStart)
        val sampleRate = 22050
        val totalSamples = (sampleRate * remainingSeconds).toInt()

        val buffer = ShortArray(totalSamples)
        val baseFreq = 180.0 + (kotlin.math.abs(seed.hashCode()) % 80)
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Modulate pitch slightly to mimic vocal speech inflection
            val pitchDrift = baseFreq + sin(t * 6.0) * 30.0 + sin(t * 1.8) * 15.0
            phase += 2.0 * PI * pitchDrift / sampleRate

            // Formant speech syllables envelope (pulsing cadence)
            val cadence = (0.5 + 0.5 * sin(t * 12.0)).pow(1.5)
            val envelope = if (t < 0.05) (t / 0.05) else if (t > remainingSeconds - 0.05) ((remainingSeconds - t) / 0.05).coerceAtLeast(0.0) else 1.0
            val gain = 0.07 * cadence * envelope

            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }

        var activeTrack: AudioTrack? = null
        var isPlaying = true

        val job = CoroutineScope(Dispatchers.Default).launch {
            try {
                val bufferSize = buffer.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeTrack = track
                track.write(buffer, 0, buffer.size)
                track.play()

                val startTime = System.currentTimeMillis()
                val totalMs = (remainingSeconds * 1000L).toLong()

                while (isPlaying) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val currentProgress = (validStart + (elapsed.toFloat() / (validDuration * 1000f))).coerceIn(0f, 1f)
                    onProgress(currentProgress)

                    if (elapsed >= totalMs) {
                        break
                    }
                    delay(30)
                }

                if (isPlaying) {
                    onProgress(1f)
                    onCompletion()
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Voice simulation note: ${e.message}")
            } finally {
                try {
                    activeTrack?.stop()
                    activeTrack?.release()
                } catch (_: Exception) {}
            }
        }

        return object : VoicePlaybackHandle {
            override fun stop() {
                isPlaying = false
                job.cancel()
                try {
                    activeTrack?.stop()
                    activeTrack?.release()
                } catch (_: Exception) {}
            }

            override fun isRunning(): Boolean = isPlaying && job.isActive
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
