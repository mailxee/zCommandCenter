package com.example.xcommandcenter.ui.home

import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.SearchScope

/** One row of the Plans tab: a plan, or a task linked to the plan above it. */
sealed interface PlanTreeItem {
    val key: String
    val depth: Int

    data class Plan(
        val plan: PlanEntity,
        override val depth: Int,
        val collapsed: Boolean,
        val subplanCount: Int,
        val taskCount: Int,
    ) : PlanTreeItem {
        override val key: String get() = "plan-${plan.id}"
        val hasChildren: Boolean get() = subplanCount + taskCount > 0
    }

    /** A task linked to several plans appears under each, so the key includes the plan. */
    data class Task(val task: TaskEntity, val planId: Long, override val depth: Int) : PlanTreeItem {
        override val key: String get() = "task-$planId-${task.id}"
    }
}

object PlanRows {
    /**
     * The Plans tab: the plan tree with each plan's linked tasks right below it, ahead of
     * its subplans. Completed plans and tasks are left out unless their toggle is on; hiding
     * a completed plan never orphans an open one, because everything below it is completed too.
     * A collapsed plan hides its tasks and its whole subtree. With a tag filter, a search or link
     * mode on, only matching plans and tasks show, plus the plans above them so the tree still reads.
     * Also the number of plans the tab shows, collapsed or not, for its counter.
     */
    fun build(
        all: List<PlanEntity>,
        tasksByPlan: Map<Long, List<TaskEntity>>,
        showPlans: Boolean,
        showTasks: Boolean,
        collapsed: Set<Long>,
        tags: TagFilter,
        search: SearchScope,
        network: Set<ItemRef>?,
        byPriority: Boolean,
        priorities: Set<Priority>,
    ): Pair<List<PlanTreeItem>, Int> {
        val narrowed = tags.active || search.active || network != null
        // Sorting the plans sorts siblings: the tree keeps each plan's children in the order given.
        val sorted = if (byPriority) all.sortedByDescending { it.priority.ordinal } else all
        val shown = if (showPlans) sorted else sorted.filterNot { it.completed }
        fun tasksOf(planId: Long) = tasksByPlan[planId].orEmpty()
            .filter {
                (showTasks || !it.completed) && tags.matches(ItemRef(ItemType.TASK, it.id)) &&
                    search.matches(ItemRef(ItemType.TASK, it.id), it.title, it.description)
            }
        val visible = if (!narrowed && priorities.isEmpty()) {
            shown
        } else {
            // The priority filter applies to the plans themselves; their tasks are shown as usual.
            val hits = shown.filter { plan ->
                (priorities.isEmpty() || plan.priority in priorities) &&
                    (!narrowed || (network.has(ItemRef(ItemType.PLAN, plan.id)) && tags.matches(ItemRef(ItemType.PLAN, plan.id)) &&
                        search.matches(ItemRef(ItemType.PLAN, plan.id), plan.title, plan.description)) ||
                        (!tags.excludes(ItemRef(ItemType.PLAN, plan.id)) && tasksOf(plan.id).isNotEmpty()))
            }.map { it.id }
            val keep = hits.toSet() + hits.flatMap { PlanTree.ancestorIds(it, shown, { p -> p.id }, { p -> p.parentId }) }
            shown.filter { it.id in keep }
        }
        val subplanCounts = visible.groupingBy { it.parentId }.eachCount()
        val rows = PlanTree.flatten(visible, { it.id }, { it.parentId }, collapsed).flatMap { row ->
            val plan = row.node
            val tasks = tasksOf(plan.id)
            val isCollapsed = plan.id in collapsed
            val planItem = PlanTreeItem.Plan(plan, row.depth, isCollapsed, subplanCounts[plan.id] ?: 0, tasks.size)
            if (isCollapsed) listOf(planItem)
            else listOf(planItem) + tasks.map { PlanTreeItem.Task(it, plan.id, row.depth + 1) }
        }
        // A plan outside link mode's network, shown only as a heading, isn't counted.
        return rows to visible.count { network.has(ItemRef(ItemType.PLAN, it.id)) }
    }
}
