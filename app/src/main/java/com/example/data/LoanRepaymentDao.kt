package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanRepaymentDao {
    @Query("SELECT * FROM loan_repayments WHERE loanId = :loanId ORDER BY date DESC")
    fun getRepaymentsForLoan(loanId: Long): Flow<List<LoanRepaymentEntity>>

    @Query("SELECT * FROM loan_repayments ORDER BY date DESC")
    fun getAllRepayments(): Flow<List<LoanRepaymentEntity>>

    @Query("SELECT * FROM loan_repayments ORDER BY date DESC")
    suspend fun getAllRepaymentsSync(): List<LoanRepaymentEntity>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM loan_repayments WHERE loanId = :loanId")
    suspend fun getTotalRepaidForLoan(loanId: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: LoanRepaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(repayments: List<LoanRepaymentEntity>)

    @Delete
    suspend fun deleteRepayment(repayment: LoanRepaymentEntity)

    @Query("DELETE FROM loan_repayments WHERE loanId = :loanId")
    suspend fun deleteRepaymentsForLoan(loanId: Long)

    @Query("DELETE FROM loan_repayments")
    suspend fun clearAll()
}
