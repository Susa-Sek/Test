package de.shortblock.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.service.BlockLog

@Composable
fun DiagnosticsScreen(
    recording: Boolean,
    entries: List<String>,
    blockLog: List<BlockLog.Entry>,
    seenPackages: List<String>,
    onToggleRecording: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // Zuerst das Protokoll: Wer hier landet, kommt meist mit der Frage „warum hat das
        // gerade zugemacht?“ — nicht, um View-IDs zu sammeln.
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.block_log_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.last_block_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    if (blockLog.isEmpty()) {
                        Text(
                            text = stringResource(R.string.block_log_empty),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        blockLog.forEach { entry ->
                            Text(
                                text = "${entry.ruleId}  ·  ${entry.detail}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diagnostics_toggle),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.diagnostics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = recording, onCheckedChange = onToggleRecording)
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.seen_packages_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.seen_packages_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    if (seenPackages.isEmpty()) {
                        Text(
                            text = stringResource(R.string.seen_packages_empty),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        seenPackages.forEach { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        item {
            TextButton(onClick = onClear) { Text(stringResource(R.string.diagnostics_clear)) }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.diagnostics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(entries) { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
