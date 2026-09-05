package de.trimbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.trimbox.app.R

/** Während gelesen wird. Der Hinweis unten ist die halbe Miete für das Vertrauen. */
@Composable
fun ScanScreen(headersRead: Int, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleMedium)
        Text(
            pluralStringResource(R.plurals.scan_progress, headersRead, headersRead),
            style = MaterialTheme.typography.bodyLarge,
        )
        Note(stringResource(R.string.scan_note))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}
