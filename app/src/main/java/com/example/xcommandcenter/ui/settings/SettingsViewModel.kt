package com.example.xcommandcenter.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.backup.BackupRepository
import com.example.xcommandcenter.data.backup.ImportException
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(
    private val tabOrderRepository: TabOrderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val backup: BackupRepository,
) : ViewModel() {

    val order: StateFlow<List<ItemType>> = tabOrderRepository.order
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabOrder.default)

    /** True while an export, import or delete runs; the data buttons are disabled meanwhile. */
    var busy by mutableStateOf(false)
        private set

    /** Shown once in a snackbar, then cleared by the screen. */
    var message by mutableStateOf<String?>(null)
        private set

    fun messageShown() {
        message = null
    }

    fun move(from: Int, to: Int) {
        val updated = TabOrder.move(order.value, from, to)
        viewModelScope.launch { tabOrderRepository.setOrder(updated) }
    }

    fun reset() {
        viewModelScope.launch { tabOrderRepository.setOrder(TabOrder.default) }
    }

    fun canScheduleExactReminders(): Boolean = reminderScheduler.canScheduleExact()

    /** Suggested name for an export, e.g. "xCommandCenter-2026-10-25.xlsx". */
    fun exportFileName(): String = "xCommandCenter-${LocalDate.now()}.xlsx"

    fun export(uri: Uri) = run("Export failed") {
        val count = backup.export(uri)
        "Exported $count ${if (count == 1) "item" else "items"} to Excel."
    }

    fun import(uri: Uri) = run("Import failed") {
        val result = backup.import(uri)
        val count = result.data.itemCount
        buildString {
            append("Imported $count ${if (count == 1) "item" else "items"}.")
            if (result.skipped > 0) append(" Skipped ${result.skipped} rows that referred to missing items.")
        }
    }

    fun deleteEverything() = run("Delete failed") {
        backup.deleteEverything()
        "Everything was deleted."
    }

    private fun run(failure: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            message = try {
                block()
            } catch (e: ImportException) {
                e.message
            } catch (e: Exception) {
                "$failure: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }
}
