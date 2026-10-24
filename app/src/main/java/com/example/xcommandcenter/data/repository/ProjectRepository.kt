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
            require(parentId !in forbidden) { "A project cannot be moved under itself or its descendants" }
        }
        return if (project.id == 0L) dao.insert(project) else project.id.also { dao.update(project) }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
