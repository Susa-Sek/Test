package de.klarzeit.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.klarzeit.app.data.AppCatalog

/**
 * Das Symbol einer App als Bild.
 *
 * Eine Liste aus zwanzig Zeilen reinem Text zwingt zum Lesen; mit Symbolen findet man
 * seine App im Vorbeischauen. Umgewandelt wird von Hand statt über eine Bildbibliothek —
 * die Symbole liegen schon auf dem Gerät, es gibt nichts zu laden.
 */
@Composable
fun AppIcon(
    catalog: AppCatalog,
    packageName: String,
    dimmed: Boolean = false,
    size: Int = 36,
) {
    val bitmap: ImageBitmap? = remember(packageName) {
        runCatching {
            catalog.icon(packageName)?.toBitmap(size * 3, size * 3)?.asImageBitmap()
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                // Ausgeschlossene Apps blasser: Der Unterschied muss auch im
                // Vorbeiscrollen sichtbar sein, nicht erst beim Lesen der Überschrift.
                .alpha(if (dimmed) 0.4f else 1f),
        )
    } else {
        androidx.compose.foundation.layout.Spacer(Modifier.size(size.dp))
    }
}
