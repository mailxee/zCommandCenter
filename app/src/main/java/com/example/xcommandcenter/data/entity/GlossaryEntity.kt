package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A definition the user rewrote in the glossary, by its term's key (see `Glossary`). */
@Entity(tableName = "glossary")
data class GlossaryEntity(@PrimaryKey val key: String, val definition: String)
