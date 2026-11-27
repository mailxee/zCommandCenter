package com.example.xcommandcenter.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.IdeaKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A Word outline read into ideas (§2.80): points and headings by level, bold names, the rest explaining. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordOutlineTest {
    private val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    /** A run of text, bold or not. */
    private fun r(text: String, bold: Boolean = false) =
        "<w:r>${if (bold) "<w:rPr><w:b w:val=\"1\"/></w:rPr>" else ""}<w:t xml:space=\"preserve\">${text.replace("&", "&amp;")}</w:t></w:r>"

    /** A point at list [level]. */
    private fun point(level: Int, vararg runs: String) =
        "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr>${runs.joinToString("")}</w:p>"

    private fun styled(style: String, vararg runs: String) = "<w:p><w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>${runs.joinToString("")}</w:p>"

    private fun plain(vararg runs: String) = "<w:p>${runs.joinToString("")}</w:p>"

    private val styles = """<w:styles xmlns:w="$w">
        <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/></w:style>
        <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style>
        <w:style w:type="paragraph" w:styleId="berschrift2"><w:name w:val="heading 2"/></w:style>
        <w:style w:type="paragraph" w:styleId="ListBullet2"><w:name w:val="List Bullet 2"/></w:style>
        <w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/></w:style>
        </w:styles>"""

    private fun docx(vararg paragraphs: String, withStyles: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            put("[Content_Types].xml", "<Types/>")
            put("word/document.xml", "<w:document xmlns:w=\"$w\"><w:body>${paragraphs.joinToString("")}<w:sectPr/></w:body></w:document>")
            if (withStyles) put("word/styles.xml", styles)
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray) = WordOutline.read(bytes.inputStream(), now = 0)

    private fun List<IdeaEntity>.named(title: String) = single { it.title == title }

    @Test
    fun pointsNestByLevelWithBoldNames() {
        val result = read(
            docx(
                point(0, r("Core Framework: Cheap Cognition", true), r(" — AI makes intelligence abundant.")),
                point(1, r("The Messiness Spectrum:", true), r(" Jobs vary. Example: A cashier versus a manager.")),
                point(1, r("The Autonomy Threshold", true), r(": Where AI works alone.")),
                plain(r("A note that belongs to the threshold.")),
                point(0, r("Part I", true), r(" — Tiers one and two.")),
                point(1, r("Tier One", true), r(" — Single tasks.")),
                point(2, r("Collapse of Verifiable Tasks:", true), r(" Verified outputs train AI fast.")),
                withStyles = false,
            ),
        )
        val ideas = result.network.ideas
        assertEquals(6, ideas.size)
        val core = ideas.named("Core Framework: Cheap Cognition")
        assertEquals(null, core.parentId)
        assertEquals("AI makes intelligence abundant.", core.body)
        val spectrum = ideas.named("The Messiness Spectrum")
        assertEquals(core.id, spectrum.parentId)
        assertEquals("Jobs vary.\n\nExample: A cashier versus a manager.", spectrum.body)
        val threshold = ideas.named("The Autonomy Threshold")
        assertEquals("Where AI works alone.\n\nA note that belongs to the threshold.", threshold.body)
        assertEquals(listOf(0, 1), listOf(spectrum.sortOrder, threshold.sortOrder))
        val part = ideas.named("Part I")
        assertEquals(null, part.parentId)
        assertEquals(1, part.sortOrder)
        assertEquals(ideas.named("Tier One").id, ideas.named("Collapse of Verifiable Tasks").parentId)
        assertTrue(ideas.all { it.kind == IdeaKind.IDEA })
        assertEquals(1, result.joined)
        assertEquals(null, result.title)
    }

    @Test
    fun headingsGoAboveListsAndATitleNamesTheDocument() {
        val result = read(
            docx(
                styled("Title", r("Messy Jobs")),
                styled("Heading1", r("Part I")),
                plain(r("How AI replaces tasks.")),
                styled("berschrift2", r("Tier One")),
                point(0, r("Collapse:", true), r(" Fast.")),
                point(1, r("Detail", true), r(" deeper.")),
                styled("Heading1", r("Part II")),
                point(0, r("Authority", true)),
                styled("ListBullet2", r("Legitimacy", true), r(": a styled point, one level in.")),
                styled("ListParagraph", r("Indented text, not a point.")),
            ),
        )
        assertEquals("Messy Jobs", result.title)
        val ideas = result.network.ideas
        val part1 = ideas.named("Part I")
        assertEquals("How AI replaces tasks.", part1.body)
        val tier = ideas.named("Tier One")
        assertEquals(part1.id, tier.parentId)
        assertEquals(tier.id, ideas.named("Collapse").parentId)
        assertEquals(ideas.named("Collapse").id, ideas.named("Detail").parentId)
        // Under a level-1 heading the list starts one deeper than the heading.
        assertEquals(ideas.named("Part II").id, ideas.named("Authority").parentId)
        // A List Bullet 2 paragraph is a point at the second level; List Paragraph alone is text.
        val legitimacy = ideas.named("Legitimacy")
        assertEquals(ideas.named("Authority").id, legitimacy.parentId)
        assertEquals("a styled point, one level in.\n\nIndented text, not a point.", legitimacy.body)
        assertEquals(0, result.leftOut)
    }

    @Test
    fun pointsWithoutABoldStartAreNamedByTheirText() {
        val ideas = read(
            docx(
                point(0, r("Satiation: demand caps out and spending moves elsewhere.")),
                point(0, r("A short point")),
                point(0, r("Most jobs are clusters of tasks bundled together to save the costs of coordinating between people. Whether a job survives depends on how tightly they interlock and on many other things besides.")),
                withStyles = false,
            ),
        ).network.ideas
        assertEquals("demand caps out and spending moves elsewhere.", ideas.named("Satiation").body)
        assertEquals("", ideas.named("A short point").body)
        val long = ideas.named("Most jobs are clusters of tasks bundled together to save the costs of coordinating between people")
        assertTrue(long.body.startsWith("Whether a job survives"))
    }

    @Test
    fun aFileWithNoOutlineOrNotWordIsRefused() {
        try {
            read(docx(plain(r("Just a paragraph.")), withStyles = false))
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("No outline"))
        }
        try {
            read("not a zip".toByteArray())
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("Word document"))
        }
    }

    @Test
    fun aPreviewedDocumentGoesInUnderItsNewIdea() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val c = (context as XCommandCenterApp).container
        val file = File(context.cacheDir, "Messy Jobs.docx")
        file.writeBytes(docx(point(0, r("One", true), r(" first")), point(1, r("Two", true)), point(0, r("Three", true)), withStyles = false))
        val preview = c.backupRepository.previewIdeas(Uri.fromFile(file), word = true)
        assertEquals("Messy Jobs", preview.rootTitle)
        assertTrue(preview.wrap)
        assertEquals(3, preview.count)
        assertEquals(2, preview.levels)

        val before = c.ideaRepository.observeAll().first().maxOf { it.id }
        val added = c.backupRepository.addPreviewed(preview, "Messy Jobs (book)", null)
        assertEquals(4, added.ideas)
        val copies = c.ideaRepository.observeAll().first().filter { it.id > before }
        val root = copies.named("Messy Jobs (book)")
        assertEquals(null, root.parentId)
        assertEquals(listOf("One", "Three"), copies.filter { it.parentId == root.id }.sortedBy { it.sortOrder }.map { it.title })
        assertEquals(copies.named("One").id, copies.named("Two").parentId)
    }
}
