package com.example.financetracker.appfunctions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.example.financetracker.FinanceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AppFunctions entry point. KSP generates the concrete `FinanceAppFunctionService` in this
 * package plus assets/finance_app_function_service.xml. Logic lives in [FinanceFunctions].
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "FinanceAppFunctionService",
    appFunctionXmlFileName = "finance_app_function_service",
)
abstract class BaseFinanceAppFunctionService : AppFunctionService() {

    private val functions by lazy {
        FinanceFunctions((applicationContext as FinanceApp).container)
    }

    /**
     * Saves every expense or income found in free text as a new record in the finance tracker.
     * Use when the user lists spending in natural language, such as "add tea 15 rupees and lunch
     * 200 to my finance tracker" or "nov 19: tea 15rs, lunch 200rs, movie 200rs". Each item with an
     * amount becomes one record; words around the amount become the title or note, and a date in
     * the text applies to the items after it. Items without an amount are skipped and reported.
     *
     * @param text The user's entries in their own words. Each item must include an amount, and may
     *   include a date, for example "nov 19\ntea 15rs, lunch 200rs".
     * @return The created records with ids, amounts, categories and dates, the expense total, and
     *   any fragments that were skipped because they had no amount.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun logExpensesFromText(text: String): LogExpensesResult =
        withContext(Dispatchers.IO) { functions.logExpensesFromText(text) }

    /**
     * Adds one expense or income record to the finance tracker with explicit fields. Use when the
     * user gives a single transaction with a clear amount, such as "I got a 50000 salary today" or
     * "log 120 rupees for an auto ride yesterday".
     *
     * @param amountRupees Amount in rupees as a positive number, for example 150 or 99.5.
     * @param title Short name of the transaction, for example "Lunch" or "Salary".
     * @param type Either "expense" for money spent or "income" for money received.
     * @param category Optional category name or id, for example "Food", "Transport", "Groceries",
     *   "Salary". The closest match is used. If null or unmatched, the "Other" category is used.
     * @param date Optional date as ISO yyyy-MM-dd, for example 2026-11-19. If null, today is used.
     * @param note Optional extra detail to store with the record.
     * @return The created record with its id, display amount, category and date.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addTransaction(
        amountRupees: Double,
        title: String,
        type: String,
        category: String?,
        date: String?,
        note: String?,
    ): TransactionRecord = withContext(Dispatchers.IO) {
        functions.addTransaction(amountRupees, title, type, category, date, note)
    }

    /**
     * Returns total expenses, total income and a per-category breakdown for a period. Use when the
     * user asks "how much did I spend this month", "what did I spend on food last month" or "what
     * is my spending today". Reads only; nothing is changed.
     *
     * @param period One of TODAY, THIS_WEEK (Monday to Sunday), THIS_MONTH, LAST_MONTH or CUSTOM.
     *   With CUSTOM, from is required.
     * @param from Start date as ISO yyyy-MM-dd. Only used when period is CUSTOM.
     * @param to End date as ISO yyyy-MM-dd, inclusive. Only used when period is CUSTOM; if null,
     *   today is used.
     * @param category Optional category name or id to restrict the totals to one category, for
     *   example "Food". If null, all categories are included.
     * @return Expense and income totals as display strings such as "₹415" and in paise, plus the
     *   per-category breakdown, largest first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getSpendingSummary(
        period: String,
        from: String?,
        to: String?,
        category: String?,
    ): SpendingSummary = withContext(Dispatchers.IO) {
        functions.getSpendingSummary(period, from, to, category)
    }

    /**
     * Lists saved transactions, newest first, optionally filtered by date range and category. Use
     * when the user asks "show my recent expenses", "what did I buy last week" or "list my food
     * spending in November". Reads only; nothing is changed.
     *
     * @param from Optional first date as ISO yyyy-MM-dd. If null, no lower bound.
     * @param to Optional last date as ISO yyyy-MM-dd, inclusive. If null, no upper bound.
     * @param category Optional category name or id to filter by, for example "Transport".
     * @param limit Maximum number of records to return, 1 to 100. If 0 or omitted, 20 are returned.
     * @return Matching records newest first, with ids, amounts, categories and dates.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listTransactions(
        from: String?,
        to: String?,
        category: String?,
        limit: Int,
    ): TransactionList = withContext(Dispatchers.IO) {
        functions.listTransactions(from, to, category, limit)
    }

    /**
     * Changes fields of an existing transaction. Use when the user corrects a record, such as
     * "change the lunch to 250 rupees" or "move that to the Transport category". Only the fields
     * that are provided change; null fields stay as they are. The record id comes from
     * listTransactions, logExpensesFromText or addTransaction.
     *
     * @param id Id of the transaction to change.
     * @param title New title, or null to keep the current one.
     * @param amountRupees New amount in rupees as a positive number, or null to keep the current one.
     * @param type New type, "expense" or "income", or null to keep the current one.
     * @param category New category name or id, or null to keep the current one. If it matches
     *   nothing, the "Other" category is used.
     * @param date New date as ISO yyyy-MM-dd, or null to keep the current one.
     * @param note New note, or null to keep the current one. An empty string removes the note.
     * @return The updated record.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun updateTransaction(
        id: String,
        title: String?,
        amountRupees: Double?,
        type: String?,
        category: String?,
        date: String?,
        note: String?,
    ): TransactionRecord = withContext(Dispatchers.IO) {
        functions.updateTransaction(id, title, amountRupees, type, category, date, note)
    }

    /**
     * Deletes a transaction from the finance tracker lists and totals. The deletion is soft: the
     * user can undo it in the app. Use when the user asks to remove a record, such as "delete the
     * movie expense". The record id comes from listTransactions.
     *
     * @param id Id of the transaction to delete.
     * @return The id, title and amount of the deleted record.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun deleteTransaction(id: String): DeleteResult =
        withContext(Dispatchers.IO) { functions.deleteTransaction(id) }
}
