package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: Long): GoalEntity?

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'GOAL' AND aId = :id) OR (bType = 'GOAL' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'GOAL' AND itemId = :id")
    suspend fun deleteTags(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteLinks(id)
        deleteTags(id)
        deleteRow(id)
    }
}
