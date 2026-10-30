package com.example.xcommandcenter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ManualOrder
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.ui.common.DragHandle
import com.example.xcommandcenter.ui.common.HabitWeek
import com.example.xcommandcenter.ui.common.HabitTodayButton
import com.example.xcommandcenter.ui.common.ItemCard
import com.example.xcommandcenter.ui.common.bookCardText
import com.example.xcommandcenter.ui.common.goalCardText
import com.example.xcommandcenter.ui.common.habitCardText
import com.example.xcommandcenter.ui.common.noteCardText
import com.example.xcommandcenter.ui.common.planCardText
import com.example.xcommandcenter.ui.common.quoteCardText
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon
import com.example.xcommandcenter.ui.common.taskCardText
import com.example.xcommandcenter.ui.plan.CompletePlanDialog
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TasksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val sort by viewModel.taskSort.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatFilter.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val checklists by viewModel.checklistCounts.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val showCompleted by viewModel.showCompletedTasks.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedTaskCount.collectAsStateWithLifecycle()
    val priorityView = viewModel.priorityView(ItemType.TASK)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Tasks can only be moved in the custom order; in the others their place follows from their fields.
    val movable = reorder && sort == TaskSort.CUSTOM
    val drag = rememberDragOrder(tasks, key = { it.id })
    // Open and completed tasks are moved separately, as the list keeps them apart: while one is
    // dragged, only tasks in its group can make way for it.
    var draggingCompleted by remember { mutableStateOf<Boolean?>(null) }
    // Either filter can empty the list; then say so instead of "No tasks yet".
    val rows = drag.rows
    // The completed row arrives after the tasks, above them, and the list keeps its first task in
    // place, which would leave the row out of sight. Scroll up to it while still at the top.
    val hasCompleted = completedCount > 0
    LaunchedEffect(hasCompleted) {
        if (hasCompleted && drag.listState.firstVisibleItemIndex <= 1) drag.listState.scrollToItem(0)
    }
    ItemList(
        ItemType.TASK,
        empty = tasks.isEmpty(),
        emptyText = "No tasks yet.",
        filtered = viewModel.filtering(tagFilter) || repeat != RepeatFilter.ALL || priorityView.filtered || (!showCompleted && completedCount > 0),
        controls = controls, state = drag.listState,
    ) {
        if (controls) item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TaskSort.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = sort == option,
                        onClick = { viewModel.setTaskSort(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, TaskSort.entries.size),
                        label = { Text(option.label, maxLines = 1) },
                    )
                }
            }
        }
        if (controls) item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RepeatFilter.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = repeat == option,
                        onClick = { viewModel.setRepeatFilter(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, RepeatFilter.entries.size),
                        label = { Text(option.label, maxLines = 1) },
                    )
                }
            }
        }
        if (controls) priorityFilterChips(ItemType.TASK, priorityView, viewModel)
        if (completedCount > 0) {
            item {
                // The chip takes the room the button leaves, so its label stays on one line.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = showCompleted,
                        onClick = { viewModel.setShowCompletedTasks(!showCompleted) },
                        label = { Text("Completed tasks ($completedCount)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete completed") }
                }
            }
        }
        itemsIndexed(rows, key = { _, task -> task.id }) { index, task ->
            val ref = ItemRef(ItemType.TASK, task.id)
            ReorderableItem(drag.state, key = task.id, enabled = movable && draggingCompleted.let { it == null || it == task.completed }) {
                ItemCard(
                    ItemType.TASK,
                    text = taskCardText(task, checklists[task.id]),
                    links = links[ref].orEmpty(),
                    tags = tagFilter.tagsOf(ref),
                    reminder = viewModel.reminderOf(ref),
                    onClick = { onOpen(ref) },
                    leading = { Checkbox(checked = task.completed, onCheckedChange = { viewModel.setTaskCompleted(task.id, it) }) },
                    trailing = dragHandleIf(
                        reorder = movable,
                        canMoveUp = rows.getOrNull(index - 1)?.completed == task.completed,
                        canMoveDown = rows.getOrNull(index + 1)?.completed == task.completed,
                        onMove = { viewModel.move(ItemType.TASK, task.id, it) },
                        onDragStarted = { draggingCompleted = task.completed },
                        onDrop = {
                            draggingCompleted = null
                            viewModel.reorder(ItemType.TASK, drag.rows.map { it.id })
                        },
                    ),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete completed tasks?") },
            text = {
                Text(
                    "This deletes all $completedCount completed tasks, including any hidden by the filters, with their links, " +
                        "tags and checklists. Open tasks, including the next occurrence of repeating ones, stay.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteCompletedTasks() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun GoalsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val drag = rememberDragOrder(goals, key = { it.id })
    val rows = drag.rows
    val view = viewModel.priorityView(ItemType.GOAL)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    ItemList(ItemType.GOAL, empty = goals.isEmpty(), emptyText = "No goals yet.", filtered = viewModel.filtering(tagFilter) || view.filtered, controls = controls, state = drag.listState) {
        if (controls) priorityControls(ItemType.GOAL, view, viewModel)
        itemsIndexed(rows, key = { _, goal -> goal.id }) { index, goal ->
            val ref = ItemRef(ItemType.GOAL, goal.id)
            ReorderableItem(drag.state, key = goal.id, enabled = movable) {
                ItemCard(
                    ItemType.GOAL,
                    goalCardText(goal),
                    links[ref].orEmpty(),
                    { onOpen(ref) },
                    tags = tagFilter.tagsOf(ref),
                    reminder = viewModel.reminderOf(ref),
                    trailing = dragHandleIf(movable, index, rows.size, { viewModel.move(ItemType.GOAL, goal.id, it) }) {
                        viewModel.reorder(ItemType.GOAL, drag.rows.map { it.id })
                    },
                )
            }
        }
    }
}

@Composable
fun HabitsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val drag = rememberDragOrder(habits, key = { it.habit.id })
    val rows = drag.rows
    val view = viewModel.priorityView(ItemType.HABIT)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    ItemList(ItemType.HABIT, empty = habits.isEmpty(), emptyText = "No habits yet.", filtered = viewModel.filtering(tagFilter) || view.filtered, controls = controls, state = drag.listState) {
        if (controls) priorityControls(ItemType.HABIT, view, viewModel)
        itemsIndexed(rows, key = { _, summary -> summary.habit.id }) { index, summary ->
            val habit = summary.habit
            val ref = ItemRef(ItemType.HABIT, habit.id)
            ReorderableItem(drag.state, key = habit.id, enabled = movable) {
                ItemCard(
                    ItemType.HABIT,
                    text = habitCardText(summary),
                    links = links[ref].orEmpty(),
                    tags = tagFilter.tagsOf(ref),
                    reminder = viewModel.reminderOf(ref),
                    onClick = { onOpen(ref) },
                    trailing = dragHandleIf(movable, index, rows.size, { viewModel.move(ItemType.HABIT, habit.id, it) }) {
                        viewModel.reorder(ItemType.HABIT, drag.rows.map { it.habit.id })
                    } ?: {
                        if (summary.scheduledToday) {
                            HabitTodayButton(summary.positive, summary.markedToday) { viewModel.toggleHabitToday(habit.id) }
                        }
                    },
                    // This week only; the habit's page has the full calendar.
                    footer = { HabitWeek(habit, summary.marks) },
                )
            }
        }
    }
}

@Composable
fun PlansTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val rows by viewModel.planTree.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val showCompleted by viewModel.showCompletedPlans.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedPlanCount.collectAsStateWithLifecycle()
    val showCompletedTasks by viewModel.showCompletedPlanTasks.collectAsStateWithLifecycle()
    val completedTaskCount by viewModel.completedPlanTaskCount.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedCompletion.collectAsStateWithLifecycle()
    val checklists by viewModel.checklistCounts.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val allHidden = rows.isEmpty() && completedCount > 0 && !showCompleted
    val view = viewModel.priorityView(ItemType.PLAN)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    // A plan moves among the shown plans with the same parent, taking everything under it along.
    val drag = rememberDragOrder(rows, key = { it.key }) { list, from, to -> ManualOrder.moveBlock(list, { it.depth }, from, to) }
    var dragging by remember { mutableStateOf<PlanEntity?>(null) }
    // While a plan is dragged, the rows under it are hidden, so only the plan's own row moves on screen.
    val shownRows = dragging?.let { plan -> withoutRowsUnder(drag.rows, plan.id) } ?: drag.rows
    val siblings = shownRows.filterIsInstance<PlanTreeItem.Plan>().groupBy({ it.plan.parentId }, { it.plan.id })
    ItemList(
        ItemType.PLAN,
        empty = rows.isEmpty(),
        emptyText = if (allHidden) "All plans are completed." else "No plans yet.",
        filtered = viewModel.filtering(tagFilter) || view.filtered,
        controls = controls, state = drag.listState,
    ) {
        if (controls) priorityControls(ItemType.PLAN, view, viewModel)
        if (completedCount > 0 || completedTaskCount > 0) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (completedCount > 0) {
                        FilterChip(
                            selected = showCompleted,
                            onClick = { viewModel.setShowCompletedPlans(!showCompleted) },
                            label = { Text("Completed plans ($completedCount)") },
                        )
                    }
                    if (completedTaskCount > 0) {
                        FilterChip(
                            selected = showCompletedTasks,
                            onClick = { viewModel.setShowCompletedPlanTasks(!showCompletedTasks) },
                            label = { Text("Completed tasks ($completedTaskCount)") },
                        )
                    }
                }
            }
        }
        items(shownRows, key = { it.key }) { row ->
            // Only plans with the dragged plan's parent make way for it; task rows never move on their own.
            val enabled = movable && row is PlanTreeItem.Plan && dragging.let { it == null || it.parentId == row.plan.parentId }
            ReorderableItem(drag.state, key = row.key, enabled = enabled) {
                Box(Modifier.padding(start = (row.depth * 20).dp)) {
                    when (row) {
                        is PlanTreeItem.Plan -> {
                            val group = siblings[row.plan.parentId].orEmpty()
                            val index = group.indexOf(row.plan.id)
                            PlanRow(
                                row, links, tagFilter, viewModel, onOpen,
                                dragHandle = dragHandleIf(
                                    reorder = movable,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < group.lastIndex,
                                    onMove = { viewModel.move(ItemType.PLAN, row.plan.id, it) },
                                    onDragStarted = { dragging = row.plan },
                                    onDrop = {
                                        val parentId = row.plan.parentId
                                        dragging = null
                                        viewModel.reorder(
                                            ItemType.PLAN,
                                            drag.rows.filterIsInstance<PlanTreeItem.Plan>().filter { it.plan.parentId == parentId }.map { it.plan.id },
                                        )
                                    },
                                ),
                            )
                        }
                        is PlanTreeItem.Task -> PlanTaskRow(row.task, tagFilter, checklists[row.task.id], viewModel, onOpen)
                    }
                }
            }
        }
    }
    blocked?.let {
        CompletePlanDialog(
            blocked = it,
            onCompleteAll = { viewModel.completeWithSubplans(it.planId) },
            onDismiss = viewModel::dismissBlockedCompletion,
        )
    }
}

