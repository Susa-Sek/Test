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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import de.shortblock.app.data.DayStat
import de.shortblock.app.data.StatsHistory
import de.shortblock.app.service.BlockLog
import de.shortblock.app.service.Feature
import de.shortblock.app.ui.components.WeekBars

/** Ein Treffer gilt als „gerade eben“, solange er so jung ist. */
private const val RECENT_WINDOW_MS = 10 * 60 * 1000L

@Composable
fun HomeScreen(
    serviceEnabled: Boolean,
    settings: BlockSettings,
    counts: Map<Feature, Int>,
    week: List<DayStat>,
    lastBlock: BlockLog.Entry?,
    onToggle: (Feature, Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val todayTotal = counts.values.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SavedHero(todayTotal)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.week_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekBars(week = week, modifier = Modifier.padding(top = 12.dp))
            }
        }

        if (!serviceEnabled) ServiceOffCard(onOpenSetup)

        AppGroup(
            title = stringResource(R.string.group_instagram),
            rows = listOf(
                ToggleRow(
                    feature = Feature.INSTAGRAM_REELS,
                    title = stringResource(R.string.toggle_reels_title),
                    description = stringResource(R.string.toggle_reels_desc),
                ),
                ToggleRow(
                    feature = Feature.INSTAGRAM_FEED,
                    title = stringResource(R.string.toggle_feed_title),
                    description = stringResource(R.string.toggle_feed_desc),
                ),
            ),
            settings = settings,
            counts = counts,
            onToggle = onToggle,
        )

        AppGroup(
            title = stringResource(R.string.group_youtube),
            rows = listOf(
                ToggleRow(
                    feature = Feature.YOUTUBE_SHORTS,
                    title = stringResource(R.string.toggle_shorts_title),
                    description = stringResource(R.string.toggle_shorts_desc),
                ),
            ),
            settings = settings,
            counts = counts,
            onToggle = onToggle,
        )

        AppGroup(
            title = stringResource(R.string.group_tiktok),
            rows = listOf(
                ToggleRow(
                    feature = Feature.TIKTOK_FYP,
                    title = stringResource(R.string.toggle_tiktok_fyp_title),
                    description = stringResource(R.string.toggle_tiktok_fyp_desc),
                ),
                ToggleRow(
                    feature = Feature.TIKTOK_ALL,
                    title = stringResource(R.string.toggle_tiktok_all_title),
                    description = stringResource(R.string.toggle_tiktok_all_desc),
                ),
            ),
            settings = settings,
            counts = counts,
            onToggle = onToggle,
        )

        RecentBlockChip(lastBlock = lastBlock, onOpenDiagnostics = onOpenDiagnostics)

        Text(
            text = stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Die große Zahl.
 *
 * Ohne Blocks steht hier bewusst nicht „0 Min gespart“ — eine Null in Riesenschrift ist ein
 * Vorwurf. Stattdessen eine neutrale Feststellung.
 */
@Composable
private fun SavedHero(todayTotal: Int) {
    val minutes = StatsHistory.savedMinutes(todayTotal)
    Column(Modifier.padding(top = 16.dp)) {
        if (todayTotal == 0) {
            Text(
                text = stringResource(R.string.saved_nothing_yet),
                style = MaterialTheme.typography.headlineSmall,
            )
        } else {
            Text(
                text = "≈ " + stringResource(R.string.minutes_short, minutes),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = stringResource(R.string.saved_blocks, todayTotal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.saved_assumption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ServiceOffCard(onOpenSetup: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.service_inactive),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.service_inactive_hint))
            }
        }
    }
}

private data class ToggleRow(
    val feature: Feature,
    val title: String,
    val description: String,
)

@Composable
private fun AppGroup(
    title: String,
    rows: List<ToggleRow>,
    settings: BlockSettings,
    counts: Map<Feature, Int>,
    onToggle: (Feature, Boolean) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                FeatureRow(
                    title = row.title,
                    description = row.description,
                    count = counts[row.feature] ?: 0,
                    checked = row.feature in settings.enabled,
                    onCheckedChange = { onToggle(row.feature, it) },
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    title: String,
    description: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (count > 0) {
                Text(
                    text = stringResource(R.string.blocked_today, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Dezenter Hinweis statt Debug-Karte.
 *
 * Die vollständige Liste steht in der Diagnose. Auf der Startseite erscheint nur dann etwas,
 * wenn gerade wirklich etwas passiert ist — und dann als Abkürzung dorthin.
 */
@Composable
private fun RecentBlockChip(lastBlock: BlockLog.Entry?, onOpenDiagnostics: () -> Unit) {
    if (lastBlock == null) return
    val ageMs = System.currentTimeMillis() - lastBlock.atMillis
    if (ageMs !in 0..RECENT_WINDOW_MS) return

    val ageMinutes = (ageMs / 60_000L).toInt()
    val ageLabel = if (ageMinutes < 1) {
        stringResource(R.string.just_now)
    } else {
        stringResource(R.string.minutes_short, ageMinutes)
    }

    AssistChip(
        onClick = onOpenDiagnostics,
        label = { Text(stringResource(R.string.recent_block_chip, lastBlock.ruleId, ageLabel)) },
    )
}
