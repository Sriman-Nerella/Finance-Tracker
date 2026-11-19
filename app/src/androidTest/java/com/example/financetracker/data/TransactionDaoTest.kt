package com.example.financetracker.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financetracker.data.db.AppDatabase
import com.example.financetracker.data.db.TransactionDao
import com.example.financetracker.data.db.TransactionEntity
import com.example.financetracker.data.repo.OfflineTransactionRepository
import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class TransactionDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        id: String,
        occurredOn: Long,
        createdAt: Long,
        updatedAt: Long = createdAt,
        dirty: Boolean = true,
        deletedAt: Long? = null,
        ownerId: String? = null,
    ) = TransactionEntity(
        id = id,
        amountMinor = 1_000,
        currency = "INR",
        type = TxType.EXPENSE.name,
        title = id,
        note = null,
        categoryId = DefaultCategories.FOOD,
        occurredOn = occurredOn,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        source = TxSource.MANUAL.name,
        dirty = dirty,
        ownerId = ownerId,
    )

    @Test
    fun observe_ordersByOccurredOnThenCreatedAt_descending() = runBlocking {
        dao.insertAll(
            listOf(
                row("a", occurredOn = 10, createdAt = 1),
                row("b", occurredOn = 12, createdAt = 1),
                row("c", occurredOn = 12, createdAt = 2),
            ),
        )

        val ids = dao.observeFiltered(null, null, null, null, -1).first().map { it.id }

        assertEquals(listOf("c", "b", "a"), ids)
    }

    @Test
    fun softDelete_hidesRowFromObserve_butGetStillReturnsIt() = runBlocking {
        val repo = OfflineTransactionRepository(dao, db.categoryDao())
        val created = repo.add(
            listOf(
                NewTransaction(
                    amount = Money(500),
                    type = TxType.EXPENSE,
                    title = "tea",
                    categoryId = DefaultCategories.FOOD,
                    occurredOn = LocalDate.of(2026, 11, 19),
                    source = TxSource.CHAT,
                ),
                NewTransaction(
                    amount = Money(20_000),
                    type = TxType.INCOME,
                    title = "salary",
                    categoryId = DefaultCategories.SALARY,
                    occurredOn = LocalDate.of(2026, 11, 1),
                    source = TxSource.MANUAL,
                ),
            ),
        )
        val deletedId = created.first { it.title == "tea" }.id

        repo.softDelete(deletedId)

        val visible = repo.observe().first().map { it.id }
        assertEquals(listOf(created.first { it.title == "salary" }.id), visible)
        assertTrue(repo.get(deletedId)?.deletedAt != null)
    }

    @Test
    fun markClean_withStaleUpdatedAt_doesNotClearDirty() = runBlocking {
        dao.insertAll(listOf(row("x", occurredOn = 5, createdAt = 1, updatedAt = 100, dirty = true)))

        dao.markClean("x", updatedAt = 99)
        assertEquals(listOf("x"), dao.dirty().map { it.id })

        dao.markClean("x", updatedAt = 100)
        assertTrue(dao.dirty().isEmpty())
    }

    @Test
    fun claimAll_setsOwnerAndMarksEveryRowDirty() = runBlocking {
        dao.upsertAll(
            listOf(
                row("p", occurredOn = 1, createdAt = 1, dirty = false),
                row("q", occurredOn = 2, createdAt = 2, dirty = false),
            ),
        )

        dao.claimAll("user-1")

        val rows = dao.getByIds(listOf("p", "q"))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.ownerId == "user-1" && it.dirty })
    }
}
