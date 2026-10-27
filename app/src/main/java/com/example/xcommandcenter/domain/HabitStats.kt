package com.example.xcommandcenter.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Streak and occurrence rules for habits. Pure functions so they can be unit-tested without
 * Android or a database.
 *
 * Only due days matter; other days are skipped, never counted or breaking. A habit is due on the
 * days its schedule gives, except that an "every N days" habit catches up after a miss (see
 * [dueDates]).
 * - Positive: count consecutive marked occurrences walking back from today. Today's occurrence
 *   being unmarked does not break the streak (the day is not over), any earlier unmarked one does.
 * - Negative: a mark is a miss. Count occurrences from today back to the most recent miss,
 *   or back to the start date if there has never been one.
 */
object HabitStats {

    fun streak(
        category: HabitCategory,
        recurrence: Recurrence,
        start: LocalDate,
        marks: Set<LocalDate>,
        today: LocalDate,
    ): Int {
        var count = 0
        for (date in dueDates(category, recurrence, start, marks, today, until = today).asReversed()) {
            val marked = date in marks
            when (category) {
                HabitCategory.POSITIVE -> when {
                    marked -> count++
                    date != today -> return count
                }
                HabitCategory.NEGATIVE -> if (marked) return count else count++
            }
        }
        return count
    }

    /**
     * The days the habit is due, oldest first, from [start] through [until]. These are the days its
     * schedule gives, except for "every N days": when a due day is missed (a positive habit left
     * unmarked, a negative one marked as missed), the habit is due again the next day, and every
     * day after until it is done; N days are then counted from the day it was done. A positive
     * habit not yet marked today counts as missed for the days after (if it is done today, the
     * list changes with the mark); days after today count as done on time.
     */
    fun dueDates(
        category: HabitCategory,
        recurrence: Recurrence,
        start: LocalDate,
        marks: Set<LocalDate>,
        today: LocalDate,
        until: LocalDate,
    ): List<LocalDate> {
        if (recurrence !is Recurrence.EveryNDays) {
            return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }
                .filter { recurrence.isScheduled(it, start) }.toList()
        }
        val result = mutableListOf<LocalDate>()
        var due = start
        while (!due.isAfter(until)) {
            result += due
            val done = when (category) {
                HabitCategory.POSITIVE -> due in marks || due.isAfter(today)
                HabitCategory.NEGATIVE -> due !in marks
            }
            due = due.plusDays(if (done) recurrence.n.toLong() else 1L)
        }
        return result
    }

    fun isDue(category: HabitCategory, recurrence: Recurrence, start: LocalDate, marks: Set<LocalDate>, date: LocalDate): Boolean =
        !date.isBefore(start) && dueDates(category, recurrence, start, marks, today = date, until = date).lastOrNull() == date

    /** The most recent due days, newest first, for the habit's history. */
    fun recentDueDates(
        category: HabitCategory,
        recurrence: Recurrence,
        start: LocalDate,
        marks: Set<LocalDate>,
        today: LocalDate,
        limit: Int,
    ): List<LocalDate> = dueDates(category, recurrence, start, marks, today, until = today).takeLast(limit).asReversed()

    /**
     * When a reminder at [time] next goes off: on the first due day, from [now]'s date on, whose
     * [time] is still ahead. Null when nothing is due within the schedule's search window.
     */
    fun nextReminder(
        category: HabitCategory,
        recurrence: Recurrence,
        start: LocalDate,
        marks: Set<LocalDate>,
        time: LocalTime,
        now: LocalDateTime,
    ): LocalDateTime? {
        val today = now.toLocalDate()
        val until = maxOf(today, start).plusDays(recurrence.searchDays.toLong())
        return dueDates(category, recurrence, start, marks, today, until)
            .asSequence()
            .filter { !it.isBefore(today) }
            .map { it.atTime(time) }
            .firstOrNull { it.isAfter(now) }
    }

    /** The most recent days the schedule gives, newest first, never before [start]; ignores catching up. */
    fun recentOccurrences(
        recurrence: Recurrence,
        start: LocalDate,
        today: LocalDate,
        limit: Int,
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var date = today
        // Bound the walk so a pathological schedule can't loop for decades.
        val floor = maxOf(start, today.minusYears(2))
        while (result.size < limit && !date.isBefore(floor)) {
            if (recurrence.isScheduled(date, start)) result += date
            date = date.minusDays(1)
        }
        return result
    }
}
