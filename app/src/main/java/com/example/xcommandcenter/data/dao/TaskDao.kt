package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.ReminderEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    /** The sortOrder for a new item: after every other one. */
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM tasks")
    suspend fun newSortOrder(): Int

    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Stores [ids] as the new order, first to last. */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("UPDATE tasks SET nextCreated = 1 WHERE id = :id")
    suspend fun markNextCreated(id: Long)

    @Query("SELECT * FROM links WHERE (aType = 'TASK' AND aId = :id) OR (bType = 'TASK' AND bId = :id)")
    suspend fun linksOf(id: Long): List<LinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<LinkEntity>)

    @Query("INSERT OR IGNORE INTO item_tags (type, itemId, tagId) SELECT type, :toId, tagId FROM item_tags WHERE type = 'TASK' AND itemId = :fromId")
    suspend fun copyTags(fromId: Long, toId: Long)

    @Query("SELECT * FROM reminders WHERE itemType = 'TASK' AND itemId = :taskId")
    suspend fun reminders(taskId: Long): List<ReminderEntity>

    @Insert
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'TASK' AND aId = :id) OR (bType = 'TASK' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'TASK' AND itemId = :id")
    suspend fun deleteTags(id: Long)

    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId ORDER BY position")
    suspend fun checklist(taskId: Long): List<ChecklistItemEntity>

    @Query("SELECT taskId, COUNT(*) AS total, SUM(done) AS done FROM checklist_items GROUP BY taskId")
    fun observeChecklistCounts(): Flow<List<ChecklistCount>>

    @Insert
    suspend fun insertChecklist(items: List<ChecklistItemEntity>)

    @Query("DELETE FROM checklist_items WHERE taskId = :taskId")
    suspend fun deleteChecklist(taskId: Long)

    /** Makes [items] the task's whole checklist, numbered 0..n-1 in the given order. */
    @Transaction
    suspend fun replaceChecklist(taskId: Long, items: List<ChecklistItemEntity>) {
        deleteChecklist(taskId)
        insertChecklist(items.mapIndexed { index, item -> item.copy(id = 0, taskId = taskId, position = index) })
    }

    /**
     * Marks the task completed. A repeating task also gets its next occurrence, once: a copy due
     * on the next scheduled date after this one's due date (today if it has none), skipping dates
     * that have already passed, with each reminder moved by the same number of days (so it keeps
     * its time and its distance from the due date), the same links and tags, and the same checklist
     * with every item unticked. The completed task stays as history. Returns the new task's id, or null if none was created.
     */
    @Transaction
    suspend fun complete(id: Long): Long? {
        val task = get(id) ?: return null
        setCompleted(id, true)
        val recurrence = task.recurrence ?: return null
        if (task.nextCreated) return null
        val today = LocalDate.now()
        val from = task.dueDate ?: today
        val next = recurrence.nextFrom(from, today) ?: return null
        val days = ChronoUnit.DAYS.between(from, next)
        val newId = insert(
            task.copy(id = 0, dueDate = next, completed = false, nextCreated = false, createdAt = System.currentTimeMillis()),
        )
        markNextCreated(id)
        val self = ItemRef(ItemType.TASK, id)
        insertLinks(
            linksOf(id).map { link ->
                val other = if (link.aType == self.type && link.aId == self.id) ItemRef(link.bType, link.bId) else ItemRef(link.aType, link.aId)
                LinkEntity.between(ItemRef(ItemType.TASK, newId), other)
            },
        )
        copyTags(id, newId)
        insertChecklist(checklist(id).map { it.copy(id = 0, taskId = newId, done = false) })
        insertReminders(
            reminders(id).map {
                val moved = Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).plusDays(days).toInstant().toEpochMilli()
                it.copy(id = 0, itemId = newId, time = moved)
            },
        )
        return newId
    }

    /** The checklist goes with the task through the foreign key's ON DELETE CASCADE. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteLinks(id)
        deleteTags(id)
        deleteRow(id)
    }

    @Query("SELECT id FROM tasks WHERE completed = 1")
    suspend fun completedIds(): List<Long>

    /** Deletes every completed task, with its links, tags and checklist, all or nothing. Returns the ids. */
    @Transaction
    suspend fun deleteCompleted(): List<Long> = completedIds().onEach { delete(it) }
}
