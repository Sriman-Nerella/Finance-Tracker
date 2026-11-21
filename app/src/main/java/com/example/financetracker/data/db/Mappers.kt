package com.example.financetracker.data.db

import com.example.financetracker.domain.model.Budget
import com.example.financetracker.domain.model.BudgetPeriod
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import java.time.Instant
import java.time.LocalDate

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id,
        amount = Money(amountMinor),
        currency = currency,
        type = runCatching { TxType.valueOf(type) }.getOrDefault(TxType.EXPENSE),
        title = title,
        note = note,
        categoryId = categoryId,
        occurredOn = LocalDate.ofEpochDay(occurredOn),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) },
        source = runCatching { TxSource.valueOf(source) }.getOrDefault(TxSource.MANUAL),
    )

/**
 * Converts to a row. [dirty] should be true for any local write. [ownerId] is preserved
 * from the existing row on update (null for new rows).
 */
fun Transaction.toEntity(dirty: Boolean, ownerId: String? = null): TransactionEntity =
    TransactionEntity(
        id = id,
        amountMinor = amount.minor,
        currency = currency,
        type = type.name,
        title = title,
        note = note,
        categoryId = categoryId,
        occurredOn = occurredOn.toEpochDay(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        deletedAt = deletedAt?.toEpochMilli(),
        source = source.name,
        dirty = dirty,
        ownerId = ownerId,
    )

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        icon = icon,
        type = runCatching { TxType.valueOf(type) }.getOrDefault(TxType.EXPENSE),
    )

/** Seed row for a built-in category: not dirty, updatedAt 0 so it never overwrites a cloud row. */
fun Category.toSeedEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        type = type.name,
        updatedAt = 0L,
        deletedAt = null,
        dirty = false,
        ownerId = null,
    )

fun BudgetEntity.toDomain(): Budget =
    Budget(
        id = id,
        categoryId = categoryId,
        amount = Money(amountMinor),
        period = runCatching { BudgetPeriod.valueOf(period) }.getOrDefault(BudgetPeriod.MONTHLY),
        rollover = rollover,
        startOn = LocalDate.ofEpochDay(startOn),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        currency = currency,
    )

fun Budget.toEntity(dirty: Boolean, ownerId: String? = null, deletedAt: Long? = null): BudgetEntity =
    BudgetEntity(
        id = id,
        categoryId = categoryId,
        amountMinor = amount.minor,
        currency = currency,
        period = period.name,
        rollover = rollover,
        startOn = startOn.toEpochDay(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        deletedAt = deletedAt,
        dirty = dirty,
        ownerId = ownerId,
    )
