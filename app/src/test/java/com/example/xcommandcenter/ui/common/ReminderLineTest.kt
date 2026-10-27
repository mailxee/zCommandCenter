package com.example.xcommandcenter.ui.common

import com.example.xcommandcenter.domain.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderLineTest {
    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val now = millis(LocalDateTime.of(2026, 10, 27, 12, 0))

    @Test
    fun itemsShowTheNextUpcomingReminderAndHowManyMore() {
        val past = millis(LocalDateTime.of(2026, 10, 26, 9, 0))
        val next = LocalDateTime.of(2026, 10, 28, 9, 0)
        val later = millis(LocalDateTime.of(2026, 11, 1, 9, 0))
        assertEquals("Reminder ${next.display()} (+1 more)", reminderLine(ItemType.NOTE, listOf(later, past, millis(next)), now))
        assertEquals("Reminder ${next.display()}", reminderLine(ItemType.TASK, listOf(millis(next)), now))
        assertNull(reminderLine(ItemType.TASK, listOf(past), now))
    }

    @Test
    fun habitsShowAllTheirTimesOfDay() {
        val line = reminderLine(ItemType.HABIT, listOf(18 * 60L, 9 * 60L), now)
        assertEquals("Reminders ${LocalTime.of(9, 0).display()}, ${LocalTime.of(18, 0).display()}", line)
        assertNull(reminderLine(ItemType.HABIT, emptyList(), now))
    }
}
