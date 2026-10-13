package de.shortblock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import de.shortblock.app.service.ServiceWatchdogWorker
import de.shortblock.app.ui.AppRoot
import de.shortblock.app.ui.theme.ShortBlockTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Setzt der Bedienungshilfen-Knopf, damit die App direkt auf der Cheat-Anfrage öffnet. */
        const val EXTRA_OPEN_CHEAT = "open_cheat"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ohne eigene SystemBarStyle rechnet Android weiter mit hellem Grund und zeichnet
        // schwarze Symbole in die Leisten — auf dem dunklen Grund praktisch unsichtbar.
        val dark = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val barStyle = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        super.onCreate(savedInstanceState)
        ServiceWatchdogWorker.schedule(applicationContext)
        setContent {
            ShortBlockTheme {
                AppRoot(openCheatOnStart = intent?.getBooleanExtra(EXTRA_OPEN_CHEAT, false) == true)
            }
        }
    }
}
