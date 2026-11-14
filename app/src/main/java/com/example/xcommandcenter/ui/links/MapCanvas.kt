package com.example.xcommandcenter.ui.links

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.BranchLayout
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.MapStyle
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LineStyle
import com.example.xcommandcenter.ui.common.kindColor
import com.example.xcommandcenter.ui.common.kindIcon
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * How much each box shows, by zoom (§2.69): one-line titles when zoomed out, full titles at
 * normal size, and the explanation's first lines too when zoomed in.
 */
private enum class Detail(val label: String, val maxWidth: Dp, val titleLines: Int) {
    FAR("One-line titles", 128.dp, 1),
    NORMAL("Full titles", 150.dp, 3),
    CLOSE("Titles and explanations", 196.dp, 3),
    ;

    companion object {
        fun of(scale: Float) = when {
            scale < FAR_BELOW -> FAR
            scale >= CLOSE_FROM -> CLOSE
            else -> NORMAL
        }
    }
}

private const val FAR_BELOW = 0.7f
private const val CLOSE_FROM = 1.35f

/** The smallest zoom the map opens at: below it the text is too small to read. */
private const val READABLE = 0.75f
private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 3f

private val NodeMinWidth = 64.dp
private val NodePadStart = 7.dp
private val NodePadEnd = 8.dp
private val NodePadV = 6.dp
private val NodeIcon = 14.dp
private val NodeIconGap = 5.dp
private val SwapIcon = 12.dp
private val SwapGap = 2.dp
private val FoldSize = 20.dp
private val FoldGap = 4.dp
private val FoldPad = 4.dp
private val ExplainGap = 2.dp
private val Margin = 24.dp

/** Room at the top of the map for the zoom chip and buttons. */
private val ControlsHeight = 44.dp

/** At least this wide, the mind map's branches split left and right. */
private val WideScreen = 600.dp

private class NodeStyles(val title: TextStyle, val root: TextStyle, val explain: TextStyle, val fold: TextStyle)

/** What a box shows under its title when zoomed in, if anything. */
private fun explanationOf(node: MapNode, state: MapState, detail: Detail): String? =
    if (detail != Detail.CLOSE) null else state.explanations[node.ref]?.let { if (node.entry.type == ItemType.QUOTE) "— $it" else it }

private fun foldLabel(folded: Boolean, below: Int) = if (folded) "+" + (if (below > 99) "99+" else below.toString()) else "–"

/** How many items a box can fold away: none for the item in the middle. */
private fun foldable(node: MapNode, state: MapState) = if (node.ref == state.root?.ref) 0 else state.belowCount(node.ref)

/**
 * A box's size at [detail], in dp, from its text measured the way [NodeBox] draws it: as wide as
 * its title needs, between [NodeMinWidth] and the detail's widest, and as tall as its lines.
 */
private fun measureNode(node: MapNode, state: MapState, detail: Detail, measurer: TextMeasurer, styles: NodeStyles, density: Density): GraphLayout.Size = with(density) {
    val below = foldable(node, state)
    val fold = if (below > 0) {
        val label = measurer.measure(foldLabel(node.ref in state.folded, below), styles.fold, maxLines = 1)
        max(FoldSize.toPx(), label.size.width + FoldPad.toPx() * 2) + FoldGap.toPx()
    } else 0f
    val chrome = NodePadStart.toPx() + NodePadEnd.toPx() + NodeIcon.toPx() + NodeIconGap.toPx() +
        (if (node.otherMode) (SwapIcon + SwapGap).toPx() else 0f) + fold
    val textMax = (detail.maxWidth.toPx() - chrome).toInt().coerceAtLeast(1)
    val isRoot = node.ref == state.root?.ref
    val title = measurer.measure(
        node.entry.title, if (isRoot) styles.root else styles.title, TextOverflow.Ellipsis,
        maxLines = detail.titleLines, constraints = Constraints(maxWidth = textMax),
    )
    var textW = title.size.width.toFloat()
    var textH = title.size.height.toFloat()
    explanationOf(node, state, detail)?.let {
        val e = measurer.measure(it, styles.explain, TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = textMax))
        textW = max(textW, e.size.width.toFloat())
        textH += ExplainGap.toPx() + e.size.height
    }
    // A pixel to spare each way, so the drawn text never wraps differently from the measured.
    val w = max(NodeMinWidth.toPx(), chrome + textW + 1f)
    val h = NodePadV.toPx() * 2 + maxOf(textH, NodeIcon.toPx() + 1.dp.toPx(), if (below > 0) FoldSize.toPx() else 0f) + 1f
    GraphLayout.Size(w.toDp().value, h.toDp().value)
}

