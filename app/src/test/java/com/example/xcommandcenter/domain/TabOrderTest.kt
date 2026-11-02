package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
    @Test
    fun `missing value gives the default order`() {
        assertEquals(TabOrder.default, TabOrder.parse(null))
    }

    @Test
    fun `default order starts with Schedule`() {
        assertEquals(
            listOf(ItemType.BLOCK, ItemType.GOAL, ItemType.PLAN, ItemType.TASK, ItemType.HABIT, ItemType.NOTE, ItemType.BOOK, ItemType.QUOTE,
                ItemType.PERSON),
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
        assertEquals(9, parsed.size)
    }

    @Test
    fun `an order stored before the People tab gets it at the end`() {
        val parsed = TabOrder.parse("QUOTE,BOOK,NOTE,HABIT,TASK,PLAN,GOAL,BLOCK")
        assertEquals(ItemType.PERSON, parsed.last())
        assertEquals(ItemType.QUOTE, parsed.first())
    }

    @Test
    fun `move shifts one tab`() {
        val moved = TabOrder.move(TabOrder.default, from = 0, to = 2)
        assertEquals(listOf(ItemType.GOAL, ItemType.PLAN, ItemType.BLOCK), moved.take(3))
    }
}
