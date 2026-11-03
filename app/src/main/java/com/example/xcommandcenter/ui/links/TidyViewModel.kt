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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TidyState(
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

/** Tidy links (§2.57): the findings of [LinkAnalysis] within one network, or all of them, with their fixes. */
class TidyViewModel(savedState: SavedStateHandle, private val links: LinkRepository) : ViewModel() {
    private val root = Routes.parseRef(savedState.get<String>(Routes.ARG_ROOT))

    val undo = MutableStateFlow<MapUndo?>(null)

    val state: StateFlow<TidyState?> = links.observeGraph().map { graph ->
        val shown = if (root == null) null else graph.nodes(root)
        fun inScope(f: LinkAnalysis.Finding) = shown == null || f.items.any { it in shown }
        val findings = graph.analysis.findings.filter(::inScope)
        val kept = graph.analysis.kept.filter(::inScope)
        TidyState(
            root = root?.let { graph.entries[it] },
            findings = findings,
            kept = kept,
            extraRoutes = (findings + kept).count { it.kind in LinkAnalysis.loopKinds },
            route = graph.route,
            entries = graph.entries,
            stored = graph.stored,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The finding's main fix: remove its link, or for a skipped layer link through the suggested item first. */
    fun fix(finding: LinkAnalysis.Finding) {
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