/** The boxes placed for one [state] at one [detail]: centres and sizes in dp, and how lines run. */
private class Placed(
    val state: MapState,
    val detail: Detail,
    val sizes: Map<ItemRef, GraphLayout.Size>,
    val layout: BranchLayout.Result,
) {
    val at: Map<ItemRef, GraphLayout.Point> get() = layout.at
    private val box = GraphLayout.bounds(at) { sizes.getValue(it) }
    val originX = box[0] - Margin.value
    val originY = box[1] - Margin.value
    val width = box[2] - box[0] + Margin.value * 2
    val height = box[3] - box[1] + Margin.value * 2

    /** Where links between branches bow away from: the item in the middle, or the picture's centre. */
    val centre: GraphLayout.Point = state.root?.ref?.let(at::get) ?: GraphLayout.Point((box[0] + box[2]) / 2, (box[1] + box[3]) / 2)
}

private fun place(state: MapState, sizes: Map<ItemRef, GraphLayout.Size>, detail: Detail, bothSides: Boolean, routeWidth: Float): Placed {
    val result = BranchLayout.place(
        roots = state.tree.roots.filter { it in state.byRef },
        children = state::shownChildren,
        size = { sizes.getValue(it) },
        chosen = state.chosen::get,
        top = state.top,
        bothSides = bothSides,
        maxWidth = routeWidth,
        layerOf = { state.layers[it] ?: 0 },
        edges = state.edges.map { it.link.a to it.link.b },
        offsets = state.offsets,
    )
    return Placed(state, detail, sizes, result)
}

/**
 * What a box's size depends on, so a size measured once is reused: switching layouts or moving
 * items measures nothing again.
 */
private data class MeasureKey(
    val title: String,
    val root: Boolean,
    val otherMode: Boolean,
    val fold: String?,
    val explanation: String?,
    val detail: Detail,
)

private fun measureKey(node: MapNode, state: MapState, detail: Detail) = MeasureKey(
    node.entry.title, node.ref == state.root?.ref, node.otherMode,
    foldable(node, state).takeIf { it > 0 }?.let { foldLabel(node.ref in state.folded, it) },
    explanationOf(node, state, detail), detail,
)

private enum class Fit { OPEN, ALL }

/** Everything shown below [ref] in the map's tree. */
private fun below(state: MapState, ref: ItemRef): Set<ItemRef> {
    val out = HashSet<ItemRef>()
    val stack = ArrayDeque(state.shownChildren(ref))
    while (stack.isNotEmpty()) {
        val r = stack.removeLast()
        if (out.add(r)) stack += state.shownChildren(r)
    }
    return out
}

/** What the map's picture does when items and lines are tapped, dragged or folded. */
internal class MapActions(
    val select: (MapSelection?) -> Unit,
    val tapNode: (ItemRef) -> Unit,
    val moveNode: (ItemRef, GraphLayout.Point) -> Unit,
    val toggleFold: (ItemRef) -> Unit,
)

/** A line's path, and points along it for its arrow, label and taps. */
private class EdgeShape(val path: Path, private val points: List<Offset>) {
    private val lengths = points.zipWithNext { a, b -> (b - a).getDistance() }.runningFold(0f) { acc, d -> acc + d }

