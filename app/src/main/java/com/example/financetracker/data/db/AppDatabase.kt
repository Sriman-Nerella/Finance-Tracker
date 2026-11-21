package com.example.financetracker.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, RecurringEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // added the (spending-limit) budgets table
        AutoMigration(from = 2, to = 3, spec = AppDatabase.DropBudgets::class), // budgets -> recurring
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun recurringDao(): RecurringDao

    /** Spending-limit budgets were replaced by recurring entries. */
    @DeleteTable(tableName = "budgets")
    class DropBudgets : AutoMigrationSpec

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "finance.db")
                .build()
    }
}
