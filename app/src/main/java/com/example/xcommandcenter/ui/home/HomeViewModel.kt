package com.example.xcommandcenter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.AppContainer
import com.example.xcommandcenter.data.entity.BookWithProgress
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ManualOrder
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.ui.plan.BlockedCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** [CUSTOM] is the user's own order, set by reordering; the only sort in which tasks can be moved. */
enum class TaskSort(val label: String) { DUE_DATE("Due date"), PRIORITY("Priority"), CUSTOM("Custom") }

enum class RepeatFilter(val label: String) {
    ALL("All"),
    REPEATING("Repeating"),
    ONE_OFF("One-off"),
    ;

    fun matches(task: TaskEntity): Boolean = when (this) {
        ALL -> true
        REPEATING -> task.recurrenceType != null
        ONE_OFF -> task.recurrenceType == null
    }
}

/** The tag filter shared by every tab: an item shows only if it carries all [selected] tags. */
data class TagFilter(
    val tagsByItem: Map<ItemRef, List<TagEntity>> = emptyMap(),
    val selected: Set<Long> = emptySet(),
) {
    val active: Boolean get() = selected.isNotEmpty()

    fun tagsOf(ref: ItemRef): List<TagEntity> = tagsByItem[ref].orEmpty()

    fun matches(ref: ItemRef): Boolean = selected.isEmpty() || tagsOf(ref).map { it.id }.containsAll(selected)
}

data class HabitSummary(
    val habit: HabitEntity,
    val streak: Int,
    val totalMarks: Int,
    val scheduledToday: Boolean,
    val markedToday: Boolean,
)

/** One row of the Plans tab: a plan, or a task linked to the plan above it. */
sealed interface PlanTreeItem {
    val key: String
    val depth: Int

    data class Plan(
        val plan: PlanEntity,
        override val depth: Int,
        val collapsed: Boolean,
        val subplanCount: Int,
        val taskCount: Int,
    ) : PlanTreeItem {
        override val key: String get() = "plan-${plan.id}"
        val hasChildren: Boolean get() = subplanCount + taskCount > 0
    }

