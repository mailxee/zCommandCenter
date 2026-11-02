package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.Priority

/** Someone worth keeping track of: who they are, where they publish and what of theirs to remember. */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    /** A few lines on who they are; optional. */
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Position in the tab's list, lowest first; ties keep the tab's default order. Set by reordering. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "MEDIUM") val priority: Priority = Priority.MEDIUM,
)

/**
 * One of a person's profiles: a web address such as "x.com/name", stored without "https://". The
 * platform is worked out from it (see `Profiles`). [position] orders them within the person, from 0.
 */
@Entity(
    tableName = "person_profiles",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val position: Int,
    val url: String,
)

/**
 * One of a person's items: an idea of theirs, or a video or post with its link, as free text. Its
 * kind is worked out from the text (see `Profiles.kindOf`). [position] orders them within the person, from 0.
 */
@Entity(
    tableName = "person_items",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class PersonItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val position: Int,
    val text: String,
)
