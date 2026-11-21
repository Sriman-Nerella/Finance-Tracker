package com.example.financetracker.domain.budget

import com.example.financetracker.domain.model.Budget
import com.example.financetracker.domain.model.BudgetStatus
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxType
import java.time.LocalDate

/** Earliest date whose expenses matter for [budget] as of [today]. */
fun Budget.trackedFrom(today: LocalDate): LocalDate {
    val current = period.startOf(today)
    return if (rollover && startOn < current) startOn else current
}

/**
 * Status of [budget] for the period containing [today].
 *
 * [expenses] may hold any transactions; only live expenses that match the budget's category
 * (any category for an overall budget) and fall on or after [Budget.trackedFrom] count.
 *
 * With rollover, every completed period since [Budget.startOn] contributes (amount - spent) to the
 * carry, so unspent money accumulates and overspending reduces later limits.
 */
fun budgetStatus(budget: Budget, today: LocalDate, expenses: List<Transaction>): BudgetStatus {
    val period = budget.period
    val currentStart = period.startOf(today)
    val currentEnd = period.endOf(currentStart)
    val from = budget.trackedFrom(today)

    // Spending per period start, for the matching expenses only.
    val spentByPeriod = HashMap<LocalDate, Long>()
    for (tx in expenses) {
        if (tx.type != TxType.EXPENSE || tx.deletedAt != null) continue
        if (budget.categoryId != null && tx.categoryId != budget.categoryId) continue
        if (tx.occurredOn < from || tx.occurredOn > currentEnd) continue
        val key = period.startOf(tx.occurredOn)
        spentByPeriod[key] = (spentByPeriod[key] ?: 0L) + tx.amount.minor
    }

    var carry = 0L
    if (budget.rollover) {
        var p = period.startOf(budget.startOn)
        while (p < currentStart) {
            carry += budget.amount.minor - (spentByPeriod[p] ?: 0L)
            p = period.next(p)
        }
    }

    return BudgetStatus(
        budget = budget,
        periodStart = currentStart,
        periodEnd = currentEnd,
        carry = Money(carry),
        spent = Money(spentByPeriod[currentStart] ?: 0L),
    )
}
