package com.example.xcommandcenter.data.backup

import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
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
import com.example.xcommandcenter.domain.ProjectTree
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import com.example.xcommandcenter.domain.shortName
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the app stores, as it goes into or comes out of a workbook. */
data class BackupData(
    val tasks: List<TaskEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitMarks: List<HabitMarkEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val books: List<BookEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val quotes: List<QuoteEntity> = emptyList(),
    val links: List<LinkEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val itemTags: List<ItemTagEntity> = emptyList(),
    val checklistItems: List<ChecklistItemEntity> = emptyList(),
) {
    val itemCount: Int get() = tasks.size + goals.size + habits.size + projects.size + notes.size + books.size + quotes.size
}

/** A problem that stops an import; [message] says where, in words the user can act on. */
class ImportException(message: String) : Exception(message)

/**
 * The workbook layout: one sheet per tab (in the user's tab order), then Checklists, Chapters,
 * Habit history, Links and Tags, so that an import restores everything. Columns are found by header name, so
 * reordering columns in Excel is fine. Ids tie rows together; a blank id gets a new one.
 * Enums are written as their labels ("Medium", "Every N days") and read by label or name.
 */
object BackupFormat {
    const val CHAPTERS = "Chapters"
    const val HABIT_HISTORY = "Habit history"
    const val LINKS = "Links"
    const val TAGS = "Tags"
    const val CHECKLISTS = "Checklists"

    /** The Plans tab's sheet was called this before the tab was renamed; old workbooks still import. */
    private const val OLD_PLANS = "Projects"

    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val excelEpoch = LocalDate.of(1899, 12, 30)

    // --- export ---

    fun toSheets(data: BackupData, tabOrder: List<ItemType>, zone: ZoneId = ZoneId.systemDefault()): List<Sheet> {
        fun time(millis: Long?) = millis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().format(dateTime) }.orEmpty()
        fun bool(b: Boolean) = if (b) "TRUE" else "FALSE"
        fun days(mask: Int) = Recurrence.daysFromMask(mask).sorted().joinToString(", ") { it.shortName() }
        val titles = titlesByRef(data)
        fun title(ref: ItemRef) = titles[ref].orEmpty()

