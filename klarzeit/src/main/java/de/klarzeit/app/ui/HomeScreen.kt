package de.klarzeit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klarzeit.app.R
import de.klarzeit.app.data.GoalState
import de.klarzeit.app.data.ScreenTimeRepository
import de.klarzeit.app.data.TimeFormat

/**
 * Die Zahl gross, die Herkunft klein darunter.
 *
 * Bewusst steht die bereinigte Zeit oben und die Gesamtzeit als Nebensatz: Wer die App
 * öffnet, will wissen, wie viel Zeit er verloren hat — nicht, wie lange der Bildschirm an
 * war, während das Navi lief.
 */
@Composable
fun HomeScreen(
    today: ScreenTimeRepository.Today,
    labelOf: (String) -> String,
    onEditExclusions: () -> Unit,
    onGoalChange: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_net_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = TimeFormat.short(today.summary.countedMillis),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (today.status == GoalState.Status.OVER) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                Text(
                    text = stringResource(
                        R.string.home_total,
                        TimeFormat.short(today.summary.totalMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (today.summary.excludedMillis > 0) {
                    Text(
                        text = stringResource(
                            R.string.home_excluded,
                            TimeFormat.short(today.summary.excludedMillis),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { GoalCard(today, onGoalChange) }

        item {
            TextButton(
                onClick = onEditExclusions,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.home_edit_excluded)) }
        }

        item {
            Text(
                text = stringResource(R.string.home_apps),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
            )
        }

        if (today.summary.apps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        items(today.summary.apps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = labelOf(app.packageName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!app.counted) {
                        Text(
                            text = stringResource(R.string.home_excluded_marker),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = TimeFormat.short(app.millis),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (app.counted) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun GoalCard(
    today: ScreenTimeRepository.Today,
    onGoalChange: (Long) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (today.goalMillis <= 0L) {
                        stringResource(R.string.goal_off)
                    } else {
                        stringResource(R.string.home_goal, TimeFormat.short(today.goalMillis))
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (today.goalMillis > 0L) {
                    Text(
                        text = if (today.status == GoalState.Status.OVER) {
                            stringResource(
                                R.string.home_goal_over,
                                TimeFormat.short(today.summary.countedMillis - today.goalMillis),
                            )
                        } else {
                            stringResource(
                                R.string.home_goal_left,
                                TimeFormat.short(today.goalMillis - today.summary.countedMillis),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (today.status == GoalState.Status.OVER) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (today.goalMillis > 0L) {
                LinearProgressIndicator(
                    progress = {
                        GoalState.progress(today.summary.countedMillis, today.goalMillis)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // In Viertelstunden, von "kein Ziel" bis sechs Stunden.
            Slider(
                value = (today.goalMillis / QUARTER_HOUR).toFloat(),
                onValueChange = { steps -> onGoalChange(steps.toLong() * QUARTER_HOUR) },
                valueRange = 0f..24f,
                steps = 23,
            )
            Text(
                text = stringResource(R.string.goal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val QUARTER_HOUR = 15 * 60 * 1000L
