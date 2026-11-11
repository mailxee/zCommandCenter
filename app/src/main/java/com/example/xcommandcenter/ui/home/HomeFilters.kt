package com.example.xcommandcenter.ui.home

import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ChipState
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType

// The sorts and filters the home tabs share; the state they hold lives in HomeViewModel.

/** [CUSTOM] is the user's own order, set by reordering; the only sort in which tasks can be moved. */
enum class TaskSort(val label: String) { DUE_DATE("Due date"), PRIORITY("Priority"), CUSTOM("Custom") }

/**
 * How the Goals, Habits, Plans, Notes, Books and Quotes tabs are sorted: [CUSTOM] is the user's own
 * order, the only one in which items can be moved; [PRIORITY] puts Critical first, keeping the
 * custom order among items of the same priority.
 */
enum class PrioritySort(val label: String) { CUSTOM("Custom"), PRIORITY("Priority") }

/** The tabs sorted and filtered by [PrioritySort] and a priority filter; Tasks have their own sorts but the same priority filter. */
val priorityTabs: Set<ItemType> = setOf(ItemType.GOAL, ItemType.HABIT, ItemType.PLAN, ItemType.NOTE, ItemType.BOOK, ItemType.QUOTE, ItemType.PERSON, ItemType.IDEA)

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
 *
 * The "Reminder" chip works the same way on every tab: [reminder] ONLY shows just the items in
 * [remindedToday] (a reminder going off today), HIDDEN hides them.
 */
data class TagFilter(
    val tagsByItem: Map<ItemRef, List<TagEntity>> = emptyMap(),
    val selected: Set<Long> = emptySet(),
    val excluded: Set<Long> = emptySet(),
    val reminder: ChipState = ChipState.ANY,
    val remindedToday: Set<ItemRef> = emptySet(),
) {
    val active: Boolean get() = selected.isNotEmpty() || excluded.isNotEmpty() || reminder != ChipState.ANY

    fun tagsOf(ref: ItemRef): List<TagEntity> = tagsByItem[ref].orEmpty()

    /** The item's tag ids, or [UNTAGGED] alone for an item without tags. */
    private fun idsOf(ref: ItemRef): Set<Long> = tagsOf(ref).map { it.id }.toSet().ifEmpty { setOf(UNTAGGED) }

    /** True when the item carries an excluded tag (or has none, with [UNTAGGED] excluded). */
    fun excludes(ref: ItemRef): Boolean =
        (excluded.isNotEmpty() && idsOf(ref).any { it in excluded }) || (reminder == ChipState.HIDDEN && ref in remindedToday)

    fun matches(ref: ItemRef): Boolean =
        (selected.isEmpty() || idsOf(ref).containsAll(selected)) && !excludes(ref) &&
            (reminder != ChipState.ONLY || ref in remindedToday)

    companion object {
        /** The "Untagged" chip; no tag has this id. */
        const val UNTAGGED = -1L
    }
}

/** In normal mode (null) every item; in link mode only the network's. */
internal fun Set<ItemRef>?.has(ref: ItemRef): Boolean = this == null || ref in this

/** A tab's counter: [shown] of [total] items. */
data class TabCount(val shown: Int, val total: Int)
