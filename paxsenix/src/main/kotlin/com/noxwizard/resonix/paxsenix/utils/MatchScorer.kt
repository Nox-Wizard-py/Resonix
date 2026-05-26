package com.noxwizard.resonix.paxsenix.utils

import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.ProviderMetadata
import kotlin.math.abs
import kotlin.math.max

// Hard gate: duration difference beyond this triggers aggressive penalty
private const val DURATION_SOFT_TOLERANCE_SEC = 5
private const val DURATION_HARD_CUTOFF_SEC = 12

// Hard gate: reject any candidate with artist similarity below this
const val MIN_ARTIST_SIMILARITY = 0.65f

object MatchScorer {

    data class ScoreWeights(
        val artist: Float = 0.45f,   // strongest signal — prevents cross-artist false positives
        val title: Float = 0.35f,
        val duration: Float = 0.15f, // synced lyrics are duration-critical
        val album: Float = 0.05f,
    )

    /**
     * Detailed breakdown used by the resolver for ranking and debug logging.
     */
    data class ScoreBreakdown(
        val artistScore: Float,
        val titleScore: Float,
        val durationScore: Float,
        val albumScore: Float,
        val finalScore: Float,
        val passesArtistGate: Boolean,
    )

    fun score(
        track: LyricsTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String? = null,
        candidateDurationSec: Int = -1,
        weights: ScoreWeights = ScoreWeights(),
    ): Float = breakdown(track, candidateTitle, candidateArtist, candidateAlbum, candidateDurationSec, weights).finalScore

    fun breakdown(
        track: LyricsTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String? = null,
        candidateDurationSec: Int = -1,
        weights: ScoreWeights = ScoreWeights(),
    ): ScoreBreakdown {
        val artistScore = similarity(
            TrackNormalizer.normalizeArtist(track.artist),
            TrackNormalizer.normalizeArtist(candidateArtist),
        )
        val titleScore = similarity(
            TrackNormalizer.normalizeTitle(track.title),
            TrackNormalizer.normalizeTitle(candidateTitle),
        )
        val albumScore = when {
            track.album != null && candidateAlbum != null ->
                similarity(
                    TrackNormalizer.normalizeAlbum(track.album)!!,
                    TrackNormalizer.normalizeAlbum(candidateAlbum)!!,
                )
            else -> 0.5f  // neutral when unavailable
        }
        val durationScore = durationScore(track.durationSec, candidateDurationSec)

        val final = artistScore * weights.artist +
                titleScore * weights.title +
                albumScore * weights.album +
                durationScore * weights.duration

        return ScoreBreakdown(
            artistScore = artistScore,
            titleScore = titleScore,
            durationScore = durationScore,
            albumScore = albumScore,
            finalScore = final,
            passesArtistGate = artistScore >= MIN_ARTIST_SIMILARITY,
        )
    }

    fun buildMetadata(
        providerName: String,
        track: LyricsTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String? = null,
        candidateDurationSec: Int = -1,
        sourceUrl: String? = null,
        trackId: String? = null,
        language: String? = null,
    ): ProviderMetadata = ProviderMetadata(
        providerName = providerName,
        sourceUrl = sourceUrl,
        confidence = score(track, candidateTitle, candidateArtist, candidateAlbum, candidateDurationSec),
        trackId = trackId,
        language = language,
    )

    private fun durationScore(queryDurationSec: Int, candidateDurationSec: Int): Float {
        if (queryDurationSec == -1 || candidateDurationSec == -1) return 0.5f
        val diff = abs(queryDurationSec - candidateDurationSec)
        return when {
            diff == 0 -> 1f
            diff <= DURATION_SOFT_TOLERANCE_SEC ->
                1f - (diff.toFloat() / DURATION_SOFT_TOLERANCE_SEC) * 0.4f
            diff <= DURATION_HARD_CUTOFF_SEC ->
                // Linear penalty from 0.6 down to 0.1
                0.6f - (diff - DURATION_SOFT_TOLERANCE_SEC).toFloat() /
                        (DURATION_HARD_CUTOFF_SEC - DURATION_SOFT_TOLERANCE_SEC) * 0.5f
            else -> 0f  // hard reject contribution — synced lyrics cannot work
        }
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a.contains(b) || b.contains(a)) return 0.85f
        val dist = levenshtein(a, b)
        return (1f - dist.toFloat() / max(a.length, b.length)).coerceAtLeast(0f)
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) for (j in 1..s2.length) {
            dp[i][j] = if (s1[i - 1] == s2[j - 1]) dp[i - 1][j - 1]
            else minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + 1)
        }
        return dp[s1.length][s2.length]
    }
}
