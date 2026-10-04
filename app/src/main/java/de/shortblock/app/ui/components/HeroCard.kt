package de.shortblock.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.data.DayStat
import de.shortblock.app.data.StatsHistory
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Die eine große Aussage der Startseite: Zahl und Wochenverlauf in **einer** Karte.
 *
 * Vor v0.7 standen die gesparten Minuten und die Wochenbalken in zwei zusammenhanglosen
 * Blöcken übereinander. Zusammengelegt beantworten sie dieselbe Frage — „wie läuft es?“ —
 * statt zweimal dieselbe Datenbasis anders anzuschneiden.
 *
 * Bei null Blocks bleibt der ehrliche Satz stehen: Eine Null in Riesenschrift ist ein Vorwurf.
 */
@Composable
fun HeroCard(todayTotal: Int, week: List<DayStat>, modifier: Modifier = Modifier) {
    InfoCard(modifier = modifier) {
        if (todayTotal == 0) {
            Text(
                text = stringResource(R.string.saved_nothing_yet),
                style = MaterialTheme.typography.headlineSmall,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = StatsHistory.savedMinutes(todayTotal).toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.hero_unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.hero_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.saved_blocks, todayTotal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            // Die Zahl ist geschätzt, und das muss dranstehen. Eine Statistik, die ihre
            // Annahme verschweigt, ist eine Behauptung.
            Text(
                text = stringResource(R.string.saved_assumption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        WeekBars(week = week)
    }
}

/**
 * Sieben Balken für sieben Tage.
 *
 * Bewusst keine Diagramm-Bibliothek: Für sieben Rechtecke wäre das eine Abhängigkeit, die bei
 * jedem Compose-Update brechen kann. Höhe über `fillMaxHeight(bruchteil)`, fertig.
 *
 * Jeder Balken trägt eine `contentDescription` mit Wochentag und Zahl — ein Diagramm, das ein
 * Screenreader nicht vorlesen kann, ist für Betroffene schlicht nicht vorhanden.
 */
@Composable
fun WeekBars(week: List<DayStat>, modifier: Modifier = Modifier) {
    val maximum = (week.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    val today = LocalDate.now().toEpochDay()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { day ->
            val label = LocalDate.ofEpochDay(day.epochDay)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val isToday = day.epochDay == today

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                        .semantics { contentDescription = "$label: ${day.total}" },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Auch ein Nulltag bekommt einen sichtbaren Sockel — eine Lücke
                            // sähe aus wie ein Darstellungsfehler, nicht wie eine Null.
                            .fillMaxHeight((day.total.toFloat() / maximum).coerceIn(0.05f, 1f))
                            .background(
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(5.dp),
                            ),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
