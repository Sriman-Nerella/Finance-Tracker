package com.example.financetracker.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/** One saved income or expense record in the finance tracker. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TransactionRecord(
    /** Unique id of the record. Pass it to updateTransaction or deleteTransaction. */
    val id: String,
    /** Short title, for example "Tea" or "Lunch". */
    val title: String,
    /** Extra free text attached to the record, or null when there is none. */
    val note: String?,
    /** Amount for display in Indian rupees, for example "₹415" or "₹99.50". */
    val amountDisplay: String,
    /** Amount in paise (minor units). 41500 means ₹415. */
    val amountMinor: Long,
    /** Name of the category, for example "Food & Drinks". */
    val category: String,
    /** Date of the transaction in ISO format yyyy-MM-dd. */
    val date: String,
    /** Either "expense" or "income". */
    val type: String,
)

/** Result of logging free-text entries as new records. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class LogExpensesResult(
    /** The records that were created, in the order they appeared in the text. */
    val records: List<TransactionRecord>,
    /** Sum of the created expense records for display, for example "₹215". */
    val totalExpenseDisplay: String,
    /** Sum of the created expense records in paise. */
    val totalExpenseMinor: Long,
    /** Sum of the created income records for display, for example "₹0". */
    val totalIncomeDisplay: String,
    /** Parts of the text that had no amount and were therefore not saved. Empty when everything was saved. */
    val unparsedFragments: List<String>,
)

/** Total of one category within a period. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategoryAmount(
    /** Category name, for example "Transport". */
    val category: String,
    /** Either "expense" or "income". */
    val type: String,
    /** Total for the category for display, for example "₹1,200". */
    val totalDisplay: String,
    /** Total for the category in paise. */
    val totalMinor: Long,
)

/** Income and expense totals for a period. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SpendingSummary(
    /** First day of the period in ISO format yyyy-MM-dd. */
    val from: String,
    /** Last day of the period in ISO format yyyy-MM-dd. */
    val to: String,
    /** Total expenses for display, for example "₹5,400". */
    val totalExpenseDisplay: String,
    /** Total expenses in paise. */
    val totalExpenseMinor: Long,
    /** Total income for display, for example "₹30,000". */
    val totalIncomeDisplay: String,
    /** Total income in paise. */
    val totalIncomeMinor: Long,
    /** Per-category totals, largest first. Only categories with activity are included. */
    val byCategory: List<CategoryAmount>,
)

/** A list of records. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TransactionList(
    /** Matching records, newest first. */
    val records: List<TransactionRecord>,
    /** Number of records returned. */
    val count: Int,
)

/** Confirmation that a record was deleted. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DeleteResult(
    /** Id of the deleted record. */
    val id: String,
    /** Title of the deleted record. */
    val title: String,
    /** Amount of the deleted record for display, for example "₹15". */
    val amountDisplay: String,
)
