package de.shortblock.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.shortblock.app.R

@Composable
fun OnboardingScreen(
    serviceEnabled: Boolean,
    batteryExempt: Boolean,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Schritt 1 lässt sich nicht abfragen: ob „Eingeschränkte Einstellungen zulassen“
        // gewählt wurde, verrät Android der App nicht. Deshalb ohne Häkchen.
        StepCard(
            title = stringResource(R.string.step_restricted_title),
            body = stringResource(R.string.step_restricted_body),
            buttonLabel = stringResource(R.string.step_restricted_button),
            done = false,
            showDoneState = false,
            onClick = onOpenAppInfo,
        )
        StepCard(
            title = stringResource(R.string.step_accessibility_title),
            body = stringResource(R.string.step_accessibility_body),
            buttonLabel = stringResource(R.string.step_accessibility_button),
            done = serviceEnabled,
            showDoneState = true,
            onClick = onOpenAccessibility,
        )
        StepCard(
            title = stringResource(R.string.step_battery_title),
            body = stringResource(R.string.step_battery_body),
            buttonLabel = stringResource(R.string.step_battery_button),
            done = batteryExempt,
            showDoneState = true,
            onClick = onOpenBattery,
        )

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    buttonLabel: String,
    done: Boolean,
    showDoneState: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (showDoneState && done) {
                    Text(
                        text = "✓ " + stringResource(R.string.step_done),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
                Text(buttonLabel)
            }
        }
    }
}
