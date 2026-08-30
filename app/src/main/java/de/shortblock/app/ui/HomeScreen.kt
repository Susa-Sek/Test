package de.shortblock.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.service.Feature

@Composable
fun HomeScreen(
    serviceEnabled: Boolean,
    settings: BlockSettings,
    counts: Map<Feature, Int>,
    onToggle: (Feature, Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp),
        )

        ServiceStatusCard(serviceEnabled = serviceEnabled, onOpenSetup = onOpenSetup)

        FeatureCard(
            title = stringResource(R.string.toggle_reels_title),
            description = stringResource(R.string.toggle_reels_desc),
            count = counts[Feature.INSTAGRAM_REELS] ?: 0,
            checked = Feature.INSTAGRAM_REELS in settings.enabled,
            onCheckedChange = { onToggle(Feature.INSTAGRAM_REELS, it) },
        )
        FeatureCard(
            title = stringResource(R.string.toggle_feed_title),
            description = stringResource(R.string.toggle_feed_desc),
            count = counts[Feature.INSTAGRAM_FEED] ?: 0,
            checked = Feature.INSTAGRAM_FEED in settings.enabled,
            onCheckedChange = { onToggle(Feature.INSTAGRAM_FEED, it) },
        )
        FeatureCard(
            title = stringResource(R.string.toggle_shorts_title),
            description = stringResource(R.string.toggle_shorts_desc),
            count = counts[Feature.YOUTUBE_SHORTS] ?: 0,
            checked = Feature.YOUTUBE_SHORTS in settings.enabled,
            onCheckedChange = { onToggle(Feature.YOUTUBE_SHORTS, it) },
        )

        Text(
            text = stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenSetup) { Text(stringResource(R.string.open_setup)) }
            TextButton(onClick = onOpenDiagnostics) { Text(stringResource(R.string.open_diagnostics)) }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ServiceStatusCard(serviceEnabled: Boolean, onOpenSetup: () -> Unit) {
    val colors = if (serviceEnabled) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    if (serviceEnabled) R.string.service_active else R.string.service_inactive,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (!serviceEnabled) {
                TextButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.service_inactive_hint))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.blocked_today, count),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
