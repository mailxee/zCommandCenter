package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabModesTest {
    @Test
    fun `the default modes split the tabs, with notes and people in both`() {
        val modes = TabModes()
        assertEquals(
            listOf(ItemType.BLOCK, ItemType.GOAL, ItemType.PLAN, ItemType.TASK, ItemType.HABIT, ItemType.NOTE, ItemType.PERSON),
            modes.tabs(TabOrder.default, AppMode.DO),
        )
        assertEquals(
            listOf(ItemType.IDEA, ItemType.NOTE, ItemType.BOOK, ItemType.QUOTE, ItemType.PERSON),
            modes.tabs(TabOrder.default, AppMode.THINK),
        )
        assertEquals(TabMode.THINK, modes.of(ItemType.CHAPTER))
    }

    @Test
    fun `items of the other mode only are marked`() {
        val modes = TabModes()
        assertTrue(modes.isOtherMode(ItemType.PLAN, AppMode.THINK))
        assertTrue(modes.isOtherMode(ItemType.IDEA, AppMode.DO))
        assertFalse(modes.isOtherMode(ItemType.NOTE, AppMode.THINK))
        assertFalse(modes.isOtherMode(ItemType.GOAL, AppMode.DO))
    }

    @Test
    fun `modes round trip and unreadable parts keep their defaults`() {
        val modes = TabModes().with(ItemType.QUOTE, TabMode.BOTH).with(ItemType.NOTE, TabMode.THINK)
        assertEquals(modes.encode(), TabModes.parse(modes.encode()).encode())
        val parsed = TabModes.parse("QUOTE:BOTH,BOGUS:DO,TASK:NOPE")
        assertEquals(TabMode.BOTH, parsed.of(ItemType.QUOTE))
        assertEquals(TabMode.DO, parsed.of(ItemType.TASK))
    }
}
