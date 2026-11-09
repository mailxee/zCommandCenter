package com.example.xcommandcenter.domain

/** How a kind of link is drawn on the map. */
enum class LineStyle(val label: String) {
    SOLID("Solid"),
    DASHED("Dashed"),
    DOTTED("Dotted"),
}

/**
 * What the map leaves out and how it draws each kind of link (the map's Show panel). A kind of
 * link is named by a key: [PLAIN] for links without a label, [UNDER] for an idea's line to the
 * idea it is under, and [label] for each link label.
 */
data class MapFilter(
    val hiddenTypes: Set<ItemType> = emptySet(),
    val hiddenLinks: Set<String> = emptySet(),
    val styles: Map<String, LineStyle> = emptyMap(),
) {
    val active: Boolean get() = hiddenTypes.isNotEmpty() || hiddenLinks.isNotEmpty()

    fun shows(type: ItemType): Boolean = type !in hiddenTypes

    fun shows(kind: String): Boolean = kind !in hiddenLinks

    /** The style picked for [kind], or its default: dotted for Contradicts, solid for the rest. */
    fun styleOf(kind: String, labelKey: String? = null): LineStyle =
        styles[kind] ?: if (labelKey == LinkLabels.CONTRADICTS) LineStyle.DOTTED else LineStyle.SOLID

    /**
     * What is left to draw of [nodes] and [edges] (pairs of items with their kind of link): items
     * of hidden tabs and links of hidden kinds go. While anything is hidden, an item left with no
     * line goes too, unless it is [root], the item whose network is shown.
     */
    fun <E> apply(nodes: Set<ItemRef>, edges: List<E>, root: ItemRef?, ends: (E) -> Pair<ItemRef, ItemRef>, kind: (E) -> String): Pair<Set<ItemRef>, List<E>> {
        val keptNodes = nodes.filter { shows(it.type) || it == root }.toSet()
        val keptEdges = edges.filter { e -> shows(kind(e)) && ends(e).let { (a, b) -> a in keptNodes && b in keptNodes } }
        if (!active) return keptNodes to keptEdges
        val touched = keptEdges.flatMap { ends(it).toList() }.toSet()
        return keptNodes.filter { it in touched || it == root }.toSet() to keptEdges
    }

    companion object {
        const val PLAIN = "plain"
        const val UNDER = "under"

        fun label(id: Long): String = "label:$id"

        /** "under=SOLID" entries, as stored in the settings. */
        fun encodeStyles(styles: Map<String, LineStyle>): Set<String> = styles.map { (k, v) -> "$k=${v.name}" }.toSet()

        fun parseStyles(entries: Set<String>?): Map<String, LineStyle> = entries.orEmpty().mapNotNull { entry ->
            val at = entry.lastIndexOf('=')
            if (at <= 0) return@mapNotNull null
            val style = LineStyle.entries.firstOrNull { it.name == entry.substring(at + 1) } ?: return@mapNotNull null
            entry.substring(0, at) to style
        }.toMap()

        fun parseTypes(names: Set<String>?): Set<ItemType> = names.orEmpty().mapNotNull { n -> ItemType.entries.firstOrNull { it.name == n } }.toSet()
    }
}
