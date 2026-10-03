package com.yourname.mymoney.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.mymoney.R
import com.yourname.mymoney.data.AutoBackupInterval
import com.yourname.mymoney.data.AutoBackupWorker
import com.yourname.mymoney.data.BackupData
import com.yourname.mymoney.data.BudgetEntity
import com.yourname.mymoney.data.CategoryBudgetStatus
import com.yourname.mymoney.data.CategoryEntity
import com.yourname.mymoney.data.DailyBackupWorker
import com.yourname.mymoney.data.DataBackupService
import com.yourname.mymoney.data.DriveAuthOutcome
import com.yourname.mymoney.data.DriveRestClient
import com.yourname.mymoney.data.GoogleDriveBackupManager
import com.yourname.mymoney.data.LoanEntity
import com.yourname.mymoney.data.LoanRepaymentEntity
import com.yourname.mymoney.data.LoanWithDetails
import com.yourname.mymoney.data.MonthlyCashflowBar
import com.yourname.mymoney.data.MyMoneyDatabase
import com.yourname.mymoney.data.MyMoneyRepository
import com.yourname.mymoney.data.PersonLoanSummary
import com.yourname.mymoney.data.RecurringTransactionsWorker
import com.yourname.mymoney.data.TransactionEntity
import com.yourname.mymoney.ui.components.AppCurrency
import com.yourname.mymoney.ui.components.CurrencyManager
import com.yourname.mymoney.ui.components.SupportedCurrencies
import com.yourname.mymoney.util.AppReleaseInfo
import com.yourname.mymoney.util.DateUtils
import com.yourname.mymoney.util.DefaultCategories
import com.yourname.mymoney.util.NetworkConnectivityObserver
import com.yourname.mymoney.util.UpdateCheckResult
import com.yourname.mymoney.util.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

data class SelectedMonth(
    val year: Int,
    val month: Int // 0-indexed (0 = Jan, 11 = Dec)
) {
    val displayName: String
        get() {
            val cal = DateUtils.monthStart(year, month)
            return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        }
}

data class FinanceOverview(
    val currentBalance: Double = 0.0,
    val selectedMonthDisplayName: String = "",
    val isCurrentMonthSelected: Boolean = true,
    val selectedYear: Int = 2026,
    val selectedMonth: Int = 9,
    val totalIncomeThisMonth: Double = 0.0,
    val totalExpenseThisMonth: Double = 0.0,
    val netSavingsThisMonth: Double = 0.0,
    val savingsRate: Int = 0,
    // Today's stats
    val todayIncome: Double = 0.0,
    val todayExpense: Double = 0.0,
    val todayNet: Double = 0.0,
    val todayTransactionCount: Int = 0,
    // Loans
    val totalLentActive: Double = 0.0,
    val totalBorrowedActive: Double = 0.0
)

data class CategorySpend(
    val category: String,
    val amount: Double,
    val percentage: Float
)

class MyMoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MyMoneyRepository

    private val initialCal = Calendar.getInstance()
    private val _selectedMonth = MutableStateFlow(
        SelectedMonth(initialCal.get(Calendar.YEAR), initialCal.get(Calendar.MONTH))
    )
    val selectedMonth: StateFlow<SelectedMonth> = _selectedMonth.asStateFlow()

    // Dark Mode: "SYSTEM", "LIGHT", "DARK"
    private val _darkThemeMode = MutableStateFlow("SYSTEM")
    val darkThemeMode: StateFlow<String> = _darkThemeMode.asStateFlow()

    // Currency
    private val _selectedCurrency = MutableStateFlow(CurrencyManager.currentCurrency)
    val selectedCurrency: StateFlow<AppCurrency> = _selectedCurrency.asStateFlow()

    // Network Connectivity & Offline Simulation
    private val connectivityObserver = NetworkConnectivityObserver(application)
    private val _isSimulatedOffline = MutableStateFlow(false)
    val isSimulatedOffline: StateFlow<Boolean> = _isSimulatedOffline.asStateFlow()

    val isOnline: StateFlow<Boolean> = combine(
        connectivityObserver.isOnline,
        _isSimulatedOffline
    ) { realOnline, simulatedOffline ->
        if (simulatedOffline) false else realOnline
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setSimulatedOffline(simulated: Boolean) {
        _isSimulatedOffline.value = simulated
    }

    // Google Drive Backup Management
    private val driveBackupManager = GoogleDriveBackupManager(application)
    private val _lastBackupTimeFormatted = MutableStateFlow(driveBackupManager.getFormattedLastBackup())
    val lastBackupTimeFormatted: StateFlow<String> = _lastBackupTimeFormatted.asStateFlow()

    private val _lastBackupSizeFormatted = MutableStateFlow(driveBackupManager.getFormattedBackupSize())
    val lastBackupSizeFormatted: StateFlow<String> = _lastBackupSizeFormatted.asStateFlow()

    private val _lastBackupError = MutableStateFlow(driveBackupManager.lastBackupError)
    val lastBackupError: StateFlow<String?> = _lastBackupError.asStateFlow()

    private val _autoBackupInterval = MutableStateFlow(driveBackupManager.autoBackupInterval)
    val autoBackupInterval: StateFlow<AutoBackupInterval> = _autoBackupInterval.asStateFlow()

    private val _isAutoBackupEnabled = MutableStateFlow(driveBackupManager.isAutoBackupEnabled)
    val isAutoBackupEnabled: StateFlow<Boolean> = _isAutoBackupEnabled.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow(driveBackupManager.accountEmail)
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    // The first Drive consent returns a PendingIntent that must be launched from the Activity.
    private val _pendingDriveAuthorization = MutableStateFlow<PendingIntent?>(null)
    val pendingDriveAuthorization: StateFlow<PendingIntent?> = _pendingDriveAuthorization.asStateFlow()
    private var pendingAuthOutcomeHandler: ((DriveAuthOutcome) -> Unit)? = null

    // Gemini AI API Key state
    private val _isApiKeyConfigured = MutableStateFlow(com.yourname.mymoney.data.SecureApiKeyStorage.hasApiKey(application))
    val isApiKeyConfigured: StateFlow<Boolean> = _isApiKeyConfigured.asStateFlow()

    // App Update Checker
    private val updateManager = UpdateManager(application)
    private val _isAutoUpdateCheckEnabled = MutableStateFlow(updateManager.isAutoCheckEnabled)
    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = _isAutoUpdateCheckEnabled.asStateFlow()

    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AppReleaseInfo?>(null)
    val availableUpdate: StateFlow<AppReleaseInfo?> = _availableUpdate.asStateFlow()

    fun refreshApiKeyConfigured() {
        _isApiKeyConfigured.value = com.yourname.mymoney.data.SecureApiKeyStorage.hasApiKey(getApplication())
    }

    fun saveApiKey(apiKey: String): Boolean {
        val success = com.yourname.mymoney.data.SecureApiKeyStorage.saveApiKey(getApplication(), apiKey)
        refreshApiKeyConfigured()
        return success
    }

    fun removeApiKey(): Boolean {
        val success = com.yourname.mymoney.data.SecureApiKeyStorage.clearApiKey(getApplication())
        refreshApiKeyConfigured()
        return success
    }

    init {
        val db = MyMoneyDatabase.getDatabase(application)
        repository = MyMoneyRepository(db)
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
        if (driveBackupManager.isAutoBackupEnabled) {
            DailyBackupWorker.schedule(application)
        }
        RecurringTransactionsWorker.schedule(application)
        // Catch up any occurrences missed while the phone was off or the app was closed.
        RecurringTransactionsWorker.triggerCheck(application)
    }

    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val rawLoans: StateFlow<List<LoanEntity>> = repository.allLoans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val repayments: StateFlow<List<LoanRepaymentEntity>> = repository.allRepayments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val budgets: StateFlow<List<BudgetEntity>> = repository.allBudgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val rawCategories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val loansWithDetails: StateFlow<List<LoanWithDetails>> = combine(rawLoans, repayments) { loansList, repaymentList ->
        val repaymentsByLoanId = repaymentList.groupBy { it.loanId }
        loansList.map { loan ->
            val loanRepayments = repaymentsByLoanId[loan.id] ?: emptyList()
            LoanWithDetails(loan, loanRepayments)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val personLoanSummaries: StateFlow<List<PersonLoanSummary>> = loansWithDetails.map { allLoans ->
        allLoans.groupBy { it.loan.personName.trim() }
            .map { (person, personLoans) ->
                var lentRemaining = 0.0
                var borrowedRemaining = 0.0
                var activeCount = 0
                var settledCount = 0

                for (lwd in personLoans) {
                    if (lwd.isSettled) {
                        settledCount++
                    } else {
                        activeCount++
                        if (lwd.loan.type == "LENT") {
                            lentRemaining += lwd.remainingBalance
                        } else {
                            borrowedRemaining += lwd.remainingBalance
                        }
                    }
                }

                val net = lentRemaining - borrowedRemaining
                PersonLoanSummary(
                    personName = person,
                    totalLentRemaining = lentRemaining,
                    totalBorrowedRemaining = borrowedRemaining,
                    netBalance = net,
                    activeLoansCount = activeCount,
                    settledLoansCount = settledCount,
                    loans = personLoans
                )
            }.sortedByDescending { Math.abs(it.netBalance) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<String>> = repository.allCategories
        .map { list ->
            if (list.isEmpty()) {
                DefaultCategories
            } else {
                val catNames = list.map { it.name }.toMutableList()
                DefaultCategories.forEach { def ->
                    if (!catNames.contains(def)) {
                        catNames.add(def)
                    }
                }
                catNames
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DefaultCategories
        )

    val overview: StateFlow<FinanceOverview> = combine(
        transactions,
        loansWithDetails,
        _selectedMonth
    ) { txList, loansList, selMonth ->
        val startOfMonth = DateUtils.monthStart(selMonth.year, selMonth.month).timeInMillis
        val endOfMonth = DateUtils.monthEnd(selMonth.year, selMonth.month).timeInMillis

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val currentRealCal = Calendar.getInstance()
        val isCurrentMonth = selMonth.year == currentRealCal.get(Calendar.YEAR) &&
                selMonth.month == currentRealCal.get(Calendar.MONTH)

        var allTimeIncome = 0.0
        var allTimeExpense = 0.0
        var monthIncome = 0.0
        var monthExpense = 0.0

        var todayIncome = 0.0
        var todayExpense = 0.0
        var todayTxCount = 0

        for (tx in txList) {
            val isIncome = tx.type.equals("INCOME", ignoreCase = true)
            if (isIncome) {
                allTimeIncome += tx.amount
                if (tx.timestamp in startOfMonth until endOfMonth) {
                    monthIncome += tx.amount
                }
                if (tx.timestamp in startOfToday until endOfToday) {
                    todayIncome += tx.amount
                    todayTxCount++
                }
            } else {
                allTimeExpense += tx.amount
                if (tx.timestamp in startOfMonth until endOfMonth) {
                    monthExpense += tx.amount
                }
                if (tx.timestamp in startOfToday until endOfToday) {
                    todayExpense += tx.amount
                    todayTxCount++
                }
            }
        }

        val balance = allTimeIncome - allTimeExpense
        val netSavings = monthIncome - monthExpense
        val savingsRate = if (monthIncome > 0) {
            ((netSavings / monthIncome) * 100).coerceIn(0.0, 100.0).toInt()
        } else 0

        val lentActive = loansList.filter { it.loan.type == "LENT" && !it.isSettled }
            .sumOf { it.remainingBalance }
        val borrowedActive = loansList.filter { it.loan.type == "BORROWED" && !it.isSettled }
            .sumOf { it.remainingBalance }

        FinanceOverview(
            currentBalance = balance,
            selectedMonthDisplayName = selMonth.displayName,
            isCurrentMonthSelected = isCurrentMonth,
            selectedYear = selMonth.year,
            selectedMonth = selMonth.month,
            totalIncomeThisMonth = monthIncome,
            totalExpenseThisMonth = monthExpense,
            netSavingsThisMonth = netSavings,
            savingsRate = savingsRate,
            todayIncome = todayIncome,
            todayExpense = todayExpense,
            todayNet = todayIncome - todayExpense,
            todayTransactionCount = todayTxCount,
            totalLentActive = lentActive,
            totalBorrowedActive = borrowedActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceOverview()
    )

    val categoryBudgetStatuses: StateFlow<List<CategoryBudgetStatus>> = combine(
        transactions,
        budgets,
        _selectedMonth
    ) { txList, budgetList, selMonth ->
        val startOfMonth = DateUtils.monthStart(selMonth.year, selMonth.month).timeInMillis
        val endOfMonth = DateUtils.monthEnd(selMonth.year, selMonth.month).timeInMillis

        val expensesThisMonth = txList.filter {
            it.type.equals("EXPENSE", ignoreCase = true) && it.timestamp in startOfMonth until endOfMonth
        }

        val expensesByCategory = expensesThisMonth.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val monthKey = DateUtils.monthKey(selMonth.year, selMonth.month)
        budgetList.filter { it.month == monthKey }.map { b ->
            val spent = expensesByCategory[b.category] ?: 0.0
            val fraction = if (b.monthlyLimit > 0) (spent / b.monthlyLimit).toFloat() else 0f
            val isExceeded = spent > b.monthlyLimit
            val isWarning = !isExceeded && fraction >= 0.80f

            CategoryBudgetStatus(
                category = b.category,
                monthlyLimit = b.monthlyLimit,
                spent = spent,
                percentage = fraction,
                isWarning = isWarning,
                isExceeded = isExceeded,
                remaining = b.monthlyLimit - spent
            )
        }.sortedWith(
            compareByDescending<CategoryBudgetStatus> { it.isExceeded }
                .thenByDescending { it.isWarning }
                .thenByDescending { it.spent }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val last6MonthsCashflow: StateFlow<List<MonthlyCashflowBar>> = combine(
        transactions,
        _selectedMonth
    ) { txList, selMonth ->
        val bars = mutableListOf<MonthlyCashflowBar>()

        for (i in 5 downTo 0) {
            val cal = DateUtils.monthStart(selMonth.year, selMonth.month).apply { add(Calendar.MONTH, -i) }
            val startOfM = cal.timeInMillis
            val endOfM = DateUtils.monthEnd(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)).timeInMillis

            val monthLabel = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
            val fullDisplay = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(cal.time)

            var inc = 0.0
            var exp = 0.0
            for (tx in txList) {
                if (tx.timestamp in startOfM until endOfM) {
                    if (tx.type.equals("INCOME", ignoreCase = true)) {
                        inc += tx.amount
                    } else {
                        exp += tx.amount
                    }
                }
            }

            bars.add(
                MonthlyCashflowBar(
                    monthLabel = monthLabel,
                    yearMonthDisplay = fullDisplay,
                    income = inc,
                    expense = exp,
                    net = inc - exp
                )
            )
        }
        bars
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setDarkThemeMode(mode: String) {
        _darkThemeMode.value = mode
    }

    fun setCurrency(currency: AppCurrency) {
        CurrencyManager.currentCurrency = currency
        _selectedCurrency.value = currency
    }

    fun selectPreviousMonth() {
        val cur = _selectedMonth.value
        val cal = DateUtils.monthStart(cur.year, cur.month).apply { add(Calendar.MONTH, -1) }
        _selectedMonth.value = SelectedMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun selectNextMonth() {
        val cur = _selectedMonth.value
        val cal = DateUtils.monthStart(cur.year, cur.month).apply { add(Calendar.MONTH, 1) }
        _selectedMonth.value = SelectedMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun resetToCurrentMonth() {
        val now = Calendar.getInstance()
        _selectedMonth.value = SelectedMonth(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    }

    fun selectMonth(year: Int, month: Int) {
        _selectedMonth.value = SelectedMonth(year, month)
    }

    fun addTransaction(
        title: String,
        amount: Double,
        type: String,
        category: String,
        note: String = "",
        timestamp: Long = System.currentTimeMillis(),
        recurrence: String = "NONE"
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                TransactionEntity(
                    title = title.trim(),
                    amount = amount,
                    type = type,
                    category = category.trim(),
                    note = note.trim(),
                    timestamp = timestamp,
                    recurrence = recurrence
                )
            )
            if (recurrence != "NONE") {
                RecurringTransactionsWorker.triggerCheck(getApplication<Application>())
            }
        }
    }

    fun updateTransaction(
        original: TransactionEntity,
        title: String,
        amount: Double,
        type: String,
        category: String,
        note: String = "",
        timestamp: Long,
        recurrence: String = "NONE"
    ) {
        viewModelScope.launch {
            // copy() from the original keeps id, lastGeneratedAt and recurrenceActive
            // so editing a rule never resets its generation progress or pause state.
            repository.updateTransaction(
                original.copy(
                    title = title.trim(),
                    amount = amount,
                    type = type,
                    category = category.trim(),
                    note = note.trim(),
                    timestamp = timestamp,
                    recurrence = recurrence
                )
            )
            if (recurrence != "NONE") {
                RecurringTransactionsWorker.triggerCheck(getApplication<Application>())
            }
        }
    }

    /** Pauses or resumes a recurring rule without touching its generated transactions. */
    fun setRecurrencePaused(tx: TransactionEntity, paused: Boolean): TransactionEntity {
        val updated = tx.copy(recurrenceActive = !paused)
        viewModelScope.launch {
            repository.updateTransaction(updated)
        }
        return updated
    }

    /** Deletes the recurring rule but keeps the rule's own transaction and all copies. */
    fun stopRecurrence(tx: TransactionEntity): TransactionEntity {
        val updated = tx.copy(recurrence = "NONE", lastGeneratedAt = 0L, recurrenceActive = true)
        viewModelScope.launch {
            repository.updateTransaction(updated)
        }
        return updated
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
        }
    }

    fun addCustomCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        if (trimmed.isNotBlank()) {
            viewModelScope.launch {
                repository.insertCategory(trimmed, isCustom = true)
            }
        }
    }

    fun setBudget(category: String, limit: Double) {
        val selMonth = _selectedMonth.value
        val monthKey = DateUtils.monthKey(selMonth.year, selMonth.month)
        viewModelScope.launch {
            repository.setBudget(category.trim(), monthKey, limit)
        }
    }

    fun deleteBudget(category: String) {
        val selMonth = _selectedMonth.value
        val monthKey = DateUtils.monthKey(selMonth.year, selMonth.month)
        viewModelScope.launch {
            repository.deleteBudget(category.trim(), monthKey)
        }
    }

    fun addLoan(
        personName: String,
        amount: Double,
        type: String,
        date: Long,
        dueDate: Long,
        note: String
    ) {
        viewModelScope.launch {
            repository.insertLoan(
                LoanEntity(
                    personName = personName.trim(),
                    amount = amount,
                    type = type,
                    date = date,
                    dueDate = dueDate,
                    note = note.trim(),
                    isSettled = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun addRepayment(
        loan: LoanEntity,
        amount: Double,
        date: Long = System.currentTimeMillis(),
        note: String = ""
    ) {
        viewModelScope.launch {
            repository.addRepayment(
                loanId = loan.id,
                amount = amount,
                date = date,
                note = note.trim()
            )
        }
    }

    fun toggleLoanSettled(loan: LoanEntity) {
        viewModelScope.launch {
            repository.updateLoan(loan.copy(isSettled = !loan.isSettled))
        }
    }

    fun deleteLoan(loan: LoanEntity) {
        viewModelScope.launch {
            repository.deleteLoan(loan)
        }
    }

    fun deleteRepayment(repayment: LoanRepaymentEntity) {
        viewModelScope.launch {
            repository.deleteRepayment(repayment)
        }
    }

    fun restoreBackupReplace(backupData: BackupData, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(restoreResult {
                repository.restoreAll(backupData)
                applyRestoredSettings(backupData)
            })
        }
    }

    fun restoreBackupMerge(backupData: BackupData, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(restoreResult {
                repository.restoreMerged(backupData)
                applyRestoredSettings(backupData)
            })
        }
    }

    private fun applyRestoredSettings(backupData: BackupData) {
        val settings = backupData.settings
        if (settings.isNotEmpty()) {
            settings["currencyCode"]?.let { code ->
                SupportedCurrencies.find { it.code.equals(code, ignoreCase = true) }?.let { curr ->
                    setCurrency(curr)
                }
            }
            settings["darkThemeMode"]?.let { mode ->
                if (mode in listOf("SYSTEM", "LIGHT", "DARK")) {
                    setDarkThemeMode(mode)
                }
            }
        }
    }

    private suspend fun restoreResult(block: suspend () -> Unit): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                Exception(getApplication<Application>().getString(R.string.vm_restore_failed, e.message ?: getApplication<Application>().getString(R.string.vm_unexpected_error)))
            )
        }
    }

    fun setAutoBackupInterval(interval: AutoBackupInterval) {
        driveBackupManager.autoBackupInterval = interval
        _autoBackupInterval.value = interval
        _isAutoBackupEnabled.value = interval != AutoBackupInterval.OFF
        AutoBackupWorker.schedule(getApplication<Application>(), interval)
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        setAutoBackupInterval(if (enabled) AutoBackupInterval.DAILY else AutoBackupInterval.OFF)
    }

    fun signInToGoogle(onResult: (Result<String>) -> Unit) {
        if (_pendingDriveAuthorization.value != null) {
            onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_signin_in_progress))))
            return
        }
        viewModelScope.launch {
            val signedIn = driveBackupManager.signInWithGoogle()
            val email = signedIn.getOrElse { error ->
                _lastBackupError.value = error.message
                onResult(Result.failure(error))
                return@launch
            }
            _googleAccountEmail.value = email
            _lastBackupError.value = null
            requestDriveAuthorization { outcome ->
                when (outcome) {
                    is DriveAuthOutcome.Granted ->
                        onResult(Result.success(getApplication<Application>().getString(R.string.vm_signed_in, email)))
                    is DriveAuthOutcome.Failed -> {
                        _lastBackupError.value = outcome.message
                        onResult(Result.failure(Exception(outcome.message)))
                    }
                    is DriveAuthOutcome.NeedsResolution ->
                        onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_auth_pending))))
                }
            }
        }
    }

    fun disconnectGoogleAccount() {
        val handler = takePendingAuthorization()
        driveBackupManager.signOut()
        AutoBackupWorker.cancel(getApplication<Application>())
        _googleAccountEmail.value = null
        _lastBackupTimeFormatted.value = driveBackupManager.getFormattedLastBackup()
        _lastBackupSizeFormatted.value = driveBackupManager.getFormattedBackupSize()
        _lastBackupError.value = null
        _autoBackupInterval.value = AutoBackupInterval.OFF
        _isAutoBackupEnabled.value = false
        handler?.invoke(DriveAuthOutcome.Failed(getApplication<Application>().getString(R.string.vm_signed_out)))
    }

    private fun requestDriveAuthorization(onOutcome: (DriveAuthOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = driveBackupManager.authorizeDrive()
            if (outcome is DriveAuthOutcome.NeedsResolution) {
                pendingAuthOutcomeHandler = onOutcome
                _pendingDriveAuthorization.value = outcome.pendingIntent
            } else {
                onOutcome(outcome)
            }
        }
    }

    fun onDriveAuthorizationResult(resultCode: Int, data: Intent?) {
        val handler = takePendingAuthorization() ?: return
        handler(driveBackupManager.completeAuthorization(resultCode, data))
    }

    fun onDriveAuthorizationLaunchFailed(message: String) {
        val handler = takePendingAuthorization() ?: return
        handler(DriveAuthOutcome.Failed(message))
    }

    private fun takePendingAuthorization(): ((DriveAuthOutcome) -> Unit)? {
        if (_pendingDriveAuthorization.value == null) return null
        _pendingDriveAuthorization.value = null
        return pendingAuthOutcomeHandler?.also { pendingAuthOutcomeHandler = null }
    }

    fun backupToGoogleDrive(onResult: (Result<String>) -> Unit) {
        if (_pendingDriveAuthorization.value != null) {
            onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_auth_in_progress))))
            return
        }
        viewModelScope.launch {
            if (driveBackupManager.accountEmail.isNullOrBlank()) {
                onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_signin_required_backup))))
                return@launch
            }
            if (!isOnline.value) {
                onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_no_internet_backup))))
                return@launch
            }
            requestDriveAuthorization { outcome ->
                when (outcome) {
                    is DriveAuthOutcome.Granted -> viewModelScope.launch {
                        try {
                            val snapshot = repository.snapshotForBackup()
                            val existingFiles = DriveRestClient.listBackupFiles(outcome.accessToken)
                            if (!snapshot.hasRecords() && existingFiles.isNotEmpty()) {
                                onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_backup_skipped_empty_cloud))))
                                return@launch
                            }

                            val json = DataBackupService.exportToJson(
                                snapshot.transactions,
                                snapshot.loans,
                                snapshot.repayments,
                                snapshot.budgets,
                                snapshot.categories,
                                settings = mapOf(
                                    "currencyCode" to CurrencyManager.currentCurrency.code,
                                    "darkThemeMode" to _darkThemeMode.value
                                )
                            )

                            driveBackupManager.uploadBackup(outcome.accessToken, json)
                                .onSuccess {
                                    _lastBackupTimeFormatted.value = driveBackupManager.getFormattedLastBackup()
                                    _lastBackupSizeFormatted.value = driveBackupManager.getFormattedBackupSize()
                                    _lastBackupError.value = null
                                    onResult(Result.success(getApplication<Application>().getString(R.string.vm_gdrive_backup_saved)))
                                }
                                .onFailure { err ->
                                    _lastBackupError.value = err.message
                                    onResult(Result.failure(Exception(err.message ?: getApplication<Application>().getString(R.string.err_gdrive_backup_failed))))
                                }
                        } catch (e: Exception) {
                            _lastBackupError.value = e.message
                            onResult(Result.failure(Exception(e.message ?: getApplication<Application>().getString(R.string.err_gdrive_backup_failed))))
                        }
                    }
                    is DriveAuthOutcome.Failed -> {
                        _lastBackupError.value = outcome.message
                        onResult(Result.failure(Exception(outcome.message)))
                    }
                    is DriveAuthOutcome.NeedsResolution ->
                        onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_auth_pending))))
                }
            }
        }
    }

    fun fetchDriveBackup(onResult: (Result<BackupData>) -> Unit) {
        if (_pendingDriveAuthorization.value != null) {
            onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_auth_in_progress))))
            return
        }
        viewModelScope.launch {
            if (driveBackupManager.accountEmail.isNullOrBlank()) {
                onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_signin_required_restore))))
                return@launch
            }
            if (!isOnline.value) {
                onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_no_internet_restore))))
                return@launch
            }
            requestDriveAuthorization { outcome ->
                when (outcome) {
                    is DriveAuthOutcome.Granted -> viewModelScope.launch {
                        driveBackupManager.downloadBackup(outcome.accessToken)
                            .onSuccess { (json, fileInfo) ->
                                _lastBackupTimeFormatted.value = driveBackupManager.getFormattedLastBackup()
                                _lastBackupSizeFormatted.value = driveBackupManager.getFormattedBackupSize()
                                _lastBackupError.value = null
                                try {
                                    val parsed = DataBackupService.parseBackupJson(json)
                                    val backupData = if (parsed.timestamp > 0L) parsed else parsed.copy(timestamp = fileInfo.modifiedTime)
                                    onResult(Result.success(backupData))
                                } catch (e: Exception) {
                                    onResult(
                                        Result.failure(
                                            Exception(e.message ?: getApplication<Application>().getString(R.string.vm_gdrive_invalid_backup))
                                        )
                                    )
                                }
                            }
                            .onFailure { err ->
                                _lastBackupError.value = err.message
                                onResult(Result.failure(Exception(err.message ?: getApplication<Application>().getString(R.string.err_gdrive_restore_failed))))
                            }
                    }
                    is DriveAuthOutcome.Failed -> {
                        _lastBackupError.value = outcome.message
                        onResult(Result.failure(Exception(outcome.message)))
                    }
                    is DriveAuthOutcome.NeedsResolution ->
                        onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_auth_pending))))
                }
            }
        }
    }

    fun exportLocalBackup(uri: Uri, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val snapshot = repository.snapshotForBackup()
                val json = DataBackupService.exportToJson(
                    snapshot.transactions,
                    snapshot.loans,
                    snapshot.repayments,
                    snapshot.budgets,
                    snapshot.categories
                )
                val written = withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    if (output == null) {
                        false
                    } else {
                        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        true
                    }
                }
                if (written) {
                    onResult(Result.success(getApplication<Application>().getString(R.string.vm_backup_written)))
                } else {
                    onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_write_open_failed))))
                }
            } catch (e: Exception) {
                onResult(Result.failure(Exception(e.message ?: getApplication<Application>().getString(R.string.err_save_backup_failed))))
            }
        }
    }

    fun importLocalBackup(uri: Uri, onResult: (Result<BackupData>) -> Unit) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }
                if (text == null) {
                    onResult(Result.failure(Exception(getApplication<Application>().getString(R.string.vm_open_file_failed))))
                    return@launch
                }
                onResult(Result.success(DataBackupService.parseBackupJson(text)))
            } catch (e: Exception) {
                onResult(Result.failure(Exception(e.message ?: getApplication<Application>().getString(R.string.vm_invalid_backup_file))))
            }
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.clearAll()
            repository.seedCategoriesIfEmpty()
            repository.seedBudgetsIfEmpty()
            repository.seedSampleData()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            repository.seedCategoriesIfEmpty()
            repository.seedBudgetsIfEmpty()
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        updateManager.isAutoCheckEnabled = enabled
        _isAutoUpdateCheckEnabled.value = enabled
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun checkForUpdates(isManual: Boolean, onResult: ((UpdateCheckResult) -> Unit)? = null) {
        if (_isCheckingForUpdates.value) return
        if (!isManual && !updateManager.shouldAutoCheckToday()) return

        viewModelScope.launch {
            _isCheckingForUpdates.value = true
            val app = getApplication<Application>()
            val currentVersion = try {
                com.yourname.mymoney.BuildConfig.VERSION_NAME
            } catch (_: Throwable) {
                try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.0"
                } catch (_: Exception) {
                    "1.0"
                }
            }
            val owner = app.getString(com.yourname.mymoney.R.string.github_owner)
            val repo = app.getString(com.yourname.mymoney.R.string.github_repo)

            val result = updateManager.checkForUpdates(currentVersion, owner, repo)
            _isCheckingForUpdates.value = false

            if (result is UpdateCheckResult.UpdateAvailable) {
                _availableUpdate.value = result.release
            }
            onResult?.invoke(result)
        }
    }
}
