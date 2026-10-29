package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.LinkDao
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LinkNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class LinkRepository(private val dao: LinkDao) {

    /**
     * Link mode, kept in memory for as long as the app runs: [picking] while waiting for the card
     * that becomes the root, then the root's [network] is all the app shows.
     */
    private val _picking = MutableStateFlow(false)
    val picking: StateFlow<Boolean> = _picking.asStateFlow()

    private val rootRef = MutableStateFlow<ItemRef?>(null)

    /** The root while link mode is on; null in normal mode, and once the root is deleted. */
    val linkRoot: Flow<ItemIndexEntry?> =
        rootRef.flatMapLatest { ref -> if (ref == null) flowOf(null) else dao.observeEntry(ref.type, ref.id) }

    /** Every item linked to the root, directly or through other items (see [LinkNetwork]); null in normal mode. */
    val network: Flow<Set<ItemRef>?> = linkRoot.flatMapLatest { root ->
        if (root == null) {
            flowOf(null)
        } else {
            combine(dao.observeAll(), dao.observeChapterBooks()) { links, chapterBooks ->
                LinkNetwork.of(
                    root.ref,
                    links.map { ItemRef(it.aType, it.aId) to ItemRef(it.bType, it.bId) },
                    chapterBooks.associate { it.chapterId to it.id },
                )
            }
        }
    }

    /** The link mode button: waits for a card to pick as the root. */
    fun startPicking() {
        rootRef.value = null
        _picking.value = true
    }

    fun pickRoot(ref: ItemRef) {
        _picking.value = false
        rootRef.value = ref
    }

    /** Back to normal mode, from picking or from link mode. */
    fun exitLinkMode() {
        _picking.value = false
        rootRef.value = null
    }

    /** In link mode, links a new item to the root, so it joins the network rather than disappearing on save. */
    suspend fun joinNetwork(ref: ItemRef) {
        val root = linkRoot.first() ?: return
        if (ref !in network.first().orEmpty()) link(ref, root.ref)
    }

    fun observeLinked(ref: ItemRef): Flow<List<ItemIndexEntry>> = dao.observeLinked(ref.type, ref.id)

    /**
     * For every item that has links, the items it is linked to, as cards list them: a linked
     * chapter is shown as its book, and a book also lists what its chapters are linked to. Each
     * item appears once, and never the item itself.
     */
    fun observeLinksByOwner(): Flow<Map<ItemRef, List<ItemIndexEntry>>> =
        combine(dao.observeAllOwned(), dao.observeChapterLinksByBook(), dao.observeChapterBooks()) { rows, chapterRows, chapterBooks ->
            val bookOf = chapterBooks.associate { it.chapterId to it.entry }
            (rows + chapterRows)
                .map { row -> if (row.type == ItemType.CHAPTER) bookOf[row.id]?.let { row.owner to it } ?: (row.owner to row.entry) else row.owner to row.entry }
                .filter { (owner, entry) -> owner != entry.ref }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, entries) -> entries.distinctBy { it.ref }.sortedBy { it.title.lowercase() } }
        }

    suspend fun link(a: ItemRef, b: ItemRef) {
        if (a != b) dao.insert(LinkEntity.between(a, b))
    }

    suspend fun unlink(a: ItemRef, b: ItemRef) {
        val link = LinkEntity.between(a, b)
        dao.delete(link.aType, link.aId, link.bType, link.bId)
    }

    /** Picker search within one tab (books only; their chapters come from [chapters]). In link mode, only the network. */
    fun search(tab: ItemType, query: String): Flow<List<ItemIndexEntry>> = inNetwork(dao.search(listOf(tab.name), query.trim()))

    /** A book's chapters in order, for the picker's second step. In link mode, only the network. */
    fun chapters(bookId: Long): Flow<List<ItemIndexEntry>> = inNetwork(dao.observeChapters(bookId))

    private fun inNetwork(entries: Flow<List<ItemIndexEntry>>): Flow<List<ItemIndexEntry>> =
        combine(entries, network) { found, network -> if (network == null) found else found.filter { it.ref in network } }
}
