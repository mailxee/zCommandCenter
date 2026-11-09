package com.example.xcommandcenter.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.xcommandcenter.data.prefs.settingsStore
import com.example.xcommandcenter.domain.BackupNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** The daily backup's settings and how the last one went. */
data class AutoBackupSettings(
    val enabled: Boolean = false,
    /** The folder chosen with Android's folder picker, as a tree URI; null until one is chosen. */
    val folder: String? = null,
    val keep: Int = BackupNames.DEFAULT_KEEP,
    /** When the last backup ran, epoch millis; 0 for never. */
    val lastRun: Long = 0,
    /** Null when the last backup worked; otherwise what went wrong, in words. */
    val lastError: String? = null,
)

/**
 * Daily backups to a folder the user picks (local storage, or a cloud app that offers folders): the
 * same workbook as Export, one file a day named by date, keeping the newest [AutoBackupSettings.keep]
 * and deleting older ones. Other files in the folder are never touched. WorkManager runs it once a
 * day, also when the app is closed; see [AutoBackupWorker].
 */
class AutoBackupRepository(private val context: Context, private val backup: BackupRepository) {
    private val enabledKey = booleanPreferencesKey("auto_backup_on")
    private val folderKey = stringPreferencesKey("auto_backup_folder")
    private val keepKey = intPreferencesKey("auto_backup_keep")
    private val lastRunKey = longPreferencesKey("auto_backup_last_run")
    private val lastErrorKey = stringPreferencesKey("auto_backup_last_error")

    val settings: Flow<AutoBackupSettings> = context.settingsStore.data.map { p ->
        AutoBackupSettings(
            enabled = p[enabledKey] ?: false,
            folder = p[folderKey],
            keep = p[keepKey] ?: BackupNames.DEFAULT_KEEP,
            lastRun = p[lastRunKey] ?: 0,
            lastError = p[lastErrorKey],
        )
    }

    /** Keeps access to [folder] across restarts, and turns daily backups on there. */
    suspend fun setFolder(folder: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(folder, flags)
        val old = settings.first().folder
        if (old != null && old != folder.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        context.settingsStore.edit { it[folderKey] = folder.toString() }
        setEnabled(true)
    }

    suspend fun setEnabled(on: Boolean) {
        context.settingsStore.edit { it[enabledKey] = on }
        schedule()
    }

    suspend fun setKeep(keep: Int) {
        context.settingsStore.edit { it[keepKey] = keep }
    }

    /** Registers the daily job when backups are on (keeping one already registered), or cancels it. */
    suspend fun schedule() {
        val s = settings.first()
        // Not set up in some test runners; on a phone it always is.
        val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (s.enabled && s.folder != null) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(DAILY)
        }
    }

    /** "Back up now": runs the backup soon, in the background, whatever the time of day. */
    fun runNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    /** The folder's name, as the file picker shows it; null when it can't be read (moved or access lost). */
    suspend fun folderName(folder: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val tree = Uri.parse(folder)
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    /**
     * Writes today's backup into the folder (replacing today's if there is one), then deletes the
     * oldest backups beyond the number to keep. Records how it went; returns false on failure.
     */
    suspend fun backUp(today: LocalDate = LocalDate.now()): Boolean = withContext(Dispatchers.IO) {
        val s = settings.first()
        val result = runCatching {
            val folder = s.folder ?: error("No folder chosen")
            val tree = Uri.parse(folder)
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            fun list(): List<Pair<String, Uri>> = resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1) to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))) }
            } ?: error("The folder can't be read. Choose it again.")
            val name = BackupNames.forDay(today)
            val target = list().firstOrNull { it.first == name }?.second
                ?: DocumentsContract.createDocument(resolver, parent, XLSX_MIME, name)
                ?: error("A file can't be made in the folder")
            backup.export(target)
            val existing = list()
            BackupNames.toDelete(existing.map { it.first }, s.keep).forEach { old ->
                existing.firstOrNull { it.first == old }?.second?.let { DocumentsContract.deleteDocument(resolver, it) }
            }
        }
        context.settingsStore.edit { p ->
            p[lastRunKey] = System.currentTimeMillis()
            val error = result.exceptionOrNull()
            if (error == null) p.remove(lastErrorKey) else p[lastErrorKey] = error.message ?: error.javaClass.simpleName
        }
        result.isSuccess
    }

    companion object {
        private const val DAILY = "daily-backup"
        private const val NOW = "backup-now"
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
