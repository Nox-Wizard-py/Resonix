package com.resonix.music.recognition

import android.content.Context
import android.net.Uri
import com.resonix.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Attempts music recognition on multiple time-windows of a WAV file.
 *
 * Strategy (8-second windows):
 *  1. First 8 s  — catches tracks that play immediately
 *  2. Middle 8 s — catches tracks buried after a long intro
 *  3. Last 8 s   — catches tracks that appear late in the reel
 *  4. Full file  — final fallback
 *
 * Returns the first [RecognitionStatus.Success], or the final result of the
 * full-file attempt if no window matched.
 */
object MultiWindowRecognizer {

    private const val WINDOW_SEC = 8.0

    suspend fun recognize(context: Context, wavFile: File): RecognitionStatus {
        val bytes = wavFile.readBytes()

        // Try to parse WAV header; if malformed, fall back to direct full-file recognition
        val info = runCatching { WavUtils.parse(bytes) }.getOrElse {
            return MusicRecognitionService.recognizeFile(context, Uri.fromFile(wavFile))
        }

        if (info.durationSeconds < 1.0) {
            return MusicRecognitionService.recognizeFile(context, Uri.fromFile(wavFile))
        }

        val windows = buildWindows(info.durationSeconds)
        val tmpDir = File(context.cacheDir, "recog_windows").also { it.mkdirs() }
        var lastStatus: RecognitionStatus = RecognitionStatus.NoMatch("No match found")

        try {
            for ((index, startSec) in windows.withIndex()) {
                coroutineContext.ensureActive()

                val sliceBytes = WavUtils.slice(bytes, info, startSec, WINDOW_SEC)
                val sliceFile = File(tmpDir, "window_$index.wav").also { it.writeBytes(sliceBytes) }

                val status = MusicRecognitionService.recognizeFile(context, Uri.fromFile(sliceFile))
                lastStatus = status
                if (status is RecognitionStatus.Success) return status
            }

            // Final fallback: recognise the entire file
            coroutineContext.ensureActive()
            val fullStatus = MusicRecognitionService.recognizeFile(context, Uri.fromFile(wavFile))
            return if (fullStatus is RecognitionStatus.Success) fullStatus else lastStatus

        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /**
     * Returns up to 3 non-overlapping start offsets (in seconds) for 8-second windows.
     * Deduplicates so very short clips don't try the same window twice.
     */
    private fun buildWindows(duration: Double): List<Double> = buildList {
        add(0.0)                                                  // start
        if (duration > WINDOW_SEC * 2.1)
            add((duration - WINDOW_SEC) / 2.0)                   // middle
        if (duration > WINDOW_SEC + 1.0)
            add(maxOf(0.0, duration - WINDOW_SEC))               // end
    }.distinct()
}
