package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.NoteDao
import com.example.xcommandcenter.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun get(id: Long): NoteEntity? = dao.get(id)

    suspend fun save(note: NoteEntity): Long =
        if (note.id == 0L) dao.insert(note) else note.id.also { dao.update(note) }

    suspend fun delete(id: Long) = dao.delete(id)
}
