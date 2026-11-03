package com.example.xcommandcenter.domain

/**
 * The suggested route of links: which layer each linking type sits in, top to bottom (by default
 * Goals, then Plans, then Tasks and Habits); end points (notes, quotes, books, people, blocks) are
 * always the last layer. Links between neighbouring layers are on route; links within one layer
 * run sideways, and links that jump a layer skip it. Only an input to the map's rows and to
 * [LinkAnalysis]: linking is never blocked.
 */
data class LinkRoute(val layers: Map<ItemType, Int> = DEFAULT) {
    enum class Kind { ON_ROUTE, SIDEWAYS, SKIPS }

    /** Layer numbers as set, closed up so gaps don't count (1, 3 reads as 1, 2). */
    private val compact: Map<ItemType, Int> = run {
        val used = types.map { layers[it] ?: DEFAULT.getValue(it) }.distinct().sorted()
        types.associateWith { used.indexOf(layers[it] ?: DEFAULT.getValue(it)) }
    }

    /** How many layers the linking types fill; end points come after them. */
    val depth: Int get() = compact.values.max() + 1

    /** The layer of [type], from 0 at the top; end points are [depth]. */
    fun layer(type: ItemType): Int = compact[type] ?: depth

    fun isEndPoint(type: ItemType): Boolean = type in LinkNetwork.endPoints

    /** A link to an end point is always on route: end points close the route wherever they hang. */
    fun kind(a: ItemType, b: ItemType): Kind {
        if (isEndPoint(a) || isEndPoint(b)) return Kind.ON_ROUTE
        val gap = kotlin.math.abs(layer(a) - layer(b))
        return when (gap) {
            0 -> Kind.SIDEWAYS
            1 -> Kind.ON_ROUTE
            else -> Kind.SKIPS
        }
    }

    /** Stored as "GOAL:1,PLAN:2,TASK:3,HABIT:3". */
    fun encode(): String = types.joinToString(",") { "${it.name}:${layers[it] ?: DEFAULT.getValue(it)}" }

    fun with(type: ItemType, layer: Int): LinkRoute = LinkRoute(types.associateWith { layers[it] ?: DEFAULT.getValue(it) } + (type to layer))

    companion object {
        /** The types links pass through, in their default order; each can be put in any layer. */
        val types = listOf(ItemType.GOAL, ItemType.PLAN, ItemType.TASK, ItemType.HABIT)

        /** The layers offered for each type. */
        val choices = 1..4

        val DEFAULT = mapOf(ItemType.GOAL to 1, ItemType.PLAN to 2, ItemType.TASK to 3, ItemType.HABIT to 3)

        /** Reads [encode]'s form; anything unreadable keeps its default. */
        fun parse(text: String?): LinkRoute {
            if (text.isNullOrBlank()) return LinkRoute()
            val read = text.split(',').mapNotNull { part ->
                val (name, n) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val type = types.firstOrNull { it.name == name } ?: return@mapNotNull null
                val layer = n.toIntOrNull()?.takeIf { it in choices } ?: return@mapNotNull null
                type to layer
            }.toMap()
            return LinkRoute(DEFAULT + read)
        }
    }
}
