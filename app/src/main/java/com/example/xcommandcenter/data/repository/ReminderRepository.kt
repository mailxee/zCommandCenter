package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.HabitDao
import com.example.xcommandcenter.data.dao.ReminderDao
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.ReminderEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What to notify when a reminder goes off. */
sealed interface ReminderNotice {
    data class Habit(val habit: HabitEntity) : ReminderNotice

    data class Item(val ref: ItemRef, val title: String) : ReminderNotice
}

/**
 * Owns every item's reminders and keeps one alarm per reminder in step with what is stored. A
 * habit's reminder is a time of day, repeated on each scheduled day; any other item's is a date
 * and time, once. Alarms are set even for completed tasks and plans; when one goes off for a
 * completed item, nothing is shown (so reopening the item needs no rescheduling).
 */
class ReminderRepository(
    private val dao: ReminderDao,
    private val habits: HabitDao,
    private val scheduler: ReminderScheduler,
) {
    /** Reminder times per item, earliest first (see [ReminderEntity.time]). */
    fun observeAll(): Flow<Map<ItemRef, List<Long>>> =
        dao.observeAll().map { rows -> rows.groupBy({ it.ref }, { it.time }) }

    suspend fun times(ref: ItemRef): List<Long> = dao.forItem(ref.type, ref.id).map { it.time }

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

    /** Alarms do not survive reboots or app updates; re-register every one. */
    suspend fun rescheduleAll() {
        dao.all().forEach { schedule(it) }
    }

    suspend fun cancelAll() {
        dao.all().forEach { scheduler.cancel(it.id) }
    }

    /**
     * Called when reminder [id]'s alarm goes off. A habit's reminder schedules its next day and
     * asks to notify only if today is scheduled and has no mark yet; another item's notifies
     * unless the item is completed. Null when there is nothing to show.
     */
    suspend fun fired(id: Long): ReminderNotice? {
        val reminder = dao.get(id) ?: return null
        if (reminder.itemType == ItemType.HABIT) {
            val habit = habits.get(reminder.itemId) ?: return null
            schedule(reminder, habit)
            val today = LocalDate.now()
            val due = habit.recurrence.isScheduled(today, habit.startDate) && habits.markCount(habit.id, today) == 0
            return if (due) ReminderNotice.Habit(habit) else null
        }
        val title = dao.title(reminder.itemType, reminder.itemId) ?: return null
        if (dao.completed(reminder.itemType, reminder.itemId) == 1) return null
        return ReminderNotice.Item(reminder.ref, title)
    }

    private suspend fun schedule(reminder: ReminderEntity) {
        if (reminder.itemType == ItemType.HABIT) {
            habits.get(reminder.itemId)?.let { schedule(reminder, it) } ?: scheduler.cancel(reminder.id)
        } else if (reminder.time > System.currentTimeMillis()) {
            scheduler.schedule(reminder.id, reminder.time)
        } else {
            scheduler.cancel(reminder.id)
        }
    }

    private fun schedule(reminder: ReminderEntity, habit: HabitEntity) {
        val next = habit.recurrence.nextReminder(habit.startDate, reminder.timeOfDay, LocalDateTime.now())
        if (next != null) {
            scheduler.schedule(reminder.id, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        } else {
            scheduler.cancel(reminder.id)
        }
    }
}
