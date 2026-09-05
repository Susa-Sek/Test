package de.trimbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.trimbox.app.ui.AppRoot
import de.trimbox.app.ui.theme.TrimBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TrimBoxTheme {
                AppRoot()
            }
        }
    }
}
