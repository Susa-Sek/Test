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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.trimbox.app.R
import de.trimbox.app.data.MailAccount
import de.trimbox.app.data.ProviderPresets

/**
 * Der erste Bildschirm. Er nimmt dem Nutzer die Serversuche ab und sagt vorher, was
 * schiefgehen wird — die meisten gescheiterten Anmeldungen sind keine Tippfehler, sondern
 * ein fehlendes App-Passwort.
 */
@Composable
fun ConnectScreen(
    busy: Boolean,
    errorText: String?,
    initialAccount: MailAccount?,
    initialPassword: String,
    onConnect: (MailAccount, String) -> Unit,
    onDisconnect: () -> Unit,
) {
    // Auf das gespeicherte Konto geschlüsselt: Es kommt erst nach dem ersten Bild aus
    // DataStore, und ohne den Schlüssel bliebe das Formular leer.
    var address by remember(initialAccount) { mutableStateOf(initialAccount?.address.orEmpty()) }
    var password by remember(initialAccount) { mutableStateOf(initialPassword) }
    var account by remember(initialAccount) {
        mutableStateOf(initialAccount ?: MailAccount.suggestFor(""))
    }

    val needsOAuth = remember(address) { ProviderPresets.needsOAuth(address) }
    val ready = account.isComplete && password.isNotBlank() && !needsOAuth && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.connect_intro), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = address,
            onValueChange = { typed ->
                address = typed
                // Server nachziehen, solange der Nutzer sie nicht selbst angefasst hat.
                val suggestion = MailAccount.suggestFor(typed)
                account = account.copy(
                    address = suggestion.address,
                    imapHost = suggestion.imapHost.ifBlank { account.imapHost },
                    imapPort = suggestion.imapPort,
                    smtpHost = suggestion.smtpHost.ifBlank { account.smtpHost },
                    smtpPort = suggestion.smtpPort,
                    smtpStartTls = suggestion.smtpStartTls,
                )
            },
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
            value = password,
            onValueChange = { password = it },
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
            host = account.imapHost,
            port = account.imapPort,
            label = stringResource(R.string.connect_imap_host),
            onHost = { account = account.copy(imapHost = it) },
            onPort = { account = account.copy(imapPort = it) },
        )
        HostRow(
            host = account.smtpHost,
            port = account.smtpPort,
            label = stringResource(R.string.connect_smtp_host),
            onHost = { account = account.copy(smtpHost = it) },
            onPort = { account = account.copy(smtpPort = it) },
        )
        Note(stringResource(R.string.connect_smtp_why))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 90, 180).forEach { days ->
                FilterChip(
                    selected = account.days == days,
                    onClick = { account = account.copy(days = days) },
                    label = { Text(pluralStringResource(R.plurals.connect_days, days, days)) },
                )
            }
        }

        if (errorText != null) {
            Note(errorText, warning = true)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onConnect(account, password) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.connect_working))
            } else {
                Text(stringResource(R.string.connect_go))
            }
        }

        if (initialAccount != null) {
            TextButton(
                onClick = {
                    address = ""
                    password = ""
                    account = MailAccount.suggestFor("")
                    onDisconnect()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.disconnect)) }
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

/** Kleiner Hinweiskasten — grau für Erklärungen, farbig für Warnungen. */
@Composable
internal fun Note(text: String, warning: Boolean = false) {
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
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
