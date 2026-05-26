package com.noxwizard.resonix.paxsenix.parser

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.WordLine
import kotlin.math.max

/**
 * Dedicated processor to sanitize TTML data after initial parsing.
 * Handles:
 * - Monotonic repair (ensuring time never flows backward).
 * - Overlap correction.
 * - Missing end-time inference.
 */
object TTMLNormalizationPipeline {

    fun normalize(lines: List<LyricsLine>): List<LyricsLine> {
        if (lines.isEmpty()) return lines

        var currentMaxTime = 0L

        return lines.mapIndexed { i, line ->
            // 1. Monotonic repair for line start time
            val safeStart = max(currentMaxTime, line.startMs)
            currentMaxTime = max(currentMaxTime, safeStart)

            // 2. Infer or repair end time
            val nextLine = lines.getOrNull(i + 1)
            val inferredEnd = nextLine?.startMs ?: (safeStart + 5000L)
            var safeEnd = if (line.endMs in (safeStart + 1)..inferredEnd) {
                line.endMs
            } else {
                inferredEnd
            }
            
            // Prevent overlaps with the next line
            if (nextLine != null && safeEnd > nextLine.startMs) {
                safeEnd = nextLine.startMs
            }

            // 3. Normalize word timings within the line
            var wordMaxTime = safeStart
            val normalizedWords = line.words.map { word ->
                val wordSafeStart = max(wordMaxTime, max(word.startMs, safeStart))
                wordMaxTime = wordSafeStart
                
                var wordSafeEnd = max(wordSafeStart + 1L, word.endMs)
                // Word shouldn't exceed line boundary
                if (wordSafeEnd > safeEnd) {
                    wordSafeEnd = safeEnd
                }
                wordMaxTime = max(wordMaxTime, wordSafeEnd)

                WordLine(
                    text = word.text,
                    startMs = wordSafeStart,
                    endMs = wordSafeEnd
                )
            }

            // Update line max time after processing words
            currentMaxTime = max(currentMaxTime, safeEnd)

            line.copy(
                startMs = safeStart,
                endMs = safeEnd,
                words = normalizedWords
            )
        }
    }
}
