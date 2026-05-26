package com.noxwizard.resonix.betterlyrics.models

private const val INSTRUMENTAL_GAP_MS = 8_000L  // gaps > 8s trigger an instrumental marker

/**
 * A single line of lyrics — render-ready, structured, karaoke-aware.
 *
 * The renderer consumes this directly; no LRC string needed.
 */
data class LyricsLine(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long = -1L,
    val words: List<WordTiming> = emptyList(),
    val translation: TranslationLine? = null,
    val romanizedText: String? = null,
    val backgroundTrackId: String? = null,
    val isInstrumental: Boolean = false,
) {
    val hasWordSync: Boolean get() = words.isNotEmpty()
    val hasTranslation: Boolean get() = translation != null
    val hasRomanized: Boolean get() = romanizedText != null
    val isBackgroundVocal: Boolean get() = backgroundTrackId != null

    companion object {
        fun instrumental(startTimeMs: Long, endTimeMs: Long) = LyricsLine(
            text = "",
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            isInstrumental = true,
        )

        fun detectInstrumentalGaps(lines: List<LyricsLine>): List<LyricsLine> {
            if (lines.isEmpty()) return lines
            val result = mutableListOf<LyricsLine>()
            for (i in lines.indices) {
                result.add(lines[i])
                if (i < lines.lastIndex) {
                    val gap = lines[i + 1].startTimeMs - lines[i].startTimeMs
                    if (gap > INSTRUMENTAL_GAP_MS) {
                        result.add(instrumental(lines[i].startTimeMs, lines[i + 1].startTimeMs))
                    }
                }
            }
            return result
        }
    }
}
