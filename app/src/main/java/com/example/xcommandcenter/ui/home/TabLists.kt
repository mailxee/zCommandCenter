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
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.ui.common.display
import com.example.xcommandcenter.ui.common.label
import com.example.xcommandcenter.ui.project.CompleteProjectDialog
import java.time.Instant
import java.time.ZoneId

@Composable
fun TasksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val sort by viewModel.taskSort.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatFilter.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    // Either filter can empty the list; then say so instead of "No tasks yet".
    ItemList(empty = tasks.isEmpty(), emptyText = "No tasks yet.", filtered = tagFilter.active || repeat != RepeatFilter.ALL) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TaskSort.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = sort == option,
                        onClick = { viewModel.setTaskSort(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, TaskSort.entries.size),
                        label = { Text("Sort: ${option.label}") },
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
        items(tasks, key = { it.id }) { task ->
            val ref = ItemRef(ItemType.TASK, task.id)
            ItemCard(
                title = task.title,
                lines = listOfNotNull(
                    "${task.priority.label} priority" + (task.dueDate?.let { " · due ${it.display()}" } ?: ""),
                    task.recurrence?.let { "Repeats: ${it.describe()}" },
                    task.reminderAt?.takeIf { !task.completed && it > System.currentTimeMillis() }
                        ?.let { "Reminder ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().display()}" },
                ),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
                done = task.completed,
                emphasis = task.priority == Priority.CRITICAL && !task.completed,
                leading = { Checkbox(checked = task.completed, onCheckedChange = { viewModel.setTaskCompleted(task.id, it) }) },
            )
        }
    }
}

@Composable
fun GoalsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    ItemList(empty = goals.isEmpty(), emptyText = "No goals yet.", filtered = tagFilter.active) {
        items(goals, key = { it.id }) { goal ->
            val ref = ItemRef(ItemType.GOAL, goal.id)
            ItemCard(goal.title, listOfNotNull(goal.strategy.ifBlank { null }), links[ref].orEmpty(), { onOpen(ref) }, tags = tagFilter.tagsOf(ref))
        }
    }
}

@Composable
fun HabitsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    ItemList(empty = habits.isEmpty(), emptyText = "No habits yet.", filtered = tagFilter.active) {
        items(habits, key = { it.habit.id }) { summary ->
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
                trailing = {
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
fun ProjectsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val rows by viewModel.projectTree.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val showCompleted by viewModel.showCompletedProjects.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedProjectCount.collectAsStateWithLifecycle()
    val showCompletedTasks by viewModel.showCompletedProjectTasks.collectAsStateWithLifecycle()
    val completedTaskCount by viewModel.completedProjectTaskCount.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedCompletion.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val allHidden = rows.isEmpty() && completedCount > 0 && !showCompleted
    ItemList(
        empty = rows.isEmpty(),
        emptyText = if (allHidden) "All projects are completed." else "No projects yet.",
        filtered = tagFilter.active,
    ) {
        if (completedCount > 0 || completedTaskCount > 0) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (completedCount > 0) {
                        FilterChip(
                            selected = showCompleted,
                            onClick = { viewModel.setShowCompletedProjects(!showCompleted) },
                            label = { Text("Completed projects ($completedCount)") },
                        )
                    }
                    if (completedTaskCount > 0) {
                        FilterChip(
                            selected = showCompletedTasks,
                            onClick = { viewModel.setShowCompletedProjectTasks(!showCompletedTasks) },
                            label = { Text("Completed tasks ($completedTaskCount)") },
                        )
                    }
                }
            }
        }
        items(rows, key = { it.key }) { row ->
            Box(Modifier.padding(start = (row.depth * 20).dp)) {
                when (row) {
                    is ProjectTreeItem.Project -> ProjectRow(row, links, tagFilter, viewModel, onOpen)
                    is ProjectTreeItem.Task -> ProjectTaskRow(row.task, tagFilter, viewModel, onOpen)
                }
            }
        }
    }
    blocked?.let {
        CompleteProjectDialog(
            blocked = it,
            onCompleteAll = { viewModel.completeWithSubprojects(it.projectId) },
            onDismiss = viewModel::dismissBlockedCompletion,
        )
    }
}

