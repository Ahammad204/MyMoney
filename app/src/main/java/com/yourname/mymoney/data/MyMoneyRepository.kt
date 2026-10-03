package com.yourname.mymoney.data

import androidx.room.withTransaction
import com.yourname.mymoney.util.DateUtils
import com.yourname.mymoney.util.DefaultCategories
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class MyMoneyRepository(private val database: MyMoneyDatabase) {
    private val transactionDao: TransactionDao = database.transactionDao()
    private val loanDao: LoanDao = database.loanDao()
    private val categoryDao: CategoryDao = database.categoryDao()
    private val loanRepaymentDao: LoanRepaymentDao = database.loanRepaymentDao()
    private val budgetDao: BudgetDao = database.budgetDao()

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allLoans: Flow<List<LoanEntity>> = loanDao.getAllLoans()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allRepayments: Flow<List<LoanRepaymentEntity>> = loanRepaymentDao.getAllRepayments()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteById(id)
    }

    suspend fun insertCategory(name: String, isCustom: Boolean = true) {
        categoryDao.insertCategory(CategoryEntity(name = name.trim(), isCustom = isCustom))
    }

    suspend fun setBudget(category: String, month: String, limit: Double) {
        budgetDao.setBudget(
            BudgetEntity(category = category.trim(), month = month, monthlyLimit = limit)
        )
    }

    suspend fun deleteBudget(category: String, month: String) {
        budgetDao.deleteBudget(category.trim(), month)
    }

    suspend fun insertLoan(loan: LoanEntity): Long {
        return loanDao.insertLoan(loan)
    }

    suspend fun updateLoan(loan: LoanEntity) {
        loanDao.updateLoan(loan)
    }

    suspend fun deleteLoan(loan: LoanEntity) {
        database.withTransaction {
            loanRepaymentDao.deleteRepaymentsForLoan(loan.id)
            loanDao.deleteLoan(loan)
        }
    }

    suspend fun deleteLoanById(id: Long) {
        database.withTransaction {
            loanRepaymentDao.deleteRepaymentsForLoan(id)
            loanDao.deleteById(id)
        }
    }

    suspend fun insertRepayment(repayment: LoanRepaymentEntity): Long {
        return loanRepaymentDao.insertRepayment(repayment)
    }

    suspend fun addRepayment(loanId: Long, amount: Double, date: Long, note: String) {
        database.withTransaction {
            loanRepaymentDao.insertRepayment(
                LoanRepaymentEntity(
                    loanId = loanId,
                    amount = amount,
                    date = date,
                    note = note
                )
            )
            val loan = loanDao.getLoanById(loanId) ?: return@withTransaction
            if (!loan.isSettled && loanRepaymentDao.getTotalRepaidForLoan(loanId) >= loan.amount) {
                loanDao.updateLoan(loan.copy(isSettled = true))
            }
        }
    }

    suspend fun deleteRepayment(repayment: LoanRepaymentEntity) {
        loanRepaymentDao.deleteRepayment(repayment)
    }

    suspend fun snapshotForBackup(): BackupData {
        return BackupData(
            transactions = transactionDao.getAllTransactionsSync(),
            loans = loanDao.getAllLoansSync(),
            repayments = loanRepaymentDao.getAllRepaymentsSync(),
            budgets = budgetDao.getAllBudgetsSync(),
            categories = categoryDao.getAllCategoriesSync()
        )
    }

    suspend fun clearAll() {
        transactionDao.clearAll()
        loanDao.clearAll()
        loanRepaymentDao.clearAll()
        budgetDao.clearAll()
    }

    suspend fun restoreAll(backupData: BackupData) {
        database.withTransaction {
            clearAll()
            categoryDao.clearAll()
            if (backupData.categories.isNotEmpty()) {
                categoryDao.insertAll(backupData.categories)
            } else {
                seedCategoriesIfEmpty()
            }
            if (backupData.transactions.isNotEmpty()) {
                transactionDao.insertAll(backupData.transactions)
            }
            if (backupData.loans.isNotEmpty()) {
                loanDao.insertAll(backupData.loans)
            }
            if (backupData.repayments.isNotEmpty()) {
                loanRepaymentDao.insertAll(backupData.repayments)
            }
            if (backupData.budgets.isNotEmpty()) {
                budgetDao.insertAll(backupData.budgets)
            }
        }
    }

    suspend fun restoreMerged(backupData: BackupData) {
        database.withTransaction {
            if (backupData.categories.isNotEmpty()) {
                val existing = categoryDao.getAllCategoriesSync().map { it.name }.toSet()
                val newCategories = backupData.categories.filter { !existing.contains(it.name) }
                if (newCategories.isNotEmpty()) {
                    categoryDao.insertAll(newCategories)
                }
            }

            if (backupData.transactions.isNotEmpty()) {
                val existingTx = transactionDao.getAllTransactionsSync()
                val existingSet = existingTx.map { "${it.title.trim().lowercase()}_${it.amount}_${it.timestamp}" }.toSet()
                val toInsert = backupData.transactions
                    .filter { !existingSet.contains("${it.title.trim().lowercase()}_${it.amount}_${it.timestamp}") }
                    .map { it.copy(id = 0) }
                if (toInsert.isNotEmpty()) {
                    transactionDao.insertAll(toInsert)
                }
            }

            mergeBudgets(backupData.budgets)
            mergeLoansWithRepayments(backupData.loans, backupData.repayments)
        }
    }

    private suspend fun mergeBudgets(backupBudgets: List<BudgetEntity>) {
        if (backupBudgets.isEmpty()) return
        // Rule: a budget that already exists on this device wins, because it reflects an edit
        // made after the backup was taken. The backup only fills (category, month) slots with
        // no budget.
        val existingKeys = budgetDao.getAllBudgetsSync().map { it.category to it.month }.toSet()
        val missing = backupBudgets.filter { !existingKeys.contains(it.category to it.month) }
        if (missing.isNotEmpty()) {
            budgetDao.insertAll(missing)
        }
    }

    private suspend fun mergeLoansWithRepayments(
        backupLoans: List<LoanEntity>,
        backupRepayments: List<LoanRepaymentEntity>
    ) {
        if (backupLoans.isEmpty() && backupRepayments.isEmpty()) return

        val existingLoans = loanDao.getAllLoansSync()
        val existingByKey = existingLoans.associateBy { loanMatchKey(it) }
        val existingById = existingLoans.associateBy { it.id }
        val takenLoanIds = existingLoans.mapTo(HashSet<Long>()) { it.id }

        val backupLoanIdToLoanId = HashMap<Long, Long>(backupLoans.size)
        backupLoans.forEach { backupLoan ->
            val matched = existingByKey[loanMatchKey(backupLoan)]
                ?: if (backupLoan.id != 0L) existingById[backupLoan.id] else null
            val targetLoanId = if (matched != null) {
                matched.id
            } else {
                // Keep the backup id when it is free so a later merge can match on it, but
                // never insert an id that another row already owns (REPLACE would drop it).
                val idTaken = backupLoan.id != 0L && takenLoanIds.contains(backupLoan.id)
                val newId = loanDao.insertLoan(if (idTaken) backupLoan.copy(id = 0) else backupLoan)
                takenLoanIds.add(newId)
                newId
            }
            if (backupLoan.id != 0L) {
                backupLoanIdToLoanId[backupLoan.id] = targetLoanId
            }
        }

        if (backupRepayments.isEmpty()) return

        val existingRepaymentKeys = loanRepaymentDao.getAllRepaymentsSync()
            .mapTo(HashSet<String>()) { repaymentMatchKey(it.loanId, it.amount, it.date, it.note) }

        val newRepayments = ArrayList<LoanRepaymentEntity>(backupRepayments.size)
        backupRepayments.forEach { repayment ->
            val targetLoanId = backupLoanIdToLoanId[repayment.loanId] ?: return@forEach
            val key = repaymentMatchKey(targetLoanId, repayment.amount, repayment.date, repayment.note)
            if (existingRepaymentKeys.contains(key)) return@forEach
            existingRepaymentKeys.add(key)
            newRepayments.add(repayment.copy(id = 0, loanId = targetLoanId))
        }
        if (newRepayments.isNotEmpty()) {
            loanRepaymentDao.insertAll(newRepayments)
        }
    }

    private fun loanMatchKey(loan: LoanEntity): String =
        "${loan.personName.trim().lowercase()}_${loan.amount}_${loan.date}"

    private fun repaymentMatchKey(loanId: Long, amount: Double, date: Long, note: String): String =
        "${loanId}_${amount}_${date}_${note.trim().lowercase()}"

    suspend fun seedInitialDataIfEmpty() {
        seedCategoriesIfEmpty()
        seedBudgetsIfEmpty()
        val count = transactionDao.getCount()
        if (count == 0) {
            seedSampleData()
        }
    }

    suspend fun seedCategoriesIfEmpty() {
        if (categoryDao.getCount() == 0) {
            val defaultEntities = DefaultCategories.map {
                CategoryEntity(name = it, isCustom = false)
            }
            categoryDao.insertAll(defaultEntities)
        }
    }

    suspend fun seedBudgetsIfEmpty() {
        if (budgetDao.getCount() == 0) {
            val month = DateUtils.currentMonthKey()
            val defaultBudgets = listOf(
                BudgetEntity("Food", month, 300.0),
                BudgetEntity("Bills", month, 1400.0),
                BudgetEntity("Transport", month, 80.0),
                BudgetEntity("Shopping", month, 150.0),
                BudgetEntity("Health", month, 100.0)
            )
            budgetDao.insertAll(defaultBudgets)
        }
    }

    suspend fun seedSampleData() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        val txList = mutableListOf<TransactionEntity>()

        // 1. Current Month Transactions
        txList.addAll(
            listOf(
                TransactionEntity(
                    title = "Monthly Salary",
                    amount = 4200.0,
                    type = "INCOME",
                    category = "Salary",
                    timestamp = now - (oneDay * 2),
                    note = "Tech Corp Payroll"
                ),
                TransactionEntity(
                    title = "Design Project Bonus",
                    amount = 850.0,
                    type = "INCOME",
                    category = "Salary",
                    timestamp = now - (oneDay * 5),
                    note = "Bonus payout"
                ),
                TransactionEntity(
                    title = "Apartment Rent & Utilities",
                    amount = 1250.0,
                    type = "EXPENSE",
                    category = "Bills",
                    timestamp = now - (oneDay * 1),
                    note = "Monthly rent transfer"
                ),
                TransactionEntity(
                    title = "Fresh Mart Groceries",
                    amount = 184.50,
                    type = "EXPENSE",
                    category = "Food",
                    timestamp = now - (oneDay * 3),
                    note = "Weekly groceries"
                ),
                TransactionEntity(
                    title = "Electric & Internet Bill",
                    amount = 125.00,
                    type = "EXPENSE",
                    category = "Bills",
                    timestamp = now - (oneDay * 4),
                    note = "Electricity & WiFi"
                ),
                TransactionEntity(
                    title = "Metro Transit Pass",
                    amount = 65.00,
                    type = "EXPENSE",
                    category = "Transport",
                    timestamp = now - (oneDay * 6),
                    note = "Monthly commuter pass"
                ),
                TransactionEntity(
                    title = "Pharmacy & Vitamins",
                    amount = 45.00,
                    type = "EXPENSE",
                    category = "Health",
                    timestamp = now - (oneDay * 7),
                    note = "Healthcare supplies"
                ),
                TransactionEntity(
                    title = "Casual Weekend Dining",
                    amount = 48.60,
                    type = "EXPENSE",
                    category = "Food",
                    timestamp = now - (1000 * 60 * 150),
                    note = "Dinner with friends"
                )
            )
        )

        // 2. Past 5 Months Historical Transactions (for the 6-month bar chart)
        for (monthOffset in 1..5) {
            val pastCal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -monthOffset)
                set(Calendar.DAY_OF_MONTH, 10)
                set(Calendar.HOUR_OF_DAY, 12)
            }
            val monthTime = pastCal.timeInMillis

            val baseSalary = 4200.0 + (monthOffset * 50)
            val baseExpenses = 1500.0 + (monthOffset * 70)

            txList.add(
                TransactionEntity(
                    title = "Salary Paycheck",
                    amount = baseSalary,
                    type = "INCOME",
                    category = "Salary",
                    timestamp = monthTime,
                    note = "Historical payroll"
                )
            )
            txList.add(
                TransactionEntity(
                    title = "Monthly Living Expenses",
                    amount = baseExpenses,
                    type = "EXPENSE",
                    category = "Bills",
                    timestamp = monthTime + (oneDay * 2),
                    note = "Historical living costs"
                )
            )
            txList.add(
                TransactionEntity(
                    title = "Groceries & Dining",
                    amount = 260.0 + (monthOffset * 20),
                    type = "EXPENSE",
                    category = "Food",
                    timestamp = monthTime + (oneDay * 5),
                    note = "Monthly food expenses"
                )
            )
        }

        transactionDao.insertAll(txList)

        if (loanDao.getCount() == 0) {
            val loan1Id = loanDao.insertLoan(
                LoanEntity(
                    personName = "Alex Smith",
                    amount = 250.0,
                    type = "LENT",
                    date = now - (oneDay * 5),
                    dueDate = now + (oneDay * 7),
                    note = "Weekend rental car share",
                    isSettled = false,
                    createdAt = now - (oneDay * 5)
                )
            )
            loanRepaymentDao.insertRepayment(
                LoanRepaymentEntity(
                    loanId = loan1Id,
                    amount = 50.0,
                    date = now - (oneDay * 2),
                    note = "First installment transfer"
                )
            )

            val loan2Id = loanDao.insertLoan(
                LoanEntity(
                    personName = "Marcus Vance",
                    amount = 500.0,
                    type = "BORROWED",
                    date = now - (oneDay * 14),
                    dueDate = now - (oneDay * 2), // Overdue!
                    note = "Equipment purchase bridge",
                    isSettled = false,
                    createdAt = now - (oneDay * 14)
                )
            )
            loanRepaymentDao.insertRepayment(
                LoanRepaymentEntity(
                    loanId = loan2Id,
                    amount = 100.0,
                    date = now - (oneDay * 7),
                    note = "Part payment"
                )
            )

            loanDao.insertLoan(
                LoanEntity(
                    personName = "Alex Smith",
                    amount = 60.0,
                    type = "BORROWED",
                    date = now - (oneDay * 3),
                    dueDate = now + (oneDay * 10),
                    note = "Concert tickets purchase",
                    isSettled = false,
                    createdAt = now - (oneDay * 3)
                )
            )

            val loan4Id = loanDao.insertLoan(
                LoanEntity(
                    personName = "Sarah Chen",
                    amount = 120.0,
                    type = "LENT",
                    date = now - (oneDay * 10),
                    dueDate = now - oneDay,
                    note = "Dinner bill payback",
                    isSettled = true,
                    createdAt = now - (oneDay * 10)
                )
            )
            loanRepaymentDao.insertRepayment(
                LoanRepaymentEntity(
                    loanId = loan4Id,
                    amount = 120.0,
                    date = now - (oneDay * 1),
                    note = "Full repayment via bank transfer"
                )
            )
        }
    }
}
