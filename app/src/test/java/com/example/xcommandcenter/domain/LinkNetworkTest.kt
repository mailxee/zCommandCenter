package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkNetworkTest {
    private fun task(id: Long) = ItemRef(ItemType.TASK, id)
    private fun goal(id: Long) = ItemRef(ItemType.GOAL, id)
    private fun note(id: Long) = ItemRef(ItemType.NOTE, id)
    private fun quote(id: Long) = ItemRef(ItemType.QUOTE, id)
    private fun book(id: Long) = ItemRef(ItemType.BOOK, id)
    private fun chapter(id: Long) = ItemRef(ItemType.CHAPTER, id)

    @Test
    fun `follows links any number of steps in both directions`() {
        val links = listOf(task(1) to goal(2), goal(3) to goal(2), goal(3) to task(4), task(5) to task(6))
        assertEquals(setOf(task(1), goal(2), goal(3), task(4)), LinkNetwork.of(goal(3), links, emptyMap()))
    }

    @Test
    fun `an item without links is a network of one`() {
        assertEquals(setOf(task(9)), LinkNetwork.of(task(9), listOf(task(1) to task(2)), emptyMap()))
    }

    @Test
    fun `notes, quotes and books linked to the network show but are not followed`() {
        val chapters = mapOf(10L to 1L, 11L to 1L, 20L to 2L)
        // Task 1 links to book 1 through chapter 10; the book's other chapter leads to task 2,
        // which stays out. Note 3 leads to task 4, which stays out too; quote 5, linked only to
        // the note, stays out. Book 2 has no link to the network.
        val links = listOf(
            task(1) to chapter(10), chapter(11) to task(2), chapter(20) to task(2),
            goal(6) to note(3), task(1) to goal(6), note(3) to task(4), note(3) to quote(5),
        )
        assertEquals(
            setOf(task(1), goal(6), book(1), chapter(10), chapter(11), note(3)),
            LinkNetwork.of(task(1), links, chapters),
        )
    }

    @Test
    fun `an end point as the root starts from the items linked to it`() {
        val chapters = mapOf(10L to 1L, 20L to 2L)
        // Book 1 reaches task 1 (through its chapter); task 1 leads on to goal 3, which is linked
        // to book 2. Note 4 is linked to book 2 only, so it stays out.
        val links = listOf(chapter(10) to task(1), task(1) to goal(3), goal(3) to chapter(20), book(2) to note(4))
        assertEquals(
            setOf(book(1), chapter(10), task(1), goal(3), book(2), chapter(20)),
            LinkNetwork.of(book(1), links, chapters),
        )
    }

    @Test
    fun `an end point as the root also shows the end points linked to it, one step only`() {
        // Note 1 is linked to quote 2 and book 3; quote 2 leads on to note 4, which stays out.
        val links = listOf(note(1) to quote(2), note(1) to book(3), quote(2) to note(4))
        assertEquals(setOf(note(1), quote(2), book(3)), LinkNetwork.of(note(1), links, emptyMap()))
    }
}
