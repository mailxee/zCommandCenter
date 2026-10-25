package com.example.xcommandcenter

import android.content.Context
import com.example.xcommandcenter.data.db.AppDatabase
import com.example.xcommandcenter.data.prefs.CollapsedProjectsRepository
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.data.repository.BookRepository
import com.example.xcommandcenter.data.repository.GoalRepository
import com.example.xcommandcenter.data.repository.HabitRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.NoteRepository
import com.example.xcommandcenter.data.repository.ProjectRepository
import com.example.xcommandcenter.data.repository.QuoteRepository
import com.example.xcommandcenter.data.repository.TaskRepository
import com.example.xcommandcenter.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-written dependency container: one instance per process, owned by [XCommandCenterApp]. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AppDatabase.create(context)

    val reminderScheduler = ReminderScheduler(context)
    val tabOrderRepository = TabOrderRepository(context)
    val collapsedProjectsRepository = CollapsedProjectsRepository(context)
    val linkRepository = LinkRepository(database.linkDao())
    val taskRepository = TaskRepository(database.taskDao(), reminderScheduler)
    val goalRepository = GoalRepository(database.goalDao())
    val habitRepository = HabitRepository(database.habitDao())
    val projectRepository = ProjectRepository(database.projectDao())
    val noteRepository = NoteRepository(database.noteDao())
    val bookRepository = BookRepository(database.bookDao())
    val quoteRepository = QuoteRepository(database.quoteDao())
}
