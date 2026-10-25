package com.example.xcommandcenter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.ui.common.display
import com.example.xcommandcenter.ui.project.CompleteProjectDialog
import java.time.Instant
import java.time.ZoneId

@Composable
fun TasksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val sort by viewModel.taskSort.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    ItemList(empty = tasks.isEmpty(), emptyText = "No tasks yet.") {
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
        items(tasks, key = { it.id }) { task ->
            val ref = ItemRef(ItemType.TASK, task.id)
            ItemCard(
                title = task.title,
                lines = listOfNotNull(
                    "${task.priority.label} priority" + (task.dueDate?.let { " · due ${it.display()}" } ?: ""),
                    task.reminderAt?.takeIf { !task.completed && it > System.currentTimeMillis() }
                        ?.let { "Reminder ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().display()}" },
                ),
                links = links[ref].orEmpty(),
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
    ItemList(empty = goals.isEmpty(), emptyText = "No goals yet.") {
        items(goals, key = { it.id }) { goal ->
            val ref = ItemRef(ItemType.GOAL, goal.id)
            ItemCard(goal.title, listOfNotNull(goal.strategy.ifBlank { null }), links[ref].orEmpty(), { onOpen(ref) })
        }
    }
}

@Composable
fun HabitsTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    ItemList(empty = habits.isEmpty(), emptyText = "No habits yet.") {
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
    val rows by viewModel.projects.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val showCompleted by viewModel.showCompletedProjects.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedProjectCount.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedCompletion.collectAsStateWithLifecycle()
    val allHidden = rows.isEmpty() && completedCount > 0 && !showCompleted
    ItemList(
        empty = rows.isEmpty(),
        emptyText = if (allHidden) "All projects are completed." else "No projects yet.",
    ) {
        if (completedCount > 0) {
            item {
                FilterChip(
                    selected = showCompleted,
                    onClick = { viewModel.setShowCompletedProjects(!showCompleted) },
                    label = { Text("Show completed ($completedCount)") },
                )
            }
        }
        items(rows, key = { it.node.id }) { row ->
            val project = row.node
            val ref = ItemRef(ItemType.PROJECT, project.id)
            Box(Modifier.padding(start = (row.depth * 20).dp)) {
                ItemCard(
                    title = project.title,
                    lines = listOfNotNull(project.description.ifBlank { null }),
                    links = links[ref].orEmpty(),
                    onClick = { onOpen(ref) },
                    done = project.completed,
                    leading = {
                        Checkbox(checked = project.completed, onCheckedChange = { viewModel.setProjectCompleted(project, it) })
                    },
                )
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
fun NotesTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    ItemList(empty = notes.isEmpty(), emptyText = "No notes yet.") {
        items(notes, key = { it.id }) { note ->
            val ref = ItemRef(ItemType.NOTE, note.id)
            ItemCard(
                title = note.title.ifBlank { "Untitled" },
                lines = listOfNotNull(note.body.ifBlank { null }),
                links = links[ref].orEmpty(),
                onClick = { onOpen(ref) },
            )
        }
    }
}

@Composable
fun BooksTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    ItemList(empty = books.isEmpty(), emptyText = "No books yet.") {
        items(books, key = { it.book.id }) { item ->
            val ref = ItemRef(ItemType.BOOK, item.book.id)
            ItemCard(
                title = item.book.title,
                lines = listOfNotNull(
                    item.book.author.ifBlank { null },
                    "${item.chapterCount} chapters · ${item.internalizedCount} internalized",
                ),
                links = links[ref].orEmpty(),
                onClick = { onOpen(ref) },
            )
        }
    }
}

@Composable
fun QuotesTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit) {
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    ItemList(empty = quotes.isEmpty(), emptyText = "No quotes yet.") {
        items(quotes, key = { it.id }) { quote ->
            val ref = ItemRef(ItemType.QUOTE, quote.id)
            val attribution = listOf(quote.author, quote.source).filter { it.isNotBlank() }.joinToString(", ")
            ItemCard(
                title = "“${quote.text}”",
                lines = listOfNotNull(attribution.ifBlank { null }?.let { "— $it" }),
                links = links[ref].orEmpty(),
                onClick = { onOpen(ref) },
                italicTitle = true,
            )
        }
    }
}

@Composable
private fun ItemList(empty: Boolean, emptyText: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
        if (empty) {
            item {
                Text(
                    "$emptyText Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** A list row: title, detail lines, and the titles of everything the item is linked to. */
@Composable
private fun ItemCard(
    title: String,
    lines: List<String>,
    links: List<ItemIndexEntry>,
    onClick: () -> Unit,
    done: Boolean = false,
    emphasis: Boolean = false,
    italicTitle: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
}

fun times(count: Int): String = if (count == 1) "once" else "$count times"

private fun linkLabel(entry: ItemIndexEntry): String {
    val title = entry.title.let { if (it.length > 40) it.take(39) + "…" else it }
    return if (entry.type == ItemType.CHAPTER) "$title (${entry.subtitle})" else title
}
