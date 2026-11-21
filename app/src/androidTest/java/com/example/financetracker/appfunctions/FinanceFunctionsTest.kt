package com.example.financetracker.appfunctions

import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.financetracker.ai.ChatEntryPipeline
import com.example.financetracker.ai.ParseResult
import com.example.financetracker.ai.ParsedEntry
import com.example.financetracker.ai.llm.LlmManager
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.CategoryTotal
import com.example.financetracker.domain.model.DashboardSummary
import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.BudgetRepository
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.domain.session.SessionRepository
import com.example.financetracker.domain.session.SyncController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Exercises [FinanceFunctions] against in-memory fakes (no Room, no service binding). */
@RunWith(AndroidJUnit4::class)
class FinanceFunctionsTest {

    private val today = LocalDate.of(2026, 11, 19)
    private val repo = FakeTransactions()
    private val pipeline = FakePipeline()
    private val functions = FinanceFunctions(FakeContainer(repo, pipeline), { today })

    @Test
    fun logExpensesFromText_savesAllEntriesAsAgentSource() = runBlocking {
        pipeline.result = ParseResult(
            entries = listOf(
                entry("Tea", 15.0, DefaultCategories.FOOD),
                entry("Lunch", 200.0, DefaultCategories.FOOD),
            ),
            unparsed = listOf("coffee"),
            refinedByLlm = false,
        )
        val result = functions.logExpensesFromText("tea 15rs, lunch 200rs, coffee")
        assertEquals(2, result.records.size)
        assertEquals(21500L, result.totalExpenseMinor)
        assertEquals("₹215", result.totalExpenseDisplay)
        assertEquals(listOf("coffee"), result.unparsedFragments)
        assertTrue(repo.all.all { it.source == TxSource.AGENT })
    }

    @Test
    fun logExpensesFromText_noAmount_throwsInvalidArgument() = runBlocking {
        pipeline.result = ParseResult(emptyList(), listOf("coffee"), false)
        try {
            functions.logExpensesFromText("coffee")
            fail("expected exception")
        } catch (e: AppFunctionInvalidArgumentException) {
            assertTrue(e.errorMessage?.contains("coffee") == true)
        }
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun addTransaction_fuzzyCategoryAndDefaults() = runBlocking {
        val r = functions.addTransaction(99.5, "Auto", "expense", "transport", null, " ")
        assertEquals("Transport", r.category)
        assertEquals("₹99.50", r.amountDisplay)
        assertEquals(9950L, r.amountMinor)
        assertEquals("2026-11-19", r.date)
        assertEquals(null, r.note)
        assertEquals("expense", r.type)
    }

    @Test
    fun addTransaction_unknownCategoryFallsBackToOther() = runBlocking {
        val r = functions.addTransaction(50000.0, "Pay", "income", "zzz", "2026-11-01", null)
        assertEquals("Other income", r.category)
        assertEquals("₹50,000", r.amountDisplay)
    }

    @Test
    fun addTransaction_rejectsBadInput() = runBlocking {
        assertInvalid { functions.addTransaction(0.0, "x", "expense", null, null, null) }
        assertInvalid { functions.addTransaction(5.0, "x", "gift", null, null, null) }
        assertInvalid { functions.addTransaction(5.0, "x", "expense", null, "19/11/2026", null) }
    }

    @Test
    fun summary_thisMonth_and_customRequiresFrom() = runBlocking {
        functions.addTransaction(100.0, "Lunch", "expense", "food", null, null)
        functions.addTransaction(40.0, "Bus", "expense", "transport", null, null)
        val s = functions.getSpendingSummary("THIS_MONTH", null, null, null)
        assertEquals("2026-11-01", s.from)
        assertEquals("2026-11-30", s.to)
        assertEquals(14000L, s.totalExpenseMinor)
        assertEquals("Food & Drinks", s.byCategory.first().category)
        val food = functions.getSpendingSummary("TODAY", null, null, "food")
        assertEquals(10000L, food.totalExpenseMinor)
        assertInvalid { functions.getSpendingSummary("CUSTOM", null, null, null) }
        assertInvalid { functions.getSpendingSummary("YESTERDAY", null, null, null) }
    }

    @Test
    fun list_newestFirst_andLimit() = runBlocking {
        functions.addTransaction(10.0, "Old", "expense", null, "2026-11-01", null)
        functions.addTransaction(20.0, "New", "expense", null, "2026-11-18", null)
        val all = functions.listTransactions(null, null, null, 0)
        assertEquals(listOf("New", "Old"), all.records.map { it.title })
        assertEquals(1, functions.listTransactions(null, null, null, 1).count)
    }

    @Test
    fun update_changesOnlyProvidedFields_andDeleteIsSoft() = runBlocking {
        val created = functions.addTransaction(200.0, "Lunch", "expense", "food", null, "with team")
        val updated = functions.updateTransaction(created.id, null, 250.0, null, null, null, null)
        assertEquals(25000L, updated.amountMinor)
        assertEquals("Lunch", updated.title)
        assertEquals("with team", updated.note)
        val cleared = functions.updateTransaction(created.id, null, null, null, null, null, "")
        assertEquals(null, cleared.note)

        val deleted = functions.deleteTransaction(created.id)
        assertEquals("Lunch", deleted.title)
        assertTrue(repo.rows.getValue(created.id).deletedAt != null)
        try {
            functions.deleteTransaction(created.id)
            fail("expected not found")
        } catch (_: AppFunctionElementNotFoundException) {
        }
    }

    private suspend fun assertInvalid(block: suspend () -> Unit) {
        try {
            block()
            fail("expected AppFunctionInvalidArgumentException")
        } catch (_: AppFunctionInvalidArgumentException) {
        }
    }

    private fun entry(title: String, amount: Double, categoryId: String) = ParsedEntry(
        title = title,
        note = null,
        amount = Money.ofMajor(amount),
        type = TxType.EXPENSE,
        categoryId = categoryId,
        occurredOn = today,
        sourceText = title,
    )
}

private class FakePipeline : ChatEntryPipeline {
    var result = ParseResult(emptyList(), emptyList(), false)
    override suspend fun parse(text: String, today: LocalDate) = result
}

private class FakeCategories : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> = MutableStateFlow(DefaultCategories.all)
    override suspend fun getAll() = DefaultCategories.all
    override suspend fun get(id: String) = DefaultCategories.all.firstOrNull { it.id == id }
    override suspend fun seedDefaults() = Unit
}

