package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.LinkDao
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LinkRepository(private val dao: LinkDao) {

    fun observeLinked(ref: ItemRef): Flow<List<ItemIndexEntry>> = dao.observeLinked(ref.type, ref.id)

    /** For every item that has links, the items it is linked to. */
    fun observeLinksByOwner(): Flow<Map<ItemRef, List<ItemIndexEntry>>> =
        dao.observeAllOwned().map { rows ->
            rows.groupBy({ it.owner }, { it.entry }).mapValues { (_, entries) -> entries.sortedBy { it.title.lowercase() } }
        }

    suspend fun link(a: ItemRef, b: ItemRef) {
        if (a != b) dao.insert(LinkEntity.between(a, b))
    }

    suspend fun unlink(a: ItemRef, b: ItemRef) {
        val link = LinkEntity.between(a, b)
        dao.delete(link.aType, link.aId, link.bType, link.bId)
    }

    /** Picker search within one tab; the Books tab also offers its chapters. */
    fun search(tab: ItemType, query: String): Flow<List<ItemIndexEntry>> {
        val types = if (tab == ItemType.BOOK) listOf(ItemType.BOOK, ItemType.CHAPTER) else listOf(tab)
        return dao.search(types.map { it.name }, query.trim())
    }
}
