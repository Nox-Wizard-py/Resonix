package com.noxwizard.resonix.paxsenix

import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.providers.BetterLyricsProvider
import com.noxwizard.resonix.paxsenix.providers.KuGouProvider
import com.noxwizard.resonix.paxsenix.providers.LrcLibProvider
import com.noxwizard.resonix.paxsenix.providers.LyricsProvider
import com.noxwizard.resonix.paxsenix.providers.MusixMatchProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixAppleMusicProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixNetEaseProvider
import com.noxwizard.resonix.paxsenix.providers.PaxsenixYouTubeProvider
import com.noxwizard.resonix.paxsenix.providers.SpotifyProvider
import com.noxwizard.resonix.paxsenix.resolver.LyricsResolver
import com.noxwizard.resonix.paxsenix.resolver.RankedLyricsCandidate

/**
 * Primary entry point for the :paxsenix lyrics intelligence module.
 *
 * Provider pipeline (priority order):
 *   1. MusixMatch         — word-synced, premium metadata coverage
 *   2. Spotify            — word-synced karaoke / word-sync enhancement
 *   3. BetterLyrics       — TTML word-sync (Apple Music via BetterLyrics API)
 *   4. PaxsenixAppleMusic — TTML word-sync from Apple Music catalog
 *   5. PaxsenixNetEase    — CJK TTML / line-synced
 *   6. LRCLib             — line-synced community (broad coverage)
 *   7. KuGou              — line-synced community
 *   8. PaxsenixYouTube    — transcript fallback (last resort)
 *
 * All providers are queried concurrently; results are ranked by confidence.
 * A hard artist-similarity gate (≥ 0.65) prevents cross-artist false positives.
 * Adaptive validation (MAINSTREAM / OBSCURE) handles niche tracks.
 */
class PaxsenixLyricsEngine(
    providers: List<LyricsProvider> = defaultProviders(),
    minConfidence: Float = 0.35f,
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
            MusixMatchProvider(),          // priority 1  — word-synced, best metadata
            SpotifyProvider(),             // priority 2  — word-synced karaoke
            BetterLyricsProvider(),        // priority 3  — TTML word-sync
            PaxsenixAppleMusicProvider(),  // priority 4  — TTML word-sync
            PaxsenixNetEaseProvider(),     // priority 5  — CJK TTML/LRC
            LrcLibProvider(),              // priority 6  — line-synced LRC (broad)
            KuGouProvider(),               // priority 7  — line-synced LRC
            PaxsenixYouTubeProvider(),     // priority 8 — transcript fallback
        )

        fun default(): PaxsenixLyricsEngine = PaxsenixLyricsEngine()

        fun withDebug(): PaxsenixLyricsEngine = PaxsenixLyricsEngine(debugLogging = true)
    }
}

