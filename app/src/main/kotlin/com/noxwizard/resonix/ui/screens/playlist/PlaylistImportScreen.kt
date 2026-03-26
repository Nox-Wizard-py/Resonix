package com.noxwizard.resonix.ui.screens.playlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.noxwizard.resonix.R
import com.noxwizard.resonix.playlistimport.ImportState
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.YouTubeMatcher
import com.noxwizard.resonix.viewmodels.PlaylistImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: PlaylistImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val isWorking = uiState.importState !is ImportState.Idle &&
            uiState.importState !is ImportState.Failed &&
            uiState.importState !is ImportState.Done

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.shouldNavigateToLibrary) {
        if (uiState.shouldNavigateToLibrary) {
            viewModel.clearNavigationFlag()
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
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Header ──
            item {
                Text(
                    text = "Paste a playlist link or enter tracks manually (one per line)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Playlist Name ──
            item {
                OutlinedTextField(
                    value = uiState.playlistName,
                    onValueChange = { viewModel.updatePlaylistName(it) },
                    label = { Text("Playlist Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isWorking
                )
            }

            // ── Input Field ──
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Link or Tracklist") },
                        placeholder = {
                            Text("https://open.spotify.com/playlist/...\nor\nArtist - Song Title\nArtist - Song Title")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        enabled = !isWorking && !uiState.importComplete,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.processInput(inputText.text, context)
                        })
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: Spotify link import is limited to 100 tracks per link. For larger playlists, please divide them into multiple playlists of 100 songs max.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Import Button ──
            if (!uiState.importComplete && uiState.matchResults.isEmpty()) {
                item {
                    Button(
                        onClick = { viewModel.processInput(inputText.text, context) },
                        enabled = inputText.text.isNotBlank() && !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isWorking) "Processing..." else "Import")
                    }
                }
            }

            // ── Progress Section ──
            item {
                AnimatedVisibility(
                    visible = isWorking,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ProgressSection(uiState.importState, uiState.matchResults.size, uiState.totalCount)
                }
            }

            // ── Match Results ──
            if (uiState.matchResults.isNotEmpty() && !uiState.importComplete) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Results",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${uiState.matchedCount} / ${uiState.totalCount} matched",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                itemsIndexed(uiState.matchResults) { index, result ->
                    MatchResultRow(
                        result = result,
                        onRetry = { viewModel.retryTrack(index) }
                    )
                }

                // Save Button
                if (uiState.importState is ImportState.Idle && uiState.matchedCount > 0) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.savePlaylist(
                                    uiState.playlistName.ifBlank {
                                        uiState.parsedPlaylist?.name ?: "Imported Playlist"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create Playlist (${uiState.matchedCount} tracks)")
                        }
                    }
                }
            }

            // ── Done State ──
            if (uiState.importComplete) {
                item {
                    val doneState = uiState.importState as? ImportState.Done
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Playlist Created!",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (doneState != null) {
                                Text(
                                    text = "${doneState.matchedCount} of ${doneState.totalCount} tracks added to \"${doneState.playlistName}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }

            // ── Error State ──
            if (uiState.importState is ImportState.Failed) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = (uiState.importState as ImportState.Failed).error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { viewModel.processInput(inputText.text, context) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        // ── Tracklist Dialog (for >100 tracks or URL extraction fallback) ──
        if (uiState.showTracklistDialog) {
            TracklistDialog(
                tracklistText = uiState.generatedTracklist,
                onPasteAndImport = { text ->
                    viewModel.importFromTracklist(text)
                },
                onDismiss = { viewModel.dismissTracklistDialog() },
                context = context
            )
        }

        // ── Save Confirmation Dialog ──
        if (uiState.showSaveDialog) {
            SaveDialog(
                matchedCount = uiState.matchedCount,
                totalCount = uiState.totalCount,
                defaultName = uiState.playlistName.ifBlank {
                    uiState.parsedPlaylist?.name ?: "Imported Playlist"
                },
                onSave = { name -> viewModel.savePlaylist(name) },
                onDismiss = { viewModel.dismissSaveDialog() }
            )
        }
    }
}

@Composable
private fun ProgressSection(
    state: ImportState,
    completed: Int,
    total: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (state) {
            is ImportState.Parsing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is ImportState.Matching -> {
                val progress = if (state.total > 0) state.current.toFloat() / state.total else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Matching ${state.current} / ${state.total}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.currentTrackName.isNotEmpty()) {
                    Text(
                        text = state.currentTrackName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            is ImportState.Saving -> {
                val progress = if (state.total > 0) state.current.toFloat() / state.total else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Saving ${state.current} / ${state.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun MatchResultRow(
    result: YouTubeMatcher.MatchResult,
    onRetry: () -> Unit
) {
    val isMatched = result.songItem != null
    val scoreColor = when {
        result.score >= 0.7f -> MaterialTheme.colorScheme.primary
        result.score >= 0.4f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status icon
        Icon(
            painter = painterResource(
                if (isMatched) R.drawable.check else R.drawable.close
            ),
            contentDescription = null,
            tint = if (isMatched) scoreColor else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (result.track.artist.isNotEmpty())
                    "${result.track.artist} - ${result.track.title}"
                else result.track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isMatched && result.songItem != null) {
                Text(
                    text = "→ ${result.songItem.artists.joinToString { it.name }} - ${result.songItem.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Score or retry
        if (isMatched) {
            Text(
                text = "${(result.score * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = scoreColor,
                fontWeight = FontWeight.Bold
            )
        } else {
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Retry", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TracklistDialog(
    tracklistText: String,
    onPasteAndImport: (String) -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    var pastedText by remember { mutableStateOf(TextFieldValue("")) }
    val hasGeneratedList = tracklistText.isNotBlank() &&
            !tracklistText.startsWith("Could not")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Large Playlist Detected") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasGeneratedList) {
                    Text(
                        text = "This playlist has more than 100 tracks. Copy the tracklist below, then paste it back to import.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("tracklist", tracklistText))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋  Copy Tracklist")
                    }
                    HorizontalDivider()
                } else {
                    Text(
                        text = "Paste your tracklist below (one track per line, format: Artist - Title)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    label = { Text("Paste tracklist here") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("Artist - Title\nArtist - Title") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasteAndImport(pastedText.text) },
                enabled = pastedText.text.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SaveDialog(
    matchedCount: Int,
    totalCount: Int,
    defaultName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(defaultName) }
    val matchPercentage = if (totalCount > 0) (matchedCount * 100) / totalCount else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Playlist?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "$matchedCount of $totalCount tracks matched ($matchPercentage%)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Playlist Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nameInput.ifBlank { defaultName }) },
                enabled = matchedCount > 0
            ) {
                Text("Create Playlist")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
