package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.LinkDao
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.KeptFindingEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.domain.LinkLabels
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ThinkAnalysis
import com.example.xcommandcenter.domain.ThinkRoute
import com.example.xcommandcenter.domain.TabModes
import kotlinx.coroutines.flow.map
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
    /** Every link label by id, for the map's line labels. */
    val labels: Map<Long, LinkLabelEntity>,
    val entries: Map<ItemRef, ItemIndexEntry>,
    /** Links by id as stored, to undo a removal. */
    val stored: Map<Long, LinkEntity>,
    /** Links with chapters folded into their books. */
    val links: List<LinkAnalysis.Link>,
    val bookOfChapter: Map<Long, Long>,
    val route: LinkRoute,
    /** Do mode's findings (goals without a why included, when that check is on). */
    val analysis: LinkAnalysis.Result,
    /** Every idea's parent and kind. */
    val ideas: List<ThinkAnalysis.Idea> = emptyList(),
    val thinkRoute: ThinkRoute = ThinkRoute(),
    /** Think mode's findings (see [ThinkAnalysis]). */
    val thinkAnalysis: LinkAnalysis.Result = LinkAnalysis.Result(emptyList(), emptyList()),
) {
    /** Each idea under its parent, as pairs (child, parent): the mind map's tree. */
    val under: List<Pair<ItemRef, ItemRef>> =
        ideas.mapNotNull { idea -> idea.parentId?.let { ItemRef(ItemType.IDEA, idea.id) to ItemRef(ItemType.IDEA, it) } }

    val kindOf: Map<Long, IdeaKind> = ideas.associate { it.id to it.kind }

    /** Findings for [mode]. */
    fun analysis(mode: AppMode): LinkAnalysis.Result = if (mode == AppMode.THINK) thinkAnalysis else analysis

    /**
     * The items in [root]'s network as the map shows them (chapters within their books), or every
     * linked item. In Think mode a network runs through ideas, along links and from each idea to the
     * one it is under; with no root, it is every idea and what they link to.
     */
    fun nodes(root: ItemRef?, mode: AppMode = AppMode.DO): Set<ItemRef> {
        val all = when {
            mode == AppMode.THINK && root == null -> {
                val ideaRefs = ideas.map { ItemRef(ItemType.IDEA, it.id) }.toSet()
                ideaRefs + links.filter { it.a in ideaRefs || it.b in ideaRefs }.flatMap { listOf(it.a, it.b) }
            }
            mode == AppMode.THINK -> LinkNetwork.of(root!!, links.map { it.a to it.b } + under, bookOfChapter, mode)
            root == null -> links.flatMap { listOf(it.a, it.b) }.toSet()
            else -> LinkNetwork.of(root, links.map { it.a to it.b }, bookOfChapter)
        }
        return all.map { if (it.type == ItemType.CHAPTER) bookOfChapter[it.id]?.let { b -> ItemRef(ItemType.BOOK, b) } ?: it else it }
            .filter { it in entries }.toSet()
    }
}

/** A link's label as read from one end: [subject] when the label points away from that end. */
data class LabelReading(val label: LinkLabelEntity, val subject: Boolean) {
    val wording: String get() = label.wording(subject)
}

