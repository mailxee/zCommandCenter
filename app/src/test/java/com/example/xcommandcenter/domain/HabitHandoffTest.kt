package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.HabitHandoff.MarkDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitHandoffTest {
    private val today = LocalDate.of(2026, 11, 16) // A Monday.
    private val run = HabitHandoff.Habit("Run", HabitCategory.POSITIVE, Recurrence.Daily, LocalDate.of(2026, 1, 1))
    private val noPhone = HabitHandoff.Habit("No phone in bed", HabitCategory.NEGATIVE, Recurrence.Daily, LocalDate.of(2026, 1, 1))

    private fun decide(habit: HabitHandoff.Habit?, date: LocalDate?, marks: Set<LocalDate> = emptySet(), requested: Boolean = true) =
        HabitHandoff.markDecision(requested, habit, date, marks, today)

    @Test
    fun newHabitNeedsANameAndDefaultsToToday() {
        assertNull(HabitHandoff.newHabit(null, "x", null, today))
        assertNull(HabitHandoff.newHabit("   ", null, null, today))
        assertEquals(HabitHandoff.NewHabit("Run", "", today), HabitHandoff.newHabit(" Run ", null, null, today))
        val start = LocalDate.of(2026, 12, 1)
        assertEquals(HabitHandoff.NewHabit("Run", "5 km", start), HabitHandoff.newHabit("Run", "5 km", start.toEpochDay(), today))
    }

    @Test
    fun asksWithDoneOrMissedWording() {
        assertEquals(MarkDecision.Ask("Mark 'Run' as done on Mon 16 Nov?", "Mark done"), decide(run, today))
        assertEquals(MarkDecision.Ask("Mark 'No phone in bed' as missed on Mon 16 Nov?", "Mark missed"), decide(noPhone, today))
        // A past day is fine too.
        assertEquals("Mark 'Run' as done on Sat 14 Nov?", decide(run, today.minusDays(2)).message)
    }

    @Test
    fun alreadyMarkedCountsAsSuccess() {
        val d = decide(run, today, marks = setOf(today))
        assertEquals(MarkDecision.AlreadyMarked("'Run' is already marked as done on Mon 16 Nov."), d)
        assertEquals("'No phone in bed' is already marked as missed on Mon 16 Nov.", decide(noPhone, today, setOf(today)).message)
    }

    @Test
    fun refusesMissingRequestMissingHabitFutureAndUnscheduledDays() {
        assertTrue(decide(run, today, requested = false) is MarkDecision.Refused)
        assertTrue(decide(run, null) is MarkDecision.Refused)
        assertEquals(MarkDecision.Refused("That habit isn't in Manage Everything; it may have been deleted."), decide(null, today))
        assertEquals(MarkDecision.Refused("Tue 17 Nov is in the future, so 'Run' can't be marked for it yet."), decide(run, today.plusDays(1)))

        val mondays = run.copy(recurrence = Recurrence.Weekly(setOf(DayOfWeek.MONDAY)))
        assertEquals(MarkDecision.Refused("'Run' isn't scheduled on Sun 15 Nov, so there's nothing to mark."), decide(mondays, today.minusDays(1)))
        assertTrue(decide(mondays, today) is MarkDecision.Ask)
        // Before the habit started is not an occurrence either.
        assertTrue(decide(run.copy(startDate = today), today.minusDays(1)) is MarkDecision.Refused)
    }

    @Test
    fun everyNDaysIsDueAgainAfterAMissedDay() {
        val start = today.minusDays(3)
        val everyThree = run.copy(recurrence = Recurrence.EveryNDays(3), startDate = start)
        assertTrue(decide(everyThree, start.plusDays(1), marks = setOf(start)) is MarkDecision.Refused)
        // Not done on the start day, so due the next day as well.
        assertTrue(decide(everyThree, start.plusDays(1)) is MarkDecision.Ask)
    }

    @Test
    fun aDayInAnotherYearShowsTheYear() {
        assertEquals("Wed 31 Dec 2025", HabitHandoff.dayLabel(LocalDate.of(2025, 12, 31), today))
    }
}
