package com.yourname.mymoney.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yourname.mymoney.data.TransactionEntity
import com.yourname.mymoney.ui.theme.ExpenseRed
import com.yourname.mymoney.ui.theme.IncomeGreen
import com.yourname.mymoney.util.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.ui.res.stringResource
import com.yourname.mymoney.R
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    transactionToEdit: TransactionEntity? = null,
    initialType: String = "EXPENSE",
    initialTitle: String = "",
    initialAmount: Double? = null,
    initialCategory: String? = null,
    initialTimestamp: Long? = null,
    initialNote: String = "",
    categories: List<String>,
    onAddCustomCategory: (String) -> Unit,
    onScanReceiptClick: (() -> Unit)? = null,
    onToggleRecurrencePause: ((TransactionEntity) -> Unit)? = null,
    onStopRecurrence: ((TransactionEntity) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        amount: Double,
        type: String,
        category: String,
        note: String,
        timestamp: Long,
        recurrence: String
    ) -> Unit
) {
    val isEditMode = transactionToEdit != null

    var type by remember {
        mutableStateOf(transactionToEdit?.type ?: initialType)
    }
    var title by remember {
        mutableStateOf(transactionToEdit?.title ?: initialTitle)
    }
    var amountText by remember {
        mutableStateOf(
            if (transactionToEdit != null) {
                if (transactionToEdit.amount % 1.0 == 0.0) transactionToEdit.amount.toLong().toString() else transactionToEdit.amount.toString()
            } else if (initialAmount != null && initialAmount > 0) {
                if (initialAmount % 1.0 == 0.0) initialAmount.toLong().toString() else initialAmount.toString()
            } else ""
        )
    }
    var category by remember {
        mutableStateOf(
            transactionToEdit?.category ?: initialCategory ?: if (initialType == "INCOME") "Salary" else "Food"
        )
    }
    var selectedTimestamp by remember {
        mutableLongStateOf(transactionToEdit?.timestamp ?: initialTimestamp ?: System.currentTimeMillis())
    }
    var note by remember {
        mutableStateOf(transactionToEdit?.note ?: initialNote)
    }
    var recurrence by remember {
        mutableStateOf(transactionToEdit?.recurrence ?: "NONE")
    }
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showNewCategoryInput by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedTimestamp
    )

    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedTimestamp = it
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) stringResource(R.string.tx_edit_record) else stringResource(R.string.tx_add_record),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isEditMode && onScanReceiptClick != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clickable {
                                        onDismiss()
                                        onScanReceiptClick()
                                    }
                                    .testTag("dialog_scan_receipt_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.tx_scan_receipt), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("close_add_transaction_dialog")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.cd_close_dialog))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Income / Expense Type Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isExpense = type == "EXPENSE"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isExpense) ExpenseRed else Color.Transparent)
                            .clickable {
                                type = "EXPENSE"
                                if (category == "Salary") category = "Food"
                            }
                            .padding(vertical = 9.dp)
                            .testTag("toggle_expense_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.type_expense),
                                color = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }

                    val isIncome = type == "INCOME"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isIncome) IncomeGreen else Color.Transparent)
                            .clickable {
                                type = "INCOME"
                                if (category == "Food" || category == "Bills") category = "Salary"
                            }
                            .padding(vertical = 9.dp)
                            .testTag("toggle_income_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.type_income),
                                color = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = it
                            errorMessage = null
                        }
                    },
                    label = { Text(stringResource(R.string.common_amount)) },
                    prefix = { Text("${CurrencyManager.currentCurrency.symbol} ", fontWeight = FontWeight.Bold) },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.tx_desc_label)) },
                    placeholder = { Text(if (type == "INCOME") "e.g. Salary, Freelance project" else "e.g. Groceries, Coffee, Electric bill") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Recurrence Selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tx_recurrence),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "NONE" to stringResource(R.string.recur_none_label),
                        "DAILY" to stringResource(R.string.recur_daily_label),
                        "WEEKLY" to stringResource(R.string.recur_weekly_label),
                        "MONTHLY" to stringResource(R.string.recur_monthly_label)
                    ).forEach { (code, label) ->
                        val isSelected = recurrence == code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { recurrence = code }
                                .testTag("recurrence_chip_$code")
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Recurrence rule controls (edit mode, rules only)
                val editingRule = transactionToEdit
                if (isEditMode && editingRule != null &&
                    editingRule.recurrence in listOf("DAILY", "WEEKLY", "MONTHLY")
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (editingRule.recurrenceActive) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val periodWord = when (editingRule.recurrence) {
                                "DAILY" -> stringResource(R.string.period_day)
                                "WEEKLY" -> stringResource(R.string.period_week)
                                else -> stringResource(R.string.period_month)
                            }
                            Text(
                                text = if (editingRule.recurrenceActive) {
                                    stringResource(R.string.tx_rule_active, periodWord)
                                } else {
                                    stringResource(R.string.tx_rule_paused)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (editingRule.recurrenceActive) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (onToggleRecurrencePause != null) {
                                TextButton(onClick = { onToggleRecurrencePause(editingRule) }) {
                                    Text(
                                        text = if (editingRule.recurrenceActive) stringResource(R.string.tx_pause) else stringResource(R.string.tx_resume),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (onStopRecurrence != null) {
                                TextButton(onClick = { onStopRecurrence(editingRule) }) {
                                    Text(
                                        text = stringResource(R.string.tx_delete_rule),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date Selection
                Text(
                    text = stringResource(R.string.common_date),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val selectedDay = Instant.ofEpochMilli(selectedTimestamp).atZone(zone).toLocalDate()
                val isToday = selectedDay == today
                val isYesterday = selectedDay == today.minusDays(1)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTimestamp = System.currentTimeMillis() }
                    ) {
                        Text(
                            text = stringResource(R.string.common_today),
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isYesterday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isYesterday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTimestamp = System.currentTimeMillis() - (24 * 60 * 60 * 1000L) }
                    ) {
                        Text(
                            text = stringResource(R.string.common_yesterday),
                            fontSize = 11.sp,
                            fontWeight = if (isYesterday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isYesterday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!isToday && !isYesterday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (!isToday && !isYesterday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1.3f)
                            .clickable { showDatePickerDialog = true }
                            .testTag("pick_date_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = stringResource(R.string.cd_calendar),
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatDate(selectedTimestamp),
                                fontSize = 11.sp,
                                fontWeight = if (!isToday && !isYesterday) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Categories (Default & Custom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.common_category),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showNewCategoryInput = !showNewCategoryInput },
                        modifier = Modifier.testTag("add_custom_category_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = stringResource(R.string.tx_new_category), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (showNewCategoryInput) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryText,
                            onValueChange = { newCategoryText = it },
                            placeholder = { Text(stringResource(R.string.tx_catname_ph), fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                if (newCategoryText.isNotBlank()) {
                                    val catTrimmed = newCategoryText.trim()
                                    onAddCustomCategory(catTrimmed)
                                    category = catTrimmed
                                    newCategoryText = ""
                                    showNewCategoryInput = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.common_add), fontSize = 12.sp)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { catName ->
                        val isSelected = category.equals(catName, ignoreCase = true)
                        val catColor = getCategoryColor(catName)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) catColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) BorderStroke(1.5.dp, catColor) else null,
                            modifier = Modifier
                                .clickable { category = catName }
                                .testTag("category_chip_$catName")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(catColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getCategoryIcon(catName),
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = catName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_optional)) },
                    placeholder = { Text(stringResource(R.string.tx_note_ph)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_note_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Submit Button
                Button(
                    onClick = {
                        val parsedAmount = amountText.toDoubleOrNull()
                        if (parsedAmount == null || parsedAmount <= 0.0) {
                            errorMessage = context.getString(R.string.err_valid_amount)
                            return@Button
                        }
                        if (title.isBlank()) {
                            errorMessage = context.getString(R.string.err_desc_required)
                            return@Button
                        }
                        onSave(title, parsedAmount, type, category, note, selectedTimestamp, recurrence)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_transaction_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (type == "INCOME") IncomeGreen else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isEditMode) stringResource(R.string.tx_save_changes) else stringResource(R.string.tx_add_transaction),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
