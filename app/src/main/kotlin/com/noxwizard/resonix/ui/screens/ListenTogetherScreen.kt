package com.noxwizard.resonix.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.noxwizard.resonix.R
import com.noxwizard.resonix.playback.ListenTogetherManager
import com.noxwizard.resonix.ui.component.IconButton as ResonixIconButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

data class ListenTogetherUiState(
    val roomCode: String = "",
    val username: String = "",
    val isDiceRolling: Boolean = false
)

object ListenTogetherSessionManager {
    var isActive by mutableStateOf(false)
    var isHost by mutableStateOf(true)
    var hasTempControl by mutableStateOf(false)
}

enum class SyncOverlayState {
    None, Calibrating, Complete, RoomNotFound
}

private val randomNames = listOf(
    "neon-wolf",
    "silent-orb",
    "night-raven",
    "cosmic-nox",
    "velvet-echo",
    "phantom-wave"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(
    navController: NavController
) {
    var uiState by remember { mutableStateOf(ListenTogetherUiState()) }
    var overlayState by remember { mutableStateOf(SyncOverlayState.None) }
    var isJoiningAsGuest by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val diceRotation = remember { Animatable(0f) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var syncStartTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    // Listen for room events
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Auto-resume active session if running in background
        if (ListenTogetherSessionManager.isActive) {
            val destination = if (ListenTogetherSessionManager.isHost) "listen_together_host_room" else "listen_together_guest_room"
            navController.navigate(destination) {
                popUpTo("listen_together") { inclusive = true }
            }
            return@LaunchedEffect
        }
        
        ListenTogetherManager.events.collect { event ->
            when (event) {
                is com.noxwizard.resonix.playback.RoomEvent.JoinSuccess -> {
                    val elapsed = System.currentTimeMillis() - syncStartTime
                    if (elapsed < 2000L) {
                        kotlinx.coroutines.delay(2000L - elapsed)
                    }
                    overlayState = SyncOverlayState.Complete
                }
                is com.noxwizard.resonix.playback.RoomEvent.RoomNotFound -> {
                    overlayState = SyncOverlayState.RoomNotFound
                }
                else -> {}
            }
        }
    }



    // Listen for QR Scan result
    val currentSavedState = navController.currentBackStackEntry?.savedStateHandle
    androidx.compose.runtime.LaunchedEffect(currentSavedState) {
        currentSavedState?.getStateFlow<String?>("qr_result", null)?.collect { code ->
            if (!code.isNullOrBlank()) {
                uiState = uiState.copy(roomCode = code)
                currentSavedState.remove<String>("qr_result")
            }
        }
    }



    val onRoomCodeChange: (String) -> Unit = { code ->
        uiState = uiState.copy(roomCode = code)
    }

    val onUsernameChange: (String) -> Unit = { name ->
        uiState = uiState.copy(username = name)
    }

    val onClearUsername: () -> Unit = {
        uiState = uiState.copy(username = "")
    }

    val onGenerateRandomName: () -> Unit = {
        if (!uiState.isDiceRolling) {
            uiState = uiState.copy(isDiceRolling = true)
            coroutineScope.launch {
                diceRotation.animateTo(
                    targetValue = diceRotation.value + 720f,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                )
                uiState = uiState.copy(
                    username = randomNames.random(),
                    isDiceRolling = false
                )
            }
        }
    }

    val onScanQrClick: () -> Unit = {
        navController.navigate("listen_together_qr_scanner")
    }

    val onJoinClick: () -> Unit = {
        val code = uiState.roomCode.trim()
        val name = uiState.username.trim()
        if (code.isNotBlank() && name.isNotBlank()) {
            isJoiningAsGuest = true
            syncStartTime = System.currentTimeMillis()
            // Show loading — navigation happens only via JoinSuccess/RoomNotFound event
            overlayState = SyncOverlayState.Calibrating
            ListenTogetherManager.joinRoom(code, name)
        }
    }

    val onCreateClick: () -> Unit = {
        val name = uiState.username.trim()
        if (name.isNotBlank()) {
            isJoiningAsGuest = false
            // Generate code, display it, then fire create_room on backend
            val code = ListenTogetherManager.createRoom(name)
            uiState = uiState.copy(roomCode = code)
            syncStartTime = System.currentTimeMillis()
            // Show loading — navigation happens only via JoinSuccess event from backend
            overlayState = SyncOverlayState.Calibrating
        }
    }

    val isJoinEnabled = uiState.roomCode.isNotBlank() && uiState.username.isNotBlank()
    val isCreateEnabled = uiState.username.isNotBlank()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Listen Together",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    ResonixIconButton(
                        onClick = navController::navigateUp
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Text(
                text = "Music in perfect sync",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // SECTION 1: Room Code
                    Text(
                        text = "Room code",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = uiState.roomCode,
                        onValueChange = onRoomCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter room code") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.account), // fallback group icon
                                contentDescription = "Room"
                            )
                        },
                        trailingIcon = {
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (uiState.roomCode.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onRoomCodeChange("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                IconButton(onClick = onScanQrClick) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = "Scan QR"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // SECTION 2: Username
                    Text(
                        text = "Username",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter username") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = "Profile"
                            )
                        },
                        trailingIcon = {
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (uiState.username.isNotEmpty()) {
                                    IconButton(
                                        onClick = onClearUsername,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onGenerateRandomName,
                                    enabled = !uiState.isDiceRolling
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Casino,
                                        contentDescription = "Random name",
                                        modifier = Modifier.rotate(diceRotation.value)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // SECTION 3: Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onJoinClick,
                            enabled = isJoinEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Join")
                        }

                        Button(
                            onClick = onCreateClick,
                            enabled = isCreateEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
        
        when (overlayState) {
            SyncOverlayState.Calibrating -> {
                ListenTogetherCalibratingDialog(
                    onDismissRequest = { overlayState = SyncOverlayState.None }
                )
            }
            SyncOverlayState.Complete -> {
                ListenTogetherSyncCompleteDialog(
                    onEnterRoom = {
                        overlayState = SyncOverlayState.None
                        ListenTogetherSessionManager.isActive = true
                        ListenTogetherSessionManager.isHost = !isJoiningAsGuest
                        ListenTogetherSessionManager.hasTempControl = false
                        if (isJoiningAsGuest) {
                            navController.navigate("listen_together_guest_room")
                        } else {
                            navController.navigate("listen_together_host_room")
                        }
                    }
                )
            }
            SyncOverlayState.RoomNotFound -> {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { overlayState = SyncOverlayState.None },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        androidx.compose.material3.Text(
                            text = "Room Not Found",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        androidx.compose.material3.Text(
                            text = "No room exists with that code. Ask the host to share their room code.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { overlayState = SyncOverlayState.None }
                        ) {
                            androidx.compose.material3.Text("OK")
                        }
                    }
                )
            }
            else -> Unit
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ListenTogetherScreenPreview() {
    MaterialTheme {
        ListenTogetherScreen(rememberNavController())
    }
}
