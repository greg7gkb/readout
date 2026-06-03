package com.greg7gkb.readout.screen

import com.greg7gkb.readout.common.model.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeWalkerTest {

    @Test
    fun `single node with text yields one ScreenNode`() {
        val root = node(text = "Hello", className = "TextView", viewId = "app:id/greeting")

        val result = NodeWalker.walk(root)

        assertEquals(1, result.size)
        assertEquals("Hello", result[0].text)
        assertEquals("TextView", result[0].className)
        assertEquals("app:id/greeting", result[0].viewIdResourceName)
        assertEquals(Bounds(0, 0, 100, 50), result[0].bounds)
    }

    @Test
    fun `children are walked recursively`() {
        val root = node(
            children = listOf(
                node(text = "First"),
                node(children = listOf(node(text = "Nested"))),
                node(text = "Third"),
            ),
        )

        val texts = NodeWalker.walk(root).mapNotNull { it.text }

        assertEquals(listOf("First", "Nested", "Third"), texts)
    }

    @Test
    fun `invisible nodes and their subtrees are dropped`() {
        val root = node(
            children = listOf(
                node(text = "Visible"),
                node(
                    visible = false,
                    text = "InvisibleSelf",
                    children = listOf(node(text = "InvisibleChild")),
                ),
                node(text = "AlsoVisible"),
            ),
        )

        val texts = NodeWalker.walk(root).mapNotNull { it.text }

        assertEquals(listOf("Visible", "AlsoVisible"), texts)
    }

    @Test
    fun `container without text but with children is walked through`() {
        val root = node(
            // Container itself emits nothing.
            children = listOf(node(text = "Inside")),
        )

        val result = NodeWalker.walk(root)

        assertEquals(1, result.size)
        assertEquals("Inside", result[0].text)
    }

    @Test
    fun `blank text and contentDescription are treated as absent`() {
        val root = node(
            children = listOf(
                node(text = "   "),
                node(contentDescription = "\n\t"),
                node(text = "Real"),
            ),
        )

        val texts = NodeWalker.walk(root).mapNotNull { it.text }

        assertEquals(listOf("Real"), texts)
    }

    @Test
    fun `contentDescription-only nodes are kept`() {
        val root = node(
            children = listOf(
                node(contentDescription = "Close button", className = "ImageButton"),
            ),
        )

        val result = NodeWalker.walk(root)

        assertEquals(1, result.size)
        assertEquals(null, result[0].text)
        assertEquals("Close button", result[0].contentDescription)
        assertEquals("ImageButton", result[0].className)
    }

    @Test
    fun `both text and contentDescription are emitted on the same node`() {
        val root = node(text = "Submit", contentDescription = "Submit form")

        val result = NodeWalker.walk(root)

        assertEquals(1, result.size)
        assertEquals("Submit", result[0].text)
        assertEquals("Submit form", result[0].contentDescription)
    }

    @Test
    fun `root that is itself invisible yields empty list`() {
        val root = node(visible = false, text = "RootText", children = listOf(node(text = "Child")))

        val result = NodeWalker.walk(root)

        assertTrue(result.isEmpty())
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null,
        viewId: String? = null,
        visible: Boolean = true,
        bounds: Bounds = Bounds(0, 0, 100, 50),
        children: List<WalkableNode> = emptyList(),
    ): WalkableNode = FakeWalkableNode(
        text = text,
        contentDescription = contentDescription,
        className = className,
        viewIdResourceName = viewId,
        isVisibleToUser = visible,
        bounds = bounds,
        children = children,
    )
}

private class FakeWalkableNode(
    override val text: CharSequence?,
    override val contentDescription: CharSequence?,
    override val className: CharSequence?,
    override val viewIdResourceName: String?,
    override val isVisibleToUser: Boolean,
    override val bounds: Bounds,
    private val children: List<WalkableNode>,
) : WalkableNode {
    override val childCount: Int = children.size
    override fun getChild(index: Int): WalkableNode? = children.getOrNull(index)
}
