package com.noxwizard.resonix.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.SwitchPreference
import com.noxwizard.resonix.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    // ── Permission state management ──
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
    val microphonePermission = Manifest.permission.RECORD_AUDIO

    fun checkPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    var hasAudioPerm by remember { mutableStateOf(checkPerm(audioPermission)) }
    var hasNotifPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) checkPerm(notificationPermission)
            else true
        )
    }
    var hasMicPerm by remember { mutableStateOf(checkPerm(microphonePermission)) }

    // Re-check permissions when returning from Android Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPerm = checkPerm(audioPermission)
                hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkPerm(notificationPermission)
                } else {
                    true
                }
                hasMicPerm = checkPerm(microphonePermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission launchers
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPerm = granted }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPerm = granted }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPerm = granted }

    // Rationale dialog states
    var showAudioRationale by remember { mutableStateOf(false) }
    var showNotifRationale by remember { mutableStateOf(false) }
    var showMicRationale by remember { mutableStateOf(false) }

    // Helper to open app detail settings (for revoking)
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            context.startActivity(intent)
        }
    }

    // ── Rationale Dialogs ──
    if (showAudioRationale) {
        AlertDialog(
            onDismissRequest = { showAudioRationale = false },
            title = { Text(stringResource(R.string.perm_audio_title)) },
            text = { Text(stringResource(R.string.perm_audio_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showAudioRationale = false
                    audioPermLauncher.launch(audioPermission)
                }) { Text(stringResource(R.string.perm_dialog_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showAudioRationale = false }) {
                    Text(stringResource(R.string.perm_dialog_deny))
                }
            }
        )
    }
    if (showNotifRationale) {
        AlertDialog(
            onDismissRequest = { showNotifRationale = false },
            title = { Text(stringResource(R.string.perm_notifications_title)) },
            text = { Text(stringResource(R.string.perm_notifications_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotifRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermLauncher.launch(notificationPermission)
                    }
                }) { Text(stringResource(R.string.perm_dialog_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotifRationale = false }) {
                    Text(stringResource(R.string.perm_dialog_deny))
                }
            }
        )
    }
    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            title = { Text(stringResource(R.string.perm_microphone_title)) },
            text = { Text(stringResource(R.string.perm_microphone_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showMicRationale = false
                    micPermLauncher.launch(microphonePermission)
                }) { Text(stringResource(R.string.perm_dialog_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) {
                    Text(stringResource(R.string.perm_dialog_deny))
                }
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.perm_audio_title)) },
            description = stringResource(R.string.perm_audio_subtitle),
            icon = { Icon(painterResource(R.drawable.music_note), null) },
            checked = hasAudioPerm,
            onCheckedChange = { enabled ->
                if (enabled) showAudioRationale = true
                else openAppSettings()
            }
        )
        
        SwitchPreference(
            title = { Text(stringResource(R.string.perm_notifications_title)) },
            description = stringResource(R.string.perm_notifications_subtitle),
            icon = { Icon(painterResource(R.drawable.notifications_unread), null) },
            checked = hasNotifPerm,
            onCheckedChange = { enabled ->
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        showNotifRationale = true
                    }
                } else {
                    openAppSettings()
                }
            }
        )
        
        SwitchPreference(
            title = { Text(stringResource(R.string.perm_microphone_title)) },
            description = stringResource(R.string.perm_microphone_subtitle),
            icon = { Icon(painterResource(R.drawable.mic), null) },
            checked = hasMicPerm,
            onCheckedChange = { enabled ->
                if (enabled) showMicRationale = true
                else openAppSettings()
            }
        )
    }
    
    TopAppBar(
        title = { Text(stringResource(R.string.permissions)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
