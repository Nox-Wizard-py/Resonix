package com.noxwizard.resonix.benchmark

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.WordLine
import kotlin.random.Random

/**
 * Generates malformed and chaotic timing data for stress testing
 * the Resonix lyrics synchronization engine.
 */
object TimingChaosGenerator {

    /**
     * Applies a random assortment of chaos mutations to a clean list of lines.
     */
    fun applyChaos(lines: List<LyricsLine>, random: Random = Random(42)): List<LyricsLine> {
        if (lines.isEmpty()) return lines

        return lines.mapIndexed { index, line ->
            var startMs = line.startMs
            var endMs = line.endMs

            // 10% chance to make start time negative
            if (random.nextFloat() < 0.1f) {
                startMs = -random.nextLong(1000, 5000)
            }

            // 10% chance to make end time before start time (backward interval)
            if (random.nextFloat() < 0.1f) {
                endMs = startMs - random.nextLong(1, 1000)
            }

            // 15% chance to set end time to -1 (missing)
            if (random.nextFloat() < 0.15f) {
                endMs = -1L
            }

            // Chaos for words
            val chaoticWords = line.words.map { word ->
                var wStart = word.startMs
                var wEnd = word.endMs

                // 5% chance word goes out of bounds of the line
                if (random.nextFloat() < 0.05f) {
                    wStart = startMs - random.nextLong(100, 1000)
                }
                
                // 5% chance word ends before it starts
                if (random.nextFloat() < 0.05f) {
                    wEnd = wStart - 100L
                }

                WordLine(word.text, wStart, wEnd)
            }

            LyricsLine(line.text, startMs, endMs, chaoticWords)
        }.toMutableList().apply {
            // 5% chance to shuffle the lines completely to test out-of-order recovery
            if (random.nextFloat() < 0.05f) {
                shuffle(random)
            }
        }
    }
}
