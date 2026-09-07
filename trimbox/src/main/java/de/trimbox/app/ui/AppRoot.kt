package de.trimbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.trimbox.app.R
import de.trimbox.app.data.AccountStore
import de.trimbox.app.data.AppPassword
import de.trimbox.app.data.MailAccount
import de.trimbox.app.data.TrimRepository
import de.trimbox.app.mail.MailError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Ein Durchlauf, vier Bildschirme, keine Navigationsbibliothek: Verbinden → Lesen →
 * Auswählen → Bericht. Für mehr ist in Fassung 1 nichts vorgesehen.
 *
 * Das Passwort lebt nur hier im Speicher und geht an [TrimRepository] weiter, ohne je
 * in DataStore, Protokoll oder Zustandsobjekt zu landen.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { TrimRepository() }
    val store = remember(context) { AccountStore(context) }

    val phase by repository.phase.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()

    // Das Formular lebt hier, nicht im ConnectScreen: Beim Verbinden wechselt der
    // Bildschirm, und ein dort gehaltener Zustand waere nach jedem Fehlversuch weg.
    var form by remember { mutableStateOf(ConnectForm()) }
    var account by remember { mutableStateOf<MailAccount?>(null) }
    var password by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    var unsubscribeFrom by remember { mutableStateOf(emptySet<String>()) }
    var cleanUp by remember { mutableStateOf(emptySet<String>()) }

    // Das gespeicherte Konto kommt aus DataStore, das Passwort entschlüsselt aus dem
    // Keystore. Bis beides da ist, zeigt der Bildschirm nichts Halbes.
    var restored by remember { mutableStateOf<Restored?>(null) }
    LaunchedEffect(Unit) {
        val saved = store.account.first()
        val secret = if (saved == null) "" else store.password().orEmpty()
        restored = Restored(saved, secret)
        if (saved != null) {
            form = ConnectForm(address = saved.address, password = secret, account = saved)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = phase) {
            TrimRepository.Phase.Idle -> when (val saved = restored) {
                null -> Loading()
                else -> ConnectScreen(
                    form = form,
                    errorText = error?.let { messageFor(it) },
                    errorDetail = error?.detail,
                    hasSavedAccount = saved.account != null,
                    onFormChange = { form = it },
                    onConnect = {
                        // Leerzeichen aus eingefuegten App-Passwoertern entfernen — der
                        // haeufigste Grund fuer eine abgelehnte Anmeldung mit richtigem Passwort.
                        val secret = AppPassword.normalize(form.password)
                        val chosen = form.account.copy(address = form.address.trim())
                        account = chosen
                        password = secret
                        unsubscribeFrom = emptySet()
                        cleanUp = emptySet()
                        scanJob = scope.launch {
                            repository.scan(chosen, secret)
                            // Erst speichern, wenn die Anmeldung nachweislich geklappt hat —
                            // sonst merkt sich die App einen Tippfehler.
                            if (repository.phase.value is TrimRepository.Phase.Ready) {
                                store.save(chosen, secret)
                                restored = Restored(chosen, secret)
                            }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            store.clear()
                            restored = Restored(null, "")
                            form = ConnectForm()
                            account = null
                            password = ""
                        }
                    },
                )
            }

            is TrimRepository.Phase.Scanning -> ScanScreen(
                headersRead = current.headersRead,
                onCancel = {
                    scanJob?.cancel()
                    repository.reset()
                },
            )

            is TrimRepository.Phase.Ready -> ResultScreen(
                senders = current.senders,
                mailsSeen = current.mailsSeen,
                days = account?.days ?: 90,
                unsubscribeFrom = unsubscribeFrom,
                cleanUp = cleanUp,
                onToggleUnsubscribe = { address ->
                    unsubscribeFrom = unsubscribeFrom.toggle(address)
                },
                onToggleCleanUp = { address -> cleanUp = cleanUp.toggle(address) },
                onExecute = {
                    val target = account ?: return@ResultScreen
                    scope.launch {
                        repository.execute(
                            account = target,
                            password = password,
                            senders = current.senders,
                            unsubscribeFrom = unsubscribeFrom,
                            cleanUp = cleanUp,
                        )
                    }
                },
                onBack = { repository.reset() },
            )

            is TrimRepository.Phase.Working -> Working(current.done, current.total)

            is TrimRepository.Phase.Done -> ReportScreen(
                report = current.report,
                onDone = { repository.reset() },
            )
        }
    }
}

/** Nur der Augenblick, in dem das gespeicherte Konto aus dem Speicher kommt. */
@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Working(done: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("$done / $total", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun messageFor(error: MailError): String = when (error) {
    is MailError.Authentication -> stringResource(R.string.error_auth)
    is MailError.Network -> stringResource(R.string.error_network)
    is MailError.Other -> stringResource(R.string.error_unknown)
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

/** Was beim Start aus dem Speicher zurückkam. `null` heisst: noch am Laden. */
private data class Restored(val account: MailAccount?, val password: String)
