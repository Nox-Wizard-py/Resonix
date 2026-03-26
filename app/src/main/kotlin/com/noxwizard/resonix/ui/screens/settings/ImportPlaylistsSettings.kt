package com.noxwizard.resonix.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.PreferenceEntry
import com.noxwizard.resonix.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistsSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val m3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            navController.navigate("playlist_import?uri=${Uri.encode(uri.toString())}&type=m3u")
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            navController.navigate("playlist_import?uri=${Uri.encode(uri.toString())}&type=csv")
        }
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

        PreferenceEntry(
            title = { Text(stringResource(R.string.import_other_sources)) },
            icon = { Icon(painterResource(R.drawable.playlist_add), null) },
            onClick = { navController.navigate("playlist_import") }
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.import_online)) },
            icon = { Icon(painterResource(R.drawable.queue_music), null) },
            onClick = {
                m3uLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "*/*"))
            }
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.import_csv)) },
            icon = { Icon(painterResource(R.drawable.queue_music), null) },
            onClick = {
                csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
            }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.import_playlists)) },
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
        }
    )
}
