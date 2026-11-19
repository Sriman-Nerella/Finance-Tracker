package com.example.financetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Per-category expense sum, produced by [TransactionDao.observeCategoryTotals]. */
data class CategoryIdTotal(
    val categoryId: String,
    val total: Long,
)

@Dao
interface TransactionDao {
    /**
     * Live rows matching optional filters, newest date first. Pass null for unbounded filters.
     * [limit] must be a non-negative count, or -1 for no limit (SQLite LIMIT semantics).
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (:from IS NULL OR occurredOn >= :from)
          AND (:to IS NULL OR occurredOn <= :to)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:type IS NULL OR type = :type)
        ORDER BY occurredOn DESC, createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeFiltered(
        from: Long?,
        to: Long?,
        categoryId: String?,
        type: String?,
        limit: Int,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE deletedAt IS NULL AND type = :type AND occurredOn BETWEEN :from AND :to")
    fun observeTotal(type: String, from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT categoryId, SUM(amountMinor) AS total FROM transactions
        WHERE deletedAt IS NULL AND type = :type AND occurredOn BETWEEN :from AND :to
        GROUP BY categoryId
        ORDER BY total DESC
        """,
    )
    fun observeCategoryTotals(type: String, from: Long, to: Long): Flow<List<CategoryIdTotal>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    /** Plain insert; fails on duplicate id. Room runs the whole list in one DB transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<TransactionEntity>)

    @Update
    suspend fun update(item: TransactionEntity): Int

    // ---- Sync surface (used by the sync engine) ----

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun dirty(): List<TransactionEntity>

    /** Clears dirty only if the row has not changed since the sync read it (guards against lost updates). */
    @Query("UPDATE transactions SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransactionEntity>)

    @Query("UPDATE transactions SET ownerId = :ownerId, dirty = 1")
    suspend fun claimAll(ownerId: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
