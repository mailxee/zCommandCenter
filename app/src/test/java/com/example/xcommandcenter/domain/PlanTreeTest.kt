package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanTreeTest {
    private data class Node(val id: Long, val parent: Long?)

    private val nodes = listOf(Node(1, null), Node(2, 1), Node(3, 2), Node(4, null), Node(5, 1))

    @Test
    fun `flatten is depth first with depths`() {
        val rows = PlanTree.flatten(nodes, Node::id, Node::parent)
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L), rows.map { it.node.id })
        assertEquals(listOf(0, 1, 2, 1, 0), rows.map { it.depth })
    }

    @Test
    fun `flatten lists collapsed nodes but nothing below them`() {
        val rows = PlanTree.flatten(nodes, Node::id, Node::parent, collapsed = setOf(2L))
        assertEquals(listOf(1L, 2L, 5L, 4L), rows.map { it.node.id })
        assertEquals(listOf(1L, 4L), PlanTree.flatten(nodes, Node::id, Node::parent, collapsed = setOf(1L)).map { it.node.id })
    }

    @Test
    fun `subtree contains root and all descendants`() {
        assertEquals(setOf(1L, 2L, 3L, 5L), PlanTree.subtreeIds(1, nodes, Node::id, Node::parent))
        assertEquals(setOf(4L), PlanTree.subtreeIds(4, nodes, Node::id, Node::parent))
    }

    @Test
    fun `ancestors walk up to the root`() {
        assertEquals(setOf(2L, 1L), PlanTree.ancestorIds(3, nodes, Node::id, Node::parent))
        assertEquals(emptySet<Long>(), PlanTree.ancestorIds(4, nodes, Node::id, Node::parent))
    }

    @Test
    fun `ancestors stop on cyclic data`() {
        val cyclic = listOf(Node(1, 3), Node(2, 1), Node(3, 2))
        assertEquals(setOf(1L, 3L), PlanTree.ancestorIds(2, cyclic, Node::id, Node::parent))
    }
}
