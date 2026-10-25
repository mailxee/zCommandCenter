package com.example.xcommandcenter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.AppContainer
import com.example.xcommandcenter.data.entity.BookWithProgress
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.ProjectEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ProjectTree
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.ui.project.BlockedCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TaskSort(val label: String) { DUE_DATE("Due date"), PRIORITY("Priority") }

data class HabitSummary(
    val habit: HabitEntity,
    val streak: Int,
    val totalMarks: Int,
    val scheduledToday: Boolean,
    val markedToday: Boolean,
)

/** One row of the Projects tab: a project, or a task linked to the project above it. */
sealed interface ProjectTreeItem {
    val key: String
    val depth: Int

    data class Project(
        val project: ProjectEntity,
        override val depth: Int,
        val collapsed: Boolean,
        val subprojectCount: Int,
        val taskCount: Int,
    ) : ProjectTreeItem {
        override val key: String get() = "project-${project.id}"
        val hasChildren: Boolean get() = subprojectCount + taskCount > 0
    }

    /** A task linked to several projects appears under each, so the key includes the project. */
    data class Task(val task: TaskEntity, val projectId: Long, override val depth: Int) : ProjectTreeItem {
        override val key: String get() = "task-$projectId-${task.id}"
    }
}

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val tasksRepo = container.taskRepository
    private val habitsRepo = container.habitRepository
    private val projectsRepo = container.projectRepository
    private val collapsedProjects = container.collapsedProjectsRepository

    val tabOrder: StateFlow<List<ItemType>> = container.tabOrderRepository.order.stateIn(TabOrder.default)

    val linksByOwner: StateFlow<Map<ItemRef, List<ItemIndexEntry>>> =
        container.linkRepository.observeLinksByOwner().stateIn(emptyMap())

    val taskSort = MutableStateFlow(TaskSort.DUE_DATE)

    /** Open tasks first, then by the chosen key; Critical is the highest priority. */
    val tasks: StateFlow<List<TaskEntity>> = combine(tasksRepo.observeAll(), taskSort) { tasks, sort ->
        val byDue = compareBy<TaskEntity, LocalDate?>(nullsLast()) { it.dueDate }
        val byPriority = compareByDescending<TaskEntity> { it.priority.ordinal }
        val order = when (sort) {
            TaskSort.DUE_DATE -> byDue.then(byPriority)
            TaskSort.PRIORITY -> byPriority.then(byDue)
        }
        tasks.sortedWith(compareBy<TaskEntity> { it.completed }.then(order).thenBy { it.title.lowercase() })
    }.stateIn(emptyList())

    val goals: StateFlow<List<GoalEntity>> = container.goalRepository.observeAll().stateIn(emptyList())

    val habits: StateFlow<List<HabitSummary>> =
        combine(habitsRepo.observeAll(), habitsRepo.observeAllMarks()) { habits, marks ->
            val today = LocalDate.now()
            habits.map { habit ->
                val habitMarks = marks[habit.id].orEmpty()
                HabitSummary(
                    habit = habit,
                    streak = HabitStats.streak(habit.category, habit.recurrence, habit.startDate, habitMarks, today),
                    totalMarks = habitMarks.size,
                    scheduledToday = habit.recurrence.isScheduled(today, habit.startDate),
                    markedToday = today in habitMarks,
                )
            }
        }.stateIn(emptyList())

    val showCompletedProjects = MutableStateFlow(false)

    val showCompletedProjectTasks = MutableStateFlow(false)

    /** Tasks linked to each project id: open first, then by due date and title. */
    private val tasksByProject: Flow<Map<Long, List<TaskEntity>>> =
        combine(tasksRepo.observeAll(), linksByOwner) { tasks, links ->
            val byId = tasks.associateBy { it.id }
            val order = compareBy<TaskEntity> { it.completed }
                .thenBy(nullsLast()) { it.dueDate }
                .thenBy { it.title.lowercase() }
            links.filterKeys { it.type == ItemType.PROJECT }
                .map { (ref, entries) ->
                    ref.id to entries.filter { it.type == ItemType.TASK }.mapNotNull { byId[it.id] }.sortedWith(order)
                }
                .toMap()
        }

    /**
     * The Projects tab: the project tree with each project's linked tasks right below it, ahead of
     * its subprojects. Completed projects and tasks are left out unless their toggle is on; hiding
     * a completed project never orphans an open one, because everything below it is completed too.
     * A collapsed project hides its tasks and its whole subtree.
     */
    val projectTree: StateFlow<List<ProjectTreeItem>> = combine(
        projectsRepo.observeAll(),
        tasksByProject,
        showCompletedProjects,
        showCompletedProjectTasks,
        container.collapsedProjectsRepository.collapsed,
    ) { all, tasksByProject, showProjects, showTasks, collapsed ->
        val visible = if (showProjects) all else all.filterNot { it.completed }
        val subprojectCounts = visible.groupingBy { it.parentId }.eachCount()
        ProjectTree.flatten(visible, { it.id }, { it.parentId }, collapsed).flatMap { row ->
            val project = row.node
            val tasks = tasksByProject[project.id].orEmpty().filter { showTasks || !it.completed }
            val isCollapsed = project.id in collapsed
            val projectItem = ProjectTreeItem.Project(project, row.depth, isCollapsed, subprojectCounts[project.id] ?: 0, tasks.size)
            if (isCollapsed) listOf(projectItem)
            else listOf(projectItem) + tasks.map { ProjectTreeItem.Task(it, project.id, row.depth + 1) }
        }
    }.stateIn(emptyList())

    /** Completed tasks linked to any project, for the toggle's label. */
    val completedProjectTaskCount: StateFlow<Int> = tasksByProject
        .map { byProject -> byProject.values.flatten().filter { it.completed }.distinctBy { it.id }.size }
        .stateIn(0)

    val completedProjectCount: StateFlow<Int> = projectsRepo.observeAll()
        .map { all -> all.count { it.completed } }
        .stateIn(0)

    /** Set when ticking a project was refused because of open subprojects. */
    val blockedCompletion = MutableStateFlow<BlockedCompletion?>(null)

    val notes: StateFlow<List<NoteEntity>> = container.noteRepository.observeAll().stateIn(emptyList())

    val books: StateFlow<List<BookWithProgress>> = container.bookRepository.observeAllWithProgress().stateIn(emptyList())

    val quotes: StateFlow<List<QuoteEntity>> = container.quoteRepository.observeAll().stateIn(emptyList())

    fun setTaskSort(sort: TaskSort) {
        taskSort.value = sort
    }

    fun setTaskCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch { tasksRepo.setCompleted(id, completed) }
    }

    fun setShowCompletedProjects(show: Boolean) {
        showCompletedProjects.value = show
    }

    fun setShowCompletedProjectTasks(show: Boolean) {
        showCompletedProjectTasks.value = show
    }

    fun setProjectCollapsed(projectId: Long, collapsed: Boolean) {
        viewModelScope.launch { collapsedProjects.setCollapsed(projectId, collapsed) }
    }

    fun setProjectCompleted(project: ProjectEntity, completed: Boolean) {
        viewModelScope.launch {
            if (completed) {
                val open = projectsRepo.complete(project.id)
                if (open > 0) blockedCompletion.value = BlockedCompletion(project.id, project.title, open)
            } else {
                projectsRepo.reopen(project.id)
            }
        }
    }

    fun completeWithSubprojects(projectId: Long) {
        blockedCompletion.value = null
        viewModelScope.launch { projectsRepo.completeWithSubprojects(projectId) }
    }

    fun dismissBlockedCompletion() {
        blockedCompletion.value = null
    }

    fun toggleHabitToday(habitId: Long) {
        viewModelScope.launch { habitsRepo.toggleMark(habitId, LocalDate.now()) }
    }

    private fun <T> Flow<T>.stateIn(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
