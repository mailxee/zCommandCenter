package com.example.xcommandcenter.domain

import java.time.LocalDate

/** The daily backups' file names, one per day, and which old ones to delete. */
object BackupNames {
    private const val PREFIX = "Manage Everything backup "
    private const val SUFFIX = ".xlsx"

    fun forDay(day: LocalDate): String = "$PREFIX$day$SUFFIX"

    /** The day a backup's file name is for, or null for any other file (those are never touched). */
    fun dayOf(name: String): LocalDate? =
        name.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Of [names] in the folder, the backups beyond the newest [keep], oldest first; other files are left out. */
    fun toDelete(names: List<String>, keep: Int): List<String> =
        names.mapNotNull { n -> dayOf(n)?.let { n to it } }
            .sortedByDescending { it.second }
            .drop(keep.coerceAtLeast(1))
            .sortedBy { it.second }
            .map { it.first }

    /** The choices for how many to keep. */
    val keepChoices = listOf(3, 7, 14, 30)
    const val DEFAULT_KEEP = 7
}