        val tabSheets = mapOf(
            ItemType.TASK to Sheet(
                ItemType.TASK.tabLabel,
                listOf(listOf("ID", "Title", "Description", "Due date", "Reminder", "Priority", "Completed", "Repeat", "Weekdays", "Day of month", "Every N days", "Next created", "Created", "Order")) +
                    data.tasks.map {
                        listOf(
                            it.id.toString(), it.title, it.description, it.dueDate?.toString().orEmpty(), time(it.reminderAt),
                            it.priority.label, bool(it.completed), it.recurrenceType?.label.orEmpty(),
                            if (it.recurrenceType == RecurrenceType.WEEKLY) days(it.weekdays) else "",
                            if (it.recurrenceType == RecurrenceType.MONTHLY) it.dayOfMonth.toString() else "",
                            if (it.recurrenceType == RecurrenceType.EVERY_N_DAYS) it.intervalDays.toString() else "",
                            bool(it.nextCreated), time(it.createdAt), it.sortOrder.toString(),
                        )
                    },
            ),
            ItemType.GOAL to Sheet(
                ItemType.GOAL.tabLabel,
                listOf(listOf("ID", "Title", "Strategy", "Created", "Order")) +
                    data.goals.map { listOf(it.id.toString(), it.title, it.strategy, time(it.createdAt), it.sortOrder.toString()) },
            ),
            ItemType.HABIT to Sheet(
                ItemType.HABIT.tabLabel,
                listOf(listOf("ID", "Name", "Category", "Repeat", "Weekdays", "Day of month", "Every N days", "Start date", "Notes", "Created", "Order")) +
                    data.habits.map {
                        listOf(
                            it.id.toString(), it.name, it.category.label, it.recurrenceType.label,
                            if (it.recurrenceType == RecurrenceType.WEEKLY) days(it.weekdays) else "",
                            if (it.recurrenceType == RecurrenceType.MONTHLY) it.dayOfMonth.toString() else "",
                            if (it.recurrenceType == RecurrenceType.EVERY_N_DAYS) it.intervalDays.toString() else "",
                            it.startDate.toString(), it.notes, time(it.createdAt), it.sortOrder.toString(),
                        )
                    },
            ),
            ItemType.PROJECT to Sheet(
                ItemType.PROJECT.tabLabel,
                listOf(listOf("ID", "Title", "Description", "Parent ID", "Completed", "Created", "Order")) +
                    data.projects.map {
                        listOf(
                            it.id.toString(), it.title, it.description, it.parentId?.toString().orEmpty(), bool(it.completed), time(it.createdAt),
                            it.sortOrder.toString(),
                        )
                    },
            ),
            ItemType.NOTE to Sheet(
                ItemType.NOTE.tabLabel,
                listOf(listOf("ID", "Title", "Body", "Created", "Order")) +
                    data.notes.map { listOf(it.id.toString(), it.title, it.body, time(it.createdAt), it.sortOrder.toString()) },
            ),
            ItemType.BOOK to Sheet(
                ItemType.BOOK.tabLabel,
                listOf(listOf("ID", "Title", "Author", "Description", "Created", "Order")) +
                    data.books.map { listOf(it.id.toString(), it.title, it.author, it.description, time(it.createdAt), it.sortOrder.toString()) },
            ),
            ItemType.QUOTE to Sheet(
                ItemType.QUOTE.tabLabel,
                listOf(listOf("ID", "Text", "Source", "Author", "Created", "Order")) +
                    data.quotes.map { listOf(it.id.toString(), it.text, it.source, it.author, time(it.createdAt), it.sortOrder.toString()) },
            ),
        )
        val chapters = Sheet(
            CHAPTERS,
            listOf(listOf("ID", "Book ID", "Book", "Position", "Title", "Read count", "Internalized", "Created")) +
                data.chapters.sortedWith(compareBy({ it.bookId }, { it.position })).map {
                    listOf(
                        it.id.toString(), it.bookId.toString(), title(ItemRef(ItemType.BOOK, it.bookId)), (it.position + 1).toString(),
                        it.title, it.readCount.toString(), bool(it.internalized), time(it.createdAt),
                    )
                },
        )
        val checklists = Sheet(
            CHECKLISTS,
            listOf(listOf("Task ID", "Task", "Position", "Item", "Done")) +
                data.checklistItems.sortedWith(compareBy({ it.taskId }, { it.position })).map {
                    listOf(it.taskId.toString(), title(ItemRef(ItemType.TASK, it.taskId)), (it.position + 1).toString(), it.text, bool(it.done))
                },
        )
        val history = Sheet(
            HABIT_HISTORY,
            listOf(listOf("Habit ID", "Habit", "Date")) +
                data.habitMarks.sortedWith(compareBy({ it.habitId }, { it.date })).map {
                    listOf(it.habitId.toString(), title(ItemRef(ItemType.HABIT, it.habitId)), it.date.toString())
                },
        )
        val links = Sheet(
            LINKS,
            listOf(listOf("Type A", "ID A", "Title A", "Type B", "ID B", "Title B", "Created")) +
                data.links.map {
                    val a = ItemRef(it.aType, it.aId)
                    val b = ItemRef(it.bType, it.bId)
                    listOf(a.type.singular, a.id.toString(), title(a), b.type.singular, b.id.toString(), title(b), time(it.createdAt))
                },
        )
        val tagNames = data.tags.associate { it.id to it.name }
        val tags = Sheet(
            TAGS,
            listOf(listOf("Type", "ID", "Title", "Tag")) +
                data.itemTags.sortedWith(compareBy({ it.type }, { it.itemId })).map {
                    val ref = ItemRef(it.type, it.itemId)
                    listOf(ref.type.singular, ref.id.toString(), title(ref), tagNames[it.tagId].orEmpty())
                },
        )
        return tabOrder.mapNotNull { tabSheets[it] } + listOf(checklists, chapters, history, links, tags)
    }

    private fun titlesByRef(data: BackupData): Map<ItemRef, String> = buildMap {
        data.tasks.forEach { put(ItemRef(ItemType.TASK, it.id), it.title) }
        data.goals.forEach { put(ItemRef(ItemType.GOAL, it.id), it.title) }
        data.habits.forEach { put(ItemRef(ItemType.HABIT, it.id), it.name) }
        data.projects.forEach { put(ItemRef(ItemType.PROJECT, it.id), it.title) }
        data.notes.forEach { put(ItemRef(ItemType.NOTE, it.id), it.title.ifBlank { it.body.take(40) }) }
        data.books.forEach { put(ItemRef(ItemType.BOOK, it.id), it.title) }
        data.chapters.forEach { put(ItemRef(ItemType.CHAPTER, it.id), it.title) }
        data.quotes.forEach { put(ItemRef(ItemType.QUOTE, it.id), it.text.take(40)) }
    }

    // --- import ---

    /**
     * Parses a workbook written by [toSheets] (or edited in a spreadsheet app). The seven tab
     * sheets must be present; the others are optional. Rows that refer to missing items (a link
     * to a deleted task, a tag on an unknown id) are skipped and counted in [ImportResult.skipped].
     */
    fun fromSheets(sheets: List<Sheet>, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): ImportResult {
        val byName = sheets.associateBy { it.name.trim().lowercase() }
        fun sheet(name: String, required: Boolean): Table? {
            val s = byName[name.lowercase()] ?: return if (required) throw ImportException("The workbook has no \"$name\" sheet. Nothing was imported.") else null
            return Table(s, zone)
        }
        var skipped = 0

        val tasks = sheet(ItemType.TASK.tabLabel, true)!!.items("Title") { r ->
            val type = r.enum("Repeat", RecurrenceType.entries) { it.label }
            TaskEntity(
                id = r.id(), title = r.required("Title"), description = r.text("Description"), dueDate = r.date("Due date"),
                reminderAt = r.dateTime("Reminder"), priority = r.enum("Priority", Priority.entries) { it.label } ?: Priority.MEDIUM,
                completed = r.bool("Completed"), createdAt = r.dateTime("Created") ?: now,
                recurrenceType = type, weekdays = r.weekdays("Weekdays"), dayOfMonth = r.int("Day of month", 1, 1..31),
                intervalDays = r.int("Every N days", 2, 1..365), nextCreated = r.bool("Next created"), sortOrder = r.order(),
            ).also { if (type != null && it.dueDate == null) r.fail("A repeating task needs a due date") }
        }.withIds("Tasks") { t, id -> t.copy(id = id) }

        val goals = sheet(ItemType.GOAL.tabLabel, true)!!.items("Title") { r ->
            GoalEntity(id = r.id(), title = r.required("Title"), strategy = r.text("Strategy"), createdAt = r.dateTime("Created") ?: now, sortOrder = r.order())
        }.withIds("Goals") { g, id -> g.copy(id = id) }

        val habits = sheet(ItemType.HABIT.tabLabel, true)!!.items("Name") { r ->
            HabitEntity(
                id = r.id(), name = r.required("Name"),
                category = r.enum("Category", HabitCategory.entries) { it.label } ?: HabitCategory.POSITIVE,
                recurrenceType = r.enum("Repeat", RecurrenceType.entries) { it.label } ?: RecurrenceType.DAILY,
                weekdays = r.weekdays("Weekdays"), dayOfMonth = r.int("Day of month", 1, 1..31), intervalDays = r.int("Every N days", 2, 1..365),
                startDate = r.date("Start date") ?: LocalDate.now(zone), notes = r.text("Notes"), createdAt = r.dateTime("Created") ?: now,
                sortOrder = r.order(),
            ).also { if (it.recurrenceType == RecurrenceType.WEEKLY && it.weekdays == 0) r.fail("A weekly habit needs at least one weekday") }
        }.withIds("Habits") { h, id -> h.copy(id = id) }

        val plansSheet = sheet(ItemType.PROJECT.tabLabel, false) ?: sheet(OLD_PLANS, false) ?: sheet(ItemType.PROJECT.tabLabel, true)!!
        val rawProjects = plansSheet.items("Title") { r ->
            ProjectEntity(
                id = r.id(), title = r.required("Title"), description = r.text("Description"), parentId = r.long("Parent ID"),
                completed = r.bool("Completed"), createdAt = r.dateTime("Created") ?: now, sortOrder = r.order(),
            )
        }.withIds(ItemType.PROJECT.tabLabel) { p, id -> p.copy(id = id) }
        val projects = consistentProjects(rawProjects)

        val notes = sheet(ItemType.NOTE.tabLabel, true)!!.items("Title", "Body") { r ->
            NoteEntity(id = r.id(), title = r.text("Title"), body = r.text("Body"), createdAt = r.dateTime("Created") ?: now, sortOrder = r.order())
        }.withIds("Notes") { n, id -> n.copy(id = id) }

        val books = sheet(ItemType.BOOK.tabLabel, true)!!.items("Title") { r ->
            BookEntity(
                id = r.id(), title = r.required("Title"), author = r.text("Author"), description = r.text("Description"),
                createdAt = r.dateTime("Created") ?: now, sortOrder = r.order(),
            )
        }.withIds("Books") { b, id -> b.copy(id = id) }

        val quotes = sheet(ItemType.QUOTE.tabLabel, true)!!.items("Text") { r ->
            QuoteEntity(
                id = r.id(), text = r.required("Text"), source = r.text("Source"), author = r.text("Author"),
                createdAt = r.dateTime("Created") ?: now, sortOrder = r.order(),
            )
        }.withIds("Quotes") { q, id -> q.copy(id = id) }

        val bookIds = books.map { it.id }.toSet()
        val chapterRows = sheet(CHAPTERS, false)?.items("Title") { r ->
            val bookId = r.long("Book ID") ?: r.fail("Book ID is required")
            if (bookId !in bookIds) return@items null.also { skipped++ }
            ChapterEntity(
                id = r.id(), bookId = bookId, position = r.int("Position", 1, 1..100_000) - 1, title = r.required("Title"),
                readCount = r.int("Read count", 0, 0..1_000_000), internalized = r.bool("Internalized"), createdAt = r.dateTime("Created") ?: now,
            )
        }.orEmpty().withIds(CHAPTERS) { c, id -> c.copy(id = id) }
        // Positions are renumbered 0..n-1 per book, in the order given, as the app expects.
        val chapters = chapterRows.groupBy { it.bookId }.values.flatMap { group ->
            group.sortedBy { it.position }.mapIndexed { i, c -> c.copy(position = i) }
        }

        val taskIds = tasks.map { it.id }.toSet()
        // Positions are renumbered 0..n-1 per task, in the order given.
        val checklistItems = sheet(CHECKLISTS, false)?.rows("Item").orEmpty().mapNotNull { r ->
            val taskId = r.long("Task ID") ?: r.fail("Task ID is required")
            if (taskId !in taskIds) return@mapNotNull null.also { skipped++ }
            ChecklistItemEntity(taskId = taskId, position = r.int("Position", 1, 1..100_000) - 1, text = r.required("Item"), done = r.bool("Done"))
        }.groupBy { it.taskId }.values.flatMap { group ->
            group.sortedBy { it.position }.mapIndexed { i, item -> item.copy(position = i) }
        }

        val habitIds = habits.map { it.id }.toSet()
        val marks = sheet(HABIT_HISTORY, false)?.rows("Date").orEmpty().mapNotNull { r ->
            val habitId = r.long("Habit ID") ?: r.fail("Habit ID is required")
            val date = r.date("Date") ?: r.fail("Date is required")
            if (habitId in habitIds) HabitMarkEntity(habitId, date) else null.also { skipped++ }
        }.distinct()

        val existing: Set<ItemRef> = buildSet {
            tasks.forEach { add(ItemRef(ItemType.TASK, it.id)) }
            goals.forEach { add(ItemRef(ItemType.GOAL, it.id)) }
            habits.forEach { add(ItemRef(ItemType.HABIT, it.id)) }
            projects.forEach { add(ItemRef(ItemType.PROJECT, it.id)) }
            notes.forEach { add(ItemRef(ItemType.NOTE, it.id)) }
            books.forEach { add(ItemRef(ItemType.BOOK, it.id)) }
            chapters.forEach { add(ItemRef(ItemType.CHAPTER, it.id)) }
            quotes.forEach { add(ItemRef(ItemType.QUOTE, it.id)) }
        }

        val links = sheet(LINKS, false)?.rows("ID A", "ID B").orEmpty().mapNotNull { r ->
            val a = ItemRef(r.itemType("Type A"), r.long("ID A") ?: r.fail("ID A is required"))
            val b = ItemRef(r.itemType("Type B"), r.long("ID B") ?: r.fail("ID B is required"))
            if (a == b || a !in existing || b !in existing) return@mapNotNull null.also { skipped++ }
            LinkEntity.between(a, b).copy(createdAt = r.dateTime("Created") ?: now)
        }.distinctBy { listOf(it.aType, it.aId, it.bType, it.bId) }

        // Tag names are unique ignoring case; the first spelling seen wins.
        val tagIds = linkedMapOf<String, TagEntity>()
        val itemTags = sheet(TAGS, false)?.rows("Tag").orEmpty().mapNotNull { r ->
            val ref = ItemRef(r.itemType("Type"), r.long("ID") ?: r.fail("ID is required"))
            val name = r.text("Tag").removePrefix("#").trim().take(30)
            if (name.isEmpty() || ref !in existing) return@mapNotNull null.also { skipped++ }
            val tag = tagIds.getOrPut(name.lowercase()) { TagEntity(id = tagIds.size + 1L, name = name) }
            ItemTagEntity(ref.type, ref.id, tag.id)
        }.distinct()

        return ImportResult(
            BackupData(tasks, goals, habits, marks, projects, notes, books, chapters, quotes, links, tagIds.values.toList(), itemTags, checklistItems),
            skipped,
        )
    }

    /**
     * Parents first (the table's foreign key needs them inserted before their children); a
     * missing parent or a cycle makes the project top-level. A completed project with an open
     * project below it is reopened, so the app's completion rule holds.
     */
    private fun consistentProjects(projects: List<ProjectEntity>): List<ProjectEntity> {
        val ids = projects.map { it.id }.toSet()
        val withParents = projects.map { if (it.parentId != null && it.parentId !in ids) it.copy(parentId = null) else it }
        val ordered = ProjectTree.flatten(withParents, { it.id }, { it.parentId }).map { it.node }
        val reached = ordered.map { it.id }.toSet()
        val all = ordered + withParents.filter { it.id !in reached }.map { it.copy(parentId = null) }
        val open = all.filterNot { it.completed }.map { it.id }.toSet()
        return all.map { p ->
            if (p.completed && (ProjectTree.subtreeIds(p.id, all, { it.id }, { it.parentId }) - p.id).any { it in open }) p.copy(completed = false) else p
        }
    }

    /** Keeps given ids (rejecting duplicates) and gives rows with a blank id new ones above the largest. */
    private fun <T> List<Pair<Long?, T>>.withIds(sheet: String, assign: (T, Long) -> T): List<T> {
        val given = mapNotNull { it.first }
        given.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()?.let {
            throw ImportException("The \"$sheet\" sheet has ID $it more than once. Nothing was imported.")
        }
        var next = (given.maxOrNull() ?: 0L) + 1
        return map { (given, item) -> assign(item, given ?: next++) }
    }

    private class Table(private val sheet: Sheet, private val zone: ZoneId) {
        private val columns: Map<String, Int> =
            sheet.rows.firstOrNull().orEmpty().mapIndexed { i, h -> h.trim().lowercase() to i }.toMap()

        /** The data rows, skipping fully blank ones. Each of [requiredColumns] must have a header. */
        fun rows(vararg requiredColumns: String): List<Row> {
            requiredColumns.forEach {
                if (it.lowercase() !in columns) throw ImportException("The \"${sheet.name}\" sheet has no \"$it\" column. Nothing was imported.")
            }
            return sheet.rows.drop(1).mapIndexedNotNull { i, cells ->
                if (cells.all { it.isBlank() }) null else Row(sheet.name, i + 2, cells, columns, zone)
            }
        }

        /** Like [rows], building one item per row (null skips it) paired with the row's ID, if any. */
        fun <T : Any> items(vararg requiredColumns: String, build: (Row) -> T?): List<Pair<Long?, T>> =
            rows(*requiredColumns).mapNotNull { row -> build(row)?.let { row.long("ID") to it } }
    }

    private class Row(
        private val sheet: String,
        private val number: Int,
        private val cells: List<String>,
        private val columns: Map<String, Int>,
        private val zone: ZoneId,
    ) {
        fun fail(problem: String): Nothing = throw ImportException("\"$sheet\" row $number: $problem. Nothing was imported.")

        fun text(column: String): String = columns[column.lowercase()]?.let { cells.getOrNull(it) }?.trim().orEmpty()

        fun required(column: String): String = text(column).ifEmpty { fail("$column is required") }

        /** Placeholder for entity constructors; the real id is set by withIds. */
        fun id(): Long = 0L

        fun long(column: String): Long? {
            val t = text(column).ifEmpty { return null }
            return t.toLongOrNull() ?: t.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toLong() ?: fail("$column \"$t\" isn't a whole number")
        }

        fun int(column: String, default: Int, range: IntRange): Int {
            val v = long(column) ?: return default
            if (v !in range.first..range.last) fail("$column must be between ${range.first} and ${range.last}")
            return v.toInt()
        }

        /** The optional "Order" column: the item's place in its tab's list. */
        fun order(): Int = int("Order", 0, -1_000_000..1_000_000)

        fun bool(column: String): Boolean = when (text(column).lowercase()) {
            "", "false", "no", "0", "n" -> false
            "true", "yes", "1", "y", "x", "✓" -> true
            else -> fail("$column \"${text(column)}\" should be TRUE or FALSE")
        }

        fun <E : Enum<E>> enum(column: String, values: List<E>, label: (E) -> String): E? {
            val t = text(column).ifEmpty { return null }
            return values.firstOrNull { it.name.equals(t.replace(' ', '_'), true) || label(it).equals(t, true) }
                ?: fail("$column \"$t\" isn't one of: ${values.joinToString(", ") { label(it) }}")
        }

        fun itemType(column: String): ItemType {
            val t = required(column)
            return ItemType.entries.firstOrNull { it.name.equals(t, true) || it.singular.equals(t, true) || it.tabLabel.equals(t, true) }
                ?: fail("$column \"$t\" isn't one of: ${ItemType.entries.joinToString(", ") { it.singular }}")
        }

        fun weekdays(column: String): Int {
            val t = text(column).ifEmpty { return 0 }
            val days = t.split(',', ';', ' ', '/').filter { it.isNotBlank() }.map { part ->
                val p = part.trim()
                DayOfWeek.entries.firstOrNull { it.shortName().equals(p, true) || it.name.equals(p, true) } ?: fail("$column: \"$p\" isn't a weekday")
            }
            return Recurrence.maskFromDays(days.toSet())
        }

        /** ISO date, or an Excel date serial number (what a date cell edited in Excel becomes). */
        fun date(column: String): LocalDate? {
            val t = text(column).ifEmpty { return null }
            runCatching { return LocalDate.parse(t.take(10)) }
            t.toDoubleOrNull()?.let { return excelEpoch.plusDays(it.toLong()) }
            fail("$column \"$t\" isn't a date like 2026-10-25")
        }

        /** "yyyy-MM-dd HH:mm", ISO with T, a plain date, or an Excel serial number; local time zone. */
        fun dateTime(column: String): Long? {
            val t = text(column).ifEmpty { return null }
            val local = runCatching { LocalDateTime.parse(t, dateTime) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(t) }.getOrNull()
                ?: runCatching { LocalDate.parse(t).atStartOfDay() }.getOrNull()
                ?: t.toDoubleOrNull()?.let { serial ->
                    excelEpoch.atStartOfDay().plusSeconds(Math.round(serial * 86_400))
                }
                ?: fail("$column \"$t\" isn't a date and time like 2026-10-25 09:00")
            return local.atZone(zone).toInstant().toEpochMilli()
        }
    }
}

/** What an import produced, and how many rows were skipped because they pointed at missing items. */
data class ImportResult(val data: BackupData, val skipped: Int)
