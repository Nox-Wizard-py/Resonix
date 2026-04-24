package com.noxwizard.resonix.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch

// ── Data models ──────────────────────────────────────────────


import androidx.hilt.navigation.compose.hiltViewModel
import com.noxwizard.resonix.viewmodels.ListenTogetherViewModel
import com.noxwizard.resonix.viewmodels.RoomUiState
import com.noxwizard.resonix.playback.ListenTogetherManager
import com.noxwizard.resonix.LocalPlayerConnection
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class PlaybackPermission { Everyone, HostOnly }

data class RoomUser(
    val userId: String,
    val username: String,
    val isHost: Boolean = false,
    val hasTempControl: Boolean = false,
    val isLocalUser: Boolean = false
)

// ── Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherHostRoomScreen(
    navController: NavController,
    viewModel: ListenTogetherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Bind PlayerConnection (CompositionLocal) to ViewModel for playback sync
    val playerConnection = LocalPlayerConnection.current
    LaunchedEffect(playerConnection) {
        playerConnection?.let { viewModel.bindPlayerConnection(it) }
    }

    if (uiState == null) {
        var isTimeout by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(5000)
            isTimeout = true
        }
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isTimeout) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Failed to connect to room.", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.navigateUp() }) {
                        Text("Go Back")
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val state = uiState!!
    var selectedUser by remember { mutableStateOf<RoomUser?>(null) }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = if (isDark) Color(0xFF2A2A32) else MaterialTheme.colorScheme.surface
    val chipColor = if (isDark) Color(0xFF1C1C24) else MaterialTheme.colorScheme.surfaceVariant

    val isHost = state.isLocalUserHost
    val hasSettingsControl = isHost || state.connectedUsers.find { it.userId == ListenTogetherManager.localUserId }?.hasTempControl == true
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Back button navigates away WITHOUT closing the room — room stays active in background.
    // Only the red Leave Room button closes the room.
    androidx.activity.compose.BackHandler {
        navController.navigate("home") {
            popUpTo("home") { inclusive = true }
        }
    }

    // Auto-navigate when kicked or room is closed by host
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            ListenTogetherSessionManager.isActive = false
            navController.navigate("listen_together") {
                popUpTo("home")
            }
        }
    }

    // User management modal — only shown when host taps a non-host user
    if (selectedUser != null && isHost && !selectedUser!!.isHost) {
        UserManagementModal(
            user = selectedUser!!,
            onDismiss = { selectedUser = null },
            onTransferHost = {
                viewModel.transferHost(selectedUser!!.userId)
                selectedUser = null
            },
            onToggleTempControl = {
                viewModel.grantSudo(selectedUser!!.userId)
                selectedUser = null
            },
            onKickUser = {
                viewModel.kickUser(selectedUser!!.userId)
                selectedUser = null
            },
            cardColor = cardColor
        )
    } else if (selectedUser != null) {
        // Dismiss — guests cannot manage users
        selectedUser = null
    }
    var showQrDialog by remember { mutableStateOf(false) }

    if (showQrDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        QrShareDialog(
            roomCode = state.roomCode,
            onDismiss = { showQrDialog = false },
            onShare = {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "resonix://listen/${state.roomCode}")
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Room Link"))
                showQrDialog = false
            },
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // ── TOP HEADER ────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 0.dp
            ) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = statusBarPadding.calculateTopPadding() + 12.dp,
                            bottom = 12.dp,
                            start = 20.dp,
                            end = 20.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back arrow + App name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "Resonix",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    // Room ID chip
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "#${state.roomCode}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // User count
                    Text(
                        text = "${state.connectedUsers.size} users",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── ROOM SECTION ──────────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Room",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isHost) "Host session active" else "Guest — Listening",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // QR code only visible to host
                    if (isHost) {
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode,
                                contentDescription = "Show QR",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ── NERD STATS ROW ────────────────────────────
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NerdStat(label = "RTT", value = state.rtt)
                    NerdStat(label = "Offset", value = state.offset)
                    NerdStat(label = "NTP", value = state.ntpSynced)
                }
            }

            // ── PLAYBACK PERMISSIONS ──────────────────────────
            // Guests see this card but cannot interact with it
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isHost) Modifier.alpha(0.75f) else Modifier
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = "Playback permissions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        PlaybackPermission.entries.forEachIndexed { index, perm ->
                            SegmentedButton(
                                selected = state.playbackPermission == perm,
                                onClick = {
                                    if (hasSettingsControl) {
                                        val mode = if (perm == PlaybackPermission.Everyone) "everyone" else "host_only"
                                        viewModel.setPlaybackPermission(mode)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = PlaybackPermission.entries.size
                                ),
                                label = { Text(perm.name) },
                                enabled = hasSettingsControl
                            )
                        }
                    }
                }
            }

            // ── GLOBAL VOLUME ─────────────────────────────────
            // Guests see the slider but cannot drag it
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Global volume",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(state.globalVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = state.globalVolume,
                        onValueChange = { if (hasSettingsControl) viewModel.updateGlobalVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasSettingsControl
                    )
                }
            }

            // ── CONNECTED USERS ───────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Connected users",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Fixed-height row — avoids intrinsic measurement crash
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pinned host — shows real hostname from socket state
                        val hostUser = state.connectedUsers.firstOrNull { it.isHost }
                        if (hostUser != null) {
                            Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                                UserChip(
                                    user = hostUser,
                                    chipColor = chipColor,
                                    onClick = {
                                        if (isHost) selectedUser = hostUser
                                    }
                                )
                            }
                            VerticalDivider(
                                modifier = Modifier
                                    .height(64.dp)
                                    .padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        // Scrollable guest rail with right-edge fade
                        val guestUsers = state.connectedUsers.filter { !it.isHost }
                        Box(modifier = Modifier.weight(1f)) {
                            LazyRow(
                                contentPadding = PaddingValues(start = 8.dp, end = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(guestUsers) { user ->
                                    UserChip(
                                        user = user,
                                        chipColor = chipColor,
                                        onClick = { if (isHost) selectedUser = user }
                                    )
                                }
                            }
                            // Subtle right-edge fade — uses matchParentSize to avoid Intrinsic crash
                            Box(modifier = Modifier.matchParentSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(48.dp)
                                        .align(Alignment.CenterEnd)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, cardColor)
                                            )
                                        )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // ── TIMING NUDGE [BETA] ───────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Timing nudge",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Beta",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                if (hasSettingsControl) {
                                    viewModel.updateTimingNudge(-50)
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Controlled by host")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease nudge")
                        }

                        Text(
                            text = if (state.timingOffsetMs >= 0)
                                "+${state.timingOffsetMs}ms"
                            else
                                "${state.timingOffsetMs}ms",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FilledTonalIconButton(
                            onClick = {
                                if (hasSettingsControl) {
                                    viewModel.updateTimingNudge(+50)
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Controlled by host")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase nudge")
                        }
                    }
                }
            }

            // ── LEAVE ROOM BUTTON ─────────────────────────────
            Button(
                onClick = {
                    viewModel.leaveRoom()
                    ListenTogetherSessionManager.isActive = false
                    navController.navigate("listen_together") {
                        popUpTo("home") 
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.error,
                        spotColor = MaterialTheme.colorScheme.error
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Leave Room",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────

@Composable
private fun NerdStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * UserChip — renders an avatar with:
 *  - Crown badge (top-right) for Host
 *  - Lightning bolt badge (top-right) for TempControl guest
 *  - Slim themed ring for the local user (self-identification)
 */
@Composable
private fun UserChip(
    user: RoomUser,
    chipColor: Color,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Avatar container — fixed 56dp to avoid Intrinsic crash
        Box(modifier = Modifier.size(56.dp)) {

            // Self-identification ring for local user
            if (user.isLocalUser) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(
                            border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.85f)),
                            shape = CircleShape
                        )
                        .align(Alignment.Center)
                )
            }

            // Avatar circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(chipColor)
                    .align(Alignment.BottomStart)
            ) {
                Text(
                    text = user.username.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Crown badge — host only (top-right)
            if (user.isHost) {
                Surface(
                    shape = CircleShape,
                    color = primaryColor,
                    border = BorderStroke(2.dp, bgColor),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(id = com.noxwizard.resonix.R.drawable.ic_crown_outline),
                        contentDescription = "Host",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // Lightning badge — temp control guest only (top-right)
            if (user.hasTempControl && !user.isHost) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    border = BorderStroke(2.dp, bgColor),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ElectricBolt,
                        contentDescription = "Temp Control",
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = user.username,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * UserManagementModal — premium floating Material 3 modal.
 * Host-only. Shows context-aware actions based on guest's current temp control state.
 */
@Composable
private fun UserManagementModal(
    user: RoomUser,
    onDismiss: () -> Unit,
    onTransferHost: () -> Unit,
    onToggleTempControl: () -> Unit,
    onKickUser: () -> Unit,
    cardColor: Color
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // User name heading
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar mini
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = user.username.first().uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (user.hasTempControl) {
                            Text(
                                text = "Has Sudo Permissions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))

                // Transfer Host
                OutlinedButton(
                    onClick = onTransferHost,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transfer Host")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Give / Revoke Sudo
                FilledTonalButton(
                    onClick = onToggleTempControl,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ElectricBolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (user.hasTempControl) "Revoke Sudo" else "Grant Sudo"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Kick User
                Button(
                    onClick = onKickUser,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonRemove,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kick from Room")
                }
            }
        }
    }
}

fun generateQrBitmap(content: String): android.graphics.Bitmap {
    val size = 512
    val hints = java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java)
    hints[com.google.zxing.EncodeHintType.MARGIN] = 1
    val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels[y * width + x] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

@Composable
fun QrShareDialog(
    roomCode: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val roomLink = "resonix://listen/$roomCode"
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    androidx.compose.runtime.LaunchedEffect(roomLink) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            qrBitmap = generateQrBitmap(roomLink)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(340.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Share Room",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // QR Box -> Always white so black QR stands out properly!
                Card(
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                        qrBitmap?.let { bmp ->
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                
                // Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Room Code Box
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(roomCode))
                                coroutineScope.launch { snackbarHostState.showSnackbar("Room code copied") }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                              Text("Room Code", style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.8f))
                            Spacer(Modifier.height(2.dp))
                            Text("#$roomCode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Invite Link Box
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(roomLink))
                                coroutineScope.launch { snackbarHostState.showSnackbar("Invite link copied") }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Invite Link", style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.8f))
                            Spacer(Modifier.height(2.dp))
                            Text("Copy Link", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(onClick = onShare) {
                        Text("Share")
                    }
                }
            }
        }
    }
}
