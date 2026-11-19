package com.example.financetracker.data.repo

import com.example.financetracker.data.db.CategoryDao
import com.example.financetracker.data.db.TransactionDao
import com.example.financetracker.data.db.toDomain
import com.example.financetracker.data.db.toEntity
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.CategoryTotal
import com.example.financetracker.domain.model.DashboardSummary
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Room-backed [TransactionRepository]. Every write sets updatedAt = now and dirty = true,
 * then calls [onLocalWrite] (used to kick the background sync).
 */
class OfflineTransactionRepository(
    private val dao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val onLocalWrite: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idGen: () -> String = { UUID.randomUUID().toString() },
) : TransactionRepository {

    private fun nowMillis(): Long = clock.millis()

    override fun observe(
        from: LocalDate?,
        to: LocalDate?,
        categoryId: String?,
        type: TxType?,
        limit: Int?,
    ): Flow<List<Transaction>> =
        dao.observeFiltered(
            from = from?.toEpochDay(),
            to = to?.toEpochDay(),
            categoryId = categoryId,
            type = type?.name,
            limit = limit ?: -1,
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeSummary(from: LocalDate, to: LocalDate, recentLimit: Int): Flow<DashboardSummary> {
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        val expense = TxType.EXPENSE.name
        val income = TxType.INCOME.name
        return combine(
            dao.observeTotal(expense, fromDay, toDay),
            dao.observeTotal(income, fromDay, toDay),
            dao.observeCategoryTotals(expense, fromDay, toDay),
            categoryDao.observeAll(),
            dao.observeFiltered(fromDay, toDay, null, null, recentLimit),
        ) { totalExpense, totalIncome, categoryTotals, categories, recent ->
            val categoriesById = categories.associate { it.id to it.toDomain() }
            DashboardSummary(
                from = from,
                to = to,
                totalExpense = Money(totalExpense),
                totalIncome = Money(totalIncome),
                byCategory = categoryTotals.map { row ->
                    CategoryTotal(
                        category = categoriesById[row.categoryId] ?: unknownCategory(row.categoryId),
                        total = Money(row.total),
                    )
                },
                recent = recent.map { it.toDomain() },
            )
        }
    }

    /** Returns the row even if soft-deleted, so callers can inspect deletedAt. */
    override suspend fun get(id: String): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun add(items: List<NewTransaction>): List<Transaction> {
        if (items.isEmpty()) return emptyList()
        val now = Instant.ofEpochMilli(nowMillis())
        val created = items.map { new ->
            Transaction(
                id = idGen(),
                amount = new.amount,
                currency = new.currency,
                type = new.type,
                title = new.title,
                note = new.note,
                categoryId = new.categoryId,
                occurredOn = new.occurredOn,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                source = new.source,
            )
        }
        dao.insertAll(created.map { it.toEntity(dirty = true) })
        onLocalWrite()
        return created
    }

    override suspend fun update(tx: Transaction): Transaction {
        val existing = dao.getById(tx.id)
            ?: throw NoSuchElementException("No transaction with id ${tx.id}")
        val updated = tx.copy(updatedAt = Instant.ofEpochMilli(nowMillis()))
        dao.update(updated.toEntity(dirty = true, ownerId = existing.ownerId))
        onLocalWrite()
        return updated
    }

    override suspend fun softDelete(id: String) {
        val existing = dao.getById(id) ?: return
        val now = nowMillis()
        dao.update(existing.copy(deletedAt = now, updatedAt = now, dirty = true))
        onLocalWrite()
    }

    override suspend fun restore(id: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(deletedAt = null, updatedAt = nowMillis(), dirty = true))
        onLocalWrite()
    }

    /** Spending under a category row that is not local yet (e.g. pending sync) stays visible. */
    private fun unknownCategory(id: String) =
        Category(id = id, name = "Other", icon = "more_horiz", type = TxType.EXPENSE)
}
