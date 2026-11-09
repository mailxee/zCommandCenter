package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A label a link can carry, with a wording for each end: [forward] reads from the link's subject
 * to its object ("supports"), [backward] the other way ("supported by"). A label without
 * [backward] reads the same from both ends ("contradicts"). [key] names the labels the app starts
 * with, so it can treat some specially (a contradiction is never suggested for removal); labels
 * the user adds have none.
 */
@Entity(tableName = "link_labels", indices = [Index(value = ["key"], unique = true)])
data class LinkLabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String? = null,
    val forward: String,
    val backward: String? = null,
    val sortOrder: Int = 0,
) {
    val symmetric: Boolean get() = backward.isNullOrBlank()

    /** The wording read from one end: [subject] is true from the end the label points away from. */
    fun wording(subject: Boolean): String = if (subject || symmetric) forward else backward.orEmpty()
}

/** How many links carry a label. */
data class LabelUse(val id: Long, val uses: Int)
