package com.example.financetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Family = FontFamily.Default

/** Tabular figures everywhere so amounts line up in lists. */
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

/** Design-system type styles that have no Material slot. */
object FinanceType {
    /** The month's spent total on the dashboard. */
    val amountXl = style(40, 44, FontWeight.ExtraBold, -0.02)

    /** Transaction detail amount, profile stats. */
    val amountLg = style(28, 34, FontWeight.Bold, -0.01)

    /** Row titles and amounts in rows. */
    val bodyStrong = style(15, 22, FontWeight.SemiBold)
}

/**
 * Material slots mapped to the design system:
 * titleLarge = title-lg, titleMedium = title, bodyLarge = body, labelLarge = label,
 * bodySmall = caption, labelSmall = overline (always uppercase).
 */
val Typography = Typography(
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.Bold),
    titleMedium = style(17, 24, FontWeight.Bold),
    titleSmall = style(15, 22, FontWeight.SemiBold),
    bodyLarge = style(15, 22, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Medium),
    labelLarge = style(13, 18, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 14, FontWeight.Bold, 0.08),
)
