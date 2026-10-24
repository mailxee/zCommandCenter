package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDate

/** One explicit mark: "done" for a positive habit, "missed" for a negative one. */
@Entity(
    tableName = "habit_marks",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class HabitMarkEntity(
    val habitId: Long,
    val date: LocalDate,
)
