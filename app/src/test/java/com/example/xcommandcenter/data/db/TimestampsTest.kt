package com.example.xcommandcenter.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.RecurrenceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** `tasks.completedAt` and `habit_marks.markedAt` are set as other apps expect (see ARCHITECTURE.md 2.72). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimestampsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val tasks = db.taskDao()
    private val habits = db.habitDao()

    @After
    fun close() = db.close()

    @Test
    fun `completing records the time and unticking clears it`() = runBlocking {
        val id = tasks.insert(TaskEntity(title = "Call"))
        val before = System.currentTimeMillis()
        tasks.complete(id)
        val at = tasks.get(id)!!.completedAt
        assertNotNull(at)
        assertTrue(at!! in before..System.currentTimeMillis())

        tasks.reopen(id)
        assertNull(tasks.get(id)!!.completedAt)
    }

    @Test
    fun `completing again keeps the first time, and a task completed before the column stays null`() = runBlocking {
        val id = tasks.insert(TaskEntity(title = "Call", completed = true, completedAt = 1_000))
        tasks.complete(id)
        assertEquals(1_000L, tasks.get(id)!!.completedAt)

        val old = tasks.insert(TaskEntity(title = "Old", completed = true))
        tasks.complete(old)
        assertNull(tasks.get(old)!!.completedAt)
    }

    @Test
    fun `a repeating task's next occurrence starts with no completion time`() = runBlocking {
        val id = tasks.insert(TaskEntity(title = "Water", dueDate = LocalDate.now(), recurrenceType = RecurrenceType.DAILY))
        val next = tasks.complete(id)!!
        assertNotNull(tasks.get(id)!!.completedAt)
        assertNull(tasks.get(next)!!.completedAt)
    }

    @Test
    fun `a mark records when it was made, and marking it again keeps that`() = runBlocking {
        val habit = habits.insert(HabitEntity(name = "Walk", category = HabitCategory.POSITIVE, startDate = LocalDate.of(2026, 1, 1)))
        val day = LocalDate.of(2026, 11, 16)
        val before = System.currentTimeMillis()
        habits.toggleMark(habit, day)
        val at = db.backupDao().habitMarks().single().markedAt!!
        assertTrue(at in before..System.currentTimeMillis())

        habits.setMark(habit, day)
        assertEquals(at, db.backupDao().habitMarks().single().markedAt)

        habits.toggleMark(habit, day)
        assertTrue(db.backupDao().habitMarks().isEmpty())
        habits.setMark(habit, day)
        assertNotNull(db.backupDao().habitMarks().single().markedAt)
    }
}
