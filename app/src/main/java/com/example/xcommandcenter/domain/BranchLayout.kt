package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.GraphLayout.Point
import com.example.xcommandcenter.domain.GraphLayout.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Lays out the map's tree branch by branch (§2.70). Each item's sub-items are placed by its
 * [MapStyle] (chosen for it, or else its parent's), each sub-item bringing its own branch already
 * laid out as a block; so one map can hold a mind map whose branches are rings, a tree, a list….
 * By route is the exception: it places its whole branch in rows by the route, as one block.
 *
 * Sizes and places are in dp; the result gives each item's centre and how the line from its parent
 * runs. Dragged [offsets] then move an item with everything below it. Pure and deterministic.
 */
object BranchLayout {
    /** How the line from an item's parent to it runs, set by the parent's layout. */
    enum class Line {
        /** From the parent's side to the item's nearer side (mind map). */
        SIDE,

        /** From the parent's bottom to the item's top (tree). */
        DOWN,

        /** Down a line at the parent's left, then across to the item (a list, an outline). */
        ELBOW,

        /**
         * To a tree's row after the first: down from the parent to the space above the first row,
         * along it to a channel left of the rows, down that, and along the space above the item's row.
         */
        CHANNEL,

        /** Centre to centre (rings, free). */
        STRAIGHT,

        /** Inside a By route branch, which draws its lines by its rows. */
        ROUTE,
    }

    class Result(
        val at: Map<ItemRef, Point>,
        val line: Map<ItemRef, Line>,
        /** For an [Line.ELBOW] line (and a list stacked in a By route branch): the x of the line down. */
        val spine: Map<ItemRef, Float>,
        /** For a [Line.CHANNEL] line: the channel's x, the y above the first row, the y above the item's row. */
        val channel: Map<ItemRef, FloatArray>,
        /** Items inside a By route branch, by branch: links between two items of one follow its rows. */
        val routeGroup: Map<ItemRef, Int>,
        /** Each item's layout: how its own sub-items are placed. */
        val style: Map<ItemRef, MapStyle>,
    )

    private const val GAP_X = 20f
    private const val GAP_Y = 8f
    private const val GAP_BRANCH = 16f
    private const val ROW_GAP = 30f
    private const val INDENT = 18f
    private const val STACK_GAP = 6f

    /** A laid-out branch: each item's centre relative to the branch's item, and the bounds. */
    private class Block(val rel: HashMap<ItemRef, Point>, var l: Float, var t: Float, var r: Float, var b: Float) {
        val w get() = r - l
        val h get() = b - t
    }

    fun place(
        roots: List<ItemRef>,
        children: (ItemRef) -> List<ItemRef>,
        size: (ItemRef) -> Size,
        chosen: (ItemRef) -> MapStyle?,
        top: MapStyle,
        bothSides: Boolean,
        maxWidth: Float,
        layerOf: (ItemRef) -> Int,
        edges: List<Pair<ItemRef, ItemRef>>,
        offsets: Map<ItemRef, Point> = emptyMap(),
    ): Result {
        val line = HashMap<ItemRef, Line>()
        val spineRel = HashMap<ItemRef, Float>()
        val channelRel = HashMap<ItemRef, FloatArray>()
        val routeGroup = HashMap<ItemRef, Int>()
        val style = HashMap<ItemRef, MapStyle>()
        var groups = 0

        fun leaf(n: ItemRef): Block {
            val s = size(n)
            return Block(hashMapOf(n to Point(0f, 0f)), -s.w / 2, -s.h / 2, s.w / 2, s.h / 2)
        }

        fun Block.put(other: Block, dx: Float, dy: Float) {
            other.rel.forEach { (k, p) -> rel[k] = Point(p.x + dx, p.y + dy) }
            l = minOf(l, other.l + dx); t = minOf(t, other.t + dy); r = max(r, other.r + dx); b = max(b, other.b + dy)
        }

        fun subtree(n: ItemRef, out: MutableList<ItemRef>) { out += n; children(n).forEach { subtree(it, out) } }

        /** By route: [items] in rows, placed relative to [centre] (or their middle, for none). */
        fun route(items: List<ItemRef>, centre: ItemRef?): Block {
            val set = items.toSet()
            val r = GraphLayout.byRoute(items, edges.filter { (a, b) -> a in set && b in set }, layerOf, size, maxWidth)
            val group = groups++
            val origin = centre?.let { r.at.getValue(it) } ?: Point(0f, 0f)
            val block = Block(HashMap(), Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            items.forEach { n ->
                val p = r.at.getValue(n)
                val s = size(n)
                val q = Point(p.x - origin.x, p.y - origin.y)
                block.rel[n] = q
                block.l = minOf(block.l, q.x - s.w / 2); block.t = minOf(block.t, q.y - s.h / 2)
                block.r = max(block.r, q.x + s.w / 2); block.b = max(block.b, q.y + s.h / 2)
                routeGroup[n] = group
                style[n] = MapStyle.BY_ROUTE
                if (n != centre) line[n] = Line.ROUTE
            }
            r.stackedUnder.forEach { (item, parent) ->
                line[item] = Line.ELBOW
                spineRel[item] = r.spine.getValue(parent) - r.at.getValue(parent).x
            }
            return block
        }

        /**
         * Places [blocks] (the branches of [kids]) round [n] by [st], [n] at the origin. [n] is null for
         * the several trees of the Everything map, which have no item above them and no lines to it.
         */
        fun arrange(st: MapStyle, n: ItemRef?, kids: List<ItemRef>, blocks: List<Block>, isTop: Boolean): Block {
            val s = n?.let(size) ?: Size(0f, 0f)
            val out = if (n != null) leaf(n) else Block(HashMap(), 0f, 0f, 0f, 0f)
            if (kids.isEmpty()) return out
            val hasKids = kids.map { children(it).isNotEmpty() }
            fun lineTo(i: Int, kind: Line) { if (n != null) line[kids[i]] = kind }
            when (st) {
                MapStyle.MIND_MAP -> {
                    // Both sides on a wide screen, at the top: the first branches right until about half the height.
                    val right = if (isTop && bothSides && kids.size > 1) {
                        val total = blocks.sumOf { it.h.toDouble() }
                        var sum = 0.0
                        kids.indices.takeWhile { i -> (sum < total / 2).also { sum += blocks[i].h } }.ifEmpty { listOf(0) }.toSet()
                    } else kids.indices.toSet()
                    listOf(1f to kids.indices.filter { it in right }, -1f to kids.indices.filter { it !in right }).forEach { (side, idx) ->
                        if (idx.isEmpty()) return@forEach
                        val dys = FloatArray(idx.size)
                        var y = 0f
                        idx.forEachIndexed { j, i ->
                            if (j > 0) y += if (hasKids[i] || hasKids[idx[j - 1]]) GAP_BRANCH else GAP_Y
                            dys[j] = y - blocks[i].t
                            y += blocks[i].h
                        }
                        // Level with the middle of its column; at the top of a phone's map, with the first branch.
                        val shift = if (isTop && !bothSides) -dys[0] else -(dys.first() + dys.last()) / 2
                        idx.forEachIndexed { j, i ->
                            val bl = blocks[i]
                            val dx = if (side > 0) s.w / 2 + GAP_X - bl.l else -(s.w / 2 + GAP_X) - bl.r
                            out.put(bl, dx, dys[j] + shift)
                            lineTo(i, Line.SIDE)
                        }
                    }
                }
                MapStyle.TREE -> {
                    if (kids.size >= 2 && hasKids.none { it }) {
                        // A plain list stacks under its item.
                        var y = s.h / 2 + STACK_GAP
                        blocks.forEachIndexed { i, bl ->
                            out.put(bl, -s.w / 2 + INDENT - bl.l, y - bl.t)
                            y += bl.h + STACK_GAP
                            lineTo(i, Line.ELBOW)
                            if (n != null) spineRel[kids[i]] = -s.w / 2 + GraphLayout.STACK_SPINE
                        }
                    } else {
                        // Rows under the item, wrapping when wider than the screen allows.
                        val rows = mutableListOf(mutableListOf<Int>())
                        var rowW = 0f
                        blocks.forEachIndexed { i, bl ->
                            val row = rows.last()
                            val add = bl.w + if (row.isEmpty()) 0f else GAP_X
                            if (row.isNotEmpty() && rowW + add > maxWidth) { rows += mutableListOf(i); rowW = bl.w } else { row += i; rowW += add }
                        }
                        var y = s.h / 2 + ROW_GAP
                        val widest = rows.maxOf { row -> row.sumOf { blocks[it].w.toDouble() }.toFloat() + GAP_X * (row.size - 1) }
                        val firstGap = s.h / 2 + ROW_GAP / 2
                        rows.forEachIndexed { r, row ->
                            val width = row.sumOf { blocks[it].w.toDouble() }.toFloat() + GAP_X * (row.size - 1)
                            var x = -width / 2
                            row.forEach { i ->
                                val bl = blocks[i]
                                out.put(bl, x - bl.l, y - bl.t)
                                x += bl.w + GAP_X
                                // Later rows are reached down a channel left of the rows, not across the first.
                                if (r == 0) lineTo(i, Line.DOWN) else {
                                    lineTo(i, Line.CHANNEL)
                                    if (n != null) channelRel[kids[i]] = floatArrayOf(-widest / 2 - GAP_X / 2, firstGap, y - ROW_GAP / 2)
                                }
                            }
                            y += row.maxOf { blocks[it].h } + ROW_GAP
                        }
                    }
                }
                MapStyle.OUTLINE -> {
                    var y = s.h / 2 + GAP_Y
                    blocks.forEachIndexed { i, bl ->
                        out.put(bl, -s.w / 2 + INDENT - bl.l, y - bl.t)
                        y += bl.h + GAP_Y
                        lineTo(i, Line.ELBOW)
                        if (n != null) spineRel[kids[i]] = -s.w / 2 + GraphLayout.STACK_SPINE
                    }
                }
                MapStyle.RINGS, MapStyle.FREE -> {
                    // Each branch round the item, in a share of the circle by its size; far enough out to fit.
                    val reach = blocks.map { hypot(it.w, it.h) / 2 }
                    val need = reach.sumOf { (2 * it + GAP_X).toDouble() }.toFloat() * 1.05f
                    val inner = hypot(s.w, s.h) / 2
                    val radius = max(need / (2 * PI.toFloat()), inner + reach.max() + GAP_X)
                    val cx = FloatArray(kids.size)
                    val cy = FloatArray(kids.size)
                    var a = if (kids.size == 1) 0f else -PI.toFloat() / 2
                    kids.indices.forEach { i ->
                        val share = 2 * PI.toFloat() * (2 * reach[i] + GAP_X) / (need / 1.05f)
                        val mid = if (kids.size == 1) 0f else a + share / 2
                        // Rings: an even circle. Free: each branch only as far out as it needs.
                        val d = if (st == MapStyle.RINGS) radius else inner + reach[i] + GAP_X * 2
                        cx[i] = d * cos(mid)
                        cy[i] = d * sin(mid)
                        a += share
                    }
                    if (st == MapStyle.FREE) spread(cx, cy, reach, inner)
                    blocks.forEachIndexed { i, bl ->
                        out.put(bl, cx[i] - (bl.l + bl.r) / 2, cy[i] - (bl.t + bl.b) / 2)
                        lineTo(i, Line.STRAIGHT)
                    }
                }
                MapStyle.BY_ROUTE -> error("By route places its whole branch")
            }
            return out
        }

        fun block(n: ItemRef, inherited: MapStyle, isTop: Boolean): Block {
            val st = chosen(n) ?: inherited
            if (st == MapStyle.BY_ROUTE) return route(mutableListOf<ItemRef>().also { subtree(n, it) }, n)
            style[n] = st
            val kids = children(n)
            return arrange(st, n, kids, kids.map { block(it, st, false) }, isTop)
        }

        val whole = when {
            roots.isEmpty() -> Block(HashMap(), 0f, 0f, 0f, 0f)
            roots.size == 1 -> block(roots[0], top, true)
            top == MapStyle.BY_ROUTE && roots.none { chosen(it) != null } ->
                route(mutableListOf<ItemRef>().also { list -> roots.forEach { subtree(it, list) } }, null)
            else -> arrange(top, null, roots, roots.map { block(it, top, false) }, true)
        }

        // Dragged offsets move an item and everything below it.
        val at = HashMap<ItemRef, Point>()
        val spine = HashMap<ItemRef, Float>()
        val channel = HashMap<ItemRef, FloatArray>()
        fun walk(n: ItemRef, parent: ItemRef?, acc: Point) {
            val own = offsets[n]
            val total = if (own == null) acc else Point(acc.x + own.x, acc.y + own.y)
            val p = whole.rel[n] ?: return
            at[n] = Point(p.x + total.x, p.y + total.y)
            if (parent != null) {
                val rel = spineRel[n]
                val pa = at.getValue(parent)
                if (own != null && (line[n] == Line.ELBOW || line[n] == Line.CHANNEL)) line[n] = if (routeGroup[n] != null) Line.ROUTE else Line.DOWN
                else if (rel != null) spine[n] = pa.x + rel
                else channelRel[n]?.let { c -> channel[n] = floatArrayOf(pa.x + c[0], pa.y + c[1], pa.y + c[2]) }
            }
            children(n).forEach { walk(it, n, total) }
        }
        roots.forEach { walk(it, null, Point(0f, 0f)) }
        return Result(at, line, spine, channel, routeGroup, style)
    }

    /**
     * Free: pushes branches (circles of [reach] round [x], [y]) apart from each other and from the
     * item in the middle (a circle of [inner]), while each is drawn back towards the middle.
     */
    private fun spread(x: FloatArray, y: FloatArray, reach: List<Float>, inner: Float) {
        val n = x.size
        repeat(80) {
            for (i in 0 until n) {
                // Back towards the middle, to just clear it.
                val d = hypot(x[i], y[i]).coerceAtLeast(1f)
                val want = inner + reach[i] + GAP_X
                val pull = (d - want) * 0.1f
                x[i] -= x[i] / d * pull
                y[i] -= y[i] / d * pull
            }
            for (i in 0 until n) for (j in i + 1 until n) {
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val d = hypot(dx, dy).coerceAtLeast(0.5f)
                val overlap = reach[i] + reach[j] + GAP_Y - d
                if (overlap > 0) {
                    val ux = if (d < 1f) 1f else dx / d
                    val uy = if (d < 1f) 0f else dy / d
                    x[i] -= ux * overlap / 2; y[i] -= uy * overlap / 2
                    x[j] += ux * overlap / 2; y[j] += uy * overlap / 2
                }
            }
        }
        // None left over the item in the middle.
        for (i in 0 until n) {
            val d = hypot(x[i], y[i]).coerceAtLeast(0.01f)
            val least = inner + reach[i] + GAP_Y
            if (d < least) { x[i] = x[i] / d * least; y[i] = y[i] / d * least }
        }
    }
}
