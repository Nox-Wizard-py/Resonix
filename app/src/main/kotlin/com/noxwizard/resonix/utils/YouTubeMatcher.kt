package com.noxwizard.resonix.utils

import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.SongItem
import com.noxwizard.resonix.playlistimport.ParsedTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import kotlin.math.abs

/**
 * Weighted YouTube Music matcher.
 * Searches InnerTube for each parsed track and selects the best match using scored criteria.
 */
object YouTubeMatcher {

    private const val CONCURRENCY = 8
    private const val MIN_SCORE_THRESHOLD = 0.30f

    // Scoring weights
    private const val WEIGHT_TITLE = 0.40f
    private const val WEIGHT_ARTIST = 0.35f
    private const val WEIGHT_DURATION = 0.10f
    private const val PENALTY_UNWANTED = 0.15f

    private val UNWANTED_KEYWORDS = listOf(
        "remix", "live", "slowed", "nightcore", "cover",
        "reverb", "sped up", "acoustic version", "karaoke",
        "instrumental", "8d audio", "bass boosted"
    )

    data class MatchResult(
        val track: ParsedTrack,
        val songItem: SongItem?,
        val score: Float,
        val index: Int
    )

    /**
     * Match all tracks in parallel with real-time progress.
     * Emits a list of MatchResult as each track is processed.
     */
    fun matchAll(tracks: List<ParsedTrack>): Flow<List<MatchResult>> = flow {
        val semaphore = Semaphore(CONCURRENCY)
        val results = Array<MatchResult?>(tracks.size) { null }

        // Emit initial state (all pending)
        emit(results.filterNotNull())

        coroutineScope {
            val jobs = tracks.mapIndexed { index, track ->
                async {
                    semaphore.withPermit {
                        val result = matchSingle(track, index)
                        results[index] = result
                        // We can't emit from inside async, so we just store results
                        result
                    }
                }
            }

            // Collect results as they complete
            var completedCount = 0
            for (job in jobs) {
                job.await()
                completedCount++
                // Emit progress snapshot every time a result completes
                emit(results.filterNotNull().toList())
            }
        }
    }

    /**
     * Match a single track against YouTube Music search results.
     */
    suspend fun matchSingle(track: ParsedTrack, index: Int): MatchResult {
        return try {
            val query = buildSearchQuery(track)
            val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            val candidates = searchResult?.items?.filterIsInstance<SongItem>() ?: emptyList()

            if (candidates.isEmpty()) {
                return MatchResult(track, null, 0f, index)
            }

            // Score each candidate and pick the best
            var bestMatch: SongItem? = null
            var bestScore = 0f

            for (candidate in candidates.take(10)) {
                val score = scoreMatch(track, candidate)
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = candidate
                }
            }

            if (bestScore >= MIN_SCORE_THRESHOLD && bestMatch != null) {
                MatchResult(track, bestMatch, bestScore, index)
            } else {
                MatchResult(track, null, bestScore, index)
            }
        } catch (e: Exception) {
            println("[YouTubeMatcher] Error matching '${track.title}': ${e.message}")
            MatchResult(track, null, 0f, index)
        }
    }

    private fun buildSearchQuery(track: ParsedTrack): String {
        return if (track.artist.isNotEmpty()) {
            "${track.title} ${track.artist}"
        } else {
            track.title
        }
    }

    /**
     * Score a candidate SongItem against a ParsedTrack.
     * Returns a value between 0.0 and 1.0.
     */
    private fun scoreMatch(track: ParsedTrack, candidate: SongItem): Float {
        var score = 0f

        // 1. Title similarity (40%)
        val titleScore = stringSimilarity(
            normalize(track.title),
            normalize(candidate.title)
        )
        score += titleScore * WEIGHT_TITLE

        // 2. Artist match (35%)
        val trackArtist = normalize(track.artist)
        val candidateArtists = candidate.artists.joinToString(" ") { it.name }.let { normalize(it) }
        val artistScore = if (trackArtist.isNotEmpty() && candidateArtists.isNotEmpty()) {
            // Check if any artist name is a substring match or fuzzy match
            val exactContains = candidateArtists.contains(trackArtist) || trackArtist.contains(candidateArtists)
            if (exactContains) {
                1.0f
            } else {
                stringSimilarity(trackArtist, candidateArtists)
            }
        } else if (trackArtist.isEmpty()) {
            // No artist info from source, give partial credit
            0.5f
        } else {
            0f
        }
        score += artistScore * WEIGHT_ARTIST

        // 3. Duration proximity (10%)
        val trackDurationMs = track.durationMs
        val candidateDuration = candidate.duration
        val durationScore = if (trackDurationMs != null && candidateDuration != null) {
            val trackDurationSec = trackDurationMs / 1000
            val diffSec = abs(trackDurationSec - candidateDuration.toLong())
            when {
                diffSec <= 5L -> 1.0f      // Within 5 sec = perfect
                diffSec <= 15L -> 0.7f     // Within 15 sec = good
                diffSec <= 30L -> 0.3f     // Within 30 sec = okay
                else -> 0f
            }
        } else {
            0.5f // No duration data, neutral
        }
        score += durationScore * WEIGHT_DURATION

        // 4. Penalty for unwanted content (-15%)
        val candidateTitle = candidate.title.lowercase(Locale.ROOT)
        val hasUnwanted = UNWANTED_KEYWORDS.any { keyword -> candidateTitle.contains(keyword) }
        val sourceTitle = track.title.lowercase(Locale.ROOT)
        val sourceHasKeyword = UNWANTED_KEYWORDS.any { keyword -> sourceTitle.contains(keyword) }

        // Only penalize if the source doesn't have the keyword but the candidate does
        if (hasUnwanted && !sourceHasKeyword) {
            score -= PENALTY_UNWANTED
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Normalized Levenshtein similarity (0.0 = completely different, 1.0 = identical).
     */
    private fun stringSimilarity(a: String, b: String): Float {
        if (a == b) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0f

        val maxLen = maxOf(a.length, b.length)
        val distance = levenshteinDistance(a, b)
        return 1.0f - (distance.toFloat() / maxLen)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Normalize a string for comparison:
     * lowercase, strip parenthetical content, remove special chars.
     */
    private fun normalize(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace(Regex("""\(.*?\)"""), "")    // Remove (feat. X), (Remix), etc.
            .replace(Regex("""\[.*?]"""), "")      // Remove [Official Video], etc.
            .replace(Regex("""[^\w\s]"""), "")      // Remove special characters
            .replace(Regex("""\s+"""), " ")         // Collapse whitespace
            .trim()
    }
}
