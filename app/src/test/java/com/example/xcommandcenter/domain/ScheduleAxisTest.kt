package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAxisTest {
    @Test
    fun `gaps of two hours or more, around and between blocks`() {
        val busy = listOf(7 * 60 to 9 * 60, 10 * 60 to 11 * 60, 14 * 60 to 18 * 60)
        // 0-7 (7 h), 9-10 (1 h, too short), 11-14 (3 h), 18-24 (6 h)
        assertEquals(listOf(0 to 420, 660 to 840, 1080 to 1440), ScheduleAxis.gaps(busy))
    }

    @Test
    fun `overlapping blocks count as one busy stretch`() {
        assertEquals(listOf(0 to 480, 720 to 1440), ScheduleAxis.gaps(listOf(480 to 600, 540 to 720, 560 to 590)))
    }

    @Test
    fun `an empty day has nothing to fold`() {
        assertTrue(ScheduleAxis.gaps(emptyList()).isEmpty())
    }

    private val axis = ScheduleAxis(listOf(0 to 420, 1080 to 1440), perMinute = 1f, bar = 30f)

    @Test
    fun `a folded gap takes only its bar`() {
        assertEquals(0f, axis.y(0))
        assertEquals(0f, axis.y(300)) // inside the first gap: its bar
        assertEquals(30f, axis.y(420)) // right after the bar
        assertEquals(30f + 600, axis.y(1020))
        assertEquals(30f + 660 + 30, axis.height)
    }

    @Test
    fun `taps map back to minutes, except on a bar`() {
        assertNull(axis.minuteAt(10f))
        assertEquals(420, axis.minuteAt(30f))
        assertEquals(480, axis.minuteAt(90f))
        assertNull(axis.minuteAt(30f + 660 + 5))
    }

    @Test
    fun `only minutes strictly inside a folded gap are hidden`() {
        assertTrue(axis.hides(60))
        assertFalse(axis.hides(420))
        assertFalse(axis.hides(600))
    }

    @Test
    fun `durations read in hours and minutes`() {
        assertEquals("45 min", durationText(45))
        assertEquals("2 h", durationText(120))
        assertEquals("1 h 15 min", durationText(75))
    }
}
