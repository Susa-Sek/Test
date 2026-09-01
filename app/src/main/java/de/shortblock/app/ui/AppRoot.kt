package de.shortblock.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.shortblock.app.R
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.data.SettingsRepository
import de.shortblock.app.data.StatsRepository
import de.shortblock.app.service.BlockLog
import de.shortblock.app.service.DiagnosticsBuffer
import de.shortblock.app.service.Feature
import de.shortblock.app.service.Health
import de.shortblock.app.service.ServiceHealth
import de.shortblock.app.service.classifyHealth
import de.shortblock.app.ui.components.StatusPill
import de.shortblock.app.system.SystemSettings
import kotlinx.coroutines.launch

enum class Screen { ONBOARDING, HOME, DIAGNOSTICS }

@Composable
fun AppRoot(openCheatOnStart: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val statsRepository = remember(context) { StatsRepository(context) }

    val settings by settingsRepository.settings.collectAsStateWithLifecycle(BlockSettings.DEFAULT)
    val counts by statsRepository.today.collectAsStateWithLifecycle(emptyMap())
    val week by statsRepository.week.collectAsStateWithLifecycle(emptyList())
    val diagnostics by DiagnosticsBuffer.entries.collectAsStateWithLifecycle()
    val blockLog by BlockLog.entries.collectAsStateWithLifecycle()
    val secondsToday by statsRepository.secondsToday.collectAsStateWithLifecycle(emptyMap())
    val healthSnapshot by ServiceHealth.state.collectAsStateWithLifecycle()
    val seenPackages by DiagnosticsBuffer.packages.collectAsStateWithLifecycle()

    var serviceEnabled by remember { mutableStateOf(SystemSettings.isServiceEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(SystemSettings.isIgnoringBatteryOptimizations(context)) }
    var onboardingSeen by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var cheatDialog by rememberSaveable { mutableStateOf(openCheatOnStart) }

    // Nach der Rückkehr aus den Systemeinstellungen den Status neu lesen — sonst zeigt die App
    // noch „Dienst ist aus“, obwohl er gerade eingeschaltet wurde.
    LifecycleResumeEffect(Unit) {
        serviceEnabled = SystemSettings.isServiceEnabled(context)
        batteryExempt = SystemSettings.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    val health = classifyHealth(serviceEnabled, healthSnapshot, System.currentTimeMillis())

    // Solange der Dienst aus ist und der Nutzer die Einrichtung noch nicht weggetippt hat,
    // ist sie das Startziel — eine Übersicht ohne laufenden Dienst zeigt nur Nullen.
    val current = if (!serviceEnabled && !onboardingSeen) Screen.ONBOARDING else screen

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Die Titelzeile beantwortet beim Scrollen die häufigste stille Frage dieser App:
            // „blockt das gerade überhaupt?“ Deshalb steht sie über allen drei Seiten.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(health)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavItem(Screen.HOME, current, Icons.Filled.Home, R.string.nav_overview) { screen = it }
                NavItem(Screen.ONBOARDING, current, Icons.Filled.Settings, R.string.nav_setup) {
                    onboardingSeen = true
                    screen = it
                }
                NavItem(Screen.DIAGNOSTICS, current, Icons.Filled.Info, R.string.nav_diagnostics) { screen = it }
            }
        },
    ) { insets ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 20.dp),
        ) {
            when (current) {
                Screen.ONBOARDING -> OnboardingScreen(
                    serviceEnabled = serviceEnabled,
                    batteryExempt = batteryExempt,
                    onOpenAppInfo = { SystemSettings.openAppInfo(context) },
                    onOpenAccessibility = { SystemSettings.openAccessibilitySettings(context) },
                    onOpenBattery = { SystemSettings.openBatterySettings(context) },
                    onContinue = {
                        onboardingSeen = true
                        screen = Screen.HOME
                    },
                )

                Screen.HOME -> HomeScreen(
                    serviceEnabled = serviceEnabled,
                    health = health,
                    settings = settings,
                    counts = counts,
                    secondsToday = secondsToday,
                    week = week,
                    lastBlock = blockLog.lastOrNull(),
                    onToggle = { feature: Feature, enabled: Boolean ->
                        scope.launch { settingsRepository.setFeatureEnabled(feature, enabled) }
                    },
                    onBudgetChange = { feature, minutes ->
                        scope.launch { settingsRepository.setBudgetMinutes(feature, minutes) }
                    },
                    onToggleSharedClips = { scope.launch { settingsRepository.setAllowSharedClips(it) } },
                    onToggleCheat = { scope.launch { settingsRepository.setCheatEnabled(it) } },
                    onToggleKeepAlive = { enabled ->
                        scope.launch { settingsRepository.setKeepAlive(enabled) }
                    },
                    onOpenBattery = { SystemSettings.openBatterySettings(context) },
                    onOpenAccessibility = { SystemSettings.openAccessibilitySettings(context) },
                    onOpenSetup = {
                        onboardingSeen = true
                        screen = Screen.ONBOARDING
                    },
                    onOpenCheat = { cheatDialog = true },
                    onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                )

                Screen.DIAGNOSTICS -> DiagnosticsScreen(
                    recording = settings.diagnostics,
                    entries = diagnostics,
                    blockLog = blockLog.asReversed(),
                    seenPackages = seenPackages,
                    onToggleRecording = { enabled ->
                        scope.launch { settingsRepository.setDiagnosticsEnabled(enabled) }
                    },
                    onClear = {
                        DiagnosticsBuffer.clear()
                        BlockLog.clear()
                    },
                )
            }

            if (cheatDialog) {
                CheatDialog(
                    settings = settings,
                    onArm = {
                        scope.launch {
                            settingsRepository.armCheat(
                                System.currentTimeMillis(),
                                java.time.LocalDate.now().toEpochDay().toInt(),
                            )
                        }
                    },
                    onDismiss = { cheatDialog = false },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    target: Screen,
    current: Screen,
    icon: ImageVector,
    labelRes: Int,
    onSelect: (Screen) -> Unit,
) {
    val label = stringResource(labelRes)
    NavigationBarItem(
        selected = current == target,
        onClick = { onSelect(target) },
        // contentDescription bleibt null: Das Label darunter ist sichtbar und wird vorgelesen,
        // eine zweite Ansage wäre für Screenreader nur Wiederholung.
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