/** @param dragHandle shown instead of the collapse button while reordering. */
@Composable
private fun PlanRow(
    row: PlanTreeItem.Plan,
    links: Map<ItemRef, List<ItemIndexEntry>>,
    tagFilter: TagFilter,
    viewModel: HomeViewModel,
    onOpen: (ItemRef) -> Unit,
    dragHandle: (@Composable () -> Unit)?,
) {
    val plan = row.plan
    val ref = ItemRef(ItemType.PLAN, plan.id)
    // Linked tasks are rows of their own below the plan, so the link lines leave them out.
    val otherLinks = links[ref].orEmpty().filter { it.type != ItemType.TASK }
    val hidden = listOfNotNull(
        row.subplanCount.takeIf { it > 0 }?.let { if (it == 1) "1 subplan" else "$it subplans" },
        row.taskCount.takeIf { it > 0 }?.let { if (it == 1) "1 task" else "$it tasks" },
    )
    ItemCard(
        ItemType.PLAN,
        text = planCardText(plan, hidden.takeIf { row.collapsed && it.isNotEmpty() }?.joinToString(" · ", postfix = " hidden")),
        links = otherLinks,
        tags = tagFilter.tagsOf(ref),
        reminder = viewModel.reminderOf(ref),
        onClick = { onOpen(ref) },
        leading = {
            Checkbox(checked = plan.completed, onCheckedChange = { viewModel.setPlanCompleted(plan, it) })
        },
        trailing = dragHandle ?: {
            if (row.hasChildren) {
                IconButton(onClick = { viewModel.setPlanCollapsed(plan.id, !row.collapsed) }) {
                    Icon(
                        painterResource(if (row.collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                        contentDescription = if (row.collapsed) "Expand" else "Collapse",
                    )
                }
            }
        },
    )
}

/** A task linked to the plan above it; outlined so it reads differently from a plan. */
@Composable
private fun PlanTaskRow(task: TaskEntity, tagFilter: TagFilter, checklist: ChecklistCount?, viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val ref = ItemRef(ItemType.TASK, task.id)
    ItemCard(
        ItemType.TASK,
        text = taskCardText(task, checklist),
        links = emptyList(),
        tags = tagFilter.tagsOf(ref),
        onClick = { onOpen(ref) },
        outlined = true,
        leading = { Checkbox(checked = task.completed, onCheckedChange = { viewModel.setTaskCompleted(task.id, it) }) },
    )
}

@Composable
fun NotesTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val listCounts by viewModel.noteListCounts.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val view = viewModel.priorityView(ItemType.NOTE)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    val drag = rememberDragOrder(notes, key = { it.id })
    val rows = drag.rows
    ItemList(
        ItemType.NOTE,
        empty = notes.isEmpty(),
        emptyText = "No notes yet.",
        filtered = viewModel.filtering(tagFilter) || view.filtered,
        controls = controls, state = drag.listState,
    ) {
        if (controls) priorityControls(ItemType.NOTE, view, viewModel)
        itemsIndexed(rows, key = { _, note -> note.id }) { index, note ->
            val ref = ItemRef(ItemType.NOTE, note.id)
            ReorderableItem(drag.state, key = note.id, enabled = movable) {
                ItemCard(
                    ItemType.NOTE,
                    text = noteCardText(note, listCounts[note.id] ?: 0),
                    links = links[ref].orEmpty(),
                    tags = tagFilter.tagsOf(ref),
                    reminder = viewModel.reminderOf(ref),
                    onClick = { onOpen(ref) },
                    trailing = dragHandleIf(movable, index, rows.size, { viewModel.move(ItemType.NOTE, note.id, it) }) {
                        viewModel.reorder(ItemType.NOTE, drag.rows.map { it.id })
                    },
                )
            }
        }
    }
}

@Composable
fun BooksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val drag = rememberDragOrder(books, key = { it.book.id })
    val rows = drag.rows
    val view = viewModel.priorityView(ItemType.BOOK)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    ItemList(ItemType.BOOK, empty = books.isEmpty(), emptyText = "No books yet.", filtered = viewModel.filtering(tagFilter) || view.filtered, controls = controls, state = drag.listState) {
        if (controls) priorityControls(ItemType.BOOK, view, viewModel)
        itemsIndexed(rows, key = { _, item -> item.book.id }) { index, item ->
            val ref = ItemRef(ItemType.BOOK, item.book.id)
            ReorderableItem(drag.state, key = item.book.id, enabled = movable) {
                ItemCard(
                    ItemType.BOOK,
                    text = bookCardText(item),
                    links = links[ref].orEmpty(),
                    tags = tagFilter.tagsOf(ref),
                    reminder = viewModel.reminderOf(ref),
                    onClick = { onOpen(ref) },
                    trailing = dragHandleIf(movable, index, rows.size, { viewModel.move(ItemType.BOOK, item.book.id, it) }) {
                        viewModel.reorder(ItemType.BOOK, drag.rows.map { it.book.id })
                    },
                )
            }
        }
    }
}

