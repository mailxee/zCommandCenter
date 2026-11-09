package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MapPositionsTest {
    private val goal = ItemRef(ItemType.GOAL, 3)
    private val idea = ItemRef(ItemType.IDEA, 12)

    @Test
    fun `each view keeps its own positions, and moving again replaces the old one`() {
        val think = MapPositions.viewKey(AppMode.THINK, "MIND_MAP", null)
        val network = MapPositions.viewKey(AppMode.DO, "BY_ROUTE", goal)
        var entries = MapPositions.move(null, think, idea, GraphLayout.Point(1f, 2f))
        entries = MapPositions.move(entries, network, idea, GraphLayout.Point(-0.5f, 4.25f))
        entries = MapPositions.move(entries, think, idea, GraphLayout.Point(3f, 1f))
        entries = MapPositions.move(entries, think, goal, GraphLayout.Point(0f, 0f))
        val parsed = MapPositions.parse(entries + "junk" + "a|BOOK:x|1|2" + "a|BOOK:1|NaN|2")
        assertEquals(mapOf(idea to GraphLayout.Point(3f, 1f), goal to GraphLayout.Point(0f, 0f)), parsed[think])
        assertEquals(mapOf(idea to GraphLayout.Point(-0.5f, 4.25f)), parsed[network])
        assertEquals(setOf(think, network), parsed.keys)
    }

    @Test
    fun `resetting a view forgets only its positions`() {
        val a = MapPositions.viewKey(AppMode.DO, "FREE", null)
        val b = MapPositions.viewKey(AppMode.DO, "FREE", goal)
        val entries = MapPositions.move(MapPositions.move(null, a, idea, GraphLayout.Point(1f, 1f)), b, idea, GraphLayout.Point(2f, 2f))
        val parsed = MapPositions.parse(MapPositions.reset(entries, a))
        assertEquals(setOf(b), parsed.keys)
    }
}
