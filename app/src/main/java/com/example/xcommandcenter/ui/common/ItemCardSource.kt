package com.example.xcommandcenter.ui.common

import com.example.xcommandcenter.data.entity.BookWithProgress
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.repository.BookRepository
import com.example.xcommandcenter.data.repository.GoalRepository
import com.example.xcommandcenter.data.repository.HabitRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.NoteRepository
import com.example.xcommandcenter.data.repository.PlanRepository
import com.example.xcommandcenter.data.repository.QuoteRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.data.repository.TaskRepository
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** The control a card carries in its own tab, so the same card can offer it elsewhere. */
sealed interface CardControl {
    data class Task(val completed: Boolean) : CardControl

    data class Plan(val plan: PlanEntity) : CardControl

    /** Only for a habit scheduled today. */
    data class Habit(val positive: Boolean, val markedToday: Boolean) : CardControl
}

/** Everything an item's card shows. */
data class ItemCardData(
    val entry: ItemIndexEntry,
    val text: CardText,
    val tags: List<TagEntity> = emptyList(),
    val links: List<ItemIndexEntry> = emptyList(),
    val control: CardControl? = null,
    val reminder: String? = null,
) {
    val ref: ItemRef get() = entry.ref
}

/**
 * Card data for every item, built as the tabs build it, for showing linked items as cards on
 * detail pages; plus the actions behind the cards' controls. Chapters are not included: they have
 * no tab card, and [chapterCardText] makes one from the link entry.
 */
class ItemCardSource(
    private val tasks: TaskRepository,
    private val goals: GoalRepository,
    private val habits: HabitRepository,
    private val plans: PlanRepository,
    private val notes: NoteRepository,
    private val books: BookRepository,
    private val quotes: QuoteRepository,
    private val links: LinkRepository,
    private val tags: TagRepository,
    private val reminders: ReminderRepository,
) {
    private val texts: Flow<Map<ItemRef, Pair<CardText, CardControl?>>> = combine(
        combine(tasks.observeAll(), tasks.observeChecklistCounts(), goals.observeAll(), ::Triple),
        combine(habits.observeAll(), habits.observeAllMarks(), plans.observeAll(), ::Triple),
        combine(notes.observeAll(), notes.observeListCounts(), books.observeAllWithProgress(), quotes.observeAll()) { a, b, c, d -> NoteBookQuote(a, b, c, d) },
    ) { (taskList, checklists, goalList), (habitList, marks, planList), (noteList, listCounts, bookList, quoteList) ->
        val today = LocalDate.now()
        buildMap {
            taskList.forEach { put(ItemRef(ItemType.TASK, it.id), taskCardText(it, checklists[it.id]) to CardControl.Task(it.completed)) }
            goalList.forEach { put(ItemRef(ItemType.GOAL, it.id), goalCardText(it) to null) }
            habitList.forEach { habit ->
                val summary = habitSummary(habit, marks[habit.id].orEmpty(), today)
                val control = if (summary.scheduledToday) CardControl.Habit(summary.positive, summary.markedToday) else null
                put(ItemRef(ItemType.HABIT, habit.id), habitCardText(summary) to control)
            }
            planList.forEach { put(ItemRef(ItemType.PLAN, it.id), planCardText(it) to CardControl.Plan(it)) }
            noteList.forEach { put(ItemRef(ItemType.NOTE, it.id), noteCardText(it, listCounts[it.id] ?: 0) to null) }
            bookList.forEach { put(ItemRef(ItemType.BOOK, it.book.id), bookCardText(it) to null) }
            quoteList.forEach { put(ItemRef(ItemType.QUOTE, it.id), quoteCardText(it) to null) }
        }
    }

    private data class NoteBookQuote(
        val notes: List<NoteEntity>,
        val listCounts: Map<Long, Int>,
        val books: List<BookWithProgress>,
        val quotes: List<QuoteEntity>,
    )

    /** Text, control, tags, links and reminders per item; the entry is filled in by [cardsFor]. */
    private val parts = combine(texts, tags.observeTagsByItem(), links.observeLinksByOwner(), reminders.observeAll(), ::CardParts)

    private data class CardParts(
        val texts: Map<ItemRef, Pair<CardText, CardControl?>>,
        val tagsByItem: Map<ItemRef, List<TagEntity>>,
        val linksByOwner: Map<ItemRef, List<ItemIndexEntry>>,
        val remindersByItem: Map<ItemRef, List<Long>>,
    )

    /**
     * Cards for [entries] (the items linked to [self]), in the same order. Each card's "Linked:"
     * line leaves out [self], whose page the cards are shown on.
     */
    fun cardsFor(self: ItemRef, entries: Flow<List<ItemIndexEntry>>): Flow<List<ItemCardData>> =
        combine(entries, parts) { list, (texts, tagsByItem, linksByOwner, remindersByItem) ->
            list.map { entry ->
                val (text, control) = texts[entry.ref] ?: (chapterCardText(entry) to null)
                ItemCardData(
                    entry = entry,
                    text = text,
                    tags = tagsByItem[entry.ref].orEmpty(),
                    links = linksByOwner[entry.ref].orEmpty().filter { it.ref != self },
                    control = control,
                    reminder = remindersByItem[entry.ref]?.let { reminderLine(entry.type, it) },
                )
            }
        }

    suspend fun setTaskCompleted(id: Long, completed: Boolean) {
        tasks.setCompleted(id, completed)
    }

    /** Returns the number of open subplans; the plan is completed only when that is 0. */
    suspend fun completePlan(id: Long): Int = plans.complete(id)

    suspend fun completePlanWithSubplans(id: Long) = plans.completeWithSubplans(id)

    suspend fun reopenPlan(id: Long) = plans.reopen(id)

    suspend fun toggleHabitToday(id: Long) = habits.toggleMark(id, LocalDate.now())
}
