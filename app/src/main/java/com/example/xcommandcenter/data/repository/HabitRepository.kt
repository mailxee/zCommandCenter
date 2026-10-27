package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.HabitDao
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Owns habits and their marks. */
class HabitRepository(
    private val dao: HabitDao,
    private val reminders: ReminderRepository,
) {
    fun observeAll(): Flow<List<HabitEntity>> = dao.observeAll()

    /** Mark dates per habit id. */
    fun observeAllMarks(): Flow<Map<Long, Set<LocalDate>>> =
        dao.observeAllMarks().map { marks -> marks.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }

    fun observeMarks(habitId: Long): Flow<Set<LocalDate>> =
        dao.observeMarks(habitId).map { marks -> marks.map { it.date }.toSet() }

    suspend fun get(id: Long): HabitEntity? = dao.get(id)

    /** A new habit gets a place in the list; an edited one keeps its place. Its reminders are saved by the caller. */
    suspend fun save(habit: HabitEntity): Long {
        val id = if (habit.id == 0L) {
            dao.insert(habit.copy(sortOrder = dao.newSortOrder()))
        } else {
            habit.id.also { dao.update(habit.copy(sortOrder = dao.get(habit.id)?.sortOrder ?: habit.sortOrder)) }
        }
        return id
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** Marks can change when an "every N days" habit is next due, so its reminders follow. */
    suspend fun toggleMark(habitId: Long, date: LocalDate) {
        dao.toggleMark(habitId, date)
        reminders.sync(ItemRef(ItemType.HABIT, habitId))
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.removeOrphans()
    }
}
