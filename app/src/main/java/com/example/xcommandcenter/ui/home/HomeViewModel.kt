package com.example.xcommandcenter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.AppContainer
import com.example.xcommandcenter.data.entity.BookWithProgress
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ManualOrder
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.domain.Search
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.ui.common.HabitSummary
import com.example.xcommandcenter.ui.common.habitSummary
import com.example.xcommandcenter.ui.common.reminderLine
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

/**
 * How the Goals, Habits, Plans, Notes, Books and Quotes tabs are sorted: [CUSTOM] is the user's own
 * order, the only one in which items can be moved; [PRIORITY] puts Critical first, keeping the
 * custom order among items of the same priority.
 */
enum class PrioritySort(val label: String) { CUSTOM("Custom"), PRIORITY("Priority") }

/** The tabs sorted and filtered by [PrioritySort] and a priority filter; Tasks have their own sorts but the same priority filter. */
val priorityTabs: Set<ItemType> = setOf(ItemType.GOAL, ItemType.HABIT, ItemType.PLAN, ItemType.NOTE, ItemType.BOOK, ItemType.QUOTE)

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

/**
 * The tag filter shared by every tab: an item shows only if it carries all [selected] tags and none
 * of the [excluded] ones (tags pressed and held in the filter bar). [UNTAGGED] stands for "no
 * tags": selected, it shows only items without tags; excluded, it hides them.
 */
