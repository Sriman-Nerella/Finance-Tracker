package com.example.financetracker.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.financetracker.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val RUPEE = "₹"

/** Indian-style grouping, paise dropped when zero: 1234567.5 -> "₹12,34,567.50", 1500 -> "₹1,500". */
fun Money.formatInr(): String {
    val absMinor = if (minor < 0) -minor else minor
    val rupees = absMinor / 100
    val paise = absMinor % 100
    val sign = if (minor < 0) "-" else ""
    val fraction = if (paise == 0L) "" else "." + paise.toString().padStart(2, '0')
    return sign + RUPEE + groupIndian(rupees) + fraction
}

private fun groupIndian(n: Long): String {
    val digits = n.toString()
    if (digits.length <= 3) return digits
    val last3 = digits.takeLast(3)
    var rest = digits.dropLast(3)
    val parts = ArrayList<String>()
    while (rest.length > 2) {
        parts.add(0, rest.takeLast(2))
        rest = rest.dropLast(2)
    }
    if (rest.isNotEmpty()) parts.add(0, rest)
    return parts.joinToString(",") + "," + last3
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val shortFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val weekdayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** "19 Nov 2026" */
fun LocalDate.formatDate(): String = format(dayFormat)

/** "19 Nov" */
fun LocalDate.formatShort(): String = format(shortFormat)

/** "Today", "Yesterday", or "Thu, 19 Nov". */
fun LocalDate.dayHeaderLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(weekdayFormat)
}

/** "−₹450" for expenses, "+₹85,000" for income (true minus sign). */
fun Money.signedInr(income: Boolean): String = (if (income) "+" else "−") + Money(kotlin.math.abs(minor)).formatInr()

private val monthShortFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

/** "NOV 2026" for month headers. */
fun YearMonth.monthHeader(): String = format(monthShortFormat).uppercase(Locale.ENGLISH)

/** "Thursday, 19 Nov 2026" */
fun LocalDate.formatLong(): String = format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH))

/** "November 2026" */
fun YearMonth.monthLabel(): String = format(monthFormat)

/** Maps DefaultCategories icon keys (and any other Material key) to an icon. */
fun categoryIcon(key: String): ImageVector = when (key) {
    "restaurant" -> Icons.Rounded.Restaurant
    "local_grocery_store" -> Icons.Rounded.LocalGroceryStore
    "directions_car" -> Icons.Rounded.DirectionsCar
    "movie" -> Icons.Rounded.Movie
    "shopping_bag" -> Icons.Rounded.ShoppingBag
    "receipt_long" -> Icons.AutoMirrored.Rounded.ReceiptLong
    "medical_services" -> Icons.Rounded.MedicalServices
    "more_horiz" -> Icons.Rounded.MoreHoriz
    "payments" -> Icons.Rounded.Payments
    "savings" -> Icons.Rounded.Savings
    else -> Icons.Rounded.Category
}
