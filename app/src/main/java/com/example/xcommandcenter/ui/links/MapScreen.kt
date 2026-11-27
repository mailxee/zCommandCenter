package com.example.xcommandcenter.ui.links

import com.example.xcommandcenter.domain.AppMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LineStyle
import com.example.xcommandcenter.domain.LineWidth
import com.example.xcommandcenter.domain.MapFilter
import com.example.xcommandcenter.domain.MapStyle
import com.example.xcommandcenter.data.repository.ItemDetail
import com.example.xcommandcenter.ui.common.LabelChoice
import com.example.xcommandcenter.ui.common.LinkLabelDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon
import kotlin.math.max

/** Amber for links off the suggested route; red for a link Tidy links suggests removing. */
internal val OffRouteColor = Color(0xFFE6A100)
internal val RemoveColor = Color(0xFFD32F2F)

/** A contradiction: purple and dotted, so it isn't taken for a suggested removal. */
internal val ContradictColor = Color(0xFF8E24AA)

/** Items of the other mode, which end this mode's networks: green, marked ⇄. */
internal val OtherModeColor = Color(0xFF2E7D32)

/**
 * The map of links (§2.57): items as boxes in their tab's colour, sized to their text, and lines
 * for links (§2.69, see [MapCanvas]). Pinch to zoom and drag to move; tap an item or a line to select it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    onTidy: (ItemRef?) -> Unit,
    viewModel: MapViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val root by viewModel.root.collectAsStateWithLifecycle()
    val lastRoot by viewModel.lastRoot.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val linkingFrom by viewModel.linkingFrom.collectAsStateWithLifecycle()
    val placingUnder by viewModel.placingUnder.collectAsStateWithLifecycle()
    val drop by viewModel.drop.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var showing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    // What the map's picture shows, for Save as PDF.
    var printView by remember { mutableStateOf<MapPrintView?>(null) }
    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        viewModel.undoShown()
        val result = snackbar.showSnackbar(action.message, actionLabel = if (action.undo != null) "Undo" else null, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.runUndo(action)
    }
    val s = state
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Map")
                        if (s != null) {
                            Text(
                                (if (s.mode == AppMode.THINK) "Think · " else "") +
                                    (s.root?.let { "Network of ${it.title}" } ?: "Every network") + " · ${s.nodes.size} items" +
                                    (if (s.hiddenCount > 0) " · ${s.hiddenCount} hidden" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
                actions = {
                    MapPdfButton(
                        view = printView?.takeIf { s != null && s.nodes.isNotEmpty() },
                        title = s?.root?.title ?: "Every network",
                        snackbar = snackbar,
                    )
                    IconButton(onClick = { showing = true }) {
                        Icon(
                            painterResource(R.drawable.ic_filter),
                            contentDescription = "Show on the map",
                            tint = if (s?.filter?.active == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val count = s?.suggestions ?: 0
                    TextButton(onClick = { onTidy(root) }) { Text(if (count > 0) "Tidy links · $count" else "Tidy links") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The whole map's layout; a branch can differ, from its item's panel.
                if (s != null) MapStylePicker("Layout", s.top, inherited = null, onPick = { viewModel.setStyle(root, it) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    val networkRoot = lastRoot
                    if (networkRoot != null) {
                        FilterChip(
                            selected = root != null,
                            onClick = { viewModel.showNetwork(root ?: networkRoot) },
                            label = { Text("This network", maxLines = 1) },
                        )
                    }
                    FilterChip(selected = root == null, onClick = viewModel::showEverything, label = { Text("Everything") })
                    // What the Show panel leaves out here (it applies to every map, so it's easy to forget).
                    val hiddenHere = s?.let { st ->
                        st.linkKinds.filter { !it.shown }.map { it.name } + st.typesPresent.filter { !st.filter.shows(it) }.map { it.tabLabel }
                    }.orEmpty()
                    if (hiddenHere.isNotEmpty()) {
                        androidx.compose.material3.AssistChip(
                            onClick = { showing = true },
                            label = { Text("Hidden: " + hiddenHere.joinToString(limit = 2, truncated = "+${hiddenHere.size - 2}"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_filter), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                        )
                    }
                    if (s?.moved == true) {
                        androidx.compose.material3.AssistChip(onClick = viewModel::resetPositions, label = { Text("Reset positions") })
                    }
                    val outline = MaterialTheme.colorScheme.outline
                    if (s?.mode == AppMode.THINK) {
                        if (s.edges.any { it.style == EdgeStyle.UNDER }) Legend(outline, s.filter.styleOf(MapFilter.UNDER), "Under", thick = true, width = s.filter.widthOf(MapFilter.UNDER))
                        Legend(outline, LineStyle.SOLID, "Link")
                        if (s.nodes.any { it.otherMode }) Legend(OtherModeColor, LineStyle.SOLID, "⇄ Do")
                    } else {
                        if (s?.edges?.any { it.style == EdgeStyle.UNDER } == true) Legend(outline, s.filter.styleOf(MapFilter.UNDER), "Under", thick = true, width = s.filter.widthOf(MapFilter.UNDER))
                        Legend(outline, LineStyle.SOLID, "On route")
                        Legend(OffRouteColor, LineStyle.SOLID, "Off route")
                        Legend(RemoveColor, LineStyle.DASHED, "Suggest removing")
                        if (s?.nodes?.any { it.otherMode } == true) Legend(OtherModeColor, LineStyle.SOLID, "⇄ Think")
                    }
                    // Kinds of link drawn other than solid, so their lines can be told apart.
                    s?.linkKinds?.filter { k ->
                        k.shown && k.key != MapFilter.UNDER && (k.style != LineStyle.SOLID || k.width != LineWidth.NORMAL) && s.edges.any { it.kind == k.key }
                    }?.forEach { k ->
                        Legend(if (k.contradicts) ContradictColor else outline, k.style, k.name.substringBefore(" / "), width = k.width)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
                when {
                    s == null -> Unit
                    s.nodes.isEmpty() -> Text(
                        "No links yet. Link items from their pages, and they show here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> MapCanvas(
                        s, selection, linkingFrom,
                        remember(viewModel) { MapActions(viewModel::select, viewModel::tapNode, viewModel::moveNode, viewModel::toggleFold, viewModel::dropOn) },
                        onView = { printView = it },
                    )
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                    val from = linkingFrom
                    val child = placingUnder
                    val dropped = drop
                    if (dropped != null && s != null) {
                        DropPanel(dropped, s, viewModel::dropped)
                    } else if (from != null && s != null) {
                        LinkingBanner("Tap the item to link to ${s.title(from)}", viewModel::cancelLinking)
                    } else if (child != null && s != null) {
                        LinkingBanner("Tap the item to put ${s.title(child)} under", viewModel::cancelLinking)
                    } else if (s != null) {
                        when (val sel = selection) {
                            is MapSelection.Node -> s.byRef[sel.ref]?.let { NodePanel(it, s, detail?.takeIf { d -> d.first == sel.ref }?.second, viewModel, onOpen) }
                            is MapSelection.Edge -> s.edges.firstOrNull { it.link.id == sel.linkId }?.let { EdgePanel(it, s, viewModel) }
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
    if (showing && s != null) ShowSheet(s, viewModel) { showing = false }
}

@Composable
private fun Legend(color: Color, style: LineStyle, label: String, thick: Boolean = false, width: LineWidth = LineWidth.NORMAL) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineSample(color, style, thick, Modifier.width(20.dp).height(10.dp), width)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun PanelHeader(label: String, title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun NodePanel(node: MapNode, state: MapState, detail: ItemDetail?, viewModel: MapViewModel, onOpen: (ItemRef) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                node.kind?.label ?: node.entry.type.singular,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
            IconButton(onClick = { viewModel.select(null) }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", modifier = Modifier.size(20.dp)) }
        }
        // The whole title and the explanation, scrolling when they run long.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(node.entry.title, style = MaterialTheme.typography.titleSmall)
            node.entry.subtitle?.takeIf { node.kind == null && node.entry.type != ItemType.QUOTE }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detail != null) {
                if (detail.explanation.isNotBlank()) {
                    Text(
                        if (node.entry.type == ItemType.QUOTE) "— ${detail.explanation}" else detail.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.list.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (detail.isEmpty) {
                    Text("No explanation.", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FilledTonalButton(onClick = { onOpen(node.ref) }) { Text("Open") }
            if (state.root?.ref != node.ref) TextButton(onClick = { viewModel.showNetwork(node.ref) }) { Text("Its network") }
            TextButton(onClick = { viewModel.startLinking(node.ref) }) { Text("Link to…") }
            // Ideas (Think) and plans (Do) can change what they are under, right here.
            if (node.ref.type == state.parentType) {
                TextButton(onClick = { viewModel.startPlacingUnder(node.ref) }) { Text("Move under…") }
                if (state.under[node.ref] != null) TextButton(onClick = { viewModel.putUnder(node.ref, null) }) { Text("Make top level") }
            }
        }
        // How this item's branch is laid out, when it has one (the map's own item uses Layout above).
        // Not inside a By route branch, which places everything in it by its rows.
        if (state.root?.ref != node.ref && state.belowCount(node.ref) > 0 && state.inheritedStyle(node.ref) != MapStyle.BY_ROUTE) {
            MapStylePicker(
                "Branch layout", state.chosen[node.ref],
                inherited = state.inheritedStyle(node.ref),
                onPick = { viewModel.setStyle(node.ref, it) },
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun EdgePanel(edge: MapEdge, state: MapState, viewModel: MapViewModel) {
    if (edge.style == EdgeStyle.UNDER) {
        Panel {
            PanelHeader("Under", "${state.title(edge.link.a)} is under ${state.title(edge.link.b)}") { viewModel.select(null) }
            Text(
                "To move it under another, drag it onto that item, or select it and use Move under….",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
            Row(Modifier.padding(bottom = 4.dp)) {
                TextButton(onClick = { viewModel.select(null); viewModel.putUnder(edge.link.a, null) }) { Text("Remove from under") }
            }
        }
        return
    }
    val finding = edge.finding
    val text = finding?.let { findingText(it, state.route, state::title) }
    var labelling by rememberSaveable(edge.link.id) { mutableStateOf(false) }
    if (labelling) {
        val labels by viewModel.labels.collectAsStateWithLifecycle()
        val stored = state.stored[edge.link.id]
        LinkLabelDialog(
            self = state.title(edge.link.a),
            other = state.title(edge.link.b),
            labels = labels,
            initial = stored?.labelId?.let { LabelChoice(it, stored.labelFromA) },
            definition = { "" },
            confirmLabel = "Save",
            onConfirm = { choice ->
                // The stored link's own ends: a chapter's link shows on its book's line.
                val a = stored?.let { ItemRef(it.aType, it.aId) } ?: edge.link.a
                val b = stored?.let { ItemRef(it.bType, it.bId) } ?: edge.link.b
                viewModel.setLabel(a, b, choice?.labelId, choice?.subject ?: true)
                labelling = false
            },
            onDismiss = { labelling = false },
        )
    }
    Panel {
        val title = if (edge.label != null) {
            val subject = edge.subject ?: edge.link.a
            val obj = if (subject == edge.link.a) edge.link.b else edge.link.a
            "${state.title(subject)} ${edge.label} ${state.title(obj)}"
        } else {
            "${state.title(edge.link.a)} — ${state.title(edge.link.b)}"
        }
        PanelHeader(text?.label ?: "Link", title) { viewModel.select(null) }
        if (text != null) {
            Text(text.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
            Text(text.suggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 10.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (finding?.add != null) {
                FilledTonalButton(onClick = { viewModel.apply(finding) }) { Text("Apply") }
                TextButton(onClick = { viewModel.removeLink(edge.link.id) }) { Text("Remove link") }
            } else {
                FilledTonalButton(onClick = { viewModel.removeLink(edge.link.id) }) { Text("Remove link") }
            }
            if (finding != null) TextButton(onClick = { viewModel.keep(finding) }) { Text("Keep as is") }
            if (state.stored[edge.link.id] != null) TextButton(onClick = { labelling = true }) { Text("Label…") }
        }
    }
}

@Composable
private fun LinkingBanner(text: String, onCancel: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** A box dropped on another: put it under that item, link the two, or just leave it there. */
@Composable
private fun DropPanel(drop: MapDrop, state: MapState, onChoose: (MapDrop.Choice) -> Unit) {
    Panel {
        PanelHeader("Dropped on ${state.title(drop.target)}", state.title(drop.item)) { onChoose(MapDrop.Choice.CANCEL) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            if (drop.canPutUnder) FilledTonalButton(onClick = { onChoose(MapDrop.Choice.UNDER) }) { Text("Put under it") }
            if (!drop.linked) TextButton(onClick = { onChoose(MapDrop.Choice.LINK) }) { Text("Link them") }
            TextButton(onClick = { onChoose(MapDrop.Choice.MOVE) }) { Text("Just move it") }
            TextButton(onClick = { onChoose(MapDrop.Choice.CANCEL) }) { Text("Cancel") }
        }
    }
}

