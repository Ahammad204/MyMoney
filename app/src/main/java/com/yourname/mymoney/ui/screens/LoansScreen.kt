package com.yourname.mymoney.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.mymoney.data.LoanEntity
import com.yourname.mymoney.data.LoanWithDetails
import com.yourname.mymoney.data.PersonLoanSummary
import com.yourname.mymoney.ui.FinanceOverview
import com.yourname.mymoney.ui.components.formatCurrency
import com.yourname.mymoney.util.formatDate
import com.yourname.mymoney.ui.theme.ExpenseRed
import com.yourname.mymoney.ui.theme.IncomeGreen
import androidx.compose.ui.res.stringResource
import com.yourname.mymoney.R

enum class LoanTabMode {
    ALL_LOANS,
    BY_PERSON
}

@Composable
fun LoansScreen(
    loansWithDetails: List<LoanWithDetails>,
    personSummaries: List<PersonLoanSummary>,
    overview: FinanceOverview,
    onAddLoanClick: () -> Unit,
    onLoanClick: (LoanWithDetails) -> Unit,
    onAddRepaymentClick: (LoanWithDetails) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(LoanTabMode.ALL_LOANS) }
    var selectedStatusFilter by remember { mutableStateOf("ACTIVE") } // ACTIVE, OVERDUE, LENT, BORROWED, SETTLED, ALL

    val filteredLoans = remember(loansWithDetails, selectedStatusFilter) {
        when (selectedStatusFilter) {
            "ACTIVE" -> loansWithDetails.filter { !it.isSettled }
            "OVERDUE" -> loansWithDetails.filter { it.isOverdue }
            "LENT" -> loansWithDetails.filter { it.loan.type == "LENT" && !it.isSettled }
            "BORROWED" -> loansWithDetails.filter { it.loan.type == "BORROWED" && !it.isSettled }
            "SETTLED" -> loansWithDetails.filter { it.isSettled }
            else -> loansWithDetails
        }
    }

    val overdueCount = remember(loansWithDetails) {
        loansWithDetails.count { it.isOverdue }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddLoanClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("loans_fab_add")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_loan), modifier = Modifier.size(24.dp))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("loans_list_column"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = stringResource(R.string.loans_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.loans_sub),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // TWO CARDS AT THE TOP AS REQUESTED:
            // 1. Total I will receive
            // 2. Total I must pay
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Total I will receive
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("total_will_receive_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(IncomeGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Handshake,
                                        contentDescription = null,
                                        tint = IncomeGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.loans_will_receive),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = formatCurrency(overview.totalLentActive),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("total_will_receive_amount")
                            )
                            Text(
                                text = stringResource(R.string.loans_owed_to_you),
                                fontSize = 11.sp,
                                color = IncomeGreen
                            )
                        }
                    }

                    // Card 2: Total I must pay
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("total_must_pay_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(ExpenseRed.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Handshake,
                                        contentDescription = null,
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.loans_must_pay),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = formatCurrency(overview.totalBorrowedActive),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ExpenseRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("total_must_pay_amount")
                            )
                            Text(
                                text = stringResource(R.string.loans_you_owe),
                                fontSize = 11.sp,
                                color = ExpenseRed
                            )
                        }
                    }
                }
            }

            // View Mode Switcher: "All Loans" vs "Per-Person View"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isAll = viewMode == LoanTabMode.ALL_LOANS
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAll) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewMode = LoanTabMode.ALL_LOANS }
                            .padding(vertical = 10.dp)
                            .testTag("tab_all_loans"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.loans_all_count, loansWithDetails.size),
                            color = if (isAll) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    val isPerson = viewMode == LoanTabMode.BY_PERSON
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPerson) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewMode = LoanTabMode.BY_PERSON }
                            .padding(vertical = 10.dp)
                            .testTag("tab_by_person"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.loans_per_person, personSummaries.size),
                            color = if (isPerson) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isPerson) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Filter chips (Only for All Loans mode)
            if (viewMode == LoanTabMode.ALL_LOANS) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "ACTIVE" to stringResource(R.string.filter_active),
                            "OVERDUE" to if (overdueCount > 0) stringResource(R.string.filter_overdue_count, overdueCount) else stringResource(R.string.filter_overdue),
                            "LENT" to stringResource(R.string.loan_i_lent_short),
                            "BORROWED" to stringResource(R.string.loan_i_borrowed_short),
                            "SETTLED" to stringResource(R.string.loans_settled_badge),
                            "ALL" to stringResource(R.string.filter_all)
                        ).forEach { (key, label) ->
                            val isSelected = selectedStatusFilter == key
                            val isOverdueChip = key == "OVERDUE"
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        if (isOverdueChip) ExpenseRed else MaterialTheme.colorScheme.primary
                                    } else {
                                        if (isOverdueChip && overdueCount > 0) ExpenseRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    border = if (isOverdueChip && overdueCount > 0 && !isSelected) androidx.compose.foundation.BorderStroke(1.dp, ExpenseRed) else null,
                                    modifier = Modifier
                                        .clickable { selectedStatusFilter = key }
                                        .testTag("loan_filter_$key")
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else if (isOverdueChip && overdueCount > 0) ExpenseRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CONTENT SECTION
            if (viewMode == LoanTabMode.ALL_LOANS) {
                if (filteredLoans.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Handshake,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.loans_no_filter),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.loans_no_filter_sub),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredLoans, key = { it.loan.id }) { lwd ->
                        LoanCardItem(
                            loanWithDetails = lwd,
                            onClick = { onLoanClick(lwd) },
                            onAddPayment = { onAddRepaymentClick(lwd) }
                        )
                    }
                }
            } else {
                // PER-PERSON VIEW WITH NET BALANCE
                if (personSummaries.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.loans_no_people),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    items(personSummaries, key = { it.personName }) { summary ->
                        PersonSummaryCard(
                            summary = summary,
                            onLoanClick = onLoanClick,
                            onAddPayment = onAddRepaymentClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoanCardItem(
    loanWithDetails: LoanWithDetails,
    onClick: () -> Unit,
    onAddPayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val loan = loanWithDetails.loan
    val isLent = loan.type == "LENT"
    val isOverdue = loanWithDetails.isOverdue
    val isSettled = loanWithDetails.isSettled
    val remaining = loanWithDetails.remainingBalance
    val paid = loanWithDetails.paidAmount

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue) {
                MaterialTheme.colorScheme.errorContainer // Highlight overdue in soft red
            } else if (isSettled) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isOverdue) {
            androidx.compose.foundation.BorderStroke(1.5.dp, ExpenseRed) // Highlight overdue border
        } else if (isSettled) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOverdue) 3.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("loan_item_${loan.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar, Person, Type Badge, Overdue Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOverdue) ExpenseRed.copy(alpha = 0.2f)
                            else if (isLent) IncomeGreen.copy(alpha = 0.15f)
                            else ExpenseRed.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isOverdue) ExpenseRed else if (isLent) IncomeGreen else ExpenseRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.personName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLent) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isLent) stringResource(R.string.loan_i_lent_short) else stringResource(R.string.loan_i_borrowed_short),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLent) IncomeGreen else ExpenseRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (isOverdue) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ExpenseRed
                            ) {
                                Text(
                                    text = stringResource(R.string.status_overdue),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isSettled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IncomeGreen
                            ) {
                                Text(
                                    text = stringResource(R.string.status_settled),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Remaining Balance display
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isSettled) formatCurrency(0.0) else formatCurrency(remaining),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSettled) IncomeGreen else if (isOverdue) ExpenseRed else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isSettled) stringResource(R.string.loans_fully_paid) else stringResource(R.string.loans_of_amount, formatCurrency(loan.amount)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note
            if (loan.note.isNotBlank()) {
                Text(
                    text = stringResource(R.string.loans_note_quoted, loan.note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Due Date & Quick Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loan.dueDate > 0) {
                    Text(
                        text = stringResource(R.string.loans_due_date, formatDate(loan.dueDate)),
                        fontSize = 11.sp,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOverdue) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.loans_loan_date, formatDate(loan.date)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isSettled) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable(onClick = onAddPayment)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.loans_pay_return),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.loans_settled_badge), fontSize = 11.sp, color = IncomeGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonSummaryCard(
    summary: PersonLoanSummary,
    onLoanClick: (LoanWithDetails) -> Unit,
    onAddPayment: (LoanWithDetails) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person_summary_${summary.personName}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = summary.personName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.personName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.loans_summary_counts, summary.activeLoansCount, summary.settledLoansCount),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Net Balance Badge
                Column(horizontalAlignment = Alignment.End) {
                    val net = summary.netBalance
                    if (net > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IncomeGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = stringResource(R.string.loans_owes_you, formatCurrency(net)),
                                color = IncomeGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (net < 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ExpenseRed.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = stringResource(R.string.loans_you_owe_amount, formatCurrency(Math.abs(net))),
                                color = ExpenseRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.loans_all_settled),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.loans.forEach { lwd ->
                        LoanCardItem(
                            loanWithDetails = lwd,
                            onClick = { onLoanClick(lwd) },
                            onAddPayment = { onAddPayment(lwd) }
                        )
                    }
                }
            }
        }
    }
}
