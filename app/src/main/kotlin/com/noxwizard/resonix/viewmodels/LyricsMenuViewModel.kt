package com.noxwizard.resonix.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.LyricsEntity
import com.noxwizard.resonix.lyrics.engine.UnifiedLyricsEngine
import com.noxwizard.resonix.models.MediaMetadata
import com.noxwizard.resonix.paxsenix.PaxsenixLyricsEngine
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.utils.NetworkConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

data class LyricsSearchResult(
    val lyrics: String,
    val providerName: String
)

@HiltViewModel
class LyricsMenuViewModel
@Inject
constructor(
    private val unifiedLyricsEngine: UnifiedLyricsEngine,
    val database: MusicDatabase,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsSearchResult>())
    val isLoading = MutableStateFlow(false)

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            networkConnectivity.networkStatus.collect { isConnected ->
                _isNetworkAvailable.value = isConnected
            }
        }
        
        // Set initial state using synchronous check
        _isNetworkAvailable.value = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true // Assume connected as fallback
        }
    }

    fun search(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
    ) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                val track = LyricsTrack(title, artist, null, duration * 1000L)
                val candidates = PaxsenixLyricsEngine.default().resolveRanked(track)
                val searchResults = candidates.mapNotNull { candidate ->
                    val raw = candidate.result.rawText
                    if (raw.isBlank()) null
                    else LyricsSearchResult(raw, candidate.result.providerName)
                }
                results.value = searchResults
                isLoading.value = false
            }
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
    }

    fun refetchLyrics(
        mediaMetadata: MediaMetadata,
        lyricsEntity: LyricsEntity?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            database.query {
                lyricsEntity?.let(::delete)
            }
            unifiedLyricsEngine.resolveLyrics(mediaMetadata)
        }
    }
}


