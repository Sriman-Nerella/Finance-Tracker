package com.example.financetracker.data.repo

import androidx.room.withTransaction
import com.example.financetracker.data.db.AppDatabase
import com.example.financetracker.data.db.toDomain
import com.example.financetracker.data.db.toEntity
import com.example.financetracker.domain.model.Recurring
import com.example.financetracker.domain.model.RecurringInput
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.recurring.dueDates
import com.example.financetracker.domain.recurring.occurrenceId
import com.example.financetracker.domain.repo.RecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Room-backed [RecurringRepository]. Writes mark rows dirty and call [onLocalWrite] to kick sync.
 *
 * Each due occurrence becomes a normal transaction with a deterministic id ([occurrenceId]) and
 * created/updated stamps at the start of its due date. So re-running, or a second device adding the
 * same occurrence, never duplicates it, and any real edit or delete (stamped "now") wins the
 * last-write-wins sync merge.
 */
class OfflineRecurringRepository(
    private val db: AppDatabase,
    private val onLocalWrite: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idGen: () -> String = { UUID.randomUUID().toString() },
) : RecurringRepository {

    private val dao get() = db.recurringDao()
    private val postLock = Mutex()

    override fun observeAll(): Flow<List<Recurring>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): Recurring? =
        dao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun add(input: RecurringInput): Recurring {
        validate(input)
        val now = Instant.ofEpochMilli(clock.millis())
        val item = Recurring(
            id = idGen(),
            title = input.title.trim(),
            amount = input.amount,
            type = input.type,
            categoryId = input.categoryId,
            note = input.note?.trim()?.takeIf { it.isNotEmpty() },
            frequency = input.frequency,
            startOn = input.startOn,
            postedThrough = null,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(item.toEntity(dirty = true))
        onLocalWrite()
        postDue(LocalDate.now(clock))
        return item
    }

    override suspend fun update(id: String, input: RecurringInput): Recurring? {
        validate(input)
        val existing = dao.getById(id)?.takeIf { it.deletedAt == null } ?: return null
        val updated = existing.toDomain().copy(
            title = input.title.trim(),
            amount = input.amount,
            type = input.type,
            categoryId = input.categoryId,
            note = input.note?.trim()?.takeIf { it.isNotEmpty() },
            frequency = input.frequency,
            startOn = input.startOn,
            // postedThrough is kept: occurrences already added are never added again.
            updatedAt = Instant.ofEpochMilli(clock.millis()),
        )
        dao.update(updated.toEntity(dirty = true, ownerId = existing.ownerId))
        onLocalWrite()
        postDue(LocalDate.now(clock))
        return updated
    }

    override suspend fun softDelete(id: String) {
        val existing = dao.getById(id) ?: return
        val now = clock.millis()
        dao.update(existing.copy(deletedAt = now, updatedAt = now, dirty = true))
        onLocalWrite()
    }

    override suspend fun postDue(today: LocalDate): Int = postLock.withLock {
        var added = 0
        var changed = false
        db.withTransaction {
            for (row in dao.getAllLive()) {
                val item = row.toDomain()
                val dates = item.dueDates(today)
                if (dates.isEmpty()) continue
                val txs = dates.map { date ->
                    val at = date.atStartOfDay(clock.zone).toInstant()
                    Transaction(
                        id = occurrenceId(item.id, date),
                        amount = item.amount,
                        currency = item.currency,
                        type = item.type,
                        title = item.title,
                        note = item.note,
                        categoryId = item.categoryId,
                        occurredOn = date,
                        createdAt = at,
                        updatedAt = at,
                        source = TxSource.RECURRING,
                    ).toEntity(dirty = true, ownerId = row.ownerId)
                }
                added += db.transactionDao().insertIgnore(txs).count { it != -1L }
                dao.update(row.copy(postedThrough = dates.last().toEpochDay(), updatedAt = clock.millis(), dirty = true))
                changed = true
            }
        }
        if (changed) onLocalWrite()
        added
    }

    private fun validate(input: RecurringInput) {
        require(input.amount.minor > 0) { "Amount must be positive" }
        require(input.title.isNotBlank()) { "Title is required" }
    }
}
