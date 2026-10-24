package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'TASK' AND aId = :id) OR (bType = 'TASK' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteLinks(id)
        deleteRow(id)
    }
}
