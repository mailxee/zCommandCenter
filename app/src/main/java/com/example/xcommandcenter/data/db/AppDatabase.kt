package com.example.xcommandcenter.data.db

import android.content.Context
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
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
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
        BookEntity::class,
        ChapterEntity::class,
        QuoteEntity::class,
        LinkEntity::class,
        TagEntity::class,
        ItemTagEntity::class,
    ],
    views = [ItemIndexEntry::class],
    // Reset to 1 when Projects became Plans; databases from earlier builds are not migrated (the app is reinstalled).
    version = 1,
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
