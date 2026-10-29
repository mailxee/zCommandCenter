package com.example.xcommandcenter.domain

/**
 * Side-by-side placement for the Schedule tab, as calendar apps do: blocks that overlap, directly
 * or through others, form a group, and each takes the first column free at its start. Every block
 * in a group is as wide as the group's column count allows.
 */
object ScheduleLayout {
    data class Placement<T>(val item: T, val column: Int, val columns: Int)

    /** [items] in start order, each with its column; [start] and [end] are minutes of the day. */
    fun <T> place(items: List<T>, start: (T) -> Int, end: (T) -> Int): List<Placement<T>> {
        val sorted = items.sortedWith(compareBy(start, end))
        val result = mutableListOf<Placement<T>>()
        var group = mutableListOf<Pair<T, Int>>()
        val columnEnds = mutableListOf<Int>()
        var groupEnd = Int.MIN_VALUE
        fun closeGroup() {
            group.forEach { (item, column) -> result += Placement(item, column, columnEnds.size) }
            group = mutableListOf()
            columnEnds.clear()
            groupEnd = Int.MIN_VALUE
        }
        sorted.forEach { item ->
            // A block that starts at or after everything so far has ended begins a new group.
            if (start(item) >= groupEnd) closeGroup()
            val column = columnEnds.indexOfFirst { it <= start(item) }.takeIf { it >= 0 } ?: columnEnds.size.also { columnEnds += 0 }
            columnEnds[column] = end(item)
            group += item to column
            groupEnd = maxOf(groupEnd, end(item))
        }
        closeGroup()
        return result
    }
}
