package com.example.xcommandcenter.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.xcommandcenter.XCommandCenterApp

/** Runs one daily backup (see [AutoBackupRepository]); a failure is recorded and shown in Settings, then tried again. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as XCommandCenterApp).container.autoBackupRepository
        return if (repository.backUp()) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}
