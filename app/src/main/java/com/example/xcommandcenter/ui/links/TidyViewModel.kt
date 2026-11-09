package com.example.xcommandcenter.ui.links

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.LinkLabels
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TidyState(
    val mode: AppMode,
    /** How many findings ask for something in each mode, for the switch. */
    val counts: Map<AppMode, Int>,
    val root: ItemIndexEntry?,
    val findings: List<LinkAnalysis.Finding>,
    val kept: List<LinkAnalysis.Finding>,
    val extraRoutes: Int,
    val route: LinkRoute,
    val entries: Map<ItemRef, ItemIndexEntry>,
    val stored: Map<Long, LinkEntity>,
) {
    fun title(ref: ItemRef): String = entries[ref]?.title ?: "an item"
}

/**
 * Tidy links (§2.57, §2.62): the findings within one network, or all of them, with their fixes;
 * Do mode's ([LinkAnalysis]) or Think mode's (`ThinkAnalysis`), starting with the app's mode.
 */
class TidyViewModel(savedState: SavedStateHandle, private val links: LinkRepository) : ViewModel() {
    private val root = Routes.parseRef(savedState.get<String>(Routes.ARG_ROOT))

    val undo = MutableStateFlow<MapUndo?>(null)

    /** The mode picked on this screen; null follows the app's. */
    private val picked = MutableStateFlow<AppMode?>(null)

    private var sourceLabel: Long? = null

    val state: StateFlow<TidyState?> = combine(links.observeGraph(), picked, links.mode) { graph, picked, appMode ->
        val mode = picked ?: appMode
        sourceLabel = graph.labels.values.firstOrNull { it.key == LinkLabels.SOURCE }?.id
        fun scoped(m: AppMode): Pair<List<LinkAnalysis.Finding>, List<LinkAnalysis.Finding>> {
            val shown = if (root == null) null else graph.nodes(root, m)
            fun inScope(f: LinkAnalysis.Finding) = shown == null || f.items.any { it in shown }
            return graph.analysis(m).findings.filter(::inScope) to graph.analysis(m).kept.filter(::inScope)
        }
        val (findings, kept) = scoped(mode)
        TidyState(
            mode = mode,
            counts = AppMode.entries.associateWith { m -> scoped(m).first.count { it.suggests } },
            root = root?.let { graph.entries[it] },
            findings = findings,
            kept = kept,
            extraRoutes = (findings + kept).count { it.kind in LinkAnalysis.loopKinds },
            route = graph.route,
            entries = graph.entries,
            stored = graph.stored,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMode(mode: AppMode) {
        picked.value = mode
    }

    /**
     * The finding's main fix: remove its link, or for a skipped layer link through the suggested
     * item first; for an author in People, link the person as the quote's source.
     */
    fun fix(finding: LinkAnalysis.Finding) {
        if (finding.kind == LinkAnalysis.Kind.AUTHOR_IS_PERSON) {
            val (person, quote) = finding.add ?: return
            viewModelScope.launch {
                links.link(person, quote, sourceLabel)
                undo.value = MapUndo("Linked as its source") { links.unlink(person, quote) }
            }
            return
        }
        val stored = finding.remove?.let { state.value?.stored?.get(it.id) }
        val add = finding.add
        viewModelScope.launch {
            add?.let { links.link(it.first, it.second) }
            stored?.let { links.removeLink(it.id) }
            undo.value = MapUndo(if (add != null) "Link moved" else "Link removed") {
                stored?.let { links.restore(it) }
                add?.let { links.unlink(it.first, it.second) }
            }
        }
    }

    fun keep(finding: LinkAnalysis.Finding) {
        viewModelScope.launch {
            links.keep(finding.key)
            undo.value = MapUndo("Kept as is") { links.unkeep(finding.key) }
        }
    }

    fun showAgain(finding: LinkAnalysis.Finding) {
        viewModelScope.launch { links.unkeep(finding.key) }
    }

    fun undoShown() {
        undo.value = null
    }

    fun runUndo(action: MapUndo) {
        viewModelScope.launch { action.undo() }
    }
}
