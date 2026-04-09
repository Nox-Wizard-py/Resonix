package com.noxwizard.resonix.services

import com.noxwizard.resonix.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── Data Models ───────────────────────────────────────────────────────────────

/** Result from the lyrics search pipeline. */
data class LyricsResult(
    val title: String,
    val artist: String,
    val album: String?,
    val youtubeSearchQuery: String,
    val coverArtUrl: String? = null,
    val cached: Boolean = false,
)

/** Result from the link download pipeline. */
data class DownloadResult(
    /** Full URL to the original audio file (m4a/webm) — used for Local Download. */
    val downloadUrl: String,
    val filename: String,
    /** Full URL to the WAV PCM file produced by the backend — used for recognition. */
    val recognitionUrl: String = downloadUrl,
    val wavFilename: String = filename,
    val cached: Boolean = false,
)

// ── Service ───────────────────────────────────────────────────────────────────

/**
 * Communicates with the Resonix backend.
 * All methods are suspending and safe to call from any coroutine scope.
 */
@Singleton
class RecognitionApiService @Inject constructor() {

    private val baseUrl: String = BuildConfig.RECOGNITION_BACKEND_URL

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── LRCLIB ───────────────────────────────────────────────────────────────

    /**
     * Searches LRCLIB for songs matching the provided lyrics text.
     */
    suspend fun searchLyrics(query: String): Result<LyricsResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl/api/lyrics?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                val request = Request.Builder().url(url).get().build()

                val response = http.newCall(request).execute()
                val body = response.body?.string() ?: error("Empty response from lyrics server")
                val json = JSONObject(body)

                if (!response.isSuccessful) {
                    error(json.optString("error", "Lyrics search failed (${response.code})"))
                }

                LyricsResult(
                    title = json.getString("title"),
                    artist = json.getString("artist"),
                    album = json.optString("album").takeIf { it.isNotEmpty() },
                    youtubeSearchQuery = json.optString("youtubeSearchQuery", "${json.getString("title")} ${json.getString("artist")}"),
                    cached = json.optBoolean("cached", false),
                )
            }
        }

    // ── yt-dlp ───────────────────────────────────────────────────────────────

    /**
     * Requests the backend to extract and download audio from a URL.
     * Returns a [DownloadResult] with the backend-relative download URL.
     */
    suspend fun requestDownload(url: String): Result<DownloadResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = """{"url":"$url"}""".toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/api/download")
                    .post(body)
                    .build()

                val response = http.newCall(request).execute()
                val respBody = response.body?.string() ?: error("Empty response from download server")
                val json = JSONObject(respBody)

                if (!response.isSuccessful) {
                    error(json.optString("error", "Download failed (${response.code})"))
                }

                DownloadResult(
                    downloadUrl    = "$baseUrl${json.getString("downloadUrl")}",
                    filename       = json.getString("filename"),
                    recognitionUrl = "$baseUrl${json.optString("recognitionUrl", json.getString("downloadUrl"))}",
                    wavFilename    = json.optString("wavFilename", json.getString("filename")),
                    cached         = json.optBoolean("cached", false),
                )
            }
        }

    /**
     * Downloads the audio file from a backend download URL and saves it to
     * a temporary cache folder so it can be passed to the local file recognizer.
     *
     * @return The absolute File reference of the temporary saved file.
     */
    suspend fun downloadToTempFile(downloadUrl: String, filename: String, context: android.content.Context): Result<java.io.File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempDir = java.io.File(context.cacheDir, "audio_extracts").also { it.mkdirs() }
                val outputFile = java.io.File(tempDir, filename)

                val request = Request.Builder().url(downloadUrl).get().build()
                val response = http.newCall(request).execute()
                if (!response.isSuccessful) error("Download failed: ${response.code}")

                response.body?.byteStream()?.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("No file data received")

                outputFile
            }
        }

    /**
     * Downloads the audio file from a backend download URL and saves it to
     * the /Music/Resonix/ folder in the system Music directory.
     *
     * @return The absolute path of the saved file.
     */
    suspend fun downloadToMusicFolder(downloadUrl: String, filename: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                )
                val resonixDir = java.io.File(musicDir, "Resonix").also { it.mkdirs() }
                val outputFile = java.io.File(resonixDir, filename)

                val request = Request.Builder().url(downloadUrl).get().build()
                val response = http.newCall(request).execute()
                if (!response.isSuccessful) error("Download failed: ${response.code}")

                response.body?.byteStream()?.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("No file data received")

                outputFile.absolutePath
            }
        }
}
