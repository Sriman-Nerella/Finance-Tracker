package com.example.financetracker.domain.recurring

import com.example.financetracker.domain.model.Recurring
import com.example.financetracker.domain.model.RecurringFrequency
import java.time.LocalDate

/** Guard against runaway loops (e.g. a daily entry started decades ago). ~27 years of daily entries. */
private const val MAX_OCCURRENCES = 10_000

/** Occurrence dates of a schedule within [from, to], inclusive, oldest first. */
fun RecurringFrequency.occurrencesBetween(start: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> {
    if (to < start || to < from) return emptyList()
    val out = ArrayList<LocalDate>()
    var n = 0L
    while (n < MAX_OCCURRENCES) {
        val d = occurrence(start, n++)
        if (d > to) break
        if (d >= from) out += d
    }
    return out
}

/** Dates that are due (on or before [today]) and not yet added to Transactions. */
fun Recurring.dueDates(today: LocalDate): List<LocalDate> {
    val from = postedThrough?.plusDays(1)?.let { maxOf(it, startOn) } ?: startOn
    return frequency.occurrencesBetween(startOn, from, today)
}

/** The next occurrence strictly after [today] (today's, if any, is already in Transactions). */
fun Recurring.nextAfter(today: LocalDate): LocalDate {
    var n = 0L
    while (n < MAX_OCCURRENCES) {
        val d = frequency.occurrence(startOn, n++)
        if (d > today) return d
    }
    return frequency.occurrence(startOn, n)
}

/**
 * Transaction id for one occurrence. Deterministic, so two devices (or a re-run) adding the same
 * occurrence produce the same row instead of a duplicate.
 */
fun occurrenceId(recurringId: String, date: LocalDate): String = "rec_${recurringId}_$date"
