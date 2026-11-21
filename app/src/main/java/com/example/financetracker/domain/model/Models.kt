package com.example.financetracker.domain.model

import java.time.Instant
import java.time.LocalDate

/** Shared domain contracts. Do not change shapes without telling the orchestrator. */

enum class TxType { EXPENSE, INCOME }

/** Where a record came from. */
enum class TxSource { MANUAL, CHAT, AGENT, RECURRING }

/** Amounts are always stored in minor units (paise for INR) to avoid floating-point errors. */
@JvmInline
value class Money(val minor: Long) {
    operator fun plus(other: Money) = Money(minor + other.minor)
    val major: Double get() = minor / 100.0

    companion object {
        val ZERO = Money(0)
        fun ofMajor(major: Double): Money = Money(Math.round(major * 100))
    }
}

data class Category(
    val id: String,
    val name: String,
    /** Material icon key, e.g. "restaurant"; resolved to an ImageVector in UI. */
    val icon: String,
    val type: TxType,
)

data class Transaction(
    val id: String,
    val amount: Money,
    val currency: String = "INR",
    val type: TxType,
    val title: String,
    val note: String? = null,
    val categoryId: String,
    val occurredOn: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val source: TxSource,
)

/** Input for creating a record; the repository assigns id, timestamps and sync flags. */
data class NewTransaction(
    val amount: Money,
    val type: TxType,
    val title: String,
    val note: String? = null,
    val categoryId: String,
    val occurredOn: LocalDate,
    val source: TxSource,
    val currency: String = "INR",
)

data class CategoryTotal(val category: Category, val total: Money)

data class DashboardSummary(
    val from: LocalDate,
    val to: LocalDate,
    val totalExpense: Money,
    val totalIncome: Money,
    val byCategory: List<CategoryTotal>,
    val recent: List<Transaction>,
)

/** Default seeded category ids (stable across devices so sync lines up). */
object DefaultCategories {
    const val FOOD = "food"
    const val TRANSPORT = "transport"
    const val ENTERTAINMENT = "entertainment"
    const val SHOPPING = "shopping"
    const val BILLS = "bills"
    const val HEALTH = "health"
    const val GROCERIES = "groceries"
    const val SALARY = "salary"
    const val OTHER_EXPENSE = "other_expense"
    const val OTHER_INCOME = "other_income"

    val all = listOf(
        Category(FOOD, "Food & Drinks", "restaurant", TxType.EXPENSE),
        Category(GROCERIES, "Groceries", "local_grocery_store", TxType.EXPENSE),
        Category(TRANSPORT, "Transport", "directions_car", TxType.EXPENSE),
        Category(ENTERTAINMENT, "Entertainment", "movie", TxType.EXPENSE),
        Category(SHOPPING, "Shopping", "shopping_bag", TxType.EXPENSE),
        Category(BILLS, "Bills & Utilities", "receipt_long", TxType.EXPENSE),
        Category(HEALTH, "Health", "medical_services", TxType.EXPENSE),
        Category(OTHER_EXPENSE, "Other", "more_horiz", TxType.EXPENSE),
        Category(SALARY, "Salary", "payments", TxType.INCOME),
        Category(OTHER_INCOME, "Other income", "savings", TxType.INCOME),
    )
}
