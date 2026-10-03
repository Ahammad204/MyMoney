package com.yourname.mymoney.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yourname.mymoney.MainActivity
import com.yourname.mymoney.R
import com.yourname.mymoney.ui.components.CurrencyManager
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return performBackup(applicationContext)
    }

    companion object {
        const val WORK_NAME = "mymoney_auto_backup"
        const val NOTIFICATION_CHANNEL_ID = "drive_backup_channel"
        const val NOTIFICATION_ID = 2001

        suspend fun performBackup(context: Context): Result {
            val backupManager = GoogleDriveBackupManager(context)
            val interval = backupManager.autoBackupInterval
            if (interval == AutoBackupInterval.OFF) {
                return Result.success()
            }
            if (backupManager.accountEmail.isNullOrBlank()) {
                return Result.success()
            }

            val auth = backupManager.authorizeDrive()
            if (auth !is DriveAuthOutcome.Granted) {
                val errorMsg = context.getString(R.string.notif_auth_expired_msg)
                backupManager.lastBackupError = errorMsg
                showAuthExpiredNotification(context)
                return Result.failure()
            }

            return try {
                val database = MyMoneyDatabase.getDatabase(context)
                val transactions = database.transactionDao().getAllTransactionsSync()
                val loans = database.loanDao().getAllLoansSync()
                val repayments = database.loanRepaymentDao().getAllRepaymentsSync()
                val budgets = database.budgetDao().getAllBudgetsSync()
                val categories = database.categoryDao().getAllCategoriesSync()

                val snapshot = BackupData(transactions, loans, repayments, budgets, categories)
                val existingFiles = DriveRestClient.listBackupFiles(auth.accessToken)
                if (!snapshot.hasRecords() && existingFiles.isNotEmpty()) {
                    // Database is empty but cloud backup exists: do not overwrite
                    return Result.success()
                }

                val json = DataBackupService.exportToJson(
                    transactions,
                    loans,
                    repayments,
                    budgets,
                    categories,
                    settings = mapOf(
                        "currencyCode" to CurrencyManager.currentCurrency.code
                    )
                )

                val uploadResult = backupManager.uploadBackup(auth.accessToken, json)
                if (uploadResult.isSuccess) {
                    Result.success()
                } else {
                    val err = uploadResult.exceptionOrNull()
                    val msg = err?.message ?: context.getString(R.string.err_gdrive_backup_failed)
                    backupManager.lastBackupError = msg
                    if (err is DriveApiException && (err.statusCode == 401 || err.statusCode == 403)) {
                        showAuthExpiredNotification(context)
                    }
                    Result.retry()
                }
            } catch (e: Exception) {
                val msg = e.message ?: context.getString(R.string.err_gdrive_backup_failed)
                backupManager.lastBackupError = msg
                if (e is DriveApiException && (e.statusCode == 401 || e.statusCode == 403)) {
                    showAuthExpiredNotification(context)
                }
                Result.retry()
            }
        }

        fun schedule(context: Context, interval: AutoBackupInterval) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (interval == AutoBackupInterval.OFF) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(interval.days, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }

        fun showAuthExpiredNotification(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.notif_channel_drive_backup),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_drive_backup_desc)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notif_auth_expired_title))
                .setContentText(context.getString(R.string.notif_auth_expired_msg))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Ignore if notification permission is not granted on API 33+
            }
        }
    }
}
