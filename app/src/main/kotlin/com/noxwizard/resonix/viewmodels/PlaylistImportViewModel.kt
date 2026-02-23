package com.noxwizard.resonix.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.PlaylistEntity
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.SongItem
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.playlistimport.ImportProgress
import com.noxwizard.resonix.playlistimport.ParsedPlaylist
import com.noxwizard.resonix.playlistimport.ParsedTrack
import com.noxwizard.resonix.playlistimport.SpotifyParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for playlist import functionality.
 * Handles Spotify URL imports with progress tracking and YouTube matching.
 */
@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val authProvider: com.noxwizard.resonix.auth.SpotifyAuthProviderImpl
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(PlaylistImportUiState())
    val uiState: StateFlow<PlaylistImportUiState> = _uiState.asStateFlow()
    
    init {
        SpotifyParser.setAuthProvider(authProvider)
    }
    
    /**
     * Import a Spotify playlist with progress updates.
     */
    fun importSpotifyPlaylist(url: String) {
        if (url.isBlank()) return

        // Proactively check for Spotify auth if the URL is a Spotify one
        if (SpotifyParser.isSpotifyUrl(url) && !authProvider.isAuthorized()) {
            _uiState.update { it.copy(showSpotifyLoginDialog = true) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, statusMessage = "Starting import...") }
            
            try {
                // Use the new infinite import with Flow
                SpotifyParser.importFullPlaylist(url)
                    .collect { progress ->
                        when (progress) {
                            is ImportProgress.Loading -> {
                                _uiState.update { state ->
                                    val total = progress.totalEstimate  // Store in local variable for smart cast
                                    state.copy(
                                        importProgress = if (total != null && total > 0) {
                                            progress.importedCount.toFloat() / total
                                        } else {
                                            0.5f // Indeterminate progress
                                        },
                                        statusMessage = progress.message,
                                        importedTracksCount = progress.importedCount
                                    )
                                }
                            }
                            
                            is ImportProgress.Success -> {
                                // Import successful, now match tracks to YouTube
                                _uiState.update { it.copy(
                                    parsedPlaylist = progress.playlist,
                                    statusMessage = "Found ${progress.playlist.tracks.size} tracks. Matching to YouTube..."
                                )}
                                
                                matchTracksToYouTube(progress.playlist)
                            }
                            
                            is ImportProgress.Error -> {
                                _uiState.update { state ->
                                    state.copy(
                                        isImporting = false,
                                        statusMessage = "Error: ${progress.error.message}",
                                        errorMessage = progress.error.message
                                    )
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isImporting = false,
                        statusMessage = "Error: ${e.message}",
                        errorMessage = e.message
                    )
                }
            }
        }
    }
    
    /**
     * Match parsed tracks to YouTube songs.
     */
    private suspend fun matchTracksToYouTube(playlist: ParsedPlaylist) = withContext(Dispatchers.IO) {
        val matched = mutableListOf<Pair<ParsedTrack, SongItem?>>()
        
        playlist.tracks.forEachIndexed { index, track ->
            // Update progress
            _uiState.update { state ->
                state.copy(
                    importProgress = (index + 1).toFloat() / playlist.tracks.size,
                    statusMessage = "Matching: ${track.artist} - ${track.title}"
                )
            }
            
            // Search YouTube
            val searchQuery = if (track.artist.isNotEmpty()) {
                "${track.artist} ${track.title}"
            } else {
                track.title
            }
            
            val ytResult = try {
                YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.firstOrNull()
            } catch (e: Exception) {
                null
            }
            
            matched.add(track to ytResult)
        }
        
        val matchCount = matched.count { it.second != null }
        _uiState.update { state ->
            state.copy(
                matchedTracks = matched,
                statusMessage = "Matched $matchCount of ${playlist.tracks.size} tracks",
                isImporting = false,
                importProgress = 1f,
                showSaveConfirmationDialog = true // Auto-show confirmation dialog
            )
        }
    }
    
    /**
     * Save matched tracks to a new playlist in the database.
     */
    fun savePlaylist(playlistName: String) {
        val validTracks = _uiState.value.matchedTracks.filter { it.second != null }
        if (validTracks.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No matched tracks to save") }
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(
                isImporting = true,
                statusMessage = "Creating playlist..."
            )}
            
            try {
                // Create playlist with bookmarkedAt set so it appears in library
                // (The playlists() query filters by bookmarkedAt IS NOT NULL)
                val playlistEntity = PlaylistEntity(
                    name = playlistName.ifBlank { "Imported Playlist" },
                    bookmarkedAt = java.time.LocalDateTime.now()
                )
                database.query { insert(playlistEntity) }
                
                // Add songs to database and playlist
                val songIds = mutableListOf<String>()
                validTracks.forEachIndexed { index, (_, songItem) ->
                    songItem?.let { song ->
                        val mediaMetadata = song.toMediaMetadata()
                        database.query { insert(mediaMetadata) }
                        songIds.add(song.id)
                    }
                    
                    _uiState.update { state ->
                        state.copy(
                            importProgress = (index + 1).toFloat() / validTracks.size,
                            statusMessage = "Adding track ${index + 1} of ${validTracks.size}"
                        )
                    }
                }
                
                // Add songs to playlist
                val playlist = database.playlist(playlistEntity.id).firstOrNull()
                if (playlist != null) {
                    database.addSongToPlaylist(playlist, songIds)
                }
                
                // Give the database a moment to complete the transaction
                // This ensures the Flow will pick up the changes
                kotlinx.coroutines.delay(100)
                
                _uiState.update { state ->
                    state.copy(
                        isImporting = false,
                        importComplete = true,
                        statusMessage = "Playlist created with ${songIds.size} tracks!",
                        createdPlaylistName = playlistEntity.name,
                        shouldNavigateToLibrary = true,
                        showSaveConfirmationDialog = false
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isImporting = false,
                        statusMessage = "Error saving: ${e.message}",
                        errorMessage = e.message
                    )
                }
            }
        }
    }
    
    /**
     * Update the playlist name.
     */
    fun updatePlaylistName(name: String) {
        _uiState.update { it.copy(playlistName = name) }
    }
    
    /**
     * Reset the import state to start a new import.
     */
    fun resetState() {
        _uiState.value = PlaylistImportUiState()
    }
    
    /**
     * Clear error message after showing it.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissLoginDialog() {
        _uiState.update { it.copy(showSpotifyLoginDialog = false) }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveConfirmationDialog = false) }
    }
    
    fun clearNavigationFlag() {
        _uiState.update { it.copy(shouldNavigateToLibrary = false) }
    }
}

/**
 * UI state for playlist import screen.
 */
data class PlaylistImportUiState(
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val statusMessage: String = "",
    val importedTracksCount: Int = 0,
    val playlistName: String = "",
    val parsedPlaylist: ParsedPlaylist? = null,
    val matchedTracks: List<Pair<ParsedTrack, SongItem?>> = emptyList(),
    val importComplete: Boolean = false,
    val createdPlaylistName: String? = null,
    val errorMessage: String? = null,
    val showSpotifyLoginDialog: Boolean = false,
    val showSaveConfirmationDialog: Boolean = false,
    val shouldNavigateToLibrary: Boolean = false
)
