package com.example.financetracker.ai.parse

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate

/** A date recognised at the start of a text, plus whatever follows it. */
data class DatePrefix(val date: LocalDate, val rest: String)

/**
 * Recognises dates at the START of a line/fragment. Pure Kotlin.
 * Numeric dates are day-month order (Indian). A missing year resolves to the most recent
 * such date that is not after `today`.
 */
object DateResolver {
    private val IC = RegexOption.IGNORE_CASE

    private const val MONTHS =
        "january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec"
    private const val WEEKDAYS =
        "monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun"
    private val MONTH_PREFIXES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private const val END = """(?![\p{L}\d])"""          // the date token must not run into more letters/digits
    private const val ORD = "(?:st|nd|rd|th)?"            // 19th
    private const val YEAR = """(?:\s*,?\s*((?:19|20)\d\d))?""" // optional ", 2026"

    // "nov 19", "november 19th", "nov 19, 2026".  1=month 2=day 3=year
    private val MONTH_FIRST = Regex("""^\s*($MONTHS)\.?\s*(\d{1,2})$ORD$YEAR$END""", IC)

    // "19 nov", "19th of november 2026".  1=day 2=month 3=year
    private val DAY_FIRST = Regex("""^\s*(\d{1,2})$ORD\s*(?:of\s+)?($MONTHS)\.?$YEAR$END""", IC)

    // "19/11", "19-11-2026", "19/11/26".  1=day 2=month 3=year
    private val SLASH = Regex("""^\s*(\d{1,2})[/-](\d{1,2})(?:[/-](\d{4}|\d{2}))?$END""")

    // "19.11", "19.11.2026" (month must be 2 digits so "12.5" is not a date).  1=day 2=month 3=year
    private val DOT = Regex("""^\s*(\d{1,2})\.(\d{2})(?:\.(\d{4}|\d{2}))?$END""")

    // "today", "yesterday", "day before yesterday", "2 days ago".  1=dbY 2=yesterday 3=today 4=N
    private val RELATIVE = Regex(
        """^\s*(?:(day\s+before\s+yesterday)|(yesterday|yday)|(today)|(\d{1,3})\s*days?\s+ago)$END""", IC,
    )

    // "mon", "last friday", "on sat".  1=last 2=weekday
    private val WEEKDAY = Regex("""^\s*(?:(last)\s+|(?:this|on)\s+)?($WEEKDAYS)$END""", IC)

    // Separators between the date and the rest ("nov 19: tea 15", "nov 19 - tea 15").
    private val LEADING_JUNK = Regex("""^(?:[\s,;:]|[-–—](?=\s))+""")

    private val rules: List<Pair<Regex, (MatchResult, LocalDate) -> LocalDate?>> = listOf(
        rule(RELATIVE) { m, t -> relative(m, t) },
        rule(MONTH_FIRST) { m, t -> resolve(m.groupValues[2].toInt(), monthNumber(m.groupValues[1]), m.groups[3]?.value, t) },
        rule(DAY_FIRST) { m, t -> resolve(m.groupValues[1].toInt(), monthNumber(m.groupValues[2]), m.groups[3]?.value, t) },
        rule(SLASH) { m, t -> resolve(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groups[3]?.value, t) },
        rule(DOT) { m, t -> resolve(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groups[3]?.value, t) },
        rule(WEEKDAY) { m, t -> weekday(m, t) },
    )

    private fun rule(regex: Regex, fn: (MatchResult, LocalDate) -> LocalDate?) = regex to fn

    /** If [text] starts with a date, returns it and the remaining text (leading separators removed). */
    fun matchPrefix(text: String, today: LocalDate): DatePrefix? {
        for ((regex, resolver) in rules) {
            val m = regex.find(text) ?: continue
            val date = resolver(m, today) ?: continue
            val rest = text.substring(m.range.last + 1).replace(LEADING_JUNK, "").trim()
            return DatePrefix(date, rest)
        }
        return null
    }

    /** Returns the date when the WHOLE text is a date (a header line), else null. */
    fun resolveHeader(text: String, today: LocalDate): LocalDate? {
        val p = matchPrefix(text, today) ?: return null
        return if (p.rest.none { it.isLetterOrDigit() }) p.date else null
    }

    private fun relative(m: MatchResult, today: LocalDate): LocalDate? {
        val back = when {
            m.groups[1] != null -> 2L
            m.groups[2] != null -> 1L
            m.groups[3] != null -> 0L
            else -> m.groupValues[4].toLong()
        }
        return today.minusDays(back)
    }

    private fun weekday(m: MatchResult, today: LocalDate): LocalDate? {
        val target = when (m.groupValues[2].lowercase().take(3)) {
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            "sat" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
        var diff = (today.dayOfWeek.value - target.value + 7) % 7
        if (m.groups[1] != null && diff == 0) diff = 7 // "last thursday" on a Thursday = a week ago
        return today.minusDays(diff.toLong())
    }

    private fun monthNumber(name: String): Int {
        val n = name.lowercase()
        return MONTH_PREFIXES.indexOfFirst { n.startsWith(it) } + 1
    }

    /** Explicit year -> that date (or null if invalid); no year -> most recent such date not after [today]. */
    private fun resolve(day: Int, month: Int, yearText: String?, today: LocalDate): LocalDate? {
        if (yearText != null) {
            val y = if (yearText.length == 2) 2000 + yearText.toInt() else yearText.toInt()
            return tryOf(y, month, day)
        }
        for (y in today.year downTo today.year - 8) { // 8 years back covers Feb 29
            val d = tryOf(y, month, day) ?: continue
            if (!d.isAfter(today)) return d
        }
        return null
    }

    private fun tryOf(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
}
