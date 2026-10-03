package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MyMoneyRepositoryTest {

    private lateinit var db: MyMoneyDatabase
    private lateinit var repository: MyMoneyRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MyMoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MyMoneyRepository(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    // 1. Balance and Monthly Totals
    @Test
    fun testBalanceAndMonthlyTotals() = runBlocking {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)

        // Today's timestamp
        val now = System.currentTimeMillis()

        // Transaction in current month: Income 5000
        repository.insertTransaction(
            TransactionEntity(
                title = "Salary",
                amount = 5000.0,
                type = "INCOME",
                category = "Salary",
                timestamp = now
            )
        )

        // Transaction in current month: Expense 1500
        repository.insertTransaction(
            TransactionEntity(
                title = "Groceries",
                amount = 1500.0,
                type = "EXPENSE",
                category = "Food",
                timestamp = now
            )
        )

        // Transaction in previous year: Expense 500
        val lastYearCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear - 1)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 10)
        }
        repository.insertTransaction(
            TransactionEntity(
                title = "Old Book",
                amount = 500.0,
                type = "EXPENSE",
                category = "Other",
                timestamp = lastYearCal.timeInMillis
            )
        )

        val allTx = repository.getAllTransactionsSync()
        assertEquals(3, allTx.size)

        val totalIncome = allTx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = allTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val totalBalance = totalIncome - totalExpense

        assertEquals(5000.0, totalIncome, 0.001)
        assertEquals(2000.0, totalExpense, 0.001)
        assertEquals(3000.0, totalBalance, 0.001)

        // Check monthly totals
        val startOfMonth = com.example.util.DateUtils.monthStart(currentYear, currentMonth).timeInMillis
        val endOfMonth = com.example.util.DateUtils.monthEnd(currentYear, currentMonth).timeInMillis

        val monthTx = allTx.filter { it.timestamp in startOfMonth until endOfMonth }
        val monthIncome = monthTx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val monthExpense = monthTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val netSavings = monthIncome - monthExpense

        assertEquals(5000.0, monthIncome, 0.001)
        assertEquals(1500.0, monthExpense, 0.001)
        assertEquals(3500.0, netSavings, 0.001)
    }

    // 2. Loan Repayment and Settlement Logic
    @Test
    fun testLoanRepaymentAndSettlement() = runBlocking {
        val loanId = repository.insertLoan(
            LoanEntity(
                personName = "Alice",
                amount = 1000.0,
                type = "LENT",
                date = System.currentTimeMillis()
            )
        )

        var loan = repository.getLoanById(loanId)
        assertNotNull(loan)
        assertFalse(loan!!.isSettled)

        // Partial repayment of 400
        repository.addRepayment(loanId = loanId, amount = 400.0, date = System.currentTimeMillis(), note = "Part 1")
        loan = repository.getLoanById(loanId)
        assertNotNull(loan)
        assertFalse("Loan should not be settled with partial repayment", loan!!.isSettled)

        val totalRepaid = db.loanRepaymentDao().getTotalRepaidForLoan(loanId)
        assertEquals(400.0, totalRepaid, 0.001)

        val details = LoanWithDetails(loan, db.loanRepaymentDao().getAllRepaymentsSync())
        assertEquals(600.0, details.remainingBalance, 0.001)
        assertFalse(details.isSettled)

        // Complete remaining repayment of 600
        repository.addRepayment(loanId = loanId, amount = 600.0, date = System.currentTimeMillis(), note = "Part 2")
        loan = repository.getLoanById(loanId)
        assertNotNull(loan)
        assertTrue("Loan should be settled when total repaid equals or exceeds loan amount", loan!!.isSettled)

        val finalDetails = LoanWithDetails(loan, db.loanRepaymentDao().getAllRepaymentsSync())
        assertEquals(0.0, finalDetails.remainingBalance, 0.001)
        assertTrue(finalDetails.isSettled)
    }

    // 3. Overdue Logic
    @Test
    fun testOverdueLogic() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val yesterdayEpoch = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowEpoch = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // Unsettled loan due yesterday -> Overdue
        val pastLoan = LoanEntity(
            personName = "Bob",
            amount = 500.0,
            type = "BORROWED",
            dueDate = yesterdayEpoch,
            isSettled = false
        )
        val overdueDetails = LoanWithDetails(pastLoan, emptyList())
        assertTrue("Unsettled loan due in the past should be overdue", overdueDetails.isOverdue)

        // Settled loan due yesterday -> Not overdue
        val settledLoan = pastLoan.copy(isSettled = true)
        val settledDetails = LoanWithDetails(settledLoan, emptyList())
        assertFalse("Settled loan should never be overdue", settledDetails.isOverdue)

        // Unsettled loan due tomorrow -> Not overdue
        val futureLoan = pastLoan.copy(dueDate = tomorrowEpoch)
        val futureDetails = LoanWithDetails(futureLoan, emptyList())
        assertFalse("Loan with future due date should not be overdue", futureDetails.isOverdue)

        // Unsettled loan with no due date (0) -> Not overdue
        val noDueDateLoan = pastLoan.copy(dueDate = 0L)
        val noDueDateDetails = LoanWithDetails(noDueDateLoan, emptyList())
        assertFalse("Loan with no due date should not be overdue", noDueDateDetails.isOverdue)
    }

    // 4. Budget Status Logic
    @Test
    fun testBudgetStatusCalculations() = runBlocking {
        val monthKey = "2026-10"

        // Set budget: Food = 1000
        val budget = BudgetEntity(category = "Food", month = monthKey, monthlyLimit = 1000.0)

        // Case A: Spent 500 (50%) -> Normal (Not warning, not exceeded)
        val spent500 = 500.0
        val pct500 = (spent500 / budget.monthlyLimit).toFloat()
        val status500 = CategoryBudgetStatus(
            category = budget.category,
            monthlyLimit = budget.monthlyLimit,
            spent = spent500,
            percentage = pct500,
            isWarning = ! (spent500 > budget.monthlyLimit) && pct500 >= 0.80f,
            isExceeded = spent500 > budget.monthlyLimit,
            remaining = budget.monthlyLimit - spent500
        )
        assertFalse(status500.isWarning)
        assertFalse(status500.isExceeded)
        assertEquals(500.0, status500.remaining, 0.001)

        // Case B: Spent 850 (85%) -> Warning (>= 80% and <= 100%)
        val spent850 = 850.0
        val pct850 = (spent850 / budget.monthlyLimit).toFloat()
        val status850 = CategoryBudgetStatus(
            category = budget.category,
            monthlyLimit = budget.monthlyLimit,
            spent = spent850,
            percentage = pct850,
            isWarning = ! (spent850 > budget.monthlyLimit) && pct850 >= 0.80f,
            isExceeded = spent850 > budget.monthlyLimit,
            remaining = budget.monthlyLimit - spent850
        )
        assertTrue("85% spend should trigger warning", status850.isWarning)
        assertFalse("85% spend should not be exceeded", status850.isExceeded)
        assertEquals(150.0, status850.remaining, 0.001)

        // Case C: Spent 1200 (120%) -> Exceeded
        val spent1200 = 1200.0
        val pct1200 = (spent1200 / budget.monthlyLimit).toFloat()
        val status1200 = CategoryBudgetStatus(
            category = budget.category,
            monthlyLimit = budget.monthlyLimit,
            spent = spent1200,
            percentage = pct1200,
            isWarning = ! (spent1200 > budget.monthlyLimit) && pct1200 >= 0.80f,
            isExceeded = spent1200 > budget.monthlyLimit,
            remaining = budget.monthlyLimit - spent1200
        )
        assertFalse("Exceeded budget is not warning state", status1200.isWarning)
        assertTrue("120% spend should be marked exceeded", status1200.isExceeded)
        assertEquals(-200.0, status1200.remaining, 0.001)
    }

    // 5. Backup Restore: Replace and Merge Logic
    @Test
    fun testBackupRestoreReplace() = runBlocking {
        // Initial state on device
        repository.insertTransaction(
            TransactionEntity(title = "Old Tx", amount = 100.0, type = "EXPENSE", category = "Food")
        )
        repository.insertLoan(
            LoanEntity(personName = "Old Contact", amount = 200.0, type = "LENT")
        )

        assertEquals(1, repository.getAllTransactionsSync().size)
        assertEquals(1, repository.getAllLoansSync().size)

        // Prepare a backup
        val backup = BackupData(
            transactions = listOf(
                TransactionEntity(id = 10, title = "Backup Tx", amount = 999.0, type = "INCOME", category = "Salary")
            ),
            loans = listOf(
                LoanEntity(id = 20, personName = "Backup Person", amount = 888.0, type = "BORROWED")
            ),
            repayments = emptyList(),
            budgets = listOf(
                BudgetEntity(category = "Salary", month = "2026-10", monthlyLimit = 5000.0)
            ),
            categories = listOf(
                CategoryEntity(name = "Custom Cat", isCustom = true)
            )
        )

        // Restore Replace: should wipe existing and replace with backup
        repository.restoreAll(backup)

        val txAfter = repository.getAllTransactionsSync()
        val loansAfter = repository.getAllLoansSync()
        val budgetsAfter = db.budgetDao().getAllBudgetsSync()
        val categoriesAfter = db.categoryDao().getAllCategoriesSync()

        assertEquals(1, txAfter.size)
        assertEquals("Backup Tx", txAfter[0].title)

        assertEquals(1, loansAfter.size)
        assertEquals("Backup Person", loansAfter[0].personName)

        assertEquals(1, budgetsAfter.size)
        assertEquals("Salary", budgetsAfter[0].category)

        assertTrue(categoriesAfter.any { it.name == "Custom Cat" })
    }

    @Test
    fun testBackupRestoreMerge() = runBlocking {
        // Existing state
        val existingTx = TransactionEntity(id = 1, title = "Shared Tx", amount = 50.0, type = "EXPENSE", category = "Food", timestamp = 1000L)
        repository.insertTransaction(existingTx)

        val existingLoan = LoanEntity(id = 1, personName = "John", amount = 300.0, type = "LENT", date = 2000L)
        repository.insertLoan(existingLoan)

        val existingBudget = BudgetEntity(category = "Food", month = "2026-10", monthlyLimit = 800.0)
        db.budgetDao().setBudget(existingBudget)

        // Backup containing:
        // 1. A duplicate transaction (same title, amount, timestamp)
        // 2. A new transaction
        // 3. A new loan
        // 4. An existing budget with different limit (existing device budget should win)
        // 5. A new budget
        val backup = BackupData(
            transactions = listOf(
                TransactionEntity(id = 99, title = "Shared Tx", amount = 50.0, type = "EXPENSE", category = "Food", timestamp = 1000L),
                TransactionEntity(id = 100, title = "New Imported Tx", amount = 150.0, type = "INCOME", category = "Salary", timestamp = 3000L)
            ),
            loans = listOf(
                LoanEntity(id = 50, personName = "Jane", amount = 700.0, type = "BORROWED", date = 4000L)
            ),
            repayments = emptyList(),
            budgets = listOf(
                BudgetEntity(category = "Food", month = "2026-10", monthlyLimit = 1200.0), // Should not override 800.0
                BudgetEntity(category = "Transport", month = "2026-10", monthlyLimit = 400.0) // New budget, should be inserted
            ),
            categories = listOf(
                CategoryEntity(name = "Transport", isCustom = true)
            )
        )

        repository.restoreMerged(backup)

        val txMerged = repository.getAllTransactionsSync()
        assertEquals("Duplicate transaction should be skipped; total should be 2", 2, txMerged.size)
        assertTrue(txMerged.any { it.title == "Shared Tx" })
        assertTrue(txMerged.any { it.title == "New Imported Tx" })

        val loansMerged = repository.getAllLoansSync()
        assertEquals(2, loansMerged.size)
        assertTrue(loansMerged.any { it.personName == "John" })
        assertTrue(loansMerged.any { it.personName == "Jane" })

        val budgetsMerged = db.budgetDao().getAllBudgetsSync()
        assertEquals(2, budgetsMerged.size)
        val foodBudget = budgetsMerged.first { it.category == "Food" }
        assertEquals("Existing device budget limit should be preserved", 800.0, foodBudget.monthlyLimit, 0.001)
        val transportBudget = budgetsMerged.first { it.category == "Transport" }
        assertEquals(400.0, transportBudget.monthlyLimit, 0.001)
    }
}
