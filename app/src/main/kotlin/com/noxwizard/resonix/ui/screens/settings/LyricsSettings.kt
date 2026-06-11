package com.noxwizard.resonix.ui.screens.settings

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.ui.component.IconButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.ui.component.Material3PreferenceGroup

// imports from AppearanceSettings.kt for Lyrics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt

import com.noxwizard.resonix.ui.component.DefaultDialog
import com.noxwizard.resonix.ui.component.PreferenceEntry
import com.noxwizard.resonix.ui.component.SwitchPreference
import com.noxwizard.resonix.ui.component.EnumListPreference
import com.noxwizard.resonix.constants.LyricsStyle
import com.noxwizard.resonix.constants.LyricsStyleKey
import com.noxwizard.resonix.constants.LyricsClickKey
import com.noxwizard.resonix.constants.LyricsScrollKey
import com.noxwizard.resonix.constants.LyricsTextSizeKey
import com.noxwizard.resonix.constants.LyricsLineSpacingKey
import com.noxwizard.resonix.utils.rememberEnumPreference
import com.noxwizard.resonix.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsStyle, onLyricsStyleChange) = rememberEnumPreference(LyricsStyleKey, defaultValue = LyricsStyle.FLOW)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            ).height(8.dp)
        )

        Material3PreferenceGroup(
            title = stringResource(R.string.lyrics),
        ) {

        EnumListPreference(
            title = { Text("Lyrics Style") },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            selectedValue = lyricsStyle,
            onValueSelected = onLyricsStyleChange,
            valueText = {
                when (it) {
                    LyricsStyle.FLOW   -> "Flow"
                    LyricsStyle.VELVET -> "Velvet"
                    LyricsStyle.HALO   -> "Halo"
                    LyricsStyle.PRISM -> "Prism"
                }
            },
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_click_change)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyricsClick,
            onCheckedChange = onLyricsClickChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyricsScroll,
            onCheckedChange = onLyricsScrollChange,
        )

        var showLyricsTextSizeDialog by rememberSaveable { mutableStateOf(false) }
        
        if (showLyricsTextSizeDialog) {
            var tempTextSize by remember { mutableFloatStateOf(lyricsTextSize) }
            
            DefaultDialog(
                onDismiss = { 
                    tempTextSize = lyricsTextSize
                    showLyricsTextSizeDialog = false 
                },
                buttons = {
                    TextButton(
                        onClick = { 
                            tempTextSize = 24f
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    TextButton(
                        onClick = { 
                            tempTextSize = lyricsTextSize
                            showLyricsTextSizeDialog = false 
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = { 
                            onLyricsTextSizeChange(tempTextSize)
                            showLyricsTextSizeDialog = false 
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_text_size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "${tempTextSize.roundToInt()} sp",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    com.noxwizard.resonix.ui.component.VolumeSlider(
                        progressProvider = { (tempTextSize - 16f) / 20f },
                        onProgressChange = { fraction -> tempTextSize = 16f + (fraction * 20f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        PreferenceEntry(
            title = { Text(stringResource(R.string.lyrics_text_size)) },
            description = "${lyricsTextSize.roundToInt()} sp",
            icon = { Icon(painterResource(R.drawable.text_fields), null) },
            onClick = { showLyricsTextSizeDialog = true }
        )
        
        var showLyricsLineSpacingDialog by rememberSaveable { mutableStateOf(false) }
        
        if (showLyricsLineSpacingDialog) {
            var tempLineSpacing by remember { mutableFloatStateOf(lyricsLineSpacing) }
            
            DefaultDialog(
                onDismiss = { 
                    tempLineSpacing = lyricsLineSpacing
                    showLyricsLineSpacingDialog = false 
                },
                buttons = {
                    TextButton(
                        onClick = { 
                            tempLineSpacing = 1.3f
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    TextButton(
                        onClick = { 
                            tempLineSpacing = lyricsLineSpacing
                            showLyricsLineSpacingDialog = false 
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = { 
                            onLyricsLineSpacingChange(tempLineSpacing)
                            showLyricsLineSpacingDialog = false 
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_line_spacing),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "${String.format("%.1f", tempLineSpacing)}x",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    com.noxwizard.resonix.ui.component.VolumeSlider(
                        progressProvider = { tempLineSpacing - 1.0f },
                        onProgressChange = { fraction -> tempLineSpacing = 1.0f + fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        PreferenceEntry(
            title = { Text(stringResource(R.string.lyrics_line_spacing)) },
            description = "${String.format("%.1f", lyricsLineSpacing)}x",
            icon = { Icon(painterResource(R.drawable.text_fields), null) },
            onClick = { showLyricsLineSpacingDialog = true }
        )

        }
        
        Material3PreferenceGroup(
            title = "Lyrics Provider and AI",
        ) {
            PreferenceEntry(
                title = { Text("Provider selection") },
                description = "Choose which lyrics providers are enabled",
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                onClick = { navController.navigate("settings/lyrics_providers") }
            )
            PreferenceEntry(
                title = { Text("Lyrics provider priority") },
                description = "Drag to reorder providers by preference. Higher position means higher priority",
                icon = { Icon(painterResource(R.drawable.drag_handle), null) },
                onClick = { navController.navigate("settings/lyrics_provider_priority") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.lyrics_romanization_title)) },
                description = stringResource(R.string.lyrics_romanization_desc),
                icon = { Icon(painterResource(R.drawable.translate), null) },
                onClick = { navController.navigate("settings/lyrics_romanization") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                description = stringResource(R.string.ai_lyrics_translation_desc),
                icon = { Icon(painterResource(R.drawable.translate), null) },
                onClick = { navController.navigate("settings/ai_lyrics_translation") }
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.lyrics)) },
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
