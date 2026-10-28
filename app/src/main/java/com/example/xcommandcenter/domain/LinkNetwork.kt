package com.example.xcommandcenter.domain

/**
 * Link mode's network: every item reachable from a root by following links, however many steps
 * away. Links are undirected. A chapter and its book count as one item, so reaching either
 * reaches both, and every chapter of the book.
 */
object LinkNetwork {
    /**
     * The items reachable from [root] over [links] (pairs of linked items), [root] included.
     * [bookOfChapter] maps each chapter id to its book id.
     */
    fun of(root: ItemRef, links: List<Pair<ItemRef, ItemRef>>, bookOfChapter: Map<Long, Long>): Set<ItemRef> {
        fun node(ref: ItemRef): ItemRef =
            if (ref.type == ItemType.CHAPTER) bookOfChapter[ref.id]?.let { ItemRef(ItemType.BOOK, it) } ?: ref else ref

        val neighbors = HashMap<ItemRef, MutableList<ItemRef>>()
        links.forEach { (a, b) ->
            val (na, nb) = node(a) to node(b)
            neighbors.getOrPut(na) { mutableListOf() } += nb
            neighbors.getOrPut(nb) { mutableListOf() } += na
        }
        val start = node(root)
        val reached = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            neighbors[queue.removeFirst()].orEmpty().forEach { if (reached.add(it)) queue.addLast(it) }
        }
        val books = reached.filter { it.type == ItemType.BOOK }.map { it.id }.toSet()
        val chapters = bookOfChapter.filterValues { it in books }.keys.map { ItemRef(ItemType.CHAPTER, it) }
        return reached + chapters + root
    }
}
