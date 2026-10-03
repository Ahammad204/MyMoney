package com.yourname.mymoney.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return AutoBackupWorker.performBackup(applicationContext)
    }

    companion object {
        fun schedule(context: Context) {
            AutoBackupWorker.schedule(context, AutoBackupInterval.DAILY)
        }

        fun cancel(context: Context) {
            AutoBackupWorker.cancel(context)
        }
    }
}
