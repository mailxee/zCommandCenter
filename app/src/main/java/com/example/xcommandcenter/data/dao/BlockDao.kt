package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.BlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks ORDER BY startMinute, endMinute, id")
    fun observeAll(): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE scheduleId = :scheduleId ORDER BY startMinute, endMinute, id")
    suspend fun inSchedule(scheduleId: Long): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun get(id: Long): BlockEntity?

    @Insert
    suspend fun insert(block: BlockEntity): Long

    @Update
    suspend fun update(block: BlockEntity)

    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'BLOCK' AND aId = :id) OR (bType = 'BLOCK' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'BLOCK' AND itemId = :id")
    suspend fun deleteTags(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteLinks(id)
        deleteTags(id)
        deleteRow(id)
    }
}
