package de.klarzeit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klarzeit.app.R
import de.klarzeit.app.data.AppCatalog
import de.klarzeit.app.data.GoalState
import de.klarzeit.app.data.ScreenTimeRepository
import de.klarzeit.app.data.TimeFormat
import de.klarzeit.app.data.UsageSessions

/**
 * Die Startseite.
 *
 * Drei Entscheidungen bestimmen den Aufbau:
 *
 * - **Die bereinigte Zahl steht oben und gross**, die Gesamtzeit als Nebensatz daneben. Wer
 *   die App öffnet, will wissen, wie viel Zeit er verloren hat — nicht, wie lange der
 *   Bildschirm an war, während das Navi lief.
 * - **Zwei Abschnitte statt einer Liste mit Markierungen.** Ob eine App zählt, sieht man
 *   dann an ihrer Position, nicht an einem kleinen Wort unter dem Namen.
 * - **Tippen schiebt eine App zwischen den Abschnitten hin und her.** Das ist die
 *   Entscheidung, um die es in dieser App geht; sie hinter einem zweiten Bildschirm zu
 *   verstecken, wäre der Hauptfehler gewesen. Der zweite Bildschirm bleibt trotzdem — für
 *   Apps, die heute gar nicht liefen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    today: ScreenTimeRepository.Today,
    catalog: AppCatalog,
    onToggleExcluded: (String) -> Unit,
    onOpenExclusions: () -> Unit,
    onEditGoal: () -> Unit,
    onRefresh: () -> Unit,
) {
    val counted = today.summary.apps.filter { it.counted }
    val excluded = today.summary.apps.filterNot { it.counted }
    // Der Balken misst sich am groessten Eintrag, nicht an der Tagessumme: Sonst sind
    // bei einem vollen Tag alle Balken gleich kurz und zeigen nichts.
    val longest = today.summary.apps.maxOfOrNull { it.millis } ?: 1L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.widget_refresh),
                        )
                    }
                    IconButton(onClick = onOpenExclusions) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tune),
                            contentDescription = stringResource(R.string.home_edit_excluded),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { Hero(today) }
            item { GoalBar(today, onEditGoal) }

            if (today.summary.apps.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            if (counted.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_section_counted),
                        hint = stringResource(R.string.home_hint_tap),
                    )
                }
                items(counted, key = { "c-${it.packageName}" }) { app ->
                    AppRow(app, catalog, longest, onClick = { onToggleExcluded(app.packageName) })
                }
            }

            if (excluded.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_section_excluded),
                        hint = null,
                    )
                }
                items(excluded, key = { "e-${it.packageName}" }) { app ->
                    AppRow(app, catalog, longest, onClick = { onToggleExcluded(app.packageName) })
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Hero(today: ScreenTimeRepository.Today) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.home_net_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
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
            if (today.summary.excludedMillis > 0) {
                Text(
                    text = "  " + stringResource(
                        R.string.widget_total_short,
                        TimeFormat.short(today.summary.totalMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }
}

/** Der Zielbalken ist gleichzeitig der Knopf, der den Regler öffnet. */
@Composable
private fun GoalBar(today: ScreenTimeRepository.Today, onEditGoal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditGoal)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (today.goalMillis <= 0L) {
            Text(
                text = stringResource(R.string.goal_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val over = today.status == GoalState.Status.OVER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.home_goal, TimeFormat.short(today.goalMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (over) {
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
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Bar(
            fraction = GoalState.progress(today.summary.countedMillis, today.goalMillis),
            color = when (today.status) {
                GoalState.Status.OVER -> MaterialTheme.colorScheme.error
                GoalState.Status.CLOSE -> MaterialTheme.colorScheme.tertiary
                GoalState.Status.UNDER -> MaterialTheme.colorScheme.secondary
            },
            height = 8,
        )
    }
}

@Composable
private fun SectionHeader(title: String, hint: String?) {
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: UsageSessions.AppTime,
    catalog: AppCatalog,
    longestMillis: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(catalog = catalog, packageName = app.packageName, dimmed = !app.counted)
        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = catalog.label(app.packageName),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (app.counted) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(5.dp))
            Bar(
                fraction = (app.millis.toFloat() / longestMillis).coerceIn(0f, 1f),
                color = if (app.counted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                height = 4,
            )
        }

        Spacer(Modifier.width(14.dp))
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
}

/** Ein Balken, der auch bei winzigen Anteilen noch sichtbar bleibt. */
@Composable
private fun Bar(fraction: Float, color: Color, height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                .height(height.dp)
                .clip(RoundedCornerShape(height.dp))
                .background(color),
        )
    }
}
