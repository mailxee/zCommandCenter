package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaOutlineTest {
    private data class N(val id: Long, val parent: Long?, val kind: IdeaKind)

    // 1 vision > 2 belief > 3 principle, 4 idea; 5 vision alone.
    private val nodes = listOf(
        N(1, null, IdeaKind.VISION), N(2, 1, IdeaKind.BELIEF), N(3, 2, IdeaKind.PRINCIPLE), N(4, 2, IdeaKind.IDEA), N(5, null, IdeaKind.VISION),
    )

    private fun rows(collapsed: Set<Long> = emptySet(), narrowed: Boolean = false, hit: (N) -> Boolean = { true }) =
        IdeaOutline.rows(nodes, { it.id }, { it.parent }, collapsed, narrowed, hit)

    @Test
    fun `the tree reads depth first with depths and child counts`() {
        val rows = rows()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), rows.map { it.node.id })
        assertEquals(listOf(0, 1, 2, 2, 0), rows.map { it.depth })
        assertEquals(listOf(1, 2, 0, 0, 0), rows.map { it.childCount })
    }

    @Test
    fun `a collapsed idea hides what is under it but still counts it`() {
        val rows = rows(collapsed = setOf(2))
        assertEquals(listOf(1L, 2L, 5L), rows.map { it.node.id })
        assertTrue(rows[1].collapsed)
        assertEquals(2, rows[1].childCount)
    }

    @Test
    fun `filtering keeps the ideas above a match`() {
        val rows = rows(narrowed = true) { it.kind == IdeaKind.IDEA }
        assertEquals(listOf(1L, 2L, 4L), rows.map { it.node.id })
        assertEquals(1, rows[1].childCount)
    }

    @Test
    fun `kind chips show only the kinds tapped and hide the kinds held`() {
        val only = IdeaKindFilter().tap(IdeaKind.BELIEF).tap(IdeaKind.IDEA)
        assertTrue(only.matches(IdeaKind.IDEA))
        assertFalse(only.matches(IdeaKind.VISION))
        val hidden = IdeaKindFilter().hold(IdeaKind.QUESTION)
        assertFalse(hidden.matches(IdeaKind.QUESTION))
        assertTrue(hidden.matches(IdeaKind.VISION))
        assertFalse(IdeaKindFilter().hold(IdeaKind.QUESTION).hold(IdeaKind.QUESTION).active)
        assertFalse(only.tap(IdeaKind.BELIEF).tap(IdeaKind.IDEA).active)
    }
}
