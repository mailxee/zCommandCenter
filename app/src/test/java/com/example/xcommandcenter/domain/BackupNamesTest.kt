package com.example.xcommandcenter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BackupNamesTest {
    @Test
    fun `a day's name reads back as that day, and other files as none`() {
        val day = LocalDate.of(2026, 11, 9)
        assertEquals(day, BackupNames.dayOf(BackupNames.forDay(day)))
        assertNull(BackupNames.dayOf("notes.xlsx"))
        assertNull(BackupNames.dayOf("Manage Everything backup soon.xlsx"))
    }

    @Test
    fun `only backups beyond the newest kept are deleted, oldest first`() {
        val names = (1..5).map { BackupNames.forDay(LocalDate.of(2026, 11, it)) }.shuffled() + "holiday.xlsx"
        assertEquals(
            listOf(BackupNames.forDay(LocalDate.of(2026, 11, 1)), BackupNames.forDay(LocalDate.of(2026, 11, 2))),
            BackupNames.toDelete(names, keep = 3),
        )
        assertEquals(emptyList<String>(), BackupNames.toDelete(names, keep = 7))
    }
}
