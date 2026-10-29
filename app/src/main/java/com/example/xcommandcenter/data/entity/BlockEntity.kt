package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A block of the Schedule tab: one part of the plan for every day, from [startMinute] to
 * [endMinute] (minutes of the day, 0..1440). Nothing is dated or completed.
 */
@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
