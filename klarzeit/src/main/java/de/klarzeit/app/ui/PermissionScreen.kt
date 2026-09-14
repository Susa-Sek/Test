package de.klarzeit.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.klarzeit.app.R

/**
 * Der Zugriff auf Nutzungsdaten lässt sich nicht per Dialog erfragen — Android verlangt den
 * Umweg über die Einstellungen. Also erklärt dieser Bildschirm, warum, und bringt einen
 * dorthin.
 */
@Composable
fun PermissionScreen(onRecheck: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.permission_body), style = MaterialTheme.typography.bodyMedium)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.permission_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }

        Button(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.permission_open)) }

        TextButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_recheck))
        }
    }
}
