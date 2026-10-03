package com.example.data

data class CategoryBudgetStatus(
    val category: String,
    val monthlyLimit: Double,
    val spent: Double,
    val percentage: Float, // e.g. 0.85f for 85%
    val isWarning: Boolean, // >= 0.80 and <= 1.0
    val isExceeded: Boolean, // > 1.0
    val remaining: Double
)

data class MonthlyCashflowBar(
    val monthLabel: String,
    val yearMonthDisplay: String,
    val income: Double,
    val expense: Double,
    val net: Double
)
