package de.trimbox.app.data

/**
 * Findet den Papierkorb eines Postfachs.
 *
 * Das klingt nach einer Kleinigkeit und ist die riskanteste Entscheidung der App: Greift
 * sie daneben, landen Mails in einem beliebigen Ordner oder — schlimmer — nirgends, und
 * der Nutzer sucht sie im Papierkorb vergeblich. Deshalb zuerst das IMAP-Attribut
 * `\Trash` (RFC 6154), das jeder halbwegs aktuelle Server mitliefert und das überall
 * gleich heisst, und erst danach die Namensliste. Passt nichts, gibt die Funktion `null`
 * zurück und der Aufrufer bricht ab, statt zu raten.
 */
object TrashFolder {

    /** Ein Ordner, so wie ihn `LIST` meldet. */
    data class Folder(
        val fullName: String,
        val attributes: Set<String> = emptySet(),
    )

    private const val TRASH_ATTRIBUTE = "\\trash"

    /**
     * Namen, unter denen Papierkörbe auftreten. Verglichen wird nur das letzte Glied des
     * Pfades, damit `[Gmail]/Papierkorb` trifft, ein Unterordner `Archiv/Trash-Vorlagen`
     * aber nicht.
     */
    private val NAMES = listOf(
        "trash",
        "papierkorb",
        "deleted messages",
        "deleted items",
        "gelöschte objekte",
        "geloeschte objekte",
        "gelöschte elemente",
        "geloeschte elemente",
        "gelöscht",
        "geloescht",
        "corbeille",
        "prullenbak",
    )

    fun choose(folders: List<Folder>): String? {
        folders.firstOrNull { folder ->
            folder.attributes.any { it.lowercase() == TRASH_ATTRIBUTE }
        }?.let { return it.fullName }

        return folders.firstOrNull { folder -> lastSegment(folder.fullName) in NAMES }?.fullName
    }

    private fun lastSegment(fullName: String): String =
        fullName.split('/', '.').last().trim().lowercase()
}
