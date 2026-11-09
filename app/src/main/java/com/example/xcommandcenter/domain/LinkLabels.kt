package com.example.xcommandcenter.domain

/** The labels the app starts with, and how a link's label reads from either end. */
object LinkLabels {
    data class Default(val key: String, val forward: String, val backward: String?)

    const val CONTRADICTS = "CONTRADICTS"
    const val LEADS_TO = "LEADS_TO"
    const val SOURCE = "SOURCE"

    val defaults = listOf(
        Default("SUPPORTS", "supports", "supported by"),
        Default(CONTRADICTS, "contradicts", null),
        Default("INSPIRED", "inspired", "inspired by"),
        Default(LEADS_TO, "leads to", "comes from"),
        Default("EXAMPLE", "example of", "has example"),
        Default(SOURCE, "source of", "from"),
        Default("DEPENDS", "depends on", "needed by"),
    )

    /**
     * Whether [self], one end of a link stored as A–B, is the label's subject: the label reads
     * forward from A when [fromA], else from B.
     */
    fun isSubject(selfIsA: Boolean, fromA: Boolean): Boolean = selfIsA == fromA
}
