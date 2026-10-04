package de.shortblock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
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

        // Der frühere Zähler „Schritt 1 von 3“ ist weg: Die Leiste sagt dasselbe, ohne es zu
        // buchstabieren — und sie musste ihn ohnehin belügen, weil Schritt 1 nicht prüfbar ist.
        // Ob „Eingeschränkte Einstellungen zulassen“ gewählt wurde, verrät Android der App
        // nicht — Schritt 1 bleibt deshalb dauerhaft ohne Häkchen.
        StepCard(
            number = 1,
            title = stringResource(R.string.step_restricted_title),
            body = stringResource(R.string.step_restricted_body),
            buttonLabel = stringResource(R.string.step_restricted_button),
            done = false,
            showDoneState = false,
            onClick = onOpenAppInfo,
        )
        StepCard(
            number = 2,
            title = stringResource(R.string.step_accessibility_title),
            body = stringResource(R.string.step_accessibility_body),
            buttonLabel = stringResource(R.string.step_accessibility_button),
            done = serviceEnabled,
            showDoneState = true,
            onClick = onOpenAccessibility,
        )
        StepCard(
            number = 3,
            title = stringResource(R.string.step_battery_title),
            body = stringResource(R.string.step_battery_body),
            buttonLabel = stringResource(R.string.step_battery_button),
            done = batteryExempt,
            showDoneState = true,
            isLast = true,
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
    number: Int,
    title: String,
    body: String,
    buttonLabel: String,
    done: Boolean,
    showDoneState: Boolean,
    isLast: Boolean = false,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Die senkrechte Leiste: Punkt plus Linie zum nächsten Schritt. Sie zeigt auf einen
        // Blick, wo man steht — und macht drei gleich aussehende Karten zu einem Weg.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = if (done) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (showDoneState && done) "✓" else number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (done) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(2.dp)
                        .height(if (done) 96.dp else 112.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }

        Column(Modifier.weight(1f).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Ehrlichkeit statt Häkchen: Android verrät nicht, ob „Eingeschränkte
                // Einstellungen zulassen“ gewählt wurde. Ein leerer Kreis sähe aus wie
                // „noch nicht erledigt“ und wäre damit eine Lüge.
                if (!showDoneState) {
                    Text(
                        text = stringResource(R.string.step_not_checkable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(buttonLabel)
            }
        }
    }
}
