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
    fun `books linked to the network show, with their chapters, but are not followed`() {
        val chapters = mapOf(10L to 1L, 11L to 1L, 20L to 2L)
        // Task 1 links to book 1 through chapter 10; the book's other chapter leads to note 2,
        // which stays out. Book 2 has no link to the network.
        val links = listOf(task(1) to chapter(10), chapter(11) to note(2), chapter(20) to note(2), book(1) to book(3))
        assertEquals(setOf(task(1), book(1), chapter(10), chapter(11)), LinkNetwork.of(task(1), links, chapters))
    }

    @Test
    fun `a book as the root starts from the items linked to it`() {
        val chapters = mapOf(10L to 1L, 20L to 2L)
        // Book 1 reaches task 1 (through its chapter) and note 2; note 2 leads on to task 3, which
        // is linked to book 2. Note 4 is linked to book 2 only, so it stays out.
        val links = listOf(
            chapter(10) to task(1), book(1) to note(2), note(2) to task(3), task(3) to chapter(20), book(2) to note(4),
        )
        assertEquals(
            setOf(book(1), chapter(10), task(1), note(2), task(3), book(2), chapter(20)),
            LinkNetwork.of(book(1), links, chapters),
        )
    }
}
