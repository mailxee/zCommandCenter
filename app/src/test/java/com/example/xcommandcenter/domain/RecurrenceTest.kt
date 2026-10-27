package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    fun `every N months keeps its day of month`() {
        val quarterly = Recurrence.EveryNMonths(3, 31)
        // From Jan 31: Apr 30 (April is short), then Jul 31, not Jul 30.
        assertEquals(LocalDate.of(2026, 4, 30), quarterly.nextAfter(LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 7, 31), quarterly.nextAfter(LocalDate.of(2026, 4, 30)))
        // A day earlier in the month than the due date falls N months later.
        assertEquals(LocalDate.of(2027, 1, 5), Recurrence.EveryNMonths(3, 5).nextAfter(LocalDate.of(2026, 10, 20)))
        // Longer than a year is still found.
        assertEquals(LocalDate.of(2028, 10, 20), Recurrence.EveryNMonths(24, 20).nextAfter(LocalDate.of(2026, 10, 20)))
        assertEquals(Recurrence.EveryNMonths(6, 15), Recurrence.of(RecurrenceType.EVERY_N_MONTHS, 0, 15, 6))
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

    @Test
    fun nextFromSkipsOccurrencesThatHavePassed() {
        val due = LocalDate.of(2026, 10, 20)
        // Completed a week late: the next occurrence is today, not the 21st.
        assertEquals(LocalDate.of(2026, 10, 27), Recurrence.Daily.nextFrom(due, LocalDate.of(2026, 10, 27)))
        // Every 3 days counted from the due date: 23, 26, 29 ... so the 29th.
        assertEquals(LocalDate.of(2026, 10, 29), Recurrence.EveryNDays(3).nextFrom(due, LocalDate.of(2026, 10, 27)))
        // Completed early: the same as nextAfter.
        assertEquals(LocalDate.of(2026, 10, 21), Recurrence.Daily.nextFrom(due, LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun nextReminderIsOnTheNextScheduledDayAtTheTime() {
        val start = LocalDate.of(2026, 10, 1)
        val nine = LocalTime.of(9, 0)
        // Every 2 days from the 1st: the 27th is scheduled, but 9:00 has passed, so the 29th.
        assertEquals(
            LocalDateTime.of(2026, 10, 29, 9, 0),
            Recurrence.EveryNDays(2).nextReminder(start, nine, LocalDateTime.of(2026, 10, 27, 10, 0)),
        )
        // Before 9:00 on a scheduled day: that same day.
        assertEquals(
            LocalDateTime.of(2026, 10, 27, 9, 0),
            Recurrence.EveryNDays(2).nextReminder(start, nine, LocalDateTime.of(2026, 10, 27, 8, 0)),
        )
        // A habit that starts later reminds from its start date.
        assertEquals(
            LocalDateTime.of(2026, 11, 5, 9, 0),
            Recurrence.Daily.nextReminder(LocalDate.of(2026, 11, 5), nine, LocalDateTime.of(2026, 10, 27, 8, 0)),
        )
        assertNull(Recurrence.Weekly(emptySet()).nextReminder(start, nine, LocalDateTime.of(2026, 10, 27, 8, 0)))
    }
}
