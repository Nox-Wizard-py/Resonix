package com.noxwizard.resonix.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.CustomThemeColorKey
import com.noxwizard.resonix.constants.DynamicThemeKey
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.theme.ColorSaver
import com.noxwizard.resonix.ui.theme.ThemeSeedPalette
import com.noxwizard.resonix.ui.theme.ThemeSeedPaletteCodec
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberPreference

private enum class SeedRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCreatorScreen(
    navController: NavController,
) {
    val context = LocalContext.current

    val (customThemeValue, setCustomThemeValue) = rememberPreference(
        key = CustomThemeColorKey,
        defaultValue = ThemePalettes.Default.id,
    )
    val (_, setDynamicThemeEnabled) = rememberPreference(
        key = DynamicThemeKey,
        defaultValue = true,
    )

    val seedFromPrefs = remember(customThemeValue) {
        ThemeSeedPaletteCodec.decodeFromPreference(customThemeValue)
            ?: ThemePalettes.findById(customThemeValue)?.toSeedPalette()
            ?: ThemePalettes.Default.toSeedPalette()
    }

    var themeName by rememberSaveable(customThemeValue) {
        mutableStateOf(ThemeSeedPaletteCodec.extractNameFromPreference(customThemeValue) ?: "")
    }

    var primary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.primary) }
    var secondary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.secondary) }
    var tertiary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.tertiary) }
    var neutral by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.neutral) }

    val currentPalette = ThemeSeedPalette(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        neutral = neutral,
    )

    var activeRole by rememberSaveable { mutableStateOf(SeedRole.PRIMARY) }

    fun applyThemeToPrefs() {
        setDynamicThemeEnabled(false)
        setCustomThemeValue(ThemeSeedPaletteCodec.encodeForPreference(currentPalette, themeName.takeIf { it.isNotBlank() }))
        Toast.makeText(context, context.getString(R.string.theme_applied), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.theme_creator_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            primary = ThemePalettes.Default.primary
                            secondary = ThemePalettes.Default.secondary
                            tertiary = ThemePalettes.Default.tertiary
                            neutral = ThemePalettes.Default.neutral
                            themeName = ""
                        }
                    ) {
                        Text(text = stringResource(R.string.reset))
                    }
                    TextButton(onClick = { applyThemeToPrefs() }) {
                        Text(text = stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            ThemeHeroPreview(
                palette = currentPalette,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )

            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.theme_meta_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = themeName,
                        onValueChange = { themeName = it.take(48) },
                        label = { Text(stringResource(R.string.theme_name_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { applyThemeToPrefs() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.theme_apply_button))
                    }
                }
            }

            SeedRolePicker(
                activeRole = activeRole,
                onRoleChange = { activeRole = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val activeColor by remember(activeRole, primary, secondary, tertiary, neutral) {
                derivedStateOf {
                    when (activeRole) {
                        SeedRole.PRIMARY -> primary
                        SeedRole.SECONDARY -> secondary
                        SeedRole.TERTIARY -> tertiary
                        SeedRole.NEUTRAL -> neutral
                    }
                }
            }

            SeedColorEditor(
                role = activeRole,
                color = activeColor,
                onColorChange = { newColor ->
                    when (activeRole) {
                        SeedRole.PRIMARY -> primary = newColor
                        SeedRole.SECONDARY -> secondary = newColor
                        SeedRole.TERTIARY -> tertiary = newColor
                        SeedRole.NEUTRAL -> neutral = newColor
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(96.dp))
        }
    }
}

private fun ThemePalette.toSeedPalette(): ThemeSeedPalette =
    ThemeSeedPalette(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        neutral = neutral,
    )

@Composable
private fun ThemeHeroPreview(
    palette: ThemeSeedPalette,
    modifier: Modifier = Modifier,
) {
    val animatedPrimary by animateColorAsState(palette.primary, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "previewPrimary")
    val animatedSecondary by animateColorAsState(palette.secondary, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "previewSecondary")
    val animatedTertiary by animateColorAsState(palette.tertiary, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "previewTertiary")
    val animatedNeutral by animateColorAsState(palette.neutral, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "previewNeutral")

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF1C1C1E) else animatedPrimary.copy(alpha = 0.08f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .shadow(18.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            val glow = Brush.radialGradient(
                colors = listOf(animatedPrimary.copy(alpha = 0.35f), Color.Transparent)
            )
            Box(Modifier.matchParentSize().background(glow))

            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.theme_preview),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.theme_creator_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        color = animatedPrimary,
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.palette),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(10.dp).size(20.dp),
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(listOf(animatedPrimary, animatedTertiary))
                                    )
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.theme_preview_trackline),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.theme_creator_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = animatedPrimary,
                                shadowElevation = 4.dp,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(10.dp).size(20.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                animatedPrimary,
                                animatedSecondary,
                                animatedTertiary,
                                animatedNeutral,
                            ).forEach { c ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(c)
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        color = animatedPrimary,
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.palette),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.theme_preview_primary_action),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.tune),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.theme_preview_secondary_action),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedRolePicker(
    activeRole: SeedRole,
    onRoleChange: (SeedRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.theme_seed_colors),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SeedChip(
                    label = stringResource(R.string.theme_seed_primary),
                    selected = activeRole == SeedRole.PRIMARY,
                    onClick = { onRoleChange(SeedRole.PRIMARY) },
                    modifier = Modifier.weight(1f),
                )
                SeedChip(
                    label = stringResource(R.string.theme_seed_secondary),
                    selected = activeRole == SeedRole.SECONDARY,
                    onClick = { onRoleChange(SeedRole.SECONDARY) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SeedChip(
                    label = stringResource(R.string.theme_seed_tertiary),
                    selected = activeRole == SeedRole.TERTIARY,
                    onClick = { onRoleChange(SeedRole.TERTIARY) },
                    modifier = Modifier.weight(1f),
                )
                SeedChip(
                    label = stringResource(R.string.theme_seed_neutral),
                    selected = activeRole == SeedRole.NEUTRAL,
                    onClick = { onRoleChange(SeedRole.NEUTRAL) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SeedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (selected) 6.dp else 0.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeedColorEditor(
    role: SeedRole,
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val roleLabel = when (role) {
        SeedRole.PRIMARY -> stringResource(R.string.theme_seed_primary)
        SeedRole.SECONDARY -> stringResource(R.string.theme_seed_secondary)
        SeedRole.TERTIARY -> stringResource(R.string.theme_seed_tertiary)
        SeedRole.NEUTRAL -> stringResource(R.string.theme_seed_neutral)
    }

    val r0 = ((color.toArgb() shr 16) and 0xFF)
    val g0 = ((color.toArgb() shr 8) and 0xFF)
    val b0 = (color.toArgb() and 0xFF)

    var r by rememberSaveable(role.name) { mutableStateOf(r0) }
    var g by rememberSaveable(role.name) { mutableStateOf(g0) }
    var b by rememberSaveable(role.name) { mutableStateOf(b0) }

    LaunchedEffect(role, color.toArgb()) {
        val argb = color.toArgb()
        r = (argb shr 16) and 0xFF
        g = (argb shr 8) and 0xFF
        b = argb and 0xFF
    }

    val hex = remember(color.toArgb()) { String.format("#%08X", color.toArgb()) }
    var hexInput by rememberSaveable(role.name) { mutableStateOf(hex) }
    var hexError by rememberSaveable(role.name) { mutableStateOf(false) }

    LaunchedEffect(hex) {
        if (!hexError) hexInput = hex
    }

    fun commitRgb() {
        hexError = false
        onColorChange(Color((0xFF shl 24) or (r shl 16) or (g shl 8) or b))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.theme_editor_title, roleLabel),
                style = MaterialTheme.typography.titleSmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = stringResource(R.string.theme_editor_hex), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = hex, style = MaterialTheme.typography.titleSmall)
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                clipboard.setText(AnnotatedString(hex))
                                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painter = painterResource(R.drawable.link), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(R.string.copy), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OutlinedTextField(
                value = hexInput,
                onValueChange = { next ->
                    hexInput = next.take(12)
                    val parsed = runCatching {
                        val normalized = hexInput.trim().let { if (it.startsWith("#")) it else "#$it" }
                        Color(android.graphics.Color.parseColor(normalized))
                    }.getOrNull()
                    if (parsed != null) {
                        hexError = false
                        onColorChange(parsed)
                    } else {
                        hexError = true
                    }
                },
                label = { Text(stringResource(R.string.theme_editor_hex_input)) },
                isError = hexError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            RgbSlider(
                label = "R",
                value = r,
                color = Color(0xFFE53935),
                onValueChange = { r = it; commitRgb() },
            )
            RgbSlider(
                label = "G",
                value = g,
                color = Color(0xFF43A047),
                onValueChange = { g = it; commitRgb() },
            )
            RgbSlider(
                label = "B",
                value = b,
                color = Color(0xFF1E88E5),
                onValueChange = { b = it; commitRgb() },
            )

            PresetSwatches(
                current = color,
                onPick = onColorChange,
            )
        }
    }
}

@Composable
private fun RgbSlider(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = color.copy(alpha = 0.18f),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun PresetSwatches(
    current: Color,
    onPick: (Color) -> Unit,
) {
    val swatches = remember {
        ThemePalettes.allPalettes
            .map { it.primary }
            .distinctBy { it.toArgb() }
            .take(18)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.theme_presets_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            swatches.forEach { c ->
                val selected = c.toArgb() == current.toArgb()
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shadow(if (selected) 8.dp else 2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onPick(c) }
                )
            }
        }
    }
}