    /** The point [t] (0 to 1) of the way along. */
    fun pointAt(t: Float): Offset {
        val total = lengths.last()
        if (total == 0f) return points.first()
        val want = t.coerceIn(0f, 1f) * total
        val i = lengths.indexOfFirst { it >= want }.coerceIn(1, points.size - 1)
        val span = lengths[i] - lengths[i - 1]
        val f = if (span == 0f) 0f else (want - lengths[i - 1]) / span
        return points[i - 1] + (points[i] - points[i - 1]) * f
    }

    fun distanceTo(p: Offset): Float = points.zipWithNext().minOf { (a, b) -> segmentDistance(p, a, b) }

    companion object {
        fun line(a: Offset, b: Offset) = EdgeShape(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }, listOf(a, b))

        fun polyline(points: List<Offset>) = EdgeShape(
            Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }, points,
        )

        fun quad(a: Offset, c: Offset, b: Offset) = EdgeShape(
            Path().apply { moveTo(a.x, a.y); quadraticTo(c.x, c.y, b.x, b.y) },
            (0..16).map { i -> val t = i / 16f; val u = 1 - t; a * (u * u) + c * (2 * u * t) + b * (t * t) },
        )

        fun cubic(a: Offset, c1: Offset, c2: Offset, b: Offset) = EdgeShape(
            Path().apply { moveTo(a.x, a.y); cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y) },
            (0..16).map { i ->
                val t = i / 16f
                val u = 1 - t
                a * (u * u * u) + c1 * (3 * u * u * t) + c2 * (3 * u * t * t) + b * (t * t * t)
            },
        )
    }
}

private fun segmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val t = if (len2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - Offset(a.x + ab.x * t, a.y + ab.y * t)).getDistance()
}

/**
 * How a line runs, in the content's pixels. A line of the map's tree runs as its parent's layout
 * says ([BranchLayout.Line]): side to side, bottom to top, down a list's line, down a channel
 * beside a tree's rows, or straight; a link
 * between two items of one By route branch follows its rows (bottom to top between rows, bowing
 * over a row within one); any other link bows outwards, away from the middle. Points run from a to
 * b, for the arrow.
 */
