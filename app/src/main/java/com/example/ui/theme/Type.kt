package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val NotoSansBengali = FontFamily(
    Font(R.font.noto_sans_bengali, FontWeight.Normal),
    Font(R.font.noto_sans_bengali_bold, FontWeight.Bold),
)

private val defaults = Typography()

// Set of Material typography styles, all using Noto Sans Bengali so Latin and
// Bangla text share one family across the app.
val Typography =
  Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = NotoSansBengali),
    displayMedium = defaults.displayMedium.copy(fontFamily = NotoSansBengali),
    displaySmall = defaults.displaySmall.copy(fontFamily = NotoSansBengali),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = NotoSansBengali),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = NotoSansBengali),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = NotoSansBengali),
    titleLarge = defaults.titleLarge.copy(fontFamily = NotoSansBengali),
    titleMedium = defaults.titleMedium.copy(fontFamily = NotoSansBengali),
    titleSmall = defaults.titleSmall.copy(fontFamily = NotoSansBengali),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = NotoSansBengali),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = NotoSansBengali),
    bodySmall = defaults.bodySmall.copy(fontFamily = NotoSansBengali),
    labelLarge = defaults.labelLarge.copy(fontFamily = NotoSansBengali),
    labelMedium = defaults.labelMedium.copy(fontFamily = NotoSansBengali),
    labelSmall = defaults.labelSmall.copy(fontFamily = NotoSansBengali),
  )
