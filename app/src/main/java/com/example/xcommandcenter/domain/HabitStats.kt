package com.example.xcommandcenter.domain

import java.time.LocalDate

/**
 * Streak and occurrence rules for habits. Pure functions so they can be unit-tested without
 * Android or a database.
 *
 * Only scheduled occurrences matter; unscheduled days are skipped, never counted or breaking.
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
        var date = today
        while (!date.isBefore(start)) {
            if (recurrence.isScheduled(date, start)) {
                val marked = date in marks
                when (category) {
                    HabitCategory.POSITIVE -> when {
                        marked -> count++
                        date != today -> return count
                    }
                    HabitCategory.NEGATIVE -> if (marked) return count else count++
                }
            }
            date = date.minusDays(1)
        }
        return count
    }

    /** The most recent scheduled occurrences, newest first, never before [start]. */
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
