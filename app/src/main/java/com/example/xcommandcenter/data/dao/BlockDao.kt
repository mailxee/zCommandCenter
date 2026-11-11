package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.BlockEntity
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao : ItemCleanup {
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

    @Transaction
    suspend fun delete(id: Long) {
        deleteRefs(ItemType.BLOCK, id)
        deleteRow(id)
    }
}
