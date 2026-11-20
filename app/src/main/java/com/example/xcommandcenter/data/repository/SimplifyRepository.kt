package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.SimplifyDao
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

/**
 * Settings › Simplify (see [SimplifyDao]): goals become Visions in Ideas, their reminders' alarms
 * follow them, and the Goals tab is hidden. A one-time step, to be removed once used.
 */
class SimplifyRepository(
    private val dao: SimplifyDao,
    private val reminders: ReminderRepository,
    private val tabs: TabOrderRepository,
) {
    val goalCount: Flow<Int> = dao.observeGoalCount()

    /** Returns how many goals were moved. */
    suspend fun goalsToVisions(): Int {
        val moved = dao.goalsToVisions()
        // A reminder keeps its id, so setting its alarm again replaces the old one, now for the idea.
        moved.values.forEach { reminders.sync(ItemRef(ItemType.IDEA, it)) }
        if (moved.isNotEmpty()) tabs.setHidden(ItemType.GOAL, true)
        return moved.size
    }
}
