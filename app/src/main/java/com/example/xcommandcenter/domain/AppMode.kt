package com.example.xcommandcenter.domain

/**
 * The app's two views of the same items: [DO] manages goals, plans, tasks and habits; [THINK]
 * ponders visions, beliefs and ideas with their sources. Each has its own tabs ([TabMode]) and its
 * own network: the types its links pass through ([through]); anything else ends a route there.
 */
enum class AppMode(val label: String) {
    DO("Do"),
    THINK("Think"),
    ;

    /** The types this mode's networks run through; the rest are its end points. */
    val through: Set<ItemType>
        get() = when (this) {
            DO -> setOf(ItemType.GOAL, ItemType.PLAN, ItemType.TASK, ItemType.HABIT)
            THINK -> setOf(ItemType.IDEA)
        }

    val other: AppMode get() = if (this == DO) THINK else DO
}

/** Which mode shows a tab. */
enum class TabMode(val label: String) {
    DO("Do"),
    BOTH("Both"),
    THINK("Think"),
    ;

    fun shows(mode: AppMode): Boolean = this == BOTH || name == mode.name
}

/** Each tab's mode, with defaults for tabs the user hasn't set; stored as "TASK:DO,NOTE:BOTH". */
data class TabModes(val modes: Map<ItemType, TabMode> = emptyMap()) {
    fun of(tab: ItemType): TabMode = modes[tab.tab] ?: DEFAULT[tab.tab] ?: TabMode.BOTH

    fun with(tab: ItemType, mode: TabMode) = TabModes(modes + (tab to mode))

    /** The tabs [mode] shows, in [order]. */
    fun tabs(order: List<ItemType>, mode: AppMode): List<ItemType> = order.filter { of(it).shows(mode) }

    /**
     * Whether [type]'s items belong to the other mode only, seen from [mode]: a plan seen from
     * Think, an idea seen from Do. Shown with ⇄ where they appear as end points.
     */
    fun isOtherMode(type: ItemType, mode: AppMode): Boolean = !of(type).shows(mode)

    fun encode(): String = ItemType.tabs.joinToString(",") { "${it.name}:${of(it).name}" }

    companion object {
        val DEFAULT = mapOf(
            ItemType.BLOCK to TabMode.DO, ItemType.GOAL to TabMode.DO, ItemType.PLAN to TabMode.DO, ItemType.TASK to TabMode.DO,
            ItemType.HABIT to TabMode.DO, ItemType.NOTE to TabMode.BOTH, ItemType.PERSON to TabMode.BOTH,
            ItemType.IDEA to TabMode.THINK, ItemType.QUOTE to TabMode.THINK, ItemType.BOOK to TabMode.THINK,
        )

        /** Reads [encode]'s form; anything unreadable keeps its default. */
        fun parse(text: String?): TabModes = TabModes(
            text.orEmpty().split(',').mapNotNull { part ->
                val (name, mode) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val tab = ItemType.tabs.firstOrNull { it.name == name } ?: return@mapNotNull null
                tab to (TabMode.entries.firstOrNull { it.name == mode } ?: return@mapNotNull null)
            }.toMap(),
        )
    }
}
