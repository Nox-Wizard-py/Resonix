package com.noxwizard.resonix.ui.screens.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalDatabase
import com.noxwizard.resonix.LocalPlayerConnection
import com.noxwizard.resonix.R
import com.noxwizard.resonix.db.entities.PlaylistEntity
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.SongItem
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.playlistimport.ParsedPlaylist
import com.noxwizard.resonix.playlistimport.ParsedTrack
import com.noxwizard.resonix.playlistimport.PlaylistImporter
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.utils.backToMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for importing playlists from external platforms.
 * Supports Spotify URLs and manual text input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // UI State
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var playlistName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    
    // Parsed data
    var parsedPlaylist by remember { mutableStateOf<ParsedPlaylist?>(null) }
    var matchedTracks by remember { mutableStateOf<List<Pair<ParsedTrack, SongItem?>>>(emptyList()) }
    var importComplete by remember { mutableStateOf(false) }
    
    fun startImport() {
        if (inputText.text.isBlank()) return
        
        isImporting = true
        importProgress = 0f
        statusMessage = "Parsing input..."
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Parse input
                val result = PlaylistImporter.import(inputText.text)
                
                result.onSuccess { playlist ->
                    parsedPlaylist = playlist
                    if (playlistName.isBlank()) {
                        playlistName = playlist.name
                    }
                    
                    withContext(Dispatchers.Main) {
                        statusMessage = "Found ${playlist.tracks.size} tracks. Searching YouTube..."
                    }
                    
                    // Step 2: Match tracks to YouTube
                    val matched = mutableListOf<Pair<ParsedTrack, SongItem?>>()
                    playlist.tracks.forEachIndexed { index, track ->
                        withContext(Dispatchers.Main) {
                            importProgress = (index + 1).toFloat() / playlist.tracks.size
                            statusMessage = "Matching: ${track.artist} - ${track.title}"
                        }
                        
                        // Search YouTube for this track
                        val searchQuery = if (track.artist.isNotEmpty()) {
                            "${track.artist} ${track.title}"
                        } else {
                            track.title
                        }
                        
                        val ytResult = try {
                            YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                        } catch (e: Exception) {
                            null
                        }
                        
                        matched.add(track to ytResult)
                    }
                    
                    withContext(Dispatchers.Main) {
                        matchedTracks = matched
                        val matchCount = matched.count { it.second != null }
                        statusMessage = "Matched $matchCount of ${playlist.tracks.size} tracks"
                        isImporting = false
                    }
                }
                
                result.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        statusMessage = "Error: ${error.message}"
                        snackbarHostState.showSnackbar("Import failed: ${error.message}")
                        isImporting = false
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "Error: ${e.message}"
                    snackbarHostState.showSnackbar("Import failed: ${e.message}")
                    isImporting = false
                }
            }
        }
    }
    
    fun savePlaylist() {
        val validTracks = matchedTracks.filter { it.second != null }
        if (validTracks.isEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("No matched tracks to save")
            }
            return
        }
        
        isImporting = true
        statusMessage = "Creating playlist..."
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Create playlist
                val playlistEntity = PlaylistEntity(
                    name = playlistName.ifBlank { "Imported Playlist" }
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
                    
                    withContext(Dispatchers.Main) {
                        importProgress = (index + 1).toFloat() / validTracks.size
                        statusMessage = "Adding track ${index + 1} of ${validTracks.size}"
                    }
                }
                
                // Add songs to playlist
                val playlist = database.playlist(playlistEntity.id).firstOrNull()
                if (playlist != null) {
                    database.addSongToPlaylist(playlist, songIds)
                }
                
                withContext(Dispatchers.Main) {
                    importComplete = true
                    isImporting = false
                    statusMessage = "Playlist created with ${songIds.size} tracks!"
                    snackbarHostState.showSnackbar("Playlist '${playlistEntity.name}' created!")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "Error saving: ${e.message}"
                    snackbarHostState.showSnackbar("Save failed: ${e.message}")
                    isImporting = false
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_playlist)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Instructions
            Text(
                text = "Paste a Spotify playlist URL or enter track names (one per line, format: 'Artist - Title')",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Playlist name field
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isImporting
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Input field
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("URL or Track List") },
                placeholder = { Text("https://open.spotify.com/playlist/...\nor\nArtist - Title\nArtist - Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                enabled = !isImporting && !importComplete,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { startImport() })
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Import button
            if (!importComplete && matchedTracks.isEmpty()) {
                Button(
                    onClick = { startImport() },
                    enabled = inputText.text.isNotBlank() && !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isImporting) "Importing..." else "Parse & Match")
                }
            }
            
            // Progress and status
            AnimatedVisibility(visible = isImporting || statusMessage.isNotEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isImporting) {
                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Matched tracks preview
            if (matchedTracks.isNotEmpty() && !importComplete) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Preview (${matchedTracks.count { it.second != null }} matched)",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show first 10 tracks as preview
                matchedTracks.take(10).forEach { (original, matched) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                if (matched != null) R.drawable.check 
                                else R.drawable.error
                            ),
                            contentDescription = null,
                            tint = if (matched != null) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (original.artist.isNotEmpty()) 
                                    "${original.artist} - ${original.title}" 
                                else original.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            if (matched != null) {
                                Text(
                                    text = "→ ${matched.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                
                if (matchedTracks.size > 10) {
                    Text(
                        text = "... and ${matchedTracks.size - 10} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Save button
                Button(
                    onClick = { savePlaylist() },
                    enabled = !isImporting && matchedTracks.any { it.second != null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Playlist")
                }
            }
            
            // Success state
            if (importComplete) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Playlist Created!",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}
