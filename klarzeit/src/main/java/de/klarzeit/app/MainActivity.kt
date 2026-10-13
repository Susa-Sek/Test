package de.klarzeit.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import de.klarzeit.app.ui.AppRoot
import de.klarzeit.app.ui.theme.KlarzeitTheme

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Ohne dieses Recht bliebe die Zielmeldung stumm. Abgelehnt wird sie nur einmal
        // gefragt — die App funktioniert auch ohne, sie schweigt dann eben.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            KlarzeitTheme {
                AppRoot()
            }
        }
    }
}
