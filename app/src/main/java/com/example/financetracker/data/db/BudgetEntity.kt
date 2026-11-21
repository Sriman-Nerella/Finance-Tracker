package com.example.financetracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    /** Null = overall budget across all expense categories. */
    val categoryId: String?,
    val amountMinor: Long,
    val currency: String,
    /** BudgetPeriod.name */
    val period: String,
    val rollover: Boolean,
    /** Epoch day of the first period counted. */
    val startOn: Long,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis; last-write-wins key for sync. */
    val updatedAt: Long,
    /** Epoch millis; null = live row. */
    val deletedAt: Long?,
    val dirty: Boolean,
    val ownerId: String?,
)
