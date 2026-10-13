package de.klarzeit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klarzeit.app.data.AppCatalog
import de.klarzeit.app.data.ScreenTimeRepository
import de.klarzeit.app.data.SettingsRepository
import de.klarzeit.app.widget.KlarzeitWidget
import de.klarzeit.app.widget.WidgetRefreshWorker
import kotlinx.coroutines.launch

enum class Screen { HOME, EXCLUSIONS }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember(context) { ScreenTimeRepository(context) }
    val settings = remember(context) { SettingsRepository(context) }
    val catalog = remember(context) { AppCatalog(context) }

    val today by repository.today.collectAsStateWithLifecycle()
    val week by repository.week.collectAsStateWithLifecycle(emptyList())
    val excluded by settings.excluded.collectAsStateWithLifecycle(emptySet())

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var editingGoal by remember { mutableStateOf(false) }
    // Zaehlt hoch, wenn der Nutzer sagt, er habe die Berechtigung erteilt.
    var recheck by remember { mutableIntStateOf(0) }

    // Die Liste aller Apps kostet einen Moment und aendert sich selten.
    val allApps = remember { catalog.launchableApps() }

    LaunchedEffect(recheck, excluded) {
        repository.refresh()
        KlarzeitWidget.refresh(context)
        WidgetRefreshWorker.schedule(context)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val current = today
        when {
            current == null -> Loading()

            !current.hasPermission -> PermissionScreen(onRecheck = { recheck++ })

            screen == Screen.EXCLUSIONS -> ExclusionsScreen(
                usedToday = current.summary.apps.map { it.packageName to it.millis },
                allApps = allApps,
                excluded = excluded,
                catalog = catalog,
                onToggle = { pkg -> scope.launch { settings.toggleExcluded(pkg) } },
                onBack = { screen = Screen.HOME },
            )

            else -> HomeScreen(
                today = current,
                week = week,
                catalog = catalog,
                onToggleExcluded = { pkg -> scope.launch { settings.toggleExcluded(pkg) } },
                onOpenExclusions = { screen = Screen.EXCLUSIONS },
                onEditGoal = { editingGoal = true },
                onRefresh = {
                    scope.launch {
                        repository.refresh()
                        KlarzeitWidget.refresh(context)
                    }
                },
            )
        }
    }

    if (editingGoal) {
        GoalDialog(
            goalMillis = today?.goalMillis ?: SettingsRepository.DEFAULT_GOAL_MILLIS,
            onDismiss = { editingGoal = false },
            onConfirm = { millis ->
                editingGoal = false
                scope.launch {
                    settings.setGoalMillis(millis)
                    repository.refresh()
                    KlarzeitWidget.refresh(context)
                }
            },
        )
    }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}
