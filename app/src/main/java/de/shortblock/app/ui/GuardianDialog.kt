package de.shortblock.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.data.GuardianLock
import kotlinx.coroutines.delay

/** Mindestlänge des Passworts. Kurz genug zum Merken, lang genug gegen Durchprobieren. */
private const val MIN_PASSWORD_LENGTH = 6

/**
 * Die Partnersperre — einrichten, den Notausgang zeigen, entsperren.
 *
 * Drei Bildschirme in einem Dialog, weil sie eine Kette sind: Passwort setzen → Code notieren →
 * fertig. Der mittlere Schritt lässt sich nicht überspringen, und das ist Absicht: Ein Code, den
 * niemand aufgeschrieben hat, ist kein Notausgang.
 */
@Composable
fun GuardianSetupDialog(
    onConfirm: (passwordHash: String, recoveryHash: String, code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }

    val shown = code
    if (shown != null) {
        RecoveryCodeDialog(code = shown, onDismiss = onDismiss)
        return
    }

    val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH
    val mismatch = repeat.isNotEmpty() && password != repeat
    val ready = password.length >= MIN_PASSWORD_LENGTH && password == repeat

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.guardian_setup_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.guardian_setup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.guardian_password)) },
                    singleLine = true,
                    isError = tooShort,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text(stringResource(R.string.guardian_password_repeat)) },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.padding(top = 8.dp),
                )
                val problem = when {
                    tooShort -> stringResource(R.string.guardian_too_short, MIN_PASSWORD_LENGTH)
                    mismatch -> stringResource(R.string.guardian_mismatch)
                    else -> null
                }
                if (problem != null) {
                    Text(
                        text = problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // Die ehrliche Auskunft steht im Einrichtungsweg, nicht im Kleingedruckten:
                // Wer sich auf einen Tresor verlässt, den es nicht gibt, ist schlechter dran
                // als jemand, der die Grenze kennt.
                Text(
                    text = stringResource(R.string.guardian_honest_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.guardian_honest_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    val fresh = GuardianLock.newRecoveryCode()
                    onConfirm(GuardianLock.store(password), GuardianLock.storeCode(fresh), fresh)
                    code = fresh
                },
            ) { Text(stringResource(R.string.guardian_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.guardian_cancel)) }
        },
    )
}

/**
 * Der Wiederherstellungscode, einmalig.
 *
 * Bewusst **ohne** Abbrechen-Knopf und ohne Schliessen durch Danebentippen: Dieser Bildschirm
 * ist der einzige Moment, in dem der Code existiert. Wer hier versehentlich hinaustippt, hat
 * eine Sperre ohne Notausgang.
 */
@Composable
private fun RecoveryCodeDialog(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* Nur über den Knopf — siehe oben. */ },
        title = { Text(stringResource(R.string.guardian_code_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.guardian_code_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = code,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 16.dp),
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("ShortBlock", code))
                        Toast.makeText(context, R.string.guardian_code_copied, Toast.LENGTH_SHORT)
                            .show()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(stringResource(R.string.guardian_code_copy)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.guardian_code_confirm))
            }
        },
    )
}

/**
 * Entsperren — mit Passwort für die Sitzung, mit Code für immer.
 *
 * Die Wartezeit nach Fehlversuchen läuft sichtbar herunter. Sie ist keine Schikane: Der
 * Ratende ist hier der Gerätebesitzer selbst, der sein eigenes Passwort sucht und beliebig
 * viel Zeit hat.
 */
@Composable
fun GuardianUnlockDialog(
    settings: BlockSettings,
    onUnlocked: () -> Unit,
    onRecovered: () -> Unit,
    onFailure: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shownFailures by remember { mutableIntStateOf(settings.guardianFailures) }

    val waiting = GuardianLock.remainingLockoutSeconds(
        settings.guardianFailures,
        settings.guardianLastFailureMillis,
        now,
    )

    LaunchedEffect(waiting > 0) {
        while (waiting > 0) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.guardian_unlock_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.guardian_unlock_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.guardian_password)) },
                    singleLine = true,
                    enabled = waiting == 0,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.padding(top = 12.dp),
                )
                val note = when {
                    waiting > 0 -> stringResource(R.string.guardian_wait, waiting)
                    shownFailures > 0 -> stringResource(R.string.guardian_wrong, shownFailures)
                    else -> null
                }
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = waiting == 0 && typed.isNotEmpty(),
                onClick = {
                    when {
                        GuardianLock.verify(typed, settings.guardianHash) -> onUnlocked()
                        // Der Code hebt die Sperre ganz auf. Er ist der Notausgang, kein
                        // zweites Passwort zum Hineinschauen.
                        GuardianLock.verifyCode(typed, settings.guardianRecoveryHash) ->
                            onRecovered()

                        else -> {
                            shownFailures = settings.guardianFailures + 1
                            typed = ""
                            onFailure()
                        }
                    }
                },
            ) { Text(stringResource(R.string.guardian_unlock)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.guardian_cancel)) }
        },
    )
}
