package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import java.time.LocalDate

/**
 * Recurrence is stored as flat columns; only the ones matching [recurrenceType] are meaningful.
 * Use [recurrence] / [withRecurrence] rather than reading them directly.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: HabitCategory = HabitCategory.POSITIVE,
    val recurrenceType: RecurrenceType = RecurrenceType.DAILY,
    /** Weekday bitmask for WEEKLY, see [Recurrence.daysFromMask]. */
    val weekdays: Int = 0,
    val dayOfMonth: Int = 1,
    val intervalDays: Int = 2,
    /** First day the habit is scheduled; anchors EVERY_N_DAYS and bounds streaks. */
    val startDate: LocalDate = LocalDate.now(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Position in the tab's list, lowest first; ties keep the tab's default order. Set by reordering. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
) {
    val recurrence: Recurrence
        get() = Recurrence.of(recurrenceType, weekdays, dayOfMonth, intervalDays)
}
