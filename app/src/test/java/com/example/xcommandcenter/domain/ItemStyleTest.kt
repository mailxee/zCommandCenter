package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemStyleTest {
    @Test
    fun roundTrip() {
        val style = ItemStyle(StyleColor.TEAL, bold = true, italic = false, underline = true, font = StyleFont.SERIF)
        assertEquals("TEAL|SERIF|1|0|1", style.serialize())
        assertEquals(style, ItemStyle.parse(style.serialize(), ItemStyle()))
    }

    @Test
    fun missingOrUnknownPartsFallBackToTheDefault() {
        val default = ItemStyle.default(ItemType.QUOTE)
        assertEquals(default, ItemStyle.parse(null, default))
        assertEquals(ItemStyle(StyleColor.RED, italic = true), ItemStyle.parse("RED|NOPE", default))
        assertEquals(default, ItemStyle.parse("", default))
    }

    @Test
    fun quotesAreItalicByDefaultAndChaptersFollowBooks() {
        assertTrue(ItemStyle.default(ItemType.QUOTE).italic)
        val styles = ItemStyles(mapOf(ItemType.BOOK to ItemStyle(StyleColor.BROWN)))
        assertEquals(StyleColor.BROWN, styles[ItemType.CHAPTER].color)
        assertEquals(ItemStyle(), styles[ItemType.TASK])
    }

    @Test
    fun summaryNamesWhatIsSet() {
        assertEquals("Default", ItemStyle().summary)
        assertEquals("Blue · Bold · Serif", ItemStyle(StyleColor.BLUE, bold = true, font = StyleFont.SERIF).summary)
    }
}
