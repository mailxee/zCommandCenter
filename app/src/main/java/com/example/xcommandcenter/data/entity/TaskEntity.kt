package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.Priority
import java.time.LocalDate

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
)
