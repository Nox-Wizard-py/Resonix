package com.noxwizard.resonix.paxsenix.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.util.Locale
import kotlin.math.abs

/**
 * Canonical Paxsenix HTTP client.
 *
 * Base URL, engine, headers, duration tolerance, fallback order, and
 * all parsing helpers are defined here.
 */
object PaxsenixLyrics {
    private const val BASE_URL = "https://lyrics.paxsenix.org/"
    private const val APPLE_MUSIC_TOKEN =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ" +
        ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3" +
        "MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
        ".4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"
    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url(BASE_URL)
                header(HttpHeaders.UserAgent, "Resonix-Lyrics-Fetcher/1.0 (https://github.com/Nox-Wizard-py/Resonix)")
                header(HttpHeaders.Accept, "application/json, text/plain, */*")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            }

            expectSuccess = false
        }
    }

    /** Separate client for Apple AMP API — no defaultRequest base URL override. */
    private val ampClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            expectSuccess = false
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Resolves duration to milliseconds.
     * Values > 360 000 are assumed to already be in ms (i.e. caller passed ms).
     * Values ≤ 0 mean "unknown duration" → returns 0.
     */
    internal fun resolveDurationMs(duration: Int): Long = when {
        duration <= 0 -> 0L
        duration > 360000 -> duration.toLong() // already ms
        else -> duration * 1000L               // seconds → ms
    }

    /**
     * Strips an outer JSON string wrapper if the server double-encodes.
     * e.g. "\"[00:01.00]Line\"" → "[00:01.00]Line"
     */
    private fun cleanJsonLyrics(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            runCatching { Json.decodeFromString<String>(trimmed) }.getOrDefault(trimmed)
        } else trimmed
    }

    // ─── Provider methods ─────────────────────────────────────────────────────

    suspend fun getAppleMusicLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = runCatching {
        val durationMs = resolveDurationMs(durationSeconds)
        val term = title
        var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
        
        val amSearch = ampClient.get("$AMP_BASE_URL/v1/catalog/us/search") {
            header("Authorization", "Bearer $APPLE_MUSIC_TOKEN")
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            parameter("term", query)
            parameter("types", "songs")
            parameter("limit", "10")
            parameter("extend", "editorialVideo")
            parameter("include", "albums")
        }

        if (amSearch.status == HttpStatusCode.OK) {
            val root = amSearch.body<JsonObject>()
            val results = root["results"]?.jsonObject?.get("songs")?.jsonObject?.get("data")?.jsonArray

            if (results != null) {
                val scoredResults = results
                    .mapNotNull { scoreAndFilterItem(it.jsonObject, term, artist, null) }
                    .sortedByDescending { it.first }

                System.err.println("PaxsenixLyrics: Found ${scoredResults.size} scored results for Apple Music search '$query'")

                for ((score, obj) in scoredResults) {
                    if (score < 12) {
                        System.err.println("PaxsenixLyrics: skipping result with low score: $score")
                        continue
                    }

                    val attributes = obj["attributes"]?.jsonObject ?: continue
                    val bestId = obj["id"]?.jsonPrimitive?.content ?: continue
                    val resultName = attributes["name"]?.jsonPrimitive?.content ?: ""
                    val durationInMillis = attributes["durationInMillis"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                    val diff = abs(durationInMillis.toLong() - durationMs)
                    System.err.println("PaxsenixLyrics: Best Apple Music match: $resultName (ID: $bestId, Duration: $durationInMillis, Diff: $diff)")
                    
                    if (durationMs <= 0 || (diff < 10000)) {
                        val lyricsResponse = client.get("apple-music/lyrics") {
                            parameter("id", bestId)
                            parameter("ttml", "true")
                        }

                        System.err.println("PaxsenixLyrics: Apple Music lyrics status: ${lyricsResponse.status}")
                        if (lyricsResponse.status == HttpStatusCode.OK) {
                            runCatching {
                                val data = lyricsResponse.body<JsonObject>()
                                val content = data["content"]?.jsonPrimitive?.content
                                if (!content.isNullOrBlank() && (content.contains("<tt") || content.contains("<?xml"))) {
                                    System.err.println("PaxsenixLyrics: SUCCESS from Apple Music (TTML, Length: ${content.length})")
                                    return@runCatching content
                                }
                                System.err.println("PaxsenixLyrics: Apple Music TTML content null/invalid. type=${data["type"]}")
                            }.onFailure {
                                System.err.println("PaxsenixLyrics: Error parsing Apple Music response: ${it.message}")
                            }
                        }
                    }
                }
            }
        }
        throw IllegalStateException("Apple Music lyrics unavailable")
    }

    private fun scoreAndFilterItem(
        obj: JsonObject,
        term: String,
        artist: String,
        album: String?,
    ): Pair<Int, JsonObject>? {
        val attributes = obj["attributes"]?.jsonObject ?: return null
        val resultArtistName = attributes["artistName"]?.jsonPrimitive?.content ?: ""
        val resultName = attributes["name"]?.jsonPrimitive?.content ?: ""
        val resultCollectionName = attributes["collectionName"]?.jsonPrimitive?.content ?: ""

        val nameLower = resultName.lowercase(Locale.ROOT)
        val collectionLower = resultCollectionName.lowercase(Locale.ROOT)
        val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                collectionLower.contains("playlist") || collectionLower.contains("set list") ||
                nameLower.contains("essentials") || collectionLower.contains("essentials") ||
                collectionLower.contains("dj mix") || collectionLower.contains("mixed") ||
                collectionLower.contains("apple music") || collectionLower.contains("today's hits") ||
                nameLower.contains("session") || collectionLower.contains("session")
        if (isBlacklisted) {
            System.err.println("  - Skipping blacklisted result: '$resultName' (Album: '$resultCollectionName')")
            return null
        }

        val artistMatch = resultArtistName.equals(artist, ignoreCase = true)
        val artistFuzzy = resultArtistName.contains(artist, ignoreCase = true) ||
                artist.contains(resultArtistName, ignoreCase = true)
        if (!artistFuzzy) return null

        var score = if (artistMatch) 10 else 5

        val nameMatch = resultName.equals(term, ignoreCase = true)
        val nameFuzzy = resultName.contains(term, ignoreCase = true) || term.contains(resultName, ignoreCase = true)
        score += when {
            nameMatch -> 15
            nameFuzzy -> 7
            else -> -10
        }

        val editionWords = listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")
        for (word in editionWords) {
            val inTerm = term.contains(word, ignoreCase = true)
            val inResult = resultName.contains(word, ignoreCase = true)
            score += when {
                inTerm && inResult -> 5
                inTerm != inResult && inResult -> -3
                else -> 0
            }
        }

        if (!album.isNullOrBlank() && resultCollectionName.isNotBlank()) {
            val albumMatch = resultCollectionName.equals(album, ignoreCase = true)
            val albumFuzzy = resultCollectionName.contains(album, ignoreCase = true) ||
                    album.contains(resultCollectionName, ignoreCase = true)
            score += when {
                albumMatch -> 20
                albumFuzzy -> 10
                else -> 0
            }
        }

        System.err.println("  - Result: '$resultName' by '$resultArtistName' (Album: '$resultCollectionName', ID: ${obj["id"]}) -> Score: $score")
        return score to obj
    }

    suspend fun getNeteaseLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = runCatching {
        val durationMs = resolveDurationMs(durationSeconds)
        val query = "$title $artist"
        val neteaseSearch = client.get("netease/search") {
            parameter("q", query)
        }

        if (neteaseSearch.status == HttpStatusCode.OK) {
            val searchResponse = neteaseSearch.body<NeteaseSearchResponse>()
            val songs = searchResponse.result?.songs ?: emptyList()

            val bestMatch = if (durationMs > 0) {
                songs.minByOrNull { abs(it.duration.toLong() - durationMs) }
            } else {
                songs.firstOrNull()
            }

            if (bestMatch != null) {
                val diff = abs(bestMatch.duration.toLong() - durationMs)
                System.err.println("PaxsenixLyrics: Best NetEase match: ${bestMatch.name} (ID: ${bestMatch.id}, Duration: ${bestMatch.duration}, Diff: $diff)")
                if (durationMs <= 0 || (diff < 10000)) {
                    val lyricsResponse = client.get("netease/lyrics") {
                        parameter("id", bestMatch.id)
                        parameter("word", "true")
                    }

                    System.err.println("PaxsenixLyrics: NetEase lyrics status: ${lyricsResponse.status}")
                    if (lyricsResponse.status == HttpStatusCode.OK) {
                        val lyricsData = lyricsResponse.body<JsonObject>()

                        // Prefer word-by-word (klyric) first
                        val klyric = lyricsData["klyric"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content
                        if (!klyric.isNullOrBlank()) {
                            System.err.println("PaxsenixLyrics: SUCCESS from NetEase (Karaoke)")
                            return@runCatching klyric
                        }

                        // Fallback to normal lyric (lrc)
                        val lrc = lyricsData["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content
                        if (!lrc.isNullOrBlank()) {
                            System.err.println("PaxsenixLyrics: SUCCESS from NetEase (LRC)")
                            return@runCatching lrc
                        }
                    }
                }
            }
        }
        throw IllegalStateException("NetEase lyrics unavailable")
    }

    suspend fun getSpotifyLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = runCatching {
        val durationMs = resolveDurationMs(durationSeconds)
        val query = "$title $artist"
        val spotifySearch = client.get("spotify/search") {
            parameter("q", query)
        }
        if (spotifySearch.status == HttpStatusCode.OK) {
            val items = spotifySearch.body<List<PaxsenixSearchItem>>()
            val bestMatch = if (durationMs > 0) {
                items.minByOrNull { abs(it.durationMs - durationMs) }
            } else {
                items.firstOrNull()
            }

            if (bestMatch != null) {
                val diff = abs(bestMatch.durationMs - durationMs)
                System.err.println("PaxsenixLyrics: Best Spotify match: ${bestMatch.name ?: bestMatch.title} (ID: ${bestMatch.realId}, Duration: ${bestMatch.durationMs}, Diff: $diff)")
                if (durationMs <= 0 || (diff < 10000)) {
                    val lyricsResponse = client.get("spotify/lyrics") {
                        parameter("id", bestMatch.realId)
                    }
                    System.err.println("PaxsenixLyrics: Spotify lyrics status: ${lyricsResponse.status}")
                    if (lyricsResponse.status == HttpStatusCode.OK) {
                        val data = cleanJsonLyrics(lyricsResponse.body<String>())
                        if (data.isNotBlank()) {
                            System.err.println("PaxsenixLyrics: SUCCESS from Spotify")
                            return@runCatching data
                        }
                    }
                }
            }
        }
        throw IllegalStateException("Spotify lyrics unavailable")
    }

    suspend fun getMusixmatchLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = runCatching {
        val query = "$title $artist"
        System.err.println("PaxsenixLyrics: Requesting Musixmatch lyrics for: $query (Duration: $durationSeconds)")

        // Try word-by-word first
        val mxmWord = client.get("musixmatch/lyrics") {
            parameter("q", query)
            parameter("t", title)
            parameter("a", artist)
            parameter("duration", durationSeconds.toString())
            parameter("type", "word")
        }
        if (mxmWord.status == HttpStatusCode.OK) {
            val data = cleanJsonLyrics(mxmWord.body<String>())
            if (data.isNotBlank() && !data.contains("\"error\"")) {
                System.err.println("PaxsenixLyrics: SUCCESS from Musixmatch (Word)")
                return@runCatching data
            }
        }

        // Fallback to default (line-synced)
        val mxmLyrics = client.get("musixmatch/lyrics") {
            parameter("q", query)
            parameter("t", title)
            parameter("a", artist)
            parameter("duration", durationSeconds.toString())
        }
        System.err.println("PaxsenixLyrics: Musixmatch lyrics status: ${mxmLyrics.status}")
        if (mxmLyrics.status == HttpStatusCode.OK) {
            val data = cleanJsonLyrics(mxmLyrics.body<String>())
            if (data.isNotBlank() && !data.contains("\"error\"")) {
                System.err.println("PaxsenixLyrics: SUCCESS from Musixmatch")
                return@runCatching data
            }
        }
        throw IllegalStateException("Musixmatch lyrics unavailable")
    }

    suspend fun getKugouLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = runCatching {
        val durationMs = resolveDurationMs(durationSeconds)
        val query = "$title $artist"
        val kugouSearch = client.get("kugou/search") {
            parameter("q", query)
        }
        if (kugouSearch.status == HttpStatusCode.OK) {
            val items = kugouSearch.body<List<PaxsenixSearchItem>>()
            val bestMatch = if (durationMs > 0) {
                items.minByOrNull { abs(it.durationMs - durationMs) }
            } else {
                items.firstOrNull()
            }

            if (bestMatch != null) {
                val diff = abs(bestMatch.durationMs - durationMs)
                if (durationMs <= 0 || (diff < 10000)) {
                    val lyricsResponse = client.get("kugou/lyrics") {
                        parameter("id", bestMatch.id ?: "")
                        parameter("word", "true")
                    }
                    if (lyricsResponse.status == HttpStatusCode.OK) {
                        val data = lyricsResponse.body<JsonObject>()
                        val lyrics = data["lyrics"]?.jsonPrimitive?.content
                        if (!lyrics.isNullOrBlank()) {
                            return@runCatching lyrics
                        }
                    }
                }
            }
        }
        throw IllegalStateException("KuGou lyrics unavailable")
    }

    /**
     * Sequential fallback chain: Apple Music → NetEase → Spotify → Musixmatch → KuGou.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = runCatching {
        System.err.println("PaxsenixLyrics: --- Starting search for [$title] by [$artist] ---")

        getAppleMusicLyrics(title, artist, durationSeconds).getOrNull()?.let {
            System.err.println("PaxsenixLyrics: Search FINISHED (Apple Music)")
            return@runCatching it
        }

        getNeteaseLyrics(title, artist, durationSeconds).getOrNull()?.let {
            System.err.println("PaxsenixLyrics: Search FINISHED (NetEase)")
            return@runCatching it
        }

        getSpotifyLyrics(title, artist, durationSeconds).getOrNull()?.let {
            System.err.println("PaxsenixLyrics: Search FINISHED (Spotify)")
            return@runCatching it
        }

        getMusixmatchLyrics(title, artist, durationSeconds).getOrNull()?.let {
            System.err.println("PaxsenixLyrics: Search FINISHED (Musixmatch)")
            return@runCatching it
        }

        getKugouLyrics(title, artist, durationSeconds).getOrNull()?.let {
            System.err.println("PaxsenixLyrics: Search FINISHED (KuGou)")
            return@runCatching it
        }

        System.err.println("PaxsenixLyrics: Search FAILED - No providers found lyrics")
        throw IllegalStateException("Lyrics unavailable from Paxsenix for $title")
    }

    /** Paxsenix does not have a YouTube lyrics endpoint. Always fails. */
    @Suppress("UnusedParameter")
    suspend fun getYouTubeLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = Result.failure(UnsupportedOperationException("Paxsenix has no YouTube lyrics endpoint"))

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration).onSuccess(callback)
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun convertAppleMusicToLrc(response: AppleMusicLyricsResponse): String {
        return response.content.joinToString("\n") { line ->
            val minutes = line.timestamp / 1000 / 60
            val seconds = (line.timestamp / 1000) % 60
            val hundredths = (line.timestamp % 1000) / 10
            val time = String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
            val text = line.text.joinToString(" ") { it.text.trim() }
            "$time$text"
        }
    }
}
