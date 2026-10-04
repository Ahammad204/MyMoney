package com.yourname.mymoney

import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.unit.Dp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.mymoney.ai.AiEntryResult
import com.yourname.mymoney.ai.GeminiClient
import com.yourname.mymoney.data.LoanEntity
import com.yourname.mymoney.data.LoanWithDetails
import com.yourname.mymoney.data.TransactionEntity
import com.yourname.mymoney.ui.MyMoneyViewModel
import com.yourname.mymoney.ui.components.AddLoanDialog
import com.yourname.mymoney.ui.components.AddRepaymentDialog
import com.yourname.mymoney.ui.components.AddTransactionDialog
import com.yourname.mymoney.ui.components.AiEntryPreviewDialog
import com.yourname.mymoney.ui.components.AskAiChatDialog
import com.yourname.mymoney.ui.components.CurrencyManager
import com.yourname.mymoney.ui.components.DeleteConfirmationDialog
import com.yourname.mymoney.ui.components.EditBudgetDialog
import com.yourname.mymoney.ui.components.LoanDetailsDialog
import com.yourname.mymoney.ui.components.MonthlyInsightsDialog
import com.yourname.mymoney.ui.components.SettingsDialog
import com.yourname.mymoney.ui.components.UpdateDialog
import com.yourname.mymoney.ui.components.formatCurrency
import com.yourname.mymoney.util.formatDate
import com.yourname.mymoney.ui.screens.HomeScreen
import com.yourname.mymoney.ui.screens.LoansScreen
import com.yourname.mymoney.ui.screens.ReportsScreen
import com.yourname.mymoney.ui.screens.TransactionsScreen
import com.yourname.mymoney.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import androidx.compose.ui.res.stringResource
import com.yourname.mymoney.ui.components.recurrenceResId

