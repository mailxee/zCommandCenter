package com.example.xcommandcenter.data.entity

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
)

/** A book with chapter counts, for the Books tab list. */
data class BookWithProgress(
    @Embedded val book: BookEntity,
    val chapterCount: Int,
    val internalizedCount: Int,
)
