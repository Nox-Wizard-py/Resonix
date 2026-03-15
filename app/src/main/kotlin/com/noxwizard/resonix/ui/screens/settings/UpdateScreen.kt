package com.noxwizard.resonix.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.noxwizard.resonix.BuildConfig
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.EnableUpdateNotificationKey
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.UpdateNotificationManager
import com.noxwizard.resonix.utils.Updater
import com.noxwizard.resonix.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var localLatestVersion by remember { mutableStateOf(latestVersionName) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }

    val (enableNotifications, onEnableNotificationsChange) = rememberPreference(
        EnableUpdateNotificationKey,
        defaultValue = false,
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onEnableNotificationsChange(true)
            UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
        }
    }

    val hasUpdate = localLatestVersion != BuildConfig.VERSION_NAME
        && localLatestVersion.isNotBlank()

    LaunchedEffect(Unit) {
        Updater.getLatestReleaseNotes().onSuccess {
            releaseNotes = it
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Version info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.current_version),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )

                if (hasUpdate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${stringResource(R.string.latest_version)}: $localLatestVersion",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                checkResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Check for updates button
        Button(
            onClick = {
                isChecking = true
                checkResult = null
                coroutineScope.launch {
                    Updater.getLatestVersionName().onSuccess { version ->
                        localLatestVersion = version
                        checkResult = if (version == BuildConfig.VERSION_NAME) {
                            context.getString(R.string.up_to_date)
                        } else {
                            "${context.getString(R.string.latest_version)}: $version"
                        }
                    }.onFailure {
                        checkResult = it.message ?: "Check failed"
                    }
                    isChecking = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isChecking,
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.checking_for_updates))
            } else {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.check_for_updates))
            }
        }

        if (hasUpdate) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val url = Updater.getLatestDownloadUrl()
                    if (url.isNotBlank()) {
                        try {
                            uriHandler.openUri(url)
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.download))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Background notifications toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.enable_update_notifications),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.enable_update_notifications_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = enableNotifications,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val permission = Manifest.permission.POST_NOTIFICATIONS
                                if (ContextCompat.checkSelfPermission(context, permission)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    onEnableNotificationsChange(true)
                                    UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            } else {
                                onEnableNotificationsChange(true)
                                UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
                            }
                        } else {
                            onEnableNotificationsChange(false)
                            UpdateNotificationManager.cancelPeriodicUpdateCheck(context)
                        }
                    },
                )
            }
        }

        // Release notes
        if (!releaseNotes.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.release_notes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val lines = releaseNotes!!.lines()
                    lines.forEach { line ->
                        when {
                            line.startsWith("# ") -> Text(
                                line.removePrefix("# ").trim(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            line.startsWith("## ") -> Text(
                                line.removePrefix("## ").trim(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            line.startsWith("### ") -> Text(
                                line.removePrefix("### ").trim(),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            line.startsWith("- ") -> Row {
                                Text("• ", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    line.removePrefix("- ").trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            line.isNotBlank() -> Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (line.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.updates)) },
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
        },
        scrollBehavior = scrollBehavior,
    )
}