/**
 * The map's Show panel: which tabs' items and which kinds of link are drawn, and how each kind
 * of link's line looks. Choices are kept for every map until changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowSheet(state: MapState, viewModel: MapViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show on the map", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (state.filter.active) TextButton(onClick = viewModel::showAll) { Text("Show all") }
                }
            }
            item { Text("Items", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.typesPresent.forEach { type ->
                        val shown = state.filter.shows(type)
                        FilterChip(
                            selected = shown,
                            onClick = { viewModel.setTypeShown(type, !shown) },
                            label = { Text(type.tabLabel) },
                            leadingIcon = { Icon(painterResource(tabIcon(type)), contentDescription = null, tint = tabAccent(type), modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (state.root != null) {
                item {
                    Text(
                        "${state.root.title} stays, whatever its tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("Links", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            if (state.linkKinds.isEmpty()) {
                item { Text("No links here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            state.linkKinds.forEach { kind ->
                item(key = kind.key) { LinkKindRow(kind, viewModel) }
            }
            item {
                Text(
                    "Items left with no line are hidden too. Lines Tidy links suggests removing stay dashed red.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun LinkKindRow(kind: LinkKindInfo, viewModel: MapViewModel) {
    val color = if (kind.contradicts) ContradictColor else MaterialTheme.colorScheme.outline
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = kind.shown, onCheckedChange = { viewModel.setLinkShown(kind.key, it) })
            Column(Modifier.weight(1f)) {
                Text(kind.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (kind.count == 1) "1 line" else "${kind.count} lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LineChoices(
            name = kind.name, color = color, thick = kind.key == MapFilter.UNDER, style = kind.style, width = kind.width,
            onStyle = { viewModel.setLineStyle(kind.key, it) }, onWidth = { viewModel.setLineWidth(kind.key, it) },
            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
        )
    }
}
