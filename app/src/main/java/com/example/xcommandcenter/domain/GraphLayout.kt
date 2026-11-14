package com.example.xcommandcenter.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Places a network's items for the map (§2.69). Each item is a box of its own [Size], in dp, and
 * the layouts return each box's centre, also in dp; the screen scales them. Pure and
 * deterministic, so the same links always draw the same picture.
 */
object GraphLayout {
    data class Point(val x: Float, val y: Float)

    /** A box's width and height, in dp. */
    data class Size(val w: Float, val h: Float)

    /**
     * One grid unit of a dragged position (see [MapPositions]) in dp. Positions are stored in the
     * grid of the earlier layouts, so ones saved before keep their place.
     */
    const val GRID_X = 150f
    const val GRID_Y = 96f

    /** Space between boxes side by side, between boxes stacked, between branches and between trees. */
    private const val GAP_X = 20f
    private const val GAP_Y = 8f
    private const val GAP_BRANCH = 16f
    private const val GAP_TREE = 32f

    /** By route: between rows, how far a stacked list is indented, and the space between its items. */
    private const val ROW_GAP = 30f
    private const val STACK_INDENT = 16f
    private const val STACK_GAP = 6f

    /** How far from its box's left edge a stacked list's line runs down. */
    const val STACK_SPINE = 8f

    /**
     * The map's tree: each item under the one it was first reached from, going out from [roots]
     * along the links in order (so put the tree's own links, such as Under, first). Folding an item
     * hides everything below it here.
     */
    data class Tree(val roots: List<ItemRef>, val parent: Map<ItemRef, ItemRef>, val children: Map<ItemRef, List<ItemRef>>) {
        fun descendants(n: ItemRef): Set<ItemRef> {
            val out = LinkedHashSet<ItemRef>()
            val stack = ArrayDeque(children[n].orEmpty())
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                if (out.add(c)) stack += children[c].orEmpty()
            }
            return out
        }

