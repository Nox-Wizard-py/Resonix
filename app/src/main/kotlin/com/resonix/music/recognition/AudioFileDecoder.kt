package com.resonix.music.recognition

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioFileDecoder {

    /**
     * Extracts and decodes the audio from the given [uri].
     * If the audio is stereo, it downmixes it to mono.
     * Returns 16-bit PCM mono data ready for resampling.
     * Extracts a maximum of 12 seconds for efficient processing.
     */
    suspend fun decodeUri(context: Context, uri: Uri): DecodedAudio = withContext(Dispatchers.IO) {
        // Fast path: WAV/PCM files produced by the backend are already decoded PCM.
        // Skip MediaCodec entirely and read the raw samples directly.
        val uriPath = uri.path ?: ""
        if (uriPath.endsWith(".wav", ignoreCase = true)) {
            return@withContext decodeWavDirect(uri)
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            throw Exception("Could not read audio file: ${e.message}")
        }

        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                audioTrackIndex = i
                inputFormat = format
                break
            }
        }

        if (audioTrackIndex < 0 || inputFormat == null) {
            extractor.release()
            throw Exception("No audio track found in file.")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: throw Exception("Unknown audio mime type.")
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val outputStream = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        val timeoutUs = 10000L
        var isEOS = false

        // We decode up to 90 seconds of audio to ensure we capture the music 
        // even if the reel/video has a long intro without the song.
        val maxOutputBytes = 90 * sampleRate * channelCount * 2

        try {
            while (!isEOS) {
                ensureActive()

                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        
                        val chunk = ByteArray(info.size)
                        outputBuffer.get(chunk)
                        outputStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isEOS = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                }

                if (outputStream.size() >= maxOutputBytes) {
                    break
                }
            }
        } finally {
            try { codec.stop() } catch (e: Exception) {}
            try { codec.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }

        val rawAudio = outputStream.toByteArray()
        if (rawAudio.isEmpty()) {
            throw Exception("Decoded audio is empty")
        }

        val monoAudio = if (channelCount >= 2) convertToMono(rawAudio, channelCount) else rawAudio

        DecodedAudio(
            data = monoAudio,
            channelCount = 1,
            sampleRate = sampleRate,
            pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        )
    }

    /**
     * Reads a WAV PCM file directly, bypassing MediaCodec.
     * Assumes the file is 16-bit PCM (any sample rate / channel count).
     */
    private fun decodeWavDirect(uri: Uri): DecodedAudio {
        val path = uri.path ?: throw Exception("Cannot determine path for WAV URI: $uri")
        val bytes = java.io.File(path).readBytes()
        val info = WavUtils.parse(bytes)  // throws if malformed

        // Cap at 90 seconds to match the MediaCodec path
        val maxBytes = 90 * info.bytesPerSecond
        val endByte = minOf(info.pcmDataLength, maxBytes)
        val pcmBytes = bytes.copyOfRange(info.pcmDataOffset, info.pcmDataOffset + endByte)

        val monoAudio = if (info.channelCount >= 2) convertToMono(pcmBytes, info.channelCount) else pcmBytes
        return DecodedAudio(
            data = monoAudio,
            channelCount = 1,
            sampleRate = info.sampleRate,
            pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT,
        )
    }

    private fun convertToMono(audioData: ByteArray, channelCount: Int): ByteArray {
        val monoData = ByteArray(audioData.size / channelCount)
        val shortBufferIn = ByteBuffer.wrap(audioData).order(ByteOrder.nativeOrder()).asShortBuffer()
        val shortBufferOut = ByteBuffer.wrap(monoData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        
        while (shortBufferIn.hasRemaining() && shortBufferOut.hasRemaining()) {
            val left = shortBufferIn.get().toInt()
            var sum = left
            for (i in 1 until channelCount) {
               if (shortBufferIn.hasRemaining()) {
                  sum += shortBufferIn.get().toInt()
               }
            }
            val mono = (sum / channelCount).toShort()
            shortBufferOut.put(mono)
        }
        return monoData
    }
}
