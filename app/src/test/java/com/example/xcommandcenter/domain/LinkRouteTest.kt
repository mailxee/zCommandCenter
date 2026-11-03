package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkRouteTest {
    @Test
    fun defaultLayers() {
        val r = LinkRoute()
        assertEquals(0, r.layer(ItemType.GOAL))
        assertEquals(1, r.layer(ItemType.PLAN))
        assertEquals(2, r.layer(ItemType.TASK))
        assertEquals(2, r.layer(ItemType.HABIT))
        assertEquals(3, r.layer(ItemType.NOTE))
        assertEquals(3, r.layer(ItemType.PERSON))
    }

    @Test
    fun kinds() {
        val r = LinkRoute()
        assertEquals(LinkRoute.Kind.ON_ROUTE, r.kind(ItemType.GOAL, ItemType.PLAN))
        assertEquals(LinkRoute.Kind.ON_ROUTE, r.kind(ItemType.TASK, ItemType.PLAN))
        assertEquals(LinkRoute.Kind.SKIPS, r.kind(ItemType.GOAL, ItemType.TASK))
        assertEquals(LinkRoute.Kind.SIDEWAYS, r.kind(ItemType.TASK, ItemType.HABIT))
        // End points hang anywhere.
        assertEquals(LinkRoute.Kind.ON_ROUTE, r.kind(ItemType.GOAL, ItemType.NOTE))
    }

    @Test
    fun gapsCloseUp() {
        val r = LinkRoute(mapOf(ItemType.GOAL to 1, ItemType.PLAN to 3, ItemType.TASK to 4, ItemType.HABIT to 4))
        assertEquals(1, r.layer(ItemType.PLAN))
        assertEquals(LinkRoute.Kind.ON_ROUTE, r.kind(ItemType.GOAL, ItemType.PLAN))
        assertEquals(3, r.depth)
    }

    @Test
    fun encodeAndParse() {
        val r = LinkRoute().with(ItemType.HABIT, 2)
        assertEquals(r, LinkRoute.parse(r.encode()))
        assertEquals(LinkRoute(), LinkRoute.parse(null))
        assertEquals(LinkRoute(), LinkRoute.parse("GOAL:9,NOPE:1,junk"))
    }
}
