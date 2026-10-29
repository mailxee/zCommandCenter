package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One of the Schedule tab's plans ("Workday", "Weekend"), holding its own blocks. [days] are the
 * weekdays it is switched to automatically (a `Recurrence.maskFromDays` mask; 0 for none). There
 * is always at least one; the first, id 1, starts as "My schedule".
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val days: Int = 0,
)
