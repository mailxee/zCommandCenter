package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.xcommandcenter.data.entity.GlossaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary")
    fun observeAll(): Flow<List<GlossaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: GlossaryEntity)

    @Query("DELETE FROM glossary WHERE `key` = :key")
    suspend fun delete(key: String)
}
