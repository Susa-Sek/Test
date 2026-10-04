package de.shortblock.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.service.Health

/**
 * Der Dienst-Zustand als kleine Kapsel in der Titelzeile.
 *
 * Sie ist die Antwort auf die häufigste stille Frage dieser App — „blockt das gerade
 * überhaupt?“ — und steht deshalb dort, wo man beim Scrollen nicht daran vorbeikommt. Der
 * Zustand kommt unverändert aus `classifyHealth`; hier wird nichts neu entschieden.
 */
@Composable
fun StatusPill(health: Health, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (dot, label) = when (health) {
        // IDLE ist ausdrücklich kein Fehler — wer vier Stunden nicht auf Instagram war, hat
        // vier Stunden Stille. Die Kapsel unterscheidet deshalb nicht zwischen beiden.
        Health.HEALTHY, Health.IDLE -> scheme.tertiary to R.string.pill_running
        Health.NOT_CONNECTED -> scheme.error to R.string.pill_asleep
        Health.OFF -> scheme.error to R.string.pill_off
    }

    Row(
        modifier = modifier
            .background(scheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
