package com.example.xcommandcenter.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The map's building blocks (§2.69, §2.70): its tree, By route's rows, and box geometry. Each item
 * is a box of its own [Size], in dp, placed by its centre, also in dp; [BranchLayout] puts the
 * branches together. Pure and deterministic, so the same links always draw the same picture.
 */
object GraphLayout {
    data class Point(val x: Float, val y: Float)

    /** A box's width and height, in dp. */
    data class Size(val w: Float, val h: Float)

    /** Space between boxes side by side. */
    private const val GAP_X = 20f

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
