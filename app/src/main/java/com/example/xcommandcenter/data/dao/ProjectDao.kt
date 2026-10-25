package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.ProjectEntity
import com.example.xcommandcenter.domain.ProjectTree
import kotlinx.coroutines.flow.Flow

/**
 * Completion rule: a project is completed only if every project below it is completed.
 * [complete] refuses otherwise, and anything that leaves an open project under a completed one
 * ([reopen], [saveDetails]) reopens those ancestors.
 */
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET completed = :completed WHERE id IN (:ids)")
    suspend fun setCompleted(ids: Collection<Long>, completed: Boolean)

    @Query("UPDATE projects SET parentId = :newParentId WHERE parentId = :id")
    suspend fun reparentChildren(id: Long, newParentId: Long?)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'PROJECT' AND aId = :id) OR (bType = 'PROJECT' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    /** Inserts or updates the editable fields; the stored completion state is kept. */
    @Transaction
    suspend fun saveDetails(project: ProjectEntity): Long {
        val stored = if (project.id == 0L) null else get(project.id)
        val toSave = project.copy(completed = stored?.completed ?: false)
        val id = if (stored == null) insert(toSave.copy(id = 0)) else toSave.id.also { update(toSave) }
        if (!toSave.completed) reopenAncestors(id)
        return id
    }

    /** Completes [id] if nothing below it is open. Returns the number of open subprojects (0 = completed). */
    @Transaction
    suspend fun complete(id: Long): Int {
        val all = getAll()
        val below = ProjectTree.subtreeIds(id, all, { it.id }, { it.parentId }) - id
        val open = all.count { it.id in below && !it.completed }
        if (open == 0) setCompleted(listOf(id), true)
        return open
    }

    @Transaction
    suspend fun completeWithSubprojects(id: Long) {
        setCompleted(ProjectTree.subtreeIds(id, getAll(), { it.id }, { it.parentId }), true)
    }

    @Transaction
    suspend fun reopen(id: Long) {
        setCompleted(listOf(id), false)
        reopenAncestors(id)
    }

    @Transaction
    suspend fun reopenAncestors(id: Long) {
        val ancestors = ProjectTree.ancestorIds(id, getAll(), { it.id }, { it.parentId })
        if (ancestors.isNotEmpty()) setCompleted(ancestors, false)
    }

    /** Children move up to the deleted project's parent instead of being deleted with it. */
    @Transaction
    suspend fun delete(id: Long) {
        val project = get(id) ?: return
        reparentChildren(id, project.parentId)
        deleteLinks(id)
        deleteRow(id)
    }
}