data class TagFilter(
    val tagsByItem: Map<ItemRef, List<TagEntity>> = emptyMap(),
    val selected: Set<Long> = emptySet(),
    val excluded: Set<Long> = emptySet(),
) {
    val active: Boolean get() = selected.isNotEmpty() || excluded.isNotEmpty()

    fun tagsOf(ref: ItemRef): List<TagEntity> = tagsByItem[ref].orEmpty()

    /** The item's tag ids, or [UNTAGGED] alone for an item without tags. */
    private fun idsOf(ref: ItemRef): Set<Long> = tagsOf(ref).map { it.id }.toSet().ifEmpty { setOf(UNTAGGED) }

    /** True when the item carries an excluded tag (or has none, with [UNTAGGED] excluded). */
    fun excludes(ref: ItemRef): Boolean = excluded.isNotEmpty() && idsOf(ref).any { it in excluded }

    fun matches(ref: ItemRef): Boolean =
        (selected.isEmpty() || idsOf(ref).containsAll(selected)) && !excludes(ref)

    companion object {
        /** The "Untagged" chip; no tag has this id. */
        const val UNTAGGED = -1L
    }
}

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

    private val excludedTagIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Only chosen tags that still exist count, so removing a tag from its last item can't leave an empty filter stuck on. */
    val tagFilter: StateFlow<TagFilter> =
        combine(tagsRepo.observeTagsByItem(), selectedTagIds, excludedTagIds, allTags) { byItem, selected, excluded, inUse ->
            val existing = inUse.map { it.id }.toSet() + TagFilter.UNTAGGED
            TagFilter(byItem, selected intersect existing, excluded intersect existing)
        }.stateIn(TagFilter())

    /** The search box: every tab shows only the items whose text contains each word typed. */
    val searchQuery = MutableStateFlow("")

    /** Each item's reminder line for its card (see [reminderLine]). */
    val reminderLines: StateFlow<Map<ItemRef, String>> = container.reminderRepository.observeAll()
        .map { byItem -> byItem.mapNotNull { (ref, times) -> reminderLine(ref.type, times)?.let { ref to it } }.toMap() }
        .stateIn(emptyMap())

    val taskSort: StateFlow<TaskSort> = taskSortRepo.sort
        .map { name -> TaskSort.entries.firstOrNull { it.name == name } ?: TaskSort.DUE_DATE }
        .stateIn(TaskSort.DUE_DATE)

    /** While on, every tab shows a drag handle on its rows instead of their usual trailing controls. */
    val reorderMode = MutableStateFlow(false)

    /** Ticks per task id, for the "Checklist 2/5" line. */
    val checklistCounts: StateFlow<Map<Long, ChecklistCount>> = tasksRepo.observeChecklistCounts().stateIn(emptyMap())

    val repeatFilter = MutableStateFlow(RepeatFilter.ALL)

    /** Each tab's sort; a tab not in the map uses [PrioritySort.CUSTOM]. */
    val prioritySorts = MutableStateFlow<Map<ItemType, PrioritySort>>(emptyMap())

    /** Each tab's priority filter, Tasks included: the priorities to show; empty or missing shows every item. */
    val priorityFilters = MutableStateFlow<Map<ItemType, Set<Priority>>>(emptyMap())

    /** Whether the Tasks tab lists completed tasks; kept across launches. */
    val showCompletedTasks: StateFlow<Boolean> = taskSortRepo.showCompleted.stateIn(true)

    /** All completed tasks, whatever the filters: for the toggle's label and "Delete completed". */
    val completedTaskCount: StateFlow<Int> = tasksRepo.observeAll().map { all -> all.count { it.completed } }.stateIn(0)

    /** Open tasks first, then by the chosen key; Critical is the highest priority. */
    val tasks: StateFlow<List<TaskEntity>> =
        combine(
            tasksRepo.observeAll(),
            taskSort,
            combine(repeatFilter, showCompletedTasks, ::Pair),
            combine(tagFilter, searchQuery, ::Pair),
            priorityFilters.map { it[ItemType.TASK].orEmpty() },
        ) { tasks, sort, (repeat, showCompleted), (tags, query), priorities ->
            val byDue = compareBy<TaskEntity, LocalDate?>(nullsLast()) { it.dueDate }
            val byPriority = compareByDescending<TaskEntity> { it.priority.ordinal }
            val order = when (sort) {
                TaskSort.DUE_DATE -> byDue.then(byPriority)
                TaskSort.PRIORITY -> byPriority.then(byDue)
                TaskSort.CUSTOM -> compareBy { it.sortOrder }
            }
            tasks.filter {
                (showCompleted || !it.completed) && repeat.matches(it) &&
                    (priorities.isEmpty() || it.priority in priorities) && tags.matches(ItemRef(ItemType.TASK, it.id)) &&
                    Search.matches(query, it.title, it.description)
            }
                .sortedWith(compareBy<TaskEntity> { it.completed }.then(order).thenBy { it.title.lowercase() })
        }.stateIn(emptyList())


    val goals: StateFlow<List<GoalEntity>> =
        goalsRepo.observeAll().sortedAndFiltered(ItemType.GOAL, { it.id }, { it.priority }) { listOf(it.title, it.strategy) }

    val habits: StateFlow<List<HabitSummary>> =
        combine(habitsRepo.observeAll(), habitsRepo.observeAllMarks()) { habits, marks ->
            val today = LocalDate.now()
            habits.map { habit -> habitSummary(habit, marks[habit.id].orEmpty(), today) }
        }.sortedAndFiltered(ItemType.HABIT, { it.habit.id }, { it.habit.priority }) { listOf(it.habit.name, it.habit.notes) }

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
     * A collapsed plan hides its tasks and its whole subtree. With a tag filter or a search on,
     * only matching plans and tasks show, plus the plans above them so the tree still reads.
     */
    val planTree: StateFlow<List<PlanTreeItem>> = combine(
        plansRepo.observeAll(),
        tasksByPlan,
        combine(showCompletedPlans, showCompletedPlanTasks, ::Pair),
        container.collapsedPlansRepository.collapsed,
        combine(combine(tagFilter, searchQuery, ::Pair), prioritySorts, priorityFilters, ::Triple),
    ) { all, tasksByPlan, (showPlans, showTasks), collapsed, (tagsAndQuery, sorts, filters) ->
        val (tags, query) = tagsAndQuery
        val narrowed = tags.active || query.isNotBlank()
        // Sorting the plans sorts siblings: the tree keeps each plan's children in the order given.
        val sorted = if (sorts[ItemType.PLAN] == PrioritySort.PRIORITY) all.sortedByDescending { it.priority.ordinal } else all
        val shown = if (showPlans) sorted else sorted.filterNot { it.completed }
        val priorities = filters[ItemType.PLAN].orEmpty()
        fun tasksOf(planId: Long) = tasksByPlan[planId].orEmpty()
            .filter {
                (showTasks || !it.completed) && tags.matches(ItemRef(ItemType.TASK, it.id)) && Search.matches(query, it.title, it.description)
            }
        val visible = if (!narrowed && priorities.isEmpty()) {
            shown
        } else {
            // The priority filter applies to the plans themselves; their tasks are shown as usual.
            val hits = shown.filter { plan ->
                (priorities.isEmpty() || plan.priority in priorities) &&
                    (!narrowed || (tags.matches(ItemRef(ItemType.PLAN, plan.id)) && Search.matches(query, plan.title, plan.description)) ||
                        (!tags.excludes(ItemRef(ItemType.PLAN, plan.id)) && tasksOf(plan.id).isNotEmpty()))
            }.map { it.id }
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

    val notes: StateFlow<List<NoteEntity>> =
        notesRepo.observeAll().sortedAndFiltered(ItemType.NOTE, { it.id }, { it.priority }) { listOf(it.title, it.body) }

    /** List items per note id, for the "list of 3 items" line. */
    val noteListCounts: StateFlow<Map<Long, Int>> = notesRepo.observeListCounts().stateIn(emptyMap())

    val books: StateFlow<List<BookWithProgress>> =
        booksRepo.observeAllWithProgress().sortedAndFiltered(ItemType.BOOK, { it.book.id }, { it.book.priority }) {
            listOf(it.book.title, it.book.author, it.book.description)
        }

    val quotes: StateFlow<List<QuoteEntity>> =
        quotesRepo.observeAll().sortedAndFiltered(ItemType.QUOTE, { it.id }, { it.priority }) { listOf(it.text, it.author, it.source) }

    /**
     * The tags on the items each tab lists, for its tag filter bar. The Plans tab also lists the
     * tasks linked to plans, so their tags count there too.
     */
    val tabTagIds: StateFlow<Map<ItemType, Set<Long>>> =
        combine(tagsRepo.observeTagsByItem(), tasksByPlan) { byItem, tasksByPlan ->
            val planTaskIds = tasksByPlan.values.flatten().map { it.id }.toSet()
            ItemType.tabs.associateWith { tab ->
                byItem.filterKeys { ref -> ref.type == tab || (tab == ItemType.PLAN && ref.type == ItemType.TASK && ref.id in planTaskIds) }
                    .values.flatten().map { it.id }.toSet()
            }
        }.stateIn(emptyMap())

    /** A tap: shows only items with the tag, or turns that off. A hidden tag becomes plain again. */
    fun toggleTag(tagId: Long) {
        if (tagId in excludedTagIds.value) {
            excludedTagIds.value -= tagId
        } else {
            selectedTagIds.value = selectedTagIds.value.let { if (tagId in it) it - tagId else it + tagId }
        }
    }

    /** A press and hold: hides items with the tag on every tab, or turns that off. */
    fun toggleExcludedTag(tagId: Long) {
        selectedTagIds.value -= tagId
        excludedTagIds.value = excludedTagIds.value.let { if (tagId in it) it - tagId else it + tagId }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearTags() {
        selectedTagIds.value = emptySet()
        excludedTagIds.value = emptySet()
    }

    fun setShowCompletedTasks(show: Boolean) {
        viewModelScope.launch { taskSortRepo.setShowCompleted(show) }
    }

    fun deleteCompletedTasks() {
        viewModelScope.launch { tasksRepo.deleteCompleted() }
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

    fun setPrioritySort(tab: ItemType, sort: PrioritySort) {
        prioritySorts.value = prioritySorts.value + (tab to sort)
    }

    fun togglePriority(tab: ItemType, priority: Priority) {
        val current = priorityFilters.value[tab].orEmpty()
        priorityFilters.value = priorityFilters.value + (tab to if (priority in current) current - priority else current + priority)
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

    /**
     * Keeps the items of [type] that pass the shared tag filter, the search (over the item's
     * [text]) and the tab's priority filter, in the tab's sort. Sorting by priority is stable, so
     * items of the same priority keep their custom order.
     */
    private fun <T> Flow<List<T>>.sortedAndFiltered(
        type: ItemType,
        id: (T) -> Long,
        priority: (T) -> Priority,
        text: (T) -> List<String>,
    ): StateFlow<List<T>> =
        combine(this, tagFilter, prioritySorts, priorityFilters, searchQuery) { items, tags, sorts, filters, query ->
            val priorities = filters[type].orEmpty()
            val shown = items.filter {
                (priorities.isEmpty() || priority(it) in priorities) && tags.matches(ItemRef(type, id(it))) &&
                    Search.matches(query, *text(it).toTypedArray())
            }
            if (sorts[type] == PrioritySort.PRIORITY) shown.sortedByDescending { priority(it).ordinal } else shown
        }.stateIn(emptyList())

    private fun <T> Flow<T>.stateIn(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
