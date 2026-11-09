package com.example.xcommandcenter.ui.links

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.repository.LinkGraph
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How a line on the map is drawn. */
enum class EdgeStyle { ROUTE, OFF_ROUTE, SUGGEST_REMOVE, SHARED, CONTRADICTS }

data class MapNode(val entry: ItemIndexEntry, val x: Float, val y: Float) {
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
)

/** What the map draws: items in grid units (see [GraphLayout]) and the lines between them. */
data class MapState(
    val root: ItemIndexEntry?,
    val nodes: List<MapNode>,
    val edges: List<MapEdge>,
    val route: LinkRoute,
    /** Open Tidy links findings within what is shown. */
    val findings: List<LinkAnalysis.Finding>,
    val stored: Map<Long, LinkEntity>,
) {
    val byRef: Map<ItemRef, MapNode> = nodes.associateBy { it.ref }

    /** How many findings suggest a change, for the Tidy links button. */
    val suggestions: Int get() = findings.count { it.actionable }

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
class MapViewModel(savedState: SavedStateHandle, private val links: LinkRepository) : ViewModel() {
    enum class Layout { BY_ROUTE, FREE }

    private val startRoot = Routes.parseRef(savedState.get<String>(Routes.ARG_ROOT))
    private val focus = savedState.get<String>(Routes.ARG_FOCUS)?.takeIf { it.isNotBlank() }

    /** The item whose network is shown; null shows everything. */
    val root = MutableStateFlow(startRoot)

    /** The last root shown, so "Everything" can switch back. */
    var lastRoot = MutableStateFlow(startRoot)
        private set

    val layout = MutableStateFlow(Layout.BY_ROUTE)
    val selection = MutableStateFlow<MapSelection?>(null)

    /** While set, the next item tapped is linked to this one. */
    val linkingFrom = MutableStateFlow<ItemRef?>(null)

    val undo = MutableStateFlow<MapUndo?>(null)

    private var focused = false

    val state: StateFlow<MapState?> = combine(links.observeGraph(), root, layout) { graph, root, layout ->
        build(graph, root, layout)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private fun build(graph: LinkGraph, root: ItemRef?, layout: Layout): MapState {
        val shown = graph.nodes(root)
        val nodes = shown.mapNotNull { graph.entries[it] }.sortedWith(compareBy({ graph.route.layer(it.type) }, { it.title.lowercase() }))
        val inside = graph.links.filter { it.a in shown && it.b in shown }
        // One line per pair of items (a book and its chapter may both be linked to a task).
        val findings = (graph.analysis.findings).filter { f -> f.items.any { it in shown } }
        val byLink = findings.filter { it.link != null }.associateBy { it.link!!.id }
        val shared = findings.filter { it.kind == LinkAnalysis.Kind.SHARED_END_POINT }
            .flatMap { f -> f.sharedBy.map { setOf(it, f.endPoint!!) } }.toSet()
        val edges = inside.groupBy { setOf(it.a, it.b) }.map { (pair, group) ->
            val link = group.firstOrNull { it.id in byLink } ?: group.first()
            val finding = byLink[link.id]
            val stored = graph.stored[link.id]
            val label = stored?.labelId?.let(graph.labels::get)
            val style = when {
                link.contradiction -> EdgeStyle.CONTRADICTS
                finding?.remove?.id == link.id -> EdgeStyle.SUGGEST_REMOVE
                graph.route.kind(link.a.type, link.b.type) != LinkRoute.Kind.ON_ROUTE -> EdgeStyle.OFF_ROUTE
                pair in shared -> EdgeStyle.SHARED
                else -> EdgeStyle.ROUTE
            }
            MapEdge(
                link, style, finding,
                label = label?.forward,
                subject = if (stored?.labelFromA != false) link.a else link.b,
                directed = label != null && !label.symmetric,
            )
        }
        val refs = nodes.map { it.ref }
        val pairs = edges.map { it.link.a to it.link.b }
        val layered = GraphLayout.layered(refs, pairs, maxPerRow = if (root == null) 8 else 6) { graph.route.layer(it.type) }
        val places = if (layout == Layout.FREE) GraphLayout.free(refs, pairs, layered) else layered
        return MapState(
            root = root?.let { graph.entries[it] },
            nodes = nodes.map { n -> places.getValue(n.ref).let { MapNode(n, it.x, it.y) } },
            edges = edges,
            route = graph.route,
            findings = findings,
            stored = graph.stored,
        )
    }

    fun setLayout(value: Layout) {
        layout.value = value
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
