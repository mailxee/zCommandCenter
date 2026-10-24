package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A table-of-contents entry. [position] orders chapters within their book, starting at 0. */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val position: Int,
    val title: String,
    val readCount: Int = 0,
    val internalized: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
