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

    /**
     * Inserts when [TaskEntity.id] is 0, otherwise updates. A change to `completed` goes through
     * [setCompleted], so completing a repeating task from its form also creates the next one.
     * Returns the task's id.
     */
    suspend fun save(task: TaskEntity): Long {
        val stored = if (task.id == 0L) null else dao.get(task.id)
        val wasCompleted = stored?.completed ?: false
        val toWrite = task.copy(completed = wasCompleted, nextCreated = stored?.nextCreated ?: false)
        val id = if (task.id == 0L) dao.insert(toWrite) else task.id.also { dao.update(toWrite) }
        if (task.completed != wasCompleted) setCompleted(id, task.completed) else dao.get(id)?.let(::syncReminder)
        return id
    }

    /** Completing a repeating task creates its next occurrence; returns that task's id, if any. */
    suspend fun setCompleted(id: Long, completed: Boolean): Long? {
        val nextId = if (completed) {
            dao.complete(id)
        } else {
            dao.setCompleted(id, false)
            null
        }
        dao.get(id)?.let(::syncReminder)
        nextId?.let { dao.get(it) }?.let(::syncReminder)
        return nextId
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
