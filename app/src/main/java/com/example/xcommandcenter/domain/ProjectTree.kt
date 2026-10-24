package com.example.xcommandcenter.domain

/** Tree helpers over any list of nodes that know their own id and their parent's id. */
object ProjectTree {

    data class Row<T>(val node: T, val depth: Int)

    /** Depth-first order with depth, roots first. Nodes whose parent is missing are treated as roots. */
    fun <T> flatten(nodes: List<T>, id: (T) -> Long, parentId: (T) -> Long?): List<Row<T>> {
        val ids = nodes.map(id).toSet()
        val children = nodes.groupBy { node -> parentId(node)?.takeIf { it in ids } }
        val result = mutableListOf<Row<T>>()
        val visited = mutableSetOf<Long>()
        fun visit(node: T, depth: Int) {
            if (!visited.add(id(node))) return // Guards against corrupt cyclic data.
            result += Row(node, depth)
            children[id(node)].orEmpty().forEach { visit(it, depth + 1) }
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
}
