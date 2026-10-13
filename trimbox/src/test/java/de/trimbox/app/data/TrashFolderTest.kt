package de.trimbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrashFolderTest {

    private fun folder(name: String, vararg attributes: String) =
        TrashFolder.Folder(name, attributes.toSet())

    @Test
    fun `the attribute beats every name`() {
        val folders = listOf(
            folder("Trash"),
            folder("Archiv/Papierkorb-Vorlagen"),
            folder("Mülleimer", "\\Trash"),
        )

        assertEquals("Mülleimer", TrashFolder.choose(folders))
    }

    @Test
    fun `the attribute is matched regardless of case`() {
        assertEquals("Bin", TrashFolder.choose(listOf(folder("Bin", "\\trash"))))
        assertEquals("Bin", TrashFolder.choose(listOf(folder("Bin", "\\TRASH"))))
    }

    @Test
    fun `german and gmail names are found without the attribute`() {
        assertEquals("Papierkorb", TrashFolder.choose(listOf(folder("INBOX"), folder("Papierkorb"))))
        assertEquals("[Gmail]/Papierkorb", TrashFolder.choose(listOf(folder("[Gmail]/Papierkorb"))))
        assertEquals("[Gmail]/Trash", TrashFolder.choose(listOf(folder("[Gmail]/Trash"))))
        assertEquals("Gelöschte Objekte", TrashFolder.choose(listOf(folder("Gelöschte Objekte"))))
        assertEquals("INBOX.Trash", TrashFolder.choose(listOf(folder("INBOX.Trash"))))
    }

    @Test
    fun `a folder that merely contains the word is not the trash`() {
        val folders = listOf(folder("INBOX"), folder("Archiv/Trash-Vorlagen"), folder("Papierkorb-Alt"))

        assertNull(TrashFolder.choose(folders))
    }

    @Test
    fun `nothing suitable means null, never a guess`() {
        assertNull(TrashFolder.choose(emptyList()))
        assertNull(TrashFolder.choose(listOf(folder("INBOX"), folder("Gesendet"), folder("Entwürfe"))))
    }
}
