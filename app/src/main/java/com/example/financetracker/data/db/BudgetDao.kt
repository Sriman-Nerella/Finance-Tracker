package com.example.financetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    /** Live budgets; the overall budget (null category) sorts first. */
    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL ORDER BY categoryId IS NOT NULL, categoryId, period")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: BudgetEntity)

    @Update
    suspend fun update(item: BudgetEntity): Int

    // ---- Sync surface (used by the sync engine) ----

    @Query("SELECT * FROM budgets WHERE dirty = 1")
    suspend fun dirty(): List<BudgetEntity>

    /** Clears dirty only if the row has not changed since the sync read it. */
    @Query("UPDATE budgets SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT * FROM budgets WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BudgetEntity>)

    @Query("UPDATE budgets SET ownerId = :ownerId, dirty = 1")
    suspend fun claimAll(ownerId: String)
}