    /** A task linked to several plans appears under each, so the key includes the plan. */
    data class Task(val task: TaskEntity, val planId: Long, override val depth: Int) : PlanTreeItem {
        override val key: String get() = "task-$planId-${task.id}"
    }
}

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val tasksRepo = container.taskRepository
    private val habitsRepo = container.habitRepository
    private val plansRepo = container.planRepository
    private val collapsedPlans = container.collapsedPlansRepository
    private val tagsRepo = container.tagRepository
    private val goalsRepo = container.goalRepository
    private val notesRepo = container.noteRepository
    private val booksRepo = container.bookRepository
    private val quotesRepo = container.quoteRepository
    private val taskSortRepo = container.taskSortRepository

    val tabOrder: StateFlow<List<ItemType>> = container.tabOrderRepository.order.stateIn(TabOrder.default)

    val linksByOwner: StateFlow<Map<ItemRef, List<ItemIndexEntry>>> =
        container.linkRepository.observeLinksByOwner().stateIn(emptyMap())

    /** Tags on at least one item: the choices in the tag filter bar. */
    val allTags: StateFlow<List<TagEntity>> = tagsRepo.observeInUse().stateIn(emptyList())

    private val selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Only selected tags that still exist count, so removing a tag from its last item can't leave an empty filter stuck on. */
    val tagFilter: StateFlow<TagFilter> =
        combine(tagsRepo.observeTagsByItem(), selectedTagIds, allTags) { byItem, selected, inUse ->
            TagFilter(byItem, selected intersect inUse.map { it.id }.toSet())
        }.stateIn(TagFilter())

    val taskSort: StateFlow<TaskSort> = taskSortRepo.sort
        .map { name -> TaskSort.entries.firstOrNull { it.name == name } ?: TaskSort.DUE_DATE }
        .stateIn(TaskSort.DUE_DATE)

    /** While on, every tab shows a drag handle on its rows instead of their usual trailing controls. */
    val reorderMode = MutableStateFlow(false)

    /** Ticks per task id, for the "Checklist 2/5" line. */
    val checklistCounts: StateFlow<Map<Long, ChecklistCount>> = tasksRepo.observeChecklistCounts().stateIn(emptyMap())

    val repeatFilter = MutableStateFlow(RepeatFilter.ALL)

    /** Open tasks first, then by the chosen key; Critical is the highest priority. */
    val tasks: StateFlow<List<TaskEntity>> =
        combine(tasksRepo.observeAll(), taskSort, repeatFilter, tagFilter) { tasks, sort, repeat, tags ->
            val byDue = compareBy<TaskEntity, LocalDate?>(nullsLast()) { it.dueDate }
            val byPriority = compareByDescending<TaskEntity> { it.priority.ordinal }
            val order = when (sort) {
                TaskSort.DUE_DATE -> byDue.then(byPriority)
                TaskSort.PRIORITY -> byPriority.then(byDue)
                TaskSort.CUSTOM -> compareBy { it.sortOrder }
            }
            tasks.filter { repeat.matches(it) && tags.matches(ItemRef(ItemType.TASK, it.id)) }
                .sortedWith(compareBy<TaskEntity> { it.completed }.then(order).thenBy { it.title.lowercase() })
        }.stateIn(emptyList())

    val goals: StateFlow<List<GoalEntity>> = goalsRepo.observeAll().filteredByTags(ItemType.GOAL) { it.id }

    val habits: StateFlow<List<HabitSummary>> =
        combine(habitsRepo.observeAll(), habitsRepo.observeAllMarks(), tagFilter) { habits, marks, tags ->
            val today = LocalDate.now()
            habits.filter { tags.matches(ItemRef(ItemType.HABIT, it.id)) }.map { habit ->
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

    val showCompletedPlans = MutableStateFlow(false)

    val showCompletedPlanTasks = MutableStateFlow(false)

    /** Tasks linked to each plan id: open first, then by due date and title. */
    private val tasksByPlan: Flow<Map<Long, List<TaskEntity>>> =
        combine(tasksRepo.observeAll(), linksByOwner) { tasks, links ->
            val byId = tasks.associateBy { it.id }
            val order = compareBy<TaskEntity> { it.completed }
                .thenBy(nullsLast()) { it.dueDate }
                .thenBy { it.title.lowercase() }
            links.filterKeys { it.type == ItemType.PLAN }
                .map { (ref, entries) ->
                    ref.id to entries.filter { it.type == ItemType.TASK }.mapNotNull { byId[it.id] }.sortedWith(order)
                }
                .toMap()
        }

    /**
     * The Plans tab: the plan tree with each plan's linked tasks right below it, ahead of
     * its subplans. Completed plans and tasks are left out unless their toggle is on; hiding
     * a completed plan never orphans an open one, because everything below it is completed too.
     * A collapsed plan hides its tasks and its whole subtree. With a tag filter on, only
     * matching plans and tasks show, plus the plans above them so the tree still reads.
     */
    val planTree: StateFlow<List<PlanTreeItem>> = combine(
        plansRepo.observeAll(),
        tasksByPlan,
        combine(showCompletedPlans, showCompletedPlanTasks, ::Pair),
        container.collapsedPlansRepository.collapsed,
        tagFilter,
    ) { all, tasksByPlan, (showPlans, showTasks), collapsed, tags ->
        val shown = if (showPlans) all else all.filterNot { it.completed }
        fun tasksOf(planId: Long) = tasksByPlan[planId].orEmpty()
            .filter { (showTasks || !it.completed) && tags.matches(ItemRef(ItemType.TASK, it.id)) }
        val visible = if (!tags.active) {
            shown
        } else {
            val hits = shown.filter { tags.matches(ItemRef(ItemType.PLAN, it.id)) || tasksOf(it.id).isNotEmpty() }.map { it.id }
            val keep = hits.toSet() + hits.flatMap { PlanTree.ancestorIds(it, shown, { p -> p.id }, { p -> p.parentId }) }
            shown.filter { it.id in keep }
        }
        val subplanCounts = visible.groupingBy { it.parentId }.eachCount()
        PlanTree.flatten(visible, { it.id }, { it.parentId }, collapsed).flatMap { row ->
            val plan = row.node
            val tasks = tasksOf(plan.id)
            val isCollapsed = plan.id in collapsed
            val planItem = PlanTreeItem.Plan(plan, row.depth, isCollapsed, subplanCounts[plan.id] ?: 0, tasks.size)
            if (isCollapsed) listOf(planItem)
            else listOf(planItem) + tasks.map { PlanTreeItem.Task(it, plan.id, row.depth + 1) }
        }
    }.stateIn(emptyList())

    /** Completed tasks linked to any plan, for the toggle's label. */
    val completedPlanTaskCount: StateFlow<Int> = tasksByPlan
        .map { byPlan -> byPlan.values.flatten().filter { it.completed }.distinctBy { it.id }.size }
        .stateIn(0)

    val completedPlanCount: StateFlow<Int> = plansRepo.observeAll()
        .map { all -> all.count { it.completed } }
        .stateIn(0)

    /** Set when ticking a plan was refused because of open subplans. */
    val blockedCompletion = MutableStateFlow<BlockedCompletion?>(null)

    val notes: StateFlow<List<NoteEntity>> = notesRepo.observeAll().filteredByTags(ItemType.NOTE) { it.id }

    val books: StateFlow<List<BookWithProgress>> =
        booksRepo.observeAllWithProgress().filteredByTags(ItemType.BOOK) { it.book.id }

    val quotes: StateFlow<List<QuoteEntity>> = quotesRepo.observeAll().filteredByTags(ItemType.QUOTE) { it.id }

    fun toggleTag(tagId: Long) {
        selectedTagIds.value = selectedTagIds.value.let { if (tagId in it) it - tagId else it + tagId }
    }

    fun clearTags() {
        selectedTagIds.value = emptySet()
    }

    fun setTaskSort(sort: TaskSort) {
        viewModelScope.launch { taskSortRepo.setSort(sort.name) }
    }

    fun setReorderMode(on: Boolean) {
        reorderMode.value = on
    }

    /**
     * Stores the order of [shownIds] after a drag: the rows shown in a tab (for plans, one set of
     * siblings), first to last. Rows hidden by a filter keep their places.
     */
    fun reorder(type: ItemType, shownIds: List<Long>) {
        viewModelScope.launch {
            val all = orderedIds(type, shownIds.firstOrNull() ?: return@launch)
            saveOrder(type, ManualOrder.reorder(all, shownIds))
        }
    }

    /**
     * Moves an item one place up (-1) or down (+1) among the rows shown in its tab; the step
     * version of a drag, offered to accessibility services. Open and completed tasks are kept
     * apart, as the list shows them, and a plan moves among its sibling plans.
     */
    fun move(type: ItemType, id: Long, offset: Int) {
        viewModelScope.launch {
            val shown = when (type) {
                ItemType.TASK -> {
                    val task = tasks.value.firstOrNull { it.id == id } ?: return@launch
                    tasks.value.filter { it.completed == task.completed }.map { it.id }
                }
                ItemType.GOAL -> goals.value.map { it.id }
                ItemType.HABIT -> habits.value.map { it.habit.id }
                ItemType.PLAN -> {
                    val shownPlans = planTree.value.filterIsInstance<PlanTreeItem.Plan>().map { it.plan }
                    val parentId = shownPlans.firstOrNull { it.id == id }?.parentId
                    shownPlans.filter { it.parentId == parentId }.map { it.id }
                }
                ItemType.NOTE -> notes.value.map { it.id }
                ItemType.BOOK -> books.value.map { it.book.id }
                ItemType.QUOTE -> quotes.value.map { it.id }
                ItemType.CHAPTER -> return@launch // Chapters are ordered on their book's page.
            }
            ManualOrder.move(orderedIds(type, id), shown, id, offset)?.let { saveOrder(type, it) }
        }
    }

    /** Every id of [type] in its stored order; for plans, the siblings of plan [anyId]. */
    private suspend fun orderedIds(type: ItemType, anyId: Long): List<Long> = when (type) {
        ItemType.TASK -> tasksRepo.observeAll().first()
            .sortedWith(compareBy<TaskEntity> { it.completed }.thenBy { it.sortOrder }.thenBy { it.title.lowercase() })
            .map { it.id }
        ItemType.GOAL -> goalsRepo.observeAll().first().map { it.id }
        ItemType.HABIT -> habitsRepo.observeAll().first().map { it.id }
        ItemType.PLAN -> {
            val all = plansRepo.observeAll().first()
            val parentId = all.firstOrNull { it.id == anyId }?.parentId
            all.filter { it.parentId == parentId }.map { it.id }
        }
        ItemType.NOTE -> notesRepo.observeAll().first().map { it.id }
        ItemType.BOOK -> booksRepo.observeAllWithProgress().first().map { it.book.id }
        ItemType.QUOTE -> quotesRepo.observeAll().first().map { it.id }
        ItemType.CHAPTER -> emptyList()
    }

    private suspend fun saveOrder(type: ItemType, ids: List<Long>) = when (type) {
        ItemType.TASK -> tasksRepo.reorder(ids)
        ItemType.GOAL -> goalsRepo.reorder(ids)
        ItemType.HABIT -> habitsRepo.reorder(ids)
        ItemType.PLAN -> plansRepo.reorder(ids)
        ItemType.NOTE -> notesRepo.reorder(ids)
        ItemType.BOOK -> booksRepo.reorder(ids)
        ItemType.QUOTE -> quotesRepo.reorder(ids)
        ItemType.CHAPTER -> Unit
    }

    fun setRepeatFilter(filter: RepeatFilter) {
        repeatFilter.value = filter
    }

    fun setTaskCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch { tasksRepo.setCompleted(id, completed) }
    }

    fun setShowCompletedPlans(show: Boolean) {
        showCompletedPlans.value = show
    }

    fun setShowCompletedPlanTasks(show: Boolean) {
        showCompletedPlanTasks.value = show
    }

    fun setPlanCollapsed(planId: Long, collapsed: Boolean) {
        viewModelScope.launch { collapsedPlans.setCollapsed(planId, collapsed) }
    }

    fun setPlanCompleted(plan: PlanEntity, completed: Boolean) {
        viewModelScope.launch {
            if (completed) {
                val open = plansRepo.complete(plan.id)
                if (open > 0) blockedCompletion.value = BlockedCompletion(plan.id, plan.title, open)
            } else {
                plansRepo.reopen(plan.id)
            }
        }
    }

    fun completeWithSubplans(planId: Long) {
        blockedCompletion.value = null
        viewModelScope.launch { plansRepo.completeWithSubplans(planId) }
    }

    fun dismissBlockedCompletion() {
        blockedCompletion.value = null
    }

    fun toggleHabitToday(habitId: Long) {
        viewModelScope.launch { habitsRepo.toggleMark(habitId, LocalDate.now()) }
    }

    /** Keeps the items of [type] that pass the shared tag filter. */
    private fun <T> Flow<List<T>>.filteredByTags(type: ItemType, id: (T) -> Long): StateFlow<List<T>> =
        combine(this, tagFilter) { items, tags -> items.filter { tags.matches(ItemRef(type, id(it))) } }.stateIn(emptyList())

    private fun <T> Flow<T>.stateIn(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