        /** Whether the line between [a] and [b] is one of the tree's own (one is under the other). */
        fun joins(a: ItemRef, b: ItemRef): Boolean = parent[a] == b || parent[b] == a
    }

    /**
     * The tree through [nodes] from [roots] (in [nodes]; with none, the first item). Each item
     * hangs from the item it is reached from in the fewest links, where a [firm] link (Under) costs
     * nothing: so an idea sits under the idea it is under even when it is also linked to the root.
     * An item not reached starts a tree of its own, in [nodes] order.
     */
    fun tree(
        roots: List<ItemRef>,
        nodes: List<ItemRef>,
        edges: List<Pair<ItemRef, ItemRef>>,
        firm: Collection<Pair<ItemRef, ItemRef>> = emptyList(),
    ): Tree {
        val list = nodes.distinct()
        val inGraph = list.toSet()
        // Each neighbour once, at the lowest cost of the links to it.
        val neighbours = LinkedHashMap<ItemRef, LinkedHashMap<ItemRef, Int>>()
        fun add(a: ItemRef, b: ItemRef, cost: Int) {
            if (a !in inGraph || b !in inGraph || a == b) return
            neighbours.getOrPut(a) { LinkedHashMap() }.merge(b, cost, ::minOf)
            neighbours.getOrPut(b) { LinkedHashMap() }.merge(a, cost, ::minOf)
        }
        firm.forEach { (a, b) -> add(a, b, 0) }
        edges.forEach { (a, b) -> add(a, b, 1) }
        val parent = HashMap<ItemRef, ItemRef>()
        val cost = HashMap<ItemRef, Int>()
        val done = HashSet<ItemRef>()
        val order = mutableListOf<ItemRef>()
        val tops = mutableListOf<ItemRef>()
        fun search(from: List<ItemRef>) {
            // Lowest cost first: firm links go to the front of the queue, others to the back.
            val queue = ArrayDeque<ItemRef>()
            from.forEach { if (it !in cost) { cost[it] = 0; queue += it; tops += it } }
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                if (!done.add(at)) continue
                order += at
                val firmNext = mutableListOf<ItemRef>()
                neighbours[at].orEmpty().forEach { (next, step) ->
                    val c = cost.getValue(at) + step
                    if (next !in done && c < (cost[next] ?: Int.MAX_VALUE)) {
                        cost[next] = c
                        parent[next] = at
                        if (step == 0) firmNext += next else queue.addLast(next)
                    }
                }
                // To the front in reverse, so they come out in their own order.
                firmNext.asReversed().forEach { queue.addFirst(it) }
            }
        }
        search(roots.filter { it in inGraph }.distinct().ifEmpty { list.take(1) })
        list.forEach { if (it !in done) search(listOf(it)) }
        val children = order.filter { it in parent }.groupBy { parent.getValue(it) }
        return Tree(tops, parent, children)
    }

    /**
     * Mind map: each tree's root with its branches beside it, and every item's sub-items stacked in
     * a column next to it, the item level with the middle of its column. With [bothSides] the
     * branches split left and right (for a wide screen); otherwise they all go right and the root
     * sits level with its first branch, so the map reads down like an outline (for a phone held
     * upright). Several trees stack one under another. [children] gives what is shown under an
     * item (nothing for a folded one).
     */
    fun mindMap(roots: List<ItemRef>, children: (ItemRef) -> List<ItemRef>, size: (ItemRef) -> Size, bothSides: Boolean): Map<ItemRef, Point> {
        val out = HashMap<ItemRef, Point>()
        var top = 0f
        roots.forEach { root ->
            val tree = HashMap<ItemRef, Point>()
            placeTree(root, children, size, bothSides, tree)
            val minY = tree.entries.minOf { it.value.y - size(it.key).h / 2 }
            val maxY = tree.entries.maxOf { it.value.y + size(it.key).h / 2 }
            tree.forEach { (ref, p) -> out[ref] = Point(p.x, p.y - minY + top) }
            top += maxY - minY + GAP_TREE
        }
        return out
    }

    private fun placeTree(root: ItemRef, children: (ItemRef) -> List<ItemRef>, size: (ItemRef) -> Size, bothSides: Boolean, out: MutableMap<ItemRef, Point>) {
        val kids = children(root)
        val leaves = HashMap<ItemRef, Int>()
        fun countLeaves(n: ItemRef): Int = leaves.getOrPut(n) { children(n).sumOf { countLeaves(it) }.coerceAtLeast(1) }
        // Both sides: the first branches go right until they hold about half the items at the ends.
        val right = if (!bothSides || kids.size < 2) kids else {
            val total = kids.sumOf { countLeaves(it) }
            var sum = 0
            kids.takeWhile { k -> (sum < total / 2f).also { sum += countLeaves(k) } }.ifEmpty { kids.take(1) }
        }
        val left = kids - right.toSet()
        var rootY = 0f
        listOf(1 to right, -1 to left).forEach { (side, tops) ->
            if (tops.isEmpty()) return@forEach
            val colW = ArrayList<Float>()
            fun widths(n: ItemRef, d: Int) {
                while (colW.size <= d) colW += 0f
                colW[d] = max(colW[d], size(n).w)
                children(n).forEach { widths(it, d + 1) }
            }
            tops.forEach { widths(it, 1) }
            val colX = FloatArray(colW.size)
            var x = size(root).w / 2 + GAP_X
            for (d in 1 until colW.size) { colX[d] = x; x += colW[d] + GAP_X }
            val heights = HashMap<ItemRef, Float>()
            fun height(n: ItemRef): Float = heights.getOrPut(n) {
                val ks = children(n)
                if (ks.isEmpty()) size(n).h else max(size(n).h, ks.sumOf { height(it).toDouble() }.toFloat() + GAP_Y * (ks.size - 1))
            }
            fun place(n: ItemRef, d: Int, top: Float): Float {
                val ks = children(n)
                val cy = if (ks.isEmpty()) top + height(n) / 2 else {
                    val sum = ks.sumOf { height(it).toDouble() }.toFloat() + GAP_Y * (ks.size - 1)
                    var ky = top + (height(n) - sum) / 2
                    val centres = ks.map { c -> place(c, d + 1, ky).also { ky += height(c) + GAP_Y } }
                    (centres.first() + centres.last()) / 2
                }
                out[n] = Point(side * (colX[d] + size(n).w / 2), cy)
                return cy
            }
            val total = tops.sumOf { height(it).toDouble() }.toFloat() + GAP_BRANCH * (tops.size - 1)
            var y = -total / 2
            tops.forEach { t -> place(t, 1, y); y += height(t) + GAP_BRANCH }
            if (!bothSides) rootY = out.getValue(tops.first()).y
        }
        out[root] = Point(0f, rootY)
    }

    /**
     * By route: what was placed, and the lists stacked under an item: [stackedUnder] gives a stacked
     * item's parent, and [spine] the x where the parent's list line runs down.
     */
    data class Route(val at: Map<ItemRef, Point>, val stackedUnder: Map<ItemRef, ItemRef>, val spine: Map<ItemRef, Float>)

    /**
     * By route: each item in its [layerOf] row, top to bottom, each row as tall as its tallest box.
     * Within a row, items are ordered by the average place of what they link to (a few sweeps down
     * and up), which keeps lines from crossing. Two or more items linked only to the same item in
     * an earlier row stack as a list under it, and a row wider than [maxWidth] wraps onto another,
     * so the map grows down rather than sideways.
     */
    fun byRoute(nodes: List<ItemRef>, edges: List<Pair<ItemRef, ItemRef>>, layerOf: (ItemRef) -> Int, size: (ItemRef) -> Size, maxWidth: Float): Route {
        val list = nodes.distinct()
        if (list.isEmpty()) return Route(emptyMap(), emptyMap(), emptyMap())
        val inGraph = list.toSet()
        val neighbours = HashMap<ItemRef, LinkedHashSet<ItemRef>>()
        edges.forEach { (a, b) ->
            if (a in inGraph && b in inGraph && a != b) {
                neighbours.getOrPut(a) { LinkedHashSet() } += b
                neighbours.getOrPut(b) { LinkedHashSet() } += a
            }
        }
        val stacks = list.mapNotNull { n ->
            val only = neighbours[n]?.singleOrNull() ?: return@mapNotNull null
            if (layerOf(only) < layerOf(n)) only to n else null
        }.groupBy({ it.first }, { it.second }).filterValues { it.size >= 2 }
        val stacked = stacks.values.flatten().toSet()
        val rows = list.filter { it !in stacked }.groupBy(layerOf).toSortedMap().values.map { it.toMutableList() }
        val place = HashMap<ItemRef, Float>()
        rows.forEach { row -> row.forEachIndexed { i, n -> place[n] = i - (row.size - 1) / 2f } }
        repeat(4) { sweep ->
            val indices = if (sweep % 2 == 0) rows.indices else rows.indices.reversed()
            indices.forEach { r ->
                val row = rows[r]
                val keyed = row.map { n ->
                    val linked = neighbours[n].orEmpty().mapNotNull { place[it] }
                    n to (if (linked.isEmpty()) place.getValue(n) else linked.average().toFloat())
                }
                row.clear()
                row += keyed.sortedBy { it.second }.map { it.first }
                row.forEachIndexed { i, n -> place[n] = i - (row.size - 1) / 2f }
            }
        }
        fun blockW(n: ItemRef) = stacks[n]?.let { s -> max(size(n).w, STACK_INDENT + s.maxOf { size(it).w }) } ?: size(n).w
        fun blockH(n: ItemRef) = size(n).h + (stacks[n]?.sumOf { (size(it).h + STACK_GAP).toDouble() }?.toFloat() ?: 0f)
        val at = HashMap<ItemRef, Point>()
        val under = HashMap<ItemRef, ItemRef>()
        val spine = HashMap<ItemRef, Float>()
        var y = 0f
        rows.forEach { row ->
            val lines = mutableListOf(mutableListOf<ItemRef>())
            var lineW = 0f
            row.forEach { n ->
                val line = lines.last()
                val add = blockW(n) + if (line.isEmpty()) 0f else GAP_X
                if (line.isNotEmpty() && lineW + add > maxWidth) { lines += mutableListOf(n); lineW = blockW(n) } else { line += n; lineW += add }
            }
            lines.forEach { line ->
                val width = line.sumOf { blockW(it).toDouble() }.toFloat() + GAP_X * (line.size - 1)
                var left = -width / 2
                line.forEach { n ->
                    val s = size(n)
                    at[n] = Point(left + s.w / 2, y + s.h / 2)
                    stacks[n]?.let { items ->
                        var ty = y + s.h + STACK_GAP
                        items.forEach { c ->
                            val cs = size(c)
                            at[c] = Point(left + STACK_INDENT + cs.w / 2, ty + cs.h / 2)
                            under[c] = n
                            ty += cs.h + STACK_GAP
                        }
                        spine[n] = left + STACK_SPINE
                    }
                    left += blockW(n) + GAP_X
                }
                y += line.maxOf { blockH(it) } + ROW_GAP
            }
        }
        return Route(at, under, spine)
    }

    /**
     * Free layout: linked items pull together and all items push apart (a force-directed layout),
     * starting from [start] so it settles the same way every time; then any boxes still
     * overlapping are pushed apart by their full size. [fixed] stays where it starts.
     */
    fun free(nodes: List<ItemRef>, edges: List<Pair<ItemRef, ItemRef>>, size: (ItemRef) -> Size, start: Map<ItemRef, Point>, fixed: ItemRef? = null): Map<ItemRef, Point> {
        val list = nodes.distinct()
        if (list.isEmpty()) return emptyMap()
        val index = list.withIndex().associate { it.value to it.index }
        val x = FloatArray(list.size) { start[list[it]]?.x ?: (it * 40f) }
        val y = FloatArray(list.size) { (start[list[it]]?.y ?: 0f) + (it % 3) * 0.5f }
        val w = FloatArray(list.size) { size(list[it]).w }
        val h = FloatArray(list.size) { size(list[it]).h }
        val pairs = edges.mapNotNull { (a, b) -> index[a]?.let { ia -> index[b]?.let { ib -> if (ia != ib) ia to ib else null } } }
        val pinned = fixed?.let(index::get)
        val fx = FloatArray(list.size)
        val fy = FloatArray(list.size)
        val rounds = 240
        repeat(rounds) { round ->
            fx.fill(0f); fy.fill(0f)
            for (i in list.indices) for (j in i + 1 until list.size) {
                var dx = x[i] - x[j]
                var dy = y[i] - y[j]
                var d2 = dx * dx + dy * dy
                if (d2 < 1f) { dx = 0.5f * (i - j); dy = 0.5f; d2 = dx * dx + dy * dy }
                val d = sqrt(d2)
                val f = 9000f / d2
                fx[i] += dx / d * f; fy[i] += dy / d * f
                fx[j] -= dx / d * f; fy[j] -= dy / d * f
            }
            pairs.forEach { (i, j) ->
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val f = (d - ((w[i] + w[j]) / 2 + 24f)) * 0.04f
                fx[i] += dx / d * f; fy[i] += dy / d * f
                fx[j] -= dx / d * f; fy[j] -= dy / d * f
            }
            val cool = 1f - round / (rounds + 40f)
            for (i in list.indices) {
                if (i == pinned) continue
                // A weak pull to the middle keeps separate networks from drifting apart.
                fx[i] -= x[i] * 0.002f; fy[i] -= y[i] * 0.002f
                x[i] += fx[i].coerceIn(-20f, 20f) * cool
                y[i] += fy[i].coerceIn(-20f, 20f) * cool * 0.8f
            }
        }
        separate(list.size, x, y, w, h, pinned)
        return list.indices.associate { list[it] to Point(x[it], y[it]) }
    }

    /** Pushes overlapping boxes apart, along whichever way they overlap less, until none do. */
    private fun separate(n: Int, x: FloatArray, y: FloatArray, w: FloatArray, h: FloatArray, pinned: Int?, pad: Float = 10f) {
        repeat(100) {
            var moved = false
            for (i in 0 until n) for (j in i + 1 until n) {
                val ox = (w[i] + w[j]) / 2 + pad - abs(x[i] - x[j])
                val oy = (h[i] + h[j]) / 2 + pad - abs(y[i] - y[j])
                if (ox <= 0f || oy <= 0f) continue
                moved = true
                val wi = when (pinned) { i -> 0f; j -> 1f; else -> 0.5f }
                val wj = 1f - wi
                if (ox < oy) {
                    val s = if (x[i] < x[j] || (x[i] == x[j] && i < j)) -1f else 1f
                    x[i] += s * ox * wi; x[j] -= s * ox * wj
                } else {
                    val s = if (y[i] < y[j] || (y[i] == y[j] && i < j)) -1f else 1f
                    y[i] += s * oy * wi; y[j] -= s * oy * wj
                }
            }
            if (!moved) return
        }
    }

    /** Whether two boxes centred at [a] and [b] overlap. */
    fun overlap(a: Point, sa: Size, b: Point, sb: Size): Boolean =
        abs(a.x - b.x) < (sa.w + sb.w) / 2 && abs(a.y - b.y) < (sa.h + sb.h) / 2

    /** The smallest box round every item: left, top, right, bottom. */
    fun bounds(at: Map<ItemRef, Point>, size: (ItemRef) -> Size): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        at.forEach { (ref, p) ->
            val s = size(ref)
            l = min(l, p.x - s.w / 2); t = min(t, p.y - s.h / 2); r = max(r, p.x + s.w / 2); b = max(b, p.y + s.h / 2)
        }
        return floatArrayOf(l, t, r, b)
    }
}
