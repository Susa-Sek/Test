package de.shortblock.app.service

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

    /** Erste zutreffende Regel für dieses Paket, beschränkt auf die aktivierten Features. */
    fun findFirstMatch(root: UiNode?, packageName: String, enabled: Set<Feature>): Rule? {
        val candidates = Rules.BLOCK_RULES.filter {
            it.packageName == packageName && it.feature in enabled
        }
        if (candidates.isEmpty()) return null

        var hit: Rule? = null
        traverse(root) { node ->
            hit = candidates.firstOrNull { rule -> rule.matches(node) }
            hit != null
        }
        return hit
    }

    /** Erster Knoten, auf den [predicate] zutrifft. */
    fun findNode(root: UiNode?, predicate: (UiNode) -> Boolean): UiNode? {
        var hit: UiNode? = null
        traverse(root) { node ->
            if (predicate(node)) {
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

    /**
     * Momentaufnahme des Baums für den Diagnose-Screen: eine Zeile je Knoten, der überhaupt
     * etwas Identifizierbares trägt. Genau das, was man nach einem Instagram-Update braucht,
     * um neue Muster in [Rules] einzutragen.
     */
    fun collectSignatures(root: UiNode?, limit: Int = 120): List<String> {
        val out = ArrayList<String>(limit)
        traverse(root) { node ->
            val viewId = node.viewId?.substringAfter("id/", missingDelimiterValue = "")?.ifEmpty { null }
            val description = node.contentDescription?.toString()?.trim()?.ifEmpty { null }
            val text = node.text?.toString()?.trim()?.ifEmpty { null }
            if (viewId != null || description != null) {
                val parts = buildList {
                    if (viewId != null) add("id=$viewId")
                    if (description != null) add("desc=${description.take(40)}")
                    if (text != null) add("text=${text.take(40)}")
                    if (node.isSelected) add("selected")
                }
                out.add(parts.joinToString("  "))
            }
            out.size >= limit
        }
        return out
    }
}
