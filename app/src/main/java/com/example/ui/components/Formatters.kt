package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale
import com.example.R

data class AppCurrency(
    val code: String,
    val symbol: String,
    val name: String
)

val SupportedCurrencies = listOf(
    AppCurrency("BDT", "৳", "Bangladeshi Taka (৳)"),
    AppCurrency("USD", "$", "US Dollar ($)"),
    AppCurrency("EUR", "€", "Euro (€)"),
    AppCurrency("GBP", "£", "British Pound (£)"),
    AppCurrency("INR", "₹", "Indian Rupee (₹)"),
    AppCurrency("CAD", "CA$", "Canadian Dollar (CA$)"),
    AppCurrency("AUD", "AU$", "Australian Dollar (AU$)"),
    AppCurrency("JPY", "¥", "Japanese Yen (¥)"),
    AppCurrency("SAR", "SAR ", "Saudi Riyal (SAR)"),
    AppCurrency("AED", "AED ", "UAE Dirham (AED)"),
    AppCurrency("PKR", "Rs ", "Pakistani Rupee (Rs)"),
    AppCurrency("BRL", "R$", "Brazilian Real (R$)")
)

object CurrencyManager {
    var currentCurrency: AppCurrency = SupportedCurrencies[0]
}

fun formatCurrency(amount: Double, currency: AppCurrency = CurrencyManager.currentCurrency): String {
    val isNeg = amount < 0
    val absAmount = Math.abs(amount)
    val formattedNumber = String.format(Locale.US, "%,.2f", absAmount)
    return if (isNeg) "-${currency.symbol}$formattedNumber" else "${currency.symbol}$formattedNumber"
}

fun recurrenceResId(code: String): Int = if (code == "DAILY") R.string.recur_word_daily else if (code == "WEEKLY") R.string.recur_word_weekly else if (code == "MONTHLY") R.string.recur_word_monthly else R.string.recur_word_none

fun getCategoryIcon(category: String): ImageVector {
    return when (category.trim().lowercase(Locale.ROOT)) {
        "salary" -> Icons.Default.Payments
        "food" -> Icons.Default.Restaurant
        "bills" -> Icons.Default.Bolt
        "transport" -> Icons.Default.DirectionsCar
        "shopping" -> Icons.Default.ShoppingBag
        "health" -> Icons.Default.LocalHospital
        "other" -> Icons.Default.Category
        else -> Icons.AutoMirrored.Filled.Label
    }
}

fun getCategoryColor(category: String): Color {
    return when (category.trim().lowercase(Locale.ROOT)) {
        "salary" -> Color(0xFF1B8755)
        "food" -> Color(0xFFE65100)
        "bills" -> Color(0xFFF57F17)
        "transport" -> Color(0xFF00838F)
        "shopping" -> Color(0xFFC2185B)
        "health" -> Color(0xFF00796B)
        "other" -> Color(0xFF546E7A)
        else -> {
            val hash = category.hashCode()
            val colors = listOf(
                Color(0xFF6A1B9A),
                Color(0xFF283593),
                Color(0xFF00695C),
                Color(0xFFAD1457),
                Color(0xFF4527A0),
                Color(0xFF1565C0),
                Color(0xFFD84315)
            )
            colors[Math.abs(hash) % colors.size]
        }
    }
}
