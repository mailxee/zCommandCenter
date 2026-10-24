package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.TaskDao
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow

/** Owns tasks and keeps their alarms in step with what is stored. */
class TaskRepository(
    private val dao: TaskDao,
    private val reminders: ReminderScheduler,
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun get(id: Long): TaskEntity? = dao.get(id)

    /** Inserts when [TaskEntity.id] is 0, otherwise updates. Returns the task's id. */
    suspend fun save(task: TaskEntity): Long {
        val id = if (task.id == 0L) dao.insert(task) else task.id.also { dao.update(task) }
        syncReminder(task.copy(id = id))
        return id
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.setCompleted(id, completed)
        dao.get(id)?.let(::syncReminder)
    }

    suspend fun delete(id: Long) {
        reminders.cancel(id)
        dao.delete(id)
    }

    /** Alarms do not survive reboots or app updates; re-register every pending one. */
    suspend fun rescheduleAllReminders() {
        dao.pendingReminders(System.currentTimeMillis()).forEach(::syncReminder)
    }

    private fun syncReminder(task: TaskEntity) {
        val at = task.reminderAt
        if (!task.completed && at != null && at > System.currentTimeMillis()) {
            reminders.schedule(task.id, task.title, at)
        } else {
            reminders.cancel(task.id)
        }
    }
}
