package com.noxwizard.resonix.lyrics

import com.noxwizard.resonix.paxsenix.utils.TrackNormalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * Validates an LRC/lyrics string against expected track metadata BEFORE
 * the [com.noxwizard.resonix.lyrics.engine.UnifiedLyricsEngine] stores it.
 *
 * This is a lightweight standalone validator that operates on raw LRC strings
 * (no :paxsenix models needed) so it can be inserted into the existing pipeline
 * without coupling `:app` more deeply to `:paxsenix`.
 *
 * Rules (applied in order):
 * 1. Duration gate — reject if duration diff > 15 s
 * 2. Title contamination — scan first 5 lines for a repeated phrase that
 *    doesn't overlap with the requested title (catches "Janam Janam" in "Itni Si Baat Hain")
 */
object LyricsIntegrityGate {

    private const val MAX_DURATION_DIFF_MS = 15_000
    private const val CONTAMINATION_THRESHOLD = 0.30f   // > 30 % of lines = reject
    private const val MIN_PHRASE_REPETITIONS = 2        // phrase must appear ≥ 2× to matter
    private val LRC_TIMESTAMP_RE = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

    data class GateResult(val accepted: Boolean, val reason: String = "OK")

    /**
     * @param title  Requested song title
     * @param artist Requested artist
     * @param expectedDurationMs Expected duration in ms (-1 = unknown)
     * @param lrc    Raw LRC string returned by the provider
     */
    fun validate(
        title: String,
        artist: String,
        expectedDurationMs: Int,
        lrc: String,
    ): GateResult {
        val lines = parseLrcLines(lrc)
        if (lines.isEmpty()) return GateResult(false, "Empty LRC body")

        // ── 1. Duration gate ─────────────────────────────────────────────────
        if (expectedDurationMs > 0 && lines.size > 1) {
            val lastTimestampMs = lines.last().first
            val diff = abs(lastTimestampMs - expectedDurationMs)
            if (diff > MAX_DURATION_DIFF_MS) {
                return GateResult(
                    false,
                    "[DurationGate] last-timestamp=${lastTimestampMs}ms expected=${expectedDurationMs}ms diff=${diff}ms"
                )
            }
        }

        // ── 2. Foreign title contamination ───────────────────────────────────
        val contaminationRatio = detectContamination(title, lines.map { it.second })
        if (contaminationRatio >= CONTAMINATION_THRESHOLD) {
            return GateResult(
                false,
                "[ContaminationGate] ratio=${"%.0f".format(contaminationRatio * 100)}% of lines match a foreign repeated phrase"
            )
        }

        return GateResult(true)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Parse LRC into list of (timestampMs, lyricText) pairs. */
    private fun parseLrcLines(lrc: String): List<Pair<Long, String>> =
        lrc.lines().mapNotNull { line ->
            val m = LRC_TIMESTAMP_RE.matchEntire(line.trim()) ?: return@mapNotNull null
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val milStr = m.groupValues[3]
            var mil = milStr.toLong()
            if (milStr.length == 2) mil *= 10
            val ms = min * 60_000L + sec * 1_000L + mil
            val text = m.groupValues[4].trim()
            if (text.isNotEmpty()) ms to text else null
        }

    /**
     * Returns the contamination ratio: fraction of lines that contain a
     * frequently repeating phrase not found in the requested title.
     *
     * Strategy:
     * - Extract 2-token phrases from each lyric line
     * - Count phrase frequency across all lines
     * - Find phrases that occur in ≥ MIN_PHRASE_REPETITIONS lines
     *   AND whose tokens don't appear in the normalised title
     * - Return (lines containing any such phrase) / total lines
     */
    private fun detectContamination(title: String, lines: List<String>): Float {
        if (lines.isEmpty()) return 0f

        val titleTokens = tokenise(title)
        val phraseFrequency = mutableMapOf<String, Int>()

        for (line in lines) {
            val words = tokenise(line)
            for (size in 2..3) {
                for (i in 0..words.size - size) {
                    val phrase = words.subList(i, i + size).joinToString(" ")
                    if (!overlapsTitle(phrase, titleTokens)) {
                        phraseFrequency[phrase] = (phraseFrequency[phrase] ?: 0) + 1
                    }
                }
            }
        }

        // Foreign phrases = those that repeat enough to be a "hook"
        val foreignHooks = phraseFrequency.filter { (_, count) -> count >= MIN_PHRASE_REPETITIONS }
        if (foreignHooks.isEmpty()) return 0f

        val contaminated = lines.count { line ->
            val text = TrackNormalizer.normalize(line)
            foreignHooks.keys.any { phrase -> text.contains(phrase) }
        }
        return contaminated.toFloat() / lines.size
    }

    private fun tokenise(text: String): List<String> =
        TrackNormalizer.normalize(text).split(Regex("""\s+""")).filter { it.length >= 3 }

    private fun overlapsTitle(phrase: String, titleTokens: List<String>): Boolean =
        titleTokens.any { phrase.contains(it) || it.contains(phrase) }
}
