package com.example.xcommandcenter.ui.links

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Amber for links off the suggested route; red for a link Tidy links suggests removing. */
internal val OffRouteColor = Color(0xFFE6A100)
internal val RemoveColor = Color(0xFFD32F2F)

/** A contradiction: purple and dotted, so it isn't taken for a suggested removal. */
internal val ContradictColor = Color(0xFF8E24AA)

private val ColumnWidth = 150.dp
private val RowHeight = 96.dp
private val NodeWidth = 132.dp
private val NodeHeight = 32.dp
private val Margin = 24.dp

/**
 * The map of links (§2.57): items as labelled pills in their tab's colour, lines for links. Pinch
 * to zoom and drag to move; tap an item or a line to select it.
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
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val linkingFrom by viewModel.linkingFrom.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        viewModel.undoShown()
        val result = snackbar.showSnackbar(action.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
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
                                (s.root?.let { "Network of ${it.title}" } ?: "Every network") + " · ${s.nodes.size} items",
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
                    val count = s?.suggestions ?: 0
                    TextButton(onClick = { onTidy(root) }) { Text(if (count > 0) "Tidy links · $count" else "Tidy links") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MapViewModel.Layout.entries.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = layout == option,
                            onClick = { viewModel.setLayout(option) },
                            shape = SegmentedButtonDefaults.itemShape(i, MapViewModel.Layout.entries.size),
                        ) { Text(if (option == MapViewModel.Layout.BY_ROUTE) "By route" else "Free layout", maxLines = 1) }
                    }
                }
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
                    Legend(MaterialTheme.colorScheme.outline, false, "On route")
                    Legend(OffRouteColor, false, "Off route")
                    Legend(RemoveColor, true, "Suggest removing")
                    if (s?.edges?.any { it.style == EdgeStyle.CONTRADICTS } == true) Legend(ContradictColor, true, "Contradicts")
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
                    else -> MapCanvas(s, layout, selection, linkingFrom, viewModel)
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                    val from = linkingFrom
                    if (from != null && s != null) {
                        LinkingBanner(s.title(from), viewModel::cancelLinking)
                    } else if (s != null) {
                        when (val sel = selection) {
                            is MapSelection.Node -> s.byRef[sel.ref]?.let { NodePanel(it, s, viewModel, onOpen) }
                            is MapSelection.Edge -> s.edges.firstOrNull { it.link.id == sel.linkId }?.let { EdgePanel(it, s, viewModel) }
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(20.dp).height(10.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where an item's pill is centred, in the content's own pixels. */
private class Geometry(state: MapState, private val density: Float, private val curved: Boolean) {
    private val col = ColumnWidth.value * density
    private val row = RowHeight.value * density
    private val margin = (Margin.value + NodeWidth.value / 2) * density
    private val minX = state.nodes.minOf { it.x }
    private val minY = state.nodes.minOf { it.y }
    val width = (state.nodes.maxOf { it.x } - minX) * col + margin * 2
    val height = (state.nodes.maxOf { it.y } - minY) * row + (Margin.value * 2 + NodeHeight.value) * density

    fun center(n: MapNode) = Offset(margin + (n.x - minX) * col, (Margin.value + NodeHeight.value / 2) * density + (n.y - minY) * row)

    private val middle = width / 2

    /**
     * By route, lines within a row bow upwards, and lines that jump a row bow outwards, so neither
     * runs under the items between and seems to link them. Null for a straight line (free layout).
     */
    fun control(a: Offset, b: Offset): Offset? {
        if (!curved) return null
        val dy = kotlin.math.abs(a.y - b.y)
        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        return when {
            dy < row / 3 -> Offset(mid.x, min(a.y, b.y) - row * 0.45f)
            dy > row * 1.5f -> Offset(mid.x + (if (mid.x >= middle) 1 else -1) * col * 0.55f, mid.y)
            else -> null
        }
    }
}

