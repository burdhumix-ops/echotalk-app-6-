package co.znxdnd.echotalk.audio

import android.content.Context
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
 * ChessSoundManager: Dedicated sound effect manager for the chess tournament slate.
 * Triggers distinct, high-fidelity acoustic clips when a piece is moved, captured, or when the game ends (win/loss/draw).
 */
class ChessSoundManager private constructor(private val context: Context? = null) {

    companion object {
        private const val TAG = "ChessSoundManager"
        private const val SAMPLE_RATE = 44100

        @Volatile
        private var instance: ChessSoundManager? = null

        fun getInstance(context: Context? = null): ChessSoundManager {
            return instance ?: synchronized(this) {
                instance ?: ChessSoundManager(context?.applicationContext).also { instance = it }
            }
        }

        fun playMove(context: Context? = null) = getInstance(context).playMoveSound()
        fun playCapture(context: Context? = null) = getInstance(context).playCaptureSound()
        fun playGameOver(isWin: Boolean, context: Context? = null) = getInstance(context).playGameEndSound(isWin)
    }

    private val moveBuffer: ShortArray by lazy { generateChessMoveTone() }
    private val captureBuffer: ShortArray by lazy { generateChessCaptureTone() }
    private val winBuffer: ShortArray by lazy { generateChessWinTone() }
    private val lossBuffer: ShortArray by lazy { generateChessLossTone() }

    /**
     * Chess Piece Move Sound: Crisp wooden slide / tactile tick (580Hz -> 720Hz quick chirp, 45ms).
     */
    private fun generateChessMoveTone(): ShortArray {
        val totalMs = 45
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 580.0 + (720.0 - 580.0) * (t / 0.045)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val gain = 0.07 * (1.0 - (t / 0.045))
            val sample = (sin(phase) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Chess Piece Capture Sound: Distinct woody impact thud with a sharp high-frequency snap (240Hz drop to 120Hz + 1100Hz click, 65ms).
     */
    private fun generateChessCaptureTone(): ShortArray {
        val totalMs = 65
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq1 = 240.0 * (120.0 / 240.0).pow(t / 0.065)
            val freq2 = 1100.0 * (400.0 / 1100.0).pow(t / 0.065)

            phase1 += 2.0 * PI * freq1 / SAMPLE_RATE
            phase2 += 2.0 * PI * freq2 / SAMPLE_RATE

            val gain = 0.09 * (1.0 - (t / 0.065))
            val sample = ((sin(phase1) * 0.7 + sin(phase2) * 0.3) * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Chess Game Win Sound: Triumphant ascending triad fanfare (C5: 523.25 Hz -> E5: 659.25 Hz -> G5: 783.99 Hz -> C6: 1046.50 Hz, 350ms).
     */
    private fun generateChessWinTone(): ShortArray {
        val totalMs = 350
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = when {
                t < 0.09 -> 523.25 // C5
                t < 0.18 -> 659.25 // E5
                t < 0.27 -> 783.99 // G5
                else -> 1046.50      // C6
            }
            phase += 2.0 * PI * freq / SAMPLE_RATE

            val localT = (t % 0.09) / 0.09
            val env = 0.08 * (1.0 - localT * 0.5)
            val sample = (sin(phase) * env * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Chess Game Loss / Draw Sound: Descending solemn chime (440Hz -> 330Hz -> 220Hz, 300ms).
     */
    private fun generateChessLossTone(): ShortArray {
        val totalMs = 300
        val numSamples = (SAMPLE_RATE * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = when {
                t < 0.10 -> 440.0
                t < 0.20 -> 330.0
                else -> 220.0
            }
            phase += 2.0 * PI * freq / SAMPLE_RATE

            val localT = (t % 0.10) / 0.10
            val env = 0.07 * (1.0 - localT * 0.5)
            val sample = (sin(phase) * env * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    fun playMoveSound() {
        playPcmBuffer(moveBuffer)
    }

    fun playCaptureSound() {
        playPcmBuffer(captureBuffer)
    }

    fun playGameEndSound(isWin: Boolean) {
        playPcmBuffer(if (isWin) winBuffer else lossBuffer)
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

                val durationMs = (buffer.size * 1000L) / SAMPLE_RATE + 40L
                delay(durationMs)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            } catch (e: Throwable) {
                Log.d(TAG, "ChessSoundManager playback note: ${e.message}")
            }
        }
    }
}
