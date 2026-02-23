package com.noxwizard.resonix.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noxwizard.resonix.R
import com.noxwizard.resonix.utils.Updater

@Composable
fun UpdateSheet(
    localLatestVersionName: String,
    releaseNotesState: MutableState<String?>,
    uriHandler: UriHandler
) {
    Column {
        Text(
            text = stringResource(R.string.new_update_available),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {},
            shape = CircleShape,
            contentPadding = PaddingValues(
                horizontal = 5.dp,
                vertical = 5.dp
            )
        ) {
            Text(text = localLatestVersionName, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            val notes = releaseNotesState.value
            if (notes != null && notes.isNotBlank()) {
                val lines = notes.lines()
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    lines.forEach { line ->
                        when {
                            line.startsWith("# ") -> Text(
                                line.removePrefix("# ").trim(),
                                style = MaterialTheme.typography.titleLarge
                            )

                            line.startsWith("## ") -> Text(
                                line.removePrefix("## ").trim(),
                                style = MaterialTheme.typography.titleMedium
                            )

                            line.startsWith("### ") -> Text(
                                line.removePrefix("### ").trim(),
                                style = MaterialTheme.typography.titleSmall
                            )

                            line.startsWith("- ") -> Row {
                                Text("• ", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    line.removePrefix("- ").trim(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            else -> Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.release_notes_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                try {
                    uriHandler.openUri(Updater.getLatestDownloadUrl())
                } catch (_: Exception) {
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.update_text))
        }
    }
}
