package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkNetworkTest {
    private fun task(id: Long) = ItemRef(ItemType.TASK, id)
    private fun note(id: Long) = ItemRef(ItemType.NOTE, id)
    private fun book(id: Long) = ItemRef(ItemType.BOOK, id)
    private fun chapter(id: Long) = ItemRef(ItemType.CHAPTER, id)

    @Test
    fun `follows links any number of steps in both directions`() {
        val links = listOf(task(1) to note(2), note(3) to note(2), note(3) to task(4), task(5) to task(6))
        assertEquals(setOf(task(1), note(2), note(3), task(4)), LinkNetwork.of(note(3), links, emptyMap()))
    }

    @Test
    fun `an item without links is a network of one`() {
        assertEquals(setOf(task(9)), LinkNetwork.of(task(9), listOf(task(1) to task(2)), emptyMap()))
    }

    @Test
    fun `a chapter and its book are one item`() {
        val chapters = mapOf(10L to 1L, 11L to 1L, 20L to 2L)
        // Task 1 reaches the book through chapter 10; the book's other chapter leads on to note 2.
        val links = listOf(task(1) to chapter(10), chapter(11) to note(2), chapter(20) to task(3))
        assertEquals(
            setOf(task(1), book(1), chapter(10), chapter(11), note(2)),
            LinkNetwork.of(task(1), links, chapters),
        )
        assertEquals(
            setOf(task(1), book(1), chapter(10), chapter(11), note(2)),
            LinkNetwork.of(chapter(11), links, chapters),
        )
    }
}
