package com.noxwizard.resonix.paxsenix.resolver

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.utils.TrackNormalizer
import com.noxwizard.resonix.paxsenix.utils.JaroWinklerCalculator
import kotlin.math.abs
import kotlin.math.max

/**
 * Validates a fetched [LyricsDocument] against the requested [LyricsTrack] BEFORE
 * the resolver accepts the result into the final ranking pool.
 *
 * Rules (in evaluation order):
 * 1. Artist similarity ≥ 0.75 — hard gate
 * 2. Title similarity ≥ 0.70 — hard gate
 * 3. Duration difference ≤ 15 000 ms — hard gate
 * 4. Foreign title contamination penalty / rejection
 */
object LyricsIntegrityValidator {

    private const val MIN_ARTIST_SIMILARITY = 0.75f
    private const val MIN_TITLE_SIMILARITY = 0.75f
    private const val MAX_DURATION_DIFF_MS = 5_000L

    // Fraction of lyric lines that may contain a foreign title token before we reject
    private const val FOREIGN_TITLE_CONTAMINATION_THRESHOLD = 0.25f

    sealed interface ValidationResult {
        data object Accept : ValidationResult
        data class Reject(val reason: String) : ValidationResult
    }

    /**
     * Full validation pipeline. Returns [ValidationResult.Accept] or
     * [ValidationResult.Reject] with a human-readable reason.
     */
    fun validate(
        track: LyricsTrack,
        result: LyricsDocument,
        candidateTitle: String = track.title,
        candidateArtist: String = track.artist,
        candidateDurationMs: Long = -1L,
    ): ValidationResult {
        // ── 1. Artist similarity ────────────────────────────────────────────
        val artistSim = similarity(
            TrackNormalizer.normalizeArtist(track.artist),
            TrackNormalizer.normalizeArtist(candidateArtist),
        )
        if (artistSim < MIN_ARTIST_SIMILARITY) {
            return ValidationResult.Reject(
                "Artist similarity too low: %.2f (min %.2f) [\"${track.artist}\" vs \"$candidateArtist\"]"
                    .format(artistSim, MIN_ARTIST_SIMILARITY)
            )
        }

        // ── 2. Title similarity ─────────────────────────────────────────────
        val titleSim = similarity(
            TrackNormalizer.normalizeTitle(track.title),
            TrackNormalizer.normalizeTitle(candidateTitle),
        )
        if (titleSim < MIN_TITLE_SIMILARITY) {
            return ValidationResult.Reject(
                "Title similarity too low: %.2f (min %.2f) [\"${track.title}\" vs \"$candidateTitle\"]"
                    .format(titleSim, MIN_TITLE_SIMILARITY)
            )
        }

        // ── 3. Duration mismatch ────────────────────────────────────────────
        if (candidateDurationMs > 0 && track.durationMs > 0) {
            val diff = abs(candidateDurationMs - track.durationMs)
            if (diff > MAX_DURATION_DIFF_MS) {
                return ValidationResult.Reject(
                    "Duration mismatch too large: ${diff}ms (max ${MAX_DURATION_DIFF_MS}ms)"
                )
            }
        }

        // ── 4. Foreign title contamination ──────────────────────────────────
        val foreignPenalty = detectForeignTitlePenalty(track, result.lines)
        if (foreignPenalty >= FOREIGN_TITLE_CONTAMINATION_THRESHOLD) {
            return ValidationResult.Reject(
                "Foreign title contamination detected: ${"%.0f".format(foreignPenalty * 100)}% of lines reference a different song"
            )
        }

        return ValidationResult.Accept
    }

    /**
     * Scans lyric lines for repeated references to a known foreign song title
     * (i.e. a title significantly different from the requested track title).
     *
     * Strategy:
     * - Tokenise the requested track title into meaningful words (≥ 3 chars).
     * - Build a normalised token set.
     * - For each lyric line, check if it contains multiple tokens from a DIFFERENT
     *   common phrase that does NOT overlap with the requested title tokens.
     * - Return a contamination ratio: contaminated_lines / total_lines.
     *
     * Returns a value in 0f..1f; caller decides threshold.
     */
    fun detectForeignTitlePenalty(track: LyricsTrack, lines: List<LyricsLine>): Float {
        if (lines.isEmpty()) return 0f

        val requestedTokens = tokenise(track.title)
        if (requestedTokens.isEmpty()) return 0f

        // Build phrase fingerprints: sliding windows of 2-3 consecutive words
        // that appear repeatedly in the lyrics (candidate "foreign title")
        val phraseFrequency = mutableMapOf<String, Int>()
        for (line in lines) {
            val words = tokenise(line.text)
            for (size in 2..3) {
                for (i in 0..words.size - size) {
                    val phrase = words.subList(i, i + size).joinToString(" ")
                    // Only track phrases that don't overlap with the requested title
                    if (!overlapsWithRequestedTitle(phrase, requestedTokens)) {
                        phraseFrequency[phrase] = (phraseFrequency[phrase] ?: 0) + 1
                    }
                }
            }
        }

        // Find phrases repeated in ≥ 20% of lines (candidate foreign hook/title)
        val lyricsLineCount = lines.size.toFloat()
        val foreignPhrases = phraseFrequency.filter { (_, count) ->
            count.toFloat() / lyricsLineCount >= 0.20f
        }

        if (foreignPhrases.isEmpty()) return 0f

        // Count lines contaminated by any foreign repeated phrase
        val contaminated = lines.count { line ->
            val text = TrackNormalizer.normalize(line.text)
            foreignPhrases.keys.any { phrase -> text.contains(phrase) }
        }

        return contaminated.toFloat() / lyricsLineCount
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun tokenise(text: String): List<String> =
        TrackNormalizer.normalize(text)
            .split(Regex("""\s+"""))
            .filter { it.length >= 3 }

    private fun overlapsWithRequestedTitle(phrase: String, requestedTokens: List<String>): Boolean =
        requestedTokens.any { phrase.contains(it) || it.contains(phrase) }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a.contains(b) || b.contains(a)) return 0.85f
        return JaroWinklerCalculator.calculate(a, b)
    }
}
