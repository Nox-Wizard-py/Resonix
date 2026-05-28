package com.noxwizard.resonix.lrclib

import com.noxwizard.resonix.lrclib.models.Track
import com.noxwizard.resonix.lrclib.models.bestMatchingFor
import com.noxwizard.resonix.lrclib.models.bestMatchingForRelaxed
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlin.math.abs

object LrcLib {
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url("https://lrclib.net")
            }

            expectSuccess = true
        }
    }

    // Title/artist cleanup patterns matching Metrolist's LrcLib
    private val titleCleanupPatterns = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
    )

    private val artistSeparators = listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) cleaned = cleaned.replace(pattern, "")
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    private suspend fun queryLyricsWithParams(
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        query: String? = null,
    ): List<Track> = runCatching {
        client.get("/api/search") {
            if (query != null) parameter("q", query)
            if (trackName != null) parameter("track_name", trackName)
            if (artistName != null) parameter("artist_name", artistName)
            if (albumName != null) parameter("album_name", albumName)
        }.body<List<Track>>()
    }.getOrDefault(emptyList())

    suspend fun queryLyrics(
        artist: String,
        title: String,
        album: String? = null,
    ): List<Track> {
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        // Strategy 1: cleaned title + artist
        var results = queryLyricsWithParams(trackName = cleanedTitle, artistName = cleanedArtist, albumName = album)
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
        if (results.isNotEmpty()) return results

        // Strategy 2: cleaned title only
        results = queryLyricsWithParams(trackName = cleanedTitle)
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
        if (results.isNotEmpty()) return results

        // Strategy 3: q combined
        results = queryLyricsWithParams(query = "$cleanedArtist $cleanedTitle")
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
        if (results.isNotEmpty()) return results

        // Strategy 4: q title only
        results = queryLyricsWithParams(query = cleanedTitle)
            .filter { it.syncedLyrics != null || it.plainLyrics != null }
        if (results.isNotEmpty()) return results

        // Strategy 5: original title if different from cleaned
        if (cleanedTitle != title.trim()) {
            results = queryLyricsWithParams(trackName = title.trim(), artistName = artist.trim())
                .filter { it.syncedLyrics != null || it.plainLyrics != null }
        }

        return results
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching {
        val tracks = queryLyrics(artist, title, album)
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        val res = when {
            duration == -1 -> {
                tracks.bestMatchingFor(duration, cleanedTitle, cleanedArtist)?.let { track ->
                    track.syncedLyrics ?: track.plainLyrics
                }?.let(LrcLib::Lyrics)
            }
            else -> {
                // Relaxed duration matching: ±5 seconds
                tracks.bestMatchingForRelaxed(duration)?.let { track ->
                    track.syncedLyrics ?: track.plainLyrics
                }?.let(LrcLib::Lyrics)
            }
        }

        if (res != null) {
            return@runCatching res.text
        } else {
            throw IllegalStateException("Lyrics unavailable")
        }
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        val tracks = queryLyrics(artist, title, album)
        var count = 0
        var plain = 0

        val sortedTracks = when {
            duration == -1 -> {
                tracks.sortedByDescending { track ->
                    var score = 0.0

                    if (track.syncedLyrics != null) score += 1.0

                    val titleSimilarity = calculateStringSimilarity(title, track.trackName)
                    val artistSimilarity = calculateStringSimilarity(artist, track.artistName)
                    score += (titleSimilarity + artistSimilarity) / 2.0
                    
                    score
                }
            }
            else -> {
                tracks.sortedBy { abs(it.duration.toInt() - duration) }
            }
        }

        sortedTracks.forEach { track ->
            currentCoroutineContext().ensureActive() // Corrected usage
            if (count <= 4) {
                if (track.syncedLyrics != null && duration == -1) {
                    count++
                    track.syncedLyrics.let(callback)
                } else {
                    if (track.syncedLyrics != null && abs(track.duration.toInt() - duration) <= 5) {
                        count++
                        track.syncedLyrics.let(callback)
                    }
                    if (track.plainLyrics != null && abs(track.duration.toInt() - duration) <= 5 && plain == 0) {
                        count++
                        plain++
                        track.plainLyrics.let(callback)
                    }
                }
            }
        }
    }

    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        val s1 = str1.trim().lowercase()
        val s2 = str2.trim().lowercase()
        
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        return when {
            s1.contains(s2) || s2.contains(s1) -> 0.8
            else -> {
                val maxLength = maxOf(s1.length, s2.length)
                val distance = levenshteinDistance(s1, s2)
                1.0 - (distance.toDouble() / maxLength)
            }
        }
    }

    private fun levenshteinDistance(str1: String, str2: String): Int {
        val len1 = str1.length
        val len2 = str2.length
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1,      // insertion
                    matrix[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return matrix[len1][len2]
    }

    suspend fun lyrics(
        artist: String,
        title: String,
    ) = runCatching {
        queryLyrics(artist = artist, title = title, album = null)
    }

    @JvmInline
    value class Lyrics(
        val text: String,
    ) {
        val sentences
            get() =
                runCatching {
                    buildMap {
                        put(0L, "")
                        text.trim().lines().filter { it.length >= 10 }.forEach {
                            put(
                                it[8].digitToInt() * 10L +
                                    it[7].digitToInt() * 100 +
                                    it[5].digitToInt() * 1000 +
                                    it[4].digitToInt() * 10000 +
                                    it[2].digitToInt() * 60 * 1000 +
                                    it[1].digitToInt() * 600 * 1000,
                                it.substring(10),
                            )
                        }
                    }
                }.getOrNull()
    }
}



