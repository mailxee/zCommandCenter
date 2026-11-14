package com.example.xcommandcenter.domain

/**
 * How far items were dragged on the map, for viewing only: nothing about the items or their links
 * changes. Each item keeps an offset in dp from where its layout puts it, and everything below it
 * in the map's tree moves with it, so a dragged branch keeps its shape (§2.70). Each arrangement
 * belongs to one view (mode and the item whose network is shown), so moving items in one map
 * leaves the others as laid out.
 *
 * Views of the earlier format (absolute places per layout) are dropped on the next change.
 */
object MapPositions {
    private const val PREFIX = "o:"

    fun viewKey(mode: AppMode, root: ItemRef?): String =
        "$PREFIX${mode.name}/" + (root?.let { "${it.type.name}:${it.id}" } ?: "all")

    /** "view|TYPE:id|x|y" entries, as stored in the settings. */
    fun encode(view: String, ref: ItemRef, point: GraphLayout.Point): String = "$view|${ref.type.name}:${ref.id}|${point.x}|${point.y}"

    /** Every view's offsets from the stored entries; malformed and old-format entries are skipped. */
    fun parse(entries: Set<String>?): Map<String, Map<ItemRef, GraphLayout.Point>> {
        val out = HashMap<String, HashMap<ItemRef, GraphLayout.Point>>()
        entries.orEmpty().forEach { entry ->
            if (!entry.startsWith(PREFIX)) return@forEach
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

    /** [entries] with [ref]'s offset in [view] set to [point] (any earlier one there replaced). */
    fun move(entries: Set<String>?, view: String, ref: ItemRef, point: GraphLayout.Point): Set<String> {
        val prefix = "$view|${ref.type.name}:${ref.id}|"
        return entries.orEmpty().filter { it.startsWith(PREFIX) && !it.startsWith(prefix) }.toSet() + encode(view, ref, point)
    }

    /** [entries] without the offsets of [refs] in [view]: those items go back to the layout's places. */
    fun forget(entries: Set<String>?, view: String, refs: Collection<ItemRef>): Set<String> {
        val prefixes = refs.map { "$view|${it.type.name}:${it.id}|" }
        return entries.orEmpty().filter { e -> e.startsWith(PREFIX) && prefixes.none { e.startsWith(it) } }.toSet()
    }

    /** [entries] without any offset in [view]: its map goes back to the layout's own places. */
    fun reset(entries: Set<String>?, view: String): Set<String> = entries.orEmpty().filter { it.startsWith(PREFIX) && !it.startsWith("$view|") }.toSet()
}
