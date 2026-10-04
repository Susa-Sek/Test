package de.trimbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.trimbox.app.R
import de.trimbox.app.data.MailAccount
import de.trimbox.app.data.ProviderPresets

/**
 * Was im Formular steht.
 *
 * Der Zustand liegt bewusst **ausserhalb** dieses Bildschirms: Beim Verbinden wechselt die
 * App auf die Fortschrittsansicht, und damit verschwindet dieser Bildschirm aus der
 * Komposition. Läge der Zustand hier, wäre nach jedem Fehlversuch alles gelöscht — Adresse,
 * Passwort, Server. Genau das war der Grund, warum sich die Anmeldung anfühlte, als
 * passiere gar nichts.
 */
data class ConnectForm(
    val address: String = "",
    val password: String = "",
    val account: MailAccount = MailAccount.suggestFor(""),
) {
    /** Adresse übernehmen und Server nachziehen, ohne selbst Eingetragenes zu überschreiben. */
    fun withAddress(typed: String): ConnectForm {
        val preset = ProviderPresets.forAddress(typed)
        val updated = account.copy(address = typed.trim())
        return copy(
            address = typed,
            account = if (preset == null) {
                updated
            } else {
                updated.copy(
                    imapHost = preset.imapHost,
                    imapPort = preset.imapPort,
                    smtpHost = preset.smtpHost,
                    smtpPort = preset.smtpPort,
                    smtpStartTls = preset.smtpStartTls,
                )
            },
        )
    }
}

/**
 * Der erste Bildschirm. Er nimmt dem Nutzer die Serversuche ab und sagt vorher, was
 * schiefgehen wird — die meisten gescheiterten Anmeldungen sind keine Tippfehler, sondern
 * ein fehlendes App-Passwort.
 */
@Composable
fun ConnectScreen(
    form: ConnectForm,
    errorText: String?,
    errorDetail: String?,
    hasSavedAccount: Boolean,
    onFormChange: (ConnectForm) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val needsOAuth = ProviderPresets.needsOAuth(form.address)
    val ready = form.account.isComplete && form.password.isNotBlank() && !needsOAuth

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall)

        // Der Fehler steht ganz oben, nicht unten am Knopf: Nach einem Fehlversuch schaut
        // niemand ans Ende eines gescrollten Formulars.
        if (errorText != null) {
            Note(errorText, warning = true, detail = errorDetail)
        }

        Text(stringResource(R.string.connect_intro), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = form.address,
            onValueChange = { typed -> onFormChange(form.withAddress(typed)) },
            label = { Text(stringResource(R.string.connect_address)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (needsOAuth) {
            Note(stringResource(R.string.connect_unsupported), warning = true)
        }

        OutlinedTextField(
            value = form.password,
            onValueChange = { onFormChange(form.copy(password = it)) },
            label = { Text(stringResource(R.string.connect_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Note(stringResource(R.string.connect_hint_app_password))

        Text(stringResource(R.string.connect_server), style = MaterialTheme.typography.titleMedium)

        HostRow(
            host = form.account.imapHost,
            port = form.account.imapPort,
            label = stringResource(R.string.connect_imap_host),
            onHost = { onFormChange(form.copy(account = form.account.copy(imapHost = it))) },
            onPort = { onFormChange(form.copy(account = form.account.copy(imapPort = it))) },
        )
        HostRow(
            host = form.account.smtpHost,
            port = form.account.smtpPort,
            label = stringResource(R.string.connect_smtp_host),
            onHost = { onFormChange(form.copy(account = form.account.copy(smtpHost = it))) },
            onPort = { onFormChange(form.copy(account = form.account.copy(smtpPort = it))) },
        )
        Note(stringResource(R.string.connect_smtp_why))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 90, 180).forEach { days ->
                FilterChip(
                    selected = form.account.days == days,
                    onClick = { onFormChange(form.copy(account = form.account.copy(days = days))) },
                    label = { Text(pluralStringResource(R.plurals.connect_days, days, days)) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConnect,
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.connect_go)) }

        if (hasSavedAccount) {
            TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disconnect))
            }
        }
    }
}

@Composable
private fun HostRow(
    host: String,
    port: Int,
    label: String,
    onHost: (String) -> Unit,
    onPort: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = onHost,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            value = if (port == 0) "" else port.toString(),
            onValueChange = { typed -> onPort(typed.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0) },
            label = { Text(stringResource(R.string.connect_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Kleiner Hinweiskasten — grau für Erklärungen, farbig für Warnungen.
 *
 * Der [detail] trägt den Originaltext des Servers. Er ist klein gesetzt und technisch, aber
 * er steht da: Ohne ihn sieht ein fehlendes App-Passwort genauso aus wie ein Tippfehler im
 * Servernamen, und der Nutzer probiert im Dunkeln.
 */
@Composable
internal fun Note(text: String, warning: Boolean = false, detail: String? = null) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodySmall)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
