package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.LinkEntity
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

    @Query("SELECT * FROM tasks WHERE completed = 0 AND reminderAt > :now")
    suspend fun pendingReminders(now: Long): List<TaskEntity>

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

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'TASK' AND aId = :id) OR (bType = 'TASK' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'TASK' AND itemId = :id")
    suspend fun deleteTags(id: Long)

    /**
     * Marks the task completed. A repeating task also gets its next occurrence, once: a copy due
     * on the next scheduled date after this one's due date (today if it has none), with the
     * reminder moved by the same number of days, and the same links and tags. The completed
     * task stays as history. Returns the new task's id, or null if none was created.
     */
    @Transaction
    suspend fun complete(id: Long): Long? {
        val task = get(id) ?: return null
        setCompleted(id, true)
        val recurrence = task.recurrence ?: return null
        if (task.nextCreated) return null
        val from = task.dueDate ?: LocalDate.now()
        val next = recurrence.nextAfter(from) ?: return null
        val days = ChronoUnit.DAYS.between(from, next)
        val reminder = task.reminderAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).plusDays(days).toInstant().toEpochMilli()
        }
        val newId = insert(
            task.copy(id = 0, dueDate = next, reminderAt = reminder, completed = false, nextCreated = false, createdAt = System.currentTimeMillis()),
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
        return newId
    }

    @Transaction
    suspend fun delete(id: Long) {
        deleteLinks(id)
        deleteTags(id)
        deleteRow(id)
    }
}
