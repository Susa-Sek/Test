package de.shortblock.app.service

/** Was gematcht hat — inklusive Knoten-Beschreibung, damit Fehlalarme benennbar sind. */
data class RuleMatch(val rule: Rule, val signature: String)

/**
 * Baum-Traversierung und Regel-Abgleich. Reines Kotlin, damit es auf der JVM testbar bleibt.
 *
 * Zwei harte Obergrenzen begrenzen die Kosten: [MAX_DEPTH] und [MAX_NODES]. Instagram-Feeds
 * können sehr tiefe Bäume haben, und dieser Code läuft im Main-Thread des Systemdienstes —
 * ein unbegrenzter Scan wäre spürbar.
 */
object RuleMatcher {

    const val MAX_DEPTH = 25
    const val MAX_NODES = 1500

    /**
     * Läuft den Baum in Tiefensuche ab und ruft [visit] für jeden Knoten auf.
     * Gibt `true` zurück, sobald [visit] `true` liefert (Abbruch), sonst `false`.
     */
    fun traverse(
        root: UiNode?,
        maxDepth: Int = MAX_DEPTH,
        maxNodes: Int = MAX_NODES,
        visit: (UiNode) -> Boolean,
    ): Boolean {
        if (root == null) return false
        var visited = 0
        val stack = ArrayDeque<UiNode>()
        val depths = ArrayDeque<Int>()
        stack.addLast(root)
        depths.addLast(0)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val depth = depths.removeLast()
            if (++visited > maxNodes) return false
            if (visit(node)) return true
            if (depth >= maxDepth) continue
            for (index in node.childCount - 1 downTo 0) {
                val child = node.child(index) ?: continue
                stack.addLast(child)
                depths.addLast(depth + 1)
            }
        }
        return false
    }

    /** Fläche des Fensters, gegen die Größenschranken von Regeln gemessen werden. */
    fun windowArea(root: UiNode?): Long = root?.bounds?.area ?: 0L

    /** Erste zutreffende Regel für dieses Paket, beschränkt auf die aktivierten Features. */
    fun findFirstMatch(root: UiNode?, packageName: String, enabled: Set<Feature>): RuleMatch? {
        val candidates = Rules.BLOCK_RULES.filter {
            it.packageName == packageName && it.feature in enabled
        }
        if (candidates.isEmpty()) return null

        // Regeln, die das ganze Paket sperren, brauchen keinen Baum — und sollen ihn auch nicht
        // durchlaufen: sie greifen selbst dann, wenn der Baum leer oder noch nicht aufgebaut ist.
        candidates.firstOrNull { it.matchAnyWindow }?.let { rule ->
            return RuleMatch(rule, "gesamtes Paket $packageName")
        }

        val area = windowArea(root)
        var hit: RuleMatch? = null
        traverse(root) { node ->
            val rule = candidates.firstOrNull { it.matches(node, area) }
            if (rule != null) hit = RuleMatch(rule, describe(node))
            hit != null
        }
        return hit
    }

    /** Erster *sichtbarer* Knoten, auf den [predicate] zutrifft. */
    fun findNode(root: UiNode?, predicate: (UiNode) -> Boolean): UiNode? {
        var hit: UiNode? = null
        traverse(root) { node ->
            if (node.isVisible && predicate(node)) {
                hit = node
                true
            } else {
                false
            }
        }
        return hit
    }

    fun containsNode(root: UiNode?, predicate: (UiNode) -> Boolean): Boolean =
        findNode(root, predicate) != null

    /** Kurzbeschreibung eines Knotens für Diagnose und Fehlalarm-Protokoll. */
    fun describe(node: UiNode): String {
        val viewId = node.viewId?.substringAfter("id/", missingDelimiterValue = "")?.ifEmpty { null }
        val description = node.contentDescription?.toString()?.trim()?.ifEmpty { null }
        val text = node.text?.toString()?.trim()?.ifEmpty { null }
        return buildList {
            if (viewId != null) add("id=$viewId")
            if (description != null) add("desc=${description.take(40)}")
            if (text != null) add("text=${text.take(40)}")
            if (node.isSelected) add("selected")
        }.joinToString("  ").ifEmpty { "<kein Merkmal>" }
    }

    /**
     * Momentaufnahme des Baums für den Diagnose-Screen: eine Zeile je sichtbarem Knoten, der
     * überhaupt etwas Identifizierbares trägt. Genau das, was man nach einem Instagram-Update
     * braucht, um neue Muster in [Rules] einzutragen.
     */
    fun collectSignatures(root: UiNode?, limit: Int = 120): List<String> {
        val out = ArrayList<String>(limit)
        traverse(root) { node ->
            if (node.isVisible && (node.viewId != null || node.contentDescription != null)) {
                out.add(describe(node))
            }
            out.size >= limit
        }
        return out
    }
}
