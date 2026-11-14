package com.example.xcommandcenter.domain

/**
 * How a branch of the map is laid out (§2.70): how an item's sub-items are placed round it, and so
 * on down the branch until an item below chooses another. The map's own (its top item's) is the
 * whole map's unless a branch differs.
 *
 * To add a layout: an entry here and its case in [BranchLayout.arrange]; the map's menus list every
 * entry, and choices are stored by name.
 */
enum class MapStyle(val label: String, val description: String) {
    MIND_MAP("Mind map", "Sub-items in a column beside each item."),
    TREE("Tree", "Sub-items in a row below each item, like a family tree; plain lists stack."),
    OUTLINE("Outline", "Sub-items listed below each item, indented."),
    RINGS("Rings", "Sub-items in a circle round each item."),
    FREE("Free", "Branches spread round each item and pushed apart, so none overlap."),
    BY_ROUTE("By route", "The whole branch in rows by the suggested route: goals, then plans, then tasks…"),
    ;

    companion object {
        fun parse(name: String?): MapStyle? = entries.firstOrNull { it.name == name }

        /** The map's own layout when none was chosen: Mind map in Think, By route in Do. */
        fun default(mode: AppMode): MapStyle = if (mode == AppMode.THINK) MIND_MAP else BY_ROUTE
    }
}

/**
 * Layouts chosen on the map, per mode: for an item (its branch, wherever it is shown) or for the
 * Everything map (key null). Stored as "MODE|TYPE:id|STYLE" or "MODE|all|STYLE" entries.
 */
object MapStyles {
    private fun key(mode: AppMode, ref: ItemRef?) = mode.name + "|" + (ref?.let { "${it.type.name}:${it.id}" } ?: "all")

    fun parse(entries: Set<String>?): Map<AppMode, Map<ItemRef?, MapStyle>> {
        val out = HashMap<AppMode, HashMap<ItemRef?, MapStyle>>()
        entries.orEmpty().forEach { entry ->
            val parts = entry.split('|')
            if (parts.size != 3) return@forEach
            val mode = AppMode.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
            val style = MapStyle.parse(parts[2]) ?: return@forEach
            val ref = if (parts[1] == "all") null else {
                val (type, id) = parts[1].split(':').takeIf { it.size == 2 } ?: return@forEach
                ItemRef(ItemType.entries.firstOrNull { it.name == type } ?: return@forEach, id.toLongOrNull() ?: return@forEach)
            }
            out.getOrPut(mode) { HashMap() }[ref] = style
        }
        return out
    }

    /** [entries] with [ref]'s layout in [mode] set to [style], or back to its default when null. */
    fun set(entries: Set<String>?, mode: AppMode, ref: ItemRef?, style: MapStyle?): Set<String> {
        val prefix = key(mode, ref) + "|"
        val rest = entries.orEmpty().filterNot { it.startsWith(prefix) }.toSet()
        return if (style == null) rest else rest + (prefix + style.name)
    }
}
