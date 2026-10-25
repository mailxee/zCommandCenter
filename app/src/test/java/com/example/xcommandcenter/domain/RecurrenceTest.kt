package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceTest {
    // A Wednesday.
    private val wednesday = LocalDate.of(2026, 10, 21)

    @Test
    fun `daily repeats the next day`() {
        assertEquals(wednesday.plusDays(1), Recurrence.Daily.nextAfter(wednesday))
    }

    @Test
    fun `weekly finds the next chosen weekday, wrapping into next week`() {
        val monFri = Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 10, 23), monFri.nextAfter(wednesday))
        assertEquals(LocalDate.of(2026, 10, 26), monFri.nextAfter(LocalDate.of(2026, 10, 23)))
        assertEquals(wednesday.plusWeeks(1), Recurrence.Weekly(setOf(DayOfWeek.WEDNESDAY)).nextAfter(wednesday))
    }

    @Test
    fun `monthly on the 31st falls on the last day of shorter months`() {
        val on31 = Recurrence.Monthly(31)
        assertEquals(LocalDate.of(2026, 2, 28), on31.nextAfter(LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 3, 31), on31.nextAfter(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `every N days counts from the given date`() {
        assertEquals(wednesday.plusDays(3), Recurrence.EveryNDays(3).nextAfter(wednesday))
    }

    @Test
    fun `weekly with no days has no next occurrence`() {
        assertNull(Recurrence.Weekly(emptySet()).nextAfter(wednesday))
    }

    @Test
    fun `of builds the matching recurrence from stored columns`() {
        val mask = Recurrence.maskFromDays(setOf(DayOfWeek.TUESDAY))
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.TUESDAY)), Recurrence.of(RecurrenceType.WEEKLY, mask, 1, 2))
        assertEquals(Recurrence.EveryNDays(5), Recurrence.of(RecurrenceType.EVERY_N_DAYS, 0, 1, 5))
    }
}
