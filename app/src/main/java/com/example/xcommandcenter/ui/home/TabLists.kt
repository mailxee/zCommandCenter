package com.example.xcommandcenter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.ChecklistCount
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.ui.common.display
import com.example.xcommandcenter.ui.common.label
import com.example.xcommandcenter.ui.plan.CompletePlanDialog
import java.time.Instant
import java.time.ZoneId

@Composable
fun TasksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val sort by viewModel.taskSort.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatFilter.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val checklists by viewModel.checklistCounts.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    // Tasks can only be moved in the custom order; in the others their place follows from their fields.
    val movable = reorder && sort == TaskSort.CUSTOM
    // Either filter can empty the list; then say so instead of "No tasks yet".
    ItemList(empty = tasks.isEmpty(), emptyText = "No tasks yet.", filtered = tagFilter.active || repeat != RepeatFilter.ALL) {
        item {
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
        item {
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
        itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
            val ref = ItemRef(ItemType.TASK, task.id)
            ItemCard(
                title = task.title,
                lines = listOfNotNull(
                    "${task.priority.label} priority" + (task.dueDate?.let { " · due ${it.display()}" } ?: ""),
                    task.recurrence?.let { "Repeats: ${it.describe()}" },
                    checklistLine(checklists[task.id]),
                    task.reminderAt?.takeIf { !task.completed && it > System.currentTimeMillis() }
                        ?.let { "Reminder ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().display()}" },
                ),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
                done = task.completed,
                emphasis = task.priority == Priority.CRITICAL && !task.completed,
                leading = { Checkbox(checked = task.completed, onCheckedChange = { viewModel.setTaskCompleted(task.id, it) }) },
                // Open and completed tasks are moved separately, as the list keeps them apart.
                trailing = if (movable) {
                    {
                        MoveButtons(
                            canMoveUp = tasks.getOrNull(index - 1)?.completed == task.completed,
                            canMoveDown = tasks.getOrNull(index + 1)?.completed == task.completed,
                            onMove = { viewModel.move(ItemType.TASK, task.id, it) },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
fun GoalsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    ItemList(empty = goals.isEmpty(), emptyText = "No goals yet.", filtered = tagFilter.active) {
        itemsIndexed(goals, key = { _, goal -> goal.id }) { index, goal ->
            val ref = ItemRef(ItemType.GOAL, goal.id)
            ItemCard(
                goal.title,
                listOfNotNull(goal.strategy.ifBlank { null }),
                links[ref].orEmpty(),
                { onOpen(ref) },
                tags = tagFilter.tagsOf(ref),
                trailing = moveButtonsIf(reorder, index, goals.size) { viewModel.move(ItemType.GOAL, goal.id, it) },
            )
        }
    }
}

@Composable
fun HabitsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    ItemList(empty = habits.isEmpty(), emptyText = "No habits yet.", filtered = tagFilter.active) {
        itemsIndexed(habits, key = { _, summary -> summary.habit.id }) { index, summary ->
            val habit = summary.habit
            val ref = ItemRef(ItemType.HABIT, habit.id)
            val positive = habit.category == HabitCategory.POSITIVE
            ItemCard(
                title = habit.name,
                lines = listOf(
                    "${habit.category.label} · ${habit.recurrence.describe()}",
                    if (positive) "Streak ${summary.streak} · done ${times(summary.totalMarks)}"
                    else "Clean streak ${summary.streak} · missed ${times(summary.totalMarks)}",
                ),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
                trailing = moveButtonsIf(reorder, index, habits.size) { viewModel.move(ItemType.HABIT, habit.id, it) } ?: {
                    if (summary.scheduledToday) {
                        FilterChip(
                            selected = summary.markedToday,
                            onClick = { viewModel.toggleHabitToday(habit.id) },
                            label = { Text(if (positive) "Done today" else "Missed today") },
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun PlansTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
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
    // A plan moves among the shown plans with the same parent.
    val siblings = rows.filterIsInstance<PlanTreeItem.Plan>().groupBy({ it.plan.parentId }, { it.plan.id })
    ItemList(
        empty = rows.isEmpty(),
        emptyText = if (allHidden) "All plans are completed." else "No plans yet.",
        filtered = tagFilter.active,
    ) {
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
        items(rows, key = { it.key }) { row ->
            Box(Modifier.padding(start = (row.depth * 20).dp)) {
                when (row) {
                    is PlanTreeItem.Plan -> {
                        val group = siblings[row.plan.parentId].orEmpty()
                        PlanRow(row, links, tagFilter, viewModel, onOpen, if (reorder) group.indexOf(row.plan.id) to group.size else null)
                    }
                    is PlanTreeItem.Task -> PlanTaskRow(row.task, tagFilter, checklists[row.task.id], viewModel, onOpen)
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

/** @param position the plan's index among its shown siblings and their count, while reordering. */
@Composable
private fun PlanRow(
    row: PlanTreeItem.Plan,
    links: Map<ItemRef, List<ItemIndexEntry>>,
    tagFilter: TagFilter,
    viewModel: HomeViewModel,
    onOpen: (ItemRef) -> Unit,
    position: Pair<Int, Int>?,
) {
    val plan = row.plan
    val ref = ItemRef(ItemType.PLAN, plan.id)
    // Linked tasks are rows of their own below the plan, so the "Linked:" line leaves them out.
    val otherLinks = links[ref].orEmpty().filter { it.type != ItemType.TASK }
    val hidden = listOfNotNull(
        row.subplanCount.takeIf { it > 0 }?.let { if (it == 1) "1 subplan" else "$it subplans" },
        row.taskCount.takeIf { it > 0 }?.let { if (it == 1) "1 task" else "$it tasks" },
    )
    ItemCard(
        title = plan.title,
        lines = listOfNotNull(
            plan.description.ifBlank { null },
            hidden.takeIf { row.collapsed && it.isNotEmpty() }?.joinToString(" · ", postfix = " hidden"),
        ),
        links = otherLinks,
        tags = tagFilter.tagsOf(ref),
        onClick = { onOpen(ref) },
        done = plan.completed,
        leading = {
            Checkbox(checked = plan.completed, onCheckedChange = { viewModel.setPlanCompleted(plan, it) })
        },
        trailing = position?.let { (index, count) -> moveButtonsIf(true, index, count) { viewModel.move(ItemType.PLAN, plan.id, it) } } ?: {
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
        title = task.title,
        lines = listOfNotNull(
            "${task.priority.label} priority" + (task.dueDate?.let { " · due ${it.display()}" } ?: ""),
            task.recurrence?.let { "Repeats: ${it.describe()}" },
            checklistLine(checklist),
        ),
        links = emptyList(),
        tags = tagFilter.tagsOf(ref),
        onClick = { onOpen(ref) },
        done = task.completed,
        emphasis = task.priority == Priority.CRITICAL && !task.completed,
        outlined = true,
        leading = { Checkbox(checked = task.completed, onCheckedChange = { viewModel.setTaskCompleted(task.id, it) }) },
    )
}

@Composable
fun NotesTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    ItemList(empty = notes.isEmpty(), emptyText = "No notes yet.", filtered = tagFilter.active) {
        itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
            val ref = ItemRef(ItemType.NOTE, note.id)
            ItemCard(
                title = note.title.ifBlank { "Untitled" },
                lines = listOfNotNull(note.body.ifBlank { null }),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
                trailing = moveButtonsIf(reorder, index, notes.size) { viewModel.move(ItemType.NOTE, note.id, it) },
            )
        }
    }
}

@Composable
fun BooksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    ItemList(empty = books.isEmpty(), emptyText = "No books yet.", filtered = tagFilter.active) {
        itemsIndexed(books, key = { _, item -> item.book.id }) { index, item ->
            val ref = ItemRef(ItemType.BOOK, item.book.id)
            ItemCard(
                title = item.book.title,
                lines = listOfNotNull(
                    item.book.author.ifBlank { null },
                    "${item.chapterCount} chapters · ${item.internalizedCount} internalized",
                ),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
                trailing = moveButtonsIf(reorder, index, books.size) { viewModel.move(ItemType.BOOK, item.book.id, it) },
            )
        }
    }
}

@Composable
fun QuotesTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    ItemList(empty = quotes.isEmpty(), emptyText = "No quotes yet.", filtered = tagFilter.active) {
        itemsIndexed(quotes, key = { _, quote -> quote.id }) { index, quote ->
            val ref = ItemRef(ItemType.QUOTE, quote.id)
            val attribution = listOf(quote.author, quote.source).filter { it.isNotBlank() }.joinToString(", ")
            ItemCard(
                title = "“${quote.text}”",
                lines = listOfNotNull(attribution.ifBlank { null }?.let { "— $it" }),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
                italicTitle = true,
                trailing = moveButtonsIf(reorder, index, quotes.size) { viewModel.move(ItemType.QUOTE, quote.id, it) },
            )
        }
    }
}

@Composable
private fun ItemList(empty: Boolean, emptyText: String, filtered: Boolean = false, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
        if (empty) {
            item {
                Text(
                    if (filtered) "Nothing here matches the current filters." else "$emptyText Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** A list row: title, detail lines, the item's tags, and the titles of everything it is linked to. */
@Composable
private fun ItemCard(
    title: String,
    lines: List<String>,
    links: List<ItemIndexEntry>,
    onClick: () -> Unit,
    tags: List<TagEntity> = emptyList(),
    done: Boolean = false,
    emphasis: Boolean = false,
    italicTitle: Boolean = false,
    outlined: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = if (italicTitle) FontStyle.Italic else null,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (emphasis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                lines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString("  ") { it.label() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (links.isNotEmpty()) {
                    Text(
                        "Linked: " + links.joinToString(" · ") { linkLabel(it) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
    if (outlined) {
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** Up and down buttons for a row while reordering; [onMove] gets -1 for up and +1 for down. */
@Composable
private fun MoveButtons(canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit) {
    Row {
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = "Move up")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Move down")
        }
    }
}

/** [MoveButtons] for row [index] of [count] while [reorder] is on, otherwise null (the row's usual trailing content). */
private fun moveButtonsIf(reorder: Boolean, index: Int, count: Int, onMove: (Int) -> Unit): (@Composable () -> Unit)? =
    if (reorder) {
        { MoveButtons(canMoveUp = index > 0, canMoveDown = index < count - 1, onMove = onMove) }
    } else {
        null
    }

private fun checklistLine(count: ChecklistCount?): String? = count?.let { "Checklist ${it.done}/${it.total}" }

fun times(count: Int): String = if (count == 1) "once" else "$count times"

private fun linkLabel(entry: ItemIndexEntry): String {
    val title = entry.title.let { if (it.length > 40) it.take(39) + "…" else it }
    return if (entry.type == ItemType.CHAPTER) "$title (${entry.subtitle})" else title
}
