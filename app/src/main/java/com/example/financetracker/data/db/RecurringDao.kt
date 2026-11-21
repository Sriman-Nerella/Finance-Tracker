package com.example.financetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE deletedAt IS NULL")
    suspend fun getAllLive(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun getById(id: String): RecurringEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: RecurringEntity)

    @Update
    suspend fun update(item: RecurringEntity): Int

    // ---- Sync surface (used by the sync engine) ----

    @Query("SELECT * FROM recurring WHERE dirty = 1")
    suspend fun dirty(): List<RecurringEntity>

    /** Clears dirty only if the row has not changed since the sync read it. */
    @Query("UPDATE recurring SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT * FROM recurring WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RecurringEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecurringEntity>)

    @Query("UPDATE recurring SET ownerId = :ownerId, dirty = 1")
    suspend fun claimAll(ownerId: String)
}
