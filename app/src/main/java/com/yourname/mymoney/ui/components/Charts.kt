package com.yourname.mymoney.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.mymoney.data.MonthlyCashflowBar
import com.yourname.mymoney.ui.CategorySpend
import com.yourname.mymoney.ui.theme.ExpenseRed
import com.yourname.mymoney.ui.theme.IncomeGreen
import androidx.compose.ui.res.stringResource
import com.yourname.mymoney.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensePieChart(
    categorySpends: List<CategorySpend>,
    totalExpense: Double,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("expense_pie_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chart_by_cat),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatCurrency(totalExpense),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseRed
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (categorySpends.isEmpty() || totalExpense <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chart_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // DONUT / PIE CANVAS
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .sizeIn(maxWidth = 190.dp, maxHeight = 190.dp)
                        .aspectRatio(1f)
                        .testTag("pie_chart_canvas_box"),
                    contentAlignment = Alignment.Center
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 800),
                        label = "pieAnimation"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 34.dp.toPx()
                        val arcSize = size.width - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                        var startAngle = -90f

                        categorySpends.forEach { spend ->
                            val sweep = (spend.percentage * 360f) * animatedProgress
                            val isSelected = selectedCategory == spend.category
                            val color = getCategoryColor(spend.category)

                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweep.coerceAtLeast(1.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(arcSize, arcSize),
                                style = Stroke(
                                    width = if (isSelected) strokeWidth + 6.dp.toPx() else strokeWidth,
                                    cap = StrokeCap.Butt
                                )
                            )
                            startAngle += sweep
                        }
                    }

                    // Center information
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val activeSpend = categorySpends.find { it.category == selectedCategory }
                        if (activeSpend != null) {
                            Text(
                                text = activeSpend.category,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = getCategoryColor(activeSpend.category)
                            )
                            Text(
                                text = formatCurrency(activeSpend.amount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(activeSpend.percentage * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.chart_total_spent),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(totalExpense),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.chart_n_categories, categorySpends.size),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Legend chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categorySpends.forEach { item ->
                        val isSelected = selectedCategory == item.category
                        val catColor = getCategoryColor(item.category)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = if (isSelected) BorderStroke(1.5.dp, catColor) else null,
                            modifier = Modifier
                                .clickable {
                                    selectedCategory = if (isSelected) null else item.category
                                }
                                .testTag("pie_legend_${item.category}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.chart_cat_pct, item.category, (item.percentage * 100).toInt()),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SixMonthCashflowBarChart(
    bars: List<MonthlyCashflowBar>,
    modifier: Modifier = Modifier
) {
    var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }

    val maxAmount = remember(bars) {
        val maxVal = bars.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1000.0
        if (maxVal <= 0.0) 1000.0 else maxVal
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("six_month_bar_chart_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.chart_income_vs_expense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.chart_last6),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(IncomeGreen)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.type_income), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ExpenseRed)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.type_expense), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hasAnyCashflow = bars.any { it.income > 0.0 || it.expense > 0.0 }
            if (!hasAnyCashflow) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chart_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Tooltip if month selected
                selectedMonthIndex?.let { index ->
                    if (index in bars.indices) {
                        val bar = bars[index]
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bar.yearMonthDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = stringResource(R.string.chart_in, formatCurrency(bar.income)),
                                        color = IncomeGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.chart_out, formatCurrency(bar.expense)),
                                        color = ExpenseRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.chart_net, formatCurrency(bar.net)),
                                        color = if (bar.net >= 0) IncomeGreen else ExpenseRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // BARS DISPLAY
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    bars.forEachIndexed { index, bar ->
                        val isSelected = selectedMonthIndex == index
                        val incomeFrac = if (bar.income > 0) (bar.income / maxAmount).coerceIn(0.04, 1.0).toFloat() else 0.02f
                        val expenseFrac = if (bar.expense > 0) (bar.expense / maxAmount).coerceIn(0.04, 1.0).toFloat() else 0.02f

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedMonthIndex = if (isSelected) null else index
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // The pair of bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .padding(horizontal = 3.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Income Bar (Green)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(incomeFrac)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (isSelected) IncomeGreen else IncomeGreen.copy(alpha = 0.85f))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            // Expense Bar (Red)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(expenseFrac)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (isSelected) ExpenseRed else ExpenseRed.copy(alpha = 0.85f))
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Month Label
                        Text(
                            text = bar.monthLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
}
