package com.noxwizard.resonix.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.playback.queues.YouTubeQueue
import com.noxwizard.resonix.services.DownloadResult
import com.noxwizard.resonix.services.LyricsResult
import com.noxwizard.resonix.services.MicRecordingService
import com.noxwizard.resonix.services.RecognitionApiService
import com.resonix.music.recognition.MultiWindowRecognizer
import com.resonix.music.recognition.MusicRecognitionService
import com.resonix.shazamkit.models.RecognitionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class RecognitionUiState {
    /** Idle — showing input field and mic button. */
    object Idle : RecognitionUiState()

    /** Currently recording from microphone. [progress] is 0f..1f. */
    data class Listening(val progress: Float = 0f) : RecognitionUiState()

    /** Sending audio / calling APIs. */
    data class Processing(val message: String = "Identifying…") : RecognitionUiState()

    /** Has a result to show (link / lyrics pipeline). */
    data class Result(
        val lyricsResult: LyricsResult? = null,
        val downloadResult: DownloadResult? = null,
        val error: String? = null,
    ) : RecognitionUiState()

    /** Shazam mic-recognition matched a track. */
    data class ShazamResult(
        val title: String,
        val artist: String,
        val coverArtUrl: String?,
        val isrc: String?,
        val shazamUrl: String?,
        val isPrefetching: Boolean = false,
        val prefetchedQueue: YouTubeQueue? = null,
        val isPreparingPlayback: Boolean = false,
        val sourceDownload: DownloadResult? = null,
        val isMatchFound: Boolean = true
    ) : RecognitionUiState()

    /** Shazam found no match. */
    data class NoMatch(val message: String = "No match found. Try again.") : RecognitionUiState()

    data class Error(val message: String) : RecognitionUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class RecognitionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: RecognitionApiService,
    private val micService: MicRecordingService,
    private val database: com.noxwizard.resonix.db.MusicDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Idle)
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadedFilePath = MutableStateFlow<String?>(null)
    val downloadedFilePath: StateFlow<String?> = _downloadedFilePath.asStateFlow()

    private var activeJob: Job? = null

    // ── Input Router ──────────────────────────────────────────────────────────

    fun processInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        if (isUrl(trimmed)) processLink(trimmed) else searchByLyrics(trimmed)
    }

    // ── Lyrics Pipeline ───────────────────────────────────────────────────────

    private fun searchByLyrics(query: String) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = RecognitionUiState.Processing("Searching all lyrics providers…")
            
            val bestMatch = com.noxwizard.resonix.lyrics.ParallelLyricsSearcher.searchAllProviders(query)
            
            if (bestMatch != null) {
                val mappedResult = LyricsResult(
                    title = bestMatch.title,
                    artist = bestMatch.artist,
                    album = bestMatch.album,
                    youtubeSearchQuery = bestMatch.youtubeSearchQuery
                )
                _uiState.value = RecognitionUiState.Result(lyricsResult = mappedResult)
                
                // Prefetch thumbnail to populate the blank disk icon
                launch(Dispatchers.IO) {
                    YouTube.searchSummary(bestMatch.youtubeSearchQuery).onSuccess { page ->
                        val topSong = page.summaries
                            .firstOrNull { it.items.any { item -> item is com.noxwizard.resonix.innertube.models.SongItem } }
                            ?.items
                            ?.filterIsInstance<com.noxwizard.resonix.innertube.models.SongItem>()
                            ?.firstOrNull()
                            
                        val artwork = topSong?.thumbnail
                        if (artwork != null) {
                            withContext(Dispatchers.Main) {
                                val current = _uiState.value
                                if (current is RecognitionUiState.Result && current.lyricsResult?.title == bestMatch.title) {
                                    _uiState.value = current.copy(
                                        lyricsResult = current.lyricsResult.copy(coverArtUrl = artwork)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                _uiState.value = RecognitionUiState.Error("Couldn't identify lyrics")
            }
        }
    }

    // ── Link Pipeline (yt-dlp) ────────────────────────────────────────────────

    private fun processLink(url: String) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                _uiState.value = RecognitionUiState.Processing("Extracting audio from link…")
                val downloadRes = apiService.requestDownload(url).getOrThrow()

                _uiState.value = RecognitionUiState.Processing("Downloading audio…")
                // Download the WAV recognition file (backend converts to 16kHz PCM)
                val wavFile = apiService.downloadToTempFile(
                    downloadRes.recognitionUrl, downloadRes.wavFilename, context
                ).getOrThrow()

                _uiState.value = RecognitionUiState.Processing("Identifying audio…")
                // Try up to 4 windows (start / middle / end / full) for maximum hit rate
                val status = withContext(Dispatchers.IO) {
                    MultiWindowRecognizer.recognize(context, wavFile)
                }

                when (status) {
                    is RecognitionStatus.Success -> {
                        val r = status.result
                        _uiState.value = RecognitionUiState.ShazamResult(
                            title        = r.title,
                            artist       = r.artist,
                            coverArtUrl  = r.coverArtHqUrl ?: r.coverArtUrl,
                            isrc         = r.isrc,
                            shazamUrl    = r.shazamUrl,
                            isPrefetching = true,
                            sourceDownload = downloadRes,
                            isMatchFound = true
                        )
                        viewModelScope.launch(Dispatchers.IO) { 
                            prefetchTopResult("${r.title} ${r.artist}")
                            database.insertRecognitionHistory(
                                com.noxwizard.resonix.db.entities.RecognitionHistory(
                                    title = r.title,
                                    artist = r.artist,
                                    coverArtUrl = r.coverArtHqUrl ?: r.coverArtUrl
                                )
                            )
                        }
                    }
                    else -> {
                        // Always keep sourceDownload so Local Download button is visible
                        _uiState.value = RecognitionUiState.ShazamResult(
                            title        = "Couldn't identify audio",
                            artist       = "",
                            coverArtUrl  = null,
                            isrc         = null,
                            shazamUrl    = null,
                            sourceDownload = downloadRes,
                            isMatchFound = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = RecognitionUiState.Error(e.message ?: "Failed to process link")
            }
        }
    }

    // ── Mic Pipeline (MetroList Shazam engine) ────────────────────────────────

    /**
     * Starts mic recognition using the migrated [MusicRecognitionService].
     * Collects its StateFlow and bridges Shazam states → [RecognitionUiState].
     */
    fun startMicRecognition() {
        activeJob?.cancel()
        MusicRecognitionService.reset()
        activeJob = viewModelScope.launch {
            // Drive recognition on IO; observe the shared StateFlow on the collector.
            launch(Dispatchers.IO) {
                MusicRecognitionService.recognize(context)
            }
            MusicRecognitionService.recognitionStatus.collect { status ->
                when (status) {
                    is RecognitionStatus.Ready -> Unit
                    is RecognitionStatus.Listening ->
                        _uiState.value = RecognitionUiState.Listening(0f)
                    is RecognitionStatus.Processing ->
                        _uiState.value = RecognitionUiState.Processing("Identifying…")
                    is RecognitionStatus.Success -> {
                        val r = status.result
                        _uiState.value = RecognitionUiState.ShazamResult(
                            title = r.title,
                            artist = r.artist,
                            coverArtUrl = r.coverArtHqUrl ?: r.coverArtUrl,
                            isrc = r.isrc,
                            shazamUrl = r.shazamUrl,
                            isPrefetching = true
                        )
                        viewModelScope.launch(Dispatchers.IO) { 
                            prefetchTopResult("${r.title} ${r.artist}")
                            database.insertRecognitionHistory(
                                com.noxwizard.resonix.db.entities.RecognitionHistory(
                                    title = r.title,
                                    artist = r.artist,
                                    coverArtUrl = r.coverArtHqUrl ?: r.coverArtUrl
                                )
                            )
                        }
                    }
                    is RecognitionStatus.NoMatch ->
                        _uiState.value = RecognitionUiState.NoMatch(status.message)
                    is RecognitionStatus.Error ->
                        _uiState.value = RecognitionUiState.Error(status.message)
                }
            }
        }
    }

    // ── File Pipeline ─────────────────────────────────────────────────────────

    fun recognizeFromFile(uri: Uri) {
        activeJob?.cancel()
        MusicRecognitionService.reset()
        activeJob = viewModelScope.launch {
            // Drive file recognition on IO
            launch(Dispatchers.IO) {
                MusicRecognitionService.recognizeFile(context, uri)
            }
            MusicRecognitionService.recognitionStatus.collect { status ->
                when (status) {
                    is RecognitionStatus.Ready -> Unit
                    is RecognitionStatus.Listening ->
                        _uiState.value = RecognitionUiState.Listening(0f)
                    is RecognitionStatus.Processing ->
                        _uiState.value = RecognitionUiState.Processing("Identifying audio file…")
                    is RecognitionStatus.Success -> {
                        val r = status.result
                        _uiState.value = RecognitionUiState.ShazamResult(
                            title = r.title,
                            artist = r.artist,
                            coverArtUrl = r.coverArtHqUrl ?: r.coverArtUrl,
                            isrc = r.isrc,
                            shazamUrl = r.shazamUrl,
                            isPrefetching = true
                        )
                        viewModelScope.launch(Dispatchers.IO) { 
                            prefetchTopResult("${r.title} ${r.artist}")
                            database.insertRecognitionHistory(
                                com.noxwizard.resonix.db.entities.RecognitionHistory(
                                    title = r.title,
                                    artist = r.artist,
                                    coverArtUrl = r.coverArtHqUrl ?: r.coverArtUrl
                                )
                            )
                        }
                    }
                    is RecognitionStatus.NoMatch ->
                        _uiState.value = RecognitionUiState.NoMatch(status.message)
                    is RecognitionStatus.Error ->
                        _uiState.value = RecognitionUiState.Error(status.message)
                }
            }
        }
    }

    // ── Play Now ──────────────────────────────────────────────────────────────

    fun resolveAndPlay(query: String, onPlay: (YouTubeQueue) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.searchSummary(query)
                .onSuccess { page ->
                    val topSong = page.summaries
                        .firstOrNull { it.items.any { item -> item is com.noxwizard.resonix.innertube.models.SongItem } }
                        ?.items
                        ?.filterIsInstance<com.noxwizard.resonix.innertube.models.SongItem>()
                        ?.firstOrNull()

                    if (topSong != null) {
                        val queue = YouTubeQueue.radio(topSong.toMediaMetadata())
                        withContext(Dispatchers.Main) { onPlay(queue) }
                    }
                }
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    fun downloadFile(downloadUrl: String, filename: String) {
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
            apiService.downloadToMusicFolder(downloadUrl, filename)
                .onSuccess { path -> _downloadedFilePath.value = path }
                .onFailure { err ->
                    _uiState.value = RecognitionUiState.Error(err.message ?: "Failed to save file.")
                    _downloadedFilePath.value = null
                }
            _isDownloading.value = false
        }
    }

    // ── Prefetch Shazam Result ────────────────────────────────────────────────

    private suspend fun prefetchTopResult(query: String) {
        YouTube.searchSummary(query)
            .onSuccess { page ->
                val topSong = page.summaries
                    .firstOrNull { it.items.any { item -> item is com.noxwizard.resonix.innertube.models.SongItem } }
                    ?.items
                    ?.filterIsInstance<com.noxwizard.resonix.innertube.models.SongItem>()
                    ?.firstOrNull()

                val queue = topSong?.let { YouTubeQueue.radio(it.toMediaMetadata()) }

                withContext(Dispatchers.Main) {
                    val current = _uiState.value
                    if (current is RecognitionUiState.ShazamResult) {
                        _uiState.value = current.copy(isPrefetching = false, prefetchedQueue = queue)
                    }
                }
            }
            .onFailure {
                withContext(Dispatchers.Main) {
                    val current = _uiState.value
                    if (current is RecognitionUiState.ShazamResult) {
                        _uiState.value = current.copy(isPrefetching = false, prefetchedQueue = null)
                    }
                }
            }
    }

    fun playShazamResult(onPlay: (YouTubeQueue) -> Unit) {
        val current = _uiState.value as? RecognitionUiState.ShazamResult ?: return
        if (!current.isPrefetching) {
            if (current.prefetchedQueue != null) {
                onPlay(current.prefetchedQueue)
            } else {
                resolveAndPlay("${current.title} ${current.artist}", onPlay)
            }
        } else {
            _uiState.value = current.copy(isPreparingPlayback = true)
            viewModelScope.launch {
                uiState.first { state ->
                    state !is RecognitionUiState.ShazamResult || !state.isPrefetching
                }
                val refreshed = _uiState.value as? RecognitionUiState.ShazamResult ?: return@launch
                
                _uiState.value = refreshed.copy(isPreparingPlayback = false)
                
                if (refreshed.prefetchedQueue != null) {
                    onPlay(refreshed.prefetchedQueue)
                } else {
                    resolveAndPlay("${refreshed.title} ${refreshed.artist}", onPlay)
                }
            }
        }
    }

    fun setPreparingPlayback(isPreparing: Boolean) {
        val current = _uiState.value
        if (current is RecognitionUiState.ShazamResult) {
            _uiState.value = current.copy(isPreparingPlayback = isPreparing)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun reset() {
        activeJob?.cancel()
        MusicRecognitionService.reset()
        _uiState.value = RecognitionUiState.Idle
        _downloadedFilePath.value = null
    }

    fun cancelRecognition() {
        activeJob?.cancel()
        MusicRecognitionService.reset()
        _uiState.value = RecognitionUiState.Idle
    }

    private fun isUrl(text: String): Boolean =
        text.startsWith("http://") || text.startsWith("https://")

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
    }
}
