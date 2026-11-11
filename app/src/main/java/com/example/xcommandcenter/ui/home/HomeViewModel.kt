package com.example.xcommandcenter.ui.home

import com.example.xcommandcenter.data.repository.PersonLists
import com.example.xcommandcenter.data.entity.PersonEntity
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.ReminderToday
import com.example.xcommandcenter.domain.TabModes
import com.example.xcommandcenter.domain.Glossary
import com.example.xcommandcenter.domain.IdeaKindFilter
import com.example.xcommandcenter.domain.IdeaOutline
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.AppContainer
import com.example.xcommandcenter.data.entity.BlockEntity
import com.example.xcommandcenter.data.entity.BookWithProgress
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.ScheduleEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ChipState
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TaskStatusFilter
import com.example.xcommandcenter.domain.ManualOrder
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.domain.SearchScope
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.domain.today
import com.example.xcommandcenter.ui.common.HabitSummary
import com.example.xcommandcenter.ui.common.habitSummary
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

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val tasksRepo = container.taskRepository
    private val habitsRepo = container.habitRepository
    private val plansRepo = container.planRepository
    private val collapsedPlans = container.collapsedPlansRepository
    private val tagsRepo = container.tagRepository
    private val goalsRepo = container.goalRepository
    private val notesRepo = container.noteRepository
    private val booksRepo = container.bookRepository
    private val quotesRepo = container.quoteRepository
    private val peopleRepo = container.personRepository
    private val ideasRepo = container.ideaRepository
    private val blocksRepo = container.blockRepository
    private val schedulesRepo = container.scheduleRepository
    private val taskSortRepo = container.taskSortRepository
    private val linksRepo = container.linkRepository

    /** The glossary's definitions, shown on empty tabs. */
    val terms: StateFlow<Glossary.Terms> = container.glossaryRepository.terms.stateIn(Glossary.Terms())

    val tabOrder: StateFlow<List<ItemType>> = container.tabOrderRepository.order.stateIn(TabOrder.default)

    /** Do or Think, kept across launches. */
    val mode: StateFlow<AppMode> = container.modeRepository.mode.stateIn(AppMode.DO)

    /** Which mode shows each tab. */
    val tabModes: StateFlow<TabModes> = container.modeRepository.tabModes.stateIn(TabModes())

    /** The tabs the current mode shows, in the user's order (never none: then every tab). */
    val shownTabs: StateFlow<List<ItemType>> = combine(tabOrder, tabModes, mode) { order, modes, mode ->
        modes.tabs(order, mode).ifEmpty { order }
    }.stateIn(TabOrder.default)

    /** Switching mode leaves link mode, as its network ran through the other mode's items. */
    fun setMode(mode: AppMode) {
        if (mode == this.mode.value) return
        linksRepo.exitLinkMode()
        viewModelScope.launch { container.modeRepository.setMode(mode) }
    }

    val linksByOwner: StateFlow<Map<ItemRef, List<ItemIndexEntry>>> =
        container.linkRepository.observeLinksByOwner().stateIn(emptyMap())

    /** Link mode's root; null in normal mode. */
    val linkRoot: StateFlow<ItemIndexEntry?> = linksRepo.linkRoot.stateIn(null)

    /** In link mode, the only items every tab shows (see [LinkRepository.network]); null in normal mode. */
    val network: StateFlow<Set<ItemRef>?> = linksRepo.network.stateIn(null)

    /** Tags on at least one item: the choices in the tag filter bar. */
    val allTags: StateFlow<List<TagEntity>> = tagsRepo.observeInUse().stateIn(emptyList())

    private val selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())

    private val excludedTagIds = MutableStateFlow<Set<Long>>(emptySet())

    /** The "Reminder" chip: tap shows only items with a reminder today, hold hides them; lasts like tag choices. */
    private val reminderChip = MutableStateFlow(ChipState.ANY)

    /** Items with a reminder going off today (see [ReminderToday]). */
    private val remindedToday: Flow<Set<ItemRef>> = combine(
        container.reminderRepository.observeAll(),
        container.habitRepository.observeAll(),
        container.habitRepository.observeAllMarks(),
        container.blockRepository.observeAll(),
        combine(container.scheduleRepository.active, today, ::Pair),
    ) { byItem, habitList, marks, blockList, (active, today) ->
        val due = habitList.filter { habitSummary(it, marks[it.id].orEmpty(), today).scheduledToday }.map { it.id }.toSet()
        val shownBlocks = blockList.filter { it.scheduleId == active?.id }.map { it.id }.toSet()
        ReminderToday.items(byItem, today, java.time.ZoneId.systemDefault(), { it in due }, { it in shownBlocks })
    }

    fun tapReminderChip() {
        reminderChip.value = if (reminderChip.value == ChipState.ANY) ChipState.ONLY else ChipState.ANY
    }

    fun holdReminderChip() {
        reminderChip.value = if (reminderChip.value == ChipState.HIDDEN) ChipState.ANY else ChipState.HIDDEN
    }

    /** Only chosen tags that still exist count, so removing a tag from its last item can't leave an empty filter stuck on. */
    val tagFilter: StateFlow<TagFilter> =
        combine(
            combine(tagsRepo.observeTagsByItem(), selectedTagIds, excludedTagIds, ::Triple), allTags, reminderChip, remindedToday,
        ) { (byItem, selected, excluded), inUse, reminder, reminded ->
            val existing = inUse.map { it.id }.toSet() + TagFilter.UNTAGGED
            TagFilter(byItem, selected intersect existing, excluded intersect existing, reminder, reminded)
        }.stateIn(TagFilter())

    /** The search box: every tab shows only the items whose text contains each word typed. */
    val searchQuery = MutableStateFlow("")

    /** The search with each item's checklist, list, chapters, profiles and items, and tag names, which it also looks through. */
    private val search: Flow<SearchScope> = combine(
        searchQuery,
        tasksRepo.observeChecklistTexts(),
        combine(notesRepo.observeListTexts(), peopleRepo.observeLists(), ideasRepo.observeListTexts(), ::Triple),
        booksRepo.observeChapterTitles(),
        tagsRepo.observeTagsByItem(),
    ) { query, checklists, (lists, personLists, ideaLists), chapters, tags ->
        if (query.isBlank()) return@combine SearchScope(query)
        val extra = HashMap<ItemRef, MutableList<String>>()
        fun add(ref: ItemRef, texts: List<String>) = extra.getOrPut(ref) { mutableListOf() }.addAll(texts)
        checklists.forEach { (id, texts) -> add(ItemRef(ItemType.TASK, id), texts) }
        lists.forEach { (id, texts) -> add(ItemRef(ItemType.NOTE, id), texts) }
        ideaLists.forEach { (id, texts) -> add(ItemRef(ItemType.IDEA, id), texts) }
        chapters.forEach { (id, texts) -> add(ItemRef(ItemType.BOOK, id), texts) }
        personLists.forEach { (id, person) -> add(ItemRef(ItemType.PERSON, id), person.profiles + person.items) }
        tags.forEach { (ref, itemTags) -> add(ref, itemTags.map { it.name }) }
        SearchScope(query, extra)
    }

    /** Each item's reminder line for its card (see [reminderLine]). */
    val reminderLines: StateFlow<Map<ItemRef, String>> = container.itemCardSource.reminderLines.stateIn(emptyMap())

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

    private val pastDueChip = MutableStateFlow(ChipState.ANY)
    private val completedOnly = MutableStateFlow(false)

    /**
     * The Tasks tab's Past due and Completed chips. Hiding completed tasks is kept across launches
     * (it was the "Completed tasks" switch); the rest lasts like tag choices.
     */
    val taskStatus: StateFlow<TaskStatusFilter> =
        combine(pastDueChip, completedOnly, showCompletedTasks) { pastDue, only, show ->
            TaskStatusFilter(pastDue, if (only) ChipState.ONLY else if (!show) ChipState.HIDDEN else ChipState.ANY)
        }.stateIn(TaskStatusFilter())

    fun tapTaskStatus(chip: TaskStatusFilter.Chip) = setTaskStatus(taskStatus.value.tap(chip))

    fun holdTaskStatus(chip: TaskStatusFilter.Chip) = setTaskStatus(taskStatus.value.hold(chip))

    private fun setTaskStatus(filter: TaskStatusFilter) {
        pastDueChip.value = filter.pastDue
        completedOnly.value = filter.completed == ChipState.ONLY
        val show = filter.completed != ChipState.HIDDEN
        if (show != showCompletedTasks.value) setShowCompletedTasks(show)
    }

    /** All completed tasks, whatever the filters: for the toggle's label and "Delete completed". */
    val completedTaskCount: StateFlow<Int> = combine(tasksRepo.observeAll(), network) { all, network ->
        all.count { it.completed && network.has(ItemRef(ItemType.TASK, it.id)) }
    }.stateIn(0)

    /** Open tasks first, then by the chosen key; Critical is the highest priority. */
    val tasks: StateFlow<List<TaskEntity>> =
        combine(
            tasksRepo.observeAll(),
            taskSort,
            combine(repeatFilter, taskStatus, ::Pair),
            combine(tagFilter, search, network, ::Triple),
            combine(priorityFilters.map { it[ItemType.TASK].orEmpty() }, today, ::Pair),
        ) { tasks, sort, (repeat, status), (tags, search, network), (priorities, today) ->
            val byDue = compareBy<TaskEntity, LocalDate?>(nullsLast()) { it.dueDate }
            val byPriority = compareByDescending<TaskEntity> { it.priority.ordinal }
            val order = when (sort) {
                TaskSort.DUE_DATE -> byDue.then(byPriority)
                TaskSort.PRIORITY -> byPriority.then(byDue)
                TaskSort.CUSTOM -> compareBy { it.sortOrder }
            }
            tasks.filter {
                network.has(ItemRef(ItemType.TASK, it.id)) && status.matches(it.completed, it.dueDate, today) && repeat.matches(it) &&
                    (priorities.isEmpty() || it.priority in priorities) && tags.matches(ItemRef(ItemType.TASK, it.id)) &&
                    search.matches(ItemRef(ItemType.TASK, it.id), it.title, it.description)
            }
                .sortedWith(compareBy<TaskEntity> { it.completed }.then(order).thenBy { it.title.lowercase() })
        }.stateIn(emptyList())


    val goals: StateFlow<List<GoalEntity>> =
        goalsRepo.observeAll().sortedAndFiltered(ItemType.GOAL, { it.id }, { it.priority }) { listOf(it.title, it.strategy) }

    val habits: StateFlow<List<HabitSummary>> =
        combine(habitsRepo.observeAll(), habitsRepo.observeAllMarks(), today) { habits, marks, today ->
            habits.map { habit -> habitSummary(habit, marks[habit.id].orEmpty(), today) }
        }.sortedAndFiltered(ItemType.HABIT, { it.habit.id }, { it.habit.priority }) { listOf(it.habit.name, it.habit.notes) }

    val showCompletedPlans = MutableStateFlow(false)

    val showCompletedPlanTasks = MutableStateFlow(false)

    /** Tasks linked to each plan id (in link mode, the network's): open first, then by due date and title. */
    private val tasksByPlan: Flow<Map<Long, List<TaskEntity>>> =
        combine(tasksRepo.observeAll(), linksByOwner, network) { tasks, links, network ->
            val byId = tasks.filter { network.has(ItemRef(ItemType.TASK, it.id)) }.associateBy { it.id }
            val order = compareBy<TaskEntity> { it.completed }
                .thenBy(nullsLast()) { it.dueDate }
                .thenBy { it.title.lowercase() }
            links.filterKeys { it.type == ItemType.PLAN }
                .map { (ref, entries) ->
                    ref.id to entries.filter { it.type == ItemType.TASK }.mapNotNull { byId[it.id] }.sortedWith(order)
                }
                .toMap()
        }

    /** The Plans tab's rows and its plan count (see [PlanRows.build]). */
    private val planView: StateFlow<Pair<List<PlanTreeItem>, Int>> = combine(
        plansRepo.observeAll(),
        tasksByPlan,
        combine(showCompletedPlans, showCompletedPlanTasks, ::Pair),
        container.collapsedPlansRepository.collapsed,
        combine(combine(tagFilter, search, network, ::Triple), prioritySorts, priorityFilters, ::Triple),
    ) { all, tasksByPlan, (showPlans, showTasks), collapsed, (tagsQueryNetwork, sorts, filters) ->
        val (tags, search, network) = tagsQueryNetwork
        PlanRows.build(
            all, tasksByPlan, showPlans, showTasks, collapsed, tags, search, network,
            byPriority = sorts[ItemType.PLAN] == PrioritySort.PRIORITY, priorities = filters[ItemType.PLAN].orEmpty(),
        )
    }.stateIn(emptyList<PlanTreeItem>() to 0)

    val planTree: StateFlow<List<PlanTreeItem>> = planView.map { it.first }.stateIn(emptyList())

    /** Completed tasks linked to any plan, for the toggle's label. */
    val completedPlanTaskCount: StateFlow<Int> = tasksByPlan
        .map { byPlan -> byPlan.values.flatten().filter { it.completed }.distinctBy { it.id }.size }
        .stateIn(0)

    val completedPlanCount: StateFlow<Int> = combine(plansRepo.observeAll(), network) { all, network ->
        all.count { it.completed && network.has(ItemRef(ItemType.PLAN, it.id)) }
    }.stateIn(0)

    /** Set when ticking a plan was refused because of open subplans. */
    val blockedCompletion = MutableStateFlow<BlockedCompletion?>(null)

    val notes: StateFlow<List<NoteEntity>> =
        notesRepo.observeAll().sortedAndFiltered(ItemType.NOTE, { it.id }, { it.priority }) { listOf(it.title, it.body) }

    /** List items per note id, for the "list of 3 items" line. */
    val noteListCounts: StateFlow<Map<Long, Int>> = notesRepo.observeListCounts().stateIn(emptyMap())

    /** List items per idea id, for the "List of 3 items" line. */
    val ideaListCounts: StateFlow<Map<Long, Int>> = ideasRepo.observeListCounts().stateIn(emptyMap())

    val books: StateFlow<List<BookWithProgress>> =
        booksRepo.observeAllWithProgress().sortedAndFiltered(ItemType.BOOK, { it.book.id }, { it.book.priority }) {
            listOf(it.book.title, it.book.author, it.book.description)
        }

    val quotes: StateFlow<List<QuoteEntity>> =
        quotesRepo.observeAll().sortedAndFiltered(ItemType.QUOTE, { it.id }, { it.priority }) { listOf(it.text, it.author, it.source) }

    val people: StateFlow<List<PersonEntity>> =
        peopleRepo.observeAll().sortedAndFiltered(ItemType.PERSON, { it.id }, { it.priority }) { listOf(it.name, it.bio) }

    /** The Ideas tab's kind chips. Like tag choices, they last while the app runs. */
    val ideaKinds = MutableStateFlow(IdeaKindFilter())

    fun tapIdeaKind(kind: IdeaKind) {
        ideaKinds.value = ideaKinds.value.tap(kind)
    }

    fun holdIdeaKind(kind: IdeaKind) {
        ideaKinds.value = ideaKinds.value.hold(kind)
    }

    /**
     * The Ideas tab: the idea tree, with collapsed ideas hiding what is under them. With a filter
     * on (kind, tag, priority, search or link mode), only matching ideas show, plus the ideas above
     * them so the tree still reads. Also the number of ideas the tab shows, for its counter.
     */
    private val ideaView: StateFlow<Pair<List<IdeaOutline.Row<IdeaEntity>>, Int>> = combine(
        ideasRepo.observeAll(),
        container.collapsedIdeasRepository.collapsed,
        ideaKinds,
        combine(tagFilter, search, network, ::Triple),
        combine(prioritySorts, priorityFilters, ::Pair),
    ) { all, collapsed, kinds, (tags, search, network), (sorts, filters) ->
        val sorted = if (sorts[ItemType.IDEA] == PrioritySort.PRIORITY) all.sortedByDescending { it.priority.ordinal } else all
        val priorities = filters[ItemType.IDEA].orEmpty()
        val narrowed = kinds.active || tags.active || search.active || network != null || priorities.isNotEmpty()
        fun hit(idea: IdeaEntity): Boolean {
            val ref = ItemRef(ItemType.IDEA, idea.id)
            return kinds.matches(idea.kind) && network.has(ref) && tags.matches(ref) &&
                (priorities.isEmpty() || idea.priority in priorities) && search.matches(ref, idea.title, idea.body)
        }
        val rows = IdeaOutline.rows(sorted, { it.id }, { it.parentId }, collapsed, narrowed, ::hit)
        rows to (if (narrowed) sorted.count(::hit) else sorted.size)
    }.stateIn(emptyList<IdeaOutline.Row<IdeaEntity>>() to 0)

    val ideaTree: StateFlow<List<IdeaOutline.Row<IdeaEntity>>> = ideaView.map { it.first }.stateIn(emptyList())

    fun setIdeaCollapsed(id: Long, collapsed: Boolean) {
        viewModelScope.launch { container.collapsedIdeasRepository.setCollapsed(id, collapsed) }
    }

    /** Each person's profiles and items by id, for their cards. */
    val personLists: StateFlow<Map<Long, PersonLists>> = peopleRepo.observeLists().stateIn(emptyMap())

    /** Every schedule, for the Schedule tab's switcher. */
    val schedules: StateFlow<List<ScheduleEntity>> = schedulesRepo.schedules.stateIn(emptyList())

    /** The schedule the Schedule tab shows (see [ScheduleRepository.active]). */
    val activeSchedule: StateFlow<ScheduleEntity?> = schedulesRepo.active.stateIn(null)

    /** Whether each day starts on the schedule set for its weekday. */
    val scheduleAutomatic: StateFlow<Boolean> = schedulesRepo.choice.map { it.automatic }.stateIn(false)

    private val activeBlocks: Flow<List<BlockEntity>> =
        combine(blocksRepo.observeAll(), schedulesRepo.active) { blocks, active -> blocks.filter { it.scheduleId == active?.id } }

    /** The Schedule tab: the active schedule's blocks by start time, passing link mode, the tag filter and the search. */
    val blocks: StateFlow<List<BlockEntity>> =
        combine(activeBlocks, tagFilter, search, network) { blocks, tags, search, network ->
            blocks.filter {
                val ref = ItemRef(ItemType.BLOCK, it.id)
                network.has(ref) && tags.matches(ref) && search.matches(ref, it.title, it.notes)
            }
        }.stateIn(emptyList())

    /** Free stretches the user has opened on the Schedule tab, as "scheduleId:start-end"; the rest stay folded. Kept while the app runs. */
    val openGaps = MutableStateFlow<Set<String>>(emptySet())

    fun toggleGap(key: String) {
        openGaps.value = openGaps.value.let { if (key in it) it - key else it + key }
    }

    fun selectSchedule(id: Long) {
        viewModelScope.launch { schedulesRepo.select(id) }
    }

    fun createSchedule(name: String) {
        viewModelScope.launch { schedulesRepo.create(name.trim()) }
    }

    fun renameSchedule(id: Long, name: String) {
        viewModelScope.launch { schedulesRepo.rename(id, name.trim()) }
    }

    fun duplicateSchedule(id: Long, name: String) {
        viewModelScope.launch { schedulesRepo.duplicate(id, name.trim()) }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch { schedulesRepo.delete(id) }
    }

    fun setScheduleDays(id: Long, days: Int) {
        viewModelScope.launch { schedulesRepo.setDays(id, days) }
    }

    fun setScheduleAutomatic(on: Boolean) {
        viewModelScope.launch { schedulesRepo.setAutomatic(on) }
    }

    /**
     * The tags on the items each tab lists, for its tag filter bar. The Plans tab also lists the
     * tasks linked to plans, so their tags count there too. In link mode, only the network's items count.
     */
    val tabTagIds: StateFlow<Map<ItemType, Set<Long>>> =
        combine(tagsRepo.observeTagsByItem(), tasksByPlan, network) { byItem, tasksByPlan, network ->
            val planTaskIds = tasksByPlan.values.flatten().map { it.id }.toSet()
            ItemType.tabs.associateWith { tab ->
                byItem.filterKeys { ref ->
                    network.has(ref) && (ref.type == tab || (tab == ItemType.PLAN && ref.type == ItemType.TASK && ref.id in planTaskIds))
                }
                    .values.flatten().map { it.id }.toSet()
            }
        }.stateIn(emptyMap())

    /**
     * Each tab's counter: the items it shows, out of all its items (in link mode, all the
     * network's). The Plans tab counts plans, not their tasks; the Books tab counts books.
     */
    val tabCounts: StateFlow<Map<ItemType, TabCount>> = run {
        fun <T> count(type: ItemType, shown: Flow<List<T>>, all: Flow<List<Long>>): Flow<Pair<ItemType, TabCount>> =
            combine(shown.map { it.size }, all, network) { shownCount, ids, network ->
                type to TabCount(shownCount, ids.count { network.has(ItemRef(type, it)) })
            }
        combine(
            listOf(
                count(ItemType.TASK, tasks, tasksRepo.observeAll().map { all -> all.map { it.id } }),
                count(ItemType.GOAL, goals, goalsRepo.observeAll().map { all -> all.map { it.id } }),
                count(ItemType.HABIT, habits, habitsRepo.observeAll().map { all -> all.map { it.id } }),
                combine(planView, plansRepo.observeAll(), network) { (_, shown), all, network ->
                    ItemType.PLAN to TabCount(shown, all.count { network.has(ItemRef(ItemType.PLAN, it.id)) })
                },
                count(ItemType.NOTE, notes, notesRepo.observeAll().map { all -> all.map { it.id } }),
                count(ItemType.BOOK, books, booksRepo.observeAllWithProgress().map { all -> all.map { it.book.id } }),
                count(ItemType.QUOTE, quotes, quotesRepo.observeAll().map { all -> all.map { it.id } }),
                count(ItemType.BLOCK, blocks, activeBlocks.map { all -> all.map { it.id } }),
                count(ItemType.PERSON, people, peopleRepo.observeAll().map { all -> all.map { it.id } }),
                combine(ideaView, ideasRepo.observeAll(), network) { (_, shown), all, network ->
                    ItemType.IDEA to TabCount(shown, all.count { network.has(ItemRef(ItemType.IDEA, it.id)) })
                },
            ),
        ) { it.toMap() }.stateIn(emptyMap())
    }

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

    /** Also turns off the Tasks tab's Past due and Completed chips and the Ideas tab's kind chips. */
    fun clearTags() {
        selectedTagIds.value = emptySet()
        excludedTagIds.value = emptySet()
        reminderChip.value = ChipState.ANY
        ideaKinds.value = IdeaKindFilter()
        setTaskStatus(TaskStatusFilter())
    }

    fun setShowCompletedTasks(show: Boolean) {
        viewModelScope.launch { taskSortRepo.setShowCompleted(show) }
    }

    /** In link mode, only the network's completed tasks. */
    fun deleteCompletedTasks() {
        val network = network.value
        viewModelScope.launch {
            tasksRepo.deleteCompleted(network?.filter { it.type == ItemType.TASK }?.map { it.id }?.toSet())
        }
    }

    /** A card pressed and held: link mode on with it as the root (or the root moved to it). Off while reordering. */
    fun showNetwork(ref: ItemRef) {
        if (!reorderMode.value) linksRepo.showNetwork(ref)
    }

    /** The banner's cross: back to normal mode. */
    fun exitLinkMode() {
        linksRepo.exitLinkMode()
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
                ItemType.PERSON -> people.value.map { it.id }
                ItemType.IDEA -> {
                    val shownIdeas = ideaTree.value.map { it.node }
                    val parentId = shownIdeas.firstOrNull { it.id == id }?.parentId
                    shownIdeas.filter { it.parentId == parentId }.map { it.id }
                }
                ItemType.BLOCK -> return@launch // Blocks are ordered by time.
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
        ItemType.PERSON -> peopleRepo.observeAll().first().map { it.id }
        ItemType.IDEA -> {
            val all = ideasRepo.observeAll().first()
            val parentId = all.firstOrNull { it.id == anyId }?.parentId
            all.filter { it.parentId == parentId }.map { it.id }
        }
        ItemType.BLOCK -> emptyList()
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
        ItemType.PERSON -> peopleRepo.reorder(ids)
        ItemType.IDEA -> ideasRepo.reorder(ids)
        ItemType.BLOCK -> Unit
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
     * Keeps the items of [type] that pass link mode, the shared tag filter, the search (over the
     * item's [text]) and the tab's priority filter, in the tab's sort. Sorting by priority is stable, so
     * items of the same priority keep their custom order.
     */
    private fun <T> Flow<List<T>>.sortedAndFiltered(
        type: ItemType,
        id: (T) -> Long,
        priority: (T) -> Priority,
        text: (T) -> List<String>,
    ): StateFlow<List<T>> =
        combine(this, tagFilter, prioritySorts, priorityFilters, combine(search, network, ::Pair)) { items, tags, sorts, filters, (search, network) ->
            val priorities = filters[type].orEmpty()
            val shown = items.filter {
                network.has(ItemRef(type, id(it))) && (priorities.isEmpty() || priority(it) in priorities) && tags.matches(ItemRef(type, id(it))) &&
                    search.matches(ItemRef(type, id(it)), *text(it).toTypedArray())
            }
            if (sorts[type] == PrioritySort.PRIORITY) shown.sortedByDescending { priority(it).ordinal } else shown
        }.stateIn(emptyList())

    private fun <T> Flow<T>.stateIn(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
