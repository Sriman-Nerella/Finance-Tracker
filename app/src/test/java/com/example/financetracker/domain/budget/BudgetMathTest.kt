package com.example.financetracker.domain.budget

import com.example.financetracker.domain.model.Budget
import com.example.financetracker.domain.model.BudgetPeriod
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BudgetMathTest {

    private val epoch = Instant.EPOCH
    private var seq = 0

    private fun budget(
        period: BudgetPeriod = BudgetPeriod.MONTHLY,
        amount: Long = 10_000_00,
        categoryId: String? = "food",
        rollover: Boolean = false,
        startOn: LocalDate = LocalDate.of(2026, 9, 1),
    ) = Budget("b", categoryId, Money(amount), period, rollover, startOn, epoch, epoch)

    private fun tx(
        on: LocalDate,
        minor: Long,
        categoryId: String = "food",
        type: TxType = TxType.EXPENSE,
        deleted: Boolean = false,
    ) = Transaction(
        id = "t${seq++}", amount = Money(minor), type = type, title = "x", categoryId = categoryId,
        occurredOn = on, createdAt = epoch, updatedAt = epoch,
        deletedAt = if (deleted) epoch else null, source = TxSource.MANUAL,
    )

    @Test
    fun periodBoundaries() {
        val d = LocalDate.of(2026, 11, 21) // Saturday
        assertEquals(LocalDate.of(2026, 11, 16), BudgetPeriod.WEEKLY.startOf(d))
        assertEquals(LocalDate.of(2026, 11, 22), BudgetPeriod.WEEKLY.endOf(BudgetPeriod.WEEKLY.startOf(d)))
        assertEquals(LocalDate.of(2026, 11, 1), BudgetPeriod.MONTHLY.startOf(d))
        assertEquals(LocalDate.of(2026, 11, 30), BudgetPeriod.MONTHLY.endOf(LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 1, 1), BudgetPeriod.YEARLY.startOf(d))
        assertEquals(LocalDate.of(2026, 12, 31), BudgetPeriod.YEARLY.endOf(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun countsOnlyMatchingLiveExpensesInCurrentPeriod() {
        val today = LocalDate.of(2026, 11, 21)
        val s = budgetStatus(
            budget(),
            today,
            listOf(
                tx(LocalDate.of(2026, 11, 2), 1_000_00),
                tx(LocalDate.of(2026, 11, 20), 500_00),
                tx(LocalDate.of(2026, 10, 31), 9_999_00), // previous month
                tx(LocalDate.of(2026, 11, 5), 700_00, categoryId = "transport"), // other category
                tx(LocalDate.of(2026, 11, 5), 300_00, type = TxType.INCOME),
                tx(LocalDate.of(2026, 11, 5), 300_00, deleted = true),
            ),
        )
        assertEquals(Money(1_500_00), s.spent)
        assertEquals(Money(0), s.carry)
        assertEquals(Money(8_500_00), s.left)
        assertEquals(LocalDate.of(2026, 11, 1), s.periodStart)
        assertFalse(s.isOver)
    }

    @Test
    fun overallBudgetCountsEveryCategory() {
        val today = LocalDate.of(2026, 11, 21)
        val s = budgetStatus(
            budget(categoryId = null),
            today,
            listOf(tx(today, 100_00), tx(today, 200_00, categoryId = "transport")),
        )
        assertEquals(Money(300_00), s.spent)
    }

    @Test
    fun rolloverCarriesLeftoverAndOverspend() {
        // Sep: spent 4,000 of 10,000 (+6,000). Oct: spent 13,000 (-3,000). Carry into Nov = +3,000.
        val today = LocalDate.of(2026, 11, 21)
        val s = budgetStatus(
            budget(rollover = true),
            today,
            listOf(
                tx(LocalDate.of(2026, 9, 10), 4_000_00),
                tx(LocalDate.of(2026, 10, 3), 13_000_00),
                tx(LocalDate.of(2026, 11, 1), 2_000_00),
                tx(LocalDate.of(2026, 8, 31), 50_000_00), // before startOn: ignored
            ),
        )
        assertEquals(Money(3_000_00), s.carry)
        assertEquals(Money(13_000_00), s.limit)
        assertEquals(Money(2_000_00), s.spent)
        assertEquals(Money(11_000_00), s.left)
    }

    @Test
    fun withoutRolloverEarlierPeriodsAreIgnored() {
        val today = LocalDate.of(2026, 11, 21)
        val s = budgetStatus(budget(rollover = false), today, listOf(tx(LocalDate.of(2026, 9, 10), 4_000_00)))
        assertEquals(Money(0), s.carry)
        assertEquals(Money(10_000_00), s.limit)
    }

    @Test
    fun weeklyRolloverAndOverspendProgress() {
        // Weeks start Mon 2 Nov 2026. Two full weeks with nothing spent: +2,000 carry.
        val today = LocalDate.of(2026, 11, 18) // Wednesday of the third week
        val s = budgetStatus(
            budget(period = BudgetPeriod.WEEKLY, amount = 1_000_00, rollover = true, startOn = LocalDate.of(2026, 11, 2)),
            today,
            listOf(tx(LocalDate.of(2026, 11, 17), 3_500_00)),
        )
        assertEquals(Money(2_000_00), s.carry)
        assertEquals(Money(3_000_00), s.limit)
        assertTrue(s.isOver)
        assertEquals(1f, s.progress)
        assertEquals(Money(-500_00), s.left)
    }

    @Test
    fun trackedFromStartsAtCurrentPeriodWithoutRollover() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals(LocalDate.of(2026, 11, 1), budget().trackedFrom(today))
        assertEquals(LocalDate.of(2026, 9, 1), budget(rollover = true).trackedFrom(today))
    }
}
