package com.example.xcommandcenter.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which items have a reminder going off today: the "Reminder" chip of the filter bar. */
object ReminderToday {
    /**
     * Of [byItem] (each item's reminders, as stored), the items with one today. A one-off reminder
     * (epoch millis) counts when it falls on [today] in [zone], carried-over ones included. A habit's
     * times of day count on days the habit is due ([habitDue]), a block's when its schedule is the one
     * in use ([blockShown]).
     */
    fun items(
        byItem: Map<ItemRef, List<Long>>,
        today: LocalDate,
        zone: ZoneId,
        habitDue: (Long) -> Boolean,
        blockShown: (Long) -> Boolean,
    ): Set<ItemRef> = byItem.filter { (ref, times) ->
        times.isNotEmpty() && when (ref.type) {
            ItemType.HABIT -> habitDue(ref.id)
            ItemType.BLOCK -> blockShown(ref.id)
            else -> times.any { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today }
        }
    }.keys
}
