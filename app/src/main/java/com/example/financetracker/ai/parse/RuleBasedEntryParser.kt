package com.example.financetracker.ai.parse

import com.example.financetracker.ai.EntryParser
import com.example.financetracker.ai.ParseResult
import com.example.financetracker.ai.ParsedEntry
import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.TxType
import java.time.LocalDate

/**
 * Deterministic chat-entry parser (pure Kotlin, no Android APIs).
 *
 * Flow per line: date header? -> split into items (newline , ; & and) -> per item: inline date,
 * amount, title/note, income detection, category.
 *
 * Date scoping: a header-only line ("nov 19") applies to all following lines until another header.
 * A date at the start of a line with more text ("yesterday tea 15") applies to that line only; a date
 * at the start of a later item in a line applies to that item only.
 */
class RuleBasedEntryParser(
    private val keywords: CategoryKeywords = CategoryKeywords.Default,
) : EntryParser {

    /** One piece of a line; [sep] is "and"/"&" when it was joined to the previous piece by that word. */
    private class Fragment(val text: String, val sep: String?)

    override fun parse(text: String, today: LocalDate): ParseResult {
        val entries = mutableListOf<ParsedEntry>()
        val unparsed = mutableListOf<String>()
        var headerDate = today

        for (rawLine in text.replace(SPACE_LIKE, " ").split(LINE_BREAK)) {
            var rest = rawLine.trim()
            if (rest.isEmpty()) continue

            var lineDate = headerDate
            val head = DateResolver.matchPrefix(rest, today)
            if (head != null) {
                if (isBlankRest(head.rest)) {
                    headerDate = head.date
                    continue
                }
                lineDate = head.date
                rest = head.rest
            }

            val frags = split(rest)
            var i = 0
            while (i < frags.size) {
                var body = frags[i].text.trim()
                i++
                if (body.isEmpty()) continue

                var itemDate = lineDate
                val inline = DateResolver.matchPrefix(body, today)
                if (inline != null) {
                    if (isBlankRest(inline.rest)) { // "tea 15, nov 20, lunch 30": date applies to the rest of the line
                        lineDate = inline.date
                        continue
                    }
                    itemDate = inline.date
                    body = inline.rest
                }

                // "rice and dal 100": the first piece has no amount but the piece joined by and/& does -> one item.
                while (i < frags.size && frags[i].sep != null &&
                    AmountExtractor.extract(stripPlus(body)) == null &&
                    AmountExtractor.extract(stripPlus(frags[i].text.trim())) != null
                ) {
                    body = body + " " + frags[i].sep + " " + frags[i].text.trim()
                    i++
                }

                parseItem(body, itemDate, entries, unparsed)
            }
        }
        return ParseResult(entries, unparsed, refinedByLlm = false)
    }

    private fun parseItem(
        fragment: String,
        date: LocalDate,
        entries: MutableList<ParsedEntry>,
        unparsed: MutableList<String>,
    ) {
        val trimmed = fragment.trim()
        val plus = trimmed.startsWith("+")
        val text = stripPlus(trimmed)
        if (text.none { it.isLetterOrDigit() }) return // pure punctuation/emoji: ignore

        val amount = AmountExtractor.extract(text)
        if (amount == null) {
            unparsed += trimmed
            return
        }

        val remainder = text.removeRange(amount.start, amount.endExclusive)
        val allTokens = remainder.split(WHITESPACE).map { tok -> tok.trim { !it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        val allLower = allTokens.map { it.lowercase() }

        // Income / category detection looks at every word, including filler words we drop below.
        val matched = keywords.categoryFor(allLower)
        val strongIncome = allLower.any { w -> CategoryKeywords.forms(w).any { it in INCOME_WORDS } }
        val weakIncome = "got" in allLower
        val income = plus || strongIncome || matched == DefaultCategories.SALARY || (weakIncome && matched == null)
        val type = if (income) TxType.INCOME else TxType.EXPENSE
        val categoryId = if (income) {
            if (matched == DefaultCategories.SALARY) DefaultCategories.SALARY else DefaultCategories.OTHER_INCOME
        } else {
            matched ?: DefaultCategories.OTHER_EXPENSE
        }

        // Title / note.
        val words = allTokens
            .dropWhile { it.lowercase() in STOP_WORDS }
            .dropLastWhile { it.lowercase() in STOP_WORDS }
        val titleStart = words.indexOfFirst { w -> w.any { it.isLetter() } }
        val fallback = if (income) "Income" else "Expense"
        val title: String
        val note: String?
        if (titleStart < 0) {
            title = fallback
            note = words.joinToString(" ").ifBlank { null }
        } else {
            val len = maxOf(1, keywords.phraseLengthAt(words.map { it.lowercase() }, titleStart))
            title = words.subList(titleStart, titleStart + len).joinToString(" ").replaceFirstChar { it.uppercase() }
            note = (words.take(titleStart) + words.drop(titleStart + len)).joinToString(" ").ifBlank { null }
        }

        entries += ParsedEntry(
            title = title,
            note = note,
            amount = amount.money,
            type = type,
            categoryId = categoryId,
            occurredOn = date,
            sourceText = trimmed,
        )
    }

    /** Splits on ; , & " and " but keeps "1,250" thousands separators together. */
    private fun split(line: String): List<Fragment> {
        val out = mutableListOf<Fragment>()
        var last = 0
        var sep: String? = null
        for (m in SEPARATOR.findAll(line)) {
            out += Fragment(line.substring(last, m.range.first), sep)
            val v = m.value.trim()
            sep = if (v == "&") "&" else if (v.equals("and", ignoreCase = true)) "and" else null
            last = m.range.last + 1
        }
        out += Fragment(line.substring(last), sep)
        return out
    }

    private fun stripPlus(s: String): String = if (s.startsWith("+")) s.substring(1).trim() else s

    private fun isBlankRest(s: String): Boolean = s.none { it.isLetterOrDigit() }

    private companion object {
        val LINE_BREAK = Regex("\r\n|\r|\n| | ")
        val WHITESPACE = Regex("\\s+")
        val SPACE_LIKE = Regex("[   ​]") // non-breaking / zero-width spaces

        // "; " , "&" , " and " , or a comma - but NOT a comma between a digit and exactly 3 digits (1,250).
        val SEPARATOR = Regex("""\s*(?:;|(?<!\d),|,(?!\d{3}(?!\d))|&)\s*|\s+and\s+""", RegexOption.IGNORE_CASE)

        val INCOME_WORDS = setOf("salary", "received", "credited", "refund", "income")

        // Filler words dropped from the start/end of the title phrase ("spent 200 on lunch" -> "Lunch").
        val STOP_WORDS = setOf(
            "for", "on", "at", "of", "to", "in", "the", "a", "an", "from",
            "paid", "spent", "bought", "got", "received", "credited", "gave",
        )
    }
}
