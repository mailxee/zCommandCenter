package com.example.xcommandcenter.data

import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.domain.LinkLabels
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

    @Test
    fun `a label reads from its subject whichever end is stored first`() {
        val quote = ItemRef(ItemType.QUOTE, 4)
        val idea = ItemRef(ItemType.IDEA, 2)
        // IDEA sorts before QUOTE, so the quote is side B.
        val supports = LinkEntity.labelled(quote, idea, labelId = 1)
        assertEquals(ItemType.IDEA, supports.aType)
        assertEquals(false, supports.labelFromA)
        assertEquals(true, LinkEntity.labelled(idea, quote, labelId = 1).labelFromA)
        val label = LinkLabelEntity(1, forward = "supports", backward = "supported by")
        assertEquals("supported by", label.wording(LinkLabels.isSubject(selfIsA = true, fromA = supports.labelFromA)))
        assertEquals("supports", label.wording(LinkLabels.isSubject(selfIsA = false, fromA = supports.labelFromA)))
        assertEquals("contradicts", LinkLabelEntity(2, forward = "contradicts").wording(subject = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `self link is rejected`() {
        LinkEntity.between(ItemRef(ItemType.GOAL, 1), ItemRef(ItemType.GOAL, 1))
    }
}
