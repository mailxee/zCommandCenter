package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'NOTE' AND aId = :id) OR (bType = 'NOTE' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteLinks(id)
        deleteRow(id)
    }
}
