package com.example.financetracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Material icon key. */
    val icon: String,
    /** TxType.name */
    val type: String,
    /** Epoch millis. Seeded defaults use 0 so they never overwrite cloud rows. */
    val updatedAt: Long,
    /** Epoch millis; null = live row. */
    val deletedAt: Long?,
    val dirty: Boolean,
    val ownerId: String?,
)
