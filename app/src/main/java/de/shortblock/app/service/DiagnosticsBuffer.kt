package de.shortblock.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Ringpuffer der zuletzt gesehenen View-IDs, nur im Arbeitsspeicher.
 *
 * Bedienungshilfe und UI laufen im selben Prozess, deshalb genügt ein Singleton — kein IPC,
 * keine Persistenz. Nichts davon überlebt einen Neustart, und das ist auch so gewollt:
 * hier stehen Bildschirminhalte fremder Apps drin.
 */
object DiagnosticsBuffer {

    private const val CAPACITY = 400

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun record(packageName: String, signatures: List<String>) {
        if (signatures.isEmpty()) return
        val prefix = packageName.substringAfterLast('.')
        _entries.update { existing ->
            val known = existing.toHashSet()
            val additions = signatures
                .map { "[$prefix] $it" }
                .filter { known.add(it) }
            if (additions.isEmpty()) {
                existing
            } else {
                (existing + additions).takeLast(CAPACITY)
            }
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
