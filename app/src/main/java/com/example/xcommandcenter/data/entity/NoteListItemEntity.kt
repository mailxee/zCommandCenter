package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One bullet of a note's list. [position] orders the items within their note, starting at 0.
 * Unlike a task's checklist, list items are plain text: nothing to tick off.
 */
@Entity(
    tableName = "note_list_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class NoteListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val position: Int,
    val text: String,
)

/** How many list items a note has, for the Notes tab. */
data class NoteListCount(val noteId: Long, val total: Int)
