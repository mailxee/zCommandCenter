package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReminderTodayTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 9)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test
    fun `one-off reminders count on their day, habits when due, blocks when their schedule is in use`() {
        val task = ItemRef(ItemType.TASK, 1)
        val note = ItemRef(ItemType.NOTE, 2)
        val dueHabit = ItemRef(ItemType.HABIT, 3)
        val restHabit = ItemRef(ItemType.HABIT, 4)
        val block = ItemRef(ItemType.BLOCK, 5)
        val otherBlock = ItemRef(ItemType.BLOCK, 6)
        val byItem = mapOf(
            task to listOf(at(today.minusDays(1), 9), at(today, 23)),
            note to listOf(at(today.plusDays(1), 0)),
            dueHabit to listOf(7 * 60L), restHabit to listOf(7 * 60L),
            block to listOf(60L), otherBlock to listOf(60L),
        )
        val found = ReminderToday.items(byItem, today, zone, habitDue = { it == 3L }, blockShown = { it == 5L })
        assertEquals(setOf(task, dueHabit, block), found)
    }
}
