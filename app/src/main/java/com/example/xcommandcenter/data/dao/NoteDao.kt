package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.NoteListCount
import com.example.xcommandcenter.data.entity.NoteListItemEntity
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao : ItemCleanup {
    @Query("SELECT * FROM notes ORDER BY sortOrder, createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    /** The sortOrder for a new item: ahead of every other one, as this tab lists newest first. */
    @Query("SELECT COALESCE(MIN(sortOrder) - 1, 0) FROM notes")
    suspend fun newSortOrder(): Int

    @Query("UPDATE notes SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Stores [ids] as the new order, first to last. */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("SELECT * FROM note_list_items WHERE noteId = :noteId ORDER BY position")
    suspend fun listItems(noteId: Long): List<NoteListItemEntity>

    /** Every note's list items, for the search. */
    @Query("SELECT * FROM note_list_items ORDER BY noteId, position")
    fun observeAllListItems(): Flow<List<NoteListItemEntity>>

    @Query("SELECT noteId, COUNT(*) AS total FROM note_list_items GROUP BY noteId")
    fun observeListCounts(): Flow<List<NoteListCount>>

    @Insert
    suspend fun insertListItems(items: List<NoteListItemEntity>)

    @Query("DELETE FROM note_list_items WHERE noteId = :noteId")
    suspend fun deleteListItems(noteId: Long)

    /** Makes [items] the note's whole list, numbered 0..n-1 in the given order. */
    @Transaction
    suspend fun replaceListItems(noteId: Long, items: List<NoteListItemEntity>) {
        deleteListItems(noteId)
        insertListItems(items.mapIndexed { index, item -> item.copy(id = 0, noteId = noteId, position = index) })
    }

    /** The list goes with the note through the foreign key's ON DELETE CASCADE. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteRefs(ItemType.NOTE, id)
        deleteRow(id)
    }
}
