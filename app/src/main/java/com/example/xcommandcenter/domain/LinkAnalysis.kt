package com.example.xcommandcenter.domain

/**
 * Finds where links make a network more tangled than it needs to be, and suggests fixes (the
 * Tidy links list and the map's markings). Links are undirected; a chapter counts as its book, and
 * end points (see [LinkNetwork]) close a route rather than carry it on.
 *
 * Routes run only through goals, plans, tasks and habits. Among their links, a spanning forest is
 * built keeping on-route links first, then links that skip a layer, then sideways ones, older
 * before newer. Every link left over closes a loop: there are two routes between its items. That
 * link is the one suggested for removal, so a loop is reported once, by its least fitting, newest
 * link ([Kind.SHORTCUT], [Kind.OFF_ROUTE_LOOP] or [Kind.EXTRA_ROUTE]). Separately, a link that skips
 * a layer without making a loop is reported with a better place for it ([Kind.SKIPS_LAYER]), and an
 * end point shared by several items of one network is noted ([Kind.SHARED_END_POINT], for
 * information only).
 */
object LinkAnalysis {
    data class Link(val id: Long, val a: ItemRef, val b: ItemRef, val createdAt: Long = 0)

    enum class Kind {
        /** A link that jumps over items already connecting its two ends: goal to task, beside goal to plan to task. */
        SHORTCUT,

        /** A sideways or layer-skipping link that closes a loop. */
        OFF_ROUTE_LOOP,

        /** An on-route link that makes a second route (a task under two plans of one goal). */
        EXTRA_ROUTE,

        /** A link that skips a layer, with no loop. */
        SKIPS_LAYER,

        /** An end point linked to several items of one network; usually intended. */
        SHARED_END_POINT,
    }

    data class Finding(
        /** Stable while the links involved stay: what "Keep as is" remembers. */
        val key: String,
        val kind: Kind,
        /** The link the finding is about (null for a shared end point). */
        val link: Link?,
        /** For a loop, the items of the other route between the link's ends, in order. */
        val via: List<ItemRef> = emptyList(),
        /** The suggested removal, if any. */
        val remove: Link? = null,
        /** A suggested new link, if any (made before [remove] is removed). */
        val add: Pair<ItemRef, ItemRef>? = null,
        /** For [Kind.SKIPS_LAYER] with no better place: the layer the link skips. */
        val missingLayer: Int? = null,
        /** For [Kind.SHARED_END_POINT]: the end point, and the items linked to it. */
        val endPoint: ItemRef? = null,
        val sharedBy: List<ItemRef> = emptyList(),
    ) {
        /** Every item the finding concerns, to place it in a network. */
        val items: Set<ItemRef>
            get() = listOfNotNull(link?.a, link?.b, endPoint).toSet() + via + sharedBy + listOfNotNull(add?.first, add?.second)

        /** Suggested fixes are in order of how much they matter. */
        val actionable: Boolean get() = remove != null || add != null
    }

    data class Result(
        /** Open findings, most important first. */
        val findings: List<Finding>,
        /** Findings the user chose to keep as they are. */
        val kept: List<Finding>,
    ) {
        /** Links some open finding suggests removing, by link id. */
        val removals: Set<Long> get() = findings.mapNotNull { it.remove?.id }.toSet()

        /** Every second route counted, kept or not. */
        val extraRoutes: Int get() = (findings + kept).count { it.kind in loopKinds }
    }

    val loopKinds = setOf(Kind.SHORTCUT, Kind.OFF_ROUTE_LOOP, Kind.EXTRA_ROUTE)

