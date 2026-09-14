package de.trimbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.trimbox.app.R
import de.trimbox.app.data.SenderTally
import java.text.DateFormat
import java.util.Date

/**
 * Die Liste, auf deren Grundlage jemand entscheidet, ein paar hundert Mails wegzuräumen.
 *
 * Zwei Häkchen je Absender statt einem: Abmelden und Aufräumen sind verschiedene Dinge.
 * Bei einem Absender, den man nicht kennt, will man das eine und ausdrücklich nicht das
 * andere — eine Abmeldung bestätigt ihm ja, dass hier jemand mitliest.
 */
@Composable
fun ResultScreen(
    senders: List<SenderTally.SenderSummary>,
    mailsSeen: Int,
    days: Int,
    unsubscribeFrom: Set<String>,
    cleanUp: Set<String>,
    onToggleUnsubscribe: (String) -> Unit,
    onToggleCleanUp: (String) -> Unit,
    onExecute: () -> Unit,
    onBack: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val plan = remember(senders, unsubscribeFrom, cleanUp) {
        SenderTally.plan(senders, unsubscribeFrom, cleanUp)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.result_subtitle, senders.size, mailsSeen, days),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (senders.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Note(stringResource(R.string.result_empty))
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Note(stringResource(R.string.result_warning))
                }
            }
            items(senders, key = { it.address }) { sender ->
                SenderRow(
                    sender = sender,
                    unsubscribeSelected = sender.address in unsubscribeFrom,
                    cleanUpSelected = sender.address in cleanUp,
                    onToggleUnsubscribe = { onToggleUnsubscribe(sender.address) },
                    onToggleCleanUp = { onToggleCleanUp(sender.address) },
                )
                HorizontalDivider()
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = planText(plan),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { confirming = true },
                    enabled = plan.unsubscribeCount > 0 || plan.mailCount > 0,
                ) {
                    Text(stringResource(R.string.result_continue))
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (plan.unsubscribeCount > 0) {
                        Text(pluralStringResource(R.plurals.confirm_unsubscribe, plan.unsubscribeCount, plan.unsubscribeCount))
                    }
                    if (plan.mailCount > 0) {
                        Text(pluralStringResource(R.plurals.confirm_move, plan.mailCount, plan.mailCount))
                    }
                    Text(
                        stringResource(R.string.confirm_trash_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirming = false
                    onExecute()
                }) { Text(stringResource(R.string.confirm_go)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun planText(plan: SenderTally.Plan): String = when {
    plan.unsubscribeCount > 0 && plan.mailCount > 0 ->
        pluralStringResource(R.plurals.confirm_unsubscribe, plan.unsubscribeCount, plan.unsubscribeCount) + " " +
            pluralStringResource(R.plurals.confirm_move, plan.mailCount, plan.mailCount)

    plan.unsubscribeCount > 0 -> pluralStringResource(R.plurals.confirm_unsubscribe, plan.unsubscribeCount, plan.unsubscribeCount)
    plan.mailCount > 0 -> pluralStringResource(R.plurals.confirm_move, plan.mailCount, plan.mailCount)
    else -> stringResource(R.string.confirm_nothing)
}

@Composable
private fun SenderRow(
    sender: SenderTally.SenderSummary,
    unsubscribeSelected: Boolean,
    cleanUpSelected: Boolean,
    onToggleUnsubscribe: () -> Unit,
    onToggleCleanUp: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = sender.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sender.address,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.result_mails, sender.count, sender.count) +
                    " · " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(sender.newestMillis)),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = unsubscribeSelected,
                    onClick = onToggleUnsubscribe,
                    enabled = sender.canUnsubscribeAutomatically,
                    label = { Text(stringResource(R.string.result_unsubscribe)) },
                )
                FilterChip(
                    selected = cleanUpSelected,
                    onClick = onToggleCleanUp,
                    label = { Text(stringResource(R.string.result_cleanup)) },
                )
            }

            if (!sender.canUnsubscribeAutomatically) {
                Text(
                    text = stringResource(
                        if (sender.route is de.trimbox.app.data.UnsubscribeHeader.Route.OpenInBrowser) {
                            R.string.result_browser_only
                        } else {
                            R.string.result_no_unsubscribe
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
