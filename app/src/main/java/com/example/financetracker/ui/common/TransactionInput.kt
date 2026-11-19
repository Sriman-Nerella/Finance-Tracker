package com.example.financetracker.ui.common

import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import com.example.financetracker.domain.repo.TransactionRepository
import java.math.RoundingMode
import java.time.LocalDate

/** Validated form output from [TransactionEditorSheet]. [id] is null for a new record. */
data class TransactionInput(
    val id: String?,
    val amount: Money,
    val type: TxType,
    val title: String,
    val note: String?,
    val categoryId: String,
    val occurredOn: LocalDate,
)

/** Creates (MANUAL source) or updates a record. */
suspend fun TransactionRepository.saveInput(input: TransactionInput) {
    val id = input.id
    if (id == null) {
        add(
            listOf(
                NewTransaction(
                    amount = input.amount,
                    type = input.type,
                    title = input.title,
                    note = input.note,
                    categoryId = input.categoryId,
                    occurredOn = input.occurredOn,
                    source = TxSource.MANUAL,
                )
            )
        )
    } else {
        val existing = get(id) ?: return
        update(
            existing.copy(
                amount = input.amount,
                type = input.type,
                title = input.title,
                note = input.note,
                categoryId = input.categoryId,
                occurredOn = input.occurredOn,
            )
        )
    }
}

/**
 * Parses user text like "1,234.5" or "₹200" into paise. Returns null when invalid or not positive.
 */
fun parseAmountToMinor(text: String): Long? {
    val cleaned = text.trim().replace(",", "").replace("₹", "").replace(" ", "")
    val value = cleaned.toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    return runCatching {
        value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()?.takeIf { it > 0L }
}
