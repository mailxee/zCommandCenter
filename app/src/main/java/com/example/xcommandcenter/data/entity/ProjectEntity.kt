package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Projects form a tree through [parentId]; null means a top-level project. */
@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId")],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
