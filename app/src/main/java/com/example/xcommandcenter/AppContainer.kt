package com.example.xcommandcenter

import com.example.xcommandcenter.data.repository.PersonRepository
import com.example.xcommandcenter.data.repository.IdeaRepository
import com.example.xcommandcenter.data.prefs.ModeRepository
import com.example.xcommandcenter.data.repository.GlossaryRepository
import android.content.Context
import com.example.xcommandcenter.data.backup.BackupRepository
import com.example.xcommandcenter.data.backup.AutoBackupRepository
import com.example.xcommandcenter.data.db.AppDatabase
import com.example.xcommandcenter.data.prefs.CollapsedPlansRepository
import com.example.xcommandcenter.data.prefs.ItemStyleRepository
import com.example.xcommandcenter.data.prefs.ReminderPrefsRepository
import com.example.xcommandcenter.data.prefs.RoutePrefsRepository
import com.example.xcommandcenter.data.prefs.ScheduleChoiceRepository
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.data.prefs.TaskSortRepository
import com.example.xcommandcenter.data.repository.BlockRepository
import com.example.xcommandcenter.data.repository.BookRepository
import com.example.xcommandcenter.data.repository.GoalRepository
import com.example.xcommandcenter.data.repository.HabitRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.NoteRepository
import com.example.xcommandcenter.data.repository.PlanRepository
import com.example.xcommandcenter.data.repository.QuoteRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.ScheduleRepository
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
    val collapsedIdeasRepository = CollapsedPlansRepository(context, CollapsedPlansRepository.IDEAS_KEY)
    val routePrefsRepository = RoutePrefsRepository(context)
    val modeRepository = ModeRepository(context)
    val linkRepository = LinkRepository(
        database.linkDao(), routePrefsRepository.route, modeRepository.mode, modeRepository.tabModes,
        routePrefsRepository.thinkRoute, routePrefsRepository.checkWhy,
    )
    val tagRepository = TagRepository(database.tagDao())
    val reminderPrefsRepository = ReminderPrefsRepository(context)
    val reminderRepository: ReminderRepository = ReminderRepository(
        database.reminderDao(),
        database.habitDao(),
        reminderScheduler,
        activeSchedule = { scheduleRepository.activeId() },
        carryDays = { reminderPrefsRepository.carryDaysNow() },
    )
    private val scheduleChoiceRepository = ScheduleChoiceRepository(context)
    val scheduleRepository: ScheduleRepository = ScheduleRepository(database.scheduleDao(), scheduleChoiceRepository) { reminderRepository }
    val taskRepository = TaskRepository(database.taskDao(), reminderRepository)
    val goalRepository = GoalRepository(database.goalDao(), reminderRepository)
    val habitRepository = HabitRepository(database.habitDao(), reminderRepository)
    val planRepository = PlanRepository(database.planDao(), reminderRepository)
    val noteRepository = NoteRepository(database.noteDao(), reminderRepository)
    val bookRepository = BookRepository(database.bookDao(), reminderRepository)
    val quoteRepository = QuoteRepository(database.quoteDao(), reminderRepository)
    val blockRepository = BlockRepository(database.blockDao(), reminderRepository)
    val personRepository = PersonRepository(database.personDao(), reminderRepository)
    val ideaRepository = IdeaRepository(database.ideaDao(), noteRepository, reminderRepository)
    val glossaryRepository = GlossaryRepository(database.glossaryDao())
    val itemCardSource = ItemCardSource(
        taskRepository, goalRepository, habitRepository, planRepository, noteRepository, bookRepository, quoteRepository,
        blockRepository, personRepository, ideaRepository, glossaryRepository, scheduleRepository, linkRepository, tagRepository, reminderRepository,
    )
    val backupRepository = BackupRepository(
        context, database.backupDao(), reminderRepository, tabOrderRepository, itemStyleRepository, scheduleChoiceRepository, modeRepository,
    )
    val autoBackupRepository = AutoBackupRepository(context, backupRepository)
}
