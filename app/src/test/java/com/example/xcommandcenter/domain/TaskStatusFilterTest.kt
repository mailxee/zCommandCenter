package com.example.xcommandcenter.domain

import com.example.xcommandcenter.domain.TaskStatusFilter.Chip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskStatusFilterTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val yesterday = today.minusDays(1)

    @Test
    fun pastDueIsOpenAndDueBeforeToday() {
        assertTrue(TaskStatusFilter.isPastDue(false, yesterday, today))
        assertFalse(TaskStatusFilter.isPastDue(false, today, today))
        assertFalse(TaskStatusFilter.isPastDue(true, yesterday, today))
        assertFalse(TaskStatusFilter.isPastDue(false, null, today))
    }

    @Test
    fun onlyAndHidden() {
        val pastDueOnly = TaskStatusFilter().tap(Chip.PAST_DUE)
        assertTrue(pastDueOnly.matches(false, yesterday, today))
        assertFalse(pastDueOnly.matches(false, today, today))
        assertFalse(pastDueOnly.matches(true, yesterday, today))

        val completedHidden = TaskStatusFilter().hold(Chip.COMPLETED)
        assertTrue(completedHidden.matches(false, null, today))
        assertFalse(completedHidden.matches(true, null, today))

        val completedOnly = TaskStatusFilter().tap(Chip.COMPLETED)
        assertTrue(completedOnly.matches(true, yesterday, today))
        assertFalse(completedOnly.matches(false, null, today))
    }

    @Test
    fun showingOnlyOneTurnsTheOtherOnlyOff() {
        val f = TaskStatusFilter().tap(Chip.PAST_DUE).tap(Chip.COMPLETED)
        assertEquals(TaskStatusFilter(ChipState.ANY, ChipState.ONLY), f)
    }

    @Test
    fun hidingKeepsTheOther() {
        val f = TaskStatusFilter().hold(Chip.COMPLETED).tap(Chip.PAST_DUE)
        assertEquals(TaskStatusFilter(ChipState.ONLY, ChipState.HIDDEN), f)
    }

    @Test
    fun tapOrHoldAgainTurnsOff() {
        assertEquals(TaskStatusFilter(), TaskStatusFilter().tap(Chip.COMPLETED).tap(Chip.COMPLETED))
        assertEquals(TaskStatusFilter(), TaskStatusFilter().hold(Chip.PAST_DUE).hold(Chip.PAST_DUE))
        assertEquals(TaskStatusFilter(), TaskStatusFilter().hold(Chip.PAST_DUE).tap(Chip.PAST_DUE))
        assertEquals(TaskStatusFilter(ChipState.HIDDEN), TaskStatusFilter().tap(Chip.PAST_DUE).hold(Chip.PAST_DUE))
    }
}
