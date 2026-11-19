package com.example.financetracker.ai

import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.TxType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Pure guardrails for LLM output. The LLM may only refine title / note / category / type;
 * amounts, dates and source text always come from the rule-based entries.
 */
class LlmRefinementValidator {

    @Serializable
    internal data class LlmItem(
        val index: Int? = null,
        val title: String? = null,
        val note: String? = null,
        val categoryId: String? = null,
        val type: String? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Returns refined entries (same order/count as [ruleEntries]), or null when the response is
     * unusable as a whole (not JSON, wrong count, wrong/duplicate indices). Individual invalid
     * fields fall back to the rule values.
     */
    fun validate(ruleEntries: List<ParsedEntry>, allowed: List<Category>, raw: String): List<ParsedEntry>? {
        if (ruleEntries.isEmpty()) return null
        val items = parse(raw) ?: return null
        if (items.size != ruleEntries.size) return null
        val byIndex = items.associateBy { it.index ?: return null }
        if (byIndex.size != items.size) return null
        if (byIndex.keys != ruleEntries.indices.toSet()) return null

        val categories = allowed.associateBy { it.id }
        return ruleEntries.mapIndexed { i, rule -> merge(rule, byIndex.getValue(i), categories) }
    }

    private fun merge(rule: ParsedEntry, item: LlmItem, categories: Map<String, Category>): ParsedEntry {
        val title = item.title?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE } ?: rule.title
        val note = item.note?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NOTE } ?: rule.note

        val llmType = item.type?.trim()?.let { t -> TxType.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } }
        val llmCategory = item.categoryId?.trim()?.let { categories[it] }
        var type = rule.type
        var categoryId = rule.categoryId
        if (llmCategory != null && llmCategory.type == (llmType ?: rule.type)) {
            type = llmCategory.type
            categoryId = llmCategory.id
        }
        // amount, occurredOn, sourceText intentionally untouched.
        return rule.copy(title = title, note = note, type = type, categoryId = categoryId)
    }

    private fun parse(raw: String): List<LlmItem>? {
        val cleaned = stripFences(raw)
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString(ListSerializer(LlmItem.serializer()), cleaned.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    private fun stripFences(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```").let { rest ->
                // drop optional language tag on the first line
                val nl = rest.indexOf('\n')
                if (nl >= 0 && rest.substring(0, nl).none { it == '[' || it == '{' }) rest.substring(nl + 1) else rest
            }
            s = s.trimEnd().removeSuffix("```")
        }
        return s.trim()
    }

    companion object {
        const val MAX_TITLE = 40
        const val MAX_NOTE = 120
    }
}
