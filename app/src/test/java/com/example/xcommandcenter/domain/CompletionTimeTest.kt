package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionTimeTest {
    @Test
    fun `ticking an open task records the time`() {
        assertEquals(500L, CompletionTime.after(wasCompleted = false, completed = true, previous = null, now = 500))
    }

    @Test
    fun `a task that stays ticked keeps its time, even none from before the column`() {
        assertEquals(100L, CompletionTime.after(wasCompleted = true, completed = true, previous = 100, now = 500))
        assertNull(CompletionTime.after(wasCompleted = true, completed = true, previous = null, now = 500))
    }

    @Test
    fun `unticking forgets the time`() {
        assertNull(CompletionTime.after(wasCompleted = true, completed = false, previous = 100, now = 500))
        assertNull(CompletionTime.after(wasCompleted = false, completed = false, previous = null, now = 500))
    }
}
