package com.example.xcommandcenter.domain

/** Declared lowest to highest, so [ordinal] doubles as the sort rank. */
enum class Priority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical"),
}
