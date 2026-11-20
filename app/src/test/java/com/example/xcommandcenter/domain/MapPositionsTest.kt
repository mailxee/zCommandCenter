package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPositionsTest {
    private val goal = ItemRef(ItemType.PLAN, 3)
    private val idea = ItemRef(ItemType.IDEA, 12)

    @Test
    fun `each view keeps its own offsets, and moving again replaces the old one`() {
        val think = MapPositions.viewKey(AppMode.THINK, null)
        val network = MapPositions.viewKey(AppMode.DO, goal)
        var entries = MapPositions.move(null, think, idea, GraphLayout.Point(1f, 2f))
        entries = MapPositions.move(entries, network, idea, GraphLayout.Point(-0.5f, 4.25f))
        entries = MapPositions.move(entries, think, idea, GraphLayout.Point(3f, 1f))
        entries = MapPositions.move(entries, think, goal, GraphLayout.Point(0f, 0f))
        val parsed = MapPositions.parse(entries + "junk" + "o:a|BOOK:x|1|2" + "o:a|BOOK:1|NaN|2")
        assertEquals(mapOf(idea to GraphLayout.Point(3f, 1f), goal to GraphLayout.Point(0f, 0f)), parsed[think])
        assertEquals(mapOf(idea to GraphLayout.Point(-0.5f, 4.25f)), parsed[network])
        assertEquals(setOf(think, network), parsed.keys)
    }

    @Test
    fun `resetting or forgetting clears only what was asked, and old places go`() {
        val a = MapPositions.viewKey(AppMode.DO, null)
        val b = MapPositions.viewKey(AppMode.DO, goal)
        val old = setOf("DO/FREE/all|IDEA:12|1.0|1.0")
        val entries = MapPositions.move(MapPositions.move(old, a, idea, GraphLayout.Point(1f, 1f)), b, idea, GraphLayout.Point(2f, 2f))
        assertTrue(old.none { it in entries })
        assertEquals(setOf(b), MapPositions.parse(MapPositions.reset(entries, a)).keys)
        val forgot = MapPositions.parse(MapPositions.forget(MapPositions.move(entries, a, goal, GraphLayout.Point(5f, 5f)), a, listOf(idea)))
        assertEquals(mapOf(goal to GraphLayout.Point(5f, 5f)), forgot[a])
        assertEquals(mapOf(idea to GraphLayout.Point(2f, 2f)), forgot[b])
    }

    @Test
    fun `chosen layouts are kept per mode and item, and cleared back to the default`() {
        var entries = MapStyles.set(null, AppMode.THINK, idea, MapStyle.RINGS)
        entries = MapStyles.set(entries, AppMode.THINK, null, MapStyle.TREE)
        entries = MapStyles.set(entries, AppMode.DO, goal, MapStyle.OUTLINE)
        entries = MapStyles.set(entries, AppMode.THINK, idea, MapStyle.FREE)
        val parsed = MapStyles.parse(entries + "junk" + "THINK|IDEA:1|NOPE")
        assertEquals(mapOf(idea to MapStyle.FREE, null to MapStyle.TREE), parsed[AppMode.THINK])
        assertEquals(mapOf(goal to MapStyle.OUTLINE), parsed[AppMode.DO])
        assertEquals(mapOf(null to MapStyle.TREE), MapStyles.parse(MapStyles.set(entries, AppMode.THINK, idea, null))[AppMode.THINK])
    }
}
