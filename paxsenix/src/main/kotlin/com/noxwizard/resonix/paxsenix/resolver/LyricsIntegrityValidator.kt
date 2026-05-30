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
 * Two adaptive validation modes (Phase 4):
 *
 * MAINSTREAM (default):
 *   - titleSimilarity >= 0.85
 *   - artistSimilarity >= 0.75
 *   - durationDelta <= 8 000ms
 *
 * OBSCURE (activated on retry when premium providers fail):
 *   - titleSimilarity >= 0.60
 *   - artistSimilarity >= 0.55
 *   - durationDelta <= 15 000ms
 *
 * Wrong-song contamination detection remains active in both modes.
 */
object LyricsIntegrityValidator {

    enum class ValidationMode {
        /** Strict thresholds — used for first-pass resolution. */
        MAINSTREAM,
        /** Relaxed thresholds — activates when mainstream pass yields zero results. */
        OBSCURE,
    }

    // MAINSTREAM thresholds
    private const val MAINSTREAM_TITLE_SIM = 0.85f
    private const val MAINSTREAM_ARTIST_SIM = 0.75f
    private const val MAINSTREAM_DURATION_MS = 8_000L

    // OBSCURE thresholds
    private const val OBSCURE_TITLE_SIM = 0.60f
    private const val OBSCURE_ARTIST_SIM = 0.55f
    private const val OBSCURE_DURATION_MS = 15_000L

    // Fraction of lyric lines that may reference a foreign title before rejection
    private const val FOREIGN_TITLE_CONTAMINATION_THRESHOLD = 0.25f

    sealed interface ValidationResult {
        data object Accept : ValidationResult
        data class Reject(val reason: String) : ValidationResult
    }

    /**
     * Full validation pipeline. Returns [ValidationResult.Accept] or
     * [ValidationResult.Reject] with a human-readable reason.
     *
     * @param mode [ValidationMode.MAINSTREAM] (default) or [ValidationMode.OBSCURE]
     */
    fun validate(
        track: LyricsTrack,
        result: LyricsDocument,
        candidateTitle: String = track.title,
        candidateArtist: String = track.artist,
        candidateDurationMs: Long = -1L,
        mode: ValidationMode = ValidationMode.MAINSTREAM,
    ): ValidationResult {
        val minArtistSim = if (mode == ValidationMode.MAINSTREAM) MAINSTREAM_ARTIST_SIM else OBSCURE_ARTIST_SIM
        val minTitleSim = if (mode == ValidationMode.MAINSTREAM) MAINSTREAM_TITLE_SIM else OBSCURE_TITLE_SIM
        val maxDurationMs = if (mode == ValidationMode.MAINSTREAM) MAINSTREAM_DURATION_MS else OBSCURE_DURATION_MS

        // ── 1. Artist similarity ─────────────────────────────────────────────
        val artistSim = similarity(
            TrackNormalizer.normalizeArtist(track.artist),
            TrackNormalizer.normalizeArtist(candidateArtist),
        )
        if (artistSim < minArtistSim) {
            return ValidationResult.Reject(
                "Artist similarity too low: %.2f (min %.2f) [\"${track.artist}\" vs \"$candidateArtist\"] [$mode]"
                    .format(artistSim, minArtistSim)
            )
        }

        // ── 2. Title similarity ──────────────────────────────────────────────
        val titleSim = similarity(
            TrackNormalizer.normalizeTitle(track.title),
            TrackNormalizer.normalizeTitle(candidateTitle),
        )
        if (titleSim < minTitleSim) {
            return ValidationResult.Reject(
                "Title similarity too low: %.2f (min %.2f) [\"${track.title}\" vs \"$candidateTitle\"] [$mode]"
                    .format(titleSim, minTitleSim)
            )
        }

        // ── 3. Duration mismatch ─────────────────────────────────────────────
        if (candidateDurationMs > 0 && track.durationMs > 0) {
            val diff = abs(candidateDurationMs - track.durationMs)
            if (diff > maxDurationMs) {
                return ValidationResult.Reject(
                    "Duration mismatch: ${diff}ms (max ${maxDurationMs}ms) [$mode]"
                )
            }
        }

        // ── 4. Foreign title contamination (always active) ───────────────────
        val foreignPenalty = detectForeignTitlePenalty(track, result.lines)
        if (foreignPenalty >= FOREIGN_TITLE_CONTAMINATION_THRESHOLD) {
            return ValidationResult.Reject(
                "Foreign title contamination: ${"%.0f".format(foreignPenalty * 100)}% of lines reference a different song"
            )
        }

        return ValidationResult.Accept
    }

    /**
     * Scans lyric lines for repeated references to a known foreign song title.
     * Contamination ratio: contaminated_lines / total_lines.
     * Returns a value in 0f..1f — caller decides threshold.
     */
    fun detectForeignTitlePenalty(track: LyricsTrack, lines: List<LyricsLine>): Float {
        if (lines.isEmpty()) return 0f

        val requestedTokens = tokenise(track.title)
        if (requestedTokens.isEmpty()) return 0f

        val phraseFrequency = mutableMapOf<String, Int>()
        for (line in lines) {
            val words = tokenise(line.text)
            for (size in 2..3) {
                for (i in 0..words.size - size) {
                    val phrase = words.subList(i, i + size).joinToString(" ")
                    if (!overlapsWithRequestedTitle(phrase, requestedTokens)) {
                        phraseFrequency[phrase] = (phraseFrequency[phrase] ?: 0) + 1
                    }
                }
            }
        }

        val lyricsLineCount = lines.size.toFloat()
        val foreignPhrases = phraseFrequency.filter { (_, count) ->
            count.toFloat() / lyricsLineCount >= 0.20f
        }

        if (foreignPhrases.isEmpty()) return 0f

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
