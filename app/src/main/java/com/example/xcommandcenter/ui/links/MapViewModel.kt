package com.example.xcommandcenter.ui.links

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.prefs.MapPrefsRepository
import com.example.xcommandcenter.data.repository.ItemDetail
import com.example.xcommandcenter.data.repository.LinkGraph
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LineStyle
import com.example.xcommandcenter.domain.LinkLabels
import com.example.xcommandcenter.domain.MapFilter
import com.example.xcommandcenter.domain.TabModes
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A column's width over a row's height on the map, so the mind map's rings come out round. */
const val MIND_MAP_ASPECT = 150f / 96f

/** How a line on the map is drawn. */
enum class EdgeStyle { ROUTE, OFF_ROUTE, SUGGEST_REMOVE, SHARED, CONTRADICTS, UNDER }

/** An item on the map; [otherMode] when it belongs only to the mode not shown (an end point there), [kind] for an idea. */
data class MapNode(val entry: ItemIndexEntry, val x: Float, val y: Float, val otherMode: Boolean = false, val kind: IdeaKind? = null) {
    val ref: ItemRef get() = entry.ref
}

/**
 * A line on the map. [label] is the link's label read forward, from [subject] (null for a plain
 * link); [directed] when it reads differently from the other end, so the line shows an arrow.
 */
data class MapEdge(
    val link: LinkAnalysis.Link,
    val style: EdgeStyle,
    val finding: LinkAnalysis.Finding?,
    val label: String? = null,
    val subject: ItemRef? = null,
    val directed: Boolean = false,
    /** The kind of link, as [MapFilter] names it, and how that kind is drawn. */
    val kind: String = MapFilter.PLAIN,
    val lineStyle: LineStyle = LineStyle.SOLID,
)

/** A kind of link in the Show panel: [count] lines of it in the network, whether hidden or not. */
data class LinkKindInfo(val key: String, val name: String, val style: LineStyle, val shown: Boolean, val count: Int, val contradicts: Boolean = false)

/** What the map draws: items in grid units (see [GraphLayout]) and the lines between them. */
data class MapState(
    val mode: AppMode,
    val layout: MapViewModel.Layout,
    val root: ItemIndexEntry?,
    val nodes: List<MapNode>,
    val edges: List<MapEdge>,
    val route: LinkRoute,
    /** Open Tidy links findings within what is shown. */
    val findings: List<LinkAnalysis.Finding>,
    val stored: Map<Long, LinkEntity>,
    val filter: MapFilter = MapFilter(),
    /** The tabs with items in the network, hidden or not, for the Show panel. */
    val typesPresent: List<ItemType> = emptyList(),
    val linkKinds: List<LinkKindInfo> = emptyList(),
    /** How many items the Show panel's choices leave out. */
    val hiddenCount: Int = 0,
) {
    val byRef: Map<ItemRef, MapNode> = nodes.associateBy { it.ref }

    /** How many findings suggest a change, for the Tidy links button. */
    val suggestions: Int get() = findings.count { it.suggests }

    fun title(ref: ItemRef): String = byRef[ref]?.entry?.title ?: "an item"
}

sealed interface MapSelection {
    data class Node(val ref: ItemRef) : MapSelection

    data class Edge(val linkId: Long) : MapSelection
}

/** Something just changed on the map, with a way back. */
data class MapUndo(val message: String, val undo: suspend () -> Unit)

/**
 * The map of links: one item's network, or every linked item, laid out by route (rows from the
 * suggested route) or freely. Selecting an item or a line shows what can be done with it.
 */
class MapViewModel(savedState: SavedStateHandle, private val links: LinkRepository, private val prefs: MapPrefsRepository) : ViewModel() {
    /** [MIND_MAP] is Think mode's own: rings round the item in the middle. */
    enum class Layout(val label: String) {
        MIND_MAP("Mind map"),
        BY_ROUTE("By route"),
        FREE("Free"),
        ;

