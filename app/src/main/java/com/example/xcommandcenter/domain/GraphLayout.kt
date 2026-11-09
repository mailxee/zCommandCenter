package com.example.xcommandcenter.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Places a network's items for the map, in grid units: x in columns, y in rows; the screen scales
 * them. Pure and deterministic, so the same links always draw the same picture.
 */
object GraphLayout {
    data class Point(val x: Float, val y: Float)

    /**
     * By route: each item in its [layerOf] row, top to bottom, with a layer wider than [maxPerRow]
     * wrapped onto extra rows. Within a row, items are ordered by the average place of what they
     * link to (a few sweeps down and up), which keeps lines from crossing; each row is centred.
     */
    fun layered(
        nodes: List<ItemRef>,
        edges: List<Pair<ItemRef, ItemRef>>,
        maxPerRow: Int = 6,
        layerOf: (ItemRef) -> Int,
    ): Map<ItemRef, Point> {
        if (nodes.isEmpty()) return emptyMap()
        val neighbours = HashMap<ItemRef, MutableList<ItemRef>>()
        edges.forEach { (a, b) ->
            neighbours.getOrPut(a) { mutableListOf() } += b
            neighbours.getOrPut(b) { mutableListOf() } += a
        }
        val byLayer = nodes.distinct().groupBy(layerOf).toSortedMap()
        val rows = byLayer.values.map { it.toMutableList() }
        val place = HashMap<ItemRef, Float>()
        fun spread() = rows.forEach { row -> row.forEachIndexed { i, n -> place[n] = i - (row.size - 1) / 2f } }
        spread()
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
        val result = HashMap<ItemRef, Point>()
        var y = 0f
        rows.forEach { row ->
            row.chunked(maxPerRow).forEach { chunk ->
                chunk.forEachIndexed { i, n -> result[n] = Point(i - (chunk.size - 1) / 2f, y) }
                y += 1f
            }
            y += 0.25f
        }
        return result
    }

    /**
     * Free layout: linked items pull together and all items push apart (a force-directed layout),
     * starting from [start] (the layered places) so it settles the same way every time.
     */
    fun free(nodes: List<ItemRef>, edges: List<Pair<ItemRef, ItemRef>>, start: Map<ItemRef, Point>, iterations: Int = 250): Map<ItemRef, Point> {
        val list = nodes.distinct()
        if (list.size < 2) return list.associateWith { Point(0f, 0f) }
        val index = list.withIndex().associate { it.value to it.index }
        val x = FloatArray(list.size) { (start[list[it]]?.x ?: it.toFloat()) * 1.2f }
        val y = FloatArray(list.size) { (start[list[it]]?.y ?: 0f) * 1.2f + (it % 3) * 0.01f }
        val pairs = edges.mapNotNull { (a, b) -> index[a]?.let { ia -> index[b]?.let { ib -> ia to ib } } }
        val k = 1.0f
        var temperature = 1.5f
        val dx = FloatArray(list.size)
        val dy = FloatArray(list.size)
        repeat(iterations) {
            dx.fill(0f); dy.fill(0f)
            for (i in list.indices) for (j in i + 1 until list.size) {
                var ox = x[i] - x[j]
                var oy = y[i] - y[j]
                var d2 = ox * ox + oy * oy
                if (d2 < 1e-4f) { ox = 0.01f * (i - j); oy = 0.01f; d2 = ox * ox + oy * oy }
                val f = k * k / d2
                dx[i] += ox * f; dy[i] += oy * f
                dx[j] -= ox * f; dy[j] -= oy * f
            }
            pairs.forEach { (i, j) ->
                val ox = x[i] - x[j]
                val oy = y[i] - y[j]
                val d = sqrt(ox * ox + oy * oy).coerceAtLeast(0.01f)
                val f = d / k
                dx[i] -= ox * f; dy[i] -= oy * f
                dx[j] += ox * f; dy[j] += oy * f
            }
            for (i in list.indices) {
                // A weak pull to the middle keeps separate networks from drifting apart.
                dx[i] -= x[i] * 0.02f; dy[i] -= y[i] * 0.02f
                val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (len > 0f) {
                    val step = min(len, temperature)
                    x[i] += dx[i] / len * step
                    y[i] += dy[i] / len * step
                }
            }
            temperature = max(0.02f, temperature * 0.98f)
        }
        // Items are wide and short, so stretch across.
        return list.indices.associate { list[it] to Point(x[it] * 0.8f, y[it] * 0.9f) }
    }

