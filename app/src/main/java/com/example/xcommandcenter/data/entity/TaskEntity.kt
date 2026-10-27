package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import java.time.LocalDate

/**
 * A task repeats when [recurrenceType] is set; the schedule is stored as flat columns, as on
 * habits, and read through [recurrence]. Completing a repeating task creates a copy due on the
 * next occurrence (see [com.example.xcommandcenter.data.dao.TaskDao.complete]).
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDate: LocalDate? = null,
    /** Epoch millis of the reminder, or null when there is none. */
    val reminderAt: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Null for a one-off task. */
    val recurrenceType: RecurrenceType? = null,
    /** Weekday bitmask for WEEKLY and EVERY_N_WEEKS, see [Recurrence.daysFromMask]. */
    @ColumnInfo(defaultValue = "0") val weekdays: Int = 0,
    @ColumnInfo(defaultValue = "1") val dayOfMonth: Int = 1,
    /** N for EVERY_N_DAYS (days), EVERY_N_WEEKS (weeks) and EVERY_N_MONTHS (months). */
    @ColumnInfo(defaultValue = "2") val intervalDays: Int = 2,
    /** Set once completing this occurrence has created the next one, so unticking and ticking again doesn't make another. */
    @ColumnInfo(defaultValue = "0") val nextCreated: Boolean = false,
    /** Position in the tab's list, lowest first; ties keep the tab's default order. Set by reordering. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
) {
    val recurrence: Recurrence?
        get() = recurrenceType?.let { Recurrence.of(it, weekdays, dayOfMonth, intervalDays) }
}
