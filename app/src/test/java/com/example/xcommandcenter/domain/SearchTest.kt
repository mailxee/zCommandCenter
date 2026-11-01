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

    @Test
    fun scopeAlsoLooksThroughTheItemsExtraText() {
        val task = ItemRef(ItemType.TASK, 1)
        val scope = SearchScope("milk", mapOf(task to listOf("Eggs", "Milk")))
        assertTrue(scope.matches(task, "Groceries", ""))
        assertFalse(scope.matches(ItemRef(ItemType.TASK, 2), "Groceries", ""))
        // Words may come from the item and its extra text together.
        assertTrue(SearchScope("groceries milk", scope.extra).matches(task, "Groceries"))
    }

    @Test
    fun blankScopeMatchesEverything() {
        assertTrue(SearchScope().matches(ItemRef(ItemType.NOTE, 1), "anything"))
    }
}
