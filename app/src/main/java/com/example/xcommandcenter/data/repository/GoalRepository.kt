package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.GoalDao
import com.example.xcommandcenter.data.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

class GoalRepository(private val dao: GoalDao) {
    fun observeAll(): Flow<List<GoalEntity>> = dao.observeAll()

    suspend fun get(id: Long): GoalEntity? = dao.get(id)

    suspend fun save(goal: GoalEntity): Long =
        if (goal.id == 0L) dao.insert(goal) else goal.id.also { dao.update(goal) }

    suspend fun delete(id: Long) = dao.delete(id)
}
