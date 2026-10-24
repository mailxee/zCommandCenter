package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val strategy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
