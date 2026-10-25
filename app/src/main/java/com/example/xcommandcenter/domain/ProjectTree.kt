package com.example.xcommandcenter.domain

/** Tree helpers over any list of nodes that know their own id and their parent's id. */
object ProjectTree {

    data class Row<T>(val node: T, val depth: Int)

    /**
     * Depth-first order with depth, roots first. Nodes whose parent is missing are treated as roots.
     * Nodes in [collapsed] are listed, but nothing below them is.
     */
    fun <T> flatten(
        nodes: List<T>,
        id: (T) -> Long,
        parentId: (T) -> Long?,
        collapsed: Set<Long> = emptySet(),
    ): List<Row<T>> {
        val ids = nodes.map(id).toSet()
        val children = nodes.groupBy { node -> parentId(node)?.takeIf { it in ids } }
        val result = mutableListOf<Row<T>>()
        val visited = mutableSetOf<Long>()
        fun visit(node: T, depth: Int) {
            if (!visited.add(id(node))) return // Guards against corrupt cyclic data.
            result += Row(node, depth)
            if (id(node) !in collapsed) children[id(node)].orEmpty().forEach { visit(it, depth + 1) }
        }
        children[null].orEmpty().forEach { visit(it, 0) }
        return result
    }

    /** [rootId] and every node below it. */
    fun <T> subtreeIds(rootId: Long, nodes: List<T>, id: (T) -> Long, parentId: (T) -> Long?): Set<Long> {
        val children = nodes.groupBy(parentId, id)
        val result = mutableSetOf<Long>()
        val stack = ArrayDeque(listOf(rootId))
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (result.add(current)) stack.addAll(children[current].orEmpty())
        }
        return result
    }

    /** Parent, grandparent, … of [id], up to a root. Excludes [id] itself. */
    fun <T> ancestorIds(id: Long, nodes: List<T>, idOf: (T) -> Long, parentId: (T) -> Long?): Set<Long> {
        val parents = nodes.associate { idOf(it) to parentId(it) }
        val result = mutableSetOf<Long>()
        var current = parents[id]
        while (current != null && current != id && result.add(current)) current = parents[current]
        return result
    }
}
