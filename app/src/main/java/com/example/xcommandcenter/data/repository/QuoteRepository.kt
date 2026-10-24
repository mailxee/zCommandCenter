package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.QuoteDao
import com.example.xcommandcenter.data.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow

class QuoteRepository(private val dao: QuoteDao) {
    fun observeAll(): Flow<List<QuoteEntity>> = dao.observeAll()

    suspend fun get(id: Long): QuoteEntity? = dao.get(id)

    suspend fun save(quote: QuoteEntity): Long =
        if (quote.id == 0L) dao.insert(quote) else quote.id.also { dao.update(quote) }

    suspend fun delete(id: Long) = dao.delete(id)
}
