package com.example.xcommandcenter.ui.common

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import com.example.xcommandcenter.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/** The habit card's week: the last seven days up to today, a due day tapped anywhere in its column. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitWeekTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25) // A Wednesday.

    @Test
    fun lastSevenDaysWithDueDaysTappable() {
        val mondays = HabitEntity(
            name = "Run", startDate = LocalDate.of(2026, 11, 1),
            recurrenceType = RecurrenceType.WEEKLY, weekdays = Recurrence.maskFromDays(setOf(DayOfWeek.MONDAY)),
        )
        val tapped = mutableListOf<LocalDate>()
        compose.setContent { AppTheme { HabitWeek(mondays, marks = setOf(LocalDate.of(2026, 11, 23)), today = today, onToggle = { tapped += it }) } }

        // Thursday the 19th to today; tomorrow isn't shown.
        compose.onNodeWithText("19").assertExists()
        compose.onNodeWithText("26").assertDoesNotExist()
        // Monday was due (and is done): tappable. Tuesday wasn't due: not.
        compose.onNodeWithContentDescription("Monday 23 November, done").assertHasClickAction().performClick()
        compose.onNodeWithText("24").assertHasNoClickAction()
        assertEquals(listOf(LocalDate.of(2026, 11, 23)), tapped)
    }
}
