package de.shortblock.app.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import de.shortblock.app.R
import de.shortblock.app.data.SettingsRepository
import de.shortblock.app.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Der eigentliche Blocker.
 *
 * Ablauf je Ereignis: Paket prüfen → Drosselung → View-Baum holen → Regeln abgleichen →
 * zurücknavigieren. Drei Zeitschranken halten das billig und schleifenfrei:
 *
 *  - [SCAN_INTERVAL_MS]: höchstens ein Baum-Scan pro Intervall. YouTube feuert im Player
 *    dutzende Content-Change-Events pro Sekunde.
 *  - [BACK_COOLDOWN_MS]: nach einem ausgelösten Zurück passiert eine Weile nichts. Ohne das
 *    entsteht eine Back-Schleife, die den Nutzer komplett aus der App wirft.
 *  - [CLICK_COOLDOWN_MS]: nach einem Tipp auf den Feed-Umschalter braucht Instagram Zeit,
 *    das Menü aufzubauen.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var statsRepository: StatsRepository

    @Volatile
    private var enabledFeatures: Set<Feature> = Feature.entries.toSet()

    @Volatile
    private var diagnosticsEnabled: Boolean = false

    private var lastScanAt = 0L
    private var pausedUntil = 0L
    private var lastPackage: String? = null

    /** Zählt fehlgeschlagene Versuche, auf „Folge ich“ umzuschalten. */
    private var feedSwitchAttempts = 0
    private var manualSwitchHintShown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(applicationContext)
        statsRepository = StatsRepository(applicationContext)
        scope.launch {
            settingsRepository.settings.collect { settings ->
                enabledFeatures = settings.enabled
                diagnosticsEnabled = settings.diagnostics
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in Packages.WATCHED) return

        if (packageName != lastPackage) {
            lastPackage = packageName
            resetFeedState()
        }

        val now = SystemClock.uptimeMillis()
        if (now < pausedUntil) return
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = now

        val root = rootInActiveWindow?.let(::AccessibilityUiNode) ?: return

        if (diagnosticsEnabled) {
            DiagnosticsBuffer.record(packageName, RuleMatcher.collectSignatures(root))
        }

        val match = RuleMatcher.findFirstMatch(root, packageName, enabledFeatures)
        if (match != null) {
            BlockLog.record(match.rule.id, match.signature)
            blockAndGoBack(match.rule.feature)
            return
        }

        if (packageName == Packages.INSTAGRAM && Feature.INSTAGRAM_FEED in enabledFeatures) {
            handleInstagramFeed(root)
        }
    }

    private fun handleInstagramFeed(root: UiNode) {
        val windowArea = RuleMatcher.windowArea(root)
        when (val decision = FeedPolicy.evaluate(root)) {
            FeedDecision.Idle -> Unit

            FeedDecision.AlreadyFiltered -> resetFeedState()

            is FeedDecision.OpenSwitcher -> {
                if (feedSwitchAttempts >= MAX_FEED_SWITCH_ATTEMPTS) {
                    // Zweimal daneben — ab hier lieber ehrlich sein als weiter blind tippen.
                    if (!manualSwitchHintShown) {
                        manualSwitchHintShown = true
                        toast(R.string.toast_feed_switch_manual)
                    }
                    return
                }
                feedSwitchAttempts++
                if (Actions.clickNearest(decision.node, windowArea)) {
                    BlockLog.record("ig_feed_open_switcher", RuleMatcher.describe(decision.node))
                    pausedUntil = SystemClock.uptimeMillis() + CLICK_COOLDOWN_MS
                }
            }

            is FeedDecision.ChooseFollowing -> {
                if (Actions.clickNearest(decision.node, windowArea)) {
                    BlockLog.record("ig_feed_choose_following", RuleMatcher.describe(decision.node))
                    resetFeedState()
                    pausedUntil = SystemClock.uptimeMillis() + CLICK_COOLDOWN_MS
                }
            }

            is FeedDecision.EndOfFeed -> {
                BlockLog.record("ig_feed_end", decision.marker)
                toast(R.string.toast_feed_end)
                blockAndGoBack(Feature.INSTAGRAM_FEED)
            }
        }
    }

    private fun blockAndGoBack(feature: Feature) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        pausedUntil = SystemClock.uptimeMillis() + BACK_COOLDOWN_MS
        scope.launch { statsRepository.increment(feature) }
    }

    private fun resetFeedState() {
        feedSwitchAttempts = 0
        manualSwitchHintShown = false
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 150L
        const val BACK_COOLDOWN_MS = 800L
        const val CLICK_COOLDOWN_MS = 600L
        const val MAX_FEED_SWITCH_ATTEMPTS = 2
    }
}
