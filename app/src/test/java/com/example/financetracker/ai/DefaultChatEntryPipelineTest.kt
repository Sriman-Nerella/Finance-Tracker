package com.example.financetracker.ai

import com.example.financetracker.ai.llm.LlmAvailability
import com.example.financetracker.ai.llm.LlmBackend
import com.example.financetracker.ai.llm.LlmManager
import com.example.financetracker.ai.llm.OnDeviceLlm
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.CategoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DefaultChatEntryPipelineTest {
    private val date = LocalDate.of(2026, 11, 19)

    private val ruleResult = ParseResult(
        entries = listOf(
            ParsedEntry("tea", null, Money(1500), TxType.EXPENSE, DefaultCategories.OTHER_EXPENSE, date, "tea 15rs"),
            ParsedEntry("movie", "o.m", Money(20000), TxType.EXPENSE, DefaultCategories.OTHER_EXPENSE, date, "movie o.m 200 rs"),
        ),
        unparsed = listOf("coffee"),
        refinedByLlm = false,
    )

    private val parser = object : EntryParser {
        override fun parse(text: String, today: LocalDate) = ruleResult
    }

    private val categories = object : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = flowOf(DefaultCategories.all)
        override suspend fun getAll() = DefaultCategories.all
        override suspend fun get(id: String) = DefaultCategories.all.firstOrNull { it.id == id }
        override suspend fun seedDefaults() {}
    }

    private class FakeLlm(
        private val availability: LlmAvailability = LlmAvailability.Available,
        private val block: suspend (String) -> String?,
    ) : OnDeviceLlm {
        var prompts = mutableListOf<String>()
        override val backend = LlmBackend.GEMINI_NANO
        override suspend fun availability() = availability
        override suspend fun generate(prompt: String): String? {
            prompts += prompt
            return block(prompt)
        }
    }

    private class FakeManager(private val llm: OnDeviceLlm) : LlmManager {
        override val activeBackend: Flow<LlmBackend> = MutableStateFlow(llm.backend)
        override suspend fun current() = llm
        override suspend fun availability(backend: LlmBackend) = llm.availability()
        override fun download(backend: LlmBackend): Flow<Float> = flowOf()
        override val gemmaEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setGemmaEnabled(enabled: Boolean) {}
    }

    private fun pipeline(llm: OnDeviceLlm) = DefaultChatEntryPipeline(parser, FakeManager(llm), categories)

    private val goodJson = """[{"index":0,"title":"Tea","note":"","categoryId":"food","type":"EXPENSE"},
        {"index":1,"title":"Movie","note":"o.m","categoryId":"entertainment","type":"EXPENSE"}]"""

    @Test
    fun refinesWhenLlmReturnsValidJson() = runTest {
        val llm = FakeLlm { goodJson }
        val result = pipeline(llm).parse("tea 15rs, movie o.m 200 rs", date)
        assertTrue(result.refinedByLlm)
        assertEquals("food", result.entries[0].categoryId)
        assertEquals("entertainment", result.entries[1].categoryId)
        assertEquals(listOf("coffee"), result.unparsed)
        assertTrue(llm.prompts.single().contains("entertainment"))
    }

    @Test
    fun llmCannotChangeAmounts() = runTest {
        val llm = FakeLlm {
            """[{"index":0,"title":"Tea","amount":5,"categoryId":"food"},{"index":1,"title":"Movie","amount":7}]"""
        }
        val result = pipeline(llm).parse("x", date)
        assertEquals(1500L, result.entries[0].amount.minor)
        assertEquals(20000L, result.entries[1].amount.minor)
    }

    @Test
    fun wrongCountFallsBackToRules() = runTest {
        val result = pipeline(FakeLlm { """[{"index":0,"title":"Tea"}]""" }).parse("x", date)
        assertFalse(result.refinedByLlm)
        assertEquals(ruleResult.entries, result.entries)
    }

    @Test
    fun invalidCategoryKeepsRuleCategory() = runTest {
        val json = """[{"index":0,"title":"Tea","categoryId":"nope","type":"EXPENSE"},
            {"index":1,"title":"Movie","categoryId":"entertainment","type":"EXPENSE"}]"""
        val result = pipeline(FakeLlm { json }).parse("x", date)
        assertTrue(result.refinedByLlm)
        assertEquals(DefaultCategories.OTHER_EXPENSE, result.entries[0].categoryId)
        assertEquals("entertainment", result.entries[1].categoryId)
    }

    @Test
    fun codeFencedJsonAccepted() = runTest {
        val result = pipeline(FakeLlm { "```json\n$goodJson\n```" }).parse("x", date)
        assertTrue(result.refinedByLlm)
        assertEquals("Tea", result.entries[0].title)
    }

    @Test
    fun llmUnavailableUsesRulesOnly() = runTest {
        val llm = FakeLlm(LlmAvailability.Unavailable) { goodJson }
        val result = pipeline(llm).parse("x", date)
        assertFalse(result.refinedByLlm)
        assertEquals(ruleResult.entries, result.entries)
        assertTrue(llm.prompts.isEmpty())
    }

    @Test
    fun llmReturningNullUsesRulesOnly() = runTest {
        val result = pipeline(FakeLlm { null }).parse("x", date)
        assertFalse(result.refinedByLlm)
        assertEquals(ruleResult.entries, result.entries)
    }

    @Test
    fun llmThrowingUsesRulesOnly() = runTest {
        val result = pipeline(FakeLlm { error("boom") }).parse("x", date)
        assertFalse(result.refinedByLlm)
        assertEquals(ruleResult.entries, result.entries)
    }

    @Test
    fun llmTimeoutUsesRulesOnly() = runTest {
        val result = pipeline(FakeLlm { delay(60_000); goodJson }).parse("x", date)
        assertFalse(result.refinedByLlm)
        assertEquals(ruleResult.entries, result.entries)
    }

    @Test
    fun noEntriesSkipsLlm() = runTest {
        val empty = object : EntryParser {
            override fun parse(text: String, today: LocalDate) = ParseResult(emptyList(), listOf("hi"), false)
        }
        val llm = FakeLlm { goodJson }
        val result = DefaultChatEntryPipeline(empty, FakeManager(llm), categories).parse("hi", date)
        assertTrue(result.entries.isEmpty())
        assertTrue(llm.prompts.isEmpty())
    }
}
