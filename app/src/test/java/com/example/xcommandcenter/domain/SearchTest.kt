package com.example.xcommandcenter.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(Search.matches("", "anything"))
        assertTrue(Search.matches("   ", "anything"))
    }

    @Test
    fun everyWordMustAppearInSomeFieldIgnoringCase() {
        assertTrue(Search.matches("paint BED", "Paint the bedroom", ""))
        assertTrue(Search.matches("paint green", "Buy paint samples", "Three shades of light green."))
        assertFalse(Search.matches("paint blue", "Buy paint samples", "Three shades of light green."))
    }
}
