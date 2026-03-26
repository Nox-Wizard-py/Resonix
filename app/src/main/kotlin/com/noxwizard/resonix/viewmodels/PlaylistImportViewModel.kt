package com.noxwizard.resonix.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.PlaylistEntity
import com.noxwizard.resonix.playlistimport.*
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.utils.YouTubeMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistImportUiState())
    val uiState: StateFlow<PlaylistImportUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null

    /**
     * Process user input — routes Spotify URLs to WebView, everything else to old pipeline.
     */
    fun processInput(input: String, context: Context) {
        if (input.isBlank()) return

        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update { it.copy(
                importState = ImportState.Parsing(),
                errorMessage = null,
                matchResults = emptyList(),
                importComplete = false
            )}

            try {
                val trimmed = input.trim()

                val result = PlaylistImporter.import(trimmed, _uiState.value.playlistName.ifBlank { null })
                handleImportResult(result)
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    importState = ImportState.Failed(e.message ?: "Unknown error"),
                    errorMessage = e.message
                )}
            }
        }
    }

    private fun handleImportResult(result: ImportInput) {
        when (result) {
            is ImportInput.ParsedTracks -> {
                _uiState.update { it.copy(
                    parsedPlaylist = result.playlist,
                    playlistName = it.playlistName.ifBlank { result.playlist.name }
                )}
                importJob = viewModelScope.launch { startMatching(result.playlist) }
            }
            is ImportInput.NeedsManualInput -> {
                _uiState.update { it.copy(
                    importState = ImportState.Idle,
                    showTracklistDialog = true,
                    generatedTracklist = result.tracklistText,
                    playlistName = it.playlistName.ifBlank { result.playlistName }
                )}
            }
        }
    }

    /**
     * Import from manually pasted tracklist text.
     */
    fun importFromTracklist(text: String) {
        if (text.isBlank()) return

        _uiState.update { it.copy(showTracklistDialog = false) }

        val playlist = TextParser.parseText(text, _uiState.value.playlistName.ifBlank { null })
        if (playlist.tracks.isEmpty()) {
            _uiState.update { it.copy(
                importState = ImportState.Failed("No tracks found in the input"),
                errorMessage = "Could not parse any tracks. Use format: Artist - Title"
            )}
            return
        }

        _uiState.update { it.copy(
            parsedPlaylist = playlist,
            playlistName = it.playlistName.ifBlank { playlist.name }
        )}

        importJob?.cancel()
        importJob = viewModelScope.launch {
            startMatching(playlist)
        }
    }

    private suspend fun startMatching(playlist: ParsedPlaylist) {
        val total = playlist.tracks.size

        _uiState.update { it.copy(
            importState = ImportState.Matching(0, total)
        )}

        YouTubeMatcher.matchAll(playlist.tracks).collect { results ->
            val matchedCount = results.count { it.songItem != null }

            _uiState.update { state ->
                state.copy(
                    importState = ImportState.Matching(results.size, total,
                        results.lastOrNull()?.track?.let { t ->
                            if (t.artist.isNotEmpty()) "${t.artist} - ${t.title}" else t.title
                        } ?: ""
                    ),
                    matchResults = results,
                    matchedCount = matchedCount,
                    totalCount = total
                )
            }
        }

        val finalResults = _uiState.value.matchResults
        val matchedCount = finalResults.count { it.songItem != null }

        _uiState.update { it.copy(
            importState = ImportState.Idle,
            matchedCount = matchedCount,
            totalCount = total,
            showSaveDialog = matchedCount > 0
        )}
    }

    fun savePlaylist(name: String) {
        val validResults = _uiState.value.matchResults.filter { it.songItem != null }
        if (validResults.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No matched tracks to save") }
            return
        }

        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            val total = validResults.size
            _uiState.update { it.copy(
                importState = ImportState.Saving(0, total),
                showSaveDialog = false
            )}

            try {
                val playlistEntity = PlaylistEntity(
                    name = name.ifBlank { "Imported Playlist" },
                    bookmarkedAt = java.time.LocalDateTime.now()
                )
                database.query { insert(playlistEntity) }

                val songIds = mutableListOf<String>()
                validResults.forEachIndexed { index, result ->
                    result.songItem?.let { song ->
                        val mediaMetadata = song.toMediaMetadata()
                        database.query { insert(mediaMetadata) }
                        songIds.add(song.id)
                    }

                    _uiState.update { it.copy(
                        importState = ImportState.Saving(index + 1, total)
                    )}
                }

                val playlist = database.playlist(playlistEntity.id).firstOrNull()
                if (playlist != null) {
                    database.addSongToPlaylist(playlist, songIds)
                }

                _uiState.update { it.copy(
                    importState = ImportState.Done(
                        playlistName = playlistEntity.name,
                        matchedCount = songIds.size,
                        totalCount = _uiState.value.totalCount
                    ),
                    importComplete = true,
                    createdPlaylistName = playlistEntity.name,
                    shouldNavigateToLibrary = true
                )}

            } catch (e: Exception) {
                _uiState.update { it.copy(
                    importState = ImportState.Failed(e.message ?: "Error saving playlist"),
                    errorMessage = e.message
                )}
            }
        }
    }

    fun retryTrack(index: Int) {
        val track = _uiState.value.matchResults.getOrNull(index)?.track ?: return

        viewModelScope.launch {
            val result = YouTubeMatcher.matchSingle(track, index)
            val updatedResults = _uiState.value.matchResults.toMutableList()
            updatedResults[index] = result
            val matchedCount = updatedResults.count { it.songItem != null }

            _uiState.update { it.copy(
                matchResults = updatedResults,
                matchedCount = matchedCount
            )}
        }
    }

    fun updatePlaylistName(name: String) {
        _uiState.update { it.copy(playlistName = name) }
    }

    fun dismissTracklistDialog() {
        _uiState.update { it.copy(showTracklistDialog = false) }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNavigationFlag() {
        _uiState.update { it.copy(shouldNavigateToLibrary = false) }
    }

    fun resetState() {
        importJob?.cancel()
        _uiState.value = PlaylistImportUiState()
    }
}

data class PlaylistImportUiState(
    val importState: ImportState = ImportState.Idle,
    val playlistName: String = "",
    val parsedPlaylist: ParsedPlaylist? = null,
    val matchResults: List<YouTubeMatcher.MatchResult> = emptyList(),
    val matchedCount: Int = 0,
    val totalCount: Int = 0,
    val importComplete: Boolean = false,
    val createdPlaylistName: String? = null,
    val errorMessage: String? = null,
    val showTracklistDialog: Boolean = false,
    val generatedTracklist: String = "",
    val showSaveDialog: Boolean = false,
    val shouldNavigateToLibrary: Boolean = false
)
