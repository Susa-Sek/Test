package de.shortblock.app.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Adapter von Androids [AccessibilityNodeInfo] auf das testbare [UiNode]-Interface.
 *
 * Bewusst ohne `recycle()`: seit Android 13 ist die Methode ein No-op und deprecated, und
 * ein zu früh recycelter Knoten wirft zur Laufzeit. Die Scans hier sind kurzlebig und durch
 * [RuleMatcher.MAX_NODES] gedeckelt, deshalb überlassen wir das Aufräumen dem GC.
 */
class AccessibilityUiNode(val node: AccessibilityNodeInfo) : UiNode {

    override val viewId: String? get() = node.viewIdResourceName
    override val text: String? get() = node.text?.toString()
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val isSelected: Boolean get() = node.isSelected
    override val childCount: Int get() = node.childCount

    override fun child(index: Int): UiNode? =
        runCatching { node.getChild(index) }.getOrNull()?.let(::AccessibilityUiNode)
}
