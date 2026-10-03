package com.yourname.mymoney.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LoanWithDetails(
    val loan: LoanEntity,
    val repayments: List<LoanRepaymentEntity>
) {
    val totalAmount: Double = loan.amount
    val paidAmount: Double = repayments.sumOf { it.amount }
    val remainingBalance: Double = (totalAmount - paidAmount).coerceAtLeast(0.0)
    val isSettled: Boolean = loan.isSettled || remainingBalance <= 0.001

    val isOverdue: Boolean
        get() {
            if (isSettled || loan.dueDate <= 0) return false
            val zone = ZoneId.systemDefault()
            val dueDate = Instant.ofEpochMilli(loan.dueDate).atZone(zone).toLocalDate()
            return dueDate < LocalDate.now(zone)
        }
}

data class PersonLoanSummary(
    val personName: String,
    val totalLentRemaining: Double,
    val totalBorrowedRemaining: Double,
    val netBalance: Double, // > 0 means person owes user, < 0 means user owes person
    val activeLoansCount: Int,
    val settledLoansCount: Int,
    val loans: List<LoanWithDetails>
)
