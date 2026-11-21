package com.example.financetracker.data.sync

import com.example.financetracker.data.db.AppDatabase
import com.example.financetracker.data.db.BudgetEntity
import com.example.financetracker.data.db.CategoryEntity
import com.example.financetracker.data.db.TransactionEntity
import com.example.financetracker.data.prefs.SessionMode
import com.example.financetracker.data.prefs.SessionStore
import com.example.financetracker.data.remote.RemoteDataSource
import com.example.financetracker.data.remote.normalizeTimestamp
import com.example.financetracker.data.remote.toEntity
import com.example.financetracker.data.remote.toRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

/**
 * Push dirty rows, pull rows changed on the server since the stored cursor, merge last-write-wins on updatedAt.
 * Soft-deleted rows are ordinary rows (deletedAt set), so deletions sync the same way.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val remote: RemoteDataSource,
    private val store: SessionStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()

    suspend fun sync(): Result<Unit> = mutex.withLock {
        try {
            val s = store.snapshot()
            val userId = s.userId
            if (s.mode != SessionMode.SIGNED_IN || userId == null) {
                return Result.failure(IllegalStateException("Not signed in"))
            }
            push(userId)
            pull(userId, s.lastPulledCategoriesAt, s.lastPulledTransactionsAt)
            // Last, so a server without the budgets table yet still syncs categories and transactions.
            syncBudgets(userId, s.lastPulledBudgetsAt)
            store.recordSyncSuccess(clock.millis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.recordSyncFailure(e.message ?: e.javaClass.simpleName, clock.millis())
            Result.failure(e)
        }
    }

    private suspend fun push(userId: String) {
        val cats = db.categoryDao()
        val txs = db.transactionDao()

        // Seeded defaults have updatedAt == 0 and were never edited: don't let them overwrite cloud rows.
        val (seeded, dirtyCats) = cats.dirty().partition { it.updatedAt <= 0L }
        seeded.forEach { cats.markClean(it.id, it.updatedAt) }
        dirtyCats.chunked(BATCH).forEach { chunk ->
            remote.upsertCategories(chunk.map { it.toRow(userId) })
            chunk.forEach { cats.markClean(it.id, it.updatedAt) }
        }

        txs.dirty().chunked(BATCH).forEach { chunk ->
            remote.upsertTransactions(chunk.map { it.toRow(userId) })
            chunk.forEach { txs.markClean(it.id, it.updatedAt) }
        }
    }

    private suspend fun pull(userId: String, catCursor: String?, txCursor: String?) {
        val cats = db.categoryDao()
        val txs = db.transactionDao()

        val remoteCats = remote.fetchCategories(catCursor)
        if (remoteCats.isNotEmpty()) {
            val local = cats.getByIds(remoteCats.map { it.id }).associateBy { it.id }
            val merged = remoteCats.mapNotNull { resolveCategory(local[it.id], it.toEntity(userId)) }
            if (merged.isNotEmpty()) cats.upsertAll(merged)
            store.setCursors(transactions = null, categories = maxCursor(remoteCats.map { it.serverUpdatedAt }))
        }

        val remoteTxs = remote.fetchTransactions(txCursor)
        if (remoteTxs.isNotEmpty()) {
            val local = txs.getByIds(remoteTxs.map { it.id }).associateBy { it.id }
            val merged = remoteTxs.mapNotNull { resolve(local[it.id], it.toEntity(userId)) }
            merged.chunked(BATCH).forEach { txs.upsertAll(it) }
            store.setCursors(transactions = maxCursor(remoteTxs.map { it.serverUpdatedAt }), categories = null)
        }
    }

    private suspend fun syncBudgets(userId: String, cursor: String?) {
        val budgets = db.budgetDao()
        budgets.dirty().chunked(BATCH).forEach { chunk ->
            remote.upsertBudgets(chunk.map { it.toRow(userId) })
            chunk.forEach { budgets.markClean(it.id, it.updatedAt) }
        }
        val pulled = remote.fetchBudgets(cursor)
        if (pulled.isNotEmpty()) {
            val local = budgets.getByIds(pulled.map { it.id }).associateBy { it.id }
            val merged = pulled.mapNotNull { resolveBudget(local[it.id], it.toEntity(userId)) }
            if (merged.isNotEmpty()) budgets.upsertAll(merged)
            store.setBudgetCursor(maxCursor(pulled.map { it.serverUpdatedAt }))
        }
    }

    private fun maxCursor(stamps: List<String>): String =
        stamps.map { Instant.parse(it.normalizeTimestamp()) }.max().toString()

    private companion object {
        const val BATCH = 200
    }
}

/**
 * Last-write-wins merge. Returns the row to write locally (always dirty=false), or null to keep local as is.
 * - no local row -> take remote
 * - remote strictly newer -> take remote (even over a dirty local edit: that edit is older)
 * - otherwise (local newer or equal) -> keep local; if dirty it will be pushed next sync
 */
internal fun resolve(local: TransactionEntity?, remote: TransactionEntity): TransactionEntity? = when {
    local == null -> remote.copy(dirty = false)
    remote.updatedAt > local.updatedAt -> remote.copy(dirty = false)
    else -> null
}

internal fun resolveCategory(local: CategoryEntity?, remote: CategoryEntity): CategoryEntity? = when {
    local == null -> remote.copy(dirty = false)
    remote.updatedAt > local.updatedAt -> remote.copy(dirty = false)
    else -> null
}

internal fun resolveBudget(local: BudgetEntity?, remote: BudgetEntity): BudgetEntity? = when {
    local == null -> remote.copy(dirty = false)
    remote.updatedAt > local.updatedAt -> remote.copy(dirty = false)
    else -> null
}
