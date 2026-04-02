package com.noxwizard.resonix.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.LyricsRomanizeAsMainKey
import com.noxwizard.resonix.constants.LyricsRomanizeCyrillicByLineKey
import com.noxwizard.resonix.constants.LyricsRomanizeList
import com.noxwizard.resonix.constants.AITranslationEnabledKey
import com.noxwizard.resonix.constants.AITranslationServiceKey
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.Material3SettingsGroup
import com.noxwizard.resonix.ui.component.Material3SettingsItem
import com.noxwizard.resonix.ui.component.Material3PreferenceGroup
import com.noxwizard.resonix.ui.component.SwitchPreference
import com.noxwizard.resonix.ui.component.ListPreference
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberPreference

val defaultList = mutableListOf(
    "Japanese" to true,
    "Korean" to true,
    "Chinese" to true,
    "Hindi" to true,
    "Punjabi" to true,
    "Russian" to true,
    "Ukrainian" to true,
    "Serbian" to true,
    "Bulgarian" to true,
    "Belarusian" to true,
    "Kyrgyz" to true,
    "Macedonian" to true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomanizationSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (pref, prefValue) = rememberPreference(LyricsRomanizeList, "")

    val initialList = remember(pref) {
        if (pref.isEmpty()) defaultList
        else {
            val savedMap = pref.split(",").associate { entry ->
                val (lang, checked) = entry.split(":")
                lang to checked.toBoolean()
            }

            defaultList.map { (lang, defaultChecked) ->
                Pair(lang, savedMap[lang] ?: defaultChecked)
            }
        }
    }

    val states = remember(initialList) { mutableStateListOf(*initialList.toTypedArray()) }

    val parentState = when {
        states.all { it.component2() } -> ToggleableState.On
        states.none { it.component2() } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    val (lyricsRomanizeAsMain, onLyricsRomanizeAsMainChange) = rememberPreference(
        LyricsRomanizeAsMainKey,
        defaultValue = false
    )

    val (lyricsRomanizeCyrillicByLine, onLyricsRomanizeCyrillicByLineChange) = rememberPreference(
        LyricsRomanizeCyrillicByLineKey,
        defaultValue = false
    )

    // AI Translation
    val (aiTranslation, onAiTranslationChange) = rememberPreference(
        AITranslationEnabledKey,
        defaultValue = false
    )
    val (aiTranslationService, onAiTranslationServiceChange) = rememberPreference(
        AITranslationServiceKey,
        defaultValue = "Mistral"
    )

    val checkboxesList: MutableList<Material3SettingsItem> = mutableListOf()

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Material3PreferenceGroup(
            title = stringResource(R.string.options)
        ) {
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_as_main)) },
                checked = lyricsRomanizeAsMain,
                onCheckedChange = onLyricsRomanizeAsMainChange,
                icon = { Icon(painterResource(R.drawable.queue_music), null) }
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.line_by_line_option_title)) },
                checked = lyricsRomanizeCyrillicByLine,
                onCheckedChange = onLyricsRomanizeCyrillicByLineChange,
                icon = { Icon(painterResource(R.drawable.info), null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Material3PreferenceGroup(
            title = stringResource(R.string.ai_translation_title)
        ) {
            SwitchPreference(
                title = { Text(stringResource(R.string.ai_translation_enable)) },
                checked = aiTranslation,
                onCheckedChange = onAiTranslationChange,
                icon = { Icon(painterResource(R.drawable.translate), null) }
            )
            
            if (aiTranslation) {
                ListPreference(
                    title = { Text(stringResource(R.string.ai_translation_service)) },
                    icon = { Icon(painterResource(R.drawable.lucide_settings), null) },
                    selectedValue = aiTranslationService,
                    values = listOf("DeepL", "Mistral", "OpenRouter"),
                    valueText = { it },
                    onValueSelected = onAiTranslationServiceChange,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        checkboxesList += Material3SettingsItem(
            title = { Text("Play all") },
            trailingContent = {
                TriStateCheckbox(
                    state = parentState,
                    onClick = {
                        val newState = parentState != ToggleableState.On
                        states.forEachIndexed { index, (language, _) ->
                            states[index] = Pair(language, newState)
                        }
                        prefValue(states.joinToString(",") { (lang, c) -> "$lang:$c" })
                    }
                )
            },
            icon = painterResource(R.drawable.info)
        )

        states.forEachIndexed { index, (language, checked) ->
            checkboxesList += Material3SettingsItem(
                title = { Text(language) },
                trailingContent = {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            states[index] = Pair(language, isChecked)
                            prefValue(states.joinToString(",") { (lang, c) -> "$lang:$c" })
                        }
                    )
                },
                icon = painterResource(R.drawable.language)
            )
        }

        Material3SettingsGroup(
            title = stringResource(R.string.content_language),
            items = checkboxesList
        )
        
        Spacer(Modifier.height(32.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.lyrics_romanize_title)) },
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
}