@Composable
private fun MapCanvas(state: MapState, layout: MapViewModel.Layout, selection: MapSelection?, linkingFrom: ItemRef?, viewModel: MapViewModel) {
    val density = LocalDensity.current.density
    val geometry = remember(state, density, layout) { Geometry(state, density, curved = layout == MapViewModel.Layout.BY_ROUTE) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Fit the picture to the screen whenever what is shown changes shape.
    LaunchedEffect(viewport, state.root, layout, geometry.width, geometry.height) {
        if (viewport == IntSize.Zero) return@LaunchedEffect
        val fit = min(viewport.width / geometry.width, viewport.height / geometry.height)
        scale = fit.coerceIn(0.2f, 1.2f)
        offset = Offset(
            max(0f, (viewport.width - geometry.width * scale) / 2),
            max(0f, (viewport.height - geometry.height * scale) / 2).coerceAtMost(24f * density),
        )
    }
    val highlighted: Set<ItemRef>? = when (selection) {
        is MapSelection.Node -> state.edges.filter { it.link.a == selection.ref || it.link.b == selection.ref }
            .flatMap { listOf(it.link.a, it.link.b) }.toSet() + selection.ref
        is MapSelection.Edge -> state.edges.firstOrNull { it.link.id == selection.linkId }?.let { setOf(it.link.a, it.link.b) }
        null -> null
    }
    val colors = MaterialTheme.colorScheme
    val outline = colors.outline
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(colors.surfaceContainerLowest)
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.2f, 3f)
                    // Zoom around the fingers.
                    offset = centroid - (centroid - offset) * (newScale / scale) + pan
                    scale = newScale
                }
            }
            .pointerInput(state) {
                detectTapGestures { tap ->
                    val at = (tap - offset) / scale
                    val near = 16 * density / scale
                    val hit = state.edges.minByOrNull { edge -> distance(at, geometry, state, edge) }
                        ?.takeIf { distance(at, geometry, state, it) < near }
                    viewModel.select(hit?.let { MapSelection.Edge(it.link.id) })
                }
            },
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .size((geometry.width / density).dp, (geometry.height / density).dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                state.edges.forEach { edge ->
                    val a = state.byRef[edge.link.a]?.let(geometry::center) ?: return@forEach
                    val b = state.byRef[edge.link.b]?.let(geometry::center) ?: return@forEach
                    val dim = highlighted != null && !(edge.link.a in highlighted && edge.link.b in highlighted)
                    val selected = selection == MapSelection.Edge(edge.link.id)
                    val color = when (edge.style) {
                        EdgeStyle.ROUTE, EdgeStyle.SHARED -> outline
                        EdgeStyle.OFF_ROUTE -> OffRouteColor
                        EdgeStyle.SUGGEST_REMOVE -> RemoveColor
                        EdgeStyle.CONTRADICTS -> ContradictColor
                    }.copy(alpha = if (dim) 0.25f else 1f)
                    val width = when (edge.style) {
                        EdgeStyle.ROUTE, EdgeStyle.SHARED -> 1.5.dp.toPx()
                        EdgeStyle.CONTRADICTS -> 2.dp.toPx()
                        else -> 2.5.dp.toPx()
                    }
                    val dash = when (edge.style) {
                        EdgeStyle.SUGGEST_REMOVE -> PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
                        EdgeStyle.CONTRADICTS -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                        EdgeStyle.SHARED -> PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                        else -> null
                    }
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        val c = geometry.control(a, b)
                        if (c != null) quadraticTo(c.x, c.y, b.x, b.y) else lineTo(b.x, b.y)
                    }
                    if (selected) drawPath(path, color.copy(alpha = 0.2f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path, color, style = Stroke(width, pathEffect = dash))
                    // A label that reads one way gets an arrow from its subject, past the middle of the line.
                    if (edge.directed) {
                        val (from, to) = if (edge.subject == edge.link.a) a to b else b to a
                        val c = geometry.control(a, b)
                        val t = 0.68f
                        val at = curvePoint(from, c, to, t)
                        val ahead = curvePoint(from, c, to, t + 0.02f)
                        val dir = (ahead - at).let { d -> val len = sqrt(d.x * d.x + d.y * d.y); if (len == 0f) d else d / len }
                        val size = 6.dp.toPx()
                        val side = Offset(-dir.y, dir.x)
                        val arrow = Path().apply {
                            moveTo(at.x + dir.x * size, at.y + dir.y * size)
                            lineTo(at.x - dir.x * size + side.x * size * 0.7f, at.y - dir.y * size + side.y * size * 0.7f)
                            lineTo(at.x - dir.x * size - side.x * size * 0.7f, at.y - dir.y * size - side.y * size * 0.7f)
                            close()
                        }
                        drawPath(arrow, color)
                    }
                }
            }
            // Labels sit on the middle of their lines.
            state.edges.filter { it.label != null }.forEach { edge ->
                val a = state.byRef[edge.link.a]?.let(geometry::center) ?: return@forEach
                val b = state.byRef[edge.link.b]?.let(geometry::center) ?: return@forEach
                val mid = curvePoint(a, geometry.control(a, b), b, 0.5f)
                val dim = highlighted != null && !(edge.link.a in highlighted && edge.link.b in highlighted)
                Box(
                    Modifier
                        .offset((mid.x / density).dp - LabelWidth / 2, (mid.y / density).dp - 8.dp)
                        .width(LabelWidth)
                        .alpha(if (dim) 0.3f else 1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        edge.label.orEmpty(),
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (edge.style == EdgeStyle.CONTRADICTS) ContradictColor else colors.onSurfaceVariant,
                        modifier = Modifier.background(colors.surfaceContainerLowest, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
                    )
                }
            }
            state.nodes.forEach { node ->
                val c = geometry.center(node)
                val dim = highlighted != null && node.ref !in highlighted
                val isSelected = selection == MapSelection.Node(node.ref) || node.ref == linkingFrom ||
                    (selection is MapSelection.Edge && highlighted?.contains(node.ref) == true)
                NodePill(
                    node,
                    selected = isSelected,
                    root = node.ref == state.root?.ref,
                    modifier = Modifier
                        .offset((c.x / density).dp - NodeWidth / 2, (c.y / density).dp - NodeHeight / 2)
                        .alpha(if (dim) 0.35f else 1f),
                    onClick = { viewModel.tapNode(node.ref) },
                )
            }
        }
    }
}

