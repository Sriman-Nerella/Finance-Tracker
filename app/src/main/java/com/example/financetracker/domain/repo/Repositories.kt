package com.example.financetracker.domain.repo

import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.DashboardSummary
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.Recurring
import com.example.financetracker.domain.model.RecurringInput
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

/** Recurring bills/subscriptions/income. Room-backed and synced like transactions (dirty flag, soft deletes). */
interface RecurringRepository {
    /** Live entries, by title. */
    fun observeAll(): Flow<List<Recurring>>

    suspend fun get(id: String): Recurring?

    suspend fun add(input: RecurringInput): Recurring

    suspend fun update(id: String, input: RecurringInput): Recurring?

    /** Stops future occurrences. Entries already in Transactions stay. */
    suspend fun softDelete(id: String)

    /**
     * Adds every occurrence dated on or before [today] that isn't in Transactions yet.
     * Safe to call often (app start/resume, after sync): already-added occurrences are skipped.
     * Returns how many transactions were added.
     */
    suspend fun postDue(today: LocalDate = LocalDate.now()): Int
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun get(id: String): Category?
    /** Inserts DefaultCategories.all if missing. Idempotent. */
    suspend fun seedDefaults()
}
