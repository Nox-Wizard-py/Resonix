package com.noxwizard.resonix.paxsenix.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object PaxsenixLyrics {
    private const val BASE_URL = "https://api.paxsenix.org"

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest { url(BASE_URL) }
            expectSuccess = false
        }
    }

    suspend fun getAppleMusicLyrics(title: String, artist: String, durationSec: Int): Result<String> = runCatching {
        val response = client.get("/lyrics/applemusic") {
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", durationSec)
        }
        if (response.status.value !in 200..299) error("HTTP ${response.status.value}")
        response.body()
    }

    suspend fun getNeteaseLyrics(title: String, artist: String, durationSec: Int): Result<String> = runCatching {
        val response = client.get("/lyrics/lrcget") {
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", durationSec)
        }
        if (response.status.value !in 200..299) error("HTTP ${response.status.value}")
        response.body()
    }

    suspend fun getLyrics(title: String, artist: String, durationSec: Int): Result<String> = runCatching {
        val response = client.get("/lyrics/lrcget") {
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", durationSec)
        }
        if (response.status.value !in 200..299) error("HTTP ${response.status.value}")
        response.body()
    }

    suspend fun getMusixmatchLyrics(title: String, artist: String, durationSec: Int): Result<String> = runCatching {
        val response = client.get("/lyrics/musixmatch") {
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", durationSec)
        }
        if (response.status.value !in 200..299) error("HTTP ${response.status.value}")
        response.body()
    }

    suspend fun getYouTubeLyrics(title: String, artist: String, durationSec: Int): Result<String> = runCatching {
        val response = client.get("/lyrics/youtube") {
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", durationSec)
        }
        if (response.status.value !in 200..299) error("HTTP ${response.status.value}")
        response.body()
    }
}
