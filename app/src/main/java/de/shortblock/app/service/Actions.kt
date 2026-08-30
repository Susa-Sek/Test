package de.shortblock.app.service

import android.view.accessibility.AccessibilityNodeInfo

object Actions {

    private const val MAX_PARENT_HOPS = 6

    /**
     * Tippt den Knoten an — oder, wenn er selbst nicht klickbar ist, den nächsten klickbaren
     * Vorfahren. Instagram hängt den Klick-Handler oft nicht an den Textknoten selbst, sondern
     * an einen Container darüber, deshalb der Aufstieg.
     */
    fun clickNearest(uiNode: UiNode): Boolean {
        var current: AccessibilityNodeInfo? = (uiNode as? AccessibilityUiNode)?.node ?: return false
        var hops = 0
        while (current != null && hops <= MAX_PARENT_HOPS) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = runCatching { current?.parent }.getOrNull()
            hops++
        }
        return false
    }
}
