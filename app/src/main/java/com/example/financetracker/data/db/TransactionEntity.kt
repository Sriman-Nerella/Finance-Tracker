package com.example.financetracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a record. Amounts in minor units, dates as epoch days, instants as epoch millis.
 * [dirty] = local change not yet pushed to the cloud. [ownerId] = account that owns the row (null = guest).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredOn"]),
        Index(value = ["dirty"]),
        Index(value = ["deletedAt"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    val currency: String,
    /** TxType.name */
    val type: String,
    val title: String,
    val note: String?,
    val categoryId: String,
    /** LocalDate.toEpochDay() */
    val occurredOn: Long,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis. */
    val updatedAt: Long,
    /** Epoch millis; null = live row. */
    val deletedAt: Long?,
    /** TxSource.name */
    val source: String,
    val dirty: Boolean,
    val ownerId: String?,
)
