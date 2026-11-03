package com.example.xcommandcenter.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Carry-over: when a task's or plan's reminder goes off and the item is still open, with no other
 * reminder still to come, the reminder is repeated [days] later at the same time of day. The rules
 * are kept here, apart from the database and alarms, so they can be tested.
 */
object ReminderCarry {
    /** The types whose items can be completed, so their reminders can be carried over. */
    val types = setOf(ItemType.TASK, ItemType.PLAN)

    /**
     * When the carried-over copy of a reminder that went off at [firedAt] goes off: [days] calendar
     * days later at the same time of day, and again [days] on until it is after [now] (a reminder
     * missed while the phone was off). Null when carry-over is off.
     */
    fun next(firedAt: Long, days: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (days <= 0) return null
        var next = Instant.ofEpochMilli(firedAt).atZone(zone)
        do {
            next = next.plusDays(days.toLong())
        } while (next.toInstant().toEpochMilli() <= now)
        return next.toInstant().toEpochMilli()
    }

    /** Whether a reminder at [firedAt] should be carried over, given the item's [otherTimes]. */
    fun applies(type: ItemType, completed: Boolean, otherTimes: List<Long>, now: Long, days: Int): Boolean =
        days > 0 && type in types && !completed && otherTimes.none { it > now }

    /**
     * After a restart, a reminder that went off while the phone was off never ran its carry-over.
     * The item's latest past reminder is carried if it was at most [days] days ago (within the
     * window its copy would have covered); older ones are left alone.
     */
    fun missed(latestPast: Long, now: Long, days: Int, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        days > 0 && latestPast <= now &&
            Instant.ofEpochMilli(latestPast).atZone(zone).plusDays(days.toLong()).toInstant().toEpochMilli() > now
}
