package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.unit.sp
import com.example.data.CategoryBudgetStatus
import com.example.data.MonthlyCashflowBar
import com.example.data.TransactionEntity
import com.example.ui.CategorySpend
import com.example.ui.FinanceOverview
import com.example.ui.components.ExpensePieChart
import com.example.ui.components.SixMonthCashflowBarChart
import com.example.ui.components.formatCurrency
import com.example.ui.components.getCategoryColor
import com.example.ui.components.getCategoryIcon
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.util.DateUtils
import androidx.compose.ui.res.stringResource
import com.example.R

val WarningOrange = Color(0xFFF57C00)

@Composable
fun ReportsScreen(
    overview: FinanceOverview,
    transactions: List<TransactionEntity>,
    budgets: List<CategoryBudgetStatus>,
    sixMonthBars: List<MonthlyCashflowBar>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetBudgetClick: (category: String?, currentLimit: Double?) -> Unit,
    onAskAiClick: () -> Unit,
    onMonthlyInsightsClick: () -> Unit,
    onOpenSettingsClick: () -> Unit = {},
    isOnline: Boolean = true,
    isAiConfigured: Boolean = false,
    modifier: Modifier = Modifier
) {
    val categorySpends = remember(transactions, overview.selectedYear, overview.selectedMonth) {
        val startOfMonth = DateUtils.monthStart(overview.selectedYear, overview.selectedMonth).timeInMillis
        val endOfMonth = DateUtils.monthEnd(overview.selectedYear, overview.selectedMonth).timeInMillis

        val expenseTx = transactions.filter {
            it.type.equals("EXPENSE", ignoreCase = true) && it.timestamp in startOfMonth until endOfMonth
        }
        val totalExpense = expenseTx.sumOf { it.amount }

        if (totalExpense > 0) {
            expenseTx.groupBy { it.category }
                .map { (cat, list) ->
                    val sum = list.sumOf { it.amount }
                    CategorySpend(
                        category = cat,
                        amount = sum,
                        percentage = (sum / totalExpense).toFloat()
                    )
                }
                .sortedByDescending { it.amount }
        } else {
            emptyList()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header & Month Switcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.reports_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.reports_sub),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.cd_prev), modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = overview.selectedMonthDisplayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cd_next), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Summary Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reports_summary_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.reports_cashflow, overview.selectedMonthDisplayName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stringResource(R.string.reports_savings_rate, overview.savingsRate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.total_income), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "+${formatCurrency(overview.totalIncomeThisMonth)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.total_expense), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "-${formatCurrency(overview.totalExpenseThisMonth)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.reports_net_savings), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatCurrency(overview.netSavingsThisMonth),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (overview.netSavingsThisMonth >= 0) IncomeGreen else ExpenseRed
                            )
                        }
                    }
                }
            }
        }

        // AI ASSISTANT & INSIGHTS CARD
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_reports_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.reports_ai_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.reports_ai_sub),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isOnline) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_needs_internet),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isAiConfigured) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.ai_key_missing_msg),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onOpenSettingsClick,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("reports_ai_open_settings_btn")
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_settings_btn),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onAskAiClick,
                                enabled = isOnline,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("ask_ai_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.reports_ask_ai), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = onMonthlyInsightsClick,
                                enabled = isOnline,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp)
                                    .testTag("monthly_insights_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.reports_monthly_insights), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 1. PIE CHART OF EXPENSES BY CATEGORY
        item {
            ExpensePieChart(
                categorySpends = categorySpends,
                totalExpense = overview.totalExpenseThisMonth
            )
        }

        // 2. BAR CHART OF INCOME VS EXPENSE FOR THE LAST 6 MONTHS
        item {
            SixMonthCashflowBarChart(
                bars = sixMonthBars
            )
        }

        // 3. MONTHLY BUDGETS SECTION
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.reports_budgets_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.reports_budgets_sub),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onSetBudgetClick(null, null) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_budget_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.reports_set_budget), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (budgets.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PriceCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.reports_no_budgets),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.reports_no_budgets_sub),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = { onSetBudgetClick(null, null) }) {
                            Text(stringResource(R.string.reports_add_budget))
                        }
                    }
                }
            }
        } else {
            items(budgets, key = { it.category }) { budget ->
                CategoryBudgetCard(
                    budget = budget,
                    onEdit = { onSetBudgetClick(budget.category, budget.monthlyLimit) }
                )
            }
        }
    }
}

@Composable
fun CategoryBudgetCard(
    budget: CategoryBudgetStatus,
    onEdit: () -> Unit
) {
    val catColor = getCategoryColor(budget.category)
    val percentageInt = (budget.percentage * 100).toInt()

    // Determine warning color state:
    // Red if exceeded (> 100%)
    // Orange if warning (80% - 100%)
    // Green / Normal if < 80%
    val progressColor = when {
        budget.isExceeded -> ExpenseRed
        budget.isWarning -> WarningOrange
        else -> IncomeGreen
    }

    val containerBg = when {
        budget.isExceeded -> MaterialTheme.colorScheme.errorContainer // Soft red alert
        budget.isWarning -> MaterialTheme.colorScheme.tertiaryContainer // Soft orange warning
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        budget.isExceeded -> ExpenseRed
        budget.isWarning -> WarningOrange
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = androidx.compose.foundation.BorderStroke(if (budget.isExceeded || budget.isWarning) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("budget_card_${budget.category}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(budget.category),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budget.category,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.reports_spent_of, formatCurrency(budget.spent), formatCurrency(budget.monthlyLimit)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = progressColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, progressColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (budget.isExceeded) {
                            Text(
                                text = stringResource(R.string.reports_exceeded_by, formatCurrency(Math.abs(budget.remaining))),
                                color = ExpenseRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        } else if (budget.isWarning) {
                            Text(
                                text = stringResource(R.string.reports_pct_used, percentageInt),
                                color = WarningOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.reports_pct_left, percentageInt, formatCurrency(budget.remaining)),
                                color = IncomeGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit_budget),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar (colored Red if > 100%, Orange if >= 80%, Green if normal)
            LinearProgressIndicator(
                progress = { budget.percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
