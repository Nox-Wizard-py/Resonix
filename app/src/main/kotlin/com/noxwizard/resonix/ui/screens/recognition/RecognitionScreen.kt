package com.noxwizard.resonix.ui.screens.recognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerConnection
import com.noxwizard.resonix.R
import com.noxwizard.resonix.ui.component.RecognitionResultSheet
import com.noxwizard.resonix.ui.component.ShazamResultSheet
import com.noxwizard.resonix.viewmodels.RecognitionUiState
import com.noxwizard.resonix.viewmodels.RecognitionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    navController: NavController,
    viewModel: RecognitionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // ── Audio Picker ──────────────────────────────────────────────────────────
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.recognizeFromFile(it) }
    }

    // ── Mic Permission ────────────────────────────────────────────────────────
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startMicRecognition()
        } else {
            showPermissionDialog = true
        }
    }

    fun hasMicPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun onMicTap() {
        when (uiState) {
            is RecognitionUiState.Listening,
            is RecognitionUiState.Processing -> viewModel.cancelRecognition()
            else -> {
                if (hasMicPermission()) viewModel.startMicRecognition()
                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // ── Permission Rationale Dialog ───────────────────────────────────────────
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Microphone Required") },
            text = { Text("Resonix needs microphone access to identify music playing around you.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Shazam Result Sheet ───────────────────────────────────────────────────
    if (uiState is RecognitionUiState.ShazamResult) {
        val result = uiState as RecognitionUiState.ShazamResult
        ShazamResultSheet(
            title = result.title,
            artist = result.artist,
            coverArtUrl = result.coverArtUrl,
            isPrefetching = result.isPrefetching,
            isPreparingPlayback = result.isPreparingPlayback,
            isMatchFound = result.isMatchFound,
            downloadResult = result.sourceDownload,
            onDismiss = { viewModel.reset() },
            onPlayNow = {
                viewModel.setPreparingPlayback(true)
                coroutineScope.launch {
                    kotlinx.coroutines.delay(250)
                    viewModel.reset()
                    val encodedQuery = Uri.encode("${result.title} ${result.artist}")
                    navController.navigate("search/$encodedQuery")
                }
            },
            onDownloadLocal = { dr ->
                viewModel.downloadFile(dr.downloadUrl, dr.filename)
            }
        )
    }

    // ── No Match sheet ────────────────────────────────────────────────────────
    if (uiState is RecognitionUiState.NoMatch) {
        AlertDialog(
            onDismissRequest = { viewModel.reset() },
            title = { Text("No Match Found") },
            text = { Text((uiState as RecognitionUiState.NoMatch).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.reset() }) { Text("Try Again") }
            }
        )
    }

    // ── Link / Lyrics Result Sheet ────────────────────────────────────────────
    if (uiState is RecognitionUiState.Result) {
        val result = uiState as RecognitionUiState.Result
        RecognitionResultSheet(
            viewModel = viewModel,
            lyricsResult = result.lyricsResult,
            downloadResult = result.downloadResult,
            error = result.error,
            onDismiss = { viewModel.reset() },
            onPlayNow = { query ->
                viewModel.resolveAndPlay(query) { queue ->
                    playerConnection?.playQueue(queue)
                }
            }
        )
    }

    // ── Main Screen ───────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Back button
        IconButton(
            onClick = { navController.navigateUp() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back")
        }

        // File picker button
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { navController.navigate("recognition_history") }
            ) {
                Icon(painterResource(R.drawable.history), contentDescription = "History")
            }
            IconButton(
                onClick = { filePicker.launch("audio/*") }
            ) {
                Icon(painterResource(R.drawable.folder), contentDescription = "Pick audio file")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Status Text ───────────────────────────────────────────────
            Text(
                text = when (val s = uiState) {
                    is RecognitionUiState.Listening -> "Listening…"
                    is RecognitionUiState.Processing -> s.message
                    is RecognitionUiState.Error -> s.message
                    else -> "Tap to identify"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = when (uiState) {
                    is RecognitionUiState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onBackground
                }
            )

            Spacer(Modifier.height(48.dp))

            // ── Interactive Sphere Component ──────────────────────────────
            val sphereState = when (uiState) {
                is RecognitionUiState.Listening -> com.noxwizard.resonix.ui.components.SphereState.ACTIVE
                is RecognitionUiState.Processing -> {
                    if ((uiState as RecognitionUiState.Processing).message == "Identifying…") {
                        com.noxwizard.resonix.ui.components.SphereState.RECOGNIZING
                    } else {
                        com.noxwizard.resonix.ui.components.SphereState.ACTIVE
                    }
                }
                else -> com.noxwizard.resonix.ui.components.SphereState.IDLE
            }

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onMicTap() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                com.noxwizard.resonix.ui.components.RecognitionSphere(
                    state = sphereState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(64.dp))
        }

        // ── Bottom Input Field ────────────────────────────────────────────────
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("Paste lyrics or reel links") },
            trailingIcon = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = { inputText = "" }) {
                            Icon(painter = painterResource(R.drawable.close), contentDescription = "Clear")
                        }
                    }
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                focusManager.clearFocus()
                                viewModel.processInput(inputText)
                            }
                        }
                    ) {
                        Icon(painter = painterResource(R.drawable.search), contentDescription = "Submit")
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (inputText.isNotBlank()) {
                    focusManager.clearFocus()
                    viewModel.processInput(inputText)
                }
            }),
            enabled = uiState is RecognitionUiState.Idle || uiState is RecognitionUiState.Error
                || uiState is RecognitionUiState.NoMatch
        )
    }
}
