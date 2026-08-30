package de.wissenshappen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.wissenshappen.app.ui.AppRoot
import de.wissenshappen.app.ui.theme.WissenshappenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WissenshappenTheme {
                AppRoot()
            }
        }
    }
}
