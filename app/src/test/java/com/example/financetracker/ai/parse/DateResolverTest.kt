package com.example.financetracker.ai.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateResolverTest {
    // 2026-11-19 is a Thursday.
    private val today = LocalDate.of(2026, 11, 19)

    private fun header(s: String, t: LocalDate = today) = DateResolver.resolveHeader(s, t)

    @Test fun monthNameForms() {
        val d = LocalDate.of(2026, 11, 19)
        assertEquals(d, header("nov 19"))
        assertEquals(d, header("Nov 19"))
        assertEquals(d, header("19 nov"))
        assertEquals(d, header("november 19th"))
        assertEquals(d, header("19th of November"))
        assertEquals(d, header("nov 19:"))
    }

    @Test fun numericForms() {
        val d = LocalDate.of(2026, 11, 19)
        assertEquals(d, header("19/11"))
        assertEquals(d, header("19-11-2026"))
        assertEquals(d, header("19.11"))
        assertEquals(LocalDate.of(2026, 3, 5), header("5/3")) // day-month order
        assertEquals(LocalDate.of(2025, 11, 19), header("19/11/25"))
    }

    @Test fun relativeForms() {
        assertEquals(today, header("today"))
        assertEquals(LocalDate.of(2026, 11, 18), header("yesterday"))
        assertEquals(LocalDate.of(2026, 11, 17), header("day before yesterday"))
        assertEquals(LocalDate.of(2026, 11, 17), header("2 days ago"))
    }

    @Test fun weekdays() {
        assertEquals(LocalDate.of(2026, 11, 13), header("last friday"))
        assertEquals(LocalDate.of(2026, 11, 16), header("mon"))
        assertEquals(today, header("thu"))
        assertEquals(LocalDate.of(2026, 11, 12), header("last thursday"))
    }

    @Test fun yearRollover() {
        val jan3 = LocalDate.of(2026, 1, 3)
        assertEquals(LocalDate.of(2025, 12, 30), header("dec 30", jan3))
        assertEquals(LocalDate.of(2025, 1, 5), header("jan 5", jan3))
        assertEquals(jan3, header("jan 3", jan3))
    }

    @Test fun invalidDatesAreNotDates() {
        assertNull(header("nov 31"))
        assertNull(header("32/1"))
        assertNull(header("12.50"))
        assertNull(header("tea 15"))
        assertNull(header("sunglasses"))
    }

    @Test fun prefixReturnsRest() {
        val p = DateResolver.matchPrefix("yesterday tea 15", today)!!
        assertEquals(LocalDate.of(2026, 11, 18), p.date)
        assertEquals("tea 15", p.rest)
        assertEquals("lunch 100", DateResolver.matchPrefix("nov 19, lunch 100", today)!!.rest)
        assertNull(DateResolver.matchPrefix("tea 15", today))
    }
}
