package de.shortblock.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Protokoll der zuletzt ausgelösten Aktionen — welche Regel hat gefeuert, und woran.
 *
 * Das ist keine Statistik, sondern Werkzeug: Wenn die App fälschlich aus einer App wirft,
 * ist die entscheidende Frage „welche der Regeln war das?“. Ohne diese Anzeige rät man
 * zwischen allen Mustern in [Rules]. Läuft im selben Prozess wie die UI, nur im Speicher.
 */
object BlockLog {

    private const val CAPACITY = 20

    data class Entry(
        val ruleId: String,
        val detail: String,
        /** Wanduhr-Zeit, damit die Startseite "vor 2 Min" anzeigen kann. */
        val atMillis: Long = System.currentTimeMillis(),
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(ruleId: String, detail: String) {
        _entries.update { existing -> (existing + Entry(ruleId, detail)).takeLast(CAPACITY) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
