package de.shortblock.app.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
     * Zustand der Ausnahme „ein Video, kein Feed“ — siehe [SharedClip].
     *
     * Seit v0.9 nur noch drei Felder. Die Herkunftserkennung davor (welcher Bildschirm war vor
     * dem Viewer zu sehen, wie alt war der DM-Verlauf) ist ersatzlos entfallen: Sie beantwortete
     * die falsche Frage.
     */
    private var singleWatchStartedAt = 0L
    private var singleWatchSwipes = 0
    private var singleWatchActive = false
    /** Zuletzt gemeldeter Listenindex der Seitenliste, -1 = noch keiner. */
    private var singleWatchIndex = -1
    /** Zuletzt protokollierter Ablehnungsgrund — verhindert, dass das Protokoll überläuft. */
    private var singleWatchLastReason: String? = null

    /**
     * Zustand der Zurück-Obergrenze — die Regel dazu steht in [BackGuard].
     *
     * `interventionPausedUntil` liegt bewusst auf der Wanduhr, nicht auf `uptimeMillis`: Es
     * wird mit [BackGuard.PAUSE_MS] verglichen, und beides muss dieselbe Zeitbasis haben.
     */
    private var backChainLength = 0
    private var lastBackAtMs = 0L
    private var interventionPausedUntil = 0L

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

    /** Seit wann das Explore-Raster vorne steht; 0 heisst: steht es nicht. */
    private var exploreSince = 0L

    /** Steht der „Für dich“-Feed gerade vorne? */
    private var forYouActive = false

    /**
     * Zustand der Feed-Eskalation — die Regeln dazu stehen in [FeedGuard].
     *
     * `lastBrakeAtMs` liegt auf der Wanduhr, weil [FeedGuard.isEcho] damit rechnet. Alle drei
     * werden in [resetFeedState] zurückgesetzt: beim Paketwechsel und bei jedem Scan, der
     * nicht mehr den algorithmischen Feed meldet.
     */
    private var feedTapsTried = 0
    private var feedBrakesUsed = 0
    private var lastBrakeAtMs = 0L
    /** Zuletzt protokollierter Grund, warum die Bremse nicht griff. */
    private var lastBrakeReason: String? = null

    /**
     * Weckt die Bedienungshilfe beim Entsperren.
     *
     * Der Grund, warum die Sperre morgens manchmal nicht zog: Die einzige Reparatur hing am
     * Herzschlag, einem `delay` in einer Koroutine — und genau solche Timer setzt Doze über
     * Nacht aus. Hier kommt sie in dem Moment, in dem sie gebraucht wird: entsperren,
     * reparieren, dann erst wird die erste App geöffnet.
     */
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val silence = System.currentTimeMillis() - ServiceHealth.state.value.lastEventAtMs
            refreshServiceInfo()
            BlockLog.record("service_repair", "wake after ${silence / 1000}s")
        }
    }

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
        // Zur Laufzeit registriert: Diese beiden Broadcasts nimmt Android seit Oreo nicht
        // mehr aus dem Manifest entgegen, aus einem laufenden Dienst heraus schon.
        runCatching {
            registerReceiver(
                wakeReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
            )
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

        // Vor der Auswertung: War es lange still, ist die Pipeline womöglich eingeschlafen
        // und liefert einen veralteten Baum. Der Empfänger oben deckt das Entsperren ab —
        // das hier den Fall, dass der Dienst erst danach neu verbunden wurde.
        val nowMs = System.currentTimeMillis()
        val health = ServiceHealth.state.value
        if (WakeRepair.needsRepair(health.lastEventAtMs, health.lastRefreshAtMs, nowMs)) {
            refreshServiceInfo()
            BlockLog.record("service_repair", "gap ${(nowMs - health.lastEventAtMs) / 1000}s")
        }

        ServiceHealth.onEvent()

        // Bewusst VOR der WATCHED-Prüfung: Genau die unbekannten Pakete sind die, die man sehen
        // will. Solange die Aufzeichnung läuft, empfängt der Dienst Ereignisse aller Apps.
        if (settings.diagnostics) DiagnosticsBuffer.recordPackage(packageName)

        if (packageName != lastPackage) {
            lastPackage = packageName
            resetFeedState()
            resetSingleWatch()
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
            if (singleWatchActive) {
                val fromPager = SharedClip.isFromPager(event.source?.viewIdResourceName, packageName)
                val index = event.fromIndex
                val since = System.currentTimeMillis() - singleWatchStartedAt
                if (SharedClip.countsAsSwipe(fromPager, index, singleWatchIndex, since)) {
                    singleWatchSwipes++
                }
                if (fromPager && index >= 0) singleWatchIndex = index
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

        val match = RuleMatcher.findFirstMatch(root, packageName, settings.enabled)
        if (match != null) {
            val intervened = handleMatch(match, root, packageName)
            // Läuft „TikTok ganz blocken“ gerade auf Kontingent, ist TikTok bewusst offen — dann
            // muss der „Für dich“-Filter mit seinem eigenen Kontingent weiterlaufen. Nur für
            // diesen Fall wird weitergeschaut; sonst bleibt ein Treffer das Ende der Kette.
            // Bewusst eng: Ein allgemeines Durchfallen ließe die Instagram-Feed-Policy im
            // laufenden Reels-Kontingent tippen — Fehlalarmrisiko ohne Gegenwert.
            if (intervened || match.rule.feature != Feature.TIKTOK_ALL) return
        } else {
            // Kein Viewer mehr auf dem Schirm: Die nächste Auswahl beginnt von vorn. Das
            // ersetzt das frühere `trackScreen` — überdeckt ein Bildschirm den Viewer kurz
            // (Kommentare), startet danach nur das Zeitfenster neu. Das ist großzügiger als
            // nötig, aber es wirft niemanden mitten im Video hinaus.
            resetSingleWatch()
        }

        if (packageName == Packages.INSTAGRAM && Feature.INSTAGRAM_FEED in settings.enabled) {
            handleInstagramFeed(root)
        }

        if (packageName == Packages.INSTAGRAM && Feature.INSTAGRAM_EXPLORE in settings.enabled) {
            handleInstagramExplore(root)
        }

        if (packageName in Packages.TIKTOK && Feature.TIKTOK_FYP in settings.enabled) {
            handleTikTokFeed(root)
        }
    }

    private fun resetSingleWatch() {
        singleWatchActive = false
        singleWatchStartedAt = 0L
        singleWatchSwipes = 0
        singleWatchIndex = -1
        singleWatchLastReason = null
    }

    /**
     * Darf dieser Treffer als bewusst ausgewähltes Video durchgehen?
     *
     * Nur für Reels und Shorts — der TikTok-Ganzblock und der Feed-Filter bleiben unberührt.
     * Die eigentliche Regel steht in [SharedClip]; hier liegt nur der Zustand.
     *
     * Zwei **positive** Nachweise, sonst wird geblockt — die Ausnahme fällt nach unten zu.
     *
     * **Was bis v0.11.2 falsch war, und warum es alles durchliess:** Für YouTube stand hier
     * `match.rule.id != "yt_shorts_tab_selected"`. [RuleMatcher.findFirstMatch] gibt aber die
     * **erste** Regel der Liste zurück, und `yt_shorts_player` steht in [Rules] vor der
     * Tab-Regel. Im Shorts-Tab greift also immer der Player zuerst, `match.rule.id` war nie die
     * Tab-Regel, jeder Short galt als bewusst ausgewählt. Der Unterscheider war toter Code —
     * und weil [BlockSettings.allowSingleClip] per Vorgabe an ist, blockte für Reels und Shorts
     * gar nichts mehr. Wer die Entscheidung wieder an einer Regel-ID festmacht, holt das zurück.
     *
     * Gefragt wird deshalb der Bildschirm, nicht die Regel.
     *
     * **Seit v0.11.5 gilt die Ausnahme nur noch für Instagram.** Bei YouTube liess sie beide
     * Wege durch — den Short aus dem Regal (so gebaut) und den aus dem Shorts-Tab (nicht so
     * gebaut). Der Tab wird nämlich nur als Algorithmus-Strom erkannt, wenn ein Knoten
     * `isSelected` meldet, und das tut YouTubes untere Leiste nicht verlässlich; an derselben
     * Stelle scheitert auch `yt_shorts_tab_selected` mit seinem `requireSelected`. Ohne diesen
     * Beleg galt der Tab als bewusste Wahl, und auf YouTube blockte gar nichts mehr.
     *
     * Der Fall, den die Ausnahme schützt, ist bei Instagram echt: Ein Reel aus einer DM muss man
     * ansehen können. Bei YouTube gibt es ihn praktisch nicht — Shorts öffnet man aus dem Regal
     * oder über einen Link, und von dort läuft man in dieselbe Endlosschleife wie im Tab. Eine
     * Unterscheidung, die nicht trägt, gehört nicht in eine Sperre.
     */
    private fun allowsSingleClip(match: RuleMatch, root: UiNode, packageName: String): Boolean {
        if (match.rule.feature != Feature.INSTAGRAM_REELS) return false
        if (!settings.allowSingleClip) return noteSingleClipDenied("single_clip_off")

        if (SharedClip.looksLikeAlgorithmicStream(root, packageName)) {
            return noteSingleClipDenied("single_clip_tab")
        }
        if (!SharedClip.canPolicySwipes(root, packageName)) {
            return noteSingleClipDenied("single_clip_no_pager")
        }

        val now = System.currentTimeMillis()
        if (!SharedClip.mayWatch(true, singleWatchSwipes, singleWatchStartedAt, now)) {
            singleWatchActive = false
            return noteSingleClipDenied(
                if (singleWatchSwipes > 0) "single_clip_swiped" else "single_clip_timeout",
            )
        }

        if (!singleWatchActive) {
            singleWatchActive = true
            singleWatchStartedAt = now
            singleWatchIndex = -1
            singleWatchLastReason = null
            BlockLog.record("single_clip_allowed", "einmal ansehen, Wisch blockt")
        }
        return true
    }

    /**
     * Schreibt **einmal je Grund** ins Protokoll, warum die Ausnahme nicht griff.
     *
     * Das eigentliche Versäumnis der letzten Versionen: Griff die Ausnahme nicht, sah man nur,
     * dass geblockt wurde — nie, welche Bedingung sie verworfen hat. Seither raten wir. Ab jetzt
     * steht der Grund unter *Diagnose → Zuletzt ausgelöst*.
     *
     * Nur beim Wechsel des Grundes, sonst liefe das Protokoll im Scan-Takt über.
     *
     * @return immer `false` — die Funktion ist die Ablehnung.
     */
    private fun noteSingleClipDenied(reason: String): Boolean {
        if (singleWatchLastReason != reason) {
            singleWatchLastReason = reason
            BlockLog.record(reason, "Ausnahme „ein Video“ hat nicht gegriffen")
        }
        return false
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
        // Nach einer gerissenen Zurück-Kette wird eine Weile gar nicht eingegriffen. Sonst
        // liefe dieselbe Fehlerkennung sofort weiter und die Kette begänne von vorn.
        if (System.currentTimeMillis() < interventionPausedUntil) return false

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
    private fun handleMatch(match: RuleMatch, root: UiNode, packageName: String): Boolean {
        val feature = match.rule.feature
        if (allowsSingleClip(match, root, packageName)) return false
        if (!shouldIntervene(feature)) return false

        val spent = settings.budgetMinutes(feature) > 0
        BlockLog.record(
            if (spent) match.rule.id + " (Kontingent aufgebraucht)" else match.rule.id,
            match.signature,
        )
        blockAndGoBack(feature, match.signature)
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

    /**
     * Der „Für dich“-Feed — seit v0.13 wird nur noch erinnert.
     *
     * Bis v0.12 zog hier eine Wand über den Feed. Die blieb nach dem Verlassen von Instagram
     * über fremden Apps stehen und machte das Gerät unbenutzbar: Abgeräumt wurde sie nur im
     * Ereignispfad, und aus einer nicht überwachten App kommt **kein** Ereignis. Es gibt in
     * dieser App deshalb kein Fenster mehr, das Berührungen schluckt.
     *
     * [showReminder] ist über `REMINDER_COOLDOWN_MS` gedrosselt — ohne das käme die Erinnerung
     * im Scan-Takt von 150 ms.
     */
    private fun handleInstagramFeed(root: UiNode) {
        when (val decision = FeedPolicy.evaluate(root)) {
            // „Unklar“ darf nichts zurücksetzen. Beim Scrollen wandert die Kopfzeile aus
            // dem Bild und die Auswertung liefert Idle — wer hier zurücksetzt, schaltet die
            // Bremse genau dann ab, wenn gescrollt wird. Dieselbe Regel wie in
            // FeedPolicy.evaluate: Idle gibt nichts weiter.
            FeedDecision.Idle -> Unit

            // Nur das ist ein positiver Beleg, dass umgeschaltet wurde.
            FeedDecision.AlreadyFiltered -> resetFeedState()

            FeedDecision.RemindToSwitch -> {
                // Einmal je Eintritt in den Feed zählen, nicht bei jedem Baum-Scan. Ohne diese
                // Unterscheidung stünde nach einer Minute im Feed eine dreistellige Zahl in der
                // Statistik, und die Zahl wäre wertlos. (Vorher leistete das `wasDown`.)
                if (!forYouActive) {
                    forYouActive = true
                    BlockLog.record("ig_feed_for_you", "erinnert")
                    scope.launch { statsRepository.increment(Feature.INSTAGRAM_FEED) }
                }
                when (
                    FeedGuard.next(
                        tapTargetFound = FeedPolicy.followingTabToTap(root) != null,
                        tapsTried = feedTapsTried,
                        brakesUsed = feedBrakesUsed,
                    )
                ) {
                    // Die sanfteste Stufe: einmal auf „Gefolgt“ tippen und das Problem lösen.
                    // Das Ziel kommt ausschliesslich aus followingTabToTap — nie aus einer
                    // Textsuche, sonst entfolgt ein Fehlgriff jemanden.
                    FeedGuard.Step.TAP_FOLLOWING -> {
                        feedTapsTried++
                        val target = FeedPolicy.followingTabToTap(root)
                        if (target != null &&
                            Actions.clickNearest(target, RuleMatcher.windowArea(root))
                        ) {
                            BlockLog.record("ig_feed_tap", "auf Gefolgt getippt")
                            pausedUntil = SystemClock.uptimeMillis() + CLICK_COOLDOWN_MS
                        }
                    }

                    FeedGuard.Step.BRAKE -> {
                        showReminder(getString(R.string.feed_reminder))
                        brakeFeed(root)
                    }

                    FeedGuard.Step.LEAVE -> {
                        BlockLog.record("ig_feed_leave", "Bremse hat nicht gereicht")
                        toast(R.string.toast_feed_left)
                        blockAndGoBack(Feature.INSTAGRAM_FEED, "ig_feed_leave")
                    }
                }
            }

            // Liefert FeedPolicy für Instagram nicht mehr; der Zweig gehört TikTok.
            is FeedDecision.ChooseFollowing -> Unit

            is FeedDecision.EndOfFeed -> {
                BlockLog.record("ig_feed_end", decision.marker)
                toast(R.string.toast_feed_end)
                blockAndGoBack(Feature.INSTAGRAM_FEED)
            }
        }
    }

    /**
     * Explore verlassen, sobald das Vorschlagsraster länger als die Schonfrist vorne steht.
     *
     * Der Zeitstempel liegt hier und nicht in [ExplorePolicy]: Die Auswertung bleibt dadurch
     * zustandslos und prüfbar, der Dienst hält nur die Uhr. Zurückgesetzt wird er, sobald
     * Explore verlassen oder gesucht wird — sonst zählte die Schonfrist über einen ganzen
     * Instagram-Besuch hinweg nur einmal.
     */
    private fun handleInstagramExplore(root: UiNode) {
        val now = SystemClock.uptimeMillis()
        when (val decision = ExplorePolicy.evaluate(root, exploreSince.takeIf { it != 0L }, now)) {
            ExploreDecision.Idle, ExploreDecision.Searching -> exploreSince = 0L

            ExploreDecision.Grace -> if (exploreSince == 0L) exploreSince = now

            is ExploreDecision.Block -> {
                BlockLog.record("ig_explore_grid", decision.marker)
                toast(R.string.toast_explore_blocked)
                exploreSince = 0L
                blockAndGoBack(Feature.INSTAGRAM_EXPLORE)
            }
        }
    }

    /**
     * Zurück drücken — aber nie öfter, als [BackGuard] erlaubt.
     *
     * Die Obergrenze ist keine Feinheit: Ohne sie drückt ein Fehlalarm Zurück im 800-ms-Takt,
     * bis die fremde App geschlossen ist. Genau das ist mit der zu weiten `reel_`-Regel
     * passiert. Ein korrekter Block verlässt den Bildschirm beim ersten Versuch und läuft nie
     * in die Grenze; nur wo Zurück nichts löst, wächst die Kette.
     *
     * @param detail Knoten-Merkmal oder Grund, der ins Protokoll kommt, wenn die Kette reisst.
     */
    private fun blockAndGoBack(feature: Feature, detail: String = "") {
        val now = System.currentTimeMillis()
        backChainLength = if (BackGuard.continuesChain(lastBackAtMs, now)) backChainLength + 1 else 0

        if (BackGuard.isRunaway(backChainLength)) {
            // Der Fehlalarm wird hier zum ersten Mal sichtbar: Bis v0.11.3 stand im Protokoll
            // nur der Regel-Name, als wäre der Block gewollt gewesen.
            BlockLog.record("back_runaway", detail.ifEmpty { feature.name })
            interventionPausedUntil = now + BackGuard.PAUSE_MS
            backChainLength = 0
            lastBackAtMs = 0L
            toast(R.string.toast_back_runaway)
            return
        }

        lastBackAtMs = now
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
    private fun showReminder(explicitLine: String? = null) {
        val now = SystemClock.uptimeMillis()
        if (now - lastReminderAt < REMINDER_COOLDOWN_MS) return
        lastReminderAt = now

        val line = explicitLine ?: run {
            val lines = resources.getStringArray(R.array.reminder_lines)
            val index = Reminders.next(lines.size, lastReminderIndex)
            if (index !in lines.indices) return
            lastReminderIndex = index
            lines[index]
        }

        // Der Cheat-Hinweis nur, solange er auch einzulösen ist. Ein Angebot, das nicht gilt,
        // macht aus der Erinnerung eine Verhöhnung.
        val detail = if (cheatIsFree()) getString(R.string.overlay_cheat_hint) else null
        if (overlay?.show(line, detail) != true) toast(R.string.toast_blocked)
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

    /**
     * Die Bremse: den „Für dich“-Feed oben halten.
     *
     * **Warum im Scan und nicht am Scroll-Ereignis.** v0.14.0 hing am `TYPE_VIEW_SCROLLED`-
     * Zweig und verlangte, dass das Ereignis selbst eine der Feed-Kennungen trägt. Scroll-
     * Ereignisse tragen aber oft gar keine View-ID. Dazu kam, dass die Bremse `forYouActive`
     * prüfte — und das wurde bei `FeedDecision.Idle` gelöscht, also ständig, sobald die
     * Kopfzeile beim Scrollen aus dem Bild wanderte. Die Bremse war damit genau dann aus, wenn
     * gescrollt wurde.
     *
     * Hier kommt die Entscheidung frisch aus dem Baum: Gerufen wird nur nach
     * [FeedDecision.RemindToSwitch], und die Liste wird über dieselben Kennungen gesucht, mit
     * denen [FeedPolicy] den Startfeed erkennt. Auf einer Profilseite gibt es kein
     * `RemindToSwitch`, also wird dort nie gebremst.
     *
     * **Begrenzt sich selbst:** Steht die Liste schon oben, liefert `ACTION_SCROLL_BACKWARD`
     * `false`. Dann passiert nichts und es wird nichts gezählt — [feedBrakesUsed] wächst nur,
     * solange wirklich nach unten gescrollt wird. Genau das ist das Signal für die harte Stufe.
     *
     * Hinterlässt nichts: kein Fenster, kein Zustand in einer fremden App. Stirbt der Dienst
     * mitten darin, bleibt nichts stehen.
     */
    private fun brakeFeed(root: UiNode) {
        val now = System.currentTimeMillis()
        // Der Zurück-Scroll erzeugt selbst ein Ereignis, und der Scan läuft im 150-ms-Takt
        // weiter. Ohne dieses Gatter zöge die App gegen ihr eigenes Bremsen.
        if (FeedGuard.isEcho(lastBrakeAtMs, now)) return

        val list = RuleMatcher.findNode(root) { node ->
            val viewId = normalizeForMatch(node.viewId) ?: return@findNode false
            Rules.InstagramFeed.FEED_ROOT_VIEW_IDS.any { viewId.contains(it) }
        }
        if (list == null) return noteBrakeSkipped("brake_no_list")

        if (!Actions.scrollBack(list)) {
            // Zwei Fälle, die gleich aussehen und es nicht sind: Die Liste ist schon oben
            // (gutartig), oder sie nimmt die Aktion gar nicht an (dann greift die Bremse nie).
            return noteBrakeSkipped(
                if (list.isScrollable) "brake_at_top" else "brake_not_scrollable",
            )
        }

        lastBrakeAtMs = now
        feedBrakesUsed++
        lastBrakeReason = null
    }

    /**
     * Schreibt **einmal je Grund** ins Protokoll, warum die Bremse nicht griff.
     *
     * Dasselbe Versäumnis wie früher bei der Ausnahme „ein Video“: v0.14.0 hatte acht
     * Abbruchstellen und protokollierte keine einzige. Zu melden blieb nur „scrollt weiter“,
     * und es blieb nur zu raten. Nur beim Wechsel des Grundes, sonst liefe das Protokoll im
     * Scan-Takt über.
     */
    private fun noteBrakeSkipped(reason: String) {
        if (lastBrakeReason == reason) return
        lastBrakeReason = reason
        BlockLog.record(reason, "Bremse hat nicht gegriffen")
    }

    private fun resetFeedState() {
        // Die Zähler gehören zu TikToks Tab-Umschaltung — Instagram tippt seit v0.11 nichts
        // mehr an, sperrt stattdessen.
        feedSwitchAttempts = 0
        manualSwitchHintShown = false
        forYouActive = false
        feedTapsTried = 0
        feedBrakesUsed = 0
        lastBrakeAtMs = 0L
        lastBrakeReason = null
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
        runCatching { unregisterReceiver(wakeReceiver) }
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
        // 900 statt 600 ms seit v0.8.1: Instagrams mittiges Feed-Menü geht animiert auf.
        // Ist es nach 600 ms noch nicht im Baum, tippt die App den Titel ein zweites Mal — und
        // schließt damit genau das Menü, das sie gerade geöffnet hat.
        const val CLICK_COOLDOWN_MS = 900L
        const val MAX_FEED_SWITCH_ATTEMPTS = 3

        /**
         * Wie lange die Wand ohne Bestätigung stehen darf.
         *
         * Grosszügig gewählt: Ein kurzer Ereignis-Aussetzer soll sie nicht wegnehmen, während
         * jemand liest. Aber lang genug ist sie nie, dass eine vergessene Wand über einer
         * anderen App zum Problem wird — beim nächsten Blick ist sie weg.
         */
        const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L
        const val REMINDER_COOLDOWN_MS = 20_000L
    }
}