@Composable
private fun ProjectRow(
    row: ProjectTreeItem.Project,
    links: Map<ItemRef, List<ItemIndexEntry>>,
    tagFilter: TagFilter,
    viewModel: HomeViewModel,
    onOpen: (ItemRef) -> Unit,
) {
    val project = row.project
    val ref = ItemRef(ItemType.PROJECT, project.id)
    // Linked tasks are rows of their own below the project, so the "Linked:" line leaves them out.
    val otherLinks = links[ref].orEmpty().filter { it.type != ItemType.TASK }
    val hidden = listOfNotNull(
        row.subprojectCount.takeIf { it > 0 }?.let { if (it == 1) "1 subproject" else "$it subprojects" },
        row.taskCount.takeIf { it > 0 }?.let { if (it == 1) "1 task" else "$it tasks" },
    )
    ItemCard(
        title = project.title,
        lines = listOfNotNull(
            project.description.ifBlank { null },
            hidden.takeIf { row.collapsed && it.isNotEmpty() }?.joinToString(" · ", postfix = " hidden"),
        ),
        links = otherLinks,
        tags = tagFilter.tagsOf(ref),
        onClick = { onOpen(ref) },
        done = project.completed,
        leading = {
            Checkbox(checked = project.completed, onCheckedChange = { viewModel.setProjectCompleted(project, it) })
        },
        trailing = {
            if (row.hasChildren) {
                IconButton(onClick = { viewModel.setProjectCollapsed(project.id, !row.collapsed) }) {
                    Icon(
                        painterResource(if (row.collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                        contentDescription = if (row.collapsed) "Expand" else "Collapse",
                    )
                }
            }
        },
    )
}

/** A task linked to the project above it; outlined so it reads differently from a project. */
@Composable
private fun ProjectTaskRow(task: TaskEntity, tagFilter: TagFilter, viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val ref = ItemRef(ItemType.TASK, task.id)
    ItemCard(
        title = task.title,
        lines = listOfNotNull(
            "${task.priority.label} priority" + (task.dueDate?.let { " · due ${it.display()}" } ?: ""),
            task.recurrence?.let { "Repeats: ${it.describe()}" },
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
    ItemList(empty = notes.isEmpty(), emptyText = "No notes yet.", filtered = tagFilter.active) {
        items(notes, key = { it.id }) { note ->
            val ref = ItemRef(ItemType.NOTE, note.id)
            ItemCard(
                title = note.title.ifBlank { "Untitled" },
                lines = listOfNotNull(note.body.ifBlank { null }),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
            )
        }
    }
}

@Composable
fun BooksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    ItemList(empty = books.isEmpty(), emptyText = "No books yet.", filtered = tagFilter.active) {
        items(books, key = { it.book.id }) { item ->
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
            )
        }
    }
}

@Composable
fun QuotesTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    ItemList(empty = quotes.isEmpty(), emptyText = "No quotes yet.", filtered = tagFilter.active) {
        items(quotes, key = { it.id }) { quote ->
            val ref = ItemRef(ItemType.QUOTE, quote.id)
            val attribution = listOf(quote.author, quote.source).filter { it.isNotBlank() }.joinToString(", ")
            ItemCard(
                title = "“${quote.text}”",
                lines = listOfNotNull(attribution.ifBlank { null }?.let { "— $it" }),
                links = links[ref].orEmpty(),
                tags = tagFilter.tagsOf(ref),
                onClick = { onOpen(ref) },
                italicTitle = true,
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

fun times(count: Int): String = if (count == 1) "once" else "$count times"

private fun linkLabel(entry: ItemIndexEntry): String {
    val title = entry.title.let { if (it.length > 40) it.take(39) + "…" else it }
    return if (entry.type == ItemType.CHAPTER) "$title (${entry.subtitle})" else title
}
