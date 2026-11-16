package com.example.xcommandcenter.handoff

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.domain.HabitCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The mark screen against the real database: it writes only on confirmation (markedAt is HabitDao's, see TimestampsTest), and reports "already". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarkHabitViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container = (context as XCommandCenterApp).container
    private val today = LocalDate.now()

    private fun newModel() = MarkHabitViewModel(container.handoffGate, container.habitRepository)

    private fun habit(category: HabitCategory = HabitCategory.POSITIVE) = runBlocking {
        container.habitRepository.save(HabitEntity(name = "Run", category = category, startDate = today.minusDays(10)))
    }

    /** Lets the view model's coroutines and Room's threads finish until [done]. */
    private fun awaitUntil(done: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(10)
        }
        error("Timed out")
    }

    @Test
    fun confirmingMarksTheDay() {
        val id = habit()
        val model = newModel()
        model.start(context.packageName, id, today.toEpochDay())
        awaitUntil { model.state.value != MarkState.Checking }
        assertTrue(model.state.value is MarkState.Ask)

        model.confirm()
        awaitUntil { model.state.value == MarkState.Marked }
        assertEquals(setOf(today), runBlocking { container.habitRepository.markDates(id) })
    }

    @Test
    fun notConfirmingWritesNothingAndAMarkedDayIsSuccess() {
        val id = habit()
        val model = newModel()
        model.start(context.packageName, id, today.toEpochDay())
        awaitUntil { model.state.value != MarkState.Checking }
        assertEquals(emptySet<LocalDate>(), runBlocking { container.habitRepository.markDates(id) })

        runBlocking { container.habitRepository.mark(id, today) }
        val again = newModel()
        again.start(context.packageName, id, today.toEpochDay())
        awaitUntil { again.state.value != MarkState.Checking }
        assertEquals(true, (again.state.value as MarkState.Message).marked)
    }

    @Test
    fun aMissingHabitOrAnUnknownCallerIsRefused() {
        val missing = newModel()
        missing.start(context.packageName, 999_999, today.toEpochDay())
        awaitUntil { missing.state.value != MarkState.Checking }
        assertEquals(false, (missing.state.value as MarkState.Message).marked)

        val unknown = newModel()
        unknown.start(null, habit(), today.toEpochDay())
        awaitUntil { unknown.state.value != MarkState.Checking }
        assertEquals(com.example.xcommandcenter.share.CallerVerdict.Unknown.MESSAGE, (unknown.state.value as MarkState.Message).text)
    }
}
