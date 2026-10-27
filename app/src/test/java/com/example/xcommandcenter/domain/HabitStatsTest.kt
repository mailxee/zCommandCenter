package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class HabitStatsTest {
    // A Wednesday.
    private val today = LocalDate.of(2026, 10, 21)
    private val start = today.minusDays(60)

    private fun streak(category: HabitCategory, recurrence: Recurrence, marks: Set<LocalDate>) =
        HabitStats.streak(category, recurrence, start, marks, today)

    @Test
    fun `positive streak counts consecutive marked days`() {
        val marks = (1L..4L).map { today.minusDays(it) }.toSet()
        assertEquals(4, streak(HabitCategory.POSITIVE, Recurrence.Daily, marks))
    }

    @Test
    fun `positive streak includes today when marked`() {
        val marks = (0L..2L).map { today.minusDays(it) }.toSet()
        assertEquals(3, streak(HabitCategory.POSITIVE, Recurrence.Daily, marks))
    }

    @Test
    fun `unmarked past day is unknown and ends a positive streak`() {
        val marks = setOf(today.minusDays(1), today.minusDays(3), today.minusDays(4))
        assertEquals(1, streak(HabitCategory.POSITIVE, Recurrence.Daily, marks))
    }

    @Test
    fun `positive streak with no marks is zero`() {
        assertEquals(0, streak(HabitCategory.POSITIVE, Recurrence.Daily, emptySet()))
    }

    @Test
    fun `weekly streak skips unscheduled days`() {
        val schedule = Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        // Mon 19th, Wed 14th, Mon 12th marked; today (Wed 21st) not yet marked.
        val marks = setOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 12))
        assertEquals(3, streak(HabitCategory.POSITIVE, schedule, marks))
    }

    @Test
    fun `negative streak counts occurrences since the last miss`() {
        val marks = setOf(today.minusDays(6))
        // Today and the five days before it are clean.
        assertEquals(6, streak(HabitCategory.NEGATIVE, Recurrence.Daily, marks))
    }

    @Test
    fun `negative streak without misses counts from the start date`() {
        assertEquals(61, streak(HabitCategory.NEGATIVE, Recurrence.Daily, emptySet()))
    }

    @Test
    fun `negative streak is zero when today is missed`() {
        assertEquals(0, streak(HabitCategory.NEGATIVE, Recurrence.Daily, setOf(today)))
    }

    @Test
    fun `negative every N days counts only scheduled occurrences`() {
        // Every 3 days from start: occurrences on today-60, -57, ..., -3, today.
        val schedule = Recurrence.EveryNDays(3)
        assertEquals(3, streak(HabitCategory.NEGATIVE, schedule, setOf(today.minusDays(9))))
    }

    @Test
    fun `monthly day past month end falls on last day`() {
        val schedule = Recurrence.Monthly(31)
        assertTrue(schedule.isScheduled(LocalDate.of(2026, 2, 28), start = LocalDate.of(2026, 1, 1)))
        assertTrue(schedule.isScheduled(LocalDate.of(2026, 3, 31), start = LocalDate.of(2026, 1, 1)))
        assertFalse(schedule.isScheduled(LocalDate.of(2026, 3, 30), start = LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `nothing is scheduled before the start date`() {
        assertFalse(Recurrence.Daily.isScheduled(start.minusDays(1), start))
    }

    @Test
    fun `recent occurrences are newest first and limited`() {
        val schedule = Recurrence.Weekly(setOf(DayOfWeek.MONDAY))
        val result = HabitStats.recentOccurrences(schedule, start, today, 3)
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 5)), result)
    }

    @Test
    fun `weekday mask round trips`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
        assertEquals(days, Recurrence.daysFromMask(Recurrence.maskFromDays(days)))
    }

    @Test
    fun `every N days is due daily after a miss, then counts from the day it was done`() {
        val every3 = Recurrence.EveryNDays(3)
        val start = LocalDate.of(2026, 10, 1)
        val now = LocalDate.of(2026, 10, 12)
        // Due 1st (done), 4th (missed), so 5th (missed) and 6th (done), then 9th (done), 12th.
        val marks = setOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 9))
        val due = HabitStats.dueDates(HabitCategory.POSITIVE, every3, start, marks, now, until = now)
        assertEquals(listOf(1, 4, 5, 6, 9, 12), due.map { it.dayOfMonth })
        // The 4th and 5th were missed: the streak runs from the 6th (12th not yet marked).
        assertEquals(2, HabitStats.streak(HabitCategory.POSITIVE, every3, start, marks, now))
        assertTrue(HabitStats.isDue(HabitCategory.POSITIVE, every3, start, marks, LocalDate.of(2026, 10, 5)))
        assertFalse(HabitStats.isDue(HabitCategory.POSITIVE, every3, start, marks, LocalDate.of(2026, 10, 7)))
    }

    @Test
    fun `a negative every N days habit marked missed is due the next day`() {
        val every3 = Recurrence.EveryNDays(3)
        val start = LocalDate.of(2026, 10, 1)
        // Missed on the 4th: due the 5th (clean), then the 8th.
        val due = HabitStats.dueDates(HabitCategory.NEGATIVE, every3, start, setOf(LocalDate.of(2026, 10, 4)), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9))
        assertEquals(listOf(1, 4, 5, 8), due.map { it.dayOfMonth })
    }

    @Test
    fun `other schedules keep their days after a miss`() {
        val weekly = Recurrence.Weekly(setOf(DayOfWeek.MONDAY))
        val due = HabitStats.dueDates(HabitCategory.POSITIVE, weekly, LocalDate.of(2026, 10, 5), emptySet(), today, until = today)
        assertEquals(listOf(5, 12, 19), due.map { it.dayOfMonth })
    }

    @Test
    fun `the next reminder of an unmarked every N days habit is tomorrow once today's time has passed`() {
        val every3 = Recurrence.EveryNDays(3)
        val start = LocalDate.of(2026, 10, 1)
        val nine = LocalTime.of(9, 0)
        val noon = LocalDate.of(2026, 10, 4).atTime(12, 0)
        val marks = setOf(LocalDate.of(2026, 10, 1))
        // Due today (the 4th), not done, 9:00 passed: tomorrow, as it will be due again if still not done.
        assertEquals(LocalDate.of(2026, 10, 5).atTime(nine), HabitStats.nextReminder(HabitCategory.POSITIVE, every3, start, marks, nine, noon))
        // Done today (asked from the end of the day, as the app does once today is marked): three days on.
        val endOfDay = LocalDate.of(2026, 10, 4).atTime(LocalTime.MAX)
        assertEquals(
            LocalDate.of(2026, 10, 7).atTime(nine),
            HabitStats.nextReminder(HabitCategory.POSITIVE, every3, start, marks + LocalDate.of(2026, 10, 4), nine, endOfDay),
        )
    }
}