@Composable
fun QuotesTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val drag = rememberDragOrder(quotes, key = { it.id })
    val rows = drag.rows
    val view = viewModel.priorityView(ItemType.QUOTE)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    ItemList(ItemType.QUOTE, empty = quotes.isEmpty(), emptyText = "No quotes yet.", filtered = viewModel.filtering(tagFilter) || view.filtered, controls = controls, state = drag.listState) {
        if (controls) priorityControls(ItemType.QUOTE, view, viewModel)
        itemsIndexed(rows, key = { _, quote -> quote.id }) { index, quote ->
            val ref = ItemRef(ItemType.QUOTE, quote.id)
            ReorderableItem(drag.state, key = quote.id, enabled = movable) {
                ItemCard(
                    ItemType.QUOTE,
                    text = quoteCardText(quote),
                    links = links[ref].orEmpty(),
                    tags = tagFilter.tagsOf(ref),
                    reminder = viewModel.reminderOf(ref),
                    onClick = { onOpen(ref) },
                    trailing = dragHandleIf(movable, index, rows.size, { viewModel.move(ItemType.QUOTE, quote.id, it) }) {
                        viewModel.reorder(ItemType.QUOTE, drag.rows.map { it.id })
                    },
                )
            }
        }
    }
}

/** Whether a filter or the search is hiding items, so an empty list says "no matches". */
@Composable
private fun HomeViewModel.filtering(tagFilter: TagFilter): Boolean {
    val query by searchQuery.collectAsStateWithLifecycle()
    return tagFilter.active || query.isNotBlank()
}

