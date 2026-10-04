package de.shortblock.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Die Schriftskala macht die Rangfolge, nicht die Rahmen.
 *
 * Zwei Eingriffe gegenüber der Voreinstellung, beide bewusst:
 * - Die große Zahl im Hero ist **eng** gesetzt (negative Laufweite). Große Ziffern in normaler
 *   Laufweite zerfallen optisch in Einzelzeichen.
 * - Abschnittsüberschriften sind klein und **gesperrt**. So trennen sie sichtbar, ohne mit dem
 *   Inhalt darunter um Aufmerksamkeit zu konkurrieren.
 */
internal val ShortBlockTypography = Typography().let { base ->
    base.copy(
        displayMedium = base.displayMedium.copy(
            fontSize = 56.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.03).em,
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.12.em,
        ),
        bodySmall = base.bodySmall.copy(lineHeight = 19.sp),
    )
}
