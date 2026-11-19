package com.example.financetracker.ai

import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.TxType
import java.time.LocalDate

/** One record extracted from free text, before it is saved. */
data class ParsedEntry(
    val title: String,
    val note: String?,
    val amount: Money,
    val type: TxType,
    val categoryId: String,
    val occurredOn: LocalDate,
    /** The raw fragment this came from, e.g. "movie o.m 200 rs". */
    val sourceText: String,
)

data class ParseResult(
    val entries: List<ParsedEntry>,
    /** Fragments that looked like entries but had no amount, e.g. "coffee". */
    val unparsed: List<String>,
    /** True when an on-device LLM refined titles/categories. */
    val refinedByLlm: Boolean,
)

/** Rule-based parser contract (deterministic; owns amounts and dates). */
interface EntryParser {
    fun parse(text: String, today: LocalDate): ParseResult
}

/**
 * Full pipeline used by Chat UI and AppFunctions: rules -> optional LLM refine -> validate.
 * Never throws for bad input; returns empty entries + unparsed instead.
 */
interface ChatEntryPipeline {
    suspend fun parse(text: String, today: LocalDate = LocalDate.now()): ParseResult
}
