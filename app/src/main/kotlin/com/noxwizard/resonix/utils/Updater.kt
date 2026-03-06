package com.noxwizard.resonix.utils

import android.os.Build
import com.noxwizard.resonix.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject

object Updater {
    private const val OWNER = "Nox-Wizard-py"
    private const val REPO = "Resonix"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private val client = HttpClient()

    var lastCheckTime = -1L
        private set

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String?,
        val publishedAt: String?,
        val htmlUrl: String,
    )

    private var cachedRelease: ReleaseInfo? = null
    private var cachedDownloadUrl: String = ""

    private suspend fun fetchLatestRelease(): Result<JSONObject> = runCatching {
        val response = client.get(API_URL) {
            header("Accept", "application/vnd.github.v3+json")
        }
        lastCheckTime = System.currentTimeMillis()
        JSONObject(response.bodyAsText())
    }

    suspend fun getLatestVersionName(): Result<String> = runCatching {
        val json = fetchLatestRelease().getOrThrow()
        val tagName = json.optString("tag_name", "")
            .removePrefix("v")
        cachedRelease = ReleaseInfo(
            tagName = tagName,
            name = json.optString("name", tagName),
            body = json.optString("body", null),
            publishedAt = json.optString("published_at", null),
            htmlUrl = json.optString("html_url", ""),
        )
        resolveDownloadUrl(json)
        tagName
    }

    suspend fun getLatestReleaseNotes(): Result<String?> = runCatching {
        cachedRelease?.body ?: run {
            val json = fetchLatestRelease().getOrThrow()
            json.optString("body", null)
        }
    }

    fun getLatestReleaseInfo(): ReleaseInfo? = cachedRelease

    fun getLatestDownloadUrl(): String = cachedDownloadUrl

    private fun resolveDownloadUrl(json: JSONObject) {
        val assets = json.optJSONArray("assets") ?: return
        val arch = mapDeviceAbi()

        // Try architecture-specific APK first
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (arch != null && name.equals("app-$arch-release.apk", ignoreCase = true)) {
                cachedDownloadUrl = asset.optString("browser_download_url", "")
                return
            }
        }

        // Fallback to universal APK
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.equals("Resonix.apk", ignoreCase = true)) {
                cachedDownloadUrl = asset.optString("browser_download_url", "")
                return
            }
        }

        // Last resort: release page URL
        cachedDownloadUrl = json.optString("html_url", "")
    }

    private fun mapDeviceAbi(): String? {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            primaryAbi.contains("arm64") -> "arm64"
            primaryAbi.contains("armeabi") -> "armeabi"
            primaryAbi.contains("x86_64") -> "x86_64"
            primaryAbi.contains("x86") -> "x86"
            else -> null
        }
    }
}
