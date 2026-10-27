package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.domain.PlanTree
import kotlinx.coroutines.flow.Flow

/**
 * Completion rule: a plan is completed only if every plan below it is completed.
 * [complete] refuses otherwise, and anything that leaves an open plan under a completed one
 * ([reopen], [saveDetails]) reopens those ancestors.
 */
@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY sortOrder, title COLLATE NOCASE")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans")
    suspend fun getAll(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun get(id: Long): PlanEntity?

    /** The sortOrder for a new item: after every other one. */
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM plans")
    suspend fun newSortOrder(): Int

    @Query("UPDATE plans SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Stores [ids] as the new order, first to last. */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Update
    suspend fun update(plan: PlanEntity)

    @Query("UPDATE plans SET completed = :completed WHERE id IN (:ids)")
    suspend fun setCompleted(ids: Collection<Long>, completed: Boolean)

    @Query("UPDATE plans SET parentId = :newParentId WHERE parentId = :id")
    suspend fun reparentChildren(id: Long, newParentId: Long?)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'PLAN' AND aId = :id) OR (bType = 'PLAN' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'PLAN' AND itemId = :id")
    suspend fun deleteTags(id: Long)

    /** Inserts or updates the editable fields; the stored completion state and position are kept. */
    @Transaction
    suspend fun saveDetails(plan: PlanEntity): Long {
        val stored = if (plan.id == 0L) null else get(plan.id)
        val toSave = plan.copy(completed = stored?.completed ?: false, sortOrder = stored?.sortOrder ?: newSortOrder())
        val id = if (stored == null) insert(toSave.copy(id = 0)) else toSave.id.also { update(toSave) }
        if (!toSave.completed) reopenAncestors(id)
        return id
    }

    /** Completes [id] if nothing below it is open. Returns the number of open subplans (0 = completed). */
    @Transaction
    suspend fun complete(id: Long): Int {
        val all = getAll()
        val below = PlanTree.subtreeIds(id, all, { it.id }, { it.parentId }) - id
        val open = all.count { it.id in below && !it.completed }
        if (open == 0) setCompleted(listOf(id), true)
        return open
    }

    @Transaction
    suspend fun completeWithSubplans(id: Long) {
        setCompleted(PlanTree.subtreeIds(id, getAll(), { it.id }, { it.parentId }), true)
    }

    @Transaction
    suspend fun reopen(id: Long) {
        setCompleted(listOf(id), false)
        reopenAncestors(id)
    }

    @Transaction
    suspend fun reopenAncestors(id: Long) {
        val ancestors = PlanTree.ancestorIds(id, getAll(), { it.id }, { it.parentId })
        if (ancestors.isNotEmpty()) setCompleted(ancestors, false)
    }

    /** Children move up to the deleted plan's parent instead of being deleted with it. */
    @Transaction
    suspend fun delete(id: Long) {
        val plan = get(id) ?: return
        reparentChildren(id, plan.parentId)
        deleteLinks(id)
        deleteTags(id)
        deleteRow(id)
    }
}
