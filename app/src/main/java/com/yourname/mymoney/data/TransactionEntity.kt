package com.yourname.mymoney.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val recurrence: String = "NONE", // "NONE", "DAILY", "WEEKLY", "MONTHLY"
    // Epoch millis of the last occurrence generated from this rule (0 = never).
    // A transaction with recurrence != "NONE" is a recurrence rule; generated
    // copies always carry recurrence = "NONE" so only the rule advances.
    @ColumnInfo(defaultValue = "0")
    val lastGeneratedAt: Long = 0,
    // false = the rule is paused (kept, but no new occurrences are created).
    @ColumnInfo(defaultValue = "1")
    val recurrenceActive: Boolean = true
)
