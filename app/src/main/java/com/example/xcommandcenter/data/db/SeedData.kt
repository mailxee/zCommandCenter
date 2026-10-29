package com.example.xcommandcenter.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ItemType.BLOCK
import com.example.xcommandcenter.domain.ItemType.BOOK
import com.example.xcommandcenter.domain.ItemType.CHAPTER
import com.example.xcommandcenter.domain.ItemType.GOAL
import com.example.xcommandcenter.domain.ItemType.HABIT
import com.example.xcommandcenter.domain.ItemType.NOTE
import com.example.xcommandcenter.domain.ItemType.PLAN
import com.example.xcommandcenter.domain.ItemType.QUOTE
import com.example.xcommandcenter.domain.ItemType.TASK
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * First-launch content for someone whose goals are to live as a Stoic, to learn the piano and to
 * run a half marathon: about five items per tab, cross-linked so every tab shows links, and two
 * schedules (Weekday and Weekend).
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

        // Goals: the three this sample life is built around, plus one that supports them all.
        row("goals", "id" to 1L, "title" to "Live as a Stoic", "priority" to Priority.CRITICAL,
            "strategy" to "Morning reflection, evening review, and a page of the Stoics every day. Focus on what is in my control.")
        row("goals", "id" to 2L, "title" to "Learn to play the piano", "priority" to Priority.HIGH,
            "strategy" to "30 minutes of practice every evening, a lesson every week, one new piece a month.")
        row("goals", "id" to 3L, "title" to "Run a half marathon", "priority" to Priority.HIGH,
            "strategy" to "12-week plan: three runs a week, a long run on Sundays, add no more than 10% distance per week.")
        row("goals", "id" to 4L, "title" to "Sleep 7.5 hours a night", "priority" to Priority.MEDIUM,
            "strategy" to "Lights out by 10:30. Training, practice and focus all depend on it.")

        // Plans: a training plan in phases (the first already done), a piano path, and a Stoic study plan.
        row("plans", "id" to 1L, "title" to "Half marathon training", "description" to "12 weeks from easy base runs to race day.",
            "parentId" to null, "priority" to Priority.HIGH)
        row("plans", "id" to 2L, "title" to "Base building (weeks 1–4)", "description" to "Easy runs only; build the habit, not the speed.",
            "parentId" to 1L, "completed" to true)
        row("plans", "id" to 3L, "title" to "Build phase (weeks 5–8)", "description" to "Add one tempo run a week; long run up to 16 km.", "parentId" to 1L)
        row("plans", "id" to 4L, "title" to "Taper and race (weeks 9–12)", "description" to "Peak at 18 km, then ease off for race day.", "parentId" to 1L)
        row("plans", "id" to 5L, "title" to "Piano foundations", "description" to "From reading notes to playing a first full piece.",
            "parentId" to null, "priority" to Priority.HIGH)
        row("plans", "id" to 6L, "title" to "Read sheet music", "description" to "Treble and bass clef, rhythm, key signatures.", "parentId" to 5L)
        row("plans", "id" to 7L, "title" to "First full piece", "description" to "Choose it, learn it hands separately, then together.", "parentId" to 5L)
        row("plans", "id" to 8L, "title" to "Read the Stoics", "description" to "Marcus Aurelius, then Seneca, then Epictetus.", "parentId" to null)

        // Tasks
        val reminder = today.plusDays(1).atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        row("tasks", "id" to 1L, "title" to "Register for the half marathon", "description" to "Early-bird entry closes soon.",
            "dueDate" to today.plusDays(5), "priority" to Priority.CRITICAL, "completed" to false)
        row("tasks", "id" to 2L, "title" to "Get fitted for running shoes", "description" to "Bring the old pair to show the wear pattern.",
            "dueDate" to today.plusDays(3), "priority" to Priority.HIGH, "completed" to false)
        row("tasks", "id" to 3L, "title" to "Book a weekly piano lesson", "description" to "Ask about Saturday mornings.",
            "dueDate" to today.plusDays(2), "priority" to Priority.HIGH, "completed" to false)
        row("tasks", "id" to 4L, "title" to "Choose a first full piece", "description" to "Something slow, two pages at most.",
            "dueDate" to today.plusDays(10), "priority" to Priority.MEDIUM, "completed" to false)
        row("tasks", "id" to 5L, "title" to "Finish the base-building weeks", "description" to "",
            "dueDate" to today.minusDays(2), "priority" to Priority.MEDIUM, "completed" to true)
        row("tasks", "id" to 6L, "title" to "Plan next week's runs", "description" to "Check the plan, the weather and the calendar.",
            "dueDate" to today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
            "priority" to Priority.MEDIUM, "completed" to false,
            "recurrenceType" to RecurrenceType.WEEKLY, "weekdays" to Recurrence.maskFromDays(setOf(DayOfWeek.SUNDAY)))
        row("tasks", "id" to 7L, "title" to "Write a letter to an old friend", "description" to "Seneca wrote letters; so can I.",
            "dueDate" to today.plusDays(6), "priority" to Priority.LOW, "completed" to false)
        // Reminders have no createdAt column, so not through row().
        fun reminderRow(type: ItemType, id: Long, time: Long) = db.insert("reminders", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("itemType", type.name)
            put("itemId", id)
            put("time", time)
        })
        reminderRow(TASK, 1L, reminder)

        // Habits, started a month ago so streaks have history.
        val start = today.minusDays(30)
        data class SeedHabit(val id: Long, val name: String, val category: HabitCategory, val recurrence: Recurrence, val notes: String)
        val habits = listOf(
            SeedHabit(1, "Morning reflection", HabitCategory.POSITIVE, Recurrence.Daily,
                "What is in my control today? What might go wrong, and how will I meet it?"),
            SeedHabit(2, "Evening review", HabitCategory.POSITIVE, Recurrence.Daily, "What did I do well, what badly, what did I leave undone?"),
            SeedHabit(3, "Piano practice", HabitCategory.POSITIVE, Recurrence.Daily, "30 minutes: scales first, then the current piece."),
            SeedHabit(4, "Run", HabitCategory.POSITIVE,
                Recurrence.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)), "Tue easy, Thu tempo, Sun long."),
            SeedHabit(5, "Complaining", HabitCategory.NEGATIVE, Recurrence.Daily, "Only mark the days I complain about what I can't control."),
        )
        for (habit in habits) {
            val recurrence = habit.recurrence
            row("habits", "id" to habit.id, "name" to habit.name, "category" to habit.category,
                "recurrenceType" to when (recurrence) {
                    Recurrence.Daily -> RecurrenceType.DAILY
                    is Recurrence.Weekly -> RecurrenceType.WEEKLY
                    is Recurrence.Monthly -> RecurrenceType.MONTHLY
                    is Recurrence.EveryNDays -> RecurrenceType.EVERY_N_DAYS
                    is Recurrence.EveryNWeeks -> RecurrenceType.EVERY_N_WEEKS
                    is Recurrence.EveryNMonths -> RecurrenceType.EVERY_N_MONTHS
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
        // Positive habits: done on most recent days, with a few misses (today left open).
        val lastMonth = HabitStats.recentOccurrences(Recurrence.Daily, start, today.minusDays(1), 30)
        marks(1, lastMonth.filterIndexed { i, _ -> i % 7 != 3 })
        marks(2, lastMonth.take(9))
        marks(3, lastMonth.filterIndexed { i, _ -> i % 5 != 4 })
        marks(4, HabitStats.recentOccurrences(habits[3].recurrence, start, today.minusDays(1), 12).drop(1))
        // Negative habit: two slips, the last a week ago, so there is a clean streak since then.
        marks(5, listOf(today.minusDays(7), today.minusDays(19)))
        reminderRow(HABIT, 1L, 6 * 60 + 30L)
        reminderRow(HABIT, 3L, 19 * 60L)

        // Notes, one with a list.
        row("notes", "id" to 1L, "title" to "The dichotomy of control",
            "body" to "Some things are up to me: my judgments, choices and effort. Everything else is not. Put my energy only into the first.")
        row("notes", "id" to 2L, "title" to "Training paces", "body" to "Easy: can hold a conversation.\nTempo: comfortably hard, about 20 minutes.\nLong run: slower than easy.")
        row("notes", "id" to 3L, "title" to "Practice routine", "body" to "")
        row("notes", "id" to 4L, "title" to "Race-day checklist", "body" to "Bib and pins, gels, the shoes I trained in, nothing new on race day.")
        row("notes", "id" to 5L, "title" to "", "body" to "A bad run, a wrong note, a hard day: each is material to practise virtue with.")
        listOf("Scales: C, G and F major, hands together", "Arpeggios, slowly", "Sight-read one short exercise", "The current piece, bar by bar")
            .forEachIndexed { index, text ->
                db.insert("note_list_items", SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply { put("noteId", 3L); put("position", index); put("text", text) })
            }

        // Books and their chapters
        row("books", "id" to 1L, "title" to "Meditations", "author" to "Marcus Aurelius", "description" to "Private notes of a Roman emperor.",
            "priority" to Priority.HIGH)
        row("books", "id" to 2L, "title" to "Letters from a Stoic", "author" to "Seneca", "description" to "Letters to his friend Lucilius.")
        row("books", "id" to 3L, "title" to "The Daily Stoic", "author" to "Ryan Holiday, Stephen Hanselman", "description" to "A page a day.")
        row("books", "id" to 4L, "title" to "Effortless Mastery", "author" to "Kenny Werner", "description" to "On practising music without fear.")
        row("books", "id" to 5L, "title" to "Daniels' Running Formula", "author" to "Jack Daniels", "description" to "Training paces and plans.")
        var chapterId = 1L
        fun chapters(bookId: Long, vararg titles: String, read: Int = 0, internalized: Int = 0) =
            titles.forEachIndexed { index, title ->
                row("chapters", "id" to chapterId++, "bookId" to bookId, "position" to index, "title" to title,
                    "readCount" to if (index < read) 2 - index % 2 else 0, "internalized" to (index < internalized))
            }
        chapters(1, "Book One", "Book Two", "Book Three", "Book Four", "Book Five", read = 3, internalized = 1)
        chapters(2, "On Saving Time", "On Discursiveness in Reading", "On True and False Friendship", "On the Terrors of Death", read = 2)

        // Quotes
        row("quotes", "id" to 1L, "text" to "Waste no more time arguing about what a good man should be. Be one.",
            "source" to "Meditations, Book 10", "author" to "Marcus Aurelius", "priority" to Priority.HIGH)
        row("quotes", "id" to 2L, "text" to "The impediment to action advances action. What stands in the way becomes the way.",
            "source" to "Meditations, Book 5", "author" to "Marcus Aurelius")
        row("quotes", "id" to 3L, "text" to "We suffer more often in imagination than in reality.",
            "source" to "Letters from a Stoic, Letter 13", "author" to "Seneca")
        row("quotes", "id" to 4L, "text" to "Men are disturbed not by things, but by the views which they take of them.",
            "source" to "Enchiridion, 5", "author" to "Epictetus")
        row("quotes", "id" to 5L, "text" to "The miracle isn't that I finished. The miracle is that I had the courage to start.",
            "source" to "", "author" to "John Bingham")

        // Schedules: the first (created with the database) becomes "Weekday"; "Weekend" is added. Automatic switching stays off.
        db.execSQL("UPDATE schedules SET name = 'Weekday', days = ${Recurrence.maskFromDays(DayOfWeek.entries.take(5).toSet())} WHERE id = 1")
        db.insert("schedules", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("id", 2L); put("name", "Weekend"); put("sortOrder", 1); put("days", Recurrence.maskFromDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        })
        fun block(id: Long, schedule: Long, title: String, from: Int, to: Int, notes: String = "") =
            row("blocks", "id" to id, "scheduleId" to schedule, "title" to title, "startMinute" to from, "endMinute" to to, "notes" to notes)
        block(1, 1, "Morning reflection", 6 * 60 + 30, 6 * 60 + 45, "Plan the day; what is in my control?")
        block(2, 1, "Run or strength", 6 * 60 + 45, 7 * 60 + 30)
        block(3, 1, "Deep work", 9 * 60, 12 * 60 + 30, "Phone in another room.")
        block(4, 1, "Lunch and a walk", 12 * 60 + 30, 13 * 60 + 15)
        block(5, 1, "Meetings and email", 13 * 60 + 15, 17 * 60 + 30)
        block(6, 1, "Piano practice", 19 * 60, 19 * 60 + 30, "Scales, then the current piece.")
        block(7, 1, "Evening review", 21 * 60 + 30, 21 * 60 + 45)
        block(8, 1, "Read the Stoics", 21 * 60 + 45, 22 * 60 + 15)
        block(9, 2, "Morning reflection", 7 * 60, 7 * 60 + 15)
        block(10, 2, "Long run", 7 * 60 + 30, 9 * 60 + 30, "Slower than easy pace. Sundays.")
        block(11, 2, "Piano lesson or new piece", 10 * 60, 11 * 60)
        block(12, 2, "Evening review", 21 * 60 + 30, 21 * 60 + 45)
        reminderRow(BLOCK, 6L, 18 * 60 + 55L)
        reminderRow(BLOCK, 10L, 7 * 60 + 15L)

        // Links. Chapter 1 is Meditations, Book One; chapter 6 is "On Saving Time".
        listOf(
            HABIT to 1L to (GOAL to 1L),
            HABIT to 2L to (GOAL to 1L),
            HABIT to 3L to (GOAL to 2L),
            HABIT to 4L to (GOAL to 3L),
            HABIT to 5L to (GOAL to 1L),
            PLAN to 1L to (GOAL to 3L),
            PLAN to 5L to (GOAL to 2L),
            PLAN to 8L to (GOAL to 1L),
            TASK to 1L to (GOAL to 3L),
            TASK to 2L to (PLAN to 3L),
            TASK to 3L to (PLAN to 5L),
            TASK to 4L to (PLAN to 7L),
            TASK to 5L to (PLAN to 2L),
            TASK to 6L to (PLAN to 1L),
            TASK to 7L to (BOOK to 2L),
            HABIT to 3L to (NOTE to 3L),
            HABIT to 4L to (NOTE to 2L),
            HABIT to 4L to (BOOK to 5L),
            PLAN to 4L to (NOTE to 4L),
            GOAL to 1L to (NOTE to 1L),
            HABIT to 5L to (NOTE to 5L),
            PLAN to 8L to (BOOK to 1L),
            PLAN to 8L to (BOOK to 2L),
            HABIT to 1L to (CHAPTER to 1L),
            PLAN to 5L to (BOOK to 4L),
            GOAL to 1L to (QUOTE to 1L),
            HABIT to 5L to (QUOTE to 4L),
            HABIT to 1L to (QUOTE to 3L),
            PLAN to 1L to (QUOTE to 5L),
            GOAL to 1L to (QUOTE to 2L),
            QUOTE to 1L to (BOOK to 1L),
            QUOTE to 2L to (BOOK to 1L),
            QUOTE to 3L to (BOOK to 2L),
            BLOCK to 1L to (HABIT to 1L),
            BLOCK to 2L to (HABIT to 4L),
            BLOCK to 6L to (HABIT to 3L),
            BLOCK to 7L to (HABIT to 2L),
            BLOCK to 8L to (PLAN to 8L),
            BLOCK to 9L to (HABIT to 1L),
            BLOCK to 10L to (HABIT to 4L),
            BLOCK to 10L to (PLAN to 1L),
            BLOCK to 11L to (PLAN to 5L),
            BLOCK to 12L to (HABIT to 2L),
        ).forEach { (from, to) -> link(db, from, to, created()) }

        // Tags (no createdAt column, so not through row()).
        listOf(1L to "stoic", 2L to "music", 3L to "running", 4L to "reading").forEach { (id, name) ->
            db.insert("tags", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply { put("id", id); put("name", name) })
        }
        listOf(
            GOAL to 1L to 1L, HABIT to 1L to 1L, HABIT to 2L to 1L, HABIT to 5L to 1L, PLAN to 8L to 1L, NOTE to 1L to 1L,
            GOAL to 2L to 2L, HABIT to 3L to 2L, PLAN to 5L to 2L, TASK to 3L to 2L, TASK to 4L to 2L, NOTE to 3L to 2L, BLOCK to 6L to 2L,
            GOAL to 3L to 3L, HABIT to 4L to 3L, PLAN to 1L to 3L, TASK to 1L to 3L, TASK to 2L to 3L, NOTE to 2L to 3L, BLOCK to 10L to 3L,
            BOOK to 1L to 4L, BOOK to 2L to 4L, BOOK to 3L to 4L, BLOCK to 8L to 4L,
        ).forEach { (item, tagId) ->
            db.insert("item_tags", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("type", item.first.name)
                put("itemId", item.second)
                put("tagId", tagId)
            })
        }
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
