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

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val tasksRepo = container.taskRepository
    private val habitsRepo = container.habitRepository

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

    val projects: StateFlow<List<ProjectTree.Row<ProjectEntity>>> = container.projectRepository.observeAll()
        .map { ProjectTree.flatten(it, { p -> p.id }, { p -> p.parentId }) }
        .stateIn(emptyList())

    val notes: StateFlow<List<NoteEntity>> = container.noteRepository.observeAll().stateIn(emptyList())

    val books: StateFlow<List<BookWithProgress>> = container.bookRepository.observeAllWithProgress().stateIn(emptyList())

    val quotes: StateFlow<List<QuoteEntity>> = container.quoteRepository.observeAll().stateIn(emptyList())

    fun setTaskSort(sort: TaskSort) {
        taskSort.value = sort
    }

    fun setTaskCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch { tasksRepo.setCompleted(id, completed) }
    }

    fun toggleHabitToday(habitId: Long) {
        viewModelScope.launch { habitsRepo.toggleMark(habitId, LocalDate.now()) }
    }

    private fun <T> Flow<T>.stateIn(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
