package com.example.xcommandcenter.domain

/** The search box on the main screen. */
object Search {
    /**
     * True when every word of [query] appears, ignoring case, in at least one of [fields] (not
     * necessarily the same one). A blank query matches everything.
     */
    fun matches(query: String, vararg fields: String): Boolean = matches(query, fields.asList())

    fun matches(query: String, fields: List<String>): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.all { word -> fields.any { it.contains(word, ignoreCase = true) } }
    }
}

/**
 * What the search box looks through: [query] as typed, and for each item the text that isn't on
 * the item itself ([extra]: a task's checklist, a note's list, a book's chapters and the item's
 * tag names), so a search finds the item by any of it.
 */
data class SearchScope(val query: String = "", val extra: Map<ItemRef, List<String>> = emptyMap()) {
    val active: Boolean get() = query.isNotBlank()

    /** True when every word appears in the item's own [fields] or its [extra] text. */
    fun matches(ref: ItemRef, vararg fields: String): Boolean =
        !active || Search.matches(query, fields.asList() + extra[ref].orEmpty())
}