    fun analyze(links: List<Link>, bookOfChapter: Map<Long, Long>, route: LinkRoute, kept: Set<String> = emptySet()): Result {
        fun node(ref: ItemRef): ItemRef =
            if (ref.type == ItemType.CHAPTER) bookOfChapter[ref.id]?.let { ItemRef(ItemType.BOOK, it) } ?: ref else ref
        val all = links.map { it.copy(a = node(it.a), b = node(it.b)) }.filter { it.a != it.b }
        val passing = all.filter { !route.isEndPoint(it.a.type) && !route.isEndPoint(it.b.type) }
        val found = mutableListOf<Finding>()

        // Loops: a spanning forest keeps the best-fitting, oldest links; the rest each close a loop.
        val rank = mapOf(LinkRoute.Kind.ON_ROUTE to 0, LinkRoute.Kind.SKIPS to 1, LinkRoute.Kind.SIDEWAYS to 2)
        val ordered = passing.sortedWith(compareBy<Link>({ rank.getValue(route.kind(it.a.type, it.b.type)) }, { it.createdAt }, { it.id }))
        val parent = HashMap<ItemRef, ItemRef>()
        fun find(x: ItemRef): ItemRef {
            var r = x
            while (parent[r] != null && parent[r] != r) r = parent.getValue(r)
            var c = x
            while (c != r) { val next = parent[c] ?: r; parent[c] = r; c = next }
            return r
        }
        val forest = HashMap<ItemRef, MutableList<ItemRef>>()
        val extra = mutableListOf<Link>()
        ordered.forEach { link ->
            val (ra, rb) = find(link.a) to find(link.b)
            if (ra == rb) {
                extra += link
            } else {
                parent[ra] = rb
                forest.getOrPut(link.a) { mutableListOf() } += link.b
                forest.getOrPut(link.b) { mutableListOf() } += link.a
            }
        }
        fun pathInForest(from: ItemRef, to: ItemRef): List<ItemRef> {
            val back = HashMap<ItemRef, ItemRef>()
            val queue = ArrayDeque(listOf(from))
            val seen = mutableSetOf(from)
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                if (at == to) break
                forest[at].orEmpty().forEach { if (seen.add(it)) { back[it] = at; queue.addLast(it) } }
            }
            val path = mutableListOf(to)
            while (path.last() != from) path += back[path.last()] ?: return emptyList()
            return path.reversed()
        }
        extra.forEach { link ->
            val inner = pathInForest(link.a, link.b).drop(1).dropLast(1)
            val kind = route.kind(link.a.type, link.b.type)
            val (lo, hi) = listOf(route.layer(link.a.type), route.layer(link.b.type)).sorted()
            val between = inner.isNotEmpty() && inner.all { route.layer(it.type) in (lo + 1) until hi }
            val findingKind = when {
                kind == LinkRoute.Kind.SKIPS && between -> Kind.SHORTCUT
                kind != LinkRoute.Kind.ON_ROUTE -> Kind.OFF_ROUTE_LOOP
                else -> Kind.EXTRA_ROUTE
            }
            found += Finding("link:${link.id}", findingKind, link, via = inner, remove = link)
        }

        // Skipped layers without a loop: suggest going through an item of the skipped layer.
        val neighbours = HashMap<ItemRef, MutableList<Pair<ItemRef, Link>>>()
        passing.forEach {
            neighbours.getOrPut(it.a) { mutableListOf() } += it.b to it
            neighbours.getOrPut(it.b) { mutableListOf() } += it.a to it
        }
        val inLoops = extra.map { it.id }.toSet()
        passing.filter { it.id !in inLoops && route.kind(it.a.type, it.b.type) == LinkRoute.Kind.SKIPS }.forEach { link ->
            val (upper, lower) = if (route.layer(link.a.type) < route.layer(link.b.type)) link.a to link.b else link.b to link.a
            val target = route.layer(lower.type) - 1
            val bridge = neighbours[upper].orEmpty()
                .filter { (other, _) -> route.layer(other.type) == target && other != lower }
                .minWithOrNull(compareBy({ it.second.createdAt }, { it.second.id }))?.first
            found += if (bridge != null) {
                Finding("skip:${link.id}", Kind.SKIPS_LAYER, link, via = listOf(bridge), remove = link, add = lower to bridge)
            } else {
                Finding("skip:${link.id}", Kind.SKIPS_LAYER, link, missingLayer = target)
            }
        }

        // End points shared within one network.
        val component = { ref: ItemRef -> find(ref) }
        val ends = HashMap<ItemRef, MutableSet<ItemRef>>()
        all.forEach { link ->
            listOf(link.a to link.b, link.b to link.a).forEach { (end, other) ->
                if (route.isEndPoint(end.type) && !route.isEndPoint(other.type)) ends.getOrPut(end) { mutableSetOf() } += other
            }
        }
        ends.forEach { (end, items) ->
            items.groupBy(component).values.filter { it.size > 1 }.forEach { group ->
                found += Finding("end:${end.type.name}:${end.id}", Kind.SHARED_END_POINT, null, endPoint = end, sharedBy = group.sortedBy { it.id })
            }
        }

        val order = compareBy<Finding>({ it.kind.ordinal }, { it.link?.createdAt ?: 0 }, { it.key })
        val (open, keptFindings) = found.distinctBy { it.key }.sortedWith(order).partition { it.key !in kept }
        return Result(open, keptFindings)
    }
}
