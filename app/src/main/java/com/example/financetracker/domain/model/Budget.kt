package com.example.financetracker.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How often a budget resets. Periods are calendar-aligned: weeks start Monday, months on the 1st, years on 1 Jan. */
enum class BudgetPeriod(val label: String, val adjective: String) {
    WEEKLY("Week", "Weekly"),
    MONTHLY("Month", "Monthly"),
    YEARLY("Year", "Yearly");

    /** First day of the period that contains [date]. */
    fun startOf(date: LocalDate): LocalDate = when (this) {
        WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTHLY -> date.withDayOfMonth(1)
        YEARLY -> date.withDayOfYear(1)
    }

    /** First day of the period after the one starting on [start]. */
    fun next(start: LocalDate): LocalDate = when (this) {
        WEEKLY -> start.plusWeeks(1)
        MONTHLY -> start.plusMonths(1)
        YEARLY -> start.plusYears(1)
    }

    /** Last day of the period starting on [start]. */
    fun endOf(start: LocalDate): LocalDate = next(start).minusDays(1)
}

/**
 * A spending limit that repeats every [period].
 * [categoryId] null means an overall budget across all expenses.
 * With [rollover], what was left (or overspent) in earlier periods since [startOn] adjusts the current limit.
 */
data class Budget(
    val id: String,
    val categoryId: String?,
    val amount: Money,
    val period: BudgetPeriod,
    val rollover: Boolean,
    /** First day of the first period counted for rollover. Always a period start. */
    val startOn: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val currency: String = "INR",
)

/** Input for creating or editing a budget; the repository assigns id, timestamps and startOn. */
data class BudgetInput(
    val categoryId: String?,
    val amount: Money,
    val period: BudgetPeriod,
    val rollover: Boolean,
)

/** Where a budget stands in the period containing "today". */
data class BudgetStatus(
    val budget: Budget,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** Leftover (positive) or overspend (negative) brought in from earlier periods; zero without rollover. */
    val carry: Money,
    val spent: Money,
) {
    /** This period's limit: the base amount plus [carry]. May be zero or negative after heavy overspending. */
    val limit: Money get() = Money(budget.amount.minor + carry.minor)
    val left: Money get() = Money(limit.minor - spent.minor)
    val isOver: Boolean get() = spent.minor > limit.minor

    /** Share of the limit used, 0..1 (1 when the limit is used up or not positive). */
    val progress: Float
        get() = if (limit.minor <= 0) 1f else (spent.minor.toFloat() / limit.minor).coerceIn(0f, 1f)
}
