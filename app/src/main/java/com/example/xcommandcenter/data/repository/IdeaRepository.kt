package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.IdeaDao
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.data.entity.IdeaListItemEntity
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PlanTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IdeaRepository(
    private val dao: IdeaDao,
    private val notes: NoteRepository,
    private val reminders: ReminderRepository,
) {
    fun observeAll(): Flow<List<IdeaEntity>> = dao.observeAll()

    suspend fun get(id: Long): IdeaEntity? = dao.get(id)

    suspend fun listItems(ideaId: Long): List<IdeaListItemEntity> = dao.listItems(ideaId)

    /** List item texts per idea id, for the search. */
    fun observeListTexts(): Flow<Map<Long, List<String>>> =
        dao.observeAllListItems().map { items -> items.groupBy({ it.ideaId }, { it.text }) }

    /** Number of list items per idea id. */
    fun observeListCounts(): Flow<Map<Long, Int>> =
        dao.observeListCounts().map { counts -> counts.associate { it.ideaId to it.total } }

    /**
     * Rejects a parent that would put the idea inside its own subtree. [list], when given,
     * becomes the idea's whole list.
     */
    suspend fun save(idea: IdeaEntity, list: List<IdeaListItemEntity>? = null): Long {
        val parentId = idea.parentId
        if (idea.id != 0L && parentId != null) {
            val forbidden = PlanTree.subtreeIds(idea.id, dao.getAll(), { it.id }, { it.parentId })
            require(parentId !in forbidden) { "An idea can't go under itself or an idea below it" }
        }
        val id = dao.save(idea)
        if (list != null) dao.replaceListItems(id, list)
        return id
    }

    /** Puts the existing idea [id] under [parentId], refusing a move into its own subtree. */
    suspend fun moveUnder(id: Long, parentId: Long) {
        val forbidden = PlanTree.subtreeIds(id, dao.getAll(), { it.id }, { it.parentId })
        require(parentId !in forbidden) { "An idea can't go under itself or an idea below it" }
        dao.setParent(id, parentId)
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** Also removes the reminders of what was deleted. */
    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.removeOrphans()
    }

    /**
     * Makes note [noteId] into an idea of [kind] and deletes the note. The note's title (or the
     * start of its text) becomes the idea, its text the explanation and its list the idea's list. Its links,
     * tags, reminders and priority move to the idea. Returns the new idea's id, or null if the note is gone.
     */
    suspend fun fromNote(noteId: Long, kind: IdeaKind): Long? {
        val note = notes.get(noteId) ?: return null
        val list = notes.listItems(noteId).map { IdeaListItemEntity(ideaId = 0, position = it.position, text = it.text) }
        val firstLine = note.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val title = note.title.trim().ifBlank { firstLine.take(120) }.ifBlank { "Untitled" }
        val bodyText = if (note.title.isBlank()) note.body.trim().removePrefix(firstLine).trim() else note.body.trim()
        val id = dao.save(IdeaEntity(title = title, body = bodyText, kind = kind, priority = note.priority, createdAt = note.createdAt))
        dao.replaceListItems(id, list)
        val from = ItemRef(ItemType.NOTE, noteId)
        val to = ItemRef(ItemType.IDEA, id)
        dao.moveLinks(from, to)
        dao.moveTags(ItemType.NOTE, noteId, ItemType.IDEA, id)
        reminders.replace(to, reminders.times(from))
        notes.delete(noteId)
        return id
    }
}
