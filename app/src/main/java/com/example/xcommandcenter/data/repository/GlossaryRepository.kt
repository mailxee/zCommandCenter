package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.GlossaryDao
import com.example.xcommandcenter.data.entity.GlossaryEntity
import com.example.xcommandcenter.domain.Glossary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GlossaryRepository(private val dao: GlossaryDao) {
    val terms: Flow<Glossary.Terms> = dao.observeAll().map { rows -> Glossary.Terms(rows.associate { it.key to it.definition }) }

    /** Stores [definition] for [key]; the starting one ([default]) or a blank one isn't stored. */
    suspend fun set(key: String, definition: String, default: String) {
        val text = definition.trim()
        if (text.isEmpty() || text == default.trim()) dao.delete(key) else dao.put(GlossaryEntity(key, text))
    }

    suspend fun reset(key: String) = dao.delete(key)
}
