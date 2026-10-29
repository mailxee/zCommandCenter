package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A block of the Schedule tab: one part of a daily plan, from [startMinute] to [endMinute]
 * (minutes of the day, 0..1440), in the schedule [scheduleId]. Nothing is dated or completed.
 */
@Entity(tableName = "blocks", indices = [Index("scheduleId")])
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Blocks from before there were several schedules belong to the first. */
    @ColumnInfo(defaultValue = "1") val scheduleId: Long = 1,
)