private val LabelWidth = 96.dp

/** The point at [t] (0 to 1) along a line from [a] to [b], or along the curve through control point [c]. */
private fun curvePoint(a: Offset, c: Offset?, b: Offset, t: Float): Offset {
    if (c == null) return a + (b - a) * t
    val u = 1 - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

/** How far [at] is from an edge's line (or curve), in content pixels. */
private fun distance(at: Offset, geometry: Geometry, state: MapState, edge: MapEdge): Float {
    val a = state.byRef[edge.link.a]?.let(geometry::center) ?: return Float.MAX_VALUE
    val b = state.byRef[edge.link.b]?.let(geometry::center) ?: return Float.MAX_VALUE
    val c = geometry.control(a, b)
    val points = if (c == null) listOf(a, b) else (0..12).map { i ->
        val t = i / 12f
        val u = 1 - t
        Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
    }
    return points.zipWithNext().minOf { (p, q) -> segmentDistance(at, p, q) }
}

private fun segmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val t = if (len2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    val q = Offset(a.x + ab.x * t, a.y + ab.y * t)
    val d = p - q
    return sqrt(d.x * d.x + d.y * d.y)
}

@Composable
private fun NodePill(node: MapNode, selected: Boolean, root: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = tabAccent(node.entry.type)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(NodeHeight / 2),
        color = if (selected) accent.copy(alpha = 0.22f) else colors.surface,
        border = BorderStroke(if (selected || root) 2.dp else 1.dp, accent),
        modifier = modifier
            .width(NodeWidth)
            .height(NodeHeight)
            .semantics { contentDescription = "${node.entry.type.singular}: ${node.entry.title}" }
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
            Icon(painterResource(tabIcon(node.entry.type)), contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(node.entry.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
private fun NodePanel(node: MapNode, state: MapState, viewModel: MapViewModel, onOpen: (ItemRef) -> Unit) {
    Panel {
        PanelHeader(node.entry.type.singular, node.entry.title) { viewModel.select(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FilledTonalButton(onClick = { onOpen(node.ref) }) { Text("Open") }
            if (state.root?.ref != node.ref) TextButton(onClick = { viewModel.showNetwork(node.ref) }) { Text("Its network") }
            TextButton(onClick = { viewModel.startLinking(node.ref) }) { Text("Link to…") }
        }
    }
}

@Composable
private fun EdgePanel(edge: MapEdge, state: MapState, viewModel: MapViewModel) {
    val finding = edge.finding
    val text = finding?.let { findingText(it, state.route, state::title) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (finding?.add != null) {
                FilledTonalButton(onClick = { viewModel.apply(finding) }) { Text("Apply") }
                TextButton(onClick = { viewModel.removeLink(edge.link.id) }) { Text("Remove link") }
            } else {
                FilledTonalButton(onClick = { viewModel.removeLink(edge.link.id) }) { Text("Remove link") }
            }
            if (finding != null) TextButton(onClick = { viewModel.keep(finding) }) { Text("Keep as is") }
        }
    }
}

@Composable
private fun LinkingBanner(title: String, onCancel: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tap the item to link to $title", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
