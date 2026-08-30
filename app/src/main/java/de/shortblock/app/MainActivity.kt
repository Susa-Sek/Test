package de.shortblock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.shortblock.app.ui.AppRoot
import de.shortblock.app.ui.theme.ShortBlockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShortBlockTheme {
                AppRoot()
            }
        }
    }
}
