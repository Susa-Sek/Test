package de.wissenshappen.app.data

/** Ein Wissenshappen — eine Karte im Feed. */
data class Card(
    val id: String,
    val title: String,
    val text: String,
    val imageUrl: String?,
    val sourceUrl: String,
    val kind: CardKind,
    val topic: String?,
)

enum class CardKind {
    /** Aus einem der gewählten Themen. */
    TOPIC,

    /** Artikel des Tages. */
    TODAY,

    /** Was geschah an diesem Tag. */
    ON_THIS_DAY,
}
