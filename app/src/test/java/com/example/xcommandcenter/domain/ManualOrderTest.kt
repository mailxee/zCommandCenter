package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualOrderTest {
    private val all = listOf(1L, 2L, 3L, 4L, 5L)

    @Test
    fun movesUpAndDownInAnUnfilteredList() {
        assertEquals(listOf(1L, 3L, 2L, 4L, 5L), ManualOrder.move(all, all, 3, -1))
        assertEquals(listOf(1L, 2L, 4L, 3L, 5L), ManualOrder.move(all, all, 3, 1))
    }

    @Test
    fun refusesToMovePastEitherEnd() {
        assertNull(ManualOrder.move(all, all, 1, -1))
        assertNull(ManualOrder.move(all, all, 5, 1))
    }

    @Test
    fun hiddenItemsKeepTheirSlots() {
        // Only 1, 3 and 5 are shown; moving 5 up swaps it with 3, and 2 and 4 stay where they were.
        assertEquals(listOf(1L, 2L, 5L, 4L, 3L), ManualOrder.move(all, listOf(1L, 3L, 5L), 5, -1))
    }

    @Test
    fun anItemThatIsNotShownIsNotMoved() {
        assertNull(ManualOrder.move(all, listOf(1L, 3L), 2, 1))
        assertNull(ManualOrder.move(all, all, 9, -1))
    }
}