        companion object {
            fun of(mode: AppMode): List<Layout> = if (mode == AppMode.THINK) entries else listOf(BY_ROUTE, FREE)
        }
    }

    private val startRoot = Routes.parseRef(savedState.get<String>(Routes.ARG_ROOT))
    private val focus = savedState.get<String>(Routes.ARG_FOCUS)?.takeIf { it.isNotBlank() }

    /** The item whose network is shown; null shows everything. */
    val root = MutableStateFlow(startRoot)

    /** The last root shown, so "Everything" can switch back. */
    var lastRoot = MutableStateFlow(startRoot)
        private set

    /** The layout picked; null shows the mode's own (mind map in Think, by route in Do). */
    private val pickedLayout = MutableStateFlow<Layout?>(null)
    val selection = MutableStateFlow<MapSelection?>(null)

    /** While set, the next item tapped is linked to this one. */
    val linkingFrom = MutableStateFlow<ItemRef?>(null)

    val undo = MutableStateFlow<MapUndo?>(null)

    private var focused = false

    val state: StateFlow<MapState?> = combine(
        links.observeGraph(), root, pickedLayout, combine(links.mode, links.tabModes, ::Pair), prefs.filter,
    ) { graph, root, picked, (mode, tabModes), filter ->
        val layout = picked?.takeIf { it in Layout.of(mode) } ?: Layout.of(mode).first()
        build(graph, root, layout, mode, tabModes, filter)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The selected item's explanation and list, for its panel. */
    val detail: StateFlow<Pair<ItemRef, ItemDetail>?> = selection.flatMapLatest { sel ->
        if (sel is MapSelection.Node) links.observeDetail(sel.ref).map { sel.ref to it } else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Opened from "Show on map": select the finding's link, or its end point.
        if (focus != null) {
            viewModelScope.launch {
                state.collect { s ->
                    if (s == null || focused) return@collect
                    focused = true
                    val f = s.findings.firstOrNull { it.key == focus }
                    selection.value = f?.link?.let { MapSelection.Edge(it.id) } ?: f?.endPoint?.let { MapSelection.Node(it) }
                }
            }
        }
    }

    private fun build(graph: LinkGraph, root: ItemRef?, layout: Layout, mode: AppMode, tabModes: TabModes, filter: MapFilter): MapState {
        val think = mode == AppMode.THINK
        val shown = graph.nodes(root, mode)
        // By route: Do's layers, or in Think the kinds' layers, then sources, then Do's items.
        fun layer(ref: ItemRef): Int = if (!think) graph.route.layer(ref.type) else when {
            ref.type == ItemType.IDEA -> graph.thinkRoute.layer(graph.kindOf[ref.id] ?: IdeaKind.IDEA)
            tabModes.isOtherMode(ref.type, mode) -> graph.thinkRoute.depth + 1
            else -> graph.thinkRoute.depth
        }
        // In Think mode only links that touch an idea are drawn: two end points (a goal and its habit)
        // aren't part of the thinking, and following them would put the habit a ring out from the goal.
        val inside = graph.links.filter { it.a in shown && it.b in shown && (!think || it.a.type == ItemType.IDEA || it.b.type == ItemType.IDEA) }
        // One line per pair of items (a book and its chapter may both be linked to a task).
        val findings = graph.analysis(mode).findings.filter { f -> f.items.any { it in shown } }
        val byLink = findings.filter { it.link != null }.associateBy { it.link!!.id }
        val shared = findings.filter { it.kind == LinkAnalysis.Kind.SHARED_END_POINT }
            .flatMap { f -> f.sharedBy.map { setOf(it, f.endPoint!!) } }.toSet()
        // In Think mode each idea also hangs from the one it is under: the mind map's tree.
        val allUnder = if (think) graph.under.filter { (child, parent) -> child in shown && parent in shown } else emptyList()
        val underEdges = allUnder.map { (child, parent) ->
            MapEdge(LinkAnalysis.Link(-child.id, child, parent), EdgeStyle.UNDER, null, kind = MapFilter.UNDER, lineStyle = filter.styleOf(MapFilter.UNDER))
        }
        val linkEdges = inside.groupBy { setOf(it.a, it.b) }
            .map { (pair, group) ->
                val link = group.firstOrNull { it.id in byLink } ?: group.first()
                val finding = byLink[link.id]
                val stored = graph.stored[link.id]
                val label = stored?.labelId?.let(graph.labels::get)
                val style = when {
                    link.contradiction -> EdgeStyle.CONTRADICTS
                    think -> EdgeStyle.ROUTE
                    finding?.remove?.id == link.id -> EdgeStyle.SUGGEST_REMOVE
                    graph.route.kind(link.a.type, link.b.type) != LinkRoute.Kind.ON_ROUTE -> EdgeStyle.OFF_ROUTE
                    pair in shared -> EdgeStyle.SHARED
                    else -> EdgeStyle.ROUTE
                }
                val kind = label?.let { MapFilter.label(it.id) } ?: MapFilter.PLAIN
                MapEdge(
                    link, style, finding,
                    label = label?.forward,
                    subject = if (stored?.labelFromA != false) link.a else link.b,
                    directed = label != null && !label.symmetric,
                    kind = kind,
                    lineStyle = filter.styleOf(kind, label?.key),
                )
            }
        val allEdges = underEdges + linkEdges
        // The Show panel's choices: what's left of the network once hidden tabs and kinds of link go.
        val (visible, edges) = filter.apply(shown, allEdges, root, { it.link.a to it.link.b }, { it.kind })
        val nodes = visible.mapNotNull { graph.entries[it] }.sortedWith(compareBy({ layer(it.ref) }, { it.title.lowercase() }))
        val counts = allEdges.groupingBy { it.kind }.eachCount()
        val linkKinds = buildList {
            counts[MapFilter.UNDER]?.let { add(LinkKindInfo(MapFilter.UNDER, "Under", filter.styleOf(MapFilter.UNDER), filter.shows(MapFilter.UNDER), it)) }
            counts[MapFilter.PLAIN]?.let { add(LinkKindInfo(MapFilter.PLAIN, "No label", filter.styleOf(MapFilter.PLAIN), filter.shows(MapFilter.PLAIN), it)) }
            graph.labels.values.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { label ->
                val key = MapFilter.label(label.id)
                counts[key]?.let {
                    val name = label.forward + (label.backward?.let { b -> " / $b" } ?: "")
                    add(LinkKindInfo(key, name, filter.styleOf(key, label.key), filter.shows(key), it, contradicts = label.key == LinkLabels.CONTRADICTS))
                }
            }
        }
        val refs = nodes.map { it.ref }
        // The tree's own lines first, so they decide the mind map's rings.
        val pairs = edges.map { it.link.a to it.link.b }
        val layered = GraphLayout.layered(refs, pairs, maxPerRow = if (root == null) 8 else 6, layerOf = ::layer)
        val places = when (layout) {
            Layout.FREE -> GraphLayout.free(refs, pairs, layered)
            Layout.BY_ROUTE -> layered
            Layout.MIND_MAP -> {
                val parents = edges.filter { it.style == EdgeStyle.UNDER }.associate { it.link.a to it.link.b }
                // With no item in the middle, the top-level ideas share the first ring.
                val roots = if (root != null) listOf(root) else refs.filter { it.type == ItemType.IDEA && it !in parents }
                GraphLayout.radial(roots, refs, pairs, aspect = MIND_MAP_ASPECT)
            }
        }
        return MapState(
            mode = mode,
            layout = layout,
            root = root?.let { graph.entries[it] },
            nodes = nodes.map { n ->
                places.getValue(n.ref).let {
                    MapNode(n, it.x, it.y, otherMode = tabModes.isOtherMode(n.type, mode), kind = if (n.type == ItemType.IDEA) graph.kindOf[n.id] else null)
                }
            },
            edges = edges,
            route = graph.route,
            findings = findings.filter { f -> f.items.any { it in visible } },
            stored = graph.stored,
            filter = filter,
            typesPresent = shown.map { it.type }.distinct().sortedBy { ItemType.tabs.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            linkKinds = linkKinds,
            hiddenCount = (shown - visible).size,
        )
    }

    /** Every link label, for labelling a line from its panel. */
    val labels: StateFlow<List<com.example.xcommandcenter.data.entity.LinkLabelEntity>> =
        links.labels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Labels the line between [a] and [b]: [labelId] reading from [a] when [fromA], or plain when null. */
    fun setLabel(a: ItemRef, b: ItemRef, labelId: Long?, fromA: Boolean) {
        viewModelScope.launch { if (fromA) links.setLabel(a, b, labelId) else links.setLabel(b, a, labelId) }
    }

    fun setTypeShown(type: ItemType, shown: Boolean) {
        viewModelScope.launch { prefs.setTypeShown(type, shown) }
    }

    fun setLinkShown(kind: String, shown: Boolean) {
        viewModelScope.launch { prefs.setLinkShown(kind, shown) }
    }

    fun setLineStyle(kind: String, style: LineStyle) {
        viewModelScope.launch { prefs.setStyle(kind, style) }
    }

    fun showAll() {
        viewModelScope.launch { prefs.showAll() }
    }

    fun setLayout(value: Layout) {
        pickedLayout.value = value
    }

    fun showEverything() {
        root.value = null
        selection.value = null
    }

    fun showNetwork(ref: ItemRef) {
        root.value = ref
        lastRoot.value = ref
        selection.value = MapSelection.Node(ref)
    }

    fun select(value: MapSelection?) {
        selection.value = value
    }

    fun startLinking(from: ItemRef) {
        linkingFrom.value = from
        selection.value = null
    }

    fun cancelLinking() {
        linkingFrom.value = null
    }

    /** While linking: links the chosen item to the one linking started from. */
    fun tapNode(ref: ItemRef) {
        val from = linkingFrom.value
        if (from == null) {
            selection.value = if (selection.value == MapSelection.Node(ref)) null else MapSelection.Node(ref)
            return
        }
        linkingFrom.value = null
        if (from == ref) return
        viewModelScope.launch {
            links.link(from, ref)
            val s = state.value
            undo.value = MapUndo("Linked ${s?.title(from)} and ${s?.title(ref)}") { links.unlink(from, ref) }
        }
    }

    fun removeLink(id: Long) {
        val stored = state.value?.stored?.get(id) ?: return
        selection.value = null
        viewModelScope.launch {
            links.removeLink(id)
            undo.value = MapUndo("Link removed") { links.restore(stored) }
        }
    }

    /** A Skips a layer finding's fix: the new link first, then the direct one goes. */
    fun apply(finding: LinkAnalysis.Finding) {
        val add = finding.add ?: return
        val remove = finding.remove?.let { state.value?.stored?.get(it.id) }
        selection.value = null
        viewModelScope.launch {
            links.link(add.first, add.second)
            remove?.let { links.removeLink(it.id) }
            undo.value = MapUndo("Link moved") {
                remove?.let { links.restore(it) }
                links.unlink(add.first, add.second)
            }
        }
    }

    fun keep(finding: LinkAnalysis.Finding) {
        selection.value = null
        viewModelScope.launch {
            links.keep(finding.key)
            undo.value = MapUndo("Kept as is") { links.unkeep(finding.key) }
        }
    }

    fun undoShown() {
        undo.value = null
    }

    fun runUndo(action: MapUndo) {
        viewModelScope.launch { action.undo() }
    }
}
