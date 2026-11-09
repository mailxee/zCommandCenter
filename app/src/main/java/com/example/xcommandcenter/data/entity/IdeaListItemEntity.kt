package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One bullet of an idea's list, like a note's: plain text ordered by [position] from 0. */
@Entity(
    tableName = "idea_list_items",
    foreignKeys = [
        ForeignKey(entity = IdeaEntity::class, parentColumns = ["id"], childColumns = ["ideaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("ideaId")],
)
data class IdeaListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ideaId: Long,
    val position: Int,
    val text: String,
)

/** How many list items an idea has, for the Ideas tab. */
data class IdeaListCount(val ideaId: Long, val total: Int)
