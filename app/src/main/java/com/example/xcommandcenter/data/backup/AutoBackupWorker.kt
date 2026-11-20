package com.example.xcommandcenter.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.reminder.Notifications
import kotlinx.coroutines.flow.first

/**
 * Runs one daily backup (see [AutoBackupRepository]). A failure is recorded and shown in Settings,
 * then tried again, up to three times; when it gives up, or at once when access to the folder is
 * gone (trying again can't help), a notification says so, so days don't pass without a backup
 * unnoticed. A backup that works clears the notification.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as XCommandCenterApp).container.autoBackupRepository
        if (repository.backUp()) {
            Notifications.cancelBackupFailed(applicationContext)
            return Result.success()
        }
        val settings = repository.settings.first()
        val hopeless = !repository.hasAccess(settings.folder)
        if (!hopeless && runAttemptCount < 2) return Result.retry()
        Notifications.showBackupFailed(applicationContext, settings.lastError ?: "The backup couldn't be saved.")
        return Result.failure()
    }
}
