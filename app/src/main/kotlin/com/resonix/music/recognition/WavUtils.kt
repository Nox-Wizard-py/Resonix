package com.resonix.music.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight WAV/RIFF header parser and slice builder.
 * Only supports PCM (format 1) and IEEE float (format 3) WAV files.
 */
object WavUtils {

    data class WavInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val bitsPerSample: Int,
        /** Byte offset in the full WAV array where raw PCM data begins. */
        val pcmDataOffset: Int,
        /** Byte length of the PCM data chunk. */
        val pcmDataLength: Int,
    ) {
        val bytesPerFrame: Int get() = channelCount * (bitsPerSample / 8)
        val bytesPerSecond: Int get() = sampleRate * bytesPerFrame
        val durationSeconds: Double get() = pcmDataLength.toDouble() / bytesPerSecond
    }

    /**
     * Parse a WAV file byte array and return its structural metadata.
     * Throws [IllegalArgumentException] if the file is malformed.
     */
    fun parse(bytes: ByteArray): WavInfo {
        require(bytes.size >= 44) { "File too small to be a valid WAV." }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        require(riff == "RIFF" && wave == "WAVE") { "Not a valid RIFF/WAVE file." }

        var offset = 12
        var sampleRate = 0; var channelCount = 0; var bitsPerSample = 0
        var pcmDataOffset = -1; var pcmDataLength = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = buf.getInt(offset + 4)
            when (chunkId) {
                "fmt " -> {
                    channelCount  = buf.getShort(offset + 10).toInt() and 0xFFFF
                    sampleRate    = buf.getInt(offset + 12)
                    bitsPerSample = buf.getShort(offset + 22).toInt() and 0xFFFF
                }
                "data" -> {
                    pcmDataOffset = offset + 8
                    pcmDataLength = minOf(chunkSize, bytes.size - pcmDataOffset)
                    break
                }
            }
            // Chunks are word-aligned; odd-size chunks have a padding byte
            offset += 8 + chunkSize + (chunkSize and 1)
        }

        require(pcmDataOffset >= 0) { "WAV data chunk not found." }
        require(sampleRate > 0 && channelCount > 0) { "WAV fmt chunk missing or malformed." }
        return WavInfo(sampleRate, channelCount, bitsPerSample, pcmDataOffset, pcmDataLength)
    }

    /**
     * Extracts a time-aligned PCM slice and wraps it in a new WAV container.
     *
     * @param fullWav   The source WAV byte array (header + PCM).
     * @param info      Parsed metadata from [parse].
     * @param startSec  Start time of the slice in seconds.
     * @param durationSec  Desired duration of the slice in seconds.
     */
    fun slice(fullWav: ByteArray, info: WavInfo, startSec: Double, durationSec: Double): ByteArray {
        val frame = info.bytesPerFrame
        fun alignToFrame(n: Int) = n - (n % frame)

        val startByte = alignToFrame((startSec * info.bytesPerSecond).toInt()
            .coerceIn(0, info.pcmDataLength))
        val endByte = alignToFrame(((startSec + durationSec) * info.bytesPerSecond).toInt()
            .coerceIn(startByte, info.pcmDataLength))

        val pcmSlice = fullWav.copyOfRange(
            info.pcmDataOffset + startByte,
            info.pcmDataOffset + endByte
        )
        return buildHeader(info.sampleRate, info.channelCount, info.bitsPerSample, pcmSlice.size) + pcmSlice
    }

    /** Builds a standard 44-byte WAV/RIFF header for the given PCM parameters. */
    fun buildHeader(sampleRate: Int, channelCount: Int, bitsPerSample: Int, pcmDataLength: Int): ByteArray {
        val byteRate   = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = (channelCount * bitsPerSample / 8).toShort()
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());  putInt(36 + pcmDataLength)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray());  putInt(16)
            putShort(1)                              // PCM format
            putShort(channelCount.toShort())
            putInt(sampleRate);         putInt(byteRate)
            putShort(blockAlign);       putShort(bitsPerSample.toShort())
            put("data".toByteArray());  putInt(pcmDataLength)
        }.array()
    }
}
