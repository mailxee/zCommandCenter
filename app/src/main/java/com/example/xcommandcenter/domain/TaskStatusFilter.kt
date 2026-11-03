package com.example.xcommandcenter.domain

import java.time.LocalDate

/** A built-in chip of the Tasks tab's filter bar: shows everything, only its tasks, or hides them. */
enum class ChipState { ANY, ONLY, HIDDEN }

/**
 * The Tasks tab's "Past due" and "Completed" chips, which work like tags: tap to show only those
 * tasks, press and hold to hide them. A task can't be both, so showing only one turns the other's
 * "only" off.
 */
data class TaskStatusFilter(val pastDue: ChipState = ChipState.ANY, val completed: ChipState = ChipState.ANY) {
    enum class Chip { PAST_DUE, COMPLETED }

    val active: Boolean get() = pastDue != ChipState.ANY || completed != ChipState.ANY

    fun state(chip: Chip): ChipState = if (chip == Chip.PAST_DUE) pastDue else completed

    fun matches(completed: Boolean, dueDate: LocalDate?, today: LocalDate): Boolean {
        val overdue = isPastDue(completed, dueDate, today)
        return pastDue.allows(overdue) && this.completed.allows(completed)
    }

    /** A tap: "only" on, or back to everything when the chip was on either way. */
    fun tap(chip: Chip): TaskStatusFilter {
        val next = if (state(chip) == ChipState.ANY) ChipState.ONLY else ChipState.ANY
        return with(chip, next)
    }

    /** A press and hold: hide, or back to everything when already hidden. */
    fun hold(chip: Chip): TaskStatusFilter =
        with(chip, if (state(chip) == ChipState.HIDDEN) ChipState.ANY else ChipState.HIDDEN)

    private fun with(chip: Chip, state: ChipState): TaskStatusFilter {
        val other = if (chip == Chip.PAST_DUE) completed else pastDue
        val otherState = if (state == ChipState.ONLY && other == ChipState.ONLY) ChipState.ANY else other
        return if (chip == Chip.PAST_DUE) TaskStatusFilter(state, otherState) else TaskStatusFilter(otherState, state)
    }

    private fun ChipState.allows(flag: Boolean) = when (this) {
        ChipState.ANY -> true
        ChipState.ONLY -> flag
        ChipState.HIDDEN -> !flag
    }

    companion object {
        /** An open task whose due date is before [today]. */
        fun isPastDue(completed: Boolean, dueDate: LocalDate?, today: LocalDate): Boolean =
            !completed && dueDate != null && dueDate.isBefore(today)
    }
}
