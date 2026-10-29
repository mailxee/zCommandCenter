package com.example.xcommandcenter.domain

/**
 * Link mode's network: every item reachable from a root by following links, however many steps
 * away. Links are undirected. Books are not followed: the walk goes only through the other tabs'
 * items, and a book (with all its chapters) is then shown when it is linked to one of them. A
 * chapter's links count as its book's.
 */
object LinkNetwork {
    /**
     * The items reachable from [root] over [links] (pairs of linked items), [root] included.
     * [bookOfChapter] maps each chapter id to its book id. A book as the root starts the walk
     * from the items linked to it.
     */
    fun of(root: ItemRef, links: List<Pair<ItemRef, ItemRef>>, bookOfChapter: Map<Long, Long>): Set<ItemRef> {
        fun node(ref: ItemRef): ItemRef =
            if (ref.type == ItemType.CHAPTER) bookOfChapter[ref.id]?.let { ItemRef(ItemType.BOOK, it) } ?: ref else ref
        fun isBook(ref: ItemRef) = ref.type == ItemType.BOOK || ref.type == ItemType.CHAPTER

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
            if (!isBook(ref) && reached.add(ref)) queue.addLast(ref)
        }
        if (isBook(start)) neighbors[start].orEmpty().forEach(::visit) else visit(start)
        while (queue.isNotEmpty()) {
            neighbors[queue.removeFirst()].orEmpty().forEach(::visit)
        }
        val books = reached.flatMap { neighbors[it].orEmpty() }.filter { it.type == ItemType.BOOK }.toSet() +
            listOfNotNull(start.takeIf { it.type == ItemType.BOOK })
        val chapters = bookOfChapter.filterValues { ItemRef(ItemType.BOOK, it) in books }.keys.map { ItemRef(ItemType.CHAPTER, it) }
        return reached + books + chapters + root
    }
}