class LinkRepository(
    private val dao: LinkDao,
    private val route: Flow<LinkRoute> = flowOf(LinkRoute()),
    /** The app's mode, which decides what link mode's network runs through. */
    val mode: Flow<AppMode> = flowOf(AppMode.DO),
    /** Which mode shows each tab, to mark items from the other mode. */
    val tabModes: Flow<TabModes> = flowOf(TabModes()),
    private val thinkRoute: Flow<ThinkRoute> = flowOf(ThinkRoute()),
    private val checkWhy: Flow<Boolean> = flowOf(true),
) {

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
            combine(dao.observeAll(), dao.observeChapterBooks(), mode, dao.observeIdeas()) { links, chapterBooks, mode, ideas ->
                // In Think mode an idea's network also runs to the idea it is under and the ones under it.
                val under = if (mode == AppMode.THINK) {
                    ideas.mapNotNull { i -> i.parentId?.let { ItemRef(ItemType.IDEA, i.id) to ItemRef(ItemType.IDEA, it) } }
                } else {
                    emptyList()
                }
                LinkNetwork.of(
                    root.ref,
                    links.map { ItemRef(it.aType, it.aId) to ItemRef(it.bType, it.bId) } + under,
                    chapterBooks.associate { it.chapterId to it.id },
                    mode,
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

    /** Links [subject] to [obj] with [labelId] reading from [subject]; an existing link gets the label. */
    suspend fun link(subject: ItemRef, obj: ItemRef, labelId: Long?) {
        if (subject == obj) return
        val link = LinkEntity.labelled(subject, obj, labelId)
        dao.insert(link)
        dao.setLabel(link.aType, link.aId, link.bType, link.bId, link.labelId, link.labelFromA)
    }

    /** Sets the label of the link between [subject] and [obj], reading from [subject]; null makes it plain. */
    suspend fun setLabel(subject: ItemRef, obj: ItemRef, labelId: Long?) {
        val link = LinkEntity.labelled(subject, obj, labelId)
        dao.setLabel(link.aType, link.aId, link.bType, link.bId, link.labelId, link.labelFromA)
    }

    val labels: Flow<List<LinkLabelEntity>> = dao.observeLabels()

    /** How many links carry each label, by label id. */
    val labelUses: Flow<Map<Long, Int>> = dao.observeLabelUses().map { uses -> uses.associate { it.id to it.uses } }

    suspend fun addLabel(forward: String, backward: String?) {
        dao.insertLabel(LinkLabelEntity(forward = forward.trim(), backward = backward?.trim()?.ifBlank { null }, sortOrder = dao.newLabelOrder()))
    }

    suspend fun renameLabel(id: Long, forward: String, backward: String?) = dao.renameLabel(id, forward.trim(), backward?.trim()?.ifBlank { null })

    suspend fun deleteLabel(id: Long) = dao.deleteLabel(id)

    /**
     * The label on each of [ref]'s links, by the item at the other end, read from [ref]'s side.
     * Plain links are left out.
     */
    fun observeLabelsOf(ref: ItemRef): Flow<Map<ItemRef, LabelReading>> =
        combine(dao.observeLinksOf(ref.type, ref.id), labels) { links, labels ->
            val byId = labels.associateBy { it.id }
            links.mapNotNull { link ->
                val label = link.labelId?.let(byId::get) ?: return@mapNotNull null
                val selfIsA = link.isA(ref)
                val other = if (selfIsA) ItemRef(link.bType, link.bId) else ItemRef(link.aType, link.aId)
                other to LabelReading(label, LinkLabels.isSubject(selfIsA, link.labelFromA))
            }.toMap()
        }

    /** See [LinkGraph]; worked out off the main thread. */
    fun observeGraph(): Flow<LinkGraph> =
        combine(
            combine(dao.observeIndex(), dao.observeAll(), dao.observeIdeas(), ::Triple),
            dao.observeChapterBooks(),
            combine(route, thinkRoute, checkWhy, ::Triple),
            dao.observeKept(),
            labels,
        ) { (index, stored, ideas), chapterBooks, (route, thinkRoute, checkWhy), kept, labels ->
            val bookOf = chapterBooks.associate { it.chapterId to it.id }
            fun node(ref: ItemRef) = if (ref.type == ItemType.CHAPTER) bookOf[ref.id]?.let { ItemRef(ItemType.BOOK, it) } ?: ref else ref
            val labelById = labels.associateBy { it.id }
            val links = stored.map {
                val label = it.labelId?.let(labelById::get)
                LinkAnalysis.Link(
                    it.id, ItemRef(it.aType, it.aId), ItemRef(it.bType, it.bId), it.createdAt,
                    labelled = label != null, contradiction = label?.key == LinkLabels.CONTRADICTS,
                )
            }
            val folded = links.map { it.copy(a = node(it.a), b = node(it.b)) }.filter { it.a != it.b }
            val doAnalysis = LinkAnalysis.analyze(links, bookOf, route, kept.toSet()).let { result ->
                if (!checkWhy) return@let result
                val goals = index.filter { it.type == ItemType.GOAL }.map { it.id }.toSet()
                val (open, keptWhy) = ThinkAnalysis.goalsWithoutWhy(folded, goals).partition { it.key !in kept }
                LinkAnalysis.Result(result.findings + open, result.kept + keptWhy)
            }
            LinkGraph(
                ideas = ideas,
                thinkRoute = thinkRoute,
                thinkAnalysis = ThinkAnalysis.analyze(
                    folded, ideas,
                    quotes = index.filter { it.type == ItemType.QUOTE }.map { it.id }.toSet(),
                    authors = index.filter { it.type == ItemType.QUOTE && !it.subtitle.isNullOrBlank() }.associate { it.id to it.subtitle!! },
                    people = index.filter { it.type == ItemType.PERSON }.associate { it.id to it.title },
                    kept = kept.toSet(),
                ),
                labels = labelById,
                entries = index.associateBy { it.ref },
                stored = stored.associateBy { it.id },
                links = folded,
                bookOfChapter = bookOf,
                route = route,
                analysis = doAnalysis,
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
