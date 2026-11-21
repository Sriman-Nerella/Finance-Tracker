package com.example.financetracker.domain.recurring

import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Recurring
import com.example.financetracker.domain.model.RecurringFrequency
import com.example.financetracker.domain.model.RecurringFrequency.DAILY
import com.example.financetracker.domain.model.RecurringFrequency.HALF_YEARLY
import com.example.financetracker.domain.model.RecurringFrequency.MONTHLY
import com.example.financetracker.domain.model.RecurringFrequency.QUARTERLY
import com.example.financetracker.domain.model.RecurringFrequency.WEEKLY
import com.example.financetracker.domain.model.RecurringFrequency.YEARLY
import com.example.financetracker.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecurringScheduleTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun netflix(start: LocalDate = d(2026, 11, 15), postedThrough: LocalDate? = null, f: RecurringFrequency = MONTHLY) =
        Recurring(
            id = "n", title = "Netflix", amount = Money(499_00), type = TxType.EXPENSE, categoryId = "entertainment",
            note = null, frequency = f, startOn = start, postedThrough = postedThrough,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )

    @Test
    fun netflixIsNotDueBeforeThe15th() {
        assertTrue(netflix().dueDates(d(2026, 11, 14)).isEmpty())
    }

    @Test
    fun netflixIsDueOnThe15thAndOnlyOnce() {
        assertEquals(listOf(d(2026, 11, 15)), netflix().dueDates(d(2026, 11, 15)))
        val posted = netflix(postedThrough = d(2026, 11, 15))
        assertTrue(posted.dueDates(d(2026, 12, 14)).isEmpty())
        assertEquals(listOf(d(2026, 12, 15)), posted.dueDates(d(2026, 12, 20)))
    }

    @Test
    fun catchesUpEverythingMissed() {
        val missed = netflix(postedThrough = d(2026, 11, 15)).dueDates(d(2027, 2, 16))
        assertEquals(listOf(d(2026, 12, 15), d(2027, 1, 15), d(2027, 2, 15)), missed)
    }

    @Test
    fun monthEndIsClampedPerOccurrenceWithoutDrifting() {
        val dates = MONTHLY.occurrencesBetween(d(2027, 1, 31), d(2027, 1, 1), d(2027, 4, 30))
        assertEquals(listOf(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31), d(2027, 4, 30)), dates)
    }

    @Test
    fun everyFrequencyStepsCorrectly() {
        val s = d(2026, 1, 10)
        assertEquals(d(2026, 1, 11), DAILY.occurrence(s, 1))
        assertEquals(d(2026, 1, 17), WEEKLY.occurrence(s, 1))
        assertEquals(d(2026, 2, 10), MONTHLY.occurrence(s, 1))
        assertEquals(d(2026, 4, 10), QUARTERLY.occurrence(s, 1))
        assertEquals(d(2026, 7, 10), HALF_YEARLY.occurrence(s, 1))
        assertEquals(d(2027, 1, 10), YEARLY.occurrence(s, 1))
    }

    @Test
    fun nextAfterSkipsTodaysOccurrence() {
        assertEquals(d(2026, 11, 15), netflix().nextAfter(d(2026, 11, 14)))
        assertEquals(d(2026, 12, 15), netflix().nextAfter(d(2026, 11, 15)))
        assertEquals(d(2026, 12, 15), netflix().nextAfter(d(2026, 11, 21)))
    }

    @Test
    fun futureStartIsNeverDue() {
        assertTrue(netflix(start = d(2026, 12, 15)).dueDates(d(2026, 11, 21)).isEmpty())
    }

    @Test
    fun occurrenceIdsAreStable() {
        assertEquals("rec_n_2026-11-15", occurrenceId("n", d(2026, 11, 15)))
    }
}
