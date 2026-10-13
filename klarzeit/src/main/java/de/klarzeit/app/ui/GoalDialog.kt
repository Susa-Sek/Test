package de.klarzeit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.klarzeit.app.R
import de.klarzeit.app.data.TimeFormat

private const val QUARTER_HOUR = 15 * 60 * 1000L
private const val MAX_STEPS = 24f

/**
 * Das Tagesziel gehört in einen Dialog, nicht auf die Startseite: Es wird einmal gesetzt
 * und danach monatelang nicht mehr angefasst. Ein Regler, der ständig sichtbar ist, nimmt
 * der Zahl den Platz, um die es geht — und lädt zum versehentlichen Verschieben ein.
 */
@Composable
fun GoalDialog(
    goalMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var steps by remember { mutableFloatStateOf((goalMillis / QUARTER_HOUR).toFloat()) }
    val chosen = steps.toLong() * QUARTER_HOUR

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (chosen <= 0L) {
                        stringResource(R.string.goal_off)
                    } else {
                        TimeFormat.short(chosen)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Slider(
                    value = steps,
                    onValueChange = { steps = it },
                    valueRange = 0f..MAX_STEPS,
                    steps = (MAX_STEPS - 1).toInt(),
                )
                Text(
                    text = stringResource(R.string.goal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