/** The item's reminder line for its card. */
@Composable
private fun HomeViewModel.reminderOf(ref: ItemRef): String? {
    val lines by reminderLines.collectAsStateWithLifecycle()
    return lines[ref]
}

/** A tab's sort and priority filter, as the tab's controls show them. */
private data class PriorityView(val sort: PrioritySort, val priorities: Set<Priority>) {
    val filtered: Boolean get() = priorities.isNotEmpty()
}

@Composable
private fun HomeViewModel.priorityView(tab: ItemType): PriorityView {
    val sorts by prioritySorts.collectAsStateWithLifecycle()
    val filters by priorityFilters.collectAsStateWithLifecycle()
    return PriorityView(sorts[tab] ?: PrioritySort.CUSTOM, filters[tab].orEmpty())
}

/**
 * The rows at the top of a tab with priorities: the sort (Custom or Priority) and the priority
 * filter chips, any combination of which can be on; none shows every item.
 */
private fun LazyListScope.priorityControls(tab: ItemType, view: PriorityView, viewModel: HomeViewModel) {
    item {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PrioritySort.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = view.sort == option,
                    onClick = { viewModel.setPrioritySort(tab, option) },
                    shape = SegmentedButtonDefaults.itemShape(index, PrioritySort.entries.size),
                    label = { Text(option.label, maxLines = 1) },
                )
            }
        }
    }
    priorityFilterChips(tab, view, viewModel)
}

