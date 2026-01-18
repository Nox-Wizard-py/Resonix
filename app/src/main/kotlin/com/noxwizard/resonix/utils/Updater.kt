package com.noxwizard.resonix.utils

import com.noxwizard.resonix.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set

    // Resonix is a personal build, so we disable upstream checks to the original repo
    suspend fun getLatestVersionName(): Result<String> =
        Result.success(BuildConfig.VERSION_NAME)

    suspend fun getLatestReleaseNotes(): Result<String?> =
        Result.success(null)

    fun getLatestDownloadUrl(): String {
        return ""
    }
}


