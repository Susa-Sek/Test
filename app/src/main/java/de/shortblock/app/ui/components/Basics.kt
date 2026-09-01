package de.shortblock.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Die gemeinsame Bausprache aller drei Bildschirme.
 *
 * Vor v0.7 baute jeder Screen seine Karten und Zeilen selbst — drei Varianten derselben Sache,
 * jede mit eigenen Farben und Abständen. Genau daran sieht man einer App an, dass sie
 * gewachsen und nicht gestaltet ist.
 */

/** Ton einer Karte. Immer über die Rolle wählen, nie über eine Farbe direkt. */
enum class CardTone { NEUTRAL, GOOD, ALERT, ACCENT }

@Composable
fun InfoCard(
    tone: CardTone = CardTone.NEUTRAL,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (tone) {
        CardTone.NEUTRAL -> scheme.surface
        CardTone.GOOD -> scheme.tertiaryContainer
        CardTone.ALERT -> scheme.errorContainer
        CardTone.ACCENT -> scheme.primaryContainer
    }
    val onContainer = when (tone) {
        CardTone.NEUTRAL -> scheme.onSurface
        CardTone.GOOD -> scheme.onTertiaryContainer
        CardTone.ALERT -> scheme.onErrorContainer
        CardTone.ACCENT -> scheme.onPrimaryContainer
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(20.dp))
            // Die feine Kante ersetzt den Schatten: Auf dunklem Grund ist ein Schatten
            // unsichtbar, eine Kante trennt trotzdem sauber.
            .border(1.dp, scheme.outline, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides onContainer,
            content = { content() },
        )
    }
}

/**
 * Überschrift einer App-Gruppe: Farbpunkt plus Name.
 *
 * Der Punkt ist der einzige Ort, an dem eine App ihre eigene Farbe bekommt — er ordnet die
 * Karten darunter zu, ohne dass die Karten selbst bunt werden müssten.
 */
@Composable
fun SectionHeader(title: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Titel, Beschreibung, Schalter — die Zeile, aus der die halbe App besteht. */
@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
