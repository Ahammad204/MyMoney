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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TransactionEntity
import com.example.ui.components.TransactionRowItem
import com.example.ui.components.formatCurrency
import com.example.util.DateUtils
import com.example.util.formatDate
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import androidx.compose.ui.res.stringResource
import com.example.R

enum class DateRangeFilter(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    CUSTOM("Custom Range")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    categories: List<String>,
    onAddTransactionClick: () -> Unit,
    onEditTransactionClick: (TransactionEntity) -> Unit,
    onDeleteTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, INCOME, EXPENSE
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDateRange by remember { mutableStateOf(DateRangeFilter.ALL_TIME) }

    // Custom date range bounds
    var customStartDate by remember {
        mutableLongStateOf(System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L))
    }
    var customEndDate by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = customStartDate)
    val endDatePickerState = rememberDatePickerState(initialSelectedDateMillis = customEndDate)

    if (pickingStartDate) {
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startDatePickerState.selectedDateMillis?.let { customStartDate = it }
                        pickingStartDate = false
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    if (pickingEndDate) {
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDatePickerState.selectedDateMillis?.let { customEndDate = it }
                        pickingEndDate = false
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    // Newest first sorting
    val sortedTransactions = remember(transactions) {
        transactions.sortedByDescending { it.timestamp }
    }

    // Filter calculations
    val filteredTransactions = remember(
        sortedTransactions,
        searchQuery,
        selectedTypeFilter,
        selectedCategory,
        selectedDateRange,
        customStartDate,
        customEndDate
    ) {
        val now = System.currentTimeMillis()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val startOfWeek = now - (7 * 24 * 60 * 60 * 1000L)

        val calMonth = DateUtils.monthStart()
        val startOfMonth = calMonth.timeInMillis

        val calLastMonthStart = (calMonth.clone() as Calendar).apply {
            add(Calendar.MONTH, -1)
        }
        val startOfLastMonth = calLastMonthStart.timeInMillis
        val endOfLastMonth = startOfMonth

        sortedTransactions.filter { tx ->
            // Search query
            val matchesSearch = searchQuery.isBlank() ||
                    tx.title.contains(searchQuery, ignoreCase = true) ||
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                    tx.note.contains(searchQuery, ignoreCase = true) ||
                    tx.amount.toString().contains(searchQuery)

            // Type filter
            val matchesType = when (selectedTypeFilter) {
                "INCOME" -> tx.type.equals("INCOME", ignoreCase = true)
                "EXPENSE" -> tx.type.equals("EXPENSE", ignoreCase = true)
                "RECURRING" -> tx.recurrence.isNotBlank() && !tx.recurrence.equals("NONE", ignoreCase = true)
                else -> true
            }

            // Category filter
            val matchesCategory = selectedCategory == null || tx.category.equals(selectedCategory, ignoreCase = true)

            // Date Range filter
            val matchesDateRange = when (selectedDateRange) {
                DateRangeFilter.ALL_TIME -> true
                DateRangeFilter.TODAY -> tx.timestamp in startOfToday until endOfToday
                DateRangeFilter.THIS_WEEK -> tx.timestamp >= startOfWeek
                DateRangeFilter.THIS_MONTH -> tx.timestamp >= startOfMonth
                DateRangeFilter.LAST_MONTH -> tx.timestamp in startOfLastMonth until endOfLastMonth
                DateRangeFilter.CUSTOM -> {
                    val startDay = Instant.ofEpochMilli(customStartDate).atZone(zone).toLocalDate()
                    val endDay = Instant.ofEpochMilli(customEndDate).atZone(zone).toLocalDate()
                    val start = startDay.atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = endDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    tx.timestamp in start until end
                }
            }

            matchesSearch && matchesType && matchesCategory && matchesDateRange
        }
    }

    val activeFilterCount = (if (selectedTypeFilter != "ALL") 1 else 0) +
            (if (selectedCategory != null) 1 else 0) +
            (if (selectedDateRange != DateRangeFilter.ALL_TIME) 1 else 0) +
            (if (searchQuery.isNotBlank()) 1 else 0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("transactions_fab_add")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_transaction),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("transactions_list_column"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.tx_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.tx_records_count, filteredTransactions.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (activeFilterCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier.clickable {
                                searchQuery = ""
                                selectedTypeFilter = "ALL"
                                selectedCategory = null
                                selectedDateRange = DateRangeFilter.ALL_TIME
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.tx_reset_count, activeFilterCount),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 1. SEARCH BAR
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.tx_search_ph)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_search_input")
                )
            }

            // 2. TYPE FILTER (All, Income, Expense)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to stringResource(R.string.filter_all),
                        "EXPENSE" to stringResource(R.string.type_expense),
                        "INCOME" to stringResource(R.string.type_income),
                        "RECURRING" to stringResource(R.string.txf_recurring)
                    ).forEach { (typeKey, label) ->
                        val isSelected = selectedTypeFilter == typeKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                when (typeKey) {
                                    "INCOME" -> IncomeGreen
                                    "EXPENSE" -> ExpenseRed
                                    "RECURRING" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTypeFilter = typeKey }
                                .testTag("filter_type_$typeKey")
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 3. DATE RANGE FILTER
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tx_date_range),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(DateRangeFilter.entries) { rangeOption ->
                            val isSelected = selectedDateRange == rangeOption
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .clickable { selectedDateRange = rangeOption }
                                    .testTag("date_filter_${rangeOption.name}")
                            ) {
                                Text(
                                    text = rangeOption.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Custom Date Pickers if CUSTOM selected
                    if (selectedDateRange == DateRangeFilter.CUSTOM) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { pickingStartDate = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.tx_from, formatDate(customStartDate)),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { pickingEndDate = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.tx_to, formatDate(customEndDate)),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. CATEGORY FILTER
            item {
                Column {
                    Text(
                        text = stringResource(R.string.common_category),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            val isAll = selectedCategory == null
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isAll) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isAll) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier
                                    .clickable { selectedCategory = null }
                                    .testTag("cat_filter_all")
                            ) {
                                Text(
                                    text = stringResource(R.string.tx_all_categories),
                                    fontSize = 11.sp,
                                    fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier
                                    .clickable { selectedCategory = if (isSelected) null else cat }
                                    .testTag("cat_filter_$cat")
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.tx_no_match),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.tx_no_match_sub),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { tx ->
                    TransactionRowItem(
                        transaction = tx,
                        onEdit = { onEditTransactionClick(tx) },
                        onDelete = { onDeleteTransactionClick(tx) }
                    )
                }
            }
        }
    }
}
