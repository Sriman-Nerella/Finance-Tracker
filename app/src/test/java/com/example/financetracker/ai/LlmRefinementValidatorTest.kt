package com.example.financetracker.ai

import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LlmRefinementValidatorTest {
    private val validator = LlmRefinementValidator()
    private val allowed = DefaultCategories.all
    private val date = LocalDate.of(2026, 11, 19)

    private fun entry(title: String, amount: Long, cat: String = DefaultCategories.OTHER_EXPENSE, note: String? = null) =
        ParsedEntry(title, note, Money(amount), TxType.EXPENSE, cat, date, "$title $amount")

    private val rules = listOf(
        entry("tea", 1500),
        entry("movie", 20000, note = "o.m"),
    )

    @Test
    fun validJsonRefinesTitleNoteCategory() {
        val raw = """[{"index":0,"title":"Tea","note":"","categoryId":"food","type":"EXPENSE"},
            {"index":1,"title":"Movie","note":"o.m","categoryId":"entertainment","type":"EXPENSE"}]"""
        val out = validator.validate(rules, allowed, raw)!!
        assertEquals("Tea", out[0].title)
        assertEquals("food", out[0].categoryId)
        assertEquals("entertainment", out[1].categoryId)
        assertEquals("o.m", out[1].note)
    }

    @Test
    fun amountsAndDatesNeverChange() {
        val raw = """[{"index":0,"title":"Tea","amount":999999,"occurredOn":"2000-01-01","categoryId":"food","type":"EXPENSE"},
            {"index":1,"title":"Movie","amount":1,"categoryId":"entertainment","type":"EXPENSE"}]"""
        val out = validator.validate(rules, allowed, raw)!!
        assertEquals(1500L, out[0].amount.minor)
        assertEquals(20000L, out[1].amount.minor)
        assertEquals(date, out[0].occurredOn)
    }

    @Test
    fun wrongCountIsRejected() {
        val raw = """[{"index":0,"title":"Tea","categoryId":"food","type":"EXPENSE"}]"""
        assertNull(validator.validate(rules, allowed, raw))
    }

    @Test
    fun wrongOrDuplicateIndicesAreRejected() {
        val dup = """[{"index":0,"title":"A"},{"index":0,"title":"B"}]"""
        val off = """[{"index":1,"title":"A"},{"index":2,"title":"B"}]"""
        assertNull(validator.validate(rules, allowed, dup))
        assertNull(validator.validate(rules, allowed, off))
    }

    @Test
    fun invalidCategoryKeepsRuleCategory() {
        val raw = """[{"index":0,"title":"Tea","categoryId":"made_up","type":"EXPENSE"},
            {"index":1,"title":"Movie","categoryId":"salary","type":"EXPENSE"}]"""
        val out = validator.validate(rules, allowed, raw)!!
        assertEquals(DefaultCategories.OTHER_EXPENSE, out[0].categoryId)
        // salary is an INCOME category: inconsistent with EXPENSE, so rule category is kept.
        assertEquals(DefaultCategories.OTHER_EXPENSE, out[1].categoryId)
        assertEquals(TxType.EXPENSE, out[1].type)
        assertEquals("Tea", out[0].title)
    }

    @Test
    fun codeFencedJsonAccepted() {
        val raw = "```json\n[{\"index\":0,\"title\":\"Tea\",\"categoryId\":\"food\",\"type\":\"EXPENSE\"}," +
            "{\"index\":1,\"title\":\"Movie\",\"categoryId\":\"entertainment\",\"type\":\"EXPENSE\"}]\n```"
        val out = validator.validate(rules, allowed, raw)
        assertNotNull(out)
        assertEquals("food", out!![0].categoryId)
    }

    @Test
    fun proseAroundJsonAccepted() {
        val raw = "Here you go: [{\"index\":0,\"title\":\"Tea\"},{\"index\":1,\"title\":\"Movie\"}] done"
        assertNotNull(validator.validate(rules, allowed, raw))
    }

    @Test
    fun badTitleAndNoteFallBackToRuleValues() {
        val long = "x".repeat(41)
        val longNote = "n".repeat(121)
        val raw = """[{"index":0,"title":"$long","note":"$longNote"},{"index":1,"title":"  ","note":null}]"""
        val out = validator.validate(rules, allowed, raw)!!
        assertEquals("tea", out[0].title)
        assertNull(out[0].note)
        assertEquals("movie", out[1].title)
        assertEquals("o.m", out[1].note)
    }

    @Test
    fun garbageIsRejected() {
        assertNull(validator.validate(rules, allowed, "sorry, I can't"))
        assertNull(validator.validate(rules, allowed, "[not json]"))
        assertNull(validator.validate(emptyList(), allowed, "[]"))
    }
}
