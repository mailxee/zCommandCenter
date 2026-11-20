package com.example.xcommandcenter.ui.links

import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.PlanParent
import com.example.xcommandcenter.data.repository.LinkGraph
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.domain.MapStyle
import com.example.xcommandcenter.domain.ThinkAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStateTest {
    private fun idea(id: Long) = ItemRef(ItemType.IDEA, id)
    private fun plan(id: Long) = ItemRef(ItemType.PLAN, id)

    private fun state(mode: AppMode, under: Map<ItemRef, ItemRef>, stored: Map<Long, LinkEntity> = emptyMap()) = MapState(
        mode = mode, top = MapStyle.default(mode), root = null, nodes = emptyList(), edges = emptyList(),
        route = LinkRoute(), findings = emptyList(), stored = stored, under = under,
    )

    @Test
    fun anItemGoesUnderAnotherOfItsTabButNeverBelowItself() {
        // 2 and 3 under 1, 4 under 3.
        val s = state(AppMode.THINK, mapOf(idea(2) to idea(1), idea(3) to idea(1), idea(4) to idea(3)))
        assertTrue(s.canPutUnder(idea(2), idea(4)))
        assertFalse("already there", s.canPutUnder(idea(2), idea(1)))
        assertFalse("below itself", s.canPutUnder(idea(3), idea(4)))
        assertFalse("under itself", s.canPutUnder(idea(3), idea(3)))
        assertFalse("another tab", s.canPutUnder(idea(2), ItemRef(ItemType.QUOTE, 1)))
        assertFalse("plans are Do mode's", s.canPutUnder(plan(1), plan(2)))
        assertTrue(state(AppMode.DO, emptyMap()).canPutUnder(plan(1), plan(2)))
    }

    @Test
    fun linkedChecksBothWays() {
        val link = LinkEntity.between(idea(1), ItemRef(ItemType.QUOTE, 7)).copy(id = 5)
        val s = state(AppMode.THINK, emptyMap(), mapOf(5L to link))
        assertTrue(s.linked(ItemRef(ItemType.QUOTE, 7), idea(1)))
        assertFalse(s.linked(idea(1), idea(2)))
    }

    @Test
    fun doModesMapIncludesAPlansSubplans() {
        val goal = ItemRef(ItemType.TASK, 1)
        val entries = listOf(goal, plan(1), plan(2), plan(3)).associateWith { ItemIndexEntry(it.type, it.id, "x", null) }
        val graph = LinkGraph(
            labels = emptyMap(), entries = entries, stored = emptyMap(),
            links = listOf(LinkAnalysis.Link(1, goal, plan(1))), bookOfChapter = emptyMap(), route = LinkRoute(),
            analysis = LinkAnalysis.Result(emptyList(), emptyList()), ideas = listOf(ThinkAnalysis.Idea(9, null, com.example.xcommandcenter.domain.IdeaKind.IDEA)),
            planParents = listOf(PlanParent(2, 1), PlanParent(3, 2)),
        )
        assertEquals(setOf(goal, plan(1), plan(2), plan(3)), graph.nodes(goal, AppMode.DO))
        assertEquals(listOf(plan(2) to plan(1), plan(3) to plan(2)), graph.under(AppMode.DO))
        assertTrue(graph.under(AppMode.THINK).isEmpty())
    }
}