private class FakeContainer(
    override val transactions: TransactionRepository,
    override val chatPipeline: ChatEntryPipeline,
) : AppContainer {
    override val categories: CategoryRepository = FakeCategories()
    override val budgets: BudgetRepository get() = error("unused in tests")
    override val llm: LlmManager get() = error("unused in tests")
    override val session: SessionRepository get() = error("unused in tests")
    override val sync: SyncController get() = error("unused in tests")
}

private class FakeTransactions : TransactionRepository {
    val rows = linkedMapOf<String, Transaction>()
    private val state = MutableStateFlow(0)
    private var seq = 0
    val all: List<Transaction> get() = rows.values.toList()

    private fun live(from: LocalDate?, to: LocalDate?, categoryId: String?, type: TxType?) =
        rows.values.filter {
            it.deletedAt == null &&
                (from == null || !it.occurredOn.isBefore(from)) &&
                (to == null || !it.occurredOn.isAfter(to)) &&
                (categoryId == null || it.categoryId == categoryId) &&
                (type == null || it.type == type)
        }.sortedByDescending { it.occurredOn }

    override fun observe(
        from: LocalDate?, to: LocalDate?, categoryId: String?, type: TxType?, limit: Int?,
    ): Flow<List<Transaction>> = state.map { live(from, to, categoryId, type).let { l -> if (limit != null) l.take(limit) else l } }

    override fun observeSummary(from: LocalDate, to: LocalDate, recentLimit: Int): Flow<DashboardSummary> =
        state.map {
            val list = live(from, to, null, null)
            val cats = DefaultCategories.all.associateBy { it.id }
            DashboardSummary(
                from = from,
                to = to,
                totalExpense = Money(list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount.minor }),
                totalIncome = Money(list.filter { it.type == TxType.INCOME }.sumOf { it.amount.minor }),
                byCategory = list.groupBy { it.categoryId }.map { (id, l) ->
                    CategoryTotal(cats.getValue(id), Money(l.sumOf { it.amount.minor }))
                },
                recent = list.take(recentLimit),
            )
        }

    override suspend fun get(id: String) = rows[id]

    override suspend fun add(items: List<NewTransaction>): List<Transaction> = items.map {
        val now = Instant.ofEpochMilli(1_000L * ++seq)
        Transaction(
            id = "t$seq", amount = it.amount, currency = it.currency, type = it.type, title = it.title,
            note = it.note, categoryId = it.categoryId, occurredOn = it.occurredOn,
            createdAt = now, updatedAt = now, source = it.source,
        ).also { tx -> rows[tx.id] = tx }
    }.also { state.value++ }

    override suspend fun update(tx: Transaction): Transaction {
        rows[tx.id] = tx
        state.value++
        return tx
    }

    override suspend fun softDelete(id: String) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = Instant.now()) }
        state.value++
    }

    override suspend fun restore(id: String) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = null) }
        state.value++
    }
}
