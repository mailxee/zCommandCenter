package com.example.xcommandcenter.domain

/**
 * Think mode's suggested route: a layer, 1 to 4, for each idea kind (by default visions 1, beliefs
 * and principles 2, ideas and questions 3). Sources (notes, quotes, books, people) come after the
 * ideas, and items of Do mode after them. Only an input to the map's rows, like [LinkRoute].
 */
data class ThinkRoute(val layers: Map<IdeaKind, Int> = DEFAULT) {
    private val compact: Map<IdeaKind, Int> = run {
        val used = IdeaKind.entries.map { layerSet(it) }.distinct().sorted()
        IdeaKind.entries.associateWith { used.indexOf(layerSet(it)) }
    }

    /** The layer as set, 1 to 4. */
    fun layerSet(kind: IdeaKind): Int = layers[kind] ?: DEFAULT.getValue(kind)

    /** How many rows the kinds fill. */
    val depth: Int get() = compact.values.max() + 1

    /** The map row of an idea of [kind], from 0 at the top. */
    fun layer(kind: IdeaKind): Int = compact.getValue(kind)

    fun with(kind: IdeaKind, layer: Int) = ThinkRoute(IdeaKind.entries.associateWith { layerSet(it) } + (kind to layer))

    /** Stored as "VISION:1,BELIEF:2,…". */
    fun encode(): String = IdeaKind.entries.joinToString(",") { "${it.name}:${layerSet(it)}" }

    companion object {
        val DEFAULT = mapOf(
            IdeaKind.VISION to 1, IdeaKind.BELIEF to 2, IdeaKind.PRINCIPLE to 2, IdeaKind.IDEA to 3, IdeaKind.QUESTION to 3,
        )

        /** Reads [encode]'s form; anything unreadable keeps its default. */
        fun parse(text: String?): ThinkRoute = ThinkRoute(
            DEFAULT + text.orEmpty().split(',').mapNotNull { part ->
                val (name, n) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val kind = IdeaKind.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                kind to (n.toIntOrNull()?.takeIf { it in LinkRoute.choices } ?: return@mapNotNull null)
            },
        )
    }
}
