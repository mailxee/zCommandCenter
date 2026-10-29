package com.example.xcommandcenter.domain

import java.time.LocalDate

/**
 * Which schedule the Schedule tab shows and whose reminders go off. The user's pick holds, except
 * that with automatic switching on, a new day starts on the schedule set for its weekday; a pick
 * made today still wins until tomorrow.
 */
object ScheduleChoice {
    /** A schedule's id and the weekdays it is used on automatically (a [Recurrence.maskFromDays] mask). */
    data class Option(val id: Long, val days: Int)

    /**
     * The active schedule among [options] (in their order): [selectedId], picked on [selectedOn],
     * unless [automatic] and the pick is from an earlier day and some schedule is set for [today]'s
     * weekday (the first such). A pick that no longer exists falls back to the first schedule.
     */
    fun active(options: List<Option>, selectedId: Long?, selectedOn: LocalDate?, automatic: Boolean, today: LocalDate): Long? {
        if (automatic && selectedOn != today) {
            val bit = 1 shl (today.dayOfWeek.value - 1)
            options.firstOrNull { it.days and bit != 0 }?.let { return it.id }
        }
        return options.firstOrNull { it.id == selectedId }?.id ?: options.firstOrNull()?.id
    }

    /** [days] given to [id], taken off every other schedule, so each weekday has at most one. */
    fun assignDays(options: List<Option>, id: Long, days: Int): List<Option> =
        options.map { if (it.id == id) it.copy(days = days) else it.copy(days = it.days and days.inv()) }
}
