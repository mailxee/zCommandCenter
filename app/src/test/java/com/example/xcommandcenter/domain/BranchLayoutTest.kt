package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.GraphLayout.Point
import com.example.xcommandcenter.domain.GraphLayout.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchLayoutTest {
    private fun idea(id: Long) = ItemRef(ItemType.IDEA, id)

    /** Boxes of different sizes, so the layouts must use each one's own. */
    private val size = { r: ItemRef -> Size(80f + (r.id % 4) * 25f, 24f + (r.id % 3) * 14f) }

    // A root with three branches, the first two with leaves.
    private val root = idea(1)
    private val branches = listOf(idea(2), idea(3), idea(4))
    private val leaves = mapOf(idea(2) to listOf(idea(5), idea(6), idea(7)), idea(3) to listOf(idea(8), idea(9)))
    private val nodes = listOf(root) + branches + leaves.values.flatten()
    private val edges = branches.map { it to root } + leaves.flatMap { (b, ls) -> ls.map { it to b } }
    private val tree = GraphLayout.tree(listOf(root), nodes, edges)

    private fun place(
        top: MapStyle,
        chosen: Map<ItemRef, MapStyle> = emptyMap(),
        bothSides: Boolean = false,
        offsets: Map<ItemRef, Point> = emptyMap(),
        folded: Set<ItemRef> = emptySet(),
        roots: List<ItemRef> = listOf(root),
    ) = BranchLayout.place(
        roots, { if (it in folded) emptyList() else tree.children[it].orEmpty() }, size, chosen::get, top, bothSides,
        maxWidth = 400f, layerOf = { if (it == root) 0 else if (it in branches) 1 else 2 }, edges = edges, offsets = offsets,
    )

    private fun assertNoOverlaps(at: Map<ItemRef, Point>) {
        val list = at.entries.toList()
        for (i in list.indices) for (j in i + 1 until list.size) {
            val (a, pa) = list[i]
            val (b, pb) = list[j]
            assertFalse("$a and $b overlap", GraphLayout.overlap(pa, size(a), pb, size(b)))
        }
    }

    @Test
    fun everyLayoutPlacesEveryItemWithoutOverlaps() {
        MapStyle.entries.forEach { style ->
            val r = place(style)
            assertEquals(style.name, nodes.toSet(), r.at.keys)
            assertNoOverlaps(r.at)
        }
    }

    @Test
    fun onAPhoneTheMindMapGoesRightAndReadsDown() {
        val at = place(MapStyle.MIND_MAP).at
        assertTrue(nodes.drop(1).all { at.getValue(it).x > at.getValue(root).x })
        // The root level with its first branch, and each item level with the middle of its column.
        assertEquals(at.getValue(branches[0]).y, at.getValue(root).y, 0.01f)
        val firstLeaves = leaves.getValue(idea(2)).map { at.getValue(it).y }
        assertEquals((firstLeaves.first() + firstLeaves.last()) / 2, at.getValue(idea(2)).y, 0.01f)
        assertTrue(firstLeaves.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun onAWideScreenBranchesSplitBothWays() {
        val at = place(MapStyle.MIND_MAP, bothSides = true).at
        assertTrue(at.getValue(branches[0]).x > at.getValue(root).x)
        assertTrue(at.getValue(branches.last()).x < at.getValue(root).x)
    }

    @Test
    fun aTreeHangsBranchesBelowAndStacksPlainLists() {
        val r = place(MapStyle.TREE)
        assertTrue(branches.all { r.at.getValue(it).y > r.at.getValue(root).y })
        assertEquals(BranchLayout.Line.DOWN, r.line[branches[0]])
        // idea(2)'s leaves are a plain list: stacked below it, lined up on the left.
        val list = leaves.getValue(idea(2))
        assertTrue(list.all { r.line[it] == BranchLayout.Line.ELBOW })
        val lefts = list.map { r.at.getValue(it).x - size(it).w / 2 }
        assertTrue(lefts.all { kotlin.math.abs(it - lefts[0]) < 0.01f })
        assertTrue(list.map { r.at.getValue(it).y }.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun aTreeTooWideWrapsAndReachesLaterRowsDownTheSide() {
        val wide = (2L..9L).map { idea(it) }
        val r = BranchLayout.place(
            listOf(root), { if (it == root) wide else if (it == wide[0]) listOf(idea(20)) else emptyList() }, size, { null },
            MapStyle.TREE, false, maxWidth = 300f, layerOf = { 0 }, edges = emptyList(),
        )
        val later = wide.filter { r.line[it] == BranchLayout.Line.CHANNEL }
        assertTrue(later.isNotEmpty())
        val channel = r.channel.getValue(later[0])
        assertTrue(wide.all { channel[0] < r.at.getValue(it).x - size(it).w / 2 })
    }

    @Test
    fun ringsGoRoundTheItem() {
        val r = place(MapStyle.RINGS)
        val c = r.at.getValue(root)
        val d = branches.map { kotlin.math.hypot(r.at.getValue(it).x - c.x, r.at.getValue(it).y - c.y) }
        assertTrue(d.all { it > 40f })
        assertEquals(BranchLayout.Line.STRAIGHT, r.line[branches[1]])
    }

    @Test
    fun aBranchCanHaveItsOwnLayout() {
        // A mind map whose first branch is an outline.
        val r = place(MapStyle.MIND_MAP, chosen = mapOf(idea(2) to MapStyle.OUTLINE))
        assertEquals(MapStyle.OUTLINE, r.style[idea(2)])
        assertEquals(MapStyle.MIND_MAP, r.style[idea(3)])
        assertEquals(BranchLayout.Line.SIDE, r.line[idea(2)])
        assertEquals(BranchLayout.Line.ELBOW, r.line[idea(5)])
        assertEquals(BranchLayout.Line.SIDE, r.line[idea(8)])
        // The outline's items are below their item, indented.
        assertTrue(leaves.getValue(idea(2)).all { r.at.getValue(it).y > r.at.getValue(idea(2)).y })
        assertNoOverlaps(r.at)
    }

    @Test
    fun byRouteTakesItsWholeBranch() {
        val r = place(MapStyle.MIND_MAP, chosen = mapOf(idea(2) to MapStyle.BY_ROUTE, idea(5) to MapStyle.RINGS))
        assertEquals(MapStyle.BY_ROUTE, r.style[idea(5)])
        val group = r.routeGroup.getValue(idea(2))
        assertTrue(leaves.getValue(idea(2)).all { r.routeGroup[it] == group })
        assertFalse(idea(3) in r.routeGroup)
    }

    @Test
    fun aDraggedItemTakesItsBranchAlongInShape() {
        val before = place(MapStyle.MIND_MAP).at
        val after = place(MapStyle.MIND_MAP, offsets = mapOf(idea(2) to Point(30f, -50f))).at
        (listOf(idea(2)) + leaves.getValue(idea(2))).forEach {
            assertEquals(before.getValue(it).x + 30f, after.getValue(it).x, 0.01f)
            assertEquals(before.getValue(it).y - 50f, after.getValue(it).y, 0.01f)
        }
        assertEquals(before.getValue(idea(3)), after.getValue(idea(3)))
    }

    @Test
    fun aFoldedBranchTakesNoRoom() {
        fun height(at: Map<ItemRef, Point>) = GraphLayout.bounds(at, size).let { it[3] - it[1] }
        val folded = place(MapStyle.MIND_MAP, folded = setOf(idea(2))).at
        assertFalse(idea(5) in folded)
        assertTrue(height(folded) < height(place(MapStyle.MIND_MAP).at))
    }

    @Test
    fun severalTreesShareTheMapsLayout() {
        val other = idea(20)
        val at = place(MapStyle.MIND_MAP, roots = listOf(root, other)).at
        assertTrue(other in at)
        assertNoOverlaps(at)
    }

    @Test
    fun layoutsAreTheSameEveryTime() {
        MapStyle.entries.forEach { assertEquals(place(it).at, place(it).at) }
    }
}
