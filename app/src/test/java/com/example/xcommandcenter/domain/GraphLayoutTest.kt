package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.GraphLayout.Point
import com.example.xcommandcenter.domain.GraphLayout.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphLayoutTest {
    private val route = LinkRoute()
    private fun idea(id: Long) = ItemRef(ItemType.IDEA, id)

    /** Boxes of different sizes, so the layouts must use each one's own. */
    private val size = { r: ItemRef -> Size(80f + (r.id % 4) * 25f, 24f + (r.id % 3) * 14f) }

    private fun assertNoOverlaps(at: Map<ItemRef, Point>) {
        val list = at.entries.toList()
        for (i in list.indices) for (j in i + 1 until list.size) {
            val (a, pa) = list[i]
            val (b, pb) = list[j]
            assertFalse("$a and $b overlap", GraphLayout.overlap(pa, size(a), pb, size(b)))
        }
    }

    @Test
    fun theTreeFollowsTheFirstLinksAndStartsAnotherForWhatIsNotReached() {
        val a = idea(1)
        val b = idea(2)
        val c = idea(3)
        val lone = idea(9)
        // c is under b (listed first), and also linked to a.
        val t = GraphLayout.tree(listOf(a), listOf(a, b, c, lone), listOf(b to a, c to b, c to a))
        assertEquals(a, t.parent[c])
        assertEquals(listOf(a, lone), t.roots)
        assertEquals(setOf(b, c), t.descendants(a))
        assertTrue(t.joins(c, a))
        assertFalse(t.joins(c, b))
        // c also linked to a, but under b: Under decides.
        val under = GraphLayout.tree(listOf(a), listOf(a, b, c), listOf(c to a, b to a, c to b), firm = listOf(b to a, c to b))
        assertEquals(b, under.parent[c])
        assertEquals(a, under.parent[b])
        // Items under the same one keep their order.
        val kids = (2L..5L).map { idea(it) }
        val ordered = GraphLayout.tree(listOf(a), listOf(a) + kids, kids.map { it to a }, firm = kids.map { it to a })
        assertEquals(kids, ordered.children[a])
    }

    @Test
    fun byRouteGoesTopToBottomAndOrdersRowsToAvoidCrossings() {
        val g = ItemRef(ItemType.GOAL, 1)
        val p1 = ItemRef(ItemType.PLAN, 1)
        val p2 = ItemRef(ItemType.PLAN, 2)
        val t1 = ItemRef(ItemType.TASK, 1)
        val t2 = ItemRef(ItemType.TASK, 2)
        val t3 = ItemRef(ItemType.TASK, 3)
        val t4 = ItemRef(ItemType.TASK, 4)
        // Listed crossed: t3 and t4 under p1, t1 and t2 under p2; each pair links on, so none stacks.
        val n = ItemRef(ItemType.NOTE, 1)
        val e = listOf(g to p1, g to p2, p1 to t3, p1 to t4, p2 to t1, p2 to t2, t1 to n, t2 to n, t3 to n, t4 to n)
        val at = GraphLayout.byRoute(listOf(g, p1, p2, t1, t2, t3, t4, n), e, { route.layer(it.type) }, size, maxWidth = 1000f).at
        assertTrue(at.getValue(g).y < at.getValue(p1).y)
        assertTrue(at.getValue(p1).y < at.getValue(t1).y)
        assertTrue(at.getValue(t1).y < at.getValue(n).y)
        assertEquals(at.getValue(p1).x < at.getValue(p2).x, at.getValue(t3).x < at.getValue(t1).x)
        assertNoOverlaps(at)
    }

    @Test
    fun itemsLinkedOnlyToOneItemAboveStackAsAList() {
        val plan = ItemRef(ItemType.PLAN, 1)
        val tasks = (1L..4L).map { ItemRef(ItemType.TASK, it) }
        val r = GraphLayout.byRoute(listOf(plan) + tasks, tasks.map { plan to it }, { route.layer(it.type) }, size, maxWidth = 1000f)
        assertEquals(tasks.toSet(), r.stackedUnder.keys)
        val lefts = tasks.map { r.at.getValue(it).x - size(it).w / 2 }
        assertTrue(lefts.all { abs(it - lefts[0]) < 0.01f })
        assertTrue(tasks.map { r.at.getValue(it).y }.zipWithNext().all { (a, b) -> a < b })
        assertTrue(lefts[0] > r.at.getValue(plan).x - size(plan).w / 2)
        assertNoOverlaps(r.at)
    }

    @Test
    fun aRowTooWideForTheScreenWraps() {
        val tasks = (1L..10L).map { ItemRef(ItemType.TASK, it) }
        val at = GraphLayout.byRoute(tasks, emptyList(), { 0 }, size, maxWidth = 400f).at
        assertTrue(at.values.map { it.y }.distinct().size > 2)
        val box = GraphLayout.bounds(at, size)
        assertTrue(box[2] - box[0] <= 400f)
        assertNoOverlaps(at)
    }

    private fun abs(f: Float) = kotlin.math.abs(f)
}
