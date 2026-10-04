package de.wissenshappen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import de.wissenshappen.app.data.SettingsRepository

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicsScreen(
    selected: List<String>,
    dailyGoal: Int,
    onToggleTopic: (String) -> Unit,
    onAddTopic: (String) -> Unit,
    onGoalChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var own by remember { mutableStateOf("") }

    // Vorschläge plus alles, was der Nutzer selbst ergänzt hat — sonst verschwindet ein
    // eigenes Thema aus der Liste, sobald man es einmal abwählt.
    val allTopics = remember(selected) {
        (SettingsRepository.SUGGESTED_TOPICS + selected).distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.topics_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }

        Text(
            text = stringResource(R.string.topics_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (selected.isEmpty()) {
            Text(
                text = stringResource(R.string.topics_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            allTopics.forEach { topic ->
                FilterChip(
                    selected = topic in selected,
                    onClick = { onToggleTopic(topic) },
                    label = { Text(topic) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = own,
                onValueChange = { own = it },
                label = { Text(stringResource(R.string.topics_own)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val trimmed = own.trim()
                    if (trimmed.isNotEmpty()) {
                        onAddTopic(trimmed)
                        own = ""
                    }
                },
                enabled = own.isNotBlank(),
            ) {
                Text(stringResource(R.string.topics_add))
            }
        }

        Text(
            text = stringResource(R.string.goal_title) + ": $dailyGoal",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Slider(
            value = dailyGoal.toFloat(),
            onValueChange = { onGoalChange(it.toInt()) },
            valueRange = 3f..30f,
            steps = 26,
        )

        Spacer(Modifier.height(24.dp))
    }
}
