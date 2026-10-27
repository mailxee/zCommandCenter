package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.xcommandcenter.data.entity.ReminderEntity
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY time")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE itemType = :type AND itemId = :itemId ORDER BY time")
    suspend fun forItem(type: ItemType, itemId: Long): List<ReminderEntity>

    @Insert
    suspend fun insert(rows: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE itemType = :type AND itemId = :itemId")
    suspend fun deleteFor(type: ItemType, itemId: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    /** Makes [times] the item's whole set of reminders; returns the new rows. */
    @Transaction
    suspend fun replace(type: ItemType, itemId: Long, times: List<Long>): List<ReminderEntity> {
        deleteFor(type, itemId)
        insert(times.distinct().map { ReminderEntity(itemType = type, itemId = itemId, time = it) })
        return forItem(type, itemId)
    }

    /** Reminders whose item has been deleted. */
    @Query("SELECT * FROM reminders r WHERE NOT EXISTS (SELECT 1 FROM item_index i WHERE i.type = r.itemType AND i.id = r.itemId)")
    suspend fun orphans(): List<ReminderEntity>

    /** The item's title as lists show it; null if the item no longer exists. */
    @Query("SELECT title FROM item_index WHERE type = :type AND id = :itemId")
    suspend fun title(type: ItemType, itemId: Long): String?

    /** 1 for a completed task or plan; anything else is never completed. */
    @Query(
        """
        SELECT CASE :type
            WHEN 'TASK' THEN (SELECT completed FROM tasks WHERE id = :itemId)
            WHEN 'PLAN' THEN (SELECT completed FROM plans WHERE id = :itemId)
            ELSE 0 END
        """,
    )
    suspend fun completed(type: ItemType, itemId: Long): Int?
}
