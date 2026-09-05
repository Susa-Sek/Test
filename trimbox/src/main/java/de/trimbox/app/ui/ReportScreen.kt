package de.trimbox.app.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.trimbox.app.R
import de.trimbox.app.data.TrimRepository

/**
 * Was tatsächlich passiert ist — auch das, was nicht geklappt hat. Ein Bericht, der nur
 * Erfolge zeigt, ist bei einer App, die fremde Postfächer anfasst, keiner.
 */
@Composable
fun ReportScreen(
    report: TrimRepository.Report,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall)

        if (report.unsubscribed > 0) {
            Text(pluralStringResource(R.plurals.report_unsubscribed, report.unsubscribed, report.unsubscribed))
        }
        if (report.moved > 0) {
            Text(pluralStringResource(R.plurals.report_moved, report.moved, report.moved))
        }
        if (report.failed > 0) {
            Text(pluralStringResource(R.plurals.report_failed, report.failed, report.failed))
        }
        if (report.trashMissing) {
            Note(stringResource(R.string.report_no_trash), warning = true)
        }
        if (report.markedOnly) {
            Note(stringResource(R.string.report_marked_only), warning = true)
        }

        if (report.browser.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.report_browser_pending, report.browser.size, report.browser.size))
            report.browser.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = task.sender,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        // Scheitert, wenn kein Browser da ist — kein Grund abzustürzen.
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, task.url.toUri()))
                        }
                    }) { Text(stringResource(R.string.report_open)) }
                }
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.done))
        }
    }
}
