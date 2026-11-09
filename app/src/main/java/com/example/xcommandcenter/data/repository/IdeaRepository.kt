package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.IdeaDao
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PlanTree
import kotlinx.coroutines.flow.Flow

class IdeaRepository(
    private val dao: IdeaDao,
    private val notes: NoteRepository,
    private val reminders: ReminderRepository,
) {
    fun observeAll(): Flow<List<IdeaEntity>> = dao.observeAll()

    suspend fun get(id: Long): IdeaEntity? = dao.get(id)

    /** Rejects a parent that would put the idea inside its own subtree. */
    suspend fun save(idea: IdeaEntity): Long {
        val parentId = idea.parentId
        if (idea.id != 0L && parentId != null) {
            val forbidden = PlanTree.subtreeIds(idea.id, dao.getAll(), { it.id }, { it.parentId })
            require(parentId !in forbidden) { "An idea can't go under itself or an idea below it" }
        }
        return dao.save(idea)
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** Also removes the reminders of what was deleted. */
    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.removeOrphans()
    }

    /**
     * Makes note [noteId] into an idea of [kind] and deletes the note. The note's title (or the
     * start of its text) becomes the idea; its text and list become the explanation. Its links,
     * tags, reminders and priority move to the idea. Returns the new idea's id, or null if the note is gone.
     */
    suspend fun fromNote(noteId: Long, kind: IdeaKind): Long? {
        val note = notes.get(noteId) ?: return null
        val list = notes.listItems(noteId).map { "• " + it.text }
        val firstLine = note.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val title = note.title.trim().ifBlank { firstLine.take(120) }.ifBlank { "Untitled" }
        val bodyText = if (note.title.isBlank()) note.body.trim().removePrefix(firstLine).trim() else note.body.trim()
        val body = (listOf(bodyText).filter { it.isNotBlank() } + list).joinToString("\n")
        val id = dao.save(IdeaEntity(title = title, body = body, kind = kind, priority = note.priority, createdAt = note.createdAt))
        val from = ItemRef(ItemType.NOTE, noteId)
        val to = ItemRef(ItemType.IDEA, id)
        dao.moveLinks(from, to)
        dao.moveTags(ItemType.NOTE, noteId, ItemType.IDEA, id)
        reminders.replace(to, reminders.times(from))
        notes.delete(noteId)
        return id
    }
}
