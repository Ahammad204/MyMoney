package com.yourname.mymoney.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourname.mymoney.util.DateUtils

@Database(
    entities = [
        TransactionEntity::class,
        LoanEntity::class,
        CategoryEntity::class,
        LoanRepaymentEntity::class,
        BudgetEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class MyMoneyDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun categoryDao(): CategoryDao
    abstract fun loanRepaymentDao(): LoanRepaymentDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        // v6: recurrence rules gained lastGeneratedAt (duplicate prevention) and
        // recurrenceActive (pause switch). Both columns are NOT NULL with defaults
        // so every existing row keeps working without data loss.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN lastGeneratedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN recurrenceActive INTEGER NOT NULL DEFAULT 1")
            }
        }

        // v7: budgets became per-category-per-month. The primary key changed from
        // (category) to (category, month), so the table is rebuilt; existing rows
        // keep their limits and are assigned to the current month.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val currentMonth = DateUtils.currentMonthKey()
                db.execSQL("CREATE TABLE budgets_new (category TEXT NOT NULL, month TEXT NOT NULL, monthlyLimit REAL NOT NULL, PRIMARY KEY(category, month))")
                db.execSQL(
                    "INSERT INTO budgets_new (category, month, monthlyLimit) SELECT category, ?, monthlyLimit FROM budgets",
                    arrayOf(currentMonth)
                )
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
            }
        }

        /**
         * Version 5 was the baseline schema: it is exported to app/schemas at build time.
         * Older versions were never exported, so no historical schema exists to migrate
         * from. To change the schema, bump `version` above, keep the exported JSON in
         * app/schemas and register the resulting Migration here.
         */
        private val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7)

        @Volatile
        private var INSTANCE: MyMoneyDatabase? = null

        fun getDatabase(context: Context): MyMoneyDatabase {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MyMoneyDatabase::class.java,
                    "mymoney_database"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
