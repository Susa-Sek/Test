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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
fun WeekBars(
    week: List<DayStat>,
    modifier: Modifier = Modifier,
) {
    val maximum = (week.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    val today = LocalDate.now().toEpochDay()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        .height(72.dp)
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "$label: ${day.total}"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Auch ein Nulltag bekommt einen sichtbaren Sockel — eine Lücke
                            // sähe aus wie ein Darstellungsfehler, nicht wie eine Null.
                            .fillMaxHeight((day.total.toFloat() / maximum).coerceIn(0.04f, 1f))
                            .background(
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(6.dp),
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

/** Text für den Hero: „≈ 34 Min“ oder die ehrliche Alternative bei null. */
@Composable
fun savedLabel(minutes: Int): String =
    if (minutes <= 0) "" else "≈ " + stringResource(R.string.minutes_short, minutes)
