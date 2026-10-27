package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.NoteDao
import com.example.xcommandcenter.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun get(id: Long): NoteEntity? = dao.get(id)

    /** A new note gets a place in the list; an edited one keeps its place. */
    suspend fun save(note: NoteEntity): Long =
        if (note.id == 0L) {
            dao.insert(note.copy(sortOrder = dao.newSortOrder()))
        } else {
            note.id.also { dao.update(note.copy(sortOrder = dao.get(note.id)?.sortOrder ?: note.sortOrder)) }
        }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun delete(id: Long) = dao.delete(id)
}
