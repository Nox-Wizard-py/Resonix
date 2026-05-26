package com.noxwizard.resonix.lyrics.playback

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.WordLine

/**
 * Represents the authoritative playback state for lyrics at a given position.
 *
 * This is the ONLY place in the codebase that owns the "which line is active"
 * computation. UI renderers must consume this and MUST NOT re-compute timing.
 *
 * @param activeLineIndex The index of the currently active lyric line (-1 if none).
 * @param activeWordIndex The index of the active word within the active line (null if not word-synced).
 * @param currentLine The currently active [LyricsLine], or null.
 * @param nextLine The next [LyricsLine] after the current one, or null.
 * @param progress 0f..1f interpolation progress within the current line.
 * @param isInstrumental True when the next line starts more than 8 000ms away.
 * @param currentPositionMs The playback position this state was computed for.
 */
data class LyricsPlaybackState(
    val activeLineIndex: Int,
    val activeWordIndex: Int?,
    val currentLine: LyricsLine?,
    val nextLine: LyricsLine?,
    val progress: Float,
    val isInstrumental: Boolean,
    val currentPositionMs: Long,
) {
    companion object {
        val EMPTY = LyricsPlaybackState(
            activeLineIndex = -1,
            activeWordIndex = null,
            currentLine = null,
            nextLine = null,
            progress = 0f,
            isInstrumental = false,
            currentPositionMs = 0L,
        )
    }
}

/**
 * Single source of truth for lyric playback state resolution.
 *
 * Key invariants:
 * - Uses `indexOfLast { currentMs >= line.startMs }` — never `currentMs in start..end`.
 *   This prevents freeze bugs caused by malformed or missing endMs values.
 * - Safely infers endMs for every line at call time using the next line's startMs.
 * - Instrumental gaps are detected when the next line is > 8 000ms away.
 * - Word-level active index is resolved independently from line-level.
 */
object LyricsPlaybackResolver {

    private const val INSTRUMENTAL_GAP_MS = 8_000L
    private const val FALLBACK_LAST_LINE_DURATION_MS = 5_000L

    /**
     * Resolves the current [LyricsPlaybackState] for [lines] at [currentPositionMs].
     *
     * This is the hot-path function called every frame by the renderer. It is
     * intentionally pure (no side effects) for easy testing.
     *
     * @param leadMs An optional offset added to [currentPositionMs] to shift transitions earlier.
     */
    fun resolve(
        lines: List<LyricsLine>,
        currentPositionMs: Long,
        songDurationMs: Long = -1L,
        leadMs: Long = 300L,
    ): LyricsPlaybackState {
        if (lines.isEmpty()) return LyricsPlaybackState.EMPTY

        val searchMs = currentPositionMs + leadMs

        // ── Active line — use binary search, never range check ────────────────
        // This is the canonical fix for autoscroll freeze from malformed endMs.
        // O(log N) efficiency for huge lyric files.
        val activeIndex = lines.binarySearchLastBy(searchMs) { it.startMs }
        if (activeIndex < 0) return LyricsPlaybackState.EMPTY

        val currentLine = lines[activeIndex]
        val nextLine = lines.getOrNull(activeIndex + 1)

        // ── Infer endMs safely ───────────────────────────────────────────────
        val effectiveEndMs = inferredEndMs(currentLine, nextLine, songDurationMs)

        // ── Instrumental gap detection ───────────────────────────────────────
        val isInstrumental = nextLine != null &&
                (nextLine.startMs - currentPositionMs) > INSTRUMENTAL_GAP_MS

        // ── Progress 0f..1f ──────────────────────────────────────────────────
        val lineSpan = (effectiveEndMs - currentLine.startMs).coerceAtLeast(1L)
        val elapsed = (currentPositionMs - currentLine.startMs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / lineSpan.toFloat()).coerceIn(0f, 1f)

        // ── Active word ──────────────────────────────────────────────────────
        val activeWordIndex = resolveActiveWord(currentLine.words, searchMs)

        return LyricsPlaybackState(
            activeLineIndex = activeIndex,
            activeWordIndex = activeWordIndex,
            currentLine = currentLine,
            nextLine = nextLine,
            progress = progress,
            isInstrumental = isInstrumental,
            currentPositionMs = currentPositionMs, // return true position without lead
        )
    }

    /**
     * Batch pre-processes a list of lines to ensure every line has a valid endMs.
     * Call this once after loading lyrics, before handing lines to [resolve].
     *
     * Rules:
     * - If a line already has endMs set (endMs != -1L), keep it.
     * - Otherwise, set endMs = nextLine.startMs - 1.
     * - For the final line: use songDurationMs if available, else startMs + 5 000ms.
     */
    fun inferTimings(
        lines: List<LyricsLine>,
        songDurationMs: Long = -1L,
    ): List<LyricsLine> {
        if (lines.isEmpty()) return lines
        return lines.mapIndexed { i, line ->
            if (line.endMs != -1L) return@mapIndexed line  // already set
            val inferred = inferredEndMs(line, lines.getOrNull(i + 1), songDurationMs)
            line.copy(endMs = inferred)
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /** Compute the effective endMs for a line without mutating it. */
    private fun inferredEndMs(
        line: LyricsLine,
        nextLine: LyricsLine?,
        songDurationMs: Long,
    ): Long = when {
        line.endMs != -1L -> line.endMs
        nextLine != null -> nextLine.startMs - 1L
        songDurationMs > 0 -> songDurationMs
        else -> line.startMs + FALLBACK_LAST_LINE_DURATION_MS
    }

    /**
     * Finds the active word in [words] for [searchMs].
     * Returns null if the line has no word-level timing.
     */
    private fun resolveActiveWord(words: List<WordLine>, searchMs: Long): Int? {
        if (words.isEmpty()) return null
        val idx = words.binarySearchLastBy(searchMs) { it.startMs }
        return if (idx < 0) null else idx
    }

    /**
     * O(log N) binary search finding the largest index where selector(item) <= target.
     * Requires the list to be sorted by selector.
     */
    private fun <T> List<T>.binarySearchLastBy(target: Long, selector: (T) -> Long): Int {
        var low = 0
        var high = size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = selector(this[mid])

            if (midVal <= target) {
                result = mid
                low = mid + 1 // Target might be here, but check if there are later items <= target
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
