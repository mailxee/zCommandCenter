package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
    @Test
    fun `missing value gives the default order`() {
        assertEquals(TabOrder.default, TabOrder.parse(null))
    }

    @Test
    fun `default order ends with Quotes`() {
        assertEquals(
            listOf(ItemType.TASK, ItemType.GOAL, ItemType.HABIT, ItemType.PROJECT, ItemType.NOTE, ItemType.BOOK, ItemType.QUOTE),
            TabOrder.default,
        )
    }

    @Test
    fun `stored order round trips`() {
        val order = TabOrder.default.reversed()
        assertEquals(order, TabOrder.parse(TabOrder.serialize(order)))
    }

    @Test
    fun `unknown names are dropped and missing tabs appended`() {
        val parsed = TabOrder.parse("QUOTE,BOGUS,TASK,CHAPTER")
        assertEquals(ItemType.QUOTE, parsed[0])
        assertEquals(ItemType.TASK, parsed[1])
        assertEquals(ItemType.tabs.toSet(), parsed.toSet())
        assertEquals(7, parsed.size)
    }

    @Test
    fun `move shifts one tab`() {
        val moved = TabOrder.move(TabOrder.default, from = 0, to = 2)
        assertEquals(listOf(ItemType.GOAL, ItemType.HABIT, ItemType.TASK), moved.take(3))
    }
}
