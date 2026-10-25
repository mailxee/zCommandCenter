package com.example.xcommandcenter.data

import com.example.xcommandcenter.data.backup.BackupData
import com.example.xcommandcenter.data.backup.BackupFormat
import com.example.xcommandcenter.data.backup.ImportException
import com.example.xcommandcenter.data.backup.Sheet
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.HabitMarkEntity
import com.example.xcommandcenter.data.entity.ItemTagEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.ProjectEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import com.example.xcommandcenter.domain.TabOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BackupFormatTest {
    private val zone = ZoneId.of("UTC")
    private val created = LocalDateTime.of(2026, 10, 20, 8, 30).atZone(zone).toInstant().toEpochMilli()

    private val data = BackupData(
        tasks = listOf(
            TaskEntity(1, "Water plants", "Balcony", LocalDate.of(2026, 10, 31), created + 3_600_000, Priority.LOW, false, created,
                RecurrenceType.WEEKLY, Recurrence.maskFromDays(setOf(DayOfWeek.SATURDAY)), 1, 2, false),
            TaskEntity(2, "File taxes", priority = Priority.CRITICAL, completed = true, createdAt = created),
        ),
        goals = listOf(GoalEntity(1, "Run a half marathon", "Three runs a week", created)),
        habits = listOf(HabitEntity(1, "Read", HabitCategory.NEGATIVE, RecurrenceType.EVERY_N_DAYS, 0, 1, 3, LocalDate.of(2026, 9, 1), "", created)),
        habitMarks = listOf(HabitMarkEntity(1, LocalDate.of(2026, 10, 1))),
        projects = listOf(ProjectEntity(2, "Child", parentId = 1, completed = true, createdAt = created), ProjectEntity(1, "Parent", completed = true, createdAt = created)),
        notes = listOf(NoteEntity(1, "", "Body only", created)),
        books = listOf(BookEntity(1, "Atomic Habits", "James Clear", "", created)),
        chapters = listOf(ChapterEntity(5, 1, 0, "The surprising power", 2, true, created)),
        quotes = listOf(QuoteEntity(1, "Done is better than perfect.", createdAt = created)),
        links = listOf(LinkEntity.between(ItemRef(ItemType.TASK, 1), ItemRef(ItemType.PROJECT, 2)).copy(createdAt = created)),
        tags = listOf(TagEntity(7, "Home")),
        itemTags = listOf(ItemTagEntity(ItemType.TASK, 1, 7), ItemTagEntity(ItemType.CHAPTER, 5, 7)),
    )

    @Test
    fun `export then import gives the same data`() {
        val back = BackupFormat.fromSheets(BackupFormat.toSheets(data, TabOrder.default, zone), zone).data
        assertEquals(data.tasks, back.tasks)
        assertEquals(data.goals, back.goals)
        assertEquals(data.habits, back.habits)
        assertEquals(data.habitMarks, back.habitMarks)
        // Parents come first on import, so the foreign key is satisfied.
        assertEquals(listOf(1L, 2L), back.projects.map { it.id })
        assertEquals(data.projects.sortedBy { it.id }, back.projects)
        assertEquals(data.notes, back.notes)
        assertEquals(data.books, back.books)
        assertEquals(data.chapters, back.chapters)
        assertEquals(data.quotes, back.quotes)
        assertEquals(data.links.map { it.copy(id = 0) }, back.links)
        assertEquals(listOf("Home"), back.tags.map { it.name })
        assertEquals(setOf(ItemRef(ItemType.TASK, 1), ItemRef(ItemType.CHAPTER, 5)), back.itemTags.map { ItemRef(it.type, it.itemId) }.toSet())
    }

    @Test
    fun `sheets follow the tab order, then the extra sheets`() {
        val order = TabOrder.default.reversed()
        val names = BackupFormat.toSheets(data, order, zone).map { it.name }
        assertEquals(order.map { it.tabLabel } + listOf("Chapters", "Habit history", "Links", "Tags"), names)
    }

    @Test
    fun `rows typed in Excel import with labels, blank ids and Excel date numbers`() {
        val sheets = BackupFormat.toSheets(BackupData(), TabOrder.default, zone).map { sheet ->
            when (sheet.name) {
                "Tasks" -> sheet.copy(rows = sheet.rows + listOf(listOf("", "New task", "", "46320", "", "high", "yes", "Every N days", "", "", "3")))
                "Goals" -> sheet.copy(rows = sheet.rows + listOf(listOf("4", "Goal"), listOf("", "Another goal"), listOf("", "", "", "")))
                else -> sheet
            }
        }
        val back = BackupFormat.fromSheets(sheets, zone, now = created).data
        val task = back.tasks.single()
        assertEquals(1L, task.id)
        assertEquals(LocalDate.of(2026, 10, 25), task.dueDate)
        assertEquals(Priority.HIGH, task.priority)
        assertTrue(task.completed)
        assertEquals(RecurrenceType.EVERY_N_DAYS, task.recurrenceType)
        assertEquals(3, task.intervalDays)
        assertEquals(created, task.createdAt)
        // A blank id gets the next one after the largest given id; fully blank rows are ignored.
        assertEquals(listOf(4L, 5L), back.goals.map { it.id })
    }

    @Test
    fun `references to missing items are skipped and counted`() {
        val sheets = BackupFormat.toSheets(data, TabOrder.default, zone).map { sheet ->
            when (sheet.name) {
                "Links" -> sheet.copy(rows = sheet.rows + listOf(listOf("Task", "1", "", "Goal", "99")))
                "Tags" -> sheet.copy(rows = sheet.rows + listOf(listOf("Note", "42", "", "work")))
                else -> sheet
            }
        }
        val result = BackupFormat.fromSheets(sheets, zone)
        assertEquals(2, result.skipped)
        assertEquals(1, result.data.links.size)
    }

    @Test
    fun `a completed project with an open child is reopened`() {
        val sheets = BackupFormat.toSheets(data.copy(projects = listOf(ProjectEntity(1, "Parent", completed = true), ProjectEntity(2, "Child", parentId = 1))), TabOrder.default, zone)
        val projects = BackupFormat.fromSheets(sheets, zone).data.projects
        assertFalse(projects.first { it.id == 1L }.completed)
        assertNull(projects.first { it.id == 1L }.parentId)
    }

    @Test(expected = ImportException::class)
    fun `a missing tab sheet stops the import`() {
        BackupFormat.fromSheets(BackupFormat.toSheets(data, TabOrder.default, zone).filterNot { it.name == "Quotes" }, zone)
    }

    @Test
    fun `a bad value names the sheet and row`() {
        val sheets = BackupFormat.toSheets(data, TabOrder.default, zone).map { sheet ->
            if (sheet.name == "Tasks") sheet.copy(rows = sheet.rows + listOf(listOf("9", "Bad", "", "", "", "Urgent"))) else sheet
        }
        val error = runCatching { BackupFormat.fromSheets(sheets, zone) }.exceptionOrNull()
        assertTrue(error is ImportException)
        assertTrue(error!!.message!!, error.message!!.startsWith("\"Tasks\" row 4: Priority \"Urgent\""))
    }

    @Test(expected = ImportException::class)
    fun `duplicate ids stop the import`() {
        val sheets = listOf(Sheet("Goals", listOf(listOf("ID", "Title"), listOf("1", "A"), listOf("1", "B"))))
        BackupFormat.fromSheets(BackupFormat.toSheets(BackupData(), TabOrder.default, zone).filterNot { it.name == "Goals" } + sheets, zone)
    }
}
