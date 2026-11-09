package com.example.xcommandcenter.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.backup.BackupRepository
import com.example.xcommandcenter.data.backup.ImportException
import com.example.xcommandcenter.data.entity.TagUsage
import com.example.xcommandcenter.data.backup.AutoBackupRepository
import com.example.xcommandcenter.data.backup.AutoBackupSettings
import com.example.xcommandcenter.data.prefs.ModeRepository
import com.example.xcommandcenter.domain.TabMode
import com.example.xcommandcenter.domain.TabModes
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.data.prefs.ItemStyleRepository
import com.example.xcommandcenter.data.prefs.ReminderPrefsRepository
import com.example.xcommandcenter.data.prefs.RoutePrefsRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.domain.ThinkRoute
import com.example.xcommandcenter.domain.IdeaKind
import kotlinx.coroutines.flow.map
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemStyle
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.reminder.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val tabOrderRepository: TabOrderRepository,
    private val itemStyleRepository: ItemStyleRepository,
    private val reminderScheduler: ReminderScheduler,
    private val backup: BackupRepository,
    private val tags: TagRepository,
    private val reminderPrefs: ReminderPrefsRepository,
    private val routePrefs: RoutePrefsRepository,
    private val links: LinkRepository,
    private val modes: ModeRepository,
    private val autoBackup: AutoBackupRepository,
) : ViewModel() {

    /** The daily backup's settings and how the last one went. */
    val autoBackupSettings: StateFlow<AutoBackupSettings> = autoBackup.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupSettings())

    /** The chosen folder's name as the file picker shows it; null if none or it can't be read. */
    val backupFolderName: StateFlow<String?> = autoBackup.settings
        .map { s -> s.folder?.let { autoBackup.folderName(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun chooseBackupFolder(folder: Uri) {
        viewModelScope.launch {
            try {
                autoBackup.setFolder(folder)
                message = "Daily backups go to that folder now."
            } catch (e: SecurityException) {
                message = "That folder can't be kept for backups. Try another."
            }
        }
    }

    fun setAutoBackup(on: Boolean) {
        viewModelScope.launch { autoBackup.setEnabled(on) }
    }

    fun setBackupKeep(keep: Int) {
        viewModelScope.launch { autoBackup.setKeep(keep) }
    }

    fun backUpNow() {
        autoBackup.runNow()
        message = "Backing up in the background…"
    }

    /** Which mode, Do or Think, shows each tab. */
    val tabModes: StateFlow<TabModes> = modes.tabModes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabModes())

    fun setTabMode(tab: ItemType, mode: TabMode) {
        viewModelScope.launch { modes.setTabMode(tabModes.value, tab, mode) }
    }

    /** Every link label, with how many links carry it. */
    val labels: StateFlow<List<LinkLabelEntity>> = links.labels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val labelUses: StateFlow<Map<Long, Int>> = links.labelUses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Adds a label, or renames [id]'s; a blank [backward] makes it read the same both ways. */
    fun saveLabel(id: Long?, forward: String, backward: String) {
        if (forward.isBlank()) return
        viewModelScope.launch {
            if (id == null) links.addLabel(forward, backward) else links.renameLabel(id, forward, backward)
        }
    }

    /** Its links stay, without the label. */
    fun deleteLabel(label: LinkLabelEntity) {
        viewModelScope.launch {
            links.deleteLabel(label.id)
            message = "Deleted the label \"${label.forward}\"; its links stay, unlabelled."
        }
    }

    /** The suggested route of links: which layer each linking type sits in. */
    val route: StateFlow<LinkRoute> = routePrefs.route
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinkRoute())

    fun setLayer(type: ItemType, layer: Int) {
        viewModelScope.launch { routePrefs.setRoute(route.value.with(type, layer)) }
    }

    fun resetRoute() {
        viewModelScope.launch { routePrefs.setRoute(LinkRoute()) }
    }

    /** Think mode's route: a layer for each idea kind. */
    val thinkRoute: StateFlow<ThinkRoute> = routePrefs.thinkRoute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThinkRoute())

    fun setThinkLayer(kind: IdeaKind, layer: Int) {
        viewModelScope.launch { routePrefs.setThinkRoute(thinkRoute.value.with(kind, layer)) }
    }

    fun resetThinkRoute() {
        viewModelScope.launch { routePrefs.setThinkRoute(ThinkRoute()) }
    }

    /** Whether Tidy links points out goals no idea links to. */
    val checkWhy: StateFlow<Boolean> = routePrefs.checkWhy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setCheckWhy(on: Boolean) {
        viewModelScope.launch { routePrefs.setCheckWhy(on) }
    }

    /** How many Tidy links findings ask for something, across every network, in both modes. */
    val tidySuggestions: StateFlow<Int> = links.observeGraph().map { g -> (g.analysis.findings + g.thinkAnalysis.findings).count { it.suggests } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Days until an open task's or plan's reminder is carried over; 0 is off. */
    val carryDays: StateFlow<Int> = reminderPrefs.carryDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderPrefsRepository.DEFAULT_CARRY_DAYS)

    fun setCarryDays(days: Int) {
        viewModelScope.launch { reminderPrefs.setCarryDays(days) }
    }

    /** Every tag in use, with its item count, for the Tags section. */
    val tagUsage: StateFlow<List<TagUsage>> = tags.observeUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Takes the tag off every item and deletes it. */
    fun deleteTag(tag: TagUsage) {
        viewModelScope.launch {
            tags.delete(tag.id)
            message = "Deleted the tag \"${tag.name}\"."
        }
    }

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

    /** Applied at once, like the tab order; the screen shows the result through the app-wide styles. */
    fun setStyle(tab: ItemType, style: ItemStyle) {
        viewModelScope.launch { itemStyleRepository.setStyle(tab, style) }
    }

    fun resetStyle(tab: ItemType) {
        viewModelScope.launch { itemStyleRepository.reset(tab) }
    }

    fun canScheduleExactReminders(): Boolean = reminderScheduler.canScheduleExact()

    /** Suggested name for an export, e.g. "ManageEverything-2026-10-25.xlsx". */
    fun exportFileName(): String = "ManageEverything-${LocalDate.now()}.xlsx"

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
        // Settings are gone, the backup folder with them, so the daily job stops too.
        autoBackup.schedule()
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
