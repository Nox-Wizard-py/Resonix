package com.noxwizard.resonix.services

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Duration of a single recognition recording in milliseconds. */
private const val RECORD_DURATION_MS = 10_000L

/** PCM audio config — matches what Shazam expects. */
private const val SAMPLE_RATE = 44_100
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

/**
 * Records audio from the device microphone for [RECORD_DURATION_MS] milliseconds.
 *
 * Returns the raw PCM bytes that can be sent directly to Shazam.
 * Emits [RecordingState] updates so the UI can show progress.
 */
class MicRecordingService {

    sealed class RecordingState {
        object Starting : RecordingState()
        data class Progress(val elapsedMs: Long, val totalMs: Long) : RecordingState()
        data class Done(val pcmBytes: ByteArray) : RecordingState()
        data class Error(val message: String) : RecordingState()
    }

    /**
     * Cold Flow that records audio and emits state updates.
     * Collect on a background dispatcher — AudioRecord requires a non-main thread.
     */
    fun record(): Flow<RecordingState> = flow {
        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufSize, SAMPLE_RATE * 2) // at least 1 second of buffer

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            emit(RecordingState.Error("Microphone unavailable. Check app permissions."))
            return@flow
        }

        emit(RecordingState.Starting)

        val totalBytes = (SAMPLE_RATE * (Short.SIZE_BYTES) * (RECORD_DURATION_MS / 1000)).toInt()
        val output = ByteArray(totalBytes)
        var offset = 0
        val chunk = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()

        recorder.startRecording()
        try {
            while (coroutineContext.isActive && offset < totalBytes) {
                val read = recorder.read(chunk, 0, minOf(chunk.size, totalBytes - offset))
                if (read > 0) {
                    System.arraycopy(chunk, 0, output, offset, read)
                    offset += read
                }
                val elapsed = System.currentTimeMillis() - startTime
                emit(RecordingState.Progress(elapsed, RECORD_DURATION_MS))
            }
            emit(RecordingState.Done(output.copyOf(offset)))
        } catch (e: CancellationException) {
            // Normal cancellation — no-op
            throw e
        } catch (e: Exception) {
            emit(RecordingState.Error("Recording failed: ${e.message}"))
        } finally {
            recorder.stop()
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
