package com.noxwizard.resonix.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.ChipSortTypeKey
import com.noxwizard.resonix.constants.DarkModeKey
import com.noxwizard.resonix.constants.DefaultOpenTabKey
import com.noxwizard.resonix.constants.DynamicThemeKey
import com.noxwizard.resonix.constants.GridItemSize
import com.noxwizard.resonix.constants.GridItemsSizeKey
import com.noxwizard.resonix.constants.LibraryFilter
import com.noxwizard.resonix.constants.LyricsClickKey
import com.noxwizard.resonix.constants.LyricsScrollKey
import com.noxwizard.resonix.constants.LyricsTextPositionKey
import com.noxwizard.resonix.constants.PlayerDesignStyle
import com.noxwizard.resonix.constants.PlayerDesignStyleKey
import com.noxwizard.resonix.constants.UseNewMiniPlayerDesignKey
import com.noxwizard.resonix.constants.PlayerBackgroundStyle
import com.noxwizard.resonix.constants.PlayerBackgroundStyleKey
import com.noxwizard.resonix.constants.PureBlackKey
import com.noxwizard.resonix.constants.PlayerButtonsStyle
import com.noxwizard.resonix.constants.PlayerButtonsStyleKey
import com.noxwizard.resonix.constants.LyricsAnimationStyleKey
import com.noxwizard.resonix.constants.LyricsAnimationStyle
import com.noxwizard.resonix.constants.LyricsTextSizeKey
import com.noxwizard.resonix.constants.LyricsLineSpacingKey
import com.noxwizard.resonix.constants.SliderStyle
import com.noxwizard.resonix.constants.SliderStyleKey
import com.noxwizard.resonix.constants.FrostedGlassNavBarKey
import com.noxwizard.resonix.constants.FrostedGlassMiniPlayerKey
import com.noxwizard.resonix.constants.SlimNavBarKey
import com.noxwizard.resonix.constants.ShowLikedPlaylistKey
import com.noxwizard.resonix.constants.ShowDownloadedPlaylistKey
import com.noxwizard.resonix.constants.ShowTopPlaylistKey
import com.noxwizard.resonix.constants.ShowCachedPlaylistKey
import com.noxwizard.resonix.constants.SwipeThumbnailKey
import com.noxwizard.resonix.constants.SwipeSensitivityKey
import com.noxwizard.resonix.constants.SwipeToSongKey
import com.noxwizard.resonix.constants.HidePlayerThumbnailKey
import com.noxwizard.resonix.constants.ThumbnailCornerRadiusKey
import com.noxwizard.resonix.constants.DisableBlurKey
import com.noxwizard.resonix.constants.UseSystemFontKey
import com.noxwizard.resonix.ui.component.DefaultDialog
import com.noxwizard.resonix.ui.component.EnumListPreference
import com.noxwizard.resonix.ui.component.PreferenceEntry
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.ListPreference
import com.noxwizard.resonix.ui.component.PlayerSliderTrack
import com.noxwizard.resonix.ui.component.WaveformSlider
import com.noxwizard.resonix.ui.component.PreferenceEntry
import com.noxwizard.resonix.ui.component.PreferenceGroupTitle
import com.noxwizard.resonix.ui.component.SwitchPreference
import com.noxwizard.resonix.ui.component.ThumbnailCornerRadiusSelectorButton
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberEnumPreference
import com.noxwizard.resonix.utils.rememberPreference
import me.saket.squiggles.SquigglySlider
import kotlin.math.roundToInt
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (darkMode, onDarkModeChange) = rememberEnumPreference(
        DarkModeKey,
        defaultValue = DarkMode.AUTO
    )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (useSystemFont, onUseSystemFontChange) = rememberPreference(UseSystemFontKey, defaultValue = false)
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(
        DefaultOpenTabKey,
        defaultValue = NavigationTab.HOME
    )
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.LEFT
    )
    val (lyricsAnimation, onLyricsAnimationChange) = rememberEnumPreference<LyricsAnimationStyle>(
        key = LyricsAnimationStyleKey,
        defaultValue = LyricsAnimationStyle.APPLE
    )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)

    val (gridItemSize, onGridItemSizeChange) = rememberEnumPreference(
        GridItemsSizeKey,
        defaultValue = GridItemSize.SMALL
    )

    val (slimNav, onSlimNavChange) = rememberPreference(
        SlimNavBarKey,
        defaultValue = false
    )

    val (frostedGlassNavBar, onFrostedGlassNavBarChange) = rememberPreference(
        FrostedGlassNavBarKey,
        defaultValue = true
    )

    val (frostedGlassMiniPlayer, onFrostedGlassMiniPlayerChange) = rememberPreference(
        FrostedGlassMiniPlayerKey,
        defaultValue = true
    )

    val (swipeToSong, onSwipeToSongChange) = rememberPreference(
        SwipeToSongKey,
        defaultValue = false
    )

    val (showLikedPlaylist, onShowLikedPlaylistChange) = rememberPreference(
        ShowLikedPlaylistKey,
        defaultValue = true
    )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) = rememberPreference(
        ShowDownloadedPlaylistKey,
        defaultValue = true
    )
    val (showTopPlaylist, onShowTopPlaylistChange) = rememberPreference(
        ShowTopPlaylistKey,
        defaultValue = true
    )
    val (showCachedPlaylist, onShowCachedPlaylistChange) = rememberPreference(
        ShowCachedPlaylistKey,
        defaultValue = true
    )

    val availableBackgroundStyles = PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    val (defaultChip, onDefaultChipChange) = rememberEnumPreference(
        key = ChipSortTypeKey,
        defaultValue = LibraryFilter.LIBRARY
    )



    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.theme),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_dynamic_theme)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            checked = dynamicTheme,
            onCheckedChange = onDynamicThemeChange,
        )

        if (!dynamicTheme) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.color_palette)) },
                icon = { Icon(painterResource(R.drawable.format_paint), null) },
                onClick = { navController.navigate("settings/appearance/palette_picker") },
            )
        }

        EnumListPreference(
            title = { Text(stringResource(R.string.dark_theme)) },
            icon = { Icon(painterResource(R.drawable.dark_mode), null) },
            selectedValue = darkMode,
            onValueSelected = onDarkModeChange,
            valueText = {
                when (it) {
                    DarkMode.ON -> stringResource(R.string.dark_theme_on)
                    DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                    DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                }
            },
        )

        AnimatedVisibility(useDarkTheme) {
            SwitchPreference(
                title = { Text(stringResource(R.string.pure_black)) },
                icon = { Icon(painterResource(R.drawable.contrast), null) },
                checked = pureBlack,
                onCheckedChange = onPureBlackChange,
            )
        }

        SwitchPreference(
            title = { Text(stringResource(R.string.disable_blur)) },
            description = stringResource(R.string.disable_blur_desc),
            icon = { Icon(painterResource(R.drawable.blur_off), null) },
            checked = disableBlur,
            onCheckedChange = onDisableBlurChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.use_system_font)) },
            description = stringResource(R.string.use_system_font_desc),
            icon = { Icon(painterResource(R.drawable.text_fields), null) },
            checked = useSystemFont,
            onCheckedChange = onUseSystemFontChange,
        )



        PreferenceGroupTitle(
            title = stringResource(R.string.lyrics),
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.lyrics_text_position)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            selectedValue = lyricsPosition,
            onValueSelected = onLyricsPositionChange,
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(R.string.left)
                    LyricsPosition.CENTER -> stringResource(R.string.center)
                    LyricsPosition.RIGHT -> stringResource(R.string.right)
                }
            },
        )

        EnumListPreference(
          title = { Text(stringResource(R.string.lyrics_animation_style)) },
          icon = { Icon(painterResource(R.drawable.animation), null) },
          selectedValue = lyricsAnimation,
          onValueSelected = onLyricsAnimationChange,
          valueText = {
              when (it) {
                  LyricsAnimationStyle.NONE -> stringResource(R.string.none)
                  LyricsAnimationStyle.FADE -> stringResource(R.string.fade)
                  LyricsAnimationStyle.GLOW -> stringResource(R.string.glow)
                  LyricsAnimationStyle.SLIDE -> stringResource(R.string.slide)
                  LyricsAnimationStyle.KARAOKE -> stringResource(R.string.karaoke)
                  LyricsAnimationStyle.APPLE -> stringResource(R.string.apple_music_style)
              }
          }
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

                    Slider(
                        value = tempTextSize,
                        onValueChange = { tempTextSize = it },
                        valueRange = 16f..36f,
                        steps = 19,
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

                    Slider(
                        value = tempLineSpacing,
                        onValueChange = { tempLineSpacing = it },
                        valueRange = 1.0f..2.0f,
                        steps = 19,
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

        PreferenceGroupTitle(
            title = stringResource(R.string.misc),
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.default_open_tab)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            selectedValue = defaultOpenTab,
            onValueSelected = onDefaultOpenTabChange,
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.SEARCH -> stringResource(R.string.search)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
        )

        ListPreference(
            title = { Text(stringResource(R.string.default_lib_chips)) },
            icon = { Icon(painterResource(R.drawable.tab), null) },
            selectedValue = defaultChip,
            values = listOf(
                LibraryFilter.LIBRARY, LibraryFilter.PLAYLISTS, LibraryFilter.SONGS,
                LibraryFilter.ALBUMS, LibraryFilter.ARTISTS
            ),
            valueText = {
                when (it) {
                    LibraryFilter.SONGS -> stringResource(R.string.songs)
                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                    LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
            onValueSelected = onDefaultChipChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.swipe_song_to_add)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeToSong,
            onCheckedChange = onSwipeToSongChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.slim_navbar)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            checked = slimNav,
            onCheckedChange = onSlimNavChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.frosted_glass_navbar)) },
            description = stringResource(R.string.frosted_glass_navbar_desc),
            icon = { Icon(painterResource(R.drawable.gradient), null) },
            checked = frostedGlassNavBar,
            onCheckedChange = onFrostedGlassNavBarChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.frosted_glass_miniplayer)) },
            description = stringResource(R.string.frosted_glass_miniplayer_desc),
            icon = { Icon(painterResource(R.drawable.gradient), null) },
            checked = frostedGlassMiniPlayer,
            onCheckedChange = onFrostedGlassMiniPlayerChange
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.grid_cell_size)) },
            icon = { Icon(painterResource(R.drawable.grid_view), null) },
            selectedValue = gridItemSize,
            onValueSelected = onGridItemSizeChange,
            valueText = {
                when (it) {
                    GridItemSize.BIG -> stringResource(R.string.big)
                    GridItemSize.SMALL -> stringResource(R.string.small)
                }
            },
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.auto_playlists)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_liked_playlist)) },
            icon = { Icon(painterResource(R.drawable.favorite), null) },
            checked = showLikedPlaylist,
            onCheckedChange = onShowLikedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_downloaded_playlist)) },
            icon = { Icon(painterResource(R.drawable.offline), null) },
            checked = showDownloadedPlaylist,
            onCheckedChange = onShowDownloadedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_top_playlist)) },
            icon = { Icon(painterResource(R.drawable.trending_up), null) },
            checked = showTopPlaylist,
            onCheckedChange = onShowTopPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_cached_playlist)) },
            icon = { Icon(painterResource(R.drawable.cached), null) },
            checked = showCachedPlaylist,
            onCheckedChange = onShowCachedPlaylistChange
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
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

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}



