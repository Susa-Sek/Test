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
    private val children: List<FakeNode> = emptyList(),
) : UiNode {
    override val childCount: Int get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)
}

fun igNode(
    id: String? = null,
    text: String? = null,
    description: String? = null,
    selected: Boolean = false,
    children: List<FakeNode> = emptyList(),
) = FakeNode(
    viewId = id?.let { "com.instagram.android:id/$it" },
    text = text,
    contentDescription = description,
    isSelected = selected,
    children = children,
)

fun ytNode(
    id: String? = null,
    text: String? = null,
    description: String? = null,
    selected: Boolean = false,
    children: List<FakeNode> = emptyList(),
) = FakeNode(
    viewId = id?.let { "com.google.android.youtube:id/$it" },
    text = text,
    contentDescription = description,
    isSelected = selected,
    children = children,
)
