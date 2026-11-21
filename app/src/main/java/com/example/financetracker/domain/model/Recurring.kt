package com.example.financetracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How often a recurring entry repeats. Each occurrence is counted from the start date, so the 31st stays the 31st where it exists. */
enum class RecurringFrequency(val label: String, private val unit: ChronoUnit, private val step: Long) {
    DAILY("Daily", ChronoUnit.DAYS, 1),
    WEEKLY("Weekly", ChronoUnit.WEEKS, 1),
    MONTHLY("Monthly", ChronoUnit.MONTHS, 1),
    QUARTERLY("Quarterly", ChronoUnit.MONTHS, 3),
    HALF_YEARLY("Half-yearly", ChronoUnit.MONTHS, 6),
    YEARLY("Yearly", ChronoUnit.YEARS, 1);

    /** The [n]th occurrence (0 = [start]). Month-based steps clamp to the month's last day (31 Jan -> 28/29 Feb). */
    fun occurrence(start: LocalDate, n: Long): LocalDate = start.plus(step * n, unit)
}

/**
 * A bill, subscription or income that repeats (e.g. Netflix ₹499 on the 15th of every month).
 * It lives on the Recurring screen; each occurrence is added to Transactions once its date arrives.
 */
data class Recurring(
    val id: String,
    val title: String,
    val amount: Money,
    val type: TxType,
    val categoryId: String,
    val note: String?,
    val frequency: RecurringFrequency,
    /** First occurrence. */
    val startOn: LocalDate,
    /** Last date already added to Transactions; null until the first occurrence is added. */
    val postedThrough: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val currency: String = "INR",
)

/** Input for creating or editing a recurring entry; the repository assigns id and timestamps. */
data class RecurringInput(
    val title: String,
    val amount: Money,
    val type: TxType,
    val categoryId: String,
    val note: String?,
    val frequency: RecurringFrequency,
    val startOn: LocalDate,
)
