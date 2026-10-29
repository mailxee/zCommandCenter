package com.example.xcommandcenter.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayStatusTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val today = LocalDate.of(2026, 10, 20)

    private fun status(date: LocalDate, marked: Boolean, due: Boolean = true, positive: Boolean) =
        dayStatus(date, marked, due, positive, start, today)

    @Test
    fun `positive habits store done days, so an unmarked past due day is missed`() {
        assertEquals(DayStatus.DONE, status(today.minusDays(3), marked = true, positive = true))
        assertEquals(DayStatus.MISSED, status(today.minusDays(3), marked = false, positive = true))
        assertEquals(DayStatus.PENDING, status(today, marked = false, positive = true))
        assertEquals(DayStatus.DONE, status(today, marked = true, positive = true))
    }

    @Test
    fun `negative habits store missed days, so an unmarked due day is done`() {
        assertEquals(DayStatus.MISSED, status(today.minusDays(3), marked = true, positive = false))
        assertEquals(DayStatus.DONE, status(today.minusDays(3), marked = false, positive = false))
        assertEquals(DayStatus.DONE, status(today, marked = false, positive = false))
    }

    @Test
    fun `days off the schedule, before the start or after today have no status`() {
        for (positive in listOf(true, false)) {
            assertEquals(DayStatus.NONE, status(today.minusDays(2), marked = false, due = false, positive = positive))
            assertEquals(DayStatus.NONE, status(start.minusDays(1), marked = false, positive = positive))
            assertEquals(DayStatus.NONE, status(today.plusDays(1), marked = false, positive = positive))
        }
    }
}
