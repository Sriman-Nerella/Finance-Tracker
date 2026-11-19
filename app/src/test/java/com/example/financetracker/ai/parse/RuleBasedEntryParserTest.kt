package com.example.financetracker.ai.parse

import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RuleBasedEntryParserTest {
    private val parser = RuleBasedEntryParser()
    private val today = LocalDate.of(2026, 11, 19)

    @Test fun goldenCase() {
        val r = parser.parse("nov 19\ntea 15rs, lunch 200rs, movie o.m 200 rs", today)
        assertEquals(3, r.entries.size)
        assertEquals(emptyList<String>(), r.unparsed)
        assertFalse(r.refinedByLlm)
        val (tea, lunch, movie) = r.entries
        assertEquals("Tea", tea.title); assertNull(tea.note)
        assertEquals(Money(1500), tea.amount); assertEquals(DefaultCategories.FOOD, tea.categoryId)
        assertEquals("Lunch", lunch.title); assertNull(lunch.note)
        assertEquals(Money(20000), lunch.amount); assertEquals(DefaultCategories.FOOD, lunch.categoryId)
        assertEquals("Movie", movie.title); assertEquals("o.m", movie.note)
        assertEquals(Money(20000), movie.amount); assertEquals(DefaultCategories.ENTERTAINMENT, movie.categoryId)
        r.entries.forEach {
            assertEquals(LocalDate.of(2026, 11, 19), it.occurredOn)
            assertEquals(TxType.EXPENSE, it.type)
        }
        assertEquals("movie o.m 200 rs", movie.sourceText)
    }

    @Test fun yearRollover() {
        val r = parser.parse("dec 30\ntea 10", LocalDate.of(2026, 1, 3))
        assertEquals(1, r.entries.size)
        assertEquals(LocalDate.of(2025, 12, 30), r.entries[0].occurredOn)
    }

    @Test fun yesterdayHeader() {
        val r = parser.parse("yesterday\ncoffee 40", today)
        assertEquals(1, r.entries.size)
        assertEquals("Coffee", r.entries[0].title)
        assertEquals(Money(4000), r.entries[0].amount)
        assertEquals(LocalDate.of(2026, 11, 18), r.entries[0].occurredOn)
    }

    @Test fun noHeaderUsesToday() {
        val r = parser.parse("tea 15", today)
        assertEquals(today, r.entries.single().occurredOn)
    }

    @Test fun multipleHeaders() {
        val r = parser.parse("nov 17\ntea 10\nnov 18\nlunch 100\ntea 20", today)
        assertEquals(3, r.entries.size)
        assertEquals(LocalDate.of(2026, 11, 17), r.entries[0].occurredOn)
        assertEquals(LocalDate.of(2026, 11, 18), r.entries[1].occurredOn)
        assertEquals(LocalDate.of(2026, 11, 18), r.entries[2].occurredOn)
        assertEquals(Money(2000), r.entries[2].amount)
    }

    @Test fun inlineDates() {
        val a = parser.parse("yesterday tea 15", today)
        assertEquals(LocalDate.of(2026, 11, 18), a.entries.single().occurredOn)
        assertEquals("Tea", a.entries.single().title)

        val b = parser.parse("tea 15, yesterday lunch 100", today)
        assertEquals(today, b.entries[0].occurredOn)
        assertEquals(LocalDate.of(2026, 11, 18), b.entries[1].occurredOn)

        val c = parser.parse("nov 19 tea 15rs, lunch 200rs", today)
        assertEquals(2, c.entries.size)
        assertEquals(LocalDate.of(2026, 11, 19), c.entries[1].occurredOn)
    }

    @Test fun thousandsSeparatorDoesNotSplit() {
        val r = parser.parse("rent 1,250rs, tea 15rs", today)
        assertEquals(2, r.entries.size)
        assertEquals("Rent", r.entries[0].title)
        assertEquals(Money(125000), r.entries[0].amount)
        assertEquals(DefaultCategories.BILLS, r.entries[0].categoryId)
        assertEquals(Money(1500), r.entries[1].amount)
    }

    @Test fun rupeeSymbolWithDecimals() {
        val e = parser.parse("electricity bill ₹1,250.50", today).entries.single()
        assertEquals(Money(125050), e.amount)
        assertEquals("Electricity", e.title)
        assertEquals("bill", e.note)
        assertEquals(DefaultCategories.BILLS, e.categoryId)
    }

    @Test fun kiloAmount() {
        val e = parser.parse("petrol 1.5k", today).entries.single()
        assertEquals(Money(150000), e.amount)
        assertEquals(DefaultCategories.TRANSPORT, e.categoryId)
    }

    @Test fun bareNumber() {
        val e = parser.parse("auto 80", today).entries.single()
        assertEquals("Auto", e.title)
        assertEquals(Money(8000), e.amount)
        assertEquals(DefaultCategories.TRANSPORT, e.categoryId)
    }

    @Test fun quantityStaysOutOfAmount() {
        val e = parser.parse("2 coffees 120rs", today).entries.single()
        assertEquals(Money(12000), e.amount)
        assertEquals("Coffees", e.title)
        assertEquals("2", e.note)
        assertEquals(DefaultCategories.FOOD, e.categoryId)
    }

    @Test fun multiWordKeywordTitle() {
        val e = parser.parse("ice cream 60rs", today).entries.single()
        assertEquals("Ice cream", e.title)
        assertNull(e.note)
        assertEquals(DefaultCategories.FOOD, e.categoryId)

        val f = parser.parse("auto rickshaw 45", today).entries.single()
        assertEquals("Auto rickshaw", f.title)
        assertEquals(DefaultCategories.TRANSPORT, f.categoryId)
    }

    @Test fun laterKeywordCategorisesButFirstWordIsTitle() {
        val e = parser.parse("team dinner 500rs", today).entries.single()
        assertEquals("Team", e.title)
        assertEquals("dinner", e.note)
        assertEquals(DefaultCategories.FOOD, e.categoryId)
    }

    @Test fun incomeSalary() {
        val e = parser.parse("salary 50000", today).entries.single()
        assertEquals(TxType.INCOME, e.type)
        assertEquals(DefaultCategories.SALARY, e.categoryId)
        assertEquals("Salary", e.title)
        assertEquals(Money(5000000), e.amount)
    }

    @Test fun incomeLeadingPlusAndReceived() {
        val a = parser.parse("+500 gift", today).entries.single()
        assertEquals(TxType.INCOME, a.type)
        assertEquals(DefaultCategories.OTHER_INCOME, a.categoryId)
        assertEquals("Gift", a.title)
        assertEquals(Money(50000), a.amount)

        val b = parser.parse("received 2000 from dad", today).entries.single()
        assertEquals(TxType.INCOME, b.type)
        assertEquals(DefaultCategories.OTHER_INCOME, b.categoryId)
        assertEquals("Dad", b.title)

        val c = parser.parse("refund amazon 300rs", today).entries.single()
        assertEquals(TxType.INCOME, c.type)
        assertEquals(DefaultCategories.OTHER_INCOME, c.categoryId)
    }

    @Test fun gotWithExpenseKeywordStaysExpense() {
        val e = parser.parse("got milk 50", today).entries.single()
        assertEquals(TxType.EXPENSE, e.type)
        assertEquals(DefaultCategories.GROCERIES, e.categoryId)
        assertEquals("Milk", e.title)
    }

    @Test fun unknownWordFallsBackToOther() {
        val e = parser.parse("gift 500", today).entries.single()
        assertEquals(DefaultCategories.OTHER_EXPENSE, e.categoryId)
    }

    @Test fun unparsedFragment() {
        val r = parser.parse("tea 15rs, coffee", today)
        assertEquals(1, r.entries.size)
        assertEquals(listOf("coffee"), r.unparsed)
    }

    @Test fun zeroAmountIsUnparsed() {
        val r = parser.parse("tea 0", today)
        assertEquals(0, r.entries.size)
        assertEquals(listOf("tea 0"), r.unparsed)
    }

    @Test fun andAndAmpersandSplit() {
        val r = parser.parse("tea 15 and coffee 20 & snacks 30; bus 10", today)
        assertEquals(4, r.entries.size)
        assertEquals(listOf("Tea", "Coffee", "Snacks", "Bus"), r.entries.map { it.title })
    }

    @Test fun andInsideOneItemIsKept() {
        val r = parser.parse("rice and dal 100", today)
        assertEquals(1, r.entries.size)
        assertEquals(Money(10000), r.entries[0].amount)
        assertEquals("Rice", r.entries[0].title)
    }

    @Test fun emptyAndHeaderOnlyInput() {
        for (s in listOf("", "   ", "\n \n", "nov 19", "yesterday\n\n")) {
            val r = parser.parse(s, today)
            assertEquals(s, 0, r.entries.size)
            assertEquals(s, 0, r.unparsed.size)
        }
    }

    @Test fun emojiAndExtraWhitespace() {
        val r = parser.parse("  🍕 pizza   300rs ,  ☕ coffee 50  \r\n\r\n", today)
        assertEquals(2, r.entries.size)
        assertEquals("Pizza", r.entries[0].title)
        assertNull(r.entries[0].note)
        assertEquals(Money(30000), r.entries[0].amount)
        assertEquals(DefaultCategories.FOOD, r.entries[0].categoryId)
        assertEquals("Coffee", r.entries[1].title)
        assertEquals(Money(5000), r.entries[1].amount)
    }

    @Test fun pureEmojiOrPunctuationIsIgnored() {
        val r = parser.parse("😀\n...\ntea 10", today)
        assertEquals(1, r.entries.size)
        assertEquals(0, r.unparsed.size)
    }

    @Test fun amountOnlyGetsFallbackTitle() {
        val e = parser.parse("200rs", today).entries.single()
        assertEquals("Expense", e.title)
        assertEquals(DefaultCategories.OTHER_EXPENSE, e.categoryId)
    }

    @Test fun fillerWordsAreDropped() {
        val e = parser.parse("spent 200 on lunch", today).entries.single()
        assertEquals("Lunch", e.title)
        assertNull(e.note)
        assertEquals(Money(20000), e.amount)
    }
}
