package com.example.financetracker.ai.parse

import com.example.financetracker.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** An amount found in text; [start]..[endExclusive] is the span (number + currency marker) to remove from the text. */
data class AmountMatch(val money: Money, val start: Int, val endExclusive: Int)

/**
 * Finds the rupee amount in one item fragment. Pure Kotlin.
 *
 * Priority: a number attached to a currency marker (or "k" suffix) wins; otherwise a bare number
 * is used only when it is the single number in the text.
 */
object AmountExtractor {
    private val IC = RegexOption.IGNORE_CASE

    // A number: "1,250.50" (thousands separators) or "15" / "1.5".
    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?"""

    // Not glued to the tail of another number.
    private const val NOT_PART = """(?<![\d.,])"""

    // Not a negative number like " -50" (a hyphen at start / after whitespace directly before the digits).
    private const val NOT_NEG = """(?<!(?:^|\s)-)"""

    // Prefix marker: "rs 15", "rs.15", "Rs15", "inr 15", "₹15", "₹ 1,250.50", "rs 2k". Group 1 = number, group 2 = k.
    private val PREFIXED = Regex("""(?:(?<!\p{L})(?:rs|inr)\.?|₹)\s*($NUM)(?:(k)(?!\p{L}))?""", IC)

    // Suffix marker: "15rs", "15 rs", "15/-", "15 inr", "200 rupees", "200 bucks", "2k rs". Group 1 = number, group 2 = k.
    private val SUFFIXED = Regex(
        """$NOT_PART$NOT_NEG($NUM)\s*(?:(k)\s*)?(?:₹|(?:rs|rupees?|inr|bucks?)(?!\p{L})|/-)""", IC,
    )

    // "1.5k", "2k" with no other marker. Group 1 = number.
    private val KILO = Regex("""(?<![\p{L}\d.,])$NOT_NEG($NUM)k(?![\p{L}\d])""", IC)

    // Any standalone number (not glued to letters, e.g. "3d" and "2nd" are ignored).
    private val BARE = Regex("""(?<![\p{L}\d.,])$NOT_NEG($NUM)(?![\p{L}\d])""")

    private val MAX_MAJOR = BigDecimal("1000000000") // 100 crore sanity cap

    fun extract(text: String): AmountMatch? {
        val marked: Pair<MatchResult, Boolean>? = listOfNotNull(
            PREFIXED.find(text)?.let { it to (it.groups[2] != null) },
            SUFFIXED.find(text)?.let { it to (it.groups[2] != null) },
            KILO.find(text)?.let { it to true },
        ).minByOrNull { it.first.range.first }

        if (marked != null) {
            val (m, kilo) = marked
            val money = toMoney(m.groupValues[1], kilo) ?: return null
            return AmountMatch(money, m.range.first, m.range.last + 1)
        }

        val numbers = BARE.findAll(text).toList()
        if (numbers.size != 1) return null
        val m = numbers[0]
        val money = toMoney(m.groupValues[1], false) ?: return null
        return AmountMatch(money, m.range.first, m.range.last + 1)
    }

    private fun toMoney(raw: String, kilo: Boolean): Money? {
        var value = raw.replace(",", "").toBigDecimalOrNull() ?: return null
        if (kilo) value = value.multiply(BigDecimal(1000))
        if (value.signum() <= 0 || value > MAX_MAJOR) return null
        val minor = value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        return if (minor <= 0) null else Money(minor)
    }
}
