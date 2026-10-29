package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleLayoutTest {
    private data class B(val name: String, val start: Int, val end: Int)

    private fun place(vararg blocks: B) =
        ScheduleLayout.place(blocks.toList(), { it.start }, { it.end }).associate { it.item.name to (it.column to it.columns) }

    @Test
    fun `blocks that don't overlap each take the full width`() {
        assertEquals(mapOf("a" to (0 to 1), "b" to (0 to 1)), place(B("a", 60, 120), B("b", 120, 180)))
    }

    @Test
    fun `overlapping blocks sit side by side`() {
        assertEquals(
            mapOf("a" to (0 to 2), "b" to (1 to 2), "c" to (0 to 1)),
            place(B("a", 600, 690), B("b", 660, 690), B("c", 700, 760)),
        )
    }

    @Test
    fun `a freed column is reused within a group`() {
        // b overlaps a and c, so all three are one group, but c can take a's column.
        assertEquals(
            mapOf("a" to (0 to 2), "b" to (1 to 2), "c" to (0 to 2)),
            place(B("a", 0, 60), B("b", 30, 120), B("c", 60, 90)),
        )
    }
}
