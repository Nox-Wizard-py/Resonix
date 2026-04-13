package com.noxwizard.resonix.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.AppLanguageKey
import com.noxwizard.resonix.constants.ContentCountryKey
import com.noxwizard.resonix.constants.ContentLanguageKey
import com.noxwizard.resonix.constants.CountryCodeToName
import com.noxwizard.resonix.constants.HideExplicitKey
import com.noxwizard.resonix.constants.LanguageCodeToName
import com.noxwizard.resonix.constants.ProxyEnabledKey
import com.noxwizard.resonix.constants.ProxyTypeKey
import com.noxwizard.resonix.constants.ProxyUrlKey
import com.noxwizard.resonix.constants.QuickPicks
import com.noxwizard.resonix.constants.QuickPicksKey
import com.noxwizard.resonix.constants.SYSTEM_DEFAULT
import com.noxwizard.resonix.constants.TopSize
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.ui.component.EditTextPreference
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.ListPreference
import com.noxwizard.resonix.ui.component.Material3PreferenceGroup
import com.noxwizard.resonix.ui.component.PreferenceEntry
import com.noxwizard.resonix.ui.component.SwitchPreference
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberEnumPreference
import com.noxwizard.resonix.utils.rememberPreference
import com.noxwizard.resonix.utils.setAppLocale
import java.net.Proxy
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)

    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
        }

        item {
            Material3PreferenceGroup(title = stringResource(R.string.general)) {
                ListPreference(
                    title = { Text(stringResource(R.string.content_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    selectedValue = contentLanguage,
                    values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                    valueText = { LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) } },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()
                        val languageTag = locale.toLanguageTag().replace("-Hant", "")
                        YouTube.locale = YouTube.locale.copy(
                            hl = newValue.takeIf { it != SYSTEM_DEFAULT }
                                ?: locale.language.takeIf { it in LanguageCodeToName }
                                ?: languageTag.takeIf { it in LanguageCodeToName }
                                ?: "en"
                        )
                        onContentLanguageChange(newValue)
                    }
                )
                ListPreference(
                    title = { Text(stringResource(R.string.content_country)) },
                    icon = { Icon(painterResource(R.drawable.location_on), null) },
                    selectedValue = contentCountry,
                    values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                    valueText = { CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) } },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()
                        YouTube.locale = YouTube.locale.copy(
                            gl = newValue.takeIf { it != SYSTEM_DEFAULT }
                                ?: locale.country.takeIf { it in CountryCodeToName }
                                ?: "US"
                        )
                        onContentCountryChange(newValue)
                    }
                )
                SwitchPreference(
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    icon = { Icon(painterResource(R.drawable.explicit), null) },
                    checked = hideExplicit,
                    onCheckedChange = onHideExplicitChange,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Material3PreferenceGroup(title = stringResource(R.string.app_language)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.app_language)) },
                        icon = { Icon(painterResource(R.drawable.language), null) },
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    "package:${context.packageName}".toUri()
                                )
                            )
                        }
                    )
                } else {
                    ListPreference(
                        title = { Text(stringResource(R.string.app_language)) },
                        icon = { Icon(painterResource(R.drawable.language), null) },
                        selectedValue = appLanguage,
                        values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                        valueText = { LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) } },
                        onValueSelected = { langTag ->
                            val newLocale = langTag
                                .takeUnless { it == SYSTEM_DEFAULT }
                                ?.let { Locale.forLanguageTag(it) }
                                ?: Locale.getDefault()
                            onAppLanguageChange(langTag)
                            setAppLocale(context, newLocale)
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Material3PreferenceGroup(title = stringResource(R.string.proxy)) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_proxy)) },
                    icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                    checked = proxyEnabled,
                    onCheckedChange = onProxyEnabledChange,
                )
                if (proxyEnabled) {
                    Column {
                        ListPreference(
                            title = { Text(stringResource(R.string.proxy_type)) },
                            selectedValue = proxyType,
                            values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                            valueText = { it.name },
                            onValueSelected = onProxyTypeChange,
                        )
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_url)) },
                            value = proxyUrl,
                            onValueChange = onProxyUrlChange,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Material3PreferenceGroup(title = stringResource(R.string.lyrics)) {
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
            Spacer(Modifier.height(16.dp))
        }

        item {
            Material3PreferenceGroup(title = stringResource(R.string.misc)) {
                EditTextPreference(
                    title = { Text(stringResource(R.string.top_length)) },
                    icon = { Icon(painterResource(R.drawable.trending_up), null) },
                    value = lengthTop,
                    isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                    onValueChange = onLengthTopChange,
                )
                ListPreference(
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                    selectedValue = quickPicks,
                    values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN),
                    valueText = {
                        when (it) {
                            QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                            QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                        }
                    },
                    onValueSelected = onQuickPicksChange,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
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
