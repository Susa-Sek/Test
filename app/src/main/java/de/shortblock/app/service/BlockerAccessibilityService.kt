package de.shortblock.app.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import de.shortblock.app.R
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.data.SettingsRepository
import de.shortblock.app.data.StatsRepository
import de.shortblock.app.data.WatchBudget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Der eigentliche Blocker.
 *
 * Ablauf je Ereignis: Paket prüfen → Drosselung → View-Baum holen → Regeln abgleichen →
 * Kontingent prüfen → zurücknavigieren. Drei Zeitschranken halten das billig und schleifenfrei:
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

    private val budgetClock = BudgetClock()

    @Volatile
    private var settings: BlockSettings = BlockSettings.DEFAULT

    @Volatile
    private var spentSeconds: Map<Feature, Int> = emptyMap()

    private var lastScanAt = 0L
    private var pausedUntil = 0L
    private var lastPackage: String? = null
    private var lastServiceInfoRefreshAt = 0L

    /** Für welche Features der „Kontingent aufgebraucht“-Hinweis heute schon kam. */
    private val exhaustedToastShown = mutableSetOf<Feature>()

    /** Zählt fehlgeschlagene Versuche, auf „Folge ich“ umzuschalten. */
    private var feedSwitchAttempts = 0
    private var manualSwitchHintShown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceHealth.onConnected()

        settingsRepository = SettingsRepository(applicationContext)
        statsRepository = StatsRepository(applicationContext)

        scope.launch {
            settingsRepository.settings.collect { loaded ->
                settings = loaded
                if (loaded.keepAlive) {
                    KeepAliveService.start(applicationContext)
                } else {
                    KeepAliveService.stop(applicationContext)
                }
            }
        }
        scope.launch {
            statsRepository.secondsToday.collect { spentSeconds = it }
        }
        startHeartbeat()
    }

    /**
     * Wachhund gegen den Fehler, bei dem der Dienst nach Stunden verstummt.
     *
     * `setServiceInfo` darf zur Laufzeit jederzeit neu gesetzt werden und registriert die
     * Ereignis-Konfiguration neu. Bei einer eingeschlafenen Ereignis-Pipeline ist das der
     * dokumentierte Weg, sie wieder anzustoßen — und kostet nichts, wenn ohnehin alles läuft.
     *
     * Gegen den anderen Fall, den abgeräumten Prozess, hilft das nicht; dafür gibt es die
     * Akku-Ausnahme und den freiwilligen [KeepAliveService].
     */
    private fun startHeartbeat() = scope.launch {
        while (isActive) {
            delay(HEARTBEAT_INTERVAL_MS)
            refreshServiceInfo()
            flushBudget(force = true)
        }
    }

    private fun refreshServiceInfo() {
        val info = serviceInfo ?: return
        runCatching { serviceInfo = info }
            .onSuccess { ServiceHealth.onRefreshed() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        ServiceHealth.onEvent()

        if (packageName !in Packages.WATCHED) return

        if (packageName != lastPackage) {
            lastPackage = packageName
            resetFeedState()
            // App gewechselt: Uhr anhalten, damit die Pause nicht als Sehdauer zählt.
            budgetClock.pause()
            flushBudget(force = true)
        }

        val now = SystemClock.uptimeMillis()
        if (now < pausedUntil) return
        // Browser bauen viel größere Bäume als eine Video-App und ändern sie beim Scrollen
        // ständig. Dort reicht ein deutlich ruhigerer Takt — die Adressleiste wechselt nicht
        // zehnmal pro Sekunde.
        val scanInterval = if (packageName in Packages.BROWSERS) {
            BROWSER_SCAN_INTERVAL_MS
        } else {
            SCAN_INTERVAL_MS
        }
        if (now - lastScanAt < scanInterval) return
        lastScanAt = now

        // Rückfall auf den Ereignisknoten: rootInActiveWindow liefert zeitweise null, und wer
        // dann einfach aussteigt, blockt in diesem Zustand gar nichts mehr. Ein kleinerer Baum
        // ist besser als kein Baum.
        val rootNode = rootInActiveWindow ?: event.source
        val root = rootNode?.let(::AccessibilityUiNode) ?: return

        if (settings.diagnostics) {
            DiagnosticsBuffer.record(packageName, RuleMatcher.collectSignatures(root))
        }

        val match = RuleMatcher.findFirstMatch(root, packageName, settings.enabled)
        if (match != null) {
            handleMatch(match)
            return
        }

        if (packageName == Packages.INSTAGRAM && Feature.INSTAGRAM_FEED in settings.enabled) {
            handleInstagramFeed(root)
        }

        if (packageName in Packages.TIKTOK && Feature.TIKTOK_FYP in settings.enabled) {
            handleTikTokFeed(root)
        }
    }

    /**
     * Treffer verwerten — entweder Zeit gutschreiben oder blocken.
     *
     * Ohne gesetztes Kontingent verhält sich das exakt wie vor v0.4: sofort zurück.
     */
    private fun handleMatch(match: RuleMatch) {
        val feature = match.rule.feature
        val budget = settings.budgetMinutes(feature)

        if (!WatchBudget.hasBudget(budget)) {
            BlockLog.record(match.rule.id, match.signature)
            blockAndGoBack(feature)
            return
        }

        val spent = budgetClock.tick(feature, System.currentTimeMillis(), spentSeconds[feature] ?: 0)
        flushBudget(force = false)

        if (!WatchBudget.isExhausted(spent, budget)) {
            exhaustedToastShown.remove(feature)
            return
        }

        if (exhaustedToastShown.add(feature)) {
            toast(R.string.toast_budget_spent)
        }
        BlockLog.record(match.rule.id + " (Kontingent aufgebraucht)", match.signature)
        blockAndGoBack(feature)
    }

    private fun flushBudget(force: Boolean, detached: Boolean = false) {
        val due = budgetClock.drainIfDue(SystemClock.uptimeMillis(), force)
        if (due.isEmpty()) return
        // Beim Beenden wird der Dienst-Scope gleich abgeräumt; die letzte Schreiboperation
        // bekommt deshalb einen eigenen, der das überlebt.
        val writer = if (detached) CoroutineScope(SupervisorJob() + Dispatchers.IO) else scope
        writer.launch {
            due.forEach { (feature, seconds) -> statsRepository.addSeconds(feature, seconds) }
        }
    }

    private fun handleTikTokFeed(root: UiNode) {
        val windowArea = RuleMatcher.windowArea(root)
        when (val decision = TikTokPolicy.evaluate(root)) {
            FeedDecision.AlreadyFiltered -> resetFeedState()

            is FeedDecision.ChooseFollowing -> {
                if (feedSwitchAttempts >= MAX_FEED_SWITCH_ATTEMPTS) {
                    if (!manualSwitchHintShown) {
                        manualSwitchHintShown = true
                        toast(R.string.toast_tiktok_switch_manual)
                    }
                    return
                }
                feedSwitchAttempts++
                if (Actions.clickNearest(decision.node, windowArea)) {
                    BlockLog.record("tiktok_choose_following", RuleMatcher.describe(decision.node))
                    resetFeedState()
                    pausedUntil = SystemClock.uptimeMillis() + CLICK_COOLDOWN_MS
                    scope.launch { statsRepository.increment(Feature.TIKTOK_FYP) }
                }
            }

            else -> Unit
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ServiceHealth.onDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        flushBudget(force = true, detached = true)
        ServiceHealth.onDisconnected()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 150L
        const val BROWSER_SCAN_INTERVAL_MS = 500L
        const val BACK_COOLDOWN_MS = 800L
        const val CLICK_COOLDOWN_MS = 600L
        const val MAX_FEED_SWITCH_ATTEMPTS = 2
        const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L
    }
}
