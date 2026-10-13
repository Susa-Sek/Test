package de.shortblock.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.ui.theme.ShortBlockTheme

/**
 * Der Bildschirm, der sich beim harten Stopp vor den Feed legt.
 *
 * **Warum eine Activity und kein Fenster.** v0.11 legte über den Feed ein Overlay. Es blieb
 * nach dem Verlassen von Instagram über fremden Apps stehen und machte das Gerät unbenutzbar —
 * abgeräumt wurde es nur im Ereignispfad, und aus einer nicht überwachten App kommt kein
 * Ereignis. Eine Activity hat dieses Problem **strukturell** nicht:
 *
 * - Das System verwaltet ihren Lebenslauf, nicht wir. Home und Zurück funktionieren immer.
 * - `noHistory` beendet sie, sobald der Nutzer weggeht. Es gibt nichts, das wir abräumen
 *   müssten — und damit nichts, das wir vergessen können.
 * - Stirbt unser Prozess mitten darin, ist sie weg, statt stehenzubleiben.
 *
 * Genau das ist der Weg, den die ausgereiften Blocker gehen; sie verlangen „Über anderen Apps
 * anzeigen" nicht für ein Overlay, sondern weil Android nur mit dieser Berechtigung erlaubt,
 * aus dem Hintergrund eine Activity zu starten.
 *
 * **Was dieser Bildschirm bewusst nicht hat: einen „Weiter"-Knopf.** Wer trotzdem scrollen
 * will, hat den Tages-Cheat — der kostet Wartezeit und Kontingent und ist dafür gebaut. Ein
 * Knopf hier wäre ein Cheat ohne Preis.
 */
class BlockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val minutes = intent?.getIntExtra(EXTRA_COOLDOWN_MINUTES, 0) ?: 0

        // Zurück führt hier nach Hause, nicht einfach weg. Ein blankes `finish()` holte
        // Instagram mit dem Feed zurück in den Vordergrund, der Dienst stoppte erneut — und
        // genau das ist die Kette, die dieser Bildschirm ersetzen soll.
        onBackPressedDispatcher.addCallback(this) { leaveToHome() }

        setContent {
            ShortBlockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlockScreenContent(cooldownMinutes = minutes, onHome = ::leaveToHome)
                }
            }
        }
    }

    /**
     * Zum Startbildschirm, dann beenden.
     *
     * Beides nötig: Nur `finish()` brächte Instagram mit dem Feed zurück in den Vordergrund,
     * der Dienst stoppte erneut, und wir hätten dieselbe Kette wie beim Zurück-Drücken.
     */
    private fun leaveToHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    companion object {

        private const val EXTRA_COOLDOWN_MINUTES = "cooldown_minutes"

        /**
         * Der Aufruf aus dem Dienst.
         *
         * `FLAG_ACTIVITY_NEW_TASK` ist Pflicht (Start aus einem Dienst-Kontext),
         * `CLEAR_TOP` verhindert, dass sich zwei davon stapeln. Scheitert der Start — fehlende
         * Berechtigung, Hersteller-ROM —, gibt die Funktion `false` zurück, und der Aufrufer
         * nimmt den Rückfall „nach Hause". **Kein Weg, der stillschweigend nichts tut.**
         */
        fun start(context: Context, cooldownMinutes: Int): Boolean = runCatching {
            context.startActivity(
                Intent(context, BlockScreenActivity::class.java)
                    .putExtra(EXTRA_COOLDOWN_MINUTES, cooldownMinutes)
                    .setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    ),
            )
            true
        }.getOrDefault(false)
    }
}

@Composable
private fun BlockScreenContent(cooldownMinutes: Int, onHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.block_screen_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.block_screen_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (cooldownMinutes > 0) {
            Text(
                text = stringResource(R.string.block_screen_cooldown, cooldownMinutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Button(
            onClick = onHome,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        ) {
            Text(stringResource(R.string.block_screen_home))
        }
    }
}
