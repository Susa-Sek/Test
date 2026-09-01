package de.shortblock.app.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import de.shortblock.app.R
import de.shortblock.app.data.BlockSettings
import de.shortblock.app.data.CheatPass
import de.shortblock.app.data.CheatStage
import de.shortblock.app.data.SettingsRepository
import de.shortblock.app.data.StatsRepository
import de.shortblock.app.data.WatchBudget
import de.shortblock.app.data.WatchdogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    /**
     * Zustand der Ausnahme „geteiltes Video“ — siehe [SharedClip].
     *
     * [sawOwnScreenSinceEntry] ist der Kern: Wer den Reels-Tab antippt, sieht vorher die
     * Startseite. War der Viewer dagegen das Erste nach dem Wechsel in die App, kam er von
     * außen.
     */
    private var sawOwnScreenSinceEntry = false
    private var directSeenAt = 0L
    private var sharedWatchStartedAt = 0L
    private var sharedWatchSwipes = 0
    private var sharedWatchActive = false

    /** Für welche Features der „Kontingent aufgebraucht“-Hinweis heute schon kam. */
    private val exhaustedToastShown = mutableSetOf<Feature>()

    private var overlay: ReminderOverlay? = null

    /**
     * Der Bedienungshilfen-Knopf.
     *
     * `AccessibilityService` hat dafür keine überschreibbare Methode — der Druck kommt über
     * diesen Callback am [AccessibilityButtonController] an, und erst durch
     * `flagRequestAccessibilityButton` in der Dienst-Konfiguration überhaupt hier an statt den
     * Dienst an- und auszuschalten.
     */
    private val cheatButton = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) = onCheatButtonPressed()
    }

    /** Zuletzt gezeigter Spruch, damit sich keiner direkt wiederholt. */
    private var lastReminderIndex = -1
    private var lastReminderAt = 0L

    /** Zählt fehlgeschlagene Versuche, auf „Folge ich“ umzuschalten. */
    private var feedSwitchAttempts = 0
    private var manualSwitchHintShown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceHealth.onConnected()

        settingsRepository = SettingsRepository(applicationContext)
        statsRepository = StatsRepository(applicationContext)
        overlay = ReminderOverlay(this)
        runCatching { accessibilityButtonController.registerAccessibilityButtonCallback(cheatButton) }

        // Der Wächter darf erst dann Alarm schlagen, wenn der Dienst wirklich einmal lief.
        // Diese Zeile ist die einzige Stelle, an der das feststeht.
        scope.launch { WatchdogState(applicationContext).onServiceRunning() }
        ServiceWatchdogWorker.schedule(applicationContext)

        scope.launch {
            var lastDiagnostics: Boolean? = null
            settingsRepository.settings.collect { loaded ->
                settings = loaded
                if (loaded.keepAlive) {
                    KeepAliveService.start(applicationContext)
                } else {
                    KeepAliveService.stop(applicationContext)
                }
                if (loaded.diagnostics != lastDiagnostics) {
                    lastDiagnostics = loaded.diagnostics
                    applyPackageScope(wide = loaded.diagnostics)
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

    /**
     * Weitet den Empfang auf alle Apps oder engt ihn wieder auf [Packages.WATCHED] ein.
     *
     * Nötig, weil der Dienst sonst für ein unbekanntes Paket — etwa TikTok Lite — gar keine
     * Ereignisse bekommt und deshalb auch nicht melden kann, dass eines fehlt. Die Weitung
     * kostet spürbar Rechenzeit und ist deshalb an die bewusst eingeschaltete Aufzeichnung
     * gekoppelt, nicht an den Dauerbetrieb.
     */
    private fun applyPackageScope(wide: Boolean) {
        val info = serviceInfo ?: return
        info.packageNames = if (wide) null else Packages.WATCHED.toTypedArray()
        runCatching { serviceInfo = info }
    }

    private fun refreshServiceInfo() {
        val info = serviceInfo ?: return
        runCatching { serviceInfo = info }
            .onSuccess { ServiceHealth.onRefreshed() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        ServiceHealth.onEvent()

        // Bewusst VOR der WATCHED-Prüfung: Genau die unbekannten Pakete sind die, die man sehen
        // will. Solange die Aufzeichnung läuft, empfängt der Dienst Ereignisse aller Apps.
        if (settings.diagnostics) DiagnosticsBuffer.recordPackage(packageName)

        // Der Paketwechsel wird bewusst VOR der WATCHED-Prüfung verbucht. Sonst bliebe ein
        // Ausflug nach WhatsApp unsichtbar, und die Rückkehr in Instagram sähe aus wie „war
        // immer schon da“ — womit ein aus WhatsApp geöffnetes Reel nicht als geteilt gälte.
        if (packageName != lastPackage) {
            lastPackage = packageName
            resetFeedState()
            resetSharedWatch()
            // App gewechselt: Uhr anhalten, damit die Pause nicht als Sehdauer zählt.
            budgetClock.pause()
            flushBudget(force = true)
        }

        // Geblockt wird ausschließlich bei überwachten Paketen. Die Weitung dient dem
        // Zusehen, nie dem Eingreifen.
        if (packageName !in Packages.WATCHED) return

        // Ein Wisch in der Seitenliste beendet die Ausnahme. Der Kommentar-Bereich scrollt
        // ebenfalls und ist deshalb durch das Muster-Gatter ausgeschlossen.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (sharedWatchActive && SharedClip.isSwipeToNext(event.source?.viewIdResourceName)) {
                sharedWatchSwipes++
            }
            return
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

        trackScreen(root, packageName)

        val match = RuleMatcher.findFirstMatch(root, packageName, settings.enabled)
        if (match != null) {
            val intervened = handleMatch(match)
            // Läuft „TikTok ganz blocken“ gerade auf Kontingent, ist TikTok bewusst offen — dann
            // muss der „Für dich“-Filter mit seinem eigenen Kontingent weiterlaufen. Nur für
            // diesen Fall wird weitergeschaut; sonst bleibt ein Treffer das Ende der Kette.
            // Bewusst eng: Ein allgemeines Durchfallen ließe die Instagram-Feed-Policy im
            // laufenden Reels-Kontingent tippen — Fehlalarmrisiko ohne Gegenwert.
            if (intervened || match.rule.feature != Feature.TIKTOK_ALL) return
        }

        if (packageName == Packages.INSTAGRAM && Feature.INSTAGRAM_FEED in settings.enabled) {
            handleInstagramFeed(root)
        }

        if (packageName in Packages.TIKTOK && Feature.TIKTOK_FYP in settings.enabled) {
            handleTikTokFeed(root)
        }
    }

    /**
     * Merkt sich, was vor dem Viewer auf dem Schirm war.
     *
     * Läuft vor jeder Regelprüfung, weil die Herkunft nur aus dem Bildschirm *davor* ablesbar
     * ist. Sobald der Viewer selbst sichtbar ist, wird nichts mehr gemerkt — sonst überschriebe
     * er die Herkunft, die er gerade beantworten soll.
     */
    private fun trackScreen(root: UiNode, packageName: String) {
        val onViewer = RuleMatcher.containsNode(root) { node ->
            val viewId = normalizeForMatch(node.viewId) ?: return@containsNode false
            Rules.SharedClip.PAGER_VIEW_IDS.any { viewId.contains(it) }
        }
        if (onViewer) return

        // Läuft gerade eine Ausnahme, beendet sie nur der Wisch, die Reißleine oder ein
        // App-Wechsel — nicht irgendein Bildschirm, der den Viewer bloß überdeckt. Ohne diese
        // Bedingung würde das Öffnen der Kommentare mitten im geteilten Reel als „im App
        // navigiert“ gelten und beim Zurückkehren sofort rauswerfen.
        if (!sharedWatchActive) {
            sawOwnScreenSinceEntry = true
            sharedWatchStartedAt = 0L
            sharedWatchSwipes = 0
        }

        if (packageName != Packages.INSTAGRAM) return
        val inDirect = RuleMatcher.containsNode(root) { node ->
            val viewId = normalizeForMatch(node.viewId) ?: return@containsNode false
            Rules.SharedClip.DIRECT_VIEW_IDS.any { viewId.contains(it) }
        }
        if (inDirect) directSeenAt = System.currentTimeMillis()
    }

    private fun resetSharedWatch() {
        sawOwnScreenSinceEntry = false
        sharedWatchActive = false
        sharedWatchStartedAt = 0L
        sharedWatchSwipes = 0
    }

    /**
     * Darf dieser Treffer als geteiltes Video durchgehen?
     *
     * Nur für Reels und Shorts — der TikTok-Ganzblock und der Feed-Filter bleiben unberührt.
     * Die eigentliche Regel steht in [SharedClip]; hier liegt nur der Zustand.
     */
    private fun allowsSharedClip(feature: Feature): Boolean {
        if (!settings.allowSharedClips) return false
        if (feature != Feature.INSTAGRAM_REELS && feature != Feature.YOUTUBE_SHORTS) return false

        val now = System.currentTimeMillis()
        val fromShare = SharedClip.cameFromShare(sawOwnScreenSinceEntry, directSeenAt, now)
        if (!SharedClip.mayWatch(fromShare, sharedWatchSwipes, sharedWatchStartedAt, now)) {
            sharedWatchActive = false
            return false
        }

        if (!sharedWatchActive) {
            sharedWatchActive = true
            sharedWatchStartedAt = now
            BlockLog.record("shared_clip_allowed", "einmal ansehen, Wisch blockt")
        }
        return true
    }

    /**
     * **Die einzige Stelle, an der über das Kontingent entschieden wird.**
     *
     * Das ist der Kern der Reparatur in v0.4.1: Vorher stand diese Logik nur im Regel-Pfad, und
     * TikToks „Für dich“ läuft über eine Policy statt über eine Regel — dort verpuffte jedes
     * eingestellte Kontingent wortlos. Beide Pfade rufen jetzt hierher.
     *
     * @return `true`, wenn eingegriffen werden soll (blocken bzw. umschalten); `false`, solange
     *   noch Kontingent übrig ist. Ohne gesetztes Kontingent immer `true` — das ist die
     *   Voreinstellung und das Verhalten vor v0.4.
     */
    private fun shouldIntervene(feature: Feature): Boolean {
        // Ein laufender Cheat hebt die **Sperre** auf — auch den TikTok-Ganz-Block, so war
        // „für alles“ gemeint. Die **Uhr** hebt er seit v0.8 ausdrücklich NICHT auf: Sie tickt
        // unten weiter, ihr Ergebnis wird nur nicht mehr zum Blocken benutzt. Damit kosten die
        // fünf Minuten Tageskontingent, statt geschenkt zu sein. (Bis v0.7 stand hier ein
        // vorgezogenes `return false` — die Umkehr ist gewollt, siehe CLAUDE.md.)
        val cheating = CheatPass.stage(
            settings.cheatArmedAtMillis,
            settings.cheatUsedOnDay,
            today(),
            System.currentTimeMillis(),
        ) == CheatStage.RUNNING

        val budget = settings.budgetMinutes(feature)
        if (!WatchBudget.hasBudget(budget)) return !cheating

        val spent = budgetClock.tick(feature, System.currentTimeMillis(), spentSeconds[feature] ?: 0)
        flushBudget(force = false)
        if (cheating) return false

        if (!WatchBudget.isExhausted(spent, budget)) {
            exhaustedToastShown.remove(feature)
            return false
        }

        if (exhaustedToastShown.add(feature)) {
            toast(R.string.toast_budget_spent)
        }
        return true
    }

    /** @return `true`, wenn tatsächlich geblockt wurde; `false`, wenn noch etwas erlaubt ist. */
    private fun handleMatch(match: RuleMatch): Boolean {
        val feature = match.rule.feature
        if (allowsSharedClip(feature)) return false
        if (!shouldIntervene(feature)) return false

        val spent = settings.budgetMinutes(feature) > 0
        BlockLog.record(
            if (spent) match.rule.id + " (Kontingent aufgebraucht)" else match.rule.id,
            match.signature,
        )
        blockAndGoBack(feature)
        return true
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
                // Kontingent zuerst: Solange Zeit übrig ist, darf „Für dich“ laufen und die Uhr
                // tickt. Erst wenn es aufgebraucht ist, wird umgeschaltet.
                if (!shouldIntervene(Feature.TIKTOK_FYP)) return

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
        showReminder()
    }

    /**
     * Der Spruch nach einem Block.
     *
     * Gedrosselt, und zwar deutlich: Nach einem Block läuft nur [BACK_COOLDOWN_MS] = 800 ms.
     * Ein Popup in diesem Takt wäre unerträglich — und wer eine Meldung wegwischt, ohne sie zu
     * lesen, liest auch die nächste nicht. Dazwischen wird still geblockt wie bisher.
     */
    private fun showReminder() {
        val now = SystemClock.uptimeMillis()
        if (now - lastReminderAt < REMINDER_COOLDOWN_MS) return
        lastReminderAt = now

        val lines = resources.getStringArray(R.array.reminder_lines)
        val index = Reminders.next(lines.size, lastReminderIndex)
        if (index !in lines.indices) return
        lastReminderIndex = index

        // Der Cheat-Hinweis nur, solange er auch einzulösen ist. Ein Angebot, das nicht gilt,
        // macht aus der Erinnerung eine Verhöhnung.
        val detail = if (cheatIsFree()) getString(R.string.overlay_cheat_hint) else null
        if (overlay?.show(lines[index], detail) != true) toast(R.string.toast_blocked)
    }

    /**
     * Der Bedienungshilfen-Knopf ist der einzige Weg zum Cheat.
     *
     * Ausdrücklich **nicht** im Popup: Ein Knopf direkt unter dem Spruch wäre nach drei Tagen
     * Reflex. So muss man den Satz lesen und danach eine andere Geste machen — das ist die
     * ganze Hürde, und sie ist der Sinn der Sache.
     */
    private fun onCheatButtonPressed() {
        val now = System.currentTimeMillis()
        val stage = CheatPass.stage(
            settings.cheatArmedAtMillis,
            settings.cheatUsedOnDay,
            today(),
            now,
        )

        if (!settings.cheatEnabled) {
            popup(getString(R.string.cheat_off_title), getString(R.string.cheat_off_body))
            return
        }

        when (stage) {
            CheatStage.RUNNING -> {
                val minutes = (CheatPass.runRemainingSeconds(settings.cheatArmedAtMillis, now) + 59) / 60
                popup(getString(R.string.cheat_running_title), getString(R.string.cheat_running_body, minutes))
            }

            CheatStage.WAITING -> {
                val seconds = CheatPass.waitRemainingSeconds(settings.cheatArmedAtMillis, now)
                popup(getString(R.string.cheat_waiting_title), getString(R.string.cheat_waiting_body, seconds))
            }

            CheatStage.USED ->
                popup(getString(R.string.cheat_used_title), getString(R.string.cheat_used_body))

            // Der Knopf gewährt seit v0.8 nichts mehr — er führt nur noch zur Tür. Abgetippt
            // und gewartet wird in der App; ein Tastaturfeld im Fenster über Instagram würde
            // der App darunter die Eingabe klauen.
            CheatStage.FREE -> if (!openCheatRequest()) {
                popup(getString(R.string.cheat_open_app_title), getString(R.string.cheat_open_app_body))
            }
        }
    }

    /** @return false, wenn sich die App nicht öffnen ließ — dann bleibt nur der Hinweis. */
    private fun openCheatRequest(): Boolean = runCatching {
        startActivity(
            android.content.Intent(this, de.shortblock.app.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(de.shortblock.app.MainActivity.EXTRA_OPEN_CHEAT, true),
        )
        true
    }.getOrDefault(false)

    private fun cheatIsFree(): Boolean = settings.cheatEnabled && CheatPass.stage(
        settings.cheatArmedAtMillis,
        settings.cheatUsedOnDay,
        today(),
        System.currentTimeMillis(),
    ) == CheatStage.FREE

    /** Antwort auf eine Handlung — anders als der Spruch nach einem Block nie gedrosselt. */
    private fun popup(title: String, body: String) {
        if (overlay?.show(title, body) != true) {
            Toast.makeText(this, "$title — $body", Toast.LENGTH_LONG).show()
        }
    }

    private fun today(): Int = LocalDate.now().toEpochDay().toInt()

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
        runCatching { accessibilityButtonController.unregisterAccessibilityButtonCallback(cheatButton) }
        overlay?.hide()
        overlay = null
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
        const val REMINDER_COOLDOWN_MS = 20_000L
    }
}