private fun shapeOf(edge: MapEdge, p: Placed, centreOf: (ItemRef) -> Offset, px: Float): EdgeShape {
    val a = edge.link.a
    val b = edge.link.b
    fun w(r: ItemRef) = p.sizes.getValue(r).w * px
    fun h(r: ItemRef) = p.sizes.getValue(r).h * px
    fun bow(x: ItemRef, y: ItemRef): EdgeShape {
        val pa = centreOf(x)
        val pb = centreOf(y)
        val mid = (pa + pb) / 2f
        val dist = (pb - pa).getDistance().coerceAtLeast(1f)
        var n = Offset(-(pb.y - pa.y) / dist, (pb.x - pa.x) / dist)
        val centre = Offset((p.centre.x - p.originX) * px, (p.centre.y - p.originY) * px)
        if (n.x * (mid.x - centre.x) + n.y * (mid.y - centre.y) < 0) n = -n
        return EdgeShape.quad(pa, mid + n * (min(100f * px, dist * 0.3f) + 24f * px), pb)
    }
    fun down(x: ItemRef, y: ItemRef): EdgeShape {
        val (up, low) = if (centreOf(x).y <= centreOf(y).y) x to y else y to x
        val pu = centreOf(up)
        val pd = centreOf(low)
        val y1 = pu.y + h(up) / 2
        val y2 = pd.y - h(low) / 2
        val my = (y1 + y2) / 2
        val shape = EdgeShape.cubic(Offset(pu.x, y1), Offset(pu.x, my), Offset(pd.x, my), Offset(pd.x, y2))
        return if (up == x) shape else reversed(shape)
    }
    fun route(x: ItemRef, y: ItemRef): EdgeShape {
        val px1 = centreOf(x)
        val py1 = centreOf(y)
        if (abs(px1.y - py1.y) >= min(h(x), h(y)) / 2) return down(x, y)
        // Within a row: over the top.
        val ta = Offset(px1.x, px1.y - h(x) / 2)
        val tb = Offset(py1.x, py1.y - h(y) / 2)
        return EdgeShape.quad(ta, Offset((ta.x + tb.x) / 2, min(ta.y, tb.y) - 40f * px), tb)
    }
    val tree = p.state.tree
    if (tree.joins(a, b)) {
        val (parent, child) = if (tree.parent[b] == a) a to b else b to a
        val pp = centreOf(parent)
        val pc = centreOf(child)
        val shape = when (p.layout.line[child]) {
            BranchLayout.Line.SIDE -> {
                val s = sign(pc.x - pp.x).takeIf { it != 0f } ?: 1f
                val x1 = pp.x + s * w(parent) / 2
                val x2 = pc.x - s * w(child) / 2
                val mx = (x1 + x2) / 2
                EdgeShape.cubic(Offset(x1, pp.y), Offset(mx, pp.y), Offset(mx, pc.y), Offset(x2, pc.y))
            }
            BranchLayout.Line.DOWN -> down(parent, child)
            BranchLayout.Line.ELBOW -> {
                // The list's line, as far in from the parent's left as the layout put it.
                val left = pp.x - w(parent) / 2
                val inset = p.layout.spine[child]?.let { it - (p.at.getValue(parent).x - p.sizes.getValue(parent).w / 2) } ?: GraphLayout.STACK_SPINE
                val sx = left + inset * px
                EdgeShape.polyline(listOf(Offset(sx, pp.y + h(parent) / 2), Offset(sx, pc.y), Offset(pc.x - w(child) / 2, pc.y)))
            }
            BranchLayout.Line.CHANNEL -> {
                // Positions from the layout, moved with any drag of the parent's branch.
                val c = p.layout.channel[child]
                if (c == null) down(parent, child) else {
                    val shift = pp - Offset((p.at.getValue(parent).x - p.originX) * px, (p.at.getValue(parent).y - p.originY) * px)
                    val cx = (c[0] - p.originX) * px + shift.x
                    val y0 = (c[1] - p.originY) * px + shift.y
                    val yi = (c[2] - p.originY) * px + shift.y
                    EdgeShape.polyline(
                        listOf(
                            Offset(pp.x, pp.y + h(parent) / 2), Offset(pp.x, y0), Offset(cx, y0), Offset(cx, yi),
                            Offset(pc.x, yi), Offset(pc.x, pc.y - h(child) / 2),
                        ),
                    )
                }
            }
            BranchLayout.Line.STRAIGHT -> EdgeShape.line(pp, pc)
            BranchLayout.Line.ROUTE -> route(parent, child)
            null -> bow(parent, child)
        }
        return if (parent == a) shape else reversed(shape)
    }
    val group = p.layout.routeGroup[a]
    return if (group != null && group == p.layout.routeGroup[b]) route(a, b) else bow(a, b)
}

/** The same line with its points running the other way (the path is unchanged). */
private fun reversed(shape: EdgeShape): EdgeShape {
    val pts = (0..16).map { shape.pointAt(1f - it / 16f) }
    return EdgeShape(shape.path, pts)
}

/**
 * The map's picture (§2.69, §2.70): boxes sized to their text, placed branch by branch by their
 * layouts, and lines between them. Pinch or use the buttons to zoom, drag to move, tap an item or a line to select it; the
 * boxes show more as you zoom in. It opens at a readable size, and Fit all shows everything.
 */
