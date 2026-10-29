package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleChoiceTest {
    private val weekdays = Recurrence.maskFromDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
    private val weekend = Recurrence.maskFromDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
    private val options = listOf(ScheduleChoice.Option(1, weekdays), ScheduleChoice.Option(2, weekend), ScheduleChoice.Option(3, 0))
    private val saturday = LocalDate.of(2026, 10, 31)
    private val friday = saturday.minusDays(1)

    @Test
    fun `without automatic switching the pick holds`() {
        assertEquals(3L, ScheduleChoice.active(options, 3, friday, automatic = false, today = saturday))
    }

    @Test
    fun `automatic switching starts a new day on that weekday's schedule`() {
        assertEquals(2L, ScheduleChoice.active(options, 1, friday, automatic = true, today = saturday))
        assertEquals(2L, ScheduleChoice.active(options, null, null, automatic = true, today = saturday))
    }

    @Test
    fun `a pick made today wins over automatic switching`() {
        assertEquals(3L, ScheduleChoice.active(options, 3, saturday, automatic = true, today = saturday))
    }

    @Test
    fun `a day with no schedule set keeps the pick`() {
        val noWeekend = options.map { if (it.id == 2L) it.copy(days = 0) else it }
        assertEquals(3L, ScheduleChoice.active(noWeekend, 3, friday, automatic = true, today = saturday))
    }

    @Test
    fun `a deleted pick falls back to the first schedule`() {
        assertEquals(1L, ScheduleChoice.active(options, 9, saturday, automatic = false, today = saturday))
        assertEquals(null, ScheduleChoice.active(emptyList(), 9, saturday, automatic = false, today = saturday))
    }

    @Test
    fun `assigning days takes them off other schedules`() {
        val result = ScheduleChoice.assignDays(options, 3, Recurrence.maskFromDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)))
        assertEquals(Recurrence.maskFromDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)), result[0].days)
        assertEquals(Recurrence.maskFromDays(setOf(DayOfWeek.SUNDAY)), result[1].days)
        assertEquals(Recurrence.maskFromDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)), result[2].days)
    }
}
