package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY category ASC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY category ASC")
    suspend fun getAllBudgetsSync(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE category = :category AND month = :month")
    suspend fun deleteBudget(category: String, month: String)

    @Query("DELETE FROM budgets")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM budgets")
    suspend fun getCount(): Int
}
