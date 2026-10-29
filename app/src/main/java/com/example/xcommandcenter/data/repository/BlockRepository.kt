package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.BlockDao
import com.example.xcommandcenter.data.entity.BlockEntity
import kotlinx.coroutines.flow.Flow

class BlockRepository(private val dao: BlockDao, private val reminders: ReminderRepository) {
    fun observeAll(): Flow<List<BlockEntity>> = dao.observeAll()

    suspend fun get(id: Long): BlockEntity? = dao.get(id)

    suspend fun save(block: BlockEntity): Long =
        if (block.id == 0L) dao.insert(block) else block.id.also { dao.update(block) }

    /** Also removes the reminders of what was deleted. */
    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.removeOrphans()
    }
}
