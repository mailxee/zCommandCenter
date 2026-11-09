package com.example.xcommandcenter.domain

/**
 * The Ideas tab's kind chips, which work like tags: tap a kind to show only that kind (several
 * can be on at once), press and hold to hide it.
 */
data class IdeaKindFilter(val states: Map<IdeaKind, ChipState> = emptyMap()) {
    val active: Boolean get() = states.values.any { it != ChipState.ANY }

    fun state(kind: IdeaKind): ChipState = states[kind] ?: ChipState.ANY

    fun matches(kind: IdeaKind): Boolean {
        val only = states.filterValues { it == ChipState.ONLY }.keys
        return state(kind) != ChipState.HIDDEN && (only.isEmpty() || kind in only)
    }

    fun tap(kind: IdeaKind) = copy(states = states + (kind to if (state(kind) == ChipState.ANY) ChipState.ONLY else ChipState.ANY))

    fun hold(kind: IdeaKind) = copy(states = states + (kind to if (state(kind) == ChipState.HIDDEN) ChipState.ANY else ChipState.HIDDEN))
}

/** Builds the Ideas tab's outline from the ideas, generic so it can be tested without entities. */
object IdeaOutline {
    data class Row<T>(val node: T, val depth: Int, val collapsed: Boolean, val childCount: Int)

    /**
     * The tree in depth-first order. When [narrowed], only [hit] nodes show, plus the nodes above
     * them so the tree still reads; a node above a hit shows even if it isn't one. Nothing below a
     * [collapsed] node is listed; [Row.childCount] counts its visible children either way.
     */
    fun <T> rows(
        nodes: List<T>,
        id: (T) -> Long,
        parentId: (T) -> Long?,
        collapsed: Set<Long>,
        narrowed: Boolean,
        hit: (T) -> Boolean,
    ): List<Row<T>> {
        val visible = if (!narrowed) nodes else {
            val hits = nodes.filter(hit).map(id)
            val keep = hits.toSet() + hits.flatMap { PlanTree.ancestorIds(it, nodes, id, parentId) }
            nodes.filter { id(it) in keep }
        }
        val ids = visible.map(id).toSet()
        val childCounts = visible.mapNotNull { parentId(it)?.takeIf { p -> p in ids } }.groupingBy { it }.eachCount()
        return PlanTree.flatten(visible, id, parentId, collapsed).map { row ->
            Row(row.node, row.depth, id(row.node) in collapsed, childCounts[id(row.node)] ?: 0)
        }
    }
}
