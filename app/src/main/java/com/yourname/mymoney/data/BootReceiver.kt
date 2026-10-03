package com.yourname.mymoney.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val backupManager = GoogleDriveBackupManager(context)
            val interval = backupManager.autoBackupInterval
            if (interval != AutoBackupInterval.OFF) {
                AutoBackupWorker.schedule(context, interval)
            }
        }
    }
}
