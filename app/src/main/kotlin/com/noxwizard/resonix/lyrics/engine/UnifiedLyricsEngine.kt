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
     * Track Metadata → Provider Resolver → Validation → Ranking → Diagnostics.
     */
    suspend fun resolveLyrics(mediaMetadata: MediaMetadata): LyricsDocument? {
        currentLyricsJob?.cancel()

        // 0. Check Cache First
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

        if (!isNetworkAvailable) {
            return null
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val deferred = scope.async {
            val track = LyricsTrack(
                title = TrackNormalizer.cleanTitle(mediaMetadata.title),
                artist = TrackNormalizer.cleanArtist(mediaMetadata.artists.joinToString { it.name }),
                album = mediaMetadata.album?.title,
                durationMs = mediaMetadata.duration * 1000L,
            )

            val startResolveMs = System.currentTimeMillis()

            // 1. Resolve & Rank Candidates
            val rankedCandidates = paxsenixEngine.resolveRanked(track)

            if (rankedCandidates.isEmpty()) {
                Log.w("UnifiedLyricsEngine", "[Reject] No candidates found")
                lyricsCacheManager.markNotFound(mediaMetadata.id)
                return@async null
            }

            // 2. Select the top candidate (the resolver already filters by minConfidence and validates)
            val selected = rankedCandidates
                .filter { it.passesArtistGate && it.confidence >= 0.35f }
                .maxByOrNull { it.finalScore }

            if (selected == null) {
                Log.w("UnifiedLyricsEngine", "[Reject] All candidates failed gates/confidence")
                lyricsCacheManager.markNotFound(mediaMetadata.id)
                return@async null
            }

            val document = selected.result
            val lineCount = document.lines.size
            val resolveEndMs = System.currentTimeMillis()

            // 2.5 Synthetic Word Interpolation
            // If the provider didn't supply native word-sync, generate it heuristically.
            val interpolatedLines = com.noxwizard.resonix.paxsenix.parser.WordInterpolationEngine.interpolate(document.lines)
            val interpolationEndMs = System.currentTimeMillis()

            val finalDocument = document.copy(
                lines = interpolatedLines,
                // Upgrade syncType if we successfully interpolated it
                syncType = if (interpolatedLines.any { it.hasWordSync }) SyncType.WORD_SYNCED else document.syncType
            )

            Log.i("UnifiedLyricsEngine", "[Selected] provider=${finalDocument.providerName} sync=${finalDocument.syncType} lines=$lineCount")

            // 3. Emit Diagnostics
            LyricsDiagnosticsHolder.updateResolver(
                providerName = finalDocument.providerName,
                syncType = finalDocument.syncType,
                confidence = selected.confidence,
                totalLines = lineCount,
                resolverLog = buildString {
                    appendLine("Provider: ${finalDocument.providerName}")
                    appendLine("Category: ${finalDocument.providerCategory}")
                    appendLine("Sync: ${finalDocument.syncType}")
                    appendLine("Final Score: ${selected.finalScore}")
                    appendLine("Validation: ${selected.validationResult}")
                }
            )

            LyricsDiagnosticsHolder.updateTelemetry(
                resolveDurationMs = resolveEndMs - startResolveMs,
                interpolationDurationMs = interpolationEndMs - resolveEndMs,
                cacheHit = false,
                validationCostMs = 0L // validation cost is handled inside resolver, hard to pull out unless we add it to candidate
            )

            // Save to cache
            lyricsCacheManager.putLyrics(mediaMetadata.id, finalDocument)

            return@async finalDocument
        }

        val document = deferred.await()
        scope.cancel()
        return document
    }

    fun cancel() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }
}
