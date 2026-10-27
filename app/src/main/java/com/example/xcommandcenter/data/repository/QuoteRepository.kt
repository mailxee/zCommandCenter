package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.QuoteDao
import com.example.xcommandcenter.data.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow

class QuoteRepository(private val dao: QuoteDao) {
    fun observeAll(): Flow<List<QuoteEntity>> = dao.observeAll()

    suspend fun get(id: Long): QuoteEntity? = dao.get(id)

    /** A new quote gets a place in the list; an edited one keeps its place. */
    suspend fun save(quote: QuoteEntity): Long =
        if (quote.id == 0L) {
            dao.insert(quote.copy(sortOrder = dao.newSortOrder()))
        } else {
            quote.id.also { dao.update(quote.copy(sortOrder = dao.get(quote.id)?.sortOrder ?: quote.sortOrder)) }
        }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun delete(id: Long) = dao.delete(id)
}
