package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.Priority

/**
 * A thought on the Ideas tab: a vision, belief, principle, idea or question. Ideas form a tree
 * through [parentId] (null means top level), like plans; links connect them to anything else.
 */
@Entity(
    tableName = "ideas",
    foreignKeys = [
        ForeignKey(entity = IdeaEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("parentId")],
)
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The thought in one line. */
    val title: String,
    /** Optional longer explanation. */
    val body: String = "",
    @ColumnInfo(defaultValue = "IDEA") val kind: IdeaKind = IdeaKind.IDEA,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Position among its siblings, lowest first. Set by reordering. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "MEDIUM") val priority: Priority = Priority.MEDIUM,
)
