package com.yourname.mymoney.data

import androidx.room.Entity

@Entity(tableName = "budgets", primaryKeys = ["category", "month"])
data class BudgetEntity(
    val category: String,
    val month: String,
    val monthlyLimit: Double
)
