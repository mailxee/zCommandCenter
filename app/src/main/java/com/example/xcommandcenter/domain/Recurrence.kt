package com.example.xcommandcenter.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class HabitCategory(val label: String) {
    /** The user marks each occurrence they performed. Unmarked occurrences are unknown. */
    POSITIVE("Positive"),

    /** The user marks only the occurrences they missed. Unmarked occurrences count as done. */
    NEGATIVE("Negative"),
}

enum class RecurrenceType(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    EVERY_N_DAYS("Every N days"),
    EVERY_N_MONTHS("Every N months"),
}

/**
 * When a habit or a repeating task is due. [isScheduled] is the single source of truth for every
 * streak rule and for when a repeating task's next occurrence falls.
 */
sealed interface Recurrence {
    fun isScheduled(date: LocalDate, start: LocalDate): Boolean

    fun describe(): String

    /** How far ahead to look for the next scheduled date. */
    val searchDays: Int get() = 400

    data object Daily : Recurrence {
        override fun isScheduled(date: LocalDate, start: LocalDate) = !date.isBefore(start)
        override fun describe() = "Daily"
    }

    data class Weekly(val days: Set<DayOfWeek>) : Recurrence {
        override fun isScheduled(date: LocalDate, start: LocalDate) =
            !date.isBefore(start) && date.dayOfWeek in days

        override fun describe() =
            "Weekly: " + days.sorted().joinToString(", ") { it.shortName() }
    }

    /** A day beyond the end of a short month (e.g. 31 in April) falls on the month's last day. */
    data class Monthly(val dayOfMonth: Int) : Recurrence {
        override fun isScheduled(date: LocalDate, start: LocalDate) =
            !date.isBefore(start) && date.dayOfMonth == minOf(dayOfMonth, date.lengthOfMonth())

        override fun describe() = "Monthly on day $dayOfMonth"
    }

    /** Counted from the habit's start date, which is always an occurrence. */
    data class EveryNDays(val n: Int) : Recurrence {
        override fun isScheduled(date: LocalDate, start: LocalDate) =
            !date.isBefore(start) && ChronoUnit.DAYS.between(start, date) % n == 0L

        override fun describe() = if (n == 1) "Every day" else "Every $n days"
    }

    /**
     * On [dayOfMonth] (or the last day of a shorter month) every [n] months, counted from the
     * start date's month: a start in March with n = 3 gives March (if the day isn't already
     * past), June, September and December.
     */
    data class EveryNMonths(val n: Int, val dayOfMonth: Int) : Recurrence {
        override fun isScheduled(date: LocalDate, start: LocalDate) =
            !date.isBefore(start) &&
                ChronoUnit.MONTHS.between(start.withDayOfMonth(1), date.withDayOfMonth(1)) % n == 0L &&
                date.dayOfMonth == minOf(dayOfMonth, date.lengthOfMonth())

        override fun describe() = if (n == 1) "Monthly on day $dayOfMonth" else "Every $n months on day $dayOfMonth"

        override val searchDays: Int get() = (n + 1) * 31
    }

    /**
     * The first scheduled date after [date], counting the schedule from [date] itself (so "every
     * 3 days" gives date + 3). Null when nothing is scheduled within [searchDays], e.g. a weekly
     * rule with no weekdays.
     */
    fun nextAfter(date: LocalDate): LocalDate? =
        generateSequence(date.plusDays(1)) { it.plusDays(1) }.take(searchDays).firstOrNull { isScheduled(it, date) }

    /**
     * Like [nextAfter], but never before [today]: occurrences that have already passed are
     * skipped, so a repeating task completed late gets its next occurrence in the future.
     */
    fun nextFrom(date: LocalDate, today: LocalDate): LocalDate? {
        val first = maxOf(date.plusDays(1), today)
        return generateSequence(first) { it.plusDays(1) }.take(searchDays).firstOrNull { isScheduled(it, date) }
    }

    /**
     * When a habit with this schedule should next remind at [time]: the first scheduled day, from
     * [start] on, whose [time] is after [now]. Null when nothing is scheduled within [searchDays].
     */
    fun nextReminder(start: LocalDate, time: LocalTime, now: LocalDateTime): LocalDateTime? =
        generateSequence(maxOf(now.toLocalDate(), start)) { it.plusDays(1) }
            .take(searchDays)
            .filter { isScheduled(it, start) }
            .map { it.atTime(time) }
            .firstOrNull { it.isAfter(now) }

    companion object {
        /** Builds a recurrence from the flat columns that habits and tasks store. */
        fun of(type: RecurrenceType, weekdays: Int, dayOfMonth: Int, interval: Int): Recurrence = when (type) {
            RecurrenceType.DAILY -> Daily
            RecurrenceType.WEEKLY -> Weekly(daysFromMask(weekdays))
            RecurrenceType.MONTHLY -> Monthly(dayOfMonth)
            RecurrenceType.EVERY_N_DAYS -> EveryNDays(interval)
            RecurrenceType.EVERY_N_MONTHS -> EveryNMonths(interval, dayOfMonth)
        }

        /** Bit i of the mask is [DayOfWeek] with value i + 1 (bit 0 = Monday). */
        fun daysFromMask(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

        fun maskFromDays(days: Set<DayOfWeek>): Int =
            days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }
    }
}

fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
