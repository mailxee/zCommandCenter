package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.LinkAnalysis.Kind
import com.example.xcommandcenter.domain.LinkAnalysis.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAnalysisTest {
    private val goal = ItemRef(ItemType.GOAL, 1)
    private val reading = ItemRef(ItemType.PLAN, 1)
    private val practice = ItemRef(ItemType.PLAN, 2)
    private val meditations = ItemRef(ItemType.TASK, 1)
    private val journal = ItemRef(ItemType.TASK, 2)
    private val review = ItemRef(ItemType.HABIT, 1)
    private val book = ItemRef(ItemType.BOOK, 1)
    private val chapter = ItemRef(ItemType.CHAPTER, 7)
    private val notes = ItemRef(ItemType.NOTE, 1)
    private var nextId = 1L
    private fun link(a: ItemRef, b: ItemRef) = Link(nextId, a, b, createdAt = nextId++)
    private fun analyze(vararg links: Link, kept: Set<String> = emptySet()) =
        LinkAnalysis.analyze(links.toList(), mapOf(7L to 1L), LinkRoute(), kept)

    @Test
    fun anUnlabelledLinkIsSuggestedBeforeALabelledOne() {
        // The shortcut is labelled, so one of the unlabelled links through the plan is the one left over.
        val shortcut = link(goal, meditations).copy(labelled = true)
        val r = analyze(link(goal, reading), link(reading, meditations), shortcut)
        val removed = r.findings.single().remove!!
        assertTrue(removed != shortcut && !removed.labelled)
    }

    @Test
    fun aContradictionIsNeverARoute() {
        val r = analyze(link(goal, reading), link(reading, meditations), link(goal, meditations).copy(labelled = true, contradiction = true))
        assertTrue(r.findings.isEmpty())
    }

    @Test
    fun aLabelledLinkThatSkipsALayerGetsNoBetterPlace() {
        val r = analyze(link(goal, reading), link(goal, journal).copy(labelled = true))
        assertTrue(r.findings.none { it.kind == Kind.SKIPS_LAYER })
    }

    @Test
    fun aTreeHasNoFindings() {
        val r = analyze(link(goal, reading), link(reading, meditations), link(meditations, book), link(goal, practice))
        assertTrue(r.findings.isEmpty())
        assertEquals(0, r.extraRoutes)
    }

    @Test
    fun aDirectGoalToTaskBesideItsPlanIsAShortcut() {
        val shortcut = link(goal, meditations)
        val r = analyze(link(goal, reading), link(reading, meditations), shortcut)
        val f = r.findings.single()
        assertEquals(Kind.SHORTCUT, f.kind)
        assertEquals(shortcut, f.remove)
        assertEquals(listOf(reading), f.via)
        assertEquals(1, r.extraRoutes)
    }

    @Test
    fun theShortcutIsChosenEvenWhenOlder() {
        // The direct link came first; the on-route ones are still the ones kept.
        val shortcut = link(goal, meditations)
        val r = analyze(shortcut, link(goal, reading), link(reading, meditations))
        assertEquals(shortcut.id, r.findings.single().remove?.id)
    }

    @Test
    fun aSidewaysLinkInALoopIsOffRoute() {
        val sideways = link(meditations, journal)
        val r = analyze(link(goal, reading), link(reading, meditations), link(goal, practice), link(practice, journal), sideways)
        val f = r.findings.single()
        assertEquals(Kind.OFF_ROUTE_LOOP, f.kind)
        assertEquals(sideways, f.remove)
        assertEquals(listOf(reading, goal, practice).toSet(), f.via.toSet())
    }

    @Test
    fun aTaskUnderTwoPlansOfOneGoalIsAnExtraRouteAndTheNewerLinkGoes() {
        val newer = link(goal, reading)
        val r = analyze(link(goal, practice), link(practice, meditations), newer, link(reading, meditations))
        val f = r.findings.single()
        assertEquals(Kind.EXTRA_ROUTE, f.kind)
        assertEquals(4L, f.remove?.id)
    }

    @Test
    fun aSkippedLayerSuggestsAPlanAlreadyUnderTheGoal() {
        val skip = link(goal, review)
        val r = analyze(link(goal, practice), skip)
        val f = r.findings.single()
        assertEquals(Kind.SKIPS_LAYER, f.kind)
        assertEquals(review to practice, f.add)
        assertEquals(skip, f.remove)
    }

    @Test
    fun aSkippedLayerWithNoPlanToUseSuggestsNoChange() {
        val f = analyze(link(goal, review)).findings.single()
        assertEquals(Kind.SKIPS_LAYER, f.kind)
        assertNull(f.add)
        assertNull(f.remove)
        assertEquals(1, f.missingLayer)
    }

    @Test
    fun endPointsCloseNoLoopButASharedOneIsNoted() {
        val r = analyze(link(goal, reading), link(reading, meditations), link(goal, practice), link(practice, journal), link(meditations, notes), link(journal, notes))
        val f = r.findings.single()
        assertEquals(Kind.SHARED_END_POINT, f.kind)
        assertEquals(notes, f.endPoint)
        assertTrue(!f.actionable)
        assertEquals(0, r.extraRoutes)
    }

    @Test
    fun anEndPointSharedAcrossSeparateNetworksIsNotNoted() {
        assertTrue(analyze(link(meditations, notes), link(journal, notes)).findings.isEmpty())
    }

    @Test
    fun aChapterCountsAsItsBook() {
        val r = analyze(link(goal, reading), link(reading, meditations), link(goal, practice), link(practice, journal), link(meditations, book), link(journal, chapter))
        assertEquals(book, r.findings.single().endPoint)
    }

    @Test
    fun keptFindingsMoveAside() {
        val shortcut = link(goal, meditations)
        val r = analyze(link(goal, reading), link(reading, meditations), shortcut, kept = setOf("link:${shortcut.id}"))
        assertTrue(r.findings.isEmpty())
        assertEquals(1, r.kept.size)
        assertEquals(1, r.extraRoutes)
    }

    @Test
    fun findingsAreOrderedByImportance() {
        val r = analyze(
            link(goal, reading), link(reading, meditations), link(goal, meditations),
            link(goal, practice), link(practice, journal), link(meditations, journal),
            link(goal, review),
            link(meditations, notes), link(journal, notes),
        )
        assertEquals(listOf(Kind.SHORTCUT, Kind.OFF_ROUTE_LOOP, Kind.SKIPS_LAYER, Kind.SHARED_END_POINT), r.findings.map { it.kind })
        assertEquals(2, r.extraRoutes)
    }
}
