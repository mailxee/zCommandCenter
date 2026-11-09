package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkRouteTest {
    @Test
    fun `kinds share layers and gaps close up`() {
        val r = ThinkRoute()
        assertEquals(0, r.layer(IdeaKind.VISION))
        assertEquals(1, r.layer(IdeaKind.PRINCIPLE))
        assertEquals(2, r.layer(IdeaKind.QUESTION))
        assertEquals(3, r.depth)
        val gap = ThinkRoute().with(IdeaKind.VISION, 1).with(IdeaKind.BELIEF, 4).with(IdeaKind.PRINCIPLE, 4)
        assertEquals(2, gap.layer(IdeaKind.BELIEF))
    }

    @Test
    fun `the route round trips and bad parts keep their defaults`() {
        val r = ThinkRoute().with(IdeaKind.QUESTION, 4)
        assertEquals(r, ThinkRoute.parse(r.encode()))
        assertEquals(ThinkRoute(), ThinkRoute.parse("VISION:9,NOPE:1"))
    }
}
