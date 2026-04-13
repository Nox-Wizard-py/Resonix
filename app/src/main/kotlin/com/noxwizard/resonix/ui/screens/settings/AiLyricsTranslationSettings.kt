package com.noxwizard.resonix.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.AiProviderKey
import com.noxwizard.resonix.constants.AiSystemPromptKey
import com.noxwizard.resonix.constants.DEFAULT_AI_SYSTEM_PROMPT
import com.noxwizard.resonix.constants.LanguageCodeToName
import com.noxwizard.resonix.constants.OpenRouterApiKey
import com.noxwizard.resonix.constants.OpenRouterBaseUrlKey
import com.noxwizard.resonix.constants.OpenRouterModelKey
import com.noxwizard.resonix.constants.TranslateLanguageKey
import com.noxwizard.resonix.constants.TranslateModeKey
import com.noxwizard.resonix.ui.component.Material3SettingsGroup
import com.noxwizard.resonix.ui.component.Material3SettingsItem
import com.noxwizard.resonix.ui.component.TextFieldDialog
import com.noxwizard.resonix.utils.rememberPreference
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLyricsTranslationSettings(navController: NavController) {
    var aiProvider by rememberPreference(AiProviderKey, "OpenRouter")
    var openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    var openRouterBaseUrl by rememberPreference(OpenRouterBaseUrlKey, "https://openrouter.ai/api/v1/chat/completions")
    var openRouterModel by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")
    var translateLanguage by rememberPreference(TranslateLanguageKey, "en")
    var translateMode by rememberPreference(TranslateModeKey, "Literal")
    var aiSystemPrompt by rememberPreference(AiSystemPromptKey, "")

    val aiProviders = listOf("OpenRouter", "Custom")

    val providerHelpText = mapOf(
        "OpenRouter" to stringResource(R.string.ai_provider_openrouter_help),
        "Custom" to "",
    )

    val commonModels = listOf(
        "google/gemini-2.5-flash-lite",
        "google/gemini-2.5-flash",
        "x-ai/grok-4.1-fast",
        "deepseek/deepseek-v3.1-terminus:exacto",
        "openai/gpt-4o-mini",
        "meta-llama/llama-4-scout",
        "openai/gpt-5-nano",
        "openai/gpt-oss-120b",
        "google/gemini-3-flash-preview",
    )

    var showProviderDialog by rememberSaveable { mutableStateOf(false) }
    var showProviderHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showTranslateModeDialog by rememberSaveable { mutableStateOf(false) }
    var showTranslateModeHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showBaseUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showModelDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomModelInput by rememberSaveable { mutableStateOf(false) }
    var showSystemPromptDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderHelpDialog) {
        AlertDialog(
            onDismissRequest = { showProviderHelpDialog = false },
            confirmButton = {
                TextButton(onClick = { showProviderHelpDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            icon = { Icon(painterResource(R.drawable.info), null) },
            title = { Text(stringResource(R.string.ai_provider_help)) },
            text = {
                Column {
                    providerHelpText.forEach { (provider, help) ->
                        if (help.isNotEmpty()) {
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val annotatedString = buildAnnotatedString {
                                append("$provider: ")
                                val urlRegex = "https?://[^\\s]+".toRegex()
                                val match = urlRegex.find(help)
                                if (match != null) {
                                    val url = match.value
                                    val beforeUrl = help.substring(0, match.range.first)
                                    val afterUrl = help.substring(match.range.last + 1)
                                    append(beforeUrl)
                                    val linkStart = length
                                    append(url)
                                    val linkEnd = length
                                    append(afterUrl)
                                    addLink(
                                        LinkAnnotation.Url(
                                            url = url,
                                            styles = TextLinkStyles(
                                                style = SpanStyle(
                                                    color = primaryColor,
                                                    textDecoration = TextDecoration.Underline,
                                                ),
                                            ),
                                        ),
                                        start = linkStart,
                                        end = linkEnd,
                                    )
                                } else {
                                    append(help)
                                }
                            }
                            Text(
                                text = annotatedString,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            },
        )
    }

    if (showTranslateModeHelpDialog) {
        AlertDialog(
            onDismissRequest = { showTranslateModeHelpDialog = false },
            confirmButton = {
                TextButton(onClick = { showTranslateModeHelpDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            icon = { Icon(painterResource(R.drawable.info), null) },
            title = { Text(stringResource(R.string.ai_translation_mode)) },
            text = {
                Column {
                    Text(
                        text = "${stringResource(R.string.ai_translation_literal)}:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.ai_translation_literal_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text(
                        text = "${stringResource(R.string.ai_translation_transcribed)}:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.ai_translation_transcribed_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
    }

    if (showProviderDialog) {
        EnumDialog(
            onDismiss = { showProviderDialog = false },
            onSelect = {
                aiProvider = it
                if (it != "Custom") {
                    openRouterBaseUrl = "https://openrouter.ai/api/v1/chat/completions"
                } else {
                    openRouterBaseUrl = ""
                }
                openRouterModel = "google/gemini-2.5-flash-lite"
                showProviderDialog = false
            },
            title = stringResource(R.string.ai_provider),
            current = aiProvider,
            values = aiProviders,
            valueText = { value: String -> value },
        )
    }

    if (showTranslateModeDialog) {
        EnumDialog(
            onDismiss = { showTranslateModeDialog = false },
            onSelect = {
                translateMode = it
                showTranslateModeDialog = false
            },
            title = stringResource(R.string.ai_translation_mode),
            current = translateMode,
            values = listOf("Literal", "Transcribed"),
            valueText = { value: String ->
                when (value) {
                    "Literal" -> stringResource(R.string.ai_translation_literal)
                    "Transcribed" -> stringResource(R.string.ai_translation_transcribed)
                    else -> value
                }
            },
        )
    }

    if (showLanguageDialog) {
        EnumDialog(
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                translateLanguage = it
                showLanguageDialog = false
            },
            title = stringResource(R.string.ai_target_language),
            current = translateLanguage,
            values = LanguageCodeToName.keys.sortedBy { LanguageCodeToName[it] },
            valueText = { value: String -> LanguageCodeToName[value] ?: value },
        )
    }

    if (showApiKeyDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_api_key)) },
            icon = { Icon(painterResource(R.drawable.lucide_lock), null) },
            initialTextFieldValue = TextFieldValue(text = openRouterApiKey),
            onDone = { value: String ->
                openRouterApiKey = value
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

    if (showBaseUrlDialog && aiProvider == "Custom") {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_base_url)) },
            icon = { Icon(painterResource(R.drawable.lucide_globe), null) },
            initialTextFieldValue = TextFieldValue(text = openRouterBaseUrl),
            onDone = { value: String ->
                openRouterBaseUrl = value
                showBaseUrlDialog = false
            },
            onDismiss = { showBaseUrlDialog = false },
        )
    }

    if (showModelDialog) {
        EnumDialog(
            onDismiss = { showModelDialog = false },
            onSelect = {
                if (it == "custom_input") {
                    showCustomModelInput = true
                    showModelDialog = false
                } else {
                    openRouterModel = it
                    showModelDialog = false
                }
            },
            title = stringResource(R.string.ai_model),
            current = if (openRouterModel in commonModels) openRouterModel else "custom_input",
            values = commonModels + "custom_input",
            valueText = { value: String ->
                if (value == "custom_input") "Custom" else value
            },
        )
    }

    if (showCustomModelInput) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_model)) },
            icon = { Icon(painterResource(R.drawable.lucide_wand_2), null) },
            initialTextFieldValue = TextFieldValue(text = openRouterModel),
            onDone = { value: String ->
                openRouterModel = value
                showCustomModelInput = false
            },
            onDismiss = { showCustomModelInput = false },
        )
    }

    if (showSystemPromptDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_system_prompt)) },
            icon = { Icon(painterResource(R.drawable.edit), null) },
            initialTextFieldValue = TextFieldValue(text = aiSystemPrompt.ifBlank { DEFAULT_AI_SYSTEM_PROMPT }),
            singleLine = false,
            maxLines = 12,
            isInputValid = { true },
            onDone = { value: String ->
                aiSystemPrompt = if (value.isBlank() || value == DEFAULT_AI_SYSTEM_PROMPT) "" else value
                showSystemPromptDialog = false
            },
            onDismiss = { showSystemPromptDialog = false },
            extraContent = {
                if (aiSystemPrompt.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                aiSystemPrompt = ""
                                showSystemPromptDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.ai_system_prompt_reset))
                        }
                    }
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.ai_provider),
            items = listOfNotNull(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_globe),
                    title = { Text(stringResource(R.string.ai_provider)) },
                    description = { Text(aiProvider) },
                    onClick = { showProviderDialog = true },
                    trailingContent = {
                        IconButton(onClick = { showProviderHelpDialog = true }) {
                            Icon(
                                painterResource(R.drawable.info),
                                contentDescription = stringResource(R.string.ai_provider_help),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                ),
                if (aiProvider == "Custom") {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.lucide_globe),
                        title = { Text(stringResource(R.string.ai_base_url)) },
                        description = { Text(openRouterBaseUrl.ifBlank { "Not set" }) },
                        onClick = { showBaseUrlDialog = true },
                    )
                } else null,
            ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.ai_setup_guide),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_lock),
                    title = { Text(stringResource(R.string.ai_api_key)) },
                    description = {
                        Text(
                            if (openRouterApiKey.isNotEmpty()) {
                                "•".repeat(minOf(openRouterApiKey.length, 8))
                            } else {
                                "Not set"
                            },
                        )
                    },
                    onClick = { showApiKeyDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_wand_2),
                    title = { Text(stringResource(R.string.ai_model)) },
                    description = { Text(openRouterModel.ifBlank { "Not set" }) },
                    onClick = { showModelDialog = true },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.ai_translation_mode),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.ai_translation_mode)) },
                    description = {
                        Text(
                            when (translateMode) {
                                "Literal" -> stringResource(R.string.ai_translation_literal)
                                "Transcribed" -> stringResource(R.string.ai_translation_transcribed)
                                else -> translateMode
                            },
                        )
                    },
                    onClick = { showTranslateModeDialog = true },
                    trailingContent = {
                        IconButton(onClick = { showTranslateModeHelpDialog = true }) {
                            Icon(
                                painterResource(R.drawable.info),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_wand_2),
                    title = { Text(stringResource(R.string.ai_system_prompt)) },
                    description = {
                        Text(
                            if (aiSystemPrompt.isNotBlank()) {
                                aiSystemPrompt.take(60).let {
                                    if (aiSystemPrompt.length > 60) "$it…" else it
                                }
                            } else {
                                stringResource(R.string.ai_system_prompt_default)
                            },
                        )
                    },
                    onClick = { showSystemPromptDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lucide_globe),
                    title = { Text(stringResource(R.string.ai_target_language)) },
                    description = { Text(LanguageCodeToName[translateLanguage] ?: translateLanguage) },
                    onClick = { showLanguageDialog = true },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.ai_lyrics_translation)) },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
fun <T> EnumDialog(
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    title: String,
    current: T,
    values: List<T>,
    valueText: @Composable (T) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                values.forEach { value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = null
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(valueText(value))
                    }
                }
            }
        }
    )
}
