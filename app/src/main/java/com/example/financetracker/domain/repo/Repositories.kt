package com.example.financetracker.domain.repo

import com.example.financetracker.domain.model.Budget
import com.example.financetracker.domain.model.BudgetInput
import com.example.financetracker.domain.model.BudgetStatus
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.DashboardSummary
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Local (Room) source of truth. Every write marks the row dirty and bumps updatedAt
 * so the sync engine picks it up. Deletes are soft (deletedAt set).
 */
interface TransactionRepository {
    /** Non-deleted records in [from, to] inclusive, newest date first. Null bounds = unbounded. */
    fun observe(
        from: LocalDate? = null,
        to: LocalDate? = null,
        categoryId: String? = null,
        type: TxType? = null,
        limit: Int? = null,
    ): Flow<List<Transaction>>

    fun observeSummary(from: LocalDate, to: LocalDate, recentLimit: Int = 10): Flow<DashboardSummary>

    suspend fun get(id: String): Transaction?

    /** Inserts all in one DB transaction; returns created records. */
    suspend fun add(items: List<NewTransaction>): List<Transaction>

    suspend fun update(tx: Transaction): Transaction

    suspend fun softDelete(id: String)

    suspend fun restore(id: String)
}

/** Recurring budgets, Room-backed and synced like transactions (dirty flag, soft deletes). */
interface BudgetRepository {
    /** Live budgets, overall first, then by category id and period. */
    fun observeAll(): Flow<List<Budget>>

    /** Status of every live budget for the period containing [today]. */
    fun observeStatuses(today: LocalDate = LocalDate.now()): Flow<List<BudgetStatus>>

    suspend fun get(id: String): Budget?

    suspend fun add(input: BudgetInput): Budget

    /** Changing the period restarts the rollover history from the current period. */
    suspend fun update(id: String, input: BudgetInput): Budget?

    suspend fun softDelete(id: String)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun get(id: String): Category?
    /** Inserts DefaultCategories.all if missing. Idempotent. */
    suspend fun seedDefaults()
}
