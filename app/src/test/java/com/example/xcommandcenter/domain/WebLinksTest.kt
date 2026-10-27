package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLinksTest {
    @Test
    fun findsLinksAndLeavesOutSentencePunctuation() {
        val text = "See https://example.com/a?b=1, and www.kotlinlang.org."
        val links = WebLinks.find(text)
        assertEquals(listOf("https://example.com/a?b=1", "https://www.kotlinlang.org"), links.map { it.url })
        assertEquals("https://example.com/a?b=1", text.substring(links[0].range))
        assertEquals("www.kotlinlang.org", text.substring(links[1].range))
    }

    @Test
    fun keepsABracketThatBelongsToTheLink() {
        val text = "(see https://en.wikipedia.org/wiki/Kotlin_(programming_language))"
        assertEquals(listOf("https://en.wikipedia.org/wiki/Kotlin_(programming_language)"), WebLinks.find(text).map { it.url })
    }

    @Test
    fun plainTextHasNoLinks() {
        assertTrue(WebLinks.find("Buy milk. e.g. www or http").isEmpty())
    }
}
