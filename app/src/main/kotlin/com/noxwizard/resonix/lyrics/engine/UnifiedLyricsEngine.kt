package com.noxwizard.resonix.lyrics.engine

import android.content.Context
import android.util.Log
import com.noxwizard.resonix.lyrics.playback.LyricsDiagnosticsHolder
import com.noxwizard.resonix.lyrics.playback.LyricsPlaybackResolver
import com.noxwizard.resonix.models.MediaMetadata
import com.noxwizard.resonix.paxsenix.PaxsenixLyricsEngine
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.utils.NetworkConnectivityObserver
import com.noxwizard.resonix.paxsenix.utils.TrackNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class UnifiedLyricsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
    private val lyricsCacheManager: LyricsCacheManager,
) {
    private val paxsenixEngine = PaxsenixLyricsEngine.default()
    private var currentLyricsJob: Job? = null

    /**
     * The single authoritative pipeline for resolving lyrics.
     *
     * Pipeline (Phase 13):
     * Track Metadata → Provider Racing → Validation → Ranking →
     * Word Interpolation → inferTimings() → Monotonic Enforcement →
     * Diagnostics → Cache → Renderer
     */
    suspend fun resolveLyrics(mediaMetadata: MediaMetadata): LyricsDocument? {
        currentLyricsJob?.cancel()

        // 0. Cache check
        val cached = lyricsCacheManager.getLyrics(mediaMetadata.id)
        if (cached != null) {
            Log.i("UnifiedLyricsEngine", "[CacheHit] provider=${cached.providerName} sync=${cached.syncType}")
            LyricsDiagnosticsHolder.updateTelemetry(
                resolveDurationMs = 0L,
                interpolationDurationMs = 0L,
                cacheHit = true,
                validationCostMs = 0L,
            )
            return cached
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) return null

        val songDurationMs = mediaMetadata.duration * 1000L

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val deferred = scope.async {
            val track = LyricsTrack(
                title = TrackNormalizer.cleanTitle(mediaMetadata.title),
                artist = TrackNormalizer.cleanArtist(mediaMetadata.artists.joinToString { it.name }),
                album = mediaMetadata.album?.title,
                durationMs = songDurationMs,
            )

            val startResolveMs = System.currentTimeMillis()

            // 1. Resolve & Rank Candidates (racing, adaptive validation, variant retry)
            val rankedCandidates = paxsenixEngine.resolveRanked(track)

            if (rankedCandidates.isEmpty()) {
                Log.w("UnifiedLyricsEngine", "[Reject] No candidates found")
                lyricsCacheManager.markNotFound(mediaMetadata.id)
                return@async null
            }

            // 2. Select top validated candidate (confidence gate ≥ 0.35)
            val selected = rankedCandidates
                .filter { it.passesArtistGate && it.confidence >= 0.35f }
                .maxByOrNull { it.finalScore }
                ?: rankedCandidates
                    .filter { it.passesArtistGate }
                    .maxByOrNull { it.finalScore }

            if (selected == null) {
                Log.w("UnifiedLyricsEngine", "[Reject] All candidates failed gates/confidence")
                lyricsCacheManager.markNotFound(mediaMetadata.id)
                return@async null
            }

            val document = selected.result
            val resolveEndMs = System.currentTimeMillis()

            // 3. Synthetic Word Interpolation
            val interpolatedLines = com.noxwizard.resonix.paxsenix.parser.WordInterpolationEngine.interpolate(document.lines)
            val interpolationEndMs = System.currentTimeMillis()

            // 4. Phase 9: inferTimings + monotonic enforcement (MUST run before cache)
            val timedLines = LyricsPlaybackResolver.inferTimings(interpolatedLines, songDurationMs)
            val monotonicLines = enforceMonotonicTimings(timedLines)

            val finalDocument = document.copy(
                lines = monotonicLines,
                syncType = if (monotonicLines.any { it.hasWordSync }) SyncType.WORD_SYNCED else document.syncType,
            )

            val lineCount = finalDocument.lines.size

            Log.i("UnifiedLyricsEngine", "[Selected] provider=${finalDocument.providerName} sync=${finalDocument.syncType} lines=$lineCount mode=${selected.validationMode}")

            // 5. Emit Diagnostics (Phase 11: full fields, no nulls, no zero resolve time)
            val durationDeltaMs = if (document.lines.isNotEmpty() && track.durationMs > 0) {
                // Approximate delta from last line's startMs vs expected duration
                abs((document.lines.last().startMs) - track.durationMs)
            } else -1L

            LyricsDiagnosticsHolder.updateResolver(
                providerName = finalDocument.providerName,
                syncType = finalDocument.syncType,
                confidence = selected.confidence,
                totalLines = lineCount,
                titleSimilarity = selected.titleScore,
                artistSimilarity = selected.artistScore,
                durationDeltaMs = durationDeltaMs,
                validationMode = selected.validationMode.name,
                resolverLog = buildString {
                    appendLine("Provider: ${finalDocument.providerName}")
                    appendLine("Category: ${finalDocument.providerCategory}")
                    appendLine("Sync: ${finalDocument.syncType}")
                    appendLine("Mode: ${selected.validationMode}")
                    appendLine("Final Score: ${selected.finalScore}")
                    appendLine("Title Sim: ${"%.2f".format(selected.titleScore)}")
                    appendLine("Artist Sim: ${"%.2f".format(selected.artistScore)}")
                    appendLine("Provider Latency: ${selected.providerLatencyMs}ms")
                },
            )

            LyricsDiagnosticsHolder.updateTelemetry(
                resolveDurationMs = (resolveEndMs - startResolveMs).coerceAtLeast(1L),
                interpolationDurationMs = interpolationEndMs - resolveEndMs,
                cacheHit = false,
                validationCostMs = 0L,
            )

            // 6. Save to cache
            lyricsCacheManager.putLyrics(mediaMetadata.id, finalDocument)

            return@async finalDocument
        }

        val document = deferred.await()
        scope.cancel()
        return document
    }

    /**
     * Phase 9: Enforces monotonic timing — ensures line[n+1].startMs > line[n].startMs.
     * Fixes inversions by nudging the offending line forward by 1ms.
     */
    private fun enforceMonotonicTimings(
        lines: List<com.noxwizard.resonix.paxsenix.models.LyricsLine>,
    ): List<com.noxwizard.resonix.paxsenix.models.LyricsLine> {
        if (lines.size < 2) return lines
        val result = lines.toMutableList()
        for (i in 1 until result.size) {
            if (result[i].startMs <= result[i - 1].startMs) {
                result[i] = result[i].copy(startMs = result[i - 1].startMs + 1L)
            }
        }
        return result
    }

    fun cancel() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }
}
