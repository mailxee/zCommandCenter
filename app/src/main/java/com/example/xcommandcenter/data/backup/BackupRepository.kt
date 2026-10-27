package com.example.xcommandcenter.data.backup

import android.content.Context
import android.net.Uri
import com.example.xcommandcenter.data.dao.BackupDao
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.data.prefs.clearCollapsedPlans
import com.example.xcommandcenter.data.prefs.clearSettings
import com.example.xcommandcenter.data.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Export to and import from an Excel workbook (see [BackupFormat]), and wiping all data.
 * Alarms are not data: they are cancelled before data goes away and re-registered after an import.
 */
class BackupRepository(
    private val context: Context,
    private val dao: BackupDao,
    private val reminders: ReminderRepository,
    private val tabOrder: TabOrderRepository,
) {
    /** Writes the whole database to [uri]; returns how many items (across the seven tabs) went in. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = dao.snapshot()
        val sheets = BackupFormat.toSheets(data, tabOrder.order.first())
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to the chosen file")
        out.use { Xlsx.write(sheets, it) }
        data.itemCount
    }

    /**
     * Replaces all data with the workbook at [uri]. Everything is read and checked first; if
     * anything is wrong an [ImportException] says what, and the current data is left untouched.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val sheets = try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Can't open the chosen file")
            input.use { Xlsx.read(it) }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("This file couldn't be read as an Excel workbook (.xlsx). Nothing was imported.")
        }
        val result = BackupFormat.fromSheets(sheets)
        reminders.cancelAll()
        dao.replaceAll(result.data)
        clearCollapsedPlans(context)
        reminders.rescheduleAll()
        result
    }

    /** Deletes every item, link, tag and habit mark, every saved setting and every pending reminder. */
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        reminders.cancelAll()
        dao.deleteAll()
        clearSettings(context)
    }
}
