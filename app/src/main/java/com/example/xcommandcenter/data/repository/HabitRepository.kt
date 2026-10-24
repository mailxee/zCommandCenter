package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.HabitDao
import com.example.xcommandcenter.data.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HabitRepository(private val dao: HabitDao) {
    fun observeAll(): Flow<List<HabitEntity>> = dao.observeAll()

    /** Mark dates per habit id. */
    fun observeAllMarks(): Flow<Map<Long, Set<LocalDate>>> =
        dao.observeAllMarks().map { marks -> marks.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }

    fun observeMarks(habitId: Long): Flow<Set<LocalDate>> =
        dao.observeMarks(habitId).map { marks -> marks.map { it.date }.toSet() }

    suspend fun get(id: Long): HabitEntity? = dao.get(id)

    suspend fun save(habit: HabitEntity): Long =
        if (habit.id == 0L) dao.insert(habit) else habit.id.also { dao.update(habit) }

    suspend fun toggleMark(habitId: Long, date: LocalDate) = dao.toggleMark(habitId, date)

    suspend fun delete(id: Long) = dao.delete(id)
}
