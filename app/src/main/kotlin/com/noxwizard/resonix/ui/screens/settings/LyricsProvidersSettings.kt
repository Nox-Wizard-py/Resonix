package com.noxwizard.resonix.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.EnableBetterLyricsKey
import com.noxwizard.resonix.constants.EnableKugouKey
import com.noxwizard.resonix.constants.EnableLrcLibKey
import com.noxwizard.resonix.constants.EnableLyricsPlusKey
import com.noxwizard.resonix.constants.EnableSimpMusicKey
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.Material3PreferenceGroup
import com.noxwizard.resonix.ui.component.SwitchPreference
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProvidersSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enableSimpMusic, onEnableSimpMusicChange) = rememberPreference(key = EnableSimpMusicKey, defaultValue = true)
    val (enableLyricsPlus, onEnableLyricsPlusChange) = rememberPreference(key = EnableLyricsPlusKey, defaultValue = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider selection") },
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
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            Material3PreferenceGroup(title = "Lyrics providers") {
                SwitchPreference(
                    title = { Text("LrcLib") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableLrclib,
                    onCheckedChange = onEnableLrclibChange,
                )
                SwitchPreference(
                    title = { Text("KuGou") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableKugou,
                    onCheckedChange = onEnableKugouChange,
                )
                SwitchPreference(
                    title = { Text("BetterLyrics") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableBetterLyrics,
                    onCheckedChange = onEnableBetterLyricsChange,
                )
                SwitchPreference(
                    title = { Text("SimpMusic") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableSimpMusic,
                    onCheckedChange = onEnableSimpMusicChange,
                )
                SwitchPreference(
                    title = { Text("LyricsPlus") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableLyricsPlus,
                    onCheckedChange = onEnableLyricsPlusChange,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
