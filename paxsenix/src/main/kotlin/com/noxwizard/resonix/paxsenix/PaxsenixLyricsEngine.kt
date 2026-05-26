package com.noxwizard.resonix.paxsenix

import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.providers.BetterLyricsProvider
import com.noxwizard.resonix.paxsenix.providers.KuGouProvider
import com.noxwizard.resonix.paxsenix.providers.LrcLibProvider
import com.noxwizard.resonix.paxsenix.providers.LyricsProvider
import com.noxwizard.resonix.paxsenix.providers.MusixMatchProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixAppleMusicProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixDeezerProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixNetEaseProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixQQMusicProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixYouTubeProvider
import com.noxwizard.resonix.paxsenix.providers.SpotifyProvider
import com.noxwizard.resonix.paxsenix.resolver.LyricsResolver
import com.noxwizard.resonix.paxsenix.resolver.RankedLyricsCandidate

/**
 * Primary entry point for the :paxsenix lyrics intelligence module.
 *
 * Provider pipeline (lowest priority number = queried first):
 *   1. MusixMatch (10) — primary metadata + lyrics matching
 *   2. Spotify    (20) — sync enhancement / karaoke / word-sync
 *   3. BetterLyrics (25) — TTML word-sync
 *   4. LRCLib     (30) — line-synced LRC
 *   5. KuGou      (40) — line-synced LRC
 *
 * All providers are queried; results are ranked by confidence.
 * A hard artist-similarity gate (≥ 0.65) prevents cross-artist false positives.
 *
 * Usage:
 * ```kotlin
 * val document: LyricsDocument? = PaxsenixLyricsEngine.default()
 *     .resolve(LyricsTrack(title = "Tera Hua", artist = "Atif Aslam", durationMs = 240_000L))
 * ```
 */
class PaxsenixLyricsEngine(
    providers: List<LyricsProvider> = defaultProviders(),
    minConfidence: Float = 0.5f,
    debugLogging: Boolean = false,
) {
    private val resolver = LyricsResolver(
        providers = providers,
        minConfidence = minConfidence,
        debugLogging = debugLogging,
    )

    suspend fun resolve(track: LyricsTrack): LyricsDocument? = resolver.resolve(track)

    suspend fun resolveAll(track: LyricsTrack): List<LyricsDocument> = resolver.resolveAll(track)

    /** Expose full candidate ranking — useful for debug screens or lyrics selection UI. */
    suspend fun resolveRanked(track: LyricsTrack): List<RankedLyricsCandidate> =
        resolver.resolveRanked(track)

    companion object {
        fun defaultProviders(): List<LyricsProvider> = listOf(
            MusixMatchProvider(),         // priority 10 — primary: reliable metadata for Bollywood/regional
            SpotifyProvider(),            // priority 20 — sync enhancement, requires spotifyTrackId
            BetterLyricsProvider(),       // priority 25 — TTML word-sync
            PaxsenixAppleMusicProvider(), // priority 26 — TTML word-sync
            PaxsenixNetEaseProvider(),    // priority 27 — CJK TTML/LRC
            LrcLibProvider(),             // priority 30 — line-synced LRC
            KuGouProvider(),              // priority 40 — line-synced LRC
            PaxsenixDeezerProvider(),     // priority 45 — line-synced LRC
            PaxsenixQQMusicProvider(),    // priority 50 — line-synced LRC
            PaxsenixYouTubeProvider(),    // priority 99 — fallback Transcript XML
        )

        fun default(): PaxsenixLyricsEngine = PaxsenixLyricsEngine()

        fun withDebug(): PaxsenixLyricsEngine = PaxsenixLyricsEngine(debugLogging = true)
    }
}
