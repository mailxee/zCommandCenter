package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun get(id: Long): QuoteEntity?

    @Insert
    suspend fun insert(quote: QuoteEntity): Long

    @Update
    suspend fun update(quote: QuoteEntity)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'QUOTE' AND aId = :id) OR (bType = 'QUOTE' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteLinks(id)
        deleteRow(id)
    }
}
