package de.shortblock.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.shortblock.app.R
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.data.CheatPass
import de.shortblock.app.data.DayStat
import de.shortblock.app.data.FypRelation
import de.shortblock.app.data.WatchBudget
import de.shortblock.app.service.BlockLog
import de.shortblock.app.service.Feature
import de.shortblock.app.service.Health
import de.shortblock.app.ui.components.CardTone
import de.shortblock.app.ui.components.HeroCard
import de.shortblock.app.ui.components.InfoCard
import de.shortblock.app.ui.components.SectionHeader
import de.shortblock.app.ui.components.SettingRow
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Ein Treffer gilt als „gerade eben“, solange er so jung ist. */
private const val RECENT_WINDOW_MS = 10 * 60 * 1000L

/** Der Farbpunkt je App-Gruppe. Der einzige Ort, an dem eine App eine eigene Farbe bekommt. */
private val InstagramDot = Color(0xFFE1729B)
private val YouTubeDot = Color(0xFFE05C4A)
private val TikTokDot = Color(0xFF4FC7C0)

@Composable
fun HomeScreen(
    serviceEnabled: Boolean,
    health: Health,
    settings: BlockSettings,
    counts: Map<Feature, Int>,
    secondsToday: Map<Feature, Int>,
    week: List<DayStat>,
    lastBlock: BlockLog.Entry?,
    onToggle: (Feature, Boolean) -> Unit,
    onBudgetChange: (Feature, Int) -> Unit,
    onToggleSharedClips: (Boolean) -> Unit,
    onToggleCheat: (Boolean) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val todayTotal = counts.values.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        // Reihenfolge nach Dringlichkeit. Was kaputt ist, steht oben — eine Statistik über
        // einen Dienst, der gar nicht läuft, zeigt nur Nullen.
        if (health == Health.NOT_CONNECTED) {
            ServiceAsleepCard(
                onOpenAccessibility = onOpenAccessibility,
                onOpenBattery = onOpenBattery,
                keepAlive = settings.keepAlive,
                onToggleKeepAlive = onToggleKeepAlive,
            )
        }
        if (!serviceEnabled) ServiceOffCard(onOpenSetup)

        HeroCard(todayTotal = todayTotal, week = week)

        CheatCard(settings)

        AppGroup(
            title = stringResource(R.string.group_instagram),
            dot = InstagramDot,
            rows = listOf(
                ToggleRow(
                    feature = Feature.INSTAGRAM_REELS,
                    title = stringResource(R.string.toggle_reels_title),
                    description = stringResource(R.string.toggle_reels_desc),
                ),
                ToggleRow(
                    feature = Feature.INSTAGRAM_FEED,
                    title = stringResource(R.string.toggle_feed_title),
                    description = stringResource(R.string.toggle_feed_desc),
                ),
            ),
            settings = settings,
            counts = counts,
            secondsToday = secondsToday,
            onToggle = onToggle,
            onBudgetChange = onBudgetChange,
        )

        AppGroup(
            title = stringResource(R.string.group_youtube),
            dot = YouTubeDot,
            rows = listOf(
                ToggleRow(
                    feature = Feature.YOUTUBE_SHORTS,
                    title = stringResource(R.string.toggle_shorts_title),
                    description = stringResource(R.string.toggle_shorts_desc),
                ),
            ),
            settings = settings,
            counts = counts,
            secondsToday = secondsToday,
            onToggle = onToggle,
            onBudgetChange = onBudgetChange,
        )

        AppGroup(
            title = stringResource(R.string.group_tiktok),
            dot = TikTokDot,
            rows = listOf(
                ToggleRow(
                    feature = Feature.TIKTOK_FYP,
                    title = stringResource(R.string.toggle_tiktok_fyp_title),
                    description = stringResource(R.string.toggle_tiktok_fyp_desc),
                    // Der Ganz-Block kann seit v0.4.2 selbst ein Kontingent haben. Dann ist
                    // TikTok zeitweise offen — und diese Zeile in genau dieser Zeit wirksam.
                    // Nur ohne Kontingent ist sie wirklich bedeutungslos.
                    note = when (settings.tiktokFypRelation()) {
                        FypRelation.INDEPENDENT -> null
                        FypRelation.DURING_BUDGET ->
                            stringResource(R.string.fyp_during_tiktok_budget)
                        FypRelation.OVERRIDDEN ->
                            stringResource(R.string.overridden_by_tiktok_all)
                    },
                    dimmed = settings.tiktokFypRelation() == FypRelation.OVERRIDDEN,
                ),
                ToggleRow(
                    feature = Feature.TIKTOK_ALL,
                    title = stringResource(R.string.toggle_tiktok_all_title),
                    description = stringResource(R.string.toggle_tiktok_all_desc),
                ),
            ),
            settings = settings,
            counts = counts,
            secondsToday = secondsToday,
            onToggle = onToggle,
            onBudgetChange = onBudgetChange,
        )

        SectionHeader(
            title = stringResource(R.string.group_general),
            dot = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoCard {
            SettingRow(
                title = stringResource(R.string.shared_clips_title),
                description = stringResource(R.string.shared_clips_desc),
                checked = settings.allowSharedClips,
                onCheckedChange = onToggleSharedClips,
            )
            RowDivider()
            SettingRow(
                title = stringResource(R.string.cheat_toggle_title),
                description = stringResource(R.string.cheat_toggle_desc, CheatPass.DURATION_MINUTES),
                checked = settings.cheatEnabled,
                onCheckedChange = onToggleCheat,
            )
            RowDivider()
            SettingRow(
                title = stringResource(R.string.keep_alive_title_setting),
                description = stringResource(R.string.keep_alive_desc),
                checked = settings.keepAlive,
                onCheckedChange = onToggleKeepAlive,
            )
        }

        RecentBlockRow(lastBlock = lastBlock, onOpenDiagnostics = onOpenDiagnostics)

        Text(
            text = stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 14.dp),
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun ServiceOffCard(onOpenSetup: () -> Unit) {
    InfoCard(tone = CardTone.ALERT) {
        Text(
            text = stringResource(R.string.service_inactive),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.service_inactive_hint))
        }
    }
}

