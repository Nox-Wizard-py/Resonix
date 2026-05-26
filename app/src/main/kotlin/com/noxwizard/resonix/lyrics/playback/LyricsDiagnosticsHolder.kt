package com.noxwizard.resonix.lyrics.playback

import com.noxwizard.resonix.paxsenix.models.SyncType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight singleton that holds the last known lyrics diagnostics snapshot.
 * Updated by whatever component drives the lyrics engine; read by [DebugSettings].
 *
 * This is intentionally kept as a simple observable data holder with NO business logic.
 * It is NOT a ViewModel — diagnostics data is transient and debug-only.
 */
object LyricsDiagnosticsHolder {

    data class Snapshot(
        /** Provider that won the resolver race (e.g. "MusixMatch"). */
        val providerName: String = "—",
        /** Sync quality of the selected lyrics. */
        val syncType: SyncType = SyncType.UNSYNCED,
        /** Final resolver confidence for the winning candidate (0f..1f). */
        val confidence: Float = 0f,
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
        // Telemetry
        val resolveDurationMs: Long = 0L,
        val interpolationDurationMs: Long = 0L,
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
        resolverLog: String = "",
    ) {
        _state.value = _state.value.copy(
            providerName = providerName,
            syncType = syncType,
            confidence = confidence,
            totalLines = totalLines,
            lastResolverLog = resolverLog,
        )
    }

    fun updateTelemetry(
        resolveDurationMs: Long = _state.value.resolveDurationMs,
        interpolationDurationMs: Long = _state.value.interpolationDurationMs,
        cacheHit: Boolean = _state.value.cacheHit,
        validationCostMs: Long = _state.value.validationCostMs,
    ) {
        _state.value = _state.value.copy(
            resolveDurationMs = resolveDurationMs,
            interpolationDurationMs = interpolationDurationMs,
            cacheHit = cacheHit,
            validationCostMs = validationCostMs,
        )
    }

    fun clear() {
        _state.value = Snapshot()
    }
}
