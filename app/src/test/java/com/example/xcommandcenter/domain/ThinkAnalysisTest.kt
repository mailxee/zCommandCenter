package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.LinkAnalysis.Kind
import com.example.xcommandcenter.domain.LinkAnalysis.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkAnalysisTest {
    private fun idea(id: Long) = ItemRef(ItemType.IDEA, id)
    private fun quote(id: Long) = ItemRef(ItemType.QUOTE, id)
    private val person = ItemRef(ItemType.PERSON, 1)
    private val plan = ItemRef(ItemType.PLAN, 1)
    private var next = 1L
    private fun link(a: ItemRef, b: ItemRef) = Link(next++, a, b)

    // 1 vision > 2 belief > 3 principle; 4 a belief on its own.
    private val ideas = listOf(
        ThinkAnalysis.Idea(1, null, IdeaKind.VISION), ThinkAnalysis.Idea(2, 1, IdeaKind.BELIEF),
        ThinkAnalysis.Idea(3, 2, IdeaKind.PRINCIPLE), ThinkAnalysis.Idea(4, null, IdeaKind.BELIEF),
    )

    private fun analyze(vararg links: Link, authors: Map<Long, String> = emptyMap(), kept: Set<String> = emptySet()) =
        ThinkAnalysis.analyze(links.toList(), ideas, setOf(7L, 8L), authors, mapOf(1L to "Epictetus"), kept)

    @Test
    fun `a belief is acted on through the ideas under it`() {
        val r = analyze(link(idea(3), plan))
        val notActed = r.findings.filter { it.kind == Kind.BELIEF_NOT_ACTED }.map { it.endPoint }
        assertEquals(listOf(idea(4)), notActed)
    }

    @Test
    fun `quotes without an idea and ideas without a source are found`() {
        val r = analyze(link(quote(7), idea(2)), link(idea(3), person))
        assertEquals(listOf(quote(8)), r.findings.filter { it.kind == Kind.UNPLACED_QUOTE }.map { it.endPoint })
        // Visions aren't expected to have a source; the belief has its quote, the principle its person.
        assertEquals(listOf(idea(4)), r.findings.filter { it.kind == Kind.UNGROUNDED_IDEA }.map { it.endPoint })
    }

    @Test
    fun `a quote by someone in People is offered a link to them, once`() {
        val r = analyze(authors = mapOf(7L to " epictetus "))
        val f = r.findings.single { it.kind == Kind.AUTHOR_IS_PERSON }
        assertEquals(person to quote(7), f.add)
        assertTrue(analyze(link(person, quote(7)), authors = mapOf(7L to "Epictetus")).findings.none { it.kind == Kind.AUTHOR_IS_PERSON })
    }

    @Test
    fun `contradictions are noted and kept ones hide`() {
        val contra = link(idea(2), idea(4)).copy(labelled = true, contradiction = true)
        val r = analyze(contra)
        assertEquals(contra, r.findings.single { it.kind == Kind.CONTRADICTION }.link)
        assertTrue(analyze(contra, kept = setOf("contra:${contra.id}")).findings.none { it.kind == Kind.CONTRADICTION })
    }

    @Test
    fun `goals no idea links to have no why`() {
        val goal1 = ItemRef(ItemType.GOAL, 1)
        val found = ThinkAnalysis.goalsWithoutWhy(listOf(link(idea(1), goal1), link(goal1, plan)), setOf(1, 2))
        assertEquals(listOf(ItemRef(ItemType.GOAL, 2)), found.map { it.endPoint })
    }
}
