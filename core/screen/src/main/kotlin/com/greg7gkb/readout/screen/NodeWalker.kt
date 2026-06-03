package com.greg7gkb.readout.screen

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.greg7gkb.readout.common.model.Bounds
import com.greg7gkb.readout.common.model.ScreenNode

/**
 * Pure walk over an AccessibilityNodeInfo tree, producing a flat list of
 * [ScreenNode]s suitable for LLM consumption.
 *
 * Two entry points:
 *   - [walk] taking an [AccessibilityNodeInfo] is what the real
 *     AccessibilityService impl calls in production.
 *   - [walk] taking a [WalkableNode] is the same algorithm but operates
 *     on a test-friendly abstraction so unit tests don't need Robolectric
 *     to construct trees.
 *
 * The walker is filter-heavy by design: a Phase-3 prompt to an LLM gets
 * cheaper and more answerable when the snapshot is just the
 * human-meaningful labels, not every container View in the hierarchy.
 *   - Invisible nodes (`isVisibleToUser == false`) are dropped along with
 *     their entire subtrees.
 *   - Nodes with neither text nor contentDescription are dropped, but
 *     their children are still walked — containers are common.
 *   - Blank/whitespace-only strings are treated as absent.
 */
object NodeWalker {

    fun walk(root: AccessibilityNodeInfo): List<ScreenNode> =
        walk(AccessibilityNodeInfoWalkable(root))

    fun walk(root: WalkableNode): List<ScreenNode> {
        val out = mutableListOf<ScreenNode>()
        walkInto(root, out)
        return out
    }

    private fun walkInto(node: WalkableNode, out: MutableList<ScreenNode>) {
        if (!node.isVisibleToUser) return

        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val contentDescription = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (text != null || contentDescription != null) {
            out.add(
                ScreenNode(
                    text = text,
                    contentDescription = contentDescription,
                    className = node.className?.toString(),
                    viewIdResourceName = node.viewIdResourceName,
                    bounds = node.bounds,
                ),
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walkInto(it, out) }
        }
    }
}

/**
 * Minimal surface of [AccessibilityNodeInfo] that [NodeWalker] needs.
 * Test code constructs trees of this interface directly; production code
 * goes through [AccessibilityNodeInfoWalkable].
 */
interface WalkableNode {
    val text: CharSequence?
    val contentDescription: CharSequence?
    val className: CharSequence?
    val viewIdResourceName: String?
    val isVisibleToUser: Boolean
    val bounds: Bounds
    val childCount: Int
    fun getChild(index: Int): WalkableNode?
}

private class AccessibilityNodeInfoWalkable(
    private val node: AccessibilityNodeInfo,
) : WalkableNode {
    override val text: CharSequence? get() = node.text
    override val contentDescription: CharSequence? get() = node.contentDescription
    override val className: CharSequence? get() = node.className
    override val viewIdResourceName: String? get() = node.viewIdResourceName
    override val isVisibleToUser: Boolean get() = node.isVisibleToUser
    override val bounds: Bounds
        get() = Rect().also(node::getBoundsInScreen).let { Bounds(it.left, it.top, it.right, it.bottom) }
    override val childCount: Int get() = node.childCount
    override fun getChild(index: Int): WalkableNode? =
        node.getChild(index)?.let(::AccessibilityNodeInfoWalkable)
}
