package co.znxdnd.echotalk.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class RecordedVoiceNote(
    val filePath: String,
    val durationSeconds: Int,
    val formattedDuration: String,
    val waveform: List<Float>,
    val peakRmsDb: Double
)

/**
 * VoiceNoteRecorder: Real 16-bit 16kHz PCM microphone capture engine writing standard
 * playable WAV RIFF files into app cache and computing logarithmic dBFS waveforms via
 * [AudioSignalProcessor].
 */
class VoiceNoteRecorder(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val pcmSamples = ArrayList<Short>()
    private var startedAtMs: Long = 0L

    private val _liveLevel = MutableStateFlow(0f)
    val liveLevel: StateFlow<Float> = _liveLevel.asStateFlow()

    @Volatile
    private var isCapturing: Boolean = false

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        stopInternal()
        synchronized(pcmSamples) {
            pcmSamples.clear()
        }
        startedAtMs = System.currentTimeMillis()
        _liveLevel.value = 0f

        return try {
            val minBufBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                .coerceAtLeast(2048)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufBytes * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return false
            }
            audioRecord = recorder
            recorder.startRecording()
            isCapturing = true

            recordingJob = scope.launch {
                val buffer = ShortArray(minBufBytes / 2)
                while (isActive && isCapturing) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        synchronized(pcmSamples) {
                            for (i in 0 until read) {
                                val s = buffer[i]
                                pcmSamples.add(s)
                                val d = s.toDouble()
                                sumSq += d * d
                            }
                        }
                        val rms = sqrt(sumSq / read.coerceAtLeast(1))
                        _liveLevel.value = (rms / 6500.0).toFloat().coerceIn(0.06f, 1f)
                    }
                }
            }
            true
        } catch (_: Throwable) {
            stopInternal()
            false
        }
    }

    suspend fun stopAndSave(): RecordedVoiceNote? = withContext(Dispatchers.IO) {
        isCapturing = false
        recordingJob?.join()
        recordingJob = null
        stopAudioHardware()
        _liveLevel.value = 0f

        val elapsedSec = (((System.currentTimeMillis() - startedAtMs) / 1000L).toInt()).coerceAtLeast(1)
        val captured: ShortArray = synchronized(pcmSamples) {
            val copy = pcmSamples.toShortArray()
            pcmSamples.clear()
            copy
        }
        if (captured.isEmpty()) return@withContext null

        val dir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
        val outFile = File(dir, "vn_${System.currentTimeMillis()}.wav")
        writeWavFile(outFile, captured, SAMPLE_RATE)

        val actualSec = (captured.size / SAMPLE_RATE).coerceAtLeast(elapsedSec).coerceAtLeast(1)
        val formatted = formatDuration(actualSec)
        val waveform = AudioSignalProcessor.extractNormalizedWaveform(captured, barCount = 24)
        val rmsDb = AudioSignalProcessor.calculateRmsDb(captured)

        RecordedVoiceNote(
            filePath = outFile.absolutePath,
            durationSeconds = actualSec,
            formattedDuration = formatted,
            waveform = waveform,
            peakRmsDb = rmsDb
        )
    }

    fun cancelRecording() {
        stopInternal()
        synchronized(pcmSamples) {
            pcmSamples.clear()
        }
        _liveLevel.value = 0f
    }

    private fun stopInternal() {
        isCapturing = false
        recordingJob?.cancel()
        recordingJob = null
        stopAudioHardware()
    }

    private fun stopAudioHardware() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (_: Throwable) {
        } finally {
            audioRecord = null
        }
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val byteData = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk
        byteData.put("RIFF".toByteArray(Charsets.US_ASCII))
        byteData.putInt(36 + dataSize)
        byteData.put("WAVE".toByteArray(Charsets.US_ASCII))
        // fmt subchunk
        byteData.put("fmt ".toByteArray(Charsets.US_ASCII))
        byteData.putInt(16) // PCM header size
        byteData.putShort(1) // PCM format
        byteData.putShort(1) // Mono
        byteData.putInt(sampleRate)
        byteData.putInt(sampleRate * 2) // ByteRate
        byteData.putShort(2) // BlockAlign
        byteData.putShort(16) // BitsPerSample
        // data subchunk
        byteData.put("data".toByteArray(Charsets.US_ASCII))
        byteData.putInt(dataSize)
        for (s in samples) {
            byteData.putShort(s)
        }
        FileOutputStream(file).use { fos ->
            fos.write(byteData.array())
            fos.flush()
        }
    }

    private fun formatDuration(totalSec: Int): String {
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }
}
