package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.HabitDao
import com.example.xcommandcenter.data.dao.ReminderDao
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.ReminderEntity
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ReminderCarry
import com.example.xcommandcenter.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What to notify when a reminder goes off. */
sealed interface ReminderNotice {
    data class Habit(val habit: HabitEntity) : ReminderNotice

    data class Item(val ref: ItemRef, val title: String) : ReminderNotice
}

/**
 * Owns every item's reminders and keeps one alarm per reminder in step with what is stored. A
 * habit's reminder is a time of day, repeated on each scheduled day; a schedule block's is a time
 * of day, every day, shown only while its schedule is the active one; any other item's is a date and time, once, and a task's or plan's
 * is carried over to a later day while the item stays open (see [ReminderCarry]). Alarms are set even for completed tasks and plans; when one goes off for a
 * completed item, nothing is shown (so reopening the item needs no rescheduling).
 */
class ReminderRepository(
    private val dao: ReminderDao,
    private val habits: HabitDao,
    private val scheduler: ReminderScheduler,
    /** The active schedule; only its blocks' reminders go off. */
    private val activeSchedule: suspend () -> Long?,
    /** Days until a reminder is carried over; 0 when off (see [ReminderCarry]). */
    private val carryDays: suspend () -> Int = { 0 },
) {
    /** Reminder times per item, earliest first (see [ReminderEntity.time]). */
    fun observeAll(): Flow<Map<ItemRef, List<Long>>> =
        dao.observeAll().map { rows -> rows.groupBy({ it.ref }, { it.time }) }

    suspend fun times(ref: ItemRef): List<Long> = dao.forItem(ref.type, ref.id).map { it.time }

    /** The times among [times] that were carried over, for their label. */
    suspend fun carriedTimes(ref: ItemRef): Set<Long> = dao.forItem(ref.type, ref.id).filter { it.carried }.map { it.time }.toSet()

    /** Removes carried-over reminders whose item has been completed; called after completing things. */
    suspend fun dropCarriedOfCompleted() {
        dao.carriedOfCompleted().forEach {
            scheduler.cancel(it.id)
            dao.delete(it.id)
        }
    }

    /** Makes [times] the item's reminders, replacing its alarms. */
    suspend fun replace(ref: ItemRef, times: List<Long>) {
        dao.forItem(ref.type, ref.id).forEach { scheduler.cancel(it.id) }
        dao.replace(ref.type, ref.id, times).forEach { schedule(it) }
    }

    /** Sets the alarms of an item whose reminders were written elsewhere (a repeating task's next occurrence). */
    suspend fun sync(ref: ItemRef) {
        dao.forItem(ref.type, ref.id).forEach { schedule(it) }
    }

    /** Removes the reminders, and their alarms, of items that have been deleted. */
    suspend fun removeOrphans() {
        dao.orphans().forEach {
            scheduler.cancel(it.id)
            dao.delete(it.id)
        }
    }

    /**
     * Alarms do not survive reboots or app updates; re-register every one. A reminder that went off
     * while the phone was off gets its carry-over now (see [ReminderCarry.missed]).
     */
    suspend fun rescheduleAll() {
        val all = dao.all()
        all.forEach { schedule(it) }
        val days = carryDays()
        if (days <= 0) return
        val now = System.currentTimeMillis()
        all.filter { it.itemType in ReminderCarry.types }.groupBy { it.ref }.forEach { (_, rows) ->
            val latest = rows.filter { it.time <= now }.maxByOrNull { it.time } ?: return@forEach
            if (ReminderCarry.missed(latest.time, now, days)) carryOver(latest, now, days)
        }
    }

    suspend fun cancelAll() {
        dao.all().forEach { scheduler.cancel(it.id) }
    }

    /**
     * Called when reminder [id]'s alarm goes off. A habit's reminder schedules its next day and
     * asks to notify only if the habit is due today (see [HabitStats.dueDates]) and today has no
     * mark yet; another item's notifies unless the item is completed. Null when there is nothing
     * to show.
     */
    suspend fun fired(id: Long): ReminderNotice? {
        val reminder = dao.get(id) ?: return null
        if (reminder.itemType == ItemType.HABIT) {
            val habit = habits.get(reminder.itemId) ?: return null
            val marks = habits.markDates(habit.id).toSet()
            schedule(reminder, habit, marks)
            val today = LocalDate.now()
            val due = today !in marks && HabitStats.isDue(habit.category, habit.recurrence, habit.startDate, marks, today)
            return if (due) ReminderNotice.Habit(habit) else null
        }
        val title = dao.title(reminder.itemType, reminder.itemId) ?: return null
        if (reminder.itemType == ItemType.BLOCK) {
            scheduleDaily(reminder)
            // Every block keeps its alarm, so switching schedules needs no rescheduling; only the active one's show.
            return if (dao.blockSchedule(reminder.itemId) == activeSchedule()) ReminderNotice.Item(reminder.ref, title) else null
        }
        if (dao.completed(reminder.itemType, reminder.itemId) == 1) {
            if (reminder.carried) dao.delete(reminder.id)
            return null
        }
        carryOver(reminder, System.currentTimeMillis(), carryDays())
        return ReminderNotice.Item(reminder.ref, title)
    }

    /**
     * Repeats [reminder], which has gone off, [days] later if its item is still open and has no other
     * reminder to come (see [ReminderCarry]). The item keeps a single carried-over reminder: a
     * carried one moves forward, and a new carry replaces any earlier one.
     */
    private suspend fun carryOver(reminder: ReminderEntity, now: Long, days: Int) {
        val others = dao.forItem(reminder.itemType, reminder.itemId).filter { it.id != reminder.id }
        val completed = dao.completed(reminder.itemType, reminder.itemId) == 1
        if (!ReminderCarry.applies(reminder.itemType, completed, others.map { it.time }, now, days)) return
        val next = ReminderCarry.next(reminder.time, days, now) ?: return
        if (reminder.carried) {
            dao.setTime(reminder.id, next)
            scheduler.schedule(reminder.id, next)
        } else {
            others.filter { it.carried }.forEach {
                scheduler.cancel(it.id)
                dao.delete(it.id)
            }
            val id = dao.insert(ReminderEntity(itemType = reminder.itemType, itemId = reminder.itemId, time = next, carried = true))
            scheduler.schedule(id, next)
        }
    }

    private suspend fun schedule(reminder: ReminderEntity) {
        if (reminder.itemType == ItemType.HABIT) {
            habits.get(reminder.itemId)?.let { schedule(reminder, it, habits.markDates(it.id).toSet()) } ?: scheduler.cancel(reminder.id)
        } else if (reminder.itemType == ItemType.BLOCK) {
            scheduleDaily(reminder)
        } else if (reminder.time > System.currentTimeMillis()) {
            scheduler.schedule(reminder.id, reminder.time)
        } else {
            scheduler.cancel(reminder.id)
        }
    }

    /** A block's alarm goes to the next time its minute of the day comes round: today if still ahead, else tomorrow. */
    private fun scheduleDaily(reminder: ReminderEntity) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate().atTime(reminder.timeOfDay)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        scheduler.schedule(reminder.id, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    /** A habit's alarm goes to its next due day (skipping today once marked), so marks change it too. */
    private fun schedule(reminder: ReminderEntity, habit: HabitEntity, marks: Set<LocalDate>) {
        val now = LocalDateTime.now()
        val from = if (now.toLocalDate() in marks) now.toLocalDate().atTime(LocalTime.MAX) else now
        val next = HabitStats.nextReminder(habit.category, habit.recurrence, habit.startDate, marks, reminder.timeOfDay, from)
        if (next != null) {
            scheduler.schedule(reminder.id, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        } else {
            scheduler.cancel(reminder.id)
        }
    }
}
