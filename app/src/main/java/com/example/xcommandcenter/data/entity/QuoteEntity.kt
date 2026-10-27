package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.Priority

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    /** Optional free text, blank when unknown. */
    val source: String = "",
    /** Optional, blank when unknown. */
    val author: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Position in the tab's list, lowest first; ties keep the tab's default order. Set by reordering. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** How important the item is; Medium unless set. Tabs can sort and filter by it. */
    @ColumnInfo(defaultValue = "MEDIUM") val priority: Priority = Priority.MEDIUM,
)
