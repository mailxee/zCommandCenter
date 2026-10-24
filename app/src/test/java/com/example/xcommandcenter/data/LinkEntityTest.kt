package com.example.xcommandcenter.data

import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkEntityTest {
    @Test
    fun `link is the same in both directions`() {
        val task = ItemRef(ItemType.TASK, 3)
        val book = ItemRef(ItemType.BOOK, 1)
        assertEquals(LinkEntity.between(task, book).copy(createdAt = 0), LinkEntity.between(book, task).copy(createdAt = 0))
    }

    @Test
    fun `same-tab links order by id`() {
        val link = LinkEntity.between(ItemRef(ItemType.NOTE, 9), ItemRef(ItemType.NOTE, 2))
        assertEquals(2L, link.aId)
        assertEquals(9L, link.bId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `self link is rejected`() {
        LinkEntity.between(ItemRef(ItemType.GOAL, 1), ItemRef(ItemType.GOAL, 1))
    }
}
