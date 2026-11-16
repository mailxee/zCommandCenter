package com.example.xcommandcenter.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the hand-off screens decide before the user confirms: another app (zNotifMonitor) asks to
 * add a habit or mark one, and nothing is written until the user taps Save or Mark here.
 */
object HabitHandoff {

    /** A new habit's fields as the request gives them; the editor fills in everything else. */
    data class NewHabit(val name: String, val notes: String, val startDate: LocalDate)

    /** A request to add a habit, or null without a name to give it. A missing start date is [today]. */
    fun newHabit(name: String?, notes: String?, startEpochDay: Long?, today: LocalDate): NewHabit? {
        val trimmed = name?.trim()
        if (trimmed.isNullOrEmpty()) return null
        val start = startEpochDay?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() } ?: today
        return NewHabit(trimmed, notes.orEmpty().trim(), start)
    }

    /** What a mark request needs to know about the habit. */
    data class Habit(val name: String, val category: HabitCategory, val recurrence: Recurrence, val startDate: LocalDate)

    sealed interface MarkDecision {
        val message: String

        /** Nothing can be written; [message] says why. */
        data class Refused(override val message: String) : MarkDecision

        /** The day is marked already, which counts as success. */
        data class AlreadyMarked(override val message: String) : MarkDecision

        /** May be marked once the user confirms [message] with the [confirm] button. */
        data class Ask(override val message: String, val confirm: String) : MarkDecision
    }

    /**
     * Whether [habit] may be marked on [date], given its [marks]. [requested] is false when the
     * request lacked the habit or the date; [habit] is null when no such habit exists. A day
     * already marked counts as success even if the schedule has changed since; otherwise it must be
     * today or earlier and a day the habit is due (as its calendar shows it, so an "every N days"
     * habit is also due on the days after a missed one).
     */
    fun markDecision(requested: Boolean, habit: Habit?, date: LocalDate?, marks: Set<LocalDate>, today: LocalDate): MarkDecision {
        if (!requested || date == null) return MarkDecision.Refused("The request didn't say which habit and day to mark.")
        if (habit == null) return MarkDecision.Refused("That habit isn't in Manage Everything; it may have been deleted.")
        val name = "'${habit.name}'"
        val day = dayLabel(date, today)
        val outcome = if (habit.category == HabitCategory.POSITIVE) "done" else "missed"
        return when {
            date in marks -> MarkDecision.AlreadyMarked("$name is already marked as $outcome on $day.")
            date.isAfter(today) -> MarkDecision.Refused("$day is in the future, so $name can't be marked for it yet.")
            !HabitStats.isDue(habit.category, habit.recurrence, habit.startDate, marks, date) ->
                MarkDecision.Refused("$name isn't scheduled on $day, so there's nothing to mark.")
            else -> MarkDecision.Ask("Mark $name as $outcome on $day?", "Mark $outcome")
        }
    }

    /** "Mon 16 Nov", with the year added when it isn't [today]'s. */
    fun dayLabel(date: LocalDate, today: LocalDate): String =
        date.format(if (date.year == today.year) SHORT else WITH_YEAR)

    private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
}
