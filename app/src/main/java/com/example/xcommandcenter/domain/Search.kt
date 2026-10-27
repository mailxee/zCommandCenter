package com.example.xcommandcenter.domain

/** The search box on the main screen. */
object Search {
    /**
     * True when every word of [query] appears, ignoring case, in at least one of [fields] (not
     * necessarily the same one). A blank query matches everything.
     */
    fun matches(query: String, vararg fields: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.all { word -> fields.any { it.contains(word, ignoreCase = true) } }
    }
}
