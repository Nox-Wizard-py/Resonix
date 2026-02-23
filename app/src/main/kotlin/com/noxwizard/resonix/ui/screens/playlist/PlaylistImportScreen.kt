package com.noxwizard.resonix.ui.screens.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.noxwizard.resonix.R
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.viewmodels.PlaylistImportViewModel

/**
 * Screen for importing playlists from external platforms.
 * Supports Spotify URLs with infinite import (100+ tracks) and manual text input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: PlaylistImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    
    // Show error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    // Navigate to library after playlist creation
    LaunchedEffect(uiState.shouldNavigateToLibrary) {
        if (uiState.shouldNavigateToLibrary) {
            viewModel.clearNavigationFlag()
            
            // Show success message
            uiState.createdPlaylistName?.let { playlistName ->
                val trackCount = uiState.matchedTracks.count { it.second != null }
                snackbarHostState.showSnackbar(
                    message = "Playlist '$playlistName' created with $trackCount tracks",
                    duration = SnackbarDuration.Short
                )
            }
            
            // Navigate back to main screen where user can see the playlist in library
            navController.navigateUp()
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
                value = uiState.playlistName.ifBlank { 
                    uiState.parsedPlaylist?.name ?: "" 
                },
                onValueChange = { viewModel.updatePlaylistName(it) },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isImporting
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
                enabled = !uiState.isImporting && !uiState.importComplete,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { 
                    viewModel.importSpotifyPlaylist(inputText.text) 
                })
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Import button
            if (!uiState.importComplete && uiState.matchedTracks.isEmpty()) {
                Button(
                    onClick = { viewModel.importSpotifyPlaylist(inputText.text) },
                    enabled = inputText.text.isNotBlank() && !uiState.isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isImporting) "Importing..." else "Import")
                }
            }
            
            // Progress and status
            AnimatedVisibility(visible = uiState.isImporting || uiState.statusMessage.isNotEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (uiState.isImporting) {
                        LinearProgressIndicator(
                            progress = { uiState.importProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Show track count during Spotify import with Flow-based progress
                    if (uiState.importedTracksCount > 0 && uiState.matchedTracks.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.importedTracksCount} tracks imported from Spotify...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Matched tracks preview
            if (uiState.matchedTracks.isNotEmpty() && !uiState.importComplete) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Preview (${uiState.matchedTracks.count { it.second != null }} matched)",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show first 10 tracks as preview
                uiState.matchedTracks.take(10).forEach { (original, matched) ->
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
                
                if (uiState.matchedTracks.size > 10) {
                    Text(
                        text = "... and ${uiState.matchedTracks.size - 10} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Save button
                Button(
                    onClick = { 
                        viewModel.savePlaylist(
                            uiState.playlistName.ifBlank { 
                                uiState.parsedPlaylist?.name ?: "Imported Playlist" 
                            }
                        )
                    },
                    enabled = !uiState.isImporting && uiState.matchedTracks.any { it.second != null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Playlist")
                }
            }
            
            // Success state
            if (uiState.importComplete) {
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
                            text = uiState.statusMessage,
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

        if (uiState.showSpotifyLoginDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissLoginDialog() },
                title = { Text("Spotify Login Required") },
                text = { Text("To import large playlists (100+ songs), you must link your Spotify account.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissLoginDialog()
                            navController.navigate("spotify_login")
                        }
                    ) {
                        Text("Login")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissLoginDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Save Confirmation Dialog
        if (uiState.showSaveConfirmationDialog) {
            val matchCount = uiState.matchedTracks.count { it.second != null }
            val totalCount = uiState.matchedTracks.size
            val matchPercentage = if (totalCount > 0) (matchCount * 100) / totalCount else 0
            var playlistNameInput by remember { 
                mutableStateOf(uiState.parsedPlaylist?.name ?: "Imported Playlist") 
            }

            AlertDialog(
                onDismissRequest = { viewModel.dismissSaveDialog() },
                title = { Text("Save Playlist?") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Match statistics
                        Text(
                            text = "$matchCount of $totalCount tracks matched ($matchPercentage%)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Playlist name input
                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            label = { Text("Playlist Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Track preview
                        Text(
                            text = "Preview:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            uiState.matchedTracks.take(5).forEach { (original, matched) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${original.artist} - ${original.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (uiState.matchedTracks.size > 5) {
                                Text(
                                    text = "... and ${uiState.matchedTracks.size - 5} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.savePlaylist(playlistNameInput.ifBlank { 
                                uiState.parsedPlaylist?.name ?: "Imported Playlist" 
                            })
                            viewModel.dismissSaveDialog()
                        },
                        enabled = matchCount > 0
                    ) {
                        Text("Create Playlist")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSaveDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
