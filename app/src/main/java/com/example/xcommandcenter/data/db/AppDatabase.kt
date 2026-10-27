package com.example.xcommandcenter.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.xcommandcenter.data.dao.BackupDao
import com.example.xcommandcenter.data.dao.BookDao
import com.example.xcommandcenter.data.dao.GoalDao
import com.example.xcommandcenter.data.dao.HabitDao
import com.example.xcommandcenter.data.dao.LinkDao
import com.example.xcommandcenter.data.dao.NoteDao
import com.example.xcommandcenter.data.dao.PlanDao
import com.example.xcommandcenter.data.dao.QuoteDao
import com.example.xcommandcenter.data.dao.ReminderDao
import com.example.xcommandcenter.data.dao.TagDao
import com.example.xcommandcenter.data.dao.TaskDao
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.HabitMarkEntity
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.ItemTagEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.NoteListItemEntity
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.ReminderEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        ChecklistItemEntity::class,
        GoalEntity::class,
        HabitEntity::class,
        HabitMarkEntity::class,
        PlanEntity::class,
        NoteEntity::class,
        NoteListItemEntity::class,
        BookEntity::class,
        ChapterEntity::class,
        QuoteEntity::class,
        LinkEntity::class,
        TagEntity::class,
        ItemTagEntity::class,
        ReminderEntity::class,
    ],
    views = [ItemIndexEntry::class],
    // Reset to 1 when Projects became Plans; databases from before that are not migrated (the app is reinstalled).
    // 2: habit reminders, note priority and lists, book read count and internalized.
    // 3: priority on goals, habits, plans, books and quotes.
    // 4: the reminders table, any number per item; a task's and a habit's one reminder are copied into it.
    // 5: the old single-reminder columns are dropped (two steps, so step 4 can still read them).
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = AppDatabase.CopyReminders::class),
        AutoMigration(from = 4, to = 5, spec = AppDatabase.DropSingleReminders::class),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun goalDao(): GoalDao
    abstract fun habitDao(): HabitDao
    abstract fun planDao(): PlanDao
    abstract fun noteDao(): NoteDao
    abstract fun bookDao(): BookDao
    abstract fun quoteDao(): QuoteDao
    abstract fun linkDao(): LinkDao
    abstract fun tagDao(): TagDao
    abstract fun backupDao(): BackupDao
    abstract fun reminderDao(): ReminderDao

    /** Moves each task's and habit's single reminder into the new reminders table. */
    class CopyReminders : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO reminders (itemType, itemId, time) SELECT 'TASK', id, reminderAt FROM tasks WHERE reminderAt IS NOT NULL")
            db.execSQL("INSERT INTO reminders (itemType, itemId, time) SELECT 'HABIT', id, reminderMinutes FROM habits WHERE reminderMinutes IS NOT NULL")
        }
    }

    @DeleteColumn(tableName = "tasks", columnName = "reminderAt")
    @DeleteColumn(tableName = "habits", columnName = "reminderMinutes")
    class DropSingleReminders : AutoMigrationSpec

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "xcommandcenter.db")
                .addCallback(
                    object : Callback() {
                        // Runs once, inside the transaction that creates the schema, so the
                        // seed is all-or-nothing and never re-inserted on later launches.
                        override fun onCreate(db: SupportSQLiteDatabase) = SeedData.insert(db)
                    },
                )
                .build()
    }
}
