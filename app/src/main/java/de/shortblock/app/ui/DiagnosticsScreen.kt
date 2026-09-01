package de.shortblock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.service.BlockLog
import de.shortblock.app.ui.components.InfoCard
import de.shortblock.app.ui.components.SectionHeader
import de.shortblock.app.ui.components.SettingRow

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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Zuerst das Protokoll: Wer hier landet, kommt meist mit der Frage „warum hat das
        // gerade zugemacht?“ — nicht, um View-IDs zu sammeln.
        item {
            SectionHeader(
                title = stringResource(R.string.block_log_title),
                dot = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            InfoCard {
                Text(
                    text = stringResource(R.string.last_block_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (blockLog.isEmpty()) {
                    Text(
                        text = stringResource(R.string.block_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    blockLog.forEach { entry ->
                        CodeLine("${entry.ruleId}  ·  ${entry.detail}")
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.diagnostics_title),
                dot = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoCard {
                SettingRow(
                    title = stringResource(R.string.diagnostics_toggle),
                    description = stringResource(R.string.diagnostics_hint),
                    checked = recording,
                    onCheckedChange = onToggleRecording,
                )
            }
        }

        item {
            InfoCard {
                Text(
                    text = stringResource(R.string.seen_packages_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.seen_packages_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                if (seenPackages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.seen_packages_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    seenPackages.forEach { CodeLine(it) }
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            items(entries) { entry -> CodeLine(entry, modifier = Modifier.padding(horizontal = 4.dp)) }
        }

        item { Text("", modifier = Modifier.padding(bottom = 12.dp)) }
    }
}

/**
 * Eine Protokollzeile.
 *
 * Waagerecht scrollbar statt abgeschnitten: Eine gekürzte Regel-ID ist als Fehlermeldung
 * wertlos — genau der lange Teil hinten sagt, an welchem Knoten die Regel gegriffen hat.
 */
@Composable
private fun CodeLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
