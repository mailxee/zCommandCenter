package com.example.xcommandcenter.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ItemType.BOOK
import com.example.xcommandcenter.domain.ItemType.CHAPTER
import com.example.xcommandcenter.domain.ItemType.GOAL
import com.example.xcommandcenter.domain.ItemType.HABIT
import com.example.xcommandcenter.domain.ItemType.NOTE
import com.example.xcommandcenter.domain.ItemType.PROJECT
import com.example.xcommandcenter.domain.ItemType.QUOTE
import com.example.xcommandcenter.domain.ItemType.TASK
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * First-launch content: about five items per tab, cross-linked so every tab shows links.
 *
 * Written as raw inserts because it runs inside Room's onCreate, before the DAOs can be used.
 * Ids are explicit so the links below can refer to them; column names match the entity fields.
 */
internal object SeedData {

    fun insert(db: SupportSQLiteDatabase, today: LocalDate = LocalDate.now()) {
        val now = System.currentTimeMillis()
        var tick = 0L
        // Distinct, increasing timestamps keep "newest first" lists in a sensible order.
        fun created() = now - 60_000L * (100 - tick++)

        fun row(table: String, vararg values: Pair<String, Any?>) {
            val content = ContentValues()
            values.forEach { (key, value) ->
                when (value) {
                    null -> content.putNull(key)
                    is String -> content.put(key, value)
                    is Long -> content.put(key, value)
                    is Int -> content.put(key, value)
                    is Boolean -> content.put(key, if (value) 1 else 0)
                    is LocalDate -> content.put(key, value.toEpochDay())
                    is Enum<*> -> content.put(key, value.name)
                    else -> error("Unsupported seed value $value")
                }
            }
            content.put("createdAt", created())
            db.insert(table, SQLiteDatabase.CONFLICT_ABORT, content)
        }

        // Goals
        row("goals", "id" to 1L, "title" to "Run a half marathon",
            "strategy" to "Three runs a week, one long run on Sundays, add 10% distance per week.")
        row("goals", "id" to 2L, "title" to "Read 24 books this year",
            "strategy" to "Twenty pages every day; always have the next book ready.")
        row("goals", "id" to 3L, "title" to "Ship xCommandCenter v1",
            "strategy" to "Data layer first, then screens, then polish. Weekly demo to myself.")
        row("goals", "id" to 4L, "title" to "Calm, consistent mornings",
            "strategy" to "Same wake-up time, no phone for the first hour, plan the day on paper.")
        row("goals", "id" to 5L, "title" to "Spanish to B1 level",
            "strategy" to "Short daily practice plus one conversation class per week.")

        // Projects: two trees.
        row("projects", "id" to 1L, "title" to "xCommandCenter app", "description" to "Personal command center for Android.", "parentId" to null)
        row("projects", "id" to 2L, "title" to "Data layer", "description" to "Room entities, DAOs and repositories.", "parentId" to 1L)
        row("projects", "id" to 3L, "title" to "UI polish", "description" to "Make every tab pleasant to use.", "parentId" to 1L)
        row("projects", "id" to 4L, "title" to "Home renovation", "description" to "Small improvements around the flat.", "parentId" to null)
        row("projects", "id" to 5L, "title" to "Paint the bedroom", "description" to "Pick a color, buy paint, paint on a weekend.", "parentId" to 4L)

        // Tasks
        val reminder = today.plusDays(1).atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        row("tasks", "id" to 1L, "title" to "Write the Room entities", "description" to "One entity per tab plus the link table.",
            "dueDate" to today.plusDays(1), "reminderAt" to null, "priority" to Priority.HIGH, "completed" to true)
        row("tasks", "id" to 2L, "title" to "Design the tab layout", "description" to "Scrollable tab row, list per tab, FAB to add.",
            "dueDate" to today.plusDays(4), "reminderAt" to null, "priority" to Priority.MEDIUM, "completed" to false)
        row("tasks", "id" to 3L, "title" to "Finish chapter 3 of Atomic Habits", "description" to "",
            "dueDate" to today.plusDays(3), "reminderAt" to null, "priority" to Priority.LOW, "completed" to false)
        row("tasks", "id" to 4L, "title" to "Buy paint samples", "description" to "Three shades of light green.",
            "dueDate" to today.plusDays(2), "reminderAt" to reminder, "priority" to Priority.MEDIUM, "completed" to false)
        row("tasks", "id" to 5L, "title" to "Register for the half marathon", "description" to "Early-bird price ends soon.",
            "dueDate" to today.plusDays(7), "reminderAt" to null, "priority" to Priority.CRITICAL, "completed" to false)

        // Habits, started a month ago so streaks have history.
        val start = today.minusDays(30)
        data class SeedHabit(val id: Long, val name: String, val category: HabitCategory, val recurrence: Recurrence, val notes: String)
        val habits = listOf(
            SeedHabit(1, "Morning run", HabitCategory.POSITIVE,
                Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), "Easy pace, 5 km."),
            SeedHabit(2, "Read 20 pages", HabitCategory.POSITIVE, Recurrence.Daily, "Before bed, paper book."),
            SeedHabit(3, "Floss", HabitCategory.NEGATIVE, Recurrence.Daily, "Almost automatic; only mark the nights I skip."),
            SeedHabit(4, "Monthly review", HabitCategory.POSITIVE, Recurrence.Monthly(1), "Review goals and projects on the 1st."),
            SeedHabit(5, "Spanish practice", HabitCategory.NEGATIVE, Recurrence.EveryNDays(2), "Mark only the sessions I miss."),
        )
        for (habit in habits) {
            val recurrence = habit.recurrence
            row("habits", "id" to habit.id, "name" to habit.name, "category" to habit.category,
                "recurrenceType" to when (recurrence) {
                    Recurrence.Daily -> RecurrenceType.DAILY
                    is Recurrence.Weekly -> RecurrenceType.WEEKLY
                    is Recurrence.Monthly -> RecurrenceType.MONTHLY
                    is Recurrence.EveryNDays -> RecurrenceType.EVERY_N_DAYS
                },
                "weekdays" to ((recurrence as? Recurrence.Weekly)?.let { Recurrence.maskFromDays(it.days) } ?: 0),
                "dayOfMonth" to ((recurrence as? Recurrence.Monthly)?.dayOfMonth ?: 1),
                "intervalDays" to ((recurrence as? Recurrence.EveryNDays)?.n ?: 2),
                "startDate" to start, "notes" to habit.notes)
        }
        fun marks(habitId: Long, dates: Iterable<LocalDate>) = dates.forEach { date ->
            db.insert("habit_marks", SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply { put("habitId", habitId); put("date", date.toEpochDay()) })
        }
        // Positive habits: marked on recent past occurrences (today left open).
        marks(1, HabitStats.recentOccurrences(habits[0].recurrence, start, today.minusDays(1), 4))
        marks(2, HabitStats.recentOccurrences(habits[1].recurrence, start, today.minusDays(1), 6))
        marks(4, HabitStats.recentOccurrences(habits[3].recurrence, start, today, 1))
        // Negative habits: a single miss each, so there is a clean streak since then.
        marks(3, listOf(today.minusDays(6)))
        marks(5, HabitStats.recentOccurrences(habits[4].recurrence, start, today, 5).drop(4))

