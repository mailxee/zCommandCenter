package com.example.xcommandcenter.ui.home

import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanRowsTest {
    private val parent = PlanEntity(id = 1, title = "House")
    private val child = PlanEntity(id = 2, title = "Kitchen", parentId = 1)
    private val done = PlanEntity(id = 3, title = "Garden", completed = true)
    private val paint = TaskEntity(id = 10, title = "Paint walls")
    private val tasks = mapOf(2L to listOf(paint))

    private fun build(collapsed: Set<Long> = emptySet(), search: String = "", showPlans: Boolean = false) =
        PlanRows.build(
            listOf(parent, child, done), tasks, showPlans, showTasks = false, collapsed,
            TagFilter(), SearchScope(search), network = null, byPriority = false, priorities = emptySet(),
        )

    private fun keys(rows: List<PlanTreeItem>) = rows.map { it.key }

    @Test
    fun tasksSitUnderTheirPlanAndCompletedPlansHide() {
        val (rows, count) = build()
        assertEquals(listOf("plan-1", "plan-2", "task-2-10"), keys(rows))
        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
        assertEquals(2, count)
        assertEquals(3, build(showPlans = true).second)
    }

    @Test
    fun collapsingHidesEverythingBelow() {
        assertEquals(listOf("plan-1"), keys(build(collapsed = setOf(1L)).first))
    }

    @Test
    fun aSearchKeepsThePlansAboveAMatchingTask() {
        assertEquals(listOf("plan-1", "plan-2", "task-2-10"), keys(build(search = "paint").first))
        assertEquals(emptyList<String>(), keys(build(search = "nothing").first))
    }
}
