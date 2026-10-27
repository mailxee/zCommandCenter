package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Position in the tab's list, lowest first; ties keep the tab's default order. Set by reordering. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** How often the whole book has been read; like a chapter's, changed at once, not through the form. */
    @ColumnInfo(defaultValue = "0") val readCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val internalized: Boolean = false,
)

/** A book with chapter counts, for the Books tab list. */
data class BookWithProgress(
    @Embedded val book: BookEntity,
    val chapterCount: Int,
    val internalizedCount: Int,
)
