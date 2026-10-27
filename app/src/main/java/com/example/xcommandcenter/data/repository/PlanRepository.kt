package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.PlanDao
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.domain.PlanTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PlanRepository(private val dao: PlanDao) {
    fun observeAll(): Flow<List<PlanEntity>> = dao.observeAll()

    suspend fun get(id: Long): PlanEntity? = dao.get(id)

    /** Rejects a parent that would put the plan inside its own subtree. */
    suspend fun save(plan: PlanEntity): Long {
        val parentId = plan.parentId
        if (plan.id != 0L && parentId != null) {
            val all = dao.observeAll().first()
            val forbidden = PlanTree.subtreeIds(plan.id, all, { it.id }, { it.parentId })
            require(parentId !in forbidden) { "A plan cannot be moved under itself or its descendants" }
        }
        return dao.saveDetails(plan)
    }

    /** Stores the order of one set of siblings. */
    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** Returns the number of open subplans; the plan is completed only when that is 0. */
    suspend fun complete(id: Long): Int = dao.complete(id)

    suspend fun completeWithSubplans(id: Long) = dao.completeWithSubplans(id)

    /** Also reopens completed ancestors, since a completed plan can't have open subplans. */
    suspend fun reopen(id: Long) = dao.reopen(id)

    suspend fun delete(id: Long) = dao.delete(id)
}
