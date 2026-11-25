package com.example.xcommandcenter.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.data.entity.IdeaListItemEntity
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A network of ideas goes out to a file and comes back in as copies: its tree, lists, tags, links
 * between its ideas and their labels, with links to anything else left out (§2.78).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IdeaNetworkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c = (context as XCommandCenterApp).container

    private fun idea(id: Long) = ItemRef(ItemType.IDEA, id)

    @Test
    fun aBranchGoesOutAndComesBackAsCopies() = runBlocking {
        val root = c.ideaRepository.save(IdeaEntity(title = "Stoicism", kind = IdeaKind.VISION, body = "A way of life"))
        val virtue = c.ideaRepository.save(IdeaEntity(title = "Virtue", parentId = root, kind = IdeaKind.BELIEF), listOf(IdeaListItemEntity(ideaId = 0, position = 0, text = "Wisdom"), IdeaListItemEntity(ideaId = 0, position = 1, text = "Courage")))
        val vice = c.ideaRepository.save(IdeaEntity(title = "Vice", parentId = root))
        val folly = c.ideaRepository.save(IdeaEntity(title = "Folly", parentId = vice))
        val outside = c.ideaRepository.save(IdeaEntity(title = "Outside the branch"))
        val task = c.taskRepository.save(TaskEntity(title = "Read Seneca"), emptyList(), emptyList())
        c.linkRepository.addLabel("opposes", null)
        c.linkRepository.addLabel("grows into", "grows from")
        val labels = c.linkRepository.labels.first()
        val opposes = labels.first { it.forward == "opposes" }
        val grows = labels.first { it.forward == "grows into" }
        c.linkRepository.link(idea(folly), idea(virtue), opposes.id)
        c.linkRepository.link(idea(virtue), idea(vice), grows.id)
        c.linkRepository.link(idea(virtue), idea(outside), null) // Leaves the branch: not exported.
        c.linkRepository.link(idea(virtue), ItemRef(ItemType.TASK, task), null) // Not an idea: not exported.
        c.tagRepository.add(idea(virtue), "ethics")

        val file = File(context.cacheDir, "network.xlsx")
        assertEquals(4, c.backupRepository.exportIdeas(Uri.fromFile(file), root))

        // As if on another install: "grows into" isn't known (here it was renamed); "opposes" is.
        c.linkRepository.renameLabel(grows.id, "leads to", null)
        val home = c.ideaRepository.save(IdeaEntity(title = "Imported here"))
        val added = c.backupRepository.importIdeas(Uri.fromFile(file), home)
        assertEquals(IdeaNetwork.Added(ideas = 4, links = 2, newLabels = 1), added)

        val all = c.ideaRepository.observeAll().first()
        val copies = all.filter { it.id > home }
        assertEquals(listOf("Stoicism", "Virtue", "Vice", "Folly").sorted(), copies.map { it.title }.sorted())
        val byTitle = copies.associateBy { it.title }
        val newRoot = byTitle.getValue("Stoicism")
        assertEquals(home, newRoot.parentId)
        assertEquals(IdeaKind.VISION, newRoot.kind)
        assertEquals("A way of life", newRoot.body)
        assertEquals(newRoot.id, byTitle.getValue("Virtue").parentId)
        assertEquals(byTitle.getValue("Vice").id, byTitle.getValue("Folly").parentId)
        assertEquals(listOf("Wisdom", "Courage"), c.ideaRepository.listItems(byTitle.getValue("Virtue").id).map { it.text })
        // The originals are untouched.
        assertEquals(root, all.first { it.id == virtue }.parentId)

        val newVirtue = idea(byTitle.getValue("Virtue").id)
        val readings = c.linkRepository.observeLabelsOf(newVirtue).first()
        val afterLabels = c.linkRepository.labels.first()
        val growsAgain = afterLabels.single { it.forward == "grows into" }
        assertEquals("grows from", growsAgain.backward)
        assertEquals(2, readings.size)
        assertEquals(opposes.id, readings.getValue(idea(byTitle.getValue("Folly").id)).label.id)
        val toVice = readings.getValue(idea(byTitle.getValue("Vice").id))
        assertEquals(growsAgain.id, toVice.label.id)
        assertTrue(toVice.subject) // Virtue grows into Vice, as before.
        assertEquals(1, afterLabels.count { it.forward == "opposes" })
        assertEquals(listOf("ethics"), c.tagRepository.observeTags(newVirtue).first().map { it.name })
    }

    @Test
    fun everyIdeaGoesToTheTopLevelAfterTheOthers() = runBlocking {
        val a = c.ideaRepository.save(IdeaEntity(title = "First"))
        c.ideaRepository.save(IdeaEntity(title = "Second"))
        val file = File(context.cacheDir, "all.xlsx")
        val before = c.ideaRepository.observeAll().first()
        assertEquals(before.size, c.backupRepository.exportIdeas(Uri.fromFile(file), null))
        c.backupRepository.importIdeas(Uri.fromFile(file), null)
        val after = c.ideaRepository.observeAll().first()
        assertEquals(before.size * 2, after.size)
        val tops = after.filter { it.parentId == null }
        // The copies come after every top-level idea already there, in their own order.
        val copies = tops.filter { it.id > before.maxOf { b -> b.id } }
        assertTrue(copies.minOf { it.sortOrder } > before.filter { it.parentId == null }.maxOf { it.sortOrder })
        assertEquals(before.filter { it.parentId == null }.map { it.title }, copies.sortedBy { it.sortOrder }.map { it.title })
        assertNull(after.first { it.id == a }.parentId)
    }

    @Test
    fun aFileWithoutIdeasIsRefused() {
        try {
            IdeaNetwork.fromSheets(listOf(Sheet("Tasks", listOf(listOf("Title"), listOf("A task")))))
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("no \"Ideas\" sheet"))
        }
    }

    @Test
    fun labelsMatchByKeyOrWording() {
        val mine = listOf(
            LinkLabelEntity(id = 1, key = "contradicts", forward = "contradicts"),
            LinkLabelEntity(id = 2, forward = "Grows into", backward = "grows from"),
        )
        assertEquals(1L, IdeaNetwork.sameLabel(LinkLabelEntity(id = 9, key = "contradicts", forward = "clashes with"), mine)?.id)
        assertEquals(2L, IdeaNetwork.sameLabel(LinkLabelEntity(id = 9, forward = "grows into ", backward = "Grows from"), mine)?.id)
        assertNull(IdeaNetwork.sameLabel(LinkLabelEntity(id = 9, forward = "grows into"), mine))
    }

    @Test
    fun fileNamesAreSafe() {
        val date = java.time.LocalDate.of(2026, 11, 25)
        assertEquals("Ideas-What-is-good?-2026-11-25.xlsx".replace("?", ""), IdeaNetworkFiles.name("What is good?", date))
        assertEquals("Ideas-2026-11-25.xlsx", IdeaNetworkFiles.name(null, date))
    }
}
