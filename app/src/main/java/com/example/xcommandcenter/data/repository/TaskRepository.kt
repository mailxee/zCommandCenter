package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.TaskDao
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Owns tasks; a repeating task's next occurrence gets its reminders' alarms set here. */
class TaskRepository(
    private val dao: TaskDao,
    private val reminders: ReminderRepository,
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun get(id: Long): TaskEntity? = dao.get(id)

    suspend fun checklist(taskId: Long): List<ChecklistItemEntity> = dao.checklist(taskId)

    /** Checklist progress per task id. */
    fun observeChecklistCounts(): Flow<Map<Long, ChecklistCount>> =
        dao.observeChecklistCounts().map { counts -> counts.associateBy { it.taskId } }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /**
     * Inserts when [TaskEntity.id] is 0, otherwise updates, and stores [checklist] and
     * [reminderTimes] as the task's checklist and reminders. A change to `completed` goes through
     * [setCompleted], so completing a repeating task from its form also creates the next one
     * (after the checklist and reminders are written, so the next one gets copies). Returns the
     * task's id.
     */
    suspend fun save(task: TaskEntity, checklist: List<ChecklistItemEntity>, reminderTimes: List<Long>): Long {
        val stored = if (task.id == 0L) null else dao.get(task.id)
        val wasCompleted = stored?.completed ?: false
        val toWrite = task.copy(
            completed = wasCompleted,
            nextCreated = stored?.nextCreated ?: false,
            sortOrder = stored?.sortOrder ?: dao.newSortOrder(),
        )
        val id = if (task.id == 0L) dao.insert(toWrite) else task.id.also { dao.update(toWrite) }
        dao.replaceChecklist(id, checklist)
        reminders.replace(ItemRef(ItemType.TASK, id), reminderTimes)
        if (task.completed != wasCompleted) setCompleted(id, task.completed)
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
        nextId?.let { reminders.sync(ItemRef(ItemType.TASK, it)) }
        return nextId
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.removeOrphans()
    }

    /** Deletes every completed task; a repeating task's open next occurrence is not completed, so it stays. */
    suspend fun deleteCompleted() {
        dao.deleteCompleted()
        reminders.removeOrphans()
    }
}
