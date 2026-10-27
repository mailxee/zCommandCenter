package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.HabitDao
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Owns habits and their marks, and keeps each habit's reminder alarm in step with what is stored. */
class HabitRepository(
    private val dao: HabitDao,
    private val reminders: ReminderScheduler,
) {
    fun observeAll(): Flow<List<HabitEntity>> = dao.observeAll()

    /** Mark dates per habit id. */
    fun observeAllMarks(): Flow<Map<Long, Set<LocalDate>>> =
        dao.observeAllMarks().map { marks -> marks.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }

    fun observeMarks(habitId: Long): Flow<Set<LocalDate>> =
        dao.observeMarks(habitId).map { marks -> marks.map { it.date }.toSet() }

    suspend fun get(id: Long): HabitEntity? = dao.get(id)

    /** A new habit gets a place in the list; an edited one keeps its place. Its reminder is (re)scheduled. */
    suspend fun save(habit: HabitEntity): Long {
        val id = if (habit.id == 0L) {
            dao.insert(habit.copy(sortOrder = dao.newSortOrder()))
        } else {
            habit.id.also { dao.update(habit.copy(sortOrder = dao.get(habit.id)?.sortOrder ?: habit.sortOrder)) }
        }
        dao.get(id)?.let(::syncReminder)
        return id
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun toggleMark(habitId: Long, date: LocalDate) = dao.toggleMark(habitId, date)

    suspend fun delete(id: Long) {
        reminders.cancelHabit(id)
        dao.delete(id)
    }

    /**
     * Called when a habit's reminder alarm goes off: schedules the next one, and returns the habit
     * if the user should be reminded now, that is, today is scheduled and has no mark yet.
     */
    suspend fun reminderFired(id: Long): HabitEntity? {
        val habit = dao.get(id) ?: return null
        syncReminder(habit)
        val today = LocalDate.now()
        val due = habit.reminderTime != null && habit.recurrence.isScheduled(today, habit.startDate)
        return habit.takeIf { due && dao.markCount(id, today) == 0 }
    }

    /** Alarms do not survive reboots or app updates; re-register every habit reminder. */
    suspend fun rescheduleAllReminders() {
        dao.withReminders().forEach(::syncReminder)
    }

    private fun syncReminder(habit: HabitEntity) {
        val next = habit.reminderTime?.let { habit.recurrence.nextReminder(habit.startDate, it, LocalDateTime.now()) }
        if (next != null) {
            reminders.scheduleHabit(habit.id, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        } else {
            reminders.cancelHabit(habit.id)
        }
    }
}
