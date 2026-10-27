package com.example.xcommandcenter.domain

/** Moving an item up or down a list of which the user may see only part (a filter can hide items). */
object ManualOrder {
    /**
     * Moves [id] by [offset] places among the [visible] ids and returns the new order of [all]:
     * hidden items keep their slots, and the visible ones fill theirs in the new order. Returns
     * null when [id] isn't visible or the move would go past either end.
     */
    fun move(all: List<Long>, visible: List<Long>, id: Long, offset: Int): List<Long>? {
        val known = all.toSet()
        val shown = visible.filter { it in known }
        val from = shown.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in shown.indices) return null
        val moved = shown.toMutableList().apply { add(to, removeAt(from)) }
        val movedSet = moved.toSet()
        val next = moved.iterator()
        return all.map { if (it in movedSet) next.next() else it }
    }
}
