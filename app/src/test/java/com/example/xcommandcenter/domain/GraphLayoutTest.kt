package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphLayoutTest {
    private fun ref(type: ItemType, id: Long) = ItemRef(type, id)
    private val route = LinkRoute()

    @Test
    fun layersGoTopToBottom() {
        val g = ref(ItemType.GOAL, 1)
        val p = ref(ItemType.PLAN, 1)
        val t = ref(ItemType.TASK, 1)
        val n = ref(ItemType.NOTE, 1)
        val at = GraphLayout.layered(listOf(n, t, p, g), listOf(g to p, p to t, t to n)) { route.layer(it.type) }
        assertTrue(at.getValue(g).y < at.getValue(p).y)
        assertTrue(at.getValue(p).y < at.getValue(t).y)
        assertTrue(at.getValue(t).y < at.getValue(n).y)
    }

    @Test
    fun orderFollowsLinksToAvoidCrossings() {
        val p1 = ref(ItemType.PLAN, 1)
        val p2 = ref(ItemType.PLAN, 2)
        val t1 = ref(ItemType.TASK, 1)
        val t2 = ref(ItemType.TASK, 2)
        // Listed crossed: t2 under p1, t1 under p2.
        val at = GraphLayout.layered(listOf(p1, p2, t1, t2), listOf(p1 to t2, p2 to t1)) { route.layer(it.type) }
        assertEquals(at.getValue(p1).x < at.getValue(p2).x, at.getValue(t2).x < at.getValue(t1).x)
    }

    @Test
    fun wideLayersWrap() {
        val tasks = (1L..10L).map { ref(ItemType.TASK, it) }
        val at = GraphLayout.layered(tasks, emptyList(), maxPerRow = 4) { 0 }
        assertEquals(3, at.values.map { it.y }.distinct().size)
        assertTrue(at.values.all { it.x in -1.5f..1.5f })
    }

    @Test
    fun freeLayoutIsDeterministicAndSpreadsItemsApart() {
        val nodes = (1L..6L).map { ref(ItemType.TASK, it) }
        val edges = nodes.zipWithNext()
        val start = GraphLayout.layered(nodes, edges) { 0 }
        val a = GraphLayout.free(nodes, edges, start)
        assertEquals(a, GraphLayout.free(nodes, edges, start))
        val points = a.values.toList()
        for (i in points.indices) for (j in i + 1 until points.size) {
            val dx = points[i].x - points[j].x
            val dy = points[i].y - points[j].y
            assertTrue(dx * dx + dy * dy > 0.05f)
        }
    }
}
