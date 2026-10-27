package com.example.xcommandcenter

import android.content.Context
import com.example.xcommandcenter.data.backup.BackupRepository
import com.example.xcommandcenter.data.db.AppDatabase
import com.example.xcommandcenter.data.prefs.CollapsedPlansRepository
import com.example.xcommandcenter.data.prefs.ItemStyleRepository
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.data.prefs.TaskSortRepository
import com.example.xcommandcenter.data.repository.BookRepository
import com.example.xcommandcenter.data.repository.GoalRepository
import com.example.xcommandcenter.data.repository.HabitRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.NoteRepository
import com.example.xcommandcenter.data.repository.PlanRepository
import com.example.xcommandcenter.data.repository.QuoteRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.data.repository.TaskRepository
import com.example.xcommandcenter.reminder.ReminderScheduler
import com.example.xcommandcenter.ui.common.ItemCardSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-written dependency container: one instance per process, owned by [XCommandCenterApp]. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AppDatabase.create(context)

    val reminderScheduler = ReminderScheduler(context)
    val tabOrderRepository = TabOrderRepository(context)
    val taskSortRepository = TaskSortRepository(context)
    val itemStyleRepository = ItemStyleRepository(context)
    val collapsedPlansRepository = CollapsedPlansRepository(context)
    val linkRepository = LinkRepository(database.linkDao())
    val tagRepository = TagRepository(database.tagDao())
    val taskRepository = TaskRepository(database.taskDao(), reminderScheduler)
    val goalRepository = GoalRepository(database.goalDao())
    val habitRepository = HabitRepository(database.habitDao(), reminderScheduler)
    val planRepository = PlanRepository(database.planDao())
    val noteRepository = NoteRepository(database.noteDao())
    val bookRepository = BookRepository(database.bookDao())
    val quoteRepository = QuoteRepository(database.quoteDao())
    val itemCardSource = ItemCardSource(
        taskRepository, goalRepository, habitRepository, planRepository, noteRepository, bookRepository, quoteRepository,
        linkRepository, tagRepository,
    )
    val backupRepository = BackupRepository(context, database.backupDao(), taskRepository, habitRepository, reminderScheduler, tabOrderRepository)
}
