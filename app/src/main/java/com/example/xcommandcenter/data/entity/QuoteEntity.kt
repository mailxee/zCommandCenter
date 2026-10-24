package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    /** Optional free text, blank when unknown. */
    val source: String = "",
    /** Optional, blank when unknown. */
    val author: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