        // Notes
        row("notes", "id" to 1L, "title" to "Link picker idea", "body" to "Pick the tab first, then search by title. Keeps the list short.")
        row("notes", "id" to 2L, "title" to "", "body" to "Habits stick when they are tied to who I want to be, not to a number.")
        row("notes", "id" to 3L, "title" to "Training thoughts", "body" to "Long run pace should feel conversational.\nHills once a week.")
        row("notes", "id" to 4L, "title" to "Bedroom colors", "body" to "Sage green walls, white trim, keep the oak floor.")
        row("notes", "id" to 5L, "title" to "On focus", "body" to "Two 90-minute blocks a day with the phone in another room.")

        // Books and their chapters
        row("books", "id" to 1L, "title" to "Atomic Habits", "author" to "James Clear", "description" to "Tiny changes, remarkable results.")
        row("books", "id" to 2L, "title" to "Deep Work", "author" to "Cal Newport", "description" to "Rules for focused success in a distracted world.")
        row("books", "id" to 3L, "title" to "The Pragmatic Programmer", "author" to "David Thomas, Andrew Hunt", "description" to "Your journey to mastery.")
        row("books", "id" to 4L, "title" to "Born to Run", "author" to "Christopher McDougall", "description" to "")
        row("books", "id" to 5L, "title" to "Meditations", "author" to "Marcus Aurelius", "description" to "Private notes of a Roman emperor.")
        var chapterId = 1L
        fun chapters(bookId: Long, vararg titles: String, read: Int = 0, internalized: Int = 0) =
            titles.forEachIndexed { index, title ->
                row("chapters", "id" to chapterId++, "bookId" to bookId, "position" to index, "title" to title,
                    "readCount" to if (index < read) 2 - index % 2 else 0, "internalized" to (index < internalized))
            }
        chapters(1, "The Surprising Power of Atomic Habits", "How Your Habits Shape Your Identity",
            "How to Build Better Habits in 4 Simple Steps", "The Man Who Didn't Look Right", read = 3, internalized = 1)
        chapters(2, "Deep Work Is Valuable", "Deep Work Is Rare", "Deep Work Is Meaningful", read = 2, internalized = 1)
        chapters(3, "A Pragmatic Philosophy", "A Pragmatic Approach", "The Basic Tools", read = 1)
        chapters(4, "Chapter 1", "Chapter 2")
        chapters(5, "Book One", "Book Two", "Book Three", read = 2)

