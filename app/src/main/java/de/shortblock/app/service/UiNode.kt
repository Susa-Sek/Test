package de.shortblock.app.service

/**
 * Ein Knoten im View-Baum der Vordergrund-App — losgelöst von [android.view.accessibility.AccessibilityNodeInfo].
 *
 * Der Grund für dieses Interface: `AccessibilityNodeInfo` lässt sich auf der JVM nicht
 * instanziieren. Hinter diese Eigenschaften gekapselt, ist die gesamte Erkennungslogik
 * ([RuleMatcher], [Rules], [FeedPolicy]) als normaler Unit-Test prüfbar — ohne Gerät, ohne
 * Emulator. Der Adapter für die echte Android-Klasse liegt in `AccessibilityUiNode.kt`.
 */
interface UiNode {
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val isSelected: Boolean

    /**
     * Ob der Knoten tatsächlich auf dem Bildschirm zu sehen ist.
     *
     * Unverzichtbar, und die Quelle des ersten Fehlalarms dieser App: Der Baum enthält auch
     * recycelte Listeneinträge und Knoten weit unterhalb des sichtbaren Bereichs. Ohne diese
     * Prüfung zählt ein „Vorgeschlagene Beiträge“-Knoten, der noch gar nicht erreicht wurde,
     * bereits als Feed-Ende — und die App wirft beim Öffnen sofort aus Instagram heraus.
     */
    val isVisible: Boolean

    /** Position auf dem Bildschirm, für Größenvergleiche. `null`, wenn leer oder unbekannt. */
    val bounds: NodeBounds?

    val childCount: Int

    fun child(index: Int): UiNode?
}

data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
}

/**
 * Vergleichsform für Textvergleiche: Kleinschreibung, typografische Apostrophe normalisiert,
 * Whitespace zusammengefasst. Leere Strings werden zu `null`, damit ein leerer
 * contentDescription nie versehentlich auf eine leere Regel matcht.
 */
internal fun normalizeForMatch(value: CharSequence?): String? {
    if (value == null) return null
    val normalized = value.toString()
        .trim()
        .lowercase()
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace(WHITESPACE, " ")
    return normalized.ifEmpty { null }
}

private val WHITESPACE = Regex("\\s+")
