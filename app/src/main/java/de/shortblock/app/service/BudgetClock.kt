package de.shortblock.app.service

import de.shortblock.app.data.WatchBudget

/**
 * Führt Buch über die Sehdauer je Feature — zustandsbehaftet, im Dienst, im Speicher.
 *
 * Warum gepuffert: Der Dienst scannt alle 150 ms. Ein DataStore-Schreibvorgang in diesem Takt
 * wäre der schnellste Weg, Akku und Flash zu ruinieren. Deshalb sammelt dieses Objekt
 * Millisekunden und gibt sie nur alle [FLUSH_INTERVAL_MS] als volle Sekunden heraus.
 *
 * Die reine Rechenregel steht in [WatchBudget]; hier liegt nur der Zustand.
 */
class BudgetClock {

    private val lastTickAt = mutableMapOf<Feature, Long>()
    private val pendingMillis = mutableMapOf<Feature, Long>()
    private var lastFlushAt = 0L

    /**
     * Einen Treffer verbuchen und die seither vergangene Zeit gutschreiben.
     * Gibt die insgesamt heute verbrauchten Sekunden zurück (persistiert plus ungeschrieben).
     */
    fun tick(feature: Feature, nowMs: Long, persistedSeconds: Int): Int {
        val credit = WatchBudget.creditableDelta(lastTickAt[feature] ?: 0L, nowMs)
        lastTickAt[feature] = nowMs
        if (credit > 0L) {
            pendingMillis[feature] = (pendingMillis[feature] ?: 0L) + credit
        }
        return persistedSeconds + ((pendingMillis[feature] ?: 0L) / 1000L).toInt()
    }

    /**
     * Vergisst die Zeitstempel, ohne die gesammelte Zeit zu verlieren.
     *
     * Beim Verlassen einer App muss der Zeitstempel weg, sonst würde die Pause bis zur Rückkehr
     * beim nächsten Treffer als Sehdauer gelten. Die Schrittgrenze in [WatchBudget] fängt das
     * zwar ohnehin ab; das hier ist der Gürtel zum Hosenträger.
     */
    fun pause() {
        lastTickAt.clear()
    }

    /** Fällige volle Sekunden je Feature, oder leer, wenn noch nichts zu schreiben ist. */
    fun drainIfDue(nowMs: Long, force: Boolean = false): Map<Feature, Int> {
        if (!force && nowMs - lastFlushAt < FLUSH_INTERVAL_MS) return emptyMap()
        lastFlushAt = nowMs

        val due = mutableMapOf<Feature, Int>()
        pendingMillis.entries.forEach { entry ->
            val seconds = (entry.value / 1000L).toInt()
            if (seconds > 0) {
                due[entry.key] = seconds
                // Der Rest unter einer Sekunde bleibt liegen und geht nicht verloren.
                entry.setValue(entry.value - seconds * 1000L)
            }
        }
        return due
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 10_000L
    }
}
