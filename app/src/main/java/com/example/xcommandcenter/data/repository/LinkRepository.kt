package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.LinkDao
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.KeptFindingEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.domain.LinkNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * Everything the map and Tidy links draw from: every item's label, the links (a chapter's shown as
 * its book's, see [LinkAnalysis]), and the analysis under the current route and kept findings.
 */
data class LinkGraph(
    val entries: Map<ItemRef, ItemIndexEntry>,
    /** Links by id as stored, to undo a removal. */
    val stored: Map<Long, LinkEntity>,
    /** Links with chapters folded into their books. */
    val links: List<LinkAnalysis.Link>,
    val bookOfChapter: Map<Long, Long>,
    val route: LinkRoute,
    val analysis: LinkAnalysis.Result,
) {
    /** The items in [root]'s network as the map shows them (chapters within their books), or every linked item. */
    fun nodes(root: ItemRef?): Set<ItemRef> {
        val all = if (root == null) {
            links.flatMap { listOf(it.a, it.b) }.toSet()
        } else {
            LinkNetwork.of(root, links.map { it.a to it.b }, bookOfChapter)
        }
        return all.map { if (it.type == ItemType.CHAPTER) bookOfChapter[it.id]?.let { b -> ItemRef(ItemType.BOOK, b) } ?: it else it }
            .filter { it in entries }.toSet()
    }
}

class LinkRepository(private val dao: LinkDao, private val route: Flow<LinkRoute> = flowOf(LinkRoute())) {

    /**
     * Link mode, kept in memory for as long as the app runs: once a card is pressed and held, it is
     * the root and the root's [network] is all the app shows.
     */
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

    /** Turns link mode on with [ref] as the root, or moves the root to [ref] when already on. */
    fun showNetwork(ref: ItemRef) {
        rootRef.value = ref
    }

    /** Back to normal mode. */
    fun exitLinkMode() {
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

    /** See [LinkGraph]; worked out off the main thread. */
    fun observeGraph(): Flow<LinkGraph> =
        combine(dao.observeIndex(), dao.observeAll(), dao.observeChapterBooks(), route, dao.observeKept()) { index, stored, chapterBooks, route, kept ->
            val bookOf = chapterBooks.associate { it.chapterId to it.id }
            fun node(ref: ItemRef) = if (ref.type == ItemType.CHAPTER) bookOf[ref.id]?.let { ItemRef(ItemType.BOOK, it) } ?: ref else ref
            val links = stored.map { LinkAnalysis.Link(it.id, ItemRef(it.aType, it.aId), ItemRef(it.bType, it.bId), it.createdAt) }
            LinkGraph(
                entries = index.associateBy { it.ref },
                stored = stored.associateBy { it.id },
                links = links.map { it.copy(a = node(it.a), b = node(it.b)) }.filter { it.a != it.b },
                bookOfChapter = bookOf,
                route = route,
                analysis = LinkAnalysis.analyze(links, bookOf, route, kept.toSet()),
            )
        }.flowOn(Dispatchers.Default)

    suspend fun removeLink(id: Long) = dao.deleteById(id)

    /** Puts back a link removed with [removeLink]. */
    suspend fun restore(link: LinkEntity) {
        dao.insert(link.copy(id = 0))
    }

    /** "Keep as is" on a Tidy links finding, and its undo. */
    suspend fun keep(key: String) = dao.keep(KeptFindingEntity(key))

    suspend fun unkeep(key: String) = dao.unkeep(key)

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
