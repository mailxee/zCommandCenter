package com.example.xcommandcenter.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.domain.LinkLabels
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Database upgrades keep people's data: every older version upgrades to the current one with the
 * schema Room expects, and what each upgrade copies or adds is there afterwards.
 *
 * Each test builds a database as an older version left it, from that version's schema in
 * app/schemas, then opens it with the app's [AppDatabase], as a phone does after an update: Room
 * runs the upgrades and checks every table and view against the current schema, failing if one
 * differs. (Room's MigrationTestHelper reads the schemas from the test's assets, which unit tests
 * don't have.) A new version needs its number in [LATEST].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<AppDatabase>()

    @After
    fun closeAll() = opened.forEach { it.close() }

    @Test
    fun everyVersionUpgradesToTheLatest() {
        for (version in 1 until LATEST) {
            val name = "upgrade-from-$version"
            createDatabase(name, version).close()
            // Throws if any step is missing or the result differs from the latest schema.
            upgrade(name)
        }
    }

    @Test
    fun itemsAndListsSurviveTheLatestUpgrade() {
        createDatabase(DB, LATEST - 1).use { db ->
            db.execSQL("INSERT INTO notes (id, title, body, createdAt) VALUES (1, 'Groceries', 'for the week', 0)")
            db.execSQL("INSERT INTO note_list_items (noteId, position, text) VALUES (1, 0, 'eggs')")
            db.execSQL("INSERT INTO ideas (id, title, body, createdAt) VALUES (1, 'Big idea', '', 0)")
            db.execSQL("INSERT INTO tasks (id, title, description, priority, completed, createdAt) VALUES (1, 'Call', '', 'HIGH', 0, 0)")
        }
        upgrade(DB).let { db ->
            assertEquals("Groceries", db.text("SELECT title FROM notes WHERE id = 1"))
            assertEquals("eggs", db.text("SELECT text FROM note_list_items WHERE noteId = 1"))
            assertEquals("Big idea", db.text("SELECT title FROM ideas WHERE id = 1"))
            assertEquals("HIGH", db.text("SELECT priority FROM tasks WHERE id = 1"))
        }
    }

    @Test
    fun existingTasksAndMarksHaveNoTimesAfterUpgradingTo15() {
        createDatabase(DB, 14).use { db ->
            db.execSQL("INSERT INTO tasks (id, title, description, priority, completed, createdAt) VALUES (1, 'Done before', '', 'HIGH', 1, 0)")
            db.execSQL(
                "INSERT INTO habits (id, name, category, recurrenceType, weekdays, dayOfMonth, intervalDays, startDate, notes, createdAt) " +
                    "VALUES (1, 'Walk', 'POSITIVE', 'DAILY', 0, 1, 2, 0, '', 0)",
            )
            db.execSQL("INSERT INTO habit_marks (habitId, date) VALUES (1, 20000)")
        }
        upgrade(DB).let { db ->
            assertEquals("1", db.text("SELECT completed FROM tasks WHERE id = 1"))
            assertEquals("1", db.text("SELECT COUNT(*) FROM tasks WHERE id = 1 AND completedAt IS NULL"))
            assertEquals("1", db.text("SELECT COUNT(*) FROM habit_marks WHERE habitId = 1 AND date = 20000 AND markedAt IS NULL"))
        }
    }

    @Test
    fun goalsBecomeVisionsWhenUpgradingTo16() {
        createDatabase(DB, 15).use { db ->
            db.execSQL("INSERT INTO ideas (id, title, body, kind, createdAt, sortOrder) VALUES (1, 'Live well', '', 'VISION', 0, 4)")
            db.execSQL("INSERT INTO goals (id, title, strategy, createdAt, sortOrder, priority) VALUES (6, 'Writer', 'Write on substack', 77, 1, 'HIGH')")
            db.execSQL("INSERT INTO goals (id, title, strategy, createdAt, sortOrder, priority) VALUES (2, 'Teacher', '', 0, 0, 'MEDIUM')")
            db.execSQL("INSERT INTO tasks (id, title, description, priority, completed, createdAt) VALUES (3, 'Write', '', 'MEDIUM', 0, 0)")
            db.execSQL("INSERT INTO link_labels (id, forward, backward, sortOrder) VALUES (50, 'supports', 'supported by', 0)")
            // "Write supports Writer", read from the task (side B, as GOAL sorts before TASK).
            db.execSQL("INSERT INTO links (aType, aId, bType, bId, createdAt, labelId, labelFromA) VALUES ('GOAL', 6, 'TASK', 3, 5, 50, 0)")
            db.execSQL("INSERT INTO links (aType, aId, bType, bId, createdAt, labelFromA) VALUES ('GOAL', 2, 'GOAL', 6, 5, 1)")
            db.execSQL("INSERT INTO links (aType, aId, bType, bId, createdAt, labelFromA) VALUES ('GOAL', 6, 'IDEA', 1, 5, 1)")
            db.execSQL("INSERT INTO tags (id, name) VALUES (1, 'career')")
            db.execSQL("INSERT INTO item_tags (type, itemId, tagId) VALUES ('GOAL', 6, 1)")
            db.execSQL("INSERT INTO reminders (itemType, itemId, time, carried) VALUES ('GOAL', 6, 1234, 0)")
            db.execSQL("INSERT INTO kept_findings (`key`, keptAt) VALUES ('why:6', 0)")
        }
        upgrade(DB).let { db ->
            assertEquals("0", db.text("SELECT COUNT(*) FROM sqlite_master WHERE name = 'goals'"))
            // In the goals' order (Teacher first), after the existing ideas.
            assertEquals("2", db.text("SELECT id FROM ideas WHERE title = 'Teacher' AND kind = 'VISION' AND sortOrder = 5 AND parentId IS NULL"))
            assertEquals("3", db.text("SELECT id FROM ideas WHERE title = 'Writer' AND body = 'Write on substack' AND priority = 'HIGH' AND createdAt = 77 AND sortOrder = 6"))
            assertEquals("0", db.text("SELECT COUNT(*) FROM links WHERE aType = 'GOAL' OR bType = 'GOAL'"))
            // IDEA sorts before TASK, so the task stays side B and the label still reads from it.
            assertEquals("50|0", db.text("SELECT labelId || '|' || labelFromA FROM links WHERE aType = 'IDEA' AND aId = 3 AND bType = 'TASK' AND bId = 3"))
            assertEquals("1", db.text("SELECT COUNT(*) FROM links WHERE aType = 'IDEA' AND aId = 2 AND bType = 'IDEA' AND bId = 3"))
            assertEquals("1", db.text("SELECT COUNT(*) FROM links WHERE aType = 'IDEA' AND aId = 1 AND bType = 'IDEA' AND bId = 3"))
            assertEquals("3", db.text("SELECT itemId FROM item_tags WHERE type = 'IDEA'"))
            assertEquals("3", db.text("SELECT itemId FROM reminders WHERE itemType = 'IDEA' AND time = 1234"))
            assertEquals("0", db.text("SELECT COUNT(*) FROM kept_findings"))
            assertEquals("Writer", db.text("SELECT title FROM item_index WHERE type = 'IDEA' AND id = 3"))
        }
    }

    @Test
    fun singleRemindersMoveIntoTheRemindersTable() {
        createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO tasks (id, title, description, reminderAt, priority, completed, createdAt) VALUES (1, 'Call', '', 1234, 'MEDIUM', 0, 0)")
            db.execSQL(
                "INSERT INTO habits (id, name, category, recurrenceType, weekdays, dayOfMonth, intervalDays, startDate, notes, createdAt, reminderMinutes) " +
                    "VALUES (1, 'Walk', 'POSITIVE', 'DAILY', 0, 1, 2, 0, '', 0, 480)",
            )
        }
        upgrade(DB).let { db ->
            assertEquals("1234", db.text("SELECT time FROM reminders WHERE itemType = 'TASK' AND itemId = 1"))
            assertEquals("480", db.text("SELECT time FROM reminders WHERE itemType = 'HABIT' AND itemId = 1"))
        }
    }

    @Test
    fun upgradesAddTheFirstScheduleAndTheStartingLabels() {
        createDatabase(DB, 1).close()
        upgrade(DB).let { db ->
            assertEquals(AppDatabase.FIRST_SCHEDULE, db.text("SELECT name FROM schedules WHERE id = 1"))
            assertEquals(LinkLabels.defaults.size.toString(), db.text("SELECT COUNT(*) FROM link_labels"))
        }
    }

    /** A database as version [version] created it: its tables, indices and views, and its version number. */
    private fun createDatabase(name: String, version: Int): SQLiteDatabase {
        val schema = JSONObject(java.io.File("schemas/${AppDatabase::class.java.name}/$version.json").readText()).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val views = schema.optJSONArray("views")
        if (views != null) for (i in 0 until views.length()) {
            val view = views.getJSONObject(i)
            db.execSQL(view.getString("createSql").replace("\${VIEW_NAME}", view.getString("viewName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    /** Opens [name] with the app's database, which upgrades it to [LATEST] and checks its schema. */
    private fun upgrade(name: String): SupportSQLiteDatabase {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        opened += database
        return database.openHelper.writableDatabase.also { assertEquals(LATEST, it.version) }
    }

    private fun SupportSQLiteDatabase.text(sql: String): String? = query(sql).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private companion object {
        const val LATEST = 16
        const val DB = "migration-test"
    }
}
