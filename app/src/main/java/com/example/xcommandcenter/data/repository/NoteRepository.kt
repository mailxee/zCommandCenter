package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.NoteDao
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.NoteListItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val dao: NoteDao, private val reminders: ReminderRepository) {
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun get(id: Long): NoteEntity? = dao.get(id)

    suspend fun listItems(noteId: Long): List<NoteListItemEntity> = dao.listItems(noteId)

    /** Number of list items per note id. */
    fun observeListCounts(): Flow<Map<Long, Int>> =
        dao.observeListCounts().map { counts -> counts.associate { it.noteId to it.total } }

    /** A new note gets a place in the list; an edited one keeps its place. [list] becomes the note's list. */
    suspend fun save(note: NoteEntity, list: List<NoteListItemEntity>): Long {
        val id = if (note.id == 0L) {
            dao.insert(note.copy(sortOrder = dao.newSortOrder()))
        } else {
            note.id.also { dao.update(note.copy(sortOrder = dao.get(note.id)?.sortOrder ?: note.sortOrder)) }
        }
        dao.replaceListItems(id, list)
        return id
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** Also removes the reminders of what was deleted. */
    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.removeOrphans()
    }
}
