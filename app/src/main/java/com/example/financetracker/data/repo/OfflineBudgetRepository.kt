package com.example.financetracker.data.repo

import com.example.financetracker.data.db.BudgetDao
import com.example.financetracker.data.db.toDomain
import com.example.financetracker.data.db.toEntity
import com.example.financetracker.domain.budget.budgetStatus
import com.example.financetracker.domain.budget.trackedFrom
import com.example.financetracker.domain.model.Budget
import com.example.financetracker.domain.model.BudgetInput
import com.example.financetracker.domain.model.BudgetStatus
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.BudgetRepository
import com.example.financetracker.domain.repo.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Room-backed [BudgetRepository]. Every write marks the row dirty and calls [onLocalWrite] to kick sync. */
class OfflineBudgetRepository(
    private val dao: BudgetDao,
    private val transactions: TransactionRepository,
    private val onLocalWrite: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idGen: () -> String = { UUID.randomUUID().toString() },
) : BudgetRepository {

    override fun observeAll(): Flow<List<Budget>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStatuses(today: LocalDate): Flow<List<BudgetStatus>> =
        observeAll().flatMapLatest { budgets ->
            if (budgets.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val from = budgets.minOf { it.trackedFrom(today) }
            val to = budgets.maxOf { it.period.endOf(it.period.startOf(today)) }
            transactions.observe(from = from, to = to, type = TxType.EXPENSE).map { expenses ->
                budgets.map { budgetStatus(it, today, expenses) }
            }
        }

    override suspend fun get(id: String): Budget? =
        dao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun add(input: BudgetInput): Budget {
        require(input.amount.minor > 0) { "Budget amount must be positive" }
        val now = Instant.ofEpochMilli(clock.millis())
        val budget = Budget(
            id = idGen(),
            categoryId = input.categoryId,
            amount = input.amount,
            period = input.period,
            rollover = input.rollover,
            startOn = input.period.startOf(LocalDate.now(clock)),
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(budget.toEntity(dirty = true))
        onLocalWrite()
        return budget
    }

    override suspend fun update(id: String, input: BudgetInput): Budget? {
        require(input.amount.minor > 0) { "Budget amount must be positive" }
        val existing = dao.getById(id)?.takeIf { it.deletedAt == null } ?: return null
        val old = existing.toDomain()
        val today = LocalDate.now(clock)
        val updated = old.copy(
            categoryId = input.categoryId,
            amount = input.amount,
            period = input.period,
            rollover = input.rollover,
            // Old periods don't line up with a new period length (or a different category), so restart history.
            startOn = if (input.period != old.period || input.categoryId != old.categoryId) {
                input.period.startOf(today)
            } else {
                old.startOn
            },
            updatedAt = Instant.ofEpochMilli(clock.millis()),
        )
        dao.update(updated.toEntity(dirty = true, ownerId = existing.ownerId))
        onLocalWrite()
        return updated
    }

    override suspend fun softDelete(id: String) {
        val existing = dao.getById(id) ?: return
        val now = clock.millis()
        dao.update(existing.copy(deletedAt = now, updatedAt = now, dirty = true))
        onLocalWrite()
    }
}
