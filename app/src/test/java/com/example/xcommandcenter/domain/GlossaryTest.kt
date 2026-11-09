package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryTest {
    @Test
    fun `every kind, tab and starting label has a definition`() {
        IdeaKind.entries.forEach { assertTrue(Glossary.default(it).isNotBlank()) }
        ItemType.entries.forEach { assertTrue(Glossary.default(it).isNotBlank()) }
        LinkLabels.defaults.forEach { assertTrue(it.key, Glossary.defaultForLabel(it.key).isNotBlank()) }
        assertEquals("", Glossary.defaultForLabel(null))
    }

    @Test
    fun `a rewritten definition wins over the starting one`() {
        val terms = Glossary.Terms(mapOf(Glossary.kindKey(IdeaKind.BELIEF) to "Mine", Glossary.labelKey(9) to "Echoes it"))
        assertEquals("Mine", terms.of(IdeaKind.BELIEF))
        assertEquals(Glossary.default(IdeaKind.VISION), terms.of(IdeaKind.VISION))
        assertEquals("Echoes it", terms.ofLabel(9, null))
        assertEquals(Glossary.defaultForLabel("SUPPORTS"), terms.ofLabel(1, "SUPPORTS"))
        assertTrue(terms.isEdited(Glossary.kindKey(IdeaKind.BELIEF)))
        assertFalse(terms.isEdited(Glossary.typeKey(ItemType.GOAL)))
    }
}
