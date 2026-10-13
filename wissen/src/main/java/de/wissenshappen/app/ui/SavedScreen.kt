package de.wissenshappen.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.wissenshappen.app.R

/**
 * Gemerkte Karten — bewusst als Abfrage, nicht als Leseliste.
 *
 * Zuerst steht nur der Titel da, der Text kommt erst auf Tippen. Dieser eine Moment, in dem
 * man versucht sich zu erinnern, ist der Unterschied zwischen Wiederlesen und Behalten.
 */
@Composable
fun SavedScreen(
    cards: List<de.wissenshappen.app.data.Card>,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.saved_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }

        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = stringResource(R.string.saved_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                RecallCard(
                    title = card.title,
                    text = card.text,
                    onRemove = { onRemove(card.id) },
                )
            }
        }
    }
}

@Composable
private fun RecallCard(title: String, text: String, onRemove: () -> Unit) {
    var revealed by remember(title) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { revealed = !revealed },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)

            AnimatedVisibility(visible = !revealed) {
                Text(
                    text = stringResource(R.string.saved_reveal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            AnimatedVisibility(visible = revealed) {
                Column {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = onRemove, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.remove))
                    }
                }
            }
        }
    }
}
