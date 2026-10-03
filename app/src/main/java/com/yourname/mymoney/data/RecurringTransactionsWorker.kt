package com.yourname.mymoney.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Creates transactions for recurring rules (DAILY / WEEKLY / MONTHLY).
 *
 * Each rule stores `lastGeneratedAt`; occurrences are generated strictly after that
 * point up to "now", so a missed run (phone off, app not opened) is caught up on the
 * next execution without ever producing duplicates. Rules and their generated copies
 * are written in one Room transaction, so a crash cannot half-apply a batch.
 */
class RecurringTransactionsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = MyMoneyDatabase.getDatabase(applicationContext)
            database.withTransaction {
                generateDueOccurrences(database)
            }
            Result.success()
        } catch (e: Exception) {
            // Local DB work: retry so a transient failure does not silently skip a day.
            Result.retry()
        }
    }

    private suspend fun generateDueOccurrences(database: MyMoneyDatabase): Int {
        val dao = database.transactionDao()
        val rules = dao.getRecurringRulesSync()
        val now = System.currentTimeMillis()
        var generated = 0

        for (rule in rules) {
            if (!rule.recurrenceActive) continue
            val periodField = when (rule.recurrence) {
                "DAILY" -> Calendar.DAY_OF_MONTH
                "WEEKLY" -> Calendar.WEEK_OF_YEAR
                "MONTHLY" -> Calendar.MONTH
                else -> continue
            }

            // Never generated yet: the rule's own timestamp is its first occurrence.
            val originalAnchor = if (rule.lastGeneratedAt > 0L) rule.lastGeneratedAt else rule.timestamp
            var anchor = originalAnchor
            var guard = 0

            while (guard < MAX_OCCURRENCES_PER_RULE) {
                val next = addPeriod(anchor, periodField)
                if (next > now) break
                dao.insertTransaction(
                    TransactionEntity(
                        title = rule.title,
                        amount = rule.amount,
                        type = rule.type,
                        category = rule.category,
                        timestamp = next,
                        note = rule.note,
                        // Copies are plain transactions; only the rule carries the label.
                        recurrence = "NONE",
                        lastGeneratedAt = 0L,
                        recurrenceActive = false
                    )
                )
                generated++
                anchor = next
                guard++
            }

            if (anchor != originalAnchor) {
                dao.updateTransaction(rule.copy(lastGeneratedAt = anchor))
            }
        }
        return generated
    }

    private fun addPeriod(fromMillis: Long, periodField: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        cal.add(periodField, 1)
        return cal.timeInMillis
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "recurring_transactions_daily"
        private const val CHECK_WORK_NAME = "recurring_transactions_check"
        // Safety bound only: at most one year of daily catch-up per run, so a clock
        // skew or absurd rule can never turn into an unbounded insert loop.
        private const val MAX_OCCURRENCES_PER_RULE = 366

        /** Daily pass. KEEP so relaunches never reset the 24h clock. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringTransactionsWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Runs one check soon (on app launch and after a rule is saved). APPEND_OR_REPLACE
         * queues behind any in-flight check instead of dropping or duplicating it.
         */
        fun triggerCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecurringTransactionsWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                CHECK_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
