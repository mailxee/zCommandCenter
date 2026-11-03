package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Tidy links finding the user chose to keep as it is, by its key (see
 * `LinkAnalysis.Finding.key`, which names the link or end point involved). It stays hidden while
 * that link stays; a link removed and made again has a new id, so it shows again.
 */
@Entity(tableName = "kept_findings")
data class KeptFindingEntity(
    @PrimaryKey val key: String,
    val keptAt: Long = System.currentTimeMillis(),
)
