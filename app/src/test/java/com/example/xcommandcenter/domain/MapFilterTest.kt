package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapFilterTest {
    private val goal = ItemRef(ItemType.GOAL, 1)
    private val book = ItemRef(ItemType.BOOK, 2)
    private val quote = ItemRef(ItemType.QUOTE, 3)
    private val idea = ItemRef(ItemType.IDEA, 4)
    private val lonely = ItemRef(ItemType.TASK, 5)

    private data class E(val a: ItemRef, val b: ItemRef, val kind: String)

    private val edges = listOf(
        E(idea, goal, MapFilter.PLAIN),
        E(idea, book, MapFilter.label(7)),
        E(book, quote, MapFilter.PLAIN),
    )
    private val nodes = setOf(goal, book, quote, idea, lonely)

    private fun MapFilter.run(root: ItemRef? = null) = apply(nodes, edges, root, { it.a to it.b }, { it.kind })

    @Test
    fun `with nothing hidden everything stays`() {
        val (n, e) = MapFilter().run()
        assertEquals(nodes, n)
        assertEquals(edges, e)
    }

    @Test
    fun `a hidden tab takes its items and their lines, and items left alone go too`() {
        val (n, e) = MapFilter(hiddenTypes = setOf(ItemType.BOOK)).run()
        assertEquals(setOf(idea, goal), n)
        assertEquals(listOf(edges[0]), e)
    }

    @Test
    fun `a hidden kind of link takes its lines, keeping the items other lines reach`() {
        val (n, e) = MapFilter(hiddenLinks = setOf(MapFilter.PLAIN)).run()
        assertEquals(setOf(idea, book), n)
        assertEquals(listOf(edges[1]), e)
    }

    @Test
    fun `the network's own item stays with its lines even when its tab is hidden`() {
        val (n, e) = MapFilter(hiddenTypes = setOf(ItemType.IDEA, ItemType.QUOTE)).run(root = idea)
        assertEquals(setOf(idea, goal, book), n)
        assertEquals(edges.take(2), e)
        assertFalse(quote in n)
    }

    @Test
    fun `line styles default to dotted for contradicts and read back as written`() {
        val filter = MapFilter()
        assertEquals(LineStyle.DOTTED, filter.styleOf(MapFilter.label(2), LinkLabels.CONTRADICTS))
        assertEquals(LineStyle.SOLID, filter.styleOf(MapFilter.UNDER))
        val styles = mapOf(MapFilter.UNDER to LineStyle.DASHED, MapFilter.label(12) to LineStyle.DOTTED)
        assertEquals(styles, MapFilter.parseStyles(MapFilter.encodeStyles(styles) + "junk" + "x=WAVY"))
        assertEquals(LineStyle.DASHED, MapFilter(styles = styles).styleOf(MapFilter.UNDER))
        assertEquals(setOf(ItemType.BOOK), MapFilter.parseTypes(setOf("BOOK", "NOPE")))
    }
}
