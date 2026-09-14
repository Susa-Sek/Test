package de.shortblock.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.system.SystemSettings
import de.shortblock.app.service.ServiceHealth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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

        // Der Zustand steht direkt hinter dem Protokoll: Wer morgens nachsieht, warum nichts
        // geblockt hat, findet hier die Antwort — war der Dienst eingeschlafen und wurde
        // geweckt, oder fehlt die Akku-Ausnahme und der Prozess wurde nachts abgeräumt?
        item { ServiceStateCard() }

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

        // Der Grund, warum es diesen Bildschirm gibt — deshalb steht er direkt unter dem
        // Schalter. Bis v0.11.2 standen die Kennungen ganz unten, unterhalb von „Leeren“ und
        // ohne Überschrift: Das Einzige, wofür der Bildschirm da ist, war das am schlechtesten
        // auffindbare Element darauf, und passte auf keinen Screenshot.
        item {
            InfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.recorded_ids_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (entries.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.recorded_ids_count, entries.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diagnostics_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    CopyButton(entries)
                }
            }
        }

        // Die Zeilen bewusst als eigene `items` statt in der Karte darüber: Der Puffer fasst
        // 400 Einträge, und die alle in einem `item` zu komponieren hebt genau die Faulheit
        // auf, für die es die LazyColumn gibt.
        items(entries) { entry -> CodeLine(entry, modifier = Modifier.padding(horizontal = 4.dp)) }

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

        item { Text("", modifier = Modifier.padding(bottom = 12.dp)) }
    }
}

/**
 * Legt die aufgezeichneten Kennungen als Text in die Zwischenablage.
 *
 * Der Grund ist sehr konkret: Ein Screenshot dieser Liste ist unten abgeschnitten und die
 * Kennungen darauf sind nicht kopierbar. Als Text lassen sie sich weitergeben. Bleibt auf dem
 * Gerät — die App hat keine Internet-Berechtigung und soll keine bekommen.
 */
@Composable
private fun CopyButton(entries: List<String>) {
    val context = LocalContext.current
    TextButton(
        onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                ClipData.newPlainText("ShortBlock", entries.joinToString("\n")),
            )
            Toast.makeText(context, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(stringResource(R.string.diagnostics_copy))
    }
}

/**
 * Eine Protokollzeile.
 *
 * Waagerecht scrollbar statt abgeschnitten: Eine gekürzte Regel-ID ist als Fehlermeldung
 * wertlos — genau der lange Teil hinten sagt, an welchem Knoten die Regel gegriffen hat.
 */
@Composable
private fun ServiceStateCard() {
    val context = LocalContext.current
    val health by ServiceHealth.state.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val batteryExempt = remember { SystemSettings.isIgnoringBatteryOptimizations(context) }

    SectionHeader(
        title = stringResource(R.string.health_title),
        dot = MaterialTheme.colorScheme.secondary,
    )
    InfoCard {
        CodeLine(stringResource(R.string.health_last_event, ago(now, health.lastEventAtMs)))
        CodeLine(stringResource(R.string.health_last_repair, ago(now, health.lastRefreshAtMs)))
        Text(
            text = stringResource(
                if (batteryExempt) R.string.health_battery_ok else R.string.health_battery_missing,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (batteryExempt) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** „vor 12 Min." oder „nie" — mehr Genauigkeit braucht hier niemand. */
@Composable
private fun ago(nowMs: Long, thenMs: Long): String =
    if (thenMs <= 0L || nowMs < thenMs) {
        stringResource(R.string.health_never)
    } else {
        stringResource(R.string.health_minutes_ago, (nowMs - thenMs) / 60_000)
    }

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
