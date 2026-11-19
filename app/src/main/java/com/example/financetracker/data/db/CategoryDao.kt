package com.example.financetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY type ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY type ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    /** Seeds defaults without overwriting any existing row (local or synced). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<CategoryEntity>)

    // ---- Sync surface (used by the sync engine) ----

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun dirty(): List<CategoryEntity>

    /** Clears dirty only if the row has not changed since the sync read it. */
    @Query("UPDATE categories SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CategoryEntity>)

    @Query("UPDATE categories SET ownerId = :ownerId, dirty = 1")
    suspend fun claimAll(ownerId: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
