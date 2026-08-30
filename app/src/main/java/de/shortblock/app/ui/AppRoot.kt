package de.shortblock.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.data.SettingsRepository
import de.shortblock.app.data.StatsRepository
import de.shortblock.app.service.BlockLog
import de.shortblock.app.service.DiagnosticsBuffer
import de.shortblock.app.service.Feature
import de.shortblock.app.system.SystemSettings
import kotlinx.coroutines.launch

enum class Screen { ONBOARDING, HOME, DIAGNOSTICS }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val statsRepository = remember(context) { StatsRepository(context) }

    val settings by settingsRepository.settings.collectAsStateWithLifecycle(BlockSettings.DEFAULT)
    val counts by statsRepository.today.collectAsStateWithLifecycle(emptyMap())
    val diagnostics by DiagnosticsBuffer.entries.collectAsStateWithLifecycle()
    val blockLog by BlockLog.entries.collectAsStateWithLifecycle()

    var serviceEnabled by remember { mutableStateOf(SystemSettings.isServiceEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(SystemSettings.isIgnoringBatteryOptimizations(context)) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var onboardingSeen by rememberSaveable { mutableStateOf(false) }

    // Nach der Rückkehr aus den Systemeinstellungen den Status neu lesen — sonst zeigt die App
    // noch „Dienst ist aus“, obwohl er gerade eingeschaltet wurde.
    LifecycleResumeEffect(Unit) {
        serviceEnabled = SystemSettings.isServiceEnabled(context)
        batteryExempt = SystemSettings.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    val effectiveScreen = when {
        screen == Screen.DIAGNOSTICS -> Screen.DIAGNOSTICS
        screen == Screen.ONBOARDING -> Screen.ONBOARDING
        !serviceEnabled && !onboardingSeen -> Screen.ONBOARDING
        else -> Screen.HOME
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .consumeWindowInsets(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            when (effectiveScreen) {
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
                    settings = settings,
                    counts = counts,
                    lastBlock = blockLog.lastOrNull(),
                    onToggle = { feature: Feature, enabled: Boolean ->
                        scope.launch { settingsRepository.setFeatureEnabled(feature, enabled) }
                    },
                    onOpenSetup = { screen = Screen.ONBOARDING },
                    onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                )

                Screen.DIAGNOSTICS -> DiagnosticsScreen(
                    recording = settings.diagnostics,
                    entries = diagnostics,
                    onToggleRecording = { enabled ->
                        scope.launch { settingsRepository.setDiagnosticsEnabled(enabled) }
                    },
                    onClear = { DiagnosticsBuffer.clear() },
                    onBack = { screen = Screen.HOME },
                )
            }
        }
    }
}
