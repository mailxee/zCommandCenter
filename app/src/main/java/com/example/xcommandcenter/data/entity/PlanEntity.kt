package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Plans form a tree through [parentId]; null means a top-level plan. [sortOrder] orders siblings. */
@Entity(
    tableName = "plans",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId")],
)
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Only true when every plan below this one is completed too (enforced by [com.example.xcommandcenter.data.dao.PlanDao]). */
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    /** Position in the tab's list, lowest first; ties keep the tab's default order. Set by reordering. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)
