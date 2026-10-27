package com.example.xcommandcenter.ui.home

import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagFilterTest {
    private val home = TagEntity(1, "home")
    private val work = TagEntity(2, "work")
    private val a = ItemRef(ItemType.TASK, 1) // home
    private val b = ItemRef(ItemType.TASK, 2) // home, work
    private val c = ItemRef(ItemType.TASK, 3) // no tags
    private val byItem = mapOf(a to listOf(home), b to listOf(home, work))

    private fun shown(filter: TagFilter) = listOf(a, b, c).filter(filter::matches)

    @Test
    fun selectedTagsKeepOnlyItemsWithAllOfThem() {
        assertEquals(listOf(a, b), shown(TagFilter(byItem, selected = setOf(1))))
        assertEquals(listOf(b), shown(TagFilter(byItem, selected = setOf(1, 2))))
    }

    @Test
    fun excludedTagsHideItemsWithAnyOfThem() {
        val filter = TagFilter(byItem, excluded = setOf(2))
        assertTrue(filter.active)
        assertEquals(listOf(a, c), shown(filter))
        assertTrue(filter.excludes(b))
        assertFalse(filter.excludes(c))
    }

    @Test
    fun untaggedSelectedShowsOnlyItemsWithoutTags() {
        assertEquals(listOf(c), shown(TagFilter(byItem, selected = setOf(TagFilter.UNTAGGED))))
        // No item both has the home tag and has no tags.
        assertEquals(emptyList<ItemRef>(), shown(TagFilter(byItem, selected = setOf(1, TagFilter.UNTAGGED))))
    }

    @Test
    fun untaggedExcludedHidesItemsWithoutTags() {
        assertEquals(listOf(a, b), shown(TagFilter(byItem, excluded = setOf(TagFilter.UNTAGGED))))
        assertEquals(listOf(a), shown(TagFilter(byItem, excluded = setOf(2, TagFilter.UNTAGGED))))
    }

    @Test
    fun bothTogether() {
        assertEquals(listOf(a), shown(TagFilter(byItem, selected = setOf(1), excluded = setOf(2))))
    }
}
