package com.example.xcommandcenter.domain

/**
 * Every kind of thing that can take part in a link. The stored name is what goes into the
 * `links` table and the `item_index` view, so renaming an entry requires a migration.
 *
 * [CHAPTER] is linkable but is not a tab: chapters live inside the Books tab.
 */
enum class ItemType(val tabLabel: String, val singular: String, val isTab: Boolean = true) {
    TASK("Tasks", "Task"),
    GOAL("Goals", "Goal"),
    HABIT("Habits", "Habit"),
    PROJECT("Projects", "Project"),
    NOTE("Notes", "Note"),
    BOOK("Books", "Book"),
    QUOTE("Quotes", "Quote"),
    CHAPTER("Chapters", "Chapter", isTab = false);

    /** The tab an item of this type is shown under. */
    val tab: ItemType get() = if (this == CHAPTER) BOOK else this

    companion object {
        val tabs: List<ItemType> = entries.filter { it.isTab }
    }
}

/** Identifies one item in any table. */
data class ItemRef(val type: ItemType, val id: Long)
