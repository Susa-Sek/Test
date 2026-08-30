package de.shortblock.app.service

/**
 * Ein Knoten im View-Baum der Vordergrund-App — losgelöst von [android.view.accessibility.AccessibilityNodeInfo].
 *
 * Der Grund für dieses Interface: `AccessibilityNodeInfo` lässt sich auf der JVM nicht
 * instanziieren. Hinter diese vier Eigenschaften gekapselt, ist die gesamte Erkennungslogik
 * ([RuleMatcher], [Rules]) als normaler Unit-Test prüfbar — ohne Gerät, ohne Emulator.
 * Der Adapter für die echte Android-Klasse liegt in `AccessibilityUiNode.kt`.
 */
interface UiNode {
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val isSelected: Boolean
    val childCount: Int

    fun child(index: Int): UiNode?
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
