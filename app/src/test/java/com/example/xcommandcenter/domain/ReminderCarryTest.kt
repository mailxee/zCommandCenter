package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderCarryTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun nextIsDaysLaterAtTheSameTime() {
        assertEquals(at("2026-11-04T09:00"), ReminderCarry.next(at("2026-11-03T09:00"), 1, at("2026-11-03T09:00"), zone))
        assertEquals(at("2026-11-06T09:00"), ReminderCarry.next(at("2026-11-03T09:00"), 3, at("2026-11-03T09:01"), zone))
    }

    @Test
    fun nextSkipsDaysAlreadyPast() {
        // Missed while the phone was off: lands on the first day still ahead.
        assertEquals(at("2026-11-07T09:00"), ReminderCarry.next(at("2026-11-03T09:00"), 2, at("2026-11-06T12:00"), zone))
    }

    @Test
    fun nextKeepsTheClockTimeAcrossDaylightSaving() {
        assertEquals(at("2026-10-26T09:00"), ReminderCarry.next(at("2026-10-25T09:00"), 1, at("2026-10-25T09:00"), zone))
        assertEquals(at("2026-03-30T09:00"), ReminderCarry.next(at("2026-03-29T09:00"), 1, at("2026-03-29T09:00"), zone))
    }

    @Test
    fun offMeansNoNext() {
        assertNull(ReminderCarry.next(at("2026-11-03T09:00"), 0, at("2026-11-03T09:00"), zone))
    }

    @Test
    fun appliesOnlyToOpenTasksAndPlansWithNothingLater() {
        val now = at("2026-11-03T09:00")
        assertTrue(ReminderCarry.applies(ItemType.TASK, false, emptyList(), now, 1))
        assertTrue(ReminderCarry.applies(ItemType.PLAN, false, listOf(at("2026-11-01T09:00")), now, 1))
        assertFalse(ReminderCarry.applies(ItemType.TASK, true, emptyList(), now, 1))
        assertFalse(ReminderCarry.applies(ItemType.TASK, false, listOf(at("2026-11-05T09:00")), now, 1))
        assertFalse(ReminderCarry.applies(ItemType.GOAL, false, emptyList(), now, 1))
        assertFalse(ReminderCarry.applies(ItemType.NOTE, false, emptyList(), now, 1))
        assertFalse(ReminderCarry.applies(ItemType.TASK, false, emptyList(), now, 0))
    }

    @Test
    fun missedOnlyWithinTheWindow() {
        val now = at("2026-11-04T08:00")
        assertTrue(ReminderCarry.missed(at("2026-11-03T09:00"), now, 1, zone))
        assertFalse(ReminderCarry.missed(at("2026-11-02T09:00"), now, 1, zone))
        assertTrue(ReminderCarry.missed(at("2026-11-02T09:00"), now, 2, zone))
        assertFalse(ReminderCarry.missed(at("2026-11-03T09:00"), now, 0, zone))
    }
}
