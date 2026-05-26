package com.noxwizard.resonix.paxsenix.parser

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.WordLine

/**
 * Generates synthetic word-level timings for line-synced lyrics.
 * Uses a heuristic that safely splits CJK characters individually,
 * while keeping Latin words together.
 */
object WordInterpolationEngine {

    /**
     * Iterates over a list of [LyricsLine]s and interpolates word timings for lines
     * that do not already have word-level sync.
     */
    fun interpolate(lines: List<LyricsLine>): List<LyricsLine> {
        return lines.map { line ->
            if (line.hasWordSync) return@map line
            
            // If the line has no text or zero duration, just return it
            if (line.text.isBlank() || line.endMs <= line.startMs) {
                return@map line
            }

            val syntheticWords = interpolateLine(line.text, line.startMs, line.endMs)
            line.copy(words = syntheticWords)
        }
    }

    private fun interpolateLine(text: String, startMs: Long, endMs: Long): List<WordLine> {
        val tokens = tokenizeCJKSafe(text)
        if (tokens.isEmpty()) return emptyList()

        val duration = endMs - startMs
        val totalLength = tokens.sumOf { it.length }.coerceAtLeast(1)
        val timePerChar = duration.toDouble() / totalLength

        var currentStart = startMs
        return tokens.map { token ->
            val tokenDuration = (timePerChar * token.length).toLong()
            val tokenEnd = currentStart + tokenDuration
            
            val wordLine = WordLine(
                text = token,
                startMs = currentStart,
                endMs = tokenEnd
            )
            currentStart = tokenEnd
            wordLine
        }
    }

    /**
     * Splits string into tokens:
     * - Latin text is split by spaces (keeping words intact).
     * - CJK text is split character by character.
     * - Spaces are attached to the preceding word or kept as tokens depending on usage,
     *   but for simple interpolation, we can just split by word boundaries or CJK chars.
     */
    private fun tokenizeCJKSafe(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val builder = StringBuilder()
        
        for (i in text.indices) {
            val char = text[i]
            if (isCJK(char)) {
                // If we were building a Latin word, push it
                if (builder.isNotEmpty()) {
                    tokens.add(builder.toString())
                    builder.clear()
                }
                tokens.add(char.toString())
            } else if (char.isWhitespace()) {
                builder.append(char)
                // Optionally we can push words immediately on whitespace, but grouping space with previous/next word is fine.
                // Let's attach space to the preceding word
                tokens.add(builder.toString())
                builder.clear()
            } else {
                builder.append(char)
            }
        }
        
        if (builder.isNotEmpty()) {
            tokens.add(builder.toString())
        }
        
        // Filter out pure whitespace tokens if they got added as empty + space, 
        // though we want to preserve spacing for UI rendering if needed. 
        // We'll keep them as they take up duration in the song.
        return tokens.filter { it.isNotEmpty() }
    }

    private fun isCJK(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
               block == Character.UnicodeBlock.HIRAGANA ||
               block == Character.UnicodeBlock.KATAKANA ||
               block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }
}
