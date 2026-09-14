package de.shortblock.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.data.CheatPass
import de.shortblock.app.data.CheatPhrase
import de.shortblock.app.data.CheatStage
import de.shortblock.app.service.Reminders
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Die Tür zum Cheat — und die drei Hürden davor.
 *
 * Bis v0.7 war der Cheat ein Tipp auf den Knopf am Bildschirmrand. Das ist genau die Geste, die
 * man aus Reflex macht, und genau in dem Moment, in dem der Reflex am stärksten ist: direkt
 * nachdem geblockt wurde. Deshalb steht hier jetzt ein Satz zum Abtippen, danach eine Wartezeit,
 * und die Minuten zählen auf das Tageskontingent.
 *
 * Der Preis ist offen genannt: Für ein geschicktes Reel ist das unbrauchbar. Dafür gibt es die
 * Ausnahme „Geteilte Videos ansehen“ — nicht diesen Dialog.
 */
@Composable
fun CheatDialog(
    settings: BlockSettings,
    onArm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val phrases = stringArrayResource(R.array.cheat_phrases)

    // Einmal je Öffnen gewürfelt, nicht bei jeder Neuzeichnung — ein Satz, der sich beim Tippen
    // unter den Fingern ändert, wäre Hohn.
    val phrase = remember(phrases) {
        val index = Reminders.next(phrases.size, lastIndex = -1)
        phrases.getOrElse(index) { "" }
    }

    var typed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val stage = CheatPass.stage(
        settings.cheatArmedAtMillis,
        settings.cheatUsedOnDay,
        LocalDate.now().toEpochDay().toInt(),
        now,
    )

    LaunchedEffect(settings.cheatArmedAtMillis, stage) {
        while (stage == CheatStage.WAITING || stage == CheatStage.RUNNING) {
            delay(500L)
            now = System.currentTimeMillis()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (stage) {
                    CheatStage.FREE -> stringResource(R.string.cheat_dialog_title, CheatPass.DURATION_MINUTES)
                    CheatStage.WAITING -> stringResource(R.string.cheat_waiting_title)
                    CheatStage.RUNNING -> stringResource(R.string.cheat_running_title)
                    CheatStage.USED -> stringResource(R.string.cheat_used_title)
                },
            )
        },
        text = {
            Column {
                when (stage) {
                    CheatStage.FREE -> {
                        Text(
                            text = stringResource(R.string.cheat_dialog_cost, CheatPass.WAIT_SECONDS),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = phrase,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            label = { Text(stringResource(R.string.cheat_dialog_field)) },
                            singleLine = false,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    CheatStage.WAITING -> Text(
                        text = stringResource(
                            R.string.cheat_waiting_body,
                            CheatPass.waitRemainingSeconds(settings.cheatArmedAtMillis, now),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    CheatStage.RUNNING -> Text(
                        text = stringResource(
                            R.string.cheat_running_body,
                            (CheatPass.runRemainingSeconds(settings.cheatArmedAtMillis, now) + 59) / 60,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    CheatStage.USED -> Text(
                        text = stringResource(R.string.cheat_used_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (stage == CheatStage.FREE) {
                TextButton(
                    onClick = onArm,
                    enabled = CheatPhrase.matches(typed, phrase),
                ) {
                    Text(stringResource(R.string.cheat_dialog_request))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.overlay_dismiss)) }
            }
        },
        dismissButton = {
            if (stage == CheatStage.FREE) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cheat_dialog_cancel)) }
            }
        },
    )
}
