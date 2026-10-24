package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET parentId = :newParentId WHERE parentId = :id")
    suspend fun reparentChildren(id: Long, newParentId: Long?)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'PROJECT' AND aId = :id) OR (bType = 'PROJECT' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    /** Children move up to the deleted project's parent instead of being deleted with it. */
    @Transaction
    suspend fun delete(id: Long) {
        val project = get(id) ?: return
        reparentChildren(id, project.parentId)
        deleteLinks(id)
        deleteRow(id)
    }
}
