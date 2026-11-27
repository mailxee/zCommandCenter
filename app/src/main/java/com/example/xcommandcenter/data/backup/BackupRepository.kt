package com.example.xcommandcenter.data.backup

import com.example.xcommandcenter.data.prefs.ModeRepository
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.xcommandcenter.data.dao.BackupDao
import com.example.xcommandcenter.data.prefs.ItemStyleRepository
import com.example.xcommandcenter.data.prefs.ScheduleChoiceRepository
import com.example.xcommandcenter.data.prefs.TabOrderRepository
import com.example.xcommandcenter.data.prefs.forgetIds
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
    private val itemStyles: ItemStyleRepository,
    private val scheduleChoice: ScheduleChoiceRepository,
    private val modes: ModeRepository,
) {
    /** Writes the whole database to [uri]; returns how many items (across the seven tabs) went in. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = dao.snapshot()
        val choice = scheduleChoice.choice.first()
        val sheets = BackupFormat.toSheets(
            data, tabOrder.order.first(), styles = itemStyles.styles.first(), tabModes = modes.tabModes.first(),
            scheduleChoice = BackupScheduleChoice(choice.selectedId, choice.automatic),
            hiddenTabs = tabOrder.hidden.first(),
        )
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to the chosen file")
        out.use { Xlsx.write(sheets, it) }
        data.itemCount
    }

    /**
     * Replaces all data with the workbook at [uri]. Everything is read and checked first; if
     * anything is wrong an [ImportException] says what, and the current data is left untouched.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val sheets = read(uri)
        val result = BackupFormat.fromSheets(sheets)
        reminders.cancelAll()
        dao.replaceAll(result.data)
        forgetIds(context)
        reminders.rescheduleAll()
        result.settings?.let { settings ->
            tabOrder.setOrder(settings.tabOrder)
            settings.styles.forEach { (tab, style) -> itemStyles.setStyle(tab, style) }
            settings.tabModes?.let { modes.setTabModes(it) }
            settings.hiddenTabs?.let { tabOrder.setAllHidden(it) }
        }
        result.scheduleChoice?.let { scheduleChoice.restore(it.selectedId, it.automatic) }
        result
    }

    /**
     * Writes a network of ideas to [uri] (see [IdeaNetwork]): [rootId] and everything under it, or
     * with null every idea. Returns how many ideas went in.
     */
    suspend fun exportIdeas(uri: Uri, rootId: Long?): Int = withContext(Dispatchers.IO) {
        val network = IdeaNetwork.select(dao.snapshot(), rootId)
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to the chosen file")
        out.use { Xlsx.write(IdeaNetwork.toSheets(network), it) }
        network.ideas.size
    }

    /**
     * Adds the network of ideas at [uri] as copies, its top-level ideas under [parentId] (null: at
     * the top level). Nothing already here changes; an [ImportException] says why a file can't be used.
     */
    suspend fun importIdeas(uri: Uri, parentId: Long?): IdeaNetwork.Added = withContext(Dispatchers.IO) {
        val read = IdeaNetwork.fromSheets(read(uri))
        dao.addIdeas(read.network, parentId).copy(skipped = read.skipped)
    }

    /**
     * Reads the ideas in the file at [uri] for a look before they are added (§2.80): a network
     * file or backup (.xlsx), or with [word] an outline in a Word document (.docx). Nothing is
     * added; an [ImportException] says why a file can't be used.
     */
    suspend fun previewIdeas(uri: Uri, word: Boolean): IdeaImportPreview = withContext(Dispatchers.IO) {
        val fileName = displayName(uri)
        val stem = fileName?.substringBeforeLast('.')?.trim()?.ifEmpty { null }
        if (word) {
            val outline = try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Can't open the chosen file")
                input.use { WordOutline.read(it) }
            } catch (e: ImportException) {
                throw e
            } catch (e: Exception) {
                throw ImportException("This file couldn't be read as a Word document (.docx). Nothing was imported.")
            }
            IdeaImportPreview(
                fileName = fileName, network = outline.network, rootTitle = outline.title ?: stem ?: "Imported ideas", wrap = true,
                notes = listOfNotNull(
                    outline.joined.takeIf { it > 0 }?.let { "$it ${if (it == 1) "paragraph that isn't a point was" else "paragraphs that aren't points were"} added to the explanation of the point above." },
                    outline.leftOut.takeIf { it > 0 }?.let { "$it ${if (it == 1) "paragraph" else "paragraphs"} before the first point ${if (it == 1) "was" else "were"} left out." },
                ),
            )
        } else {
            val read = IdeaNetwork.fromSheets(read(uri))
            IdeaImportPreview(
                fileName = fileName, network = read.network, rootTitle = stem ?: "Imported ideas", wrap = false, skipped = read.skipped,
                notes = listOfNotNull(
                    read.skipped.takeIf { it > 0 }?.let { "$it ${if (it == 1) "row" else "rows"} pointing at items outside the network will be left out." },
                ),
            )
        }
    }

    /** Adds a previewed network as copies, under a new idea named [rootTitle] if given, its top under [parentId]. */
    suspend fun addPreviewed(preview: IdeaImportPreview, rootTitle: String?, parentId: Long?): IdeaNetwork.Added = withContext(Dispatchers.IO) {
        val network = rootTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { IdeaNetwork.wrap(preview.network, it) } ?: preview.network
        dao.addIdeas(network, parentId).copy(skipped = preview.skipped)
    }

    /** The name the file at [uri] is shown with, if the app that holds it says. */
    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun read(uri: Uri): List<Sheet> = try {
        val input = context.contentResolver.openInputStream(uri) ?: error("Can't open the chosen file")
        input.use { Xlsx.read(it) }
    } catch (e: ImportException) {
        throw e
    } catch (e: Exception) {
        throw ImportException("This file couldn't be read as an Excel workbook (.xlsx). Nothing was imported.")
    }

    /**
     * Deletes every item, link, tag and habit mark, every saved setting (the glossary's definitions
     * and the link labels go back to the starting ones) and every pending reminder.
     */
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        reminders.cancelAll()
        dao.deleteEverything()
        clearSettings(context)
    }
}
