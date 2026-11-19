package com.example.financetracker.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
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

/** "November 2026" */
fun YearMonth.monthLabel(): String = format(monthFormat)

/** Maps DefaultCategories icon keys (and any other Material key) to an icon. */
fun categoryIcon(key: String): ImageVector = when (key) {
    "restaurant" -> Icons.Outlined.Restaurant
    "local_grocery_store" -> Icons.Outlined.LocalGroceryStore
    "directions_car" -> Icons.Outlined.DirectionsCar
    "movie" -> Icons.Outlined.Movie
    "shopping_bag" -> Icons.Outlined.ShoppingBag
    "receipt_long" -> Icons.Outlined.ReceiptLong
    "medical_services" -> Icons.Outlined.MedicalServices
    "more_horiz" -> Icons.Outlined.MoreHoriz
    "payments" -> Icons.Outlined.Payments
    "savings" -> Icons.Outlined.Savings
    else -> Icons.Filled.Category
}
