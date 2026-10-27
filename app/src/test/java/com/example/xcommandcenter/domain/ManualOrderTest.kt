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

    @Test
    fun reorderAppliesADraggedOrderAroundHiddenItems() {
        // 1, 3 and 5 are shown and dragged into the order 5, 1, 3; 2 and 4 keep their slots.
        assertEquals(listOf(5L, 2L, 1L, 4L, 3L), ManualOrder.reorder(all, listOf(5L, 1L, 3L)))
        assertEquals(all, ManualOrder.reorder(all, listOf(9L)))
    }

    private data class Row(val name: String, val depth: Int)

    private fun names(rows: List<Row>) = rows.joinToString(" ") { it.name }

    // A (with child a1), B, C (with children c1, c2)
    private val outline = listOf(Row("A", 0), Row("a1", 1), Row("B", 0), Row("C", 0), Row("c1", 1), Row("c2", 1))

    @Test
    fun moveBlockTakesTheRowsBelowAlong() {
        // A moves down past B.
        assertEquals("B A a1 C c1 c2", names(ManualOrder.moveBlock(outline, { it.depth }, 0, 2)))
        // B moves down past C and everything under C.
        assertEquals("A a1 C c1 c2 B", names(ManualOrder.moveBlock(outline, { it.depth }, 2, 3)))
        // C moves up before A, with its children.
        assertEquals("C c1 c2 A a1 B", names(ManualOrder.moveBlock(outline, { it.depth }, 3, 0)))
    }

    @Test
    fun moveBlockOntoItsOwnRowsChangesNothing() {
        assertEquals(outline, ManualOrder.moveBlock(outline, { it.depth }, 0, 1))
        assertEquals(outline, ManualOrder.moveBlock(outline, { it.depth }, 3, 3))
    }
}
