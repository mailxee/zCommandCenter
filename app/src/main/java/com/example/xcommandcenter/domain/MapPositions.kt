package com.example.xcommandcenter.domain

/**
 * Where items were dragged to on the map, for viewing only: nothing about the items or their links
 * changes. Each arrangement belongs to one view (mode, layout and the item whose network is shown),
 * so moving items in one map leaves the others as laid out. Positions are in grid units, the same
 * as [GraphLayout.Point].
 */
object MapPositions {
    fun viewKey(mode: AppMode, layout: String, root: ItemRef?): String =
        "${mode.name}/$layout/" + (root?.let { "${it.type.name}:${it.id}" } ?: "all")

    /** "view|TYPE:id|x|y" entries, as stored in the settings. */
    fun encode(view: String, ref: ItemRef, point: GraphLayout.Point): String = "$view|${ref.type.name}:${ref.id}|${point.x}|${point.y}"

    /** Every view's positions from the stored entries; malformed entries are skipped. */
    fun parse(entries: Set<String>?): Map<String, Map<ItemRef, GraphLayout.Point>> {
        val out = HashMap<String, HashMap<ItemRef, GraphLayout.Point>>()
        entries.orEmpty().forEach { entry ->
            val parts = entry.split('|')
            if (parts.size != 4) return@forEach
            val (type, id) = parts[1].split(':').takeIf { it.size == 2 } ?: return@forEach
            val itemType = ItemType.entries.firstOrNull { it.name == type } ?: return@forEach
            val ref = ItemRef(itemType, id.toLongOrNull() ?: return@forEach)
            val x = parts[2].toFloatOrNull()?.takeIf { it.isFinite() } ?: return@forEach
            val y = parts[3].toFloatOrNull()?.takeIf { it.isFinite() } ?: return@forEach
            out.getOrPut(parts[0]) { HashMap() }[ref] = GraphLayout.Point(x, y)
        }
        return out
    }

    /** [entries] with [ref] in [view] moved to [point] (any earlier position of it there replaced). */
    fun move(entries: Set<String>?, view: String, ref: ItemRef, point: GraphLayout.Point): Set<String> {
        val prefix = "$view|${ref.type.name}:${ref.id}|"
        return entries.orEmpty().filterNot { it.startsWith(prefix) }.toSet() + encode(view, ref, point)
    }

    /** [entries] without any position in [view]: its map goes back to the layout's own places. */
    fun reset(entries: Set<String>?, view: String): Set<String> = entries.orEmpty().filterNot { it.startsWith("$view|") }.toSet()
}
