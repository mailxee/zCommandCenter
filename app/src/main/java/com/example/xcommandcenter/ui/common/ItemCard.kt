package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.BlockEntity
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
 * tab and as a linked item on another item's page. [priority] colors the card's edge and leads its
 * row of small [meta] facts (due date, streak, chapter count); [lines] are the longer texts.
 */
data class CardText(
    val title: String,
    val lines: List<String>,
    val done: Boolean = false,
    val emphasis: Boolean = false,
    val priority: Priority? = null,
    val meta: List<String> = emptyList(),
)

/** A habit with what its card shows: streak, mark count, and whether today needs a mark. */
data class HabitSummary(
    val habit: HabitEntity,
    val streak: Int,
    val totalMarks: Int,
    val scheduledToday: Boolean,
    val markedToday: Boolean,
    val marks: Set<LocalDate>,
) {
    val positive: Boolean get() = habit.category == HabitCategory.POSITIVE
}

fun habitSummary(habit: HabitEntity, marks: Set<LocalDate>, today: LocalDate): HabitSummary = HabitSummary(
    habit = habit,
    streak = HabitStats.streak(habit.category, habit.recurrence, habit.startDate, marks, today),
    totalMarks = marks.size,
    scheduledToday = HabitStats.isDue(habit.category, habit.recurrence, habit.startDate, marks, today),
    markedToday = today in marks,
    marks = marks,
)

fun taskCardText(task: TaskEntity, checklist: ChecklistCount?) = CardText(
    title = task.title,
    lines = emptyList(),
    done = task.completed,
    emphasis = task.priority == Priority.CRITICAL && !task.completed,
    priority = task.priority,
    meta = listOfNotNull(
        task.dueDate?.let { "Due ${it.display()}" },
        task.recurrence?.let { "Repeats: ${it.describe()}" },
        checklist?.let { "Checklist ${it.done}/${it.total}" },
    ),
)

/** Critical items are also shown in red. */
private fun critical(priority: Priority, open: Boolean = true) = open && priority == Priority.CRITICAL

fun goalCardText(goal: GoalEntity) = CardText(
    title = goal.title,
    lines = listOfNotNull(goal.strategy.ifBlank { null }),
    emphasis = critical(goal.priority),
    priority = goal.priority,
)

fun habitCardText(summary: HabitSummary) = CardText(
    title = summary.habit.name,
    lines = emptyList(),
    emphasis = critical(summary.habit.priority),
    priority = summary.habit.priority,
    meta = listOf(
        "${summary.habit.category.label} · ${summary.habit.recurrence.describe()}",
        if (summary.positive) "Streak ${summary.streak} · done ${times(summary.totalMarks)}"
        else "Clean streak ${summary.streak} · missed ${times(summary.totalMarks)}",
    ),
)

/** [hidden] says what a collapsed plan hides in the Plans outline. */
fun planCardText(plan: PlanEntity, hidden: String? = null) = CardText(
    title = plan.title,
    lines = listOfNotNull(plan.description.ifBlank { null }),
    done = plan.completed,
    emphasis = critical(plan.priority, open = !plan.completed),
    priority = plan.priority,
    meta = listOfNotNull(hidden),
)

/** [listCount] is the number of items in the note's list. */
fun noteCardText(note: NoteEntity, listCount: Int) = CardText(
    title = note.title.ifBlank { "Untitled" },
    lines = listOfNotNull(note.body.ifBlank { null }),
    emphasis = critical(note.priority),
    priority = note.priority,
    meta = listOfNotNull(if (listCount > 0) "List of ${listCount.items()}" else null),
)

private fun Int.items(): String = if (this == 1) "1 item" else "$this items"

fun bookCardText(item: BookWithProgress) = CardText(
    title = item.book.title,
    lines = listOfNotNull(item.book.author.ifBlank { null }),
    emphasis = critical(item.book.priority),
    priority = item.book.priority,
    meta = listOfNotNull(
        "${item.chapterCount} chapters · ${item.internalizedCount} done",
        "Read ${times(item.book.readCount)}".takeIf { item.book.readCount > 0 },
        "Book done".takeIf { item.book.internalized },
    ),
)

