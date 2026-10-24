package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Optional; blank means untitled and the start of the body is shown instead. */
    val title: String = "",
    val body: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
