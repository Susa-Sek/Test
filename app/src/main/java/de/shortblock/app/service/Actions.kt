package de.shortblock.app.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object Actions {

    private const val MAX_PARENT_HOPS = 3

    /**
     * Obergrenze für die Fläche eines Knotens, auf den geklickt werden darf.
     *
     * Der Feed-Umschalter ist ein kleiner Titel oben links. Findet der Aufstieg nur einen
     * riesigen Container als klickbaren Vorfahren, ist das nicht der gesuchte Button, sondern
     * irgendein Layout-Wrapper — dann lieber gar nicht tippen.
     */
    private const val MAX_CLICK_AREA_FRACTION = 0.3

    /**
     * Tippt den Knoten an — oder, wenn er selbst nicht klickbar ist, den nächsten klickbaren
     * Vorfahren. Instagram hängt den Klick-Handler oft nicht an den Textknoten selbst, sondern
     * an einen Container darüber, deshalb der Aufstieg.
     */
    fun clickNearest(uiNode: UiNode, windowArea: Long): Boolean {
        var current: AccessibilityNodeInfo? = (uiNode as? AccessibilityUiNode)?.node ?: return false
        var hops = 0
        while (current != null && hops <= MAX_PARENT_HOPS) {
            val node = current
            if (node.isClickable && isSmallEnough(node, windowArea) &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            current = runCatching { node.parent }.getOrNull()
            hops++
        }
        return false
    }

    private fun isSmallEnough(node: AccessibilityNodeInfo, windowArea: Long): Boolean {
        if (windowArea <= 0L) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        val area = rect.width().toLong() * rect.height().toLong()
        return area <= windowArea * MAX_CLICK_AREA_FRACTION
    }
}