fun quoteCardText(quote: QuoteEntity): CardText {
    val attribution = listOf(quote.author, quote.source).filter { it.isNotBlank() }.joinToString(", ")
    return CardText(
        title = "“${quote.text}”",
        lines = listOfNotNull(attribution.ifBlank { null }?.let { "— $it" }),
        emphasis = critical(quote.priority),
        priority = quote.priority,
    )
}

/** "7:00 – 7:45 AM": a block's time of day. */
fun blockTimeText(block: BlockEntity): String = "${minuteText(block.startMinute)} – ${minuteText(block.endMinute)}"

/** [schedule] names the block's schedule, when there is more than one to tell apart. */
fun blockCardText(block: BlockEntity, schedule: String? = null) = CardText(
    title = block.title,
    lines = listOfNotNull(block.notes.ifBlank { null }),
    meta = listOfNotNull(schedule, blockTimeText(block)),
)

/** Chapters have no tab of their own; their card names the book they are in. */
fun chapterCardText(entry: ItemIndexEntry) = CardText(entry.title, listOfNotNull(entry.describe()))

fun times(count: Int): String = if (count == 1) "once" else "$count times"

/**
 * An item's card: title in its tab's style, detail lines, its [reminder] line (see
 * [reminderLine]; not shown once the item is done), the item's tags, and what it is linked to: a
 * line per tab, in the user's tab order ("Tasks: …"). Used by the tab lists and by the linked
 * items on detail pages.
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
    footer: (@Composable () -> Unit)? = null,
) {
    // A strip down the left edge in the priority's color, faded once the item is done.
    val strip = text.priority?.let { priorityColor(it).copy(alpha = if (text.done) 0.35f else 1f) }
    val content: @Composable ColumnScope.() -> Unit = {
        Column(
            Modifier.drawBehind {
                if (strip != null) drawRect(strip, size = Size(StripWidth.toPx(), size.height))
            },
        ) {
            Row(
                Modifier.padding(start = if (strip != null) 12.dp + StripWidth else 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    MetaRow(text.priority, text.meta)
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_notifications),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                reminder,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                        // The links read as a footnote, apart from the item's own text.
                        HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        LinkLines(links)
                    }
                }
                trailing?.invoke()
            }
            // Full width below the text and controls, such as a habit's calendar.
            footer?.let { Box(Modifier.padding(start = if (strip != null) 12.dp + StripWidth else 12.dp, end = 12.dp, bottom = 12.dp)) { it() } }
        }
    }
    if (outlined) {
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            content = content,
        )
    }
}

private val StripWidth = 4.dp

/**
 * The card's small facts in one wrapping row: the priority (a colored dot and its name), then
 * [meta] as quiet chips.
 */
@Composable
private fun MetaRow(priority: Priority?, meta: List<String>) {
    if (priority == null && meta.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (priority != null) {
            val color = priorityColor(priority)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clearAndSetSemantics { contentDescription = "${priority.label} priority" },
            ) {
                Box(Modifier.size(6.dp).background(color, CircleShape))
                Text(priority.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
            }
        }
        meta.forEach { fact ->
            Text(
                fact,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** An item's links, one line per tab in the user's tab order: the tab's name, then its items, each in its tab's style. */
@Composable
fun LinkLines(links: List<ItemIndexEntry>, maxLines: Int = 2) {
    val styles = LocalItemStyles.current
    val dark = isDarkSurface()
    LocalTabOrder.current.forEach { tab ->
        val group = links.filter { it.type.tab == tab }
        if (group.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    // The tab's name in the tab's color, its items quieter, each in its tab's style.
                    withStyle(styles[tab].toSpanStyle(dark).merge(SpanStyle(color = tabAccent(tab)))) { append("${tab.tabLabel}: ") }
                    // Blocks of the same name in different schedules are told apart by their schedule.
                    val repeated = group.groupingBy { it.type to it.title }.eachCount().filterValues { it > 1 }.keys
                    group.forEachIndexed { index, entry ->
                        if (index > 0) append(" · ")
                        val label = linkLabel(entry).let { if (entry.type == ItemType.BLOCK && (entry.type to entry.title) in repeated) "$it (${entry.subtitle})" else it }
                        withStyle(styles[entry.type].toSpanStyle(dark)) { append(label) }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun linkLabel(entry: ItemIndexEntry): String {
    val title = entry.title.let { if (it.length > 40) it.take(39) + "…" else it }
    return if (entry.type == ItemType.CHAPTER) "$title (${entry.subtitle})" else title
}
