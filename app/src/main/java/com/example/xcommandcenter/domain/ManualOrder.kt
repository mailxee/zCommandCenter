package com.example.xcommandcenter.domain

/** Reordering a list of which the user may see only part (a filter can hide items). */
object ManualOrder {
    /**
     * Gives the [visible] ids the order they have in [visible], and returns the new order of
     * [all]: hidden items keep their slots, and the visible ones fill theirs in the new order.
     * Visible ids that aren't in [all] are ignored.
     */
    fun reorder(all: List<Long>, visible: List<Long>): List<Long> {
        val known = all.toSet()
        val shown = visible.filter { it in known }
        val shownSet = shown.toSet()
        val next = shown.iterator()
        return all.map { if (it in shownSet) next.next() else it }
    }

    /**
     * Moves [id] by [offset] places among the [visible] ids (see [reorder]). Returns null when
     * [id] isn't visible or the move would go past either end.
     */
    fun move(all: List<Long>, visible: List<Long>, id: Long, offset: Int): List<Long>? {
        val known = all.toSet()
        val shown = visible.filter { it in known }
        val from = shown.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in shown.indices) return null
        return reorder(all, shown.toMutableList().apply { add(to, removeAt(from)) })
    }

    /**
     * In an outline where a row owns the deeper rows right after it, moves the row at [from]
     * together with everything it owns: before the row at [to] when moving up, after everything
     * that row owns when moving down. A move onto the row's own block changes nothing.
     */
    fun <T> moveBlock(rows: List<T>, depth: (T) -> Int, from: Int, to: Int): List<T> {
        if (from == to || from !in rows.indices || to !in rows.indices) return rows
        fun blockEnd(start: Int): Int {
            var end = start + 1
            while (end < rows.size && depth(rows[end]) > depth(rows[start])) end++
            return end
        }
        val fromEnd = blockEnd(from)
        if (to in from until fromEnd) return rows
        val block = rows.subList(from, fromEnd)
        val rest = rows.subList(0, from) + rows.subList(fromEnd, rows.size)
        val insertAt = if (to < from) to else blockEnd(to) - block.size
        return rest.subList(0, insertAt) + block + rest.subList(insertAt, rest.size)
    }
}
