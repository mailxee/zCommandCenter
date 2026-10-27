package com.example.xcommandcenter.domain

/** A web address found in free text: where it is, and the URL to open. */
data class WebLink(val range: IntRange, val url: String)

/** Finds web addresses (http://, https:// or www.) in descriptions and notes, so they can be opened. */
object WebLinks {
    private val pattern = Regex("""\b(?:https?://|www\.)[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** Punctuation that usually ends the sentence around a link rather than the link itself. */
    private const val TRAILING = ".,;:!?'\")]}"

    fun find(text: String): List<WebLink> = pattern.findAll(text).mapNotNull { match ->
        val raw = match.value.trimEnd { it in TRAILING }
        // A closing bracket belongs to the link when the link opened one, as in Wikipedia URLs.
        val value = if (raw.count { it == '(' } > raw.count { it == ')' } && match.value.getOrNull(raw.length) == ')') {
            raw + ")"
        } else {
            raw
        }
        if (value.length <= 4) return@mapNotNull null
        val url = if (value.startsWith("www.", ignoreCase = true)) "https://$value" else value
        WebLink(match.range.first until match.range.first + value.length, url)
    }.toList()
}