private data class ToggleRow(
    val feature: Feature,
    val title: String,
    val description: String,
    /** Erklärt das Zusammenspiel mit einem anderen Schalter, falls es eines gibt. */
    val note: String? = null,
    /** Nur setzen, wenn die Zeile wirklich nichts bewirkt. */
    val dimmed: Boolean = false,
)

@Composable
private fun AppGroup(
    title: String,
    dot: Color,
    rows: List<ToggleRow>,
    settings: BlockSettings,
    counts: Map<Feature, Int>,
    secondsToday: Map<Feature, Int>,
    onToggle: (Feature, Boolean) -> Unit,
    onBudgetChange: (Feature, Int) -> Unit,
) {
    Column {
        SectionHeader(title = title, dot = dot)
        InfoCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                FeatureRow(
                    title = row.title,
                    description = row.description,
                    count = counts[row.feature] ?: 0,
                    checked = row.feature in settings.enabled,
                    onCheckedChange = { onToggle(row.feature, it) },
                    budgetMinutes = settings.budgetMinutes(row.feature),
                    spentSeconds = secondsToday[row.feature] ?: 0,
                    budgetable = row.feature in BlockSettings.BUDGETABLE,
                    onBudgetChange = { onBudgetChange(row.feature, it) },
                    note = row.note,
                    dimmed = row.dimmed,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    title: String,
    description: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    budgetMinutes: Int,
    spentSeconds: Int,
    budgetable: Boolean,
    onBudgetChange: (Int) -> Unit,
    note: String? = null,
    dimmed: Boolean = false,
) {
    Column(Modifier.alpha(if (dimmed) 0.45f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (count > 0) {
                    Text(
                        text = stringResource(R.string.blocked_today, count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }

        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Auch in der gedämpften Zeile: Der eingestellte Wert gilt, sobald der Ganz-Block
        // wieder aus ist — ihn dann zu verstecken, hieße ihn zu verstecken statt zu erklären.
        if (budgetable && checked) {
            BudgetSection(
                budgetMinutes = budgetMinutes,
                spentSeconds = spentSeconds,
                onBudgetChange = onBudgetChange,
            )
        }
    }
}

/**
 * Das Kontingent als **eine** Zeile, die sich bei Bedarf öffnet.
 *
 * Bis v0.6 standen hier fünf Chips dauerhaft nebeneinander — je Blocker. Auf einem normalen
 * Telefon brach die Reihe um, „30 Min“ fiel allein in die nächste Zeile, und die Startseite
 * war doppelt so hoch wie nötig. Der eingestellte Wert steht jetzt zusammengeklappt in der
 * Zeile, denn das ist die Information, die man beim Überfliegen sucht; die Auswahl selbst
 * braucht man selten.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetSection(
    budgetMinutes: Int,
    spentSeconds: Int,
    onBudgetChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasBudget = WatchBudget.hasBudget(budgetMinutes)
    val remaining = WatchBudget.remainingSeconds(spentSeconds, budgetMinutes)

    Column(Modifier.padding(top = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.budget_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    !hasBudget -> stringResource(R.string.budget_always)
                    remaining <= 0 -> stringResource(R.string.budget_spent)
                    else -> stringResource(R.string.budget_remaining_short, (remaining + 59) / 60, budgetMinutes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hasBudget && remaining <= 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = if (expanded) "  ⌃" else "  ⌄",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BlockSettings.BUDGET_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = minutes == budgetMinutes,
                        onClick = { onBudgetChange(minutes) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        label = {
                            Text(
                                if (minutes == 0) {
                                    stringResource(R.string.budget_always)
                                } else {
                                    stringResource(R.string.budget_minutes, minutes)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Der Stand des Tages-Cheats.
 *
 * Hier steht bewusst **kein** Knopf zum Einlösen. Der Cheat geht nur über den
 * Bedienungshilfen-Knopf am Bildschirmrand — ein Knopf an dieser Stelle wäre bequem und
 * genau deshalb falsch: Der kleine Umweg ist die ganze Hürde.
 */
@Composable
private fun CheatCard(settings: BlockSettings) {
    if (!settings.cheatEnabled) return

    // Läuft ein Cheat, muss die Restzeit sichtbar herunterlaufen — eine stehende Zahl wirkt
    // wie ein Fehler. Außerhalb eines Cheats tickt hier nichts.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = CheatPass.isActive(settings.cheatUntilMillis, now)
    LaunchedEffect(settings.cheatUntilMillis, running) {
        while (CheatPass.isActive(settings.cheatUntilMillis, System.currentTimeMillis())) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
        now = System.currentTimeMillis()
    }

    val free = CheatPass.isAvailable(settings.cheatUsedOnDay, LocalDate.now().toEpochDay().toInt())

    val title: String
    val body: String
    when {
        running -> {
            val seconds = CheatPass.remainingSeconds(settings.cheatUntilMillis, now)
            title = stringResource(
                R.string.cheat_card_running_title,
                stringResource(R.string.time_remaining, seconds / 60, seconds % 60),
            )
            body = stringResource(R.string.cheat_card_running_body)
        }

        free -> {
            title = stringResource(R.string.cheat_card_free_title, CheatPass.DURATION_MINUTES)
            body = stringResource(R.string.cheat_card_free_body)
        }

        else -> {
            title = stringResource(R.string.cheat_card_used_title)
            body = stringResource(R.string.cheat_card_used_body)
        }
    }

    InfoCard(tone = if (running) CardTone.GOOD else CardTone.NEUTRAL) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = if (running) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Der Befund, der bisher fehlte.
 *
 * Erscheint nur, wenn Android den Dienst als eingeschaltet meldet, in diesem Prozess aber kein
 * Dienst-Objekt läuft — genau der Zustand, in dem die App stillschweigend nichts mehr blockt
 * und nur Aus/Ein hilft. Reine Untätigkeit („seit Stunden kein Instagram geöffnet“) löst das
 * bewusst NICHT aus; eine App, die ständig falschen Alarm gibt, wird nicht mehr gelesen.
 */
@Composable
private fun ServiceAsleepCard(
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    keepAlive: Boolean,
    onToggleKeepAlive: (Boolean) -> Unit,
) {
    InfoCard(tone = CardTone.ALERT) {
        Text(
            text = stringResource(R.string.health_broken_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.health_broken_body),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row {
            TextButton(onClick = onOpenAccessibility) {
                Text(stringResource(R.string.step_accessibility_button))
            }
            TextButton(onClick = onOpenBattery) {
                Text(stringResource(R.string.health_battery))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.keep_alive_title_setting),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = keepAlive, onCheckedChange = onToggleKeepAlive)
        }
    }
}

/**
 * Dezenter Hinweis statt Debug-Karte.
 *
 * Die vollständige Liste steht in der Diagnose. Auf der Startseite erscheint nur dann etwas,
 * wenn gerade wirklich etwas passiert ist — und dann als Abkürzung dorthin.
 */
@Composable
private fun RecentBlockRow(lastBlock: BlockLog.Entry?, onOpenDiagnostics: () -> Unit) {
    if (lastBlock == null) return
    val ageMs = System.currentTimeMillis() - lastBlock.atMillis
    if (ageMs !in 0..RECENT_WINDOW_MS) return

    val ageMinutes = (ageMs / 60_000L).toInt()
    val ageLabel = if (ageMinutes < 1) {
        stringResource(R.string.just_now)
    } else {
        stringResource(R.string.minutes_short, ageMinutes)
    }

    TextButton(onClick = onOpenDiagnostics) {
        Text(
            text = stringResource(R.string.recent_block_chip, lastBlock.ruleId, ageLabel),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