        // Quotes
        row("quotes", "id" to 1L, "text" to "You do not rise to the level of your goals. You fall to the level of your systems.",
            "source" to "Atomic Habits", "author" to "James Clear")
        row("quotes", "id" to 2L, "text" to "Clarity about what matters provides clarity about what does not.",
            "source" to "Deep Work", "author" to "Cal Newport")
        row("quotes", "id" to 3L, "text" to "Waste no more time arguing about what a good man should be. Be one.",
            "source" to "Meditations, Book 10", "author" to "Marcus Aurelius")
        row("quotes", "id" to 4L, "text" to "Care about your craft.", "source" to "The Pragmatic Programmer, Tip 1", "author" to "")
        row("quotes", "id" to 5L, "text" to "Done is better than perfect.", "source" to "", "author" to "")

        // Links. Chapter 3 is "How to Build Better Habits in 4 Simple Steps".
        listOf(
            TASK to 1L to (PROJECT to 2L),
            TASK to 2L to (PROJECT to 3L),
            TASK to 3L to (BOOK to 1L),
            TASK to 3L to (CHAPTER to 3L),
            TASK to 4L to (PROJECT to 5L),
            TASK to 5L to (GOAL to 1L),
            HABIT to 1L to (GOAL to 1L),
            HABIT to 2L to (GOAL to 2L),
            HABIT to 3L to (NOTE to 2L),
            HABIT to 4L to (GOAL to 4L),
            HABIT to 5L to (GOAL to 5L),
            PROJECT to 1L to (GOAL to 3L),
            NOTE to 1L to (PROJECT to 1L),
            NOTE to 1L to (NOTE to 2L),
            NOTE to 3L to (GOAL to 1L),
            NOTE to 3L to (BOOK to 4L),
            NOTE to 4L to (PROJECT to 5L),
            NOTE to 5L to (BOOK to 2L),
            BOOK to 2L to (GOAL to 2L),
            QUOTE to 1L to (BOOK to 1L),
            QUOTE to 1L to (NOTE to 2L),
            QUOTE to 2L to (BOOK to 2L),
            QUOTE to 2L to (NOTE to 5L),
            QUOTE to 3L to (BOOK to 5L),
            QUOTE to 4L to (BOOK to 3L),
            QUOTE to 4L to (PROJECT to 1L),
            QUOTE to 5L to (PROJECT to 1L),
        ).forEach { (from, to) -> link(db, from, to, created()) }
    }

    private fun link(db: SupportSQLiteDatabase, from: Pair<ItemType, Long>, to: Pair<ItemType, Long>, createdAt: Long) {
        val link = LinkEntity.between(ItemRef(from.first, from.second), ItemRef(to.first, to.second))
        db.insert("links", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
            put("aType", link.aType.name)
            put("aId", link.aId)
            put("bType", link.bType.name)
            put("bId", link.bId)
            put("createdAt", createdAt)
        })
    }
}
