package com.example.xcommandcenter.ui.home

import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.ui.common.personCardText
import com.example.xcommandcenter.ui.common.ideaCardText
import com.example.xcommandcenter.domain.Glossary
import com.example.xcommandcenter.ui.common.KindIcon
import com.example.xcommandcenter.data.entity.IdeaEntity
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ChipState
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ManualOrder
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.ui.common.DragHandle
import com.example.xcommandcenter.ui.common.HabitWeek
import com.example.xcommandcenter.ui.common.OutlineGuides
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
    val status by viewModel.taskStatus.collectAsStateWithLifecycle()
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
    // The delete row arrives after the tasks, above them, and the list keeps its first task in
    // place, which would leave the row out of sight. Scroll up to it while still at the top.
    val hasDeleteRow = completedCount > 0 && status.completed == ChipState.ONLY
    LaunchedEffect(hasDeleteRow) {
        if (hasDeleteRow && drag.listState.firstVisibleItemIndex <= 1) drag.listState.scrollToItem(0)
    }
    ItemList(
        ItemType.TASK,
        empty = tasks.isEmpty(),
        emptyText = "No tasks yet.",
        filtered = viewModel.filtering(tagFilter) || repeat != RepeatFilter.ALL || priorityView.filtered || status.active,
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
        // Showing only completed tasks (the Completed chip) offers to delete them.
        if (completedCount > 0 && status.completed == ChipState.ONLY) {
            item {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete completed ($completedCount)") }
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
                    onLongClick = { viewModel.showNetwork(ref) },
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
                    onLongClick = { viewModel.showNetwork(ref) },
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
                    onLongClick = { viewModel.showNetwork(ref) },
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
        // A plan and everything under it read as one card; the gap is between top-level plans.
        spacing = 0.dp,
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
        val guides = outlineGuides(shownRows)
        itemsIndexed(shownRows, key = { _, row -> row.key }) { i, row ->
            val last = shownRows.getOrNull(i + 1)?.depth?.let { it == 0 } ?: true
            val round = 12.dp
            val shape = RoundedCornerShape(
                topStart = if (row.depth == 0) round else 0.dp, topEnd = if (row.depth == 0) round else 0.dp,
                bottomStart = if (last) round else 0.dp, bottomEnd = if (last) round else 0.dp,
            )
            // Only plans with the dragged plan's parent make way for it; task rows never move on their own.
            val enabled = movable && row is PlanTreeItem.Plan && dragging.let { it == null || it.parentId == row.plan.parentId }
            ReorderableItem(drag.state, key = row.key, enabled = enabled) {
                Box(Modifier.padding(top = if (row.depth == 0) 6.dp else 0.dp)) {
                    when (row) {
                        is PlanTreeItem.Plan -> {
                            val group = siblings[row.plan.parentId].orEmpty()
                            val index = group.indexOf(row.plan.id)
                            PlanRow(
                                row, links, tagFilter, viewModel, onOpen, shape, guides[i],
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
                        is PlanTreeItem.Task -> PlanTaskRow(row.task, tagFilter, checklists[row.task.id], viewModel, onOpen, shape, guides[i])
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

/**
 * Each row's place in the outline: for every level above it, whether a later row at the level
 * below still hangs from that line, so the line runs on past this row.
 */
private fun outlineGuides(rows: List<PlanTreeItem>): List<OutlineGuides> = outlineGuidesOf(rows.map { it.depth })

/** [outlineGuides] from the rows' depths alone. */
private fun outlineGuidesOf(depths: List<Int>): List<OutlineGuides> = depths.mapIndexed { i, depth ->
    val continues = (0 until depth).map { level ->
        depths.drop(i + 1).takeWhile { it > level }.any { it == level + 1 }
    }
    OutlineGuides(depth, continues, hasChildrenBelow = (depths.getOrNull(i + 1) ?: 0) > depth)
}

/** @param dragHandle shown instead of the collapse button while reordering. */
@Composable
private fun PlanRow(
    row: PlanTreeItem.Plan,
    links: Map<ItemRef, List<ItemIndexEntry>>,
    tagFilter: TagFilter,
    viewModel: HomeViewModel,
    onOpen: (ItemRef) -> Unit,
    shape: Shape,
    outline: OutlineGuides,
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
        onLongClick = { viewModel.showNetwork(ref) },
        reminder = viewModel.reminderOf(ref),
        onClick = { onOpen(ref) },
        shape = shape,
        outline = outline,
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

/** A task linked to the plan above it, a row of the plan's card. */
@Composable
private fun PlanTaskRow(
    task: TaskEntity,
    tagFilter: TagFilter,
    checklist: ChecklistCount?,
    viewModel: HomeViewModel,
    onOpen: (ItemRef) -> Unit,
    shape: Shape,
    outline: OutlineGuides,
) {
    val ref = ItemRef(ItemType.TASK, task.id)
    ItemCard(
        ItemType.TASK,
        text = taskCardText(task, checklist),
        links = emptyList(),
        tags = tagFilter.tagsOf(ref),
        onLongClick = { viewModel.showNetwork(ref) },
        onClick = { onOpen(ref) },
        shape = shape,
        outline = outline,
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
                    onLongClick = { viewModel.showNetwork(ref) },
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
                    onLongClick = { viewModel.showNetwork(ref) },
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
                    onLongClick = { viewModel.showNetwork(ref) },
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

@Composable
fun PeopleTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val lists by viewModel.personLists.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val drag = rememberDragOrder(people, key = { it.id })
    val rows = drag.rows
    val view = viewModel.priorityView(ItemType.PERSON)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    ItemList(ItemType.PERSON, empty = people.isEmpty(), emptyText = "No people yet.", filtered = viewModel.filtering(tagFilter) || view.filtered, controls = controls, state = drag.listState) {
        if (controls) priorityControls(ItemType.PERSON, view, viewModel)
        itemsIndexed(rows, key = { _, person -> person.id }) { index, person ->
            val ref = ItemRef(ItemType.PERSON, person.id)
            ReorderableItem(drag.state, key = person.id, enabled = movable) {
                ItemCard(
                    ItemType.PERSON,
                    text = personCardText(person, lists[person.id]),
                    links = links[ref].orEmpty(),
                    tags = tagFilter.tagsOf(ref),
                    onLongClick = { viewModel.showNetwork(ref) },
                    reminder = viewModel.reminderOf(ref),
                    onClick = { onOpen(ref) },
                    trailing = dragHandleIf(movable, index, rows.size, { viewModel.move(ItemType.PERSON, person.id, it) }) {
                        viewModel.reorder(ItemType.PERSON, drag.rows.map { it.id })
                    },
                )
            }
        }
    }
}

/**
 * The Ideas tab: the ideas as an outline, like the Plans tab's, each with its kind's icon. A
 * chevron collapses what is under an idea; while reordering, an idea moves among its siblings
 * and takes everything under it along.
 */
@Composable
fun IdeasTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, controls: Boolean) {
    val rows by viewModel.ideaTree.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val kinds by viewModel.ideaKinds.collectAsStateWithLifecycle()
    val tabModes by viewModel.tabModes.collectAsStateWithLifecycle()
    val ideaListCounts by viewModel.ideaListCounts.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val view = viewModel.priorityView(ItemType.IDEA)
    val movable = reorder && view.sort == PrioritySort.CUSTOM
    val drag = rememberDragOrder(rows, key = { it.node.id }) { list, from, to -> ManualOrder.moveBlock(list, { it.depth }, from, to) }
    var dragging by remember { mutableStateOf<IdeaEntity?>(null) }
    val shownRows = dragging?.let { idea -> withoutRowsBelow(drag.rows, { it.node.id == idea.id }, { it.depth }) } ?: drag.rows
    val siblings = shownRows.groupBy({ it.node.parentId }, { it.node.id })
    val guides = outlineGuidesOf(shownRows.map { it.depth })
    ItemList(
        ItemType.IDEA,
        empty = rows.isEmpty(),
        emptyText = "No ideas yet.",
        filtered = viewModel.filtering(tagFilter) || view.filtered || kinds.active,
        controls = controls, state = drag.listState,
        spacing = 0.dp,
    ) {
        if (controls) priorityControls(ItemType.IDEA, view, viewModel)
        itemsIndexed(shownRows, key = { _, row -> row.node.id }) { i, row ->
            val idea = row.node
            val ref = ItemRef(ItemType.IDEA, idea.id)
            val last = shownRows.getOrNull(i + 1)?.depth?.let { it == 0 } ?: true
            val round = 12.dp
            val shape = RoundedCornerShape(
                topStart = if (row.depth == 0) round else 0.dp, topEnd = if (row.depth == 0) round else 0.dp,
                bottomStart = if (last) round else 0.dp, bottomEnd = if (last) round else 0.dp,
            )
            val group = siblings[idea.parentId].orEmpty()
            val index = group.indexOf(idea.id)
            val enabled = movable && dragging.let { it == null || it.parentId == idea.parentId }
            ReorderableItem(drag.state, key = idea.id, enabled = enabled) {
                Box(Modifier.padding(top = if (row.depth == 0) 6.dp else 0.dp)) {
                    val hidden = row.childCount.takeIf { row.collapsed && it > 0 }?.let { if (it == 1) "1 idea hidden" else "$it ideas hidden" }
                    // How many goals, plans, tasks and habits (Do mode's items) this idea links to.
                    val inDo = links[ref].orEmpty().count { tabModes.isOtherMode(it.type, AppMode.THINK) }
                    ItemCard(
                        ItemType.IDEA,
                        // The kind's icon leads the row, where the outline's lines meet it, rather than in the title.
                        text = ideaCardText(idea, hidden = hidden, listCount = ideaListCounts[idea.id] ?: 0).let { t ->
                            t.copy(kind = null, meta = t.meta + listOfNotNull(inDo.takeIf { it > 0 }?.let { "⇄ $it in Do" }))
                        },
                        leading = { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { KindIcon(idea.kind, size = 22.dp) } },
                        links = links[ref].orEmpty(),
                        tags = tagFilter.tagsOf(ref),
                        onLongClick = { viewModel.showNetwork(ref) },
                        reminder = viewModel.reminderOf(ref),
                        onClick = { onOpen(ref) },
                        shape = shape,
                        outline = guides[i],
                        trailing = dragHandleIf(
                            reorder = movable,
                            canMoveUp = index > 0,
                            canMoveDown = index < group.lastIndex,
                            onMove = { viewModel.move(ItemType.IDEA, idea.id, it) },
                            onDragStarted = { dragging = idea },
                            onDrop = {
                                val parentId = idea.parentId
                                dragging = null
                                viewModel.reorder(ItemType.IDEA, drag.rows.filter { it.node.parentId == parentId }.map { it.node.id })
                            },
                        ) ?: {
                            if (row.childCount > 0) {
                                IconButton(onClick = { viewModel.setIdeaCollapsed(idea.id, !row.collapsed) }) {
                                    Icon(
                                        painterResource(if (row.collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                                        contentDescription = if (row.collapsed) "Expand" else "Collapse",
                                    )
                                }
                            }
                        },
                    )
                }
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

/** The glossary's definitions, for the empty lists. */
val LocalTerms = staticCompositionLocalOf { Glossary.Terms() }

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
    spacing: Dp = 6.dp,
    content: LazyListScope.() -> Unit,
) {
    // The sort and filter rows open at the top of the list; scroll up to them, or they would open
    // above the cards in view, out of sight.
    LaunchedEffect(controls) { if (controls) state.scrollToItem(0) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
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
            // What this tab is for, in the user's own words from the glossary.
            val definition = LocalTerms.current.of(type)
            if (definition.isNotBlank()) {
                Text(
                    "${if (type == ItemType.BLOCK) "A schedule block" else "A ${type.singular.lowercase()}"}: $definition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
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
private fun withoutRowsUnder(rows: List<PlanTreeItem>, planId: Long): List<PlanTreeItem> =
    withoutRowsBelow(rows, { it is PlanTreeItem.Plan && it.plan.id == planId }, { it.depth })

/** [rows] without the rows deeper than the first [isTop] row that follow it. */
private fun <T> withoutRowsBelow(rows: List<T>, isTop: (T) -> Boolean, depth: (T) -> Int): List<T> {
    val start = rows.indexOfFirst(isTop)
    if (start < 0) return rows
    var end = start + 1
    while (end < rows.size && depth(rows[end]) > depth(rows[start])) end++
    return rows.subList(0, start + 1) + rows.subList(end, rows.size)
}
