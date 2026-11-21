package com.example.financetracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    /** TxType.name */
    val type: String,
    val categoryId: String,
    val note: String?,
    /** RecurringFrequency.name */
    val frequency: String,
    /** Epoch day of the first occurrence. */
    val startOn: Long,
    /** Epoch day of the last occurrence added to Transactions; null = none yet. */
    val postedThrough: Long?,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis; last-write-wins key for sync. */
    val updatedAt: Long,
    /** Epoch millis; null = live row. */
    val deletedAt: Long?,
    val dirty: Boolean,
    val ownerId: String?,
)
