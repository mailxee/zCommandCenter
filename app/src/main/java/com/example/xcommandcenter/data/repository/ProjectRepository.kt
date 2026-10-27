package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.ProjectDao
import com.example.xcommandcenter.data.entity.ProjectEntity
import com.example.xcommandcenter.domain.ProjectTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ProjectRepository(private val dao: ProjectDao) {
    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    suspend fun get(id: Long): ProjectEntity? = dao.get(id)

    /** Rejects a parent that would put the project inside its own subtree. */
    suspend fun save(project: ProjectEntity): Long {
        val parentId = project.parentId
        if (project.id != 0L && parentId != null) {
            val all = dao.observeAll().first()
            val forbidden = ProjectTree.subtreeIds(project.id, all, { it.id }, { it.parentId })
            require(parentId !in forbidden) { "A plan cannot be moved under itself or its descendants" }
        }
        return dao.saveDetails(project)
    }

    /** Stores the order of one set of siblings. */
    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** Returns the number of open subprojects; the project is completed only when that is 0. */
    suspend fun complete(id: Long): Int = dao.complete(id)

    suspend fun completeWithSubprojects(id: Long) = dao.completeWithSubprojects(id)

    /** Also reopens completed ancestors, since a completed project can't have open subprojects. */
    suspend fun reopen(id: Long) = dao.reopen(id)

    suspend fun delete(id: Long) = dao.delete(id)
}
