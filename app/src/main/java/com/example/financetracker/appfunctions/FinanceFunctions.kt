package com.example.financetracker.appfunctions

import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.example.financetracker.di.AppContainer
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.Transaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.model.TxType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Plain-Kotlin implementation behind the AppFunctions service, so it can be tested with a
 * fake [AppContainer]. Validation and AppFunction exceptions live here.
 */
class FinanceFunctions(
    private val container: AppContainer,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend fun logExpensesFromText(text: String): LogExpensesResult {
        if (text.isBlank()) throw AppFunctionInvalidArgumentException(
            "text is empty. Provide entries with amounts, for example: tea 15rs, lunch 200rs."
        )
        val parsed = container.chatPipeline.parse(text, today())
        if (parsed.entries.isEmpty()) {
            val hint = if (parsed.unparsed.isEmpty()) "" else
                " Fragments without an amount: ${parsed.unparsed.joinToString("; ") { "\"$it\"" }}."
            throw AppFunctionInvalidArgumentException(
                "No amount found in the text, so nothing was saved. Include an amount with each item, " +
                    "for example \"tea 15 rs, lunch 200 rs\".$hint"
            )
        }
        val created = container.transactions.add(
            parsed.entries.map {
                NewTransaction(
                    amount = it.amount,
                    type = it.type,
                    title = it.title,
                    note = it.note,
                    categoryId = it.categoryId,
                    occurredOn = it.occurredOn,
                    source = TxSource.AGENT,
                )
            }
        )
        val categories = categoriesById()
        val expense = created.filter { it.type == TxType.EXPENSE }.sumOf { it.amount.minor }
        val income = created.filter { it.type == TxType.INCOME }.sumOf { it.amount.minor }
        return LogExpensesResult(
            records = created.map { it.toRecord(categories) },
            totalExpenseDisplay = formatRupees(expense),
            totalExpenseMinor = expense,
            totalIncomeDisplay = formatRupees(income),
            unparsedFragments = parsed.unparsed,
        )
    }

    suspend fun addTransaction(
        amountRupees: Double,
        title: String,
        type: String,
        category: String?,
        date: String?,
        note: String?,
    ): TransactionRecord {
        if (!amountRupees.isFinite() || amountRupees <= 0.0) throw AppFunctionInvalidArgumentException(
            "amountRupees must be a positive number of rupees, for example 150 or 99.5."
        )
        val txType = parseType(type)
        if (title.isBlank()) throw AppFunctionInvalidArgumentException("title must not be blank, for example \"Lunch\".")
        val cats = container.categories.getAll()
        val categoryId = resolveCategory(category, txType, cats)?.id ?: otherId(txType)
        val created = container.transactions.add(
            listOf(
                NewTransaction(
                    amount = Money.ofMajor(amountRupees),
                    type = txType,
                    title = title.trim(),
                    note = note?.takeIf { it.isNotBlank() }?.trim(),
                    categoryId = categoryId,
                    occurredOn = parseDate(date, "date") ?: today(),
                    source = TxSource.AGENT,
                )
            )
        ).first()
        return created.toRecord(cats.associateBy { it.id })
    }

    suspend fun getSpendingSummary(
        period: String,
        from: String?,
        to: String?,
        category: String?,
    ): SpendingSummary {
        val now = today()
        val (start, end) = when (period.trim().uppercase(Locale.ROOT).replace(' ', '_')) {
            "TODAY" -> now to now
            "THIS_WEEK" -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to
                now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            "THIS_MONTH" -> now.withDayOfMonth(1) to now.with(TemporalAdjusters.lastDayOfMonth())
            "LAST_MONTH" -> {
                val first = now.minusMonths(1).withDayOfMonth(1)
                first to first.with(TemporalAdjusters.lastDayOfMonth())
            }
            "CUSTOM" -> {
                val s = parseDate(from, "from") ?: throw AppFunctionInvalidArgumentException(
                    "from is required when period is CUSTOM. Use yyyy-MM-dd."
                )
                s to (parseDate(to, "to") ?: now)
            }
            else -> throw AppFunctionInvalidArgumentException(
                "Unknown period \"$period\". Use TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH or CUSTOM."
            )
        }
        if (end.isBefore(start)) throw AppFunctionInvalidArgumentException("to must not be before from.")
        val summary = container.transactions.observeSummary(start, end).first()
        var rows = summary.byCategory
        var expense = summary.totalExpense.minor
        var income = summary.totalIncome.minor
        if (!category.isNullOrBlank()) {
            val cats = container.categories.getAll()
            val match = resolveCategory(category, null, cats) ?: throw unknownCategory(category, cats)
            rows = rows.filter { it.category.id == match.id }
            expense = rows.filter { it.category.type == TxType.EXPENSE }.sumOf { it.total.minor }
            income = rows.filter { it.category.type == TxType.INCOME }.sumOf { it.total.minor }
        }
        return SpendingSummary(
            from = start.toString(),
            to = end.toString(),
            totalExpenseDisplay = formatRupees(expense),
            totalExpenseMinor = expense,
            totalIncomeDisplay = formatRupees(income),
            totalIncomeMinor = income,
            byCategory = rows.sortedByDescending { it.total.minor }.map {
                CategoryAmount(
                    category = it.category.name,
                    type = typeName(it.category.type),
                    totalDisplay = formatRupees(it.total.minor),
                    totalMinor = it.total.minor,
                )
            },
        )
    }

    suspend fun listTransactions(
        from: String?,
        to: String?,
        category: String?,
        limit: Int,
    ): TransactionList {
        val start = parseDate(from, "from")
        val end = parseDate(to, "to")
        if (start != null && end != null && end.isBefore(start)) {
            throw AppFunctionInvalidArgumentException("to must not be before from.")
        }
        val cats = container.categories.getAll()
        val categoryId = if (category.isNullOrBlank()) null else
            (resolveCategory(category, null, cats) ?: throw unknownCategory(category, cats)).id
        val max = if (limit <= 0) DEFAULT_LIMIT else minOf(limit, MAX_LIMIT)
        val items = container.transactions.observe(start, end, categoryId, null, max).first()
        val byId = cats.associateBy { it.id }
        val records = items.sortedWith(
            compareByDescending<Transaction> { it.occurredOn }.thenByDescending { it.createdAt }
        ).take(max).map { it.toRecord(byId) }
        return TransactionList(records, records.size)
    }

    suspend fun updateTransaction(
        id: String,
        title: String?,
        amountRupees: Double?,
        type: String?,
        category: String?,
        date: String?,
        note: String?,
    ): TransactionRecord {
        val existing = findActive(id)
        val newType = type?.takeIf { it.isNotBlank() }?.let { parseType(it) } ?: existing.type
        if (amountRupees != null && (!amountRupees.isFinite() || amountRupees <= 0.0)) {
            throw AppFunctionInvalidArgumentException("amountRupees must be a positive number of rupees.")
        }
        val cats = container.categories.getAll()
        val currentCategory = cats.firstOrNull { it.id == existing.categoryId }
        val categoryId = when {
            !category.isNullOrBlank() -> resolveCategory(category, newType, cats)?.id ?: otherId(newType)
            currentCategory != null && currentCategory.type == newType -> existing.categoryId
            else -> otherId(newType)
        }
        val updated = existing.copy(
            title = title?.takeIf { it.isNotBlank() }?.trim() ?: existing.title,
            amount = amountRupees?.let { Money.ofMajor(it) } ?: existing.amount,
            type = newType,
            categoryId = categoryId,
            occurredOn = parseDate(date, "date") ?: existing.occurredOn,
            // An empty string clears the note; null leaves it unchanged.
            note = when {
                note == null -> existing.note
                note.isBlank() -> null
                else -> note.trim()
            },
        )
        return container.transactions.update(updated).toRecord(cats.associateBy { it.id })
    }

    suspend fun deleteTransaction(id: String): DeleteResult {
        val existing = findActive(id)
        container.transactions.softDelete(existing.id)
        return DeleteResult(existing.id, existing.title, formatRupees(existing.amount.minor))
    }

    // ---- helpers ----

    private suspend fun findActive(id: String): Transaction {
        val tx = container.transactions.get(id.trim())
        if (tx == null || tx.deletedAt != null) {
            throw AppFunctionElementNotFoundException("No record with id \"$id\". Use listTransactions to find ids.")
        }
        return tx
    }

    private suspend fun categoriesById(): Map<String, Category> =
        container.categories.getAll().associateBy { it.id }

    private fun otherId(type: TxType) =
        if (type == TxType.INCOME) DefaultCategories.OTHER_INCOME else DefaultCategories.OTHER_EXPENSE

    private fun parseType(raw: String): TxType = when (raw.trim().lowercase(Locale.ROOT)) {
        "expense", "spend", "spent", "debit" -> TxType.EXPENSE
        "income", "earning", "credit" -> TxType.INCOME
        else -> throw AppFunctionInvalidArgumentException("type must be \"expense\" or \"income\", got \"$raw\".")
    }

    private fun parseDate(raw: String?, field: String): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw.trim())
        } catch (e: DateTimeParseException) {
            throw AppFunctionInvalidArgumentException("$field must be an ISO date like 2026-11-19, got \"$raw\".")
        }
    }

    private fun unknownCategory(query: String, cats: List<Category>) =
        AppFunctionInvalidArgumentException(
            "No category matches \"$query\". Available categories: ${cats.joinToString { it.name }}."
        )

    private fun norm(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Matches by id, exact name, then substring or word match. Prefers categories of [type] when given. */
    private fun resolveCategory(query: String?, type: TxType?, cats: List<Category>): Category? {
        if (query.isNullOrBlank()) return null
        val q = norm(query)
        if (q.isEmpty()) return null
        val pools = if (type != null) listOf(cats.filter { it.type == type }, cats) else listOf(cats)
        for (pool in pools) {
            pool.firstOrNull { it.id.equals(query.trim(), ignoreCase = true) || norm(it.id) == q }?.let { return it }
            pool.firstOrNull { norm(it.name) == q }?.let { return it }
            pool.firstOrNull { norm(it.name).contains(q) }?.let { return it }
            pool.firstOrNull { c ->
                c.name.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+"))
                    .any { w -> w.length >= 3 && q.startsWith(w) }
            }?.let { return it }
        }
        return null
    }

    private fun Transaction.toRecord(cats: Map<String, Category>) = TransactionRecord(
        id = id,
        title = title,
        note = note,
        amountDisplay = formatRupees(amount.minor),
        amountMinor = amount.minor,
        category = cats[categoryId]?.name ?: categoryId,
        date = occurredOn.toString(),
        type = typeName(type),
    )

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100

        fun typeName(type: TxType) = if (type == TxType.INCOME) "income" else "expense"

        /** "₹415" for whole rupees, "₹99.50" otherwise, with thousands separators. */
        fun formatRupees(minor: Long): String {
            val neg = minor < 0
            val abs = Math.abs(minor)
            val whole = String.format(Locale.US, "%,d", abs / 100)
            val paise = abs % 100
            val body = if (paise == 0L) whole else "$whole.${paise.toString().padStart(2, '0')}"
            return (if (neg) "-" else "") + "₹" + body
        }
    }
}
