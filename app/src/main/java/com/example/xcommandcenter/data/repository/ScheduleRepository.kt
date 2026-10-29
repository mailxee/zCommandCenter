package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.ScheduleDao
import com.example.xcommandcenter.data.entity.ScheduleEntity
import com.example.xcommandcenter.data.prefs.ScheduleChoiceRepository
import com.example.xcommandcenter.data.prefs.StoredScheduleChoice
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ScheduleChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * The Schedule tab's schedules and which one is active (see [ScheduleChoice]): the one shown, the
 * one new blocks go into, and the only one whose reminders go off.
 */
class ScheduleRepository(
    private val dao: ScheduleDao,
    private val choices: ScheduleChoiceRepository,
    private val reminders: () -> ReminderRepository,
) {
    val schedules: Flow<List<ScheduleEntity>> = dao.observeAll()

    val choice: Flow<StoredScheduleChoice> = choices.choice

    /** Today's date, checked each minute, so automatic switching happens at midnight. */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val active: Flow<ScheduleEntity?> = combine(schedules, choice, today) { all, choice, today ->
        val id = ScheduleChoice.active(all.options(), choice.selectedId, choice.selectedOn, choice.automatic, today)
        all.firstOrNull { it.id == id }
    }.distinctUntilChanged()

    suspend fun activeId(today: LocalDate = LocalDate.now()): Long? {
        val choice = choices.choice.first()
        return ScheduleChoice.active(dao.all().options(), choice.selectedId, choice.selectedOn, choice.automatic, today)
    }

    suspend fun select(id: Long) = choices.select(id, LocalDate.now())

    suspend fun setAutomatic(on: Boolean) = choices.setAutomatic(on)

    /** A new, empty schedule, which becomes the one shown. */
    suspend fun create(name: String) {
        select(dao.insert(ScheduleEntity(name = name, sortOrder = dao.nextSortOrder())))
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    /** A copy with every block, link, tag and reminder, which becomes the one shown. */
    suspend fun duplicate(id: Long, name: String) {
        val blocks = dao.duplicate(id, name)
        blocks.forEach { reminders().sync(ItemRef(ItemType.BLOCK, it)) }
        dao.all().lastOrNull()?.let { select(it.id) }
    }

    /** The weekdays [id] is switched to automatically; they are taken off every other schedule. */
    suspend fun setDays(id: Long, days: Int) {
        val all = dao.all()
        val updated = ScheduleChoice.assignDays(all.options(), id, days).associate { it.id to it.days }
        dao.update(all.map { it.copy(days = updated[it.id] ?: it.days) })
    }

    /** Deletes a schedule and its blocks; the last one can't be deleted. */
    suspend fun delete(id: Long) {
        if (dao.all().size <= 1) return
        dao.delete(id)
        reminders().removeOrphans()
    }

    private fun List<ScheduleEntity>.options() = map { ScheduleChoice.Option(it.id, it.days) }
}