    /**
     * Mind map: [roots] in the middle (one root at the centre, several spread on the first ring),
     * and everything else on rings by how many steps it is from them, following [edges] in order
     * (put the tree's own edges first so they decide the rings). Each item gets a share of its
     * parent's angle by the number of items at the ends of its branch, so branches don't overlap,
     * and a ring is pushed out when it has more items than fit round it. [aspect] is a column's
     * width over a row's height, so the rings come out round on screen.
     */
    fun radial(roots: List<ItemRef>, nodes: List<ItemRef>, edges: List<Pair<ItemRef, ItemRef>>, aspect: Float = 1.5f): Map<ItemRef, Point> {
        val list = nodes.distinct()
        if (list.isEmpty()) return emptyMap()
        val neighbours = LinkedHashMap<ItemRef, MutableList<ItemRef>>()
        edges.forEach { (a, b) ->
            neighbours.getOrPut(a) { mutableListOf() } += b
            neighbours.getOrPut(b) { mutableListOf() } += a
        }
        val inGraph = list.toSet()
        val starts = roots.filter { it in inGraph }.distinct().ifEmpty { listOf(list.first()) }
        val parent = HashMap<ItemRef, ItemRef?>()
        val depth = HashMap<ItemRef, Int>()
        val order = mutableListOf<ItemRef>()
        val single = starts.size == 1
        fun bfs(from: List<ItemRef>, baseDepth: Int) {
            val queue = ArrayDeque<ItemRef>()
            from.forEach { if (it !in depth) { depth[it] = baseDepth; parent[it] = null; queue += it; order += it } }
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                neighbours[at].orEmpty().filter { it in inGraph && it !in depth }.forEach {
                    depth[it] = depth.getValue(at) + 1
                    parent[it] = at
                    queue += it
                    order += it
                }
            }
        }
        bfs(starts, if (single) 0 else 1)
        // Anything not reached hangs on the first ring as a root of its own.
        list.filter { it !in depth }.forEach { bfs(listOf(it), 1) }
        val children = order.groupBy { parent[it] }
        val leaves = HashMap<ItemRef, Int>()
        fun countLeaves(n: ItemRef): Int = leaves.getOrPut(n) { children[n].orEmpty().sumOf { countLeaves(it) }.coerceAtLeast(1) }
        val tops = children[null].orEmpty()
        tops.forEach { countLeaves(it) }
        // Ring radii in columns: at least one apart, and wide enough for the items on them.
        val perRing = order.groupingBy { depth.getValue(it) }.eachCount()
        val radius = HashMap<Int, Float>()
        var r = 0f
        (0..(perRing.keys.maxOrNull() ?: 0)).forEach { d ->
            val needed = (perRing[d] ?: 0) * 1.0f / (2f * Math.PI.toFloat())
            r = if (d == 0) 0f else max(r + 1.15f, needed)
            radius[d] = r
        }
        val angle = HashMap<ItemRef, Float>()
        fun place(n: ItemRef, from: Float, to: Float) {
            angle[n] = (from + to) / 2
            val kids = children[n].orEmpty()
            val total = kids.sumOf { leaves.getValue(it) }.toFloat()
            var at = from
            kids.forEach { k ->
                val share = (to - from) * leaves.getValue(k) / total
                place(k, at, at + share)
                at += share
            }
        }
        val full = 2f * Math.PI.toFloat()
        val total = tops.sumOf { leaves.getValue(it) }.toFloat()
        var at = -Math.PI.toFloat() / 2
        tops.forEach { t ->
            val share = full * leaves.getValue(t) / total
            place(t, at, at + share)
            at += share
        }
        return order.associateWith { n ->
            val d = radius.getValue(depth.getValue(n))
            val a = angle.getValue(n)
            Point(d * kotlin.math.cos(a), d * kotlin.math.sin(a) * aspect)
        }
    }
}