enum class AppTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
    TRANSACTIONS("Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "tab_transactions"),
    LOANS("Loans", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance, "tab_loans"),
    REPORTS("Reports", Icons.Filled.PieChart, Icons.Outlined.PieChart, "tab_reports")
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MyMoneyViewModel = viewModel()
            val darkThemeMode by viewModel.darkThemeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (darkThemeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
                MyMoneyApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MyMoneyApp(
    viewModel: MyMoneyViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.HOME) }

    // Transaction dialog state
    var showAddTxDialog by remember { mutableStateOf(false) }
    var addTxInitialType by remember { mutableStateOf("EXPENSE") }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    // Prefilled values for receipt scan
    var prefilledTitle by remember { mutableStateOf("") }
    var prefilledAmount by remember { mutableStateOf<Double?>(null) }
    var prefilledCategory by remember { mutableStateOf<String?>(null) }
    var prefilledDate by remember { mutableStateOf<Long?>(null) }
    var prefilledNote by remember { mutableStateOf("") }
    var isScanningReceipt by remember { mutableStateOf(false) }

    // Loan dialog state
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var selectedLoanForDetails by remember { mutableStateOf<LoanWithDetails?>(null) }
    var selectedLoanForRepayment by remember { mutableStateOf<LoanWithDetails?>(null) }
    var loanToDelete by remember { mutableStateOf<LoanEntity?>(null) }

    // Budget dialog state
    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetCategoryToEdit by remember { mutableStateOf<String?>(null) }
    var budgetLimitToEdit by remember { mutableStateOf<Double?>(null) }

    // Settings dialog state
    var showSettingsDialog by remember { mutableStateOf(false) }

    // AI dialog states
    var aiEntryToPreview by remember { mutableStateOf<Pair<AiEntryResult, String>?>(null) }
    var showAskAiChat by remember { mutableStateOf(false) }
    var showMonthlyInsights by remember { mutableStateOf(false) }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val loansWithDetails by viewModel.loansWithDetails.collectAsStateWithLifecycle()
    val personSummaries by viewModel.personLoanSummaries.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val rawCategories by viewModel.rawCategories.collectAsStateWithLifecycle()
    val budgetStatuses by viewModel.categoryBudgetStatuses.collectAsStateWithLifecycle()
    val allBudgets by viewModel.budgets.collectAsStateWithLifecycle()
    val sixMonthBars by viewModel.last6MonthsCashflow.collectAsStateWithLifecycle()
    val darkThemeMode by viewModel.darkThemeMode.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSimulatedOffline by viewModel.isSimulatedOffline.collectAsStateWithLifecycle()
    val lastBackupTimeFormatted by viewModel.lastBackupTimeFormatted.collectAsStateWithLifecycle()
    val lastBackupSizeFormatted by viewModel.lastBackupSizeFormatted.collectAsStateWithLifecycle()
    val lastBackupError by viewModel.lastBackupError.collectAsStateWithLifecycle()
    val autoBackupInterval by viewModel.autoBackupInterval.collectAsStateWithLifecycle()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsStateWithLifecycle()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsStateWithLifecycle()
    val pendingDriveAuthorization by viewModel.pendingDriveAuthorization.collectAsStateWithLifecycle()
    val isApiKeyConfigured by viewModel.isApiKeyConfigured.collectAsStateWithLifecycle()
    val isAutoUpdateCheckEnabled by viewModel.isAutoUpdateCheckEnabled.collectAsStateWithLifecycle()
    val isCheckingForUpdates by viewModel.isCheckingForUpdates.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Automatically check for updates on app start (once a day threshold in UpdateManager)
    LaunchedEffect(Unit) {
        if (isAutoUpdateCheckEnabled) {
            viewModel.checkForUpdates(isManual = false)
        }
    }

    // Google Drive OAuth consent resolution: AuthorizationClient hands back a PendingIntent
    // that must be launched from an Activity result launcher.
    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDriveAuthorizationResult(result.resultCode, result.data)
    }

    LaunchedEffect(pendingDriveAuthorization) {
        val pending = pendingDriveAuthorization ?: return@LaunchedEffect
        try {
            driveAuthorizationLauncher.launch(
                IntentSenderRequest.Builder(pending.intentSender).build()
            )
        } catch (e: Exception) {
            viewModel.onDriveAuthorizationLaunchFailed(
                e.message ?: context.getString(R.string.err_drive_auth_start)
            )
        }
    }

    // Photo Picker for Receipt Scanning (Zero-permission compliant with Google Play Policy)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            isScanningReceipt = true
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val maxDimension = 1024
                        val width = originalBitmap.width
                        val height = originalBitmap.height
                        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                            val ratio = width.toFloat() / height.toFloat()
                            val newWidth: Int
                            val newHeight: Int
                            if (ratio > 1) {
                                newWidth = maxDimension
                                newHeight = (maxDimension / ratio).toInt()
                            } else {
                                newHeight = maxDimension
                                newWidth = (maxDimension * ratio).toInt()
                            }
                            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                        } else {
                            originalBitmap
                        }

                        val outputStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                        val result = GeminiClient.scanReceipt(context, base64, categories)
                        isScanningReceipt = false
                        result.onSuccess { scanned ->
                            prefilledTitle = scanned.title
                            prefilledAmount = scanned.amount
                            prefilledCategory = scanned.category
                            prefilledDate = scanned.date
                            prefilledNote = scanned.note
                            transactionToEdit = null
                            addTxInitialType = "EXPENSE"
                            showAddTxDialog = true
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_receipt_extracted, scanned.title, formatCurrency(scanned.amount)))
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_receipt_scan_failed, err.message))
                        }
                    } else {
                        isScanningReceipt = false
                        snackbarHostState.showSnackbar(context.getString(R.string.snack_image_load_failed))
                    }
                } catch (e: Exception) {
                    isScanningReceipt = false
                    snackbarHostState.showSnackbar(context.getString(R.string.snack_receipt_process_error, e.message))
                }
            }
        }
    }

    fun buildFinancialContext(): String {
        val sb = StringBuilder()
        sb.append("Currency: ${selectedCurrency.code} (${selectedCurrency.symbol})\n")
        sb.append("Current Total Balance: ${formatCurrency(overview.currentBalance)}\n")
        sb.append("Selected Month (${overview.selectedMonthDisplayName}):\n")
        sb.append("  Total Income: ${formatCurrency(overview.totalIncomeThisMonth)}\n")
        sb.append("  Total Expense: ${formatCurrency(overview.totalExpenseThisMonth)}\n")
        sb.append("  Net Savings: ${formatCurrency(overview.netSavingsThisMonth)}\n")
        sb.append("  Savings Rate: ${overview.savingsRate}%\n")
        sb.append("  Today's Inflow: ${formatCurrency(overview.todayIncome)}, Outflow: ${formatCurrency(overview.todayExpense)}\n\n")

        sb.append("Recent Transactions:\n")
        transactions.take(25).forEach { tx ->
            val rec = if (tx.recurrence.isNotBlank() && tx.recurrence != "NONE") " [Recurring: ${tx.recurrence}]" else ""
            sb.append("- ${formatDate(tx.timestamp)}: ${tx.type} ${formatCurrency(tx.amount)} for \"${tx.title}\" [Category: ${tx.category}]$rec${if (tx.note.isNotBlank()) " (Note: ${tx.note})" else ""}\n")
        }

        sb.append("\nActive Loans / Debts:\n")
        loansWithDetails.forEach { lwd ->
            val due = if (lwd.loan.dueDate > 0) "due ${formatDate(lwd.loan.dueDate)}" else "no due date"
            sb.append("- ${lwd.loan.personName}: ${lwd.loan.type} total ${formatCurrency(lwd.totalAmount)}, paid ${formatCurrency(lwd.paidAmount)}, remaining ${formatCurrency(lwd.remainingBalance)}, $due, status: ${if (lwd.isSettled) "Settled" else if (lwd.isOverdue) "OVERDUE" else "Active"}\n")
        }

        sb.append("\nCategory Budgets:\n")
        budgetStatuses.forEach { b ->
            val status = when {
                b.isExceeded -> "OVER BUDGET by ${formatCurrency(Math.abs(b.remaining))}"
                b.isWarning -> "WARNING: 80%+ of budget used"
                else -> "On track (${formatCurrency(b.remaining)} left)"
            }
            sb.append("- ${b.category}: Limit ${formatCurrency(b.monthlyLimit)}, Spent ${formatCurrency(b.spent)} (${(b.percentage * 100).toInt()}%) -> $status\n")
        }

        return sb.toString()
    }

    // Handle back button when not on Home tab
    if (currentTab != AppTab.HOME) {
        BackHandler {
            currentTab = AppTab.HOME
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = Dp(8f),
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.HOME -> {
                    HomeScreen(
                        overview = overview,
                        transactions = transactions,
                        categories = categories,
                        onAddTransactionClick = { defaultType ->
                            addTxInitialType = defaultType
                            transactionToEdit = null
                            prefilledTitle = ""
                            prefilledAmount = null
                            prefilledCategory = null
                            prefilledDate = null
                            prefilledNote = ""
                            showAddTxDialog = true
                        },
                        onEditTransactionClick = { tx ->
                            transactionToEdit = tx
                        },
                        onDeleteTransactionClick = { tx ->
                            transactionToDelete = tx
                        },
                        onAddLoanClick = { showAddLoanDialog = true },
                        onViewAllTransactions = { currentTab = AppTab.TRANSACTIONS },
                        onPreviousMonth = { viewModel.selectPreviousMonth() },
                        onNextMonth = { viewModel.selectNextMonth() },
                        onResetToCurrentMonth = { viewModel.resetToCurrentMonth() },
                        onOpenSettingsClick = { showSettingsDialog = true },
                        onScanReceiptClick = {
                            if (!isApiKeyConfigured) {
                                scope.launch {
                                    val action = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.ai_key_missing_msg),
                                        actionLabel = context.getString(R.string.ai_settings_btn),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (action == SnackbarResult.ActionPerformed) {
                                        showSettingsDialog = true
                                    }
                                }
                            } else if (!isOnline) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.ai_needs_internet))
                                }
                            } else {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        onClearAllData = {
                            viewModel.clearAllData()
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.snack_all_cleared))
                            }
                        },
                        isOnline = isOnline,
                        isAiConfigured = isApiKeyConfigured
                    )
                }

                AppTab.TRANSACTIONS -> {
                    TransactionsScreen(
                        transactions = transactions,
                        categories = categories,
                        isOnline = isOnline,
                        isAiConfigured = isApiKeyConfigured,
                        onAiParsed = { parsed, raw ->
                            aiEntryToPreview = parsed to raw
                        },
                        onOpenSettingsClick = { showSettingsDialog = true },
                        onAddTransactionClick = {
                            addTxInitialType = "EXPENSE"
                            transactionToEdit = null
                            prefilledTitle = ""
                            prefilledAmount = null
                            prefilledCategory = null
                            prefilledDate = null
                            prefilledNote = ""
                            showAddTxDialog = true
                        },
                        onEditTransactionClick = { tx ->
                            transactionToEdit = tx
                        },
                        onDeleteTransactionClick = { tx ->
                            transactionToDelete = tx
                        }
                    )
                }

                AppTab.LOANS -> {
                    LoansScreen(
                        loansWithDetails = loansWithDetails,
                        personSummaries = personSummaries,
                        overview = overview,
                        onAddLoanClick = { showAddLoanDialog = true },
                        onLoanClick = { lwd ->
                            selectedLoanForDetails = lwd
                        },
                        onAddRepaymentClick = { lwd ->
                            selectedLoanForRepayment = lwd
                        }
                    )
                }

                AppTab.REPORTS -> {
                    ReportsScreen(
                        overview = overview,
                        transactions = transactions,
                        budgets = budgetStatuses,
                        sixMonthBars = sixMonthBars,
                        onPreviousMonth = { viewModel.selectPreviousMonth() },
                        onNextMonth = { viewModel.selectNextMonth() },
                        onSetBudgetClick = { cat, limit ->
                            budgetCategoryToEdit = cat
                            budgetLimitToEdit = limit
                            showBudgetDialog = true
                        },
                        onAskAiClick = {
                            if (!isApiKeyConfigured) {
                                scope.launch {
                                    val action = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.ai_key_missing_msg),
                                        actionLabel = context.getString(R.string.ai_settings_btn),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (action == SnackbarResult.ActionPerformed) {
                                        showSettingsDialog = true
                                    }
                                }
                            } else if (!isOnline) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.ai_needs_internet))
                                }
                            } else {
                                showAskAiChat = true
                            }
                        },
                        onMonthlyInsightsClick = {
                            if (!isApiKeyConfigured) {
                                scope.launch {
                                    val action = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.ai_key_missing_msg),
                                        actionLabel = context.getString(R.string.ai_settings_btn),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (action == SnackbarResult.ActionPerformed) {
                                        showSettingsDialog = true
                                    }
                                }
                            } else if (!isOnline) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.ai_needs_internet))
                                }
                            } else {
                                showMonthlyInsights = true
                            }
                        },
                        onOpenSettingsClick = { showSettingsDialog = true },
                        isOnline = isOnline,
                        isAiConfigured = isApiKeyConfigured
                    )
                }
            }

            // Scanning Receipt Progress Modal
            if (isScanningReceipt) {
                Dialog(onDismissRequest = {}) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.scanning_receipt), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(stringResource(R.string.scanning_receipt_sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Settings Dialog
            if (showSettingsDialog) {
                SettingsDialog(
                    currentThemeMode = darkThemeMode,
                    currentCurrency = selectedCurrency,
                    transactions = transactions,
                    loansWithDetails = loansWithDetails,
                    budgets = budgetStatuses,
                    allBudgets = allBudgets,
                    categories = rawCategories,
                    isOnline = isOnline,
                    isSimulatedOffline = isSimulatedOffline,
                    lastBackupTimeFormatted = lastBackupTimeFormatted,
                    lastBackupSizeFormatted = lastBackupSizeFormatted,
                    lastBackupError = lastBackupError,
                    autoBackupInterval = autoBackupInterval,
                    isAutoBackupEnabled = isAutoBackupEnabled,
                    googleAccountEmail = googleAccountEmail,
                    onThemeModeChange = { mode ->
                        viewModel.setDarkThemeMode(mode)
                    },
                    onCurrencyChange = { curr ->
                        viewModel.setCurrency(curr)
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_currency_updated, curr.name))
                        }
                    },
                    onToggleSimulatedOffline = {
                        viewModel.setSimulatedOffline(!isSimulatedOffline)
                    },
                    onGoogleSignIn = { onResult ->
                        viewModel.signInToGoogle { result ->
                            onResult(result)
                            result.onSuccess { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    },
                    onDisconnectGoogleAccount = {
                        viewModel.disconnectGoogleAccount()
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_drive_disconnected))
                        }
                    },
                    onAutoBackupIntervalChange = { interval ->
                        viewModel.setAutoBackupInterval(interval)
                    },
                    onToggleAutoBackup = { enabled ->
                        viewModel.setAutoBackupEnabled(enabled)
                        scope.launch {
                            snackbarHostState.showSnackbar(if (enabled) context.getString(R.string.snack_autobackup_on) else context.getString(R.string.snack_autobackup_off))
                        }
                    },
                    onDriveBackupNow = { onComplete ->
                        viewModel.backupToGoogleDrive { result ->
                            onComplete(result)
                            result.onSuccess {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snack_drive_backup_saved)) }
                            }
                        }
                    },
                    onDriveRestoreRequest = { onConfirmReady, onError ->
                        viewModel.fetchDriveBackup { result ->
                            result.onSuccess { backup ->
                                onConfirmReady(backup)
                            }.onFailure { err ->
                                onError(err.message ?: context.getString(R.string.err_drive_read_backup))
                            }
                        }
                    },
                    onRestoreBackupReplace = { backup, onResult ->
                        viewModel.restoreBackupReplace(backup) { result ->
                            onResult(result)
                            result.onSuccess {
                                showSettingsDialog = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.snack_restored_replace))
                                }
                            }
                        }
                    },
                    onRestoreBackupMerge = { backup, onResult ->
                        viewModel.restoreBackupMerge(backup) { result ->
                            onResult(result)
                            result.onSuccess {
                                showSettingsDialog = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.snack_restored_merge))
                                }
                            }
                        }
                    },
                    onExportLocalBackup = { uri, onResult ->
                        viewModel.exportLocalBackup(uri) { result ->
                            onResult(result)
                            result.onSuccess { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    },
                    onImportLocalBackup = { uri, onResult ->
                        viewModel.importLocalBackup(uri, onResult)
                    },
                    onScanReceiptClick = {
                        if (!isApiKeyConfigured) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.ai_key_missing_msg))
                            }
                        } else if (!isOnline) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.ai_needs_internet))
                            }
                        } else {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    isAutoUpdateCheckEnabled = isAutoUpdateCheckEnabled,
                    isCheckingForUpdates = isCheckingForUpdates,
                    onToggleAutoUpdateCheck = { enabled ->
                        viewModel.setAutoUpdateCheckEnabled(enabled)
                    },
                    onCheckForUpdates = { onResult ->
                        viewModel.checkForUpdates(isManual = true, onResult = onResult)
                    },
                    onDismiss = { showSettingsDialog = false }
                )
            }

            // AI Entry Preview Dialog
            aiEntryToPreview?.let { (result, raw) ->
                AiEntryPreviewDialog(
                    result = result,
                    rawPrompt = raw,
                    onConfirmSave = {
                        when (result) {
                            is AiEntryResult.TransactionResult -> {
                                viewModel.addTransaction(
                                    title = result.title,
                                    amount = result.amount,
                                    type = result.type,
                                    category = result.category,
                                    note = result.note,
                                    recurrence = result.recurrence
                                )
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.snack_saved_entry, result.type.lowercase(), result.title))
                                }
                            }
                            is AiEntryResult.LoanResult -> {
                                viewModel.addLoan(
                                    personName = result.personName,
                                    amount = result.amount,
                                    type = result.type,
                                    date = System.currentTimeMillis(),
                                    dueDate = result.dueDateTimestamp,
                                    note = result.note
                                )
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.snack_saved_loan, result.personName, result.type))
                                }
                            }
                        }
                        aiEntryToPreview = null
                    },
                    onDismiss = { aiEntryToPreview = null }
                )
            }

            // Ask AI Chat Dialog
            if (showAskAiChat) {
                AskAiChatDialog(
                    financialContext = buildFinancialContext(),
                    onDismiss = { showAskAiChat = false }
                )
            }

            // Monthly Insights Dialog
            if (showMonthlyInsights) {
                MonthlyInsightsDialog(
                    financialContext = buildFinancialContext(),
                    onDismiss = { showMonthlyInsights = false }
                )
            }

            // Add or Edit Transaction Dialog
            if (showAddTxDialog || transactionToEdit != null) {
                AddTransactionDialog(
                    transactionToEdit = transactionToEdit,
                    initialType = addTxInitialType,
                    initialTitle = prefilledTitle,
                    initialAmount = prefilledAmount,
                    initialCategory = prefilledCategory,
                    initialTimestamp = prefilledDate,
                    initialNote = prefilledNote,
                    categories = categories,
                    onAddCustomCategory = { newCat ->
                        viewModel.addCustomCategory(newCat)
                    },
                    onScanReceiptClick = {
                        if (!isApiKeyConfigured) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.ai_key_missing_msg))
                            }
                        } else if (!isOnline) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.ai_needs_internet))
                            }
                        } else {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    onDismiss = {
                        showAddTxDialog = false
                        transactionToEdit = null
                        prefilledTitle = ""
                        prefilledAmount = null
                        prefilledCategory = null
                        prefilledDate = null
                        prefilledNote = ""
                    },
                    onSave = { title, amount, type, category, note, timestamp, recurrence ->
                        val editing = transactionToEdit
                        if (editing != null) {
                            viewModel.updateTransaction(
                                original = editing,
                                title = title,
                                amount = amount,
                                type = type,
                                category = category,
                                note = note,
                                timestamp = timestamp,
                                recurrence = recurrence
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.snack_tx_updated))
                            }
                        } else {
                            viewModel.addTransaction(
                                title = title,
                                amount = amount,
                                type = type,
                                category = category,
                                note = note,
                                timestamp = timestamp,
                                recurrence = recurrence
                            )
                            val recText = if (recurrence != "NONE") " [${context.getString(recurrenceResId(recurrence)).lowercase()}]" else ""
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.snack_added, type, title, recText))
                            }
                        }
                        showAddTxDialog = false
                        transactionToEdit = null
                        prefilledTitle = ""
                        prefilledAmount = null
                        prefilledCategory = null
                        prefilledDate = null
                        prefilledNote = ""
                    },
                    onToggleRecurrencePause = { tx ->
                        val paused = tx.recurrenceActive
                        val updated = viewModel.setRecurrencePaused(tx, paused)
                        transactionToEdit = updated
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (paused) context.getString(R.string.snack_rule_paused)
                                else context.getString(R.string.snack_rule_resumed)
                            )
                        }
                    },
                    onStopRecurrence = { tx ->
                        viewModel.stopRecurrence(tx)
                        showAddTxDialog = false
                        transactionToEdit = null
                        prefilledTitle = ""
                        prefilledAmount = null
                        prefilledCategory = null
                        prefilledDate = null
                        prefilledNote = ""
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_recurring_deleted))
                        }
                    }
                )
            }

            // Delete Transaction Confirmation Dialog
            transactionToDelete?.let { tx ->
                val isIncome = tx.type.equals("INCOME", ignoreCase = true)
                val isRecurringRule = tx.recurrence in listOf("DAILY", "WEEKLY", "MONTHLY")
                DeleteConfirmationDialog(
                    itemTitle = tx.title,
                    itemDetail = buildString {
                        append(if (isIncome) "+" else "-")
                        append(formatCurrency(tx.amount))
                        if (isRecurringRule) append(" • recurring rule — future entries stop")
                    },
                    onConfirm = {
                        viewModel.deleteTransaction(tx)
                        transactionToDelete = null
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_deleted_tx, tx.title))
                        }
                    },
                    onDismiss = {
                        transactionToDelete = null
                    }
                )
            }

            // Add Loan Dialog
            if (showAddLoanDialog) {
                AddLoanDialog(
                    onDismiss = { showAddLoanDialog = false },
                    onSave = { personName, amount, type, date, dueDate, note ->
                        viewModel.addLoan(
                            personName = personName,
                            amount = amount,
                            type = type,
                            date = date,
                            dueDate = dueDate,
                            note = note
                        )
                        showAddLoanDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_loan_saved_for, personName))
                        }
                    }
                )
            }

            // Loan Details Dialog (with Payment History and Overdue Highlight)
            selectedLoanForDetails?.let { currentLwd ->
                val freshLwd = loansWithDetails.find { it.loan.id == currentLwd.loan.id } ?: currentLwd
                LoanDetailsDialog(
                    loanWithDetails = freshLwd,
                    onDismiss = { selectedLoanForDetails = null },
                    onAddRepaymentClick = {
                        selectedLoanForRepayment = freshLwd
                    },
                    onDeleteRepaymentClick = { rep ->
                        viewModel.deleteRepayment(rep)
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_payment_removed))
                        }
                    },
                    onDeleteLoanClick = {
                        loanToDelete = freshLwd.loan
                        selectedLoanForDetails = null
                    },
                    onToggleSettledClick = {
                        viewModel.toggleLoanSettled(freshLwd.loan)
                        selectedLoanForDetails = null
                    }
                )
            }

            // Add Repayment Dialog
            selectedLoanForRepayment?.let { lwd ->
                val freshLwd = loansWithDetails.find { it.loan.id == lwd.loan.id } ?: lwd
                AddRepaymentDialog(
                    loanWithDetails = freshLwd,
                    onDismiss = { selectedLoanForRepayment = null },
                    onSave = { amount, date, note ->
                        viewModel.addRepayment(freshLwd.loan, amount, date, note)
                        selectedLoanForRepayment = null
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_repayment_recorded, formatCurrency(amount)))
                        }
                    }
                )
            }

            // Delete Loan Confirmation Dialog
            loanToDelete?.let { loan ->
                DeleteConfirmationDialog(
                    itemTitle = context.getString(R.string.item_loan, loan.personName),
                    itemDetail = "${loan.type} ${formatCurrency(loan.amount)}",
                    onConfirm = {
                        viewModel.deleteLoan(loan)
                        loanToDelete = null
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_loan_deleted))
                        }
                    },
                    onDismiss = {
                        loanToDelete = null
                    }
                )
            }

            // Edit Category Budget Dialog
            if (showBudgetDialog) {
                EditBudgetDialog(
                    initialCategory = budgetCategoryToEdit,
                    initialLimit = budgetLimitToEdit,
                    categories = categories,
                    onDismiss = { showBudgetDialog = false },
                    onSave = { category, limit ->
                        viewModel.setBudget(category, limit)
                        showBudgetDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_budget_set, category, formatCurrency(limit)))
                        }
                    },
                    onDelete = { category ->
                        viewModel.deleteBudget(category)
                        showBudgetDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_budget_removed, category))
                        }
                    }
                )
            }

            // Update Available Dialog
            availableUpdate?.let { updateInfo ->
                val currentVer = try {
                    com.yourname.mymoney.BuildConfig.VERSION_NAME
                } catch (_: Throwable) {
                    "1.0"
                }
                UpdateDialog(
                    release = updateInfo,
                    currentVersion = currentVer,
                    onDismiss = {
                        viewModel.dismissUpdateDialog()
                    }
                )
            }
        }
    }
}
