package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.TagDao
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.domain.ItemRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepository(private val dao: TagDao) {
    fun observeInUse(): Flow<List<TagEntity>> = dao.observeInUse()

    /** For every tagged item, its tags sorted by name. */
    fun observeTagsByItem(): Flow<Map<ItemRef, List<TagEntity>>> =
        dao.observeAllItemTags().map { rows -> rows.groupBy({ it.ref }, { it.tag }) }

    fun observeTags(ref: ItemRef): Flow<List<TagEntity>> = dao.observeTags(ref.type, ref.id)

    /** Trims the name and ignores a blank one. */
    suspend fun add(ref: ItemRef, name: String) {
        val trimmed = name.trim().removePrefix("#").trim()
        if (trimmed.isNotEmpty()) dao.addTag(ref.type, ref.id, trimmed.take(MAX_LENGTH))
    }

    /** A tag no item carries any more is deleted. */
    suspend fun remove(ref: ItemRef, tagId: Long) = dao.removeTag(ref.type, ref.id, tagId)

    companion object {
        const val MAX_LENGTH = 30
    }
}
