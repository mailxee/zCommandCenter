package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.data.entity.IdeaListCount
import com.example.xcommandcenter.data.entity.IdeaListItemEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY sortOrder, title COLLATE NOCASE")
    fun observeAll(): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas")
    suspend fun getAll(): List<IdeaEntity>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun get(id: Long): IdeaEntity?

    /** The sortOrder for a new idea: after every other one. */
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM ideas")
    suspend fun newSortOrder(): Int

    @Query("UPDATE ideas SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(idea: IdeaEntity): Long

    @Update
    suspend fun update(idea: IdeaEntity)

    @Query("UPDATE ideas SET parentId = :newParentId WHERE parentId = :id")
    suspend fun reparentChildren(id: Long, newParentId: Long?)

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'IDEA' AND aId = :id) OR (bType = 'IDEA' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'IDEA' AND itemId = :id")
    suspend fun deleteTags(id: Long)

    /** Inserts or updates the editable fields; a stored idea keeps its position. */
    @Transaction
    suspend fun save(idea: IdeaEntity): Long {
        val stored = if (idea.id == 0L) null else get(idea.id)
        return if (stored == null) {
            insert(idea.copy(id = 0, sortOrder = newSortOrder()))
        } else {
            update(idea.copy(sortOrder = stored.sortOrder))
            idea.id
        }
    }

    @Query("UPDATE ideas SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

    @Query("SELECT * FROM idea_list_items WHERE ideaId = :ideaId ORDER BY position")
    suspend fun listItems(ideaId: Long): List<IdeaListItemEntity>

    /** Every idea's list items, for the search. */
    @Query("SELECT * FROM idea_list_items ORDER BY ideaId, position")
    fun observeAllListItems(): Flow<List<IdeaListItemEntity>>

    @Query("SELECT ideaId, COUNT(*) AS total FROM idea_list_items GROUP BY ideaId")
    fun observeListCounts(): Flow<List<IdeaListCount>>

    @Insert
    suspend fun insertListItems(items: List<IdeaListItemEntity>)

    @Query("DELETE FROM idea_list_items WHERE ideaId = :ideaId")
    suspend fun deleteListItems(ideaId: Long)

    /** Makes [items] the idea's whole list, numbered 0..n-1 in the given order. */
    @Transaction
    suspend fun replaceListItems(ideaId: Long, items: List<IdeaListItemEntity>) {
        deleteListItems(ideaId)
        insertListItems(items.mapIndexed { index, item -> item.copy(id = 0, ideaId = ideaId, position = index) })
    }

    /** Children move up to the deleted idea's parent instead of being deleted with it; its list goes with it (ON DELETE CASCADE). */
    @Transaction
    suspend fun delete(id: Long) {
        val idea = get(id) ?: return
        reparentChildren(id, idea.parentId)
        deleteLinks(id)
        deleteTags(id)
        deleteRow(id)
    }

    // Turning a note into an idea: its links and tags move over.

    @Query("SELECT * FROM links WHERE (aType = :type AND aId = :id) OR (bType = :type AND bId = :id)")
    suspend fun linksOf(type: ItemType, id: Long): List<LinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: LinkEntity): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun deleteLink(id: Long)

    @Query("UPDATE OR IGNORE item_tags SET type = :toType, itemId = :toId WHERE type = :fromType AND itemId = :fromId")
    suspend fun moveTags(fromType: ItemType, fromId: Long, toType: ItemType, toId: Long)

    /**
     * Moves every link of [from] to [to], keeping each link's other end and age. Links are stored
     * with their pair in order (see [LinkEntity.between]), so each is made again rather than edited.
     */
    @Transaction
    suspend fun moveLinks(from: ItemRef, to: ItemRef) {
        linksOf(from.type, from.id).forEach { link ->
            val other = if (link.aType == from.type && link.aId == from.id) ItemRef(link.bType, link.bId) else ItemRef(link.aType, link.aId)
            deleteLink(link.id)
            if (other != to) insertLink(LinkEntity.between(to, other).copy(createdAt = link.createdAt))
        }
    }
}