@Composable
internal fun MapCanvas(state: MapState, selection: MapSelection?, linkingFrom: ItemRef?, actions: MapActions) {
    val density = LocalDensity.current
    val px = density.density
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Read through derivedStateOf, so zooming recomposes only when the detail level changes.
    val detail by remember { derivedStateOf { Detail.of(scale) } }
    val typography = MaterialTheme.typography
    val styles = remember(typography) {
        NodeStyles(
            title = typography.labelMedium,
            root = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            explain = typography.labelSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
            fold = typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
        )
    }
    val widthDp = viewport.width / px
    val bothSides = widthDp >= WideScreen.value
    val routeWidth = ((widthDp - 16f) / READABLE).coerceAtLeast(320f)
    // Boxes are measured off the main thread, each once per text and detail level; then placed.
    val fonts = LocalFontFamilyResolver.current
    val direction = LocalLayoutDirection.current
    val measured = remember(styles, density, fonts) { ConcurrentHashMap<MeasureKey, GraphLayout.Size>() }
    val placed by produceState<Placed?>(null, state, detail, bothSides, routeWidth, measured) {
        if (viewport == IntSize.Zero) return@produceState
        value = withContext(Dispatchers.Default) {
            val measurer = TextMeasurer(fonts, density, direction, cacheSize = 0)
            val sizes = state.nodes.associate { n ->
                n.ref to measured.getOrPut(measureKey(n, state, detail)) { measureNode(n, state, detail, measurer, styles, density) }
            }
            place(state, sizes, detail, bothSides, routeWidth)
        }
    }
    // An item being dragged, and how far in content pixels; once let go it stays put (pending) until
    // the map comes back with its new place, so it doesn't jump back for a moment.
    var drag by remember { mutableStateOf<Pair<ItemRef, Offset>?>(null) }
    var pending by remember { mutableStateOf<Pair<ItemRef, Offset>?>(null) }
    LaunchedEffect(placed) { pending = null }

    // Open at a readable size when what is shown changes: another network, layout or set of items
    // (folding aside). Otherwise, when the boxes move (zooming changes what they show, folding, an
    // item dragged), the item nearest the middle of the screen stays where it is.
    var fit by remember { mutableStateOf<Fit?>(null) }
    var shown by remember { mutableStateOf<Placed?>(null) }
    val fitKey = listOf(viewport, state.root?.ref, state.top, state.viewKey, (state.nodes.map { it.ref } + state.foldHidden).toSet(), state.moved)
    var fitted by remember { mutableStateOf<List<Any?>?>(null) }
    LaunchedEffect(fitKey) {
        if (viewport == IntSize.Zero) return@LaunchedEffect
        // Same view, unless positions were just reset: then the picture opens again.
        val wasReset = fitted?.last() == true && !state.moved
        val sameView = fitted?.let { it.dropLast(1) == fitKey.dropLast(1) } == true && !wasReset
        if (!sameView) fit = Fit.OPEN
        fitted = fitKey
    }
    LaunchedEffect(placed, fit) {
        val p = placed ?: return@LaunchedEffect
        if (viewport == IntSize.Zero || p.state !== state) return@LaunchedEffect
        val vw = viewport.width.toFloat()
        val vh = viewport.height.toFloat()
        val top = ControlsHeight.value * px
        val cw = p.width * px
        val ch = p.height * px
        val fitScale = min(vw / cw, (vh - top) / ch)
        fun centre(s: Float) {
            scale = s
            offset = Offset((vw - cw * s) / 2, top + max(0f, (vh - top - ch * s) / 2))
        }
        when (fit) {
            Fit.OPEN -> {
                if (p.detail != Detail.NORMAL) { scale = 1f; return@LaunchedEffect }
                when {
                    fitScale >= READABLE -> centre(min(fitScale, 1.15f))
                    state.top == MapStyle.FREE || state.top == MapStyle.RINGS -> {
                        // Round the item in the middle: that item, at a readable size.
                        scale = 0.9f
                        val c = p.centre
                        offset = Offset(vw / 2 - (c.x - p.originX) * px * scale, top + (vh - top) / 2 - (c.y - p.originY) * px * scale)
                    }
                    else -> {
                        // As wide as the screen allows at a readable size, from the top: you scroll down.
                        scale = (vw / cw).coerceIn(READABLE, 1f)
                        offset = Offset(if (cw * scale <= vw) (vw - cw * scale) / 2 else 0f, top)
                    }
                }
                fit = null
            }
            Fit.ALL -> when {
                p.detail == Detail.CLOSE -> { scale = 1f; return@LaunchedEffect }
                p.detail == Detail.NORMAL && fitScale >= FAR_BELOW -> { centre(min(fitScale, 1.15f)); fit = null }
                // Too big at full titles: try again with one-line titles.
                p.detail == Detail.NORMAL -> { scale = 0.5f; return@LaunchedEffect }
                else -> { centre(min(fitScale, FAR_BELOW - 0.01f).coerceAtLeast(MIN_SCALE)); fit = null }
            }
            null -> {
                val old = shown
                if (old != null && old !== p) {
                    val cx = (vw / 2 - offset.x) / (px * scale) + old.originX
                    val cy = (vh / 2 - offset.y) / (px * scale) + old.originY
                    // Not an item just dragged (or below one): the rest of the picture stays still.
                    val dragged = p.state.nodes.map { it.ref }.filter { old.state.offsets[it] != p.state.offsets[it] }
                    val still = p.at.keys - (dragged + dragged.flatMap { below(p.state, it) }).toSet()
                    val anchor = still.filter { it in old.at }.minByOrNull { r ->
                        val o = old.at.getValue(r)
                        (o.x - cx) * (o.x - cx) + (o.y - cy) * (o.y - cy)
                    }
                    if (anchor != null) {
                        val o = old.at.getValue(anchor)
                        val n = p.at.getValue(anchor)
                        offset += Offset(((o.x - old.originX) - (n.x - p.originX)) * px * scale, ((o.y - old.originY) - (n.y - p.originY)) * px * scale)
                    }
                }
            }
        }
        shown = p
    }

    val colors = MaterialTheme.colorScheme
    val outline = colors.outline
    fun zoomBy(factor: Float, centroid: Offset) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        offset = centroid - (centroid - offset) * (newScale / scale)
        scale = newScale
    }
    val p = placed?.takeIf { it.state.nodes.isNotEmpty() }
    val highlighted: Set<ItemRef>? = if (p == null) null else when (selection) {
        is MapSelection.Node -> p.state.edges.filter { it.link.a == selection.ref || it.link.b == selection.ref }
            .flatMap { listOf(it.link.a, it.link.b) }.toSet() + selection.ref
        is MapSelection.Edge -> p.state.edges.firstOrNull { it.link.id == selection.linkId }?.let { setOf(it.link.a, it.link.b) }
        null -> null
    }
    // An item dragged moves with everything shown below it.
    val movingRef = (drag ?: pending)?.first
    val moving = remember(movingRef, p) { if (movingRef == null || p == null) emptySet() else below(p.state, movingRef) + movingRef }
    fun centreOf(ref: ItemRef): Offset {
        val c = p!!.at.getValue(ref)
        val moved = if (ref in moving) (drag ?: pending)?.second ?: Offset.Zero else Offset.Zero
        return Offset((c.x - p.originX) * px, (c.y - p.originY) * px) + moved
    }
    val edges = p?.state?.edges?.filter { it.link.a in p.at && it.link.b in p.at }.orEmpty()
    val shapes = remember(p, drag, pending) { if (p == null) emptyMap() else edges.associate { it.link.id to shapeOf(it, p, ::centreOf, px) } }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(colors.surfaceContainerLowest)
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // Zoom around the fingers.
                    zoomBy(zoom, centroid)
                    offset += pan
                }
            }
            .pointerInput(shapes) {
                detectTapGestures { tap ->
                    val at = (tap - offset) / scale
                    val near = 16 * px / scale
                    val hit = edges.minByOrNull { shapes.getValue(it.link.id).distanceTo(at) }
                        ?.takeIf { shapes.getValue(it.link.id).distanceTo(at) < near }
                    actions.select(hit?.let { MapSelection.Edge(it.link.id) })
                }
            },
    ) {
        if (p != null) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .size(p.width.dp, p.height.dp),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    edges.forEach { edge ->
                        val shape = shapes.getValue(edge.link.id)
                        val dim = highlighted != null && !(edge.link.a in highlighted && edge.link.b in highlighted)
                        val selected = selection == MapSelection.Edge(edge.link.id)
                        val color = when (edge.style) {
                            EdgeStyle.ROUTE, EdgeStyle.SHARED, EdgeStyle.UNDER -> outline
                            EdgeStyle.OFF_ROUTE -> OffRouteColor
                            EdgeStyle.SUGGEST_REMOVE -> RemoveColor
                            EdgeStyle.CONTRADICTS -> ContradictColor
                        }.copy(alpha = if (dim) 0.15f else 1f)
                        val width = when (edge.style) {
                            EdgeStyle.ROUTE, EdgeStyle.SHARED -> 1.5.dp.toPx()
                            EdgeStyle.CONTRADICTS -> 2.dp.toPx()
                            EdgeStyle.UNDER -> 3.dp.toPx()
                            else -> 2.5.dp.toPx()
                        }
                        // A line Tidy links suggests removing is always dashed; otherwise its kind's style decides.
                        val lineStyle = if (edge.style == EdgeStyle.SUGGEST_REMOVE) LineStyle.DASHED else edge.lineStyle
                        if (selected) drawPath(shape.path, color.copy(alpha = 0.2f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(
                            shape.path, color,
                            style = Stroke(width, cap = if (lineStyle == LineStyle.DOTTED) StrokeCap.Round else StrokeCap.Butt, pathEffect = dashEffect(lineStyle, 1.dp.toPx())),
                        )
                        // A label that reads one way gets an arrow from its subject, past the middle of the line.
                        if (edge.directed) {
                            val forward = edge.subject == edge.link.a
                            val t = if (forward) 0.68f else 0.32f
                            val at = shape.pointAt(t)
                            val ahead = shape.pointAt(if (forward) t + 0.02f else t - 0.02f)
                            val dir = (ahead - at).let { d -> val len = d.getDistance(); if (len == 0f) d else d / len }
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
                edges.filter { it.label != null }.forEach { edge ->
                    val mid = shapes.getValue(edge.link.id).pointAt(0.5f)
                    val dim = highlighted != null && !(edge.link.a in highlighted && edge.link.b in highlighted)
                    Box(
                        Modifier
                            .offset((mid.x / px).dp - LabelWidth / 2, (mid.y / px).dp - 8.dp)
                            .width(LabelWidth)
                            .alpha(if (dim) 0.2f else 1f),
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
                p.state.nodes.forEach { node ->
                    val size = p.sizes[node.ref] ?: return@forEach
                    val c = centreOf(node.ref)
                    val dim = highlighted != null && node.ref !in highlighted
                    val isSelected = selection == MapSelection.Node(node.ref) || node.ref == linkingFrom ||
                        (selection is MapSelection.Edge && highlighted?.contains(node.ref) == true)
                    NodeBox(
                        node, p.state, p.detail, size, styles,
                        selected = isSelected,
                        root = node.ref == p.state.root?.ref,
                        modifier = Modifier
                            .offset((c.x / px).dp - (size.w / 2).dp, (c.y / px).dp - (size.h / 2).dp)
                            .alpha(if (dim) 0.25f else 1f)
                            // Drag an item to move it, for viewing only; the place is kept for this view.
                            .pointerInput(node.ref, p) {
                                detectDragGestures(
                                    onDragStart = { drag = node.ref to Offset.Zero },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag = node.ref to ((drag?.second ?: Offset.Zero) + amount)
                                    },
                                    onDragEnd = {
                                        val moved = drag?.second ?: Offset.Zero
                                        pending = drag
                                        drag = null
                                        actions.moveNode(node.ref, GraphLayout.Point(moved.x / px, moved.y / px))
                                    },
                                    onDragCancel = { drag = null },
                                )
                            },
                        onClick = { actions.tapNode(node.ref) },
                        onFold = { actions.toggleFold(node.ref) },
                    )
                }
            }
        }
        // How much the boxes show at this zoom, and the zoom buttons.
        Surface(
            shape = RoundedCornerShape(50),
            color = colors.surfaceContainerHigh.copy(alpha = 0.92f),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Text(
                "${(scale * 100).roundToInt()}% · ${detail.label}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val middle = { Offset(viewport.width / 2f, (viewport.height + ControlsHeight.value * px) / 2f) }
            MapButton("−", "Zoom out") { zoomBy(1 / 1.35f, middle()) }
            MapButton("+", "Zoom in") { zoomBy(1.35f, middle()) }
            MapButton("Fit all", null) { fit = Fit.ALL }
        }
    }
}

private val LabelWidth = 96.dp

@Composable
private fun MapButton(label: String, description: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
    ) {
        Box(Modifier.heightIn(min = 32.dp).widthIn(min = 32.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * An item's box: its icon and title (as many lines as the zoom allows), its explanation's first
 * lines when zoomed in, and a button to fold what is below it. Its size comes from [measureNode].
 */
@Composable
private fun NodeBox(
    node: MapNode,
    state: MapState,
    detail: Detail,
    size: GraphLayout.Size,
    styles: NodeStyles,
    selected: Boolean,
    root: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onFold: () -> Unit,
) {
    // An idea in its kind's colour; an item of the other mode in green with ⇄, as an end point.
    val accent = node.kind?.let { kindColor(it) } ?: if (node.otherMode) OtherModeColor else tabAccent(node.entry.type)
    val colors = MaterialTheme.colorScheme
    val below = foldable(node, state)
    val folded = node.ref in state.folded
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else if (node.otherMode) OtherModeColor.copy(alpha = 0.08f) else colors.surface,
        border = BorderStroke(if (selected || root) 2.dp else 1.dp, if (selected || root) accent else accent.copy(alpha = 0.6f)),
        modifier = modifier
            .size(size.w.dp, size.h.dp)
            .semantics { contentDescription = "${node.entry.type.singular}: ${node.entry.title}" }
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = NodePadStart, end = NodePadEnd, top = NodePadV, bottom = NodePadV)) {
            if (node.otherMode) {
                Icon(painterResource(R.drawable.ic_swap), contentDescription = null, tint = OtherModeColor, modifier = Modifier.padding(top = 1.dp).size(SwapIcon))
                Spacer(Modifier.width(SwapGap))
            }
            Icon(
                painterResource(node.kind?.let { kindIcon(it) } ?: tabIcon(node.entry.type)), contentDescription = null, tint = accent,
                modifier = Modifier.padding(top = 1.dp).size(NodeIcon),
            )
            Spacer(Modifier.width(NodeIconGap))
            Column(Modifier.weight(1f)) {
                Text(node.entry.title, style = if (root) styles.root else styles.title, maxLines = detail.titleLines, overflow = TextOverflow.Ellipsis)
                explanationOf(node, state, detail)?.let {
                    Spacer(Modifier.height(ExplainGap))
                    Text(it, style = styles.explain, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (below > 0) {
                Spacer(Modifier.width(FoldGap))
                Box(
                    Modifier
                        .align(Alignment.CenterVertically)
                        .heightIn(min = FoldSize)
                        .widthIn(min = FoldSize)
                        .background(if (folded) accent else colors.surfaceContainerHighest, RoundedCornerShape(50))
                        .semantics { contentDescription = if (folded) "Unfold, $below items hidden" else "Fold the $below items below" }
                        .clickable(onClick = onFold)
                        .padding(horizontal = FoldPad),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(foldLabel(folded, below), style = styles.fold, color = if (folded) colors.surface else accent, maxLines = 1)
                }
            }
        }
    }
}
