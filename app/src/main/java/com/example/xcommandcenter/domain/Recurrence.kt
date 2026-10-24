package com.example.xcommandcenter.domain

import java.time.DayOfWeek
import java.time.LocalDate
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
}

/** When a habit is due. [isScheduled] is the single source of truth for every streak rule. */
sealed interface Recurrence {
    fun isScheduled(date: LocalDate, start: LocalDate): Boolean

    fun describe(): String

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

    companion object {
        /** Bit i of the mask is [DayOfWeek] with value i + 1 (bit 0 = Monday). */
        fun daysFromMask(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

        fun maskFromDays(days: Set<DayOfWeek>): Int =
            days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }
    }
}

fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
