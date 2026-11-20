package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkRouteTest {
    @Test
    fun defaultLayers() {
        val r = LinkRoute()
        assertEquals(0, r.layer(ItemType.PLAN))
        assertEquals(1, r.layer(ItemType.TASK))
        assertEquals(1, r.layer(ItemType.HABIT))
        assertEquals(2, r.layer(ItemType.NOTE))
        assertEquals(2, r.layer(ItemType.PERSON))
        assertEquals(2, r.layer(ItemType.IDEA))
    }

    @Test
    fun kinds() {
        val r = LinkRoute()
        assertEquals(LinkRoute.Kind.ON_ROUTE, r.kind(ItemType.TASK, ItemType.PLAN))
        assertEquals(LinkRoute.Kind.SIDEWAYS, r.kind(ItemType.TASK, ItemType.HABIT))
        // End points hang anywhere.
        assertEquals(LinkRoute.Kind.ON_ROUTE, r.kind(ItemType.PLAN, ItemType.NOTE))
        // With habits a layer below tasks, a plan linked straight to a habit skips one.
        val deeper = r.with(ItemType.HABIT, 3)
        assertEquals(LinkRoute.Kind.SKIPS, deeper.kind(ItemType.PLAN, ItemType.HABIT))
    }

    @Test
    fun gapsCloseUp() {
        val r = LinkRoute(mapOf(ItemType.PLAN to 1, ItemType.TASK to 3, ItemType.HABIT to 4))
        assertEquals(1, r.layer(ItemType.TASK))
        assertEquals(LinkRoute.Kind.ON_ROUTE, r.kind(ItemType.PLAN, ItemType.TASK))
        assertEquals(3, r.depth)
    }

    @Test
    fun encodeAndParse() {
        val r = LinkRoute().with(ItemType.HABIT, 3)
        assertEquals(r, LinkRoute.parse(r.encode()))
        // A route stored while goals were there still reads, the goal dropped.
        assertEquals(LinkRoute().with(ItemType.PLAN, 2).with(ItemType.TASK, 3).with(ItemType.HABIT, 3), LinkRoute.parse("GOAL:1,PLAN:2,TASK:3,HABIT:3"))
    }
}
