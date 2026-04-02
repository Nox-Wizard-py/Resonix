package com.noxwizard.resonix.lyrics.simpmusic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
data class SimpMusicLyricsData(
    val id: String? = null,
    val videoId: String? = null,
    @SerialName("songTitle") val title: String? = null,
    @SerialName("artistName") val artist: String? = null,
    @SerialName("albumName") val album: String? = null,
    @SerialName("durationSeconds") val duration: Int? = null,
    val syncedLyrics: String? = null,
    @SerialName("plainLyric") val plainLyrics: String? = null,
    val richSyncLyrics: String? = null,
    val vote: Int? = null,
)

@Serializable
data class SimpMusicApiResponse(
    val type: String? = null,
    val data: List<SimpMusicLyricsData> = emptyList(),
) {
    val success: Boolean get() = type == "success"
}

object SimpMusicLyrics {
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            defaultRequest {
                url(BASE_URL)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
                header(HttpHeaders.ContentType, "application/json")
            }
            expectSuccess = false
        }
    }

    private suspend fun getLyricsByVideoId(videoId: String): List<SimpMusicLyricsData> = runCatching {
        val response = client.get(BASE_URL + videoId)
        if (response.status == HttpStatusCode.OK) {
            val apiResponse = response.body<SimpMusicApiResponse>()
            if (apiResponse.success) apiResponse.data else emptyList()
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())

    suspend fun getLyrics(videoId: String, duration: Int = 0): Result<String> = runCatching {
        val tracks = getLyricsByVideoId(videoId)
        if (tracks.isEmpty()) throw IllegalStateException("Lyrics unavailable")

        val validTracks = if (duration > 0) {
            tracks.filter { track -> abs((track.duration ?: 0) - duration) <= 10 }
        } else {
            tracks
        }
        if (validTracks.isEmpty()) throw IllegalStateException("Lyrics unavailable")

        val bestMatch = if (duration > 0 && validTracks.size > 1) {
            validTracks.minByOrNull { track -> abs((track.duration ?: 0) - duration) }
        } else {
            validTracks.firstOrNull()
        }

        bestMatch?.richSyncLyrics?.takeIf { it.isNotBlank() }
            ?: bestMatch?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: bestMatch?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Lyrics unavailable")
    }

    suspend fun getAllLyrics(videoId: String, duration: Int = 0, callback: (String) -> Unit) {
        val tracks = getLyricsByVideoId(videoId)
        var count = 0
        var plain = 0

        val sortedTracks = if (duration > 0) {
            tracks.sortedBy { abs((it.duration ?: 0) - duration) }
        } else {
            tracks
        }

        sortedTracks.forEach { track ->
            if (count <= 4) {
                val durationMatch = duration <= 0 || abs((track.duration ?: 0) - duration) <= 10
                if (track.richSyncLyrics != null && track.richSyncLyrics.isNotBlank() && durationMatch) {
                    count++
                    callback(track.richSyncLyrics)
                } else if (track.syncedLyrics != null && track.syncedLyrics.isNotBlank() && durationMatch) {
                    count++
                    callback(track.syncedLyrics)
                }
                if (track.plainLyrics != null && track.plainLyrics.isNotBlank() && durationMatch && plain == 0) {
                    count++
                    plain++
                    callback(track.plainLyrics)
                }
            }
        }
    }
}
