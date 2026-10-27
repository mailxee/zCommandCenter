package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import java.time.LocalDate

/**
 * The text of an item's card. Built by the functions below, so an item reads the same in its own
 * tab and as a linked item on another item's page.
 */
data class CardText(val title: String, val lines: List<String>, val done: Boolean = false, val emphasis: Boolean = false)

/** A habit with what its card shows: streak, mark count, and whether today needs a mark. */
data class HabitSummary(
    val habit: HabitEntity,
    val streak: Int,
    val totalMarks: Int,
    val scheduledToday: Boolean,
    val markedToday: Boolean,
) {
    val positive: Boolean get() = habit.category == HabitCategory.POSITIVE
}

fun habitSummary(habit: HabitEntity, marks: Set<LocalDate>, today: LocalDate): HabitSummary = HabitSummary(
    habit = habit,
    streak = HabitStats.streak(habit.category, habit.recurrence, habit.startDate, marks, today),
    totalMarks = marks.size,
    scheduledToday = HabitStats.isDue(habit.category, habit.recurrence, habit.startDate, marks, today),
    markedToday = today in marks,
)

fun taskCardText(task: TaskEntity, checklist: ChecklistCount?) = CardText(
    title = task.title,
    lines = listOfNotNull(
        "${task.priority.label} priority" + (task.dueDate?.let { " · due ${it.display()}" } ?: ""),
        task.recurrence?.let { "Repeats: ${it.describe()}" },
        checklist?.let { "Checklist ${it.done}/${it.total}" },
    ),
    done = task.completed,
    emphasis = task.priority == Priority.CRITICAL && !task.completed,
)

/** "High priority", the first line of most cards; critical items are also shown in red ([critical]). */
private fun priorityLine(priority: Priority) = "${priority.label} priority"

private fun critical(priority: Priority, open: Boolean = true) = open && priority == Priority.CRITICAL

fun goalCardText(goal: GoalEntity) = CardText(
    title = goal.title,
    lines = listOfNotNull(priorityLine(goal.priority), goal.strategy.ifBlank { null }),
    emphasis = critical(goal.priority),
)

fun habitCardText(summary: HabitSummary) = CardText(
    title = summary.habit.name,
    lines = listOf(
        priorityLine(summary.habit.priority),
        "${summary.habit.category.label} · ${summary.habit.recurrence.describe()}",
        if (summary.positive) "Streak ${summary.streak} · done ${times(summary.totalMarks)}"
        else "Clean streak ${summary.streak} · missed ${times(summary.totalMarks)}",
    ),
    emphasis = critical(summary.habit.priority),
)

/** [hidden] says what a collapsed plan hides in the Plans outline. */
fun planCardText(plan: PlanEntity, hidden: String? = null) = CardText(
    title = plan.title,
    lines = listOfNotNull(priorityLine(plan.priority), plan.description.ifBlank { null }, hidden),
    done = plan.completed,
    emphasis = critical(plan.priority, open = !plan.completed),
)

/** [listCount] is the number of items in the note's list. */
fun noteCardText(note: NoteEntity, listCount: Int) = CardText(
    title = note.title.ifBlank { "Untitled" },
    lines = listOfNotNull(
        priorityLine(note.priority) + (if (listCount > 0) " · list of ${listCount.items()}" else ""),
        note.body.ifBlank { null },
    ),
    emphasis = critical(note.priority),
)

private fun Int.items(): String = if (this == 1) "1 item" else "$this items"

fun bookCardText(item: BookWithProgress) = CardText(
    title = item.book.title,
    lines = listOfNotNull(
        priorityLine(item.book.priority),
        item.book.author.ifBlank { null },
        "${item.chapterCount} chapters · ${item.internalizedCount} done",
        listOfNotNull(
            "Read ${times(item.book.readCount)}".takeIf { item.book.readCount > 0 },
            "Book done".takeIf { item.book.internalized },
        ).joinToString(" · ").ifEmpty { null },
    ),
    emphasis = critical(item.book.priority),
)

fun quoteCardText(quote: QuoteEntity): CardText {
    val attribution = listOf(quote.author, quote.source).filter { it.isNotBlank() }.joinToString(", ")
    return CardText(
        title = "“${quote.text}”",
        lines = listOfNotNull(attribution.ifBlank { null }?.let { "— $it" }, priorityLine(quote.priority)),
        emphasis = critical(quote.priority),
    )
}

/** Chapters have no tab of their own; their card names the book they are in. */
fun chapterCardText(entry: ItemIndexEntry) = CardText(entry.title, listOfNotNull(entry.describe()))

fun times(count: Int): String = if (count == 1) "once" else "$count times"

/**
 * An item's card: title in its tab's style, detail lines, its [reminder] line (see
 * [reminderLine]; not shown once the item is done), the item's tags, and the titles of everything
 * it is linked to. Used by the tab lists and by the linked items on detail pages.
 */
@Composable
fun ItemCard(
    type: ItemType,
    text: CardText,
    links: List<ItemIndexEntry>,
    onClick: () -> Unit,
    tags: List<TagEntity> = emptyList(),
    reminder: String? = null,
    outlined: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // The tab's style, except that a critical task stays red and a done item is struck through.
                val titleStyle = itemTextStyle(type, MaterialTheme.typography.titleMedium)
                Text(
                    text.title,
                    style = titleStyle,
                    textDecoration = TextDecoration.combine(listOfNotNull(titleStyle.textDecoration, TextDecoration.LineThrough.takeIf { text.done })),
                    color = if (text.emphasis) MaterialTheme.colorScheme.error else titleStyle.color.takeOrElse { MaterialTheme.colorScheme.onSurface },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                text.lines.forEach { line ->
                    Text(
                        linkified(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (reminder != null && !text.done) {
                    Text(
                        reminder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
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
                    // Each linked item in its own tab's style.
                    val styles = LocalItemStyles.current
                    val dark = isDarkSurface()
                    Text(
                        buildAnnotatedString {
                            append("Linked: ")
                            links.forEachIndexed { index, entry ->
                                if (index > 0) append(" · ")
                                withStyle(styles[entry.type].toSpanStyle(dark)) { append(linkLabel(entry)) }
                            }
                        },
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

private fun linkLabel(entry: ItemIndexEntry): String {
    val title = entry.title.let { if (it.length > 40) it.take(39) + "…" else it }
    return if (entry.type == ItemType.CHAPTER) "$title (${entry.subtitle})" else title
}
