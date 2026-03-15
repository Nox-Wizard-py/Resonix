package com.noxwizard.resonix.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.BuildConfig
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.CustomThemeColorKey
import com.noxwizard.resonix.constants.DynamicThemeKey
import com.noxwizard.resonix.constants.PauseListenHistoryKey
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.Material3SettingsGroup
import com.noxwizard.resonix.ui.component.Material3SettingsItem
import com.noxwizard.resonix.ui.screens.settings.ThemePalettes
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )

    val (historyTracking, onHistoryTrackingChange) = rememberPreference(
        PauseListenHistoryKey,
        defaultValue = false
    )

    val (customThemeColor, onCustomThemeColorChange) = rememberPreference(
        CustomThemeColorKey,
        defaultValue = ThemePalettes.Default.id
    )

    // These toggles are placeholders to match UX
    var volumeBoost by rememberSaveable { mutableStateOf(false) }
    var autoDownload by rememberSaveable { mutableStateOf(false) }
    
    val quickPalettes = remember {
        listOf(
            ThemePalettes.Amethyst,
            ThemePalettes.HotPink,
            ThemePalettes.OceanBlue,
            ThemePalettes.EmeraldGreen,
            ThemePalettes.GoldenHour,
            ThemePalettes.CrimsonRed
        )
    }
    
    val currentThemeNameRes = remember(customThemeColor) {
        ThemePalettes.findById(customThemeColor)?.nameResId
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        // ── Header Section ──────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.lucide_settings),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Resonix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── 1. Personalization ──────────────────────────────────────
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_personalization),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_wand_2),
                    title = { Text(stringResource(R.string.dynamic_theme)) },
                    subtitle = stringResource(R.string.dynamic_theme_subtitle),
                    trailingContent = {
                        Switch(
                            checked = dynamicTheme,
                            onCheckedChange = { onDynamicThemeChange(it) }
                        )
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_palette),
                    title = { Text(stringResource(R.string.color_palette)) },
                    subtitle = if (dynamicTheme) "Theme: Dynamic" else {
                        currentThemeNameRes?.let { "Theme: ${stringResource(it)}" } ?: "Theme: Custom"
                    },
                    disabled = dynamicTheme,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 3x2 Grid
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    quickPalettes.take(3).forEach { palette ->
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(palette.primary)
                                                .border(
                                                    width = if (customThemeColor == palette.id && !dynamicTheme) 2.dp else 0.dp,
                                                    color = if (customThemeColor == palette.id && !dynamicTheme) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable(enabled = !dynamicTheme) { onCustomThemeColorChange(palette.id) }
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    quickPalettes.drop(3).forEach { palette ->
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(palette.primary)
                                                .border(
                                                    width = if (customThemeColor == palette.id && !dynamicTheme) 2.dp else 0.dp,
                                                    color = if (customThemeColor == palette.id && !dynamicTheme) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable(enabled = !dynamicTheme) { onCustomThemeColorChange(palette.id) }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.lucide_chevron_right),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { navController.navigate("settings/appearance/palette_picker") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_monitor_smartphone),
                    title = { Text(stringResource(R.string.appearance)) },
                    subtitle = stringResource(R.string.appearance_subtitle),
                    onClick = { navController.navigate("settings/appearance") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_layout),
                    title = { Text(stringResource(R.string.player_style)) },
                    subtitle = stringResource(R.string.player_style_subtitle),
                    onClick = { navController.navigate("settings/player_style") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 2. Playback ─────────────────────────────────────────────
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_playback),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_speaker),
                    title = { Text(stringResource(R.string.player_and_audio)) },
                    subtitle = stringResource(R.string.player_audio_subtitle),
                    onClick = { navController.navigate("settings/player") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_volume_2),
                    title = { Text(stringResource(R.string.volume_normalization)) },
                    subtitle = stringResource(R.string.volume_normalization_subtitle),
                    trailingContent = {
                        Switch(
                            checked = volumeBoost,
                            onCheckedChange = { volumeBoost = it }
                        )
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_headphones),
                    title = { Text(stringResource(R.string.high_resolution_audio)) },
                    subtitle = stringResource(R.string.high_resolution_audio_subtitle),
                    onClick = { navController.navigate("settings/high_res_audio") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 3. Library & Content ────────────────────────────────────
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_library_content),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_list_music),
                    title = { Text(stringResource(R.string.library_behavior)) },
                    subtitle = stringResource(R.string.library_behavior_subtitle),
                    onClick = { navController.navigate("settings/library_behaviour") } // Existing route mapping
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_arrow_down_to_line),
                    title = { Text(stringResource(R.string.import_playlists)) },
                    subtitle = stringResource(R.string.import_playlists_subtitle),
                    onClick = { navController.navigate("settings/import_playlists") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_globe),
                    title = { Text(stringResource(R.string.content)) },
                    subtitle = stringResource(R.string.content_subtitle),
                    onClick = { navController.navigate("settings/content") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_puzzle),
                    title = { Text(stringResource(R.string.integrations)) },
                    subtitle = stringResource(R.string.integrations_subtitle),
                    onClick = { navController.navigate("settings/integration") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 4. Privacy ──────────────────────────────────────────────
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_privacy),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_history),
                    title = { Text(stringResource(R.string.listening_history)) },
                    subtitle = stringResource(R.string.listening_history_subtitle),
                    trailingContent = {
                        Switch(
                            checked = !historyTracking,
                            onCheckedChange = { onHistoryTrackingChange(!it) }
                        )
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_shield),
                    title = { Text(stringResource(R.string.permissions)) },
                    subtitle = stringResource(R.string.permissions_subtitle),
                    onClick = { navController.navigate("settings/permissions") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_lock),
                    title = { Text(stringResource(R.string.privacy_options)) },
                    subtitle = stringResource(R.string.privacy_options_subtitle),
                    onClick = { navController.navigate("settings/privacy") }
                )
            )
        )

        // ── 5. Storage ──────────────────────────────────────────────
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_storage),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_download),
                    title = { Text(stringResource(R.string.auto_download)) },
                    subtitle = stringResource(R.string.auto_download_subtitle),
                    trailingContent = {
                        Switch(
                            checked = autoDownload,
                            onCheckedChange = { autoDownload = it }
                        )
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_hard_drive),
                    title = { Text(stringResource(R.string.cache_management)) },
                    subtitle = stringResource(R.string.cache_management_subtitle),
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "1.2 GB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.lucide_chevron_right),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_database_backup),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    subtitle = stringResource(R.string.backup_restore_subtitle),
                    onClick = { navController.navigate("settings/backup_restore") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 6. System ───────────────────────────────────────────────
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_system),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.lucide_refresh_cw),
                        title = { Text(stringResource(R.string.check_for_updates)) },
                        subtitle = if (latestVersionName != BuildConfig.VERSION_NAME) {
                            stringResource(R.string.new_version_available)
                        } else {
                            stringResource(R.string.you_are_on_latest)
                        },
                        showBadge = latestVersionName != BuildConfig.VERSION_NAME,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (latestVersionName != BuildConfig.VERSION_NAME) "Update available" else "Up to date",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.lucide_chevron_right),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { navController.navigate("settings/update") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.lucide_sparkles),
                        title = { Text(stringResource(R.string.experimental_features)) },
                        subtitle = stringResource(R.string.experimental_features_subtitle),
                        onClick = { navController.navigate("settings/misc") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.lucide_info),
                        title = { Text(stringResource(R.string.about_resonix)) },
                        subtitle = stringResource(R.string.about_resonix_subtitle),
                        onClick = { navController.navigate("settings/about") }
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
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
        },
        scrollBehavior = scrollBehavior
    )
}
