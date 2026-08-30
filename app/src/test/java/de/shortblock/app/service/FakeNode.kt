package de.shortblock.app.service

/**
 * Handgebauter View-Baum für die Tests. Genau deshalb gibt es das [UiNode]-Interface:
 * `AccessibilityNodeInfo` ließe sich hier nicht instanziieren.
 */
class FakeNode(
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val isSelected: Boolean = false,
    override val isVisible: Boolean = true,
    override val bounds: NodeBounds? = FULLSCREEN,
    private val children: List<FakeNode> = emptyList(),
) : UiNode {
    override val childCount: Int get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)

    companion object {
        /** Ein typisches Telefon-Fenster. Wurzelknoten und Vollbild-Views nutzen das. */
        val FULLSCREEN = NodeBounds(0, 0, 1080, 2400)

        /** Ein quadratisches Feed-Medium — knapp 45 % des Fensters. */
        val FEED_MEDIA = NodeBounds(0, 300, 1080, 1380)
    }
}

fun igNode(
    id: String? = null,
    text: String? = null,
    description: String? = null,
    selected: Boolean = false,
    visible: Boolean = true,
    bounds: NodeBounds? = FakeNode.FULLSCREEN,
    children: List<FakeNode> = emptyList(),
) = FakeNode(
    viewId = id?.let { "com.instagram.android:id/$it" },
    text = text,
    contentDescription = description,
    isSelected = selected,
    isVisible = visible,
    bounds = bounds,
    children = children,
)

fun ytNode(
    id: String? = null,
    text: String? = null,
    description: String? = null,
    selected: Boolean = false,
    visible: Boolean = true,
    bounds: NodeBounds? = FakeNode.FULLSCREEN,
    children: List<FakeNode> = emptyList(),
) = FakeNode(
    viewId = id?.let { "com.google.android.youtube:id/$it" },
    text = text,
    contentDescription = description,
    isSelected = selected,
    isVisible = visible,
    bounds = bounds,
    children = children,
)