/** One chip per priority; any combination can be on, and none shows every item. */
private fun LazyListScope.priorityFilterChips(tab: ItemType, view: PriorityView, viewModel: HomeViewModel) {
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { priority ->
                FilterChip(
                    selected = priority in view.priorities,
                    onClick = { viewModel.togglePriority(tab, priority) },
                    label = { Text(priority.label) },
                )
            }
        }
    }
}

/** What the add button does, for the empty lists' own add button. */
val LocalCreateItem = staticCompositionLocalOf<(ItemType) -> Unit> { {} }

@Composable
private fun ItemList(
    type: ItemType,
    empty: Boolean,
    emptyText: String,
    filtered: Boolean = false,
    controls: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    // The sort and filter rows open at the top of the list; scroll up to them, or they would open
    // above the cards in view, out of sight.
    LaunchedEffect(controls) { if (controls) state.scrollToItem(0) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
        if (empty) {
            item { EmptyList(type, if (filtered) "Nothing here matches the current filters." else emptyText, offerAdd = !filtered) }
        }
    }
}

/** An empty list: the tab's icon, why it is empty, and a button to add the first item. */
@Composable
private fun EmptyList(type: ItemType, text: String, offerAdd: Boolean) {
    val create = LocalCreateItem.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
    ) {
        val accent = tabAccent(type)
        Box(Modifier.size(72.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(tabIcon(type)), contentDescription = null, tint = accent, modifier = Modifier.size(36.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (offerAdd) {
            FilledTonalButton(onClick = { create(type) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New ${type.singular.lowercase()}")
            }
        }
    }
}

/**
 * A tab's rows as they can be dragged: [rows] is a local copy of the stored rows that follows the
 * finger during a drag, and is replaced whenever the stored rows change (after a drop is saved).
 */
private class DragOrder<T>(val listState: LazyListState, val state: ReorderableLazyListState, private val local: State<List<T>>) {
    val rows: List<T> get() = local.value
}

/** [move] reorders the local rows when the dragged row passes another: from and to are indices in it. */
@Composable
private fun <T> rememberDragOrder(
    items: List<T>,
    key: (T) -> Any,
    move: (List<T>, Int, Int) -> List<T> = { list, from, to -> list.toMutableList().apply { add(to, removeAt(from)) } },
): DragOrder<T> {
    val listState = rememberLazyListState()
    val local = remember(items) { mutableStateOf(items) }
    val state = rememberReorderableLazyListState(listState) { from, to ->
        val list = local.value
        val fromIndex = list.indexOfFirst { key(it) == from.key }
        val toIndex = list.indexOfFirst { key(it) == to.key }
        if (fromIndex >= 0 && toIndex >= 0) local.value = move(list, fromIndex, toIndex)
    }
    return DragOrder(listState, state, local)
}

/**
 * While [reorder] is on, a drag handle for the row; otherwise null, so the row shows its usual
 * trailing content. [onDrop] saves the order when a drag ends; [onMove] moves the row one place
 * (-1 up, +1 down) for accessibility services.
 */
private fun ReorderableCollectionItemScope.dragHandleIf(
    reorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onDragStarted: () -> Unit = {},
    onDrop: () -> Unit,
): (@Composable () -> Unit)? =
    if (reorder) {
        {
            DragHandle(
                Modifier.draggableHandle(onDragStarted = { onDragStarted() }, onDragStopped = onDrop),
                onMoveUp = if (canMoveUp) ({ onMove(-1) }) else null,
                onMoveDown = if (canMoveDown) ({ onMove(1) }) else null,
            )
        }
    } else {
        null
    }

/** [dragHandleIf] for row [index] of [count] in a plain list. */
private fun ReorderableCollectionItemScope.dragHandleIf(
    reorder: Boolean,
    index: Int,
    count: Int,
    onMove: (Int) -> Unit,
    onDrop: () -> Unit,
): (@Composable () -> Unit)? = dragHandleIf(reorder, index > 0, index < count - 1, onMove, onDrop = onDrop)

/** [rows] without the rows under plan [planId] (its tasks and subplans, at any depth). */
private fun withoutRowsUnder(rows: List<PlanTreeItem>, planId: Long): List<PlanTreeItem> {
    val start = rows.indexOfFirst { it is PlanTreeItem.Plan && it.plan.id == planId }
    if (start < 0) return rows
    var end = start + 1
    while (end < rows.size && rows[end].depth > rows[start].depth) end++
    return rows.subList(0, start + 1) + rows.subList(end, rows.size)
}
