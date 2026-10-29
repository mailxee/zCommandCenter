package com.example.xcommandcenter.domain

/**
 * Link mode's network: every item reachable from a root by following links, however many steps
 * away. Links are undirected. Notes, quotes, books and schedule blocks are end points, never
 * followed: the walk goes only through tasks, goals, habits and plans, and an end point shows (a
 * book with all its chapters) when it is linked to one of them. A chapter's links count as its book's.
 */
object LinkNetwork {
    /** The tabs whose items end the walk instead of extending it. */
    private val endPoints = setOf(ItemType.NOTE, ItemType.QUOTE, ItemType.BOOK, ItemType.CHAPTER, ItemType.BLOCK)

    /**
     * The items reachable from [root] over [links] (pairs of linked items), [root] included.
     * [bookOfChapter] maps each chapter id to its book id. An end point as the root starts the
     * walk from the items linked to it, and also shows the end points linked to it directly.
     */
    fun of(root: ItemRef, links: List<Pair<ItemRef, ItemRef>>, bookOfChapter: Map<Long, Long>): Set<ItemRef> {
        fun node(ref: ItemRef): ItemRef =
            if (ref.type == ItemType.CHAPTER) bookOfChapter[ref.id]?.let { ItemRef(ItemType.BOOK, it) } ?: ref else ref
        fun isEndPoint(ref: ItemRef) = ref.type in endPoints

        val neighbors = HashMap<ItemRef, MutableSet<ItemRef>>()
        links.forEach { (a, b) ->
            val (na, nb) = node(a) to node(b)
            neighbors.getOrPut(na) { mutableSetOf() } += nb
            neighbors.getOrPut(nb) { mutableSetOf() } += na
        }
        val start = node(root)
        val reached = mutableSetOf<ItemRef>()
        val queue = ArrayDeque<ItemRef>()
        fun visit(ref: ItemRef) {
            if (!isEndPoint(ref) && reached.add(ref)) queue.addLast(ref)
        }
        if (isEndPoint(start)) neighbors[start].orEmpty().forEach(::visit) else visit(start)
        while (queue.isNotEmpty()) {
            neighbors[queue.removeFirst()].orEmpty().forEach(::visit)
        }
        val ends = reached.flatMap { neighbors[it].orEmpty() }.filter(::isEndPoint).toSet() +
            if (isEndPoint(start)) neighbors[start].orEmpty().filter(::isEndPoint) + start else emptyList()
        val chapters = bookOfChapter.filterValues { ItemRef(ItemType.BOOK, it) in ends }.keys.map { ItemRef(ItemType.CHAPTER, it) }
        return reached + ends + chapters + root
    }
}
