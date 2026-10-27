package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.GoalDao
import com.example.xcommandcenter.data.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

class GoalRepository(private val dao: GoalDao) {
    fun observeAll(): Flow<List<GoalEntity>> = dao.observeAll()

    suspend fun get(id: Long): GoalEntity? = dao.get(id)

    /** A new goal gets a place in the list; an edited one keeps its place. */
    suspend fun save(goal: GoalEntity): Long =
        if (goal.id == 0L) {
            dao.insert(goal.copy(sortOrder = dao.newSortOrder()))
        } else {
            goal.id.also { dao.update(goal.copy(sortOrder = dao.get(goal.id)?.sortOrder ?: goal.sortOrder)) }
        }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun delete(id: Long) = dao.delete(id)
}
