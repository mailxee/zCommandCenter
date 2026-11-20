package com.example.xcommandcenter.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.ItemTagEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.data.entity.ReminderEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Simplify: goals become visions with their links (labels read the same way), tags and reminders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SimplifyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    @Test
    fun goalsBecomeVisionsKeepingWhatHangsOffThem() = runBlocking {
        val links = db.linkDao()
        val supports = links.insertLabel(LinkLabelEntity(forward = "supports", backward = "supported by", sortOrder = 0))
        val writer = db.goalDao().insert(GoalEntity(title = "Writer", strategy = "Become a writer via substack", priority = Priority.HIGH, createdAt = 1000))
        val teacher = db.goalDao().insert(GoalEntity(title = "Teacher"))
        val task = db.taskDao().insert(TaskEntity(title = "Write on substack"))
        val book = db.bookDao().insert(BookEntity(title = "Self persuasion"))
        val goalRef = ItemRef(ItemType.GOAL, writer)
        val taskRef = ItemRef(ItemType.TASK, task)
        // The task supports the goal: the label reads from the task, and must still after the move.
        links.insert(LinkEntity.labelled(taskRef, goalRef, supports).copy(createdAt = 42))
        links.insert(LinkEntity.between(ItemRef(ItemType.BOOK, book), goalRef))
        links.insert(LinkEntity.between(goalRef, ItemRef(ItemType.GOAL, teacher)))
        val tag = db.tagDao().insertTag(TagEntity(name = "career"))
        db.tagDao().insertItemTag(ItemTagEntity(ItemType.GOAL, writer, tag))
        db.reminderDao().insert(ReminderEntity(itemType = ItemType.GOAL, itemId = writer, time = 5000))

        val moved = db.simplifyDao().goalsToVisions()

        assertEquals(listOf(writer, teacher), moved.keys.toList())
        assertTrue(db.goalDao().observeAll().first().isEmpty())
        val vision = db.ideaDao().get(moved.getValue(writer))!!
        assertEquals("Writer", vision.title)
        assertEquals("Become a writer via substack", vision.body)
        assertEquals(IdeaKind.VISION, vision.kind)
        assertEquals(Priority.HIGH, vision.priority)
        assertEquals(1000L, vision.createdAt)
        assertEquals(null, vision.parentId)

        val visionRef = ItemRef(ItemType.IDEA, vision.id)
        val all = links.observeAll().first()
        assertEquals(3, all.size)
        assertTrue(all.none { it.aType == ItemType.GOAL || it.bType == ItemType.GOAL })
        // Still "the task supports the vision", whichever side each now sorts to.
        val labelled = all.single { it.labelId == supports }
        val subject = if (labelled.labelFromA) ItemRef(labelled.aType, labelled.aId) else ItemRef(labelled.bType, labelled.bId)
        assertEquals(taskRef, subject)
        assertEquals(setOf(taskRef, visionRef), setOf(ItemRef(labelled.aType, labelled.aId), ItemRef(labelled.bType, labelled.bId)))
        assertEquals(42L, labelled.createdAt)
        assertTrue(all.any { setOf(ItemRef(it.aType, it.aId), ItemRef(it.bType, it.bId)) == setOf(ItemRef(ItemType.BOOK, book), visionRef) })
        assertTrue(all.any { setOf(ItemRef(it.aType, it.aId), ItemRef(it.bType, it.bId)) == setOf(visionRef, ItemRef(ItemType.IDEA, moved.getValue(teacher))) })

        assertEquals(listOf(vision.id), db.tagDao().observeAllItemTags().first().filter { it.type == ItemType.IDEA }.map { it.itemId })
        assertEquals(listOf(5000L), db.reminderDao().forItem(ItemType.IDEA, vision.id).map { it.time })
        assertTrue(db.reminderDao().forItem(ItemType.GOAL, writer).isEmpty())
        Unit
    }
}
