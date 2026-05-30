package com.noxwizard.resonix.lyrics.playback

import com.noxwizard.resonix.paxsenix.models.SyncType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight singleton that holds the last known lyrics diagnostics snapshot.
 * Updated by [UnifiedLyricsEngine]; read by debug/diagnostics screens.
 *
 * Diagnostics are guaranteed non-null/non-zero for every resolved track:
 * - [providerName] is never "null" — defaults to "None"
 * - [resolveDurationMs] is -1L when unresolved (not 0)
 * - [titleSimilarity], [artistSimilarity] populated from resolver candidate
 * - [validationMode] reflects the validation tier used (MAINSTREAM / OBSCURE)
 */
object LyricsDiagnosticsHolder {

    data class Snapshot(
        /** Provider that won the resolver race (e.g. "MusixMatch"). */
        val providerName: String = "None",
        /** Sync quality of the selected lyrics. */
        val syncType: SyncType = SyncType.UNSYNCED,
        /** Final resolver confidence for the winning candidate (0f..1f). */
        val confidence: Float = 0f,
        /** Title similarity score from MatchScorer (0f..1f). */
        val titleSimilarity: Float = 0f,
        /** Artist similarity score from MatchScorer (0f..1f). */
        val artistSimilarity: Float = 0f,
        /** Duration delta between query and result in ms (-1 = not available). */
        val durationDeltaMs: Long = -1L,
        /** Currently active lyric line index. */
        val activeLineIndex: Int = -1,
        /** Current playback position in ms. */
        val currentPositionMs: Long = 0L,
        /** Start time of the active line in ms. */
        val activeLineStartMs: Long = 0L,
        /** End time of the active line in ms (-1 = not set). */
        val activeLineEndMs: Long = -1L,
        /** Whether the current gap is detected as instrumental. */
        val isInstrumental: Boolean = false,
        /** Total number of lyric lines in the loaded result. */
        val totalLines: Int = 0,
        /** Last resolver decision summary (multiline). */
        val lastResolverLog: String = "",
        /** Validation mode used: "MAINSTREAM", "OBSCURE", or "—". */
        val validationMode: String = "—",
        // Telemetry
        val resolveDurationMs: Long = -1L,
        val interpolationDurationMs: Long = 0L,
        val parserLatencyMs: Long = 0L,
        val cacheHit: Boolean = false,
        val validationCostMs: Long = 0L,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun update(block: Snapshot.() -> Snapshot) {
        _state.value = block(_state.value)
    }

    fun updatePlayback(playbackState: LyricsPlaybackState, totalLines: Int) {
        _state.value = _state.value.copy(
            activeLineIndex = playbackState.activeLineIndex,
            currentPositionMs = playbackState.currentPositionMs,
            activeLineStartMs = playbackState.currentLine?.startMs ?: 0L,
            activeLineEndMs = playbackState.currentLine?.endMs ?: -1L,
            isInstrumental = playbackState.isInstrumental,
            totalLines = totalLines,
        )
    }

    fun updateResolver(
        providerName: String,
        syncType: SyncType,
        confidence: Float,
        totalLines: Int,
        titleSimilarity: Float = 0f,
        artistSimilarity: Float = 0f,
        durationDeltaMs: Long = -1L,
        validationMode: String = "—",
        resolverLog: String = "",
    ) {
        _state.value = _state.value.copy(
            providerName = providerName.ifBlank { "None" },
            syncType = syncType,
            confidence = confidence,
            totalLines = totalLines,
            titleSimilarity = titleSimilarity,
            artistSimilarity = artistSimilarity,
            durationDeltaMs = durationDeltaMs,
            validationMode = validationMode,
            lastResolverLog = resolverLog,
        )
    }

    fun updateTelemetry(
        resolveDurationMs: Long = _state.value.resolveDurationMs,
        interpolationDurationMs: Long = _state.value.interpolationDurationMs,
        parserLatencyMs: Long = _state.value.parserLatencyMs,
        cacheHit: Boolean = _state.value.cacheHit,
        validationCostMs: Long = _state.value.validationCostMs,
    ) {
        _state.value = _state.value.copy(
            resolveDurationMs = resolveDurationMs,
            interpolationDurationMs = interpolationDurationMs,
            parserLatencyMs = parserLatencyMs,
            cacheHit = cacheHit,
            validationCostMs = validationCostMs,
        )
    }

    fun clear() {
        _state.value = Snapshot()
    }
}
