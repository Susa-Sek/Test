package de.shortblock.app.service

import android.graphics.Rect
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
    override val isVisible: Boolean get() = node.isVisibleToUser
    override val childCount: Int get() = node.childCount

    override val bounds: NodeBounds?
        get() {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return if (rect.isEmpty) null else NodeBounds(rect.left, rect.top, rect.right, rect.bottom)
        }

    override fun child(index: Int): UiNode? =
        runCatching { node.getChild(index) }.getOrNull()?.let(::AccessibilityUiNode)
}
