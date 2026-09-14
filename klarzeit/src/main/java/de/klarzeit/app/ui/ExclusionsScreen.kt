package de.klarzeit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.klarzeit.app.R
import de.klarzeit.app.data.AppCatalog
import de.klarzeit.app.data.TimeFormat

/**
 * Die Ausschlussliste — der eigentliche Grund für diese App.
 *
 * Oben die Apps, die heute wirklich gelaufen sind, mit ihren Zeiten: Das ist die
 * Entscheidung, die gerade ansteht. Darunter erst alle übrigen. Eine alphabetische Liste
 * von zweihundert Apps wäre formal vollständig und praktisch unbenutzbar.
 */
@Composable
fun ExclusionsScreen(
    usedToday: List<Pair<String, Long>>,
    allApps: List<String>,
    excluded: Set<String>,
    catalog: AppCatalog,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val labelOf: (String) -> String = catalog::label
    var query by remember { mutableStateOf("") }

    val usedFiltered = remember(query, usedToday) {
        usedToday.filter { labelOf(it.first).contains(query, ignoreCase = true) }
    }
    val usedPackages = remember(usedToday) { usedToday.map { it.first }.toSet() }
    val restFiltered = remember(query, allApps, usedPackages) {
        allApps.filter { it !in usedPackages && labelOf(it).contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.exclusions_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.exclusions_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.exclusions_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (usedFiltered.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.exclusions_used_today)) }
                items(usedFiltered, key = { "used-${it.first}" }) { (pkg, millis) ->
                    AppRow(
                        catalog = catalog,
                        packageName = pkg,
                        label = labelOf(pkg),
                        detail = TimeFormat.short(millis),
                        checked = pkg in excluded,
                        onToggle = { onToggle(pkg) },
                    )
                }
            }
            if (restFiltered.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.exclusions_all)) }
                items(restFiltered, key = { "all-$it" }) { pkg ->
                    AppRow(
                        catalog = catalog,
                        packageName = pkg,
                        label = labelOf(pkg),
                        detail = null,
                        checked = pkg in excluded,
                        onToggle = { onToggle(pkg) },
                    )
                }
            }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) { Text(stringResource(R.string.done)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(
    catalog: AppCatalog,
    packageName: String,
    label: String,
    detail: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        AppIcon(catalog = catalog, packageName = packageName, size = 28)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
