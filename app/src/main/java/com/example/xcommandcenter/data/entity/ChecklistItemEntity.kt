package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One bullet of a task's checklist. [position] orders the items within their task, starting at 0.
 * Ticking items is independent of completing the task: a task can be completed with open items.
 */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val position: Int,
    val text: String,
    val done: Boolean = false,
)

/** How many of a task's checklist items are ticked, for the Tasks tab. */
data class ChecklistCount(val taskId: Long, val total: Int, val done: Int)
