package com.noxwizard.resonix.lyrics

import android.content.Context
import com.noxwizard.resonix.constants.EnableLyricsPlusKey
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LyricLineResponse(
    val time: Long,
    val duration: Long,
    val text: String,
)

@Serializable
private data class LyricsPlusResponse(
    val type: String? = null,
    val lyrics: List<LyricLineResponse>? = null,
    val cached: String? = null,
)

object LyricsPlusProvider : LyricsProvider {
    override val name = "LyricsPlus"

    private val baseUrls = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus-seven.vercel.app",
    )

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            expectSuccess = false
        }
    }

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLyricsPlusKey] ?: false

    private suspend fun fetchFromUrl(
        url: String,
        title: String,
        artist: String,
        duration: Int,
    ): LyricsPlusResponse? = runCatching {
        val response = client.get("$url/v2/lyrics/get") {
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", if (duration > 0) duration / 1000 else -1)
            parameter("source", "apple,lyricsplus,musixmatch,spotify,musixmatch-word")
        }
        if (response.status == HttpStatusCode.OK) response.body<LyricsPlusResponse>() else null
    }.getOrNull()

    private suspend fun fetchLyrics(
        title: String,
        artist: String,
        duration: Int,
    ): LyricsPlusResponse? {
        for (baseUrl in baseUrls) {
            try {
                val result = fetchFromUrl(baseUrl, title, artist, duration)
                if (result != null && !result.lyrics.isNullOrEmpty()) return result
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun convertToLrc(response: LyricsPlusResponse?): String? {
        val lyrics = response?.lyrics ?: return null
        if (lyrics.isEmpty()) return null

        return lyrics.mapNotNull { line ->
            val minutes = line.time / 1000 / 60
            val seconds = (line.time / 1000) % 60
            val millis = line.time % 1000 / 10
            if (line.text.isNotBlank()) {
                String.format("[%02d:%02d.%02d]%s", minutes, seconds, millis, line.text)
            } else null
        }.joinToString("\n")
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val lrc = convertToLrc(fetchLyrics(title, artist, duration))
        if (lrc.isNullOrBlank()) throw IllegalStateException("Lyrics unavailable")
        lrc
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration).onSuccess { callback(it) }
    }
}
