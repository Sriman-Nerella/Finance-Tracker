package com.example.financetracker.data.remote

import com.example.financetracker.data.db.BudgetEntity
import com.example.financetracker.data.db.CategoryEntity
import com.example.financetracker.data.db.TransactionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

internal fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()
internal fun String.isoToMillis(): Long = OffsetDateTime.parse(this).toInstant().toEpochMilli()

/** Normalises a PostgREST timestamp ("...+00:00") to Instant.toString() form ("...Z"), safe in query strings. */
internal fun String.normalizeTimestamp(): String = OffsetDateTime.parse(this).toInstant().toString()

/** Push payloads (no server_updated_at: the server stamps it). */
@Serializable
data class TransactionRow(
    @SerialName("user_id") val userId: String,
    val id: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val type: String,
    val title: String,
    val note: String? = null,
    @SerialName("category_id") val categoryId: String,
    @SerialName("occurred_on") val occurredOn: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val source: String,
)

@Serializable
data class CategoryRow(
    @SerialName("user_id") val userId: String,
    val id: String,
    val name: String,
    val icon: String,
    val type: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** Pull payloads. */
@Serializable
data class TransactionPull(
    val id: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val type: String,
    val title: String,
    val note: String? = null,
    @SerialName("category_id") val categoryId: String,
    @SerialName("occurred_on") val occurredOn: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val source: String,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
)

@Serializable
data class CategoryPull(
    val id: String,
    val name: String,
    val icon: String,
    val type: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
)

fun TransactionEntity.toRow(userId: String) = TransactionRow(
    userId = userId, id = id, amountMinor = amountMinor, currency = currency, type = type,
    title = title, note = note, categoryId = categoryId,
    occurredOn = LocalDate.ofEpochDay(occurredOn).toString(),
    createdAt = createdAt.toIso(), updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt?.toIso(), source = source,
)

fun CategoryEntity.toRow(userId: String) = CategoryRow(
    userId = userId, id = id, name = name, icon = icon, type = type,
    updatedAt = updatedAt.toIso(), deletedAt = deletedAt?.toIso(),
)

fun TransactionPull.toEntity(userId: String) = TransactionEntity(
    id = id, amountMinor = amountMinor, currency = currency, type = type, title = title, note = note,
    categoryId = categoryId, occurredOn = LocalDate.parse(occurredOn).toEpochDay(),
    createdAt = createdAt.isoToMillis(), updatedAt = updatedAt.isoToMillis(),
    deletedAt = deletedAt?.isoToMillis(), source = source, dirty = false, ownerId = userId,
)

fun CategoryPull.toEntity(userId: String) = CategoryEntity(
    id = id, name = name, icon = icon, type = type,
    updatedAt = updatedAt.isoToMillis(), deletedAt = deletedAt?.isoToMillis(),
    dirty = false, ownerId = userId,
)

@Serializable
data class BudgetRow(
    @SerialName("user_id") val userId: String,
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val period: String,
    val rollover: Boolean,
    @SerialName("start_on") val startOn: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class BudgetPull(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val period: String,
    val rollover: Boolean,
    @SerialName("start_on") val startOn: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
)

fun BudgetEntity.toRow(userId: String) = BudgetRow(
    userId = userId, id = id, categoryId = categoryId, amountMinor = amountMinor, currency = currency,
    period = period, rollover = rollover, startOn = LocalDate.ofEpochDay(startOn).toString(),
    createdAt = createdAt.toIso(), updatedAt = updatedAt.toIso(), deletedAt = deletedAt?.toIso(),
)

fun BudgetPull.toEntity(userId: String) = BudgetEntity(
    id = id, categoryId = categoryId, amountMinor = amountMinor, currency = currency, period = period,
    rollover = rollover, startOn = LocalDate.parse(startOn).toEpochDay(),
    createdAt = createdAt.isoToMillis(), updatedAt = updatedAt.isoToMillis(),
    deletedAt = deletedAt?.isoToMillis(), dirty = false, ownerId = userId,
)
